import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class ClassCopy {

	private static String outputDir = ".";
	private static boolean overwirte = false;

	public static void anasis(String pwd, String libDir, List<String> jars, String jar, String mainClass) {
		String cmd = "";
		if (pwd != null && !"".equals(pwd.trim())) {
			File lib = new File(pwd);
			if (!lib.exists()) {
				System.out.println("pwd 目录不存在：" + pwd);
			} else if (!lib.isDirectory()) {
				System.out.println("pwd 目录不是目录：" + pwd);
			} else {
				cmd += "cmd /c cd /d " + pwd + " && ";
			}
			outputDir = pwd + "/output";
		} else {
			outputDir += "/output";
		}
		File file = new File(outputDir);
		if (!file.exists()) {
			file.mkdirs();
		}

		cmd += ".\\jre\\bin\\java  -verbose ";
		if (libDir != null && !"".equals(libDir.trim())) {
			cmd += "-Djava.ext.dirs=" + libDir.trim() + " ";
		}

		if (jars != null && jars.size() > 0) {
			int i = 0;
			String cps = "";
			for (String j : jars) {
				if (i > 0) {
					cps += ";";
				}

				cps += j;
				i++;
			}
			if (i > 0) {
				cmd += "-cp " + cps + " ";
			}
		}

		if (jar != null && !"".equals(jar.trim())) {
			cmd += "-jar " + jar;
		}

		if (mainClass != null && !"".equals(mainClass.trim())) {
			cmd += mainClass + " ";
		}
		if (overwirte) {
			cmd += "> output\\class.txt";
		} else {
			cmd += ">> output\\class.txt";
		}
		System.out.println(cmd);
		exec(cmd, true);
	}

	public static void cutJars(String classListFile) {
		Map<String, List<String>> clss = new HashMap<String, List<String>>();
		Map<String, String> jarPathMap = new HashMap<String, String>();
		try {
			if (classListFile == null) {
				classListFile = outputDir + "/class.txt";
			}
			FileReader fr = new FileReader(classListFile);
			BufferedReader br = new BufferedReader(fr);
			String line = null;

			// 1. 统计类
			while ((line = br.readLine()) != null) {
				if (line.matches("\\[Opened.*\\]")) {
					String[] tmp = line.split("\\\\");
					String jarName = tmp[tmp.length - 1].substring(0, tmp[tmp.length - 1].length() - 1);
					clss.put(jarName, new ArrayList<String>());
				} else {
					String[] tmp = line.split(" ");
					if (tmp.length < 4) {
						continue;
					}
					String clssName = tmp[1];
					String jar = tmp[3];

					String jarPath = jar.substring(0, jar.length() - 1);

					if (jar.contains("/")) {

						tmp = jar.split("/");
					} else {
						tmp = jar.split("\\\\");
					}
					String jarName = tmp[tmp.length - 1].substring(0, tmp[tmp.length - 1].length() - 1);

					List<String> t = clss.get(jarName);
					if (t == null) {
						t = new ArrayList<String>();
						clss.put(jarName, t);
					}
					t.add(clssName);
					jarPathMap.put(jarName, jarPath);
				}
			}
			br.close();
			fr.close();

			String path = outputDir + "/";

			// 2. 拷贝类资源
			for (String jar : clss.keySet()) {
				if (!jar.endsWith(".jar")) {
					continue;
				}
				List<String> clssList = clss.get(jar);
				String jarPath = jarPathMap.get(jar);
				if (!jarPath.startsWith("file")) {
					jarPath = "file:/" + jarPath;
				}
				System.out.println(jarPath);
				
				// 向结果中补充Exception类
				clss.get(jar).addAll(scanJar(jarPath.replace("file:/", "")));
				
				URLClassLoader myClassLoader = new URLClassLoader(new URL[] { new URL(jarPath) },
						Thread.currentThread().getContextClassLoader());
				String pp = path + jar.replace(".jar", "");

				for (String c : clssList) {
					try {
						myClassLoader.loadClass(c);
					} catch (SecurityException e) {

					}
					String cpath = c.replace(".", "/") + ".class";
					InputStream is = myClassLoader.getResourceAsStream(cpath);
					// System.out.println(cpath);
					if (cpath.contains("UnsupportedEncodingException")) {
						System.out.println("cpath:" + is + ",cpath:" + cpath);
					}
					String outputPath = pp + "/" + cpath;

					File f = new File(outputPath);
					if (!f.getParentFile().exists()) {
						f.getParentFile().mkdirs();
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
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void reJar(String output) {
		if (output != null) {
			outputDir = output;
		}
		File p = new File(outputDir);
		if (!p.exists()) {
			System.out.println("输出目录不存在!");
			return;
		}

		File[] dirs = p.listFiles();
		if (dirs == null) {
			System.out.println("数据目录无数据!");
			return;
		}

		for (File d : dirs) {
			if (d.isDirectory()) {
				String jarName = d.getAbsolutePath() + ".jar";
				String cmd = "cmd.exe /c cd /d " + d.getAbsolutePath() + " && ";
				cmd += "..\\..\\jre\\bin\\jar cvf " + jarName + " * ";
				System.out.println(cmd);
				exec(cmd, true);
			}
		}
	}

	public static void exec(String cmds, boolean output) {
		Process pro;
		try {
			pro = Runtime.getRuntime().exec(cmds);
			InputStream is = pro.getInputStream();
			if (is.available() > 0) {
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				String tmp = null;

				while ((tmp = br.readLine()) != null) {
					if (output) {
						System.out.println(tmp);
					}
				}
				br.close();
			}
			is.close();
			is = pro.getErrorStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			String tmp = null;
			while ((tmp = br.readLine()) != null) {
				if (output) {
					System.out.println(tmp);
				}
			}
			br.close();
			is.close();
			pro.destroy();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static List<String> scanJar(String jarPath) {
		try {
			JarInputStream jarIn = new JarInputStream(new FileInputStream(jarPath));
			JarEntry entry = jarIn.getNextJarEntry();

			List<String> nameList = new ArrayList<String>();
			while (null != entry) {
				String name = entry.getName();
				if (name.endsWith("Exception.class")) {
					nameList.add(name);
				}
				entry = jarIn.getNextJarEntry();
			}
			jarIn.close();
			return nameList;
		} catch (Exception e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}

	public static void main(String[] args) throws MalformedURLException, ClassNotFoundException, NoSuchMethodException,
			SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		int i = 0;
		boolean isFirst = true;
		for (String cls : args) {
			if (i > 0) {
				overwirte = isFirst;
				anasis(".", args[0], null, null, cls);
				isFirst = false;
			}
			i++;
		}
		cutJars(null);// "F:\\cutjre\\mycute\\wsclient_1\\class.txt"
		reJar(null);
		
		
		// URL url =
		// ClassCopy.class.getClassLoader().getResource("/com/aotain/dams/test/CommandSender.java");

		// URL url = new
		// URL("file:/F:/cutjre/mycute/wsclient_1/lib/wstest.jar");
		// URLClassLoader myClassLoader = new URLClassLoader(new URL[] { url },
		// Thread.currentThread().getContextClassLoader());
		//
		// // 包路径定义
		// //GetPI.class
		// URLClassLoader urlLoader = (URLClassLoader)
		// ClassLoader.getSystemClassLoader();
		// Class<URLClassLoader> sysclass = URLClassLoader.class;
		// Method method = sysclass.getDeclaredMethod("addURL", new
		// Class[]{URL.class});
		// method.setAccessible(true);
		// method.invoke(urlLoader, url);
		//
		// urlLoader.loadClass("com.aotain.dams.test.CommandSender");
		// URL[] urls = urlLoader.getURLs();
		// for(URL u : urls){
		// System.out.println(u);
		// }
		// URL s =
		// myClassLoader.getResource("com/aotain/dams/test/CommandSender.class");
		// System.out.println(s);
	}
}
