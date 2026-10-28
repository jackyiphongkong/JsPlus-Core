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
package com.killcoding.tool;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.killcoding.file.BaseFile;
import com.killcoding.file.DiskFile;
import com.killcoding.log.Logger;
import com.killcoding.tool.CipherTools;

/**
* Extended Properties: reads .properties files with ${VAR} environment expansion and typed value
* parsing (time -> seconds / millis via TIME_UNIT_PATTERN, file sizes via FILE_SIZE_PATTERN),
* returning the provided defaultValue instead of null when a value is missing or invalid.
* */
public class ConfigProperties extends Properties {

	private static final long serialVersionUID = 1L;

	public static final String ENV_VAR_REGEX = "[\\$%#]{1}\\{?([\\.\\w-]+)(:{1}([^\\}]+)){0,1}\\}";

	public static String DATETIME_PATTERN = "yyyy.MM.dd HH:mm:ss[.SSSSSSSSS]";

	public static String Config_Cipher_AesKey = null;

    public static Long AUTO_LOAD_TIMER = 5000L;
	private static final List<Runnable> AUTO_LOAD_CP = new CopyOnWriteArrayList<Runnable>();

	/** Unit-suffix time pattern used by parseTimeToSec/parseTimeToMs ("5s"/"12m"/"4h"/"1d"/"2w"). */
	private static final Pattern TIME_UNIT_PATTERN = Pattern.compile("^([-\\+]{0,1}[0-9]+)(((?i)ms|s|m|h|d|w){1})$");

	/** Unit-suffix size pattern used by parseFileSize ("1kb"/"5mb"/"2gb"/"1tb"). */
	private static final Pattern FILE_SIZE_PATTERN = Pattern.compile("^([-\\+]{0,1}[0-9]+)(((?i)b|kb|mb|gb|tb){1})$");

	/** Env-var substitution pattern (reused by getEnv - do not re-compile per call). */
	private static final Pattern ENV_VAR_PATTERN = Pattern.compile(ENV_VAR_REGEX);

	/** Env-var substitution pattern with DOTALL|MULTILINE (reused by parseContent - do not re-compile per call). */
	private static final Pattern ENV_VAR_PATTERN_FLAGS = Pattern.compile(ENV_VAR_REGEX, Pattern.DOTALL | Pattern.MULTILINE);

	/** Time unit -> multiplier relative to one millisecond, shared by parseTimeToSec/parseTimeToMs. */
	private static final Map<String, Long> TIME_UNIT_MULTIPLIER_MS = new HashMap<String, Long>();
	static {
		TIME_UNIT_MULTIPLIER_MS.put("ms", 1L);
		TIME_UNIT_MULTIPLIER_MS.put("s", 1000L);
		TIME_UNIT_MULTIPLIER_MS.put("m", 60000L);
		TIME_UNIT_MULTIPLIER_MS.put("h", 3600000L);
		TIME_UNIT_MULTIPLIER_MS.put("d", 86400000L);
		TIME_UNIT_MULTIPLIER_MS.put("w", 604800000L);
	}

	/** File-size unit -> multiplier relative to one byte, shared by parseFileSize. */
	private static final Map<String, Long> FILE_SIZE_MULTIPLIER = new HashMap<String, Long>();
	static {
		FILE_SIZE_MULTIPLIER.put("b", 1L);
		FILE_SIZE_MULTIPLIER.put("kb", 1024L);
		FILE_SIZE_MULTIPLIER.put("mb", 1024L * 1024);
		FILE_SIZE_MULTIPLIER.put("gb", 1024L * 1024 * 1024);
		FILE_SIZE_MULTIPLIER.put("tb", 1024L * 1024 * 1024 * 1024);
	}

    private static ConfigCustomLoad CUSTOMLOAD = null;

	protected static ExecutorService executor = null;

	private String parseContentRaw = null;

	/**
	* Constructs a new ConfigProperties
	* @param arg0 - the arg0
	* */
	public ConfigProperties(int arg0) {
		super(arg0);
	}

	/**
	* Constructs a new ConfigProperties
	* @param arg0 - the arg0
	* */
	public ConfigProperties(Properties arg0) {
		super(arg0);
	}

	/**
	* Constructs a new ConfigProperties
	* */
	public ConfigProperties() {
		super();
	}

	/**
	* Performs the addAutoLoad operation
	* @param cpRun - the cpRun
	* */
	private void addAutoLoad(Runnable cpRun) {
		AUTO_LOAD_CP.add(cpRun);
		startAutoLoad();
	}

	/**
	* Performs the startAutoLoad operation
	* */
	private static synchronized void startAutoLoad() {
		if (executor == null) {
			executor = Executors.newFixedThreadPool(1);
			executor.execute(new Runnable() {
								/*
				* Performs the run operation
				* */
				@Override
				public void run() {
				    while(!Thread.currentThread().isInterrupted()){
				        Thread.currentThread().setName("ConfigProperties.startAutoLoad");
    					for (Runnable cpRun : AUTO_LOAD_CP) {
    					    cpRun.run();
    					}
    					try {
    						Thread.sleep(getAutoLoadTimer());
    					} catch (InterruptedException e) {
    						Logger.systemError(ConfigProperties.class, e.getMessage(), e);
    						break;
    					}
				    }
				}
			});
		}
	}

	/**
	* Performs the getAutoLoadTimer operation
	* @return the getAutoLoadTimer result
	* */
	private static Long getAutoLoadTimer(){
	    return AUTO_LOAD_TIMER;
	}

	/**
	* Performs the setConfigCustom operation
	* @param customLoad - the customLoad
	* */
	public static void setConfigCustom(ConfigCustomLoad customLoad){
	    ConfigProperties.CUSTOMLOAD =  customLoad;
	}

	/**
	* Performs the loadConfigCustom operation
	* @param file - the file
	* @param target - the target
	* @return the loadConfigCustom result
	* */
	private static boolean loadConfigCustom(File file,ConfigProperties target){
	    if(ConfigProperties.CUSTOMLOAD != null){
    	    ConfigProperties.CUSTOMLOAD.load(file,target);
    	    return true;
	    }
	    return false;
	}

	/**
	* Performs the load operation
	* @param file - the file
	* @param updatedRunnable - the updatedRunnable
	* */
	public synchronized void load(File file, Runnable updatedRunnable) throws IOException {
		this.load(file);
		final ConfigProperties the = this;
		final Runnable cpRun = new Runnable() {
						/*
			* Performs the run operation
			* */
			@Override
			public void run() {
				Thread.currentThread().setName(String.format("AutoLoad-%s", file.getName()));
				try {
					ConfigProperties target = new ConfigProperties();
					if(Files.exists(Paths.get(file.getAbsolutePath()))){
    					target.load(file);
    					boolean isUseCustom = false;
    					boolean haveCustomLoad = loadConfigCustom(file,the);
    					if(haveCustomLoad){
    					    isUseCustom = ConfigProperties.CUSTOMLOAD.isUseCustom();
    					    boolean isUpdated = ConfigProperties.CUSTOMLOAD.isUpdated();
    					    if(isUpdated){
        						Logger.systemMark(ConfigProperties.class, "CustomUpdated.");
        						if (updatedRunnable != null)
        							updatedRunnable.run();
    					    }
    					}
    					if(!isUseCustom){
    					    boolean same = ConfigProperties.compareProperties(the, target);
        					if (!same) {
        						the.load(file);
        						Logger.systemMark(ConfigProperties.class, "Updated");
        						Logger.systemDebug(ConfigProperties.class, the.parseContentRaw);

        						if (updatedRunnable != null)
        							updatedRunnable.run();
        					}
    					}
					}
				} catch (Exception e) {
					Logger.systemError(ConfigProperties.class, e.getMessage(), e);
				}
			}
		};
		addAutoLoad(cpRun);
	}

	/**
	* Performs the load operation
	* @param file - the file
	* */
	public void load(File file) throws IOException {
	    if(CommonTools.isBlank(Config_Cipher_AesKey)){
	        load(new FileInputStream(file));
	    }else{
	        try{
    	        DiskFile df = new DiskFile(file);
    	        byte[] encryptBytes = df.readAllBytes();
    	        byte[] decryptBytes = CipherTools.AESDecrypt(Config_Cipher_AesKey,encryptBytes);
    	        load(new ByteArrayInputStream(decryptBytes));
	        }catch(Exception e){
	            throw new IOException(String.format("The file '%s' decryption failed.",file),e);
	        }
	    }
	}

		/*
	* Performs the load operation
	* @param inputStream - the inputStream
	* */
	@Override
	public void load(InputStream inputStream) throws IOException {
		try {
			Reader reader = new InputStreamReader(inputStream,BaseFile.CHARSET);
			load(reader);
		} finally {
			if (inputStream != null)
				inputStream.close();
		}
	}

		/*
	* Performs the load operation
	* @param reader - the reader
	* */
	@Override
	public void load(Reader reader) throws IOException {
		try {
			// Batch-read into a char[] buffer instead of read()-ing char by char.
			char[] buffer = new char[8192];
			int readCount;
			StringBuilder sbf = new StringBuilder();
			while ((readCount = reader.read(buffer)) != -1) {
				sbf.append(buffer, 0, readCount);
			}
			this.parseContentRaw = parseContent(sbf.toString());
			super.load(new StringReader(this.parseContentRaw));
		} finally {
			if (reader != null)
				reader.close();
		}
	}

	/**
	* Performs the putAndReturnSelf operation
	* @param key - the key
	* @param value - the value
	* @return the putAndReturnSelf result
	* */
	@SuppressWarnings("unchecked")
	public ConfigProperties putAndReturnSelf(String key, Object value) {
		if (isBlank(value)) {
			put(key, "");
		} else if (value instanceof Map) {
			Map<String, Object> subMap = convert(key, (Map<String, Object>) value);
			putAllAndReturnSelf(subMap);
		} else if (value instanceof List) {
			List<Object> list = (List<Object>) value;
			int size = list.size();
			for (int i = 0; i < size; i++) {
				Object item = list.get(i);
				String newKey = String.format("%s[%s]", key, i);
				putAndReturnSelf(newKey, item);
			}
		} else if (value instanceof Object[]) {
			List<Object> list = Arrays.asList((Object[]) value);
			int size = list.size();
			for (int i = 0; i < size; i++) {
				Object item = list.get(i);
				String newKey = String.format("%s[%s]", key, i);
				putAndReturnSelf(newKey, item);
			}
		} else if (value instanceof String) {
			put(key, value);
		} else {
			put(key, value.toString());
		}
		return this;
	}

	/** @deprecated misspelled legacy name; kept for compatibility - use {@link #putAndReturnSelf(String, Object)}. */
	@Deprecated
	public ConfigProperties putAndReturnSlef(String key, Object value) {
		return putAndReturnSelf(key, value);
	}

	/**
	* Performs the removeAndReturnSelf operation
	* @param key - the key
	* @return the removeAndReturnSelf result
	* */
	public ConfigProperties removeAndReturnSelf(String key) {
		this.remove(key);
		return this;
	}

	/** @deprecated misspelled legacy name; kept for compatibility - use {@link #removeAndReturnSelf(String)}. */
	@Deprecated
	public ConfigProperties removeAndReturnSlef(String key) {
		return removeAndReturnSelf(key);
	}

	/**
	* Performs the putAllAndReturnSelf operation
	* @param map - the map
	* @return the putAllAndReturnSelf result
	* */
	public ConfigProperties putAllAndReturnSelf(Map<String, Object> map) {
		Set<String> keys = map.keySet();
		for (String key : keys) {
			Object value = map.get(key);
			putAndReturnSelf(key, value);
		}
		return this;
	}

	/** @deprecated misspelled legacy name; kept for compatibility - use {@link #putAllAndReturnSelf(Map)}. */
	@Deprecated
	public ConfigProperties putAllAndReturnSlef(Map<String, Object> map) {
		return putAllAndReturnSelf(map);
	}

	/**
	* Performs the removeAllNullValues operation
	* */
	public void removeAllNullValues() {
		// Copy the key set before removing: mutating the live keySet during iteration
		// trips the fail-fast Hashtable iterator (ConcurrentModificationException).
		List<Object> keys = new ArrayList<Object>(this.keySet());
		for (Object key : keys) {
			if (isBlank(get(key))) {
				this.remove(key);
			}
		}
	}

	/**
	* Performs the convert operation
	* @param parentKey - the parentKey
	* @param map - the map
	* @return the convert result
	* */
	private Map<String, Object> convert(String parentKey, Map<String, Object> map) {
		Map<String, Object> subMap = new HashMap<String, Object>();
		Set<String> keys = map.keySet();
		for (String key : keys) {
			String newKey = String.format("%s.%s", parentKey, key);
			Object value = map.get(key);
			subMap.put(newKey, value);
		}
		return subMap;
	}

	/**
	* Performs the getBoolean operation
	* @param key - the key
	* @return the getBoolean result
	* */
	public Boolean getBoolean(String key) {
		return getBoolean(key, null);
	}

	/**
	* Performs the getBoolean operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getBoolean result
	* */
	public Boolean getBoolean(String key, Boolean defaultValue) {
	    Object originValue = get(key);

	    if(originValue == null) return defaultValue;

	    if(originValue instanceof Boolean) return (Boolean)originValue;

		String o = getString(key);
		if (isBlank(o)) {
			return defaultValue;
		}
		return Boolean.parseBoolean(o.trim());
	}

	/**
	* Performs the getArray operation
	* @param key - the key
	* @return the getArray result
	* */
	public List<String> getArray(String key) {
		List<String> defaultValue = null;
		List<String> v = getArray(key, defaultValue);
		if (v == null) {
			String sv = getString(key);
			if (sv == null)
				return new ArrayList<String>();

			return new ArrayList<String>(Arrays.asList(new String[] { sv }));
		} else {
			return v;
		}
	}

	/**
	* Performs the getArray operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getArray result
	* */
	public List<String> getArray(String key, String[] defaultValue) {
		return getArray(key, Arrays.asList(defaultValue));
	}

	/**
	* Performs the getArray operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getArray result
	* */
	public List<String> getArray(String key, List<String> defaultValue) {
		String[] list = null;
		Map<Integer, String> listMap = new HashMap<Integer, String>();
		List<Integer> listIndex = new ArrayList<Integer>();
		// Compile the indexed-key pattern once instead of per key.
		String format = String.format("^%s\\[(\\w+)\\]$", key);
		Pattern pattern = Pattern.compile(format);
		Set<Object> keys = keySet();
		for (Object _key : keys) {
			Matcher matcher = pattern.matcher(_key.toString());
			if (matcher.find()) {
				Integer index = Integer.parseInt(matcher.group(1));
				String value = getString(_key.toString());
				listMap.put(index, value);
				listIndex.add(index);
			}
		}
		int listIndexSize = listIndex.size();
		if (listIndexSize > 0) {
			Collections.sort(listIndex);
			list = new String[listIndex.get(listIndexSize - 1) + 1];
			for (int i = 0; i < listIndexSize; i++) {
				int index = listIndex.get(i);
				String value = listMap.get(index);
				list[index] = isBlank(value) ? null : value;
			}
			return Arrays.asList(list);
		} else {
			return defaultValue;
		}

	}

	/**
	* Performs the getString operation
	* @param key - the key
	* @return the getString result
	* */
	public String getString(String key) {
		return getString(key, null);
	}

	/**
	* Performs the getString operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getString result
	* */
	public String getString(String key, String defaultValue) {
		String o = getProperty(key);
		if (isBlank(o)) {
			return defaultValue;
		}
		return o.trim();
	}

	/**
	* Performs the getShort operation
	* @param key - the key
	* @return the getShort result
	* */
	public Short getShort(String key) {
		return getShort(key, null);
	}

	/**
	* Performs the getShort operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getShort result
	* */
	public Short getShort(String key, Short defaultValue) {
	    Object originValue = get(key);

	    if(originValue == null) return defaultValue;

	    if(originValue instanceof Number) return ((Number) originValue).shortValue();

		String o = getString(key);
		if (isBlank(o)) {
			return defaultValue;
		}
		return Short.parseShort(o.trim());
	}

	/**
	* Performs the getInteger operation
	* @param key - the key
	* @return the getInteger result
	* */
	public Integer getInteger(String key) {
		return getInteger(key, null);
	}

	/**
	* Performs the getInteger operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getInteger result
	* */
	public Integer getInteger(String key, Integer defaultValue) {
	    Object originValue = get(key);

	    if(originValue == null) return defaultValue;

	    if(originValue instanceof Number) return ((Number) originValue).intValue();

		String o = getString(key);
		if (isBlank(o)) {
			return defaultValue;
		}
		return Integer.parseInt(o.trim());
	}

	/**
	* Performs the getLong operation
	* @param key - the key
	* @return the getLong result
	* */
	public Long getLong(String key) {
		return getLong(key, null);
	}

	/**
	* Performs the getLong operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getLong result
	* */
	public Long getLong(String key, Long defaultValue) {
	    Object originValue = get(key);

	    if(originValue == null) return defaultValue;

	    if(originValue instanceof Number) return ((Number) originValue).longValue();

		String o = getString(key);
		if (isBlank(o)) {
			return defaultValue;
		}
		return Long.parseLong(o.trim());
	}

	/**
	* Performs the getFloat operation
	* @param key - the key
	* @return the getFloat result
	* */
	public Float getFloat(String key) {
		return getFloat(key, null);
	}

	/**
	* Performs the getFloat operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getFloat result
	* */
	public Float getFloat(String key, Float defaultValue) {
	    Object originValue = get(key);

	    if(originValue == null) return defaultValue;

	    if(originValue instanceof Number) return ((Number) originValue).floatValue();

		String o = getString(key);
		if (isBlank(o)) {
			return defaultValue;
		}
		return Float.parseFloat(o.trim());
	}

	/**
	* Performs the getDouble operation
	* @param key - the key
	* @return the getDouble result
	* */
	public Double getDouble(String key) {
		return getDouble(key, null);
	}

	/**
	* Performs the getDouble operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getDouble result
	* */
	public Double getDouble(String key, Double defaultValue) {
	    Object originValue = get(key);

	    if(originValue == null) return defaultValue;

	    if(originValue instanceof Number) return ((Number) originValue).doubleValue();

		String o = getString(key);
		if (isBlank(o)) {
			return defaultValue;
		}
		return Double.parseDouble(o.trim());
	}

	/**
	* Performs the getSeconds operation
	* @param key - the key
	* @return the getSeconds result
	* */
	public Long getSeconds(String key) {
		return getSeconds(key, null);
	}

	/**
	* Performs the getSeconds operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getSeconds result
	* */
	public Long getSeconds(String key, Long defaultValue) {
		return parseTimeToSec(getString(key, null), defaultValue);
	}

	/**
	* Performs the getMilliSeconds operation
	* @param key - the key
	* @return the getMilliSeconds result
	* */
	public Long getMilliSeconds(String key) {
		return getMilliSeconds(key, null);
	}

	/**
	* Performs the getMilliSeconds operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getMilliSeconds result
	* */
	public Long getMilliSeconds(String key, Long defaultValue) {
		return parseTimeToMs(getString(key, null), defaultValue);
	}

	/**
	* Performs the getFileSize operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getFileSize result
	* */
	public Long getFileSize(String key, Long defaultValue) {
		return parseFileSize(getString(key, null), defaultValue);
	}

	/**
	* Performs the getDateTime operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getDateTime result
	* */
	public Date getDateTime(String key, String defaultValue) {
		return parseDateTime(getString(key, null), defaultValue);
	}

	/**
	* Performs the getDateTime operation
	* @param key - the key
	* @return the getDateTime result
	* */
	public Date getDateTime(String key) {
		return getDateTime(key,null);
	}

	/**
	* Performs the getConfigProperties operation
	* @param key - the key
	* @return the getConfigProperties result
	* */
	public ConfigProperties getConfigProperties(String key) {
		Map<String, Object> map = getMap(key);
		if (map != null) {
			ConfigProperties cp = new ConfigProperties();
			cp.putAllAndReturnSelf(map);
			return cp;
		}
		return null;
	}

	/**
	* Performs the getConfigProperties operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getConfigProperties result
	* */
	public ConfigProperties getConfigProperties(String key, ConfigProperties defaultValue) {
		Map<String, Object> map = getMap(key);
		if (map != null) {
			ConfigProperties cp = new ConfigProperties();
			cp.putAllAndReturnSelf(map);
			return cp;
		}
		return defaultValue;
	}

	/**
	* Performs the getMap operation
	* @param key - the key
	* @return the getMap result
	* */
	public Map<String, Object> getMap(String key) {
		return getMap(key, null);
	}

	/**
	* Performs the getMap operation
	* @param key - the key
	* @param defaultValue - the defaultValue
	* @return the getMap result
	* */
	public Map<String, Object> getMap(String key, Map<String, Object> defaultValue) {
		Map<String, Object> cp = null;
		Set<Object> keys = this.keySet();
		for (Object keyObject : keys) {
			String _key = keyObject.toString();
			boolean isMap = _key.startsWith(String.format("%s.", key));
			if (isMap) {
				if (cp == null)
					cp = new HashMap<String, Object>();

				String mapKey = _key.replaceFirst(String.format("^%s\\.", key), "");
				cp.put(mapKey, this.getString(_key, null));
			}
		}
		return isBlank(cp) ? defaultValue : cp;
	}

	/**
	* Performs the getContentRaw operation
	* @return the getContentRaw result
	* */
	public String getContentRaw(){
	    return this.parseContentRaw;
	}

	/**
	* Performs the isBlank operation
	* @param o - the o
	* @return the isBlank result
	* */
	public static boolean isBlank(Object o) {
		return CommonTools.isBlank(o);
	}

	/**
	* Performs the parseTimeToSec operation
	* @param objectValue - the objectValue
	* @param defaultValue - the defaultValue
	* @return the parseTimeToSec result
	* */
	public static Long parseTimeToSec(Object objectValue, Long defaultValue) {
		// Numeric input is treated as seconds as-is (kept for compatibility).
		if (objectValue == null)
			return defaultValue;
		if (!(objectValue instanceof String))
			return Long.parseLong(objectValue.toString().trim());
		Long ms = parseSuffixed(objectValue.toString().trim(), TIME_UNIT_PATTERN, TIME_UNIT_MULTIPLIER_MS,
				defaultValue, "Missing unit(ms|s|m|h|d|w) '{}' will using default value '{}', (e.g. {}ms,{}s,...).");
		return ms == null ? defaultValue : ms / 1000;
	}

	/**
	* Performs the parseTimeToMs operation
	* @param objectValue - the objectValue
	* @param defaultValue - the defaultValue
	* @return the parseTimeToMs result
	* */
	public static Long parseTimeToMs(Object objectValue, Long defaultValue) {
		// Numeric input is treated as milliseconds as-is (kept for compatibility).
		if (objectValue == null)
			return defaultValue;
		if (!(objectValue instanceof String))
			return Long.parseLong(objectValue.toString().trim());
		Long ms = parseSuffixed(objectValue.toString().trim(), TIME_UNIT_PATTERN, TIME_UNIT_MULTIPLIER_MS,
				defaultValue, "Missing unit(ms|s|m|h|d|w) '{}' will using default value '{}', (e.g. {}ms,{}s,...).");
		return ms == null ? defaultValue : ms;
	}

    /**
    * Performs the parseDateTime operation
    * @param objectValue - the objectValue
    * @param defaultValue - the defaultValue
    * @return the parseDateTime result
    * */
    public static Date parseDateTime(String objectValue, String defaultValue) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
        try {
            String value = objectValue;
            if (CommonTools.isBlank(value)) {
                if (CommonTools.isBlank(defaultValue)) {
                    return null;
                }
                value = defaultValue;
            }
            LocalDateTime ldt = LocalDateTime.parse(value.trim(), formatter);
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            Logger.systemError(ConfigProperties.class, e);
            return null;
        }
    }

	/**
	* Performs the parseFileSize operation
	* @param objectValue - the objectValue
	* @param defaultValue - the defaultValue
	* @return the parseFileSize result
	* */
	public static Long parseFileSize(Object objectValue, Long defaultValue) {
		// Numeric input is treated as bytes as-is (kept for compatibility).
		if (objectValue == null)
			return defaultValue;
		if (!(objectValue instanceof String))
			return Long.parseLong(objectValue.toString().trim());
		Long size = parseSuffixed(objectValue.toString().trim(), FILE_SIZE_PATTERN, FILE_SIZE_MULTIPLIER,
				defaultValue, "Missing unit(b|kb|mb|gb|tb) '{}' will using default value '{}', (e.g. {}mb,{}kb,...).");
		return size == null ? defaultValue : size;
	}

	/**
	* Shared suffix-parser: matches the unit pattern, multiplies the numeric part by the unit
	* multiplier map; logs a missing-unit warning; returns null when unmatched or invalid so the
	* caller can fall back to its defaultValue.
	* @param stringValue - the trimmed value to parse
	* @param pattern - the unit pattern to match
	* @param unitMultiplier - the unit -> multiplier lookup
	* @param defaultValue - the caller's default (used for the missing-unit warning only)
	* @param missingUnitMsg - the warning template
	* @return the computed raw value, or null when the input has no valid unit
	* */
	private static Long parseSuffixed(String stringValue, Pattern pattern, Map<String, Long> unitMultiplier,
			Long defaultValue, String missingUnitMsg) {
		Long value = null;
		try {
			Matcher matcher = pattern.matcher(stringValue);
			if (matcher.find()) {
				Long originValue = Long.parseLong(matcher.group(1));
				Long multiplier = unitMultiplier.get(matcher.group(2).toLowerCase());
				if (multiplier != null) {
					value = originValue * multiplier;
				}
			} else {
				Logger.systemMark(ConfigProperties.class, missingUnitMsg, stringValue, defaultValue, stringValue);
			}
			return value;
		} catch (Exception e) {
			Logger.systemError(ConfigProperties.class, e);
			return null;
		}
	}

	/**
	* Performs the getSysEnv operation
	* @param envKey - the envKey
	* @return the getSysEnv result
	* */
	public static String getSysEnv(String envKey) {
		return System.getenv(envKey) == null ? System.getProperty(envKey) : System.getenv(envKey);
	}

	/**
	* Performs the getEnv operation
	* @param originValue - the originValue
	* @return the getEnv result
	* */
	public static String getEnv(String originValue) {
		if (originValue == null)
			return null;

		Pattern p = ENV_VAR_PATTERN;
		Matcher m = p.matcher(originValue.trim());
		boolean findEnvFormat = m.find();
		if (findEnvFormat) {
			String envVar = m.group(1);
			String envDefValue = m.group(3);
			String envValue = getSysEnv(envVar);
			if (!isBlank(envValue)) {
				return envValue;
			}
			if (!isBlank(envDefValue)) {
				return envDefValue;
			}
			return null;
		}
		return originValue;
	}

	/**
	* Performs the parseContent operation
	* @param content - the content
	* @return the parseContent result
	* */
	public static String parseContent(String content) {
		return parseContent(content, "");
	}

	/**
	* Performs the parseContent operation
	* @param content - the content
	* @param replaceNullChar - the replaceNullChar
	* @return the parseContent result
	* */
	public static String parseContent(String content, String replaceNullChar) {
		Pattern p = ENV_VAR_PATTERN_FLAGS;
		StringBuilder sb = new StringBuilder();
		Matcher matcher = p.matcher(content);
		while (matcher.find()) {
			String envValue = ConfigProperties.getEnv(matcher.group());
			envValue = (envValue == null ? replaceNullChar : envValue);
			// quoteReplacement() is REQUIRED: appendReplacement treats '\' and '$'
			// as meta characters, so a value like "C:\Users\x" would silently lose
			// its backslashes (observed: 'C:\Users\AutoTrader' -> 'C:UsersAutoTrader').
			matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	* Performs the compareProperties operation
	* @param props1 - the props1
	* @param props2 - the props2
	* @return the compareProperties result
	* */
	public static boolean compareProperties(ConfigProperties props1, ConfigProperties props2) {

		if (props1 == null || props2 == null)
			return false;

		// parseContentRaw may be null when a ConfigProperties was never loaded.
		return Objects.equals(props1.parseContentRaw, props2.parseContentRaw);
	}

}
