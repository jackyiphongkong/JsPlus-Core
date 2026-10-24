package org.slf4j;

import java.util.Map;


public class LoggerClass implements org.slf4j.Logger {
    
    private com.killcoding.log.Logger logger = null; 
    
	public LoggerClass(Class clazz) {
		super();
		logger = new com.killcoding.log.Logger(clazz);
	}    

	public LoggerClass(String clazzName) {
		super();
		logger = new com.killcoding.log.Logger(clazzName);
	}
	
	@Override
	public void debug(Object message){
	    logger.debug(message + "");
	}
	
	@Override
	public void info(Object message){
	    logger.info(message + "");
	}
	
    @Override	
	public void warn(Object message){
	    logger.warn(message + "");
	}
	
	@Override
	public void error(Object message){
	    logger.error(message + "");
	}	
	
	@Override
	public void trace(Object message){
	    logger.mark(message + "");
	} 
	
	@Override
	public void debug(Object message,Throwable throwable){
	    logger.debug(message + "",throwable);
	}
	
	@Override
	public void info(Object message,Throwable throwable){
	    logger.info(message + "",throwable);
	}	
	
	@Override
	public void warn(Object message,Throwable throwable){
	    logger.warn(message + "",throwable);
	}	
	
	@Override
	public void error(Object message,Throwable throwable){
	    logger.error(message + "",throwable);
	}	
	
	@Override
	public void trace(Object message,Throwable throwable){
	    logger.mark(message + "",throwable);
	}	
	
	@Override
	public void debug(String message){
	    logger.debug(message);
	}
	
    @Override
    public void debug(String message,Object o){
        logger.debug(message,o);
    }  	

    @Override
    public void debug(String message,Object o1,Object o2){
        logger.debug(message,o1,o2);
    } 
    
	@Override
    public void debug(String message,Object...params){
        logger.debug(message,params);
    }
    
    @Override
    public void debug(String message,Exception exception){
        logger.debug(message,exception);
    }
    
    @Override
    public void debug(String message,Throwable throwable){
        logger.debug(message,throwable);
    }    
    
    @Override
    public void debug(Exception exception){
        logger.debug(exception);
    }
    
    @Override
    public void info(String message){
        logger.info(message);
    }
    
    @Override
    public void info(String message,Object o){
        logger.info(message,o);
    }
    
    @Override
    public void info(String message,Object o1,Object o2){
        logger.info(message,o1,o2);
    }     
    
    @Override
    public void info(String message,Object...params){
        logger.info(message,params);
    }
    
    public void info(String message,Exception exception){
        logger.info(message,exception);
    }
    
    @Override
    public void info(Exception exception){
        logger.info(exception);
    }
    
    @Override
    public void info(String message,Throwable throwable){
        logger.info(message,throwable);
    }      
    
    @Override
    public void warn(String message){
        logger.warn(message);
    }
    
    @Override
    public void warn(String message,Object o){
        logger.warn(message,o);
    }    
    
    @Override
    public void warn(String message,Object o1,Object o2){
        logger.warn(message,o1,o2);
    }    
    
    @Override
    public void warn(String message,Object...params){
        logger.warn(message,params);
    }
    
    public void warn(String message,Exception exception){
        logger.warn(message,exception);
    }
    
    @Override
    public void warn(Exception exception){
        logger.warn(exception);
    }
    
    @Override
    public void warn(String message,Throwable throwable){
        logger.warn(message,throwable);
    }      
    
    @Override
    public void error(String message){
         logger.error(message);
    }
    
    @Override
    public void error(String message,Object o){
        logger.error(message,o);
    }    
 
    @Override
    public void error(String message,Object o1,Object o2){
        logger.error(message,o1,o2);
    } 
    
    @Override
    public void error(String message,Object...params){
        logger.error(message,params);
    }
    
    @Override
    public void error(String message,Exception exception){
        logger.error(message,exception);
    }
    
    @Override
    public void error(Exception exception){
        logger.error(exception);
    }
    
    @Override
    public void error(String message,Throwable throwable){
        logger.error(message,throwable);
    }      

    @Override
    public void trace(String message){
         logger.mark(message);
    }
    
    @Override
    public void trace(String message,Object o){
        logger.mark(message,o);
    }    
 
    @Override
    public void trace(String message,Object o1,Object o2){
        logger.mark(message,o1,o2);
    } 
    
    @Override
    public void trace(String message,Object...params){
        logger.mark(message,params);
    }
    
    @Override
    public void trace(String message,Exception exception){
        logger.mark(message,exception);
    }
    
    @Override
    public void trace(Exception exception){
        logger.mark(exception.getMessage(),exception);
    }
    
    @Override
    public void trace(String message,Throwable throwable){
        logger.mark(message,throwable);
    } 
    
    @Override
    public boolean isTraceEnabled(){
        return logger.isWarnEnabled();
    }    
    
    @Override
    public boolean isDebugEnabled(){
        return logger.isDebugEnabled();
    }
    
    @Override
    public boolean isInfoEnabled(){
        return logger.isInfoEnabled();
    }
    
    @Override
    public boolean isWarnEnabled(){
        return logger.isWarnEnabled();
    }
    
    @Override
    public boolean isErrorEnabled(){
        return logger.isErrorEnabled();
    }

}