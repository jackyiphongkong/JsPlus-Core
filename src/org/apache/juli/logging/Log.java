package org.apache.juli.logging;

public interface Log {

    public void debug(Object arg0);

	public void debug(Object arg0, Throwable arg1);

	public void error(Object arg0);

	public void error(Object arg0, Throwable arg1);

	public void fatal(Object arg0);

	public void fatal(Object arg0, Throwable arg1);

	public void info(Object arg0);

	public void info(Object arg0, Throwable arg1);

	public boolean isDebugEnabled();;

	public boolean isErrorEnabled();

	public boolean isFatalEnabled();

	public boolean isInfoEnabled();

	public boolean isTraceEnabled();

	public boolean isWarnEnabled();

	public void trace(Object arg0);

	public void trace(Object arg0, Throwable arg1);

	public void warn(Object arg0);

	public void warn(Object arg0, Throwable arg1);

}