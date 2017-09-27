package com.jrecut;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

/**
 * Java进程工具类(针对windows系统中运行的java进程)
 * 
 * @author liuz@aotian.com
 * @date 2017年9月27日 下午2:39:28
 */
public class JavaProcessUtils {
	/**
	 * 除了本身以外的所有java pid
	 * 
	 * @return
	 */
	public static List<Pair<String, Long>> listJavaProcessPidWithoutSelf() {
		List<Pair<String, Long>> javaProcessList = listJavaProcessPid();
		Pair<String, Long> tmp = null;
		for (Pair<String, Long> p : javaProcessList) {
			if (p.getValue().equals(getPid())) {
				tmp = p;
				break;
			}
		}
		if (tmp != null) {
			javaProcessList.remove(tmp);
		}
		return javaProcessList;
	}

	/**
	 * 列出所有的java进程
	 * 
	 * @return
	 */
	public static List<Pair<String, Long>> listJavaProcessPid() {
		BufferedReader out = null;
		BufferedReader err = null;
		try {
			final List<Pair<String, Long>> list = new ArrayList<Pair<String, Long>>();
			final EasyProcess ep = new EasyProcess("tasklist", "/v");
			ep.run(new EasyProcess.IStreamParser() {

				@Override
				public void handleOut(BufferedReader out) {
					try {
						String ostr;
						while ((ostr = out.readLine()) != null) {
							if (ostr.startsWith("java")) {
								String[] tmps = ostr.split("\\s+");
								if (tmps.length > 1) {
									list.add(new Pair<String, Long>(tmps[0], Long.parseLong(tmps[1])));
								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				@Override
				public void handleErr(BufferedReader err) {
					try {
						String ostr;
						while ((ostr = err.readLine()) != null) {
							System.err.println(ostr);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});

			return list;

		} catch (Exception e) {
			e.printStackTrace();
			return Collections.emptyList();
		} finally {
			try {
				if (out != null) {
					out.close();
				}
				if (err != null) {
					err.close();
				}
			} catch (IOException e) {
			}
		}
	}

	/**
	 * 列举某个java进程所占用的dll文件列表
	 * 
	 * @param pid java进程的pid
	 * @return
	 */
	public static List<String> listMoudles(Long pid) {
		final List<String> mlist = new ArrayList<String>();
		new EasyProcess("jmap", String.valueOf(pid)).run(new EasyProcess.IStreamParser() {

			@Override
			public void handleOut(BufferedReader out) {
				String tmp = null;
				try {
					int count = 0;
					while (!out.ready()) {
						if (count == 10) {
							return;
						}
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						count++;
					}

					while ((tmp = out.readLine()) != null) {
						if (tmp.matches("^[0-9a-fA-FxX]+\\s+[\\d]+.+")) {
							String[] tmps = tmp.split("\\t+");
							mlist.add(tmps[2]);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void handleErr(BufferedReader err) {
				String tmp = null;
				try {
					int count = 0;
					while (!err.ready()) {
						if (count == 10) {
							return;
						}
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						count++;
					}

					while ((tmp = err.readLine()) != null) {
						System.err.println(tmp);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		});
		return mlist;
	}

	/**
	 * 获取当前进程的PID
	 * 
	 * @return
	 */
	public static long getPid() {
		String name = ManagementFactory.getRuntimeMXBean().getName();
		return Long.parseLong(name.split("@")[0]);
	}

	/**
	 * 获取进程PID
	 * 
	 * @param process
	 * @return
	 */
	public static long getPid(Process process) {
		long pid = -1;
		Field field = null;
		if (Platform.isWindows()) {
			try {
				field = process.getClass().getDeclaredField("handle");
				field.setAccessible(true);
				pid = Kernel32.INSTANCE.GetProcessId((Long) field.get(process));
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		} else if (Platform.isLinux() || Platform.isAIX()) {
			try {
				Class<?> clazz = Class.forName("java.lang.UNIXProcess");
				field = clazz.getDeclaredField("pid");
				field.setAccessible(true);
				pid = (Integer) field.get(process);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		return pid;
	}

	/**
	 * 获取PID的工具接口
	 * 
	 * @author liuz@aotian.com
	 * @date 2017年9月27日 下午6:38:29
	 */
	public interface Kernel32 extends Library {
		public static Kernel32 INSTANCE = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);

		public long GetProcessId(Long hProcess);
	}

	public static void main(String[] args) {
		List<Pair<String, Long>> javaProcessList = listJavaProcessPidWithoutSelf();
		System.out.println(javaProcessList);
		for (Pair<String, Long> p : javaProcessList) {
			List<String> modoules = listMoudles(p.getValue());
			System.out.println(modoules);
		}
	}
}
