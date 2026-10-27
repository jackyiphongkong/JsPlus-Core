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
package com.killcoding.log;

import java.io.File;



import java.io.IOException;


import com.killcoding.tool.ConfigProperties;
import com.killcoding.log.Logger;


import com.killcoding.tool.CommonTools;


/**
* Logger factory: builds Logger instances keyed by class name and resolves the logging
* configuration (file targets, levels, output mode) from Logger properties.
* */
public final class LoggerFactory {

	public final static String APP_LOGGER_KEY = "APP_LOGGER";
	private static boolean LOADED_CONFIG = false;

	private static ConfigProperties defaultConfig = null;

	static {
		String hostname = CommonTools.getHostname();
		System.setProperty("HOSTNAME", hostname);
		System.setProperty("hostname", hostname);
		Logger.systemDebug(LoggerFactory.class, "===LoggerFactory Loading Config===");
		try {
			File logCnfFile = CommonTools.getSystemProperties(LoggerFactory.class, APP_LOGGER_KEY,
					"Logger.properties");
			if (logCnfFile == null) {
				Logger.systemDebug(LoggerFactory.class, "Using default config.");
				defaultConfig();
			} else {
				String absPath = logCnfFile.getAbsolutePath();
				Logger.systemDebug(LoggerFactory.class, "Loading config(For System Init Debug) -> {}", absPath);
				config(new File(absPath));
			}
			Logger.systemDebug(LoggerFactory.class, "===LoggerFactory Loaded Config===");
		} catch (Exception e) {
			Logger.systemDebug(LoggerFactory.class, "LoggerFactory static block falied!");
			Logger.systemError(LoggerFactory.class, e);
		}
	}

	/**
	 * Use default config
	 * */
	public static synchronized void defaultConfig() {
		defaultConfig = new ConfigProperties();
		try {
			LogRecord.setConfig(defaultConfig);
			LogRecord.start();
		} catch (Exception e) {
			Logger.systemError(LoggerFactory.class, e);
		}
	}

	/**
	 * Use target ConfigProperties
	 * @param _defaultConfig
	 * */
	public static synchronized void config(ConfigProperties _defaultConfig) {
		defaultConfig = _defaultConfig;
		try {
			LogRecord.setConfig(defaultConfig);
			LogRecord.start();
		} catch (Exception e) {
			Logger.systemError(LoggerFactory.class, e);
		}
		LOADED_CONFIG = true;
	}

	/**
	 * Use target Logger.properties file
	 * @param loggerPropFile - It is file object
	 * @throws IOException
	 * */
	public static synchronized void config(File loggerPropFile) throws IOException {
		if (!LOADED_CONFIG) {
		    defaultConfig = new ConfigProperties();
		    Runnable updatedRun = new Runnable() {
			    @Override

				/*
				* Performs the run operation
				* */
				public void run() {
					try {
						config(defaultConfig);
						Logger.systemMark(LoggerFactory.class, "Logger Reloaded");
					} catch (Exception e) {
						Logger.systemError(LoggerFactory.class, e);
					}
				}
			};	
			defaultConfig.load(loggerPropFile,updatedRun);
			config(defaultConfig);	 
		}
	}

	/**
	 * Check is started
	 * @return boolean
	 * */
	public static boolean isStarted() {
		return LogRecord.isStarted();
	}

	/**
	 * Check is loaded config
	 * @return boolean
	 * */
	public static boolean isLoadedConfig() {
		return LOADED_CONFIG;
	}

	/**
	 * Get logger
	 * @return Logger
	 * @param clazz
	 * */
	public static Logger getLogger(Class<?> clazz) {
		return getLogger(clazz.getName());
	}

	/**
	 * Get logger
	 * @return Logger
	 * @param clazz
	 * */
	public static Logger getLogger(String clazzName) {
		return new Logger(clazzName);
	}
	
	/**
	* Performs the getConfigProperties operation
	* @return the getConfigProperties result
	* */
	public static ConfigProperties getConfigProperties(){
	    return defaultConfig;
	}

}
