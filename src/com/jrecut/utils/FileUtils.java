package com.jrecut.utils;

import java.io.BufferedReader;
import java.io.File;
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
 * 文件操作工具
 * 
 * @author liuz@aotian.com
 * @date 2017年9月28日 下午3:11:24
 */
public class FileUtils {
	/**
	 * 按行读取文件
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
	 * 删除目录
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
				System.err.println("删除文件/目录失败：" + files[i].getAbsolutePath());
				return false;
			}
		}
		return path.delete();
	}

	/**
	 * 文件拷贝
	 * 
	 * @param file
	 * @param dir
	 * @return
	 * @throws IOException
	 */
	public static void copyTo(File file, File dstFile) throws IOException {
		if (file == null) {
			throw new IOException("源文件为空");
		}
		if (!file.exists()) {
			throw new IOException("源文件不存在：" + file.getAbsolutePath());
		}

		if (dstFile == null) {
			throw new IOException("目的文件为空");
		}
		if (dstFile.exists()) {
			throw new IOException("目的文件已存在：" + dstFile.getAbsolutePath());
		}

		File p = dstFile.getParentFile();
		if (!p.exists()) {
			if (!p.mkdirs()) {
				throw new IOException("目的文件目录创建失败：" + p.getAbsolutePath());
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
}
