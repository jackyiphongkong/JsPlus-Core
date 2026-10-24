package org.apache.commons.logging;

public class LogFactory { 

	public LogFactory() {
		super();
	}
	
	public static org.apache.commons.logging.Log getLog(Class clazz){
	    org.apache.commons.logging.Log logger = new org.apache.commons.logging.LoggerClass(clazz);
	    return logger;
	}
	
	public static org.apache.commons.logging.Log getLog(String clazzName){
	    org.apache.commons.logging.Log logger = new org.apache.commons.logging.LoggerClass(clazzName);
	    return logger;
	}	
	
	public static void release(ClassLoader classLoader) {
	    
	}

}