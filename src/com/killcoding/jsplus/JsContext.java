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
import java.nio.file.Files;
import java.util.List;
import java.nio.file.Paths;
import com.killcoding.tool.FileTools;
import java.io.File;
import com.killcoding.file.DiskFile;
import com.killcoding.tool.CipherTools;
import com.killcoding.file.BaseFile;
import com.killcoding.jsplus.ScriptConsole;

public class JsContext extends JsPlusContext {

    private ScriptEngineManager scriptEngineManager = null;
	private Invocable invocable = null;
	private ScriptEngine scriptEngine = null;
	private ScriptContext context = null;
	private Bindings bindings = null;
	
    public JsContext(String engineName){
        super(engineName);
    }
    
	@Override
	public Object getBindings(){
	    return bindings;
	}    
    
	@Override
    public ScriptEngineManager getEngine(){
        return scriptEngineManager;
    }
    
	@Override
    public ScriptContext getContext(){
        return context;
    }    
    
    @Override
	public void remove(String name){
	    bindings.remove(name);
	}        
    
    @Override
	public void put(String name,Object value){
	    bindings.put(name,value);
	}  
	
	@Override
	public Object get(String name){
	    return bindings.get(name);
	}	

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
	
	@Override
	public void setAllowAllAccess(boolean allowAllAccess){
	    this.allowAllAccess = allowAllAccess;
	    disableAllowAllAccess();
	}

    @Override
	public Object eval(String scriptPath) throws Exception {
		return eval(new File(scriptPath));
	}

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
	
	@Override
	public Object evalScript(String script) throws Exception {
		Object result = scriptEngine.eval(script, context);
		scriptEngine.setContext(context);
		invocable = (Invocable) scriptEngine;
		return result;
	}	
	
    @Override
    public void close(){
        bindings.clear();
        bindings = null;
        context = null;
    }	
    
	@Override
	public String getEngineName(){
	    return engineName;
	} 
	
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