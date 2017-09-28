package com.jrecut;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

/**
 * 简洁的进程执行工具
 * 
 * @author liuz@aotian.com
 * @date 2017年9月27日 下午2:55:03
 */
public class EasyProcess {
	private ProcessBuilder pb = null;

	private long pid = -1;

	public EasyProcess(String... cmds) {
		pb = new ProcessBuilder(cmds);
	}

	public EasyProcess dir(String dir) {
		pb.directory(new File(dir));
		return this;
	}

	public void run(final IStreamParser iStreamParser) {
		Process p = null;
		try {
			p = pb.start();
			pid = getPid(p);
			final BufferedReader out = new BufferedReader(
					new InputStreamReader(new BufferedInputStream(p.getInputStream())));
			iStreamParser.init(pid);
			Thread oThread = new Thread(new Runnable() {

				@Override
				public void run() {
					iStreamParser.handleOut(out);
					if (out != null) {
						try {
							out.close();
						} catch (Exception e) {

						}
					}
				}
			});
			oThread.start();

			final BufferedReader err = new BufferedReader(
					new InputStreamReader(new BufferedInputStream(p.getErrorStream())));
			Thread eThread = new Thread(new Runnable() {

				@Override
				public void run() {
					iStreamParser.handleErr(err);
					if (err != null) {
						try {
							err.close();
						} catch (Exception e) {
						}
					}
				}
			});
			eThread.start();
			iStreamParser.finish();
			oThread.join();
			eThread.join();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (p != null) {
				p.destroy();
			}
			pid = -1;
		}
	}

	/**
	 * 流解析接口
	 * 
	 * @author liuz@aotian.com
	 * @date 2017年9月27日 下午2:52:18
	 */
	public interface IStreamParser {
		
		/**
		 * 提供当前进程的PID
		 * @param pid
		 */
		public void init(long pid);
		
		/**
		 * 解析结束
		 */
		public void finish();

		/**
		 * 解析正常流
		 * 
		 * @param out 正常流对象
		 */
		public void handleOut(BufferedReader out);

		/**
		 * 解析异常流
		 * 
		 * @param err
		 */
		public void handleErr(BufferedReader err);

	}
	
	/**
	 * 打印异常信息
	 * @param err
	 * @param pid
	 */
	public static void handlErr(BufferedReader err,long pid){
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
				System.err.println(pid+" --> "+tmp);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
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
}
