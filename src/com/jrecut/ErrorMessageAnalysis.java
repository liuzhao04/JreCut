package com.jrecut;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jrecut.utils.Pair;

/**
 * �쳣��Ϣ��������
 * 
 * @author liuz@aotian.com
 * @date 2017��9��29�� ����4:35:50
 */
public class ErrorMessageAnalysis {
	// java.lang.NoClassDefFoundError:
	// sun/security/provider/SeedGenerator$ThreadedSeedGenerator
	private static final Pattern SOLV_NO_CLASS_DEF_FOUND_ERR = Pattern
			.compile("java\\.lang\\.NoClassDefFoundError:\\s*([\\w\\$/]+)\\s*");
	/**
	 * δ�ҵ�Class
	 */
	public static final int SOLV_TYPE_NO_CLASS_DEF_FOUND_ERR = 1;
	

	/**
	 * �����쳣��Ϣ���õ��������
	 * 
	 * @param error
	 * @return
	 */
	public static Pair<Integer, String> analysisError(String error) {
		Matcher m = SOLV_NO_CLASS_DEF_FOUND_ERR.matcher(error);
		if (m.find()) {
			return new Pair<Integer, String>(SOLV_TYPE_NO_CLASS_DEF_FOUND_ERR, m.group(1));
		}
		return null;
	}
	
	public static void main(String[] args) {
		System.out.println(analysisError("Exception in thread \"main\" java.lang.NoClassDefFoundError: sun/security/provider/SeedGenerator$ThreadedSeedGenerator \r\n"+
        "at sun.security.provider.SeedGenerator.<clinit>(SeedGenerator.java:109)\r\n"+
        "at sun.security.provider.SecureRandom.engineNextBytes(SecureRandom.java:170)"));
	}
}
