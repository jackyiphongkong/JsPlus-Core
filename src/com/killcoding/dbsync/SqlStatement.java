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

package com.killcoding.dbsync;

import com.killcoding.datasource.Clock;
import com.killcoding.file.DiskFile;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
* Base sync statement - common scheduling, batching and SQL scaffolding for a
* table sync task. syncTo() starts ONE stoppable worker thread per statement:
* the loop runs until stop() is called (while(!stopped); the ExecutorService is
* kept and shutdownNow() interrupts the sleep), so Database.stopSync() /
* Table.stopAll() can terminate every worker cleanly. Subclasses implement
* executeTask(begin, end, targetTable) to perform one sync round.
* */
public class SqlStatement {
	
	private final static Logger log = LoggerFactory.getLogger(SqlStatement.class);

	protected Table table;
	protected long batchTaskTime = 0;
	protected String primaryKey;
	protected String key;
	protected Long period = 0L;
	protected Long window = 0L;
	protected Long ignoredPeriod = 0L;

	protected boolean enable = false;

	protected ExecutorService executor = null;
	protected volatile boolean stopped = false;

	/**
	* Binds a statement worker to its owning source table.
	* @param table - the source Table facade
	* */
	public SqlStatement(Table table) {
		super();
		this.table = table;
	}
	
	/**
	* Returns the earliest key-timestamp at or after begin for this table, or
	* null when no row qualifies - used to anchor a sync round at the first
	* pending record.
	* @param begin - the lower window bound
	* @return the first key timestamp as a Date, or null
	* */
	protected Date getFirstTime(Date begin) throws Exception {
		final String quoteKey = table.quote(key);
		final String sql = String.format("SELECT %s FROM %s WHERE %s >= ? ORDER BY %s ASC", quoteKey,
				table.getQuoteName(), quoteKey,quoteKey);
		final List<Object> params = new ArrayList<Object>();
		params.add(begin);
		Map<String, Object> firstRecord = table.first(sql, params);
		if (firstRecord != null) {
			Object timeValue = firstRecord.get(key);
			if (timeValue instanceof Timestamp) {
				return new Date(((Timestamp) timeValue).getTime());
			}
		}
		return null;
	}

	/**
	* Fetches the source window rows whose key timestamp falls in [begin, end)
	* ordered ascending - the candidate set a worker round processes.
	* @param begin - lower bound (inclusive)
	* @param end - upper bound (exclusive)
	* @return the window rows
	* */
	protected List<Map<String, Object>> getTaskBatchList(Date begin, Date end) throws Exception {
		final String quoteKey = table.quote(key);
		final String sql = String.format("SELECT * FROM %s WHERE %s >= ? AND %s < ? ORDER BY %s ASC",
				table.getQuoteName(), quoteKey, quoteKey,quoteKey);
		final List<Object> params = new ArrayList<Object>();
		params.add(begin);
		params.add(end);
		return table.find(sql, params);
	}

	/**
	* Template method: performs one sync round over the given source window.
	* Subclasses implement the actual insert/update/delete replication.
	* @param begin - window begin (inclusive)
	* @param end - window end (exclusive)
	* @param targetTable - the target Table facade
	* */
	protected synchronized void executeTask(Date begin, Date end, Table targetTable) throws Exception {

	}
	
	/**
	* Writes a heartbeat timestamp into <TableLogFolder>/Heartbeat.log when
	* Table.TableLog.Heartbeat is enabled (no-op otherwise).
	* */
	protected void writeHeartbeat(){
		if (table.enableHeartbeat) {
			try {
				Date now = new Date();
				SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
				String dateStr = sf.format(now);
				String logPath = String.format("%s/Heartbeat.log", table.tableLogFolder);
				new DiskFile(logPath).write(dateStr,false);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Starts ONE periodic worker thread for this statement: each loop round
	* walks the source window [batchTaskTime-period, now-ignoredPeriod) and
	* calls executeTask() on the target. The worker honours the connection-usage
	* guard on both databases and stops when stop() is called (while(!stopped)
	* plus executor shutdownNow()).
	* @param targetTable - the target Table facade
	* */
	protected void syncTo(final Table targetTable) {
	    final SimpleDateFormat sf = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");
	    final String ctName = this.getClass().getSimpleName();
		batchTaskTime = new Clock().getTime() - ignoredPeriod;
		final Table sourceTable = this.table;
		final Runnable runnable = new Runnable() {
			/**
			* Worker loop: repeatedly executes sync rounds until stop() is called.
			* */
			@Override
			public void run() {
				Date begin = null;
				Date end = null;
				while (!stopped) {
				    Thread.currentThread().setName(String.format("%s-%s",targetTable.name,ctName));
				    
				    boolean sourceAllowedConn = sourceTable.database.allowedUseConnection();
				    boolean targetAllowedConn = targetTable.database.allowedUseConnection();
					if(!sourceAllowedConn){
						log.debug("Skip task {} Usage={}",sourceTable.name,sourceTable.database.getUsage());
					}
					if(!targetAllowedConn){
						log.debug("Skip task {} Usage={}",targetTable.name,targetTable.database.getUsage());
					}              
					writeHeartbeat();
				    if(sourceAllowedConn && targetAllowedConn && targetTable != null){
						try {
						    
							if (begin == null){
								begin = new Date(batchTaskTime - period);
								log.info("Begin of round (Table '{}' has been pushed to {}).",sourceTable.name,ctName);
							}
							
							Date firstTime = getFirstTime(begin);
							if (firstTime != null) {
								begin = firstTime;
							}    							
						    
						    String beginStr = (begin == null ? "0" : sf.format(begin));
						    Thread.currentThread().setName(String.format("%s-%s-%s",targetTable.name,ctName,beginStr));
	
							long endTime = begin.getTime() + window;
							if (endTime < batchTaskTime) {
								end = new Date(endTime);
								executeTask(begin, end, targetTable);
								begin = new Date(endTime);
							} else {
							    end = new Date(new Clock().getTime() - ignoredPeriod); 
							    executeTask(begin, end, targetTable);
								batchTaskTime = end.getTime();
								begin = null;
								end = null;
								log.info("End of round (Table '{}' has {}).",sourceTable.name,getAction());
							}
						} catch (Exception e) {
						    if(e instanceof SkipCreateConnectionException){
						        log.debug(e.getMessage(), e);
						    }else{
							    log.error(e.getMessage(), e);
						    }
						}
				    }
					try {
						Thread.sleep(table.timer);
					} catch (InterruptedException e) {
						break;
					}
				}
				log.info("Stopped sync task {} ({})",sourceTable.name,ctName);
			}
		};
		stopped = false;
		executor = Executors.newSingleThreadExecutor();
		executor.execute(runnable);
	}

	/**
	* Stops the periodic sync worker for this statement (a running round finishes,
	* then the loop exits and the executor thread is interrupted/terminated).
	* Safe to call when no worker is running.
	* */
	public void stop() {
		stopped = true;
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	/**
	* Sets the primary key column name used by this sync statement.
	* @param primaryKey - the primary key column
	* */
	public void setPrimaryKey(String primaryKey) {
		this.primaryKey = primaryKey;
	}

	/**
	* Returns the primary key column name used by this sync statement.
	* @return the primary key column
	* */
	public String getPrimaryKey() {
		return this.primaryKey;
	}

	/**
	* Sets the incremental (time) key column name used by this sync statement.
	* @param key - the incremental key column
	* */
	public void setKey(String key) {
		this.key = key;
	}

	/**
	* Returns the incremental (time) key column name used by this sync statement.
	* @return the incremental key column
	* */
	public String getKey() {
		return this.key;
	}

	/**
	* Sets the scan window size (the time span each worker round walks back).
	* @param period - the scan window in milliseconds
	* */
	public void setPeriod(Long period) {
		this.period = period;
	}

	/**
	* Returns the scan window size in milliseconds.
	* @return the scan window
	* */
	public Long getPeriod() {
		return this.period;
	}

	/**
	* Sets the batch window size (the time span processed per executeTask call).
	* @param window - the batch window in milliseconds
	* */
	public void setWindow(Long window) {
		this.window = window;
	}

	/**
	* Returns the batch window size in milliseconds.
	* @return the batch window
	* */
	public Long getWindow() {
		return this.window;
	}

	/**
	* Sets whether this sync statement is enabled.
	* @param enable - true to enable the worker
	* */
	public void setEnable(boolean enable) {
		this.enable = enable;
	}

	/**
	* Returns whether this sync statement is enabled.
	* @return true when enabled
	* */
	public boolean isEnable() {
		return this.enable;
	}

	/**
	* Sets the ignored trailing window (records newer than this are not synced).
	* @param ignoredPeriod - the ignored window in milliseconds
	* */
	public void setIgnoredPeriod(Long ignoredPeriod) {
		this.ignoredPeriod = ignoredPeriod;
	}

	/**
	* Returns the ignored trailing window in milliseconds.
	* @return the ignored window
	* */
	public Long getIgnoredPeriod() {
		return this.ignoredPeriod;
	}
	
	/**
	* Returns the past-tense action label used in round log messages.
	* @return the action label (e.g. "inserted")
	* */
	protected String getAction(){
	    return "NONE";
	}

}