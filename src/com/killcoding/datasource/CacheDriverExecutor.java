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
package com.killcoding.datasource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;




import java.util.Arrays;
import com.killcoding.tool.ResultMap;


import com.killcoding.cache.CacheArray;
import java.io.IOException;
import java.sql.Blob;
import java.sql.Clob;
import java.io.InputStream;
import java.sql.CallableStatement;

/**
 * This class uses a 'CacheArray' to process the asynchronous processing method of the data query result set.
 * If you choose to use the mode 'DiskCache', temporary files will be generated. 
 * If you choose to use the mode 'MemoryCache', temporary files will not be generated but the physical memory size must be considered.
 * */
public final class CacheDriverExecutor extends DriverExecutor {

	public static long READ_TIMER = 10L;

	/**
	 * New a object CacheDriverExecutor
	 * @param connection - it is jdbc connection
	 * */
	public CacheDriverExecutor(Connection connection) {
		super(connection);
	}

	/**
	 * This is full table query function
	 * @param sql - Query sql 
	 * @param params - Query params 
	 * @param rows - CacheArray object
	 * @exception SQLException - if query failed
	 * */
	public void find(String sql, List<Object> params, final CacheArray rows) throws SQLException {
		find(0, 0, sql, params, rows);
	}

	/**
	 * This is full table query function
	 * @param sql - Query sql 
	 * @param params - Query params 
	 * @param rows - CacheArray object
	 * @exception SQLException - if query failed
	 * */
	public void find(String sql, Map<String, Object> params, final CacheArray rows)
			throws SQLException {
		String csql = converSql(sql);
		List<Object> cparams = converParams(sql, params);
		find(0, 0, csql, cparams, rows);
	}

	/**
	 * This is full table query function
	 * @param sql - Query sql 
	 * @param rows - CacheArray object
	 * @exception SQLException - if query failed
	 * */
	public void find(String sql, final CacheArray rows) throws SQLException {
		find(0, 0, sql, Arrays.asList(new Object[] {}), rows);
	}

	/**
	 * This is limited rows query function
	 * @param cursorStart - JDBC result Cursor start index
	 * @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	 * @param sql - Query sql 
	 * @param rows - CacheArray object
	 * @exception SQLException - if query failed
	 * */
	public void find(int cursorStart, int maxRows, String sql, final CacheArray rows)
			throws SQLException {
		find(cursorStart, maxRows, sql, Arrays.asList(new Object[] {}), rows);
	}

	/**
	 * This is limited rows query function (use Map param mode)
	 * @param cursorStart - JDBC result Cursor start index
	 * @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	 * @param sql - Query sql 
	 * @param rows - CacheArray object
	 * @exception SQLException - if query failed
	 * */
	public void find(int cursorStart, int maxRows, String sql, Map<String, Object> params,
			final CacheArray rows) throws SQLException {
		String csql = converSql(sql);
		List<Object> cparams = converParams(sql, params);
		find(cursorStart, maxRows, csql, cparams, rows);
	}

	/**
	 * This is limited rows query function (use List param mode)
	 * @param cursorStart - JDBC result Cursor start index
	 * @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	 * @param sql - Query sql 
	 * @param rows - CacheArray object
	 * @exception SQLException - if query failed
	 * */
	public void find(int cursorStart, int maxRows, String sql, List<Object> params,
			final CacheArray rows) throws SQLException {
		long begin = System.currentTimeMillis();
		boolean allowedLog = writeSqlLog("find", begin,
				String.format("%s [cursorStart=%s,maxRows=%s]", sql, cursorStart, maxRows), params);
		PreparedStatement statement = null;
		Map<String, Object> row = null;

		ResultSet result = null;
		try {
			// ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE
			// ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY
			statement = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			if (params != null) {
				int size = params.size();
				for (int i = 0; i < size; i++) {
					int ci = i + 1;
					Object param = params.get(i);
					if (param == null) {
						statement.setNull(ci, Types.VARCHAR);
					} else {
						statement.setObject(ci, param);
					}
				}
			}
			if (maxRows > 0) {
				statement.setMaxRows(maxRows);
			}
			result = statement.executeQuery();
			result.absolute(cursorStart);
			final ResultSetMetaData rsmd = result.getMetaData();
			final int c = rsmd.getColumnCount();
			while (result.next()) {
				row = new ResultMap<String, Object>();
				for (int i = 0; i < c; i++) {
					int ci = i + 1;
					Object value = null;
					Object originValue = result.getObject(ci);
					if (originValue == null) {
						value = originValue;
					} else if (originValue instanceof Blob) {
						Blob blobValue = (Blob) originValue;
						InputStream is = null;
						try {
							is = blobValue.getBinaryStream();
							if(is != null) value = is.readAllBytes();
						} catch (IOException e) {
							throw new SQLException(e.getMessage(), e);
						} finally {
							if (blobValue != null) {
								try {
									blobValue.free();
								} catch (SQLException e) {
									throw e;
								}
							}
							if (is != null) {
								try {
									is.close();
								} catch (IOException e) {
									throw new SQLException(e.getMessage(), e);
								}
							}
						}
					} else if (originValue instanceof Clob) {
						Clob clobValue = (Clob) originValue;
						InputStream is = null;
						try {
							is = clobValue.getAsciiStream();
                            if(is != null) value = is.readAllBytes();
						} catch (IOException e) {
							throw new SQLException(e.getMessage(), e);
						} finally {
							if (clobValue != null) {
								try {
									clobValue.free();
								} catch (SQLException e) {
									throw e;
								}
							}
							if (is != null) {
								try {
									is.close();
								} catch (IOException e) {
									throw new SQLException(e.getMessage(), e);
								}
							}
						}
					} else {
						value = originValue;
					}
					row.put(converCase(rsmd.getColumnLabel(ci)), value);
				}
				try {
					rows.add(row,getReadTimer());
				} catch (Exception e) {
					log.debug(e);
					continue;
				}
			}
			rows.add(null);

			if (allowedLog) {
				writeSqlLog("rows", begin, "rows", rows.size() - 1);
				long spend = System.currentTimeMillis() - begin;
				writeSqlLog("spend", begin, "spend", spend);
			}
		} catch (SQLException e) {
		    if(rows != null) rows.add(null);
		    
			if (allowedLog)
				writeSqlLog("error", begin, "error", String.format("%s,%s", e.getErrorCode(), e.getMessage()));
			throw e;
		} finally {
			if (result != null)
				result.close();

			if (statement != null)
				statement.close();

		}
	}

	/**
	 * Execute stored proc(and return result to CacheArray) 
	 * @param cursorStart - JDBC result Cursor start index
	 * @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	 * @param sql - Query sql 
	 * @param rows - CacheArray object
	 * @exception SQLException - if query failed 
	 * */
	public void callAndReturnList(int cursorStart, int maxRows, String sql, final CacheArray rows)
			throws SQLException {
		callAndReturnList(cursorStart, maxRows, sql, Arrays.asList(new Object[] {}), rows);
	}

	/**
	 * Execute stored proc(and return result) 
	 * @param cursorStart - JDBC result Cursor start index
	 * @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	 * @param sql - Query sql 
	 * @param params - Use Map param mode (use ':column_name' to mapping)
	 * @param rows - CacheArray object
	 * @exception SQLException - if query failed 
	 * */
	public void callAndReturnList(int cursorStart, int maxRows, String sql, Map<String, Object> params,
			final CacheArray rows) throws SQLException {
		String csql = converSql(sql);
		List<Object> cparams = converParams(sql, params);
		callAndReturnList(cursorStart, maxRows, csql, cparams, rows);
	}

	/**
	 * Execute stored proc(and return result) 
	 * @param cursorStart - JDBC result Cursor start index
	 * @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	 * @param sql - Query sql  (use '?' to mapping)
	 * @param params - Use List param mode
	 * @param rows - CacheArray object
	 * @exception SQLException - if query failed 
	 * */
	public void callAndReturnList(int cursorStart, int maxRows, String sql, List<Object> params,
			final CacheArray rows) throws SQLException {

		if (!checkSqlAvailable(sql))
			return;

		long begin = System.currentTimeMillis();
		boolean allowedLog = writeSqlLog("call", begin,
				String.format("%s [cursorStart=%s,maxRows=%s]", sql, cursorStart, maxRows), params);

		CallableStatement statement = null;
		Map<String, Object> row = null;
		ResultSet result = null;
		try {
			statement = connection.prepareCall("{" + sql + "}");
			if (params != null) {
				int size = params.size();
				for (int i = 0; i < size; i++) {
					int ci = i + 1;
					Object param = params.get(i);
					if (param == null) {
						statement.setNull(ci, Types.VARCHAR);
					} else {
						statement.setObject(ci, param);
					}
				}
			}
			if (maxRows > 0) {
				statement.setMaxRows(maxRows);
			}
			result = statement.executeQuery();
			callAndReturnListSync(connection, cursorStart, maxRows, sql, params);
			final ResultSetMetaData rsmd = result.getMetaData();
			final int c = rsmd.getColumnCount();
			int rowIndex = 0;
			while (result.next()) {
				if (rowIndex >= cursorStart) {
					row = new ResultMap<String, Object>();
					for (int i = 0; i < c; i++) {
						int ci = i + 1;
						Object value = null;
						Object originValue = result.getObject(ci);
						if (originValue == null) {
							value = originValue;
						} else if (originValue instanceof Blob) {
							Blob blobValue = (Blob) originValue;
							InputStream is = null;
							try {
								is = blobValue.getBinaryStream();
								if(is != null) value = is.readAllBytes();
							} catch (IOException e) {
								throw new SQLException(e.getMessage(), e);
							} finally {
								if (blobValue != null) {
									try {
										blobValue.free();
									} catch (SQLException e) {
										throw e;
									}
								}
								if (is != null) {
									try {
										is.close();
									} catch (IOException e) {
										throw new SQLException(e.getMessage(), e);
									}
								}
							}
						} else if (originValue instanceof Clob) {
							Clob clobValue = (Clob) originValue;
							InputStream is = null;
							try {
								is = clobValue.getAsciiStream();
								if(is != null) value = is.readAllBytes();
							} catch (IOException e) {
								throw new SQLException(e.getMessage(), e);
							} finally {
								if (clobValue != null) {
									try {
										clobValue.free();
									} catch (SQLException e) {
										throw e;
									}
								}
								if (is != null) {
									try {
										is.close();
									} catch (IOException e) {
										throw new SQLException(e.getMessage(), e);
									}
								}
							}
						} else {
							value = originValue;
						}
						row.put(converCase(rsmd.getColumnLabel(ci)), value);
					}
					try {
						rows.add(row,getReadTimer());
					} catch (Exception e) {
						log.debug(e);
						continue;
					}
				}
				rowIndex++;
			}
			rows.add(null);

			if (allowedLog) {
				writeSqlLog("rows", begin, "rows", rows.size() - 1);
				long spend = System.currentTimeMillis() - begin;
				writeSqlLog("spend", begin, "spend", spend);
			}
		} catch (SQLException e) {
		    if(rows != null) rows.add(null);
		    
			if (allowedLog)
				writeSqlLog("error", begin, "error", e.getErrorCode());
			throw e;
		} finally {
			if (result != null)
				result.close();

			if (statement != null)
				statement.close();
		
		}
	}
	
	private long getReadTimer(){
	    return READ_TIMER;
	}

}