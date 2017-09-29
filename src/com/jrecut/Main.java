package com.jrecut;

import java.io.IOException;

public class Main {

	public static void main(String[] args) {
		if (args == null || args.length < 1) {
			System.out.println("请输入参数：[mainClass] [libDir],libDir为可选项");
			return;
		}

		try {
			JavaClassExecutor jce = new JavaClassExecutor("F:\\cutjre\\mycute\\cut");
			jce.exec(args[0], args.length > 1 ? args[1] : null, true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
