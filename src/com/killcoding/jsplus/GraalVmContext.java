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
import com.killcoding.tool.FileTools;


import com.killcoding.tool.CommonTools;
import java.util.Set;

import com.killcoding.tool.ConfigProperties;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.Engine;

import com.killcoding.tool.CipherTools;
import com.killcoding.file.DiskFile;
import com.killcoding.file.BaseFile;

/**
* GraalVM JavaScript engine context (org.graalvm.polyglot): evaluates scripts, configures host
* access (allowAllAccess / PolyglotAccess) and option descriptors for the embedded engine.
* */
public class GraalVmContext extends JsPlusContext {

	private Engine engine = null;
	private Context context = null;
	private Value value = null;

	/**
	* Constructs a new GraalVmContext
	* @param engineName - the engineName
	* */
	public GraalVmContext(String engineName) {
		super(engineName);
		String[] parseJsParams = engineName.split("\\.",2);
        String useEngineName = "js";
        if(parseJsParams.length >= 2) {
            useEngineName = parseJsParams[1];
        }
        this.engineName = useEngineName;
	}

	/*
	* Performs the getEngineName operation
	* @return the getEngineName result
	* */
	@Override
	public String getEngineName(){
	    return engineName;
	}

	/*
	* Performs the initScriptEngineManager operation
	* */
	@Override
	public void initScriptEngineManager() throws Exception {
		Engine.Builder builder = Engine.newBuilder();
		ConfigProperties appenvCp = ScriptConsole.getAppenv();
		if(appenvCp != null){
		   String engineNameMapKey = String.format("GraalVM.%s",engineName);
		   Map<String, Object> appenvMap = appenvCp.getMap(engineNameMapKey,null);
		   if(appenvMap != null){
		       System.out.println("GraalVM options: " + appenvMap);
		       Map<String, String> optionsMap = new java.util.HashMap<String, String>();
		       for (Map.Entry<String, Object> opt : appenvMap.entrySet()) {
		           optionsMap.put(opt.getKey(), String.valueOf(opt.getValue()));
		       }
		       builder.options(optionsMap);
		   }
		}
		engine = builder.build();
		Set<String> languages = engine.getLanguages().keySet();
		System.out.println("GraalVM supported Languages: " + languages);

		Context.Builder contextBuiilder = Context.newBuilder();
		if(allowAllAccess){
            contextBuiilder
	            .allowAllAccess(true)
	            .allowHostAccess(HostAccess.ALL)
			    .allowCreateThread(true);
		}
	    context = contextBuiilder.engine(engine).build();

		value = context.getBindings(engineName);
		value.putMember("ScriptClassLoader", context.getClass().getClassLoader());
	}

	/*
	* Performs the getBindings operation
	* @return the getBindings result
	* */
	@Override
	public Object getBindings() {
		return value;
	}

	/*
	* Performs the getEngine operation
	* @return the getEngine result
	* */
	@Override
	public Engine getEngine() {
		return engine;
	}

	/*
	* Performs the getContext operation
	* @return the getContext result
	* */
	@Override
	public Context getContext() {
		return context;
	}

	/*
	* Performs the remove operation
	* @param name - the name
	* */
	@Override
	public void remove(String name) {
		value.removeMember(name);
	}

	/*
	* Performs the get operation
	* @param name - the name
	* @return the get result
	* */
	@Override
	public Object get(String name) {
		return value.getMember(name);
	}

	/*
	* Performs the put operation
	* @param name - the name
	* @param obj - the obj
	* */
	@Override
	public void put(String name, Object obj) {
		value.putMember(name, obj);
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
	    return evalScript(script);
	}

	/*
	* Performs the evalScript operation
	* @param script - the script
	* @return the evalScript result
	* */
	@Override
	public Object evalScript(String script) throws Exception {
		synchronized (context) {
			try {
				context.enter();
				return context.eval(engineName, script);
			} finally {
				context.leave();
			}
		}
	}

	/*
	* Performs the close operation
	* */
	@Override
	public void close() {
		context.close(true);
	}

}
