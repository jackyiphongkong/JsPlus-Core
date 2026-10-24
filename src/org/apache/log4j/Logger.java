package org.apache.log4j;


public class Logger extends org.slf4j.LoggerClass {
    
	public Logger(Class clazz) {
		super(clazz);
	}       

	public Logger(String clazzName) {
		super(clazzName);
	}

}