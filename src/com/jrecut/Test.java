package com.jrecut;

public class Test {
	public static void main(String[] args) {
										 
//		EasyProcess p = new EasyProcess("F:\\cutjre\\mycute\\cut\\jre\\bin\\java");
		String[] arr = {"F:\\cutjre\\mycute\\cut\\output\\jre\\bin\\java","-Djava.ext.dirs=lib",  "com.aotain.dams.test.CommandSender"};
		EasyProcess p = new EasyProcess(arr);
										 
		try {
			p.dir("F:\\cutjre\\mycute\\cut").run(new EasyProcess.StreamParserAdpter());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
