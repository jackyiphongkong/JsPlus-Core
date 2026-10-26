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

import javax.script.ScriptEngine;
import javax.script.Invocable;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;
import com.killcoding.tool.CommonTools;

import java.util.List;

import com.killcoding.tool.FileTools;
import java.io.File;
import com.killcoding.file.DiskFile;
import com.killcoding.tool.CipherTools;
import com.killcoding.file.BaseFile;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.openjdk.nashorn.api.scripting.ClassFilter;
import com.killcoding.tool.ConfigProperties;

public class NashornContext extends JsPlusContext {

    private NashornScriptEngineFactory nashornFactory = null;
    private Invocable invocable = null;
    private ScriptEngine scriptEngine = null;
    private ScriptContext context = null;
    private Bindings bindings = null;
    private ClassLoader customClassLoader = null;
    
    private class SecurityClassFilter implements ClassFilter {
        @Override
        public boolean exposeToScripts(String className) {
            if (allowAllAccess) {
                return true;
            }
            return false;
        }
    }
    
    public NashornContext(String engineName){
        super(engineName);
    }
    
    @Override
    public Object getBindings(){
        return bindings;
    }    
    
    @Override
    public Object getEngine(){
        return nashornFactory;
    }
    
    @Override
    public ScriptContext getContext(){
        return context;
    }    
    
    @Override
    public void remove(String name){
        if (bindings != null) {
            bindings.remove(name);
        }
    }        
    
    @Override
    public void put(String name,Object value){
        if (bindings != null) {
            bindings.put(name,value);
        }
    }  
    
    @Override
    public Object get(String name){
        return bindings != null ? bindings.get(name) : null;
    }    

    @Override
    public void initScriptEngineManager() throws Exception {
        ConfigProperties appenvCp = ScriptConsole.getAppenv();
        String[] options = new String[]{};
        if(appenvCp != null){
            String engineNameMapKey = String.format("%s.options",engineName);
            List<String> optionsArray = appenvCp.getArray(engineNameMapKey,new String[]{});
            options = optionsArray.toArray(new String[]{});
            System.out.println("Options: " + optionsArray);
        }
        customClassLoader = ClassLoader.getSystemClassLoader();
        
        nashornFactory = new NashornScriptEngineFactory();
        
        if (allowAllAccess) {
            if (customClassLoader != null && customClassLoader != ClassLoader.getSystemClassLoader()) {
                scriptEngine = nashornFactory.getScriptEngine(options,customClassLoader);
            } else {
                scriptEngine = nashornFactory.getScriptEngine(options);
            }
        } else {
            ClassFilter filter = new SecurityClassFilter();
            scriptEngine = nashornFactory.getScriptEngine(options,customClassLoader,filter);

        }
        
        if(scriptEngine == null){
            throw new Exception(String.format("Unable to create engine '%s'.", engineName));
        }
        
        System.out.println(String.format("Engine Name: %s, Version: %s", 
            scriptEngine.getFactory().getEngineName(),
            scriptEngine.getFactory().getEngineVersion()));
        
        context = new SimpleScriptContext();
        context.setBindings(scriptEngine.createBindings(), ScriptContext.ENGINE_SCOPE);
        bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        
        if (customClassLoader != null) {
            bindings.put("ScriptClassLoader", customClassLoader);
        }

        invocable = (Invocable) scriptEngine;
    }
    
    @Override
    public void setAllowAllAccess(boolean allowAllAccess){
        boolean changed = this.allowAllAccess != allowAllAccess;
        this.allowAllAccess = allowAllAccess;

        if (changed && scriptEngine != null) {
            try {
                recreateEngine();
            } catch (Exception e) {
                log.error("Error resetting access permissions: {}",e.getMessage(),e);
            }
        }
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
        if (bindings != null) {
            bindings.clear();
            bindings = null;
        }
        context = null;
        scriptEngine = null;
        invocable = null;
        nashornFactory = null;
        customClassLoader = null;
    }	
    
    @Override
    public String getEngineName(){
        return engineName;
    } 
    
    public NashornScriptEngineFactory getNashornFactory() {
        return nashornFactory;
    }
    
    public ClassLoader getCustomClassLoader() {
        return customClassLoader;
    }
    
    private void recreateEngine() throws Exception {
        if (nashornFactory != null) {
            ConfigProperties appenvCp = ScriptConsole.getAppenv();
            String engineNameMapKey = String.format("%s.options",engineName);
            List<String> optionsArray = appenvCp.getArray(engineNameMapKey,new String[]{});
            String[] options = optionsArray.toArray(new String[]{});
            System.out.println("Options: " + optionsArray);
        
            ScriptEngine oldEngine = scriptEngine;
            Bindings oldBindings = bindings;
            ScriptContext oldContext = context;
            
            if (allowAllAccess) {
                if (customClassLoader != null && customClassLoader != ClassLoader.getSystemClassLoader()) {
                    scriptEngine = nashornFactory.getScriptEngine(options,customClassLoader);
                } else {
                    scriptEngine = nashornFactory.getScriptEngine(options);
                }
            } else {
                ClassFilter filter = new SecurityClassFilter();
                scriptEngine = nashornFactory.getScriptEngine(options, customClassLoader,filter);
            }
            
            if (scriptEngine == null) {
                throw new Exception("Unable to recreate engine.");
            }
            
            if (oldContext != null) {
                context = oldContext;
                scriptEngine.setContext(context);
            }
            
            if (oldBindings != null) {
                bindings = oldBindings;
                scriptEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
            }
            
            invocable = (Invocable) scriptEngine;
        }
    }
    
}