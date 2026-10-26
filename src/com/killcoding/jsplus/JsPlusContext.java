/*******************************************************************************
The MIT License (MIT)
Copyright (c) 2024 KILLCODING.COM
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*****************************************************************************/

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