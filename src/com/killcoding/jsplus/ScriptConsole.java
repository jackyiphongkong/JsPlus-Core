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

import com.killcoding.tool.ConfigProperties;
import java.util.Arrays;
import java.util.List;
import com.killcoding.tool.CommonTools;
import java.text.SimpleDateFormat;
import java.io.File;
import java.util.Map;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.killcoding.cache.CacheArray;
import com.killcoding.cache.CacheArrayFilter;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import com.killcoding.datasource.Clock;

/**
* Script console: executes .js / .txt script files and inline scripts through the configured JS
* engine. Manages the console variable map, task timers, Console.callback / getObject() /
* getDataSource() plumbing, cache-api wiring and the ExitPolicy for controlled shutdown.
*
* Inherits the process-wide static foundation from ScriptConsoleBase (exit policy, JS_PLUS_PATH /
* Appenv bootstrap, exit monitor, static datasource, console variables, connection lifecycle,
* formatters, history) and the JDBC data access layer from ScriptConsoleJdbcBase (SQL execution,
* metadata / DDL, CSV import / export, JDBC-side Console functions).
*
* This top level owns: the script engine bootstrap (initScriptEngineManager / newBuilder /
* eval / evalScript), the GlobalCacheArray + Console.callback plumbing, the task timer and
* cron-like schedule machinery, the command router (execute / executeViaScript / executeViaCmd /
* executeViaGhost) and the 15 base-side Console functions (List<String> overloads).
* */
public class ScriptConsole extends ScriptConsoleJdbcBase implements ConsoleImpl {

	protected boolean taskExecuted = false;
	private String engineName = null;

    protected JsPlusContext jsPlusContext = null;

	protected CacheArray globalCacheArray = null;
	protected Runnable callback = null;
	protected Runnable callbackBatch = null;
	protected boolean callbackSync = true;

	private ScheduledExecutorService scheduleExecutor = null;
	private final Map<String,Map<String,Object>> scheduleMap = new HashMap<String,Map<String,Object>>();

	/**
	* Performs the callback operation
	* @param callback - the callback
	* @param callbackSync - the callbackSync
	* */
	public void callback(Runnable callback,boolean callbackSync) throws Exception {
	    if(this.callback == null){
	        this.callback = callback;
	        this.callbackSync = callbackSync;
	    }else{
	        throw new Exception("The GlobalCacheArray callback already exists.");
	    }
	}

	/**
	* Performs the callback operation
	* @param callback - the callback
	* */
	public void callback(Runnable callback) throws Exception {
	    callback(callback,true);
	}

	/**
	* Performs the callbackBatch operation
	* @param callbackBatch - the callbackBatch
	* @param callbackSync - the callbackSync
	* */
	public void callbackBatch(Runnable callbackBatch,boolean callbackSync) throws Exception {
	    if(this.callbackBatch == null){
	        this.callbackBatch = callbackBatch;
	        this.callbackSync = callbackSync;
	    }else{
	        throw new Exception("The GlobalCacheArray callbackBatch already exists.");
	    }
	}

	/**
	* Performs the callbackBatch operation
	* @param callbackBatch - the callbackBatch
	* */
	public void callbackBatch(Runnable callbackBatch) throws Exception {
	    callbackBatch(callbackBatch,true);
	}

	/**
	* Performs the getGlobalCacheArray operation
	* @param filterTimer - the filterTimer
	* @param filterLoopTimer - the filterLoopTimer
	* @return the getGlobalCacheArray result
	* */
	public CacheArray getGlobalCacheArray(long filterTimer,int filterLoopTimer){
	    initGlobalCacheArray(filterTimer,filterLoopTimer);
	    return globalCacheArray;
	}

	/**
	* Performs the getGlobalCacheArray operation
	* @return the getGlobalCacheArray result
	* */
	public CacheArray getGlobalCacheArray(){
	    initGlobalCacheArray(10L,100L);
	    return globalCacheArray;
	}

	/**
	* Performs the createCacheArrayFilter operation
	* @param filterTimer - the filterTimer
	* @param filterLoopTimer - the filterLoopTimer
	* @return the createCacheArrayFilter result
	* */
	protected CacheArrayFilter createCacheArrayFilter(long filterTimer,long filterLoopTimer){
	    return new CacheArrayFilter(filterTimer,filterLoopTimer){
	        @Override

	        /*
	        * Performs the execute operation
	        * @param index - the index
	        * @param o - the o
	        * */
	        public void execute(Integer index, Object o) {
                if(callback != null) {
                    if(callbackSync){
                        callback.run();
                    }else{
                        Executors.newSingleThreadExecutor().execute(callback);
                    }
                }
	        }

	        @Override

	        /*
	        * Performs the executeBatch operation
	        * @param batchIndex - the batchIndex
	        * @param batch - the batch
	        * */
	        public void executeBatch(Integer batchIndex,List<?> batch) {
                if(callbackBatch != null) {
                    if(callbackSync){
                        callbackBatch.run();
                    }else{
                        Executors.newSingleThreadExecutor().execute(callbackBatch);
                    }
                }
	        }

	        @Override

	        /*
	        * Performs the terminated operation
	        * */
	        public void terminated() {
                log.info("The GlobalCacheArray Terminated.");
	        }
	    };
	}

	/**
	* Performs the initGlobalCacheArray operation
	* @param filterTimer - the filterTimer
	* @param filterLoopTimer - the filterLoopTimer
	* */
	public void initGlobalCacheArray(long filterTimer,long filterLoopTimer){
	    if(globalCacheArray == null){
    	    globalCacheArray = new CacheArray(false);
    	    globalCacheArray.filter(createCacheArrayFilter(filterTimer,filterLoopTimer));
	    }
	}

	/**
	* Performs the initScriptEngineManager operation
	* */
	public void initScriptEngineManager() throws Exception {
	    initScriptEngineManager(true);
	}

	/**
	* Performs the initScriptEngineManager operation
	* @param allowAllAccess - the allowAllAccess
	* */
	public void initScriptEngineManager(boolean allowAllAccess) throws Exception {
	    String engineName = consoleVariables.getString("ScriptEngine", DEFAULT_ScriptEngine);
	    this.engineName = engineName;
	    System.out.println(String.format("***Current engine is '%s'.***",engineName));
	    boolean isGraalJs = JsPlusContext.isGraalJs(engineName);
	    boolean isNashornJs = JsPlusContext.isNashornJs(engineName);
	    if(isGraalJs){
	        jsPlusContext = new GraalVmContext(engineName);
	    }else if(isNashornJs){
	        jsPlusContext = new NashornContext(engineName);
	    }else{
            jsPlusContext = new JsContext(engineName);
	    }
	    jsPlusContext.setAllowAllAccess(allowAllAccess);
	    jsPlusContext.initScriptEngineManager();
	    String aesKey = consoleVariables.getString("Script_Cipher_AesKey", DEFAULT_Script_Cipher_AesKey);
	    jsPlusContext.setScriptCipherAesKey(aesKey);
	    jsPlusContext.put("Console",this);
	    displayEngineName = jsPlusContext.getEngineName();
	    set("Name",displayEngineName);
	}

	/**
	* Performs the getContext operation
	* @return the getContext result
	* */
	public JsPlusContext getContext(){
	    return jsPlusContext;
	}

	/**
	* Performs the eval operation
	* @param scriptPath - the scriptPath
	* @return the eval result
	* */
	public Object eval(String scriptPath) throws Exception {
		return eval(new File(scriptPath));
	}

	/**
	* Performs the eval operation
	* @param scriptPath - the scriptPath
	* @return the eval result
	* */
	public Object eval(File scriptPath) throws Exception {
		return jsPlusContext.eval(scriptPath);
	}

	/**
	* Performs the eval operation
	* @param execRun - the execRun
	* */
	public void eval(Runnable execRun) throws Exception {
		ExecutorService exec = Executors.newFixedThreadPool(1);
		exec.execute(execRun);
	}

	/**
	* Performs the closeContext operation
	* */
	public void closeContext() throws Exception {
		jsPlusContext.close();
	}

	/**
	* Performs the newBuilder operation
	* @param _engineName - the _engineName
	* @param allowAllAccess - the allowAllAccess
	* @return the newBuilder result
	* */
	public ScriptConsole newBuilder(String _engineName,boolean allowAllAccess) throws Exception {
	    ScriptConsole console = new ScriptConsole();
	    console.useScriptEnv = this.useScriptEnv;
	    console.useCmdEnv = this.useCmdEnv;
	    console.useJdbcEnv = this.useJdbcEnv;
	    console.consoleVariables = (ConfigProperties)this.consoleVariables.clone();
	    console.stopTask();
	    console.datasourceVariables = (ConfigProperties)this.datasourceVariables.clone();
	    console.set("ScriptEngine",_engineName);
	    console.initScriptEngineManager(allowAllAccess);
	    System.out.println("***New ScriptConsole: " + console.hashCode() + "***");
	    CONNECT_CONSOLES.put(console.hashCode(),console);
	    return console;
	}

	/**
	* Performs the newBuilder operation
	* @param _engineName - the _engineName
	* @return the newBuilder result
	* */
	public ScriptConsole newBuilder(String _engineName) throws Exception {
	    return newBuilder(_engineName,true);
	}

	/**
	* Performs the newBuilder operation
	* @param allowAllAccess - the allowAllAccess
	* @return the newBuilder result
	* */
	public ScriptConsole newBuilder(boolean allowAllAccess) throws Exception {
	    return newBuilder(this.engineName,allowAllAccess);
	}

	/**
	* Performs the newBuilder operation
	* @return the newBuilder result
	* */
	public ScriptConsole newBuilder() throws Exception {
	    return newBuilder(this.engineName,true);
	}

	/**
	* Performs the evalScript operation
	* @param script - the script
	* @return the evalScript result
	* */
	public Object evalScript(String script) throws Exception {
		return jsPlusContext.evalScript(script);
	}

	/**
	* Performs the executeViaGhost operation
	* @param commands - the commands
	* @return the executeViaGhost result
	* */
	public boolean executeViaGhost(String commands){
	    return executeViaGhost(true,commands);
	}

	/**
	* Performs the executeViaGhost operation
	* @param enableSystemOut - the enableSystemOut
	* @param commands - the commands
	* @return the executeViaGhost result
	* */
	public boolean executeViaGhost(boolean enableSystemOut,String commands){
	    String commandEnd = consoleVariables.getString("CommandEnd",DEFAULT_CommandEnd);
	    String executeCommand = commands.toString().replaceFirst("(" + commandEnd + "){1,}[\s]*$","").trim();

		if (executeCommand.trim().equals("")) {
			System.out.println("ERROR: Invalid blank command");
			return false;
		}

		Boolean writeHistory = consoleVariables.getBoolean("WriteHistory",DEFAULT_WriteHistory);
        String commandHistory = consoleVariables.getString("CommandHistory", DEFAULT_CommandHistory);

	    boolean isCancel = isCommandCancel(executeCommand);

	    if(isCancel) return true;

	    try{
	        log.debug(executeCommand);

		    if(writeHistory && executeCommand.indexOf(commandHistory) != 0){
		        if(!CommonTools.isBlank(executeCommand))
		            addHistory(executeCommand.trim());
		    }

	    	if (executeCommand.toUpperCase().equals("EXIT")) {
				if(cacheDriverExecutor != null){
                    if(!cacheDriverExecutor.isClosed()){
                        System.out.println("ERROR: The connection is in use, please close the connection first.");
                    }else{
                        exitConsole(0);
                        return true;
                    }
			    }
			    if(cacheDriverExecutor == null)
				    exitConsole(0);

    				return true;
    		}

    		if(executeCommand.indexOf(commandHistory) == 0){
			    String hiscmd = executeCommand.replaceFirst("^" + commandHistory,"");
		        showHistory(hiscmd);
		    }else if(executeCommand.toUpperCase().equals("CLEAR")){
		        clear(true);
		    }else if(executeCommand.toUpperCase().equals("JDBC")){
    	        setUseJdbcEnv();
    	        consoleVariables.putAndReturnSlef("CommandEnd",";");
    	        consoleVariables.putAndReturnSlef("Name","jdbc");
		        System.out.println("Changed CommandEnd to ';'");
        	}else if(executeCommand.toUpperCase().equals("SCRIPT")){
    	        setUseScriptEnv();
    	        consoleVariables.putAndReturnSlef("CommandEnd","/");
    	        set("Name",displayEngineName);
		        System.out.println("Changed CommandEnd to '/'");
        	} else if (executeCommand.matches(CONSOLE_VAR_REGEX)){
			    String[] keyValue = executeCommand.split("=",2);
			    if(keyValue.length == 2){
			        String key = keyValue[0].trim().replaceFirst("^Console\\.","");
			        String value = keyValue[1].trim();
			        consoleVariables.putAndReturnSlef(key,value);
			        if(enableSystemOut) System.out.println(String.format("Successfully Console.%s=%s",key,value));
			    }else{
			        if(enableSystemOut) System.out.println("ERROR: Invalid console variable");
			    }
			} else if (executeCommand.matches(CONSOLE_FUN_REGEX)){
        	    Pattern pattern = Pattern.compile(CONSOLE_FUN_REGEX);
                Matcher matcher = pattern.matcher(executeCommand);
                if(matcher.find()){
                    String fun = matcher.group(2);
                    String paramsStr = matcher.group(3).trim();
                    List<String> params = new ArrayList<String>();
                    if(!CommonTools.isBlank(paramsStr)){
                        params = Arrays.asList(paramsStr.split(","));
                    }
                    try{
                        Method method = CmdConsole.class.getMethod(fun,List.class);
                        Object msg = method.invoke(this,params);
                        if(enableSystemOut) {
                            if(msg != null){
                                System.out.println(msg.toString());
                            }
                        }
                    }catch(Exception e){
                        log.error(e.getMessage(),e);
                        if(enableSystemOut){
                            System.out.println(String.format("ERROR: Invalid method '%s'",fun));
                            System.out.println(error(e));
                        }
                    }
                }
			}else{
	           if(ghostConsole == null){
	               ghostConsole = new GhostConsole(this,consoleVariables.getString("GhostName","DefaultGhost"));
	               ghostConsole.detect();
	           }
	           ghostConsole.submit(executeCommand);
        	}
	    }catch(Exception e){
            System.out.println(error(e));
            log.info(executeCommand);
            log.error(e.getMessage(),e);
            if(writeHistory && executeCommand.indexOf(commandHistory) != 0){
                addHistory(" <INVALID");
            }
            return false;
		}finally {
    		if(writeHistory && executeCommand.indexOf(commandHistory) != 0){
    	    	addHistory(System.lineSeparator());
    		}
		}
		return true;
	}

	/**
	* Performs the executeViaCmd operation
	* @param enableSystemOut - the enableSystemOut
	* @param commands - the commands
	* @return the executeViaCmd result
	* */
	public boolean executeViaCmd(boolean enableSystemOut,String commands){
	    return executeViaCmd(enableSystemOut,commands,null,null);
	}

	/**
	* Performs the executeViaCmd operation
	* @param enableSystemOut - the enableSystemOut
	* @param commands - the commands
	* @param successfullyRun - the successfullyRun
	* @param faiiledRun - the faiiledRun
	* @return the executeViaCmd result
	* */
	public boolean executeViaCmd(boolean enableSystemOut,String commands,Runnable successfullyRun,Runnable faiiledRun){
	    String commandEnd = consoleVariables.getString("CommandEnd",DEFAULT_CommandEnd);
	    String executeCommand = commands.toString().replaceFirst("(" + commandEnd + "){1,}[\s]*$","").trim();

		if (executeCommand.trim().equals("")) {
			System.out.println("ERROR: Invalid blank command");
			return false;
		}

		Boolean writeHistory = consoleVariables.getBoolean("WriteHistory",DEFAULT_WriteHistory);
        String commandHistory = consoleVariables.getString("CommandHistory", DEFAULT_CommandHistory);

	    boolean isCancel = isCommandCancel(executeCommand);

	    if(isCancel) return true;

	    try{
	        log.debug(executeCommand);

		    if(writeHistory && executeCommand.indexOf(commandHistory) != 0){
		        if(!CommonTools.isBlank(executeCommand))
		            addHistory(executeCommand.trim());
		    }

	    	if (executeCommand.toUpperCase().equals("EXIT")) {
				if(cacheDriverExecutor != null){
                    if(!cacheDriverExecutor.isClosed()){
                        System.out.println("ERROR: The connection is in use, please close the connection first.");
                    }else{
                        exitConsole(0);
                        return true;
                    }
			    }
			    if(cacheDriverExecutor == null)
				    exitConsole(0);

    				return true;
    		}

    		if(executeCommand.indexOf(commandHistory) == 0){
			    String hiscmd = executeCommand.replaceFirst("^" + commandHistory,"");
		        showHistory(hiscmd);
		    }else if(executeCommand.toUpperCase().equals("CLEAR")){
		        clear(true);
		    }else if(executeCommand.toUpperCase().equals("JDBC")){
    	        setUseJdbcEnv();
    	        consoleVariables.putAndReturnSlef("CommandEnd",";");
    	        consoleVariables.putAndReturnSlef("Name","jdbc");
    	        if(enableSystemOut) {
		            System.out.println("Changed CommandEnd to ';'");
    	        }
        	}else if(executeCommand.toUpperCase().equals("SCRIPT")){
    	        setUseScriptEnv();
    	        consoleVariables.putAndReturnSlef("CommandEnd","/");
    	        set("Name",displayEngineName);
    	        if(enableSystemOut) {
		            System.out.println("Changed CommandEnd to '/'");
    	        }
        	}else if(executeCommand.toUpperCase().equals("GHOST")){
    	        setUseGhostEnv();
    	        consoleVariables.putAndReturnSlef("CommandEnd","/");
    	        set("Name","ghost");
    	        if(enableSystemOut) {
		            System.out.println("Changed CommandEnd to '/'");
    	        }
        	} else if (executeCommand.matches(CONSOLE_VAR_REGEX)){
			    String[] keyValue = executeCommand.split("=",2);
			    if(keyValue.length == 2){
			        String key = keyValue[0].trim().replaceFirst("^Console\\.","");
			        String value = keyValue[1].trim();
			        consoleVariables.putAndReturnSlef(key,value);
			        if(enableSystemOut) System.out.println(String.format("Successfully Console.%s=%s",key,value));
			    }else{
			        if(enableSystemOut) System.out.println("ERROR: Invalid console variable");
			    }
			} else if (executeCommand.matches(CONSOLE_FUN_REGEX)){
        	    Pattern pattern = Pattern.compile(CONSOLE_FUN_REGEX);
                Matcher matcher = pattern.matcher(executeCommand);
                if(matcher.find()){
                    String fun = matcher.group(2);
                    String paramsStr = matcher.group(3).trim();
                    List<String> params = new ArrayList<String>();
                    if(!CommonTools.isBlank(paramsStr)){
                        params = Arrays.asList(paramsStr.split(","));
                    }
                    try{
                        Method method = CmdConsole.class.getMethod(fun,List.class);
                        Object msg = method.invoke(this,params);
                        if(enableSystemOut) {
                            if(msg != null){
                                System.out.println(msg.toString());
                            }
                        }
                    }catch(Exception e){
                        log.error(e.getMessage(),e);
                        if(enableSystemOut){
                            System.out.println(String.format("ERROR: Invalid method '%s'",fun));
                            System.out.println(error(e));
                        }
                    }
                }
			}else{
	           if(cmdConsole == null){
	               cmdConsole = new CmdConsole(enableSystemOut);
	           }
	           Process process = cmdConsole.submit(executeCommand,successfullyRun,faiiledRun);
	           if(process != null){
	               System.out.println(String.format("Current PID '%s'.",process.pid()));
	           }
        	}
	    }catch(Exception e){
            System.out.println(error(e));
            log.error(executeCommand);
            log.error(e.getMessage(),e);
            if(writeHistory && executeCommand.indexOf(commandHistory) != 0){
                addHistory(" <INVALID");
            }
            return false;
		}finally {
    		if(writeHistory && executeCommand.indexOf(commandHistory) != 0){
    	    	addHistory(System.lineSeparator());
    		}
		}
		return true;
	}

	/**
	* Performs the executeViaScript operation
	* @param commands - the commands
	* @return the executeViaScript result
	* */
	public boolean executeViaScript(String commands){
	    String commandEnd = consoleVariables.getString("CommandEnd",DEFAULT_CommandEnd);
	    String executeCommand = commands.toString().replaceFirst("(" + commandEnd + "){1,}[\s]*$","").trim();

		if (executeCommand.trim().equals("")) {
			System.out.println("ERROR: Invalid blank command");
			return false;
		}

		Boolean writeHistory = consoleVariables.getBoolean("WriteHistory",DEFAULT_WriteHistory);
        String commandHistory = consoleVariables.getString("CommandHistory", DEFAULT_CommandHistory);

	    boolean isCancel = isCommandCancel(executeCommand);

	    if(isCancel) return true;

	    try{
	        log.debug(executeCommand);

		    if(writeHistory && executeCommand.indexOf(commandHistory) != 0){
		        if(!CommonTools.isBlank(executeCommand))
		            addHistory(executeCommand.trim());
		    }

	    	if (executeCommand.toUpperCase().equals("EXIT")) {
				if(cacheDriverExecutor != null){
                    if(!cacheDriverExecutor.isClosed()){
                        System.out.println("ERROR: The connection is in use, please close the connection first.");
                    }else{
                        exitConsole(0);
                        return true;
                    }
			    }
			    if(cacheDriverExecutor == null)
				    exitConsole(0);

    				return true;
    		}

    		if(executeCommand.indexOf(commandHistory) == 0){
			    String hiscmd = executeCommand.replaceFirst("^" + commandHistory,"");
		        showHistory(hiscmd);
		    }else if(executeCommand.toUpperCase().equals("CLEAR")){
		        clear(true);
		    }else if(executeCommand.toUpperCase().equals("JDBC")){
    	        setUseJdbcEnv();
    	        consoleVariables.putAndReturnSlef("CommandEnd",";");
    	        consoleVariables.putAndReturnSlef("Name","jdbc");
		        System.out.println("Changed CommandEnd to ';'");
        	}else if(executeCommand.toUpperCase().equals("CMD")){
    	        setUseCmdEnv();
    	        consoleVariables.putAndReturnSlef("CommandEnd","/");
    	        consoleVariables.putAndReturnSlef("Name","cmd");
		        System.out.println("Changed CommandEnd to '/'");
        	}else if(executeCommand.toUpperCase().equals("GHOST")){
    	        setUseGhostEnv();
    	        consoleVariables.putAndReturnSlef("CommandEnd","/");
    	        consoleVariables.putAndReturnSlef("Name","ghost");
		        System.out.println("Changed CommandEnd to '/'");
        	}else{
        		if(jsPlusContext == null){
        		    initScriptEngineManager();
        		}
	            evalScript(executeCommand);
        	}
	    }catch(Exception e){
            System.out.println(error(e));
            log.debug(executeCommand);
            log.error(e.getMessage(),e);
            if(writeHistory && executeCommand.indexOf(commandHistory) != 0){
                addHistory(" <INVALID");
            }
            return false;
		}finally {
    		if(writeHistory && executeCommand.indexOf(commandHistory) != 0){
    	    	addHistory(System.lineSeparator());
    		}
		}
		return true;
    }

 	/**
 	* Performs the execute operation
 	* @param commands - the commands
 	* @return the execute result
 	* */
 	public boolean execute(String commands) {
 	    if(isUseScriptEnv()) return executeViaScript(commands);

 	    if(isUseJdbcEnv()) return executeViaJdbc(commands);

 	    if(isUseCmdEnv()) return executeViaCmd(commands);

 	    return false;
	}

	/**
	* Performs the executeViaCmd operation
	* @param commands - the commands
	* @return the executeViaCmd result
	* */
	public boolean executeViaCmd(String commands) {
		return executeViaCmd(true, commands);
	}

	/**
	* Performs the executeViaCmd operation
	* @param enableSystemOut - the enableSystemOut
	* @param commands - the commands
	* @param successfullyRun - the successfullyRun
	* @param faiiledRun - the faiiledRun
	* @return the executeViaCmd result
	* */
	public boolean executeViaCmd(Boolean enableSystemOut,String commands,Runnable successfullyRun,Runnable faiiledRun) {
		return executeViaCmd(enableSystemOut, commands,successfullyRun,faiiledRun);
	}

	/**
	* Performs the executeBatch operation
	* @param batchFile - the batchFile
	* @return the executeBatch result
	* */
	public Object executeBatch(String batchFile) throws Exception {
	    if(isUseScriptEnv()){
		    return eval(batchFile);
	    }
	    if(isUseJdbcEnv()){
		    return executeJdbcBatch(batchFile);
	    }
	    if(isUseCmdEnv()){
		    return executeCmdBatch(batchFile);
	    }
	    return null;
	}

	/**
	* Performs the executeCmdBatch operation
	* @param _batchFile - the _batchFile
	* @return the executeCmdBatch result
	* */
	protected String executeCmdBatch(String _batchFile) throws Exception {
		try {
		    File batchFile = new File(_batchFile);
			if (!batchFile.exists()) {
				return String.format("ERROR: Batch file not found '%s'", batchFile);
			}
			String commandLines = decryptScript(batchFile);
			if (!CommonTools.isBlank(commandLines)) {
				String commandEnd = consoleVariables.getString("CommandEnd", DEFAULT_CommandEnd);
				List<String> commandList = Arrays.asList(commandLines.split(commandEnd + "[^\\S]*\n"));
				int row = 0;
				for (String cl : commandList) {
					if (!CommonTools.isBlank(cl)) {
						String command = cl.trim();
						if (command.indexOf("#") != 0) {
							System.out.println(
									String.format("%s) %s",row,command));

							executeViaCmd(true, command);
						}
					}
					row++;
				}
			}
			return null;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return e.getMessage();
		}
	}

	/**
	* Performs the task operation
	* */
	protected void task() throws Exception {
        String task = consoleVariables.getString("Task", DEFAULT_Task);
		Long taskTimer = consoleVariables.getMilliSeconds("TaskTimer", DEFAULT_TaskTimer);
		task(task,taskTimer);
	}

	/**
	* Performs the task operation
	* @param task - the task
	* @param taskTimer - the taskTimer
	* */
	protected void task(String task,Long taskTimer) throws Exception {
		String commandLines = null;
		if (!CommonTools.isBlank(task)) {
			long timeout = new java.util.Date().getTime() - preExecuteTime;
			if (timeout >= taskTimer) {
				preExecuteTime = new java.util.Date().getTime();
				task = task.trim();
				//check task content script "(...js...)"
				if(task.indexOf("(") == 0){
					commandLines = task;
				}else{
					String taskFilePath = null;
					if(Files.exists(Paths.get(task))){
						taskFilePath = task;
					}else{
						taskFilePath = getJsPlusPath() + "/" + task;
					}
					File taskFile = new File(taskFilePath);
					if(!taskFile.exists()){
						throw new FileNotFoundException(String.format("The task file '%s' missing."));
					}
					commandLines = decryptScript(taskFile);
				}
				String commandEnd = consoleVariables.getString("CommandEnd", DEFAULT_CommandEnd);
				if (!CommonTools.isBlank(commandLines) && !useScriptEnv) {
					Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
					String[] lines = commandLines.split("\\" + commandEnd + "[^\\S]*[\r]*\n{1,}");
					int lineSize = lines.length;
					for (int i = 0; i < lineSize; i++) {
						String command = lines[i].trim();
						if (showTaskLog) {
							System.out.println(String.format("%s) %s",i, command));
						}

						if (command.indexOf("-") == 0)
							continue;

						executeViaJdbc(showTaskLog, command);
					}
				}
				if(!CommonTools.isBlank(commandLines) && useScriptEnv){
				    executeViaScript(commandLines);
				}
				if(!CommonTools.isBlank(commandLines) && useGhostEnv){
				    executeViaGhost(commandLines);
				}
			}
			if (taskTimer <= 0) {
				stopTask();
			}
		}
	}

	/**
	* Performs the setTask operation
	* @param taskTimer - the taskTimer
	* @param task - the task
	* */
	public void setTask(String taskTimer,String task){
	    set("Task",task);
	    set("TaskTimer",taskTimer);
	}

	/**
	* Performs the setTask operation
	* @param task - the task
	* */
	public void setTask(String task){
	    set("Task",task);
	    set("TaskTimer","0ms");
	}

	/**
	* Performs the setTaskTimer operation
	* @param taskTimer - the taskTimer
	* */
	public void setTaskTimer(String taskTimer){
	    set("TaskTimer",taskTimer);
	}

	/**
	* Performs the taskExecute operation
	* */
	public void taskExecute() {
		taskExecuted = true;
		ExecutorService executor = Executors.newFixedThreadPool(1);
		executor.execute(new Runnable() {

			@Override

			/*
			* Performs the run operation
			* */
			public void run() {

				while (true) {

					if (Thread.currentThread().isInterrupted())
						break;

					try {
						String task = consoleVariables.getString("Task", DEFAULT_Task);
						if (!CommonTools.isBlank(task)) {
							task();
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
					try {
						Thread.sleep(100L);
					} catch (InterruptedException e) {
						log.error(e.getMessage(), e);
					}
				}

			}

		});
	}

    /**
    * Performs the startSchedule operation
    * */
    public synchronized void startSchedule() {
        if (scheduleExecutor == null) {
            scheduleExecutor = Executors.newSingleThreadScheduledExecutor();
            scheduleNextMinute();
        }
    }

    /**
    * Performs the scheduleNextMinute operation
    * */
    private void scheduleNextMinute() {
        long delay = getDelayToNextMinute();
        scheduleExecutor.schedule(new Runnable() {
            @Override

            /*
            * Performs the run operation
            * */
            public void run() {
                try {
                    executeTasks();
                } finally {
                    scheduleNextMinute();
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
    * Performs the executeTasks operation
    * */
    private void executeTasks() {
        try{
            List<String> nameList = new ArrayList<String>(scheduleMap.keySet());
            for (String name : nameList) {
                Thread.currentThread().setName("executeTasks-" + name);
                Map<String, Object> taskMap = scheduleMap.get(name);
                String cronRules = (String) taskMap.get("cronRules");
                Runnable runnable = (Runnable) taskMap.get("runnable");
                Clock clock = new Clock();
                Date date = clock.getDate();
                String currentMinsStr = new SimpleDateFormat("mm").format(date).toUpperCase();
                String currentTimeStr = new SimpleDateFormat("HH:mm").format(date).toUpperCase();
                String currentDayTimeStr = new SimpleDateFormat("E HH:mm").format(date).toUpperCase();
                List<String> cronRulesList = Arrays.asList(cronRules.trim().toUpperCase().split(",|;"));
                int currentMins = Integer.parseInt(currentMinsStr);
                boolean haveStar = cronRules.indexOf("*") >= 0;
                boolean matchedStar = false;
                if(haveStar && cronRulesList.size() == 1){
                    String minStr = cronRules.replaceFirst("^\\*","");
                    if(CommonTools.isBlank(minStr)){
                        minStr = "1";
                    }
                    int min = Integer.parseInt(minStr);
                    matchedStar = (currentMins <= 0 || currentMins % min <= 0);
                }
                boolean matched = cronRulesList.contains(currentTimeStr)
                               || cronRulesList.contains(currentDayTimeStr)
                               || cronRulesList.contains(currentMinsStr)
                               || matchedStar;
                if (matched) {
                    try {
                        if (runnable != null) {
                            Executors.newSingleThreadExecutor().execute(runnable);
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        }catch(Exception e){
            log.error(e.getMessage(), e);
        }
    }

	/**
	* Performs the addSchedule operation
	* @param name - the name
	* @param cronRules - the cronRules
	* @param runnable - the runnable
	* */
	public void addSchedule(String name,String cronRules,Runnable runnable) {
	    clearSchedule(name);
	    final Map<String,Object> map = new HashMap<String,Object>();
	    map.put("cronRules",cronRules);
	    map.put("runnable",runnable);
	    scheduleMap.put(name,map);
	}

	/**
	* Performs the clearSchedule operation
	* @param name - the name
	* */
	public void clearSchedule(String name) {
	    scheduleMap.remove(name);
	}

	/**
	* Performs the clearAllSchedule operation
	* @param name - the name
	* */
	public void clearAllSchedule(String name) {
	    scheduleMap.clear();
	}

	public Map<String,Map<String,Object>> getScheduleMap() {
	    return new HashMap<String, Map<String,Object>>(scheduleMap);
	}

    /**
    * Performs the getDelayToNextMinute operation
    * @return the getDelayToNextMinute result
    * */
    private long getDelayToNextMinute() {
        long now = System.currentTimeMillis();
        long nextMinute = ((now / 60000) + 1) * 60000;
        long delay = nextMinute - now;
        return delay > 0 ? delay : 60000;
    }

	/* For base console */

	/**
	* Performs the version operation
	* @param params - the params
	* @return the version result
	* */
	public Object version(List<String> params) {
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.version()'";
		}
		try {
			return version();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the help operation
	* @param params - the params
	* @return the help result
	* */
	public Object help(List<String> params) {
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.help()'";
		}
		try {
			return help();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the exit operation
	* @param params - the params
	* @return the exit result
	* */
	public Object exit(List<String> params) {
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.exit()'";
		}
		try {
			return ScriptConsoleBase.exit();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the reset operation
	* @param params - the params
	* @return the reset result
	* */
	public Object reset(List<String> params) {
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.reset()'";
		}
		reset();
		return "Successfully";
	}

	/**
	* Performs the setCacheApi operation
	* @param params - the params
	* @return the setCacheApi result
	* */
	public Object setCacheApi(List<String> params) {
	    if(isUseScriptEnv()){
	        return "ERROR: JDBC or CMD environment is supported.";
	    }
		if (params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.setCacheApi(apiClass)'";
		}
		try {
			String apiClass = formatCommand(params.get(0).trim());
			setCacheApi(apiClass);
			return String.format("Changed cache api to %s", apiClass);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the when operation
	* @param params - the params
	* @return the when result
	* */
	public Object when(List<String> params) {
	    if(isUseScriptEnv()){
	        return "ERROR: JDBC or CMD environment is supported.";
	    }
		if (params.size() == 0 || params.size() > 3) {
			return "ERROR: Invalid params, eg.'Console.when(variableName,compareValue,batchFile)'";
		}
		try {
			String variableName = formatCommand(params.get(0).trim());
			String compareValue = formatCommand(params.get(1).trim());
			String batchFile = formatCommand(params.get(2).trim());
			String variableValue = consoleVariables.getString(variableName);
			if (CommonTools.isBlank(variableValue)) {
				variableValue = "NULL";
			}
			if (variableValue.equals(compareValue)) {
				Object msg = executeBatch(batchFile);
				return msg;
			} else {
				return String.format("No matched compare (%s != %s)", variableValue, compareValue);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the into operation
	* @param params - the params
	* @return the into result
	* */
	public Object into(List<String> params) {
	    if(isUseScriptEnv()){
	        return "ERROR: JDBC or CMD environment is supported.";
	    }
		if (params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.into(variableName)'";
		}
		String name = formatCommand(params.get(0).trim());
		into(name);
		return null;
	}

	/**
	* Performs the batch operation
	* @param params - the params
	* @return the batch result
	* */
	public Object batch(List<String> params) {
	    if(isUseScriptEnv()){
	        return "ERROR: JDBC or CMD environment is supported.";
	    }
		if (params.size() == 0 || params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.batch(path)'";
		}
		if (params.size() == 1) {
		    try{
    			String batFile = formatCommand(params.get(0));
    			if (CommonTools.isBlank(batFile)) {
    				return "ERROR: Invalid params, eg.'Console.batch(path)'";
    			}
    			Object msg = executeBatch(batFile);
    			return msg;
		    }catch(Exception e){
    			log.error(e.getMessage(), e);
    			return ScriptConsoleBase.error(e);
		    }
		}
		return "Successfully";
	}

	/**
	* Performs the sleep operation
	* @param params - the params
	* @return the sleep result
	* */
	public Object sleep(List<String> params) {
	    if(isUseScriptEnv()){
	        return "ERROR: JDBC or CMD environment is supported.";
	    }
		if (params.size() == 0 || params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.sleep(10s)'";
		}
		String ms = "0s";
		if (params.size() == 1) {
			ms = formatCommand(params.get(0).trim());
		}
		try {
			sleep(ms);
			return "Successfully";
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the hostname operation
	* @param params - the params
	* @return the hostname result
	* */
	public Object hostname(List<String> params) {
	    if(isUseScriptEnv()){
	        return "ERROR: JDBC or CMD environment is supported.";
	    }
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.hostname()'";
		}
		try {
			return hostname();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the startTask operation
	* @param params - the params
	* @return the startTask result
	* */
	public Object startTask(List<String> params) {
	    if(isUseScriptEnv()){
	        return "ERROR: JDBC or CMD environment is supported.";
	    }
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.startTask(10s,path) or startTask(path)'";
		}
		String taskTimer = null;
		String task = null;
		if (params.size() == 1) {
			task = formatCommand(params.get(0).trim());
		}
		if (params.size() == 2) {
			taskTimer = formatCommand(params.get(0).trim());
			task = formatCommand(params.get(1).trim());
		}
		startTask(taskTimer, task);
		return "Successfully";
	}

	/**
	* Performs the stopTask operation
	* @param params - the params
	* @return the stopTask result
	* */
	public Object stopTask(List<String> params) {
	    if(isUseScriptEnv()){
	        return "ERROR: JDBC or CMD environment is supported.";
	    }
		if (params.size() == 0) {
			stopTask();
			return "Successfully";
		} else {
			return "ERROR: Invalid params, eg.'Console.stopTask()'";
		}
	}

	/**
	* Performs the set operation
	* @param params - the params
	* @return the set result
	* */
	public Object set(List<String> params) {
	    if(isUseScriptEnv()){
	        return "ERROR: JDBC or CMD environment is supported.";
	    }
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.set(variableName,value)'";
		}
		if (params.size() == 2) {
			String key = formatCommand(params.get(0).trim());
			String value = formatCommand(params.get(1));
			return set(key, value);
		}
		return null;
	}

	/**
	* Performs the show operation
	* @param params - the params
	* @return the show result
	* */
	public Object show(List<String> params) {
	    if(isUseScriptEnv()){
	        return "ERROR: JDBC or CMD environment is supported.";
	    }
		if (params.size() == 0 || params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.show(variableName)'";
		}
		if (params.size() == 1) {
			String key = formatCommand(params.get(0).trim());
			return show(key);
		}
		return null;
	}

	/**
	* Performs the showAll operation
	* @param params - the params
	* @return the showAll result
	* */
	public Object showAll(List<String> params) {
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.showAll()'";
		}
		return showAll();
	}

}
