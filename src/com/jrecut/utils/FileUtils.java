package com.jrecut.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * �ļ���������
 * 
 * @author liuz@aotian.com
 * @date 2017��9��28�� ����3:11:24
 */
public class FileUtils {
	/**
	 * ���ж�ȡ�ļ�
	 * 
	 * @param path
	 * @param charset
	 * @return
	 * @throws IOException
	 */
	public static List<String> readFile(String path, String charset) throws IOException {
		if (path == null || "".equals(path.trim())) {
			return Collections.emptyList();
		}
		BufferedReader br = null;
		if (charset == null) {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
		} else {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(path), charset));
		}
		try {
			String tmp = null;
			List<String> list = new ArrayList<String>();
			while ((tmp = br.readLine()) != null) {
				list.add(tmp);
			}
			return list;
		} finally {
			br.close();
		}
	}

	/**
	 * ɾ��Ŀ¼
	 * 
	 * @param path
	 * @return
	 */
	public static boolean deleteAllFilesOfDir(File path) {
		if (!path.exists())
			return true;
		if (path.isFile()) {
			return path.delete();
		}
		File[] files = path.listFiles();
		for (int i = 0; i < files.length; i++) {
			if (!deleteAllFilesOfDir(files[i])) {
				System.err.println("ɾ���ļ�/Ŀ¼ʧ�ܣ�" + files[i].getAbsolutePath());
				return false;
			}
		}
		return path.delete();
	}

	/**
	 * �ļ�����
	 * 
	 * @param file
	 * @param dir
	 * @return
	 * @throws IOException
	 */
	public static void copyTo(File file, File dstFile) throws IOException {
		if (file == null) {
			throw new IOException("Դ�ļ�Ϊ��");
		}
		if (!file.exists()) {
			throw new IOException("Դ�ļ������ڣ�" + file.getAbsolutePath());
		}

		if (dstFile == null) {
			throw new IOException("Ŀ���ļ�Ϊ��");
		}
		/*if (dstFile.exists()) { // �������е��ļ�
			throw new IOException("Ŀ���ļ��Ѵ��ڣ�" + dstFile.getAbsolutePath());
		}*/

		File p = dstFile.getParentFile();
		if (!p.exists()) {
			if (!p.mkdirs()) {
				throw new IOException("Ŀ���ļ�Ŀ¼����ʧ�ܣ�" + p.getAbsolutePath());
			}
		}

		FileInputStream in = new FileInputStream(file);
		FileOutputStream out = new FileOutputStream(dstFile);
		FileChannel inC = in.getChannel();
		FileChannel outC = out.getChannel();
		int length = 2097152;
		ByteBuffer b = null;
		while (true) {
			if (inC.position() == inC.size()) {
				inC.close();
				outC.close();
				break;
			}
			if ((inC.size() - inC.position()) < length) {
				length = (int) (inC.size() - inC.position());
			} else
				length = 2097152;
			b = ByteBuffer.allocateDirect(length);
			inC.read(b);
			b.flip();
			outC.write(b);
			outC.force(false);
		}
		in.close();
		out.close();
	}

	/**
	 * Ŀ¼����
	 * 
	 * @param orgDir ԴĿ¼ ����Ŀ¼������ļ�
	 * @param dstDir Ŀ��Ŀ¼
	 * @param fileFilter ����ָ�����ļ�
	 * @throws IOException
	 */
	public static void copyDirTo(File orgDir, File dstDir, FileFilter fileFilter) throws IOException {
		File[] files = orgDir.listFiles(fileFilter);
		if (files == null) {
			return;
		}

		for (File file : files) {
			File dst = new File(dstDir.getAbsolutePath() + File.separator + file.getName());
			if (file.isDirectory()) {
				copyDirTo(file, dst, fileFilter);
			} else {
				copyTo(file, dst);
			}
		}
	}

	/**
	 * Ŀ¼����
	 * 
	 * @param orgDir
	 * @param dstDir
	 * @throws IOException
	 */
	public static void copyDirTo(File orgDir, File dstDir) throws IOException {
		File[] files = orgDir.listFiles();
		if (files == null) {
			return;
		}

		for (File file : files) {
			File dst = new File(dstDir.getAbsolutePath() + File.separator + file.getName());
			if (file.isDirectory()) {
				copyDirTo(file, dst);
			} else {
				copyTo(file, dst);
			}
		}
	}
}
