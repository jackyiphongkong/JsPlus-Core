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
package com.killcoding.file;

import java.io.File;
import com.killcoding.datasource.DriverDataSource;
import com.killcoding.tool.ConfigProperties;
import com.killcoding.tool.CommonTools;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import com.killcoding.datasource.CacheDriverExecutor;
import java.util.Map;
import java.util.HashMap;
import com.killcoding.datasource.Clock;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

import java.io.ByteArrayOutputStream;

import java.nio.file.Paths;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import com.killcoding.file.DiskFile;

import com.killcoding.cache.CacheArray;
import com.killcoding.cache.CacheArrayFilter;


import com.killcoding.datasource.IdentityCard;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import com.killcoding.tool.CipherTools;
import com.killcoding.cache.Cache;
import java.util.Date;

/**
 * RemoteFileBase - implementation layer of the remote (database-backed) file engine.
 * Holds all shared state (static registry maps, identity-card/encryption keys, per-file
 * datasource/table/dsKey bindings) and the private/protected machinery (SQL access,
 * shard read/write engine, async archive/sync scheduling, encryption). The public
 * facade RemoteFile extends this class and exposes only its public API.
 * */
public abstract class RemoteFileBase extends BaseFile implements RemoteFileSQL {

	/** @param path the remote path this file object operates on */
	protected RemoteFileBase(String path) { super(path); }

	/** @param origin the origin File backing this remote file object */
	protected RemoteFileBase(File origin) { super(origin); }

	/** Overload of BaseFile.isDebug(RemoteFile) for this implementation layer. */
	protected static boolean isDebug(RemoteFileBase remoteFile) {
		boolean debug = false;
		if (remoteFile != null) {
			boolean fileDebug = remoteFile.getOrigin().getName().matches("^(\\.{0,1}DEBUG[\\.\\d\\w]*)$");
			boolean logDeubg = remoteFile.configProperties != null
					? remoteFile.configProperties.getBoolean("LogCompletedStatus", false) : false;
			debug = fileDebug || logDeubg;
		}
		return debug || LoggerFactory.getLogger(BaseFile.class).isDebugEnabled();
	}


	/** How long waitForCopy() polls for a remote copy to complete. */
	protected static final long COPY_WAIT_TIMEOUT = 15000L;
	/** Poll interval used by the copy-wait loop. */

	protected static final long COPY_WAIT_POLL_MS = 200L;
	/** Max time merge() waits for the async CacheArray worker to finish. */

	private static final long MERGE_WAIT_TIMEOUT = 300 * 1000L;

	private static final Map<String, DriverDataSource> DATASOURCE_REMOTE_FILE = new ConcurrentHashMap<String, DriverDataSource>();

	private static final Map<String, ConfigProperties> CONFIG_PROPS_REMOTE_FILE = new ConcurrentHashMap<String, ConfigProperties>();

	private static final Map<String, Long> QUEUE_PATH_MAPPING = new ConcurrentHashMap<String, Long>();

	private static final Map<String, ConfigProperties> DATA_MAPPING = new ConcurrentHashMap<String, ConfigProperties>();

	private static final Map<String, List<String>> DATA_MAPPING_LIST = new ConcurrentHashMap<String, List<String>>();

	private static final Map<String, Integer> DATA_MAPPING_INDEX = new ConcurrentHashMap<String, Integer>();

    protected static boolean FIRST_LOADED = false;

    public static Integer MAX_POOL_SIZE = 100;

	public static Double MAX_CONNECT_USAGE = 0.5D;

	public static Double MAX_MEMORY_USAGE = 0.8D;

	public static Integer MAX_QUEUE_SIZE = 10;

	public static Long MAX_FILE_SZIE = -1L;

	public static boolean SHOW_LINK = false;

	public static boolean SHOW_HIDDEN = false;

	public static boolean COPY_STRUCTURE_ONLY = false;

	public static Integer MAX_FILE_PARENT_PATH_LENGTH = 200;

	public static Integer MAX_FILE_NAME_LENGTH = 200;

	public static Integer CACHE_SECONDS = 5;

	public static Date SYNC_CUTOFF_TIME = null;

	public static Date DELETE_CUTOFF_TIME = null;

	private static IdentityCard identityCard = null;

	private static File appRemoteFile = null;

	protected DriverDataSource dataSourceReader = null;

	protected DriverDataSource dataSourceWriter = null;

	private String dataSourceReaderPath = null;

	private String dataSourceWriterPath = null;

	protected String modifyUserId = null;

	private Integer filePartSize = null;

	protected Boolean identityCardEnable = false;

	protected String identityCardName = null;

	protected String identityCardPrefix = null;

	protected Integer identityCardSuffix = 0;

	protected Timestamp modifyTime = null;

    protected ConfigProperties configProperties = null;

    protected String fileId = null;

    protected String tableName = null;

	protected Integer dataMappingTable = null;

	protected String dataMappingDs = null;

	protected DriverDataSource dataMappingDataSourceReader = null;

	protected DriverDataSource dataMappingDataSourceWriter = null;

	protected String dsKey = null;

	public static Long BEFORE_THE_QUEUE_TIME = 60000L;

	public static Integer BATCH_SIZE = 10;

	public static String DEFAULE_MODIFY_USER_ID = null;

	protected Logger log = null;

	protected static ExecutorService mergePool = null;

	/*
	* Constructs a new RemoteFile
	* @param path - the path
	* */


	/**
	* Performs the initPool operation
	* @param poolSize - the poolSize
	* */
	public static synchronized void initPool(int poolSize) {
		if (mergePool == null) {
			MAX_POOL_SIZE = poolSize;
			LoggerFactory.getLogger(RemoteFile.class).mark("MAX_POOL_SIZE={}", MAX_POOL_SIZE);
			mergePool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
		}
	}

	/**
	* Performs the init operation
	* */

	private synchronized void init() {
		dsKey = CommonTools.md5(appRemoteFile.getAbsolutePath());
		dataSourceReader = DATASOURCE_REMOTE_FILE.get(String.format("%s.reader", dsKey));
		dataSourceWriter = DATASOURCE_REMOTE_FILE.get(String.format("%s.writer", dsKey));
		configProperties = CONFIG_PROPS_REMOTE_FILE.get(dsKey);
		if (dataSourceReader == null && dataSourceWriter == null) {
			try {
				if (!appRemoteFile.exists())
					throw new IOException(String.format("Not exist '%s'.", appRemoteFile.getAbsolutePath()));

				configProperties = new ConfigProperties();
				Runnable updatedRun = new Runnable() {

					/**
					* Performs the run operation
					* */
					public void run() {
						try {
							reload();
							LoggerFactory.getLogger(RemoteFile.class).mark("RemoteFile Reloaded");
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						}
					}
				};
				configProperties.load(appRemoteFile, updatedRun);

				CONFIG_PROPS_REMOTE_FILE.put(dsKey, configProperties);

				String dataSourcePath = configProperties.getString("DataSource");
				dataSourceReaderPath = configProperties.getString("DataSourceReader", dataSourcePath);
				dataSourceWriterPath = configProperties.getString("DataSourceWriter", dataSourcePath);
				reload();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
		if (configProperties != null) {
			String dataSourcePath = configProperties.getString("DataSource");
			dataSourceReaderPath = configProperties.getString("DataSourceReader", dataSourcePath);
			dataSourceWriterPath = configProperties.getString("DataSourceWriter", dataSourcePath);
			reload();
		}
		calculateDataMappingDataSourceWriter(this);
	}

	/**
	* Performs the initGlobal operation
	* */

	protected final synchronized void initGlobal() {
	    if(appRemoteFile == null){
    		appRemoteFile = CommonTools.getSystemProperties(RemoteFile.class, "APP_REMOTE_FILE",
    					"RemoteFile.properties");
			log.info("appRemoteFile={}", appRemoteFile);
	    }
		init();
	}

	/**
	* Performs the reload operation
	* */

	private synchronized void reload() {
		try {
			LOGIC_TIMEOUT_MS = configProperties.getMilliSeconds("LogicTimeout", 300000L);
			LOGIC_ACCESS_TIMEOUT_MS = configProperties.getMilliSeconds("LogicAccessTimeout", 60000L);
			LOGIC_CHECK_TIMEOUT_MS = configProperties.getMilliSeconds("LogicCheckTimeout", 60000L);
			MAX_FILE_SZIE = configProperties.getFileSize("MaxFileSize", 1024 * 1024 * 1L);
			SHOW_HIDDEN = configProperties.getBoolean("ShowHidden", false);
			SHOW_LINK = configProperties.getBoolean("ShowLink", false);
			COPY_STRUCTURE_ONLY = configProperties.getBoolean("CopyStructureOnly", false);
			MAX_QUEUE_SIZE = configProperties.getInteger("MaxQueueSize", 10);
			BATCH_SIZE = configProperties.getInteger("BatchSize", 10);
			MAX_CONNECT_USAGE = configProperties.getDouble("MaxConnectUsage", 0.5D);
			MAX_MEMORY_USAGE = configProperties.getDouble("MaxMemoryUsage", 0.8D);
			BEFORE_THE_QUEUE_TIME = configProperties.getMilliSeconds("BeforeTheQueueTime", 15000L);
			MAX_FILE_PARENT_PATH_LENGTH = configProperties.getInteger("MaxFileParentPathLength", 200);
			MAX_FILE_NAME_LENGTH = configProperties.getInteger("MaxFileNameLength", 200);
			CACHE_SECONDS = configProperties.getInteger("CacheSeconds", 5);

			SYNC_CUTOFF_TIME = configProperties.getDateTime("SyncCutoffTime","0000-01-01 00:00:00.0000");
			DELETE_CUTOFF_TIME = configProperties.getDateTime("DeleteCutoffTime","0000-01-01 00:00:00.0000");

			if(BEFORE_THE_QUEUE_TIME < 0) BEFORE_THE_QUEUE_TIME = 15000L;

			if(MAX_CONNECT_USAGE < 0) MAX_CONNECT_USAGE = 0D;

			if(MAX_MEMORY_USAGE < 0) MAX_MEMORY_USAGE = 0D;

			if(BATCH_SIZE <= 0) BATCH_SIZE = 10;

			if(MAX_QUEUE_SIZE <= 0) MAX_QUEUE_SIZE = 10;

			if(MAX_FILE_SZIE <= 0) MAX_FILE_SZIE = 1024 * 1024 * 1L;

			if(LOGIC_TIMEOUT_MS <= 0) LOGIC_TIMEOUT_MS = 900000L;

			if(LOGIC_ACCESS_TIMEOUT_MS < 0) LOGIC_ACCESS_TIMEOUT_MS = 60000L;

			if(LOGIC_CHECK_TIMEOUT_MS < 0) LOGIC_CHECK_TIMEOUT_MS = 60000L;

			if(MAX_FILE_PARENT_PATH_LENGTH <= 0) MAX_FILE_PARENT_PATH_LENGTH = 200;

			if(MAX_FILE_NAME_LENGTH <= 0) MAX_FILE_NAME_LENGTH = 200;

			if(CACHE_SECONDS < 0) CACHE_SECONDS = 0;

			log.debug("LOGIC_TIMEOUT_MS={}", LOGIC_TIMEOUT_MS);
			log.debug("LOGIC_ACCESS_TIMEOUT_MS={}", LOGIC_ACCESS_TIMEOUT_MS);
			log.debug("LOGIC_CHECK_TIMEOUT_MS={}", LOGIC_CHECK_TIMEOUT_MS);
			log.debug("MAX_FILE_SZIE={}", MAX_FILE_SZIE);
			log.debug("SHOW_HIDDEN={}", SHOW_HIDDEN);
			log.debug("SHOW_LINK={}", SHOW_LINK);
			log.debug("COPY_STRUCTURE_ONLY={}", COPY_STRUCTURE_ONLY);
			log.debug("MAX_QUEUE_SIZE={}", MAX_QUEUE_SIZE);
			log.debug("BATCH_SIZE={}", BATCH_SIZE);
			log.debug("MAX_CONNECT_USAGE={}", MAX_CONNECT_USAGE);
			log.debug("MAX_MEMORY_USAGE={}", MAX_MEMORY_USAGE);
			log.debug("MAX_FILE_PARENT_PATH_LENGTH={}", MAX_FILE_PARENT_PATH_LENGTH);
			log.debug("MAX_FILE_NAME_LENGTH={}", MAX_FILE_NAME_LENGTH);
			log.debug("CACHE_SECONDS={}", CACHE_SECONDS);

			String dataSourcePath = configProperties.getString("DataSource");
			String reloadDataSourceReaderPath = configProperties.getString("DataSourceReader", dataSourcePath);
			String reloadDataSourceWriterPath = configProperties.getString("DataSourceWriter", dataSourcePath);
			filePartSize = configProperties.getFileSize("FilePartSize", 1024 * 1024 * 1L).intValue();

			if(filePartSize <= 0) filePartSize = 1024 * 1024 * 1;

			tableName = configProperties.getString("TableName", "remote_file");
			identityCardEnable = configProperties.getBoolean("IdentityCardEnable", false);
			identityCardName = configProperties.getString("IdentityCardName", "RemoteFile");
			identityCardPrefix = configProperties.getString("IdentityCardPrefix", "RMF");
			identityCardSuffix = configProperties.getInteger("IdentityCardSuffix", 0);

			String readerDsKey = String.format("%s.reader", dsKey);
			String writerDsKey = String.format("%s.writer", dsKey);
			/**
			May be referenced when expansion is needed
			if (dataSourceReader == null || !dataSourceReaderPath.equals(reloadDataSourceReaderPath)) {
				DriverDataSource currentDs = DATASOURCE_REMOTE_FILE.get(readerDsKey);
				if (currentDs != null) {
					currentDs.closeAll();
			}
			**/
			if (dataSourceReader == null || !dataSourceReaderPath.equals(reloadDataSourceReaderPath)) {
				dataSourceReaderPath = reloadDataSourceReaderPath;
				dataSourceReader = new DriverDataSource(new File(dataSourceReaderPath));
				DATASOURCE_REMOTE_FILE.put(readerDsKey, dataSourceReader);
			}
			/**
			May be referenced when expansion is needed
			if (dataSourceWriter == null || !dataSourceWriterPath.equals(reloadDataSourceWriterPath)) {
				DriverDataSource currentDs = DATASOURCE_REMOTE_FILE.get(writerDsKey);
				if (currentDs != null) {
					currentDs.closeAll();
			}
			**/
			if (dataSourceWriter == null || !dataSourceWriterPath.equals(reloadDataSourceWriterPath)) {
				dataSourceWriterPath = reloadDataSourceWriterPath;
				dataSourceWriter = new DriverDataSource(new File(dataSourceWriterPath));
				DATASOURCE_REMOTE_FILE.put(writerDsKey, dataSourceWriter);
			}

			loadDataMapping(this);

			FIRST_LOADED = true;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	/**
	* Performs the formatSql operation
	* @param sql - the sql
	* @return the formatSql result
	* */

	public String formatSql(String sql) {
		return String.format(sql, tableName);
	}

	/**
	* Performs the formatSqlForFilePartData operation
	* @param sql - the sql
	* @return the formatSqlForFilePartData result
	* */

	public String formatSqlForFilePartData(String sql) {
		return String.format(sql, getDataMappingTableName());
	}

	/**
	* Performs the writeToDisk operation
	* @param diskFile - the diskFile
	* @return the writeToDisk result
	* */

	public DiskFile writeToDisk(DiskFile diskFile) throws IOException {
		if (!exists()) {
			log.mark(String.format("The remote file '%s' does not exist.", this.getPath()));
		}
		// Embedded engine: when no sync root is configured (server mode wires
		// one in), derive a local lock root from the target file's parent so
		// logicModify() can place its lock files under TEMP_DIR.
		if (diskFile.syncRoot == null) {
			File parent = diskFile.getOrigin().getParentFile();
			if (parent != null) {
				DiskFile.copyAttrs(diskFile, false, parent);
			}
		}
		RemoteFileBase the = this;

		Timestamp rfModifyTime = the.getModifyTime();
		if (rfModifyTime != null) {
			diskFile.setModifyTimeMsFromClock(rfModifyTime.getTime());
		}
		if (isLink()) {
			if (isDebug(the)) {
				LoggerFactory.getLogger(RemoteFile.class).mark("DiskLink - {}", diskFile.getPath());
			}
			diskFile.createLink(new String(readAllBytes(),BaseFile.CHARSET));
		} else if (isFile()) {
			merge(new FilePart() {
				final List<byte[]> partDataList = new ArrayList<byte[]>();
				boolean isWritingDf = false;
				DiskFile realDf = null;


				/**
				* Performs the process operation
				* @param partIndex - the partIndex
				* @param data - the data
				* */
				protected void process(int partIndex, byte[] data) throws IOException {
					Thread.currentThread()
							.setName(String.format("RemoteFile-writeToDisk-merge-%s", diskFile.getOrigin().getName()));

					try {

    					if (syncRoot != null) {
    						if (!diskFile.isLogicModify()) {
    							throw new IOException(
    									String.format("The file '%s' missing modify logic lock.", the.getPath()));
    						}
    					}

					    if (diskFile != null)
						    diskFile.logicModify();

						String fileName = diskFile.getOrigin().getName();
						isWritingDf = fileName.matches(TMP_WRITING_SWP);
						if (isWritingDf) {
							if (partIndex == 0) {
								if (diskFile.exists()) {
									diskFile.delete();
								}
								if (isDebug(the)) {
									LoggerFactory.getLogger(RemoteFile.class).mark("CreateDiskFile - {}",
											diskFile.getPath());
								}
								String realFileName = fileName.replaceFirst(BaseFile.TMP_WRITING_FOR_REPLACE, "");
								String realDfPath = String.format("%s/%s", diskFile.getParent(), realFileName);
								realDf = new DiskFile(realDfPath);
								DiskFile.copyAttrs(realDf, diskFile.isCopyStructureOnly(), diskFile.syncRoot);
								if(!diskFile.manualOpen(true)){
									throw new IOException(
											String.format("The disk file '%s' cannot open.", diskFile.getPath()));
								}
							}
							partDataList.add(decrypt(data));
							if (partDataList.size() >= BATCH_SIZE) {

								if (realDf != null)
									realDf.logicModify();

								ByteArrayOutputStream ops = new ByteArrayOutputStream();
								int size = partDataList.size();
								for (int i = 0; i < size; i++) {
									byte[] partData = partDataList.get(i);
									ops.write(partData);
								}
								diskFile.manualWrite(ops.toByteArray());
								partDataList.clear();
							}
						} else {
							if (partIndex == 0) {
								if (diskFile.exists()) {
									throw new IOException(
											String.format("The remote file '%s' already exist.", diskFile.getPath()));
								}
								if (isDebug(the)) {
									LoggerFactory.getLogger(RemoteFile.class).mark("CreateDiskFile - {}",
											diskFile.getPath());
								}
								if(!diskFile.manualOpen(true)){
									throw new IOException(
											String.format("The disk file '%s' cannot open.", diskFile.getPath()));
								}
							}
							partDataList.add(decrypt(data));
							if (partDataList.size() >= BATCH_SIZE) {
								ByteArrayOutputStream ops = new ByteArrayOutputStream();
								int size = partDataList.size();
								for (int i = 0; i < size; i++) {
									byte[] partData = partDataList.get(i);
									ops.write(partData);
								}
								diskFile.manualWrite(ops.toByteArray());
								partDataList.clear();
							}
						}

					} catch (Exception e) {
						log.warn(e.getMessage(), e);
						if (diskFile != null) {
							diskFile.manualClose();
							diskFile.delete();
						}
						throw new IOException(e.getMessage(), e);
					}
				}


				/**
				* Performs the completed operation
				* @param lastPartIndex - the lastPartIndex
				* @param modifyTime - the modifyTime
				* @param fileSize - the fileSize
				* */
				protected void completed(int lastPartIndex, Timestamp modifyTime, long fileSize) throws IOException {
					if (partDataList.size() > 0) {
						ByteArrayOutputStream ops = new ByteArrayOutputStream();
						int size = partDataList.size();
						for (int i = 0; i < size; i++) {
							byte[] partData = partDataList.get(i);
							ops.write(partData);
						}
						diskFile.manualWrite(ops.toByteArray());
						partDataList.clear();
					}
					diskFile.manualClose();
					try {
    					if (syncRoot != null && !diskFile.isLogicModify()) {
    						diskFile.delete();
    						throw new IOException(
    								String.format("The file '%s' missing modify logic lock.", diskFile.getPath()));
    					}
						if (isWritingDf) {
							if (diskFile.exists()) {
								diskFile.moveTo(realDf.getPath());
								if (isDebug(the)) {
									LoggerFactory.getLogger(RemoteFile.class).mark("CompletedDiskFile - {}",
											realDf.getPath());
								}
							}
							if (realDf.exists())
								the.syncedToDisk();
						}
						if (realDf == null) {
							if (diskFile != null) {
								if (diskFile.getOrigin().getName().startsWith(".")) {
									Files.setAttribute(Paths.get(diskFile.getPath()), "dos:hidden", true);
								}
							}
						} else {
							if (realDf.getOrigin().getName().startsWith(".")) {
								Files.setAttribute(Paths.get(realDf.getPath()), "dos:hidden", true);
							}
						}

						RemoteFile.removeQueuePathMapping(the.getPath());

						if (diskFile != null)
							diskFile.removeLogicModify();

						if (realDf != null)
							realDf.removeLogicModify();

					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}


				/**
				* Performs the ended operation
				* @param lastPartIndex - the lastPartIndex
				* @param fileSize - the fileSize
				* */
				protected void ended(int lastPartIndex, long fileSize) {
					try {
						if (diskFile != null) {
							diskFile.manualClose();
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}
			});
		} else {
			List<Map<String, Object>> writeList = list(getPath(), "%");
			writeList.addAll(list(parsePath(getPath() + "/%"), "%"));

			int size = writeList.size();
			if (size > 0) {
				if (!diskFile.exists()) {
					if (isDebug(the)) {
						log.mark("CreateDiskDir - {} ", diskFile.getPath());
					}
					diskFile.mkdirs();
				}
			}

			for (Map<String, Object> item : writeList) {
				RemoteFileBase rf = new RemoteFile(
						String.format("%s/%s", item.get("file_parent_path"), item.get("file_name")));

				if (rf.getPath().equals(getPath()))
					continue;

				String dfPath = rf.getPath().replaceFirst(getPath(), diskFile.getPath());
				DiskFile df = new DiskFile(dfPath);
				DiskFile.copyAttrs(df, false, this.syncRoot);
				Timestamp mt = rf.getModifyTime();
				if (mt != null) {
					df.setModifyTimeMsFromClock(mt.getTime());
				}
				if (rf.isDir()) {
					if (!df.exists()) {
						if (isDebug(the)) {
							log.mark("CreateDiskDir - {}", diskFile.getPath());
						}
						df.mkdirs();
					}
				} else {
					rf.writeToDisk(df);
				}
			}
		}
		return diskFile;
	}

	/**
	* Performs the writeToDisk operation
	* @return the writeToDisk result
	* */

	public DiskFile writeToDisk() throws IOException {
		DiskFile df = new DiskFile(this.origin.getAbsolutePath());
		DiskFile.copyAttrs(df, false, this.syncRoot);
		return writeToDisk(df);
	}

	/**
	* Performs the writeToDisk operation
	* @param path - the path
	* @return the writeToDisk result
	* */

	public DiskFile writeToDisk(String path) throws IOException {
		DiskFile df = new DiskFile(path);
		DiskFile.copyAttrs(df, false, this.syncRoot);
		return writeToDisk(df);
	}

	/**
	* Performs the isCompleted operation
	* @return the isCompleted result
	* */

	public boolean isCompleted() throws IOException {
		return getCompletedRecord() != null;
	}

	public Map<String, Object> getCompletedRecord() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_COMPLETED);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_name", origin.getName());
		dataRecord.put("file_parent_path", getParent());
		String cacheKey = String.format("first-%s-%s",sql,dataRecord);
		@SuppressWarnings("unchecked")
		Map<String,Object> record = (Map<String,Object>)Cache.get(cacheKey);
		if(record == null){
			CacheDriverExecutor executor = null;
			Connection conn = null;
			try {
				conn = dataSourceReader.getConnection();
				conn.setAutoCommit(true);
				executor = new CacheDriverExecutor(conn);
				record = executor.first(sql, dataRecord);
				if(record != null){
				    Cache.set(cacheKey,record,CACHE_SECONDS);
				}
			} catch (SQLException e) {
				throw new IOException(e.getMessage(), e);
			} finally {
				try {
					if (executor != null)
						executor.close();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		return record;
	}

	public Map<String, Object> getRecordById(Object id) throws IOException {
		String sql = formatSql(SQL_SELECT_GET_RECORD_BY_ID);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
		dataRecord.put("id", id);
		Map<String, Object> record = null;
		if (record == null) {
			CacheDriverExecutor executor = null;
			Connection conn = null;
			try {
				conn = dataSourceReader.getConnection();
				conn.setAutoCommit(true);
				executor = new CacheDriverExecutor(conn);
				record = executor.first(sql, dataRecord);
			} catch (SQLException e) {
				throw new IOException(e.getMessage(), e);
			} finally {
				try {
					if (executor != null)
						executor.close();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		return record;
	}


	public Map<String, Object> getLastUpdateRecord() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_LAST_UPDATE);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_name", origin.getName());
		dataRecord.put("file_parent_path", getParent());
		Map<String, Object> record = null;
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			record = executor.first(sql, dataRecord);
			return record;
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the getFirstCreatedAt operation
	* @return the getFirstCreatedAt result
	* */

	protected Timestamp getFirstCreatedAt() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_FIRST_CREATED_AT);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_parent_path", getPath());
		dataRecord.put("like_file_parent_path", parsePath(getPath() + "/%"));
		dataRecord.put("deleted_on_hostnames", "%;" + CommonTools.getHostname() + ";%");
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> record = executor.first(sql, dataRecord);
			return record == null ? null : (Timestamp)record.get("created_at");
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the getMaxFilePartDataTable operation
	* @return the getMaxFilePartDataTable result
	* */

	protected Integer getMaxFilePartDataTable() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_MAX_DATA_TABLE);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_parent_path", getPath());
		dataRecord.put("like_file_parent_path", parsePath(getPath() + "/%"));
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> record = executor.first(sql, dataRecord);
			return record == null ? 0 : Integer.parseInt(record.get("file_part_data_table")+"");
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	private Map<String, Object> getSelectByPathForCheckExists() throws IOException {
		String sql = formatSql(SQL_SELECT_BY_PATH_FOR_CHECK_EXISTS);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_name", origin.getName());
		dataRecord.put("file_parent_path", getParent());
		Map<String, Object> record = null;
		if (record == null) {
			CacheDriverExecutor executor = null;
			Connection conn = null;
			try {
				conn = dataSourceReader.getConnection();
				conn.setAutoCommit(true);
				executor = new CacheDriverExecutor(conn);
				record = executor.first(sql, dataRecord);
				return record;
			} catch (SQLException e) {
				throw new IOException(e.getMessage(), e);
			} finally {
				try {
					if (executor != null)
						executor.close();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		return record;
	}

	/**
	* Performs the countAllSubFiles operation
	* @param fileParentPath - the fileParentPath
	* @param fileName - the fileName
	* @return the countAllSubFiles result
	* */

	protected int countAllSubFiles(String fileParentPath, String fileName) throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_COUNT_ALL_SUB_FILES);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_name", fileName);
		dataRecord.put("file_parent_path", fileParentPath);
		Map<String, Object> record = null;
		if (record == null) {
			CacheDriverExecutor executor = null;
			Connection conn = null;
			try {
				conn = dataSourceReader.getConnection();
				conn.setAutoCommit(true);
				executor = new CacheDriverExecutor(conn);
				record = executor.first(sql, dataRecord);
				return Integer.parseInt(record.get("as_count") + "");
			} catch (SQLException e) {
				throw new IOException(e.getMessage(), e);
			} finally {
				try {
					if (executor != null)
						executor.close();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		return -1;
	}

	protected Map<String, Object> getFirstFilePart() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_FIRST_FILE);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_name", origin.getName());
		dataRecord.put("file_parent_path", getParent());
		String cacheKey = String.format("first-%s-%s",sql,dataRecord);
		@SuppressWarnings("unchecked")
		Map<String, Object> record = (Map<String,Object>)Cache.get(cacheKey);
		if(record == null){
			CacheDriverExecutor executor = null;
			Connection conn = null;
			try {
				conn = dataSourceReader.getConnection();
				conn.setAutoCommit(true);
				executor = new CacheDriverExecutor(conn);
				record = executor.first(sql, dataRecord);
				if(record != null){
				    Cache.set(cacheKey,record,CACHE_SECONDS);
				}
			} catch (SQLException e) {
				throw new IOException(e.getMessage(), e);
			} finally {
				try {
					if (executor != null)
						executor.close();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		return record;
	}

	protected Map<String, Object> getFirstFilePartRefresh() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_FIRST_FILE);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_name", origin.getName());
		dataRecord.put("file_parent_path", getParent());
		String cacheKey = String.format("first-%s-%s",sql,dataRecord);
		Cache.remove(cacheKey);
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String,Object> record =  executor.first(sql, dataRecord);
			if(record != null){
			    Cache.set(cacheKey,record,CACHE_SECONDS);
			}
			return record;
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	private Map<String, Object> getLastIndexRecord() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_LAST_FILE_PART);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_name", origin.getName());
		dataRecord.put("file_parent_path", getParent());
		dataRecord.put("file_id", fileId);
		Map<String, Object> record = null;
		if (record == null) {
			CacheDriverExecutor executor = null;
			Connection conn = null;
			try {
				conn = dataSourceReader.getConnection();
				conn.setAutoCommit(true);
				executor = new CacheDriverExecutor(conn);
				record = executor.first(sql, dataRecord);
			} catch (SQLException e) {
				throw new IOException(e.getMessage(), e);
			} finally {
				try {
					if (executor != null)
						executor.close();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		return record;
	}

	/**
	* Performs the isTimeout operation
	* @param timeoutMs - the timeoutMs
	* @return the isTimeout result
	* */


	protected Map<String, Object> readPart(Map<String, Object> rfRecord) throws IOException {
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			calculateDataMappingDataSourceReader(this, rfRecord);
			if(this.dataMappingTable <= -1){
			    return null;
			}
			Clock clock = new Clock();
			conn = dataMappingDataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("remote_file_id", rfRecord.get("id"));
			return executor.first(formatSqlForFilePartData(SQL_SELECT_BY_RF_ID_FROM_FILE_DATA), dataRecord);
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the merge operation
	* @param filePart - the filePart
	* */

	public void merge(FilePart filePart) throws IOException {
		if (isLink()) {
			throw new IOException(String.format("The file '%s' is a link.", origin.getAbsolutePath()));
		}
		if (isDir()) {
			throw new IOException(String.format("The file '%s' is a folder.", origin.getAbsolutePath()));
		}
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			Clock clock = new Clock();
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> dataRecord = getCompletedRecord();
			if (dataRecord != null) {
				CacheArray rows = new CacheArray();
				CacheArrayFilter filter = new CacheArrayFilter(CACHE_ARRAY_FILTER_TIMER) {
					long fileSize = 0L;
					Map<String, Object> fp = null;
					Map<String, Object> item = null;


					/**
					* Performs the execute operation
					* @param index - the index
					* @param o - the o
					* */
					@SuppressWarnings("unchecked")
					public void execute(Integer index, Object o) {

						try {
							item = (Map<String, Object>) o;
							fp = readPart(item);
							Object fpData = null;

							if(fp == null){
							    fpData = new byte[]{};
							}else{
							    fpData = fp.get("file_part_data");
							}
							Thread.currentThread()
									.setName(String.format("RemoteFile-merge-%s", item.get("file_name")));
							byte[] partData = null;
							if (fpData instanceof byte[]) {
								partData = (byte[]) fpData;
							}
							fileSize += partData.length;
							filePart.process(index, partData);
						} catch (Exception e) {
							log.error(e.getMessage(), e);
							this.terminated = true;
						}
					}


					/**
					* Performs the completed operation
					* @param size - the size
					* */
					public void completed(Integer size) {
						try {
							if (item != null) {
								Thread.currentThread()
										.setName(String.format("RemoteFile-merge-completed-%s", item.get("file_name")));
							}
							filePart.completed(size, new Clock().getSqlTimestamp(), fileSize);
						} catch (IOException e) {
							log.error(e.getMessage(), e);
						}
					}


					/**
					* Performs the terminated operation
					* */
					public void terminated() {
						if (item != null) {
							Thread.currentThread()
									.setName(String.format("RemoteFile-merge-trminated-%s", item.get("file_name")));
							log.error("Merge terminated: {}/{}", item.get("file_parent_path"), item.get("file_name"));
						}
					}
				};
				rows.filter(filter);
				executor.find(formatSql(SQL_SELECT_FILE_HAVE_DATA), dataRecord, rows);
				// P0-10: merge is synchronous from the caller's viewpoint -
				// wait (bounded) for the async worker to drain the rows before
				// returning, so the merged file is complete when merge() exits.
				waitForCacheArray(rows, filter);
			}
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			if (executor != null) {
				try {
					executor.close();
				} catch (Exception e) {
					throw new IOException(e.getMessage(), e);
				}
			}
		}
	}

	/**
	 * Boundedly wait for a CacheArray worker to finish (P0-10). On timeout the
	 * merge degrades to the historic asynchronous behaviour with a warning -
	 * never throws, so merge() semantics stay safe.
	 * */

	protected void waitForCacheArray(CacheArray rows, CacheArrayFilter filter) {
		long deadline = System.currentTimeMillis() + MERGE_WAIT_TIMEOUT;
		while (!rows.isCompleted() && !filter.isForceTerminated()) {
			if (System.currentTimeMillis() > deadline) {
				log.warn("Merge worker did not complete within {}ms - continuing asynchronously.", MERGE_WAIT_TIMEOUT);
				return;
			}
			try {
				Thread.sleep(CACHE_ARRAY_FILTER_TIMER);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Merge wait interrupted.", e);
				return;
			}
		}
	}

	/**
	* Performs the copyFrom operation
	* @param copyStructureOnly - the copyStructureOnly
	* @param diskFile - the diskFile
	* */

	protected void copyFrom(boolean copyStructureOnly, DiskFile diskFile) throws IOException {
		var the = this;
		if (diskFile.isLink()) {
			the.setModifyTime(diskFile.getModifyTimeForClock());
			the.createLink(diskFile.readLink());
		}
		if (diskFile.isDir()) {
			the.setModifyTime(diskFile.getModifyTimeForClock());
			the.mkdirs();
		}
		if (diskFile.isFile()) {
			the.setModifyTime(diskFile.getModifyTimeForClock());
			if (copyStructureOnly) {
				the.createStructureFile(diskFile.size());
				diskFile.removeLogicAccess();
				DiskFile.removeQueuePathMapping(diskFile.getPath());
			} else {
				diskFile.split(filePartSize, new FilePart() {
					final List<byte[]> partDataList = new ArrayList<byte[]>();
					int currentFilePartStartIndex = 0;


					/**
					* Performs the process operation
					* @param partIndex - the partIndex
					* @param data - the data
					* */
					protected void process(int partIndex, byte[] data) throws IOException {
					    try{
    						if (partIndex == 0) {
    							if (diskFile.syncRoot != null) {
    								if (diskFile.isLogicModify()) {
    									throw new IOException(
    											String.format("The file '%s' is already logic locked by another thread.",
    													diskFile.getPath()));
    								}
    							}
    							if (the.exists(true)) {
    								throw new IOException(
    										String.format("The remote file '%s' already exist.", the.getPath()));
    							}
    							if (isDebug(the)) {
    								LoggerFactory.getLogger(RemoteFile.class).mark("CreateRemoteFile - {}", the.getPath());
    							}
    						}

    						partDataList.add(data);
    						if (partDataList.size() >= BATCH_SIZE) {
    							// Embedded engine: no sync root configured (server
    							// mode wires one in) - skip the logic-access lock
    							// file (toAccessTmpFilePath needs a syncRoot).
    							if (diskFile.syncRoot != null) {
    								diskFile.logicAccess();
    							}
    							if(!the.writeToBatch(currentFilePartStartIndex, partDataList)){
    								throw new IOException(String.format("The remote file '%s' write failed.",the.getPath()));
    							}
    							partDataList.clear();
    							currentFilePartStartIndex += BATCH_SIZE;
    						}
					    }catch(Exception e){
					        throw new IOException(e.getMessage(),e);
					    }
					}


					/**
					* Performs the completed operation
					* @param lastPartIndex - the lastPartIndex
					* @param modifyTime - the modifyTime
					* @param fileSize - the fileSize
					* */
					protected void completed(int lastPartIndex, Timestamp modifyTime, long fileSize)
							throws IOException {
						try {
							if (diskFile.syncRoot != null) {
								if (diskFile.isLogicModify()) {
									throw new IOException(
											String.format("The file '%s' is already logic locked by another thread.",
													diskFile.getPath()));
								}
							}
							/**This is 0 bytes file**/
							if (lastPartIndex == -1) {

								if (the.exists(true)) {
									the.forceDeleteFile(false);
								}

								the.setModifyTime(modifyTime);
								the.createEmptyFile();
							} else {
								the.setModifyTime(modifyTime);
								if (partDataList.size() > 0) {
									if(!the.writeToBatch(currentFilePartStartIndex, partDataList)){
									    throw new IOException(String.format("The remote file '%s' write failed.",the.getPath()));
									}
									partDataList.clear();
								}
								the.complete(lastPartIndex, fileSize);
							}
							if (isDebug(the)) {
								LoggerFactory.getLogger(RemoteFile.class).mark("CompletedRemoteFile - {}",
										the.getPath());
							}
							DiskFile.removeQueuePathMapping(diskFile.getPath());
						} catch (Exception e) {
							throw new IOException(e.getMessage(),e);
						}
					}


					/**
					* Performs the ended operation
					* @param lastPartIndex - the lastPartIndex
					* @param fileSize - the fileSize
					* */
					protected void ended(int lastPartIndex, long fileSize) {

					}

				});
			}
		}
	}

	/**
	* Performs the createEmptyFile operation
	* @return the createEmptyFile result
	* */

	protected boolean createEmptyFile() throws IOException {
	    boolean allowed = beforeWrite();

	    if(!allowed) return false;

		Clock clock = new Clock();
		Timestamp currentTime = clock.getSqlTimestamp();
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
		    calculateDataMappingDataSourceWriter(this);
			conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("id",
					generateId(identityCardEnable, identityCardName, identityCardPrefix, identityCardSuffix));
			dataRecord.put("file_name", origin.getName());
			dataRecord.put("file_parent_path", getParent());
			dataRecord.put("file_type", "F");
			dataRecord.put("file_part_index", 0);
			dataRecord.put("file_part_size", 0);
			dataRecord.put("file_part_data_ds", "NONE");
			dataRecord.put("file_part_data_table", this.dataMappingTable);
			dataRecord.put("file_part_last_index", 0);
			dataRecord.put("file_size", 0);
			dataRecord.put("file_modify_time", modifyTime == null ? currentTime : modifyTime);
			dataRecord.put("file_deleted", 0);
			dataRecord.put("created_user_id", getDefaultModifyUserId());
			dataRecord.put("updated_user_id", getDefaultModifyUserId());
			dataRecord.put("created_at", currentTime);
			dataRecord.put("updated_at", currentTime);
			dataRecord.put("file_id", fileId);
			dataRecord.put("source_hostname", CommonTools.getHostname());
			dataRecord.put("synced_on_hostnames", String.format(";%s;", CommonTools.getHostname()));
			dataRecord.put("deleted_on_hostnames", ";");
			executor.execute(formatSql(SQL_INSERT_INTO_FILE), dataRecord);

			afterWrite();

			return true;
		} catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the createStructureFile operation
	* @param fileSize - the fileSize
	* @return the createStructureFile result
	* */

	private boolean createStructureFile(long fileSize) throws IOException {
	    boolean allowed = beforeWrite();

	    if(!allowed) return false;

		log.debug("CreateStructureFile: {}", getPath());
		Clock clock = new Clock();
		Timestamp currentTime = clock.getSqlTimestamp();
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
		    calculateDataMappingDataSourceWriter(this);
			conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("id",
					generateId(identityCardEnable, identityCardName, identityCardPrefix, identityCardSuffix));
			dataRecord.put("file_name", origin.getName());
			dataRecord.put("file_parent_path", getParent());
			dataRecord.put("file_type", "F");
			dataRecord.put("file_part_index", 0);
			dataRecord.put("file_part_size", fileSize);
			dataRecord.put("file_part_data_ds", "NONE");
			dataRecord.put("file_part_data_table", this.dataMappingTable);
			dataRecord.put("file_part_last_index", 0);
			dataRecord.put("file_size", fileSize);
			dataRecord.put("file_modify_time", modifyTime == null ? currentTime : modifyTime);
			dataRecord.put("file_deleted", 0);
			dataRecord.put("created_user_id", getDefaultModifyUserId());
			dataRecord.put("updated_user_id", getDefaultModifyUserId());
			dataRecord.put("created_at", currentTime);
			dataRecord.put("updated_at", currentTime);
			dataRecord.put("file_id", fileId);
			dataRecord.put("source_hostname", CommonTools.getHostname());
			dataRecord.put("synced_on_hostnames", String.format(";%s;", CommonTools.getHostname()));
			dataRecord.put("deleted_on_hostnames", ";");
			executor.execute(formatSql(SQL_INSERT_INTO_FILE), dataRecord);

			afterWrite();

			return true;
		} catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the syncedToDisk operation
	* @return the syncedToDisk result
	* */

	protected boolean syncedToDisk() throws IOException {
		Clock clock = new Clock();
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> record = getCompletedRecord();
			if (record == null) {
				log.debug(String.format("Remmote file not found '%s'.", origin.getAbsolutePath()));
			}
			if (record != null) {
			    String hostname = CommonTools.getHostname();
				String syncedOnHostnames = (String) record.get("synced_on_hostnames");
				syncedOnHostnames = syncedOnHostnames.replaceAll(String.format(";%s;",hostname),";");
				String shn = String.format("%s%s;", syncedOnHostnames, hostname);
				Map<String, Object> dataRecord = new HashMap<String, Object>();
			    dataRecord.put("file_parent_path",record.get("file_parent_path"));
			    dataRecord.put("file_name", record.get("file_name"));
				dataRecord.put("synced_on_hostnames",shn);
				dataRecord.put("updated_at", clock.getSqlTimestamp());
				dataRecord.put("updated_user_id", getDefaultModifyUserId());
				executor.execute(formatSql(SQL_UPDATE_FOR_SYNCED_TO_DISK), dataRecord);
				return true;
			}
			return false;
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the deletedToDiskAndSub operation
	* @param id - the id
	* @return the deletedToDiskAndSub result
	* */

	protected boolean deletedToDiskAndSub(Object id) throws IOException {
		Clock clock = new Clock();
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> record = getRecordById(id);
			if (record == null) {
				log.debug(String.format("Remmote file not found '%s'.", origin.getAbsolutePath()));
			}
			if (record != null) {
			    String hostname = CommonTools.getHostname();
				String deletedOnHostnames = (String) record.get("deleted_on_hostnames");
				deletedOnHostnames = deletedOnHostnames.replaceAll(String.format(";%s;",hostname),";");
				String dhn = String.format("%s%s;", deletedOnHostnames, hostname);
				Timestamp now = clock.getSqlTimestamp();
				final Map<String, Object> dataRecord = new HashMap<String, Object>();
			    dataRecord.put("file_parent_path", record.get("file_parent_path"));
			    dataRecord.put("file_name", record.get("file_name"));
				dataRecord.put("deleted_on_hostnames", dhn);
				dataRecord.put("updated_at", now);
				dataRecord.put("updated_user_id", getDefaultModifyUserId());
				executor.execute(formatSql(SQL_UPDATE_FOR_DELETED_TO_DISK), dataRecord);
				if (record.get("file_type").equals("D")) {
					String dirPath = String.format("%s/%s", record.get("file_parent_path"),
							record.get("file_name"));
					dataRecord.put("file_parent_path", dirPath);
					dataRecord.put("like_file_parent_path", dirPath + "/%");
					executor.execute(formatSql(SQL_UPDATE_FOR_DELETED_TO_DISK_SUB_FILES), dataRecord);
				}
				return true;
			}
			return false;
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the complete operation
	* @param filePartLastIndex - the filePartLastIndex
	* @param fileSize - the fileSize
	* @return the complete result
	* */

	protected boolean complete(int filePartLastIndex, long fileSize) throws IOException {
		Clock clock = new Clock();
		CacheDriverExecutor executor = null;
		Timestamp currentTime = clock.getSqlTimestamp();
		Connection conn = null;
		try {
			conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("file_id", fileId);
			dataRecord.put("file_part_last_index", filePartLastIndex);
			dataRecord.put("file_size", fileSize);
			dataRecord.put("file_modify_time", modifyTime == null ? currentTime : modifyTime);
			dataRecord.put("updated_at", currentTime);
			dataRecord.put("updated_user_id", getDefaultModifyUserId());
			int rows = executor.execute(formatSql(SQL_UPDATE_FOR_COMPLETED), dataRecord);

			afterWrite();

			return rows > 0;
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}


	/**
	* Performs the complete operation
	* @return the complete result
	* */

	public boolean complete() throws IOException {
		Clock clock = new Clock();
		Timestamp currentTime = clock.getSqlTimestamp();
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("file_name", origin.getName());
			dataRecord.put("file_parent_path", getParent());
			dataRecord.put("file_id", fileId);

			Map<String, Object> sumRecord = executor.first(formatSql(SQL_SELECT_FOR_SUM_FILE_PART_SIZE_INDEX),
					dataRecord);

			if (sumRecord == null) {
				log.debug(String.format("Remmote file not found '%s'.", origin.getAbsolutePath()));
			}

			if (sumRecord != null) {
				long fileSize = Long.parseLong(sumRecord.get("file_part_size") + "");
				long filePartLastIndex = Integer.parseInt(sumRecord.get("file_part_index") + "");

				dataRecord.put("file_part_last_index", filePartLastIndex);
				dataRecord.put("file_size", fileSize);
				dataRecord.put("file_modify_time", modifyTime == null ? currentTime : modifyTime);
				dataRecord.put("updated_at", currentTime);
				dataRecord.put("updated_user_id", getDefaultModifyUserId());
				int rows = executor.execute(formatSql(SQL_UPDATE_FOR_COMPLETED), dataRecord);

				afterWrite();

				return rows > 0;
			}
			return false;
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the forceDelete operation
	* @return the forceDelete result
	* */

	public boolean forceDelete() throws IOException {
		return forceDeleteAll(true);
	}

	/*
	* Performs the forceDeleteDir operation
	* @param realDeleted - the realDeleted
	* @return the forceDeleteDir result
	* */


	/**
	* Performs the forceDeleteLink operation
	* @param realDeleted - the realDeleted
	* @return the forceDeleteLink result
	* */
	public boolean forceDeleteLink(boolean realDeleted) throws IOException {
		return forceDelete(realDeleted, "L");
	}

	/**
	* Performs the forceDeleteFile operation
	* @param realDeleted - the realDeleted
	* @return the forceDeleteFile result
	* */

	public boolean forceDeleteFile(boolean realDeleted) throws IOException {
		return forceDelete(realDeleted, "F");
	}

	/**
	* Performs the forceDelete operation
	* @param realDeleted - the realDeleted
	* @param fileType - the fileType
	* @return the forceDelete result
	* */

	public boolean forceDelete(boolean realDeleted, String fileType) throws IOException {
		if (fileType.equalsIgnoreCase("D")) {
			return forceDeleteAll(realDeleted);
		} else {
			boolean allowed = beforeDelete(realDeleted);
			if (allowed) {
				CacheDriverExecutor executor = null;
				Connection conn = null;
				try {
					Clock clock = new Clock();
					Timestamp currentTime = clock.getSqlTimestamp();
					conn = dataSourceWriter.getConnection();
					conn.setAutoCommit(true);
					executor = new CacheDriverExecutor(conn);
					Map<String, Object> dataRecord = new HashMap<String, Object>();
					dataRecord.put("file_deleted", realDeleted ? 1 : 0);
					dataRecord.put("deleted", realDeleted ? 0 : 1);
					dataRecord.put("file_name", origin.getName());
					dataRecord.put("file_type", fileType.toUpperCase());
					dataRecord.put("file_parent_path", getParent());
					dataRecord.put("updated_user_id", getDefaultModifyUserId());
					dataRecord.put("deleted_user_id", getDefaultModifyUserId());
					dataRecord.put("updated_at", currentTime);
					dataRecord.put("deleted_at", currentTime);
					dataRecord.put("root_folder", getPath());
					dataRecord.put("like_subfolder", parsePath(getPath() + "/%"));
					int resultRows = executor.execute(formatSql(SQL_UPDATE_FOR_DELETE_FILE_LINK), dataRecord);
					log.debug("ForceDelete({}): {} -> {}", realDeleted, getPath(), resultRows);

                    afterDelete(realDeleted);

					return resultRows > 0;
				} catch (SQLException e) {
					throw new IOException(e.getMessage(), e);
				} finally {
					try {
						if (executor != null)
							executor.close();
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}
			}
		}
		return false;
	}

	/**
	* Performs the forceDeleteAll operation
	* @param realDeleted - the realDeleted
	* @return the forceDeleteAll result
	* */

	public boolean forceDeleteAll(boolean realDeleted) throws IOException {
		boolean allowed = beforeDelete(realDeleted);
		if (allowed) {
			CacheDriverExecutor executor = null;
			Connection conn = null;
			try {
				Clock clock = new Clock();
				Timestamp currentTime = clock.getSqlTimestamp();
				conn = dataSourceWriter.getConnection();
				conn.setAutoCommit(true);
				executor = new CacheDriverExecutor(conn);
				Map<String, Object> dataRecord = new HashMap<String, Object>();
				dataRecord.put("file_deleted", realDeleted ? 1 : 0);
				dataRecord.put("deleted", realDeleted ? 0 : 1);
				dataRecord.put("file_name", origin.getName());
				dataRecord.put("file_parent_path", getParent());
				dataRecord.put("updated_user_id", getDefaultModifyUserId());
				dataRecord.put("deleted_user_id", getDefaultModifyUserId());
				dataRecord.put("updated_at", currentTime);
				dataRecord.put("deleted_at", currentTime);
				dataRecord.put("root_folder", getPath());
				dataRecord.put("like_subfolder", parsePath(getPath() + "/%"));
				int resultRows = executor.execute(formatSql(SQL_UPDATE_FOR_DELETE_ALL), dataRecord);
				log.debug("ForceDelete({}): {} -> {}", realDeleted, getPath(), resultRows);

                afterDelete(realDeleted);

				return resultRows > 0;
			} catch (SQLException e) {
				throw new IOException(e.getMessage(), e);
			} finally {
				try {
					if (executor != null)
						executor.close();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		return false;
	}


	/**
	* Performs the delete operation
	* @return the delete result
	* */

	public boolean delete() throws IOException {
		return forceDeleteAll(true);
	}

	/**
	* Performs the delete operation
	* @param realDeleted - the realDeleted
	* @return the delete result
	* */

	public boolean delete(boolean realDeleted) throws IOException {
		return forceDeleteAll(realDeleted);
	}


	/**
	* Performs the beforeDelete operation
	* @param realDeleted - the realDeleted
	* @return the beforeDelete result
	* */

	public boolean beforeDelete(boolean realDeleted) {
		return true;
	}


	/**
	* Performs the afterDelete operation
	* @param realDeleted - the realDeleted
	* */

	public void afterDelete(boolean realDeleted) {

	}


	/**
	* Performs the beforeMkdirs operation
	* @return the beforeMkdirs result
	* */

	public boolean beforeMkdirs() {
	    return true;
	}


	/**
	* Performs the afterMkdirs operation
	* */

	public void afterMkdirs() {

	}


	/**
	* Performs the beforeCreateLink operation
	* @param target - the target
	* @return the beforeCreateLink result
	* */

	public boolean beforeCreateLink(String target) {
	    return true;
	}


	/**
	* Performs the afterCreateLink operation
	* @param target - the target
	* */

	public void afterCreateLink(String target) {

	}


	/**
	* Performs the beforeWrite operation
	* @return the beforeWrite result
	* */

	public boolean beforeWrite(){
	    return true;
	}


	/**
	* Performs the afterWrite operation
	* */

	public void afterWrite(){

	}


	/**
	* Performs the exists operation
	* @return the exists result
	* */

	public boolean exists() throws IOException {
		return getFirstFilePart() != null;
	}

	/**
	* Performs the exists operation
	* @param refresh - the refresh
	* @return the exists result
	* */

	public boolean exists(boolean refresh) throws IOException {
	    if(refresh){
	        return getFirstFilePartRefresh() != null;
	    }else{
		    return exists();
	    }
	}


	/**
	* Performs the mkdirs operation
	* @return the mkdirs result
	* */

	public boolean mkdirs() throws IOException {
		return mkdirs(false);
	}

	/**
	* Performs the mkdirs operation
	* @param checkSourceHostname - the checkSourceHostname
	* @return the mkdirs result
	* */

	public boolean mkdirs(boolean checkSourceHostname) throws IOException {

	    boolean allowed = beforeMkdirs();

	    if(!allowed) return false;

		Clock clock = new Clock();
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			if (getFirstFilePart() != null) {
				return false;
			}
			if (checkSourceHostname) {
				String lastSourceHostname = getLastSourceHostname();
				if (lastSourceHostname != null) {
					String clientHostname = CommonTools.getHostname();
					boolean sameHostname = clientHostname.equals(lastSourceHostname);
					if (!sameHostname) {
						return false;
					}
				}
			}
			log.debug("Mkidrs: {}", getPath());
			calculateDataMappingDataSourceWriter(this);
			Timestamp currentTime = clock.getSqlTimestamp();
			conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("id",
					generateId(identityCardEnable, identityCardName, identityCardPrefix, identityCardSuffix));
			dataRecord.put("file_name", origin.getName());
			dataRecord.put("file_parent_path", getParent());
			dataRecord.put("file_type", "D");
			dataRecord.put("file_part_index", 0);
			dataRecord.put("file_part_size", 0);
			dataRecord.put("file_part_data_ds", "NONE");
			dataRecord.put("file_part_data_table", this.dataMappingTable);
			dataRecord.put("file_part_last_index", 0);
			dataRecord.put("file_size", 0);
			dataRecord.put("file_modify_time", modifyTime == null ? currentTime : modifyTime);
			dataRecord.put("file_deleted", 0);
			dataRecord.put("created_user_id", getDefaultModifyUserId());
			dataRecord.put("updated_user_id", getDefaultModifyUserId());
			dataRecord.put("created_at", currentTime);
			dataRecord.put("updated_at", currentTime);
			dataRecord.put("file_id", fileId);
			dataRecord.put("source_hostname", CommonTools.getHostname());
			dataRecord.put("synced_on_hostnames", String.format(";%s;", CommonTools.getHostname()));
			dataRecord.put("deleted_on_hostnames", ";");
			int rows = executor.execute(formatSql(SQL_INSERT_INTO_FILE), dataRecord);

			afterMkdirs();

			return rows > 0;
		} catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}


	/**
	* Performs the write operation
	* @param data - the data
	* @param append - the append
	* @return the write result
	* */

	public boolean write(byte[] data, boolean append) throws IOException {
		return writeTo(data, append);
	}

	/*
	* Performs the writeBlock operation
	* @param data - the data
	* @param append - the append
	* @return the writeBlock result
	* */


	/**
	* Performs the writeToBatch operation
	* @param currentFilePartStartIndex - the currentFilePartStartIndex
	* @param partDataList - the partDataList
	* @return the writeToBatch result
	* */
	protected boolean writeToBatch(int currentFilePartStartIndex, List<byte[]> partDataList) throws IOException {

	    boolean allowed = beforeWrite();

	    if(!allowed) return false;

		CacheDriverExecutor executor = null;
		CacheDriverExecutor dmExecutor = null;
		List<Map<String, Object>> rfList = new ArrayList<Map<String, Object>>();
		List<Map<String, Object>> dmList = new ArrayList<Map<String, Object>>();
		try {
			Clock clock = new Clock();
			log.debug("Write: {}", getPath());
			int size = partDataList.size();
			Timestamp currentTime = clock.getSqlTimestamp();
			String hostname = CommonTools.getHostname();
			calculateDataMappingDataSourceWriter(this);
			for (int filePartIndex = 0; filePartIndex < size; filePartIndex++) {
				byte[] data = partDataList.get(filePartIndex);
				String remoteFileId = generateId(identityCardEnable, identityCardName, identityCardPrefix,
						identityCardSuffix);
				Map<String, Object> rfRecord = new HashMap<String, Object>();
				rfRecord.put("id", remoteFileId);
				rfRecord.put("file_name", origin.getName());
				rfRecord.put("file_parent_path", getParent());
				rfRecord.put("file_type", "F");
				rfRecord.put("file_part_index", filePartIndex + currentFilePartStartIndex);
				rfRecord.put("file_part_size", data.length);
				rfRecord.put("file_part_data_ds", this.dataMappingDs);
				rfRecord.put("file_part_data_table", this.dataMappingTable);
				rfRecord.put("file_part_last_index", -1);
				rfRecord.put("file_size", 0);
				rfRecord.put("file_modify_time", modifyTime == null ? currentTime : modifyTime);
				rfRecord.put("file_deleted", 0);
				rfRecord.put("created_user_id", getDefaultModifyUserId());
				rfRecord.put("updated_user_id", getDefaultModifyUserId());
				rfRecord.put("created_at", currentTime);
				rfRecord.put("updated_at", currentTime);
				rfRecord.put("file_id", fileId);
				rfRecord.put("source_hostname", hostname);
				rfRecord.put("synced_on_hostnames", String.format(";%s;", hostname));
				rfRecord.put("deleted_on_hostnames", ";");
				rfList.add(rfRecord);

				Map<String, Object> dataRecord = new HashMap<String, Object>();
				dataRecord.put("id", generateDataId(identityCardEnable, identityCardName, identityCardSuffix, this));
				dataRecord.put("file_part_data", encrypt(data));
				dataRecord.put("remote_file_id", remoteFileId);
				dataRecord.put("file_id", fileId);
				dataRecord.put("created_at", currentTime);
				dataRecord.put("updated_at", currentTime);
				dmList.add(dataRecord);
			}
			Connection conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			executor.executeBatch(formatSql(SQL_INSERT_INTO_FILE), rfList);

			conn = dataMappingDataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			dmExecutor = new CacheDriverExecutor(conn);
			dmExecutor.executeBatch(formatSqlForFilePartData(SQL_INSERT_INTO_FILE_DATA), dmList);

			return true;
		} catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
			try {
				if (dmExecutor != null)
					dmExecutor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the writeTo operation
	* @param data - the data
	* @param append - the append
	* @return the writeTo result
	* */

	protected boolean writeTo(byte[] data, boolean append) throws IOException {

	    boolean allowed = beforeWrite();

	    if(!allowed) return false;

		CacheDriverExecutor executor = null;
		CacheDriverExecutor dmExecutor = null;
		try {
			if (!append) {
				if (exists())
					throw new IOException(
							String.format("The remote file '%s' already exists.", origin.getAbsolutePath()));
			}
			Clock clock = new Clock();
			Timestamp currentTime = clock.getSqlTimestamp();
			Map<String, Object> lastRecord = getLastIndexRecord();
			int filePartIndex = 0;
			if (lastRecord != null) {
				if (lastRecord.get("file_type").equals("D")) {
					throw new IOException(String.format("This is a folder '%s'.", origin.getAbsolutePath()));
				}
				if (lastRecord.get("file_type").equals("L")) {
					throw new IOException(String.format("This is a link '%s'.", origin.getAbsolutePath()));
				}
				if (append) {
					filePartIndex = Integer.parseInt(lastRecord.get("file_part_index") + "") + 1;
				}
				boolean isDeleted = false;
				Object deletedValue = lastRecord.get("deleted");
				if (deletedValue instanceof Boolean) {
					isDeleted = (Boolean) deletedValue;
				} else {
					isDeleted = Integer.parseInt(deletedValue + "") == 1;
				}
				if (isDeleted) {
					throw new IOException(String.format("The file '%s' is deleted.", origin.getAbsolutePath()));
				}
			}
			calculateDataMappingDataSourceWriter(this);
			log.debug("Write: {}", getPath());
			String remoteFileId = generateId(identityCardEnable, identityCardName, identityCardPrefix,
					identityCardSuffix);
			Map<String, Object> rfRecord = new HashMap<String, Object>();
			rfRecord.put("id", remoteFileId);
			rfRecord.put("file_name", origin.getName());
			rfRecord.put("file_parent_path", getParent());
			rfRecord.put("file_type", "F");
			rfRecord.put("file_part_index", filePartIndex);
			rfRecord.put("file_part_size", data.length);
			rfRecord.put("file_part_data_ds", this.dataMappingDs);
			rfRecord.put("file_part_data_table", this.dataMappingTable);
			rfRecord.put("file_part_last_index", -1);
			rfRecord.put("file_size", 0);
			rfRecord.put("file_modify_time", modifyTime == null ? currentTime : modifyTime);
			rfRecord.put("file_deleted", 0);
			rfRecord.put("created_user_id", getDefaultModifyUserId());
			rfRecord.put("updated_user_id", getDefaultModifyUserId());
			rfRecord.put("created_at", currentTime);
			rfRecord.put("updated_at", currentTime);
			rfRecord.put("file_id", fileId);
			rfRecord.put("source_hostname", CommonTools.getHostname());
			rfRecord.put("synced_on_hostnames", String.format(";%s;", CommonTools.getHostname()));
			rfRecord.put("deleted_on_hostnames", ";");

			Connection conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			executor.execute(formatSql(SQL_INSERT_INTO_FILE), rfRecord);

			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("id", generateDataId(identityCardEnable, identityCardName, identityCardSuffix, this));
			dataRecord.put("file_part_data", encrypt(data));
			dataRecord.put("remote_file_id", remoteFileId);
			dataRecord.put("file_id", fileId);
			dataRecord.put("created_at", currentTime);
			dataRecord.put("updated_at", currentTime);

			conn = dataMappingDataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			dmExecutor = new CacheDriverExecutor(conn);
			dmExecutor.execute(formatSqlForFilePartData(SQL_INSERT_INTO_FILE_DATA), dataRecord);

			return true;
		} catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
			try {
				if (dmExecutor != null)
					dmExecutor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}


	/**
	* Performs the write operation
	* @param data - the data
	* @return the write result
	* */

	public boolean write(byte[] data) throws IOException {
		return write(data, false);
	}


	/**
	* Performs the write operation
	* @param data - the data
	* @param append - the append
	* @return the write result
	* */

	public boolean write(String data, boolean append) throws IOException {
		return write(data.getBytes(CHARSET), append);
	}


	/**
	* Performs the write operation
	* @param data - the data
	* @return the write result
	* */

	public boolean write(String data) throws IOException {
		return write(data.getBytes(CHARSET));
	}


	/**
	* Performs the createLink operation
	* @param target - the target
	* @return the createLink result
	* */

	public boolean createLink(String target) throws IOException {
	    boolean allowed = beforeCreateLink(target);

	    if(!allowed) return false;

		Clock clock = new Clock();
		CacheDriverExecutor executor = null;
		CacheDriverExecutor dmExecutor = null;
		try {
			if (getFirstFilePart() != null) {
				return false;
			}
			log.debug("CreateLink: {}", getPath());
			Timestamp currentTime = clock.getSqlTimestamp();
			calculateDataMappingDataSourceWriter(this);
			String remoteFileId = generateId(identityCardEnable, identityCardName, identityCardPrefix,
					identityCardSuffix);
			byte[] data = target.getBytes(CHARSET);
			Map<String, Object> rfRecord = new HashMap<String, Object>();
			rfRecord.put("id", remoteFileId);
			rfRecord.put("file_name", origin.getName());
			rfRecord.put("file_parent_path", getParent());
			rfRecord.put("file_type", "L");
			rfRecord.put("file_part_index", 0);
			rfRecord.put("file_part_size", data.length);
			rfRecord.put("file_part_data_ds", this.dataMappingDs);
			rfRecord.put("file_part_data_table", this.dataMappingTable);
			rfRecord.put("file_part_last_index", -1);
			rfRecord.put("file_size", 0);
			rfRecord.put("file_modify_time", modifyTime == null ? currentTime : modifyTime);
			rfRecord.put("file_deleted", 0);
			rfRecord.put("created_user_id", getDefaultModifyUserId());
			rfRecord.put("updated_user_id", getDefaultModifyUserId());
			rfRecord.put("created_at", currentTime);
			rfRecord.put("updated_at", currentTime);
			rfRecord.put("file_id", fileId);
			rfRecord.put("source_hostname", CommonTools.getHostname());
			rfRecord.put("synced_on_hostnames", String.format(";%s;", CommonTools.getHostname()));
			rfRecord.put("deleted_on_hostnames", ";");

			Connection conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			int rows = executor.execute(formatSql(SQL_INSERT_INTO_FILE), rfRecord);

			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("id", generateDataId(identityCardEnable, identityCardName, identityCardSuffix, this));
			dataRecord.put("file_part_data", encrypt(data));
			dataRecord.put("remote_file_id", remoteFileId);
			dataRecord.put("file_id", fileId);
			dataRecord.put("created_at", currentTime);
			dataRecord.put("updated_at", currentTime);

			conn = dataMappingDataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			dmExecutor = new CacheDriverExecutor(conn);
			dmExecutor.execute(formatSqlForFilePartData(SQL_INSERT_INTO_FILE_DATA), dataRecord);

			complete();

            afterCreateLink(target);

			return rows > 0;
		} catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
			try {
				if (dmExecutor != null)
					dmExecutor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}


	/**
	* Performs the readLink operation
	* @return the readLink result
	* */

	public String readLink() throws IOException {
		return new String(readAllBytes(),BaseFile.CHARSET);
	}


	/**
	* Performs the readAllBytes operation
	* @return the readAllBytes result
	* */

	public byte[] readAllBytes() throws IOException {
		return readAllBytesFrom();
	}

	/**
	* Performs the readAllBytesFrom operation
	* @return the readAllBytesFrom result
	* */

	protected byte[] readAllBytesFrom() throws IOException {
		if (isDir()) {
			throw new IOException(String.format("The file '%s' is a folder.", origin.getAbsolutePath()));
		}
		if (!exists()) {
			throw new IOException(String.format("The file '%s' does not exist.", origin.getAbsolutePath()));
		}
		CacheDriverExecutor executor = null;
		Connection conn = null;
		ByteArrayOutputStream baos = null;
		try {
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> dataRecord = getCompletedRecord();
			List<Map<String, Object>> filePartList = executor.find(formatSql(SQL_SELECT_FILE), dataRecord);
			baos = new ByteArrayOutputStream();
			for (Map<String, Object> fpItem : filePartList) {
				Map<String, Object> fp = readPart(fpItem);
				Object fpData = null;

				if(fp == null){
				    fpData = new byte[]{};
				}else{
				    fpData = fp.get("file_part_data");
				}

				if (fpData instanceof byte[]) {
					byte[] partData = (byte[]) fpData;
					baos.write(decrypt(partData));
					baos.flush();
				}
			}
			return baos == null ? null : baos.toByteArray();
		} catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			try {
				if (baos != null)
					baos.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
			try {
				if (executor != null)
					executor.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}


	/**
	* Performs the isFile operation
	* @return the isFile result
	* */

	public boolean isFile() throws IOException {
		Map<String, Object> lastRecord = getFirstFilePart();

		if (lastRecord == null) {
			return false;
		}
		return lastRecord.get("file_type").equals("F");
	}


	/*
	* Performs the isDirectory operation
	* @return the isDirectory result
	* */


	/**
	* Performs the isLink operation
	* @return the isLink result
	* */
	public boolean isLink() throws IOException {
		Map<String, Object> lastRecord = getFirstFilePart();

		if (lastRecord == null) {
			return false;
		}
		return lastRecord.get("file_type").equals("L");
	}


	/*
	* Performs the readAllString operation
	* @return the readAllString result
	* */


	/**
	* Performs the size operation
	* @return the size result
	* */
	public long size() throws IOException {
		Map<String, Object> record = getCompletedRecord();
		if (record != null) {
			return Long.parseLong(record.get("file_size") + "");
		}
		return 0L;
	}

	public List<Map<String, Object>> list() throws IOException {
		return list(getPath(), "%");
	}

	public List<Map<String, Object>> list(String likeFileName) throws IOException {
		return list(getPath(), likeFileName);
	}

	public List<Map<String, Object>> list(String likeFolderName, String likeFileName) throws IOException {
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			if (CommonTools.isBlank(likeFileName)) {
				dataRecord.put("file_name", "%");
			} else {
				dataRecord.put("file_name", likeFileName);
			}
			if (CommonTools.isBlank(likeFolderName)) {
				dataRecord.put("file_parent_path", "%");
			} else {
				dataRecord.put("file_parent_path", likeFolderName);
			}
			return executor.find(formatSql(SQL_SELECT_FOR_LIST_ALL), dataRecord);
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			if (executor != null) {
				try {
					executor.close();
				} catch (Exception e) {
					throw new IOException(e.getMessage(), e);
				}
			}
		}
	}

	/*
	* Performs the listAll operation
	* @param likeFileName - the likeFileName
	* @param rows - the rows
	* */


	/**
	* Performs the listAllForDelete operation
	* @param likeFolderName - the likeFolderName
	* @param likeFileName - the likeFileName
	* @param rows - the rows
	* */
	protected void listAllForDelete(String likeFolderName, String likeFileName, CacheArray rows) throws IOException {
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
		    long deleteCutoffTimeMs = 0L;

		    if(DELETE_CUTOFF_TIME != null) deleteCutoffTimeMs = DELETE_CUTOFF_TIME.getTime();

			Timestamp currentTime = new Clock().getSqlTimestamp();

			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			if (CommonTools.isBlank(likeFileName)) {
				dataRecord.put("file_name", "%");
			} else {
				dataRecord.put("file_name", likeFileName);
			}
			if (CommonTools.isBlank(likeFolderName)) {
				dataRecord.put("file_parent_path", "%");
			} else {
				dataRecord.put("file_parent_path", likeFolderName);
			}
			dataRecord.put("deleted_on_hostnames", "%;" + CommonTools.getHostname() + ";%");
			dataRecord.put("begin_time", new Timestamp(deleteCutoffTimeMs));
			dataRecord.put("end_time", currentTime);
			dataRecord.put("created_at", new Timestamp(currentTime.getTime() - BEFORE_THE_QUEUE_TIME));
			executor.find(0, MAX_QUEUE_SIZE,formatSql(SQL_SELECT_FOR_LIST_ALL_DELETE), dataRecord, rows);
		} catch (SQLException e) {
			throw new IOException(e.getMessage(), e);
		} finally {
			if (executor != null) {
				try {
					executor.close();
				} catch (Exception e) {
					throw new IOException(e.getMessage(), e);
				}
			}
		}
	}


	/**
	* Performs the setModifyUserId operation
	* @param modifyUserId - the modifyUserId
	* */

	public void setModifyUserId(String modifyUserId) {
		this.modifyUserId = modifyUserId;
	}

	/**
	* Performs the getDefaultModifyUserId operation
	* @return the getDefaultModifyUserId result
	* */

	protected String getDefaultModifyUserId() {
		if (CommonTools.isBlank(modifyUserId)) {
			return DEFAULE_MODIFY_USER_ID;
		} else {
			return modifyUserId;
		}
	}


	/**
	* Performs the setModifyTime operation
	* @param modifyTime - the modifyTime
	* */

	public void setModifyTime(Timestamp modifyTime) throws IOException {
		this.modifyTime = modifyTime;
	}


	/**
	* Performs the getModifyTime operation
	* @return the getModifyTime result
	* */

	public Timestamp getModifyTime() throws IOException {
		Map<String, Object> record = getFirstFilePart();
		if (record != null) {
			return (Timestamp) record.get("file_modify_time");
		}
		return null;
	}

	/*
	* Performs the isTimeout operation
	* @return the isTimeout result
	* */


	/**
	* Performs the copyTo operation
	* @param toPath - the toPath
	* */
	public void copyTo(String toPath) throws IOException {
		log.debug("CopyTo: {} -> {}", getPath(), toPath);
		if (!exists()) {
			log.mark(String.format("The remote file '%s' does not exist.", this.getPath()));
		}
		RemoteFileBase destRf = new RemoteFile(toPath);
		final Timestamp originModifyTime = this.getModifyTime();
		destRf.setModifyUserId(modifyUserId);
		destRf.setModifyTime(originModifyTime);
		if (isLink()) {
			if (destRf.exists(true)) {
				destRf.forceDeleteLink(false);
			}
			destRf.createLink(readLink());
		}
		if (isFile()) {
			final RemoteFileBase _destRf = destRf;
			if (_destRf.exists(true)) {
				_destRf.forceDeleteFile(false);
			}
			merge(new FilePart() {

				/**
				* Performs the process operation
				* @param partIndex - the partIndex
				* @param data - the data
				* */
				protected void process(int partIndex, byte[] data) {
					try {
						_destRf.write(decrypt(data), partIndex > 0);
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}


				/**
				* Performs the completed operation
				* @param lastPartIndex - the lastPartIndex
				* @param modifyTime - the modifyTime
				* @param fileSize - the fileSize
				* */
				protected void completed(int lastPartIndex, Timestamp modifyTime, long fileSize) {
					try {
						/**This is 0 bytes file**/
						if (lastPartIndex == -1) {

							if (_destRf.exists(true))
								_destRf.forceDeleteFile(false);

							_destRf.setModifyTime(originModifyTime);
							_destRf.createEmptyFile();
						} else {
							_destRf.setModifyTime(originModifyTime);
							_destRf.complete();
						}
					} catch (IOException e) {
						log.error(e.getMessage(), e);
					}
				}


				/**
				* Performs the ended operation
				* @param lastPartIndex - the lastPartIndex
				* @param fileSize - the fileSize
				* */
				protected void ended(int lastPartIndex, long fileSize) {

				}

			});
		}
		if (isDir()) {
			List<Map<String, Object>> copyList = list(getPath(), "%");
			int size = copyList.size();
			if (size > 0) {
				if (!destRf.exists(true)) {
					destRf.mkdirs();
				}
			}
			String copyRootPath = getPath();
			copyList = list(parsePath(getPath() + "/%"), "%");
			if (copyList.size() > 0) {
				if (!destRf.exists(true)) {
					destRf.mkdirs();
				}
			}
			for (Map<String, Object> item : copyList) {
				String itemParentPath = (String) item.get("file_parent_path");
				String destParentPath = itemParentPath.replaceFirst(copyRootPath + "/", toPath);
				RemoteFileBase sourceRf = new RemoteFile(
						String.format("%s/%s", item.get("file_parent_path"), item.get("file_name")));
				String destRfPath = new File(String.format("%s/%s", destParentPath, item.get("file_name")))
						.getAbsolutePath();
				if (sourceRf.isDir()) {
					RemoteFileBase tmpRf = new RemoteFile(destRfPath + "/");
					tmpRf.setModifyTime(sourceRf.getModifyTime());
					if (!tmpRf.exists(true)) {
						tmpRf.mkdirs();
					}
				} else {
					sourceRf.copyTo(destRfPath);
				}
			}
		}
	}


	/**
	* Performs the moveTo operation
	* @param toPath - the toPath
	* */

	public void moveTo(String toPath) throws IOException {
		log.debug("MoveTo: {} -> {}", getPath(), toPath);
		RemoteFileBase destRf = new RemoteFile(toPath);
		copyTo(toPath);
		// copyTo() drives merge() over the async CacheArray worker, so the
		// destination only becomes complete after this method returns. Wait
		// for the copy (bounded) before removing the source - otherwise the
		// completed-check below always fails and moveTo silently degrades
		// into copyTo (the source is never deleted).
		boolean copied = false;
		long deadline = System.currentTimeMillis() + COPY_WAIT_TIMEOUT;
		while (System.currentTimeMillis() < deadline) {
			try {
				if (destRf.exists(true) && destRf.isCompleted()) {
					copied = true;
					break;
				}
			} catch (Exception e) {
				log.debug(e.getMessage());
			}
			try {
				Thread.sleep(COPY_WAIT_POLL_MS);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		boolean exists = this.exists(true);
		if (exists && copied) {
			forceDeleteAll(true);
		}
	}


	/*
	* Performs the startFullAsync operation
	* @param timerMs - the timerMs
	* */


	/**
	* Performs the getDataSourceReader operation
	* @return the getDataSourceReader result
	* */
	public DriverDataSource getDataSourceReader() {
		return dataSourceReader;
	}

	/**
	* Performs the getDataSourceWriter operation
	* @return the getDataSourceWriter result
	* */

	public DriverDataSource getDataSourceWriter() {
		return dataSourceWriter;
	}

	/*
	* Performs the getConfigProperties operation
	* @return the getConfigProperties result
	* */


	/**
	* Performs the encrypt operation
	* @param data - the data
	* @return the encrypt result
	* */
	protected byte[] encrypt(byte[] data) throws Exception {
		boolean useEncrypt = configProperties.getBoolean("EncryptEnable", false);
		if (useEncrypt) {
            String aesKey = configProperties.getString("EncryptAesKey");
            return CipherTools.AESEncrypt(aesKey,data);
		} else {
			return data;
		}
	}

	/**
	* Performs the decrypt operation
	* @param encData - the encData
	* @return the decrypt result
	* */

	protected byte[] decrypt(byte[] encData) throws Exception {
		boolean useEncrypt = configProperties.getBoolean("EncryptEnable", false);
		if (useEncrypt) {
            String aesKey = configProperties.getString("EncryptAesKey");
            return CipherTools.AESDecrypt(aesKey,encData);
		} else {
			return encData;
		}
	}

	/*
	* Performs the getSyncedOnHostnames operation
	* @return the getSyncedOnHostnames result
	* */


	/**
	* Performs the getLastSourceHostname operation
	* @return the getLastSourceHostname result
	* */

	protected String getLastSourceHostname() throws IOException {
		Map<String, Object> record = getLastUpdateRecord();
		if (record != null) {
			return (String) record.get("source_hostname");
		}
		return null;
	}


	/**
	* Performs the getParentFile operation
	* @return the getParentFile result
	* */

	public RemoteFile getParentFile() {
		return new RemoteFile(getParent());
	}


	/**
	* Performs the startArchiveForFileDeleted operation
	* @param timerMs - the timerMs
	* @param archiveTime - the archiveTime
	* */


	public static Map<String, DriverDataSource> getDriverDataSourceMap() {
		return Collections.synchronizedMap(DATASOURCE_REMOTE_FILE);
	}

	/**
	* Performs the generateId operation
	* @param identityCardEnable - the identityCardEnable
	* @param identityCardName - the identityCardName
	* @param identityCardPrefix - the identityCardPrefix
	* @param identityCardSuffix - the identityCardSuffix
	* @return the generateId result
	* */

	protected static synchronized String generateId(boolean identityCardEnable, String identityCardName,
			String identityCardPrefix, int identityCardSuffix) throws SQLException {
		if (identityCardEnable) {
			if (identityCard == null) {
				identityCard = new IdentityCard(identityCardName);
			}
			return identityCard.generateId(16, identityCardPrefix, "", identityCardSuffix);
		} else {
			return CommonTools.generateId(16);
		}
	}

	/**
	* Performs the generateDataId operation
	* @param identityCardEnable - the identityCardEnable
	* @param identityCardName - the identityCardName
	* @param identityCardSuffix - the identityCardSuffix
	* @param theRf - the theRf
	* @return the generateDataId result
	* */

	protected static synchronized String generateDataId(boolean identityCardEnable, String identityCardName,
			int identityCardSuffix, RemoteFileBase theRf) throws SQLException {
		if (identityCardEnable) {
			if (identityCard == null) {
				identityCard = new IdentityCard(identityCardName + "Data");
			}
			return identityCard.generateId(16, String.format("%04d", theRf.dataMappingTable), "", identityCardSuffix);
		} else {
			return CommonTools.generateId(16);
		}
	}

	/**
	* Performs the addQueuePathMapping operation
	* @param path - the path
	* @return the addQueuePathMapping result
	* */

	protected static synchronized boolean addQueuePathMapping(String path) {
		Long addTimeMs = null;

		boolean allowed = checkQueuePathMapping();

		if (!allowed)
			return false;

		addTimeMs = QUEUE_PATH_MAPPING.get(path);

		if (addTimeMs == null) {
			QUEUE_PATH_MAPPING.put(path, System.currentTimeMillis());
			return true;
		}

		return false;
	}

	/**
	* Performs the checkQueuePathMapping operation
	* @return the checkQueuePathMapping result
	* */

	public static synchronized boolean checkQueuePathMapping() {
		Long addTimeMs = null;
		Map<String, Long> copyQueueMap = getQueuePathMapping();
		List<String> keyList = new ArrayList<String>(copyQueueMap.keySet());
		for (String key : keyList) {
			addTimeMs = copyQueueMap.get(key);
			if (addTimeMs != null) {
				boolean timeout = (System.currentTimeMillis() - addTimeMs) >= LOGIC_TIMEOUT_MS;

				if (timeout) {
					QUEUE_PATH_MAPPING.remove(key);
				}
			}
		}

		if (QUEUE_PATH_MAPPING.keySet().size() >= MAX_QUEUE_SIZE)
			return false;

		return true;
	}

	/**
	* Performs the removeQueuePathMapping operation
	* @param path - the path
	* */

	protected static synchronized void removeQueuePathMapping(String path) {
		QUEUE_PATH_MAPPING.remove(path);
	}

	public static synchronized Map<String, Long> getQueuePathMapping() {
		return Collections.synchronizedMap(QUEUE_PATH_MAPPING);
	}

	/**
	* Performs the loadDataMapping operation
	* @param rf - the rf
	* */

	protected static synchronized void loadDataMapping(RemoteFileBase rf) throws Exception {
		List<String> dmArray = rf.configProperties.getArray("DataMapping", new String[] {});
		int size = dmArray.size();
		List<String> dmList = DATA_MAPPING_LIST.get(rf.dsKey);

		if (dmList == null)
			dmList = new ArrayList<String>();

		if (!DATA_MAPPING_INDEX.containsKey(rf.dsKey))
			DATA_MAPPING_INDEX.put(rf.dsKey, 0);

		for (int i = 0; i < size; i++) {
			String dm = dmArray.get(i);
			String originKey = String.format("DataMapping[%s]", i);
			Map<String, Object> dmMap = rf.configProperties.getMap(originKey);
			String shortKey = String.format("%s.%s", rf.dsKey, dm);
			if (dmMap != null) {
				String dmKey = String.format("%s.%s", rf.dsKey, originKey);

				if (!DATA_MAPPING.containsKey(shortKey)) {
					ConfigProperties dmCp = new ConfigProperties();
					dmCp.putAllAndReturnSelf(dmMap);
					int priority = dmCp.getInteger(String.format("%s.Priority", originKey), 0);
					if (i == 0 && priority < 1) {
						throw new Exception("First DataMapping priority cannot be less than 1.");
					}
					String dataSourceFileOriginValue = dmCp.getString(String.format("%s.DataSource", originKey));
					String dataSourceReaderFileOriginValue = dmCp
							.getString(String.format("%s.DataSourceReader", originKey), dataSourceFileOriginValue);
					String dataSourceWriterFileOriginValue = dmCp
							.getString(String.format("%s.DataSourceWriter", originKey), dataSourceFileOriginValue);

					String dmDsReaderKey = String.format("%s.reader", CommonTools.md5(dataSourceReaderFileOriginValue));
					String dmDsWriterKey = String.format("%s.writer", CommonTools.md5(dataSourceWriterFileOriginValue));
					DriverDataSource dmDataSourceReader = DATASOURCE_REMOTE_FILE.get(dmDsReaderKey);
					DriverDataSource dmDataSourceWriter = DATASOURCE_REMOTE_FILE.get(dmDsWriterKey);
					if (dmDataSourceReader == null) {
						DATASOURCE_REMOTE_FILE.put(dmDsReaderKey,
								new DriverDataSource(new File(dataSourceReaderFileOriginValue)));
					}
					if (dmDataSourceWriter == null) {
						DATASOURCE_REMOTE_FILE.put(dmDsWriterKey,
								new DriverDataSource(new File(dataSourceWriterFileOriginValue)));
					}
					String tableRangOriginValue = dmCp.getString(String.format("%s.TableRange", originKey), "0");
					ConfigProperties shortCp = new ConfigProperties();
					List<Integer> tableRange = new ArrayList<Integer>();
					if (tableRangOriginValue.contains("-")) {
						String[] ranges = tableRangOriginValue.split("-");
						Integer begin = Integer.parseInt(ranges[0].trim());
						Integer end = Integer.parseInt(ranges[1].trim());
						for (int j = begin; j <= end; j++) {
							tableRange.add(begin + j);
						}
					} else if (tableRangOriginValue.contains(",")) {
						String[] ranges = tableRangOriginValue.split(",");
						int rangesLen = ranges.length;
						for (int j = 0; j < rangesLen; j++) {
							tableRange.add(Integer.parseInt(ranges[j].trim()));
						}
					} else {
						tableRange.add(Integer.parseInt(tableRangOriginValue.trim()));
					}
					shortCp.put("TableRange", tableRange);
					shortCp.putAndReturnSelf("DataSourceReader", dmDsReaderKey);
					shortCp.putAndReturnSelf("DataSourceWriter", dmDsWriterKey);
					shortCp.putAndReturnSelf("Priority", priority);
					shortCp.putAndReturnSelf("UsingCount", 0);
					shortCp.putAndReturnSelf("Name", dm);
					int randomIndex = ThreadLocalRandom.current().nextInt(0, tableRange.size());
					shortCp.putAndReturnSelf("Table", tableRange.get(randomIndex));
					DATA_MAPPING.put(shortKey, shortCp);
					dmList.add(dm);
					DATA_MAPPING_LIST.put(rf.dsKey, dmList);
				}
			}
		}

	}

	/**
	* Performs the calculateDataMappingDataSourceReader operation
	* @param rf - the rf
	* @param String - the String
	* @param rfRecord - the rfRecord
	* */

	protected static synchronized void calculateDataMappingDataSourceReader(RemoteFileBase rf,
			Map<String, Object> rfRecord) {
		rf.dataMappingDs = (String) rfRecord.get("file_part_data_ds");
		rf.dataMappingTable = (Integer) rfRecord.get("file_part_data_table");
		String shortKey = String.format("%s.%s", rf.dsKey, rf.dataMappingDs);
		ConfigProperties cp = DATA_MAPPING.get(shortKey);
		if (cp != null) {
			String dmDsReaderKey = cp.getString("DataSourceReader");
			rf.dataMappingDataSourceReader = DATASOURCE_REMOTE_FILE.get(dmDsReaderKey);
		}
	}

	/**
	* Performs the calculateDataMappingDataSourceWriter operation
	* @param rf - the rf
	* */

	@SuppressWarnings("unchecked")
	protected static synchronized void calculateDataMappingDataSourceWriter(RemoteFileBase rf) {
		if (DATA_MAPPING_INDEX.get(rf.dsKey) >= DATA_MAPPING_LIST.size() - 1) {
			DATA_MAPPING_INDEX.put(rf.dsKey, 0);
		}
		ConfigProperties cp = getDataMapping(rf, DATA_MAPPING_INDEX.get(rf.dsKey));
		List<Integer> tableRange = (List<Integer>) cp.get("TableRange");
		Integer priority = cp.getInteger("Priority", 0);
		Integer usingCount = cp.getInteger("UsingCount", 0);
		String key = String.format("%s.%s", rf.dsKey, cp.getString("Name"));
		if (usingCount < priority) {
			rf.dataMappingDataSourceReader = DATASOURCE_REMOTE_FILE.get(cp.getString("DataSourceReader"));
			rf.dataMappingDataSourceWriter = DATASOURCE_REMOTE_FILE.get(cp.getString("DataSourceWriter"));
			rf.dataMappingDs = cp.getString("Name");
			rf.dataMappingTable = cp.getInteger("Table");
			usingCount++;
			if (usingCount >= priority) {
				DATA_MAPPING_INDEX.put(rf.dsKey, DATA_MAPPING_INDEX.get(rf.dsKey) + 1);
				cp.putAndReturnSelf("UsingCount", 0);
				int randomIndex = ThreadLocalRandom.current().nextInt(0, tableRange.size());
				cp.putAndReturnSelf("Table", tableRange.get(randomIndex));
				DATA_MAPPING.put(key, cp);
			} else {
				cp.putAndReturnSelf("UsingCount", usingCount);
				int randomIndex = ThreadLocalRandom.current().nextInt(0, tableRange.size());
				cp.putAndReturnSelf("Table", tableRange.get(randomIndex));
				DATA_MAPPING.put(key, cp);
			}
		} else {
			DATA_MAPPING_INDEX.put(rf.dsKey, DATA_MAPPING_INDEX.get(rf.dsKey) + 1);
			calculateDataMappingDataSourceWriter(rf);
		}
	}

	/**
	* Performs the getDataMapping operation
	* @param rf - the rf
	* @param index - the index
	* @return the getDataMapping result
	* */

	protected static synchronized ConfigProperties getDataMapping(RemoteFileBase rf, int index) {
		List<String> dmList = DATA_MAPPING_LIST.get(rf.dsKey);
		String dm = dmList.get(index);
		String key = String.format("%s.%s", rf.dsKey, dm);
		return DATA_MAPPING.get(key);
	}

	/**
	* Performs the getDataMappingTableName operation
	* @return the getDataMappingTableName result
	* */

	protected String getDataMappingTableName() {
		return String.format("%s_data_%s", this.tableName, this.dataMappingTable);
	}

	/**
	* Performs the debugLog operation
	* @param point - the point
	* @param rf - the rf
	* @param filter - the filter
	* */

	protected static void debugLog(String point, RemoteFileBase rf, CacheArrayFilter filter){
		if (rf == null || !isDebug(rf)) { return; }
		LoggerFactory.getLogger(RemoteFileBase.class).debug("{}: {}", point, rf.getPath());
	}

}
