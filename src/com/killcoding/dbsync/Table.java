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

import com.killcoding.datasource.CacheDriverExecutor;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;


/**
* Per-table sync configuration and orchestrator (inserter/updater/deleter/remover).
* */
public class Table {

	private final static Logger log = LoggerFactory.getLogger(Table.class);
	
	protected Database database;
	protected ConfigProperties porps;

	protected String name;
	protected Inserter inserter;
	protected Updater updater;
	protected Deleter deleter;
	protected Remover remover;
	protected boolean enableTableLog = false;
	protected String tableLogFolder = null;
	protected boolean enableHeartbeat = false;

	protected Long timer = 0L;
	protected Long delayStart = 0L;
	protected Long delayTask = 0L;
	protected String primaryKey = null;
	protected String inserterKey = null;
	protected String updaterKey = null;
	protected String deleterKey = null;
	protected String deleterDeletedKey = null;
	protected String removerKey = null;

	protected boolean inserterEnable = false;
	protected boolean updaterEnable = false;
	protected boolean deleterEnable = false;
	protected boolean removerEnable = false;

	protected Integer maxRows = 0;
	protected Integer batch = 0;

	protected Long inserterPeriod = 0L;
	protected Long inserterBatch = 0L;

	protected Long updaterPeriod = 0L;
	protected Long updaterBatch = 0L;

	protected Long deleterPeriod = 0L;
	protected Long deleterBatch = 0L;

	protected Long before = 0L;
	protected Long removerTimer = 0L;
	
	protected Long ignoredPeriod = 0L;
	
	private static long defaultPeriod = 24*3600*1000L;
	private static long defaultBatch = 3600*1000L;

	protected Table(Database database) {
		super();
		this.database = database;
		this.porps = database.getPorps();
		init();
	}

	public Table(Database database, String name) {
		super();
		this.database = database;
		this.name = name;
		this.porps = database.getPorps();
		init();
	}

	private void init() {
		inserter = new Inserter(this);
		updater = new Updater(this);
		deleter = new Deleter(this);
		remover = new Remover(this);

		timer = porps.getMilliSeconds("Table.Timer", 1000L);
		delayStart = porps.getMilliSeconds("Table.DelayStart", 1000L);
		delayTask = porps.getMilliSeconds("Table.DelayTask", 1000L);
		maxRows = porps.getInteger("Table.MaxRows",10000);
		batch = porps.getInteger("Table.Batch",100);
		enableTableLog = porps.getBoolean("Table.TableLog.Enable",false);
		tableLogFolder = porps.getString("Table.TableLog.Folder",String.format("%s/TableLogs/%s",Enter.ROOT_PATH,ProcessHandle.current().pid()));
		enableHeartbeat = porps.getBoolean("Table.TableLog.Heartbeat",false);

		primaryKey = porps.getString("Table.PrimaryKey","id");
		inserterKey = porps.getString("Table.Inserter.Key","created_at");
		updaterKey = porps.getString("Table.Updater.Key","updated_at");
		deleterKey = porps.getString("Table.Deleter.Key","deleted_at");
		deleterDeletedKey = porps.getString("Table.Deleter.DeletedKey","deleted");
		removerKey = porps.getString("Table.Remover.Key","deleted_at");

		inserterPeriod = porps.getMilliSeconds("Table.Inserter.Period",defaultPeriod);
		inserterBatch = porps.getMilliSeconds("Table.Inserter.Batch",defaultBatch);

		updaterPeriod = porps.getMilliSeconds("Table.Updater.Period",defaultPeriod);
		updaterBatch = porps.getMilliSeconds("Table.Updater.Batch",defaultBatch);
		
		deleterPeriod = porps.getMilliSeconds("Table.Deleter.Period",defaultPeriod);
		deleterBatch = porps.getMilliSeconds("Table.Deleter.Batch",defaultBatch);

		before = porps.getMilliSeconds("Table.Remover.Before",defaultPeriod);
		removerTimer = porps.getMilliSeconds("Table.Remover.Timer",10000L);
		
		inserterEnable = porps.getBoolean("Table.Inserter.Enable",true);
		updaterEnable = porps.getBoolean("Table.Updater.Enable",true);
		deleterEnable = porps.getBoolean("Table.Deleter.Enable",true);
		removerEnable = porps.getBoolean("Table.Remover.Enable",true);
		
		ignoredPeriod = porps.getMilliSeconds("Table.IgnoredPeriod",0L);

		inserter.setPrimaryKey(primaryKey);
		inserter.setKey(inserterKey);
		inserter.setPeriod(inserterPeriod);
		inserter.setBatch(inserterBatch);
		inserter.setIgnoredPeriod(ignoredPeriod);

		updater.setPrimaryKey(primaryKey);
		updater.setKey(updaterKey);
		updater.setPeriod(updaterPeriod);
		updater.setBatch(updaterBatch);
		updater.setIgnoredPeriod(ignoredPeriod);

		deleter.setPrimaryKey(primaryKey);
		deleter.setKey(deleterKey);
		deleter.setPeriod(deleterPeriod);
		deleter.setBatch(deleterBatch);
		deleter.setIgnoredPeriod(ignoredPeriod);

		remover.setPrimaryKey(primaryKey);
		remover.setKey(removerKey);
		remover.setBefore(before);
		remover.setTimer(removerTimer);

		inserter.setEnable(inserterEnable);
		updater.setEnable(updaterEnable);
		deleter.setEnable(deleterEnable);
		remover.setEnable(removerEnable);

	}

	/**
	* Resolves the table names this facade syncs: the "Table.GetTables.List"
	* CSV (optional) plus any names returned by the "Table.GetTables.Sql"
	* query (optional). Both may be combined.
	* @return the configured table names (may be empty)
	* */
	public List<String> getTablesList() throws Exception {
		List<String> getTablesList = new ArrayList<String>();
		String getTablesSql = porps.getString("Table.GetTables.Sql","");
		String getTablesListValue = porps.getString("Table.GetTables.List","");

		if (!CommonTools.isBlank(getTablesListValue)) {
			getTablesList.addAll(Arrays.asList(getTablesListValue.split(",")));
		}

		if (!CommonTools.isBlank(getTablesSql)) {    
			List<Map<String, Object>> list = find(getTablesSql, null);
			for (Map<String, Object> map : list) {
				String tableName = map.get("table_name").toString();
				getTablesList.add(tableName);
			}
		}
		return getTablesList;
	}

	/**
	* Runs a SELECT through a CacheDriverExecutor on this database, honouring
	* the configured connection-usage guard (SkipCreateConnectionException when
	* the pool is over max usage). Scans up to Table.MaxRows rows.
	* @param sql - the SELECT statement (may contain '?' placeholders)
	* @return the fetched rows as a list of column->value maps
	* */
	public List<Map<String, Object>> find(String sql) throws Exception {
		return find(sql, new ArrayList<Object>(), maxRows);
	}
	
	/**
	* Runs a SELECT with bound parameters, scanning up to Table.MaxRows rows.
	* @param sql - the SELECT statement (may contain '?' placeholders)
	* @param params - bound parameters (null falls back to the no-param scan)
	* @return the fetched rows as a list of column->value maps
	* */
	public List<Map<String, Object>> find(String sql, List<Object> params) throws Exception {
	    if(params == null) {
	        return find(sql);
	    }else{
		    return find(sql, params, maxRows);
	    }
	}

	/**
	* Runs a SELECT with bound parameters and an explicit row cap.
	* @param sql - the SELECT statement (may contain '?' placeholders)
	* @param params - bound parameters
	* @param maxRows - the row cap applied by the driver
	* @return the fetched rows as a list of column->value maps
	* */
	public List<Map<String, Object>> find(String sql, List<Object> params, int maxRows) throws Exception {
	    boolean allowed = database.allowedUseConnection();
	    if(!allowed) {
	        throw new SkipCreateConnectionException(String.format("Skip find, Over usage '%s'.",database.getUsage()));
	    }		    
	   CacheDriverExecutor cacheDriverExecutor = null;
	    try{
		    cacheDriverExecutor = new CacheDriverExecutor(database.open());
		    List<Map<String, Object>> rows = cacheDriverExecutor.find(0,maxRows,sql,params);
		    return rows;
	    }finally{
	        if(cacheDriverExecutor != null) cacheDriverExecutor.close();
	    }
	}

	/**
	* Runs a SELECT and returns only its first row (row cap 1).
	* @param sql - the SELECT statement
	* @param params - bound parameters
	* @return the first row map, or null when no row matches
	* */
	public Map<String, Object> first(String sql, List<Object> params) throws Exception {
		List<Map<String, Object>> list = find(sql, params, 1);
		if (list.size() > 0) {
			return list.get(0);
		} else {
			return null;
		}
	}
	
	/**
	* Executes a DML/DDL statement through a CacheDriverExecutor on this database.
	* @param sql - the statement (may contain '?' placeholders)
	* @param params - bound parameters
	* @return the affected row count
	* */
	public int execute(String sql, List<Object> params) throws Exception {
	   CacheDriverExecutor cacheDriverExecutor = null;
	    try{
		    cacheDriverExecutor = new CacheDriverExecutor(database.open());
		    int rows = cacheDriverExecutor.execute(sql,params);
		    return rows;
	    }finally{
	        if(cacheDriverExecutor != null) cacheDriverExecutor.close();
	    }
	}	
	
	private synchronized int batchPart(String sql, List<List<Object>> recordsPart) throws Exception {
	   CacheDriverExecutor cacheDriverExecutor = null;
	    try{
	        cacheDriverExecutor = new CacheDriverExecutor(database.open());
	        return cacheDriverExecutor.executeBatchList(sql,recordsPart);
	    }finally{
	        if(cacheDriverExecutor != null) cacheDriverExecutor.close();
	    }
	}

	protected synchronized int batch(String sql, List<List<Object>> records) throws Exception {
	    int rows = 0;
		List<List<List<Object>>> recordsParts = averageAssign(records, batch);
		for (List<List<Object>> recordsPart : recordsParts) {
		    int row = batchPart(sql, recordsPart);
		    rows += row;
		}
		return rows;
	}

	/**
	* Renders the fully qualified table name "schema.table"; when quoting is
	* enabled each part is wrapped with the configured quote format.
	* @return the qualified (and optionally quoted) table name
	* */
	public String getQuoteName() {
		String schema = database.schema;
		String tableName = name;
		if (database.quoteEnable) {
			schema = String.format(database.quoteFormat, database.schema);
			tableName = String.format(database.quoteFormat, name);
		}
		return String.format("%s.%s", schema, tableName);
	}

	/**
	* Wraps a single column/identifier with the configured quote format when
	* quoting is enabled; otherwise returns it unchanged.
	* @param key - the identifier to quote
	* @return the quoted (or raw) identifier
	* */
	public String quote(String key) {
		if (database.quoteEnable) {
			return String.format(database.quoteFormat, key);
		}
		return key;
	}

	/**
	* Splits a source list into roughly equal-sized chunks (at most limit items
	* per chunk) - used to partition one sync batch into sub-batches.
	* @param source - the list to partition (null/empty returns an empty result)
	* @param limit - max items per chunk
	* @return the partitioned chunks
	* */
	protected <T> List<List<T>> averageAssign(List<T> source, int limit) {
		if (null == source || source.isEmpty()) {
			return Collections.emptyList();
		}
		List<List<T>> result = new ArrayList<>();
		int listCount = (source.size() - 1) / limit + 1;
		int remainder = source.size() % listCount;
		int number = source.size() / listCount;
		int offset = 0;
		for (int i = 0; i < listCount; i++) {
			List<T> value;
			if (remainder > 0) {
				value = source.subList(i * number + offset, (i + 1) * number + offset + 1);
				remainder--;
				offset++;
			} else {
				value = source.subList(i * number + offset, (i + 1) * number + offset);
			}
			result.add(value);
		}
		return result;
	}

	/**
	* Appends one CSV line (action, begin, end, size) to the per-table daily log
	* under Table.TableLog.Folder and archives logs older than
	* Table.TableLog.ArchiveDays. No-op unless Table.TableLog.Enable=true.
	* @param action - the action label (INSERT/UPDATE/DELETE/REMOVE/...)
	* @param begin - window begin timestamp
	* @param end - window end timestamp (may be null)
	* @param size - affected row count (or any size value)
	* */
	protected void writeToTableLog(String action, Date begin, Date end, Object size) {
		if (enableTableLog) {
			try {
				Date now = new Date();
				SimpleDateFormat sf = new SimpleDateFormat("yyyyMMdd");
				String dateStr = sf.format(now);
				String logFolderPath = String.format("%s/%s",tableLogFolder, this.name);
				String logPath = String.format("%s/%s.log", logFolderPath, dateStr);
				sf = new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS");
				String beginStr = sf.format(begin);
				String endStr = (end == null ? "-" : sf.format(end));
				sf = new SimpleDateFormat("HH:mm:ss.SSS");
				String logAt = sf.format(now);
				String msg = String.format("%s,%s,%s,%s,%s%s", logAt, action, beginStr, endStr, size, System.lineSeparator());
				FileUtils.write(new File(logPath), msg, true);
				archiveLog(new File(logFolderPath));
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}
	
	private void archiveLog(File directory) {
	    Integer archiveDays = porps.getInteger("Table.TableLog.ArchiveDays",31);
		if (archiveDays > 0 && directory != null) {
			try {
				long archiveDaysMs = new Date().getTime() - (archiveDays * 24 * 3600000L);
				deleteFilesOlderThan(directory, archiveDaysMs);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}	

	private static void deleteFilesOlderThan(File directory, long archiveDaysMs) throws IOException {
		if (directory.isDirectory()) {
			File[] files = directory.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
					    boolean isLog = file.getName().endsWith(".log");
						boolean canWrite = file.canWrite();
						if (isLog && canWrite) {
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
	
	/**
	* Stops all periodic sync workers (inserter/updater/deleter/remover) of this
	* table. Safe to call when none are running.
	* */
	public void stopAll() {
		if (inserter != null) {
			inserter.stop();
		}
		if (updater != null) {
			updater.stop();
		}
		if (deleter != null) {
			deleter.stop();
		}
		if (remover != null) {
			remover.stop();
		}
	}

	/**
	* Queues this table's enabled workers (inserter/updater/deleter/remover)
	* against the given target table; each worker starts its own loop after the
	* configured delay.
	* @param target - the target Table facade to sync into
	* */
	protected void syncTo(Table target) {
		if (inserter.isEnable()) {
		    log.mark("Queuing table for insert - '{}'",this.name);
			inserter.syncTo(target);
		}
		
		try{
		    Thread.sleep(delayTask);
		}catch(InterruptedException e){
		    log.error(e.getMessage(),e);
		}
	
		if (updater.isEnable()) {
		    log.mark("Queuing table for update - '{}'",this.name);
			updater.syncTo(target);
		}
		
		try{
		    Thread.sleep(delayTask);
		}catch(InterruptedException e){
		    log.error(e.getMessage(),e);
		}

		if (deleter.isEnable()) {
		    log.mark("Queuing table for delete - '{}'",this.name);
			deleter.syncTo(target);
		}
		
		try{
		    Thread.sleep(delayTask);
		}catch(InterruptedException e){
		    log.error(e.getMessage(),e);
		}

		if (remover.isEnable()) {
		    log.mark("Queuing table for remove - '{}'",this.name);
			remover.syncTo(target);
		}
	}

}
