package com.jrecut;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jrecut.utils.Pair;

/**
 * Java���̹�����(���windowsϵͳ�����е�java����)
 * 
 * @author liuz@aotian.com
 * @date 2017��9��27�� ����2:39:28
 */
public class JavaProcessUtils {
	/**
	 * ���˱������������java pid
	 * 
	 * @return
	 */
	public static List<Pair<String, Long>> listJavaProcessPidWithoutSelf() {
		List<Pair<String, Long>> javaProcessList = listJavaProcessPid();
		Pair<String, Long> tmp = null;
		for (Pair<String, Long> p : javaProcessList) {
			if (p.getValue().equals(EasyProcess.getPid())) {
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
	 * �г����е�java����
	 * 
	 * @return
	 */
	public static List<Pair<String, Long>> listJavaProcessPid() {
		BufferedReader out = null;
		BufferedReader err = null;
		try {
			final List<Pair<String, Long>> list = new ArrayList<Pair<String, Long>>();
			final EasyProcess ep = new EasyProcess("tasklist", "/v");
			ep.run(new EasyProcess.StreamParserAdpter() {
				
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
	 * �о�ĳ��java������ռ�õ�dll�ļ��б�
	 * 
	 * @param pid java���̵�pid
	 * @return
	 */
	public static List<String> listMoudles(Long pid) {
		final List<String> mlist = new ArrayList<String>();
		System.out.println("jmap "+pid+" ...");
		new EasyProcess("jmap", String.valueOf(pid)).run(new EasyProcess.StreamParserAdpter() {
			@Override
			public void handleOut(BufferedReader out) {
				String tmp = null;
				try {
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
		});
		return mlist;
	}
}
