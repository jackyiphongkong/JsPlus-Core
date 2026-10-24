package org.apache.logging.log4j;

public class LoggerClass extends org.slf4j.LoggerClass implements org.apache.logging.log4j.Logger {
    
    public LoggerClass(Class clazz) {
		super(clazz);
	}    

	public LoggerClass(String clazzName) {
		super(clazzName);
	}
	
}