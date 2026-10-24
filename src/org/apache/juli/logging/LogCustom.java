package org.apache.juli.logging;

import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;


public class LogCustom implements Log {
    
    protected final Logger log;

	public LogCustom(Class clazz){
		super();
		log = LoggerFactory.getLogger(clazz);
	}
	
	public LogCustom(String clazz){
		super();
		log = LoggerFactory.getLogger(clazz);
	}

    @Override
	public void debug(Object arg0) {
		log.debug(String.format("%s",arg0));
	}

    @Override
	public void debug(Object arg0, Throwable arg1) {
		log.debug(String.format("%s",arg0),arg1);
	}

    @Override
	public void error(Object arg0) {
		log.error(String.format("%s",arg0));
	}

    @Override
	public void error(Object arg0, Throwable arg1) {
		log.error(String.format("%s",arg0),arg1);
	}

    @Override
	public void fatal(Object arg0) {
		log.error(String.format("%s",arg0));
	}

    @Override
	public void fatal(Object arg0, Throwable arg1) {
		log.error(String.format("%s",arg0),arg1);
	}
    
    @Override
	public void info(Object arg0) {
		log.info(String.format("%s",arg0));
	}

    @Override
	public void info(Object arg0, Throwable arg1) {
		log.info(String.format("%s",arg0));
	}

    @Override
	public boolean isDebugEnabled() {
		return log.isDebugEnabled();
	}

    @Override
	public boolean isErrorEnabled() {
		return log.isErrorEnabled();
	}
	
	@Override
	public boolean isFatalEnabled() {
		return log.isErrorEnabled();
	}

    @Override
	public boolean isInfoEnabled() {
		return log.isInfoEnabled();
	}

    @Override
	public boolean isTraceEnabled() {
		return log.isDebugEnabled();
	}

    @Override
	public boolean isWarnEnabled() {
		return log.isWarnEnabled();
	}

    @Override
	public void trace(Object arg0) {
		log.debug(String.format("%s",arg0));
	}

    @Override
	public void trace(Object arg0, Throwable arg1) {
		log.debug(String.format("%s",arg0), arg1);
	}

    @Override
	public void warn(Object arg0) {
		log.warn(String.format("%s",arg0));
	}

    @Override
	public void warn(Object arg0, Throwable arg1) {
		log.warn(String.format("%s",arg0),arg1);
	}

}
