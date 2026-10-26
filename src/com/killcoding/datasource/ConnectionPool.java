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

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.killcoding.log.LoggerFactory;

import java.util.Date;
import java.sql.Connection;
import java.util.HashMap;

/**
 * This class functions as a connection pool counter
 * */
public class ConnectionPool {

	/**
	 * Record all connection hash code
	 * */
	private final Map<Long, Integer> CONNECTION_POOL = new ConcurrentHashMap<Long, Integer>();
	
	private final Map<Integer, String> CONNECTION_THREAD_MARK = new ConcurrentHashMap<Integer, String>();
	private final Map<Integer, Connection> CONNECTION_MARK = new ConcurrentHashMap<Integer, Connection>();
	private final Map<Integer, Date> CONNECTION_OPENED_AT_MARK = new ConcurrentHashMap<Integer, Date>();
	private final Map<Integer, Date> CONNECTION_USING_AT_MARK = new ConcurrentHashMap<Integer, Date>();
	private final Map<Integer, Date> CONNECTION_IDLE_BEGIN_MARK = new ConcurrentHashMap<Integer, Date>();	

	/**
	 * New a ConnectionPool object
	 * */
	public ConnectionPool() {
		super();
	}

	/**
	 * List all connection hash code
	 * @return Map - All connection hash code map
	 * */
	public synchronized Map<Long,Integer> map() {
	   return new HashMap<Long,Integer>(CONNECTION_POOL);
	}
	
	/**
	 * List all connection hash code
	 * @return List - All connection hash code
	 * */
	public synchronized List<Integer> list() {
	   List<Integer> list = new ArrayList<Integer>();
       List<Long> klist = CONNECTION_POOL.keySet()
            .stream()
            .sorted()
            .collect(Collectors.toList());
	   for(Object k : klist){
	       list.add((Integer)CONNECTION_POOL.get(k));
	   }
	   return list;
	}

	/**
	 * Get connection pool size
	 * @return int - All connection size
	 * */
	public synchronized int size() {
		return CONNECTION_POOL.keySet().size();
	}


	protected synchronized boolean add(Connection conn) {
	    Integer connHashCode = conn.hashCode();
	    if(!contains(connHashCode)){
    	    Long newKey = System.nanoTime();
    	    while(CONNECTION_POOL.containsKey(newKey)){
    	        newKey = System.nanoTime();
    	    }
    	    
    		final Date openedAt = new Date();
    		CONNECTION_IDLE_BEGIN_MARK.put(connHashCode, openedAt);
    		CONNECTION_OPENED_AT_MARK.put(connHashCode, openedAt);
    		CONNECTION_MARK.put(connHashCode, conn);	     	    
    	    CONNECTION_POOL.put(newKey,connHashCode);
    	    return true;
	    }
	    return false;
	}

	protected synchronized void freed(Integer connHashCode) {
	    if(!contains(connHashCode)){
    	    Long newKey = System.nanoTime();
    	    while(CONNECTION_POOL.containsKey(newKey)){
    	        newKey = System.nanoTime();
    	    }
    	    CONNECTION_POOL.put(newKey,connHashCode);
	    }
	    CONNECTION_IDLE_BEGIN_MARK.put(connHashCode, new Date());
	}

	protected synchronized void removeAll(Integer connHashCode) {
		CONNECTION_MARK.remove(connHashCode);
		CONNECTION_OPENED_AT_MARK.remove(connHashCode);
		CONNECTION_USING_AT_MARK.remove(connHashCode);
		CONNECTION_IDLE_BEGIN_MARK.remove(connHashCode);
		CONNECTION_THREAD_MARK.remove(connHashCode);	    
        remove(connHashCode);
	}
	
	protected synchronized Boolean remove(Integer connHashCode) {
        List klist = new ArrayList(CONNECTION_POOL.keySet());
        Object rmKey = -1;
        int size = klist.size();
	    for(int i = 0;i < size;i++){
	       Object key = klist.get(i);
	       Object hashCode = (Integer)CONNECTION_POOL.get(key);
	       if(hashCode.equals(connHashCode)){
	           rmKey = key;
	           break;
	       }
	    }
	    if(rmKey.equals(-1)){
	        return false;
	    }else{
    	    Object rmHashCode = CONNECTION_POOL.remove(rmKey);
    	    return rmHashCode != null;
	    }
	}	

	public synchronized boolean contains(Integer connHashCode) {
		return list().contains(connHashCode);
	}

	private synchronized Integer get() {
	    try{
    	    Integer hashCode =  list().get(0);
    	    remove(hashCode);
    	    return hashCode;
	    }catch(Exception e){
	        LoggerFactory.getLogger(ConnectionPool.class).error(e.getMessage(),e);
	        return null;
	    }
	}
	
	public synchronized Integer getOpenConnectionSize() {
		return CONNECTION_OPENED_AT_MARK.keySet().size();
	}	
	
	public synchronized Map<Integer, Date> getConnectionOpenedAtMark(){
	    return new HashMap<Integer, Date>(CONNECTION_OPENED_AT_MARK);
	}
	
	protected synchronized Object removeConnectionOpenedAtMark(Integer hashCode){
	     return CONNECTION_OPENED_AT_MARK.remove(hashCode);
	}	
	
	protected synchronized Object setConnectionOpenedAtMark(Integer hashCode,Date date){
	     return CONNECTION_OPENED_AT_MARK.put(hashCode,date);
	}	
	
	public synchronized Map<Integer, Date> getConnectionUsingAtMark(){
	    return new HashMap<Integer, Date>(CONNECTION_USING_AT_MARK);
	}
	
	protected synchronized Object removeConnectionUsingAtMark(Integer hashCode){
	     return CONNECTION_USING_AT_MARK.remove(hashCode);
	}
	
	protected synchronized Object setConnectionUsingAtMark(Integer hashCode,Date date){
	     return CONNECTION_USING_AT_MARK.put(hashCode,date);
	}

	protected synchronized Map<Integer, Connection> getConnectionMark(){
	    return CONNECTION_MARK;
	}
	
	protected synchronized Connection getConnection(){
    	Thread ct = Thread.currentThread();
    	String tn = ct.getName();
    	long tid = ct.getId();	    
    	Integer hashCode = get();
    	
    	if(hashCode == null) return null;
    	
	    CONNECTION_USING_AT_MARK.put(hashCode, new Date());
    	CONNECTION_IDLE_BEGIN_MARK.remove(hashCode);
    	CONNECTION_THREAD_MARK.put(hashCode, String.format("%s-%s", tn, tid));
	    return CONNECTION_MARK.get(hashCode);
	}	
	
	protected synchronized Object removeConnectionMark(Integer hashCode){
	     return CONNECTION_MARK.remove(hashCode);
	}
	
	protected synchronized Object setConnectionMark(Integer hashCode,Connection conn){
	     return CONNECTION_MARK.put(hashCode,conn);
	}
	
	public synchronized Map<Integer, Date> getConnectionIdleBeginMark(){
	    return new HashMap<Integer,Date>(CONNECTION_IDLE_BEGIN_MARK);
	}
	
	protected synchronized Object removeConnectionIdleBeginMark(Integer hashCode){
	     return CONNECTION_IDLE_BEGIN_MARK.remove(hashCode);
	}
	
	protected synchronized Object setConnectionIdleBeginMark(Integer hashCode,Date date){
	     return CONNECTION_IDLE_BEGIN_MARK.put(hashCode,date);
	}
	
	public synchronized Map<Integer, String> getConnectionThreadMark(){
	    return new HashMap<Integer, String>(CONNECTION_THREAD_MARK);
	}

}