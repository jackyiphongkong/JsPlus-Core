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
package com.killcoding.cache;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

import com.killcoding.tool.CommonTools;

import java.util.Collections;
import com.killcoding.cache.CacheArrayFilter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.killcoding.log.LoggerFactory;
import com.killcoding.log.Logger;
import com.killcoding.cache.CacheArray;
import java.util.concurrent.CopyOnWriteArrayList;

/**
* Asynchronous cache-list for large results / large collections.
* Thread model:
*   - Producer(s): call add()/addAll() - synchronized, append-only.
*   - One worker per filter(): drains items on a shared fixed thread pool,
*     invoking CacheArrayFilter.execute()/executeBatch()/completed()/
*     terminated() on the worker thread (never call JS proxies from there -
*     GraalJS forbids worker-thread JS entry; use a Runnable proxy + the
*     owning console's callback queue instead).
*   - Consumer(s): poll getIndex()/getObject()/isCompleted()/getProcessedTotal()
*     from the owning (producer) thread; the volatile fields guarantee
*     visibility of the worker's writes.
* Lifecycle: initPool() optional -> filter() starts the worker -> the worker
* ends when the producer feed is exhausted (completed), on forceTerminate(),
* or when CacheArray.forceStop() is set globally. shutdownPool() releases the
* shared executor (lazily re-created on the next filter()).
* */
public class CacheArray implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
    
    private static Integer MAX_POOL_SIZE = 100;
    
    private static Integer USING_POOL_SIZE = 0;

	private static ExecutorService arrayPool = null;

	protected transient volatile Object lastItem = null;

	private static volatile boolean stop = false;
	
	private static volatile boolean forceStop = false;
	
	private transient volatile CacheArrayFilter filter = null;
	
	private boolean nullIsCompleted = true;
	
	protected volatile Integer INDEX = -1;
	protected volatile Integer SIZE = -1;

	/** Global count of items processed by the filter thread. sweepProcessed()
	 * never resets this counter, so callers can still observe a monotonic
	 * order (getProcessedTotal()) even though the local index (getIndex())
	 * cycles back to 0 after each head sweep. */
	private long processedTotal = 0;
	protected transient volatile Object OBJECT = null;
	protected volatile Integer BATCH_INDEX = -1;
    protected transient final List<Object> BATCH = new ArrayList<Object>();
    
	/**
	 * All cache keys list
	 * */
	private transient final List<String> cacheKeys = new CopyOnWriteArrayList<String>();

	/**
	 * 'true' is completed
	 * */
	private volatile boolean completed = false;

	/**
	 * New a CacheArray object
	 * */
	public CacheArray() {
		super();
		this.nullIsCompleted = true;
	}
	
	/**
	* Constructs a new CacheArray
	* @param nullIsCompleted - the nullIsCompleted
	* */
	public CacheArray(boolean nullIsCompleted) {
		super();
		this.nullIsCompleted = nullIsCompleted;
	}	

	/**
	* Performs the initPool operation
	* @param poolSize - the poolSize
	* */
	public static synchronized void initPool(int poolSize) {
		if (arrayPool == null) {
		    MAX_POOL_SIZE = poolSize;
		    Logger.systemMark(CacheArray.class,"MAX_POOL_SIZE={}",MAX_POOL_SIZE);
			arrayPool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
		}
	}
	
	/**
	 * Shut down the shared worker pool. Idempotent: safe to call multiple
	 * times; subsequent filter() calls lazily re-create the pool via initPool().
	 * */
	public static synchronized void shutdownPool() {
		if (arrayPool != null) {
			arrayPool.shutdown();
			arrayPool = null;
		}
	}
	
	/**
	* Performs the filter operation
	* @param timer - the timer
	* @param executeRun - the executeRun
	* @param completedRun - the completedRun
	* @param terminatedRun - the terminatedRun
	* @return the filter result
	* */
	public CacheArrayFilter filter(final long timer,final Runnable executeRun,final Runnable completedRun,final Runnable terminatedRun) {
	    CacheArray the = this;
	    the.filter = new CacheArrayFilter(timer){
	        @Override

        	/*
        	* Performs the execute operation
        	* @param index - the index
        	* @param object - the object
        	* */
        	public void execute(Integer index, Object object){
        	    the.INDEX = index;
        	    the.OBJECT = object;
        	    
        	    if(executeRun != null) executeRun.run();
        	}
        	@Override

        	/*
        	* Performs the completed operation
        	* @param size - the size
        	* */
        	public void completed(Integer size) {
        	    the.SIZE = size;
        	    
                if(completedRun != null) completedRun.run();
        	}   
        	@Override

        	/*
        	* Performs the terminated operation
        	* */
        	public void terminated() {
        	    
        	    if(terminatedRun != null) terminatedRun.run();
        	}
	    };
	    filter(the.filter);
	    return the.filter;
	}

	/**
	* Performs the filter operation
	* @param timer - the timer
	* @param loopTimer - the loopTimer
	* @param executeRun - the executeRun
	* @param executeBatchRun - the executeBatchRun
	* @param completedRun - the completedRun
	* @param terminatedRun - the terminatedRun
	* @return the filter result
	* */
	public CacheArrayFilter filter(final long timer,final long loopTimer,final Runnable executeRun,final Runnable executeBatchRun,final Runnable completedRun,final Runnable terminatedRun) {
	    CacheArray the = this;
	    the.filter = new CacheArrayFilter(timer,loopTimer){
	        @Override

        	/*
        	* Performs the execute operation
        	* @param index - the index
        	* @param object - the object
        	* */
        	public void execute(Integer index, Object object){
        	    
        	    if(executeRun != null) executeRun.run();
        	}
        	
        	@Override

        	/*
        	* Performs the executeBatch operation
        	* @param index - the index
        	* @param batch - the batch
        	* */
        	public void executeBatch(Integer index,List<?> batch) {
        	    
                if(executeBatchRun != null) executeBatchRun.run();
        	}    
        	
        	@Override

        	/*
        	* Performs the completed operation
        	* @param size - the size
        	* */
        	public void completed(Integer size) {

                if(completedRun != null) completedRun.run();
        	}   
        	
        	@Override

        	/*
        	* Performs the terminated operation
        	* */
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

			/*
			* Performs the run operation
			* */
			public void run() {
				boolean usageReturned = false;
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
                        	    the.processedTotal++;								
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
						// Reclaim the processed head items now that the batch
						// has been persisted; keep the local index in sync so
						// newly appended rows start again from position 0.
						int swept = the.sweepProcessed(index);
						index -= swept;
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
					    usageReturned = true;
					}
					
					if(the.filter.terminated){
					    the.filter.terminated();
					    if(!usageReturned) {
					        setUsingPoolSize(-1);
					        usageReturned = true;
					    }
					}
					
					// Guard every other exit path: the pool usage slot must
					// always be returned exactly once (never leaked, never
					// double-released).
					if(!usageReturned) setUsingPoolSize(-1);
					
					the.removeAll();
					
					// NOTE: do NOT Thread.currentThread().interrupt() here - the
					// worker runs on a shared fixed-thread-pool thread; marking
					// it interrupted makes the next task on that thread exit
					// immediately at its first isInterrupted() check.
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
	    // Reject adds once the worker has completed - otherwise data appended
	    // after completion would be swept away by the worker's final removeAll().
	    if(!completed && (nullIsCompleted || (!nullIsCompleted && value != null))) {
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
	public synchronized void addAll(List<?> list) {
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
	 * Reclaim 'count' items from the head of the cache after the filter
	 * thread has persisted the batch. Thread-safe under concurrent add():
	 * adds always append to the tail, this method only touches the head and
	 * is synchronized, so new rows can never be swept away. The filter
	 * thread must call this AFTER executeBatch (i.e. after the batch has
	 * been persisted) and must keep its local index in sync.
	 * @return - the number of items actually removed
	 * @param count - number of processed head items to sweep
	 * */
	public synchronized int sweepProcessed(int count) {
		if (count <= 0 || cacheKeys.isEmpty()) {
			return 0;
		}
		if (count > cacheKeys.size()) {
			count = cacheKeys.size();
		}
		List<String> removed = new ArrayList<String>(count);
		for (int i = 0; i < count; i++) {
			String key = cacheKeys.get(i);
			StoredCache.remove(key);
			removed.add(key);
		}
		cacheKeys.removeAll(removed);   // COW list: one snapshot pass, O(n), no per-item shift
		return count;
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
	
	/**
	* Performs the isStoped operation
	* @return the isStoped result
	* */
	public static Boolean isStoped() {
		return stop;
	}	
	
	/**
	* Performs the isForceStoped operation
	* @return the isForceStoped result
	* */
	public static Boolean isForceStoped() {
		return forceStop;
	}	
	
	/**
	* Performs the stop operation
	* */
	public static void stop() {
		stop = true;
	}
	
	/**
	* Performs the forceStop operation
	* */
	public static void forceStop() {
		stop = true;
		forceStop = true;
	}	
	
	/**
	* Performs the start operation
	* */
	public static void start() {
		stop = false;
		forceStop = false;
	}
	
	/**
	* Performs the terminate operation
	* */
	public void terminate(){
	    if(filter != null) filter.terminate();
	}

	/**
	* Performs the forceTerminate operation
	* */
	public void forceTerminate(){
	    if(filter != null) filter.forceTerminate();
	}
	
	/**
	* Performs the isTerminated operation
	* @return the isTerminated result
	* */
	public Boolean isTerminated(){
	    return filter == null ? true : filter.isTerminated();
	}
	
	/**
	* Performs the isForceTerminated operation
	* @return the isForceTerminated result
	* */
	public Boolean isForceTerminated(){
	    return filter == null ? true : filter.isForceTerminated();
	}	
	
	/**
	* Performs the getFilter operation
	* @return the getFilter result
	* */
	public CacheArrayFilter getFilter(){
	    return filter;
	}
	
	/**
	* Performs the getIndex operation
	* @return the getIndex result
	* */
	public Integer getIndex(){
	    return INDEX;
	}

	/** Monotonic count of items processed by the filter thread (never reset
	 * by sweepProcessed()); the counterpart of getIndex(), which is local to
	 * the current sweep cycle. */
	public synchronized long getProcessedTotal(){
	    return processedTotal;
	}
	
	/**
	* Performs the getSize operation
	* @return the getSize result
	* */
	public Integer getSize(){
	    return SIZE;
	}
	
	/**
	* Performs the getObject operation
	* @return the getObject result
	* */
	public Object getObject(){
	    return OBJECT;
	}		

	/**
	* Performs the getBatch operation
	* @return the getBatch result
	* */
	public Object getBatch(){
	    return BATCH;
	}	
	
	/**
	* Performs the getBatchIndex operation
	* @return the getBatchIndex result
	* */
	public Integer getBatchIndex(){
	    return BATCH_INDEX;
	}
	
	/**
	* Performs the setUsingPoolSize operation
	* @param value - the value
	* @return the setUsingPoolSize result
	* */
	private static synchronized Integer setUsingPoolSize(int value){
	    return USING_POOL_SIZE += value;
	}
	
	/**
	* Performs the getUsingPoolSize operation
	* @return the getUsingPoolSize result
	* */
	public static synchronized Integer getUsingPoolSize(){
	    return USING_POOL_SIZE;
	}	

	/**
	* Performs the getMaxPoolSize operation
	* @return the getMaxPoolSize result
	* */
	public static Integer getMaxPoolSize(){
	    return MAX_POOL_SIZE;
	}	
	
	/**
	* Performs the getUsage operation
	* @return the getUsage result
	* */
	public static synchronized double getUsage(){
	    return getUsingPoolSize()/(MAX_POOL_SIZE*1.0D);
	}
	
	/**
	* Performs the isNulIsCompleted operation
	* @return the isNulIsCompleted result
	* */
	public boolean isNulIsCompleted(){
	    return nullIsCompleted;
	}
	
	/**
	* Performs the setNulIsCompleted operation
	* @param nullIsCompleted - the nullIsCompleted
	* */
	public void setNulIsCompleted(boolean nullIsCompleted){
	    this.nullIsCompleted = nullIsCompleted;
	}	
}
