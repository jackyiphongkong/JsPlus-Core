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

import java.io.File;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.datasource.DriverDataSource;
import com.killcoding.tool.ConfigProperties;
import com.killcoding.tool.CommonTools;
import java.sql.Connection;
import java.sql.SQLException;
import com.killcoding.datasource.CacheDriverExecutor;
import java.util.Map;
import java.util.HashMap;
import com.killcoding.datasource.Clock;
import java.sql.Timestamp;
import java.util.concurrent.ConcurrentHashMap;

/**
*
CREATE TABLE identity_card (
id CHAR(16),
identity_name VARCHAR(200),
identity_value BIGINT,
generate_task_id CHAR(16),
created_user_id CHAR(16),
updated_user_id CHAR(16),
deleted_user_id CHAR(16),
created_at DATETIME(6),
updated_at DATETIME(6),
deleted_at DATETIME(6),
deleted TINYINT(1)
) ENGINE=InnoDB;

ALTER TABLE identity_card ADD PRIMARY KEY (id);
ALTER TABLE identity_card ADD UNIQUE INDEX idx_idcard_unique_idname (identity_name);
ALTER TABLE identity_card ADD INDEX idx_idcard_deleted (deleted);
*
* */

/**
* Distributed identity-card sequence generator: produces fixed-length sequential IDs with
* optional prefix / suffix / delimiter, persisted through a DB counter table so the next value
* is unique across JVM instances. Requires IdentityCard.properties (Enable, TableName, Timeout,
* StepValue, DataSource, GetAppClockSql). Invalid length configuration raises error 99801.
* */
public final class IdentityCard {
    
    private final static Map<String, Integer> CURRENT_VALUES = new ConcurrentHashMap<String, Integer>();
    private final static Map<String, Long> STEP_LAST_VALUES = new ConcurrentHashMap<String, Long>();

	private static final String SQL_INSERT = "INSERT INTO %s (id,identity_name,identity_value,generate_task_id,created_user_id,updated_user_id,deleted_user_id,created_at,updated_at,deleted_at,deleted) VALUES (:id,:identity_name,:identity_value,:generate_task_id,:created_user_id,:updated_user_id,NULL,:created_at,:updated_at,:deleted_at,:deleted)";

	private static final String SQL_UPDATE = "UPDATE %s SET identity_value = identity_value + :step_value,generate_task_id = :generate_task_id,updated_user_id = :updated_user_id,updated_at = :updated_at WHERE id = :id AND (generate_task_id = '0' OR updated_at <= :timeout_updated_at)";

	private static final String SQL_RESET = "UPDATE %s SET identity_value = :identity_value,updated_user_id = :updated_user_id,updated_at = :updated_at WHERE id = :id AND generate_task_id = :generate_task_id";

	private static final String SQL_RESET_FORCE = "UPDATE %s SET identity_value = :identity_value,updated_user_id = :updated_user_id,updated_at = :updated_at WHERE id = :id";

	private static final String SQL_CLEAR = "UPDATE %s SET generate_task_id = '0',updated_user_id = :updated_user_id,updated_at = :updated_at WHERE id = :id AND generate_task_id = :generate_task_id";

	private static final String SQL_FIRST = "SELECT * FROM %s WHERE deleted = 0 AND identity_name = :identity_name";

	private static final String SQL_FIRST_BY_GENERATE_TASK_ID = "SELECT * FROM %s WHERE deleted = 0 AND identity_name = :identity_name AND generate_task_id = :generate_task_id";

	public static volatile File APP_IDENTITY_CARD = null;

	private Logger log = LoggerFactory.getLogger(IdentityCard.class);

	private CacheDriverExecutor driverExecutor = null;
	
	private static volatile Long MAX_VALUE_LIMITED = Long.MAX_VALUE - 1;
	private static volatile Long MIN_VALUE_LIMITED = 1L;
	private static volatile Integer STEP_VALUE_LIMITED = 0;

	private static volatile boolean enable = false;
	private static volatile ConfigProperties configProperties = null;
	private static volatile DriverDataSource dataSource = null;
	private static volatile String dataSourcePath = null;
	private static volatile String tableName = null;
	private static volatile Long timeout = 10000L;
	private static volatile Long maxValue = MAX_VALUE_LIMITED;
	private static volatile Long minValue = MIN_VALUE_LIMITED;
	private static volatile Integer stepValue = STEP_VALUE_LIMITED;

	private String identityName = null;
	private String generateTaskId = null;
	private Object userId = null;

	/**
	* Constructs a new IdentityCard
	* @param identityName - the identityName
	* */
	public IdentityCard(String identityName) {
		super();
		this.identityName = identityName;
        initValues();
		init();
	}
	
	/**
	* Performs the initValues operation
	* */
	private synchronized void initValues(){
	    if(!CURRENT_VALUES.containsKey(identityName)){
    		CURRENT_VALUES.put(identityName,0);
    		STEP_LAST_VALUES.put(identityName,0L);
	    }
	}

	/**
	* Performs the initConn operation
	* */
	private void initConn() throws SQLException {
		Connection conn = dataSource.getConnection();
		conn.setAutoCommit(false);
		driverExecutor = new CacheDriverExecutor(conn);
	}
	
	/**
	* Performs the generateId operation
	* @return the generateId result
	* */
	public synchronized Long generateId() throws SQLException {
	    if (!enable)
			return null;
		
		if(CURRENT_VALUES.get(identityName) <= 0){	
		    STEP_LAST_VALUES.put(identityName,generateIdForNextStep());
		    CURRENT_VALUES.put(identityName,stepValue);
		}
		
		Long val = STEP_LAST_VALUES.get(identityName) - CURRENT_VALUES.get(identityName);
		CURRENT_VALUES.put(identityName,CURRENT_VALUES.get(identityName) - 1);
		return val;
	}

	/**
	* Performs the generateIdForNextStep operation
	* @return the generateIdForNextStep result
	* */
	public synchronized Long generateIdForNextStep() throws SQLException {

		if (!enable)
			return null;

		Long nextValue = null;
		try {
			initConn();
			Object id = null;
			generateTaskId = CommonTools.generateId(16);
			Map<String, Object> item = first();
			if (item == null) {
				id = create();
				item = first();
			}
			id = item.get("id");
			boolean updated = update(id);
			if (updated) {
				item = firstByGenerateTaskId();
				nextValue = Long.parseLong(item.get("identity_value").toString());
			}
			if (nextValue != null) {
				if (nextValue > maxValue) {
				    nextValue = minValue + stepValue;
					reset(id,nextValue);
				}
			}
			clear(id);
			driverExecutor.commit();
			if (nextValue == null) {
				int errorCode = 99800;
				String errorMsg = String.format("The '%s' next value is null.", identityName);
				log.warn(String.format("ERROR CODE: (%s) %s", errorCode, errorMsg));
				throw new SQLException(errorMsg) {
					@Override

					/**
					* Performs the getErrorCode operation
					* @return the getErrorCode result
					* */
					public int getErrorCode() {
						return errorCode;
					}
				};
			}
			return nextValue;
		} catch (Exception e) {
			log.warn(e.getMessage(), e);

			if (driverExecutor != null)
				driverExecutor.rollback();
				
    		int errorCode = 99802;
    		String errorMsg = e.getMessage();
    		log.warn(String.format("ERROR CODE: (%s) %s", errorCode, errorMsg));
    		throw new SQLException(errorMsg) {
    			@Override

    			/**
    			* Performs the getErrorCode operation
    			* @return the getErrorCode result
    			* */
    			public int getErrorCode() {
    				return errorCode;
    			}
    		};				

		} finally {
			if (driverExecutor != null){
				driverExecutor.close();
			}
		}
	}

	/**
	* Performs the generateId operation
	* @param length - the length
	* @return the generateId result
	* */
	public synchronized String generateId(int length) throws SQLException {
		if (!enable)
			return null;
			
		if(length < 1){
    		int errorCode = 99801;
    		String errorMsg = String.format("The value length '%s' cannot be less than 1.", length);
    		log.warn(String.format("ERROR CODE: (%s) %s", errorCode, errorMsg));
    		throw new SQLException(errorMsg) {
    			@Override

    			/**
    			* Performs the getErrorCode operation
    			* @return the getErrorCode result
    			* */
    			public int getErrorCode() {
    				return errorCode;
    			}
    		};	
		}
		
		Long val = generateId();
		if(val > getMaxValueForLength(length)){
		    Map<String,Object> item = first();
			Object id = item.get("id");
		    resetForce(id);
		    val = minValue;
		}
		if (val == null)
			return null;

		return String.format("%0" + length + "d", val);
	}

	/**
	* Performs the generateId operation
	* @param length - the length
	* @param prefix - the prefix
	* @return the generateId result
	* */
	public synchronized String generateId(int length, String prefix) throws SQLException {
		if (!enable)
			return null;
			
		int valLen = length - prefix.length();
		return String.format("%s%s", prefix, generateId(valLen));
	}
	
	/**
	* Performs the generateId operation
	* @param length - the length
	* @param prefix - the prefix
	* @param delimiter - the delimiter
	* @param randomSuffixLength - the randomSuffixLength
	* @return the generateId result
	* */
	public synchronized String generateId(int length, String prefix, String delimiter,int randomSuffixLength) throws SQLException {
		if (!enable)
			return null;
			
	    if(CommonTools.isBlank(delimiter)){
	        delimiter = "";
	    }
	    String suffix = "";
	    if(randomSuffixLength > 0){
	        suffix = String.format("%s%s",delimiter,CommonTools.generateId(randomSuffixLength).toUpperCase());
	    }
	    int valLen = length - prefix.length() - delimiter.length() - suffix.length();
	    return String.format("%s%s%s%s", prefix,delimiter,generateId(valLen),suffix).substring(0,length);
	}

	/**
	* Performs the init operation
	* */
	private synchronized void init() {
		if (dataSource == null) {
			try {
				if (APP_IDENTITY_CARD == null) {
					APP_IDENTITY_CARD = CommonTools.getSystemProperties(IdentityCard.class, "APP_IDENTITY_CARD",
							"IdentityCard.properties");
				}
				if (APP_IDENTITY_CARD != null && APP_IDENTITY_CARD.exists() && configProperties == null) {
					configProperties = new ConfigProperties();
					Runnable updatedRun = new Runnable() {
						@Override

						/**
						* Performs the run operation
						* */
						public void run() {
							try {
								reload();
								Logger.systemMark(IdentityCard.class, "IdentityCard Reloaded");
							} catch (Exception e) {
								log.error(e.getMessage(), e);
							}
						}
					};

					configProperties.load(APP_IDENTITY_CARD, updatedRun);
					enable = configProperties.getBoolean("Enable", false);
					tableName = configProperties.getString("TableName", "identity_card");
					timeout = configProperties.getMilliSeconds("Timeout", 10000L);
					maxValue = configProperties.getLong("MaxValue", MAX_VALUE_LIMITED);
					minValue = configProperties.getLong("MinValue", MIN_VALUE_LIMITED);
					stepValue = configProperties.getInteger("StepValue", STEP_VALUE_LIMITED);
					
        			if(maxValue > MAX_VALUE_LIMITED) maxValue = MAX_VALUE_LIMITED;
        			
        			if(minValue < MIN_VALUE_LIMITED) minValue = MIN_VALUE_LIMITED;
        			
        			if(stepValue < STEP_VALUE_LIMITED) stepValue = STEP_VALUE_LIMITED;
        			
					if (enable) {
						dataSourcePath = configProperties.getString("DataSource");
						if (!CommonTools.isBlank(dataSourcePath)) {
							dataSource = new DriverDataSource(new File(dataSourcePath));
						}
					}
				}
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the reload operation
	* */
	private void reload() {
		if (configProperties != null) {
			enable = configProperties.getBoolean("Enable", false);
			tableName = configProperties.getString("TableName", "identity_card");
			timeout = configProperties.getMilliSeconds("Timeout", 10000L);
			String reloadDataSourcePath = configProperties.getString("DataSource");
			
			maxValue = configProperties.getLong("MaxValue", MAX_VALUE_LIMITED);
			minValue = configProperties.getLong("MinValue", MIN_VALUE_LIMITED);
			stepValue = configProperties.getInteger("StepValue", STEP_VALUE_LIMITED);
			
			if(maxValue > MAX_VALUE_LIMITED) maxValue = MAX_VALUE_LIMITED;
			
			if(minValue < MIN_VALUE_LIMITED) minValue = MIN_VALUE_LIMITED;
			
			if(stepValue < STEP_VALUE_LIMITED) stepValue = STEP_VALUE_LIMITED;			

			boolean isReload = false;
			if (!CommonTools.isBlank(reloadDataSourcePath)) {
				isReload = !dataSourcePath.equals(reloadDataSourcePath);
			}
			if (dataSource != null && isReload) {
			    // May be referenced when expansion is needed
				// dataSource.closeAll();
				dataSourcePath = reloadDataSourcePath;
			}
			// May be referenced when expansion is needed
			// if (!enable && dataSource != null) {
			// 	 dataSource.closeAll();
			// }
			if (enable && isReload) {
				if (!CommonTools.isBlank(dataSourcePath)) {
					dataSource = new DriverDataSource(new File(dataSourcePath));
				}
			}
		}
	}

	/**
	* Performs the update operation
	* @param id - the id
	* @return the update result
	* */
	private boolean update(Object id) throws SQLException {
		Clock clock = new Clock();
		Timestamp currentTime = clock.getSqlTimestamp();
		String sql = formatSql(SQL_UPDATE);
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("id", id);
        params.put("step_value",stepValue);
		params.put("generate_task_id", generateTaskId);
		params.put("updated_user_id", userId);
		params.put("updated_at", currentTime);
		params.put("timeout_updated_at", new Timestamp(currentTime.getTime() - timeout));
		return driverExecutor.execute(sql, params) > 0;
	}

	/**
	* Performs the clear operation
	* @param id - the id
	* */
	private void clear(Object id) throws SQLException {
		Clock clock = new Clock();
		Timestamp currentTime = clock.getSqlTimestamp();
		String sql = formatSql(SQL_CLEAR);
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("id", id);
		params.put("generate_task_id", generateTaskId);
		params.put("updated_user_id", userId);
		params.put("updated_at", currentTime);
		driverExecutor.execute(sql, params);
	}

	/**
	* Performs the reset operation
	* @param id - the id
	* @param resetValue - the resetValue
	* */
	private void reset(Object id,Long resetValue) throws SQLException {
		Clock clock = new Clock();
		Timestamp currentTime = clock.getSqlTimestamp();
		String sql = formatSql(SQL_RESET);
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("id", id);
		params.put("identity_value", resetValue);
		params.put("generate_task_id", generateTaskId);
		params.put("updated_user_id", userId);
		params.put("updated_at", currentTime);
		driverExecutor.execute(sql, params);
	}
	
	/**
	* Performs the resetForce operation
	* @param id - the id
	* */
	private void resetForce(Object id) throws SQLException {
		Clock clock = new Clock();
		Timestamp currentTime = clock.getSqlTimestamp();
		String sql = formatSql(SQL_RESET_FORCE);
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("id", id);
		params.put("identity_value", minValue);
		params.put("updated_user_id", userId);
		params.put("updated_at", currentTime);
		driverExecutor.execute(sql, params);
	}	

	/**
	* Performs the create operation
	* @return the create result
	* */
	private Object create() throws SQLException {
		Object id = CommonTools.generateId(16);
		Clock clock = new Clock();
		Timestamp currentTime = clock.getSqlTimestamp();
		Long createIdentityValue = minValue;
		
		if(createIdentityValue < 0)  createIdentityValue = 0L;
		
		String sql = formatSql(SQL_INSERT);
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("id", id);
		params.put("identity_name", identityName);
		params.put("identity_value", createIdentityValue);
		params.put("generate_task_id", "0");
		params.put("created_user_id", userId);
		params.put("updated_user_id", userId);
		params.put("deleted_user_id", null);
		params.put("created_at", currentTime);
		params.put("updated_at", currentTime);
		params.put("deleted_at", null);
		params.put("deleted", 0);
		driverExecutor.execute(sql, params);
		return id;
	}

	private Map<String, Object> first() throws SQLException {
		String sql = formatSql(SQL_FIRST);
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("identity_name", identityName);
		return driverExecutor.first(sql, params);
	}

	private Map<String, Object> firstByGenerateTaskId() throws SQLException {
		String sql = formatSql(SQL_FIRST_BY_GENERATE_TASK_ID);
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("identity_name", identityName);
		params.put("generate_task_id", generateTaskId);
		return driverExecutor.first(sql, params);
	}
	
	/**
	* Performs the getMaxValueForLength operation
	* @param length - the length
	* @return the getMaxValueForLength result
	* */
	private Long getMaxValueForLength(int length) {
	    String strValue = String.format("%0" + length + "d",0).replaceAll("0","9");
	    return Long.parseLong(strValue);
	}

	/**
	* Performs the getDataSource operation
	* @return the getDataSource result
	* */
	public static DriverDataSource getDataSource() {
		return dataSource;
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
	* Performs the formatSql operation
	* @param sql - the sql
	* @param stepValue - the stepValue
	* @return the formatSql result
	* */
	public String formatSql(String sql,Integer stepValue) {
		return String.format(sql, tableName,stepValue);
	}	

	/**
	* Performs the setUserId operation
	* @param userId - the userId
	* */
	public void setUserId(Object userId) {
		this.userId = userId;
	}

	/**
	* Performs the getUserId operation
	* @return the getUserId result
	* */
	public Object getUserId() {
		return this.userId;
	}

	/**
	* Performs the getIdentityName operation
	* @return the getIdentityName result
	* */
	public String getIdentityName() {
		return identityName;
	}
	
	/**
	* Performs the getConfigProperties operation
	* @return the getConfigProperties result
	* */
	public static ConfigProperties getConfigProperties(){
	    return configProperties;
	}	
}