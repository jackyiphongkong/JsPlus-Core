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

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Random;
import java.net.InetAddress;
import com.killcoding.log.Logger;
import java.net.URL;

import java.io.File;
import com.killcoding.log.LoggerFactory;
import java.net.URLClassLoader;

import java.security.MessageDigest;
import com.killcoding.file.BaseFile;
import java.util.Arrays;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.IOException;

/**
 * Common static utility methods: hostname cache (volatile HOSTNAME),
 * external-jar loading, blank /
 * null checks and miscellaneous helpers shared across the killcoding packages.
 */
public final class CommonTools {

	private static volatile String HOSTNAME = null;

	/**
	 * Performs the loadJars operation
	 * 
	 * @param libDirs - the libDirs
	 * @return the loadJars result
	 */
	public static URLClassLoader loadJars(String libDirs) throws Exception {
		return loadJars(libDirs, ClassLoader.getSystemClassLoader());
	}

	/**
	 * Performs the loadJars operation
	 * 
	 * @param libDirs - the libDirs
	 * @return the loadJars result
	 */
	public static URLClassLoader loadJars(String[] libDirs) throws Exception {
		return loadJars(libDirs, ClassLoader.getSystemClassLoader());
	}

	/**
	 * Performs the loadJars operation
	 * 
	 * @param libDirs     - the libDirs
	 * @param classLoader - the classLoader
	 * @return the loadJars result
	 */
	public static URLClassLoader loadJars(String[] libDirs, ClassLoader classLoader) throws Exception {
		return loadJars(String.join(";", libDirs), classLoader);
	}

	/**
	 * Performs the loadJars operation
	 * 
	 * @param libDirs     - the libDirs
	 * @param classLoader - the classLoader
	 * @return the loadJars result
	 */
	public static URLClassLoader loadJars(String libDirs, ClassLoader classLoader) throws Exception {
		if (libDirs == null)
			return null;

		List<URL> urlList = new ArrayList<URL>();
		List<String> libDirArr = Arrays.asList(libDirs.split(";"));
		for (String libDir : libDirArr) {
			File jars = new File(libDir);
			if (!jars.exists())
				continue;

			if (jars.isDirectory()) {
				List<String> list = findFiles(jars.getAbsolutePath(), ".jar");
				for (String jarPath : list) {
					urlList.add(new File(jarPath).toURI().toURL());
				}
			}
			if (jars.isFile()) {
				boolean isJar = jars.getName().toLowerCase().endsWith(".jar");

				if (isJar)
					urlList.add(jars.toURI().toURL());
			}
		}

		URL[] urls = urlList.toArray(new URL[] {});

		URLClassLoader cl = new URLClassLoader(urls, classLoader);
		return cl;
	}

	/**
	 * Recursively finds files under a directory with the given extension.
	 * 
	 * @param dir - the root directory
	 * @param ext - the file extension filter (e.g. "java")
	 * @return the absolute paths of the matching files
	 */
	public static List<String> findFiles(String dir, String ext) throws IOException {

		if (!new File(dir).exists())
			return new ArrayList<String>();

		List<Path> paths = Files.walk(Paths.get(dir))
				.filter(Files::isRegularFile)
				.filter(path -> path.toString().toLowerCase().endsWith(ext))
				.collect(Collectors.toList());

		return paths.stream()
				.map(path -> path.toAbsolutePath().toString())
				.collect(Collectors.toList());
	}

	/**
	 * Recursively finds every file under a directory.
	 * 
	 * @param dir - the root directory
	 * @return the absolute paths of all files
	 */
	public static List<String> findFiles(String dir) throws IOException {

		if (!new File(dir).exists())
			return new ArrayList<String>();

		List<Path> paths = Files.walk(Paths.get(dir))
				.filter(Files::isRegularFile)
				.collect(Collectors.toList());

		return paths.stream()
				.map(path -> path.toAbsolutePath().toString())
				.collect(Collectors.toList());
	}

	/**
	 * Recursively finds every sub-directory under a directory.
	 * 
	 * @param dir - the root directory
	 * @return the absolute paths of the sub-directories
	 */
	public static List<String> findSubDirs(String dir) throws IOException {

		if (!new File(dir).exists())
			return new ArrayList<String>();

		List<Path> paths = Files.walk(Paths.get(dir))
				.filter(Files::isDirectory)
				.collect(Collectors.toList());

		return paths.stream()
				.map(path -> path.toAbsolutePath().toString())
				.collect(Collectors.toList());
	}

	/**
	 * Performs the getJarPath operation
	 * 
	 * @param clazz - the clazz
	 * @return the getJarPath result
	 */
	public static String getJarPath(Class<?> clazz) {
		try {
			return new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI())
					.getParentFile().getPath().replaceAll("\\\\", "/");
		} catch (Exception e) {
			Logger.systemError(CommonTools.class, e);
			return null;
		}
	}

	/**
	 * Performs the isBlank operation
	 * 
	 * @param o - the o
	 * @return the isBlank result
	 */
	public static boolean isBlank(Object o) {
		if (o == null)
			return true;

		if (o.toString().trim().equals(""))
			return true;

		return false;
	}

	/**
	 * Performs the getSystemProperties operation
	 * 
	 * @param clazz        - the clazz
	 * @param envKey       - the envKey
	 * @param propFileName - the propFileName
	 * @return the getSystemProperties result
	 */
	public static File getSystemProperties(Class<?> clazz, String envKey, String propFileName) {
		try {
			File configFile = null;
			String configPath = null;
			try {
				configPath = System.getenv(envKey) == null ? System.getProperty(envKey) : System.getenv(envKey);
				if (configPath != null) {
					configFile = new File(configPath);
					if (!configFile.exists()) {
						Logger.systemError(CommonTools.class, "Missing {}", configFile);
					}
					return configFile;
				}

				String jsPlusPath = System.getenv("JS_PLUS_PATH");
				if(jsPlusPath != null){
					configPath = jsPlusPath + "/config/" + propFileName;
					configFile = new File(configPath);
					if (!configFile.exists()) {
						Logger.systemDebug(CommonTools.class, "Missing {}", configFile);
					}
					return configFile;	
				}
				
				configPath = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI())
						.getParentFile().getPath();
				configFile = new File(String.format("%s/%s", configPath, propFileName));
				if (configFile.exists()) {
					return configFile;
				}else{
					Logger.systemDebug(CommonTools.class, "Missing {}", configFile);
				}
				configFile = new File(String.format("%s/config/%s", configPath, propFileName));
				if (!configFile.exists()) {
					Logger.systemDebug(CommonTools.class, "Missing {}", configFile);
				}
				return configFile;
			} catch (Exception e) {
				Logger.systemDebug(CommonTools.class, "Missing class path.");
				Logger.systemDebug(CommonTools.class, e.getMessage(), e);
			}
		} catch (Exception e) {
			Logger.systemError(CommonTools.class, e);
		}
		Logger.systemError(CommonTools.class, "Missing {}", propFileName);
		return null;
	}

	/**
	 * Performs the getHostname operation
	 * 
	 * @return the getHostname result
	 */
	public synchronized static String getHostname() {

		if (HOSTNAME != null)
			return HOSTNAME;

		HOSTNAME = System.getenv("HOSTNAME");
		if (HOSTNAME == null) {
			try {
				InetAddress addr = InetAddress.getLocalHost();
				HOSTNAME = addr.getHostName();
				System.setProperty("HOSTNAME", HOSTNAME);
				System.setProperty("hostname", HOSTNAME);
			} catch (Exception e) {
				Logger.systemError(CommonTools.class, e);
			}
		}
		return HOSTNAME;
	}

	/**
	 * Performs the generateId operation
	 * 
	 * @param len - the len
	 * @return the generateId result
	 */
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

	/**
	 * Splits a list into roughly equal-sized chunks of at most the given limit.
	 * 
	 * @param source - the source list
	 * @param limit  - the maximum chunk size
	 * @return the partitioned list
	 */
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

	/**
	 * Performs the md5 operation
	 * 
	 * @param text - the text
	 * @return the md5 result
	 */
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
			LoggerFactory.getLogger(CommonTools.class).error(e.getMessage(), e);
			return null;
		}

	}

}
