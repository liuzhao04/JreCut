package com.jrecut;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import com.jrecut.utils.FileUtils;
import com.jrecut.utils.Pair;

public class JavaClassExecutor {
	private String out = "output";
	private String pwd = ".";
	private File root;
	private String classes = "classes"; // class 拷贝输出目录
	private String bin = "bin"; // bin目录
	private String binOther = "bin\\other";  // 非jdk中的依赖
	private String lib = "lib"; // bin目录
	private String extRes = "extRes.txt";
	private String jrePath = null;
	private String pubJre = "jre";

	private String mainClass;
	private String runLib;

	final Map<String, String> jarPathMap = new HashMap<String, String>();

	public JavaClassExecutor() throws IOException {
		init();
	}

	public JavaClassExecutor(String pwd) throws IOException {
		if (pwd != null && !pwd.trim().equals("")) {
			this.pwd = pwd;
		}
		init();
	}

	public JavaClassExecutor(String pwd, String out) throws IOException {
		if (pwd != null && !pwd.trim().equals("")) {
			this.pwd = pwd;
		}
		if (out != null && !out.trim().equals("")) {
			this.out = out;
		}
		init();
	}

	private void init() throws IOException {
		String wroot = new File(pwd).getAbsolutePath();
		String sroot = wroot + File.separator + out;
		root = new File(sroot);
		if (!root.exists()) {
			if (!root.mkdirs()) {
				throw new IOException("输出根目录创建失败:" + sroot);
			}
		} else if (root.isFile()) {
			throw new IOException("输出根目录为文件:" + sroot);
		}
		System.out.println("工具执行目录:" + wroot);
		System.out.println("工具执行输出目录:" + sroot);

		historySolution.clear();
	}

	/**
	 * 清空输出根目录
	 * 
	 * @throws IOException
	 */
	public void resetOutputDir() throws IOException {
		System.out.println("清空根目录:" + root.getAbsolutePath());
		if (root.exists()) {
			if (!FileUtils.deleteAllFilesOfDir(root)) {
				System.err.println("清空输入目录失败：" + root.getAbsolutePath());
			}
		}
		init();
	}

	/**
	 * 执行环境变量中的某个main类
	 * 
	 * @param mclass 含main的类
	 * @param libDir 依赖lib包
	 * @param append 追加模式标志，true表示jar/dll/class/配置文件都是在现有工作输出目录中新增，否则清空工作目录后在新增
	 */
	public void exec(String mclass, String libDir, boolean append) {
		if (append) {
			try {
				resetOutputDir();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		this.mainClass = mclass;
		this.runLib = libDir;
		List<String> cmds = new ArrayList<String>();
		cmds.add("java");
		cmds.add("-verbose");
		if (libDir != null && !"".equals(libDir.trim())) {
			cmds.add("-Djava.ext.dirs=" + libDir.trim());
		}
		cmds.add(mclass);
		printCmd(cmds);

		String[] cmdArr = new String[cmds.size()];
		cmds.toArray(cmdArr);
		EasyProcess p = new EasyProcess(cmdArr);

		final Map<String, List<String>> clss = new HashMap<String, List<String>>();
		final List<String> modules = new ArrayList<String>();

		// 0。 依赖查找
		resourceSearch(p, clss, jarPathMap, modules);

		// 1. 拷贝modules
		copyModules(modules);

		// 2. 拷贝class(包含各个jar包中的资源文件，所有资源文件都拷贝)
		copyClasses(clss, jarPathMap);

		// 3. 拷贝常见配置文件(由配置文件中指定)
		copyExtResource();

		// 4. 重新打包
		rePackage();

		// 5. 测试与异常处理
		boolean success = testAtNewJre();

		// 6. 发布jre
		publishJre(false);

		if (!success) {
			System.err.println("抱歉，生成的JRE可能无法正确执行目标程序，请尝试手动优化存在的问题,异常信息如下：");
			System.err.println(testErrorMessage);
		}
	}

	private String testErrorMessage; // 测试异常日志

	/**
	 * 测试是否能够正常运行目标程序，不能则尽量解决问题后，在测试，是在无法解决时，返回false
	 * 
	 * @return 成功返回true,否则false
	 */
	private boolean testAtNewJre() {
		int i = 1;
		String tmp = null;
		while ((tmp = testTargetCmd()) != null && !tmp.trim().equals("")) {
			System.out.println("目标程序测试，第" + i + "次测试，异常信息如下：");
			this.testErrorMessage = tmp;
			System.err.println(tmp);

			// 尝试解决异常
			// 1. 分析异常信息，得到解决方案
			Pair<Integer, String> solution = ErrorMessageAnalysis.analysisError(tmp);
			// 2. 一次解决一个问题，如果返回为空，表示没有解决方案，直接返回false
			if (solution == null) {
				System.err.println("目标程序测试，第" + i + "次测试的异常未找到对应的解决方案");
				return false;
			}

			// 3. 解决问题
			String error = solveException(solution);
			if (error != null) {
				System.err.println("目标程序测试，第" + i + "次测试的异常应用解决方案后，处理失败：");
				System.err.println(error);
				String warn = "应用解决方案：" + solution.toString() + "\r\n";
				this.testErrorMessage += "\r\n" + warn + error; // 记录处理流程
				return false;
			}

			i++;
		}
		System.out.println("目标程序测试，第" + i + "次测试，测试成功");
		return true;
	}

	// 解决方案的历史记录
	private List<Pair<Integer, String>> historySolution = new ArrayList<Pair<Integer, String>>();

	/**
	 * 解决问题
	 * 
	 * @param solution
	 * @return
	 */
	private String solveException(Pair<Integer, String> solution) {
		if (solution == null) {
			return "无解决方案";
		}

		if (historySolution.contains(solution)) {
			return "与历史解决方案完全重复，第一次解决时未成功，不再重复尝试";
		}

		switch (solution.getKey()) {
		case ErrorMessageAnalysis.SOLV_TYPE_NO_CLASS_DEF_FOUND_ERR:
			// 1. 在java包中遍历查找目标class，拷贝找到的class
			System.out.println("尝试寻找并加载类：" + solution.getValue() + "...");
			for (String jar : jarPathMap.keySet()) {
				String error = loadAndCopyClass(jarPathMap.get(jar), jar, solution.getValue());
				if (null == error) {
					System.out.println(jar + "中尝试处理成功");
					historySolution.add(solution);
					return null;
				}
				System.out.println(jar + "中尝试失败：" + error);
			}

			// 3. 重新打包
			rePackage();

			historySolution.add(solution);
			return "尝试加载：" + solution.getValue() + "失败";
		default:
			return "不支持的解决方案类型：" + solution.getKey();
		}

	}

	private void printCmd(List<String> cmds) {
		int i = 0;
		StringBuilder sb = new StringBuilder();
		for (String str : cmds) {
			if (i++ > 0) {
				sb.append(" ");
			}
			sb.append(str);
		}
		System.out.println("exec cmd : " + sb.toString());
	}

	/**
	 * 目标程序测试，测试成功返回空，失败返回异常提示流
	 * 
	 * @return
	 */
	private String testTargetCmd() {
		List<String> cmds = new ArrayList<String>();

//		String javaCmd = "jre\\bin\\java";
		String javaCmd = root + File.separator + pubJre + File.separator + "bin\\java";
		cmds.add(javaCmd);
		if (this.runLib != null && !"".equals(this.runLib.trim())) {
			cmds.add("-Djava.ext.dirs=" + this.runLib.trim());
		}
		cmds.add(this.mainClass);
		printCmd(cmds);

		final Pair<Integer, String> error = new Pair<Integer, String>(1, null);
		String[] cmdArr = new String[cmds.size()];
		cmds.toArray(cmdArr);
		EasyProcess p = new EasyProcess(cmdArr);
		try {
			p.dir(this.pwd).run(new EasyProcess.StreamParserAdpter() {

				@Override
				public void handleOut(BufferedReader out) {
					super.handleOut(out);
				}

				@Override
				public void handleErr(BufferedReader err) {
					String errorMsg = EasyProcess.handlerOutStrem(err);
					error.setValue(errorMsg);
					// super.handleErr(err);
				}

			});
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
		return error.getValue();
	}

	/**
	 * 将output目录下的lib,bin目录，整理成标准的JRE目录
	 * 
	 * @param withOtherModules 是否连同other类型的dll一起发布
	 */
	public void publishJre(boolean withOtherModules) {
		// 1. 拷贝bin目录
		String binDir = root + File.separator + bin;
		String binDst = root + File.separator + pubJre + File.separator + "bin";
		System.out.println("publish jre dir ...");
		File orgBinFile = new File(binDir);
		if (orgBinFile.exists()) {
			try {
				System.out.println("publish bin dir ...");
				FileUtils.copyDirTo(orgBinFile, new File(binDst), new FileFilter() {

					@Override
					public boolean accept(File pathname) {
						if (pathname.isFile()) {
							return true;
						}
						if (pathname.getName().equals(new File(binOther).getName())) {
							if (pathname.getParentFile().getName()
									.equals(new File(binOther).getParentFile().getName())) {
								return false;
							}
						}
						return true;
					}
				});
				// 如果需要发布，则手动拷贝other下的所有文件到bin
				if (withOtherModules) {
					File binOtherFile = new File(root + File.separator + binOther);
					if (binOtherFile.exists() && binOtherFile.isDirectory()) {
						System.out.println("publish bin other dir ...");
						FileUtils.copyDirTo(binOtherFile, new File(binDst));
					}
				}
			} catch (IOException e) {
				System.err.println("publish jre bin/binOther failed : " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			System.err.println("publish jre error , dir is not exist : " + binDir);
		}

		// 2. 拷贝lib目录
		String libDir = root + File.separator + lib;
		String libDst = root + File.separator + pubJre + File.separator + "lib";
		File orgLibFile = new File(libDir);
		System.out.println("publish lib dir ...");
		//
		if (orgLibFile.exists() && orgLibFile.isDirectory()) {
			try {
				FileUtils.copyDirTo(orgLibFile, new File(libDst));
			} catch (IOException e) {
				System.err.println("publish jre lib failed : " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			System.err.println("publish jre error , dir is not exist : " + libDir);
		}
		System.out.println("publish jre finished");
	}

	/**
	 * 重新打包
	 */
	public void rePackage() {
		String classesPath = root.getAbsolutePath() + File.separator + classes;
		File p = new File(classesPath);
		if (!p.exists()) {
			System.err.println("class 输出目录不存在：" + classesPath);
			return;
		}

		File[] dirs = p.listFiles();
		if (dirs == null) {
			System.out.println("class 输出目录无数据：" + classesPath);
			return;
		}

		for (File d : dirs) {
			if (d.isDirectory()) {
				final String jarName = d.getAbsolutePath() + ".jar";
				String[] cmds = new String[] { "jar", "cvf", jarName, "*" };
				new EasyProcess(cmds).dir(d.getAbsolutePath()).run(new EasyProcess.StreamParserAdpter());
				File jarFile = new File(jarName);
				String dstPath = root.getAbsolutePath() + File.separator + lib + File.separator + jarFile.getName();
				try {
					FileUtils.copyTo(jarFile, new File(dstPath));
					System.err.println("copied " + dstPath);
				} catch (IOException e) {
					System.err.println("copy jar failed : " + dstPath);
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * 拷贝必备配置文件
	 */
	private void copyExtResource() {
		if (!new File(extRes).exists()) {
			System.err.println("extRes 配置不存在:" + extRes);
			return;
		}
		try {
			List<String> files = FileUtils.readFile("extRes.txt", null);
			for (String file : files) {
				String org = jrePath + File.separator + file;
				String dst = null;
				if (org.contains("\\jre\\lib\\")) {
					dst = root.getAbsolutePath() + File.separator + lib;
				} else {
					dst = root.getAbsolutePath() + File.separator + bin;
				}
				dst += File.separator + file.replaceAll("^((lib\\\\)|(bin\\\\))", "");
				try {
					FileUtils.copyTo(new File(org), new File(dst));
				} catch (IOException e) {
					System.err.println("copy ext resource failed : " + file);
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			System.err.println("copy ext resources failed ");
			e.printStackTrace();
		}

	}

	/**
	 * 拷贝class(包含各个jar包中的资源文件，所有资源文件都拷贝)
	 * 
	 * @param clss
	 * @param jarPathMap
	 */
	private void copyClasses(Map<String, List<String>> clss, Map<String, String> jarPathMap) {
		String classesPath = root.getAbsolutePath() + File.separator + classes; // class输出目录

		// 手动增加resources.jar，因为当中有些资源文件需要加载
		String resourcesJar = "resources.jar";
		jarPathMap.put(resourcesJar, jrePath + File.separator + lib + File.separator + resourcesJar);
		clss.put(resourcesJar, Collections.<String>emptyList());

		for (String jar : clss.keySet()) {
			if (!jar.endsWith(".jar")) {
				continue;
			}
			List<String> clssList = clss.get(jar);
			String jarPath = jarPathMap.get(jar);

			List<String> nameList = new ArrayList<String>();

			// 1. 开始扫描jar包中的资源
			System.out.println("scanning jar : " + jarPath);
			try {
				JarInputStream jarIn = new JarInputStream(new FileInputStream(jarPath));
				JarEntry entry = jarIn.getNextJarEntry();
				while (null != entry) {
					String name = entry.getName();
					// 1. 所有异常类都要拷贝
					if (name.endsWith("Exception.class") || name.endsWith("Error.class")) {
						nameList.add(name.replace("/", "."));
					}
					// 2. 所有非.class文件
					else if (!name.endsWith(".class") && !name.endsWith("/")) {
						nameList.add(name);
					} else {
						for (String c : clssList) {
							String cname = c + ".class";
							if (name.replace("/", ".").equals(cname)) {
								nameList.add(cname);
							}
						}
					}

					entry = jarIn.getNextJarEntry();
				}
				jarIn.close();

				// 2. 开始拷贝资源
				URLClassLoader myClassLoader = new URLClassLoader(new URL[] { new URL("file:/" + jarPath) },
						Thread.currentThread().getContextClassLoader());

				String jarName = jar.replace(".jar", "");
				String cdir = classesPath + File.separator + jarName; // jar包目录

				for (String c : nameList) {
					InputStream is = null;
					String outputPath = null;
					if (!c.endsWith(".class")) { // 加载非class文件
						is = myClassLoader.getResourceAsStream(c);
						if (is == null) {
							System.err.println("copy resource fail : " + c);
							continue;
						}
						outputPath = cdir + "/" + c;
					} else {
						String cpath = c.replace(".", "/").replaceAll("/class$", ".class");
						// 加载类资源：
						// 1. 在当前类加载器中加载资源，如找不到，则在系统加载器中加载
						is = myClassLoader.getResourceAsStream(cpath);
						if (is == null) {
							// 2. 尝试手动加载，失败则放弃拷贝
							try {
								myClassLoader.loadClass(c);
								is = myClassLoader.getResourceAsStream(cpath);
							} catch (Exception e) {
								System.err.println("copy class fail : " + c);
								e.printStackTrace();
								continue;
							}
							// 找不到资源，不再拷贝
							if (is == null) {
								System.out.println("copy class fail : " + c);
								continue;
							}
						}
						outputPath = cdir + "/" + cpath;
					}
					File f = new File(outputPath);
					if (!f.getParentFile().exists()) {
						if (!f.getParentFile().mkdirs()) {
							System.out.println("copy resource/class fail - 创建父路径失败 : " + c);
							continue;
						}
					}

					try {
						FileOutputStream fos = new FileOutputStream(f);
						byte[] buffered = new byte[1024];
						int size = -1;
						while ((size = is.read(buffered)) != -1) {
							fos.write(buffered, 0, size);
						}
						fos.flush();
						fos.close();
						is.close();
						System.out.println("copied class : " + c);
					} catch (Exception e) {
						System.out.println("copy resource fail - 拷贝发生异常 : " + c);
						e.printStackTrace();
					}
				}

			} catch (Exception e) {
				System.out.println("scan jar failed : " + jarPath);
				e.printStackTrace();
			}
		}

	}

	/**
	 * 尝试从某个jar包中加载某个类
	 * 
	 * @param jarPath
	 * @param jar
	 * @param clss
	 * @return
	 */
	private String loadAndCopyClass(String jarPath, String jar, String clss) {
		String classesPath = root.getAbsolutePath() + File.separator + classes; // class输出目录
		String jarName = jar.replace(".jar", "");
		String cdir = classesPath + File.separator + jarName; // jar包目录
		try {
			URLClassLoader myClassLoader = new URLClassLoader(new URL[] { new URL("file:/" + jarPath) },
					Thread.currentThread().getContextClassLoader());

			InputStream is = null;
			String outputPath = null;
			String cpath = clss.replace("/", ".") + ".class";
			// 加载类资源：
			// 1. 在当前类加载器中加载资源，如找不到，则在系统加载器中加载
			is = myClassLoader.getResourceAsStream(cpath);
			if (is == null) {
				// 2. 尝试手动加载，失败则放弃拷贝
				try {
					myClassLoader.loadClass(clss);
					is = myClassLoader.getResourceAsStream(cpath);
				} catch (Exception e) {
					System.err.println("copy class fail : " + clss);
					e.printStackTrace();
					return "copy class fail : " + clss;
				}
				// 找不到资源，不再拷贝
				if (is == null) {
					System.out.println("copy class fail : " + clss);
					return "copy class fail : " + clss;
				}
			}
			outputPath = cdir + "/" + cpath;
			File f = new File(outputPath);
			if (!f.getParentFile().exists()) {
				if (!f.getParentFile().mkdirs()) {
					System.out.println("copy class fail - 创建父路径失败 : " + clss);
					return "copy class fail : " + clss;
				}
			}

			try {
				FileOutputStream fos = new FileOutputStream(f);
				byte[] buffered = new byte[1024];
				int size = -1;
				while ((size = is.read(buffered)) != -1) {
					fos.write(buffered, 0, size);
				}
				fos.flush();
				fos.close();
				is.close();
				System.out.println("copied class : " + clss);
				return null;
			} catch (Exception e) {
				System.err.println("copy resource fail - 拷贝发生异常 : " + clss);
				e.printStackTrace();
				return "copy resource fail - 拷贝发生异常 : " + clss;
			}
		} catch (Exception e) {
			String msg = "尝试加载类时发生异常：" + clss + "\r\n" + e.getMessage();
			System.err.println(msg);
			e.printStackTrace();
			return msg;
		}

	}

	/**
	 * 拷贝modules
	 * 
	 * @param modules
	 */
	private void copyModules(List<String> modules) {
		String mRoot = root + File.separator + bin;
		String mOther = root + File.separator + binOther;
		System.out.println("copy modules ...");
		// module分成：jre\bin,bin,other三种
		for (String m : modules) {
			// 1. \jre\bin\ 或者 \bin\
			if (m.contains("\\jre\\bin\\") || m.contains("\\bin\\")) {
				if (jrePath == null && m.contains("\\jre\\bin\\")) {
					String tmps = m.replaceAll("jre\\\\bin\\\\.*$", "");
					jrePath = tmps + "jre";
				}
				String[] tmp = m.split("\\\\");
				String name = tmp[tmp.length - 1];
				File org = new File(m);
				File dst = new File(mRoot + File.separator + name);
				try {
					FileUtils.copyTo(org, dst);
					System.out.println("copied file " + m);
				} catch (IOException e) {
					System.out.println("copy file failed :　" + m);
					e.printStackTrace();
				}
			}
			// 2. other
			else {
				String[] tmp = m.split("\\\\");
				String name = tmp[tmp.length - 1];
				File org = new File(m);
				File dst = new File(mOther + File.separator + name);
				try {
					FileUtils.copyTo(org, dst);
					System.out.println("copied file " + m);
				} catch (IOException e) {
					System.out.println("copy file failed :　" + m);
					e.printStackTrace();
				}
			}
		}

	}

	/**
	 * 搜索资源
	 * 
	 * @param p
	 * @param clss
	 * @param jarPathMap
	 * @param modules
	 */
	private void resourceSearch(EasyProcess p, final Map<String, List<String>> clss,
			final Map<String, String> jarPathMap, final List<String> modules) {
		p.dir(this.pwd).run(new EasyProcess.StreamParserAdpter() {
			@Override
			public void handleOut(BufferedReader out) {
				String line = null;
				System.out.println("searching classes ...");
				try {
					while ((line = out.readLine()) != null) {
						if (line.matches("\\[Opened .*\\]")) {
							String jarPath = line.replace("[Opened ", "").replaceAll("]", "");
							String[] tmp = jarPath.split("\\\\");
							String jarName = tmp[tmp.length - 1];
							clss.put(jarName, new ArrayList<String>());
							jarPathMap.put(jarName, jarPath);
						} else {
							if (!line.startsWith("[Loaded ")) {
								continue;
							}
							String jarPath = line.replace("[Loaded ", "").replaceAll("]", "");
							String[] tmp = jarPath.split(" from ");
							if (tmp.length < 2) {
								continue;
							}
							String clssName = tmp[0];
							String jar = tmp[1];

							jarPath = jar.substring(0, jar.length());

							if (jar.contains("/")) {
								tmp = jar.split("/");
							} else {
								tmp = jar.split("\\\\");
							}
							String jarName = tmp[tmp.length - 1];

							List<String> t = clss.get(jarName);
							if (t == null) {
								t = new ArrayList<String>();
								clss.put(jarName, t);
							}
							t.add(clssName);

							jarPathMap.put(jarName, jarPath.replace("file:/", ""));
						}
					}
					System.out.println("jar count : " + jarPathMap.size());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			private int isProcessCount = 0;

			@Override
			public void init(long pid) {
				super.init(pid);
				isProcessCount = 0;
			}

			@Override
			public void isProcess(long pid) {

				this.pid = pid;
				isProcessCount++;

				System.out.println("searching modules [" + isProcessCount + "] ...");
				List<String> list = JavaProcessUtils.listMoudles(pid);
				for (String m : list) {
					if (!modules.contains(m)) {
						modules.add(m);
					}
				}
				System.out.println("moudules size : " + modules.size());
			}

			@Override
			public void finish() {
				System.out.println("searching modules finished ...");
			}
		});
	}

}
