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
import java.nio.file.Path;



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
 * RemoteFile - public facade of the remote (database-backed) file engine.
 * Public API surface: construction, existence/listing, shard read/write,
 * copy/move/link, delete/force-delete, completion and the async archive/sync
 * schedulers. The implementation lives in the abstract parent RemoteFileBase.
 * */
public class RemoteFile extends RemoteFileBase {


	/**
	* Constructs a new RemoteFile
	* @param path - the path
	* */
	public RemoteFile(String path) {
		super(path);
		log = LoggerFactory.getLogger(getClass());
		fileId = CommonTools.generateId(16);
		initGlobal();
	}
	
	/**
	* Constructs a new RemoteFile
	* @param origin - the origin
	* */

	public RemoteFile(File origin) {
		super(origin);
		log = LoggerFactory.getLogger(getClass());
		fileId = CommonTools.generateId(16);
		initGlobal();
	}	

	/**
	* Performs the initPool operation
	* @param poolSize - the poolSize
	* */


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


	/**
	* Performs the isTimeout operation
	* @param timeoutMs - the timeoutMs
	* @return the isTimeout result
	* */
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


	/**
	* Performs the forceDeleteDir operation
	* @param realDeleted - the realDeleted
	* @return the forceDeleteDir result
	* */
	public boolean forceDeleteDir(boolean realDeleted) throws IOException {
		return forceDelete(realDeleted, "D");
	}

	/**
	* Performs the forceDeleteLink operation
	* @param realDeleted - the realDeleted
	* @return the forceDeleteLink result
	* */


	/**
	* Performs the writeBlock operation
	* @param data - the data
	* @param append - the append
	* @return the writeBlock result
	* */
	public synchronized boolean writeBlock(byte[] data, boolean append) throws IOException {
		return writeTo(data, append);
	}

	/**
	* Performs the writeToBatch operation
	* @param currentFilePartStartIndex - the currentFilePartStartIndex
	* @param partDataList - the partDataList
	* @return the writeToBatch result
	* */



	/**
	* Performs the isDirectory operation
	* @return the isDirectory result
	* */
	public boolean isDirectory() throws IOException {
		Map<String, Object> lastRecord = getFirstFilePart();

		if (lastRecord == null) {
			return false;
		}
		return lastRecord.get("file_type").equals("D");
	}


	/**
	* Performs the isLink operation
	* @return the isLink result
	* */



	/**
	* Performs the readAllString operation
	* @return the readAllString result
	* */
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


	/**
	* Performs the size operation
	* @return the size result
	* */


	/**
	* Performs the listAll operation
	* @param likeFileName - the likeFileName
	* @param rows - the rows
	* */
	public void listAll(String likeFileName, CacheArray rows) throws IOException {
		listAll(getPath(), likeFileName, rows);
	}

	/**
	* Performs the listAll operation
	* @param rows - the rows
	* */

	public void listAll(CacheArray rows) throws IOException {
		listAll(getPath(), "%", rows);
	}

	/**
	* Performs the listAll operation
	* @param likeFolderName - the likeFolderName
	* @param likeFileName - the likeFileName
	* @param rows - the rows
	* */

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

	/**
	* Performs the listAllForDelete operation
	* @param likeFolderName - the likeFolderName
	* @param likeFileName - the likeFileName
	* @param rows - the rows
	* */


	/**
	* Performs the isTimeout operation
	* @return the isTimeout result
	* */
	public boolean isTimeout() throws IOException {
		return isTimeout(LOGIC_TIMEOUT_MS);
	}


	/**
	* Performs the copyTo operation
	* @param toPath - the toPath
	* */


	/**
	* Performs the startFullAsync operation
	* @param timerMs - the timerMs
	* */
	public void startFullAsync(long timerMs) throws IOException {
		startFullAsync(timerMs, getPath() + "/", null);
	}

	/**
	* Performs the startFullAsync operation
	* @param timerMs - the timerMs
	* @param completedCallback - the completedCallback
	* */

	public void startFullAsync(long timerMs, Runnable completedCallback) throws IOException {
		startFullAsync(timerMs, getPath() + "/", completedCallback);
	}

	/**
	* Performs the startFullAsync operation
	* @param timerMs - the timerMs
	* @param replaceTo - the replaceTo
	* */

	public void startFullAsync(long timerMs, String replaceTo) throws IOException {
		startFullAsync(timerMs, replaceTo, null);
	}

	/**
	* Performs the startFullAsync operation
	* @param timerMs - the timerMs
	* @param replaceTo - the replaceTo
	* @param completedCallback - the completedCallback
	* */

	public void startFullAsync(long timerMs, String replaceTo, Runnable completedCallback) throws IOException {

		stopSync = false;

		RemoteFileBase the = this;
		Clock clock = new Clock();
		Timestamp toTime = new Timestamp(clock.getTime() - timerMs);
		Executors.newFixedThreadPool(1).execute(new Runnable() {


			/**
			* Performs the run operation
			* */
			public void run() {
				startFullAsyncThread(false, timerMs, replaceTo, completedCallback);
			}

		});

		Executors.newFixedThreadPool(1).execute(new Runnable() {


			/**
			* Performs the run operation
			* */
			public void run() {
				startFullAsyncThreadForDelete(false, timerMs, replaceTo, completedCallback);
			}

		});
	}

	/**
	* Performs the getDataSourceReader operation
	* @return the getDataSourceReader result
	* */


	/**
	* Performs the getConfigProperties operation
	* @return the getConfigProperties result
	* */
	public ConfigProperties getConfigProperties() {
		return configProperties;
	}

	/**
	* Performs the encrypt operation
	* @param data - the data
	* @return the encrypt result
	* */


	/**
	* Performs the getSyncedOnHostnames operation
	* @return the getSyncedOnHostnames result
	* */
	public String getSyncedOnHostnames() throws IOException {
		Map<String, Object> record = getCompletedRecord();
		if (record != null) {
			return (String) record.get("synced_on_hostnames");
		}
		return null;
	}

	/**
	* Performs the getDeletedOnHostnames operation
	* @return the getDeletedOnHostnames result
	* */

	public String getDeletedOnHostnames() throws IOException {
		Map<String, Object> record = getCompletedRecord();
		if (record != null) {
			return (String) record.get("deleted_on_hostnames");
		}
		return null;
	}

	/**
	* Performs the getSourceHostname operation
	* @return the getSourceHostname result
	* */


	/**
	* Performs the startArchiveForFileDeleted operation
	* @param timerMs - the timerMs
	* @param archiveTime - the archiveTime
	* */
	public void startArchiveForFileDeleted(long timerMs, String archiveTime) throws IOException {
		long archiveMs = ConfigProperties.parseTimeToMs(archiveTime, 0L);
		startArchiveForFileDeleted(timerMs, archiveMs, null);
	}

	/**
	* Performs the startArchiveForFileDeleted operation
	* @param timerMs - the timerMs
	* @param archiveMs - the archiveMs
	* */

	public void startArchiveForFileDeleted(long timerMs, long archiveMs) throws IOException {
		startArchiveForFileDeleted(timerMs, archiveMs, null);
	}

	/**
	* Performs the startArchiveForFileDeleted operation
	* @param timerMs - the timerMs
	* @param archiveTime - the archiveTime
	* @param completedCallback - the completedCallback
	* */

	public void startArchiveForFileDeleted(long timerMs, String archiveTime, Runnable completedCallback)
			throws IOException {
		long archiveMs = ConfigProperties.parseTimeToMs(archiveTime, 0L);
		startArchiveForFileDeleted(timerMs, archiveMs, completedCallback);
	}

	/**
	* Performs the startArchiveForFileDeleted operation
	* @param timerMs - the timerMs
	* @param archiveMs - the archiveMs
	* @param completedCallback - the completedCallback
	* */

	public void startArchiveForFileDeleted(long timerMs, long archiveMs, Runnable completedCallback)
			throws IOException {
		final RemoteFileBase the = this;
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


				/**
				* Performs the completed operation
				* @param size - the size
				* */
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


				/**
				* Performs the execute operation
				* @param index - the index
				* @param o - the o
				* */
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

			    /**
			    * Performs the run operation
			    * */
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

	/**
	* Performs the startArchiveForDeleted operation
	* @param timerMs - the timerMs
	* @param archiveTime - the archiveTime
	* */

	public void startArchiveForDeleted(long timerMs, String archiveTime) throws IOException {
		long archiveMs = ConfigProperties.parseTimeToMs(archiveTime, 0L);
		startArchiveForDeleted(timerMs, archiveMs, null);
	}

	/**
	* Performs the startArchiveForDeleted operation
	* @param timerMs - the timerMs
	* @param archiveMs - the archiveMs
	* */

	public void startArchiveForDeleted(long timerMs, long archiveMs) throws IOException {
		startArchiveForDeleted(timerMs, archiveMs, null);
	}

	/**
	* Performs the startArchiveForDeleted operation
	* @param timerMs - the timerMs
	* @param archiveTime - the archiveTime
	* @param completedCallback - the completedCallback
	* */

	public void startArchiveForDeleted(long timerMs, String archiveTime, Runnable completedCallback)
			throws IOException {
		long archiveMs = ConfigProperties.parseTimeToMs(archiveTime, 0L);
		startArchiveForDeleted(timerMs, archiveMs, completedCallback);
	}

	/**
	* Performs the startArchiveForDeleted operation
	* @param timerMs - the timerMs
	* @param archiveMs - the archiveMs
	* @param completedCallback - the completedCallback
	* */

	public void startArchiveForDeleted(long timerMs, long archiveMs, Runnable completedCallback) throws IOException {
	    final RemoteFileBase the = this;
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


				/**
				* Performs the completed operation
				* @param size - the size
				* */
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


				/**
				* Performs the execute operation
				* @param index - the index
				* @param o - the o
				* */
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

			    /**
			    * Performs the run operation
			    * */
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
	
	/**
	* Performs the startArchiveForTimeout operation
	* @param timerMs - the timerMs
	* */

	public void startArchiveForTimeout(long timerMs) throws IOException {
	    startArchiveForTimeout(timerMs,null);
	}
	
	/**
	* Performs the startArchiveForTimeout operation
	* @param timerMs - the timerMs
	* @param completedCallback - the completedCallback
	* */

	public void startArchiveForTimeout(long timerMs,Runnable completedCallback) throws IOException {
	    final RemoteFileBase the = this;
	    
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


				/**
				* Performs the completed operation
				* @param size - the size
				* */
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


				/**
				* Performs the execute operation
				* @param index - the index
				* @param o - the o
				* */
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

			    /**
			    * Performs the run operation
			    * */
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

}
