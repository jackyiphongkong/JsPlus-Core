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

import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
* Sync task that propagates soft-delete state TO THE TARGET table. Like the
* Updater it compares windows by primary key and overwrites the target row
* with the source values when the source key timestamp (Table.Deleter.Key,
* default deleted_at) is newer; the 'deleted' flag (Table.Deleter.DeletedKey,
* default 'deleted') carried in the source row is what marks the row deleted
* on the target. It does NOT physically delete rows from the target.
* */
public class Deleter extends SqlStatement {

	private final static Logger log = LoggerFactory.getLogger(Deleter.class);

	public Deleter(Table table) {
		super(table);
	}

	private boolean contains(List<Map<String, Object>> targetBatchList, Map<String, Object> srcItem) {
		for (Map<String, Object> targetBatchItem : targetBatchList) {
			Object pk = targetBatchItem.get(primaryKey);
			Object spk = srcItem.get(primaryKey);
			if (pk != null && spk != null && pk.equals(spk)) {
				Object atObj = targetBatchItem.get(key);
				Object satObj = srcItem.get(key);

				// A null or non-Timestamp key on either side means "not comparable" -
				// treat the row as needing an update (conservative, never misses).
				if (!(atObj instanceof Timestamp) || !(satObj instanceof Timestamp)) return false;

				Timestamp at = (Timestamp) atObj;
				Timestamp sat = (Timestamp) satObj;

				if(at.getTime() < sat.getTime()) return false;
				
				if(at.getTime() >= sat.getTime()) return true;
				
			} else{
			    continue;
			}
		}
		return false;
	}

	private List<Map<String, Object>> compareBatchList(Date begin, Date end, List<Map<String, Object>> srcBatchList)
			throws Exception {
		final List<Map<String, Object>> compareBatchList = new ArrayList<Map<String, Object>>();

		if (srcBatchList == null)
			return compareBatchList;

		int size = srcBatchList.size();
		if (size > 0) {
			List<Map<String, Object>> targetBatchList = getTaskBatchList(begin, end);
			for (Map<String, Object> srcItem : srcBatchList) {
				boolean exist = contains(targetBatchList, srcItem);

				if (!exist)
					compareBatchList.add(srcItem);
			}
		}
		return compareBatchList;
	}

	private void executeBatch(List<Map<String, Object>> batchList) throws Exception {
	    if(log.isInfoEnabled()){
	        log.info("Delete -> {} ({})" ,table.name,batchList.size());
	    }	    
		String sqlFormat = "UPDATE %s SET %s WHERE %s = ? AND (%s < ? OR %s IS NULL)";
		Map<String, Object> firstItem = new HashMap<String,Object>(batchList.get(0));
		firstItem.remove(primaryKey);
		List<String> keys = new ArrayList<String>(firstItem.keySet());
		int keySize = keys.size();
		List<String> columns = new ArrayList<String>();
		for (String _key : keys) {
			columns.add(table.quote(_key) + " = ?");
		}
		String columnStr = String.join(",", columns);
		String sql = String.format(sqlFormat, table.getQuoteName(), columnStr, table.quote(primaryKey),
				table.quote(key),table.quote(key));
		List<List<Object>> records = new ArrayList<List<Object>>();
		for (Map<String, Object> item : batchList) {
			List<Object> params = new ArrayList<Object>();
			for (String _key : keys) {
				params.add(item.get(_key));
			}
			params.add(item.get(primaryKey));
			params.add(item.get(key));
			records.add(params);
		}
		Date begin = new Date();
		int rows = table.batch(sql, records);
		Date end = new Date();
		table.writeToTableLog("DELETE-BATCH",begin,end, rows);
	}

	@Override
	protected synchronized void executeTask(Date begin, Date end, Table targetTable) throws Exception{
		List<Map<String, Object>> taskBatchList = getTaskBatchList(begin, end);
		List<Map<String, Object>> compareBatchList = targetTable.deleter.compareBatchList(begin, end,
				taskBatchList);
		int size = compareBatchList.size();

		if (size > 0){
		    table.writeToTableLog("DELETE",begin,end, size);
			targetTable.deleter.executeBatch(compareBatchList);
		}
	}

	/**
	* Returns the past-tense action label used in round log messages.
	* @return the action label
	* */
	protected String getAction(){
	    return "deleted";
	}
	
}