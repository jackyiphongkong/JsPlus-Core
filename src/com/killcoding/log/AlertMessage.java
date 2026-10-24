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
package com.killcoding.log;

import java.util.concurrent.Executors;
import com.killcoding.log.Logger;
import java.util.Map;

/**
 * This abstract class is alert message base class
 * Can notify administrators via email or other means
 * */
public class AlertMessage {
    
    public static Map<String, Object> msg = null;
    protected static Runnable sendRun = null;

	public AlertMessage() {
		super();
	}
	
    protected void send(Map<String, Object> msg){
        AlertMessage.msg = msg;
        if(AlertMessage.sendRun != null){
            AlertMessage.sendRun.run();
        }        
    }	
    
    public static void send(Runnable sendRun){
        AlertMessage.sendRun = sendRun;
    }    
	
	public void push(Map<String, Object> msg) {
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try{
				    send(msg);
				}catch(Exception e){
				    Logger.systemError(AlertMessage.class,e.getMessage(),e);
				}
			}
		};
		Executors.newSingleThreadExecutor().execute(runnable);
	}

}