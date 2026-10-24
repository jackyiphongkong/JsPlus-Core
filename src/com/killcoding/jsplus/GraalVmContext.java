package com.killcoding.jsplus;

import com.killcoding.jsplus.JsPlusContext;
import java.io.File;
import com.killcoding.tool.FileTools;
import com.killcoding.log.LoggerFactory;
import com.killcoding.log.Logger;
import com.killcoding.tool.CommonTools;
import java.util.Set;
import java.util.Iterator;
import com.killcoding.tool.ConfigProperties;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.Engine;
import org.graalvm.options.OptionDescriptors;
import com.killcoding.tool.CipherTools;
import com.killcoding.file.DiskFile;
import com.killcoding.file.BaseFile;

public class GraalVmContext extends JsPlusContext {

	private Engine engine = null;
	private Context context = null;
	private Value value = null;
	
	public GraalVmContext(String engineName) {
		super(engineName);
		String[] parseJsParams = engineName.split("\\.",2);
        String useEngineName = "js";
        if(parseJsParams.length >= 2) {
            useEngineName = parseJsParams[1];
        }
        this.engineName = useEngineName;
	}
	
	@Override
	public String getEngineName(){
	    return engineName;
	}

	@Override
	public void initScriptEngineManager() throws Exception {
		Engine.Builder builder = Engine.newBuilder();
		ConfigProperties appenvCp = ScriptConsole.getAppenv();
		if(appenvCp != null){
		   String engineNameMapKey = String.format("GraalVM.%s",engineName);
		   Map appenvMap = appenvCp.getMap(engineNameMapKey,null);
		   if(appenvMap != null){
		       System.out.println("GraalVM options: " + appenvMap);
		       builder.options(appenvMap);
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

	@Override
	public Object getBindings() {
		return value;
	}

	@Override
	public Engine getEngine() {
		return engine;
	}

	@Override
	public Context getContext() {
		return context;
	}

	@Override
	public void remove(String name) {
		value.removeMember(name);
	}

	@Override
	public Object get(String name) {
		return value.getMember(name);
	}

	@Override
	public void put(String name, Object obj) {
		value.putMember(name, obj);
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
	    return evalScript(script);
	}

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

	@Override
	public void close() {
		context.close(true);
	}

}