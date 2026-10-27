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
package com.killcoding.tool;

import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.Map;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import com.killcoding.log.Logger;
import java.io.StringReader;
import java.io.Reader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Files;


import java.nio.file.Paths;

import java.util.Date;
import com.killcoding.file.BaseFile;
import java.util.concurrent.CopyOnWriteArrayList;
import com.killcoding.file.DiskFile;
import com.killcoding.tool.CipherTools;
import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
* Extended Properties: reads .properties files with ${VAR} environment expansion and typed value
* parsing (time -> seconds / millis via TIME_UNIT_PATTERN, file sizes via FILE_SIZE_PATTERN),
* returning the provided defaultValue instead of null when a value is missing or invalid.
* */
public class ConfigProperties extends Properties {

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
				@Override

				/**
				* Performs the run operation
				* */
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
			@Override

			/**
			* Performs the run operation
			* */
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

	@Override

	/**
	* Performs the load operation
	* @param inputStream - the inputStream
	* */
	public void load(InputStream inputStream) throws IOException {
		try {
			Reader reader = new InputStreamReader(inputStream,BaseFile.CHARSET);
			load(reader);
		} finally {
			if (inputStream != null)
				inputStream.close();
		}
	}

	@Override

	/**
	* Performs the load operation
	* @param reader - the reader
	* */
	public void load(Reader reader) throws IOException {
		try {
			int readChar;
			StringBuffer sbf = new StringBuffer();
			while ((readChar = reader.read()) != -1) {
				sbf.append((char) readChar);
			}
			this.parseContentRaw = parseContent(sbf.toString());
			super.load(new StringReader(this.parseContentRaw));
		} finally {
			if (reader != null)
				reader.close();
		}
	}

	/**
	* Performs the putAndReturnSlef operation
	* @param key - the key
	* @param value - the value
	* @return the putAndReturnSlef result
	* */
	public ConfigProperties putAndReturnSlef(String key, Object value) {
		if (isBlank(value)) {
			put(key, "");
		} else if (value instanceof Map) {
			Map<String, Object> subMap = conver(key, (Map<String, Object>) value);
			putAllAndReturnSlef(subMap);
		} else if (value instanceof List) {
			List<Object> list = (List<Object>) value;
			int size = list.size();
			for (int i = 0; i < size; i++) {
				Object item = list.get(i);
				String newKey = String.format("%s[%s]", key, i);
				putAndReturnSlef(newKey, item);
			}
		} else if (value instanceof Object[]) {
			List<Object> list = Arrays.asList((Object[]) value);
			int size = list.size();
			for (int i = 0; i < size; i++) {
				Object item = list.get(i);
				String newKey = String.format("%s[%s]", key, i);
				putAndReturnSlef(newKey, item);
			}
		} else if (value instanceof String) {
			put(key, value);
		} else {
			put(key, value.toString());
		}
		return this;
	}

	/**
	* Performs the removeAndReturnSlef operation
	* @param key - the key
	* @return the removeAndReturnSlef result
	* */
	public ConfigProperties removeAndReturnSlef(String key) {
		this.remove(key);
		return this;
	}

	/**
	* Performs the putAllAndReturnSlef operation
	* @param String - the String
	* @param map - the map
	* @return the putAllAndReturnSlef result
	* */
	public ConfigProperties putAllAndReturnSlef(Map<String, Object> map) {
		Set<String> keys = map.keySet();
		for (String key : keys) {
			Object value = map.get(key);
			putAndReturnSlef(key, value);
		}
		return this;
	}

	/**
	* Performs the removeAllNullValues operation
	* */
	public void removeAllNullValues() {
		Set<Object> keys = this.keySet();
		for (Object key : keys) {
			Object value = get(key);
			boolean blank = isBlank(value);
			if (blank) {
				this.remove(key);
				continue;
			}
		}
	}

	private Map<String, Object> conver(String parentKey, Map<String, Object> map) {
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

	public List<String> getArray(String key, String[] defaultValue) {
		return getArray(key, Arrays.asList(defaultValue));
	}

	public List<String> getArray(String key, List<String> defaultValue) {
		String[] list = null;
		Map<Integer, String> listMap = new HashMap<Integer, String>();
		List<Integer> listIndex = new ArrayList<Integer>();
		Set<Object> keys = keySet();
		for (Object _key : keys) {
			String format = String.format("^%s\\[(\\w+)\\]$", key);
			Pattern pattern = Pattern.compile(format);
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

	// 	public String getString(String key, String defaultValue) {
	// 	    String o = getProperty(key);
	// 	    if(o == null)
	// 	        return o;

	// 	    String originValue = o.trim();     
	// 	    Pattern p = Pattern.compile(ENV_VAR_REGEX);  
	//         Matcher m = p.matcher(originValue);
	//         boolean findEnvFormat = m.find();
	//         if(findEnvFormat){
	//             String envVar = m.group(1);
	//             String envDefValue = m.group(3);
	//             String envValue = getSysEnv(envVar);
	//     		if (!isBlank(envValue)) {
	//     			return envValue;
	//     		}  
	//     		if (!isBlank(envDefValue)) {
	//     			return envDefValue;
	//     		}      		
	//     		return defaultValue;
	//         }else{
	//     		if (isBlank(originValue)) {
	//     			return defaultValue;
	//     		}
	//     		return originValue.trim();
	//         }
	// 	}	

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
	    
	    if(originValue instanceof Number) return Short.parseShort(originValue + "");	   
	    
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
	    
	    if(originValue instanceof Number) return Integer.parseInt(originValue + "");
	    
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
	    
	    if(originValue instanceof Number) return Long.parseLong(originValue + "");	    
	    
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
	    
	    if(originValue instanceof Number) return Float.parseFloat(originValue + "");	    
	    
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
	    
	    if(originValue instanceof Number) return Double.parseDouble(originValue + "");	    
	    
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
			cp.putAllAndReturnSlef(map);
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
			cp.putAllAndReturnSlef(map);
			return cp;
		}
		return defaultValue;
	}

	public Map<String, Object> getMap(String key) {
		return getMap(key, null);
	}

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

		if (objectValue == null)
			return defaultValue;

		String stringValue = null;
		if (objectValue instanceof String) {
			stringValue = objectValue.toString().trim();
		} else {
			return Long.parseLong(objectValue.toString().trim());
		}

		Long value = null;
		try {
			Matcher matcher = TIME_UNIT_PATTERN.matcher(stringValue);
			if (matcher.find()) {
				Long originValue = Long.parseLong(matcher.group(1));
				String unit = matcher.group(2).toLowerCase();
				if (unit.equals("ms")) {
					value = originValue / 1000;
				}
				if (unit.equals("s")) {
					value = originValue;
				}
				if (unit.equals("m")) {
					value = originValue * 60;
				}
				if (unit.equals("h")) {
					value = originValue * 60 * 60;
				}
				if (unit.equals("d")) {
					value = originValue * 60 * 60 * 24;
				}
				if (unit.equals("w")) {
					value = originValue * 60 * 60 * 24 * 7;
				}
			} else {
				Logger.systemMark(ConfigProperties.class,
						"Missing unit(ms|s|m|h|d|w) '{}' will using default value '{}', (e.g. {}ms,{}s,...).",
						stringValue, defaultValue, stringValue);
			}
			return value == null ? defaultValue : value;
		} catch (Exception e) {
			Logger.systemError(ConfigProperties.class, e);
			return defaultValue;
		}
	}

	/**
	* Performs the parseTimeToMs operation
	* @param objectValue - the objectValue
	* @param defaultValue - the defaultValue
	* @return the parseTimeToMs result
	* */
	public static Long parseTimeToMs(Object objectValue, Long defaultValue) {

		if (objectValue == null)
			return defaultValue;

		String stringValue = null;
		if (objectValue instanceof String) {
			stringValue = objectValue.toString().trim();
		} else {
			return Long.parseLong(objectValue.toString().trim());
		}

		Long value = null;
		try {
			Matcher matcher = TIME_UNIT_PATTERN.matcher(stringValue);
			if (matcher.find()) {
				Long originValue = Long.parseLong(matcher.group(1));
				String unit = matcher.group(2).toLowerCase();
				if (unit.equals("ms")) {
					value = originValue;
				}
				if (unit.equals("s")) {
					value = originValue * 1000;
				}
				if (unit.equals("m")) {
					value = originValue * 60 * 1000;
				}
				if (unit.equals("h")) {
					value = originValue * 60 * 60 * 1000;
				}
				if (unit.equals("d")) {
					value = originValue * 60 * 60 * 1000 * 24;
				}
				if (unit.equals("w")) {
					value = originValue * 60 * 60 * 1000 * 24 * 7;
				}
			} else {
				Logger.systemMark(ConfigProperties.class,
						"Missing unit(ms|s|m|h|d|w) '{}' will using default value '{}', (e.g. {}ms,{}s,...).",
						stringValue, defaultValue, stringValue);
			}
			return value == null ? defaultValue : value;
		} catch (Exception e) {
			Logger.systemError(ConfigProperties.class, e);
			return defaultValue;
		}
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

		if (objectValue == null)
			return defaultValue;

		String stringValue = null;
		if (objectValue instanceof String) {
			stringValue = objectValue.toString().trim();
		} else {
			return Long.parseLong(objectValue.toString().trim());
		}

		Long value = null;
		try {
			Matcher matcher = FILE_SIZE_PATTERN.matcher(stringValue);
			if (matcher.find()) {
				Long originValue = Long.parseLong(matcher.group(1));
				String unit = matcher.group(2).toLowerCase();
				if (unit.equals("b")) {
					value = originValue;
				}
				if (unit.equals("kb")) {
					value = originValue * 1024;
				}
				if (unit.equals("mb")) {
					value = originValue * 1024 * 1024;
				}
				if (unit.equals("gb")) {
					value = originValue * 1024 * 1024 * 1024;
				}
				if (unit.equals("tb")) {
					value = originValue * 1024 * 1024 * 1024 * 1024;
				}
			} else {
				Logger.systemMark(ConfigProperties.class,
						"Missing unit(b|kb|mb|gb|tb) '{}' will using default value '{}', (e.g. {}mb,{}kb,...).",
						stringValue, defaultValue, stringValue);
			}
			return value == null ? defaultValue : value;
		} catch (Exception e) {
			Logger.systemError(ConfigProperties.class, e);
			return defaultValue;
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
		Pattern p = Pattern.compile(ENV_VAR_REGEX, Pattern.DOTALL | Pattern.MULTILINE);
		StringBuffer sb = new StringBuffer();
		Matcher matcher = p.matcher(content);
		while (matcher.find()) {
			String envValue = ConfigProperties.getEnv(matcher.group());
			envValue = (envValue == null ? replaceNullChar : envValue);
			matcher.appendReplacement(sb, envValue);
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

		return props1.parseContentRaw.equals(props2.parseContentRaw);
	}

}