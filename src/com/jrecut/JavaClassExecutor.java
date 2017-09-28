package com.jrecut;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import com.jrecut.utils.FileUtils;

public class JavaClassExecutor {
	private String out = "output";
	private String pwd = ".";
	private File root;
	private String classes = "classes"; // class �������Ŀ¼
	private String bin = "bin"; // binĿ¼
	private String binOther = "bin\\other";  // ��jdk�е�����
	private String lib = "lib"; // binĿ¼
	private String extRes = "extRes.txt";
	private String jrePath = null;

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
				throw new IOException("�����Ŀ¼����ʧ��:" + sroot);
			}
		} else if (root.isFile()) {
			throw new IOException("�����Ŀ¼Ϊ�ļ�:" + sroot);
		}
		System.out.println("����ִ��Ŀ¼:" + wroot);
		System.out.println("����ִ�����Ŀ¼:" + sroot);
	}

	/**
	 * ��������Ŀ¼
	 * 
	 * @throws IOException
	 */
	public void resetOutputDir() throws IOException {
		System.out.println("��ո�Ŀ¼:" + root.getAbsolutePath());
		if (root.exists()) {
			if (!FileUtils.deleteAllFilesOfDir(root)) {
				System.err.println("�������Ŀ¼ʧ�ܣ�" + root.getAbsolutePath());
			}
		}
		init();
	}

	/**
	 * ִ�л��������е�ĳ��main��
	 * 
	 * @param mclass ��main����
	 * @param libDir ����lib��
	 * @param append ׷��ģʽ��־��true��ʾjar/dll/class/�����ļ����������й������Ŀ¼��������������չ���Ŀ¼��������
	 */
	public void exec(String mclass, String libDir, boolean append) {
		if (append) {
			try {
				resetOutputDir();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		List<String> cmds = new ArrayList<String>();
		cmds.add("java");
		cmds.add("-verbose");
		if (libDir != null && !"".equals(libDir.trim())) {
			cmds.add("-Djava.ext.dirs=" + libDir.trim());
		}
		cmds.add(mclass);

		int i = 0;
		StringBuilder sb = new StringBuilder();
		for (String str : cmds) {
			if (i++ > 0) {
				sb.append(" ");
			}
			sb.append(str);
		}
		System.out.println("exec cmd : " + sb.toString());

		String[] cmdArr = new String[cmds.size()];
		cmds.toArray(cmdArr);
		EasyProcess p = new EasyProcess(cmdArr);

		final Map<String, List<String>> clss = new HashMap<String, List<String>>();
		final Map<String, String> jarPathMap = new HashMap<String, String>();
		final List<String> modules = new ArrayList<String>();

		// 0�� ��������
		resourceSearch(p, clss, jarPathMap, modules);

		// 1. ����modules
		copyModules(modules);

		// 2. ����class(��������jar���е���Դ�ļ���������Դ�ļ�������)
		copyClasses(clss, jarPathMap);

		// 3. �������������ļ�(�������ļ���ָ��)
		copyExtResource();

		// 4. ���´��
		rePackage();
	}

	/**
	 * �����ر������ļ�
	 */
	private void copyExtResource() {
		if (!new File(extRes).exists()) {
			System.err.println("extRes ���ò�����:" + extRes);
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
	 * ���´��
	 */
	public void rePackage() {
		String classesPath = root.getAbsolutePath() + File.separator + classes;
		File p = new File(classesPath);
		if (!p.exists()) {
			System.err.println("class ���Ŀ¼�����ڣ�" + classesPath);
			return;
		}

		File[] dirs = p.listFiles();
		if (dirs == null) {
			System.out.println("class ���Ŀ¼�����ݣ�" + classesPath);
			return;
		}

		for (File d : dirs) {
			if (d.isDirectory()) {
				final String jarName = d.getAbsolutePath() + ".jar";
				String[] cmds = new String[] { "jar", "cvf", jarName, "*" };
				new EasyProcess(cmds).dir(d.getAbsolutePath()).run(new EasyProcess.IStreamParser() {
					private long pid;

					@Override
					public void init(long pid) {
						this.pid = pid;
						System.out.println("packaging " + jarName);
					}

					@Override
					public void handleOut(BufferedReader out) {
						EasyProcess.handlErr(out, pid);
					}

					@Override
					public void handleErr(BufferedReader err) {
						EasyProcess.handlErr(err, pid);
					}

					@Override
					public void finish() {
					}
				});
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
	 * ����class(��������jar���е���Դ�ļ���������Դ�ļ�������)
	 * 
	 * @param clss
	 * @param jarPathMap
	 */
	private void copyClasses(Map<String, List<String>> clss, Map<String, String> jarPathMap) {
		String classesPath = root.getAbsolutePath() + File.separator + classes; // class���Ŀ¼

		for (String jar : clss.keySet()) {
			if (!jar.endsWith(".jar")) {
				continue;
			}
			List<String> clssList = clss.get(jar);
			String jarPath = jarPathMap.get(jar);

			List<String> nameList = new ArrayList<String>();

			// 1. ��ʼɨ��jar���е���Դ
			System.out.println("scanning jar : " + jarPath);
			try {
				JarInputStream jarIn = new JarInputStream(new FileInputStream(jarPath));
				JarEntry entry = jarIn.getNextJarEntry();
				while (null != entry) {
					String name = entry.getName();
					// 1. �����쳣�඼Ҫ����
					if (name.endsWith("Exception.class")) {
						nameList.add(name.replace("/", "."));
					}
					// 2. ���з�.class�ļ�
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

				// 2. ��ʼ������Դ
				URLClassLoader myClassLoader = new URLClassLoader(new URL[] { new URL("file:/" + jarPath) },
						Thread.currentThread().getContextClassLoader());

				String jarName = jar.replace(".jar", "");
				String cdir = classesPath + File.separator + jarName; // jar��Ŀ¼

				for (String c : nameList) {
					InputStream is = null;
					String outputPath = null;
					if (!c.endsWith(".class")) { // ���ط�class�ļ�
						is = myClassLoader.getResourceAsStream(c);
						if (is == null) {
							System.err.println("copy resource fail : " + c);
							continue;
						}
						outputPath = cdir + "/" + c;
					} else {
						String cpath = c.replace(".", "/").replaceAll("/class$", ".class");
						// ��������Դ��
						// 1. �ڵ�ǰ��������м�����Դ�����Ҳ���������ϵͳ�������м���
						is = myClassLoader.getResourceAsStream(cpath);
						if (is == null) {
							// 2. �����ֶ����أ�ʧ�����������
							try {
								myClassLoader.loadClass(c);
								is = myClassLoader.getResourceAsStream(cpath);
							} catch (Exception e) {
								System.err.println("copy class fail : " + c);
								e.printStackTrace();
								continue;
							}
							// �Ҳ�����Դ�����ٿ���
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
							System.out.println("copy resource/class fail - ������·��ʧ�� : " + c);
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
						System.out.println("copy resource fail - ���������쳣 : " + c);
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
	 * ����modules
	 * 
	 * @param modules
	 */
	private void copyModules(List<String> modules) {
		String mRoot = root + File.separator + bin;
		String mOther = root + File.separator + binOther;
		System.out.println("copy modules ...");
		// module�ֳɣ�jre\bin,bin,other����
		for (String m : modules) {
			// 1. \jre\bin\ ���� \bin\
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
					System.out.println("copy file failed :��" + m);
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
					System.out.println("copy file failed :��" + m);
					e.printStackTrace();
				}
			}
		}

	}

	/**
	 * ������Դ
	 * 
	 * @param p
	 * @param clss
	 * @param jarPathMap
	 * @param modules
	 */
	private void resourceSearch(EasyProcess p, final Map<String, List<String>> clss,
			final Map<String, String> jarPathMap, final List<String> modules) {
		p.dir(this.pwd).run(new EasyProcess.IStreamParser() {
			private long pid;

			@Override
			public void init(long pid) {
				this.pid = pid;
			}

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

			@Override
			public void handleErr(BufferedReader err) {
				EasyProcess.handlErr(err, pid);
			}

			@Override
			public void finish() {
				System.out.println("searching modules ...");
				modules.addAll(JavaProcessUtils.listMoudles(this.pid));
				System.out.println("moudules count : " + modules.size());
			}
		});
	}

}
