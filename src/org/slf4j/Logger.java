package org.slf4j;

public interface Logger {
    
    public void debug(Object message);
	public void info(Object message);
	public void warn(Object message);
	public void error(Object message);
	public void trace(Object message);
    
    public void debug(String message);
    public void debug(String message,Object o);
    public void debug(String message,Object o1,Object o2);
    public void debug(String message,Object...params);
    public void debug(String message,Exception exception);
    public void debug(Exception exception);
    public void debug(String message,Throwable throwable);
    public void debug(Object message,Throwable throwable);
    
    public void info(String message);
    public void info(String message,Object o);
    public void info(String message,Object o1,Object o2);
    public void info(String message,Object...params);
    public void info(String message,Exception exception);
    public void info(Exception exception);
    public void info(String message,Throwable throwable);
    public void info(Object message,Throwable throwable);
    
    public void warn(String message);
    public void warn(String message,Object o);
    public void warn(String message,Object o1,Object o2);
    public void warn(String message,Object...params);
    public void warn(String message,Exception exception);
    public void warn(Exception exception);
    public void warn(String message,Throwable throwable);
    public void warn(Object message,Throwable throwable);
    
    public void error(String message);
    public void error(String message,Object o);
    public void error(String message,Object o1,Object o2);
    public void error(String message,Object...params);
    public void error(String message,Exception exception);
    public void error(Exception exception);  
    public void error(String message,Throwable throwable);
    public void error(Object message,Throwable throwable);
    
    public void trace(String message);
    public void trace(String message,Object o);
    public void trace(String message,Object o1,Object o2);
    public void trace(String message,Object...params);
    public void trace(String message,Exception exception);
    public void trace(Exception exception);  
    public void trace(String message,Throwable throwable);    
    public void trace(Object message,Throwable throwable);
    
    public boolean isTraceEnabled();
    public boolean isDebugEnabled();
    public boolean isInfoEnabled();
    public boolean isWarnEnabled();
    public boolean isErrorEnabled();

}