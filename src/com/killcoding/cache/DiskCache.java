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
import com.killcoding.tool.CommonTools;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;


import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.tool.FileTools;
import java.util.Set;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.text.SimpleDateFormat;


/**
* This cache class is save to hard disk
* */
public class DiskCache extends AbsCacheApi {

	private static final Logger log = LoggerFactory.getLogger(DiskCache.class);

	private final long PID = ProcessHandle.current().pid();

	private final static Map<String, String> CACHE_FILE = new ConcurrentHashMap<String, String>();
	private final static Map<String, Date> CACHE_LIFE = new ConcurrentHashMap<String, Date>();

	private File cacheTmpdir = null;
	private static boolean isStartRemoveExpiredThread = false;
	private static ExecutorService executor = null;

	/**
	* New a DiskCache object
	* */
	public DiskCache() {
		super();
		this.stored = false;
		String tmpdir = System.getProperty("java.io.tmpdir");
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		String time = df.format(new Date());
		String cacheTmp = String.format("%s/DiskCache/%s_%s_%s",tmpdir,PID,time,CommonTools.generateId(4));
		cacheTmpdir = new File(cacheTmp);
		try{
		    FileTools.deleteDirectory(cacheTmpdir);
		    cacheTmpdir.mkdirs();
		}catch(Exception e){
		    log.warn(e.getMessage(),e);
		}
	}

	/**
	* New a DiskCache object
	* @param stored - 'true' is force write cache
	* */
	public DiskCache(Boolean stored) {
		super();
		this.stored = stored;
		String tmpdir = System.getProperty("java.io.tmpdir");
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		String time = df.format(new Date());
		String cacheTmp = String.format("%s/DiskCache/%s_%s_%s",tmpdir,PID,time,CommonTools.generateId(4));
		cacheTmpdir = new File(cacheTmp);
		try{
		    FileTools.deleteDirectory(cacheTmpdir);
		    cacheTmpdir.mkdirs();
		}catch(Exception e){
		    log.warn(e.getMessage(),e);
		}
	}

	/**
	* Shutdown cahee thread
	* */
	public synchronized void shutdown(){
	    removeAll();
	    if(executor != null)
	        executor.shutdown();
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
			/*
			* Performs the run operation
			* */
			@Override
			public void run() {
				Thread.currentThread().setName("DiskCache");
				while(true){

				    if(Thread.currentThread().isInterrupted()){
				       break;
				    }

    				Set<String> names = CACHE_FILE.keySet();
    				for (String name : names) {
    					Date current = new Date();
    					Date life = CACHE_LIFE.get(name);
    					if(life != null){
        					if (current.getTime() > life.getTime()) {
        						remove(name);
        						log.debug("Removed(Expired) -> {}",name);
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

	/*
	* Performs the included operation
	* @param name - the name
	* @return the included result
	* */
	@Override
	synchronized boolean included(String name) {
	    if(stored){
	        return CACHE_FILE.containsKey(name);
	    }else{
    		if (CACHE_LIFE.containsKey(name)) {
    			Date current = new Date();
    			Date life = CACHE_LIFE.get(name);
    			if(life != null){
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
		Set<String> names = CACHE_FILE.keySet();
		if (names != null) {
			return new ArrayList<String>(names);
		} else {
			return null;
		}
	}

	/*
	* Performs the removeAll operation
	* */
	@Override
	synchronized void removeAll() {
		Set<String> names = CACHE_FILE.keySet();
		for (String name : names) {
			remove(name);
		}
	}

	/*
	* Performs the remove operation
	* @param name - the name
	* */
	@Override
	synchronized void remove(String name) {
		String cacheFileName = CACHE_FILE.get(name);
		if (cacheFileName != null) {
			File cacheFile = new File(String.format("%s/%s", cacheTmpdir.getAbsolutePath(), cacheFileName));
			CACHE_LIFE.remove(name);
			CACHE_FILE.remove(name);
			if (cacheFile.exists())
				cacheFile.delete();
		}
	}

	/*
	* Performs the get operation
	* @param name - the name
	* @return the get result
	* */
	@Override
	synchronized Object get(String name) {
		try {
			if (included(name)) {
				String cacheFileName = CACHE_FILE.get(name);
				File cacheFile = new File(String.format("%s/%s", cacheTmpdir.getAbsolutePath(), cacheFileName));
				if(cacheFile != null && cacheFile.exists()){
    				DataObject data = new DataObject(cacheFile.getAbsolutePath());
    				return data.getData();
				}
				return null;
			} else {
				return null;
			}
		} catch (Exception e) {
			log.warn(e);
			return null;
		}
	}

	/*
	* Performs the set operation
	* @param name - the name
	* @param value - the value
	* @param lifeSeconds - the lifeSeconds
	* */
	@Override
	synchronized void set(String name, Object value, int lifeSeconds) {
	    if(value == null){
			log.debug("Remove Cache: {}",name);
			remove(name);
		} else {

    	    if(lifeSeconds < 0){
    	        log.debug("Skip use cache, key '{}' life is '{}s'",name,lifeSeconds);
    	        return;
    	    }

    		try {
    			String cacheFileName = CommonTools.generateId(16);
    			CACHE_FILE.put(name, cacheFileName);
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
    			File cacheFile = new File(String.format("%s/%s", cacheTmpdir.getAbsolutePath(), cacheFileName));
    			DataObject data = new DataObject(cacheFile.getAbsolutePath());
    			data.setObject(value);
    		} catch (Exception e) {
    			log.warn(e);
    		}

    		if(!stored && !isStartRemoveExpiredThread)
    		    startRemoveExpiredThread();

	    }
	}

}
