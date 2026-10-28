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

import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Arrays;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;
import com.killcoding.tool.CodeEscape;
import com.killcoding.log.Logger;
import java.util.ArrayList;

/**
* This is logger class
* Sample,
* Logger log = LoggerFactory.getLogger(clazz);
* */
public final class Logger {

	private final static String DEFAULT_MESSAGE_FORMAT = ":level :log_time_format [:log_thread_name-:log_thread_id] :log_class_name.:log_method_name(:log_file_name::log_line_number) **:message** \r\n:stack_trace";

	public final static List<String> LEVEL_LIST = Arrays.asList(new String[] { "DEBUG", "INFO", "WARN", "ERROR" });
	public final static Integer LEVEL_DEBUG = 0;
	public final static Integer LEVEL_INFO = 1;
	public final static Integer LEVEL_WARN = 2;
	public final static Integer LEVEL_ERROR = 3;
	public final static Integer LEVEL_MARK = 4;

	private static Integer LEVEL = LEVEL_DEBUG;

	private String clazzName;

	private String hostname;

	private static String classNameEnableRegex = null;
	private static String classNameDisableRegex = null;

	/**
	* Constructs a new Logger
	* @param clazz - the clazz
	* */
	public Logger(Class<?> clazz) {
		super();
		this.clazzName = clazz.getName();
		hostname = CommonTools.getHostname();
	}

	/**
	* Constructs a new Logger
	* @param clazzName - the clazzName
	* */
	public Logger(String clazzName) {
		super();
		this.clazzName = clazzName;
		hostname = CommonTools.getHostname();
	}

	/**
	* Performs the setClassNameEnableRegex operation
	* @param _classNameEnableRegex - the _classNameEnableRegex
	* */
	protected static void setClassNameEnableRegex(String _classNameEnableRegex) {
		classNameEnableRegex = _classNameEnableRegex;
	}

	/**
	* Performs the setClassNameDisableRegex operation
	* @param _classNameDisableRegex - the _classNameDisableRegex
	* */
	protected static void setClassNameDisableRegex(String _classNameDisableRegex) {
		classNameDisableRegex = _classNameDisableRegex;
	}

	/**
	* Performs the checkLevel operation
	* @param _level - the _level
	* @return the checkLevel result
	* */
	protected static boolean checkLevel(String _level) {
		if (_level == null)
			return false;

		return LEVEL_LIST.contains(_level);
	}

	/**
	* Performs the setLevel operation
	* @param _level - the _level
	* */
	public static void setLevel(String _level) {
		if (checkLevel(_level)) {
			setLevel(LEVEL_LIST.indexOf(_level.toUpperCase()));
		}
	}

	/**
	* Performs the setLevel operation
	* @param level - the level
	* */
	public static void setLevel(Integer level) {
		LEVEL = (level == null ? LEVEL_DEBUG : level);
	}

	/**
	* Performs the isDebugEnabled operation
	* @return the isDebugEnabled result
	* */
	public boolean isDebugEnabled() {
		return LEVEL == LEVEL_DEBUG;
	}

	/**
	* Performs the isInfoEnabled operation
	* @return the isInfoEnabled result
	* */
	public boolean isInfoEnabled() {
		return LEVEL == LEVEL_INFO;
	}

	/**
	* Performs the isWarnEnabled operation
	* @return the isWarnEnabled result
	* */
	public boolean isWarnEnabled() {
		return LEVEL == LEVEL_WARN;
	}

	/**
	* Performs the isErrorEnabled operation
	* @return the isErrorEnabled result
	* */
	public boolean isErrorEnabled() {
		return LEVEL == LEVEL_ERROR;
	}

	/**
	* Performs the debug operation
	* @param obj - the obj
	* */
	public void debug(Object obj) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", obj + "");
				log(LEVEL_DEBUG, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the debug operation
	* @param message - the message
	* */
	public void debug(String message) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				log(LEVEL_DEBUG, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the debug operation
	* @param message - the message
	* @param exception - the exception
	* */
	public void debug(String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				map.put("exception", exception);
				log(LEVEL_DEBUG, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the debug operation
	* @param exception - the exception
	* */
	public void debug(Exception exception) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", exception.getMessage());
				map.put("exception", exception);
				log(LEVEL_DEBUG, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void debug(String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log(LEVEL_DEBUG, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the debug operation
	* @param message - the message
	* @param obj - the obj
	* */
	public void debug(String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				Object[] params = new Object[] { obj + "" };
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log(LEVEL_DEBUG, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the info operation
	* @param message - the message
	* */
	public void info(String message) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				log(LEVEL_INFO, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the info operation
	* @param obj - the obj
	* */
	public void info(Object obj) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", obj + "");
				log(LEVEL_INFO, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the info operation
	* @param message - the message
	* @param exception - the exception
	* */
	public void info(String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				map.put("exception", exception);
				log(LEVEL_INFO, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the info operation
	* @param exception - the exception
	* */
	public void info(Exception exception) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", exception.getMessage());
				map.put("exception", exception);
				log(LEVEL_INFO, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void info(String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log(LEVEL_INFO, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the info operation
	* @param message - the message
	* @param obj - the obj
	* */
	public void info(String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				Object[] params = new Object[] { obj + "" };
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log(LEVEL_INFO, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the warn operation
	* @param message - the message
	* */
	public void warn(String message) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				log(LEVEL_WARN, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the warn operation
	* @param obj - the obj
	* */
	public void warn(Object obj) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", obj + "");
				log(LEVEL_WARN, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the warn operation
	* @param message - the message
	* @param exception - the exception
	* */
	public void warn(String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				map.put("exception", exception);
				log(LEVEL_WARN, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the warn operation
	* @param exception - the exception
	* */
	public void warn(Exception exception) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", exception.getMessage());
				map.put("exception", exception);
				log(LEVEL_WARN, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void warn(String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log(LEVEL_WARN, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the warn operation
	* @param message - the message
	* @param obj - the obj
	* */
	public void warn(String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				Object[] params = new Object[] { obj + "" };
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log(LEVEL_WARN, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the error operation
	* @param message - the message
	* */
	public void error(String message) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				log(LEVEL_ERROR, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the error operation
	* @param obj - the obj
	* */
	public void error(Object obj) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", obj + "");
				log(LEVEL_ERROR, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the error operation
	* @param message - the message
	* @param exception - the exception
	* */
	public void error(String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				map.put("exception", exception);
				log(LEVEL_ERROR, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the error operation
	* @param exception - the exception
	* */
	public void error(Exception exception) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", exception.getMessage());
				map.put("exception", exception);
				log(LEVEL_ERROR, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void error(String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log(LEVEL_ERROR, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the error operation
	* @param message - the message
	* @param obj - the obj
	* */
	public void error(String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				Object[] params = new Object[] { obj + "" };
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log(LEVEL_ERROR, map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the mark operation
	* @param message - the message
	* */
	public void mark(String message) {
		try {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("message", message);
			log(LEVEL_MARK, map);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the mark operation
	* @param obj - the obj
	* */
	public void mark(Object obj) {
		try {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("message", obj + "");
			log(LEVEL_MARK, map);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void mark(String message, Object... params) {
		try {
			String msg = (message + "").replaceAll("\\{\\}", "%s");
			String _message = stringFormat(msg, converParams(params));
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("message", _message);
			log(LEVEL_MARK, map);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the mark operation
	* @param message - the message
	* @param obj - the obj
	* */
	public void mark(String message, Object obj) {
		try {
			String msg = (message + "").replaceAll("\\{\\}", "%s");
			Object[] params = new Object[] { obj + "" };
			String _message = stringFormat(msg, converParams(params));
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("message", _message);
			log(LEVEL_MARK, map);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemError operation
	* @param clazz - the clazz
	* @param message - the message
	* */
	public static void systemError(String clazz, String message) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					log("ERROR", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).error(message);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemError operation
	* @param clazz - the clazz
	* @param obj - the obj
	* */
	public static void systemError(String clazz, Object obj) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				String message = obj + "";
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					log("ERROR", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).error(message);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemError operation
	* @param clazz - the clazz
	* @param message - the message
	* */
	public static void systemError(Class<?> clazz, String message) {
		if (LEVEL <= LEVEL_ERROR) {
			if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				log("ERROR", clazz, map);
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).error(message);
			}
		}
	}

	/**
	* Performs the systemError operation
	* @param clazz - the clazz
	* @param obj - the obj
	* */
	public static void systemError(Class<?> clazz, Object obj) {
		if (LEVEL <= LEVEL_ERROR) {
			String message = obj + "";
			if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				log("ERROR", clazz, map);
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).error(message);
			}
		}
	}

	/**
	* Performs the systemMark operation
	* @param clazz - the clazz
	* @param message - the message
	* @param exception - the exception
	* */
	public static void systemMark(String clazz, String message, Exception exception) {
		try {
			if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				map.put("exception", exception);
				log("MARK", clazz, map);
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).mark(message, exception);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemMark operation
	* @param clazz - the clazz
	* @param message - the message
	* @param exception - the exception
	* */
	public static void systemMark(Class<?> clazz, String message, Exception exception) {
		try {
			if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				map.put("exception", exception);
				log("MARK", clazz, map);
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).mark(message, exception);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void systemMark(String clazz, String message, Object... params) {
		try {
			if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log("MARK", clazz, map);
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).mark(message, params);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the systemMark operation
	* @param clazz - the clazz
	* @param message - the message
	* @param obj - the obj
	* */
	public static void systemMark(String clazz, String message, Object obj) {
		try {
			Object[] params = new Object[] { obj + "" };
			if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log("MARK", clazz, map);
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).mark(message, params);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemMark operation
	* @param clazz - the clazz
	* @param obj - the obj
	* */
	public static void systemMark(String clazz, Object obj) {
		try {
			String message = obj + "";
			if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				log("MARK", clazz, map);
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).mark(message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void systemMark(Class<?> clazz, String message, Object... params) {
		try {
			if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log("MARK", clazz, map);
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).mark(message, params);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the systemMark operation
	* @param clazz - the clazz
	* @param message - the message
	* @param obj - the obj
	* */
	public static void systemMark(Class<?> clazz, String message, Object obj) {
		try {
			Object[] params = new Object[] { obj + "" };
			if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
				String msg = (message + "").replaceAll("\\{\\}", "%s");
				String _message = stringFormat(msg, converParams(params));
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", _message);
				log("MARK", clazz, map);
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).mark(message, params);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemMark operation
	* @param clazz - the clazz
	* @param obj - the obj
	* */
	public static void systemMark(Class<?> clazz, Object obj) {
		try {
			String message = obj + "";
			if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("message", message);
				log("MARK", clazz, map);
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).mark(message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemError operation
	* @param clazz - the clazz
	* @param message - the message
	* @param exception - the exception
	* */
	public static void systemError(String clazz, String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					map.put("exception", exception);
					log("ERROR", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).error(message, exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemError operation
	* @param clazz - the clazz
	* @param message - the message
	* @param exception - the exception
	* */
	public static void systemError(Class<?> clazz, String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					map.put("exception", exception);
					log("ERROR", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).error(message, exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemError operation
	* @param clazz - the clazz
	* @param exception - the exception
	* */
	public static void systemError(String clazz, Exception exception) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", exception.getMessage());
					map.put("exception", exception);
					log("ERROR", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).error(exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemError operation
	* @param clazz - the clazz
	* @param exception - the exception
	* */
	public static void systemError(Class<?> clazz, Exception exception) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", exception.getMessage());
					map.put("exception", exception);
					log("ERROR", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).error(exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void systemError(String clazz, String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("ERROR", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).error(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the systemError operation
	* @param clazz - the clazz
	* @param message - the message
	* @param obj - the obj
	* */
	public static void systemError(String clazz, String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				Object[] params = new Object[] { obj + "" };
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("ERROR", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).error(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void systemError(Class<?> clazz, String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("ERROR", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).error(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the systemError operation
	* @param clazz - the clazz
	* @param message - the message
	* @param obj - the obj
	* */
	public static void systemError(Class<?> clazz, String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_ERROR) {
				Object[] params = new Object[] { obj + "" };
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("ERROR", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).error(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemWarn operation
	* @param clazz - the clazz
	* @param message - the message
	* */
	public static void systemWarn(String clazz, String message) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					log("WARN", clazz, map);
				}
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).warn(message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemWarn operation
	* @param clazz - the clazz
	* @param message - the message
	* */
	public static void systemWarn(Class<?> clazz, String message) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					log("WARN", clazz, map);
				}
			}
			if (LoggerFactory.isLoadedConfig()) {
				LoggerFactory.getLogger(clazz).warn(message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemWarn operation
	* @param clazz - the clazz
	* @param message - the message
	* @param exception - the exception
	* */
	public static void systemWarn(String clazz, String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					map.put("exception", exception);
					log("WARN", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).warn(message, exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemWarn operation
	* @param clazz - the clazz
	* @param message - the message
	* @param exception - the exception
	* */
	public static void systemWarn(Class<?> clazz, String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					map.put("exception", exception);
					log("WARN", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).warn(message, exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemWarn operation
	* @param clazz - the clazz
	* @param exception - the exception
	* */
	public static void systemWarn(String clazz, Exception exception) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", exception.getMessage());
					map.put("exception", exception);
					log("WARN", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).warn(exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemWarn operation
	* @param clazz - the clazz
	* @param exception - the exception
	* */
	public static void systemWarn(Class<?> clazz, Exception exception) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", exception.getMessage());
					map.put("exception", exception);
					log("WARN", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).warn(exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void systemWarn(String clazz, String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("WARN", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).warn(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the systemWarn operation
	* @param clazz - the clazz
	* @param message - the message
	* @param obj - the obj
	* */
	public static void systemWarn(String clazz, String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				Object[] params = new Object[] { obj + "" };
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("WARN", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).warn(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void systemWarn(Class<?> clazz, String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("WARN", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).warn(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the systemWarn operation
	* @param clazz - the clazz
	* @param message - the message
	* @param obj - the obj
	* */
	public static void systemWarn(Class<?> clazz, String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_WARN) {
				Object[] params = new Object[] { obj + "" };
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("WARN", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).warn(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemInfo operation
	* @param clazz - the clazz
	* @param message - the message
	* */
	public static void systemInfo(String clazz, String message) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					log("INFO", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).info(message);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemInfo operation
	* @param clazz - the clazz
	* @param message - the message
	* */
	public static void systemInfo(Class<?> clazz, String message) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					log("INFO", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).info(message);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemInfo operation
	* @param clazz - the clazz
	* @param message - the message
	* @param exception - the exception
	* */
	public static void systemInfo(String clazz, String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					map.put("exception", exception);
					log("INFO", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).info(message, exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemInfo operation
	* @param clazz - the clazz
	* @param message - the message
	* @param exception - the exception
	* */
	public static void systemInfo(Class<?> clazz, String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					map.put("exception", exception);
					log("INFO", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).info(message, exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemInfo operation
	* @param clazz - the clazz
	* @param exception - the exception
	* */
	public static void systemInfo(String clazz, Exception exception) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", exception.getMessage());
					map.put("exception", exception);
					log("INFO", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).info(exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemInfo operation
	* @param clazz - the clazz
	* @param exception - the exception
	* */
	public static void systemInfo(Class<?> clazz, Exception exception) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", exception.getMessage());
					map.put("exception", exception);
					log("INFO", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).info(exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void systemInfo(String clazz, String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("INFO", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).info(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the systemInfo operation
	* @param clazz - the clazz
	* @param message - the message
	* @param obj - the obj
	* */
	public static void systemInfo(String clazz, String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				Object[] params = new Object[] { obj + "" };
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("INFO", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).info(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void systemInfo(Class<?> clazz, String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("INFO", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).info(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the systemInfo operation
	* @param clazz - the clazz
	* @param message - the message
	* @param obj - the obj
	* */
	public static void systemInfo(Class<?> clazz, String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_INFO) {
				Object[] params = new Object[] { obj + "" };
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("INFO", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).info(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemDebug operation
	* @param clazz - the clazz
	* @param message - the message
	* */
	public static void systemDebug(String clazz, String message) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					log("DEBUG", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).debug(message);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemDebug operation
	* @param clazz - the clazz
	* @param message - the message
	* */
	public static void systemDebug(Class<?> clazz, String message) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					log("DEBUG", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).debug(message);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemDebug operation
	* @param clazz - the clazz
	* @param message - the message
	* @param exception - the exception
	* */
	public static void systemDebug(String clazz, String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					map.put("exception", exception);
					log("DEBUG", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).debug(exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemDebug operation
	* @param clazz - the clazz
	* @param message - the message
	* @param exception - the exception
	* */
	public static void systemDebug(Class<?> clazz, String message, Exception exception) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", message);
					map.put("exception", exception);
					log("DEBUG", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).debug(exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemDebug operation
	* @param clazz - the clazz
	* @param exception - the exception
	* */
	public static void systemDebug(String clazz, Exception exception) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", exception.getMessage());
					map.put("exception", exception);
					log("DEBUG", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).debug(exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the systemDebug operation
	* @param clazz - the clazz
	* @param exception - the exception
	* */
	public static void systemDebug(Class<?> clazz, Exception exception) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", exception.getMessage());
					map.put("exception", exception);
					log("DEBUG", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).debug(exception);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void systemDebug(String clazz, String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("DEBUG", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).debug(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the systemDebug operation
	* @param clazz - the clazz
	* @param message - the message
	* @param obj - the obj
	* */
	public static void systemDebug(String clazz, String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				Object[] params = new Object[] { obj + "" };
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("DEBUG", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).debug(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void systemDebug(Class<?> clazz, String message, Object... params) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("DEBUG", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).debug(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Wrong msg format: " + message);
		}
	}

	/**
	* Performs the systemDebug operation
	* @param clazz - the clazz
	* @param message - the message
	* @param obj - the obj
	* */
	public static void systemDebug(Class<?> clazz, String message, Object obj) {
		try {
			if (LEVEL <= LEVEL_DEBUG) {
				Object[] params = new Object[] { obj + "" };
				if (LogRecord.isSystemOut() || !LoggerFactory.isLoadedConfig()) {
					String msg = (message + "").replaceAll("\\{\\}", "%s");
					String _message = stringFormat(msg, converParams(params));
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("message", _message);
					log("DEBUG", clazz, map);
				}
				if (LoggerFactory.isLoadedConfig()) {
					LoggerFactory.getLogger(clazz).debug(message, params);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Performs the log operation
	* @param level - the level
	* @param clazzName - the clazzName
	* @param String - the String
	* @param map - the map
	* */
	private static void log(String level, String clazzName, Map<String, Object> map) {
		map.put("level", level);

		StackTraceElement[] threadStacks = Thread.currentThread().getStackTrace();
		for (StackTraceElement stack : threadStacks) {
			String className = stack.getClassName();
			if (className.equals(clazzName)) {
				String fileName = stack.getFileName();
				String methodName = stack.getMethodName();
				int lineNumber = stack.getLineNumber();
				map.put("log_file_name", fileName);
				map.put("log_class_name", className);
				map.put("log_method_name", methodName);
				map.put("log_line_number", lineNumber);
				break;
			}
		}

		Thread currentThread = Thread.currentThread();
		long threadId = currentThread.threadId();
		String threadName = currentThread.getName();

		Date logTime = new Date();
		map.put("log_time", logTime);
		map.put("log_time_format", LogRecord.getLogTimeFormat().format(logTime));
		map.put("log_thread_id", threadId);
		map.put("log_thread_name", threadName);
		map.put("hostname", CommonTools.getHostname());

		Exception exception = (Exception) map.get("exception");
		if (exception != null) {
			StringWriter sw = new StringWriter();
			exception.printStackTrace(new PrintWriter(sw));
			String exceptionAsString = sw.toString();
			String exceptionClassName = exception.getClass().getName();
			String exceptionMessage = exception.getMessage();
			StackTraceElement[] stacks = exception.getStackTrace();
			String fileName = null;
			String className = null;
			String methodName = null;
			int lineNumber = 0;
			for (StackTraceElement stack : stacks) {
				fileName = stack.getFileName();
				className = stack.getClassName();
				methodName = stack.getMethodName();
				lineNumber = stack.getLineNumber();
				break;
			}
			map.put("stack_trace", sw.toString());
			map.put("exception_class_name", exceptionClassName);
			map.put("exception_message", exceptionMessage);
			map.put("issued_file_name", fileName);
			map.put("issued_class_name", className);
			map.put("issued_method_name", methodName);
			map.put("issued_line_number", lineNumber);
		}

		if (!map.containsKey("stack_trace"))
			map.put("stack_trace", "");

		if (!map.containsKey("stack_trace_single"))
			map.put("stack_trace_single", "");

		if (!map.containsKey("log_file_name"))
			map.put("log_file_name", "Unknown");

		if (!map.containsKey("log_class_name"))
			map.put("log_class_name", clazzName);

		if (!map.containsKey("log_method_name"))
			map.put("log_method_name", "Unknown");

		if (!map.containsKey("log_line_number"))
			map.put("log_line_number", 0);

		map.remove("exception");

		String messageFormat = DEFAULT_MESSAGE_FORMAT;
		if (LoggerFactory.getConfigProperties() != null) {
			messageFormat = LoggerFactory.getConfigProperties().getString("MessageFormat", DEFAULT_MESSAGE_FORMAT);
		}
		String msg = LogRecord.format(messageFormat, map);
		System.out.println(msg);
	}

	/**
	* Performs the log operation
	* @param level - the level
	* @param clazz - the clazz
	* @param String - the String
	* @param map - the map
	* */
	private static void log(String level, Class<?> clazz, Map<String, Object> map) {
		log(level, clazz.getName(), map);
	}

	/**
	* Performs the log operation
	* @param _level - the _level
	* @param map - the map
	* */
	protected void log(Integer _level, Map<String, Object> map) {
		String levelStr = null;
		String log4jMethodName = null;
		switch (_level) {
		case 0:
			levelStr = "DEBUG";
			break;
		case 1:
			levelStr = "INFO";
			break;
		case 2:
			levelStr = "WARN";
			break;
		case 3:
			levelStr = "ERROR";
			break;
		case 4:
			levelStr = "MARK";
			break;
		default:
			break;
		}
		map.put("level", levelStr);

		StackTraceElement[] threadStacks = Thread.currentThread().getStackTrace();
		for (StackTraceElement stack : threadStacks) {
			String className = stack.getClassName();
			if (className.equals(clazzName)) {
				String fileName = stack.getFileName();
				String methodName = stack.getMethodName();
				int lineNumber = stack.getLineNumber();
				map.put("log_file_name", fileName);
				map.put("log_class_name", className);
				map.put("log_method_name", methodName);
				map.put("log_line_number", lineNumber);
				break;
			}
		}

		Thread currentThread = Thread.currentThread();
		long threadId = currentThread.threadId();
		String threadName = currentThread.getName();

		Date logTime = new Date();
		map.put("log_time", logTime);
		map.put("log_time_format", LogRecord.getLogTimeFormat().format(logTime));
		map.put("log_thread_id", threadId);
		map.put("log_thread_name", threadName);

		Exception exception = (Exception) map.get("exception");
		if (exception != null) {
			StringWriter sw = new StringWriter();
			exception.printStackTrace(new PrintWriter(sw));
			String exceptionAsString = sw.toString();
			String exceptionClassName = exception.getClass().getName();
			String exceptionMessage = exception.getMessage();
			StackTraceElement[] stacks = exception.getStackTrace();
			String fileName = null;
			String className = null;
			String methodName = null;
			int lineNumber = 0;
			for (StackTraceElement stack : stacks) {
				fileName = stack.getFileName();
				className = stack.getClassName();
				methodName = stack.getMethodName();
				lineNumber = stack.getLineNumber();
				break;
			}
			map.put("stack_trace", sw.toString());
			map.put("stack_trace_single", toSingle(sw.toString()));
			map.put("exception_class_name", exceptionClassName);
			map.put("exception_message", exceptionMessage);
			map.put("exception_message_single", toSingle(exceptionMessage));
			map.put("issued_file_name", fileName);
			map.put("issued_class_name", className);
			map.put("issued_method_name", methodName);
			map.put("issued_line_number", lineNumber);
		}

		if (map.containsKey("message")) {
			map.put("message_single", toSingle((String) map.get("message")));
		}

		if (!map.containsKey("stack_trace"))
			map.put("stack_trace", "");

		if (!map.containsKey("stack_trace_single"))
			map.put("stack_trace_single", "");

		if (!map.containsKey("log_file_name"))
			map.put("log_file_name", "Unknown");

		if (!map.containsKey("log_class_name"))
			map.put("log_class_name", clazzName);

		if (!map.containsKey("log_method_name"))
			map.put("log_method_name", "Unknown");

		if (!map.containsKey("log_line_number"))
			map.put("log_line_number", 0);

		map.put("id", CommonTools.generateId(16));
		map.put("created_at", CommonTools.generateId(16));
		map.put("hostname", hostname);

		map.remove("exception");

		String logClassName = String.format("%s.%s", map.get("log_class_name"), map.get("log_method_name"));
		boolean passEnableRegex = checkClassNameEnableRegex(logClassName);
		boolean passDisableRegex = !checkClassNameDisableRegex(logClassName);

		if (passEnableRegex && passDisableRegex) {
			LogRecord.add(map);
		}
	}

	/**
	* Performs the checkClassNameEnableRegex operation
	* @param logClassName - the logClassName
	* @return the checkClassNameEnableRegex result
	* */
	private boolean checkClassNameEnableRegex(String logClassName) {
		if (!ConfigProperties.isBlank(classNameEnableRegex)) {
			return logClassName.matches(classNameEnableRegex);
		}
		return true;
	}

	/**
	* Performs the checkClassNameDisableRegex operation
	* @param logClassName - the logClassName
	* @return the checkClassNameDisableRegex result
	* */
	private boolean checkClassNameDisableRegex(String logClassName) {
		if (!ConfigProperties.isBlank(classNameDisableRegex)) {
			return logClassName.matches(classNameDisableRegex);
		}
		return false;
	}

	/**
	* Performs the getClazzName operation
	* @return the getClazzName result
	* */
	public String getClazzName() {
		return clazzName;
	}

	/**
	* Performs the toSingle operation
	* @param text - the text
	* @return the toSingle result
	* */
	private String toSingle(String text) {
		return CodeEscape.escapeToSingleLineForCsv(text);
	}

	private static String[] converParams(Object... params) {
		if (params == null)
			return new String[] { "null" };

		String[] stringParams = new String[params.length];
		for (int i = 0; i < params.length; i++) {
			stringParams[i] = params[i] + "";
		}
		return stringParams;
	}

	/**
	* Performs the stringFormat operation
	* @param msg - the msg
	* @param params - the params
	* @return the stringFormat result
	* */
	private static String stringFormat(String msg,String[] params){
	    String newMsg = msg + "";
	    List<String> newParams = new ArrayList<String>();
	    newParams.addAll(Arrays.asList(params));
	    int msgParamsSize = (msg.length() - msg.replaceAll("%s","").length())*2;
	    int paramsSize = params.length;
	    if(msgParamsSize > paramsSize){
	        int addSize = msgParamsSize - paramsSize;
	        for(int i = 0;i < addSize;i++){
	            newParams.add("");
	        }
	    }
	    if(msgParamsSize < paramsSize){
	        int addSize = paramsSize - msgParamsSize;
	        for(int i = 0;i < addSize;i++){
	            newMsg += " %s ";
	        }
	    }
	    return String.format(newMsg,newParams.toArray());
	}

}
