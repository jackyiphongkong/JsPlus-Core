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

import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;

import java.util.List;

/**
* This class is Cache entry
* */
public final class Cache {

    /**
     * Default is use mode 'MemoryCache'
     * */
	private static AbsCacheApi api = null;
	private static final Logger log = LoggerFactory.getLogger(Cache.class);

	/**
	 * Set cache mode
	 * @param _api - It is class 'MemoryCache' or 'DiskCache'
	 * */
    public synchronized static void setApi(AbsCacheApi _api){
        api = _api;
    }

 	/**
	 * Set detect cache timer (Default is 100MS)
	 * @param timer - It is detect cache timer
	 * */
    public static void setTimer(long timer) {
        AbsCacheApi.TIMER = timer;
    }

    /**
     * Set a cache
     * @param name - It is cache key name
     * @param value - It is cache value
     * @param life - It is life length seconds
     * */
	public synchronized static void set(String name,Object value,int life){
	    if(api == null) setApi(new MemoryCache());

		if(life > 0){
    		log.debug("Set Cache: {}",name);
    		api.set(name, value, life);
		}
	}

    /**
     * Set a cache
     * @param name - It is cache key name
     * @param value - It is cache value
     * */
	public synchronized static void set(String name,Object value){
	    if(api == null) setApi(new MemoryCache());

		log.debug("Set Cache: {}",name);
		api.set(name, value, 0);
	}

	/**
	 * Get a cache value
	 * @return Object - It is cache value
	 * @param name - It is cache key name
	 * */
	public synchronized static Object get(String name){
	    if(api == null) setApi(new MemoryCache());

		log.debug("Get Cache: {}",name);
		return api.get(name);
	}

	/**
	 * Check is included cache key
	 * @return boolean - 'true' is included, 'false' is not included.
	 * @param name - It is cache key name
	 * */
	public synchronized static boolean included(String name){
	    if(api == null) setApi(new MemoryCache());

		log.debug("Included Cache: {}",name);
		boolean b = api.included(name);
		log.debug("Included: {}",b);
		return b;
	}

	/**
	 * Remove a cache
	 * @param name - It is cache key name
	 * */
	public synchronized static void remove(String name){
	    if(api == null) setApi(new MemoryCache());

		log.debug("Remove Cache: {}",name);
		api.remove(name);
	}

	/**
	 * Remove all cache
	 * */
	public synchronized static void removeAll(){
		log.debug("Remove All Cache");
		api.removeAll();
	}

	/**
	 * Get all cache key name list
	 * @return List&lt;String&gt; - It is key name list
	 * */
	public synchronized static List<String> getNames(){
		return api.getNames();
	}

}
