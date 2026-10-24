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
import java.util.Enumeration;
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
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.net.URI;
import java.util.Date;
import com.killcoding.file.BaseFile;
import java.util.concurrent.CopyOnWriteArrayList;
import com.killcoding.file.DiskFile;
import com.killcoding.tool.CipherTools;
import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class ConfigProperties extends Properties {

	public static final String ENV_VAR_REGEX = "[\\$%#]{1}\\{?([\\.\\w-]+)(:{1}([^\\}]+)){0,1}\\}";

	public static String DATETIME_PATTERN = "yyyy.MM.dd HH:mm:ss[.SSSSSSSSS]";
	
	public static String Config_Cipher_AesKey = null;
	
    public static Long AUTO_LOAD_TIMER = 5000L;
	private static final List<Runnable> AUTO_LOAD_CP = new CopyOnWriteArrayList<Runnable>();

    private static ConfigCustomLoad CUSTOMLOAD = null;
    
	protected static ExecutorService executor = null;

	private String parseContentRaw = null;

	public ConfigProperties(int arg0) {
		super(arg0);
	}

	public ConfigProperties(Properties arg0) {
		super(arg0);
	}

	public ConfigProperties() {
		super();
	}

	private void addAutoLoad(Runnable cpRun) {
		AUTO_LOAD_CP.add(cpRun);
		startAutoLoad();
	}

	private static synchronized void startAutoLoad() { 
		if (executor == null) {
			executor = Executors.newFixedThreadPool(1);
			executor.execute(new Runnable() {
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
	
	private static Long getAutoLoadTimer(){
	    return AUTO_LOAD_TIMER;
	}

	public static void setConfigCustom(ConfigCustomLoad customLoad){
	    ConfigProperties.CUSTOMLOAD =  customLoad;
	}	
	
	private static boolean loadConfigCustom(File file,ConfigProperties target){
	    if(ConfigProperties.CUSTOMLOAD != null){
    	    ConfigProperties.CUSTOMLOAD.load(file,target);
    	    return true;
	    }
	    return false;
	}

	public synchronized void load(File file, Runnable updatedRunnable) throws IOException {
		this.load(file);
		final ConfigProperties the = this;
		final Runnable cpRun = new Runnable() {
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

	public ConfigProperties removeAndReturnSlef(String key) {
		this.remove(key);
		return this;
	}

	public ConfigProperties putAllAndReturnSlef(Map<String, Object> map) {
		Set<String> keys = map.keySet();
		for (String key : keys) {
			Object value = map.get(key);
			putAndReturnSlef(key, value);
		}
		return this;
	}

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

	public Boolean getBoolean(String key) {
		return getBoolean(key, null);
	}

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

	public String getString(String key) {
		return getString(key, null);
	}

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

	public Short getShort(String key) {
		return getShort(key, null);
	}

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

	public Integer getInteger(String key) {
		return getInteger(key, null);
	}

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

	public Long getLong(String key) {
		return getLong(key, null);
	}

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

	public Float getFloat(String key) {
		return getFloat(key, null);
	}

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

	public Double getDouble(String key) {
		return getDouble(key, null);
	}

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

	public Long getSeconds(String key) {
		return getSeconds(key, null);
	}

	public Long getSeconds(String key, Long defaultValue) {
		return parseTimeToSec(getString(key, null), defaultValue);
	}

	public Long getMilliSeconds(String key) {
		return getMilliSeconds(key, null);
	}

	public Long getMilliSeconds(String key, Long defaultValue) {
		return parseTimeToMs(getString(key, null), defaultValue);
	}

	public Long getFileSize(String key, Long defaultValue) {
		return parseFileSize(getString(key, null), defaultValue);
	}
	
	public Date getDateTime(String key, String defaultValue) {
		return parseDateTime(getString(key, null), defaultValue);
	}
	
	public Date getDateTime(String key) {
		return getDateTime(key,null);
	}	

	public ConfigProperties getConfigProperties(String key) {
		Map<String, Object> map = getMap(key);
		if (map != null) {
			ConfigProperties cp = new ConfigProperties();
			cp.putAllAndReturnSlef(map);
			return cp;
		}
		return null;
	}

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
	
	public String getContentRaw(){
	    return this.parseContentRaw;
	}

	public static boolean isBlank(Object o) {
		return CommonTools.isBlank(o);
	}

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
			Pattern pattern = Pattern.compile("^([-\\+]{0,1}[0-9]+)(((?i)ms|s|m|h|d|w){1})$");
			Matcher matcher = pattern.matcher(stringValue);
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
						stringValue, defaultValue, stringValue, stringValue);
			}
			return value == null ? defaultValue : value;
		} catch (Exception e) {
			Logger.systemError(ConfigProperties.class, e);
			return null;
		}
	}

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
			Pattern pattern = Pattern.compile("^([-\\+]{0,1}[0-9]+)(((?i)ms|s|m|h|d|w){1})$");
			Matcher matcher = pattern.matcher(stringValue);
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
						stringValue, defaultValue, stringValue, stringValue);
			}
			return value == null ? defaultValue : value;
		} catch (Exception e) {
			Logger.systemError(ConfigProperties.class, e);
			return null;
		}
	}
	
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
			Pattern pattern = Pattern.compile("^([-\\+]{0,1}[0-9]+)(((?i)b|kb|mb|gb|tb){1})$");
			Matcher matcher = pattern.matcher(stringValue);
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
						stringValue, defaultValue, stringValue, stringValue);
			}
			return value == null ? defaultValue : value;
		} catch (Exception e) {
			Logger.systemError(ConfigProperties.class, e);
			return null;
		}
	}

	public static String getSysEnv(String envKey) {
		return System.getenv(envKey) == null ? System.getProperty(envKey) : System.getenv(envKey);
	}

	public static String getEnv(String originValue) {
		if (originValue == null)
			return null;

		Pattern p = Pattern.compile(ENV_VAR_REGEX);
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

	public static String parseContent(String content) {
		return parseContent(content, "");
	}

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

	public static boolean compareProperties(ConfigProperties props1, ConfigProperties props2) {

		if (props1 == null || props2 == null)
			return false;

		return props1.parseContentRaw.equals(props2.parseContentRaw);
	}

}