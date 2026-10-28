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
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.datasource.DriverDataSource;
import java.util.Arrays;
import java.util.List;
import com.killcoding.tool.CommonTools;
import java.text.SimpleDateFormat;
import com.killcoding.datasource.CacheDriverExecutor;
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
import java.util.Iterator;
import java.util.HashMap;
import com.killcoding.tool.EscapeTools;
import com.killcoding.cache.AbsCacheApi;
import java.lang.reflect.Constructor;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Clob;
import java.sql.Blob;
import com.killcoding.cache.DataObject;
import com.killcoding.tool.CipherTools;
import com.killcoding.tool.Base64Tools;
import com.killcoding.file.DiskFile;
import java.sql.Timestamp;
import com.killcoding.file.BaseFile;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import com.killcoding.cache.StoredCache;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalTime;

/**
* ScriptConsoleBase - the shared foundation of every console flavour (ScriptConsole and its
* CmdConsole / JdbcConsole / GhostConsole subclasses).
*
* Owns the process-wide static infrastructure (exit policy, connected-console registry, JS_PLUS_PATH
* / Appenv bootstrap, exit.tmp monitor thread, static datasource), the console variable map and its
* defaults, the environment switch flags (script / jdbc / cmd / ghost), the JDBC connection lifecycle
* (open / close / commit / rollback / abort), the value formatters used by query and CSV rendering,
* the command history, and the small general-purpose helpers shared by every console.
* */
public class ScriptConsoleBase implements ConsoleImpl {

	/** Callback used instead of System.exit - lets embedded hosts control termination. */
	public interface ExitPolicy {

		/**
		* Performs the exit operation
		* @param status - the status
		* */
		void exit(int status);
	}

	protected Logger log = null;

    protected static final Map<Integer,ScriptConsole> CONNECT_CONSOLES = new ConcurrentHashMap<Integer,ScriptConsole>();

	/** Default policy keeps the historic behaviour (System.exit). */
	private static volatile ExitPolicy EXIT_POLICY = new ExitPolicy() {
		@Override
		/*
		* Performs the exit operation
		* @param status - the status
		* */
		public void exit(int status) {
			System.exit(status);
		}
	};

	/**
	 * Override the process-exit behaviour (e.g. tests/embedded runners want a
	 * callback instead of killing the JVM). Pass null to restore the default.
	 * */
	public static void setExitPolicy(ExitPolicy policy) {
		if (policy != null) {
			EXIT_POLICY = policy;
		} else {
			EXIT_POLICY = new ExitPolicy() {
				@Override
				/*
				* Performs the exit operation
				* @param status - the status
				* */
				public void exit(int status) {
					System.exit(status);
				}
			};
		}
	}

	/** Single exit point for every "quit console" path (P2-4). */
	protected static void exitConsole(int status) {
		EXIT_POLICY.exit(status);
	}

	private static DriverDataSource datasource = null;

	protected ConfigProperties datasourceVariables = new ConfigProperties();
	protected ConfigProperties consoleVariables = new ConfigProperties();

	protected static ConfigProperties appenvCp = null;

	protected boolean firstOpen = true;
	protected boolean useScriptEnv = false;
	protected boolean useJdbcEnv = false;
	protected boolean useCmdEnv = false;
	protected boolean useGhostEnv = false;

	protected long preExecuteTime = 0L;
	protected CacheDriverExecutor cacheDriverExecutor = null;
	protected String lastSelectCommand = null;
	protected String intoValue = null;
	private boolean exportedCompleted = false;

    protected GhostConsole ghostConsole = null;
    protected CmdConsole cmdConsole = null;
    protected String displayEngineName = "script";

	private static final List<Runnable> exitRuns = new ArrayList<Runnable>();
	private static final long startedTimeMs = System.currentTimeMillis();
	private static Path tmpPath = null;
	private static java.util.concurrent.ExecutorService exitExecute = null;

	public final static String JS_PLUS_PATH_KEY = "JS_PLUS_PATH";

	/**
	* Constructs a new ScriptConsoleBase
	* */
	@SuppressWarnings("this-escape")
	public ScriptConsoleBase() {
		super();
		initEnv();
		log = LoggerFactory.getLogger(this.getClass());
		loadAppenv();
		initReset();
	}

	/**
	* Performs the getJsPlusPath operation
	* @return the getJsPlusPath result
	* */
	public static String getJsPlusPath(){
	    return System.getProperty(JS_PLUS_PATH_KEY);
	}

	/**
	* Performs the initEnv operation
	* */
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

	/**
	* Performs the appenvReloaded operation
	* @param apenvCp - the apenvCp
	* */
	public void appenvReloaded(ConfigProperties apenvCp){

	}

	/**
	* Performs the loadAppenv operation
	* */
	@SuppressWarnings("this-escape")
	private void loadAppenv(){
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
            	        /*
            	        * Performs the run operation
            	        * */
            	        public void run(){
            	            Logger.systemDebug(ScriptConsole.class, "Appenv '{}' Reloading...", appenvPropFile);
    						List<String> propKeys = new ArrayList<String>(appenvCp.stringPropertyNames());
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

	/**
	* Performs the getAppenv operation
	* @return the getAppenv result
	* */
	public static ConfigProperties getAppenv(){
	    return appenvCp;
	}

	/**
	* Performs the detectSystemExit operation
	* @return the detectSystemExit result
	* */
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

	/**
	* Performs the initPool operation
	* */
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
			exitExecute = java.util.concurrent.Executors.newFixedThreadPool(1);
			exitExecute.execute(new Runnable() {
				@Override
				/*
				* Performs the run operation
				* */
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
    				exitConsole(0);
				}
			});
		}
	}

	/**
	* Performs the setStaticDataSource operation
	* @param datasource - the datasource
	* */
	public static void setStaticDataSource(DriverDataSource datasource){
	    ScriptConsoleBase.datasource = datasource;
	}

	/**
	* Performs the setDataSource operation
	* @param datasource - the datasource
	* */
	public void setDataSource(DriverDataSource datasource){
	    setStaticDataSource(datasource);
	}

	/**
	* Performs the getDataSource operation
	* @return the getDataSource result
	* */
	public synchronized DriverDataSource getDataSource() throws IOException {

	    if(datasource == null){
            // Runtime override: -DAPP_DATASOURCE=/path/xxx.properties (or the
            // APP_DATASOURCE env var) lets test runners such as
            // test/TestDatasource.bat/.sh exercise the datasource suite against
            // a dedicated pool configuration (e.g. config/TestDatasource.properties
            // with second-scale idle/using/abort/timeout values) without touching
            // the production DataSource.properties. Absent the override, behaviour
            // is exactly as before: default config/DataSource(.properties) file.
            String appDataSourcePath = System.getProperty("APP_DATASOURCE");
            if (appDataSourcePath == null || appDataSourcePath.trim().length() == 0) {
                appDataSourcePath = System.getenv("APP_DATASOURCE");
            }
            File datasourceConfigFile = null;
            if (appDataSourcePath != null && appDataSourcePath.trim().length() > 0) {
                File overrideFile = new File(appDataSourcePath);
                if (overrideFile.exists()) {
                    datasourceConfigFile = overrideFile;
                } else {
                    Logger.systemDebug(ScriptConsole.class, "APP_DATASOURCE file not found: " + appDataSourcePath + " - falling back to default config");
                }
            }
            if (datasourceConfigFile == null) {
                datasourceConfigFile = new File(ScriptConsole.getJsPlusPath() + "/config/DataSource.properties");
                if(!datasourceConfigFile.exists()){
                    datasourceConfigFile = new File(ScriptConsole.getJsPlusPath() + "/config/Datasource.properties");
                }
            }
    		if(datasourceConfigFile.exists()){
	            ScriptConsoleBase.datasource = new DriverDataSource(datasourceConfigFile);
    		}else{
    		    ScriptConsoleBase.datasource = new DriverDataSource();
    		    ScriptConsoleBase.datasource.setConfig(datasourceVariables);
    		}
	    }

		return datasource;
	}

	/**
	* Performs the getIntoValue operation
	* @return the getIntoValue result
	* */
	public String getIntoValue() {
		return intoValue;
	}

	/**
	* Performs the getLastSelectCommand operation
	* @return the getLastSelectCommand result
	* */
	public String getLastSelectCommand() {
		return lastSelectCommand;
	}

	/**
	* Performs the getCacheDriverExecutor operation
	* @return the getCacheDriverExecutor result
	* */
	public CacheDriverExecutor getCacheDriverExecutor() {
		return cacheDriverExecutor;
	}

	/**
	* Performs the getCacheDriverExecutor operation
	* @param refreshConnection - the refreshConnection
	* @return the getCacheDriverExecutor result
	* */
	public CacheDriverExecutor getCacheDriverExecutor(boolean refreshConnection) {
		if (refreshConnection) {
			open(true);
		}
		return getCacheDriverExecutor();
	}

	/**
	* Performs the getConsoleVariables operation
	* @return the getConsoleVariables result
	* */
	public ConfigProperties getConsoleVariables() {
		return consoleVariables;
	}

	/**
	* Performs the setConsoleVariables operation
	* @param consoleVariables - the consoleVariables
	* */
	public void setConsoleVariables(ConfigProperties consoleVariables) {
		this.consoleVariables = consoleVariables;
	}

	/**
	* Performs the setLastSelectCommand operation
	* @param lastSelectCommand - the lastSelectCommand
	* */
	public void setLastSelectCommand(String lastSelectCommand) {
		this.lastSelectCommand = lastSelectCommand;
	}

	/**
	* Performs the clearLastSelectCommand operation
	* */
	public void clearLastSelectCommand() {
		lastSelectCommand = null;
	}

	/**
	* Performs the into operation
	* @param name - the name
	* */
	public void into(String name) {
		consoleVariables.putAndReturnSelf(name, intoValue);
	}

	/**
	* Performs the sleep operation
	* @param sleepTime - the sleepTime
	* */
	public void sleep(String sleepTime) throws Exception {
		Long ms = ConfigProperties.parseTimeToMs(sleepTime, 0L);
		Thread.sleep(ms);
	}

	/**
	* Performs the hostname operation
	* @return the hostname result
	* */
	public String hostname() throws Exception {
		return CommonTools.getHostname();
	}

	/**
	* Performs the startTask operation
	* @param taskTimer - the taskTimer
	* @param taskFile - the taskFile
	* */
	public void startTask(String taskTimer, String taskFile) {
		if (taskTimer == null) {
			preExecuteTime = 0L;
			consoleVariables.putAndReturnSelf("TaskTimer", DEFAULT_TaskTimer);
			consoleVariables.putAndReturnSelf("Task", taskFile);
		} else {
			preExecuteTime = 0L;
			consoleVariables.putAndReturnSelf("TaskTimer", taskTimer);
			consoleVariables.putAndReturnSelf("Task", taskFile);
		}
	}

	/**
	* Performs the stopTask operation
	* */
	public void stopTask() {
		preExecuteTime = 0L;
		consoleVariables.putAndReturnSelf("TaskTimer", DEFAULT_TaskTimer + "ms");
		consoleVariables.putAndReturnSelf("Task", null);
	}

	/**
	* Performs the set operation
	* @param key - the key
	* @param value - the value
	* @return the set result
	* */
	public String set(String key,Object value) {
		consoleVariables.putAndReturnSelf(key,value);
		return String.format("Set %s=%s", key, value);
	}

	/**
	* Performs the show operation
	* @param key - the key
	* @return the show result
	* */
	public String show(String key) {
		Object value = consoleVariables.getString(key);
		return String.format("Get %s=%s", key, value);
	}

	/**
	* Performs the showAll operation
	* @return the showAll result
	* */
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

	/**
	* Performs the setAutoCommit operation
	* @param autoCommit - the autoCommit
	* @return the setAutoCommit result
	* */
	public Boolean setAutoCommit(Boolean autoCommit) throws Exception {
		if (cacheDriverExecutor == null) {
			return false;
		} else {
			getCacheDriverExecutor(true).getConnection().setAutoCommit(autoCommit);
			return true;
		}
	}

	/**
	* Performs the setCacheApi operation
	* @param apiClass - the apiClass
	* */
	public static void setCacheApi(String apiClass) throws Exception {
		Class<?> clazz = Class.forName(apiClass.trim());
		Constructor<?> con = clazz.getConstructor(Boolean.class);
		AbsCacheApi api = (AbsCacheApi) con.newInstance(true);
		StoredCache.setApi(api);
	}

	/**
	* Performs the reset operation
	* */
	public synchronized void reset() {
		initReset();
	}

	/**
	* Performs the initReset operation (constructor-safe reset,
	* private so it cannot be overridden during super-class construction)
	* */
	private synchronized void initReset() {
		consoleVariables.putAndReturnSelf("QueryCursorStart", DEFAULT_QueryCursorStart);
		consoleVariables.putAndReturnSelf("QueryMaxRows", DEFAULT_QueryMaxRows);
		consoleVariables.putAndReturnSelf("AutoCommit", DEFAULT_AutoCommit);
		consoleVariables.putAndReturnSelf("CommandEnd", DEFAULT_CommandEnd);
		consoleVariables.putAndReturnSelf("CommandCancel", DEFAULT_CommandCancel);
		consoleVariables.putAndReturnSelf("ColumnWidth", DEFAULT_ColumnWidth);
		consoleVariables.putAndReturnSelf("ColumnCut", DEFAULT_ColumnCut);
		consoleVariables.putAndReturnSelf("ShowResultHeader", DEFAULT_ShowResultHeader);
		consoleVariables.putAndReturnSelf("ShowResultBody", DEFAULT_ShowResultBody);
		consoleVariables.putAndReturnSelf("ShowResultFooter", DEFAULT_ShowResultFooter);
		consoleVariables.putAndReturnSelf("DateTimeFormat", DEFAULT_DateTimeFormat);
		consoleVariables.putAndReturnSelf("DateFormat", DEFAULT_DateFormat);
		consoleVariables.putAndReturnSelf("TimeFormat", DEFAULT_TimeFormat);
		consoleVariables.putAndReturnSelf("Name", DEFAULT_Name);
		consoleVariables.putAndReturnSelf("WriteHistory", DEFAULT_WriteHistory);
		consoleVariables.putAndReturnSelf("CommandHistory", DEFAULT_CommandHistory);
		consoleVariables.putAndReturnSelf("ShowTaskLog", DEFAULT_ShowTaskLog);
		consoleVariables.putAndReturnSelf("ExportMaxFileSize", DEFAULT_ExportMaxFileSize);
		consoleVariables.putAndReturnSelf("ExportCursorStart", DEFAULT_ExportCursorStart);
		consoleVariables.putAndReturnSelf("ExportMaxRows", DEFAULT_ExportMaxRows);
		consoleVariables.putAndReturnSelf("ExportTimer", DEFAULT_ExportTimer);
		consoleVariables.putAndReturnSelf("ExportOrderBy", DEFAULT_ExportOrderBy);
		consoleVariables.putAndReturnSelf("ExportUpperOrLowerCase", DEFAULT_ExportUpperOrLowerCase);
		consoleVariables.putAndReturnSelf("ExportSplit", DEFAULT_ExportSplit);
		consoleVariables.putAndReturnSelf("ImportSplit", DEFAULT_ImportSplit);
		consoleVariables.putAndReturnSelf("BatchSize", DEFAULT_BatchSize);
		consoleVariables.putAndReturnSelf("Quote", DEFAULT_Quote);
		consoleVariables.putAndReturnSelf("CallStoredProcedureFun", DEFAULT_CallStoredProcedureFun);
		consoleVariables.putAndReturnSelf("ScriptEngine", DEFAULT_ScriptEngine);

		consoleVariables.putAndReturnSelf("JdbcCipherAesKey", DEFAULT_Jdbc_Cipher_AesKey);
		consoleVariables.putAndReturnSelf("ScriptCipherAesKey", DEFAULT_Script_Cipher_AesKey);
		consoleVariables.putAndReturnSelf("ConfigCipherAesKey", DEFAULT_Config_Cipher_AesKey);
	}

	/**
	* Performs the open operation
	* @return the open result
	* */
	public boolean open() {
		return open(true);
	}

	/**
	* Performs the close operation
	* */
	public void close() {
		try {
			close(true);
		} catch (SQLException e) {
			log.error(e.getMessage(), e);
			System.out.println(error(e));
		}
	}

	/**
	* Performs the commit operation
	* */
	public void commit() {
		try {
			commit(true);
		} catch (SQLException e) {
			log.error(e.getMessage(), e);
			System.out.println(error(e));
		}
	}

	/**
	* Performs the rollback operation
	* */
	public void rollback() {
		try {
			rollback(true);
		} catch (SQLException e) {
			log.error(e.getMessage(), e);
			System.out.println(error(e));
		}
	}

	/**
	* Performs the abort operation
	* */
	public void abort() {
		try {
			abort(true);
		} catch (SQLException e) {
			log.error(e.getMessage(), e);
			System.out.println(error(e));
		}
	}

	/**
	* Performs the abort operation
	* @param enableSystemOut - the enableSystemOut
	* */
	protected void abort(Boolean enableSystemOut) throws SQLException {
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

	/**
	* Performs the rollback operation
	* @param enableSystemOut - the enableSystemOut
	* */
	protected void rollback(Boolean enableSystemOut) throws SQLException {
		if (cacheDriverExecutor == null) {
			if (enableSystemOut)
				System.out.println("ERROR: Failed rollback, connection is null.");
		} else {
			cacheDriverExecutor.rollback();
			if (enableSystemOut)
				System.out.println("Rollback completed");
		}
	}

	/**
	* Performs the commit operation
	* @param enableSystemOut - the enableSystemOut
	* */
	protected void commit(Boolean enableSystemOut) throws SQLException {
		if (cacheDriverExecutor == null) {
			if (enableSystemOut)
				System.out.println("ERROR: Failed commit, connection is null.");
		} else {
			cacheDriverExecutor.commit();
			if (enableSystemOut)
				System.out.println("Commit completed");
		}
	}

	/**
	* Performs the close operation
	* @param enableSystemOut - the enableSystemOut
	* */
	protected void close(Boolean enableSystemOut) throws SQLException {
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

	/**
	* Performs the open operation
	* @param enableSystemOut - the enableSystemOut
	* @return the open result
	* */
	protected boolean open(Boolean enableSystemOut) {
		if (cacheDriverExecutor == null || cacheDriverExecutor.isClosed()) {
			firstOpen = false;
			try {

			    if(ScriptConsoleBase.datasource == null) ScriptConsoleBase.datasource = getDataSource();

				Connection connection = ScriptConsoleBase.datasource.getConnection();
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

	/**
	* Performs the format operation
	* @param objValue - the objValue
	* @return the format result
	* */
	public String format(Object objValue) {
		String format = "%" + consoleVariables.getInteger("ColumnWidth", DEFAULT_ColumnWidth) + "s";
		String value = formatObjectValue(false, false, objValue, String.format("%s/tmp", getJarPath()));
		return String.format(format, columnCut(value));
	}

	/**
	* Performs the formatForExport operation
	* @param objValue - the objValue
	* @return the formatForExport result
	* */
	public String formatForExport(Object objValue) {
		return formatObjectValue(false, true, objValue, String.format("%s/tmp", getJarPath()));
	}

	/**
	* Performs the formatForExport operation
	* @param tableObjectFolder - the tableObjectFolder
	* @param objValue - the objValue
	* @return the formatForExport result
	* */
	public String formatForExport(String tableObjectFolder, Object objValue) {
		String _aesKey = consoleVariables.getString("JdbcCipherAesKey");
		boolean useEncrypt = !CommonTools.isBlank(_aesKey);
		return formatObjectValue(useEncrypt, true, objValue, tableObjectFolder);
	}

	/**
	* Performs the formatObjectValue operation
	* @param useEncrypt - the useEncrypt
	* @param single - the single
	* @param objValue - the objValue
	* @param tableObjectFolder - the tableObjectFolder
	* @return the formatObjectValue result
	* */
	public String formatObjectValue(boolean useEncrypt, boolean single, Object objValue,
			String tableObjectFolder) {

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

	/**
	* Performs the toByteArray operation
	* @param blobValue - the blobValue
	* @return the toByteArray result
	* */
	public byte[] toByteArray(Blob blobValue) {
		try {
			return blobValue.getBinaryStream().readAllBytes();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	* Performs the toByteArray operation
	* @param clobValue - the clobValue
	* @return the toByteArray result
	* */
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

	/**
	* Performs the columnCut operation
	* @param strValue - the strValue
	* @return the columnCut result
	* */
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

	/**
	* Performs the toSingle operation
	* @param text - the text
	* @return the toSingle result
	* */
	public String toSingle(String text) {
		if (text == null)
			return null;

        return escapeCsv(text);
	}

	/**
	* Performs the clear operation
	* */
	public void clear() {
		clear(true);
	}

	/**
	* Performs the clear operation
	* @param enableSystemOut - the enableSystemOut
	* */
	protected void clear(Boolean enableSystemOut) {
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

	/**
	* Performs the checkHistory operation
	* */
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

	/**
	* Performs the addHistory operation
	* @param command - the command
	* */
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

	/**
	* Performs the showHistory operation
	* @param hiscmd - the hiscmd
	* */
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

	/**
	* Performs the toResultList operation
	* @param list - the list
	* @return the toResultList result
	* */
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

	/**
	* Performs the isCommandEnd operation
	* @param command - the command
	* @return the isCommandEnd result
	* */
	public boolean isCommandEnd(String command){
	    String commandEnd = consoleVariables.getString("CommandEnd",DEFAULT_CommandEnd);
		return command.matches(".*("+commandEnd+"){1,}[\s]*$");
	}

	/**
	* Performs the isCommandCancel operation
	* @param command - the command
	* @return the isCommandCancel result
	* */
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

	/**
	* Performs the getJarPath operation
	* @return the getJarPath result
	* */
	public static String getJarPath() {
		return CommonTools.getJarPath(ScriptConsole.class);
	}

	/**
	* Performs the quote operation
	* @param tableOrColumn - the tableOrColumn
	* @return the quote result
	* */
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

	/**
	* Performs the setIntoValue operation
	* @param value - the value
	* */
	public void setIntoValue(Object value) {
		if (CommonTools.isBlank(value)) {
			intoValue = "NULL";
		} else {
			if (value instanceof List) {
				@SuppressWarnings("unchecked")
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

	/**
	* Performs the formatCommand operation
	* @param command - the command
	* @return the formatCommand result
	* */
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

	/**
	* Performs the exportUpperOrLowerCase operation
	* @param s - the s
	* @return the exportUpperOrLowerCase result
	* */
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

	/**
	* Performs the getCmdConsole operation
	* @return the getCmdConsole result
	* */
	public CmdConsole getCmdConsole(){
	    return cmdConsole;
	}

	/**
	* Performs the error operation
	* @param e - the e
	* @return the error result
	* */
	public static String error(Exception e) {
		if (e instanceof SQLException) {
			SQLException se = (SQLException) e;
			return String.format("ERROR: (%s) %s", se.getErrorCode(), se.getMessage());
		} else {
			return String.format("ERROR: %s", e.getMessage());
		}
	}

	/**
	* Performs the exit operation
	* @return the exit result
	* */
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

    /**
    * Performs the memoryUsing operation
    * @return the memoryUsing result
    * */
    public long memoryUsing(){
        int mb = 1024 * 1024;
		Runtime instance = Runtime.getRuntime();
		long totalMemory = instance.totalMemory() / mb;
		long freeMemory = instance.freeMemory() / mb;
		return  (instance.totalMemory() - instance.freeMemory()) / mb;
    }

    /**
    * Performs the memoryUsage operation
    * @param using - the using
    * @return the memoryUsage result
    * */
    public double memoryUsage(long using){
        int mb = 1024 * 1024;
		Runtime instance = Runtime.getRuntime();
		long maxMemory = instance.maxMemory() / mb;
		return (using * 1.0D / maxMemory * 1.0D);
    }

    /**
    * Performs the memoryUsage operation
    * @return the memoryUsage result
    * */
    public double memoryUsage(){
        return memoryUsage(memoryUsing());
    }

	/**
	* Performs the addExitRunnable operation
	* @param exitRun - the exitRun
	* */
	public void addExitRunnable(Runnable exitRun) {
		exitRuns.add(exitRun);
	}

	/**
	* Performs the version operation
	* @return the version result
	* */
	public String version() {
		return VERSION;
	}

	/**
	* Performs the setUseGhostEnv operation
	* */
	public void setUseGhostEnv(){
	    this.useScriptEnv = false;
        this.useJdbcEnv = false;
        this.useCmdEnv = false;
        this.useGhostEnv = true;
        set("Name","ghost");
	}

	/**
	* Performs the setUseJdbcEnv operation
	* */
	public void setUseJdbcEnv(){
	    this.useScriptEnv = false;
        this.useJdbcEnv = true;
        this.useCmdEnv = false;
        this.useGhostEnv = false;
        set("Name","jdbc");
	}

	/**
	* Performs the setUseScriptEnv operation
	* */
	public void setUseScriptEnv(){
	    this.useScriptEnv = true;
        this.useJdbcEnv = false;
        this.useCmdEnv = false;
        this.useGhostEnv = false;
	}

	/**
	* Performs the setUseCmdEnv operation
	* */
	public void setUseCmdEnv(){
	    this.useScriptEnv = false;
        this.useJdbcEnv = false;
        this.useCmdEnv = true;
        this.useGhostEnv = false;
        set("Name","cmd");
	}

	/**
	* Performs the isUseGhostEnv operation
	* @return the isUseGhostEnv result
	* */
	public boolean isUseGhostEnv(){
        return this.useGhostEnv;
	}

	/**
	* Performs the isUseJdbcEnv operation
	* @return the isUseJdbcEnv result
	* */
	public boolean isUseJdbcEnv(){
        return this.useJdbcEnv;
	}

	/**
	* Performs the isUseScriptEnv operation
	* @return the isUseScriptEnv result
	* */
	public boolean isUseScriptEnv(){
	    return this.useScriptEnv;
	}

	/**
	* Performs the isUseCmdEnv operation
	* @return the isUseCmdEnv result
	* */
	public boolean isUseCmdEnv(){
        return this.useCmdEnv;
	}

	/**
	* Performs the help operation
	* @return the help result
	* */
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

	/**
	* Performs the connect operation
	* @param consoleHashCode - the consoleHashCode
	* */
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

	/**
	* Performs the getConsole operation
	* @return the getConsole result
	* */
	protected ScriptConsole getConsole(){

    	ScriptConsole sc = CONNECT_CONSOLES.get(0);

    	if(sc != null) return sc;

	    return null;
	}

	/**
	* Performs the getDisplayEngineName operation
	* @return the getDisplayEngineName result
	* */
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

	/**
	* Performs the decryptScript operation
	* @param scriptPath - the scriptPath
	* @return the decryptScript result
	* */
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

    /**
    * Performs the escapeCsv operation
    * @param input - the input
    * @return the escapeCsv result
    * */
    public static String escapeCsv(String input) {
        return EscapeTools.escapeToSingleLineForCsv(input);
    }

    /**
    * Performs the unescapeCsv operation
    * @param input - the input
    * @return the unescapeCsv result
    * */
    public static String unescapeCsv(String input) {
        return EscapeTools.unescape(input);
    }

}
