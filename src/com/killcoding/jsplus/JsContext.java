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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptEngineFactory;
import javax.script.Invocable;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;
import com.killcoding.jsplus.JsPlusContext;
import com.killcoding.tool.CommonTools;

import java.util.List;

import com.killcoding.tool.FileTools;
import java.io.File;
import com.killcoding.file.DiskFile;
import com.killcoding.tool.CipherTools;
import com.killcoding.file.BaseFile;


/**
* Abstract JavaScript engine context: defines the common script-evaluation lifecycle (engine
* manager init, eval of path / file / inline script, bindings, close) shared by the GraalVM and
* Nashorn concrete contexts.
* */
public class JsContext extends JsPlusContext {

    private ScriptEngineManager scriptEngineManager = null;
	private Invocable invocable = null;
	private ScriptEngine scriptEngine = null;
	private ScriptContext context = null;
	private Bindings bindings = null;

    /**
    * Constructs a new JsContext
    * @param engineName - the engineName
    * */
    public JsContext(String engineName){
        super(engineName);
    }

	/*
	* Performs the getBindings operation
	* @return the getBindings result
	* */
	@Override
	public Object getBindings(){
	    return bindings;
	}

    /*
    * Performs the getEngine operation
    * @return the getEngine result
    * */
    @Override
    public ScriptEngineManager getEngine(){
        return scriptEngineManager;
    }

    /*
    * Performs the getContext operation
    * @return the getContext result
    * */
    @Override
    public ScriptContext getContext(){
        return context;
    }

	/*
	* Performs the remove operation
	* @param name - the name
	* */
	@Override
	public void remove(String name){
	    bindings.remove(name);
	}

	/*
	* Performs the put operation
	* @param name - the name
	* @param value - the value
	* */
	@Override
	public void put(String name,Object value){
	    bindings.put(name,value);
	}

	/*
	* Performs the get operation
	* @param name - the name
	* @return the get result
	* */
	@Override
	public Object get(String name){
	    return bindings.get(name);
	}

	/*
	* Performs the initScriptEngineManager operation
	* */
	@Override
	public void initScriptEngineManager() throws Exception {
		ClassLoader classLoader = null;
		classLoader = ClassLoader.getSystemClassLoader();
		scriptEngineManager = new ScriptEngineManager();
		List<ScriptEngineFactory> factories = scriptEngineManager.getEngineFactories();
		for (ScriptEngineFactory factory : factories) {
			System.out.println(
					String.format("%s %s %s", factory.getEngineName(), factory.getEngineVersion(), factory.getNames()));
		}
		if (factories.isEmpty()) {
			System.out.println("No script engines found");
		} else {
			scriptEngine = scriptEngineManager.getEngineByName(engineName);
			if(scriptEngine == null){
			    throw new Exception(String.format("Missing engine '%s'.",engineName));
			}
			context = new SimpleScriptContext();
			context.setBindings(scriptEngine.createBindings(), ScriptContext.ENGINE_SCOPE);
			bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
			bindings.put("ScriptClassLoader", classLoader);
			disableAllowAllAccess();
			invocable = (Invocable) scriptEngine;
		}
	}

	/*
	* Performs the setAllowAllAccess operation
	* @param allowAllAccess - the allowAllAccess
	* */
	@Override
	public void setAllowAllAccess(boolean allowAllAccess){
	    this.allowAllAccess = allowAllAccess;
	    disableAllowAllAccess();
	}

	/*
	* Performs the eval operation
	* @param scriptPath - the scriptPath
	* @return the eval result
	* */
	@Override
	public Object eval(String scriptPath) throws Exception {
		return eval(new File(scriptPath));
	}

	/*
	* Performs the eval operation
	* @param scriptPath - the scriptPath
	* @return the eval result
	* */
	@Override
	public Object eval(File scriptPath) throws Exception {
	    String script = null;
	    if(CommonTools.isBlank(scriptCipherAesKey)){
    		script = FileTools.text(scriptPath);
	    }else{
	        DiskFile df = new DiskFile(scriptPath);
	        byte[] encryptBytes = df.readAllBytes();
	        byte[] decryptBytes = CipherTools.AESDecrypt(scriptCipherAesKey,encryptBytes);
	        script = new String(decryptBytes,BaseFile.CHARSET);
	    }
		Object result = scriptEngine.eval(script, context);
		scriptEngine.setContext(context);
		invocable = (Invocable) scriptEngine;
		return result;
	}

	/*
	* Performs the evalScript operation
	* @param script - the script
	* @return the evalScript result
	* */
	@Override
	public Object evalScript(String script) throws Exception {
		Object result = scriptEngine.eval(script, context);
		scriptEngine.setContext(context);
		invocable = (Invocable) scriptEngine;
		return result;
	}

    /*
    * Performs the close operation
    * */
    @Override
    public void close(){
        bindings.clear();
        bindings = null;
        context = null;
    }

	/*
	* Performs the getEngineName operation
	* @return the getEngineName result
	* */
	@Override
	public String getEngineName(){
	    return engineName;
	}

	/**
	* Performs the disableAllowAllAccess operation
	* */
	protected void disableAllowAllAccess(){
	    if(!this.allowAllAccess && bindings != null){
    	    bindings.put("Java", null);
    	    bindings.put("JavaImporter", null);
            bindings.put("java", null);
            bindings.put("javax", null);
            bindings.put("app", null);
            bindings.put("com", null);
            bindings.put("org", null);
            bindings.put("net", null);
            bindings.put("sun", null);
            bindings.put("android", null);
            bindings.put("oracle", null);
            bindings.put("ibm", null);
            bindings.put("io", null);
            bindings.put("jdk", null);
            bindings.put("jakarta", null);
	    }
	}

}
