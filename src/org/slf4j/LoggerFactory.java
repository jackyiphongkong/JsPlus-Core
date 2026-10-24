package org.slf4j;

public class LoggerFactory {

	public LoggerFactory() {
		super();
	}
	
	public static org.slf4j.Logger getLogger(Class clazz){
	    org.slf4j.Logger logger = new org.slf4j.LoggerClass(clazz);
	    return logger;
	}
	
	public static Logger getLogger(String clazzName){
	    org.slf4j.Logger logger = new org.slf4j.LoggerClass(clazzName);
	    return logger;
	}	

}