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
import com.killcoding.tool.ConfigProperties;
import com.killcoding.tool.CommonTools;
import com.killcoding.log.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import com.killcoding.datasource.CacheDriverExecutor;
import java.util.Map;
import java.util.HashMap;
import com.killcoding.datasource.Clock;
import java.sql.Timestamp;

import java.io.ByteArrayOutputStream;
import com.killcoding.cache.CacheArray;

/**
 * RemoteFile - public facade of the remote (database-backed) file engine.
 * Public API surface: construction, existence/listing, shard read/write,
 * copy/move/link, delete/force-delete, completion and the async archive/sync
 * schedulers. The implementation lives in the abstract parent RemoteFileBase.
 * */
public class RemoteFile extends RemoteFileSync {


	/**
	* Constructs a new RemoteFile
	* @param path - the path
	* */
	@SuppressWarnings("this-escape")
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
	@SuppressWarnings("this-escape")
	public RemoteFile(File origin) {
		super(origin);
		log = LoggerFactory.getLogger(getClass());
		fileId = CommonTools.generateId(16);
		initGlobal();
	}

	/**
	* Performs the initPool operation
	* @param fileId - the fileId
	* */
	public Map<String, Object> getRecordByFileId(Object fileId) throws IOException {
		String sql = formatSql(SQL_SELECT_GET_RECORD_BY_FILE_ID);
		Map<String, Object> dataRecord = new HashMap<String, Object>();
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
			Map<String, Object> dataRecord = new HashMap<String, Object>();
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
	* Performs the writeBlock operation
	* @param data - the data
	* @param append - the append
	* @return the writeBlock result
	* */
	public synchronized boolean writeBlock(byte[] data, boolean append) throws IOException {
		return writeTo(data, append);
	}

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
	* Performs the isTimeout operation
	* @return the isTimeout result
	* */
	public boolean isTimeout() throws IOException {
		return isTimeout(LOGIC_TIMEOUT_MS);
	}


	/*
	* Performs the copyTo operation
	* @param toPath - the toPath
	* */
	/**

	/*
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

}
