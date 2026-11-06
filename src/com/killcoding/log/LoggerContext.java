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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.killcoding.datasource.CacheDriverExecutor;
import com.killcoding.datasource.DriverDataSource;
import com.killcoding.file.BaseFile;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;
import com.killcoding.tool.FileTools;

/**
* One logging bucket: an independent configuration (Logger.properties file),
* queue, flusher thread, file archive and optional database output.
*
* <p>A bucket is registered through {@link LoggerFactory#register(File)}; its key is
* md5(normalizePath(properties file absolute path)), and its SystemName is the
* {@code SystemName} entry of that file (falling back to the file base name when
* the entry is absent). Several buckets can coexist in one JVM and write to
* different folders with different levels and formats.</p>
*
* <p>The JVM-wide standard streams ({@code System.out}/{@code System.err}) can only be
* captured once, so only the default bucket (the first registered one) handles the
* {@code SystemOut} switch; other buckets write to their files/database only.</p>
* */
public final class LoggerContext {

	protected final static String DEFAULT_MESSAGE_FORMAT = ":level :log_time_format [:log_thread_name-:log_thread_id] :log_class_name.:log_method_name(:log_file_name::log_line_number) **:message** \r\n:stack_trace";

	private final String bucketKey;

	private final File configFile;

	private volatile String systemName = null;

	private final Queue<Map<String, Object>> LOG_RECORD_POOL = new ConcurrentLinkedQueue<Map<String, Object>>();

	private volatile ConfigProperties config = null;

	private volatile File fileOutFolder = null;

	private volatile Integer level = Logger.LEVEL_DEBUG;

	private volatile String classNameEnableRegex = null;

	private volatile String classNameDisableRegex = null;

	private volatile String hostname = null;

	private volatile boolean started = false;

	private volatile boolean firstRefreshConfig = false;

	private volatile Date LAST_REFRESH_CONFIG_TIME = null;

	private volatile String appDataSourcePath = "";

	private volatile DriverDataSource dataSource = null;

	private volatile AlertMessage alertMesage = null;

	private volatile List<String> alertMessageLevelList = Arrays.asList(new String[] { "DEBUG", "INFO", "WARN",
			"ERROR", "MARK" });

	private volatile long lastInitFailAt = 0L;

	private volatile long lastArchiveExecuteTime = 0L;

	private ExecutorService flusher = null;

	private ExecutorService dbWritePool = null;

	/** True only for the default bucket: it owns System.out / System.err capture. */
	private boolean stdStreamOwner = false;

	private PrintStream originalOut = null;

	private PrintStream originalErr = null;

	private ByteArrayOutputStream customOutput = null;

	private PrintStream customPrint = null;

	/**
	* Constructs a logging bucket
	* @param bucketKey - md5(normalizePath(properties file absolute path))
	* @param configFile - the Logger.properties file of this bucket
	* */
	LoggerContext(String bucketKey, File configFile) {
		this.bucketKey = bucketKey;
		this.configFile = configFile;
	}

	/**
	* Returns the bucket key
	* @return the key
	* */
	public String getBucketKey() {
		return bucketKey;
	}

	/**
	* Returns the properties file of this bucket
	* @return the file
	* */
	public File getConfigFile() {
		return configFile;
	}

	/**
	* Returns the SystemName of this bucket
	* @return the system name
	* */
	public String getSystemName() {
		return systemName;
	}

	/**
	* Sets the SystemName of this bucket
	* @param systemName - the system name
	* */
	protected void setSystemName(String systemName) {
		this.systemName = systemName;
	}

	/**
	* Marks this bucket as the owner of the JVM-wide standard streams
	* */
	protected void setStdStreamOwner() {
		this.stdStreamOwner = true;
	}

	/**
	* Returns the current configuration of this bucket
	* @return the config
	* */
	public ConfigProperties getConfig() {
		return config;
	}

	/**
	* Returns the log output folder of this bucket
	* @return the folder
	* */
	public File getFileOutFolder() {
		return fileOutFolder;
	}

	/**
	* Returns the current level of this bucket
	* @return the level
	* */
	public Integer getLevel() {
		return level;
	}

	/**
	* Returns the class-name allowlist regex of this bucket
	* @return the regex or null
	* */
	public String getClassNameEnableRegex() {
		return classNameEnableRegex;
	}

	/**
	* Returns the class-name denylist regex of this bucket
	* @return the regex or null
	* */
	public String getClassNameDisableRegex() {
		return classNameDisableRegex;
	}

	/**
	* Check whether a record of the given level passes this bucket's level
	* @param messageLevel - the level of the record
	* @return boolean
	* */
	public boolean isEnabled(Integer messageLevel) {
		Integer currentLevel = level;
		if (currentLevel == null)
			currentLevel = Logger.LEVEL_DEBUG;
		if (messageLevel == null)
			return false;
		return currentLevel <= messageLevel;
	}

	/**
	* Check whether a class/method name passes this bucket's regex filters
	* @param logClassName - the class.method name
	* @return boolean
	* */
	public boolean isClassEnabled(String logClassName) {
		if (!ConfigProperties.isBlank(classNameEnableRegex) && !logClassName.matches(classNameEnableRegex)) {
			return false;
		}
		if (!ConfigProperties.isBlank(classNameDisableRegex) && logClassName.matches(classNameDisableRegex)) {
			return false;
		}
		return true;
	}

	/**
	* Set the configuration of this bucket (level, folder, filters, alert)
	* @param _config - the config
	* */
	protected synchronized void setConfig(ConfigProperties _config) {
		config = _config;
		String fileOutFolderConfig = config.getString("FileOutFolder", null);
		if (fileOutFolderConfig == null) {
			try {
				String jarPath = new File(LoggerContext.class.getProtectionDomain().getCodeSource().getLocation()
						.toURI()).getPath();
				fileOutFolder = new File(String.format("%s/logs/", jarPath));
			} catch (Exception e) {
				Logger.systemWarn(LoggerContext.class, e);
			}
		} else {
			fileOutFolder = new File(fileOutFolderConfig);
		}
		String levelStr = config.getString("Level", "DEBUG");
		if (Logger.checkLevel(levelStr)) {
			level = Logger.LEVEL_LIST.indexOf(levelStr.toUpperCase());
		} else {
			level = Logger.LEVEL_DEBUG;
		}
		classNameEnableRegex = config.getString("ClassNameEnableRegex", null);
		classNameDisableRegex = config.getString("ClassNameDisableRegex", null);
		hostname = CommonTools.getHostname();
		// The static system logger channel (Logger.systemXxx) always follows the
		// default bucket's level; other buckets only affect their own loggers.
		if (stdStreamOwner) {
			Logger.setLevel(level);
		}
	}

	/**
	* Returns the log time format of this bucket
	* @return SimpleDateFormat
	* */
	protected SimpleDateFormat getLogTimeFormat() {
		String defValue = "yyyyMMdd HH:mm:ss.SSS";
		ConfigProperties cfg = config;
		if (cfg == null) {
			return new SimpleDateFormat(defValue);
		}
		return new SimpleDateFormat(cfg.getString("LogTimeFormat", defValue));
	}

	/**
	* Returns the log date format of this bucket
	* @return SimpleDateFormat
	* */
	protected SimpleDateFormat getLogDateFormat() {
		String defValue = "yyyyMMdd";
		ConfigProperties cfg = config;
		if (cfg == null) {
			return new SimpleDateFormat(defValue);
		}
		return new SimpleDateFormat(cfg.getString("LogDateFormat", defValue));
	}

	/**
	* Is the flusher of this bucket started
	* @return boolean
	* */
	public boolean isStarted() {
		return started;
	}

	/**
	* Returns the current log-record list of this bucket (drains the queue)
	* @return List&lt;Map&lt;String, Object&gt;&gt;
	* */
	@SuppressWarnings("unchecked")
	public synchronized List<Map<String, Object>> list() {
		return (List<Map<String, Object>>) process("LIST", null);
	}

	/**
	* Get the log list size of this bucket
	* @return int
	* */
	public synchronized int size() {
		return (Integer) process("SIZE", null);
	}

	/**
	* Add a log record to this bucket
	* @param o - the record
	* */
	protected synchronized void add(Map<String, Object> o) {
		process("ADD", o);
		if (o != null) {
			String recordLevel = (String) o.get("level");
			if (recordLevel != null && alertMessageLevelList != null && alertMessageLevelList.contains(recordLevel)) {
				pushAlertMessage(o);
			}
		}
	}

	/**
	* Remove a log record from this bucket
	* @param o - the record
	* */
	protected synchronized void remove(Map<String, Object> o) {
		process("REMOVE", o);
	}

	/**
	* Push a log record to the alert handler of this bucket
	* @param o - the record
	* */
	protected void pushAlertMessage(Map<String, Object> o) {
		AlertMessage alert = alertMesage;
		if (alert != null) {
			alert.push(o);
		}
	}

	/**
	* Performs the queue action
	* @param action - ADD / SIZE / LIST / REMOVE
	* @param o - the record
	* @return the result
	* */
	private Object process(String action, Map<String, Object> o) {
		if ("ADD".equals(action)) {
			ConfigProperties cfg = config;
			Integer outLogBatchLimit = cfg == null ? 1000 : cfg.getInteger("OutLogBatchLimit", 1000);
			if (LOG_RECORD_POOL.size() >= outLogBatchLimit) {
				LOG_RECORD_POOL.poll();
			}
			if (o != null) {
				o.put("system_name", cfg == null ? systemName : cfg.getString("SystemName"));
				LOG_RECORD_POOL.offer(o);
			}
		}

		if ("SIZE".equals(action)) {
			return LOG_RECORD_POOL.size();
		}

		if ("LIST".equals(action)) {
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
	* @return the index
	* */
	private int getLogFileIndex(String prefix, String suffix) {
		Integer fileOutMaxBackupIndex = config.getInteger("FileOutMaxBackupIndex", 10);
		for (int i = 1; i <= fileOutMaxBackupIndex; i++) {
			String logFileName = String.format("%s/%s.%s.%s", fileOutFolder.getAbsolutePath(), prefix, i, suffix);
			File logFile = new File(logFileName);
			if (!logFile.exists())
				return i;
		}
		return 0;
	}

	/**
	* Build the batch of log lines grouped by date
	* @param list - the records
	* @return Map date -&gt; lines
	* */
	private Map<String, List<String>> getLogBatch(List<Map<String, Object>> list) {
		Map<String, List<String>> logBatch = new HashMap<String, List<String>>();
		for (Map<String, Object> row : list) {
			Date logTime = (Date) row.get("log_time");
			String logDateStr = getLogDateFormat().format(logTime);
			String msg = LogRecord.format(config.getString("MessageFormat", DEFAULT_MESSAGE_FORMAT), row);
			List<String> batchLines = logBatch.get(logDateStr);
			if (batchLines == null) {
				batchLines = new ArrayList<String>();
			}
			batchLines.add(msg);

			if (stdStreamOwner && customOutput != null) {
				try {
					customOutput.flush();
					customPrint.flush();
					String outMsg = customOutput.toString(BaseFile.CHARSET);
					if (!CommonTools.isBlank(outMsg)) {
						customOutput = new ByteArrayOutputStream();
						customPrint = new PrintStream(customOutput);
						System.setOut(customPrint);
						System.setErr(customPrint);
						Logger.systemMark(LoggerContext.class, "SystemOut: {}", outMsg);
					}
				} catch (Exception e) {
					Logger.systemError(LoggerContext.class, e.getMessage(), e);
				}
			}

			logBatch.put(logDateStr, batchLines);
		}
		return logBatch;
	}

	/**
	* Resolve the current log file of this bucket
	* @return File
	* */
	private File getLogFile() {
		String logFileNameFormat = config.getString("FileNameFormat", "`yyyyMMdd`.log");
		int startIndex = logFileNameFormat.indexOf("`");
		int endIndex = logFileNameFormat.lastIndexOf("`");
		if (startIndex > -1 && endIndex > -1) {
			String FileNameFormatStr = logFileNameFormat.substring(startIndex + 1, endIndex);
			SimpleDateFormat ldf = new SimpleDateFormat(FileNameFormatStr);
			String ff = ldf.format(new Date());
			String ffv = logFileNameFormat.replaceFirst("`.*`", ff);
			return new File(ffv);
		}
		return new File(logFileNameFormat);
	}

	/**
	* Returns the current log file name of this bucket
	* @return the name
	* */
	public String getLogFileName() {
		return getLogFile().getName();
	}

	/**
	* Force-restore the JVM-wide stdout/stderr streams (default bucket only)
	* */
	public synchronized void restoreStdStreams() {
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
			Logger.systemError(LoggerContext.class, e);
		}
	}

	/**
	* Is the standard output currently not captured (default bucket only)
	* @return boolean
	* */
	protected boolean isSystemOut() {
		return customPrint == null;
	}

	/**
	* Write the batch to the log file of this bucket
	* @param logBatch - the batch
	* */
	private synchronized void writeLogBatch(Map<String, List<String>> logBatch) {
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
			if (stdStreamOwner) {
				try {
					boolean systemOutConfig = config.getBoolean("SystemOut", true);
					if (systemOutConfig) {
						if (customOutput != null) {
							customOutput.flush();
							customPrint.flush();
							String outMsg = customOutput.toString(BaseFile.CHARSET);
							System.setOut(originalOut);
							System.setErr(originalErr);
							customOutput.close();
							customPrint.close();
							customOutput = null;
							customPrint = null;
							if (!CommonTools.isBlank(outMsg)) {
								System.out.println(outMsg);
							}
						}
					} else {
						if (customOutput == null) {
							customOutput = new ByteArrayOutputStream();
							customPrint = new PrintStream(customOutput);
							System.setOut(customPrint);
							System.setErr(customPrint);
						}
					}
				} catch (Exception e) {
					Logger.systemError(LoggerContext.class, e);
				}
			}
			try {
				String linesStr = String.join(System.lineSeparator(), logBatch.get(logDate))
						+ System.lineSeparator();
				if (config.getBoolean("FileOut", false)) {
					long fileOutMaxFileSize = config.getFileSize("FileOutMaxFileSize", 1024 * 1024 * 10L);
					String logFileName = String.format("%s/%s.%s", fileOutFolder.getAbsolutePath(),
							logFileNamePrefix, logFileNameSuffix);
					File logFile = new File(logFileName);

					if (fileOutFolder.exists()) {
						if (!fileOutFolder.canWrite())
							return;
					} else {
						fileOutFolder.mkdirs();
					}

					if (logFile.exists()) {
						if (!logFile.canWrite())
							return;
					}

					long logSize = FileTools.size(logFile);
					if (logSize < fileOutMaxFileSize) {
						FileTools.write(logFile, linesStr, true);
					} else {
						int logIndex = getLogFileIndex(logFileNamePrefix, logFileNameSuffix);
						if (logIndex == 0) {
							String header = config.getString("MessageHeader");
							if (CommonTools.isBlank(header)) {
								FileTools.write(logFile, linesStr, false);
							} else {
								FileTools.write(logFile, String.format("%s%s", header, System.lineSeparator()),
										false);
								FileTools.write(logFile, linesStr, true);
							}
						} else {
							String backupLogFileName = String.format("%s/%s.%s.%s",
									fileOutFolder.getAbsolutePath(), logFileNamePrefix, logIndex, logFileNameSuffix);
							Path source = Paths.get(logFile.getAbsolutePath());
							Path target = Paths.get(backupLogFileName);
							Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
							String header = config.getString("MessageHeader");
							if (CommonTools.isBlank(header)) {
								FileTools.write(logFile, linesStr, false);
							} else {
								FileTools.write(logFile, String.format("%s%s", header, System.lineSeparator()),
										false);
								FileTools.write(logFile, linesStr, true);
							}
						}
					}
				}
			} catch (Exception e) {
				Logger.systemError(LoggerContext.class, e);
			}
		}
	}

	/**
	* Write the batch to the database of this bucket
	* @param list - the records
	* */
	private void writeLogBatchToDatabase(List<Map<String, Object>> list) {
		try {
			if (config.getBoolean("DatabaseOut", false)) {

				if (LAST_REFRESH_CONFIG_TIME == null) {
					LAST_REFRESH_CONFIG_TIME = new Date();
					firstRefreshConfig = true;
				}
				long ms = new Date().getTime() - LAST_REFRESH_CONFIG_TIME.getTime();
				Long databaseOutRefreshConfigTimer = config.getMilliSeconds("DatabaseOutRefreshConfigTimer",
						60 * 1000L);
				String databaseOutDataSourceConfig = config.getString("DatabaseOutDataSource", "");
				String databaseOutArchiveExecute = config.getString("DatabaseOutArchiveExecute", null);
				String databaseOutInsertExecute = config.getString("DatabaseOutInsertExecute", null);
				String databaseOutConfigFrom = config.getString("DatabaseOutConfigFrom", null);
				Integer databaseOutArchiveDays = config.getInteger("DatabaseOutArchiveDays", 31);

				initDataSource(databaseOutDataSourceConfig);

				if (dataSource != null && !CommonTools.isBlank(databaseOutConfigFrom)
						&& (ms >= databaseOutRefreshConfigTimer || firstRefreshConfig)) {
					firstRefreshConfig = false;
					Map<String, Object> paramMap = new HashMap<String, Object>();
					paramMap.put("hostname", hostname);

					Map<String, Object> remoteConfig = first(databaseOutConfigFrom, paramMap);

					if (remoteConfig.containsKey("level")) {
						String removeLevel = (String) remoteConfig.get("level");
						if (Logger.checkLevel(removeLevel)) {
							level = Logger.LEVEL_LIST.indexOf(removeLevel.toUpperCase());
						}
					} else {
						String levelStr = config.getString("Level", "DEBUG");
						level = Logger.checkLevel(levelStr) ? Logger.LEVEL_LIST.indexOf(levelStr.toUpperCase())
								: Logger.LEVEL_DEBUG;
					}

					if (remoteConfig.containsKey("class_name_enable_regex")) {
						classNameEnableRegex = (String) remoteConfig.get("class_name_enable_regex");
					} else {
						classNameEnableRegex = config.getString("ClassNameEnableRegex", null);
					}

					if (remoteConfig.containsKey("class_name_disable_regex")) {
						classNameDisableRegex = (String) remoteConfig.get("class_name_disable_regex");
					} else {
						classNameDisableRegex = config.getString("ClassNameDisableRegex", null);
					}

					LAST_REFRESH_CONFIG_TIME = new Date();
				}

				if (dataSource != null && !CommonTools.isBlank(databaseOutInsertExecute) && list.size() > 0) {
					batch(databaseOutInsertExecute, list);
				}

				if (dataSource != null && !CommonTools.isBlank(databaseOutArchiveExecute)) {
					long archiveTimerMs = config.getMilliSeconds("DatabaseOutArchiveTimer", 60000L);
					long nowMs = System.currentTimeMillis();
					if (nowMs - lastArchiveExecuteTime >= archiveTimerMs) {
						lastArchiveExecuteTime = nowMs;
						long archiveTime = nowMs - databaseOutArchiveDays * 24 * 3600 * 1000L;
						Map<String, Object> paramMap = new HashMap<String, Object>();
						paramMap.put("hostname", hostname);
						paramMap.put("archive_time", new Timestamp(archiveTime));
						execute(databaseOutArchiveExecute, paramMap);
					}
				}
			}
		} catch (Exception e) {
			Logger.systemError(LoggerContext.class, e.getMessage(), e);
		}
	}

	/**
	* Init the database output datasource of this bucket
	* @param loadDatabaseOutDataSource - the datasource path
	* */
	private synchronized void initDataSource(String loadDatabaseOutDataSource) {
		try {
			if (!appDataSourcePath.equals(loadDatabaseOutDataSource)) {
				if (dataSource != null) {
					dataSource.closeAll();
					dataSource = null;
				}
				appDataSourcePath = loadDatabaseOutDataSource;
			}
			if (dataSource == null) {
				long retryTimerMs = config.getMilliSeconds("DatabaseOutRetryTimer", 30000L);
				if (System.currentTimeMillis() - lastInitFailAt < retryTimerMs) {
					return;
				}
				File appDataSource = new File(appDataSourcePath);
				dataSource = new DriverDataSource(appDataSource);
			}
		} catch (Exception e) {
			lastInitFailAt = System.currentTimeMillis();
			Logger.systemError(LoggerContext.class, e.getMessage(), e);
		}
	}

	/**
	* Archive old log files of this bucket
	* */
	private void archiveLog() {
		Integer fileOutArchiveDays = config.getInteger("FileOutArchiveDays", 30);
		Boolean fileOut = config.getBoolean("FileOut", false);
		if (fileOutArchiveDays > 0 && fileOut && fileOutFolder != null) {
			try {
				long fileOutArchiveDaysMs = new Date().getTime() - (fileOutArchiveDays * 24 * 3600000L);
				deleteFilesOlderThan(fileOutFolder, fileOutArchiveDaysMs);
			} catch (Exception e) {
				Logger.systemError(LoggerContext.class, e);
			}
		}
	}

	/**
	* Delete log files older than the archive threshold
	* @param directory - the directory
	* @param fileOutArchiveDaysMs - the threshold
	* */
	private void deleteFilesOlderThan(File directory, long fileOutArchiveDaysMs) throws IOException {
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
	* Query one row from the database of this bucket
	* @param sql - the sql
	* @param params - the params
	* @return Map
	* @throws Exception
	* */
	public Map<String, Object> first(String sql, Map<String, Object> params) throws Exception {
		Logger.systemDebug(LoggerContext.class, "Sql={}", sql);
		Logger.systemDebug(LoggerContext.class, "Params={}", params);
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
	* Execute a statement on the database of this bucket
	* @param sql - the sql
	* @param params - the params
	* @return int
	* @throws Exception
	* */
	private int execute(String sql, Map<String, Object> params) throws Exception {
		Logger.systemDebug(LoggerContext.class, "Sql={}", sql);
		Logger.systemDebug(LoggerContext.class, "Params={}", params);
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
	* Batch a part of records into the database of this bucket
	* @param sql - the sql
	* @param recordsPart - the records
	* @return int
	* @throws Exception
	* */
	private synchronized int batchPart(String sql, List<Map<String, Object>> recordsPart) throws Exception {
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
	* Batch records into the database of this bucket
	* @param sql - the sql
	* @param records - the records
	* @return int
	* @throws Exception
	* */
	private synchronized int batch(String sql, List<Map<String, Object>> records) throws Exception {
		Logger.systemDebug(LoggerContext.class, "Sql={}", sql);
		Logger.systemDebug(LoggerContext.class, "Batch Size={}", records.size());
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
	* Returns the data source of this bucket (database output)
	* @return DataSource
	* */
	public DriverDataSource getDataSource() {
		return dataSource;
	}

	/**
	* Start the flusher of this bucket (idempotent)
	* */
	protected synchronized void start() {
		if (started) {
			return;
		}
		started = true;

		originalOut = System.out;
		originalErr = System.err;

		try {
			String alertMessageClazz = config.getString("AlertMessage.class");
			String[] alertMessageLevels = config
					.getString("AlertMessage.level", "DEBUG,INFO,WARN,ERROR,MARK").trim().toUpperCase()
					.split("[^\\S]*,[^\\S]*");
			alertMessageLevelList = Arrays.asList(alertMessageLevels);
			if (!CommonTools.isBlank(alertMessageClazz)) {
				Class<?> amc = Class.forName(alertMessageClazz);
				alertMesage = (AlertMessage) amc.getDeclaredConstructor().newInstance();
			}
		} catch (Exception e) {
			Logger.systemError(LoggerContext.class, e);
		}

		dbWritePool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1024),
				r -> {
					Thread t = new Thread(r, "LogRecord-DB-Writer");
					t.setDaemon(true);
					return t;
				}, new ThreadPoolExecutor.CallerRunsPolicy());

		flusher = Executors.newFixedThreadPool(1, r -> {
			Thread t = new Thread(r, "Logger-Flusher-" + (systemName == null ? bucketKey : systemName));
			t.setDaemon(true);
			return t;
		});

		final LoggerContext context = this;
		flusher.execute(new Runnable() {

			/*
			* Performs the run operation
			* */
			@Override
			public void run() {
				Thread.currentThread().setName(Logger.class.getSimpleName());
				while (true) {

					if (Thread.currentThread().isInterrupted())
						break;

					try {
						final List<Map<String, Object>> list = context.list();
						Map<String, List<String>> logBatch = context.getLogBatch(list);
						context.writeLogBatch(logBatch);
						if (context.getConfig() != null && context.getConfig().getBoolean("DatabaseOut", false)) {
							final ExecutorService pool = context.dbWritePool;
							if (pool != null) {
								pool.execute(new Runnable() {
									/*
									* Performs the run operation
									* */
									@Override
									public void run() {
										context.writeLogBatchToDatabase(list);
									}
								});
							}
						}
						context.archiveLog();
					} catch (Exception e) {
						Logger.systemError(LoggerContext.class, e);
					}

					try {
						if (Thread.currentThread().isInterrupted()) {
							break;
						} else {
							long outLogTimer = context.getConfig() == null ? 1000L
									: context.getConfig().getMilliSeconds("OutLogTimer", 1000L);
							Thread.sleep(outLogTimer);
						}
					} catch (InterruptedException e) {
						Logger.systemError(LoggerContext.class, e);
						break;
					}
				}
			}

		});
	}

}
