package org.apache.juli.logging;

public class LogFactory{

	public static Log getLog(Class clazz) {
		return new LogCustom(clazz);
	}
	
	public static Log getLog(String clazz) {
		return new LogCustom(clazz);
	}
	
	public void release(){
		
	}
	
	public static void release(java.lang.ClassLoader classLoader){
		
	}

	
}
