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

import com.killcoding.cache.CacheArray;


import java.util.List;

/**
* This class is used with 'CacheArray'
* */
public class CacheArrayFilter {

	private Long timer = 10L;
	
	private Long loopTimer = 10L;
	
	private CacheArray cacheArray = null;
	
	protected boolean terminated = false;
	protected boolean forceTerminated = false;

    /**
     * New a CacheArrayFilter Object
     * @param timer - Timer,default is 10ms
     * */
	public CacheArrayFilter(Long timer) {
		super();
		this.timer = timer;
	}

    /**
     * New a CacheArrayFilter Object
     * @param timer - Timer,default is 10ms
     * @param loopTimer - Loop Timer,default is 10ms
     * */
	public CacheArrayFilter(Long timer,Long loopTimer) {
		super();
		this.timer = timer;
		this.loopTimer = loopTimer;
	}	

    /**
     * Need override the method to use
     * @param index - Index of cache list
     * @param o - Cache value
     * */
	public void execute(Integer index, Object o) {

	}

    /**
     * Handle execute batch method
     * */
	public void executeBatch(Integer batchIndex,List<?> batch){
	    
	}
	
    /**
     * Handle completed method
     * */
	public void completed(Integer size) {

	}
	
    /**
     * Handle terminated method
     * */
	public void terminated() {

	}	
	
	/**
	* Performs the terminate operation
	* */
	public void terminate(){
	    this.terminated = true;
	}
	
	/**
	* Performs the forceTerminate operation
	* */
	public void forceTerminate(){
	    this.terminated = true;
	    this.forceTerminated = true;
	}		
	
	/**
	* Performs the isTerminated operation
	* @return the isTerminated result
	* */
	public boolean isTerminated(){
	    return terminated;
	}
	
	/**
	* Performs the isForceTerminated operation
	* @return the isForceTerminated result
	* */
	public boolean isForceTerminated(){
	    return forceTerminated;
	}	
	
	/**
	* Performs the setTimer operation
	* @param timer - the timer
	* */
	public void setTimer(long timer) {
		this.timer = timer;
	}	

	/**
	* Performs the getTimer operation
	* @return the getTimer result
	* */
	public Long getTimer() {
		return timer;
	}
	
	/**
	* Performs the setLoopTimer operation
	* @param loopTimer - the loopTimer
	* */
	public void setLoopTimer(long loopTimer) {
		this.loopTimer = loopTimer;
	}	
	
	/**
	* Performs the getLoopTimer operation
	* @return the getLoopTimer result
	* */
	public Long getLoopTimer() {
		return loopTimer;
	}	

	/**
	* Performs the setCacheArray operation
	* @param cacheArray - the cacheArray
	* */
	public void setCacheArray(CacheArray cacheArray) {
		this.cacheArray = cacheArray;
	}

	/**
	* Performs the getCacheArray operation
	* @return the getCacheArray result
	* */
	public CacheArray getCacheArray() {
		return this.cacheArray;
	}	

}
