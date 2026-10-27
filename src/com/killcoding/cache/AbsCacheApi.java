/* **********************************************************************
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

import java.util.List;

/**
* This abstract class, it is the framework base cache class.
* */
public abstract class AbsCacheApi {
    
    protected static Long TIMER = 100L;
    
    /**
     * Force write cache
     * */
    protected Boolean stored = false;    

    /**
     * New an AbsCacheApi
     * Need 'override' abstract method
     * 'stored' is use default value (default is false)
     * */
	public AbsCacheApi() {
		super();
	}
	
	/**
	 * New an AbsCacheApi
	 * Need 'override' abstract method
	 * @param stored - true is force write
	 * */
	public AbsCacheApi(Boolean stored) {
		super();
		this.stored = stored;
	}	
	
	/**
	* Performs the set operation
	* @param name - the name
	* @param value - the value
	* @param live - the live
	* */
	abstract void set(String name,Object value,int live);

	/**
	* Performs the get operation
	* @param name - the name
	* @return the get result
	* */
	abstract Object get(String name);
	abstract List<String> getNames();

	/**
	* Performs the included operation
	* @param name - the name
	* @return the included result
	* */
	abstract boolean included(String name);

	/**
	* Performs the remove operation
	* @param name - the name
	* */
	abstract void remove(String name);

	/**
	* Performs the removeAll operation
	* */
	abstract void removeAll();

}
