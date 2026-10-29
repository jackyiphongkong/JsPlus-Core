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
package com.killcoding.datasource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;
import com.killcoding.tool.ResultMap;
import java.sql.DatabaseMetaData;
import com.killcoding.log.LoggerFactory;
import com.killcoding.log.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import com.killcoding.tool.FileTools;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import com.killcoding.cache.CacheArray;
import com.killcoding.cache.CacheArrayFilter;
import com.killcoding.tool.CodeEscape;
import java.sql.Blob;


import java.sql.Timestamp;
import java.sql.Clob;
import java.io.InputStream;
import java.sql.CallableStatement;

/**
* This class is execute sql base class.
* Support database replication.
* Support database multi-activity.
* Support database CRUD
* */
public class DriverExecutor {

	protected final static Map<Integer, List<DriverExecutor>> SYNC_EXECUTOR_MARK = new ConcurrentHashMap<Integer, List<DriverExecutor>>();
	private final static Map<Integer, Long> SYNC_CONN_ERROR_TIME = new ConcurrentHashMap<Integer, Long>();

	private final static Map<String,String> SQL_LOG_MSG_MAPPING = new ConcurrentHashMap<String,String>();
	private final static Map<String,Boolean> SQL_LOG_OVERSPEND_MAPPING = new ConcurrentHashMap<String,Boolean>();

	protected Logger log = null;

	public final static String COLUMN_NAME_CASE_UPPER = "UPPER";
	public final static String COLUMN_NAME_CASE_LOWER = "LOWER";
	public final static String COLUMN_NAME_CASE_ORIGINAL = "ORIGINAL";
	public static String COLUMN_NAME_CASE_MODE = COLUMN_NAME_CASE_LOWER;

	protected boolean closed = true;
	protected Connection connection = null;
	private static CacheArray sqlLogCacheArray = null;

	/**
	* New a DriverExecutor object
	* @param connection - JDBC connection
	* */
	@SuppressWarnings("this-escape")
	public DriverExecutor(Connection connection) {
		super();
		log = LoggerFactory.getLogger(this.getClass());
		this.connection = connection;
		if(this.connection == null){
		    writeSqlLog("open", 0, "open", "The connection is null");
		}else{
		    try{
		        writeSqlLog("open", 0, "open", String.format("AutoCommit=%s",this.connection.getAutoCommit() + ""));
		    }catch(Exception e) {
		        log.error(e.getMessage(),e);
		    }
		}
		closed = (this.connection == null);
	}

	/**
	* Get current Connection
	* @return Connection
	* */
	public Connection getConnection() {
		return connection;
	}

	/**
	* Get column classes by table name
	* @exception SQLException
	* @return Map&lt;String,Object&gt; - column and java type class mapping
	* @param tableName - table name
	* */
	public Map<String, Object> getColumnClasses(String tableName) throws SQLException {
		Map<String, Object> types = null;
		ResultSet result = null;
		PreparedStatement statement = null;
		String sql = String.format("SELECT * FROM %s WHERE 1 = 0", tableName);
		statement = connection.prepareStatement(sql);
		result = statement.executeQuery();
		final ResultSetMetaData rsmd = result.getMetaData();
		for (int i = 0; i < rsmd.getColumnCount(); i++) {
			if (types == null) {
				types = new ResultMap<String, Object>();
			}
			String cn = rsmd.getColumnClassName(i + 1);
			types.put(converCase(rsmd.getColumnLabel(i + 1)), cn);
		}
		return types;
	}

	/**
	* Get column db data types by table name
	* @exception SQLException
	* @return Map&lt;String, Object&gt; - column and db data type mapping
	* @param tableName - Table name
	* */
	public Map<String, Object> getColumnTypes(String tableName) throws SQLException {
		ResultSet result = null;
		PreparedStatement statement = null;
		String sql = String.format("SELECT * FROM %s WHERE 1 = 0", tableName);
		Map<String, Object> types = null;
		statement = connection.prepareStatement(sql);
		result = statement.executeQuery();
		final ResultSetMetaData rsmd = result.getMetaData();
		for (int i = 0; i < rsmd.getColumnCount(); i++) {
			if (types == null) {
				types = new ResultMap<String, Object>();
			}
			types.put(converCase(rsmd.getColumnLabel(i + 1)), rsmd.getColumnTypeName(i + 1));
		}
		return types;
	}

	/**
	* Show column data type and java type mapping
	* @exception SQLException
	* @return List&lt;Map&lt;String, Object&gt;> - Mapping list
	* @param tableName - Table name
	* */
	public List<Map<String, Object>> desc(String tableName) throws SQLException {
		List<String> primaryKeys = getPrimaryKeys(tableName);
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
		ResultSet result = null;
		PreparedStatement statement = null;
		String sql = String.format("SELECT * FROM %s WHERE 1 = 0", tableName);
		statement = connection.prepareStatement(sql);
		result = statement.executeQuery();
		final ResultSetMetaData rsmd = result.getMetaData();
		for (int i = 0; i < rsmd.getColumnCount(); i++) {
			Map<String, Object> types = new ResultMap<String, Object>();
			int ci = i + 1;
			boolean nullable = rsmd.isNullable(ci) == 1;
			String name = converCase(rsmd.getColumnLabel(ci));
			String isPk = "UNKNOWN";
			if (primaryKeys != null) {
				isPk = primaryKeys.contains(name) ? "Y" : "N";
			}
			types.put("NAME", name);
			types.put("PRIMARY_KEY", isPk);
			types.put("DATA_TYPE", rsmd.getColumnTypeName(ci));
			types.put("JAVA_TYPE", rsmd.getColumnClassName(ci));
			types.put("PRECISION", rsmd.getPrecision(ci));
			types.put("ALLOW_NULLABLE", nullable ? "Y" : 'N');
			results.add(types);
		}
		return results;
	}

	/**
	* Get primary Keys by table name
	* @return List&lt;String&gt; - Primary Keys
	* @param _tableName - Table name
	* */
	public List<String> getPrimaryKeys(String _tableName) {
		try {
			List<String> pks = new ArrayList<String>();
			DatabaseMetaData meta = connection.getMetaData();
			ResultSet tables = meta.getTables(null, null, "%", new String[] { "TABLE" });
			while (tables.next()) {
				String catalog = tables.getString("TABLE_CAT");
				String schema = tables.getString("TABLE_SCHEM");
				String tableName = tables.getString("TABLE_NAME");
				if (tableName.equalsIgnoreCase(_tableName)) {
					ResultSet primaryKeys = meta.getPrimaryKeys(catalog, schema, tableName);
					while (primaryKeys.next()) {
						pks.add(primaryKeys.getString("COLUMN_NAME"));
					}
					break;
				}
			}
			return pks;
		} catch (Exception e) {
			log.warn(e);
			return null;
		}
	}

	/**
	* Get all tables
	* @return List&lt;Map&lt;String, Object&gt;>
	* @exception SQLException
	* */
	public List<Map<String, Object>> getAllTables() throws SQLException {
		List<Map<String, Object>> tablesList = new ArrayList<Map<String, Object>>();
		DatabaseMetaData meta = connection.getMetaData();
		ResultSet tables = meta.getTables(null, null, "%", new String[] { "TABLE" });
		while (tables.next()) {
			String schema = tables.getString("TABLE_SCHEM");
			String tableName = tables.getString("TABLE_NAME");
			Map<String, Object> t = new ResultMap<String, Object>();
			t.put("TABLE_SCHEMA", schema);
			t.put("TABLE_NAME", tableName);
			tablesList.add(t);
		}
		return tablesList;
	}

	/**
	* Get all tables by schema
	* @param _schema - the _schema
	* @return List&lt;Map&lt;String, Object&gt;>
	* @exception SQLException
	* */
	public List<Map<String, Object>> getAllTables(String _schema) throws SQLException {
		List<Map<String, Object>> tablesList = new ArrayList<Map<String, Object>>();
		DatabaseMetaData meta = connection.getMetaData();
		ResultSet tables = meta.getTables(null, null, "%", new String[] { "TABLE" });
		while (tables.next()) {
			String schema = tables.getString("TABLE_SCHEM");
			if (schema != null && schema.equalsIgnoreCase(_schema)) {
				String tableName = tables.getString("TABLE_NAME");
				Map<String, Object> t = new ResultMap<String, Object>();
				t.put("TABLE_SCHEMA", schema);
				t.put("TABLE_NAME", tableName);
				tablesList.add(t);
			}
		}
		return tablesList;
	}

	/**
	* Query first record
	* @exception SQLException
	* @return Map&lt;String,Object&gt; - First result
	* @param sql - Condition use format ':column_name'
	* @param params
	* */
	public Map<String, Object> first(String sql, Map<String, Object> params) throws SQLException {
		List<Map<String, Object>> list = find(0, 1, sql, params);
		if (list.size() > 0) {
			return list.get(0);
		}
		return null;
	}

	/**
	* Query first record
	* @exception SQLException
	* @return Map&lt;String,Object&gt; - First result
	* @param sql
	* */
	public Map<String, Object> first(String sql) throws SQLException {
		return first(sql, Arrays.asList(new Object[] {}));
	}

	/**
	* Query first record
	* @exception SQLException
	* @return Map&lt;String,Object&gt; - First result
	* @param sql - Condition use format '?'
	* @param params
	* */
	public Map<String, Object> first(String sql, List<Object> params) throws SQLException {
		List<Map<String, Object>> list = find(0, 1, sql, params);
		if (list.size() > 0) {
			return list.get(0);
		}
		return null;
	}

	/**
	* Query all matched records
	* @exception SQLException
	* @return List&lt;Map&lt;String, Object&gt;>
	* @param sql
	* */
	public List<Map<String, Object>> find(String sql) throws SQLException {
		return find(0, 0, sql, Arrays.asList(new Object[] {}));
	}

	/**
	* Query all matched records
	* @exception SQLException
	* @return List&lt;Map&lt;String, Object&gt;>
	* @param cursorStart - JDBC result Cursor start index
	* @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	* @param sql
	* */
	public List<Map<String, Object>> find(int cursorStart, int maxRows, String sql) throws SQLException {
		return find(cursorStart, maxRows, sql, Arrays.asList(new Object[] {}));
	}

	/**
	* Query all matched records
	* @exception SQLException
	* @return List&lt;Map&lt;String, Object&gt;>
	* @param sql - Condition use format ':column_name'
	* @param params
	* */
	public List<Map<String, Object>> find(String sql, Map<String, Object> params) throws SQLException {
		return find(0, 0, sql, params);
	}

	/**
	* Query all matched records
	* @exception SQLException
	* @return List&lt;Map&lt;String, Object&gt;>
	* @param cursorStart - JDBC result Cursor start index
	* @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	* @param sql - Condition use format ':column_name'
	* @param params
	* */
	public List<Map<String, Object>> find(int cursorStart, int maxRows, String sql, Map<String, Object> params)
			throws SQLException {
		String csql = converSql(sql);
		List<Object> cparams = converParams(sql, params);
		return find(cursorStart, maxRows, csql, cparams);
	}

	/**
	* Query all matched records
	* @exception SQLException
	* @return List&lt;Map&lt;String, Object&gt;>
	* @param cursorStart - JDBC result Cursor start index
	* @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	* @param sql - Condition use format '?'
	* @param params
	* */
	public List<Map<String, Object>> find(int cursorStart, int maxRows, String sql, List<Object> params)
			throws SQLException {
		long begin = System.currentTimeMillis();
		boolean allowedLog = writeSqlLog("find", begin,
				String.format("%s [cursorStart=%s,maxRows=%s]", sql, cursorStart, maxRows), params);

		PreparedStatement statement = null;
		Map<String, Object> row = null;
		ResultSet result = null;
		final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
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
				rows.add(row);
			}
			if (allowedLog) {
				writeSqlLog("rows", begin, "rows", rows.size());
				long spend = System.currentTimeMillis() - begin;
				writeSqlLog("spend", begin, "spend", spend);
			}
			return rows;
		} catch (SQLException e) {
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
	* Execute stored proc (and return boolean)
	* @param sql - Query sql
	* @exception SQLException
	* @return boolean
	* */
	public boolean callAndReturnBoolean(String sql) throws SQLException {
		return callAndReturnBoolean(sql, Arrays.asList(new Object[] {}));
	}

	/**
	* Execute stored proc (and return boolean)
	* @param sql - Query sql, condition use format ':column_name'
	* @param params
	* @exception SQLException
	* @return boolean
	* */
	public boolean callAndReturnBoolean(String sql, Map<String, Object> params) throws SQLException {
		String csql = converSql(sql);
		List<Object> cparams = converParams(sql, params);
		return callAndReturnBoolean(sql, cparams);
	}

	/**
	* Execute stored proc (and return boolean)
	* @param sql - Query sql,condition use format '?'
	* @param params
	* @exception SQLException
	* @return boolean
	* */
	public boolean callAndReturnBoolean(String sql, List<Object> params) throws SQLException {

		if (!checkSqlAvailable(sql))
			return false;

		long begin = System.currentTimeMillis();
		boolean allowedLog = writeSqlLog("call", begin, sql, params);

		CallableStatement statement = null;
		try {
			statement = connection.prepareCall("{" + sql + "}");
			if (params != null) {
				int size = params.size();
				for (int i = 0; i < size; i++) {
					Object param = params.get(i);
					if (param == null) {
						statement.setNull(i + 1, Types.VARCHAR);
					} else {
						statement.setObject(i + 1, param);
					}
				}
			}

            statement.execute();

			boolean returnResult = true; //true is not exception

			if (allowedLog) {
				writeSqlLog("return", begin, "return", returnResult);
				long spend = System.currentTimeMillis() - begin;
				writeSqlLog("spend", begin, "spend", spend);
			}

			callAndReturnBooleanSync(connection, begin, sql, params, returnResult);

			return returnResult;
		} catch (SQLException e) {
			if (allowedLog)
				writeSqlLog("error", begin, "error", String.format("%s,%s", e.getErrorCode(), e.getMessage()));

			throw e;
		} finally {
			if (statement != null)
				statement.close();
		}
	}

	/**
	* Execute stored proc (and return rows)
	* @param sql - Query sql
	* @exception SQLException
	* @return int
	* */
	public int callAndReturnRows(String sql) throws SQLException {
		return callAndReturnRows(sql, Arrays.asList(new Object[] {}));
	}

	/**
	* Execute stored proc (and return rows)
	* @param sql - Query sql,condition use format ':column_name'
	* @param params
	* @exception SQLException
	* @return int
	* */
	public int callAndReturnRows(String sql, Map<String, Object> params) throws SQLException {
		String csql = converSql(sql);
		List<Object> cparams = converParams(sql, params);
		return callAndReturnRows(sql, cparams);
	}

	/**
	* Performs the callAndReturnRows operation
	* @param sql - the sql
	* @param params - the params
	* @return the callAndReturnRows result
	* */
	public int callAndReturnRows(String sql, List<Object> params) throws SQLException {
        return (int)callAndReturnRows(Types.INTEGER,sql,params);
	}

	/**
	* Execute stored proc (and return rows)
	* @param sqlTypes - e.g Types.INTEGER
	* @param sql - Query sql,condition use format '?'
	* @param params
	* @exception SQLException
	* @return Object
	* */
	public Object callAndReturnRows(int sqlTypes,String sql, List<Object> params) throws SQLException {

		if (!checkSqlAvailable(sql))
			return -1;

		long begin = System.currentTimeMillis();
		boolean allowedLog = writeSqlLog("call", begin, sql, params);

		CallableStatement statement = null;
		try {
			statement = connection.prepareCall("{?=" + sql + "}");
			statement.registerOutParameter(1,sqlTypes);
			if (params != null) {
				int size = params.size();
				for (int i = 0; i < size; i++) {
					Object param = params.get(i);
					if (param == null) {
						statement.setNull(i + 1, Types.VARCHAR);
					} else {
						statement.setObject(i + 1, param);
					}
				}
			}
			boolean hasResult = statement.execute();

			Object row = statement.getObject(1);

			if (allowedLog) {
				writeSqlLog("return", begin, "return", row);
				long spend = System.currentTimeMillis() - begin;
				writeSqlLog("spend", begin, "spend", spend);
			}

			callAndReturnRowsSync(connection, begin, sql, params, row);

			return row;
		} catch (SQLException e) {
			if (allowedLog)
				writeSqlLog("error", begin, "error", String.format("%s,%s", e.getErrorCode(), e.getMessage()));

			throw e;
		} finally {
			if (statement != null)
				statement.close();
		}
	}

	/**
	* Execute stored proc (and return List)
	* @param sql - Query sql
	* @exception SQLException
	* @return List&lt;Map&lt;String, Object&gt;>
	* */
	public List<Map<String, Object>> callAndReturnList(String sql) throws SQLException {
		return callAndReturnList(0, 0, sql, Arrays.asList(new Object[] {}));
	}

	/**
	* Execute stored proc (and return List)
	* @param cursorStart - JDBC result Cursor start index
	* @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	* @param sql - Query sql
	* @exception SQLException
	* @return List&lt;Map&lt;String, Object&gt;>
	* */
	public List<Map<String, Object>> callAndReturnList(int cursorStart, int maxRows, String sql) throws SQLException {
		return callAndReturnList(cursorStart, maxRows, sql, Arrays.asList(new Object[] {}));
	}

	/**
	* Execute stored proc (and return List)
	* @param cursorStart - JDBC result Cursor start index
	* @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	* @param sql - Query sql, condition use format ':column_name'
	* @param params - Query params (Map mode: :name placeholders are converted)
	* @exception SQLException
	* @return List&lt;Map&lt;String, Object&gt;>
	* */
	public List<Map<String, Object>> callAndReturnList(int cursorStart, int maxRows, String sql,
			Map<String, Object> params) throws SQLException {
		String csql = converSql(sql);
		List<Object> cparams = converParams(sql, params);
		return callAndReturnList(cursorStart, maxRows, sql, cparams);
	}

	/**
	* Execute stored proc (and return List)
	* @param params - the params
	* @param cursorStart - JDBC result Cursor start index
	* @param maxRows - JDBC result max rows (JDBC limited rows 50,000,000)
	* @param sql - Query sql, condition use format '?'
	* @exception SQLException
	* @return List&lt;Map&lt;String, Object&gt;>
	* */
	public List<Map<String, Object>> callAndReturnList(int cursorStart, int maxRows, String sql, List<Object> params)
			throws SQLException {

		if (!checkSqlAvailable(sql))
			return null;

		long begin = System.currentTimeMillis();
		boolean allowedLog = writeSqlLog("call", begin,
				String.format("%s [cursorStart=%s,maxRows=%s]", sql, cursorStart, maxRows), params);

		CallableStatement statement = null;
		Map<String, Object> row = null;
		ResultSet result = null;
		final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
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
					rows.add(row);
				}
				rowIndex++;
			}

			if (allowedLog) {
				writeSqlLog("rows", begin, "rows", rows);
				long spend = System.currentTimeMillis() - begin;
				writeSqlLog("spend", begin, "spend", spend);
			}

			callAndReturnListSync(connection, cursorStart, maxRows, sql, params);

			return rows;
		} catch (SQLException e) {
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
	* Execute sql
	* @exception SQLException
	* @param sql
	* @return int
	* */
	public int execute(String sql) throws SQLException {
		return execute(sql, Arrays.asList(new Object[] {}));
	}

	/**
	* Execute sql
	* @exception SQLException
	* @param sql - Condition use format ':column_name'
	* @param params
	* @return int
	* */
	public int execute(String sql, Map<String, Object> params) throws SQLException {
		String csql = converSql(sql);
		List<Object> cparams = converParams(sql, params);
		return execute(csql, cparams);
	}

	/**
	* Execute sql
	* @exception SQLException
	* @param sql - Condition use format '?'
	* @param params
	* @return int
	* */
	public int execute(String sql, List<Object> params) throws SQLException {
		if (!checkSqlAvailable(sql))
			return -1;

		long begin = System.currentTimeMillis();
		boolean allowedLog = writeSqlLog("execute", begin, sql, params);

		PreparedStatement statement = null;
		try {
			statement = connection.prepareStatement(sql);
			if (params != null) {
				int size = params.size();
				for (int i = 0; i < size; i++) {
					Object param = params.get(i);
					if (param == null) {
						statement.setNull(i + 1, Types.VARCHAR);
					} else {
						statement.setObject(i + 1, param);
					}
				}
			}
			int row = statement.executeUpdate();

			if (allowedLog) {
				writeSqlLog("return", begin, "return", row);
				long spend = System.currentTimeMillis() - begin;
				writeSqlLog("spend", begin, "spend", spend);
			}

			executeSync(connection, begin, sql, params, row);

			return row;
		} catch (SQLException e) {
			if (allowedLog)
				writeSqlLog("error", begin, "error", String.format("%s,%s", e.getErrorCode(), e.getMessage()));
			throw e;
		} finally {
			if (statement != null)
				statement.close();
		}
	}

	/**
	* Execute batch
	* @exception SQLException
	* @param sql - Condition use format ':column_name'
	* @param records
	* @return int - Return rows
	* */
	public int executeBatch(String sql, List<Map<String, Object>> records) throws SQLException {
		String csql = converSql(sql);
		List<List<Object>> crecords = new ArrayList<List<Object>>();
		for (Map<String, Object> record : records) {
			List<Object> crecord = converParams(sql, record);
			crecords.add(crecord);
		}
		return executeBatchList(csql, crecords);
	}

	/**
	* Execute batch
	* @exception SQLException
	* @param sql - Condition use format '?'
	* @param records
	* @return int - Return rows
	* */
	public int executeBatchList(String sql, List<List<Object>> records) throws SQLException {
		boolean allowedLog = false;
		if (!checkSqlAvailable(sql))
			return -1;

		long begin = System.currentTimeMillis();
		if (records != null) {
			allowedLog = writeSqlLog("batch", begin, sql, String.format("[batchSize=%s]", records.size()));
		}

		boolean first = true;
		PreparedStatement statement = null;
		try {
			for (List<Object> params : records) {
				if (first) {
					statement = connection.prepareStatement(sql);
					first = false;
				}

				int size = params.size();
				for (int i = 0; i < size; i++) {
					Object param = params.get(i);
					if (param == null) {
						statement.setNull(i + 1, Types.VARCHAR);
					} else {
						statement.setObject(i + 1, param);
					}
				}

				statement.addBatch();
			}

			int sumRow = 0;
			if (records.size() > 0) {
				int[] rows = statement.executeBatch();

				for (int r : rows) {
					sumRow += r;
				}

				if (allowedLog) {
					long spend = System.currentTimeMillis() - begin;
					writeSqlLog("return", begin, "return", sumRow);
					writeSqlLog("spend", begin, "spend", spend);
				}

				executeBatchListSync(connection, begin, sql, records, sumRow);
			}

			return sumRow;
		} catch (SQLException e) {
			if (allowedLog)
				writeSqlLog("error", begin, "error", String.format("%s,%s", e.getErrorCode(), e.getMessage()));

			throw e;
		} finally {
			if (statement != null)
				statement.close();
		}
	}

	/**
	* Check connection is closed
	* @return boolean
	* */
	public boolean isClosed() {
		try {
		    if(closed) return closed;

			return (connection == null || connection.isClosed());
		} catch (Exception e) {
			log.warn(e);
			return true;
		}
	}

	/**
	* Abort connection
	* @exception SQLException
	* */
	public void abort() throws SQLException {
		writeSqlLog("aborting", 0, "aborting", "");
		if (connection != null) {
			try {
				if (connection instanceof DriverConnection) {
					abortSyncConnection(connection);
					connection.abort(null);
				} else {
					connection.abort(Executors.newFixedThreadPool(1));
				}
				closed = true;
			} catch (SQLException e) {
				writeSqlLog("error", 0, "error", String.format("%s,%s", e.getErrorCode(), e.getMessage()));
				throw e;
			}
		}
		writeSqlLog("aborted", 0, "aborted", "");
	}

	/**
	* Close connection
	* @exception SQLException
	* */
	public void close() throws SQLException {
		writeSqlLog("closing", 0, "closing", "");
		if (connection != null) {
			try {
				closeSyncConnection(connection);
				connection.close();
				closed = true;
			} catch (SQLException e) {
				writeSqlLog("error", 0, "error", String.format("%s,%s", e.getErrorCode(), e.getMessage()));
				throw e;
			}
		}
		writeSqlLog("closed", 0, "closed", "");
	}

	/**
	* Commit connection
	* @exception SQLException
	* */
	public void commit() throws SQLException {
		writeSqlLog("committing", 0, "committing", "");
		if (connection != null) {
			try {
				commitSyncConnection(connection);
				connection.commit();
			} catch (SQLException e) {
				writeSqlLog("error", 0, "", String.format("%s,%s", e.getErrorCode(), e.getMessage()));
				throw e;
			}
		}
		writeSqlLog("committed", 0, "committed", "");
	}

	/**
	* Rollback connection
	* @exception SQLException
	* */
	public void rollback() throws SQLException {
		writeSqlLog("rollbacking", 0, "rollbacking", "");
		if (connection != null) {
			try {
				rollbackSyncConnection(connection);
				connection.rollback();
			} catch (SQLException e) {
				writeSqlLog("error", 0, "error", String.format("%s,%s", e.getErrorCode(), e.getMessage()));
				throw e;
			}
		}
		writeSqlLog("rollbacked", 0, "rollbacked", "");
	}

	/**
	* Get origin connection hash code
	* @return Integer - hash code
	* */
	private Integer getOriginConnectionHashCode() {
		if (connection instanceof DriverConnection) {
			return ((DriverConnection) connection).getOriginConnectionHashCode();
		} else {
			return connection.hashCode();
		}
	}

	/**
	* Get data source thread name
	* @return String - DataSource thread name
	* */
	private String getDataSourceName() {
		if (connection instanceof DriverConnection) {
			return ((DriverConnection) connection).getDriverDataSource().getName();
		} else {
			return null;
		}
	}

	/**
	* Conver sql from ':column_name' to '?'
	* @param sql - the sql
	* @return String
	* */
	protected String converSql(String sql) {
		return sql.replaceAll(":[a-zA-Z0-9_]+", "?");
	}

	/**
	* Conver param from map to list
	* @param sql
	* @param map
	* @return List&lt;Object&gt;
	* */
	protected List<Object> converParams(String sql, Map<String, Object> map) {
		List<String> paramKeys = new ArrayList<String>();
		Pattern pattern = Pattern.compile(":[a-zA-Z0-9_]+");
		Matcher matcher = pattern.matcher(sql);
		while (matcher.find()) {
			String key = matcher.group().replaceFirst(":", "");
			paramKeys.add(key);
		}

		List<Object> params = new ArrayList<Object>();
		for (String pk : paramKeys) {
			Object pv = map.get(pk);
			if (pv == null) {
				pv = map.get(pk.toLowerCase());
			}
			if (pv == null) {
				pv = map.get(pk.toUpperCase());
			}
			if (pv instanceof java.util.Date) {
				java.util.Date utilDate = (java.util.Date) pv;
				java.sql.Timestamp sqlDate = new java.sql.Timestamp(utilDate.getTime());
				params.add(sqlDate);
			} else {
				params.add(pv);
			}
		}

		return params;
	}

	/**
	* Covner to upper or lower
	* @param s - Column name or table name
	* @return String
	* */
	protected String converCase(String s) {
		if (COLUMN_NAME_CASE_MODE.equals(COLUMN_NAME_CASE_UPPER)) {
			return s.toUpperCase();
		} else if (COLUMN_NAME_CASE_MODE.equals(COLUMN_NAME_CASE_LOWER)) {
			return s.toLowerCase();
		} else {
			return s;
		}
	}

	/**
	* Check available sql to write to log
	* @return boolean
	* @param connection - Connection
	* @param sql
	* */
	protected static boolean checkSqlLogAvailable(Connection connection, String sql) {
		if (connection instanceof DriverConnection) {
			DriverConnection dc = (DriverConnection) connection;
			DriverDataSource dds = dc.getDriverDataSource();
			ConfigProperties configProperties = dds.getConfigProperties();
			List<String> sqlAllowed = configProperties.getArray("SqlLogAllowed");
			List<String> sqlIgnored = configProperties.getArray("SqlLogIgnored");
			return checkSqlAvailable(sql, sqlAllowed, sqlIgnored);
		}
		return false;
	}

	/**
	* Check available sql to execute
	* @return boolean
	* @param sql
	* */
	protected boolean checkSqlAvailable(String sql) {
		return checkSqlAvailable(connection, sql);
	}

	/**
	* Check available sql to execute
	* @return boolean
	* @param connection
	* @param sql
	* */
	protected static boolean checkSqlAvailable(Connection connection, String sql) {
		if (connection instanceof DriverConnection) {
			DriverConnection dc = (DriverConnection) connection;
			DriverDataSource dds = dc.getDriverDataSource();
			ConfigProperties configProperties = dds.getConfigProperties();
			List<String> sqlAllowed = configProperties.getArray("SqlExecuteAllowed");
			List<String> sqlIgnored = configProperties.getArray("SqlExecuteIgnored");
			return checkSqlAvailable(sql, sqlAllowed, sqlIgnored);
		}
		return true;
	}

	/**
	* Check available sql to execute
	* @return boolean
	* @param sql
	* @param sqlAllowed - From DataSources.properties
	* @param sqlIgnored - From DataSources.properties
	* */
	private static boolean checkSqlAvailable(String sql, List<String> sqlAllowed, List<String> sqlIgnored) {
		boolean matchedAllowed = true;
		boolean matchedIgnored = false;
		if (sqlAllowed != null) {
			for (String regex : sqlAllowed) {
				if (!CommonTools.isBlank(regex)) {
					matchedAllowed = sql.matches(regex);

					if (matchedAllowed)
						break;
				}
			}
		}
		if (matchedAllowed && sqlIgnored != null) {
			for (String regex : sqlIgnored) {
				if (!CommonTools.isBlank(regex)) {
					matchedIgnored = sql.matches(regex);

					if (matchedIgnored)
						break;
				}
			}
		}

		boolean b = matchedAllowed && !matchedIgnored;
		if (!b) {
			LoggerFactory.getLogger(DriverExecutor.class).debug("Not available - '{}'", sql);
		}
		return b;
	}

	/**
	* Write sql log
	* @return boolean
	* @param type
	* @param seq
	* @param sql
	* @param params
	* */
	protected synchronized boolean writeSqlLog(String type, long seq, String sql, Object params) {
		return writeSqlLog(this.hashCode(),connection, type, seq, sql, params);
	}

	/**
	* Write sql log
	* @param deHashCode - hash code of the originating DriverExecutor instance
	* @param connection - the connection the statement ran on
	* @param type - statement type label (e.g. SELECT/UPDATE)
	* @param seq - execution sequence number
	* @param sql - the sql statement
	* @param params - statement parameters
	* @return true when the log record was written
	* */
	protected synchronized static boolean writeSqlLog(int deHashCode,Connection connection, String type, long seq, String sql,
			Object params) {
		if (connection instanceof DriverConnection) {
			if (!checkSqlLogAvailable(connection, sql))
				return false;

			final String header = String.format(
					"LOG_DATE,LOG_HOUR,LOG_MI,LOG_SEC,LOG_MS,LOG_HOST,LOG_THREAD,LOG_DS,LOG_CONN_ID,LOG_EXEC_ID,LOG_TYPE,LOG_SEQ,LOG_SQL,LOG_PARAMS%s",
					System.lineSeparator());
			final DateFormat df = new SimpleDateFormat("yyyyMMdd");
			final DateFormat dtf = new SimpleDateFormat("yyyyMMdd,HH,mm,ss,SSS");
			DriverConnection dc = (DriverConnection) connection;
			Integer connHashCode = dc.getOriginConnectionHashCode();
			DriverDataSource dds = dc.getDriverDataSource();
			ConfigProperties configProperties = dds.getConfigProperties();

			boolean logEnable = configProperties.getBoolean("SqlLogEnable", false);
			long overspend = configProperties.getMilliSeconds("SqlLogOverspend",0L);

			if (!logEnable)
				return false;

			final String defaultLogFolderPath = String.format("%s/SqlLog/",
					CommonTools.getJarPath(DriverExecutor.class));
			final String logFolderPath = configProperties.getString("SqlLogFolder", defaultLogFolderPath);
			final long maxFileSize = configProperties.getFileSize("SqlLogMaxFileSize", 1024 * 1024 * 10L);
			final int archiveDays = configProperties.getInteger("SqlLogArchiveDays", 31);
			final int logParamMaxLength = configProperties.getInteger("SqlLogParamMaxLength", 20);

			if (sqlLogCacheArray == null) {

				sqlLogCacheArray = new CacheArray();
				long sqlLogFilterTimer = configProperties.getMilliSeconds("SqlLogTimer", 1000L);
				sqlLogCacheArray.filter(new CacheArrayFilter(0L,sqlLogFilterTimer) {
					/*
					* Performs the executeBatch operation
					* @param index - the index
					* @param batch - the batch
					* */
					@Override
					public void executeBatch(Integer index, List<?> batch) {
					    final StringBuffer sbf = new StringBuffer();
					    for(Object item : batch){
					        sbf.append(item);
					    }
						try {
							String msg = sbf.toString();
							String dateStr = df.format(new Timestamp(System.currentTimeMillis()));
							File sqlLogFile = new File(String.format("%s/%s.csv", logFolderPath, dateStr));
							File sqlLogFolder = new File(sqlLogFile.getParent());

							if (sqlLogFolder.exists()) {
								if (!sqlLogFolder.canWrite())
									throw new IOException(String.format("Can not write to log folder '%s'",
											sqlLogFolder.getAbsolutePath()));
							} else {
								sqlLogFolder.mkdirs();
							}

							if (sqlLogFile.exists()) {
								if (!sqlLogFile.canWrite())
									throw new IOException(String.format("Can not write to log file '%s'",
											sqlLogFile.getAbsolutePath()));
							}

							int suffixIndex = sqlLogFile.getName().lastIndexOf(".");
							String logFileNamePrefix = sqlLogFile.getName().substring(0, suffixIndex);
							long logSize = FileTools.size(sqlLogFile);
							if (!sqlLogFile.exists()) {
								FileTools.write(sqlLogFile, header, false);
							}
							if (logSize < maxFileSize) {
								FileTools.write(sqlLogFile, msg, true);
							} else {
								int logIndex = getLogFileIndex(configProperties, sqlLogFolder, logFileNamePrefix,
										"csv");
								if (logIndex == 0) {
									FileTools.write(sqlLogFile, String.format("%s%s", header, msg), false);
								} else {
									backupLog(sqlLogFolder, sqlLogFile, logFileNamePrefix, logIndex, header, msg);
								}
							}
							archiveLog(archiveDays, sqlLogFolder);
						} catch (Exception e) {
							LoggerFactory.getLogger(DriverExecutor.class).warn(e.getMessage(), e);
						}
					}
				});
			}

			if (logEnable) {
				String hostname = CommonTools.getHostname();
				String dateTimeStr = dtf.format(new Timestamp(System.currentTimeMillis()));
				boolean isOverspend = false;
				boolean isSpendSeq = sql.equals("spend");
				if(isSpendSeq && params != null){
				    long spendValue = Long.parseLong(params + "");
				    isOverspend = (spendValue >= overspend);
				}

                String threadId = Thread.currentThread().getName() + "-" + Thread.currentThread().threadId();
				String msg = String.format("%s,%s,%s,%s,%s,%s,%s,%s,\"%s\",\"%s\"%s", dateTimeStr, hostname,
				                    threadId, dds.getName(), connHashCode,deHashCode, type, seq,
				                    replaceToSigleLine(sql), handleParams(logParamMaxLength, params), System.lineSeparator());

				String key = String.format("%s_%s",deHashCode,connHashCode);
				if(seq <= 0 || overspend <= 0){
				    sqlLogCacheArray.add(msg);
   				    SQL_LOG_MSG_MAPPING.remove(key);
    				SQL_LOG_OVERSPEND_MAPPING.remove(key);
				}
				if(seq > 0 && overspend > 0){
    				SQL_LOG_OVERSPEND_MAPPING.put(key,isOverspend);
    				String existsMsg = SQL_LOG_MSG_MAPPING.get(key);
    				existsMsg = existsMsg == null ? msg : (existsMsg + msg);
    				SQL_LOG_MSG_MAPPING.put(key, existsMsg);

    				boolean seqOverspend = SQL_LOG_OVERSPEND_MAPPING.get(key);
    				if(isSpendSeq && seqOverspend){
    				    sqlLogCacheArray.add(SQL_LOG_MSG_MAPPING.get(key) + "");
    				    SQL_LOG_MSG_MAPPING.remove(key);
    				    SQL_LOG_OVERSPEND_MAPPING.remove(key);
    				}
    				if(isSpendSeq && !seqOverspend){
    				    SQL_LOG_MSG_MAPPING.remove(key);
    				    SQL_LOG_OVERSPEND_MAPPING.remove(key);
    				}
				}
				return true;
			}

		}
		return false;
	}

	/**
	* Backup log file
	* @param logFolder - the log folder
	* @param logFile - the active log file being rotated
	* @param logFileNamePrefix - file-name prefix used for the backup
	* @param logIndex - rotation index (older backups are deleted beyond the max)
	* @param header - header text written into the new active file
	* @param msg - message text written into the new active file
	* */
	private synchronized static void backupLog(File logFolder, File logFile, String logFileNamePrefix, int logIndex, String header,
			String msg) {
		try{
    		String backupLogFileName = String.format("%s/%s.%s.csv", logFolder.getAbsolutePath(), logFileNamePrefix,
    				logIndex);
    		Path source = Paths.get(logFile.getAbsolutePath());
    		Path target = Paths.get(backupLogFileName);
    		Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    		FileTools.write(logFile, String.format("%s%s", header, msg), false);
		}catch(Exception e){
		    LoggerFactory.getLogger(DriverExecutor.class).error(e.getMessage(),e);
		}
	}

	/**
	* Get log file index
	* For backup log file use
	* @param configProperties - the configProperties
	* @param folder - the folder
	* @param prefix - the prefix
	* @param suffix - the suffix
	* */
	private static int getLogFileIndex(ConfigProperties configProperties, File folder, String prefix, String suffix) {
		Integer maxBackupIndex = configProperties.getInteger("SqlLogMaxBackupIndex", 10);
		for (int i = 1; i <= maxBackupIndex; i++) {
			String logFileName = String.format("%s/%s.%s.%s", folder.getAbsolutePath(), prefix, i, suffix);
			File logFile = new File(logFileName);
			if (!logFile.exists())
				return i;
		}
		return 0;
	}

	/**
	* Archive Log
	* @param archiveDays - the archiveDays
	* @param sqlLogFolder - the sqlLogFolder
	* */
	private static void archiveLog(int archiveDays, File sqlLogFolder) {
		if (archiveDays > 0) {
			try {
				long archiveDaysMs = new Date().getTime() - (archiveDays * 24 * 3600000L);
				deleteFilesOlderThan(sqlLogFolder, archiveDaysMs);
			} catch (Exception e) {
				LoggerFactory.getLogger(DriverExecutor.class).warn(e);
			}
		}
	}

	/**
	* Delete old archive logs
	* @param directory - the directory
	* @param archiveDaysMs - the archiveDaysMs
	* */
	private static void deleteFilesOlderThan(File directory, long archiveDaysMs) throws IOException {
		if (directory.isDirectory()) {
			File[] files = directory.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
						boolean isLogFile = file.getName().toLowerCase().endsWith(".csv");
						if (isLogFile) {
							boolean canWrite = file.canWrite();
							if (canWrite) {
								long lastModified = file.lastModified();
								if (lastModified < archiveDaysMs) {
									Files.deleteIfExists(Paths.get(file.toURI()));
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	* Replace to sigle line
	* For write csv log
	* @param msg - the msg
	* */
	private static String replaceToSigleLine(String msg) {
		return CodeEscape.escapeToSingleLineForCsv(msg);
	}

	/**
	* Hahdle Params
	* For write csv log
	* @param paramMaxLength - the paramMaxLength
	* @param params - the params
	* */
	@SuppressWarnings("unchecked")
	private static String handleParams(int paramMaxLength, Object params) {

		if (params == null)
			return "null";

		StringBuffer paramSbf = new StringBuffer("");
		if (params instanceof List) {
			List<Object> listParams = (List<Object>) params;
			int size = listParams.size();
			for (int i = 0; i < size; i++) {
				Object param = listParams.get(i);
				String str = param + "";
				if (str.length() > paramMaxLength) {
					paramSbf.append(str.substring(0, paramMaxLength) + "...");
				} else {
					paramSbf.append(str);
				}
				if (i < size - 1) {
					paramSbf.append(";");
				}
			}
		} else {
			paramSbf.append(params);
		}
		return replaceToSigleLine(paramSbf.toString());
	}

	/*For Sync DataSource**/
	/**
	* For database replication - executes the statement on the master connection
	* and returns whether the execution succeeded (thrown when connCheck requires).
	* @param masterConn - the master connection to execute on
	* @param seq - execution sequence number
	* @param sql - the sql statement
	* @param params - statement parameters
	* @param returnResult - when true the boolean result is returned to the caller
	* */
	protected static void callAndReturnBooleanSync(Connection masterConn, long seq, String sql, List<Object> params,
			boolean returnResult) throws SQLException {
		if (masterConn instanceof DriverConnection) {
			openSyncConnection(masterConn);

			DriverConnection masterDriverConn = (DriverConnection) masterConn;
			Integer masterConnHashCode = masterDriverConn.getOriginConnectionHashCode();
			ConfigProperties ddscp = masterDriverConn.getDriverDataSource().getConfigProperties();
			boolean connCheck = true;
			boolean returnCheck = true;
			if (ddscp != null) {
				connCheck = ddscp.getBoolean("SyncConnectionCheck", true);
				returnCheck = ddscp.getBoolean("SyncReturnCheck", true);
			}
			List<DriverExecutor> deList = SYNC_EXECUTOR_MARK.get(masterConnHashCode);
			if (deList != null) {
				for (DriverExecutor de : deList) {
					try {
						boolean resultSync = de.callAndReturnBoolean(sql, params);
						if (returnCheck) {
							if (resultSync != returnResult) {
								writeSqlLog(de.hashCode(),masterDriverConn, "diffed", seq, "diffed", String.format("%s-%s",
										de.getDataSourceName(), de.getOriginConnectionHashCode()));
								int errorCode = 99906;
								String errorMsg = String.format("The returned results are inconsistent '%s-%s'.",
										de.getDataSourceName(), de.getOriginConnectionHashCode());
								throw new SQLException(errorMsg) {
									/*
									* Performs the getErrorCode operation
									* @return the getErrorCode result
									* */
									@Override
									public int getErrorCode() {
										return errorCode;
									}
								};
							}
						}
					} catch (SQLException e) {
						LoggerFactory.getLogger(DriverExecutor.class)
								.warn(String.format("ERROR CODE: (%s) %s", e.getErrorCode(), e.getMessage()));
						if (connCheck)
							throw e;
					}
				}
			}
		}
	}

	/**
	* For database replication - executes the statement on the master connection
	* and hands the result rows back through the driver executor callbacks.
	* @param masterConn - the master connection to execute on
	* @param cursorStart - JDBC result cursor start index
	* @param maxRows - JDBC result max rows
	* @param sql - the sql statement
	* @param params - statement parameters
	* */
	protected static void callAndReturnListSync(Connection masterConn, int cursorStart, int maxRows, String sql,
			List<Object> params) throws SQLException {
		if (masterConn instanceof DriverConnection) {
			openSyncConnection(masterConn);

			DriverConnection masterDriverConn = (DriverConnection) masterConn;
			Integer masterConnHashCode = masterDriverConn.getOriginConnectionHashCode();

			List<DriverExecutor> deList = SYNC_EXECUTOR_MARK.get(masterConnHashCode);
			if (deList != null) {
				for (DriverExecutor de : deList) {
					try {
						de.callAndReturnBoolean(sql, params);
					} catch (SQLException e) {
						LoggerFactory.getLogger(DriverExecutor.class)
								.warn(String.format("ERROR CODE: (%s) %s", e.getErrorCode(), e.getMessage()));
						ConfigProperties ddscp = masterDriverConn.getDriverDataSource().getConfigProperties();
						boolean connCheck = true;
						if (ddscp != null) {
							connCheck = ddscp.getBoolean("SyncConnectionCheck", true);
						}
						if (connCheck)
							throw e;
					}
				}
			}
		}
	}

	/**
	* For database replication - executes the statement on the master connection
	* and returns the affected row count.
	* @param masterConn - the master connection to execute on
	* @param seq - execution sequence number
	* @param sql - the sql statement
	* @param params - statement parameters
	* @param returnRows - when true the affected row count is returned to the caller
	* */
	protected static void callAndReturnRowsSync(Connection masterConn, long seq, String sql, List<Object> params,
			Object returnRows) throws SQLException {
		if (masterConn instanceof DriverConnection) {
			openSyncConnection(masterConn);

			DriverConnection masterDriverConn = (DriverConnection) masterConn;
			Integer masterConnHashCode = masterDriverConn.getOriginConnectionHashCode();
			ConfigProperties ddscp = masterDriverConn.getDriverDataSource().getConfigProperties();
			boolean connCheck = true;
			boolean returnCheck = true;
			if (ddscp != null) {
				connCheck = ddscp.getBoolean("SyncConnectionCheck", true);
				returnCheck = ddscp.getBoolean("SyncReturnCheck", true);
			}
			List<DriverExecutor> deList = SYNC_EXECUTOR_MARK.get(masterConnHashCode);
			if (deList != null) {
				for (DriverExecutor de : deList) {
					try {
						Object rowSync = de.callAndReturnRows(sql, params);
						if (returnCheck) {
							if (rowSync != null && rowSync.equals(returnRows)) {
								writeSqlLog(de.hashCode(),masterDriverConn, "diffed", seq, "diffed", String.format("%s-%s",
										de.getDataSourceName(), de.getOriginConnectionHashCode()));
								int errorCode = 99906;
								String errorMsg = String.format("The returned results are inconsistent '%s-%s'.",
										de.getDataSourceName(), de.getOriginConnectionHashCode());
								throw new SQLException(errorMsg) {
									/*
									* Performs the getErrorCode operation
									* @return the getErrorCode result
									* */
									@Override
									public int getErrorCode() {
										return errorCode;
									}
								};
							}
						}
					} catch (SQLException e) {
						LoggerFactory.getLogger(DriverExecutor.class)
								.warn(String.format("ERROR CODE: (%s) %s", e.getErrorCode(), e.getMessage()));
						if (connCheck)
							throw e;
					}
				}
			}
		}
	}

	/**
	* For database replication - executes a batch of record lists on the master
	* connection and returns the total affected row count.
	* @param masterConn - the master connection to execute on
	* @param seq - execution sequence number
	* @param sql - the sql statement
	* @param records - batch rows (each list is one parameter set)
	* @param returnRows - when true the affected row count is returned to the caller
	* */
	protected static void executeBatchListSync(Connection masterConn, long seq, String sql, List<List<Object>> records,
			int returnRows) throws SQLException {
		if (masterConn instanceof DriverConnection) {
			openSyncConnection(masterConn);

			DriverConnection masterDriverConn = (DriverConnection) masterConn;
			Integer masterConnHashCode = masterDriverConn.getOriginConnectionHashCode();
			ConfigProperties ddscp = masterDriverConn.getDriverDataSource().getConfigProperties();
			boolean connCheck = true;
			boolean returnCheck = true;
			if (ddscp != null) {
				connCheck = ddscp.getBoolean("SyncConnectionCheck", true);
				returnCheck = ddscp.getBoolean("SyncReturnCheck", true);
			}
			List<DriverExecutor> deList = SYNC_EXECUTOR_MARK.get(masterConnHashCode);
			if (deList != null) {
				for (DriverExecutor de : deList) {
					try {
						int rowSync = de.executeBatchList(sql, records);
						if (returnCheck) {
							if (rowSync != returnRows) {
								writeSqlLog(de.hashCode(),masterDriverConn, "diffed", seq, "diffed", String.format("%s-%s",
										de.getDataSourceName(), de.getOriginConnectionHashCode()));
								int errorCode = 99906;
								String errorMsg = String.format("The returned results are inconsistent '%s-%s'.",
										de.getDataSourceName(), de.getOriginConnectionHashCode());
								throw new SQLException(errorMsg) {
									/*
									* Performs the getErrorCode operation
									* @return the getErrorCode result
									* */
									@Override
									public int getErrorCode() {
										return errorCode;
									}
								};
							}
						}
					} catch (SQLException e) {
						LoggerFactory.getLogger(DriverExecutor.class)
								.warn(String.format("ERROR CODE: (%s) %s", e.getErrorCode(), e.getMessage()));
						if (connCheck)
							throw e;
					}
				}
			}
		}
	}

	/**
	* For database replication
	* @param masterConn - the masterConn
	* @param seq - the seq
	* @param sql - the sql
	* @param params - the params
	* @param returnRows - the returnRows
	* */
	protected static void executeSync(Connection masterConn, long seq, String sql, List<Object> params, int returnRows)
			throws SQLException {
		if (masterConn instanceof DriverConnection) {
			openSyncConnection(masterConn);

			DriverConnection masterDriverConn = (DriverConnection) masterConn;
			Integer masterConnHashCode = masterDriverConn.getOriginConnectionHashCode();
			ConfigProperties ddscp = masterDriverConn.getDriverDataSource().getConfigProperties();
			boolean connCheck = true;
			boolean returnCheck = true;
			if (ddscp != null) {
				connCheck = ddscp.getBoolean("SyncConnectionCheck", true);
				returnCheck = ddscp.getBoolean("SyncReturnCheck", true);
			}
			List<DriverExecutor> deList = SYNC_EXECUTOR_MARK.get(masterConnHashCode);
			if (deList != null) {
				for (DriverExecutor de : deList) {
					try {
						int rowSync = de.execute(sql, params);
						if (returnCheck) {
							if (rowSync != returnRows) {
								writeSqlLog(de.hashCode(),masterDriverConn, "diffed", seq, "diffed", String.format("%s-%s",
										de.getDataSourceName(), de.getOriginConnectionHashCode()));
								int errorCode = 99906;
								String errorMsg = String.format("The returned results are inconsistent '%s-%s'.",
										de.getDataSourceName(), de.getOriginConnectionHashCode());
								throw new SQLException(errorMsg) {
									/*
									* Performs the getErrorCode operation
									* @return the getErrorCode result
									* */
									@Override
									public int getErrorCode() {
										return errorCode;
									}
								};
							}
						}
					} catch (SQLException e) {
						LoggerFactory.getLogger(DriverExecutor.class)
								.warn(String.format("ERROR CODE: (%s) %s", e.getErrorCode(), e.getMessage()));
						if (connCheck)
							throw e;
					}
				}
			}
		}
	}

	/**
	* For database replication
	* @param masterConn - the masterConn
	* */
	protected synchronized static boolean openSyncConnection(Connection masterConn) throws SQLException {
		if (masterConn instanceof DriverConnection) {

			DriverConnection masterDriverConn = (DriverConnection) masterConn;
			Integer masterConnHashCode = masterDriverConn.getOriginConnectionHashCode();

			boolean isExist = SYNC_EXECUTOR_MARK.containsKey(masterConnHashCode);

			if (isExist) {
				return false;
			} else {
				SYNC_EXECUTOR_MARK.put(masterConnHashCode, new ArrayList<DriverExecutor>());
			}

			DriverDataSource dds = masterDriverConn.getDriverDataSource();
			if (dds != null) {
				List<DriverDataSource> sdsl = dds.getSyncDataSourceList(dds.getName());
				if (sdsl != null) {

					if (sdsl.isEmpty())
						return false;

					for (DriverDataSource sds : sdsl) {

						Integer sdsHashCode = sds.hashCode();
						if (!SYNC_CONN_ERROR_TIME.containsKey(sdsHashCode)) {
							SYNC_CONN_ERROR_TIME.put(sdsHashCode, 0L);
						}

						Long syncConnErrorTime = SYNC_CONN_ERROR_TIME.get(sdsHashCode);
						if (syncConnErrorTime > 0) {
							ConfigProperties ddscp = dds.getConfigProperties();
							long connCheckMs = 10000L;
							if (ddscp != null) {
								connCheckMs = ddscp.getMilliSeconds("SyncConnectionCheckTime", 10000L);
							}
							boolean isSkipConn = syncConnErrorTime > 0
									&& (System.currentTimeMillis() - syncConnErrorTime) <= connCheckMs;
							if (isSkipConn) {
								continue;
							}
						}

						String masterFingerprint = dds.getFingerprint();
						String syncFingerprint = sds.getFingerprint();
						if (masterFingerprint.equalsIgnoreCase(syncFingerprint)) {
							LoggerFactory.getLogger(DriverExecutor.class)
									.warn("Skip sync reason 'same connection fingerprint'.");
							continue;
						}

						try {
							final Connection conn = sds.getConnection();
							if (conn == null) {
								int errorCode = 99904;
								String errorMsg = "Connection is null.";
								throw new SQLException(errorMsg) {
									/*
									* Performs the getErrorCode operation
									* @return the getErrorCode result
									* */
									@Override
									public int getErrorCode() {
										return errorCode;
									}
								};
							}
							conn.setAutoCommit(masterConn.getAutoCommit());
							SYNC_CONN_ERROR_TIME.put(sdsHashCode, 0L);
							SYNC_EXECUTOR_MARK.get(masterConnHashCode).add(new DriverExecutor(conn));
						} catch (SQLException e) {
							SYNC_CONN_ERROR_TIME.put(sdsHashCode, System.currentTimeMillis());
							LoggerFactory.getLogger(DriverExecutor.class)
									.warn(String.format("ERROR CODE: (%s) %s", e.getErrorCode(), e.getMessage()));
							ConfigProperties ddscp = dds.getConfigProperties();
							boolean connCheck = true;
							if (ddscp != null) {
								connCheck = ddscp.getBoolean("SyncConnectionCheck", true);
							}
							if (connCheck)
								throw e;
						}

					}

					return true;
				}
			}
		}
		return false;
	}

	/**
	* For database replication
	* @param masterConn - the masterConn
	* */
	protected static void closeSyncConnection(Connection masterConn) throws SQLException {
		if (masterConn instanceof DriverConnection) {
			DriverConnection masterDriverConn = (DriverConnection) masterConn;
			Integer masterConnHashCode = masterDriverConn.getOriginConnectionHashCode();

			List<DriverExecutor> deList = SYNC_EXECUTOR_MARK.get(masterConnHashCode);
			if (deList != null) {
				for (DriverExecutor de : deList) {
					try {
						de.close();
					} catch (SQLException e) {
						LoggerFactory.getLogger(DriverExecutor.class)
								.warn(String.format("ERROR CODE: (%s) %s", e.getErrorCode(), e.getMessage()));
						ConfigProperties ddscp = masterDriverConn.getDriverDataSource().getConfigProperties();
						boolean connCheck = true;
						if (ddscp != null) {
							connCheck = ddscp.getBoolean("SyncConnectionCheck", true);
						}
						if (connCheck)
							throw e;
					}
				}
				SYNC_EXECUTOR_MARK.remove(masterConnHashCode);
				LoggerFactory.getLogger(DriverExecutor.class).debug("CloseSyncConnection - masterConn '{}' finished.",
						masterConnHashCode);
			}
		}
	}

	/**
	* For database replication
	* @param masterConn - the masterConn
	* */
	protected static void commitSyncConnection(Connection masterConn) throws SQLException {
		if (masterConn instanceof DriverConnection) {
			DriverConnection masterDriverConn = (DriverConnection) masterConn;
			Integer masterConnHashCode = masterDriverConn.getOriginConnectionHashCode();

			List<DriverExecutor> deList = SYNC_EXECUTOR_MARK.get(masterConnHashCode);
			if (deList != null) {
				for (DriverExecutor de : deList) {
					try {
						de.commit();
					} catch (SQLException e) {
						LoggerFactory.getLogger(DriverExecutor.class)
								.warn(String.format("ERROR CODE: (%s) %s", e.getErrorCode(), e.getMessage()));
						ConfigProperties ddscp = masterDriverConn.getDriverDataSource().getConfigProperties();
						boolean connCheck = true;
						if (ddscp != null) {
							connCheck = ddscp.getBoolean("SyncConnectionCheck", true);
						}
						if (connCheck)
							throw e;
					}
				}
				LoggerFactory.getLogger(DriverExecutor.class).debug("CommitSyncConnection - masterConn '{}' finished.",
						masterConnHashCode);
			}
		}
	}

	/**
	* For database replication
	* @param masterConn - the masterConn
	* */
	protected static void rollbackSyncConnection(Connection masterConn) throws SQLException {
		if (masterConn instanceof DriverConnection) {
			DriverConnection masterDriverConn = (DriverConnection) masterConn;
			Integer masterConnHashCode = masterDriverConn.getOriginConnectionHashCode();

			List<DriverExecutor> deList = SYNC_EXECUTOR_MARK.get(masterConnHashCode);
			if (deList != null) {
				for (DriverExecutor de : deList) {
					try {
						de.rollback();
					} catch (SQLException e) {
						LoggerFactory.getLogger(DriverExecutor.class)
								.warn(String.format("ERROR CODE: (%s) %s", e.getErrorCode(), e.getMessage()));
						ConfigProperties ddscp = masterDriverConn.getDriverDataSource().getConfigProperties();
						boolean connCheck = true;
						if (ddscp != null) {
							connCheck = ddscp.getBoolean("SyncConnectionCheck", true);
						}
						if (connCheck)
							throw e;
					}
				}
				LoggerFactory.getLogger(DriverExecutor.class)
						.debug("RollbackSyncConnection - masterConn '{}' finished.", masterConnHashCode);
			}
		}
	}

	/**
	* For database replication
	* @param masterConn - the masterConn
	* */
	protected static void abortSyncConnection(Connection masterConn) throws SQLException {
		if (masterConn instanceof DriverConnection) {
			DriverConnection masterDriverConn = (DriverConnection) masterConn;
			Integer masterConnHashCode = masterDriverConn.getOriginConnectionHashCode();

			List<DriverExecutor> deList = SYNC_EXECUTOR_MARK.get(masterConnHashCode);
			if (deList != null) {
				for (DriverExecutor de : deList) {
					try {
						de.abort();
					} catch (SQLException e) {
						LoggerFactory.getLogger(DriverExecutor.class)
								.warn(String.format("ERROR CODE: (%s) %s", e.getErrorCode(), e.getMessage()));
						ConfigProperties ddscp = masterDriverConn.getDriverDataSource().getConfigProperties();
						boolean connCheck = true;
						if (ddscp != null) {
							connCheck = ddscp.getBoolean("SyncConnectionCheck", true);
						}
						if (connCheck)
							throw e;
					}
				}
				LoggerFactory.getLogger(DriverExecutor.class).debug("AbortSyncConnection - masterConn '{}' finished.",
						masterConnHashCode);
			}
		}
	}

}
