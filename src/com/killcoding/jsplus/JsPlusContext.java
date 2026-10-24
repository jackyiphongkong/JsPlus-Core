package com.killcoding.jsplus;

import java.io.File;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;

public abstract class JsPlusContext {

    protected String scriptCipherAesKey = null;
    protected Logger log = null;
	protected String engineName = null;
	protected boolean allowAllAccess = true;

	public JsPlusContext(String engineName) {
		super();
		this.engineName = engineName.trim();
		log = LoggerFactory.getLogger(this.getClass());
	}
	
	public static boolean isGraalJs(String engineName){
	    try{
	        return engineName.matches("^((?i)GraalVM\\.).*$");
	    }catch(Exception e){
	        return false;
	    }
	}
	
	public static boolean isNashornJs(String engineName){
	    try{
	        return engineName.matches("^((?i)Nashorn[^\\S]*)$");
	    }catch(Exception e){
	        return false;
	    }
	}	
	
	public void setAllowAllAccess(boolean allowAllAccess){
	    this.allowAllAccess = allowAllAccess;
	}
	
	public boolean isAllowAllAccess(){
	    return this.allowAllAccess;
	}		
	
	public void setScriptCipherAesKey(String scriptCipherAesKey){
	    this.scriptCipherAesKey = scriptCipherAesKey;
	}
	
	public void remove(){
	    String tid = Thread.currentThread().getId() + "";
	    remove(tid);
	}
    
    public void put(Object value){
        String tid = Thread.currentThread().getId() + "";
        put(tid,value);
    }
    
    public Object get() {
        String tid = Thread.currentThread().getId() + "";
        return get(tid);
    }  
	
	public abstract String getEngineName();
	
	public abstract Object getBindings();
	
    public abstract Object getEngine();
    
    public abstract Object getContext();	

    public abstract void close();
    
    public abstract void remove(String name);
    
    public abstract void put(String name,Object value);
    
    public abstract Object get(String name);

	public abstract void initScriptEngineManager() throws Exception;

	public abstract Object eval(String scriptPath) throws Exception;

	public abstract Object eval(File scriptPath) throws Exception;
	
	public abstract Object evalScript(String script) throws Exception;

}