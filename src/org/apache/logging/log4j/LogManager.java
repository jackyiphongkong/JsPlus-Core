package org.apache.logging.log4j;

public class LogManager { 

	public LogManager() {
		super();
	}
	
	public static org.apache.logging.log4j.Logger getLogger(Class clazz){
	    org.apache.logging.log4j.Logger logger = new org.apache.logging.log4j.LoggerClass(clazz);
	    return logger;
	}
	
	public static org.apache.logging.log4j.Logger getLogger(String clazzName){
	    org.apache.logging.log4j.Logger logger = new org.apache.logging.log4j.LoggerClass(clazzName);
	    return logger;
	}	

}