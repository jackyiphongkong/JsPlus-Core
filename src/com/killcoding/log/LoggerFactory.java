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
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;

/**
* Logger factory: builds Logger instances keyed by class name and manages the
* logging buckets ({@link LoggerContext}).
*
* <p>One bucket is one Logger.properties file. The bucket key is
* {@code md5(normalizePath(properties file absolute path))}; the SystemName of
* the file is the handle used by {@link #getLogger(String, Class)}. Several
* buckets can coexist in one JVM, each with its own folder, level, format,
* queue, flusher and database output.</p>
*
* <p>The bucket registered first (normally the APP_LOGGER file) is the default
* bucket: {@code getLogger(Class)} keeps using it, and it is the only bucket
* allowed to capture the JVM-wide {@code System.out}/{@code System.err}.</p>
* */
public final class LoggerFactory {

	public final static String APP_LOGGER_KEY = "APP_LOGGER";

	private static boolean LOADED_CONFIG = false;

	/** bucket key (md5 of normalized properties path) -&gt; context */
	private static final Map<String, LoggerContext> CONTEXTS = new ConcurrentHashMap<String, LoggerContext>();

	/** SystemName -&gt; bucket key */
	private static final Map<String, String> SYSTEM_NAME_INDEX = new ConcurrentHashMap<String, String>();

	private static LoggerContext DEFAULT_CONTEXT = null;

	/**
	* Fixed alias of the default bucket: {@link #getLogger(String, Class)} accepts
	* it even when the first bucket declares its own SystemName
	* */
	public final static String DEFAULT_SYSTEM_NAME = "default";

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
				String absPath = CommonTools.normalizePath(logCnfFile.getAbsolutePath());
				Logger.systemDebug(LoggerFactory.class, "Loading config(For System Init Debug) -> {}", absPath);
				register(new File(absPath));
			}
			Logger.systemDebug(LoggerFactory.class, "===LoggerFactory Loaded Config===");
		} catch (Exception e) {
			Logger.systemDebug(LoggerFactory.class, "LoggerFactory static block falied!");
			Logger.systemError(LoggerFactory.class, e);
		}
	}

	/**
	* Register a logging bucket for a Logger.properties file.
	*
	* <p>Repeated registration of the same file (same normalized path) returns the
	* existing bucket. A SystemName already used by another bucket is reported and
	* the bucket registered first keeps the name (the new bucket still exists but
	* is not reachable through that SystemName). The first registered bucket is
	* the default one and always answers to {@link #DEFAULT_SYSTEM_NAME} as an
	* alias, in addition to its own SystemName.</p>
	*
	* @param propFile - the Logger.properties file
	* @return the bucket
	* @throws IOException when the properties file cannot be read
	* */
	public static synchronized LoggerContext register(File propFile) throws IOException {
		if (propFile == null) {
			return null;
		}
		String absPath = CommonTools.normalizePath(propFile.getAbsolutePath());
		String key = CommonTools.md5(absPath);
		LoggerContext exist = CONTEXTS.get(key);
		if (exist != null) {
			return exist;
		}

		final ConfigProperties cfg = new ConfigProperties();
		final LoggerContext ctx = new LoggerContext(key, new File(absPath));
		if (DEFAULT_CONTEXT == null) {
			ctx.setStdStreamOwner();
			DEFAULT_CONTEXT = ctx;
		}

		Runnable updatedRun = new Runnable() {
			/*
			* Performs the run operation
			* */
			@Override
			public void run() {
				try {
					ctx.setConfig(cfg);
					ctx.start();
					Logger.systemMark(LoggerFactory.class, "Logger Reloaded");
				} catch (Exception e) {
					Logger.systemError(LoggerFactory.class, e);
				}
			}
		};
		cfg.load(ctx.getConfigFile(), updatedRun);
		ctx.setConfig(cfg);
		ctx.start();

		String systemName = cfg.getString("SystemName");
		if (CommonTools.isBlank(systemName)) {
			// The default bucket always owns the "default" name; any other bucket
			// falls back to its file base name so two files named Logger.properties
			// never compete for the alias.
			systemName = (ctx == DEFAULT_CONTEXT) ? DEFAULT_SYSTEM_NAME : baseName(propFile);
		}
		ctx.setSystemName(systemName);

		CONTEXTS.put(key, ctx);
		String existKey = SYSTEM_NAME_INDEX.get(systemName);
		if (existKey != null && !existKey.equals(key)) {
			Logger.systemWarn(LoggerFactory.class,
					"Duplicate SystemName '{}' - keeping the bucket registered first ({})", systemName, existKey);
		} else {
			SYSTEM_NAME_INDEX.put(systemName, key);
		}
		if (ctx == DEFAULT_CONTEXT) {
			// "default" is an alias of the default bucket (its own SystemName stays
			// addressable too); put it after the own name so a bucket that declares
			// SystemName=default keeps a single mapping.
			SYSTEM_NAME_INDEX.put(DEFAULT_SYSTEM_NAME, key);
		}
		LOADED_CONFIG = true;
		return ctx;
	}

	/**
	* Use default config (no bucket file available)
	* */
	public static synchronized void defaultConfig() {
		ConfigProperties cfg = new ConfigProperties();
		LoggerContext ctx = new LoggerContext(CommonTools.md5("DEFAULT_CONFIG"), null);
		ctx.setSystemName(DEFAULT_SYSTEM_NAME);
		if (DEFAULT_CONTEXT == null) {
			ctx.setStdStreamOwner();
			DEFAULT_CONTEXT = ctx;
		}
		ctx.setConfig(cfg);
		ctx.start();
		CONTEXTS.put(ctx.getBucketKey(), ctx);
		SYSTEM_NAME_INDEX.put(DEFAULT_SYSTEM_NAME, ctx.getBucketKey());
		LOADED_CONFIG = true;
	}

	/**
	* Use target ConfigProperties for the default bucket
	* @param _defaultConfig - the config
	* */
	public static synchronized void config(ConfigProperties _defaultConfig) {
		LoggerContext ctx = DEFAULT_CONTEXT;
		if (ctx == null) {
			ctx = new LoggerContext(CommonTools.md5("DEFAULT_CONFIG"), null);
			ctx.setSystemName(DEFAULT_SYSTEM_NAME);
			ctx.setStdStreamOwner();
			DEFAULT_CONTEXT = ctx;
			CONTEXTS.put(ctx.getBucketKey(), ctx);
			SYSTEM_NAME_INDEX.put(DEFAULT_SYSTEM_NAME, ctx.getBucketKey());
		}
		ctx.setConfig(_defaultConfig);
		ctx.start();
		LOADED_CONFIG = true;
	}

	/**
	* Use target Logger.properties file as the first bucket (compatibility entry)
	* @param loggerPropFile - the file
	* @throws IOException
	* */
	public static synchronized void config(File loggerPropFile) throws IOException {
		if (!LOADED_CONFIG) {
			register(loggerPropFile);
		}
	}

	/**
	* Check is started (default bucket)
	* @return boolean
	* */
	public static boolean isStarted() {
		LoggerContext ctx = DEFAULT_CONTEXT;
		return ctx != null && ctx.isStarted();
	}

	/**
	* Check is loaded config
	* @return boolean
	* */
	public static boolean isLoadedConfig() {
		return LOADED_CONFIG;
	}

	/**
	* Returns the bucket registered for a SystemName
	* @param systemName - the SystemName entry of the bucket's properties file
	* @return the bucket or null when not registered
	* */
	public static LoggerContext getContext(String systemName) {
		if (systemName == null) {
			return null;
		}
		String key = SYSTEM_NAME_INDEX.get(systemName);
		if (key == null) {
			return null;
		}
		return CONTEXTS.get(key);
	}

	/**
	* Returns the default bucket (the first registered one)
	* @return the default bucket or null before initialisation
	* */
	public static LoggerContext getDefaultContext() {
		return DEFAULT_CONTEXT;
	}

	/**
	* Get logger of a bucket by SystemName
	*
	* <p>The name {@link #DEFAULT_SYSTEM_NAME} resolves to the default bucket, so
	* static-style call sites and {@code getLogger(Class)} land on the same bucket
	* without a warning. When the SystemName is not registered, a warning is issued
	* once per call and the default bucket is used, so business code is never
	* interrupted.</p>
	*
	* @param systemName - the SystemName of the target bucket
	* @param clazz - the class
	* @return Logger
	* */
	public static Logger getLogger(String systemName, Class<?> clazz) {
		LoggerContext ctx = getContext(systemName);
		if (ctx == null) {
			Logger.systemWarn(LoggerFactory.class,
					"SystemName '{}' is not registered - falling back to the default bucket", systemName);
			return new Logger(DEFAULT_CONTEXT, clazz);
		}
		return new Logger(ctx, clazz);
	}

	/**
	* Get logger of the default bucket
	* @return Logger
	* @param clazz
	* */
	public static Logger getLogger(Class<?> clazz) {
		return new Logger(DEFAULT_CONTEXT, clazz);
	}

	/**
	* Get logger of the default bucket
	* @return Logger
	* @param clazzName
	* */
	public static Logger getLogger(String clazzName) {
		return new Logger(DEFAULT_CONTEXT, clazzName);
	}

	/**
	* Performs the getConfigProperties operation (default bucket)
	* @return the getConfigProperties result
	* */
	public static ConfigProperties getConfigProperties() {
		LoggerContext ctx = DEFAULT_CONTEXT;
		return ctx == null ? null : ctx.getConfig();
	}

	/**
	* Returns all registered buckets (bucket key -&gt; context)
	* @return unmodifiable map
	* */
	public static Map<String, LoggerContext> getContexts() {
		return Collections.unmodifiableMap(CONTEXTS);
	}

	/**
	* Returns the SystemName index (SystemName -&gt; bucket key)
	* @return unmodifiable map
	* */
	public static Map<String, String> getSystemNameIndex() {
		return Collections.unmodifiableMap(SYSTEM_NAME_INDEX);
	}

	/**
	* Base name of a properties file without extension
	* @param file - the file
	* @return the base name
	* */
	private static String baseName(File file) {
		String name = file.getName();
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			return name.substring(0, dot);
		}
		return name;
	}

}
