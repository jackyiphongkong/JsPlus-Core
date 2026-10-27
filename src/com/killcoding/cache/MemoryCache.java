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

import com.killcoding.cache.AbsCacheApi;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;

import java.util.Set;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
* This cache class is save to memory
* */
public class MemoryCache extends AbsCacheApi {

	private static final Logger log = LoggerFactory.getLogger(MemoryCache.class);

	private final long PID = ProcessHandle.current().pid();

	private final static Map<String, Object> CACHE_VALUE = new ConcurrentHashMap<String, Object>();
	private final static Map<String, Date> CACHE_LIFE = new ConcurrentHashMap<String, Date>();

	private static boolean isStartRemoveExpiredThread = false;
	private static ExecutorService executor = null;

    /**
    * New a MemoryCache object
    * */
	public MemoryCache() {
		super();
	}

    /**
    * New a MemoryCache object
    * @param stored - 'true' is force write cache
    * */
	public MemoryCache(Boolean stored) {
		super();
		this.stored = stored;
	}

    /**
     * Start remove expired thread
     * */
	private synchronized void startRemoveExpiredThread() {
		// Double-checked guard: the unsynchronized caller may race here, so a
		// second thread must not rebuild the executor / spawn a second remover.
		if (isStartRemoveExpiredThread) return;
		isStartRemoveExpiredThread = true;
		executor = Executors.newFixedThreadPool(1);
		executor.execute(new Runnable() {
			@Override

			/*
			* Performs the run operation
			* */
			public void run() {
				Thread.currentThread().setName("MemoryCache");
				while (true) {

					if (Thread.currentThread().isInterrupted()) {
						break;
					}

					Set<String> names = CACHE_LIFE.keySet();
					for (String name : names) {
						Date current = new Date();
						Date life = CACHE_LIFE.get(name);
						if (life != null) {
    						if (current.getTime() > life.getTime()) {
    							remove(name);
    							log.debug("Removed(Expired) -> {}", name);
    						}
						}
					}
					try {
						Thread.sleep(TIMER);
					} catch (InterruptedException e) {
						log.warn(e);
						Thread.interrupted();
					}
				}
			}

		});
	}

	@Override

	/*
	* Performs the included operation
	* @param name - the name
	* @return the included result
	* */
	synchronized boolean included(String name) {
		if (stored) {
			return CACHE_VALUE.containsKey(name);
		}else{
			if (CACHE_LIFE.containsKey(name)) {
				Date current = new Date();
				Date life = CACHE_LIFE.get(name);
				if (life != null) {
    				if (current.getTime() > life.getTime()) {
    					return false;
    				} else {
    					return true;
    				}
				}
				return false;
			} else {
				return false;
			}
		}
	}

	@Override
	synchronized List<String> getNames() {
		Set<String> names = CACHE_VALUE.keySet();
		if (names != null) {
			return new ArrayList<String>(names);
		} else {
			return null;
		}
	}

	@Override

	/*
	* Performs the removeAll operation
	* */
	synchronized void removeAll() {
		Set<String> names = CACHE_VALUE.keySet();
		for (String name : names) {
			remove(name);
		}
	}

	@Override

	/*
	* Performs the remove operation
	* @param name - the name
	* */
	synchronized void remove(String name) {
		if (CACHE_VALUE.containsKey(name)) {
			CACHE_LIFE.remove(name);
			CACHE_VALUE.remove(name);
		}
	}

	@Override

	/*
	* Performs the get operation
	* @param name - the name
	* @return the get result
	* */
	synchronized Object get(String name) {
		try {
			if (included(name)) {
				Object cacheObjectValue = CACHE_VALUE.get(name);
				return cacheObjectValue;
			} else {
				return null;
			}
		} catch (Exception e) {
			log.warn(e);
			return null;
		}
	}

	@Override

	/*
	* Performs the set operation
	* @param name - the name
	* @param value - the value
	* @param lifeSeconds - the lifeSeconds
	* */
	synchronized void set(String name, Object value, int lifeSeconds) {
	    if(value != null) {

    	    if(lifeSeconds < 0){
    	        log.debug("Skip use cache, key '{}' life is '{}s'",name,lifeSeconds);
    	        return;
    	    }

    		try {
    			CACHE_VALUE.put(name, value);
    			if(!stored){
    			    if(lifeSeconds == 0){
        			    Date life = new Date(Long.MAX_VALUE);
        			    CACHE_LIFE.put(name, life);
    			    }
    			    if(lifeSeconds > 0){
    			        long lifeLong = System.currentTimeMillis() + lifeSeconds * 1000;

    			        if(lifeLong > Long.MAX_VALUE) lifeLong = Long.MAX_VALUE;

        			    Date life = new Date(lifeLong);
        			    CACHE_LIFE.put(name, life);
    			    }
    		    }
    		} catch (Exception e) {
    			log.warn(e);
    		}

    		if (!isStartRemoveExpiredThread)
    			startRemoveExpiredThread();

	    }
	}

}
