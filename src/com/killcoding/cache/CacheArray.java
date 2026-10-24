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
package com.killcoding.cache;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import com.killcoding.tool.CommonTools;
import java.util.Collection;
import java.util.Collections;
import com.killcoding.cache.CacheArrayFilter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.killcoding.log.LoggerFactory;
import com.killcoding.log.Logger;
import com.killcoding.cache.CacheArray;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class use asynchronous caching to handle large result and large list.
 * Need class CacheArrayFilter to use.
 * */
public class CacheArray implements java.io.Serializable {
    
    private static Integer MAX_POOL_SIZE = 100;
    
    private static Integer USING_POOL_SIZE = 0;

	private static ExecutorService arrayPool = null;

	protected Object lastItem = null;

	private static boolean stop = false;
	
	private static boolean forceStop = false;
	
	private CacheArrayFilter filter = null;
	
	private boolean nullIsCompleted = true;
	
	protected Integer INDEX = -1;
	protected Integer SIZE = -1;
	protected Object OBJECT = null;
	protected Integer BATCH_INDEX = -1;
    protected final List BATCH = new ArrayList();
    
	/**
	 * All cache keys list
	 * */
	private final List<String> cacheKeys = new CopyOnWriteArrayList<String>();

	/**
	 * 'true' is completed
	 * */
	private boolean completed = false;

	/**
	 * New a CacheArray object
	 * */
	public CacheArray() {
		super();
		this.nullIsCompleted = true;
	}
	
	public CacheArray(boolean nullIsCompleted) {
		super();
		this.nullIsCompleted = nullIsCompleted;
	}	

	public static synchronized void initPool(int poolSize) {
		if (arrayPool == null) {
		    MAX_POOL_SIZE = poolSize;
		    Logger.systemMark(CacheArray.class,"MAX_POOL_SIZE={}",MAX_POOL_SIZE);
			arrayPool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
		}
	}
	
	public CacheArrayFilter filter(final long timer,final Runnable executeRun,final Runnable completedRun,final Runnable terminatedRun) {
	    CacheArray the = this;
	    the.filter = new CacheArrayFilter(timer){
	        @Override
        	public void execute(Integer index, Object object){
        	    the.INDEX = index;
        	    the.OBJECT = object;
        	    
        	    if(executeRun != null) executeRun.run();
        	}
        	@Override
        	public void completed(Integer size) {
        	    the.SIZE = size;
        	    
                if(completedRun != null) completedRun.run();
        	}   
        	@Override
        	public void terminated() {
        	    
        	    if(terminatedRun != null) terminatedRun.run();
        	}
	    };
	    filter(the.filter);
	    return the.filter;
	}

	public CacheArrayFilter filter(final long timer,final long loopTimer,final Runnable executeRun,final Runnable executeBatchRun,final Runnable completedRun,final Runnable terminatedRun) {
	    CacheArray the = this;
	    the.filter = new CacheArrayFilter(timer,loopTimer){
	        @Override
        	public void execute(Integer index, Object object){
        	    
        	    if(executeRun != null) executeRun.run();
        	}
        	
        	@Override
        	public void executeBatch(Integer index,List batch) {
        	    
                if(executeBatchRun != null) executeBatchRun.run();
        	}    
        	
        	@Override
        	public void completed(Integer size) {

                if(completedRun != null) completedRun.run();
        	}   
        	
        	@Override
        	public void terminated() {
        	    
        	    if(terminatedRun != null) terminatedRun.run();
        	}
	    };
	    filter(the.filter);
	    return the.filter;
	}
	/**
	 * Need override the method to process logic handle
	 * @param filter - CacheArrayFilter
	 * */
	public synchronized void filter(CacheArrayFilter _filter) {
	    this.filter = _filter;
        int usagePool = setUsingPoolSize(+1);
        if(usagePool > MAX_POOL_SIZE){
            LoggerFactory.getLogger(CacheArray.class).debug(String.format("Exceeds the entire pool size ('%s' > '%s').",usagePool,MAX_POOL_SIZE));
        }
		final CacheArray the = this;
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				int index = 0;
				try {
					while (!the.completed && !the.filter.isForceTerminated() && !isForceStoped()) {
					    
						if (Thread.currentThread().isInterrupted()){
						    the.filter.terminated = true;
						    the.filter.forceTerminated = true;
						    the.completed = false;
							break;
						}

						the.filter.setCacheArray(the);
						int size = the.size();
						while (index < size && !the.filter.isForceTerminated() && !isForceStoped() && !Thread.currentThread().isInterrupted()) {
							try {
        						if (Thread.currentThread().isInterrupted()){
        						    the.filter.terminated = true;
        						    the.filter.forceTerminated = true;
        						    the.completed = false;
        							break;
        						}			
        						
								if (forceStop) {
									the.filter.terminated = true;
									the.filter.forceTerminated = true;
									the.completed = false;
									break;
								}
								
								if (the.filter.forceTerminated){
								    the.completed = false;
									break;
								}
								
								Object t = the.get(index);
								if (t == null) {
									the.completed = true;
									break;
								}
								lastItem = t;
                        	    the.INDEX = index;
                        	    the.OBJECT = t;								
								BATCH.add(t);
								the.filter.execute(index, t);
								String cacheKey = the.getCacheKey(index);
								StoredCache.remove(cacheKey);
							} catch (Exception ee) {
								LoggerFactory.getLogger(CacheArray.class).warn(ee);
							}
							index++;
    						try {
    							Thread.sleep(the.filter.getTimer());
    						} catch (InterruptedException e) {
    							LoggerFactory.getLogger(CacheArray.class).warn(e);
    							the.filter.terminated = true;
    							the.completed = false;
    							break;
    						}							
						}
						BATCH_INDEX++;
						the.filter.executeBatch(BATCH_INDEX,BATCH);
						BATCH.clear();
						try {
							Thread.sleep(the.filter.getLoopTimer());
						} catch (InterruptedException e) {
							LoggerFactory.getLogger(CacheArray.class).warn(e);
							the.filter.terminated = true;
							the.completed = false;
							break;
						}						
					}
				} finally {
				    
					if(the.completed) {
					    the.SIZE = index;
					    the.filter.completed(index);
					    setUsingPoolSize(-1);
					}
					
					if(the.filter.terminated){
					    the.filter.terminated();
					    setUsingPoolSize(-1);
					}
					
					the.removeAll();
					
					Thread.currentThread().interrupt();
				}

			}
		};
		if (arrayPool == null) {
		    Logger.systemMark(CacheArray.class,"MAX_POOL_SIZE={}",MAX_POOL_SIZE);
		    arrayPool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
		}
		arrayPool.execute(runnable);
	}

	/**
	 * Get all cache keys
	 * @return List
	 * */
	public synchronized List<String> getCacheKeys() {
		return new ArrayList<String>(Collections.synchronizedList(cacheKeys));
	}

	/**
	 * Get all cache size
	 * @reteurn int
	 * */
	public synchronized int size() {
		return cacheKeys.size();
	}

	/**
	 * Add object to the cache list
	 * @return - return cacke key
	 * @value - It is object value
	 * */
	public synchronized String add(Object value) {
	    if(nullIsCompleted || (!nullIsCompleted && value != null)) {
    	    if(this.filter != null && !this.filter.isForceTerminated() && !isForceStoped()){
        		String key = CommonTools.generateId(16);
        		StoredCache.set(key, value);
        		cacheKeys.add(key);
        		return key;
    	    }
	    }
	    return null;
	}
	
	/**
	 * Add object to the cache list
	 * @return - return cacke key
	 * @value - It is object value
	 * @sleepMs - It is object value
	 * @throws InterruptedException
	 * */
	public synchronized String add(Object value,long sleepMs) throws InterruptedException {
	    String key = add(value);
	    
	    if(sleepMs > 0) Thread.sleep(sleepMs);
	    
	    return key;
	}	

	/**
	 * Add list to the cache list
	 * @param list - It is object list value
	 * */
	public synchronized void addAll(List list) {
		for (Object value : list) {
			add(value);
		}
	}

	/**
	 * Remove cache list index object
	 * @return - return removed cache key name
	 * @param index - It is remove index cache
	 * */
	public synchronized String remove(int index) {
		String key = cacheKeys.get(index);
		StoredCache.remove(key);
		cacheKeys.remove(index);
		return key;
	}

	/**
	 * Remove all cache list
	 * */
	public synchronized void removeAll() {
		for (String key : cacheKeys) {
			StoredCache.remove(key);
		}
		cacheKeys.clear();
	}

	/**
	 * Get cache value by index
	 * @return - Cache value
	 * @param index - Cache index
	 * */
	public synchronized Object get(int index) {
		String key = cacheKeys.get(index);
		return StoredCache.get(key);
	}

	/**
	 * Get cache key by index
	 * @return - Cache key
	 * @param index - Cache index
	 * */
	public synchronized String getCacheKey(int index) {
		return cacheKeys.get(index);
	}

	/**
	 * Check complete status
	 * @return - 'true' is completed
	 * */
	public synchronized Boolean isCompleted() {
		return completed;
	}
	
	public static Boolean isStoped() {
		return stop;
	}	
	
	public static Boolean isForceStoped() {
		return forceStop;
	}	
	
	public static void stop() {
		stop = true;
	}
	
	public static void forceStop() {
		stop = true;
		forceStop = true;
	}	
	
	public static void start() {
		stop = false;
	}
	
	public void terminate(){
	    if(filter != null) filter.terminate();
	}

	public void forceTerminate(){
	    if(filter != null) filter.forceTerminate();
	}
	
	public Boolean isTerminated(){
	    return filter == null ? true : filter.isTerminated();
	}
	
	public Boolean isForceTerminated(){
	    return filter == null ? true : filter.isForceTerminated();
	}	
	
	public CacheArrayFilter getFilter(){
	    return filter;
	}
	
	public Integer getIndex(){
	    return INDEX;
	}
	
	public Integer getSize(){
	    return SIZE;
	}
	
	public Object getObject(){
	    return OBJECT;
	}		

	public Object getBatch(){
	    return BATCH;
	}	
	
	public Integer getBatchIndex(){
	    return BATCH_INDEX;
	}
	
	private static synchronized Integer setUsingPoolSize(int value){
	    return USING_POOL_SIZE += value;
	}
	
	public static synchronized Integer getUsingPoolSize(){
	    return USING_POOL_SIZE;
	}	

	public static Integer getMaxPoolSize(){
	    return MAX_POOL_SIZE;
	}	
	
	public static synchronized double getUsage(){
	    return getUsingPoolSize()/(MAX_POOL_SIZE*1.0D);
	}
	
	public boolean isNulIsCompleted(){
	    return nullIsCompleted;
	}
	
	public void setNulIsCompleted(boolean nullIsCompleted){
	    this.nullIsCompleted = nullIsCompleted;
	}	
}