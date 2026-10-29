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
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;


/**
* SOURCE-side cleanup worker - deliberately different from the other three
* sync tasks: it does NOT participate in source->target replication. Every
* round it scans the SOURCE table (this.table - the table this worker belongs
* to) and PHYSICALLY DELETEs the rows that are soft-deleted
* (Table.Deleter.DeletedKey = 1) AND whose remover key timestamp
* (Table.Remover.Key, default deleted_at) is older than
* Table.Remover.Before (a duration accepted in unit-suffix form such as
* "100h"/"3600s"/"1d"/"2w", default 24h). The targetTable argument of syncTo()
* is used for naming only; the cleanup always targets the source database.
*
* NOTE: it keeps its own worker loop instead of reusing SqlStatement.syncTo
* because it has no time-window batching; the loop still honours stop() via
* the stopped flag and the kept ExecutorService (see stop()).
* */
public class Remover extends SqlStatement {

	private final static Logger log = LoggerFactory.getLogger(Remover.class);

	private static final String TIMESTAMP_PATTERN = "yyyyMMdd_HHmmssSSS";
	private static final long DEFAULT_TIMER = 10000L;
	private static final long DEFAULT_BEFORE = 24*3600*1000L;
	private Long timer = DEFAULT_TIMER;
	private Long before = DEFAULT_BEFORE;

	/**
	* Creates a Remover worker bound to a source table. The cleanup window
	* (timer/before) is read from the shared Table config with sane defaults.
	* @param table - the source Table facade this worker cleans up
	* */
	public Remover(Table table) {
		super(table);
		// Read the removable-window defaults from the shared Table config so a
		// Remover works standalone; Table.init() may still override them later
		// via setBefore()/setTimer() using the same config keys.
		this.timer = table.porps.getMilliSeconds("Table.Remover.Timer", DEFAULT_TIMER);
		this.before = table.porps.getMilliSeconds("Table.Remover.Before", DEFAULT_BEFORE);
	}

	private void executeBatch() throws Exception {
		Timestamp beforeTime = new Timestamp(new Clock().getTime() - before);
		SimpleDateFormat sf = new SimpleDateFormat(TIMESTAMP_PATTERN);
		Thread.currentThread().setName(String.format("%s-%s-%s",table.name,this.getClass().getSimpleName(),sf.format(beforeTime)));
		List<Object> params = new ArrayList<Object>();
		params.add(1);		
		params.add(beforeTime);
		String selectSqlFormat = "SELECT %s FROM %s WHERE %s = ? AND %s <= ?";
		String selectSql = String.format(selectSqlFormat, table.quote(table.primaryKey),table.getQuoteName(),
				table.quote(table.deleterDeletedKey),table.quote(table.removerKey));
		Map<String,Object> deletedRecord = table.first(selectSql, params);
		if(deletedRecord != null){		
			String deleteSqlFormat = "DELETE FROM %s WHERE %s = ? AND %s <= ?";
			String deleteSql = String.format(deleteSqlFormat, table.getQuoteName(),
					table.quote(table.deleterDeletedKey),table.quote(table.removerKey));
					
			Date begin = new Date();
			int rows = table.execute(deleteSql, params);
			Date end = new Date();
			table.writeToTableLog("REMOVE-BATCH",begin,end, rows);
		}
	}

	/**
	* One cleanup round: probes for an expired soft-deleted source row and
	* physically deletes every matching expired row from the source table.
	* */
	protected synchronized void executeTask() throws Exception {
		table.remover.executeBatch();
	}
	
	/**
	* Starts the remover loop worker. Unlike the other workers the remover only
	* needs the source table (the target is unused here): it probes for expired
	* soft-deleted source rows and physically deletes them.
	* @param targetTable - the target Table facade (unused by the remover)
	* */
	@Override
	protected void syncTo(final Table targetTable) {
	    final String ctName = this.getClass().getSimpleName();
	    final Table sourceTable = this.table;
		final Runnable runnable = new Runnable() {
			/**
			* Remover loop: repeatedly probes for expired soft-deleted rows until
			* stop() is called.
			* */
			@Override
			public void run() {
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
				    if(sourceAllowedConn && targetAllowedConn && table != null){
						try {
						    log.info("Begin of round (Table '{}' has been pushed to {}).",sourceTable.name,ctName);
							executeTask();
							log.info("End of round (Table '{}' has {}).",sourceTable.name,getAction());
						} catch (Exception e) {
						    if(e instanceof SkipCreateConnectionException){
						        log.debug(e.getMessage(), e);
						    }else{
							    log.error(e.getMessage(), e);
						    }
						}
				    }
					try {
						Thread.sleep(timer);
					} catch (InterruptedException e) {
						break;
					}
				}
				log.info("Stopped sync task {} ({})",sourceTable.name,ctName);
			}
		};
		stopped = false;
		executor = Executors.newFixedThreadPool(1);
		executor.execute(runnable);
	}	
	
	/**
	* Sets the soft-delete age threshold: rows whose remover key timestamp is
	* older than this are physically deleted from the source table. The value is
	* a duration in milliseconds (parse "100h"/"3600s" style values with
	* ConfigProperties.getMilliSeconds before calling).
	* @param before - the age threshold in milliseconds
	* */
	public void setBefore(Long before){
	    this.before = before;
	}
	
	/**
	* Sets the worker round interval (sleep between cleanup rounds).
	* @param timer - the interval in milliseconds
	* */
	public void setTimer(Long timer){
	    this.timer = timer;
	}
	
	/**
	* Returns the past-tense action label used in round log messages.
	* @return the action label
	* */
	protected String getAction(){
	    return "removed";
	}	
	
}