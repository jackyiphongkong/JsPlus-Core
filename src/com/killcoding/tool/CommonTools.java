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

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Random;
import java.net.InetAddress;
import com.killcoding.log.Logger;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.File;
import com.killcoding.log.LoggerFactory;
import java.net.URLClassLoader;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import com.killcoding.file.BaseFile;
import java.util.Arrays;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.LinkOption;

public final class CommonTools {
    
    private static String HOSTNAME = null;


	public static URLClassLoader loadJars(String libDirs) throws Exception {
        return loadJars(libDirs,ClassLoader.getSystemClassLoader());
	}
	
	public static URLClassLoader loadJars(String[] libDirs) throws Exception {
        return loadJars(libDirs,ClassLoader.getSystemClassLoader());
	}	
	
	public static URLClassLoader loadJars(String[] libDirs,ClassLoader classLoader) throws Exception {
	    return loadJars(String.join(";",libDirs),classLoader);
	}

	public static URLClassLoader loadJars(String libDirs,ClassLoader classLoader) throws Exception {
		if (libDirs == null) 
			return null;
	
		List<URL> urlList = new ArrayList<URL>();
		List<String> libDirArr = Arrays.asList(libDirs.split(";"));
		for(String libDir : libDirArr){
    		File jars = new File(libDir);
    		if(!jars.exists())
    		    continue;
    		
    		if(jars.isDirectory()){    
        		List<String> list = findFiles(jars.getAbsolutePath(),".jar");
        		for(String jarPath : list){
        		    urlList.add(new File(jarPath).toURL());
        		}
    		}
    		if(jars.isFile()){
    		   boolean isJar = jars.getName().toLowerCase().endsWith(".jar");

    		   if(isJar) urlList.add(jars.toURL());
    		}
		}

		URL[] urls = urlList.toArray(new URL[]{});
		
		URLClassLoader cl = new URLClassLoader(urls,classLoader);
		return cl;
	}
	
    public static List<String> findFiles(String dir,String ext) throws IOException {
        
        if(!new File(dir).exists()) return new ArrayList<String>();
        
        List<Path> paths = Files.walk(Paths.get(dir))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(ext))
                .collect(Collectors.toList());
                
        return paths.stream()
                      .map(path -> path.toAbsolutePath().toString())
                      .collect(Collectors.toList());
    }
    
    public static List<String> findFiles(String dir) throws IOException {
        
        if(!new File(dir).exists()) return new ArrayList<String>();
        
        List<Path> paths = Files.walk(Paths.get(dir))
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
                
        return paths.stream()
                      .map(path -> path.toAbsolutePath().toString())
                      .collect(Collectors.toList());                
    }    
    
    public static List<String> findSubDirs(String dir) throws IOException {
        
        if(!new File(dir).exists()) return new ArrayList<String>();
        
        List<Path> paths = Files.walk(Paths.get(dir))
                .filter(Files::isDirectory)
                .collect(Collectors.toList());
                
        return paths.stream()
                      .map(path -> path.toAbsolutePath().toString())
                      .collect(Collectors.toList());                
    }    

	public static String getJarPath(Class clazz) {
		try {
			return new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI())
					.getParentFile().getPath().replaceAll("\\\\", "/");
		} catch (Exception e) {
			Logger.systemError(CommonTools.class, e);
			return null;
		}
	}
	
	public static boolean isBlank(Object o) {
		if (o == null)
			return true;

		if (o.toString().trim().equals(""))
			return true;

		return false;
	}

	public static File getSystemProperties(Class clazz, String envKey, String propFileName) {
		try {
			File envFile = null;

			String envFilePath = System.getenv(envKey) == null ? System.getProperty(envKey) : System.getenv(envKey);
			if (envFilePath != null) {
				envFile = new File(envFilePath);
				if(!envFile.exists()){
				    Logger.systemDebug(CommonTools.class,"Missing %s",envFilePath);
				}
			}

			if (envFile != null && envFile.exists()) {
			    Logger.systemDebug(CommonTools.class,"Found %s",envFile.getPath());
				return envFile;
			}
			
			String jarPath = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getPath();
			File jarConfigFile = new File(String.format("%s/%s", jarPath, propFileName));

			if(!jarConfigFile.exists()) {
			    jarConfigFile = new File(String.format("%s/config/%s", jarPath, propFileName));
			}
			
            if (jarConfigFile.exists()) {
                Logger.systemDebug(CommonTools.class,"Found %s",jarConfigFile.getPath());
				return jarConfigFile;
			}			
			
			String jarParentPath = new File(jarPath).getParentFile()
					.getPath();
					
			File jarParentConfigFile = new File(String.format("%s/%s", jarParentPath, propFileName));
			
			if(!jarParentConfigFile.exists()) {
			    jarParentConfigFile = new File(String.format("%s/config/%s", jarParentPath, propFileName));
			}			
			
            if (jarParentConfigFile.exists()) {
                Logger.systemDebug(CommonTools.class,"Found %s",jarParentConfigFile.getPath());
				return jarParentConfigFile;
			}
			
			URL classPathFileUrl = clazz.getResource(String.format("/%s", propFileName));
			if (classPathFileUrl != null) {
			    Logger.systemDebug(CommonTools.class,"Found %s",classPathFileUrl.getPath());
				return new File(classPathFileUrl.toURI());
			}
			
            classPathFileUrl = clazz.getResource(String.format("/config/%s", propFileName));
			if (classPathFileUrl != null) {
			    Logger.systemDebug(CommonTools.class,"Found %s",classPathFileUrl.getPath());
				return new File(classPathFileUrl.toURI());
			}
			try{
    			classPathFileUrl = clazz.getResource("/");
    			if (classPathFileUrl != null) {
    			    String classParentPath = new File(classPathFileUrl.toURI()).getParent();
    			    if(classParentPath != null){
        			    File classParentConfigFile = new File(String.format("%s/%s", classParentPath, propFileName));
        			    
        			    if(!classParentConfigFile.exists()) {
        			        classParentConfigFile = new File(String.format("%s/config/%s", classParentPath, propFileName));
        			    }
        			    
        			    if(!classParentConfigFile.exists()) {
        			        Logger.systemDebug(CommonTools.class,"Found %s",classParentConfigFile.getPath());
        			        return classParentConfigFile;
        			    }
    			    }
    			}	
			}catch(Exception e){
			    Logger.systemDebug(CommonTools.class,"Missing class path.");
			    Logger.systemDebug(CommonTools.class, e.getMessage(),e);
			}
		} catch (Exception e) {
            Logger.systemError(CommonTools.class, e);
		}
		Logger.systemDebug(CommonTools.class,"Missing %s",propFileName);
		return null;
	}

	public synchronized static String getHostname() {
	    
	    if(HOSTNAME != null) return HOSTNAME;
	    
	    HOSTNAME = System.getenv("HOSTNAME");
        if(HOSTNAME == null){
    		try {
    			InetAddress addr = InetAddress.getLocalHost();
    			HOSTNAME = addr.getHostName();
    			System.setProperty("HOSTNAME",HOSTNAME);
    			System.setProperty("hostname",HOSTNAME);
    		} catch (Exception e) {
    			Logger.systemError(CommonTools.class, e);
    		}
        }
		return HOSTNAME;
	}

	public static String generateId(int len) {
		final Character[] CHARS = new Character[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c',
				'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
				'y', 'z' };
		StringBuffer id = new StringBuffer();
		for (int i = 0; i < len; i++) {
			char c = CHARS[new Random().nextInt(CHARS.length)];
			id.append(c);
		}
		return id.toString();
	}

	public static <T> List<List<T>> averageAssign(List<T> source, int limit) {
		if (null == source || source.isEmpty()) {
			return Collections.emptyList();
		}
		List<List<T>> result = new ArrayList<>();
		int listCount = (source.size() - 1) / limit + 1;
		int remaider = source.size() % listCount;
		int number = source.size() / listCount;
		int offset = 0;
		for (int i = 0; i < listCount; i++) {
			List<T> value;
			if (remaider > 0) {
				value = source.subList(i * number + offset, (i + 1) * number + offset + 1);
				remaider--;
				offset++;
			} else {
				value = source.subList(i * number + offset, (i + 1) * number + offset);
			}
			result.add(value);
		}
		return result;
	}
	
	public static String md5(String text) {
		char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
				'A', 'B', 'C', 'D', 'E', 'F' };

		try {
			byte[] btInput = text.getBytes(BaseFile.CHARSET);
			MessageDigest mdInst = MessageDigest.getInstance("MD5");
			mdInst.update(btInput);
			byte[] md = mdInst.digest();
			int j = md.length;
			char str[] = new char[j * 2];
			int k = 0;
			for (int i = 0; i < j; i++) {
				byte byte0 = md[i];
				str[k++] = hexDigits[byte0 >>> 4 & 0xf];
				str[k++] = hexDigits[byte0 & 0xf];
			}
			return new String(str);
		} catch (Exception e) {
		    LoggerFactory.getLogger(CommonTools.class).error(e.getMessage(),e);
			return null;
		}

	}	

}