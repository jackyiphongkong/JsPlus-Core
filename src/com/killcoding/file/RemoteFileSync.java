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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import com.killcoding.cache.CacheArray;
import com.killcoding.cache.CacheArrayFilter;
import com.killcoding.datasource.CacheDriverExecutor;
import com.killcoding.datasource.Clock;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;

/**
* RemoteFileSync - the sync / archive layer of the remote (database-backed) file engine.
* Owns the full-sync schedulers (startFullAsync and its two background threads), the
* sync-state queries (getSyncedOnHostnames / getDeletedOnHostnames / isSyncedOnHostname /
* isDeletedOnHostname / getSourceHostname / getLast* status helpers), the disk-sync
* executor (executeSyncRunnable / getSyncRunnable) and the archive / purge schedulers
* (startArchiveForFileDeleted / startArchiveForDeleted / startArchiveForTimeout with their
* scan-delete list helper). The public facade RemoteFile extends this class and exposes
* only its public API; the implementation lives in the abstract parent RemoteFileBase.
* */
public abstract class RemoteFileSync extends RemoteFileBase {

	/** @param path the remote path this file object operates on */
	protected RemoteFileSync(String path) { super(path); }

	/** @param origin the origin File backing this remote file object */
	protected RemoteFileSync(File origin) { super(origin); }

	/**
	* Performs the startFullAsyncThreadForDelete operation
	* @param subFolders - the subFolders
	* @param timerMs - the timerMs
	* @param replaceTo - the replaceTo
	* @param completedCallback - the completedCallback
	* */
	protected void startFullAsyncThreadForDelete(boolean subFolders, long timerMs, String replaceTo,
			Runnable completedCallback) {
		RemoteFileBase the = this;
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

				/**
				* Performs the execute operation
				* @param index - the index
				* @param o - the o
				* */
				@Override
				@SuppressWarnings("unchecked")
				public void execute(Integer index, Object o) {
					Map<String, Object> item = (Map<String, Object>) o;
					String fileName = (String) item.get("file_name");
					String fullPath = String.format("%s/%s", item.get("file_parent_path"), fileName);
					Thread.currentThread()
							.setName(String.format("RemoteFile-startFullAsyncThreadForDelete-%s", fileName));
					RemoteFileSync rf = new RemoteFile(fullPath);
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


				/**
				* Performs the terminated operation
				* */
				@Override
				public void terminated() {
					log.warn("Terminated: {}-{}", Thread.currentThread().getName(), Thread.currentThread().threadId());
				}


				/**
				* Performs the completed operation
				* @param size - the size
				* */
				@Override
				public void completed(Integer size) {
					Thread.currentThread().setName(String
							.format("RemoteFile-startFullAsyncThreadForDelete-completed-%s", the.origin.getName()));
					if (isStopSync()) {
						LoggerFactory.getLogger(RemoteFileSync.class).mark("Stoped start delete task thread.");
					}
					if (timerMs <= 0) {
						LoggerFactory.getLogger(RemoteFileSync.class).mark("Completed start delete task thread.");
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

	/**
	* Performs the startFullAsyncThread operation
	* @param subFolders - the subFolders
	* @param timerMs - the timerMs
	* @param replaceTo - the replaceTo
	* @param completedCallback - the completedCallback
	* */
	@SuppressWarnings("unchecked")
	protected void startFullAsyncThread(boolean subFolders, long timerMs, String replaceTo, Runnable completedCallback) {
		RemoteFileBase the = this;
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

				/**
				* Performs the execute operation
				* @param index - the index
				* @param o - the o
				* */
				@Override
				public void execute(Integer index, Object o) {
					Map<String, Object> item = (Map<String, Object>) o;
					String fileName = (String) item.get("file_name");
					Thread.currentThread().setName(String.format("RemoteFile-startFullAsyncThread-%s", fileName));
					String fullPath = String.format("%s/%s", item.get("file_parent_path"), fileName);
					RemoteFileSync rf = new RemoteFile(fullPath);
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


				/**
				* Performs the terminated operation
				* */
				@Override
				public void terminated() {
					log.warn("Terminated: {}-{}", Thread.currentThread().getName(), Thread.currentThread().threadId());
				}


				/**
				* Performs the completed operation
				* @param size - the size
				* */
				@Override
				public void completed(Integer size) {
					Thread.currentThread().setName(
							String.format("RemoteFile-startFullAsyncThread-completed-%s", the.origin.getName()));
					if (isStopSync()) {
						LoggerFactory.getLogger(RemoteFileSync.class).mark("Stoped start full async thread.");
					}
					if (timerMs <= 0) {
						LoggerFactory.getLogger(RemoteFileSync.class).mark("Completed start full async thread.");
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

	/**
	* Performs the listAllForSync operation
	* @param likeFolderName - the likeFolderName
	* @param likeFileName - the likeFileName
	* @param rows - the rows
	* */
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

	/**
	* Performs the getSourceHostname operation
	* @return the getSourceHostname result
	* */
	public String getSourceHostname() throws IOException {
		Map<String, Object> record = getCompletedRecord();
		if (record != null) {
			return (String) record.get("source_hostname");
		}
		return null;
	}

	/**
	* Performs the getLastOperateTime operation
	* @return the getLastOperateTime result
	* */
	protected Timestamp getLastOperateTime() throws IOException {
		Map<String, Object> record = getLastUpdateRecord();
		if (record != null) {
			Timestamp updatedAt = (Timestamp) record.get("updated_at");
			Timestamp deletedAt = (Timestamp) record.get("deleted_at");

			return deletedAt == null ? updatedAt : deletedAt;
		}
		return null;
	}

	/**
	* Performs the getLastModifyTime operation
	* @return the getLastModifyTime result
	* */
	protected Timestamp getLastModifyTime() throws IOException {
		Map<String, Object> record = getLastUpdateRecord();
		if (record != null) {
			return (Timestamp) record.get("file_modify_time");
		}
		return null;
	}

	/**
	* Performs the isLastOperateDelete operation
	* @return the isLastOperateDelete result
	* */
	protected boolean isLastOperateDelete() throws IOException {
		Map<String, Object> record = getLastUpdateRecord();
		if (record != null) {
			Timestamp deletedAt = (Timestamp) record.get("deleted_at");

			return deletedAt == null ? false : true;
		}
		return false;
	}

	/**
	* Performs the isLastOperateRealDelete operation
	* @return the isLastOperateRealDelete result
	* */
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

	/**
	* Performs the listAllForScanDelete operation
	* @param filePartDataTable - the filePartDataTable
	* @param rows - the rows
	* */
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

	/**
	* Performs the isSyncedOnHostname operation
	* @return the isSyncedOnHostname result
	* */
	protected boolean isSyncedOnHostname() throws IOException {
		Map<String, Object> record = getCompletedRecord();
		if (record != null) {
			String syncedOnHostnames = (String) record.get("synced_on_hostnames");
			return syncedOnHostnames.indexOf(";" + CommonTools.getHostname() + ";") > -1;
		}
		return false;
	}

	/**
	* Performs the isSyncedOnHostname operation
	* @param id - the id
	* @return the isSyncedOnHostname result
	* */
	protected boolean isSyncedOnHostname(Object id) throws IOException {
		Map<String, Object> record = getRecordById(id);
		if (record != null) {
			String syncedOnHostnames = (String) record.get("synced_on_hostnames");
			return syncedOnHostnames.indexOf(";" + CommonTools.getHostname() + ";") > -1;
		}
		return false;
	}

	/**
	* Performs the isDeletedOnHostname operation
	* @param id - the id
	* @return the isDeletedOnHostname result
	* */
	protected boolean isDeletedOnHostname(Object id) throws IOException {
		Map<String, Object> record = getRecordById(id);
		if (record != null) {
			String deletedOnHostnames = (String) record.get("deleted_on_hostnames");
			return deletedOnHostnames.indexOf(";" + CommonTools.getHostname() + ";") > -1;
		}
		return false;
	}

	/**
	* Performs the executeSyncRunnable operation
	* @param filter - the filter
	* @param item - the item
	* @param df - the df
	* @param rf - the rf
	* */
	protected void executeSyncRunnable(final CacheArrayFilter filter, final Map<String, Object> item, final DiskFile df,
			final RemoteFileSync rf) {
	    try{
    		if (!df.isLogicModify()) {
    			df.logicModify();
    			if (mergePool == null) {
    				LoggerFactory.getLogger(RemoteFileSync.class).mark("MAX_POOL_SIZE={}", MAX_POOL_SIZE);
    				mergePool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
    			}
    			mergePool.execute(getSyncRunnable(filter, item, rf, df));
    		}
	    }catch(Exception e){
	        LoggerFactory.getLogger(RemoteFileSync.class).error(e.getMessage(),e);
	    }
	}

	/**
	* Performs the getSyncRunnable operation
	* @param filter - the filter
	* @param item - the item
	* @param rf - the rf
	* @param df - the df
	* @return the getSyncRunnable result
	* */
	private Runnable getSyncRunnable(final CacheArrayFilter filter, final Map<String, Object> item, final RemoteFileSync rf,
			final DiskFile df) {
		Runnable runnable = new Runnable() {

			/**
			* Performs the run operation
			* */
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
        			    Logger.systemError(RemoteFileSync.class,"The root path '{}' does not exist.",syncRoot.getPath());
        			}

					if (isDeleted && rootExists) {
						if (df.exists()) {
							if (isDebug) {
								LoggerFactory.getLogger(RemoteFileSync.class).mark("DeleteDiskFile - {}", df.getPath());
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
										LoggerFactory.getLogger(RemoteFileSync.class).mark("ReCreateDiskLink - {}",
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
									LoggerFactory.getLogger(RemoteFileSync.class).mark("CreateDiskLink - {}", df.getPath());
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
												LoggerFactory.getLogger(RemoteFileSync.class).mark("DiskQueuing - {}",
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
												LoggerFactory.getLogger(RemoteFileSync.class).mark("DiskQueuing - {}",
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
									LoggerFactory.getLogger(RemoteFileSync.class).mark("CreateDiskDir - {}", df.getPath());
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
			@Override
			public void run() {
				startFullAsyncThread(false, timerMs, replaceTo, completedCallback);
			}

		});

		Executors.newFixedThreadPool(1).execute(new Runnable() {


			/**
			* Performs the run operation
			* */
			@Override
			public void run() {
				startFullAsyncThreadForDelete(false, timerMs, replaceTo, completedCallback);
			}

		});
	}

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
				@Override
				public void completed(Integer size) {
					if (isStopSync()) {
						LoggerFactory.getLogger(RemoteFileSync.class).mark("Stoped archive for file deleted.");
					}
					if (timerMs <= 0) {
						LoggerFactory.getLogger(RemoteFileSync.class).mark("Completed archive for file deleted.");
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
				@Override
				public void execute(Integer index, Object o) {
				    Thread.currentThread().setName(String.format("RemoteFile-execute-archiveForFileDeleted-%s", index));
					if(isDebug(the)){
					    debugLog("CheckUsage",the,this);
					}
					if (memoryUsage() < RemoteFile.MAX_MEMORY_USAGE) {
						CacheDriverExecutor _executor = null;
						try {
							@SuppressWarnings("unchecked")
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
				@Override
				public void completed(Integer size) {
					if (isStopSync()) {
						LoggerFactory.getLogger(RemoteFileSync.class).mark("Stoped archive for deleted.");
					}
					if (timerMs <= 0) {
						LoggerFactory.getLogger(RemoteFileSync.class).mark("Completed archive for deleted.");
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
							@SuppressWarnings("unchecked")
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
				@Override
				public void completed(Integer size) {
					if (isStopSync()) {
						LoggerFactory.getLogger(RemoteFileSync.class).mark("Stoped archive for timeout deleted.");
					}
					if (timerMs <= 0) {
						LoggerFactory.getLogger(RemoteFileSync.class).mark("Completed archive for timeout deleted.");
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
							@SuppressWarnings("unchecked")
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

}
