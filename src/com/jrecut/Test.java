package com.jrecut;

public class Test {
	public static void main(String[] args) {
										 
//		EasyProcess p = new EasyProcess("F:\\cutjre\\mycute\\cut\\jre\\bin\\java");
		EasyProcess p = new EasyProcess("F:\\cutjre\\mycute\\cut\\output\\jre\\bin\\java");
		p.dir("F:\\cutjre\\mycute\\cut").run(new EasyProcess.StreamParserAdpter());
	}
}
