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
* This class is a permanent cache storage that requires manual deletion of the cache
* */
public final class StoredCache {

	private static AbsCacheApi api = null;
	private static final Logger log = LoggerFactory.getLogger(StoredCache.class);

	/**
	* Performs the setApi operation
	* @param _api - the _api
	* */
	public synchronized static void setApi(AbsCacheApi _api){
	    api = _api;
	}

	/**
	* Performs the getApi operation
	* @return the getApi result
	* */
	public static AbsCacheApi getApi(){
	    return api;
	}

	/**
	* Performs the set operation
	* @param name - the name
	* @param value - the value
	* */
	public synchronized static void set(String name,Object value){
	    if(api == null) setApi(new MemoryCache(true));

		if(value == null){
			log.debug("Remove Cache: {}",name);
			api.remove(name);
		}else{
			log.debug("Set Cache: {}",name);
			api.set(name, value, Integer.MAX_VALUE);
		}

	}

	/**
	* Performs the get operation
	* @param name - the name
	* @return the get result
	* */
	public synchronized static Object get(String name){
	    if(api == null) setApi(new MemoryCache(true));

		log.debug("Get Cache: {}",name);
		return api.get(name);
	}

	/**
	* Performs the included operation
	* @param name - the name
	* @return the included result
	* */
	public synchronized static boolean included(String name){
	    if(api == null) setApi(new MemoryCache(true));

		log.debug("Included Cache: {}",name);
		boolean b = api.included(name);
		log.debug("Included: {}",b);
		return b;
	}

	/**
	* Performs the remove operation
	* @param name - the name
	* */
	public synchronized static void remove(String name){
	    if(api == null) setApi(new MemoryCache(true));

		log.debug("Remove Cache: {}",name);
		api.remove(name);
	}

	/**
	* Performs the removeAll operation
	* */
	public synchronized static void removeAll(){
	    if(api == null) setApi(new MemoryCache(true));

		log.debug("Remove All Cache");
		api.removeAll();
	}

	/**
	* Returns the names of all entries held in the stored cache.
	* @return the entry names
	* */
	public synchronized static List<String> getNames(){
	    if(api == null) setApi(new MemoryCache(true));

		return api.getNames();
	}

}
