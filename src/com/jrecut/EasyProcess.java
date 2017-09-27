package com.jrecut;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * ���Ľ���ִ�й���
 * 
 * @author liuz@aotian.com
 * @date 2017��9��27�� ����2:55:03
 */
public class EasyProcess {
	private ProcessBuilder pb = null;

	private long pid = -1;

	public long getPid() {
		return pid;
	}

	public EasyProcess(String... cmds) {
		pb = new ProcessBuilder(cmds);
	}

	public EasyProcess dir(String dir) {
		pb.directory(new File(dir));
		return this;
	}

	public void run(final IStreamParser iSteamParser) {
		Process p = null;
		try {
			p = pb.start();
			pid = JavaProcessUtils.getPid(p);
			final BufferedReader out = new BufferedReader(
					new InputStreamReader(new BufferedInputStream(p.getInputStream())));
			Thread oThread = new Thread(new Runnable() {

				@Override
				public void run() {
					iSteamParser.handleOut(out);
					if (out != null) {
						try {
							out.close();
						} catch (Exception e) {

						}
					}
				}
			});
			oThread.start();
			oThread.join();

			final BufferedReader err = new BufferedReader(
					new InputStreamReader(new BufferedInputStream(p.getErrorStream())));
			Thread eThread = new Thread(new Runnable() {

				@Override
				public void run() {
					iSteamParser.handleErr(err);
					if (out != null) {
						try {
							out.close();
						} catch (Exception e) {
						}
					}
				}
			});
			eThread.start();
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
	 * �������ӿ�
	 * 
	 * @author liuz@aotian.com
	 * @date 2017��9��27�� ����2:52:18
	 */
	public interface IStreamParser {
		/**
		 * ����������
		 * 
		 * @param out ����������
		 */
		public void handleOut(BufferedReader out);

		/**
		 * �����쳣��
		 * 
		 * @param err
		 */
		public void handleErr(BufferedReader err);

	}
}
