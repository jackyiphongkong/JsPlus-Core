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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ConnectionBuilder;
import java.sql.SQLException;
import javax.sql.DataSource;
import java.sql.SQLFeatureNotSupportedException;

import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.io.File;
import java.net.URLClassLoader;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.sql.Driver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.DriverManager;
import java.util.Iterator;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import com.killcoding.tool.ConfigProperties;
import com.killcoding.tool.FileTools;

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.ExecutorService;
import com.killcoding.tool.CommonTools;
import java.text.SimpleDateFormat;

import java.util.Comparator;
import java.util.concurrent.Callable;


import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;

/**
*  Implement and enhance the datasource class
*  Support write connection status log
*  Support abort tiemout connection
*  Support failover
* */
public final class DriverDataSource implements DataSource {

	private final Logger log = LoggerFactory.getLogger(DriverDataSource.class);

	private String DATASOURCE_ID = null;
	private final long PID = ProcessHandle.current().pid();

	private long countCreatedConnection = 0L;
	private long countAbortedIdle = 0L;
	private long countAbortedTimeout = 0L;
	private long countAbortedLifeCycle = 0L;
	private long countAbortedInvalid = 0L;
	private long countAbortedCloseFailed = 0L;
	private long countClosed = 0L;
	private volatile double maxUsage = 0.0D;

	private final Map<String, Driver> DRIVER_MARK = new ConcurrentHashMap<String, Driver>();
	private final Map<Integer, Long> CONN_ERROR_TIME = new ConcurrentHashMap<Integer, Long>();

	private volatile boolean inited = false;

	private Boolean enableStatusLog = false;
	private Boolean enableFullStatusLog = true;
	private Integer statusLogArchiveDays = 30;
	private String statusLogFolder = null;
	private File statusLogFile = null;
	private Integer maxAveUsageSize = 10000;

    private final List<Double> aveUsageSum = new ArrayList<Double>();
	private final List<String> urlList = new ArrayList<String>();

	private int urlIndex = 0;

	private Integer maxPoolSize = Integer.MAX_VALUE;
	private Integer minPoolSize = 0;
	private Long lifeCycle = Long.MAX_VALUE;
	private Long idleTimeout = 60L;
	private Long timeout = 0L;
	private Long transactionTimeout = 0L;
	private Long refreshPoolTimer = 1000L;
	private Long testValidTimeout = 5L;

	private ConfigProperties configProperties = null;
	private String driverJar;
	private String connProps;
	private String driverClassName;
	private String url;
	private String username;
	private String password;
	private String name = "DriverDataSource";
	private String abortMode = "abort";
	private String appDataSourcePath;

	private boolean useExternalConfig = false;
	private boolean loadedConfig = false;
	private boolean closed = false;

	private File appDataSource = null;
	private String owner = null;
	private List<DriverDataSource> syncDriverDataSource = null;

	private final ConnectionPool connectionPool = new ConnectionPool();
	private final ExecutorService connectionPoolExecutor = Executors.newFixedThreadPool(1);

	private long dynamicReloadConfigTime = System.currentTimeMillis();

	/**
	 * New a DriverDataSource object
	 * Use env APP_DATASOURCE=/path/DataSource.properties
	 * */
	public DriverDataSource() {
		super();
		this.appDataSource = CommonTools.getSystemProperties(DriverDataSource.class, "APP_DATASOURCE",
				"DataSource.properties");
		if (this.appDataSource == null) {
			this.appDataSource = CommonTools.getSystemProperties(DriverDataSource.class, "APP_DATASOURCE",
					"Datasource.properties");
		}

		if (this.appDataSource != null) {
			this.appDataSourcePath = this.appDataSource.getAbsolutePath();
		}

		initDs();
		reloadConfig();
	}

	/**
	 * New a DriverDataSource object
	 * @param appDataSource - It is file object (e.g. /your_path/DataSource.properties)
	 * */
	public DriverDataSource(File appDataSource) {
		super();
		this.appDataSource = appDataSource;

		if (this.appDataSource != null) {
		    if(!this.appDataSource.exists()){
		        this.appDataSourcePath = this.appDataSource.getAbsolutePath();
		        Logger.systemError(DriverDataSource.class, "The '{}' does not exists.",this.appDataSourcePath);
		    }

		}

		initDs();
		reloadConfig();
	}

	/**
	 * New a DriverDataSource object
	 * @param configPropertiesContent - It is properties context
	 * */
	public DriverDataSource(String configPropertiesContent) throws IOException {
		super();
		initDs();
		setConfig(configPropertiesContent);
	}

	/**
	 * New a DriverDataSource object
	 * @param configProperties - It is ConfigProperties object
	 * */
	public DriverDataSource(ConfigProperties configProperties) throws IOException {
		super();
		initDs();
		setConfig(configProperties);
	}

	/**
	* Performs the initDs operation
	* */
	private void initDs() {
		SimpleDateFormat datasourceIdFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		DATASOURCE_ID = String.format("%s.%s.%s", datasourceIdFormat.format(new Date()), PID,
				CommonTools.generateId(4).toUpperCase());
	}

	/**
	 * Set app datasource properties file path
	 * @param appDataSourcePath - file path
	 * */
	public void setAppDataSourcePath(String appDataSourcePath) {
		if (!CommonTools.isBlank(appDataSourcePath)) {
			this.appDataSource = new File(appDataSourcePath);
			this.appDataSourcePath = this.appDataSource.getAbsolutePath();
		}
	}

	/**
	 * Get app datasource properties file path
	 * */
	public String getAppDataSourcePath() {
		return this.appDataSourcePath;
	}

	/**
	 * Check is loaded config
	 * @return boolean
	 * */
	public boolean isLoadedConfig() {
		return loadedConfig;
	}

	/**
	 * Set enable status log
	 * @param enableStatusLog - 'true' is enable write status log
	 * */
	public void setEnableStatusLog(Boolean enableStatusLog) {
		this.enableStatusLog = enableStatusLog == null ? false : enableStatusLog;
	}

	/**
	 * Set enable full status log
	 * @param enableFullStatusLog - 'true' is enable write status log when full connections
	 * */
	public void setEnableFullStatusLog(Boolean enableFullStatusLog) {
		this.enableFullStatusLog = enableFullStatusLog == null ? true : enableFullStatusLog;
	}

	/**
	 * Set status log archive days
	 * @param  statusLogArchiveDays - Delete before x days log
	 * */
	public void setStatusLogArchiveDays(Integer statusLogArchiveDays) {
		this.statusLogArchiveDays = statusLogArchiveDays == null ? 30 : statusLogArchiveDays;
	}

	/**
	 * Set status log target folder
	 * @param _statusLogFolder - Target folder
	 * */
	public void setStatusLogFolder(String _statusLogFolder) {
		this.statusLogFolder = CommonTools.isBlank(_statusLogFolder) ? null : _statusLogFolder;
		if (this.statusLogFolder == null) {
			statusLogFile = new File(String.format("%s/DriverDataSource_Status/%s.log",
					CommonTools.getJarPath(DriverDataSource.class), DATASOURCE_ID));
		} else {
			statusLogFile = new File(String.format("%s/%s.log", statusLogFolder, DATASOURCE_ID));
		}
	}

	/**
	 * Set connection life cycle
	 * @param lifeCycle - Unit is ms
	 * */
	public void setLifeCycle(Long lifeCycle) {
		this.lifeCycle = (lifeCycle == null ? Long.MAX_VALUE : lifeCycle);
	}

	/**
	 * Get connection life cycle
	 * @return Long - Unit is ms
	 * */
	public Long getLifeCycle() {
		return this.lifeCycle;
	}

	/**
	 * Set refresh pool timer
	 * @param refreshPoolTimer - Unit is ms
	 * */
	public void setRefreshPoolTimer(Long refreshPoolTimer) {
		this.refreshPoolTimer = (refreshPoolTimer == null ? 1000L : refreshPoolTimer);
	}

	/**
	 * Get refresh pool timer
	 * @return Long - Unit is ms
	 * */
	public Long getRefreshPoolTimer() {
		return this.refreshPoolTimer;
	}

	/**
	 * Set test valid timeout
	 * @param testValidTimeout Unit is seconds,default is 5s
	 * */
	public void setTestValidTimeout(Long testValidTimeout) {
		this.testValidTimeout = (testValidTimeout == null ? 5L : testValidTimeout);
	}

	/**
	 * Get test valid timeout
	 * @return Long - Unit is seconds
	 * */
	public Long getTestValidTimeout() {
		return this.testValidTimeout;
	}

	/**
	 * Set idle timeout
	 * @param idleTimeout - Unit is seconds, default is 60s
	 * */
	public void setIdleTimeout(Long idleTimeout) {
		this.idleTimeout = (idleTimeout == null ? 60L : idleTimeout);
	}

	/**
	 * Get idle timeout
	 * @return Long - Unit is seconds
	 * */
	public Long getIdleTimeout() {
		return this.idleTimeout;
	}

	/**
	 * Set execute timeout
	 * Default is 0s
	 * Disabled timeou if value is 0s
	 * @param timeout - Unit is seconds,
	 * */
	public void setTimeout(Long timeout) {
		this.timeout = (timeout == null ? 0L : timeout);
	}

	/**
	 * Get execute timeout
	 * @return Long - Unit is seconds,
	 * */
	public Long getTimeout() {
		return this.timeout;
	}

	/**
	 * Set execute transaction timeout
	 * Default is 0s
	 * Disabled timeou if value is 0s
	 * @param transactionTimeout - Unit is seconds,
	 * */
	public void setTransactionTimeout(Long transactionTimeout) {
		this.transactionTimeout = (transactionTimeout == null ? 0L : transactionTimeout);
	}

	/**
	 * Get execute transaction timeout
	 * @return Long - Unit is seconds,
	 * */
	public Long getTransactionTimeout() {
		return this.transactionTimeout;
	}

	/**
	 * Set max connection pool size
	 * @param maxPoolSize
	 * */
	public void setMaxPoolSize(Integer maxPoolSize) {
		this.maxPoolSize = (maxPoolSize == null ? Integer.MAX_VALUE : maxPoolSize);
	}

	/**
	 * Get max connection pool size
	 * @return Integer
	 * */
	public Integer getMaxPoolSize() {
		return this.maxPoolSize;
	}

	/**
	 * Set jdbc driver jar path
	 * @param driverJar - Driver jar file or folder path
	 * */
	public void setDriverJar(String driverJar) {
		this.driverJar = driverJar;
	}

	/**
	 * Get jdbc driver jar path
	 * @return String
	 * */
	public String getDriverJar() {
		return this.driverJar;
	}

	/**
	 * Set connection properties
	 * @param connProps -  Sample: autoReconnection=true;key=value
	 * */
	public void setConnProps(String connProps) {
		this.connProps = connProps;
	}

	/**
	 * Get connection properties
	 * @return connProps -  Sample: autoReconnection=true;key=value
	 * */
	public String getConnProps() {
		return this.connProps;
	}

	/**
	 * Set driver class name
	 * @param driverClassName
	 * */
	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}

	/**
	 * Get driver class name
	 * @return String
	 * */
	public String getDriverClassName() {
		return this.driverClassName;
	}

	/**
	 * Set connection url
	 * @param urlOrList - String or List
	 * */
	@SuppressWarnings("unchecked")
	public void setUrl(Object urlOrList) {
		if (urlOrList instanceof String) {
			this.urlList.add((String) urlOrList);
			this.urlIndex = 0;
			this.url = this.urlList.get(this.urlIndex);
		}
		if (urlOrList instanceof List) {
			this.urlList.addAll((List) urlOrList);
			this.urlIndex = 0;
			this.url = this.urlList.get(this.urlIndex);
		}
	}

	/**
	 * Get connection url
	 * */
	public String getUrl() {
		return this.url;
	}

	/**
	 * Set min connection pool size
	 * @param minPoolSize
	 * */
	public void setMinPoolSize(Integer minPoolSize) {
		this.minPoolSize = (minPoolSize == null ? 0 : minPoolSize);
	}

	/**
	 * Get min connection pool size
	 * */
	public Integer getMinPoolSize() {
		return this.minPoolSize;
	}

	/**
	 * Set abort mode
	 * @param abortMode - 'close' or 'abort', default is 'abort'
	 * */
	public void setAbortMode(String abortMode) {
		if (abortMode == null)
			this.abortMode = "abort";
	}

	/**
	 * Get abort mode
	 * @return abortMode - 'close' or 'abort', default is 'abort'
	 * */
	public String getAbortMode() {
		return this.abortMode;
	}

	/**
	 * Set thread name
	 * @param name - It is thread name, default is 'DriverDataSource'
	 * */
	public void setName(String name) {
		if (name != null)
			this.name = name;
	}

	/**
	 * Get thread name
	 * @return String
	 * */
	public String getName() {
		return this.name;
	}

	/**
	 * Set database username
	 * @param username - Database username
	 * */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Set database password
	 * @param password - Database password
	 * */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	* Performs the getOpenConnectionSize operation
	* @return the getOpenConnectionSize result
	* */
	private Integer getOpenConnectionSize() {
		return connectionPool.getOpenConnectionSize();
	}


	/**
	 * Get connection pool size
	 * @return Integer
	 * */
	public ConnectionPool getConnectionPool() {
		return connectionPool;
	}

	/**
	 * Abort timeout connection
	 * Skip execute when 'timeout' is 0 or 'transactionTimeout' is 0
	 * */
	private void abortTimeoutConnection() {
		try {
			if (timeout > 0 || transactionTimeout > 0) {
				Date currentTime = new Date();
				List<Integer> abortList = new ArrayList<Integer>();
				Set<Integer> hashCodeList = connectionPool.getConnectionOpenedAtMark().keySet();
				Map<Integer,Date> usingAtMark = connectionPool.getConnectionUsingAtMark();
				for (Integer hashCode : hashCodeList) {
					Date openedAt = usingAtMark.get(hashCode);
					if (openedAt != null) {
						Connection conn = connectionPool.getConnectionMark().get(hashCode);

						if (conn == null) {
							connectionPool.removeConnectionUsingAtMark(hashCode);
							continue;
						}

                        try{
    						long timeoutSec = (currentTime.getTime() - openedAt.getTime()) / 1000;
    						Long _timeout = conn.getAutoCommit() ? timeout : transactionTimeout;

    						if (_timeout == 0)
    							continue;

    						if (timeoutSec >= _timeout) {
    							if (!abortList.contains(hashCode))
    								abortList.add(hashCode);
    						}
                        }catch(Exception ee){
                            log.warn(ee.getMessage(), ee);
							if (!abortList.contains(hashCode))
								abortList.add(hashCode);
                        }
					}
				}
				for (Integer hashCode : abortList) {
					countAbortedTimeout++;
					log.mark("AbortTimeoutConnection '{}', ConnectionPoolSize '{}', OpenConnectionSize '{}'.",hashCode,connectionPool.size(),getOpenConnectionSize());
					abort(hashCode);
				}
			}
		} catch (Exception e) {
			log.warn(e.getMessage(), e);
		}
	}

	/**
	 * Abort life cycle connection
	 * Skip execute when 'lifeCycle' is 0
	 * */
	private void abortLifeCycleEndedConnection() {
		if (lifeCycle > 0) {
			Date currentTime = new Date();
			List<Integer> abortList = new ArrayList<Integer>();
			Map<Integer,Date> openedAtMark = connectionPool.getConnectionOpenedAtMark();
			Set<Integer> hashCodeList = openedAtMark.keySet();
			for (Integer hashCode : hashCodeList) {
				Date openedAt = openedAtMark.get(hashCode);
				if (openedAt != null) {
					long lifeSec = (currentTime.getTime() - openedAt.getTime()) / 1000;
					if (lifeSec >= lifeCycle && connectionPool.contains(hashCode)) {
						if (!abortList.contains(hashCode))
							abortList.add(hashCode);
					}
				}
			}
			for (Integer hashCode : abortList) {
				if (connectionPool.contains(hashCode)) {
					countAbortedLifeCycle++;
					log.mark("AbortLifeCycleEndedConnection '{}', ConnectionPoolSize '{}', OpenConnectionSize '{}'.",hashCode,connectionPool.size(),getOpenConnectionSize());
					abort(hashCode);
				}
			}
		}
	}

	/**
	 * Abort idle timeout connection
	 * Skip execute when 'idleTimeout' is 0
	 * */
	private void abortIdleTimeoutConnection() {
		if (idleTimeout > 0) {
			Date currentTime = new Date();
			List<Integer> abortList = new ArrayList<Integer>();
			Map<Integer,Date> idleBeginMark = connectionPool.getConnectionIdleBeginMark();
			Set<Integer> hashCodeList = idleBeginMark.keySet();
			for (Integer hashCode : hashCodeList) {
				Date openedAt = idleBeginMark.get(hashCode);
				if (openedAt != null) {
					long idleTime = (currentTime.getTime() - openedAt.getTime()) / 1000;
					if (idleTime >= idleTimeout && connectionPool.contains(hashCode)) {
						if (!abortList.contains(hashCode))
							abortList.add(hashCode);
					}
				}
			}
			for (Integer hashCode : abortList) {
				if (connectionPool.contains(hashCode)) {
					int loadPoolSize = connectionPool.size();
					boolean overMinPoolSize = loadPoolSize > minPoolSize;
					log.debug("Check abort idle connection, overMinPoolSize[%s] = loadPoolSize[%s] > minPoolSize[%s].",overMinPoolSize, loadPoolSize,minPoolSize);
					if (overMinPoolSize && connectionPool.contains(hashCode)) {
						countAbortedIdle++;
						log.mark("AbortIdleTimeoutConnection '{}', ConnectionPoolSize '{}', OpenConnectionSize '{}'.",hashCode,connectionPool.size(),getOpenConnectionSize());
						abort(hashCode);
					}
				}
			}
		}
	}

	/**
	 * Abort invalid connection
	 * Skip execute when 'testValidTimeout' is 0
	 * */
	private void abortInvalidConnection() {
		if (testValidTimeout > 0) {
			try {
				List<Integer> abortList = new ArrayList<Integer>();
				Map<Integer,Date> idleBeginMark = connectionPool.getConnectionIdleBeginMark();
				Set<Integer> hashCodeList = idleBeginMark.keySet();
				for (Integer hashCode : hashCodeList) {
					Connection conn = connectionPool.getConnectionMark().get(hashCode);
					if (conn != null) {
						boolean valid = conn.isValid(testValidTimeout.intValue());
						if (!valid && !abortList.contains(hashCode))
							abortList.add(hashCode);
					}
				}
				for (Integer hashCode : abortList) {
					if (connectionPool.contains(hashCode)) {
						countAbortedInvalid++;
						log.mark("AbortInvalidConnection '{}', ConnectionPoolSize '{}', OpenConnectionSize '{}'.",hashCode,connectionPool.size(),getOpenConnectionSize());
						abort(hashCode);
					}
				}
			} catch (Exception e) {
				log.warn(e.getMessage(), e);

			}
		}
	}

	/**
	 * Create connection to pool
	 * When,
	 *   (loadPoolSize + usedPoolSize) &lt; maxPoolSize
	 *   and
	 *   loadPoolSize &lt; minPoolSize
	 * */
	private synchronized void createConnectionToPool() {
		final String threadName = String.format("%s-createConnectionToPool", this.name);

		boolean lessThanMaxPoolSize = getOpenConnectionSize() < maxPoolSize;
		boolean lessThanMinPoolSize = connectionPool.size() < minPoolSize;
		boolean lessThanMaxUsage = getUsage() < 1.0D;

		if (log.isDebugEnabled()) {
			log.debug("boolean lessThanMaxPoolSize[{}] = getOpenConnectionSize()[{}] < maxPoolSize[{}];",
					lessThanMaxPoolSize, getOpenConnectionSize(), maxPoolSize);
			log.debug("boolean lessThanMinPoolSize[{}] = connectionPool.size()[{}] < minPoolSize[{}];",
					lessThanMinPoolSize, connectionPool.size(), minPoolSize);
			log.debug("boolean lessThanMaxUsage[{}] = getUsage()[{}] < 1.0D;", lessThanMaxUsage, getUsage());
			log.debug("Checking create conditions,LessThanMaxPoolSize={}, LessThanMinPoolSize={}, lessThanMaxUsage={}",
					lessThanMaxPoolSize, lessThanMinPoolSize, lessThanMaxUsage);

		}
		if (lessThanMaxPoolSize && lessThanMinPoolSize && lessThanMaxUsage) {
			int willCreateConnTotal = minPoolSize - connectionPool.size();
			if (willCreateConnTotal > 0) {
				log.debug("Will create connection total = {}", willCreateConnTotal);
			}
			for (int i = 0; i < willCreateConnTotal; i++) {
				Callable<Boolean> callable = new Callable<Boolean>() {
					@Override
					/*
					* Performs the call operation
					* @return the call result
					* */
					public Boolean call() {
						try {
						    Thread.currentThread().setName("call-" + threadName);
							if (closed || Thread.currentThread().isInterrupted()){
								return false;
							}

							boolean lessThanMaxPoolSize = getOpenConnectionSize() < maxPoolSize;
							boolean lessThanMinPoolSize = connectionPool.size() < minPoolSize;
							boolean lessThanMaxUsage = getUsage() < 1.0D;

							if (lessThanMaxPoolSize && lessThanMinPoolSize && lessThanMaxUsage) {
								Thread.currentThread().setName(threadName);
								loadConnectionToPool();
								log.debug("Loaded connection to pool");
							}
						} catch (Exception e) {
							log.warn(e.getMessage(), e);
						}
						return true;
					}
				};
				connectionPoolExecutor.submit(callable);
			}
			log.debug("Creating {} connection to pool.", willCreateConnTotal);
		}
	}

	/**
	 * Set internal config
	 * @param _configProperties
	 * */
	private synchronized void setInternalConfig(ConfigProperties _configProperties) {
		this.configProperties = _configProperties;
		if (this.configProperties.containsKey("EnableStatusLog"))
			setEnableStatusLog(this.configProperties.getBoolean("EnableStatusLog", false));

		if (this.configProperties.containsKey("EnableFullStatusLog"))
			setEnableFullStatusLog(this.configProperties.getBoolean("EnableFullStatusLog", true));

		if (this.configProperties.containsKey("StatusLogArchiveDays"))
			setStatusLogArchiveDays(this.configProperties.getInteger("StatusLogArchiveDays", 30));

		if (this.configProperties.containsKey("StatusLogFolder"))
			setStatusLogFolder(this.configProperties.getString("StatusLogFolder", null));

		if (this.configProperties.containsKey("MaxPoolSize"))
			setMaxPoolSize(this.configProperties.getInteger("MaxPoolSize", Integer.MAX_VALUE));

		if (this.configProperties.containsKey("MinPoolSize"))
			setMinPoolSize(this.configProperties.getInteger("MinPoolSize", 0));

		if (this.configProperties.containsKey("LifeCycle"))
			setLifeCycle(this.configProperties.getSeconds("LifeCycle", Long.MAX_VALUE));

		if (this.configProperties.containsKey("IdleTimeout"))
			setIdleTimeout(this.configProperties.getSeconds("IdleTimeout", 0L));

		if (this.configProperties.containsKey("Timeout"))
			setTimeout(this.configProperties.getSeconds("Timeout", 0L));

		if (this.configProperties.containsKey("TransactionTimeout"))
			setTransactionTimeout(this.configProperties.getSeconds("TransactionTimeout", 0L));

		if (this.configProperties.containsKey("TestValidTimeout"))
			setTestValidTimeout(this.configProperties.getSeconds("TestValidTimeout", 5L));

		if (this.configProperties.containsKey("RefreshPoolTimer"))
			setRefreshPoolTimer(this.configProperties.getMilliSeconds("RefreshPoolTimer", 1000L));

		if (this.configProperties.containsKey("DriverJar"))
			setDriverJar(this.configProperties.getString("DriverJar", null));

		if (this.configProperties.containsKey("ConnProps"))
			setConnProps(this.configProperties.getString("ConnProps", null));

		if (this.configProperties.containsKey("ThreadName"))
			setName(this.configProperties.getString("ThreadName", null));

		if (this.configProperties.containsKey("Driver"))
			setDriverClassName(this.configProperties.getString("Driver", null));

		if (this.configProperties.containsKey("Url"))
			setUrl(this.configProperties.getString("Url", null));

		if (this.configProperties.containsKey("Url[0]"))
			setUrl(this.configProperties.getArray("Url"));

		if (this.configProperties.containsKey("Username"))
			setUsername(this.configProperties.getString("Username", null));

		if (this.configProperties.containsKey("Password"))
			setPassword(this.configProperties.getString("Password", null));

		if (this.configProperties.containsKey("AbortMode"))
			setAbortMode(this.configProperties.getString("AbortMode", "abort"));

		if (this.configProperties.containsKey("MaxAveUsageSize"))
			setMaxAveUsageSize(this.configProperties.getInteger("MaxAveUsageSize", 10000));

		loadedConfig = true;
	}

	/**
	 * Set internal config
	 * @param configPropertiesContent
	 * */
	private synchronized void setInternalConfig(String configPropertiesContent) throws IOException {
		log.debug("setInternalConfig -> {}", configPropertiesContent);
		ConfigProperties _configProperties = new ConfigProperties();
		_configProperties.load(new StringReader(configPropertiesContent));
		setInternalConfig(_configProperties);
	}

	/**
	 * Set config
	 * @param configPropertiesContent
	 * */
	public synchronized void setConfig(String configPropertiesContent) throws IOException {
		setInternalConfig(configPropertiesContent);
	}

	/**
	 * Set config
	 * @param _configProperties
	 * */
	public synchronized void setConfig(ConfigProperties _configProperties) throws IOException {
		setInternalConfig(_configProperties);
	}

	/**
	 * Reload config
	 * When 'appDataSource' is file object
	 * */
	private synchronized void reloadConfig() {
	    try{
    	    DriverDataSource the = this;
    	    if (this.appDataSource != null) {
    	        configProperties = new ConfigProperties();
    	        Runnable updateRun = new Runnable(){
    	            @Override
    	            /*
    	            * Performs the run operation
    	            * */
    	            public void run(){
    	                try{
        	                the.setInternalConfig(configProperties);
        	                Logger.systemMark(DriverDataSource.class, "DriverDataSource '{}' Reloaded", name);
    	                }catch(Exception e){
    	                    log.error(e.getMessage(),e);
    	                }
    	            }
    	        };
    	        configProperties.load(this.appDataSource,updateRun);
    	        updateRun.run();
    	    }
	    }catch(Exception e){
	        log.error(e.getMessage(),e);
	    }
	}

	/**
	 *  Archive log
	 * */
	private void archiveLog() {
		if (statusLogFile != null && statusLogArchiveDays != null) {
			File statusLogFileParent = statusLogFile.getParentFile();
			if (statusLogArchiveDays > 0 && statusLogFileParent != null) {
				try {
					long fileOutArchiveDaysMs = new Date().getTime() - (statusLogArchiveDays * 24 * 3600000L);
					deleteFilesOlderThan(statusLogFileParent, fileOutArchiveDaysMs);
				} catch (Exception e) {
					log.warn(e);

				}
			}
		}
	}

	/**
	 * Delete old archive log
	 * */
	private void deleteFilesOlderThan(File directory, long fileOutArchiveDaysMs) throws IOException {
		if (directory.isDirectory()) {
			File[] files = directory.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
						boolean isLogFile = file.getName().toLowerCase().endsWith(".log");
						if (isLogFile) {
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
	}

	/**
	 * Write connection status log
	 * */
	private synchronized void statusLogOutput() {
		File folder = statusLogFile.getParentFile();
		if (folder.exists()) {
			if (!folder.canWrite())
				return;
		} else {
			folder.mkdirs();
		}
		if (statusLogFile.exists()) {
			if (!statusLogFile.canWrite())
				return;
		}

		SimpleDateFormat timeFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS");
		Map<Integer, String> CONNECTION_THREAD_MARK_SYNC = connectionPool.getConnectionThreadMark();
		Map<Integer, Date> CONNECTION_OPENED_AT_MARK_SYNC = connectionPool.getConnectionOpenedAtMark();
		Map<Integer, Date> CONNECTION_USING_AT_MARK_SYNC = connectionPool.getConnectionUsingAtMark();
		Map<Integer, Date> CONNECTION_IDLE_BEGIN_MARK_SYNC = connectionPool.getConnectionIdleBeginMark();

		int usingSize = CONNECTION_USING_AT_MARK_SYNC.keySet().size();
		int idleSize = CONNECTION_IDLE_BEGIN_MARK_SYNC.keySet().size();

		boolean isFull = (usingSize > idleSize && idleSize == 0 && usingSize >= maxPoolSize);

		String driverInfo = String.format("Driver: %s%s", this.driverClassName, System.lineSeparator());
		StringBuffer countInfoSb = new StringBuffer();
		countInfoSb.append(
				String.format("MinPoolSize: %s\tMaxPoolSize: %s%s", minPoolSize, maxPoolSize, System.lineSeparator()));
		countInfoSb.append(String.format("RefreshPoolTimer: %sms%s", refreshPoolTimer, System.lineSeparator()));
		countInfoSb.append(String.format("TestValidTimeout: %ss\tIdleTimeout: %ss%s", testValidTimeout, idleTimeout,
				System.lineSeparator()));
		countInfoSb.append(String.format("Timeout: %ss\tTransactionTimeout: %ss%s", timeout, transactionTimeout,
				System.lineSeparator()));
		countInfoSb.append(
				String.format("LifeCycle: %ss\tCountClosed: %s%s", lifeCycle, countClosed, System.lineSeparator()));
		countInfoSb.append(String.format("CountCreatedConnection: %s\tCountAbortedCloseFailed: %s%s",
				countCreatedConnection, countAbortedCloseFailed, System.lineSeparator()));
		countInfoSb.append(String.format("CountAbortedIdleTimeouted: %s\tCountAbortedTimeouted: %s%s", countAbortedIdle,
				countAbortedTimeout, System.lineSeparator()));
		countInfoSb.append(String.format("CountAbortedLifeCycleEnded: %s\tCountAbortedInvalid: %s%s",
				countAbortedLifeCycle, countAbortedInvalid, System.lineSeparator()));
		countInfoSb.append(String.format("MaxUsage: %.2f%%\tCurrentUsage: %.2f%%\tAveUsage: %.2f%%\tCounter: %s/%s%s", maxUsage * 100,
				getUsage() * 100,getAveUsage() * 100, aveUsageSum.size(),maxAveUsageSize,System.lineSeparator()));

		String urlInfo = String.format("URL: %s%s", this.url, System.lineSeparator());
		String usernameInfo = String.format("User: %s%s", this.username, System.lineSeparator());
		String header = String.format("%sLine\tHashCode\tCreatedTime\tChangedTime\tStatus\tLastUsingBy%s",
				System.lineSeparator(), System.lineSeparator());
		String lineFormat = "%s\t%s\t%s\t%s\t%s\t%s%s";
		StringBuffer sbf = new StringBuffer();
		sbf.append(String.format("ThreadName: %s%s", this.name, System.lineSeparator()));
		sbf.append(String.format("Host: %s\tPID: %s\tSystemTime: %s%s", CommonTools.getHostname(), PID,
				timeFormat.format(new Date()), System.lineSeparator()));
		sbf.append(urlInfo);
		sbf.append(usernameInfo);
		sbf.append(driverInfo);
		sbf.append(countInfoSb.toString());
		sbf.append(header);
		int lineNumber = 1;

		Set<Integer> keys = CONNECTION_OPENED_AT_MARK_SYNC.keySet();
		List<Object[]> list = new ArrayList<Object[]>();
		for (Integer hashCode : keys) {
			String status = "-";
			String thread = "-";
			Date changedTime = null;
			Date createdTime = CONNECTION_OPENED_AT_MARK_SYNC.get(hashCode);

			if (CONNECTION_THREAD_MARK_SYNC.containsKey(hashCode)) {
				thread = CONNECTION_THREAD_MARK_SYNC.get(hashCode);
			}

			if (CONNECTION_USING_AT_MARK_SYNC.containsKey(hashCode)) {
				changedTime = CONNECTION_USING_AT_MARK_SYNC.get(hashCode);
				status = "Using";
			} else if (CONNECTION_IDLE_BEGIN_MARK_SYNC.containsKey(hashCode)) {
				changedTime = CONNECTION_IDLE_BEGIN_MARK_SYNC.get(hashCode);
				status = "Idle";
			}

			Object[] objArr = new Object[] { hashCode, createdTime, changedTime, status, thread };
			list.add(objArr);
		}

		Comparator<Object[]> comp = new Comparator<Object[]>() {
			@Override
			/*
			* Performs the compare operation
			* @param arg0 - the arg0
			* @param arg1 - the arg1
			* @return the compare result
			* */
			public int compare(Object[] arg0, Object[] arg1) {
				Date date0 = (Date) arg0[1];
				Date date1 = (Date) arg1[1];
				return date0.getTime() < date1.getTime() ? -1 : 1;
			}
		};

		Collections.sort(list, comp);

		for (Object[] objArr : list) {
			Integer hashCode = (Integer) objArr[0];
			Date createdTime = (Date) objArr[1];
			String createdTimeStr = createdTime == null ? "-" : timeFormat.format(createdTime);
			Date changedTime = (Date) objArr[2];
			String changedTimeStr = changedTime == null ? "-" : timeFormat.format(changedTime);
			String status = objArr[3].toString();
			String thread = objArr[4].toString();
			String line = String.format(lineFormat, lineNumber, hashCode, createdTimeStr, changedTimeStr, status,
					thread, System.lineSeparator());
			sbf.append(line);
			lineNumber++;
		}

		try {
			String msg = sbf.toString();
			FileTools.write(statusLogFile, msg, false);

			if (isFull && enableFullStatusLog) {
				SimpleDateFormat timeFullFormat = new SimpleDateFormat("yyyyMMddHHmm");
				File fullStatusLogFile = new File(String.format("%s_full_%s", statusLogFile.getAbsolutePath(),
						timeFullFormat.format(new Date())));
				FileTools.write(fullStatusLogFile, msg, false);

			}
		} catch (IOException e) {
			log.warn(e.getMessage(), e);

		}
	}

	/**
	 * Manage connection pool
	 * */
	private void manageConnectionPool() {
		// Starts the single management thread exactly once. Double-checked
		// locking keeps this off the borrow hot path after first init - the
		// former method-level synchronized made every getConnection() contend
		// on the same lock just to re-read an already-true flag.
		if (!inited) {
			synchronized (this) {
				if (!inited) {
					inited = true;

			String threadName = this.name;
			ExecutorService executor = Executors.newFixedThreadPool(1);
			executor.execute(new Runnable() {

				@Override
				/*
				* Performs the run operation
				* */
				public void run() {
					Thread.currentThread().setName(String.format("%s-manageConnectionPool-1", threadName));
					while (true) {
						if (closed || Thread.currentThread().isInterrupted())
							break;

						try {
							abortLifeCycleEndedConnection();
							abortTimeoutConnection();
							abortIdleTimeoutConnection();

							if(!closed && !Thread.currentThread().isInterrupted()){
							    createConnectionToPool();
							}

							countUsage(true);
							if (enableStatusLog && statusLogFile != null) {
								statusLogOutput();
								archiveLog();
							}
						} catch (Exception e) {
							log.warn(e.getMessage(), e);
						}

						try {
							Thread.sleep(refreshPoolTimer);
						} catch (InterruptedException e) {
							log.warn(e.getMessage(), e);
							// Restore the interrupt flag and stop the loop - a
							// swallowed interrupt would leave the pool-manager
							// thread running forever after closeAll().
							Thread.currentThread().interrupt();
							break;
						}
					}
				}

			});

            executor = Executors.newFixedThreadPool(1);
			executor.execute(new Runnable() {

				@Override
				/*
				* Performs the run operation
				* */
				public void run() {
					Thread.currentThread().setName(String.format("%s-manageConnectionPool-2", threadName));
					while (true) {
						if (closed || Thread.currentThread().isInterrupted())
							break;

						try {
							abortInvalidConnection();
						} catch (Exception e) {
							log.warn(e.getMessage(), e);

						}

						try {
							Thread.sleep(refreshPoolTimer);
						} catch (InterruptedException e) {
							log.warn(e.getMessage(), e);
							// Restore the interrupt flag and stop the loop - a
							// swallowed interrupt would leave the pool-manager
							// thread running forever after closeAll().
							Thread.currentThread().interrupt();
							break;
						}
					}
				}

			});
			}
		}
	}
	}

	/**
	 * Get connection
	 * @exception SQLException
	 * @return Connection
	 * */
    @Override
	public Connection getConnection() throws SQLException {
	    try{
    		manageConnectionPool();
    		Connection conn = null;
    		// Borrow path is lock-free: ConnectionPool serializes only the
    		// compound pick-and-remove on its own poolLock. Under contention
    		// another thread may take the last idle connection right after our
    		// empty-pool check, so retry once after (re)loading - preserves the
    		// former fully-synchronized semantics (either a connection or an
    		// error, never a spurious null).
    		for (int attempt = 0; attempt < 2 && conn == null; attempt++) {
	    		if(connectionPool.size() == 0){
	    		     loadConnectionToPool();
	    		}
	    		conn = connectionPool.getConnection();
    		}
    		if (conn == null) {
    			throwConnIsNull();
    		}
            countUsage(false);

    		if (conn == null) {
    			throwConnIsNull();
    		}
    		log.debug("Get Connection -> {}", conn.hashCode());
    		return new DriverConnection(this, conn);
	    }catch(Exception e){
	        log.error(e.getMessage(),e);
	        throwConnIsNull();
	    }
	    return null;
	}

	/**
	* Performs the throwConnIsNull operation
	* */
	private void throwConnIsNull() throws SQLException {
    	int errorCode = 99904;
		String errorMsg = "Connection is null.";
		log.warn(String.format("ERROR CODE: (%s) %s", errorCode, errorMsg));
		throw new SQLException(errorMsg) {
			@Override
			/*
			* Performs the getErrorCode operation
			* @return the getErrorCode result
			* */
			public int getErrorCode() {
				return errorCode;
			}
		};
	}

	/**
	* Performs the checkConnectionPoolSize operation
	* */
	private synchronized void checkConnectionPoolSize() throws SQLException {
		int openConnectionSize = getOpenConnectionSize();
		if (openConnectionSize >= maxPoolSize) {
			int errorCode = 99903;
			String errorMsg = String.format("Full connection pool,current size is %s.", openConnectionSize);
			log.warn(String.format("ERROR CODE: (%s) %s", errorCode, errorMsg));
			throw new SQLException(errorMsg) {
				@Override
				/*
				* Performs the getErrorCode operation
				* @return the getErrorCode result
				* */
				public int getErrorCode() {
					return errorCode;
				}
			};

		}
	}

	/**
	 * Get connection properties
	 * @return Properties
	 * */
	private Properties getConnectionProperties() {
		Properties props = new Properties();
		props.setProperty("user", username);
		props.setProperty("password", password);

		if (!CommonTools.isBlank(connProps)) {
			try {
				String[] ps = connProps.split(";");
				int psSize = ps.length;
				for (int i = 0; i < psSize; i++) {
					String[] propKeyValue = ps[i].split("=");
					String pkey = propKeyValue[0].trim();
					String pval = propKeyValue[1].trim();
					props.setProperty(pkey, pval);
				}
			} catch (Exception e) {
				log.warn(e.getMessage(), e);

			}
		}
		return props;
	}

	/**
	 * Get connection from driver
	 * @exception SQLException
	 * @return Connection
	 * */
	private Connection getConnectionFromDriver() throws SQLException {
		Properties props = getConnectionProperties();
		Connection conn = null;
		if (driverJar == null) {
			conn = tryGetConnection(null, props);
		} else {
			Driver driver = loadJdbcDriver(driverJar, driverClassName);
			log.debug("Success init drive -> {}", driverClassName);
			conn = tryGetConnection(driver, props);
		}

		if (conn == null) {
			throwConnIsNull();
		}
		return conn;
	}

	/**
	 * Load connection to pool
	 * @exception SQLException
	 * */
	private synchronized void loadConnectionToPool() throws SQLException {
		checkConnectionPoolSize();
		log.debug("Opening(Pool)");
		final Connection conn = getConnectionFromDriver();
		final Integer hashCode = conn.hashCode();
		log.debug("Created Connection -> {}", hashCode);
		connectionPool.add(conn);
	}

	/**
	 * Close connection
	 * @exception SQLException
	 * @param conn - Connection object
	 * */
	protected void closeConnection(Connection conn) throws SQLException {
		if (conn != null)
			closeConnection(conn.hashCode());
	}

	/**
	 * Close connection
	 * @exception SQLException
	 * @param hashCode - Connection hash code
	 * */
	protected void closeConnection(Integer hashCode) throws SQLException {
		Connection conn = connectionPool.getConnectionMark().get(hashCode);
		if (conn != null) {
			try {
				synchronized (this) {
					connectionPool.removeConnectionUsingAtMark(hashCode);
				}
				if (lifeCycle <= 0) {
					conn.close();
					connectionPool.removeAll(hashCode);
					countClosed++;
					log.debug("Closed Connection -> {}", hashCode);
				} else {
				    if(closed){
    					conn.close();
						connectionPool.removeAll(hashCode);
						countClosed++;
    					log.debug("Closed Connection -> {}", hashCode);
				    }else{
    					conn.setAutoCommit(true);
    					connectionPool.freed(hashCode);
    					log.debug("Freed Connection -> {}", hashCode);
				    }
				}
			} catch (SQLException e) {
				addCountAbortedCloseFailed(+1);
				abort(hashCode);

				throw e;
			}
		}
	}

	/**
	 * Add count abort close failed for write status log
	 * @param value - Change value
	 * */
	private synchronized void addCountAbortedCloseFailed(int value) {
		countAbortedCloseFailed += value;
	}

	/**
	 * Close all connections
	 * */
	public synchronized void abortAll() {
		closed = true;
		log.mark("Try Abort(On SystemExit,CLOSE-{}) connections.",name);
		Map<Integer,Connection> connectionMark = connectionPool.getConnectionMark();
		Iterator<Integer> itr = connectionMark.keySet().iterator();
		while (itr.hasNext()) {
			Integer key = itr.next();
			Connection conn = connectionMark.get(key);
			if (conn != null) {
				log.mark("Abort(On SystemExit) connection '{}.{}'.",name,conn.hashCode());
				abort(conn.hashCode());
			}
		}
		List<Integer> connList = connectionPool.list();
		for(Integer hashCode : connList){
		    log.mark("Abort(On SystemExit) connection '{}.{}'.",name,hashCode);
		    abort(hashCode);
		}
		log.mark("Aborted all connections '{}'.",name);
	}

	/**
	 * Loop close all connections
	 * */
	public synchronized void closeAll(int waitSeconds) {
		closed = true;

		if(waitSeconds < 0) waitSeconds = 10;

		log.mark("Try Abort(On SystemExit,CLOSE_ALL-{}) connections.",name);
		Map<Integer,Date> idleBeginMark = connectionPool.getConnectionIdleBeginMark();
		Set<Integer> hashCodeList = idleBeginMark.keySet();
		for(Integer hashCode : hashCodeList){
		    log.mark("Abort(On SystemExit) connection '{}.{}'.",name,hashCode);
		    abort(hashCode);
		}
		double currentUsage = getUsage();
		log.mark("Checking usage for close all , the '{}' usage is '{}'.",name,currentUsage);

		if(currentUsage > 0 && waitSeconds > 0){
		  waitSeconds--;
		    try{
		        Thread.sleep(1000L);
		    }catch(InterruptedException e){
		        log.error(e.getMessage(),e);
		    }
		    closeAll(waitSeconds);
		}
		abortAll();
		log.mark("Aborted all connections '{}'.",name);
	}

	/**
	 * Loop close all connections
	 * */
	public synchronized void closeAll() {
		closeAll(10);
	}

	/**
	 * Deregister JDBC Driver
	 * For system exit
	 * */
	public synchronized static void deregisterDriver(){
	    LoggerFactory.getLogger(DriverDataSource.class).mark("DeregisterDriver processing.");
	    Enumeration<Driver> drivers = DriverManager.getDrivers();
	    if(drivers != null){
    		while (drivers.hasMoreElements()) {
    			Driver driver = drivers.nextElement();
    			try{
    			    LoggerFactory.getLogger(DriverDataSource.class).mark("Driver: '{}'",driver.hashCode());
    			    DriverManager.deregisterDriver(driver);
    			}catch(Exception e){
    			    LoggerFactory.getLogger(DriverDataSource.class).error(e.getMessage(),e);
    			}
    		}
	    }
		LoggerFactory.getLogger(DriverDataSource.class).mark("DeregisterDriver processed.");
	}

	/**
	 * Try get connection
	 * Support multi url
	 * @param driver - Driver
	 * @param props - Connection properties ,var name is 'connProps'
	 * */
	private Connection tryGetConnection(Driver driver, Properties props) {
		Connection conn = null;
		int urlLastIndex = urlList.size() - 1;

		do {
			if (closed || Thread.currentThread().isInterrupted())
				break;

			if (connectionPool.size() >= maxPoolSize)
				break;

			if (!CONN_ERROR_TIME.containsKey(urlIndex)) {
				CONN_ERROR_TIME.put(urlIndex, 0L);
			}

			Long connErrorTime = CONN_ERROR_TIME.get(urlIndex);
			if (connErrorTime > 0) {
				ConfigProperties cp = getConfigProperties();
				long connCheckMs = 10000L;
				if (cp != null) {
					connCheckMs = cp.getLong("TryGetConnectionCheckTime", 10000L);
				}
				boolean isSkipConn = connErrorTime > 0 && (System.currentTimeMillis() - connErrorTime) <= connCheckMs;
				if (isSkipConn) {
					try {
						Thread.sleep(100L);
					} catch (InterruptedException e) {
						log.debug(e.getMessage(), e);
					}
					continue;
				}
			}

			url = urlList.get(urlIndex);
			try {
				if (driver == null) {
					Class.forName(driverClassName);
					conn = DriverManager.getConnection(url, props);
				} else {
					conn = driver.connect(url, props);
				}
				conn.setAutoCommit(true);

				countCreatedConnection++;

				CONN_ERROR_TIME.put(urlIndex, 0L);

				break;
			} catch (Exception e) {

				CONN_ERROR_TIME.put(urlIndex, System.currentTimeMillis());

				urlIndex += 1;

				Logger.systemError(DriverDataSource.class,"Failed connect to '{}'.", url);
				Logger.systemError(DriverDataSource.class,e.getMessage(), e);

			}
		} while (urlIndex <= urlLastIndex);

		if (conn == null) {
			urlIndex = 0;
			url = urlList.get(urlIndex);
		}

		return conn;
	}

	/**
	 * Abort connection by hash code
	 * @param hashCode - Connection hash code
	 * */
	protected void abort(Integer hashCode) {
		log.debug("Abort Connection {}", hashCode);
		Connection conn = connectionPool.getConnectionMark().get(hashCode);
		if (conn != null) {
			try {
				callAbortConn(conn);
				log.debug("Aborted Connection -> {}", hashCode);
				connectionPool.removeAll(hashCode);
				log.debug("Abort(If Aborted,{}), UsedPoolSize={}.", hashCode, getOpenConnectionSize());
			} catch (Exception e) {
				log.warn(e.getMessage(), e);

			}
		}
	}

	/**
	 * Call abort connection
	 * Please see 'abortMode' options (rollback,close,abort)
	 * Default is 'abort'
	 * @param conn - Connection
	 * */
	private void callAbortConn(Connection conn) {
		try {
			if (abortMode.equalsIgnoreCase("rollback")) {
				if (!conn.getAutoCommit())
					conn.rollback();

				conn.close();
			} else if (abortMode.equalsIgnoreCase("close")) {
				conn.close();
			} else {
				conn.abort(Executors.newFixedThreadPool(1));
			}
		} catch (SQLException e) {
			log.warn("EROR CODE: {}", e.getErrorCode());
			log.warn(e.getMessage(), e);

		} catch (Exception e) {
			log.warn(e.getMessage(), e);

		}
	}

	/**
	 * Load jdbc driver jar
	 * @param driverJar - It is jar file path or jar dir path
	 * @param driver
	 * @exception SQLException
	 * @return Driver
	 * */
	private synchronized Driver loadJdbcDriver(String driverJar, String driver) throws SQLException {
		try {

			if (driverJar == null) {
				int errorCode = 99901;
				String errorMsg = "Driver jar is null.";
				log.warn(String.format("ERROR CODE: (%s) %s", errorCode, errorMsg));
				throw new SQLException(errorMsg) {
					@Override
					/*
					* Performs the getErrorCode operation
					* @return the getErrorCode result
					* */
					public int getErrorCode() {
						return errorCode;
					}
				};
			}

			Driver driverNewInstance = DRIVER_MARK.get(driverJar);

			if (driverNewInstance == null) {
				URLClassLoader driverClassLoader = CommonTools.loadJars(driverJar);
				Class<?> classToLoad = Class.forName(driver, true, driverClassLoader);
				Constructor<?> driverConstructor = classToLoad.getConstructor();
				driverConstructor.setAccessible(true);
				driverNewInstance = (Driver) driverConstructor.newInstance();

				DRIVER_MARK.put(driverJar, driverNewInstance);
			}

			return driverNewInstance;
		} catch (Exception e) {
			int errorCode = 99902;
			String errorMsg = e.getMessage();
			log.warn(String.format("ERROR CODE: (%s) %s", errorCode, errorMsg));
			throw new SQLException(e) {

				@Override
				/*
				* Performs the getErrorCode operation
				* @return the getErrorCode result
				* */
				public int getErrorCode() {
					return errorCode;
				}

			};
		}
	}

	/**
	 * Get owner thread name
	 * For use sync DataSource
	 * @return String
	 * */
	public String getOwner() {
		return owner;
	}

	/**
	 * Get config properties
	 * @return ConfigProperties
	 * */
	public ConfigProperties getConfigProperties() {
		return configProperties;
	}

	/**
	 * Get sync data source list
	 * @exception SQLException
	 * @param owner - From master data source thread name
	 * @return List&lt;DriverDataSource&gt;
	 * */
	protected final synchronized List<DriverDataSource> getSyncDataSourceList(String owner) throws SQLException {
		if (syncDriverDataSource == null) {
			syncDriverDataSource = new ArrayList<DriverDataSource>();
			List<String> syncDsPropList = configProperties.getArray("SyncDataSource");

			if (syncDsPropList == null)
				return null;

			for (String syncDsProp : syncDsPropList) {

				if (CommonTools.isBlank(syncDsProp))
					continue;

				File _appDsFile = new File(syncDsProp);
				if (_appDsFile.exists()) {
					DriverDataSource dds = new DriverDataSource(_appDsFile);
					dds.owner = owner;
					syncDriverDataSource.add(dds);
				} else {
					int errorCode = 99905;
					String errorMsg = String.format("SyncDatasource is null - %s .", syncDsProp);
					throw new SQLException(errorMsg) {
						@Override
						/*
						* Performs the getErrorCode operation
						* @return the getErrorCode result
						* */
						public int getErrorCode() {
							return errorCode;
						}
					};
				}

			}
		}
		return syncDriverDataSource;
	}

	/**
	 * For checksum master and sync data source connection url
	 * Pause sync where same connection url
	 * @return String
	 * */
	public String getFingerprint() {
		return CommonTools.md5(String.format("%s-%s", this.url, this.username));
	}

	/**
	* Performs the getAveUsage operation
	* @return the getAveUsage result
	* */
	public double getAveUsage(){
	    int size = aveUsageSum.size();
	    double sum = 0.0D;
	    for(int i = 0; i < size;i++){
	        sum += aveUsageSum.get(i);
	    }

	    if(size <= 0) return 0.0D;

	    return sum/size;
	}

	/**
	* Performs the setMaxAveUsageSize operation
	* @param maxAveUsageSize - the maxAveUsageSize
	* */
	public void setMaxAveUsageSize(Integer maxAveUsageSize){
	    this.maxAveUsageSize = maxAveUsageSize == null ? 10000 : maxAveUsageSize;
	}

	/**
	* Performs the countUsage operation
	* @param calAve - the calAve
	* */
	private void countUsage(boolean calAve) {
		double usage = getUsage();
        if(calAve){
            if(aveUsageSum.size() >= maxAveUsageSize){
		        aveUsageSum.remove(0);
            }
            aveUsageSum.add(usage);
        }
		if (usage > maxUsage) {
			maxUsage = usage;
		}
	}

	/**
	 * Get pool using
	 * @return double
	 * */
	public double getUsage() {
		Map<Integer, Date> CONNECTION_USING_AT_MARK_SYNC = connectionPool.getConnectionUsingAtMark();
		int usingSize = CONNECTION_USING_AT_MARK_SYNC.keySet().size();

		if (usingSize <= 0)
			return 0.0D;

		if (maxPoolSize <= 0)
			return 0.0D;

		return usingSize / (maxPoolSize * 1.0D);
	}

	/** Abandon the following methods **/
	@Override
	public <T> T unwrap(java.lang.Class<T> arg0) throws java.sql.SQLException {
		return null;
	}

	@Override
	public boolean isWrapperFor(java.lang.Class<?> arg0) throws java.sql.SQLException {
		return false;
	}

	@Override
	public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
		return null;
	}

	@Override
	/*
	* Performs the setLogWriter operation
	* @param arg0 - the arg0
	* */
	public void setLogWriter(PrintWriter arg0) throws SQLException {
	}

	@Override
	/*
	* Performs the getLogWriter operation
	* @return the getLogWriter result
	* */
	public PrintWriter getLogWriter() throws SQLException {
		return null;
	}

	@Override
	/*
	* Performs the getLoginTimeout operation
	* @return the getLoginTimeout result
	* */
	public int getLoginTimeout() throws SQLException {
		return 0;
	}

	@Override
	/*
	* Performs the setLoginTimeout operation
	* @param arg0 - the arg0
	* */
	public void setLoginTimeout(int arg0) throws SQLException {
	}

	@Override
	/*
	* Performs the getConnection operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @return the getConnection result
	* */
	public Connection getConnection(String arg0, String arg1) throws SQLException {
		return null;
	}

	@Override
	/*
	* Performs the createConnectionBuilder operation
	* @return the createConnectionBuilder result
	* */
	public ConnectionBuilder createConnectionBuilder() throws SQLException {
		return null;
	}

}
