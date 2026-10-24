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

import com.killcoding.tool.ConfigProperties;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.datasource.DriverDataSource;
import java.util.Arrays;
import java.util.List;
import com.killcoding.tool.CommonTools;
import java.text.SimpleDateFormat;
import com.killcoding.datasource.CacheDriverExecutor;
import com.killcoding.cache.CacheArray;
import java.io.File;
import com.killcoding.tool.FileTools;
import java.util.Map;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.killcoding.tool.ResultMap;
import com.killcoding.cache.StoredCache;
import java.util.stream.Stream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.Scanner;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import com.killcoding.tool.EscapeTools;
import com.killcoding.tool.CodeEscapeLevel;
import com.killcoding.cache.AbsCacheApi;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import com.killcoding.cache.CacheArrayFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.net.URLClassLoader;
import java.sql.Clob;
import java.io.Reader;
import java.sql.Blob;
import com.killcoding.cache.DataObject;
import com.killcoding.tool.CipherTools;
import com.killcoding.tool.Base64Tools;
import java.nio.file.LinkOption;
import com.killcoding.file.DiskFile;
import java.sql.Timestamp;
import com.killcoding.file.BaseFile;
import java.nio.file.FileAlreadyExistsException;
import com.killcoding.jsplus.JsPlusContext;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import com.killcoding.jsplus.NashornContext;
import com.killcoding.datasource.Clock;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalTime;

public class ScriptConsole implements ConsoleImpl {

	protected Logger log = null;
	
    protected static final Map<Integer,ScriptConsole> CONNECT_CONSOLES = new ConcurrentHashMap<Integer,ScriptConsole>();
	
	private static DriverDataSource datasource = null;

	protected ConfigProperties datasourceVariables = new ConfigProperties();
	protected ConfigProperties consoleVariables = new ConfigProperties();
	
	protected static ConfigProperties appenvCp = null;
		
	protected boolean taskExecuted = false;
	protected boolean firstOpen = true;
	private boolean useScriptEnv = false;
	private boolean useJdbcEnv = false;
	private boolean useCmdEnv = false;
	private boolean useGhostEnv = false;
	private String engineName = null;

	private long preExecuteTime = 0L;
	private CacheDriverExecutor cacheDriverExecutor = null;
	private String lastSelectCommand = null;
	private String intoValue = null;
	private boolean exportedCompleted = false;

    protected GhostConsole ghostConsole = null;
    protected CmdConsole cmdConsole = null;
    protected JsPlusContext jsPlusContext = null;
    protected String displayEngineName = "script";
    
	private static final List<Runnable> exitRuns = new ArrayList<Runnable>();
	private static final long startedTimeMs = System.currentTimeMillis();
	private static Path tmpPath = null;
	private static ExecutorService exitExecute = null;
	private ScheduledExecutorService scheduleExecutor = null;
	private final Map<String,Map<String,Object>> scheduleMap = new HashMap<String,Map<String,Object>>();
	
	public final static String JS_PLUS_PATH_KEY = "JS_PLUS_PATH";
	
	protected CacheArray globalCacheArray = null;
	protected Runnable callback = null;
	protected Runnable callbackBatch = null;
	protected boolean callbackSync = true;
	
	public ScriptConsole() {
		super();
		initEnv();
		log = LoggerFactory.getLogger(this.getClass());
		loadAppenv();
		reset();
	}
	
	public static String getJsPlusPath(){
	    return System.getProperty(JS_PLUS_PATH_KEY);
	}
	
	private synchronized void initEnv(){
	    String jsPlusPath = getJsPlusPath();
	    if(CommonTools.isBlank(jsPlusPath)){
	        jsPlusPath = System.getenv(JS_PLUS_PATH_KEY) == null ? System.getProperty(JS_PLUS_PATH_KEY) : System.getenv(JS_PLUS_PATH_KEY);
	        
	        if(CommonTools.isBlank(jsPlusPath)) jsPlusPath = CommonTools.getJarPath(ScriptConsole.class);
	        
    		System.setProperty(JS_PLUS_PATH_KEY, jsPlusPath);
    		File logConfFile = new File(String.format("%s/config/Logger.properties",jsPlusPath));
    		if(logConfFile.exists()){
    		    System.setProperty(LoggerFactory.APP_LOGGER_KEY,logConfFile.getAbsolutePath());
    		}
    		System.out.println("SET JS_PLUS_PATH=" + System.getProperty(JS_PLUS_PATH_KEY));
	    }
		ScriptConsole.initPool();
	}
	
	public void appenvReloaded(ConfigProperties apenvCp){
	    
	}
	
	public void loadAppenv(){
	    try{
	        if(appenvCp == null){
        	    String jsPlusPath = getJsPlusPath();
        	    File _appenvPropFile = new File(String.format("%s/config/Appenv.properties",jsPlusPath));
        	    if(_appenvPropFile != null && !_appenvPropFile.exists()){
        	        _appenvPropFile = new File(String.format("%s/config/appenv.properties",jsPlusPath));
        	    }
        	    if(_appenvPropFile != null && _appenvPropFile.exists()){
        	        final File appenvPropFile = _appenvPropFile;
            	    appenvCp = new ConfigProperties();
            	    Runnable updateRun = new Runnable(){
            	        @Override
            	        public void run(){
            	            Logger.systemDebug(ScriptConsole.class, "Appenv '{}' Reloading...", appenvPropFile);
    						List<String> propKeys = new ArrayList(appenvCp.keySet());
    						for (String pk : propKeys) {
    							String v = appenvCp.getString(pk, "");
    							if(log.isDebugEnabled()){
    							    System.out.println(String.format("Appenv -> %s=%s",pk,v));
    							}
    							System.setProperty(pk, v);
    						}
            	            Logger.systemMark(ScriptConsole.class, "Appenv '{}' Reloaded", appenvPropFile);
            	            appenvReloaded(appenvCp);
            	        }
            	    };
            	    appenvCp.load(appenvPropFile,updateRun);
            	    updateRun.run();
        	    }
	        }
	    }catch(Exception e){
	        log.error(e.getMessage(),e);
	    }
	}
	
	public static ConfigProperties getAppenv(){
	    return appenvCp;
	}
	
	private static boolean detectSystemExit() throws IOException {
        String exitTmpPathStr = String.format("%s/exit.tmp", tmpPath.toString());
		Path exitTmpPath = Paths.get(exitTmpPathStr);
		DiskFile exitTmpDf = new DiskFile(exitTmpPathStr);
		if(exitTmpDf.exists()) {
		    long exitMs = exitTmpDf.getModifyTime().getTime();
		    if(exitMs > startedTimeMs){
				int size = exitRuns.size();
				for (int i = 0; i < size; i++) {
					Runnable exitRun = exitRuns.get(i);
					if (exitRun != null) {
						exitRun.run();
					}
				}
				return true;
		    }
		}
		return false;
	}
	
	public synchronized static void initPool(){
		if (exitExecute == null) {
    		String jsPlusPath = getJsPlusPath();
    		String tmpPathStr = String.format("%s/.tmp/",jsPlusPath);
    		tmpPath = Paths.get(tmpPathStr);
    		try{
        		if (!Files.exists(tmpPath)) {
        			Files.createDirectory(tmpPath);
        			Files.setAttribute(tmpPath, "dos:hidden", true);
        		}	
    		}catch(Exception e){
    		    LoggerFactory.getLogger(ScriptConsole.class).error(e.getMessage(),e);
    		}
			exitExecute = Executors.newFixedThreadPool(1);
			exitExecute.execute(new Runnable() {
				@Override
				public void run() {
					while (!Thread.currentThread().isInterrupted()) {
					    try{
					        Thread.sleep(1000L);
					        Thread.currentThread().setName(String.format("DetectSystemExit-PID-%s",ProcessHandle.current().pid()));
                            
                            if(detectSystemExit()) break;
                            
					    }catch(Exception e){
					        LoggerFactory.getLogger(ScriptConsole.class).error(e.getMessage(),e);
					    }
					}
					LoggerFactory.getLogger(ScriptConsole.class).mark("System exit - PID {}.",ProcessHandle.current().pid());
					Logger.systemMark(ScriptConsole.class, "System exit - PID {}.",ProcessHandle.current().pid());
    				System.exit(0);
				}
			});
		}	    
	}
	
	public static void setStaticDataSource(DriverDataSource datasource){
	    ScriptConsole.datasource = datasource;
	}
	
	public void setDataSource(DriverDataSource datasource){
	    setStaticDataSource(datasource);
	}	

	public synchronized DriverDataSource getDataSource() throws IOException {
	    
	    if(datasource == null){
            File datasourceConfigFile = new File(ScriptConsole.getJsPlusPath() + "/config/DataSource.properties");
    		if(!datasourceConfigFile.exists()){
    		    datasourceConfigFile = new File(ScriptConsole.getJsPlusPath() + "/config/Datasource.properties");
    		}	    
    		if(datasourceConfigFile.exists()){
	            ScriptConsole.datasource = new DriverDataSource(datasourceConfigFile);
    		}else{
    		    ScriptConsole.datasource = new DriverDataSource();
    		    ScriptConsole.datasource.setConfig(datasourceVariables);
    		}
	    }
	    
		return datasource;
	}

	public String getIntoValue() {
		return intoValue;
	}

	public String getLastSelectCommand() {
		return lastSelectCommand;
	}

	public CacheDriverExecutor getCacheDriverExecutor() {
		return cacheDriverExecutor;
	}

	public CacheDriverExecutor getCacheDriverExecutor(boolean refreshConnection) {
		if (refreshConnection) {
			open(true);
		}
		return getCacheDriverExecutor();
	}

	public ConfigProperties getConsoleVariables() {
		return consoleVariables;
	}
	
	public void setConsoleVariables(ConfigProperties consoleVariables) {
		this.consoleVariables = consoleVariables;
	}	

	public void setLastSelectCommand(String lastSelectCommand) {
		this.lastSelectCommand = lastSelectCommand;
	}
	
	public void clearLastSelectCommand() {
		lastSelectCommand = null;
	}

	public void into(String name) {
		consoleVariables.putAndReturnSlef(name, intoValue);
	}

	public void sleep(String sleepTime) throws Exception {
		Long ms = ConfigProperties.parseTimeToMs(sleepTime, 0L);
		Thread.sleep(ms);
	}

	public String hostname() throws Exception {
		return CommonTools.getHostname();
	}

	public void startTask(String taskTimer, String taskFile) {
		if (taskTimer == null) {
			preExecuteTime = 0L;
			consoleVariables.putAndReturnSlef("TaskTimer", DEFAULT_TaskTimer);
			consoleVariables.putAndReturnSlef("Task", taskFile);
		} else {
			preExecuteTime = 0L;
			consoleVariables.putAndReturnSlef("TaskTimer", taskTimer);
			consoleVariables.putAndReturnSlef("Task", taskFile);
		}
	}

	public void stopTask() {
		preExecuteTime = 0L;
		consoleVariables.putAndReturnSlef("TaskTimer", DEFAULT_TaskTimer + "ms");
		consoleVariables.putAndReturnSlef("Task", null);
	}
	
	public void callback(Runnable callback,boolean callbackSync) throws Exception {
	    if(this.callback == null){
	        this.callback = callback;
	        this.callbackSync = callbackSync;
	    }else{
	        throw new Exception("The GlobalCacheArray callback already exists.");
	    }
	}	
	
	public void callback(Runnable callback) throws Exception {
	    callback(callback,true);
	}
	
	public void callbackBatch(Runnable callbackBatch,boolean callbackSync) throws Exception {
	    if(this.callbackBatch == null){
	        this.callbackBatch = callbackBatch;
	        this.callbackSync = callbackSync;
	    }else{
	        throw new Exception("The GlobalCacheArray callbackBatch already exists.");
	    }
	}	
	
	public void callbackBatch(Runnable callbackBatch) throws Exception {
	    callbackBatch(callbackBatch,true);
	}	
	
	public CacheArray getGlobalCacheArray(long filterTimer,int filterLoopTimer){
	    initGlobalCacheArray(filterTimer,filterLoopTimer);
	    return globalCacheArray;
	}
	
	public CacheArray getGlobalCacheArray(){
	    initGlobalCacheArray(10L,100L);
	    return globalCacheArray;
	}	

	protected CacheArrayFilter createCacheArrayFilter(long filterTimer,long filterLoopTimer){
	    return new CacheArrayFilter(filterTimer,filterLoopTimer){
	        @Override
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
	        public void executeBatch(Integer batchIndex,List batch) {
                if(callbackBatch != null) {
                    if(callbackSync){
                        callbackBatch.run();
                    }else{
                        Executors.newSingleThreadExecutor().execute(callbackBatch);
                    }
                }
	        }
	        
	        @Override
	        public void terminated() {
                log.info("The GlobalCacheArray Terminated.");
	        }
	    };
	}
	
	public void initGlobalCacheArray(long filterTimer,long filterLoopTimer){
	    if(globalCacheArray == null){
    	    globalCacheArray = new CacheArray(false);
    	    globalCacheArray.filter(createCacheArrayFilter(filterTimer,filterLoopTimer));
	    }
	}
	
	public void initScriptEngineManager() throws Exception {
	    initScriptEngineManager(true);
	}

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
	
	public JsPlusContext getContext(){
	    return jsPlusContext;
	}

	public Object eval(String scriptPath) throws Exception {
		return eval(new File(scriptPath));
	}
	
	public Object eval(File scriptPath) throws Exception {
		return jsPlusContext.eval(scriptPath);
	}
	
	public void eval(Runnable execRun) throws Exception {
		ExecutorService exec = Executors.newFixedThreadPool(1);
		exec.execute(execRun);
	}		

	public void closeContext() throws Exception {
		jsPlusContext.close();
	}
	
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
	
	public ScriptConsole newBuilder(String _engineName) throws Exception {
	    return newBuilder(_engineName,true);
	}	
	
	public ScriptConsole newBuilder(boolean allowAllAccess) throws Exception {
	    return newBuilder(this.engineName,allowAllAccess);
	}
	
	public ScriptConsole newBuilder() throws Exception {
	    return newBuilder(this.engineName,true);
	}	
	
	public List<Map<String, Object>> desc(String schema, String tableName) throws Exception {
		String schemaTableName = null;
		if (CommonTools.isBlank(schema)) {
			schemaTableName = tableName;
		} else {
			schemaTableName = String.format("%s.%s", schema, tableName);
		}
		return getCacheDriverExecutor(true).desc(schemaTableName);
	}

	public List<Map<String, Object>> showTables() throws Exception {
		return showTables((String)null);
	}

	public List<Map<String, Object>> showTables(String schema) throws Exception {
		if (schema == null) {
			return getCacheDriverExecutor(true).getAllTables();
		} else {
			return getCacheDriverExecutor(true).getAllTables(schema);
		}
	}

	public Map<String, Object> showColumnTypes(String tableName) throws Exception {
		return getCacheDriverExecutor(true).getColumnTypes(tableName);

	}

	public Map<String, Object> showColumnClasses(String tableName) throws Exception {
		return getCacheDriverExecutor(true).getColumnClasses(tableName);
	}
	
	public String set(String key,Object value) {
		consoleVariables.putAndReturnSlef(key,value);
		return String.format("Set %s=%s", key, value);
	}	

	public String show(String key) {
		Object value = consoleVariables.getString(key);
		return String.format("Get %s=%s", key, value);
	}

	public String showAll() {
		StringBuffer sbf = new StringBuffer();
		Object[] keys = consoleVariables.keySet().toArray();
		Arrays.sort(keys);
		for (Object _key : keys) {
			String key = _key.toString();
			Object value = consoleVariables.get(key);
			sbf.append(String.format("Get %s=%s%s", key, value, System.lineSeparator()));
		}
		return sbf.toString();
	}

	public Boolean setAutoCommit(Boolean autoCommit) throws Exception {
		if (cacheDriverExecutor == null) {
			return false;
		} else {
			getCacheDriverExecutor(true).getConnection().setAutoCommit(autoCommit);
			return true;
		}
	}

	public String genCreateTables(String schema, String sqlFilePath) {
		try {
			StringBuffer errMsg = null;
			StringBuffer sql = new StringBuffer();
			List<Map<String, Object>> tables = getCacheDriverExecutor(true).getAllTables();
			File sqlFile = new File(sqlFilePath);
			if (tables.size() > 0) {
				if (sqlFile.exists())
					sqlFile.delete();
			}
			for (Map<String, Object> table : tables) {
				try {
					String tableSchema = (String) table.get("TABLE_SCHEMA");
					String tableName = (String) table.get("TABLE_NAME");
					if (CommonTools.isBlank(schema) && CommonTools.isBlank(tableSchema)) {
						boolean append = sqlFile.exists();
						StringBuffer ts = new StringBuffer(genCreateTable(null, tableName));
						ts.append(System.lineSeparator());
						ts.append(System.lineSeparator());
						FileTools.write(sqlFile, ts.toString(), append);
					}
					if (!CommonTools.isBlank(schema) && !CommonTools.isBlank(tableSchema)) {
						if (schema.equalsIgnoreCase(tableSchema)) {
							boolean append = sqlFile.exists();
							StringBuffer ts = new StringBuffer(genCreateTable(tableSchema, tableName));
							ts.append(System.lineSeparator());
							ts.append(System.lineSeparator());
							FileTools.write(sqlFile, ts.toString(), append);
						}
					}
				} catch (Exception ee) {
					log.error(ee);
					if (errMsg == null)
						errMsg = new StringBuffer();

					errMsg.append(ee.getMessage());
					errMsg.append(System.lineSeparator());
				}
			}
			if (errMsg == null)
				return "Successfully";
			else
				return errMsg.toString();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return String.format("ERROR: %s", e.getMessage());
		}
	}

	public String genCreateTable(String schema, String tableName) throws Exception {
		String schemaTableName = null;
		if (CommonTools.isBlank(schema)) {
			schemaTableName = tableName;
		} else {
			schemaTableName = String.format("%s.%s", schema, tableName);
		}
		String commandEnd = consoleVariables.getString("CommandEnd", DEFAULT_CommandEnd);
		StringBuffer sql = new StringBuffer(String.format("CREATE TABLE %s", quote(exportUpperOrLowerCase(tableName))));
		sql.append(System.lineSeparator());
		sql.append("(");
		sql.append(System.lineSeparator());
		List<Map<String, Object>> list = getCacheDriverExecutor(true).desc(schemaTableName);
		int columnSize = list.size();
		List<String> pkeys = new ArrayList<String>();
		for (int i = 0; i < columnSize; i++) {
			Map<String, Object> tableInfo = list.get(i);
			Boolean isPrimaryKey = tableInfo.get("PRIMARY_KEY").equals("Y");

			if (!isPrimaryKey)
				continue;

			String name = (String) tableInfo.get("NAME");
			pkeys.add(quote(exportUpperOrLowerCase(name)));
		}
		String pkeysStr = null;
		if (pkeys.size() > 0) {
			pkeysStr = String.format(",%sPRIMARY KEY (%s)", System.lineSeparator(), String.join(",", pkeys));
		}
		for (int i = 0; i < columnSize; i++) {
			Map<String, Object> tableInfo = list.get(i);
			String name = exportUpperOrLowerCase((String) tableInfo.get("NAME"));
			String javaType = (String) tableInfo.get("JAVA_TYPE");
			String dataType = (String) tableInfo.get("DATA_TYPE");
			Integer precision = (Integer) tableInfo.get("PRECISION");
			Boolean isAllowNullable = tableInfo.get("ALLOW_NULLABLE").equals("Y");
			String allowNullableStr = isAllowNullable ? "" : "NOT NULL";
			boolean isId = name.matches("^.*[\\_]{0,1}id$") && dataType.equals("CHAR") && precision == 16;
			String tableDataType = getDataType(isId, javaType, precision);
			sql.append(String.format("%s %s %s", quote(name), tableDataType, allowNullableStr).trim());
			if (i < columnSize - 1) {
				sql.append(",");
				sql.append(System.lineSeparator());
			}
		}

		if (pkeysStr != null)
			sql.append(pkeysStr);

		sql.append(System.lineSeparator());
		sql.append(")");
		sql.append(commandEnd);
		return sql.toString();
	}

	public String getDataType(boolean isId, String javaType, int precision) {
		if (isId) {
			return "CHAR(16)";
		}
		if (javaType.equals(java.sql.Timestamp.class.getName())) {
			return String.format("DATETIME(%s)", 6);
		} else if (javaType.equals(java.util.Date.class.getName())) {
			return String.format("DATETIME(%s)", 6);
		} else if (javaType.equals(java.sql.Date.class.getName())) {
			return "DATE";
		} else if (javaType.equals(java.sql.Time.class.getName())) {
			return "TIME";
		} else if (javaType.equals(java.lang.Boolean.class.getName())) {
			return String.format("TINYINT(%s)", 1);
		} else if (javaType.equals(java.lang.Byte.class.getName())) {
			return "INT";
		} else if (javaType.equals(java.lang.Short.class.getName())) {
			return "INT";
		} else if (javaType.equals(java.lang.Integer.class.getName())) {
			return "INT";
		} else if (javaType.equals(java.lang.Long.class.getName())) {
			return "BIGINT";
		} else if (javaType.equals(java.lang.Float.class.getName())) {
			return "FLOAT";
		} else if (javaType.equals(java.lang.Double.class.getName())) {
			return "DOUBLE";
		} else if (javaType.equals(java.math.BigDecimal.class.getName())) {
			return "DOUBLE";
		} else if (javaType.equals(java.math.BigInteger.class.getName())) {
			return "BIGINT";
		} else if (javaType.equals(java.lang.String.class.getName())) {
			if (precision <= 500) {
				return String.format("VARCHAR(%s)", precision);
			} else {
				return "TEXT";
			}
		}
		return "TEXT";
	}

	public static void setCacheApi(String apiClass) throws Exception {
		Class clazz = Class.forName(apiClass.trim());
		Constructor con = clazz.getConstructor(Boolean.class);
		AbsCacheApi api = (AbsCacheApi) con.newInstance(true);
		StoredCache.setApi(api);
	}

	public synchronized void reset() {
		consoleVariables.putAndReturnSlef("QueryCursorStart", DEFAULT_QueryCursorStart);
		consoleVariables.putAndReturnSlef("QueryMaxRows", DEFAULT_QueryMaxRows);
		consoleVariables.putAndReturnSlef("AutoCommit", DEFAULT_AutoCommit);
		consoleVariables.putAndReturnSlef("CommandEnd", DEFAULT_CommandEnd);
		consoleVariables.putAndReturnSlef("CommandCancel", DEFAULT_CommandCancel);
		consoleVariables.putAndReturnSlef("ColumnWidth", DEFAULT_ColumnWidth);
		consoleVariables.putAndReturnSlef("ColumnCut", DEFAULT_ColumnCut);
		consoleVariables.putAndReturnSlef("ShowResultHeader", DEFAULT_ShowResultHeader);
		consoleVariables.putAndReturnSlef("ShowResultBody", DEFAULT_ShowResultBody);
		consoleVariables.putAndReturnSlef("ShowResultFooter", DEFAULT_ShowResultFooter);
		consoleVariables.putAndReturnSlef("DateTimeFormat", DEFAULT_DateTimeFormat);
		consoleVariables.putAndReturnSlef("DateFormat", DEFAULT_DateFormat);
		consoleVariables.putAndReturnSlef("TimeFormat", DEFAULT_TimeFormat);
		consoleVariables.putAndReturnSlef("Name", DEFAULT_Name);
		consoleVariables.putAndReturnSlef("WriteHistory", DEFAULT_WriteHistory);
		consoleVariables.putAndReturnSlef("CommandHistory", DEFAULT_CommandHistory);
		consoleVariables.putAndReturnSlef("ShowTaskLog", DEFAULT_ShowTaskLog);
		consoleVariables.putAndReturnSlef("ExportMaxFileSize", DEFAULT_ExportMaxFileSize);
		consoleVariables.putAndReturnSlef("ExportCursorStart", DEFAULT_ExportCursorStart);
		consoleVariables.putAndReturnSlef("ExportMaxRows", DEFAULT_ExportMaxRows);
		consoleVariables.putAndReturnSlef("ExportTimer", DEFAULT_ExportTimer);
		consoleVariables.putAndReturnSlef("ExportOrderBy", DEFAULT_ExportOrderBy);
		consoleVariables.putAndReturnSlef("ExportUpperOrLowerCase", DEFAULT_ExportUpperOrLowerCase);
		consoleVariables.putAndReturnSlef("ExportSplit", DEFAULT_ExportSplit);
		consoleVariables.putAndReturnSlef("ImportSplit", DEFAULT_ImportSplit);
		consoleVariables.putAndReturnSlef("BatchSize", DEFAULT_BatchSize);
		consoleVariables.putAndReturnSlef("Quote", DEFAULT_Quote);
		consoleVariables.putAndReturnSlef("CallStoredProcedureFun", DEFAULT_CallStoredProcedureFun);
		consoleVariables.putAndReturnSlef("ScriptEngine", DEFAULT_ScriptEngine);

		consoleVariables.putAndReturnSlef("JdbcCipherAesKey", DEFAULT_Jdbc_Cipher_AesKey);
		consoleVariables.putAndReturnSlef("ScriptCipherAesKey", DEFAULT_Script_Cipher_AesKey);
		consoleVariables.putAndReturnSlef("ConfigCipherAesKey", DEFAULT_Config_Cipher_AesKey);
	}

	public String converToSqlFromMap(String schema, String tableName, List<String> columns) {
		int columnSize = columns.size();
		StringBuffer sql = new StringBuffer();
		String fullTableName = schema == null ? tableName : String.format("%s.%s", schema, tableName);
		sql.append(String.format("INSERT INTO %s ", quote(fullTableName)));
		sql.append("(");
		for (int i = 0; i < columnSize; i++) {
			String column = columns.get(i);
			sql.append(quote(column));
			if (i < columnSize - 1) {
				sql.append(",");
			}
		}
		sql.append(") VALUES (");
		for (int i = 0; i < columnSize; i++) {
			String column = columns.get(i);
			sql.append(":" + column);
			if (i < columnSize - 1) {
				sql.append(",");
			}
		}
		sql.append(")");
		return sql.toString();
	}

	public Object conver(String tableObjectFolder, String javaType, String encryptCell) throws Exception {
		String _aesKey = consoleVariables.getString("JdbcCipherAesKey");

		Object objectValue = null;

		if (CommonTools.isBlank(encryptCell)) {
			return "";
		}

		boolean useEncrypt = !CommonTools.isBlank(_aesKey);

		String cell = null;

		if (useEncrypt) {
			byte[] by = Base64Tools.decode(encryptCell);
			cell = new String(CipherTools.AESDecrypt(_aesKey, by),BaseFile.CHARSET);
		} else {
			cell = encryptCell;
		}

		if (cell.toUpperCase().equals("NULL")) {
			return null;
		}
		
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(consoleVariables.getString("DateTimeFormat"));
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(consoleVariables.getString("DateFormat"));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(consoleVariables.getString("TimeFormat"));
// 		SimpleDateFormat dateTimeFormat = new SimpleDateFormat(consoleVariables.getString("DateTimeFormat"));
// 		SimpleDateFormat dateFormat = new SimpleDateFormat(consoleVariables.getString("DateFormat"));
// 		SimpleDateFormat timeFormat = new SimpleDateFormat(consoleVariables.getString("TimeFormat"));

// 		if (javaType.equals(java.lang.String.class.getName())) {
//             objectValue = unescapeCsv(cell);
// 		} else if (javaType.equals(java.util.Date.class.getName())) {
// 			objectValue = new java.util.Date(dateTimeFormat.parse(cell).getTime());
// 		} else if (javaType.equals(java.sql.Date.class.getName())) {
// 			objectValue = new java.sql.Date(dateFormat.parse(cell).getTime());
// 		} else if (javaType.equals(java.sql.Timestamp.class.getName())) {
// 			objectValue = new java.sql.Timestamp(dateTimeFormat.parse(cell).getTime());
// 		} else if (javaType.equals(java.sql.Time.class.getName())) {
// 			objectValue = new java.sql.Time(dateTimeFormat.parse(cell).getTime());
// 		} 
		
        if (javaType.equals(java.lang.String.class.getName())) {
            objectValue = unescapeCsv(cell);
        } else if (javaType.equals(java.util.Date.class.getName())) {
            LocalDateTime ldt = LocalDateTime.parse(cell, dateTimeFormatter);
            objectValue = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        } else if (javaType.equals(java.sql.Date.class.getName())) {
            LocalDate ld = LocalDate.parse(cell, dateFormatter);
            objectValue = java.sql.Date.valueOf(ld);
        } else if (javaType.equals(java.sql.Timestamp.class.getName())) {
            LocalDateTime ldt = LocalDateTime.parse(cell, dateTimeFormatter);
            objectValue = java.sql.Timestamp.valueOf(ldt);
        } else if (javaType.equals(java.sql.Time.class.getName())) {
            LocalTime lt = LocalTime.parse(cell, timeFormatter);
            objectValue = java.sql.Time.valueOf(lt);
        }else if (javaType.equals(java.math.BigDecimal.class.getName())) {
			objectValue = Double.parseDouble(cell);
		} else if (javaType.equals(java.math.BigInteger.class.getName())) {
			objectValue = Long.parseLong(cell);
		} else if (javaType.equals(java.lang.Short.class.getName())) {
			objectValue = Short.parseShort(cell);
		} else if (javaType.equals(java.lang.Integer.class.getName())) {
			objectValue = Integer.parseInt(cell);
		} else if (javaType.equals(java.lang.Long.class.getName())) {
			objectValue = Long.parseLong(cell);
		} else if (javaType.equals(java.lang.Float.class.getName())) {
			objectValue = Float.parseFloat(cell);
		} else if (javaType.equals(java.lang.Double.class.getName())) {
			objectValue = Double.parseDouble(cell);
		} else if (javaType.equals(java.sql.Clob.class.getName())) {
			String objPath = String.format("%s/%s", tableObjectFolder, cell);
			DataObject dob = new DataObject(_aesKey, objPath);
			objectValue = (byte[]) dob.getData();
		} else if (javaType.equals(java.sql.Blob.class.getName())) {
			String objPath = String.format("%s/%s", tableObjectFolder, cell);
			DataObject dob = new DataObject(_aesKey, objPath);
			objectValue = (byte[]) dob.getData();
		} else if (javaType.equals("[B")) {
			String objPath = String.format("%s/%s", tableObjectFolder, cell);
			DataObject dob = new DataObject(_aesKey, objPath);
			objectValue = (byte[]) dob.getData();
		} else {
			//Not support the type.
			objectValue = null;
		}
		return objectValue;
	}

	public static String getJarPath() {
		return CommonTools.getJarPath(ScriptConsole.class);
	}
	
	public void exportTo(String _lastSelectCommand,File folder,String filePrefix) throws Exception {
		exportTo(_lastSelectCommand,String.format("%s/%s",folder.getAbsolutePath(),filePrefix));
	}	
	
	public void exportTo(File folder,String filePrefix) throws Exception {
		exportTo(String.format("%s/%s",folder.getAbsolutePath(),filePrefix));
	}	
	
	public synchronized void exportTo(String _lastSelectCommand,String file) throws Exception {
	    setLastSelectCommand(_lastSelectCommand);
		Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
		if (CommonTools.isBlank(lastSelectCommand)) {
			throw new Exception("ERROR: Invalid blank select command");
		}
		Integer exportCursorStart = consoleVariables.getInteger("ExportCursorStart", DEFAULT_ExportCursorStart);
		Integer exportMaxRows = consoleVariables.getInteger("ExportMaxRows", DEFAULT_ExportMaxRows);
		CacheArray cacheArray = new CacheArray();
		cacheArray.filter(getCacheArrayFilterForExport(showTaskLog, file));
		getCacheDriverExecutor(true).find(exportCursorStart, exportMaxRows, lastSelectCommand, cacheArray);
	}	

	public synchronized void exportTo(String file) throws Exception {
		Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
		if (CommonTools.isBlank(lastSelectCommand)) {
			throw new Exception("ERROR: Invalid blank select command");
		}
		Integer exportCursorStart = consoleVariables.getInteger("ExportCursorStart", DEFAULT_ExportCursorStart);
		Integer exportMaxRows = consoleVariables.getInteger("ExportMaxRows", DEFAULT_ExportMaxRows);
		CacheArray cacheArray = new CacheArray();
		cacheArray.filter(getCacheArrayFilterForExport(showTaskLog, file));
		getCacheDriverExecutor(true).find(exportCursorStart, exportMaxRows, lastSelectCommand, cacheArray);
	}
	
	public CacheArrayFilter getCacheArrayFilterForExport(boolean showTaskLog, String file) {
	    return getCacheArrayFilterForExport(showTaskLog,file,null,null);
	}
	
	public void exportTo(String _lastSelectCommand,File folder,String filePrefix,Runnable completedRun,Runnable terminatedRun) throws Exception {
	    setLastSelectCommand(_lastSelectCommand);
		Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
		if (CommonTools.isBlank(lastSelectCommand)) {
			throw new Exception("ERROR: Invalid blank select command");
		}
		Integer exportCursorStart = consoleVariables.getInteger("ExportCursorStart", DEFAULT_ExportCursorStart);
		Integer exportMaxRows = consoleVariables.getInteger("ExportMaxRows", DEFAULT_ExportMaxRows);
		CacheArray cacheArray = new CacheArray();
		String file = String.format("%s/%s",folder.getAbsolutePath(),filePrefix);
		cacheArray.filter(getCacheArrayFilterForExport(showTaskLog, file,completedRun,terminatedRun));
		getCacheDriverExecutor(true).find(exportCursorStart, exportMaxRows, lastSelectCommand, cacheArray);
	}		

	public CacheArrayFilter getCacheArrayFilterForExport(boolean showTaskLog, String file,Runnable completedRun,Runnable terminatedRun) {
		Integer exportMaxFileSize = consoleVariables.getInteger("ExportMaxFileSize", DEFAULT_ExportMaxFileSize);
		Long exportTimer = consoleVariables.getLong("ExportTimer", DEFAULT_ExportTimer);
		String exportSplit = consoleVariables.getString("ExportSplit", DEFAULT_ExportSplit);
		final java.util.Date beginTime = new java.util.Date();
		
		return new CacheArrayFilter(0L,exportTimer) {
			int outIndex = 0;
			int exportedIndex = 0;
			DiskFile csvFile = null;
			File outFile = new File(file);
			String outFolder = outFile.getParent();
			String outFileName = exportUpperOrLowerCase(outFile.getName().replaceFirst("\\..*", ""));
			final List<String> columns = new ArrayList<String>();
			final StringBuffer header = new StringBuffer();
			
			@Override
			public void executeBatch(Integer index,List batch) {
                if (showTaskLog) {
    				System.out.println(String.format("Exporting table %s (%s -> %s).", outFileName,index,batch.size()));
    			}
    			
			    for(Object o : batch){
    				try {
    					String tableObjectFolder = String.format("%s/%s_objects", outFolder, outFileName);
    					Map<String, Object> cacheRow = (Map<String, Object>) o;
    					if (csvFile == null) {
    						String outIndexFormat = String.format("%09d", outIndex);
    						csvFile = new DiskFile(String.format("%s/%s.%s.csv", outFolder, outFileName, outIndexFormat));
    						if (csvFile.exists()) {
    							csvFile.delete();
    						}
    						if (showTaskLog) {
    							System.out.println(String.format("Exporting table %s", outFileName));
    						}
    						columns.addAll(cacheRow.keySet());
    						int columsSize = columns.size();
    						for (int i = 0; i < columsSize; i++) {
    							header.append(exportUpperOrLowerCase(columns.get(i)));
    							if (i < columsSize - 1)
    								header.append(exportSplit);
    						}
    						header.append(System.lineSeparator());
    						csvFile.manualOpen(true); 
    						csvFile.manualWrite(header.toString());
    					}
    					if (index >= 0) {
    						StringBuffer cells = new StringBuffer();
    						int columsSize = columns.size();
    						for (int j = 0; j < columsSize; j++) {
    							String column = columns.get(j);
    							Object objValue = cacheRow.get(column);
    							String value = formatForExport(tableObjectFolder, objValue);
    							cells.append(value);
    							if (j < columsSize - 1)
    								cells.append(exportSplit);
    						}
    						cells.append(System.lineSeparator());
    						Path path = Paths.get(csvFile.getPath());
    						long sizeInBytes = Files.size(path);
    						long sizeInMb = sizeInBytes / (1024 * 1024);
    						if (sizeInMb >= exportMaxFileSize) {
    							outIndex++;
    							String outIndexFormat = String.format("%09d", outIndex);
    							
    							csvFile.manualClose();
    
    							csvFile = new DiskFile(String.format("%s/%s.%s.csv", outFolder, outFileName, outIndexFormat));
    							if (csvFile.exists()) {
    								csvFile.delete();
    							}
    							
    							csvFile.manualOpen(true); 
    							csvFile.manualWrite(header.toString());
    						}
    						csvFile.manualWrite(cells.toString());
    					}
    
    				} catch (Exception e) {
    					log.error(e.getMessage(), e);
    				}
			    }
			}

			@Override
			public void execute(Integer index, Object o) {
			    
			}

			@Override
			public void completed(Integer size) {
				try {
					String outIndexFormat = String.format("%09d", 0);
				    if(csvFile != null){
				        csvFile.manualClose();
				    }
					if (csvFile == null) {
					    csvFile = new DiskFile(String.format("%s/%s.%s.csv", outFolder, outFileName, outIndexFormat));
						csvFile.write("", false);
					}
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
				if (showTaskLog) {
					long timeCostMs = new java.util.Date().getTime() - beginTime.getTime();
					System.out
							.println(String.format("Table %s %s rows exported (%sms)", outFileName, size, timeCostMs));
				}
				
				if(completedRun != null) completedRun.run();
			}
			
			@Override
			public void terminated(){
			    if(csvFile != null){
			        try{
				        csvFile.manualClose();
			        }catch(Exception e){
			            log.error(e.getMessage(), e);
			        }
				}
				
				if(terminatedRun != null) terminatedRun.run();
			}
		};
	}

	public String format(Object objValue) {
		String format = "%" + consoleVariables.getInteger("ColumnWidth", DEFAULT_ColumnWidth) + "s";
		String value = formatObjectValue(false, false, objValue, String.format("%s/tmp", getJarPath()));
		return String.format(format, columnCut(value));
	}

	public String formatForExport(Object objValue) {
		return formatObjectValue(false, true, objValue, String.format("%s/tmp", getJarPath()));
	}

	public String formatForExport(String tableObjectFolder, Object objValue) {
		String _aesKey = consoleVariables.getString("JdbcCipherAesKey");
		boolean useEncrypt = !CommonTools.isBlank(_aesKey);
		return formatObjectValue(useEncrypt, true, objValue, tableObjectFolder);
	}

	public String formatObjectValue(boolean useEncrypt, boolean single, Object objValue,
			String tableObjectFolder) {
// 		SimpleDateFormat dateTimeFormat = new SimpleDateFormat(consoleVariables.getString("DateTimeFormat"));
// 		SimpleDateFormat dateFormat = new SimpleDateFormat(consoleVariables.getString("DateFormat"));
// 		SimpleDateFormat timeFormat = new SimpleDateFormat(consoleVariables.getString("TimeFormat"));
		
		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(consoleVariables.getString("DateTimeFormat"));
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(consoleVariables.getString("DateFormat"));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(consoleVariables.getString("TimeFormat"));
        
		String _aesKey = consoleVariables.getString("JdbcCipherAesKey");
		String value = null;
		if (objValue == null) {
			value = "NULL";
		} else if (objValue instanceof byte[]) {
			try {
				String mappingName = String.format("object_%s", CommonTools.generateId(32));
				byte[] by = (byte[]) objValue;
				String objPath = String.format("%s/%s", tableObjectFolder, mappingName);
				DataObject dob = new DataObject(_aesKey, objPath);
				dob.setObject(by);
				value = mappingName;
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				return null;
			}
		} else if (objValue instanceof java.sql.Clob) {
			try {
				String mappingName = String.format("object_%s", CommonTools.generateId(32));
				byte[] by = toByteArray((Clob) objValue);
				String objPath = String.format("%s/%s", tableObjectFolder, mappingName);
				DataObject dob = new DataObject(_aesKey, objPath);
				dob.setObject(by);
				value = mappingName;
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				return null;
			}
		} else if (objValue instanceof java.sql.Blob) {
			try {
				String mappingName = String.format("object_%s", CommonTools.generateId(32));
				byte[] by = toByteArray((Blob) objValue);
				String objPath = String.format("%s/%s", tableObjectFolder, mappingName);
				DataObject dob = new DataObject(_aesKey, objPath);
				dob.setObject(by);
				value = mappingName;
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				return null;
			}
		}
		
// 		else if (objValue instanceof java.sql.Timestamp) {
// 			value = dateTimeFormat.format((java.sql.Timestamp) objValue);
// 		} else if (objValue instanceof java.util.Date) {
// 			value = dateTimeFormat.format((java.util.Date) objValue);
// 		} else if (objValue instanceof java.sql.Date) {
// 			value = dateFormat.format((java.sql.Date) objValue);
// 		} else if (objValue instanceof java.sql.Time) {
// 			value = timeFormat.format((java.sql.Time) objValue);
// 		} 
       if (objValue instanceof java.sql.Timestamp) {
            value = ((java.sql.Timestamp) objValue).toLocalDateTime().format(dateTimeFormatter);
        } else if (objValue instanceof java.util.Date) {
            value = ((java.util.Date) objValue).toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(dateTimeFormatter);
        } else if (objValue instanceof java.sql.Date) {
            value = ((java.sql.Date) objValue).toLocalDate().format(dateFormatter);
        } else if (objValue instanceof java.sql.Time) {
            value = ((java.sql.Time) objValue).toLocalTime().format(timeFormatter);
        }		
		else if (objValue instanceof Boolean) {
			value = objValue.equals(true) ? "1" : "0";
		} else if (objValue instanceof String) {
			if (single)
				value = toSingle((String) objValue);
			else
				value = (String) objValue;
		} else {
			value = objValue + "";
		}
		if (!useEncrypt) {
			return value;
		}
		try {
			byte[] encData = CipherTools.AESEncrypt(_aesKey, value.getBytes(BaseFile.CHARSET));
			return new String(Base64Tools.encode(encData));
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	public byte[] toByteArray(Blob blobValue) {
		try {
			return blobValue.getBinaryStream().readAllBytes();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

	public byte[] toByteArray(Clob clobValue) {
		try {
			int length = Long.bitCount(clobValue.length());
			byte[] array = new byte[length];
			InputStream in = clobValue.getAsciiStream();
			int offset = 0;
			int n;
			do
				n = in.read(array, offset, length - offset);
			while (n != -1);
			return array;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

	public String columnCut(String strValue) {
		if (strValue == null)
			strValue = "NULL";

		Integer columnCut = consoleVariables.getInteger("ColumnCut", DEFAULT_ColumnCut);
		if (columnCut > 0) {
			if (strValue.length() > columnCut) {
				strValue = strValue.substring(0, columnCut);
			}
		}
		return strValue;
	}

	public String toSingle(String text) {
		if (text == null)
			return null;

        return escapeCsv(text);
	}

	public void clear() {
		clear(true);
	}

	private void clear(Boolean enableSystemOut) {
		if (enableSystemOut){
		    try{
                String os = System.getProperty("os.name").toUpperCase();
                if (os.contains("WINDOWS")) {
                    new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
                } else { 
                    new ProcessBuilder("clear").inheritIO().start().waitFor();
                }
		    }catch(Exception e){
		        System.out.println(error(e));
		        log.error(e.getMessage(), e);
                System.out.print("\033[H\033[2J");
                System.out.flush();
		    }
		}
	}

	public boolean open() {
		return open(true);
	}

	public void close() {
		try {
			close(true);
		} catch (SQLException e) {
			log.error(e.getMessage(), e);
			System.out.println(error(e));
		}
	}

	public void commit() {
		try {
			commit(true);
		} catch (SQLException e) {
			log.error(e.getMessage(), e);
			System.out.println(error(e));
		}
	}

	public void rollback() {
		try {
			rollback(true);
		} catch (SQLException e) {
			log.error(e.getMessage(), e);
			System.out.println(error(e));
		}
	}

	public void abort() {
		try {
			abort(true);
		} catch (SQLException e) {
			log.error(e.getMessage(), e);
			System.out.println(error(e));
		}
	}

	private void abort(Boolean enableSystemOut) throws SQLException {
		if (cacheDriverExecutor == null) {
			if (enableSystemOut)
				System.out.println("ERROR: Connection is null");
		} else {
			cacheDriverExecutor.abort();
			cacheDriverExecutor = null;
			if (enableSystemOut)
				System.out.println("Aborted connection");
		}
	}

	private void rollback(Boolean enableSystemOut) throws SQLException {
		if (cacheDriverExecutor == null) {
			if (enableSystemOut)
				System.out.println("ERROR: Failed rollback, connection is null.");
		} else {
			cacheDriverExecutor.rollback();
			if (enableSystemOut)
				System.out.println("Rollback completed");
		}
	}

	private void commit(Boolean enableSystemOut) throws SQLException {
		if (cacheDriverExecutor == null) {
			if (enableSystemOut)
				System.out.println("ERROR: Failed commit, connection is null.");
		} else {
			cacheDriverExecutor.commit();
			if (enableSystemOut)
				System.out.println("Commit completed");
		}
	}

	private void close(Boolean enableSystemOut) throws SQLException {
		if (cacheDriverExecutor == null) {
			if (enableSystemOut)
				System.out.println("ERROR: Connection is null");
		} else {
			cacheDriverExecutor.close();
			cacheDriverExecutor = null;
			if (enableSystemOut)
				System.out.println("Closed connection");
		}
	}

	private boolean open(Boolean enableSystemOut) {
		if (cacheDriverExecutor == null || cacheDriverExecutor.isClosed()) {
			firstOpen = false;
			try {
			    
			    if(ScriptConsole.datasource == null) ScriptConsole.datasource = getDataSource(); 
			    
				Connection connection = ScriptConsole.datasource.getConnection();
				connection.setAutoCommit(consoleVariables.getBoolean("AutoCommit", DEFAULT_AutoCommit));
				cacheDriverExecutor = new CacheDriverExecutor(connection);
				if (enableSystemOut)
					System.out.println(String.format("Connected (AutoCommit=%s)",connection.getAutoCommit()+""));

				return true;
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				System.out.println(e.getMessage());
				if (enableSystemOut)
					System.out.println("Disconnected");

				return false;
			}
		}

		return true;
	}

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
	
	protected String executeJdbcBatch(String _batchFile) throws Exception {
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
						if (command.indexOf("-") != 0) {
							System.out.println(
									String.format("%s) %s",row,command));
									
							executeViaJdbc(true, command);
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

	public Object evalScript(String script) throws Exception {
		return jsPlusContext.evalScript(script);
	}
	
	public boolean executeViaGhost(String commands){
	    return executeViaGhost(true,commands);
	}	
	
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
                        System.exit(0);
                        return true;
                    }
			    }
			    if(cacheDriverExecutor == null)
				    System.exit(0);
				    
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
	
	public boolean executeViaCmd(boolean enableSystemOut,String commands){
	    return executeViaCmd(enableSystemOut,commands,null,null);
	}
	
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
                        System.exit(0);
                        return true;
                    }
			    }
			    if(cacheDriverExecutor == null)
				    System.exit(0);
				    
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
                        System.exit(0);
                        return true;
                    }
			    }
			    if(cacheDriverExecutor == null)
				    System.exit(0);
				    
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
    
 	public boolean execute(String commands) {
 	    if(isUseScriptEnv()) return executeViaScript(commands);
 	    
 	    if(isUseJdbcEnv()) return executeViaJdbc(commands);
 	    
 	    if(isUseCmdEnv()) return executeViaCmd(commands);
 	    
 	    return false;
	}   

	public boolean executeViaJdbc(String commands) {
		return executeViaJdbc(true, commands);
	}
	
	public boolean executeViaCmd(String commands) {
		return executeViaCmd(true, commands);
	}
	
	public boolean executeViaCmd(Boolean enableSystemOut,String commands,Runnable successfullyRun,Runnable faiiledRun) {
		return executeViaCmd(enableSystemOut, commands,successfullyRun,faiiledRun);
	}	

	public boolean executeViaJdbc(Boolean enableSystemOut,String commands) {
	    String commandEnd = consoleVariables.getString("CommandEnd",DEFAULT_CommandEnd);
	    String executeCommand = commands.toString().replaceFirst("(" + commandEnd + "){1,}[\s]*$","").trim();
	    
		if (executeCommand.trim().equals("")) {
			System.out.println("ERROR: Invalid blank command");
			return false;
		}
		
		boolean isCancel = isCommandCancel(executeCommand);
	    
	    if(isCancel) return true;
	    
        Boolean writeHistory = consoleVariables.getBoolean("WriteHistory",DEFAULT_WriteHistory);
        String commandHistory = consoleVariables.getString("CommandHistory", DEFAULT_CommandHistory);
		try {
		    
		    log.info(executeCommand);

		    if(writeHistory && enableSystemOut && executeCommand.indexOf(commandHistory) != 0){
		        if(!CommonTools.isBlank(executeCommand))
		            addHistory(executeCommand.trim());
		    }
		    
			if (executeCommand.toUpperCase().equals("EXIT")) {
				if(cacheDriverExecutor != null){
                    if(!cacheDriverExecutor.isClosed()){
                        System.out.println("ERROR: The connection is in use, please close the connection first.");
                    }
				}
				
				if(cacheDriverExecutor == null)
				    System.exit(0);
				    
				return true;
			}  
			
			
            if(executeCommand.toUpperCase().equals("SCRIPT")){
		        setUseScriptEnv();
		        consoleVariables.putAndReturnSlef("CommandEnd","/");
		        if(enableSystemOut){
		            System.out.println("Changed CommandEnd to '/'");
		        }
		        set("Name",displayEngineName); 
		    } else if(executeCommand.toUpperCase().equals("CMD")){
		        setUseCmdEnv();
		        consoleVariables.putAndReturnSlef("CommandEnd","/");
		        if(enableSystemOut){
		            System.out.println("Changed CommandEnd to '/'");
		        }
		        set("Name","cmd"); 
		    }else if(executeCommand.toUpperCase().equals("GHOST")){
    	        setUseGhostEnv();
    	        consoleVariables.putAndReturnSlef("CommandEnd","/");
    	        set("Name","ghost");
    	        if(enableSystemOut){
		            System.out.println("Changed CommandEnd to '/'");
    	        }
        	}else if(executeCommand.indexOf(commandHistory) == 0){
			    String hiscmd = executeCommand.replaceFirst("^" + commandHistory,"");
		        showHistory(hiscmd);
		    }else if(executeCommand.toUpperCase().equals("CLEAR")){
		        clear(enableSystemOut);
		    }else if (executeCommand.toUpperCase().equals("COMMIT")) {
                commit(enableSystemOut);
			} else if (executeCommand.toUpperCase().equals("ROLLBACK")) {
				rollback(enableSystemOut);
			} else if (executeCommand.toUpperCase().equals("CLOSE")) {
				close(enableSystemOut);
			} else if (executeCommand.toUpperCase().equals("ABORT")) {
				abort(enableSystemOut);
			}else if (executeCommand.toUpperCase().equals("OPEN")) {
				open(enableSystemOut);
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
			    open(enableSystemOut);
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
                        Method method = JdbcConsole.class.getMethod(fun,List.class);
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
			} else {
			    try{
			        Integer queryCursorStart = consoleVariables.getInteger("QueryCursorStart", DEFAULT_QueryCursorStart);
			        Integer queryMaxRows = consoleVariables.getInteger("QueryMaxRows",DEFAULT_QueryMaxRows);
    				if (executeCommand.toUpperCase().matches(SELECT_REGEX)) {
    				    open(enableSystemOut);
    				    lastSelectCommand = executeCommand;
    				    CacheArray cacheArray = new CacheArray();
    				    cacheArray.filter(getCacheArrayFilter(enableSystemOut));
    				    cacheDriverExecutor.find(queryCursorStart,queryMaxRows,executeCommand,cacheArray);
    				} else if (executeCommand.toUpperCase().matches(CALL_REGEX)) {
    				    open(enableSystemOut);
    				    lastSelectCommand = executeCommand;
    				    String callStoredProcedureFun = consoleVariables.getString("CallStoredProcedureFun",DEFAULT_CallStoredProcedureFun);
    				    if(callStoredProcedureFun.equals("callAndReturnList")){
        				    CacheArray cacheArray = new CacheArray();
        				    cacheArray.filter(getCacheArrayFilter(enableSystemOut));
        				    cacheDriverExecutor.callAndReturnList(queryCursorStart,queryMaxRows,executeCommand,cacheArray);  
    				    }
    					if(callStoredProcedureFun.equals("callAndReturnRows")){
        					int callRows = cacheDriverExecutor.callAndReturnRows(executeCommand);
        					setIntoValue(callRows);
        					if(enableSystemOut) System.out.println(callRows);
    					}
    					if(callStoredProcedureFun.equals("callAndReturnBoolean")){
        					Boolean callBoolean = cacheDriverExecutor.callAndReturnBoolean(executeCommand);
        					setIntoValue(callBoolean);
        					if(enableSystemOut) System.out.println(callBoolean);
    					}
    				}else {
    				    open(enableSystemOut);
    				    lastSelectCommand = executeCommand;
    				    final java.util.Date beginTime = new java.util.Date();
    					int rows = cacheDriverExecutor.execute(executeCommand);
    					long timeCostMs = new java.util.Date().getTime() - beginTime.getTime();
    					setIntoValue(rows);
    					if(enableSystemOut) {
    					    System.out.println(String.format("%s row executed (%sms)",rows,timeCostMs));
    					}
    				}
			    }catch(Exception ee){
			        setIntoValue(null);
			        throw ee;
			    }
			}
		} catch (Exception e) {
            System.out.println(error(e));
            log.info(executeCommand);
            log.error(e.getMessage(),e);
            if(writeHistory && enableSystemOut && executeCommand.indexOf(commandHistory) != 0){
                addHistory(" <INVALID");
            }
            return false;
		} finally{
    		if(writeHistory && enableSystemOut && executeCommand.indexOf(commandHistory) != 0){
    	    	addHistory(System.lineSeparator());
    		}
		}
		return true;
	}

	public CacheArrayFilter getCacheArrayFilter(Boolean enableSystemOut) {
		return new CacheArrayFilter(1L,1L) {
			final java.util.Date beginTime = new java.util.Date();
			final List<String> columns = new ArrayList<String>();
			String format = "%" + consoleVariables.getInteger("ColumnWidth", DEFAULT_ColumnWidth) + "s";

			@Override
			public void execute(Integer index, Object o) {
				if (index == 0) {
					StringBuffer header = new StringBuffer();
					boolean showResultHeader = consoleVariables.getBoolean("ShowResultHeader",
							DEFAULT_ShowResultHeader);
					Map<String, Object> firstRow = (Map<String, Object>) o;
					columns.addAll(firstRow.keySet());
					int columsSize = columns.size();
					for (int i = 0; i < columsSize; i++) {
						header.append(String.format(format, columns.get(i)));
						if (i < columsSize - 1)
							header.append("\t");
					}
					if(enableSystemOut){
					    try{
					        Thread.sleep(500);
					        System.out.println();
					    }catch(Exception e){
					        log.error(e.getMessage(),e);
					    }
					}
					if (enableSystemOut && showResultHeader) {
						System.out.println(header);
					}
				}
				if (index >= 0) {
					int columsSize = columns.size();
					StringBuffer body = new StringBuffer();
					boolean showResultBody = consoleVariables.getBoolean("ShowResultBody", DEFAULT_ShowResultBody);
					Map<String, Object> row = (Map<String, Object>) o;
					for (int i = 0; i < columsSize; i++) {
						String column = columns.get(i);
						Object objValue = row.get(column);
						String value = format(objValue);
						if (index == 0 && i == 0) {
							setIntoValue(objValue);
						}
						body.append(value);
						if (i < columsSize - 1)
							body.append("\t");
					}
					if (enableSystemOut && showResultBody) {
						System.out.println(body);
					}
				}
			}

			@Override
			public void completed(Integer size) {
				boolean showResultFooter = consoleVariables.getBoolean("ShowResultFooter", DEFAULT_ShowResultFooter);
				if (enableSystemOut && showResultFooter) {
					long timeCostMs = new java.util.Date().getTime() - beginTime.getTime();
					String footer = String.format("%s rows selected (%sms)", size, timeCostMs);
					System.out.println(System.lineSeparator());
					System.out.println(footer);
					Integer queryMaxRows = consoleVariables.getInteger("QueryMaxRows",DEFAULT_QueryMaxRows);
					System.out.println(String.format("Current QueryMaxRows=%s",queryMaxRows));
				}
			}
		};
	}

	public void checkHistory() {
		try {
			long pid = ProcessHandle.current().pid();
			String jarPath = getJarPath();
			String historyLog = String.format("%s/history/history_%s.log", getJsPlusPath(), pid);
			File hisFile = new File(historyLog);
			if (hisFile.exists()) {
				SimpleDateFormat sf = new SimpleDateFormat("yyyy.MM.dd_HH:mm:ss.SSS");
				File backupFile = new File(historyLog + ".BAK_" + sf.format(new java.util.Date()));
				hisFile.renameTo(backupFile);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	public void addHistory(String command) {
		try {
			long pid = ProcessHandle.current().pid();
			String jarPath = getJarPath();
			String historyLog = String.format("%s/history/history_%s.log", getJsPlusPath(), pid);
			FileTools.write(new File(historyLog), command, true);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	public void showHistory(String hiscmd) {
		try {
			long pid = ProcessHandle.current().pid();
			int showLineNum = 0;
			if (!CommonTools.isBlank(hiscmd)) {
				showLineNum = Integer.parseInt(hiscmd.trim());
			}
			String commandHistory = consoleVariables.getString("CommandHistory", DEFAULT_CommandHistory);
			String jarPath = getJarPath();
			String historyLog = String.format("%s/history/history_%s.log", getJsPlusPath(), pid);
			if (!new File(historyLog).exists()) {
				System.out.println("No history log");
				return;
			}
			String hiscmdLines = FileTools.text(historyLog);
			List<String> lines = Arrays.asList(hiscmdLines.split(System.lineSeparator()));
			int size = lines.size();
			for (int i = 0; i < size; i++) {

				if (showLineNum == 0) {
					String line = lines.get(i);
					System.out.println(String.format("%s) %s", i + 1, line));
				}
				if (showLineNum > 0 && i == (showLineNum - 1)) {
					String line = lines.get(showLineNum - 1);
					System.out.println(String.format("%s) %s", i + 1, line));
					break;
				}
				if (showLineNum < 0) {
					int beginIndex = size + showLineNum;
					beginIndex = beginIndex < 0 ? 0 : beginIndex;
					if (i >= beginIndex) {
						String line = lines.get(i);
						System.out.println(String.format("%s) %s", i + 1, line));
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			System.out.println("ERROR: Invalid history command");
		}
	}

	public String toResultList(List<Map<String, Object>> list) {
		String format = "%" + consoleVariables.getInteger("ColumnWidth", DEFAULT_ColumnWidth) + "s";
		List<String> columns = null;
		StringBuffer body = new StringBuffer();
		StringBuffer header = new StringBuffer();
		int listSize = list.size();
		int columsSize = 0;
		if (listSize > 0) {
			Map<String, Object> firstRow = list.get(0);
			columns = new ArrayList<String>(firstRow.keySet());
			columsSize = columns.size();
			for (int i = 0; i < columsSize; i++) {
				header.append(String.format(format, columns.get(i)));
				if (i < columsSize - 1)
					header.append("\t");
			}
		}
		for (int i = 0; i < listSize; i++) {
			Map<String, Object> row = list.get(i);
			for (int j = 0; j < columsSize; j++) {
				String column = columns.get(j);
				Object objValue = row.get(column);
				String value = format(objValue);
				body.append(value);
				if (j < columsSize - 1)
					body.append("\t");
			}
			if (i < listSize - 1)
				body.append(System.lineSeparator());
		}
		String footer = String.format("%s rows selected", listSize);

		boolean showResultHeader = consoleVariables.getBoolean("ShowResultHeader", DEFAULT_ShowResultHeader);
		boolean showResultBody = consoleVariables.getBoolean("ShowResultBody", DEFAULT_ShowResultBody);
		boolean showResultFooter = consoleVariables.getBoolean("ShowResultFooter", DEFAULT_ShowResultFooter);

		StringBuffer result = new StringBuffer();
		if (showResultHeader) {
			result.append(header);
			result.append(System.lineSeparator());
		}
		if (showResultBody) {
			result.append(body);
			result.append(System.lineSeparator());
		}
		if (showResultFooter) {
			result.append(footer);
		}
		return result.toString();
	}

	public boolean isCommandEnd(String command){
	    String commandEnd = consoleVariables.getString("CommandEnd",DEFAULT_CommandEnd);
		return command.matches(".*("+commandEnd+"){1,}[\s]*$");
	}

	public boolean isCommandCancel(String command){
	    String commandCancel = consoleVariables.getString("CommandCancel",DEFAULT_CommandCancel);
		return command.matches(".*("+commandCancel+"){1,}[\s]*$");
	}

	public static Map<String, Object> parseArgs(String[] args) {

		if (args == null)
			return null;

		Map<String, Object> params = null;
		List<String> argsArray = Arrays.asList(args);
		for (Iterator<String> iterator = argsArray.iterator(); iterator.hasNext();) {

			if (params == null)
				params = new HashMap<String, Object>();

			String arg = iterator.next();
			boolean isKeyValueArg = arg.matches("^-[^-]+$");
			boolean isKeyArg = arg.matches("^--[^-]+$");
			if (isKeyValueArg) {
				String key = arg.replaceFirst("^-", "").trim();
				String value = iterator.next().trim();
				params.put(key, value);
			}
			if (isKeyArg) {
				String key = arg.replaceFirst("^--", "").trim();
				params.put(key, "true");
			}
		}
		return params;
	}
	
	protected void task() throws Exception {
        String task = consoleVariables.getString("Task", DEFAULT_Task);
		Long taskTimer = consoleVariables.getMilliSeconds("TaskTimer", DEFAULT_TaskTimer);
		task(task,taskTimer);
	}	

	protected void task(String task,Long taskTimer) throws Exception {
		if (!CommonTools.isBlank(task)) {
			long timeout = new java.util.Date().getTime() - preExecuteTime;
			if (timeout >= taskTimer) {
				preExecuteTime = new java.util.Date().getTime();
				String taskFile = null;
				if(Files.exists(Paths.get(task))){
				    taskFile = task;
				}else{
				    taskFile = getJsPlusPath() + "/" + task;
				}
				String commandLines = decryptScript(new File(taskFile));
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
	
	public void setTask(String taskTimer,String task){
	    set("Task",task);
	    set("TaskTimer",taskTimer);
	}
	
	public void setTask(String task){
	    set("Task",task);
	    set("TaskTimer","0ms");
	}	
	
	public void setTaskTimer(String taskTimer){
	    set("TaskTimer",taskTimer);
	}		

	public void taskExecute() {
		taskExecuted = true;
		ExecutorService executor = Executors.newFixedThreadPool(1);
		executor.execute(new Runnable() {

			@Override
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

	public String importFrom(String schema, String tableName, String csv) throws Exception {
		InputStream inputStream = null;
		Scanner sc = null;
		int updatedRows = 0;
		try {
			String tableObjectFolder = String.format("%s/%s_objects", new File(csv).getParent(), tableName);
			Integer batchSize = consoleVariables.getInteger("BatchSize", DEFAULT_BatchSize);
			String importSplit = consoleVariables.getString("ImportSplit", DEFAULT_ImportSplit);
			batchSize = batchSize < 10 ? 10 : batchSize;
			Map<String, Object> classes = cacheDriverExecutor.getColumnClasses(tableName);
			inputStream = new FileInputStream(csv);
			sc = new Scanner(inputStream, BaseFile.CHARSET);
			int r = 0;
			String sql = null;
			final java.util.Date beginTime = new java.util.Date();
			List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
			List<String> header = new ArrayList<String>();
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (r == 0) {
                    if (line.length() > 0 && line.charAt(0) == '\uFEFF') {
                        line = line.substring(1);
                    }				    
					String[] hs = line.split(importSplit);
					for (String h : hs) {
						if (!CommonTools.isBlank(h)) {
							header.add(h.trim());
						}
					}
					sql = converToSqlFromMap(schema, tableName, header);
				}
				if (r > 0) {
					if (!CommonTools.isBlank(line)) {
						Map<String, Object> record = new ResultMap<String, Object>();
						String[] cells = line.split(importSplit);
						int cellSize = cells.length;
						for (int c = 0; c < cellSize; c++) {
							String cell = cells[c];
							String column = header.get(c);
							String javaType = (String) classes.get(column);
							if (javaType == null) {
								javaType = (String) classes.get(column.toLowerCase());
							}
							if (javaType == null) {
								javaType = (String) classes.get(column.toUpperCase());
							}
							if (javaType == null) {
								throw new Exception(
										String.format("Cloumn %s not matched the table %s", column, tableName));
							}
							record.put(column, conver(tableObjectFolder, javaType, cell));
						}
						records.add(record);
					}
				}
				int mod = r % batchSize;
				if (r > 0 && mod == 0) {
					updatedRows += cacheDriverExecutor.executeBatch(sql, records);
					records.clear();
				}
				r++;
			}

			if (records.size() > 0) {
				updatedRows += cacheDriverExecutor.executeBatch(sql, records);
				records.clear();
			}
			long timeCostMs = new java.util.Date().getTime() - beginTime.getTime();
			return String.format("Table %s %s rows inserted (%sms)", tableName, updatedRows, timeCostMs);
		} finally {
			if (sc != null)
				sc.close();
			if (inputStream != null)
				inputStream.close();
		}
	}

	public String importTables(String schema, String folder) {
		Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
		int imported = 0;
		int failed = 0;
		final java.util.Date beginTime = new java.util.Date();
		if (showTaskLog) {
			String beginTimeFormat = new SimpleDateFormat(DEFAULT_DateTimeFormat).format(beginTime);
			System.out.println(String.format("Importing tables at %s", beginTimeFormat));
		}
		StringBuffer importResult = new StringBuffer();
		File folderFile = new File(folder);
		File[] list = folderFile.listFiles();
		Arrays.sort(list);
		for (File file : list) {

			if (!file.exists() || !file.isFile())
				continue;

			String fileName = file.getName();
			if (fileName.toUpperCase().endsWith(".CSV")) {
				String tableName = fileName.replaceFirst("\\..*$", "");
				try {
					if (showTaskLog)
						System.out.println(String.format("Table %s from %s", tableName, file.getAbsolutePath()));

					String msg = importFrom(schema, tableName, file.getAbsolutePath());
					imported++;
					if (showTaskLog)
						System.out.println(msg);
				} catch (Exception e) {
					failed++;
					log.error(e.getMessage(), e);
					if (showTaskLog)
						System.out.println(e.getMessage());
				}
			}
		}
		java.util.Date completedTime = new java.util.Date();
		if (showTaskLog) {
			String completedTimeFormat = new SimpleDateFormat(DEFAULT_DateTimeFormat).format(completedTime);
			System.out.println(String.format("Imported tables at %s", completedTimeFormat));
		}
		long timeCostMs = completedTime.getTime() - beginTime.getTime();
		return String.format("%s tables imported and %s tables failed (%sms)", imported, failed, timeCostMs);
	}

	public String exportTables(String schema, String exportToFolder) {
		Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
		int exported = 0;
		int failed = 0;
		final java.util.Date beginTime = new java.util.Date();
		if (showTaskLog) {
			String beginTimeFormat = new SimpleDateFormat(DEFAULT_DateTimeFormat).format(beginTime);
			System.out.println(String.format("Exporting tables at %s", beginTimeFormat));
		}
		try {
			final List<String> tables = new ArrayList<String>();
			if (CommonTools.isBlank(schema)) {
				List<Map<String, Object>> list = cacheDriverExecutor.getAllTables();
				for (Map<String, Object> map : list) {
					String tableSchema = (String) map.get("TABLE_SCHEMA");
					String tableName = (String) map.get("TABLE_NAME");
					String schemaTableName = tableName;
					if (!CommonTools.isBlank(tableSchema)) {
						schemaTableName = String.format("%s.%s", tableSchema, tableName);
					}
					if (!tables.contains(schemaTableName))
						tables.add(schemaTableName);
				}
			} else {
				List<Map<String, Object>> list = cacheDriverExecutor.getAllTables();
				for (Map<String, Object> map : list) {
					String tableSchema = (String) map.get("TABLE_SCHEMA");
					String tableName = (String) map.get("TABLE_NAME");
					if (!CommonTools.isBlank(tableSchema)) {
						if (schema.equalsIgnoreCase(tableSchema)) {
							String schemaTableName = String.format("%s.%s", tableSchema, tableName);

							if (!tables.contains(schemaTableName))
								tables.add(schemaTableName);
						}
					}
				}
			}
			String exportOrderBy = consoleVariables.getString("ExportOrderBy", DEFAULT_ExportOrderBy);
			for (String table : tables) {
				String command = String.format("SELECT * FROM %s %s", quote(table), exportOrderBy);
				String fileName = String.format("%s/%s", exportToFolder,
						exportUpperOrLowerCase(table.replaceFirst("\\.", "/")));
				File file = new File(fileName);
				boolean success = false;
				try {
				    exportTo(command,file.getAbsolutePath());
					exported++;
				} catch (Exception ee) {
					log.error(ee);
					failed++;
				}
			}
			java.util.Date completedTime = new java.util.Date();
			if (showTaskLog) {
				String completedTimeFormat = new SimpleDateFormat(DEFAULT_DateTimeFormat).format(completedTime);
				System.out.println(String.format("Exported tables at %s", completedTimeFormat));
			}
			long timeCostMs = completedTime.getTime() - beginTime.getTime();
			return String.format("*********%s tables exported and %s tables failed (%sms)*********", exported, failed,
					timeCostMs);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return e.getMessage();
		}
	}

	public String quote(String tableOrColumn) {
		String quote = consoleVariables.getString("Quote", DEFAULT_Quote);
		String[] schemaTableOrColumn = tableOrColumn.split("\\.");
		if (schemaTableOrColumn.length == 1) {
			return String.format(quote, tableOrColumn);
		}
		if (schemaTableOrColumn.length == 2) {
			String quoteSchema = String.format(quote, schemaTableOrColumn[0]);
			String quoteTable = String.format(quote, schemaTableOrColumn[1]);
			return String.format("%s.%s", quoteSchema, quoteTable);
		}
		return tableOrColumn;
	}

	public void setIntoValue(Object value) {
		if (CommonTools.isBlank(value)) {
			intoValue = "NULL";
		} else {
			if (value instanceof List) {
				List<Map<String, Object>> list = (List<Map<String, Object>>) value;
				if (list.size() > 0) {
					Map<String, Object> map = list.get(0);
					List<String> keys = new ArrayList<String>(map.keySet());
					String key = keys.get(0);
					intoValue = formatForExport(map.get(key));
				}
			} else {
				intoValue = formatForExport(value);
			}
		}
	}

	public String formatCommand(String command) {

		if (command == null)
			return null;

		Pattern pattern = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");
		Matcher matcher = pattern.matcher(command);
		List<String> intoNames = new ArrayList<String>();
		while (matcher.find()) {
			intoNames.add(matcher.group(1));
		}
		for (String intoName : intoNames) {
			command = command.replaceAll(String.format("\\{%s\\}", intoName), consoleVariables.getString(intoName));
		}
		return command;
	}

	public String exportUpperOrLowerCase(String s) {
		String exportUpperOrLowerCase = consoleVariables.getString("ExportUpperOrLowerCase",
				DEFAULT_ExportUpperOrLowerCase);
		if (exportUpperOrLowerCase.equalsIgnoreCase("Upper")) {
			return s.toUpperCase();
		}
		if (exportUpperOrLowerCase.equalsIgnoreCase("Lower")) {
			return s.toLowerCase();
		}
		return s;
	}
	
	public CmdConsole getCmdConsole(){
	    return cmdConsole;
	}

	public static String error(Exception e) {
		if (e instanceof SQLException) {
			SQLException se = (SQLException) e;
			return String.format("ERROR: (%s) %s", se.getErrorCode(), se.getMessage());
		} else {
			return String.format("ERROR: %s", e.getMessage());
		}
	}
    
	public synchronized static String exit() {
	    try{
    		String exitTmpPathStr = String.format("%s/exit.tmp", tmpPath.toString());
    		Path exitTmpPath = Paths.get(exitTmpPathStr);
    		DiskFile exitTmpDf = new DiskFile(exitTmpPathStr);
    		exitTmpDf.write(System.currentTimeMillis() + "");
    		return "Successfully";
	    }catch(Exception e){
			LoggerFactory.getLogger(ScriptConsole.class).error(e.getMessage(), e);
			return String.format("ERROR: %s", e.getMessage());
	    }
	}
	
    public long memoryUsing(){
        int mb = 1024 * 1024;
		Runtime instance = Runtime.getRuntime();
		long totalMemory = instance.totalMemory() / mb;
		long freeMemory = instance.freeMemory() / mb;
		return  (instance.totalMemory() - instance.freeMemory()) / mb;
    }	

    public double memoryUsage(long using){
        int mb = 1024 * 1024;
		Runtime instance = Runtime.getRuntime();
		long maxMemory = instance.maxMemory() / mb;
		return (using * 1.0D / maxMemory * 1.0D);
    }	
    
    public double memoryUsage(){
        return memoryUsage(memoryUsing());
    }   	

	public void addExitRunnable(Runnable exitRun) {
		exitRuns.add(exitRun);
	}

	public String version() {
		return VERSION;
	}
	
	public void setUseGhostEnv(){
	    this.useScriptEnv = false;
        this.useJdbcEnv = false;
        this.useCmdEnv = false; 
        this.useGhostEnv = true; 
        set("Name","ghost");
	}	
	
	public void setUseJdbcEnv(){
	    this.useScriptEnv = false;
        this.useJdbcEnv = true;
        this.useCmdEnv = false; 
        this.useGhostEnv = false; 
        set("Name","jdbc");
	}
	
	public void setUseScriptEnv(){
	    this.useScriptEnv = true;
        this.useJdbcEnv = false;
        this.useCmdEnv = false;
        this.useGhostEnv = false; 
	}	
	
	public void setUseCmdEnv(){
	    this.useScriptEnv = false;
        this.useJdbcEnv = false;
        this.useCmdEnv = true;
        this.useGhostEnv = false; 
        set("Name","cmd");
	}
	
	public boolean isUseGhostEnv(){
        return this.useGhostEnv;
	}	
	
	public boolean isUseJdbcEnv(){
        return this.useJdbcEnv;
	}	
	
	public boolean isUseScriptEnv(){
	    return this.useScriptEnv;
	}		
	
	public boolean isUseCmdEnv(){
        return this.useCmdEnv;
	}	

	public String help() {
		StringBuffer content = new StringBuffer();
		content.append("---------------------------------------------------------");
		content.append(System.lineSeparator());
		content.append("Usage,");
		content.append(System.lineSeparator());
		content.append("> java -jar jsplus-embed.jar [Options]");
		content.append(System.lineSeparator());
		content.append(System.lineSeparator());
		content.append("DataSource Options,");
		content.append(System.lineSeparator());
		content.append("*The following parameters will override the 'DataSource.properties' settings*");
		content.append(System.lineSeparator());
		content.append("-ThreadName               - ThreadName, default is 'DriverDataSource',eg. Console_DS");
		content.append(System.lineSeparator());
		content.append("-DriverJar                - DriverJar, eg. /jdbc/db_driver.jar");
		content.append(System.lineSeparator());
		content.append("-Driver                   - Driver, JDBC driver class name");
		content.append(System.lineSeparator());
		content.append("-Url                      - Url, Connection string");
		content.append(System.lineSeparator());
		content.append("-Username                 - Username, Database account");
		content.append(System.lineSeparator());
		content.append("-Password                 - Password, Database password");
		content.append(System.lineSeparator());
		content.append("-TestValidTimeout         - TestValidTimeout, default is 5s");
		content.append(System.lineSeparator());
		content.append("-RefreshPoolTimer         - RefreshPoolTimer, default is 1s");
		content.append(System.lineSeparator());
		content.append("-IdleTimeout              - IdleTimeout, default is 60s");
		content.append(System.lineSeparator());
		content.append("-Timeout                  - Query or execute timeout, 0s is disabled, default is 0s");
		content.append(System.lineSeparator());
		content.append(
				"-TransactionTimeout       - Query or execute timeout if auto commit is false, 0s is disabled, default is 0s");
		content.append(System.lineSeparator());
		content.append("-LifeCycle                - LifeCycle, default is Long.MAX_VALUE");
		content.append(System.lineSeparator());
		content.append("-MinPoolSize              - MinPoolSize, default is 0");
		content.append(System.lineSeparator());
		content.append("-MaxPoolSize              - MaxPoolSize, default is Integer.MAX_VALUE");
		content.append(System.lineSeparator());
		content.append("-ConnProps                - ConnProps, Defaul is empty");
		content.append(System.lineSeparator());
		content.append("-EnableStatusLog          - EnableStatusLog, default is false");
		content.append(System.lineSeparator());
		content.append(
				"-StatusLogFolder          - StatusLogFolder, Write to jar location if value is empty, default is empty");

		content.append(System.lineSeparator());
		content.append(
				"-AllowAllAccess          - AllowAllAccess, Default is true");

		content.append(System.lineSeparator());		
		content.append(System.lineSeparator());
		content.append("Task Options,");
		content.append(System.lineSeparator());
		content.append("-TaskTimer                - Task timer, 0s is run once only, Default 0s");
		content.append(System.lineSeparator());
		content.append("-Task                     - Task batch file path, Default is empty");
		content.append(System.lineSeparator());
		content.append("-Env                      - 'cmd/jdbc/script', Default is 'jdbc'");
		content.append(System.lineSeparator());
		content.append("-ScriptEngine             - Default is 'nashorn'");
		content.append(System.lineSeparator());
		content.append(System.lineSeparator());
		content.append("Others,");
		content.append(System.lineSeparator());
		content.append("--exit                    - Exit all.");		
		content.append(System.lineSeparator());
		content.append("--help                    - Display help info.");
		content.append(System.lineSeparator());
		content.append("--version                 - Display version.");

		return content.toString();
	}
	
	public void connect(int consoleHashCode) {
        ScriptConsole sc = CONNECT_CONSOLES.get(consoleHashCode);
        if(sc == null){
            System.out.println("Missing Console: " + consoleHashCode);
        }
        if(sc != null){
            ScriptConsole currentConsole = CONNECT_CONSOLES.get(0);
            CONNECT_CONSOLES.put(0,sc);
            if(currentConsole != null){
                System.out.println("Origin Console: " + currentConsole.hashCode());
            }
            System.out.println("Connected Console: " + consoleHashCode);
            set("Name",sc.displayEngineName);
        }
	}
	
	protected ScriptConsole getConsole(){

    	ScriptConsole sc = CONNECT_CONSOLES.get(0);
    	
    	if(sc != null) return sc;

	    return this;
	}
	
	protected String getDisplayEngineName(){
	    if(isUseJdbcEnv()) return "jdbc";
	    
	    if(isUseCmdEnv()) return "cmd";
	    
	    if(isUseGhostEnv()) return "ghost";
	    
	    if(isUseScriptEnv()) return displayEngineName;
	    
	    return "";
	}
	
	public Map<Integer,ScriptConsole> getConsoles(){
	    return CONNECT_CONSOLES;
	}
	
	public String decryptScript(File scriptPath) throws Exception {
	    String aesKey = consoleVariables.getString("Script_Cipher_AesKey", DEFAULT_Script_Cipher_AesKey);
	    String script = null;
	    if(CommonTools.isBlank(aesKey)){
    		script = FileTools.text(scriptPath);
	    }else{
	        DiskFile df = new DiskFile(scriptPath);
	        byte[] encryptBytes = df.readAllBytes();
	        byte[] decryptBytes = CipherTools.AESDecrypt(aesKey,encryptBytes);
	        script = new String(decryptBytes,BaseFile.CHARSET);
	    }
	    return script;
	}

    public synchronized void startSchedule() {
        if (scheduleExecutor == null) {
            scheduleExecutor = Executors.newSingleThreadScheduledExecutor();
            scheduleNextMinute();
        }
    }
    
    private void scheduleNextMinute() {
        long delay = getDelayToNextMinute();
        scheduleExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    executeTasks();
                } finally {
                    scheduleNextMinute();
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
    
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
	
	public void addSchedule(String name,String cronRules,Runnable runnable) {
	    clearSchedule(name);
	    final Map<String,Object> map = new HashMap<String,Object>();
	    map.put("cronRules",cronRules);
	    map.put("runnable",runnable);
	    scheduleMap.put(name,map);
	}
	
	public void clearSchedule(String name) {
	    scheduleMap.remove(name);
	}
	
	public void clearAllSchedule(String name) {
	    scheduleMap.clear();
	}	
	
	public Map<String,Map<String,Object>> getScheduleMap() {
	    return new HashMap<String, Map<String,Object>>(scheduleMap);
	}
	
    private long getDelayToNextMinute() {
        long now = System.currentTimeMillis();
        long nextMinute = ((now / 60000) + 1) * 60000;
        long delay = nextMinute - now;
        return delay > 0 ? delay : 60000;
    }	
	
	/* For base console */
	
	
	public Object version(List<String> params) {
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.version()'";
		}
		try {
			return version();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

	public Object help(List<String> params) {
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.help()'";
		}
		try {
			return help();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

	public Object exit(List<String> params) {
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.exit()'";
		}
		try {
			return ScriptConsole.exit();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

	public Object reset(List<String> params) {
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.reset()'";
		}
		reset();
		return "Successfully";
	}

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
			return ScriptConsole.error(e);
		}
	}

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
			return ScriptConsole.error(e);
		}
	}

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
    			return ScriptConsole.error(e);
		    }
		}
		return "Successfully";
	}

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
			return ScriptConsole.error(e);
		}
	}

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
			return ScriptConsole.error(e);
		}
	}

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

	public Object showAll(List<String> params) {
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.showAll()'";
		}
		return showAll();
	}
	
	/*For jdbc console*/
	
	public Object importTables(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }	    
		if (params.size() == 0 && params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.importTables(csvFolder) or Console.importTables(schema,csvFolder)'";
		}
		String schema = null;
		String csvFolder = null;
		if (params.size() == 1) {
			schema = null;
			csvFolder = formatCommand(params.get(0).trim());
		}
		if (params.size() == 2) {
			schema = formatCommand(params.get(0).trim());
			csvFolder = formatCommand(params.get(1).trim());
		}
		return importTables(schema, csvFolder);
	}

	public Object clearLastSelectCommand(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }	    
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.clearLastSelectCommand()'";
		}
		clearLastSelectCommand();
		return "Cleared last select command";
	}

	public Object exportTables(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }	    
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.exportTables(schema,toCsvFolder) or Console.exportTables(toCsvFolder)'";
		}

		String _aesKey = consoleVariables.getString("JdbcCipherAesKey");
		boolean useEncrypt = !CommonTools.isBlank(_aesKey);
		if (useEncrypt) {
			if (_aesKey.length() < 16 || _aesKey.length() > 256) {
				return "ERROR: Invalid Console.JdbcCipherAesKey (Required 16 - 256 chars)";
			}
			System.out.println("*********************************************");
			System.out.println("Please remember the info(For import),");
			System.out.println(String.format("Console.JdbcCipherAesKey=%s;", _aesKey));
			System.out.println("*********************************************");
		}

		try {
			String schema = null;
			String exportToFolder = null;
			if (params.size() == 1) {
				schema = null;
				exportToFolder = formatCommand(params.get(0).trim());
			}
			if (params.size() == 2) {
				schema = formatCommand(params.get(0).trim()).toUpperCase().equals("NULL") ? null : params.get(0).trim();
				exportToFolder = formatCommand(params.get(1).trim());
			}
			return exportTables(schema, exportToFolder);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

	public Object desc(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }	    
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.desc(tableName) or Console.desc(schema,tableName)'";
		}
		try {
			String schema = null;
			String tableName = null;
			if (params.size() == 1) {
				tableName = formatCommand(params.get(0).trim());
			}
			if (params.size() == 2) {
				schema = formatCommand(params.get(0).trim());
				tableName = formatCommand(params.get(1).trim());
			}
			List<Map<String, Object>> list = desc(schema, tableName);
			String lines = toResultList(list);
			return lines;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

	public Object showTables(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }	    
		if (params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.showTables() or Console.showTables(schema)'";
		}
		try {
			String schema = null;
			List<Map<String, Object>> tables = new ArrayList<Map<String, Object>>();
			if (params.size() == 1) {
				schema = formatCommand(params.get(0).trim());
			}
			tables.addAll(showTables(schema));
			String lines = toResultList(tables);
			return lines;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

	public Object showColumnTypes(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }	    
		if (params.size() == 0 || params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.showColumnTypes(tableName)'";
		}
		try {
			String tableName = formatCommand(params.get(0).trim());
			Map<String, Object> result = showColumnTypes(tableName);
			List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
			list.add(result);
			String lines = toResultList(list);
			return lines;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

	public Object showColumnClasses(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }	    
		if (params.size() == 0 || params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.showColumnClasses(tableName)'";
		}
		try {
			String tableName = formatCommand(params.get(0).trim());
			Map<String, Object> result = (Map<String, Object>) showColumnClasses(tableName);
			List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
			list.add(result);
			String lines = toResultList(list);
			return lines;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

	public Object exportTo(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }	    
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.exportTo(path)'";
		}
		try {
		    if(params.size() == 1){
    			String file = formatCommand(params.get(0).trim());
    			if (CommonTools.isBlank(file)) {
    				return "ERROR: Invalid params, eg.'Console.exportTo(path)'";
    			}
    			exportTo(file);
		    }
		    if(params.size() == 2){
		        String folder = formatCommand(params.get(0).trim());
    			String filePrefix = formatCommand(params.get(1).trim());
    			if (CommonTools.isBlank(folder) || CommonTools.isBlank(filePrefix)) {
    				return "ERROR: Invalid params, eg.'Console.exportTo(folder,filePrefix)'";
    			}
    			exportTo(new File(folder),filePrefix);
		    }		    
			return "Successfully";
		} catch (Exception e) {
			return ScriptConsole.error(e);
		}
	}

	public Object importFrom(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 || params.size() == 1 || params.size() > 3) {
			return "ERROR: Invalid params, eg.'Console.importFrom(schema,tableName,csv) or Console.importFrom(tableName,csv)'";
		}
		try {
			String schema = null;
			String tableName = null;
			Integer batchSize = consoleVariables.getInteger("BatchSize", DEFAULT_BatchSize);
			String csv = null;
			if (params.size() == 2) {
				tableName = formatCommand(params.get(0).trim());
				csv = params.get(1).trim();
			}
			if (params.size() == 3) {
				schema = formatCommand(params.get(0).trim()).toUpperCase().equals("NULL") ? null : params.get(0).trim();
				tableName = formatCommand(params.get(1).trim());
				csv = params.get(2).trim();
			}
			return importFrom(schema, tableName, csv);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}	
	
	public Object setAutoCommit(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }	    
		if (params.size() == 0 || params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.setAutoCommit(true/false)'";
		}
		Boolean autoCommit = null;
		try {
			if (params.size() == 1) {
				autoCommit = Boolean.parseBoolean(formatCommand(params.get(0).trim()));
			}
			Boolean b = setAutoCommit(autoCommit);
			return b ? "Successfully" : "Connection is null";
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

	public Object genCreateTables(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }	    
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.genCreateTables(schema,sqlFilePath) or Console.genCreateTable(sqlFilePath)'";
		}
		String schema = null;
		String sqlFilePath = null;
		try {
			if (params.size() == 1) {
				sqlFilePath = formatCommand(params.get(0).trim());
			}
			if (params.size() == 2) {
				schema = formatCommand(params.get(0).trim());
				sqlFilePath = formatCommand(params.get(1).trim());
			}
			return genCreateTables(schema, sqlFilePath);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

	public Object genCreateTable(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }   
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.genCreateTable(schema,tableName) or Console.genCreateTable(tableName)'";
		}
		String schema = null;
		String tableName = null;
		try {
			if (params.size() == 1) {
				tableName = formatCommand(params.get(0).trim());
			}
			if (params.size() == 2) {
				schema = formatCommand(params.get(0).trim());
				tableName = formatCommand(params.get(1).trim());
			}
			return genCreateTable(schema, tableName);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsole.error(e);
		}
	}

    public static String escapeCsv(String input) {
        return EscapeTools.escapeToSingleLineForCsv(input);
    }
    
    public static String unescapeCsv(String input) {
        return EscapeTools.unescape(input);
    }
    
}