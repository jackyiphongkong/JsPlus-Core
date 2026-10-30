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
package com.killcoding.log;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;
import com.killcoding.tool.FileTools;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.text.MessageFormat;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


import com.killcoding.tool.ConfigProperties;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.sql.DataSource;

import com.killcoding.tool.CommonTools;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.killcoding.datasource.CacheDriverExecutor;
import java.sql.Connection;


import com.killcoding.datasource.DriverDataSource;
import java.sql.Timestamp;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import com.killcoding.file.BaseFile;
/**
* The class is write application log
* */
public final class LogRecord {

	protected final static String DEFAULT_MESSAGE_FORMAT = ":level :log_time_format [:log_thread_name-:log_thread_id] :log_class_name.:log_method_name(:log_file_name::log_line_number) **:message** \r\n:stack_trace";

	private final static Queue<Map<String, Object>> LOG_RECORD_POOL = new ConcurrentLinkedQueue<Map<String, Object>>();

	/** Shared daemon pool for database log flushing - created once, reused by
	* every log cycle. Never re-create per cycle (leaks a thread per second). */
	private static final ExecutorService DB_WRITE_POOL = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<Runnable>(1024), r -> {
				Thread t = new Thread(r, "LogRecord-DB-Writer");
				t.setDaemon(true);
				return t;
			}, new ThreadPoolExecutor.CallerRunsPolicy());

	private static boolean started = false;
	private static boolean firstRefreshConfig = false;
	private static String level = "DEBUG";
	private static String hostname = null;
	private static ConfigProperties config = null;

	private static Date LAST_REFRESH_CONFIG_TIME = null;

    private static String appDataSourcePath = "";
	private static File fileOutFolder = null;
	private static DriverDataSource dataSource = null;

	private static AlertMessage alertMesage = null;
	private static List<String> alertMessageLevelList = null;

	private static PrintStream originalOut = System.out;
    private static PrintStream originalErr = System.err;
	private static ByteArrayOutputStream customOutput = null;
    private static PrintStream customPrint = null;

	/**
	* Get log time format
	* */
	protected static SimpleDateFormat getLogTimeFormat() {
		String defValue = "yyyyMMdd HH:mm:ss.SSS";
		if (config == null) {
			return new SimpleDateFormat(defValue);
		} else {
			String logTimeFormat = config.getString("LogTimeFormat", defValue);
			return new SimpleDateFormat(logTimeFormat);
		}
	}

	/**
	* Get log date format
	* */
	protected static SimpleDateFormat getLogDateFormat() {
		String defValue = "yyyyMMdd";
		if (config == null) {
			return new SimpleDateFormat(defValue);
		} else {
			String logDateFormat = config.getString("LogDateFormat", defValue);
			return new SimpleDateFormat(logDateFormat);
		}
	}

	/**
	* Is started log thread
	* @return boolean
	* */
	protected static boolean isStarted() {
		return started;
	}

	/**
	* Set config
	* @param _config
	* */
	protected static synchronized void setConfig(ConfigProperties _config) {
		config = _config;
		String fileOutFolderConfig = config.getString("FileOutFolder", null);
		if (fileOutFolderConfig == null) {
			try {
				String jarPath = new File(LogRecord.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
				fileOutFolder = new File(String.format("%s/logs/", jarPath));
			} catch (Exception e) {
				Logger.systemWarn(LogRecord.class, e);
			}
		} else {
			fileOutFolder = new File(fileOutFolderConfig);
		}
		level = config.getString("Level", "DEBUG");
		Logger.setLevel(level);
		Logger.setClassNameEnableRegex(config.getString("ClassNameEnableRegex", null));
		Logger.setClassNameDisableRegex(config.getString("ClassNameDisableRegex", null));
		hostname = CommonTools.getHostname();
	}

	/**
	* Returns the current log-record list of this instance.
	* @return List&lt;Map&lt;String, Object&gt;>
	* */
	@SuppressWarnings("unchecked")
	protected static synchronized List<Map<String, Object>> list() {
		return (List<Map<String, Object>>) process("LIST", null);
	}

	/**
	* Get log list size
	* */
	protected static synchronized int size() {
		return (Integer) process("SIZE", null);
	}

	/**
	* Add log record object
	* @param o - the o
	* */
	protected static synchronized void add(Map<String, Object> o) {
		process("ADD", o);
		if (o != null) {
			String level = (String) o.get("level");
			if (level != null) {
				if (alertMessageLevelList.contains(level)) {
					pushAlertMessage(o);
				}
			}
		}
	}

	/**
	* Remove log record object
	* @param o - the o
	* */
	protected static synchronized void remove(Map<String, Object> o) {
		process("REMOVE", o);
	}

	/**
	* Push log record to alert message list;
	* @param o - the o
	* */
	protected static void pushAlertMessage(Map<String, Object> o) {
		if (alertMesage != null) {
			alertMesage.push(o);
		}
	}

	/**
	* Performs the process operation
	* @param action - the action
	* @param o - the o
	* @return the process result
	* */
	private static Object process(String action, Map<String, Object> o) {
		Integer outLogBatchLimit = config.getInteger("OutLogBatchLimit", 1000);
		if (action.equals("ADD")) {
			if (LOG_RECORD_POOL.size() >= outLogBatchLimit) {
				// Drop the oldest record when over the batch limit (ring-buffer
				// semantics on a ConcurrentLinkedQueue - lock-free).
				LOG_RECORD_POOL.poll();
			}
			o.put("system_name",config.getString("SystemName"));
			LOG_RECORD_POOL.offer(o);
		}

		if (action.equals("SIZE")) {
			return LOG_RECORD_POOL.size();
		}

		if (action.equals("LIST")) {
			List<Map<String, Object>> list = new ArrayList<Map<String, Object>>(LOG_RECORD_POOL.size());
			Map<String, Object> item = null;
			while ((item = LOG_RECORD_POOL.poll()) != null) {
				list.add(item);
			}
			return list;
		}
		return null;
	}

	/**
	* Performs the getLogFileIndex operation
	* @param prefix - the prefix
	* @param suffix - the suffix
	* @return the getLogFileIndex result
	* */
	private static int getLogFileIndex(String prefix, String suffix) {
		Integer fileOutMaxBackupIndex = config.getInteger("FileOutMaxBackupIndex", 10);
		for (int i = 1; i <= fileOutMaxBackupIndex; i++) {
			String logFileName = String.format("%s/%s.%s.%s", fileOutFolder.getAbsolutePath(), prefix, i, suffix);
			File logFile = new File(logFileName);
			if (!logFile.exists())
				return i;
		}
		return 0;
	}

	private static Map<String, List<String>> getLogBatch(List<Map<String, Object>> list) {
		Map<String, List<String>> logBatch = new HashMap<String, List<String>>();
		for (Map<String, Object> row : list) {
			Date logTime = (Date) row.get("log_time");
			String logDateStr = getLogDateFormat().format(logTime);
			String msg = format(config.getString("MessageFormat", DEFAULT_MESSAGE_FORMAT), row);
			List<String> batchLines = logBatch.get(logDateStr);
			if (batchLines == null) {
				batchLines = new ArrayList<String>();
			}
			batchLines.add(msg);

			if(customOutput != null){
			    try{
    			    customOutput.flush();
    			    customPrint.flush();
    			    String outMsg = customOutput.toString(BaseFile.CHARSET);
    			    if(!CommonTools.isBlank(outMsg)){
    			        customOutput = new ByteArrayOutputStream();
    			        customPrint = new PrintStream(customOutput);
    			        System.setOut(customPrint);
    			        System.setErr(customPrint);
    			        LoggerFactory.getLogger(LogRecord.class).mark("SystemOut: {}",outMsg);
    			    }
			    }catch(Exception e){
			        Logger.systemError(LogRecord.class,e.getMessage(),e);
			    }
			}

			logBatch.put(logDateStr, batchLines);
		}
		return logBatch;
	}

	/**
	* Performs the format operation
	* @param messageFormat - the messageFormat
	* @param row - the row
	* @return the format result
	* */
	public static String format(String messageFormat, Map<String, Object> row) {
		List<Object> params = new ArrayList<Object>();
		Pattern pattern = Pattern.compile(":[a-z0-9_]+");
		Matcher matcher = pattern.matcher(messageFormat);
		while (matcher.find()) {
			String key = matcher.group().replaceFirst(":", "");
			params.add(row.get(key));
		}
		String mf = messageFormat.replaceAll(":[a-z0-9_]+", "%s");
		String msg = String.format(mf, params.toArray()).trim();
		return msg;
	}

	/**
	* Performs the getLogFile operation
	* @return the getLogFile result
	* */
	private static File getLogFile() {
		String logFileNameFormat = config.getString("FileNameFormat", "`yyyyMMdd`.log");
		int startIndex = logFileNameFormat.indexOf("`");
		int endIndex = logFileNameFormat.lastIndexOf("`");
		if (startIndex > -1 && endIndex > -1) {
			String FileNameFormatStr = logFileNameFormat.substring(startIndex + 1, endIndex);
			SimpleDateFormat ldf = new SimpleDateFormat(FileNameFormatStr);
			String ff = ldf.format(new java.util.Date());
			String ffv = logFileNameFormat.replaceFirst("`.*`", ff);
			return new File(ffv);
		}
		return new File(logFileNameFormat);
	}

	/**
	* Performs the getLogFileName operation
	* @return the getLogFileName result
	* */
	public static String getLogFileName() {
	    return getLogFile().getName();
	}

	/**
	* Force-restore the JVM-wide stdout/stderr streams captured by
	* LogRecord (SystemOut=false mode). Host applications should call this on
	* shutdown or on fatal error paths so their console output is not
	* permanently swallowed after the engine redirects the streams.
	* */
	public static synchronized void restoreStdStreams() {
		try {
			if (customPrint != null) {
				System.setOut(originalOut);
				System.setErr(originalErr);
				customOutput.flush();
				customPrint.flush();
				customOutput.close();
				customPrint.close();
				customOutput = null;
				customPrint = null;
			}
		} catch (Exception e) {
			Logger.systemError(LogRecord.class, e);
		}
	}

	/**
	* Performs the isSystemOut operation
	* @return the isSystemOut result
	* */
	protected static boolean isSystemOut(){
	    return customPrint == null;
	}

	/**
	* For write log to database
	* @param logBatch - the logBatch
	* */
	private static synchronized void writeLogBatch(Map<String, List<String>> logBatch) {
		List<String> logDateRecord = new ArrayList<String>(logBatch.keySet());
		for (String logDate : logDateRecord) {
			File logFileNameFile = getLogFile();
			String logFileFormatName = logFileNameFile.getName();
			String logFileNamePrefix = logDate;
			String logFileNameSuffix = "log";
			int suffixIndex = logFileFormatName.lastIndexOf(".");
			if (suffixIndex > -1) {
				logFileNamePrefix = logFileFormatName.substring(0, suffixIndex);
				logFileNameSuffix = logFileFormatName.substring(suffixIndex + 1);
			}
			try{
				if (config.getBoolean("SystemOut", true)) {
				    if(customOutput != null){
				        customOutput.flush();
				        customPrint.flush();
				        String outMsg = customOutput.toString(BaseFile.CHARSET);

    					System.setOut(originalOut);
    	                System.setErr(originalErr);

    	                customOutput.close();
    	                customPrint.close();

    	                customOutput = null;
    	                customPrint = null;

        	            if(!CommonTools.isBlank(outMsg)){
        	                System.out.println(outMsg);
				        }
				    }
				}else{
				    if(customOutput == null){
    				    customOutput = new ByteArrayOutputStream();
    				    customPrint = new PrintStream(customOutput);
    				    System.setOut(customPrint);
        	            System.setErr(customPrint);
				    }
				}
			}catch(Exception e){
			    Logger.systemError(LogRecord.class, e);
			}
			try {
				String linesStr = String.join(System.lineSeparator(), logBatch.get(logDate)) + System.lineSeparator();
				if (config.getBoolean("FileOut", false)) {
					long fileOutMaxFileSize = config.getFileSize("FileOutMaxFileSize",1024*1024*10L);
					String logFileName = String.format("%s/%s.%s", fileOutFolder.getAbsolutePath(), logFileNamePrefix,
							logFileNameSuffix);
					File logFile = new File(logFileName);

					if(fileOutFolder.exists()){
					    if(!fileOutFolder.canWrite()) return;
					}else{
					    fileOutFolder.mkdirs();
					}

					if(logFile.exists()){
					    if(!logFile.canWrite()) return;
					}

					long logSize = FileTools.size(logFile);
					if (logSize < fileOutMaxFileSize) {
						FileTools.write(logFile, linesStr, true);
					} else {
						int logIndex = getLogFileIndex(logFileNamePrefix, logFileNameSuffix);
						if (logIndex == 0) {
						    String header = config.getString("MessageHeader");
						    if(CommonTools.isBlank(header)){
							    FileTools.write(logFile, linesStr, false);
						    }else{
						        FileTools.write(logFile, String.format("%s%s",header,System.lineSeparator()), false);
						        FileTools.write(logFile, linesStr, true);
						    }
						} else {
							String backupLogFileName = String.format("%s/%s.%s.%s", fileOutFolder.getAbsolutePath(),
									logFileNamePrefix, logIndex, logFileNameSuffix);
							Path source = Paths.get(logFile.getAbsolutePath());
							Path target = Paths.get(backupLogFileName);
							Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
						    String header = config.getString("MessageHeader");
						    if(CommonTools.isBlank(header)){
							    FileTools.write(logFile, linesStr, false);
						    }else{
						        FileTools.write(logFile, String.format("%s%s",header,System.lineSeparator()), false);
						        FileTools.write(logFile, linesStr, true);
						    }
						}
					}
				}
			} catch (Exception e) {
				Logger.systemError(LogRecord.class, e);
			}
		}
	}

	/**
	* For write log to database
	* @param list - the list
	* */
	private static void writeLogBatchToDatabase(List<Map<String, Object>> list) {
		try {
			if (config.getBoolean("DatabaseOut", false)) {

				if (LAST_REFRESH_CONFIG_TIME == null) {
					LAST_REFRESH_CONFIG_TIME = new Date();
					firstRefreshConfig = true;
				}
				long ms = new Date().getTime() - LAST_REFRESH_CONFIG_TIME.getTime();
				Long databaseOutRefreshConfigTimer = config.getMilliSeconds("DatabaseOutRefreshConfigTimer",
						60 * 1000L);
				String databaseOutDataSource = config.getString("DatabaseOutDataSource", "");
				String databaseOutArchiveExecute = config.getString("DatabaseOutArchiveExecute", null);
				String databaseOutInsertExecute = config.getString("DatabaseOutInsertExecute", null);
				String databaseOutConfigFrom = config.getString("DatabaseOutConfigFrom", null);
			    Integer databaseOutArchiveDays = config.getInteger("DatabaseOutArchiveDays",31);

				initDataSource(databaseOutDataSource);

				if (dataSource != null && !CommonTools.isBlank(databaseOutConfigFrom)
						&& (ms >= databaseOutRefreshConfigTimer || firstRefreshConfig)) {
					firstRefreshConfig = false;
					Map<String, Object> paramMap = new HashMap<String, Object>();
					paramMap.put("hostname", hostname);

					Map<String, Object> remoteConfig = first(databaseOutConfigFrom, paramMap);

					if (remoteConfig.containsKey("level")) {
						String removeLevel = (String) remoteConfig.get("level");
						if (Logger.checkLevel(removeLevel)) {
							level = removeLevel;
							Logger.setLevel(level);
						}
					} else {
						level = config.getString("Level", "DEBUG");
						Logger.setLevel(level);
					}

					if (remoteConfig.containsKey("class_name_enable_regex")) {
						String classNameEnableRegex = (String) remoteConfig.get("class_name_enable_regex");
						Logger.setClassNameEnableRegex(classNameEnableRegex);
					} else {
						String defaultClassNameEnableRegex = config.getString("ClassNameEnableRegex", null);
						Logger.setClassNameEnableRegex(defaultClassNameEnableRegex);
					}

					if (remoteConfig.containsKey("class_name_disable_regex")) {
						String classNameDisableRegex = (String) remoteConfig.get("class_name_disable_regex");
						Logger.setClassNameDisableRegex(classNameDisableRegex);
					} else {
						String classNameDisableRegex = config.getString("ClassNameDisableRegex", null);
						Logger.setClassNameDisableRegex(classNameDisableRegex);
					}

					LAST_REFRESH_CONFIG_TIME = new Date();
				}

				if (dataSource != null && !CommonTools.isBlank(databaseOutInsertExecute) && list.size() > 0) {
					batch(databaseOutInsertExecute, list);
				}

				if (dataSource != null && !CommonTools.isBlank(databaseOutArchiveExecute)) {
				    // Archive throttling: the archive scan used to run on every flush
				    // (OutLogTimer, ~1/sec); DatabaseOutArchiveTimer (ms, default 60s)
				    // caps its frequency so the condition scan does not hammer a large table.
				    long archiveTimerMs = config.getMilliSeconds("DatabaseOutArchiveTimer", 60000L);
				    long nowMs = System.currentTimeMillis();
				    if (nowMs - lastArchiveExecuteTime >= archiveTimerMs) {
				        lastArchiveExecuteTime = nowMs;
				        long archiveTime = nowMs - databaseOutArchiveDays*24*3600*1000L;
					    Map<String, Object> paramMap = new HashMap<String, Object>();
					    paramMap.put("hostname", hostname);
					    paramMap.put("archive_time",new Timestamp(archiveTime));
					    execute(databaseOutArchiveExecute, paramMap);
				    }
				}
			}
		} catch (Exception e) {
			Logger.systemError(LogRecord.class, e.getMessage(),e);
		}
	}

	/** Last failed datasource init time (DB offline reconnect back-off). */
	private static volatile long lastInitFailAt = 0L;

	/** Last archive execution time (archive frequency throttle). */
	private static volatile long lastArchiveExecuteTime = 0L;

	/**
	* For write log to database
	* @param loadDatabaseOutDataSource - the loadDatabaseOutDataSource
	* */
	private static synchronized void initDataSource(String loadDatabaseOutDataSource) {
		try {
		    if(!appDataSourcePath.equals(loadDatabaseOutDataSource)){
		        if(dataSource != null){
		            dataSource.closeAll();
		            dataSource = null;
		        }
		        appDataSourcePath = loadDatabaseOutDataSource;
		    }
		    if(dataSource == null){
		        long retryTimerMs = config.getMilliSeconds("DatabaseOutRetryTimer", 30000L);
		        if (System.currentTimeMillis() - lastInitFailAt < retryTimerMs) {
		            return; // DB offline: back off reconnect attempts
		        }
		        File appDataSource = new File(appDataSourcePath);
                dataSource = new DriverDataSource(appDataSource);
		    }
		} catch (Exception e) {
			lastInitFailAt = System.currentTimeMillis();
			Logger.systemError(LogRecord.class, e.getMessage(),e);
		}
	}

	/**
	* Archive Log
	* */
	private static void archiveLog() {
		Integer fileOutArchiveDays = config.getInteger("FileOutArchiveDays", 30);
		Boolean fileOut = config.getBoolean("FileOut", false);
		if (fileOutArchiveDays > 0 && fileOut && fileOutFolder != null) {
			try {
				long fileOutArchiveDaysMs = new Date().getTime() - (fileOutArchiveDays * 24 * 3600000L);
				deleteFilesOlderThan(fileOutFolder, fileOutArchiveDaysMs);
			} catch (Exception e) {
				Logger.systemError(LogRecord.class, e);
			}
		}
	}

	/**
	* Delete old log file
	* @param directory - the directory
	* @param fileOutArchiveDaysMs - the fileOutArchiveDaysMs
	* */
	private static void deleteFilesOlderThan(File directory, long fileOutArchiveDaysMs) throws IOException {
		if (directory.isDirectory()) {
			File[] files = directory.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
						boolean canWrite = file.canWrite();
						if (canWrite) {
							long lastModified = file.lastModified();
							if (lastModified < fileOutArchiveDaysMs) {
								Files.deleteIfExists(Paths.get(file.toURI()));
							}
						}
					}
				}
			}
		}
	}

	/**
	* For write log to database
	* @param sql - the sql
	* @param params - the params
	* */
	public static Map<String, Object> first(String sql, Map<String, Object> params) throws Exception {
		Logger.systemDebug(LogRecord.class, "Sql={}", sql);
		Logger.systemDebug(LogRecord.class, "Params={}", params);
		CacheDriverExecutor driverExecutor = null;
		try {
			Connection conn = dataSource.getConnection();
			conn.setAutoCommit(true);
			driverExecutor = new CacheDriverExecutor(conn);
			return driverExecutor.first(sql, params);
		} finally {
			if (driverExecutor != null)
				driverExecutor.close();
		}
	}

	/**
	* For write log to database
	* @param sql - the sql
	* @param params - the params
	* */
	private static int execute(String sql, Map<String, Object> params) throws Exception {
		Logger.systemDebug(LogRecord.class, "Sql={}", sql);
		Logger.systemDebug(LogRecord.class, "Params={}", params);
		CacheDriverExecutor driverExecutor = null;
		try {
			Connection conn = dataSource.getConnection();
			conn.setAutoCommit(true);
			driverExecutor = new CacheDriverExecutor(conn);
			return driverExecutor.execute(sql, params);
		} finally {
			if (driverExecutor != null)
				driverExecutor.close();
		}
	}

	/**
	* For write log to database
	* @param sql - the sql
	* @param recordsPart - the recordsPart
	* */
	private static synchronized int batchPart(String sql, List<Map<String, Object>> recordsPart) throws Exception {
		CacheDriverExecutor driverExecutor = null;
		try {
			Connection conn = dataSource.getConnection();
			conn.setAutoCommit(false);
			driverExecutor = new CacheDriverExecutor(conn);
			return driverExecutor.executeBatch(sql, recordsPart);
		} finally {
			if (driverExecutor != null)
				driverExecutor.close();
		}
	}

	/**
	* For write log to database
	* @param sql - the sql
	* @param records - the records
	* */
	private static synchronized int batch(String sql, List<Map<String, Object>> records) throws Exception {
		Logger.systemDebug(LogRecord.class, "Sql={}", sql);
		Logger.systemDebug(LogRecord.class, "Batch Size={}", records.size());
		int rows = 0;
		Integer databaseOutBatchLimit = config.getInteger("DatabaseOutBatchLimit", 100);
		List<List<Map<String, Object>>> recordsParts = CommonTools.averageAssign(records, databaseOutBatchLimit);
		for (List<Map<String, Object>> recordsPart : recordsParts) {
			int row = batchPart(sql, recordsPart);
			rows += row;
		}
		return rows;
	}

	/**
	* Get data source
	* @return DataSource
	* */
	public static DataSource getDataSource(){
	    return dataSource;
	}

	/**
	* Start write log thread
	* */
	protected static synchronized void start() {
		if (!started) {
			started = true;

			try {
				String alertMessageClazz = config.getString("AlertMessage.class");
				String[] alertMessageLevels = config.getString("AlertMessage.level", "DEBUG,INFO,WARN,ERROR,MARK").trim()
						.toUpperCase().split("[^\\S]*,[^\\S]*");
				alertMessageLevelList = Arrays.asList(alertMessageLevels);
				if (!CommonTools.isBlank(alertMessageClazz)) {
					Class<?> amc = Class.forName(alertMessageClazz);
					alertMesage = (AlertMessage) amc.getDeclaredConstructor().newInstance();
				}
			} catch (Exception e) {
				Logger.systemError(LogRecord.class, e);
			}

			ExecutorService executor = Executors.newFixedThreadPool(1);
			executor.execute(new Runnable() {

				/*
				* Performs the run operation
				* */
				@Override
				public void run() {
					Thread.currentThread().setName(Logger.class.getSimpleName());
					while (true) {

					    if(Thread.currentThread().isInterrupted()) break;

						try {
							final List<Map<String, Object>> list = list();
							Map<String, List<String>> logBatch = getLogBatch(list);
							writeLogBatch(logBatch);
							if (config.getBoolean("DatabaseOut", false)) {
    							DB_WRITE_POOL.execute(new Runnable(){
    							    /*
    							    * Performs the run operation
    							    * */
    							    @Override
    							    public void run(){
    							        LogRecord.writeLogBatchToDatabase(list);
    							    }
    							});
							}
							archiveLog();
						} catch (Exception e) {
							Logger.systemError(LogRecord.class, e);
						}

						try {
							if (Thread.currentThread().isInterrupted()) {
								break;
							} else {
								Thread.sleep(config.getMilliSeconds("OutLogTimer", 1000L));
							}
						} catch (InterruptedException e) {
							Logger.systemError(LogRecord.class, e);
							break;
						}
					}

				}

			});
		}
	}

}
