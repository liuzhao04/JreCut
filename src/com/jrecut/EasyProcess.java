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
			iStreamParser.init(getPid(p));
			final BufferedReader out = new BufferedReader(
					new InputStreamReader(new BufferedInputStream(p.getInputStream())));
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

			// 循环等待线程（进程结束）
			while (oThread.isAlive() || eThread.isAlive()) {
				long tpid = -1;
				if ((tpid = getPid(p)) != -1L) {
					iStreamParser.isProcess(tpid);
					Thread.sleep(100);
				}
			}

			iStreamParser.finish();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (p != null) {
				p.destroy();
			}
		}
	}

	/**
	 * 流解析接口
	 * 
	 * @author liuz@aotian.com
	 * @date 2017年9月27日 下午2:52:18
	 */
	public static interface IStreamParser {
		
		/**
		 * 获取pid
		 * @param pid
		 */
		public void init(long pid);

		/**
		 * 进程正在运行
		 * 
		 * @param pid
		 */
		public void isProcess(long pid);

		/**
		 * 解析结束
		 * 
		 * @return 返回为true是，等待3秒在结束进程
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
	 * 流处理接口适配器
	 * 
	 * @author liuz@aotian.com
	 * @date 2017年9月29日 下午1:49:22
	 */
	public static class StreamParserAdpter implements IStreamParser {
		protected long pid = -1;

		@Override
		public void isProcess(long pid) {
			this.pid = pid;
		}

		@Override
		public void finish() { // 进程完成
		}

		@Override
		public void handleOut(BufferedReader out) {
			EasyProcess.handleOutStream(out, pid, false);
		}

		@Override
		public void handleErr(BufferedReader err) {
			EasyProcess.handleOutStream(err, pid, true);
		}

		@Override
		public void init(long pid) {
			this.pid = pid;
		}

	}

	/**
	 * 打印异常信息
	 * 
	 * @param err
	 * @param pid
	 */
	public static void handleOutStream(BufferedReader err, long pid, boolean isErr) {
		String tmp = null;
		try {
			while ((tmp = err.readLine()) != null) {
				if (isErr) {
					System.err.println("process " + pid + " --> " + tmp);
				} else {
					System.out.println("process " + pid + " --> " + tmp);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static String handlerOutStrem(BufferedReader is){
		String tmp = null;
		StringBuilder sb = new StringBuilder();
		try {
			int i = 0;
			while ((tmp = is.readLine()) != null) {
				if(i++ > 0){
					sb.append("\r\n");
				}
				sb.append(tmp);
			}
			return sb.toString();
		} catch (IOException e) {
			e.printStackTrace();
			return e.getMessage();
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
