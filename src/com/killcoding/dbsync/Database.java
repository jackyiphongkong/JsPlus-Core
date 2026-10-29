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

import com.killcoding.datasource.DriverDataSource;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.tool.ConfigProperties;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;


/**
* Database sync facade - manages a source or target database and drives per-table sync jobs.
* */
public class Database {

	private final static Logger log = LoggerFactory.getLogger(Database.class);
	private DataSource dataSource = null;

	protected List<Table> syncingTables = new ArrayList<Table>();

	protected String schema = null;
	protected Boolean quoteEnable = false;
	protected String quoteFormat = null;
	protected Boolean quoteToUpperCase = null;

	protected String sourceOrTarget = null;
	protected ConfigProperties porps = null;

	private final Double DEFAULT_MAX_USAGE = 0.0D;
	protected Double maxUsage = DEFAULT_MAX_USAGE;
	

	/**
	* Creates a database facade with an instance-level configuration - the JS-friendly
	* entry (no global/static config is read). The DataSource file name is resolved
	* against ROOT_PATH/config/<fileName>.
	* @param sourceOrTarget - "Source" or "Target" (or any custom name), the prefix
	*        under which the database keys are read (e.g. Source.Database.DataSource)
	* @param porps - the configuration store for THIS instance (never shared; a null
	*        value falls back to an empty store so the missing-config behaviour is explicit)
	* */
	public Database(String sourceOrTarget, ConfigProperties porps) throws Exception {
		super();
		this.sourceOrTarget = sourceOrTarget;
		this.porps = porps == null ? new ConfigProperties() : porps;
		
	    String appDataSourceKey = String.format("%s.Database.DataSource", this.sourceOrTarget);
		String schemaKey = String.format("%s.Database.Schema", this.sourceOrTarget);
		String quoteEnableKey = String.format("%s.Database.Quote.Enable", this.sourceOrTarget);
		String quoteFormatKey = String.format("%s.Database.Quote.Format", this.sourceOrTarget);
		String quoteToUpperCaseKey = String.format("%s.Database.Quote.ToUpperCase", this.sourceOrTarget);
		String maxUsageKey = String.format("%s.Database.MaxUsage", this.sourceOrTarget);

		String appDataSource = this.porps.getString(appDataSourceKey);
		this.schema = this.porps.getString(schemaKey);
		this.quoteEnable = this.porps.getBoolean(quoteEnableKey,true);
		this.quoteFormat = this.porps.getString(quoteFormatKey,"%s");
		this.quoteToUpperCase = this.porps.getBoolean(quoteToUpperCaseKey,false);
		
		this.maxUsage = this.porps.getDouble(maxUsageKey,DEFAULT_MAX_USAGE);
		
		String appDsFile = String.format("%s/config/%s", Enter.ROOT_PATH,appDataSource);
		dataSource = new DriverDataSource(new File(appDsFile));
	}

	public ConfigProperties getPorps() {
		return this.porps;
	}

	public String getSourceOrTarget() {
		return this.sourceOrTarget;
	}

	/**
	* Resolves the configured table list and returns a Table facade for each
	* name (see Table.getTablesList()).
	* @return the table facades, or null when the list cannot be resolved
	* */
	public List<Table> getTables() {
		try {
			List<Table> tables = new ArrayList<Table>();
			Table initTable = new Table(this);
			List<String> tableNames = initTable.getTablesList();
			for (String tableName : tableNames) {
				tables.add(new Table(this, tableName));
			}
			return tables;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	* Returns a Table facade for the named table when it is part of the
	* configured table list; otherwise null.
	* @param name - the table name
	* @return the Table facade, or null when not configured/found
	* */
	public Table getTable(String name) {
		try {
			Table initTable = new Table(this);
			List<String> tableNames = initTable.getTablesList();
			for (String tableName : tableNames) {
				if (tableName.equals(name))
					return new Table(this, tableName);
			}
			return null;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}
	
	/**
	* Opens a raw connection from this facade's DataSource.
	* @return the new connection
	* */
	protected Connection open() throws SQLException {
	    return dataSource.getConnection();
	}
	
	/**
	* Returns the current connection-pool usage of this facade's DataSource
	* (0 when the datasource does not expose usage).
	* @return usage ratio (0.0-1.0 style value from DriverDataSource)
	* */
	public synchronized double getUsage(){
	    if(dataSource instanceof DriverDataSource){
	        DriverDataSource dds = (DriverDataSource)dataSource;
	        return dds.getUsage();
	    }
	    return 0;
	}
	
	/**
	* Whether a new connection may be created right now: false when
	* Source/Target.Database.MaxUsage > 0 and the current usage reached it.
	* @return true when allowed (or when no limit is configured)
	* */
	public synchronized boolean allowedUseConnection(){
	    if(maxUsage > 0){
	        return getUsage() < maxUsage;
	    }
	    return true;
	}

	/**
	* Starts the per-table sync workers against the target database
	* (inserter/updater/deleter/remover per Table config) and tracks the queued
	* tables so stopSync() can terminate them.
	* @param target - the target Database facade
	* */
	public void syncTo(Database target) {
		List<Table> tables = getTables();
		for (Table table : tables) {
			if (table != null) {
				log.debug("syncTo={}",table.name);
				Table targetTable = target.getTable(table.name);
				table.syncTo(targetTable);
				syncingTables.add(table);
				try{
				    Thread.sleep(table.delayStart);
				}catch(InterruptedException e){
				    log.error(e.getMessage(),e);
				}
			}
		}
		int size = 0;
		if (tables != null) {
		    size = tables.size();
		}
		log.mark("Queued tables {}",size);
	}

	/**
	* Stops every periodic sync worker started by syncTo(...) on this database
	* (inserter/updater/deleter/remover of each queued table). Safe to call when
	* nothing is syncing.
	* */
	public void stopSync() {
		for (Table table : syncingTables) {
			if (table != null) {
				table.stopAll();
			}
		}
		syncingTables.clear();
		log.mark("Stopped sync workers");
	}

}