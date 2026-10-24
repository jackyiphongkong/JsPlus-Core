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
package com.killcoding.file;

import java.io.File;
import com.killcoding.datasource.DriverDataSource;
import com.killcoding.tool.ConfigProperties;
import com.killcoding.tool.CommonTools;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import java.io.FileInputStream;
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
import java.util.Arrays;
import java.io.ByteArrayOutputStream;
import java.sql.Blob;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.net.URI;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.nio.file.LinkOption;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import com.killcoding.file.DiskFile;
import java.util.Collection;
import com.killcoding.cache.CacheArray;
import com.killcoding.cache.CacheArrayFilter;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Callable;
import com.killcoding.datasource.IdentityCard;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import com.killcoding.tool.CipherTools;
import com.killcoding.cache.Cache;
import java.util.Date;

public class RemoteFile extends BaseFile implements RemoteFileSQL {

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
	private DriverDataSource dataSourceReader = null;
	private DriverDataSource dataSourceWriter = null;
	private String dataSourceReaderPath = null;
	private String dataSourceWriterPath = null;
	private String modifyUserId = null;
	private Integer filePartSize = null;
	private Boolean identityCardEnable = false;
	private String identityCardName = null;
	private String identityCardPrefix = null;
	private Integer identityCardSuffix = 0;
	private Timestamp modifyTime = null;

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

	private Logger log = null;

	private static ExecutorService mergePool = null;

	public RemoteFile(String path) {
		super(path);
		log = LoggerFactory.getLogger(getClass());
		fileId = CommonTools.generateId(16);
		initGlobal();
	}
	
	public RemoteFile(File origin) {
		super(origin);
		log = LoggerFactory.getLogger(getClass());
		fileId = CommonTools.generateId(16);
		initGlobal();
	}	

	public static synchronized void initPool(int poolSize) {
		if (mergePool == null) {
			MAX_POOL_SIZE = poolSize;
			LoggerFactory.getLogger(RemoteFile.class).mark("MAX_POOL_SIZE={}", MAX_POOL_SIZE);
			mergePool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
		}
	}

	private synchronized void init() {
		dsKey = CommonTools.md5(this.appRemoteFile.getAbsolutePath());
		dataSourceReader = DATASOURCE_REMOTE_FILE.get(String.format("%s.reader", dsKey));
		dataSourceWriter = DATASOURCE_REMOTE_FILE.get(String.format("%s.writer", dsKey));
		configProperties = CONFIG_PROPS_REMOTE_FILE.get(dsKey);
		if (dataSourceReader == null && dataSourceWriter == null) {
			try {
				if (!appRemoteFile.exists())
					throw new IOException(String.format("Not exist '%s'.", appRemoteFile.getAbsolutePath()));

				configProperties = new ConfigProperties();
				Runnable updatedRun = new Runnable() {
					@Override
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

	private synchronized void initGlobal() {
	    if(this.appRemoteFile == null){
    		this.appRemoteFile = CommonTools.getSystemProperties(RemoteFile.class, "APP_REMOTE_FILE",
    					"RemoteFile.properties");
	    }
		init();
	}

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

	public String formatSql(String sql) {
		return String.format(sql, tableName);
	}

	public String formatSqlForFilePartData(String sql) {
		return String.format(sql, getDataMappingTableName());
	}

	public DiskFile writeToDisk(DiskFile diskFile) throws IOException {
		if (!exists()) {
			log.mark(String.format("The remote file '%s' does not exist.", this.getPath()));
		}
		RemoteFile the = this;

		Timestamp rfModifyTime = the.getModifyTime();
		if (rfModifyTime != null) {
			diskFile.setModifyTimeMsFromClock(rfModifyTime.getTime());
		}
		if (isLink()) {
			if (isDebug(the)) {
				LoggerFactory.getLogger(RemoteFile.class).mark("CreateDiskLink - {}", diskFile.getPath());
			}
			diskFile.createLink(new String(readAllBytes(),BaseFile.CHARSET));
		} else if (isFile()) {
			merge(new FilePart() {
				final List<byte[]> partDataList = new ArrayList<byte[]>();
				boolean isWritingDf = false;
				DiskFile realDf = null;

				@Override
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

				@Override
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

				@Override
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
				RemoteFile rf = new RemoteFile(
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

	public DiskFile writeToDisk() throws IOException {
		DiskFile df = new DiskFile(this.origin.getAbsolutePath());
		DiskFile.copyAttrs(df, false, this.syncRoot);
		return writeToDisk(df);
	}

	public DiskFile writeToDisk(String path) throws IOException {
		DiskFile df = new DiskFile(path);
		DiskFile.copyAttrs(df, false, this.syncRoot);
		return writeToDisk(df);
	}

	public boolean isCompleted() throws IOException {
		return getCompletedRecord() != null;
	}

	public Map<String, Object> getCompletedRecord() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_COMPLETED);
		Map dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_name", origin.getName());
		dataRecord.put("file_parent_path", getParent());
		String cacheKey = String.format("first-%s-%s",sql,dataRecord);
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
		Map dataRecord = new HashMap<String, Object>();
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

	public Map<String, Object> getRecordByFileId(Object fileId) throws IOException {
		String sql = formatSql(SQL_SELECT_GET_RECORD_BY_FILE_ID);
		Map dataRecord = new HashMap<String, Object>();
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

	public Map<String, Object> getLastUpdateRecord() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_LAST_UPDATE);
		Map dataRecord = new HashMap<String, Object>();
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
	
	protected Timestamp getFirstCreatedAt() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_FIRST_CREATED_AT);
		Map dataRecord = new HashMap<String, Object>();
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
	
	protected Integer getMaxFilePartDataTable() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_MAX_DATA_TABLE);
		Map dataRecord = new HashMap<String, Object>();
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
		Map dataRecord = new HashMap<String, Object>();
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

	private int countAllSubFiles(String fileParentPath, String fileName) throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_COUNT_ALL_SUB_FILES);
		Map dataRecord = new HashMap<String, Object>();
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

	private Map<String, Object> getFirstFilePart() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_FIRST_FILE);
		Map dataRecord = new HashMap<String, Object>();
		dataRecord.put("file_name", origin.getName());
		dataRecord.put("file_parent_path", getParent());
		String cacheKey = String.format("first-%s-%s",sql,dataRecord);
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
	
	private Map<String, Object> getFirstFilePartRefresh() throws IOException {
		String sql = formatSql(SQL_SELECT_FOR_FIRST_FILE);
		Map dataRecord = new HashMap<String, Object>();
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
		Map dataRecord = new HashMap<String, Object>();
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

	public boolean isTimeout(long timeoutMs) throws IOException {
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			Clock clock = new Clock();
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map dataRecord = new HashMap<String, Object>();
			dataRecord.put("file_name", origin.getName());
			dataRecord.put("file_parent_path", getParent());
			Map<String, Object> completedRecord = getCompletedRecord();

			if (completedRecord != null)
				return false;

			Map<String, Object> record = executor.first(formatSql(SQL_SELECT_FOR_TIMEOUT), dataRecord);

			if (record == null)
				return false;

			boolean completed = executor.first(formatSql(SQL_SELECT_CHECK_COMPLETED), record) != null;
			if (!completed) {
				boolean timeout = (clock.getTime() - ((Timestamp) record.get("updated_at")).getTime()) > timeoutMs;

				return timeout;
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

	private Map<String, Object> readPart(Map<String, Object> rfRecord) throws IOException {
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
			Map dataRecord = getCompletedRecord();
			if (dataRecord != null) {
				CacheArray rows = new CacheArray();
				CacheArrayFilter filter = new CacheArrayFilter(CACHE_ARRAY_FILTER_TIMER) {
					long fileSize = 0L;
					Map<String, Object> fp = null;
					Map<String, Object> item = null;

					@Override
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

					@Override
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

					@Override
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

					@Override
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
    							diskFile.logicAccess();
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

					@Override
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

					@Override
					protected void ended(int lastPartIndex, long fileSize) {

					}

				});
			}
		}
	}

	private boolean createEmptyFile() throws IOException {
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
			Map dataRecord = new HashMap<String, Object>();
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
			Map dataRecord = new HashMap<String, Object>();
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

	private boolean complete(int filePartLastIndex, long fileSize) throws IOException {
		Clock clock = new Clock();
		CacheDriverExecutor executor = null;
		Timestamp currentTime = clock.getSqlTimestamp();
		Connection conn = null;
		try {
			conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map dataRecord = new HashMap<String, Object>();
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

	@Override
	public boolean complete() throws IOException {
		Clock clock = new Clock();
		Timestamp currentTime = clock.getSqlTimestamp();
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
			conn = dataSourceWriter.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Map dataRecord = new HashMap<String, Object>();
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

	public boolean forceDelete() throws IOException {
		return forceDeleteAll(true);
	}

	public boolean forceDeleteDir(boolean realDeleted) throws IOException {
		return forceDelete(realDeleted, "D");
	}

	public boolean forceDeleteLink(boolean realDeleted) throws IOException {
		return forceDelete(realDeleted, "L");
	}

	public boolean forceDeleteFile(boolean realDeleted) throws IOException {
		return forceDelete(realDeleted, "F");
	}

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
					Map dataRecord = new HashMap<String, Object>();
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
				Map dataRecord = new HashMap<String, Object>();
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

	@Override
	public boolean delete() throws IOException {
		return forceDeleteAll(true);
	}

	public boolean delete(boolean realDeleted) throws IOException {
		return forceDeleteAll(realDeleted);
	}

	@Override
	public boolean beforeDelete(boolean realDeleted) {
		return true;
	}
	
	@Override
	public void afterDelete(boolean realDeleted) {
	    
	}	
	
	@Override
	public boolean beforeMkdirs() {
	    return true;
	}
	
	@Override
	public void afterMkdirs() {
	    
	}
	
	@Override
	public boolean beforeCreateLink(String target) {
	    return true;
	}
	
	@Override
	public void afterCreateLink(String target) {
	    
	}
	
	@Override
	public boolean beforeWrite(){
	    return true;
	}
	
	@Override
	public void afterWrite(){
	    
	}	

	@Override
	public boolean exists() throws IOException {
		return getFirstFilePart() != null;
	}
	
	public boolean exists(boolean refresh) throws IOException {
	    if(refresh){
	        return getFirstFilePartRefresh() != null;
	    }else{
		    return exists();
	    }
	}	

	@Override
	public boolean mkdirs() throws IOException {
		return mkdirs(false);
	}

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
			Map dataRecord = new HashMap<String, Object>();
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

	@Override
	public boolean write(byte[] data, boolean append) throws IOException {
		return writeTo(data, append);
	}

	public synchronized boolean writeBlock(byte[] data, boolean append) throws IOException {
		return writeTo(data, append);
	}

	private boolean writeToBatch(int currentFilePartStartIndex, List<byte[]> partDataList) throws IOException {
	    
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
				Map rfRecord = new HashMap<String, Object>();
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

	private boolean writeTo(byte[] data, boolean append) throws IOException {
	    
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
			Map rfRecord = new HashMap<String, Object>();
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

			Map dataRecord = new HashMap<String, Object>();
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

	@Override
	public boolean write(byte[] data) throws IOException {
		return write(data, false);
	}

	@Override
	public boolean write(String data, boolean append) throws IOException {
		return write(data.getBytes(CHARSET), append);
	}

	@Override
	public boolean write(String data) throws IOException {
		return write(data.getBytes(CHARSET));
	}

	@Override
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
			Map rfRecord = new HashMap<String, Object>();
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

			Map dataRecord = new HashMap<String, Object>();
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

	@Override
	public String readLink() throws IOException {
		return new String(readAllBytes(),BaseFile.CHARSET);
	}

	@Override
	public byte[] readAllBytes() throws IOException {
		return readAllBytesFrom();
	}

	private byte[] readAllBytesFrom() throws IOException {
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
			Map dataRecord = getCompletedRecord();
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

	@Override
	public boolean isFile() throws IOException {
		Map<String, Object> lastRecord = getFirstFilePart();

		if (lastRecord == null) {
			return false;
		}
		return lastRecord.get("file_type").equals("F");
	}

	@Override
	public boolean isDirectory() throws IOException {
		Map<String, Object> lastRecord = getFirstFilePart();

		if (lastRecord == null) {
			return false;
		}
		return lastRecord.get("file_type").equals("D");
	}

	@Override
	public boolean isLink() throws IOException {
		Map<String, Object> lastRecord = getFirstFilePart();

		if (lastRecord == null) {
			return false;
		}
		return lastRecord.get("file_type").equals("L");
	}

	@Override
	public String readAllString() throws IOException {
		ByteArrayOutputStream baos = null;
		try {
			byte[] bytes = readAllBytes();
			
			if(bytes == null) return null;
			
			baos = new ByteArrayOutputStream();
			baos.write(bytes);
			baos.flush();
			return baos.toString();
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		}
	}

	@Override
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

	public void listAll(String likeFileName, CacheArray rows) throws IOException {
		listAll(getPath(), likeFileName, rows);
	}

	public void listAll(CacheArray rows) throws IOException {
		listAll(getPath(), "%", rows);
	}

	public void listAll(String likeFolderName, String likeFileName, CacheArray rows) throws IOException {
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
			executor.find(formatSql(SQL_SELECT_FOR_LIST_ALL), dataRecord, rows);
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

	protected void listAllForSync(String likeFolderName, String likeFileName, CacheArray rows) throws IOException {
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
		    long deleteCutoffTimeMs = 0L;
		    
		    if(DELETE_CUTOFF_TIME != null) deleteCutoffTimeMs = SYNC_CUTOFF_TIME.getTime();
		    
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
			dataRecord.put("synced_on_hostnames", "%;" + CommonTools.getHostname() + ";%");
			dataRecord.put("begin_time", new Timestamp(deleteCutoffTimeMs));
			dataRecord.put("end_time", currentTime);
			dataRecord.put("created_at", new Timestamp(currentTime.getTime() - BEFORE_THE_QUEUE_TIME));
			executor.find(0, MAX_QUEUE_SIZE, formatSql(SQL_SELECT_FOR_LIST_ALL_SYNC), dataRecord, rows);
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

	public void setModifyUserId(String modifyUserId) {
		this.modifyUserId = modifyUserId;
	}

	private String getDefaultModifyUserId() {
		if (CommonTools.isBlank(modifyUserId)) {
			return DEFAULE_MODIFY_USER_ID;
		} else {
			return modifyUserId;
		}
	}

	@Override
	public void setModifyTime(Timestamp modifyTime) throws IOException {
		this.modifyTime = modifyTime;
	}

	@Override
	public Timestamp getModifyTime() throws IOException {
		Map<String, Object> record = getFirstFilePart();
		if (record != null) {
			return (Timestamp) record.get("file_modify_time");
		}
		return null;
	}

	public boolean isTimeout() throws IOException {
		return isTimeout(LOGIC_TIMEOUT_MS);
	}

	@Override
	public void copyTo(String toPath) throws IOException {
		log.debug("CopyTo: {} -> {}", getPath(), toPath);
		if (!exists()) {
			log.mark(String.format("The remote file '%s' does not exist.", this.getPath()));
		}
		RemoteFile destRf = new RemoteFile(toPath);
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
			final RemoteFile _destRf = destRf;
			if (_destRf.exists(true)) {
				_destRf.forceDeleteFile(false);
			}
			merge(new FilePart() {
				@Override
				protected void process(int partIndex, byte[] data) {
					try {
						_destRf.write(decrypt(data), partIndex > 0);
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}

				@Override
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

				@Override
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
				RemoteFile sourceRf = new RemoteFile(
						String.format("%s/%s", item.get("file_parent_path"), item.get("file_name")));
				String destRfPath = new File(String.format("%s/%s", destParentPath, item.get("file_name")))
						.getAbsolutePath();
				if (sourceRf.isDir()) {
					RemoteFile tmpRf = new RemoteFile(destRfPath + "/");
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

	@Override
	public void moveTo(String toPath) throws IOException {
		log.debug("MoveTo: {} -> {}", getPath(), toPath);
		RemoteFile destRf = new RemoteFile(toPath);
		copyTo(toPath);
		boolean exists = this.exists(true);
		if (exists && destRf.exists(true) && destRf.isCompleted()) {
			forceDeleteAll(true);
		}
	}

	private void startFullAsyncThreadForDelete(boolean subFolders, long timerMs, String replaceTo,
			Runnable completedCallback) {
		RemoteFile the = this;
		try {
			if (syncRoot == null) {
				File replaceToFile = new File(replaceTo);
				if (replaceToFile.getParent() == null) {
					throw new IOException("Cannot execute delete task.");
				}
				String replaceToParent = replacePath(replaceToFile.getParent());
				syncRoot = new File(String.format("%s/%s", replaceToParent, replaceToFile.getName()));
			}

			CacheArray rows = new CacheArray();
			CacheArrayFilter filter = new CacheArrayFilter(CACHE_ARRAY_FILTER_TIMER) {
				@Override
				public void execute(Integer index, Object o) {
					Map<String, Object> item = (Map<String, Object>) o;
					String fileName = (String) item.get("file_name");
					String fullPath = String.format("%s/%s", item.get("file_parent_path"), fileName);
					Thread.currentThread()
							.setName(String.format("RemoteFile-startFullAsyncThreadForDelete-%s", fileName));
					RemoteFile rf = new RemoteFile(fullPath);
					if(isDebug(rf)){
					    debugLog("CheckUsage",rf,this);
					}					
					if (memoryUsage() < RemoteFile.MAX_MEMORY_USAGE) {
						rf.fileId = (String) item.get("file_id");
    					String toDf = rf.getPath();
    					if (!CommonTools.isBlank(replaceTo)) {
    						if (subFolders) {
    							toDf = fullPath.replaceFirst(getPath() + "/", replaceTo + "/");
    						} else {
    							toDf = fullPath.replaceFirst(getPath(), replaceTo + "/");
    						}
    					}
    					DiskFile df = new DiskFile(toDf);
    					DiskFile.copyAttrs(df, false, the.syncRoot);
    					executeSyncRunnable(this, item, df, rf);	    
					} else{
					    log.warn("Over max memory usage 'MAX_MEMORY_USAGE={}'.",MAX_MEMORY_USAGE);
					}
				}

				@Override
				public void terminated() {
					log.warn("Terminated: {}-{}", Thread.currentThread().getName(), Thread.currentThread().getId());
				}

				@Override
				public void completed(Integer size) {
					Thread.currentThread().setName(String
							.format("RemoteFile-startFullAsyncThreadForDelete-completed-%s", the.origin.getName()));
					if (isStopSync()) {
						LoggerFactory.getLogger(RemoteFile.class).mark("Stoped start delete task thread.");
					}
					if (timerMs <= 0) {
						LoggerFactory.getLogger(RemoteFile.class).mark("Completed start delete task thread.");
					}
					if (!isStopSync() && timerMs > 0) {
						try {
							Thread.sleep(timerMs);
							if (!isStopSync()) {
								startFullAsyncThreadForDelete(!subFolders, timerMs, replaceTo, completedCallback);
							}
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						}
					}
					if (completedCallback != null) {
						completedCallback.run();
					}
				}

			};
			rows.filter(filter);
			listAllForDelete(getPath() + (subFolders ? "/%" : ""), "%", rows);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if (timerMs > 0) {
				try {
					Thread.sleep(timerMs);
					startFullAsyncThreadForDelete(subFolders, timerMs, replaceTo, completedCallback);
				} catch (InterruptedException ee) {
					log.error(ee.getMessage(), ee);
				}
			}
		}
	}

	private void startFullAsyncThread(boolean subFolders, long timerMs, String replaceTo, Runnable completedCallback) {
		RemoteFile the = this;
		try {
			if (syncRoot == null) {
				File replaceToFile = new File(replaceTo);
				if (replaceToFile.getParent() == null) {
					throw new IOException("Cannot sync the root path.");
				}
				String replaceToParent = replacePath(replaceToFile.getParent());
				syncRoot = new File(String.format("%s/%s", replaceToParent, replaceToFile.getName()));
			}

			CacheArray rows = new CacheArray();
			CacheArrayFilter filter = new CacheArrayFilter(CACHE_ARRAY_FILTER_TIMER) {
				@Override
				public void execute(Integer index, Object o) {
					Map<String, Object> item = (Map<String, Object>) o;
					String fileName = (String) item.get("file_name");
					Thread.currentThread().setName(String.format("RemoteFile-startFullAsyncThread-%s", fileName));
					String fullPath = String.format("%s/%s", item.get("file_parent_path"), fileName);
					RemoteFile rf = new RemoteFile(fullPath);
					if(isDebug(the)){
					    debugLog("CheckUsage",the,this);
		            }
					if (memoryUsage() < RemoteFile.MAX_MEMORY_USAGE) {
						String toDf = rf.getPath();
						if (!CommonTools.isBlank(replaceTo)) {
							if (subFolders) {
								toDf = fullPath.replaceFirst(getPath() + "/", replaceTo + "/");
							} else {
								toDf = fullPath.replaceFirst(getPath(), replaceTo + "/");
							}
						}
						DiskFile df = new DiskFile(toDf);
						DiskFile.copyAttrs(df, false, the.syncRoot);
						executeSyncRunnable(this, item, df, rf);
					} else{
					     log.warn("Over max memory usage 'MAX_MEMORY_USAGE={}'.",MAX_MEMORY_USAGE);
					}
				}

				@Override
				public void terminated() {
					log.warn("Terminated: {}-{}", Thread.currentThread().getName(), Thread.currentThread().getId());
				}

				@Override
				public void completed(Integer size) {
					Thread.currentThread().setName(
							String.format("RemoteFile-startFullAsyncThread-completed-%s", the.origin.getName()));
					if (isStopSync()) {
						LoggerFactory.getLogger(RemoteFile.class).mark("Stoped start full async thread.");
					}
					if (timerMs <= 0) {
						LoggerFactory.getLogger(RemoteFile.class).mark("Completed start full async thread.");
					}
					if (!isStopSync() && timerMs > 0) {
						try {
							Thread.sleep(timerMs);
							if (!isStopSync()) {
								startFullAsyncThread(!subFolders, timerMs, replaceTo, completedCallback);
							}
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						}
					}
					if (completedCallback != null) {
						completedCallback.run();
					}
				}

			};
			rows.filter(filter);
			listAllForSync(getPath() + (subFolders ? "/%" : ""), "%", rows);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if (timerMs > 0) {
				try {
					Thread.sleep(timerMs);
					startFullAsyncThread(subFolders, timerMs, replaceTo, completedCallback);
				} catch (InterruptedException ee) {
					log.error(ee.getMessage(), ee);
				}
			}
		}
	}

	public void startFullAsync(long timerMs) throws IOException {
		startFullAsync(timerMs, getPath() + "/", null);
	}

	public void startFullAsync(long timerMs, Runnable completedCallback) throws IOException {
		startFullAsync(timerMs, getPath() + "/", completedCallback);
	}

	public void startFullAsync(long timerMs, String replaceTo) throws IOException {
		startFullAsync(timerMs, replaceTo, null);
	}

	public void startFullAsync(long timerMs, String replaceTo, Runnable completedCallback) throws IOException {

		stopSync = false;

		RemoteFile the = this;
		Clock clock = new Clock();
		Timestamp toTime = new Timestamp(clock.getTime() - timerMs);
		Executors.newFixedThreadPool(1).execute(new Runnable() {

			@Override
			public void run() {
				startFullAsyncThread(false, timerMs, replaceTo, completedCallback);
			}

		});

		Executors.newFixedThreadPool(1).execute(new Runnable() {

			@Override
			public void run() {
				startFullAsyncThreadForDelete(false, timerMs, replaceTo, completedCallback);
			}

		});
	}

	public DriverDataSource getDataSourceReader() {
		return dataSourceReader;
	}

	public DriverDataSource getDataSourceWriter() {
		return dataSourceWriter;
	}

	public ConfigProperties getConfigProperties() {
		return configProperties;
	}

	private byte[] encrypt(byte[] data) throws Exception {
		boolean useEncrypt = configProperties.getBoolean("EncryptEnable", false);
		if (useEncrypt) {
            String aesKey = configProperties.getString("EncryptAesKey");
            return CipherTools.AESEncrypt(aesKey,data);
		} else {
			return data;
		}
	}

	private byte[] decrypt(byte[] encData) throws Exception {
		boolean useEncrypt = configProperties.getBoolean("EncryptEnable", false);
		if (useEncrypt) {
            String aesKey = configProperties.getString("EncryptAesKey");
            return CipherTools.AESDecrypt(aesKey,encData);
		} else {
			return encData;
		}
	}

	public String getSyncedOnHostnames() throws IOException {
		Map<String, Object> record = getCompletedRecord();
		if (record != null) {
			return (String) record.get("synced_on_hostnames");
		}
		return null;
	}

	public String getDeletedOnHostnames() throws IOException {
		Map<String, Object> record = getCompletedRecord();
		if (record != null) {
			return (String) record.get("deleted_on_hostnames");
		}
		return null;
	}

	public String getSourceHostname() throws IOException {
		Map<String, Object> record = getCompletedRecord();
		if (record != null) {
			return (String) record.get("source_hostname");
		}
		return null;
	}

	protected String getLastSourceHostname() throws IOException {
		Map<String, Object> record = getLastUpdateRecord();
		if (record != null) {
			return (String) record.get("source_hostname");
		}
		return null;
	}

	protected Timestamp getLastOperateTime() throws IOException {
		Map<String, Object> record = getLastUpdateRecord();
		if (record != null) {
			Timestamp updatedAt = (Timestamp) record.get("updated_at");
			Timestamp deletedAt = (Timestamp) record.get("deleted_at");

			return deletedAt == null ? updatedAt : deletedAt;
		}
		return null;
	}

	protected Timestamp getLastModifyTime() throws IOException {
		Map<String, Object> record = getLastUpdateRecord();
		if (record != null) {
			return (Timestamp) record.get("file_modify_time");
		}
		return null;
	}

	protected boolean isLastOperateDelete() throws IOException {
		Map<String, Object> record = getLastUpdateRecord();
		if (record != null) {
			Timestamp deletedAt = (Timestamp) record.get("deleted_at");

			return deletedAt == null ? false : true;
		}
		return false;
	}

	protected boolean isLastOperateRealDelete() throws IOException {
		Map<String, Object> record = getLastUpdateRecord();
		if (record != null) {
			Object deletedValue = record.get("file_deleted");
			if (deletedValue != null) {
				if (deletedValue instanceof Boolean) {
					return (Boolean) deletedValue;
				} else {
					return Integer.parseInt(deletedValue + "") == 1;
				}
			}
		}
		return false;
	}
	
	protected void listAllForScanDelete(Integer filePartDataTable, CacheArray rows) throws IOException {
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
			dataRecord.put("file_parent_path", getPath());
			dataRecord.put("like_file_parent_path", parsePath(getPath() + "/%"));
			dataRecord.put("deleted_on_hostnames", "%;" + CommonTools.getHostname() + ";%");
			dataRecord.put("begin_time", new Timestamp(deleteCutoffTimeMs));
			dataRecord.put("end_time", currentTime);
			dataRecord.put("created_at", new Timestamp(currentTime.getTime() - BEFORE_THE_QUEUE_TIME));
			dataRecord.put("file_part_data_table", filePartDataTable);
			executor.find(formatSql(SQL_SELECT_FOR_LIST_ALL_SCAN_DELETE), dataRecord, rows);
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

	protected boolean isSyncedOnHostname() throws IOException {
		Map<String, Object> record = getCompletedRecord();
		if (record != null) {
			String syncedOnHostnames = (String) record.get("synced_on_hostnames");
			return syncedOnHostnames.indexOf(";" + CommonTools.getHostname() + ";") > -1;
		}
		return false;
	}

	protected boolean isSyncedOnHostname(Object id) throws IOException {
		Map<String, Object> record = getRecordById(id);
		if (record != null) {
			String syncedOnHostnames = (String) record.get("synced_on_hostnames");
			return syncedOnHostnames.indexOf(";" + CommonTools.getHostname() + ";") > -1;
		}
		return false;
	}

	protected boolean isDeletedOnHostname(Object id) throws IOException {
		Map<String, Object> record = getRecordById(id);
		if (record != null) {
			String deletedOnHostnames = (String) record.get("deleted_on_hostnames");
			return deletedOnHostnames.indexOf(";" + CommonTools.getHostname() + ";") > -1;
		}
		return false;
	}

	public RemoteFile getParentFile() {
		return new RemoteFile(getParent());
	}

	private void executeSyncRunnable(final CacheArrayFilter filter, final Map<String, Object> item, final DiskFile df,
			final RemoteFile rf) {
	    try{
    		if (!df.isLogicModify()) {
    			df.logicModify();
    			if (mergePool == null) {
    				LoggerFactory.getLogger(RemoteFile.class).mark("MAX_POOL_SIZE={}", MAX_POOL_SIZE);
    				mergePool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
    			}
    			mergePool.execute(getSyncRunnable(filter, item, rf, df));
    		}
	    }catch(Exception e){
	        LoggerFactory.getLogger(RemoteFile.class).error(e.getMessage(),e);
	    }
	}

	private Runnable getSyncRunnable(final CacheArrayFilter filter, final Map<String, Object> item, final RemoteFile rf,
			final DiskFile df) {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					String fileName = (String) item.get("file_name");
					Thread.currentThread()
							.setName(String.format("RemoteFile-getSyncRunnable-%s-%s", fileName, item.get("id")));
					boolean isDebug = isDebug(rf);
					boolean isDeleted = false;
					Object deletedValue = item.get("file_deleted");

					if (deletedValue instanceof Boolean) {
						isDeleted = (Boolean) deletedValue;
					} else {
						isDeleted = Integer.parseInt(deletedValue + "") == 1;
					}

					String clientHostname = CommonTools.getHostname();
					String lastSourceHostname = rf.getLastSourceHostname();

					boolean isClientHostname = lastSourceHostname != null && !lastSourceHostname.equals(clientHostname);
					boolean rootExists = syncRoot != null && syncRoot.exists();

            		if(!rootExists){
            		    Logger.systemError(RemoteFile.class,"The root path '{}' does not exist.",syncRoot.getPath());
            		}
            		
					if (isDeleted && rootExists) {
						if (df.exists()) {
							if (isDebug) {
								LoggerFactory.getLogger(RemoteFile.class).mark("DeleteDiskFile - {}", df.getPath());
							}
							df.delete();
							rf.deletedToDiskAndSub(item.get("id"));
						} else {
							rf.deletedToDiskAndSub(item.get("id"));
						}
						df.removeLogicModify();
					}

					String sourceHostname = rf.getSourceHostname();
					isClientHostname = sourceHostname != null && !sourceHostname.equals(clientHostname);
					boolean checkHaveParent = configProperties.getBoolean("CheckHaveParent", false);
					boolean haveParent = true;
					if (checkHaveParent) {
						haveParent = df.getOrigin().getParentFile().exists();
					}
					String fileType = (String) item.get("file_type");

					if (!isDeleted && isClientHostname && haveParent && rootExists) {
						Timestamp rfModifyTime = rf.getModifyTime();
						df.setModifyTimeMsFromClock(rfModifyTime.getTime());

						if (fileType.equals("L")) {
							if (df.exists() && !df.isLink()) {
								df.delete();
							}
							if (df.exists()) {
								Timestamp dfModifyTime = df.getModifyTimeForClock();
								if (rfModifyTime.getTime() > dfModifyTime.getTime()) {
									if (isDebug) {
										LoggerFactory.getLogger(RemoteFile.class).mark("ReCreateDiskLink - {}",
												df.getPath());
									}
									df.delete();
									if (df.createLink(rf.readLink())) {
										if (df.getOrigin().getName().startsWith(".")) {
											Files.setAttribute(Paths.get(df.getPath()), "dos:hidden", true);
										}
										rf.syncedToDisk();
									}
								} else {
									rf.syncedToDisk();
								}
							} else {
								if (isDebug) {
									LoggerFactory.getLogger(RemoteFile.class).mark("CreateDiskLink - {}", df.getPath());
								}
								if (df.createLink(rf.readLink())) {
									if (df.getOrigin().getName().startsWith(".")) {
										Files.setAttribute(Paths.get(df.getPath()), "dos:hidden", true);
									}
									rf.syncedToDisk();
								}
							}
							df.removeLogicModify();
						}
						if (fileType.equals("F")) {
							if (df.exists()) {
								Timestamp dfModifyTime = df.getModifyTimeForClock();
								boolean changed = rfModifyTime.getTime() > dfModifyTime.getTime();
								if (changed) {
									DiskFile writingDf = new DiskFile(df.getPath() + TMP_WRITING_FILE);
									DiskFile.copyAttrs(writingDf, df.isCopyStructureOnly(), df.syncRoot);
									writingDf.setModifyTimeMsFromClock(rfModifyTime.getTime());
									if (!writingDf.isLogicModify()) {
										writingDf.logicModify();
										if (RemoteFile.addQueuePathMapping(rf.getPath())) {
											if (isDebug(rf)) {
												LoggerFactory.getLogger(RemoteFile.class).mark("DiskQueuing - {}",
														df.getPath());
											}
											rf.writeToDisk(writingDf);
										}
									}
								} else {
									rf.syncedToDisk();
									df.removeLogicModify();
								}
							} else {
								if (rf.exists()) {
									DiskFile writingDf = new DiskFile(df.getPath() + TMP_WRITING_FILE);
									DiskFile.copyAttrs(writingDf, df.isCopyStructureOnly(), df.syncRoot);
									writingDf.setModifyTimeMsFromClock(rfModifyTime.getTime());
									if (!writingDf.isLogicModify()) {
										writingDf.logicModify();
										if (RemoteFile.addQueuePathMapping(rf.getPath())) {
											if (isDebug(rf)) {
												LoggerFactory.getLogger(RemoteFile.class).mark("DiskQueuing - {}",
														df.getPath());
											}
											rf.writeToDisk(writingDf);
										}
									}
								}
							}
						}
						if (fileType.equals("D")) {
							if (rf.exists()) {
								if (isDebug) {
									LoggerFactory.getLogger(RemoteFile.class).mark("CreateDiskDir - {}", df.getPath());
								}
								if (df.exists()) {
									if (df.isDir()) {
										rf.syncedToDisk();
									} else {
										df.delete();
										if (df.mkdirs()) {
											if (df.getOrigin().getName().startsWith(".")) {
												Files.setAttribute(Paths.get(df.getPath()), "dos:hidden", true);
											}
											rf.syncedToDisk();
										}
									}
								} else {
									if (df.mkdirs()) {
										if (df.getOrigin().getName().startsWith(".")) {
											Files.setAttribute(Paths.get(df.getPath()), "dos:hidden", true);
										}
										rf.syncedToDisk();
									}
								}
							}
							df.removeLogicModify();
						}
					}
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		};
		return runnable;
	}

	public void startArchiveForFileDeleted(long timerMs, String archiveTime) throws IOException {
		long archiveMs = ConfigProperties.parseTimeToMs(archiveTime, 0L);
		startArchiveForFileDeleted(timerMs, archiveMs, null);
	}

	public void startArchiveForFileDeleted(long timerMs, long archiveMs) throws IOException {
		startArchiveForFileDeleted(timerMs, archiveMs, null);
	}

	public void startArchiveForFileDeleted(long timerMs, String archiveTime, Runnable completedCallback)
			throws IOException {
		long archiveMs = ConfigProperties.parseTimeToMs(archiveTime, 0L);
		startArchiveForFileDeleted(timerMs, archiveMs, completedCallback);
	}

	public void startArchiveForFileDeleted(long timerMs, long archiveMs, Runnable completedCallback)
			throws IOException {
		final RemoteFile the = this;
		if (archiveMs <= 0) {
			throw new IOException(String.format(
					"This parameter 'archiveMs' cannot be '%s', please use a number greater than 0.", archiveMs));
		}

		stopSync = false;
        
		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
		    Thread.currentThread().setName(String.format("RemoteFile-archiveForFileDeleted-%s", getPath()));
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Timestamp beforeTime = new Timestamp(new Clock().getTime() - archiveMs);
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("file_parent_path", getPath());
			dataRecord.put("like_file_parent_path", parsePath(getPath() + "/%"));
			dataRecord.put("before_deleted_at", beforeTime);
			CacheArray rows = new CacheArray();
			CacheArrayFilter filter = new CacheArrayFilter(CACHE_ARRAY_FILTER_TIMER) {

				@Override
				public void completed(Integer size) {
					if (isStopSync()) {
						LoggerFactory.getLogger(RemoteFile.class).mark("Stoped archive for file deleted.");
					}
					if (timerMs <= 0) {
						LoggerFactory.getLogger(RemoteFile.class).mark("Completed archive for file deleted.");
					}
					
					if (completedCallback != null) {
						completedCallback.run();
					}
					
					if (!isStopSync() && timerMs > 0) {
					    while(true){
					        
					        if(isStopSync()) break;
					        
    						try {
    							Thread.sleep(timerMs);
    							if (!isStopSync()) {
    							    boolean allowed = checkUsage(the);
    							    if(allowed){
    								    startArchiveForFileDeleted(timerMs, archiveMs, completedCallback);
    								    break;
    							    }
    							}
    						} catch (Exception e) {
    							log.error(e.getMessage(), e);
    						}
					    }
					}

				}

				@Override
				public void execute(Integer index, Object o) {
				    Thread.currentThread().setName(String.format("RemoteFile-execute-archiveForFileDeleted-%s", index));
					if(isDebug(the)){
					    debugLog("CheckUsage",the,this);
					}				    
					if (memoryUsage() < RemoteFile.MAX_MEMORY_USAGE) {
						CacheDriverExecutor _executor = null;
						try {
							Map<String, Object> item = (Map<String, Object>) o;
							boolean notDir = !item.get("file_type").equals("D");
							if(notDir){
    							calculateDataMappingDataSourceReader(the, item);
    							Object fileId = item.get("file_id");
    							Map<String, Object> dataRecord = new HashMap<String, Object>();
    							dataRecord.put("file_id", fileId);
    							dataRecord.put("deleted_at", new Clock().getSqlTimestamp());
    							Thread.currentThread().setName(String.format("RemoteFile-archiveForFileDeleted-%s", fileId));
    				            Connection _conn = dataMappingDataSourceReader.getConnection();
    							_conn.setAutoCommit(true);
    							_executor = new CacheDriverExecutor(_conn);
    			                _executor.execute(formatSqlForFilePartData(SQL_UPDATE_BY_FILE_ID_FOR_CLEAR_FILE_DATA), dataRecord);
							}
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						} finally {
							try {
								if (_executor != null)
									_executor.close();
							} catch (Exception e) {
								log.error(e.getMessage(), e);
							}
						}
					}else{
					    log.warn("Over max memory usage 'MAX_MEMORY_USAGE={}'.",MAX_MEMORY_USAGE);
					}
				}
			};
			rows.filter(filter);
			final CacheDriverExecutor finalExecutor = executor;
			Executors.newFixedThreadPool(1).execute(new Runnable(){
			    @Override
			    public void run(){
			        try{
			             finalExecutor.find(formatSql(SQL_SELECT_FOR_ARCHIVE_FILE_DELETED), dataRecord, rows);
			        }catch(Exception e){
			            log.error(e.getMessage(),e);
			        }
			    }
			});
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

	public void startArchiveForDeleted(long timerMs, String archiveTime) throws IOException {
		long archiveMs = ConfigProperties.parseTimeToMs(archiveTime, 0L);
		startArchiveForDeleted(timerMs, archiveMs, null);
	}

	public void startArchiveForDeleted(long timerMs, long archiveMs) throws IOException {
		startArchiveForDeleted(timerMs, archiveMs, null);
	}

	public void startArchiveForDeleted(long timerMs, String archiveTime, Runnable completedCallback)
			throws IOException {
		long archiveMs = ConfigProperties.parseTimeToMs(archiveTime, 0L);
		startArchiveForDeleted(timerMs, archiveMs, completedCallback);
	}

	public void startArchiveForDeleted(long timerMs, long archiveMs, Runnable completedCallback) throws IOException {
	    final RemoteFile the = this;
		if (archiveMs <= 0) {
			throw new IOException(String.format(
					"This parameter 'archiveMs' cannot be '%s', please use a number greater than 0.", archiveMs));
		}

		stopSync = false;

		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
		    Thread.currentThread().setName(String.format("RemoteFile-archiveForDeleted-%s", getPath()));
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Timestamp beforeTime = new Timestamp(new Clock().getTime() - archiveMs);
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("file_parent_path", getPath());
			dataRecord.put("like_file_parent_path", parsePath(getPath() + "/%"));
			dataRecord.put("before_deleted_at", beforeTime);

			CacheArray rows = new CacheArray();
			CacheArrayFilter filter = new CacheArrayFilter(CACHE_ARRAY_FILTER_TIMER) {

				@Override
				public void completed(Integer size) {
					if (isStopSync()) {
						LoggerFactory.getLogger(RemoteFile.class).mark("Stoped archive for deleted.");
					}
					if (timerMs <= 0) {
						LoggerFactory.getLogger(RemoteFile.class).mark("Completed archive for deleted.");
					}
					
					if (completedCallback != null) {
						completedCallback.run();
					}
					
					if (!isStopSync() && timerMs > 0) {
					    while(true){
					        
					        if(isStopSync()) break;
					        
    						try {
    							Thread.sleep(timerMs);
    							if (!isStopSync()) {
    							    boolean allowed = checkUsage(the);
    							    if(allowed){
    								    startArchiveForDeleted(timerMs, archiveMs, completedCallback);
    								    break;
    							    }
    							}
    						} catch (Exception e) {
    							log.error(e.getMessage(), e);
    						}
					    }
					}
				}

				@Override
				public void execute(Integer index, Object o) {
				    Thread.currentThread().setName(String.format("RemoteFile-execute-archiveForDeleted-%s", index));
					CacheDriverExecutor dmExecutor = null;
					CacheDriverExecutor dmDelExecutor = null;
					CacheDriverExecutor executor = null;
					if(isDebug(the)){
					    debugLog("CheckUsage",the,this);
					}					
					if (memoryUsage() < RemoteFile.MAX_MEMORY_USAGE) {
						try {
							Clock clock = new Clock();
							Timestamp currentTime = clock.getSqlTimestamp();
							Map<String, Object> item = (Map<String, Object>) o;
							Object fileId = item.get("file_id");
							Thread.currentThread().setName(String.format("RemoteFile-archiveForDeleted-%s", fileId));
							calculateDataMappingDataSourceReader(the, item);
							Map<String, Object> dataRecord = null;
							boolean haveSubFiles = false;
							/**Need use dataMappingDataSourceReader**/
							if (item.get("file_type").equals("D")) {
								int count = countAllSubFiles(item.get("file_parent_path") + "",
										item.get("file_name") + "");
								haveSubFiles = count > 0;
							}
							if (!haveSubFiles) {
								if (!item.get("file_type").equals("D")) {
									Connection dmConn = dataMappingDataSourceReader.getConnection();
									dmConn.setAutoCommit(true);
									dmExecutor = new CacheDriverExecutor(dmConn);
									dataRecord = new HashMap<String, Object>();
									dataRecord.put("file_id", item.get("file_id"));
									dataRecord.put("deleted_at", currentTime);
									dmExecutor.execute(
											formatSqlForFilePartData(SQL_UPDATE_BY_FILE_ID_FOR_CLEAR_FILE_DATA),
											dataRecord);
								}
								Connection conn = dataSourceWriter.getConnection();
								conn.setAutoCommit(true);
								executor = new CacheDriverExecutor(conn);
								dataRecord = new HashMap<String, Object>();
								dataRecord.put("file_id", item.get("file_id"));
								executor.execute(formatSql(SQL_DELETE_BY_FILE_ID_FROM_FILE), dataRecord);
							}

							Connection dmDelConn = dataMappingDataSourceReader.getConnection();
							dmDelConn.setAutoCommit(true);
							dmDelExecutor = new CacheDriverExecutor(dmDelConn);
							dataRecord = new HashMap<String, Object>();
							dataRecord.put("deleted_at", new Timestamp(currentTime.getTime() - archiveMs));
							dmDelExecutor.execute(formatSqlForFilePartData(SQL_DELETE_BY_RF_ID_FROM_FILE_DATA),
									dataRecord);
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						} finally {
							try {
								if (dmExecutor != null)
									dmExecutor.close();
							} catch (Exception e) {
								log.error(e.getMessage(), e);
							}
							try {
								if (dmDelExecutor != null)
									dmDelExecutor.close();
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

					}else{
					    log.warn("Over max memory usage 'MAX_MEMORY_USAGE={}'.",MAX_MEMORY_USAGE);
					}
				}
			};
			rows.filter(filter);
			final CacheDriverExecutor finalExecutor = executor;
			Executors.newFixedThreadPool(1).execute(new Runnable(){
			    @Override
			    public void run(){
			        try{
			            finalExecutor.find(formatSql(SQL_SELECT_FOR_ARCHIVE_DELETED), dataRecord, rows);
			        }catch(Exception e){
			            log.error(e.getMessage(),e);
			        }
			    }
			});			
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
	
	public void startArchiveForTimeout(long timerMs) throws IOException {
	    startArchiveForTimeout(timerMs,null);
	}
	
	public void startArchiveForTimeout(long timerMs,Runnable completedCallback) throws IOException {
	    final RemoteFile the = this;
	    
		stopSync = false;

		CacheDriverExecutor executor = null;
		Connection conn = null;
		try {
		    Thread.currentThread().setName(String.format("RemoteFile-archiveForTimeout-%s", getPath()));
			conn = dataSourceReader.getConnection();
			conn.setAutoCommit(true);
			executor = new CacheDriverExecutor(conn);
			Timestamp beforeTime = new Timestamp(new Clock().getTime() - (LOGIC_TIMEOUT_MS*BATCH_SIZE));
			Map<String, Object> dataRecord = new HashMap<String, Object>();
			dataRecord.put("file_parent_path", getPath());
			dataRecord.put("like_file_parent_path", parsePath(getPath() + "/%"));
			dataRecord.put("before_updated_at", beforeTime);

			CacheArray rows = new CacheArray();
			CacheArrayFilter filter = new CacheArrayFilter(CACHE_ARRAY_FILTER_TIMER) {

				@Override
				public void completed(Integer size) {
					if (isStopSync()) {
						LoggerFactory.getLogger(RemoteFile.class).mark("Stoped archive for timeout deleted.");
					}
					if (timerMs <= 0) {
						LoggerFactory.getLogger(RemoteFile.class).mark("Completed archive for timeout deleted.");
					}
					
					if (completedCallback != null) {
						completedCallback.run();
					}
					
					if (!isStopSync() && timerMs > 0) {
					    while(true){
					        
					        if(isStopSync()) break;
					        
    						try {
    							Thread.sleep(timerMs);
    							if (!isStopSync()) {
    							    boolean allowed = checkUsage(the);
    							    if(allowed){
    								    startArchiveForTimeout(timerMs, completedCallback);
    								    break;
    							    }
    							}
    						} catch (Exception e) {
    							log.error(e.getMessage(), e);
    						}
					    }
					}

				}

				@Override
				public void execute(Integer index, Object o) {
				    Thread.currentThread().setName(String.format("RemoteFile-execute-archiveForTimeout-%s", index));
					if(isDebug(the)){
					    debugLog("CheckUsage",the,this);
					}				    
					CacheDriverExecutor executor = null;
					if (memoryUsage() < RemoteFile.MAX_MEMORY_USAGE) {
						try {
							Clock clock = new Clock();
							Timestamp currentTime = clock.getSqlTimestamp();
							Map<String, Object> item = (Map<String, Object>) o;
							Object fileId = item.get("file_id");
							Thread.currentThread().setName(String.format("RemoteFile-archiveForTimeout-%s", fileId));
							Connection conn = dataSourceWriter.getConnection();
							conn.setAutoCommit(true);
							executor = new CacheDriverExecutor(conn);
							Map<String,Object> dataRecord = new HashMap<String, Object>();
							dataRecord.put("file_id",fileId);
							dataRecord.put("deleted_at",currentTime);
							dataRecord.put("deleted_user_id",getDefaultModifyUserId());
							executor.execute(formatSql(SQL_UPDATE_FOR_TIMEOUT_DELETE), dataRecord);
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						} finally {
							try {
								if (executor != null)
									executor.close();
							} catch (Exception e) {
								log.error(e.getMessage(), e);
							}
						}

					}else{
					     log.warn("Over max memory usage 'MAX_MEMORY_USAGE={}'.",MAX_MEMORY_USAGE);
					}
				}
			};
			rows.filter(filter);
			final CacheDriverExecutor finalExecutor = executor;
			Executors.newFixedThreadPool(1).execute(new Runnable(){
			    @Override
			    public void run(){
			        try{
			            finalExecutor.find(formatSql(SQL_SELECT_FOR_ALL_TIMEOUT), dataRecord, rows);
			        }catch(Exception e){
			            log.error(e.getMessage(),e);
			        }
			    }
			});				
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

	public static Map<String, DriverDataSource> getDriverDataSourceMap() {
		return Collections.synchronizedMap(DATASOURCE_REMOTE_FILE);
	}

	private static synchronized String generateId(boolean identityCardEnable, String identityCardName,
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

	private static synchronized String generateDataId(boolean identityCardEnable, String identityCardName,
			int identityCardSuffix, RemoteFile theRf) throws SQLException {
		if (identityCardEnable) {
			if (identityCard == null) {
				identityCard = new IdentityCard(identityCardName + "Data");
			}
			return identityCard.generateId(16, String.format("%04d", theRf.dataMappingTable), "", identityCardSuffix);
		} else {
			return CommonTools.generateId(16);
		}
	}

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

	protected static synchronized void removeQueuePathMapping(String path) {
		QUEUE_PATH_MAPPING.remove(path);
	}

	public static synchronized Map<String, Long> getQueuePathMapping() {
		return Collections.synchronizedMap(QUEUE_PATH_MAPPING);
	}

	protected static synchronized void loadDataMapping(RemoteFile rf) throws Exception {
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
					dmCp.putAllAndReturnSlef(dmMap);
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
					shortCp.putAndReturnSlef("DataSourceReader", dmDsReaderKey);
					shortCp.putAndReturnSlef("DataSourceWriter", dmDsWriterKey);
					shortCp.putAndReturnSlef("Priority", priority);
					shortCp.putAndReturnSlef("UsingCount", 0);
					shortCp.putAndReturnSlef("Name", dm);
					int randomIndex = ThreadLocalRandom.current().nextInt(0, tableRange.size());
					shortCp.putAndReturnSlef("Table", tableRange.get(randomIndex));
					DATA_MAPPING.put(shortKey, shortCp);
					dmList.add(dm);
					DATA_MAPPING_LIST.put(rf.dsKey, dmList);
				}
			}
		}

	}

	protected static synchronized void calculateDataMappingDataSourceReader(RemoteFile rf,
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

	protected static synchronized void calculateDataMappingDataSourceWriter(RemoteFile rf) {
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
				cp.putAndReturnSlef("UsingCount", 0);
				int randomIndex = ThreadLocalRandom.current().nextInt(0, tableRange.size());
				cp.putAndReturnSlef("Table", tableRange.get(randomIndex));
				DATA_MAPPING.put(key, cp);
			} else {
				cp.putAndReturnSlef("UsingCount", usingCount);
				int randomIndex = ThreadLocalRandom.current().nextInt(0, tableRange.size());
				cp.putAndReturnSlef("Table", tableRange.get(randomIndex));
				DATA_MAPPING.put(key, cp);
			}
		} else {
			DATA_MAPPING_INDEX.put(rf.dsKey, DATA_MAPPING_INDEX.get(rf.dsKey) + 1);
			calculateDataMappingDataSourceWriter(rf);
		}
	}

	protected static synchronized ConfigProperties getDataMapping(RemoteFile rf, int index) {
		List<String> dmList = DATA_MAPPING_LIST.get(rf.dsKey);
		String dm = dmList.get(index);
		String key = String.format("%s.%s", rf.dsKey, dm);
		return DATA_MAPPING.get(key);
	}

	protected String getDataMappingTableName() {
		return String.format("%s_data_%s", this.tableName, this.dataMappingTable);
	}
	
	private static void debugLog(String point,RemoteFile rf,CacheArrayFilter filter){
		debugLog(RemoteFile.class,point,rf,filter);
	}	

}
