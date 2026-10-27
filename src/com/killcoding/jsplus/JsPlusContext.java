/* ************************************************************************
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

/**
* Abstract engine-context base: per-thread script variable access (put / get / remove), engine
* detection helpers (isGraalJs / isNashornJs), script cipher AES key, and host access-control
* settings (allowAllAccess). Concrete contexts implement the bindings / engine / eval contract.
* */
public abstract class JsPlusContext {

    protected String scriptCipherAesKey = null;
    protected Logger log = null;
	protected String engineName = null;
	protected boolean allowAllAccess = true;

	/**
	* Constructs a new JsPlusContext
	* @param engineName - the engineName
	* */
	public JsPlusContext(String engineName) {
		super();
		this.engineName = engineName.trim();
		log = LoggerFactory.getLogger(this.getClass());
	}

	/**
	* Performs the isGraalJs operation
	* @param engineName - the engineName
	* @return the isGraalJs result
	* */
	public static boolean isGraalJs(String engineName){
	    try{
	        return engineName.matches("^((?i)GraalVM\\.).*$");
	    }catch(Exception e){
	        return false;
	    }
	}

	/**
	* Performs the isNashornJs operation
	* @param engineName - the engineName
	* @return the isNashornJs result
	* */
	public static boolean isNashornJs(String engineName){
	    try{
	        return engineName.matches("^((?i)Nashorn[^\\S]*)$");
	    }catch(Exception e){
	        return false;
	    }
	}

	/**
	* Performs the setAllowAllAccess operation
	* @param allowAllAccess - the allowAllAccess
	* */
	public void setAllowAllAccess(boolean allowAllAccess){
	    this.allowAllAccess = allowAllAccess;
	}

	/**
	* Performs the isAllowAllAccess operation
	* @return the isAllowAllAccess result
	* */
	public boolean isAllowAllAccess(){
	    return this.allowAllAccess;
	}

	/**
	* Performs the setScriptCipherAesKey operation
	* @param scriptCipherAesKey - the scriptCipherAesKey
	* */
	public void setScriptCipherAesKey(String scriptCipherAesKey){
	    this.scriptCipherAesKey = scriptCipherAesKey;
	}

	/**
	* Performs the remove operation
	* */
	public void remove(){
	    String tid = Thread.currentThread().threadId() + "";
	    remove(tid);
	}

    /**
    * Performs the put operation
    * @param value - the value
    * */
    public void put(Object value){
        String tid = Thread.currentThread().threadId() + "";
        put(tid,value);
    }

    /**
    * Performs the get operation
    * @return the get result
    * */
    public Object get() {
        String tid = Thread.currentThread().threadId() + "";
        return get(tid);
    }

	/**
	* Performs the getEngineName operation
	* @return the getEngineName result
	* */
	public abstract String getEngineName();

	/**
	* Performs the getBindings operation
	* @return the getBindings result
	* */
	public abstract Object getBindings();

    /**
    * Performs the getEngine operation
    * @return the getEngine result
    * */
    public abstract Object getEngine();

    /**
    * Performs the getContext operation
    * @return the getContext result
    * */
    public abstract Object getContext();

    /**
    * Performs the close operation
    * */
    public abstract void close();

    /**
    * Performs the remove operation
    * @param name - the name
    * */
    public abstract void remove(String name);

    /**
    * Performs the put operation
    * @param name - the name
    * @param value - the value
    * */
    public abstract void put(String name,Object value);

    /**
    * Performs the get operation
    * @param name - the name
    * @return the get result
    * */
    public abstract Object get(String name);

	/**
	* Performs the initScriptEngineManager operation
	* */
	public abstract void initScriptEngineManager() throws Exception;

	/**
	* Performs the eval operation
	* @param scriptPath - the scriptPath
	* @return the eval result
	* */
	public abstract Object eval(String scriptPath) throws Exception;

	/**
	* Performs the eval operation
	* @param scriptPath - the scriptPath
	* @return the eval result
	* */
	public abstract Object eval(File scriptPath) throws Exception;

	/**
	* Performs the evalScript operation
	* @param script - the script
	* @return the evalScript result
	* */
	public abstract Object evalScript(String script) throws Exception;

}
