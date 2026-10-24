package org.apache.commons.logging;

public class LoggerClass extends org.slf4j.LoggerClass implements org.apache.commons.logging.Log {
    
    public LoggerClass(Class clazz) { 
		super(clazz);
	}    

	public LoggerClass(String clazzName) {
		super(clazzName);
	}
	
}