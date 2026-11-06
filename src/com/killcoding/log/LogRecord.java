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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.killcoding.datasource.DriverDataSource;
import com.killcoding.tool.ConfigProperties;

/**
* Legacy facade over the default {@link LoggerContext}.
*
* <p>The static API is kept for backward compatibility (host applications and
* existing call sites use it unchanged); every call is delegated to the
* default bucket, which is the bucket registered first through
* {@link LoggerFactory#register(java.io.File)} (normally the APP_LOGGER file).
* New code should obtain a bucket-aware logger via
* {@link LoggerFactory#getLogger(String, Class)}.</p>
*
* <p><b>Known issues / design constraints:</b></p>
* <ol>
* <li><b>Clock recursion ring (StackOverflowError).</b> The write path must not
*     call {@link com.killcoding.datasource.Clock}; raw system time is used
*     instead. See {@link Logger} for the full explanation.</li>
* <li><b>Timezone skew.</b> File names and DB timestamps follow the JVM default
*     timezone, not Clock's DefaultTimeZone.</li>
* </ol>
* */
public final class LogRecord {

	protected final static String DEFAULT_MESSAGE_FORMAT = ":level :log_time_format [:log_thread_name-:log_thread_id] :log_class_name.:log_method_name(:log_file_name::log_line_number) **:message** \r\n:stack_trace";

	/**
	* Returns the default bucket, or null before {@link LoggerFactory} finished
	* its static initialisation
	* @return the default context
	* */
	private static LoggerContext ctx() {
		return LoggerFactory.getDefaultContext();
	}

	/**
	* Returns the log-time format of the default bucket
	* @return SimpleDateFormat
	* */
	protected static SimpleDateFormat getLogTimeFormat() {
		LoggerContext context = ctx();
		if (context == null) {
			return new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS");
		}
		return context.getLogTimeFormat();
	}

	/**
	* Returns the log-date format of the default bucket
	* @return SimpleDateFormat
	* */
	protected static SimpleDateFormat getLogDateFormat() {
		LoggerContext context = ctx();
		if (context == null) {
			return new SimpleDateFormat("yyyyMMdd");
		}
		return context.getLogDateFormat();
	}

	/**
	* Set the default-bucket configuration
	* @param _config - the config
	* */
	protected static void setConfig(ConfigProperties _config) {
		LoggerContext context = ctx();
		if (context == null) {
			return;
		}
		context.setConfig(_config);
	}

	/**
	* Is the default bucket's flusher started
	* @return boolean
	* */
	protected static boolean isStarted() {
		LoggerContext context = ctx();
		return context != null && context.isStarted();
	}

	/**
	* Returns the current records of the default bucket (drains the queue)
	* @return the list
	* */
	@SuppressWarnings("unchecked")
	protected static synchronized List<Map<String, Object>> list() {
		LoggerContext context = ctx();
		if (context == null) {
			return new ArrayList<Map<String, Object>>();
		}
		return context.list();
	}

	/**
	* Get the queue size of the default bucket
	* @return int
	* */
	protected static synchronized int size() {
		LoggerContext context = ctx();
		return context == null ? 0 : context.size();
	}

	/**
	* Add a record to the default bucket
	* @param o - the record
	* */
	protected static synchronized void add(Map<String, Object> o) {
		LoggerContext context = ctx();
		if (context == null) {
			return;
		}
		context.add(o);
	}

	/**
	* Remove a record from the default bucket
	* @param o - the record
	* */
	protected static synchronized void remove(Map<String, Object> o) {
		LoggerContext context = ctx();
		if (context == null) {
			return;
		}
		context.remove(o);
	}

	/**
	* Returns the current log file name of the default bucket
	* @return the name
	* */
	public static String getLogFileName() {
		LoggerContext context = ctx();
		if (context == null) {
			return null;
		}
		return context.getLogFileName();
	}

	/**
	* Force-restore the JVM-wide stdout/stderr streams (default bucket only)
	* */
	public static synchronized void restoreStdStreams() {
		LoggerContext context = ctx();
		if (context == null) {
			return;
		}
		context.restoreStdStreams();
	}

	/**
	* Is the standard output not captured (default bucket only)
	* @return boolean
	* */
	protected static boolean isSystemOut() {
		LoggerContext context = ctx();
		return context == null || context.isSystemOut();
	}

	/**
	* Returns the database-output datasource of the default bucket
	* @return DataSource
	* */
	public static DataSource getDataSource() {
		LoggerContext context = ctx();
		return context == null ? null : context.getDataSource();
	}

	/**
	* Query one row from the default bucket's database
	* @param sql - the sql
	* @param params - the params
	* @return Map
	* @throws Exception
	* */
	public static Map<String, Object> first(String sql, Map<String, Object> params) throws Exception {
		LoggerContext context = ctx();
		if (context == null) {
			return null;
		}
		return context.first(sql, params);
	}

	/**
	* Performs the format operation
	* @param messageFormat - the messageFormat
	* @param row - the row
	* @return the format result
	* */
	public static String format(String messageFormat, Map<String, Object> row) {
		List<Object> params = new ArrayList<Object>();
		Pattern pattern = Pattern.compile(":[a-z0-9_]+");
		Matcher matcher = pattern.matcher(messageFormat);
		while (matcher.find()) {
			String key = matcher.group().replaceFirst(":", "");
			params.add(row.get(key));
		}
		String mf = messageFormat.replaceAll(":[a-z0-9_]+", "%s");
		String msg = String.format(mf, params.toArray()).trim();
		return msg;
	}

	/**
	* Returns the driver datasource of the default bucket (unused placeholder kept
	* for API stability)
	* @return the datasource
	* */
	protected static DriverDataSource getDriverDataSource() {
		LoggerContext context = ctx();
		return context == null ? null : context.getDataSource();
	}

}
