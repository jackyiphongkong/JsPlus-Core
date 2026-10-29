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

package com.killcoding.datasource;

import java.util.Calendar;
import java.util.TimeZone;
import java.sql.Timestamp;
import java.io.File;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;

import java.sql.Connection;
import java.sql.SQLException;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import java.util.Map;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import com.killcoding.cache.Cache;

/**
* System clock facade for JsPlus: returns the current time in several representations
* (java.util.Date, millis, SQL Timestamp, LocalDate, Calendar) from the local JVM, and
* exposes an AppClock static accessor that tracks the DB server time when Clock.properties
* is configured (GetAppClockSql). All accessors are consistent within one snapshot.
* */
public final class Clock {

	private Calendar calendar = null;

	public static volatile File APP_CLOCK = null;
	private static volatile boolean enable = false;
	private static volatile ConfigProperties configProperties = null;
	private static volatile DriverDataSource dataSource = null;
	private static volatile String dataSourcePath = null;
	private Long gap = 0L;

	/**
	* Cache key for the remote-clock gap (localMillis - remoteMillis).
	* The gap is stored via Cache.set with a life of GapCachePeriod, so an
	* expired entry falls through and re-fetches the DB server time once.
	* */
	private static final String GAP_CACHE_KEY = Clock.class.getName() + "-Clock.gap";

	private static final String GAP_CACHE_KEY_HOLD = Clock.class.getName() + "-Clock.hold.gap";

	/**
	* Last time zone returned by the remote clock SQL (or DefaultTimeZone).
	* Kept alongside the cached gap so cache hits need no config lookup.
	* */
	private static volatile String cachedTimeZone = null;

	private Logger log = LoggerFactory.getLogger(Clock.class);

	/**
	* Constructs a new Clock
	* */
	public Clock() {
		super();
		init();
	}

	/**
	* Performs the init operation
	* */
	private synchronized void init() {
		if (dataSource == null) {
			try {
				if (APP_CLOCK == null) {
					APP_CLOCK = CommonTools.getSystemProperties(Clock.class, "APP_CLOCK", "Clock.properties");
				}
				if (APP_CLOCK != null && APP_CLOCK.exists() && configProperties == null) {
					configProperties = new ConfigProperties();
					Runnable updatedRun = new Runnable(){
        		        /*
        		        * Performs the run operation
        		        * */
        		        @Override
        		        public void run(){
        	            	try {
        						reload();
        						Logger.systemMark(Clock.class, "Clock Reloaded");
        					} catch (Exception e) {
        						log.error(e.getMessage(), e);
        					}
        		        }
        		    };
					configProperties.load(APP_CLOCK,updatedRun);
					enable = configProperties.getBoolean("Enable", false);
					if (enable) {
						dataSourcePath = configProperties.getString("DataSource");
						if (!CommonTools.isBlank(dataSourcePath)) {
							dataSource = new DriverDataSource(new File(dataSourcePath));
						}
					}
				}
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	* Performs the reload operation
	* */
	private void reload() {
		if (configProperties != null) {
			enable = configProperties.getBoolean("Enable", false);
			String reloadDataSourcePath = configProperties.getString("DataSource");

			boolean isReload = false;
			if(!CommonTools.isBlank(reloadDataSourcePath)){
			    isReload = !dataSourcePath.equals(reloadDataSourcePath);
			}
			if(dataSource != null && isReload){
			    dataSourcePath = reloadDataSourcePath;
			}
			if (enable && isReload) {
				if (!CommonTools.isBlank(dataSourcePath)) {
					dataSource = new DriverDataSource(new File(dataSourcePath));
				}
			}
		}
	}

	/**
	* Performs the getRemoteCalendar operation
	* @return the getRemoteCalendar result
	* */
	private synchronized Calendar getRemoteCalendar() {
		if (enable && dataSource != null) {
			// Serve from the gap cache when present (Cache life = GapCachePeriod,
			// so an expired entry falls through and re-fetches the DB once).
			Object cachedGap = Cache.get(GAP_CACHE_KEY);
			if (cachedGap instanceof Long) {
				return buildRemoteCalendar(System.currentTimeMillis() - ((Long) cachedGap).longValue(), cachedTimeZone);
			}
			Connection conn = null;
			CacheDriverExecutor executor = null;
			try {
				String getAppClockSql = configProperties.getString("GetAppClockSql");
				if (CommonTools.isBlank(getAppClockSql))
					getAppClockSql = configProperties.getString("GetAppClockSql");

				if (CommonTools.isBlank(getAppClockSql))
					return null;

				conn = dataSource.getConnection();

				if (conn == null)
					return null;

				executor = new CacheDriverExecutor(conn);
				Map<String, Object> result = executor.first(getAppClockSql);

				if (result == null)
					return null;

				Object appSystemDateObj = result.get("app_systime_date");
				Object appTimeZoneObj = result.get("app_time_zone");
				Long appSystemDateTime = null;

				if (appSystemDateObj instanceof Timestamp) {
					appSystemDateTime = ((Timestamp) appSystemDateObj).getTime();
				}
				if (appSystemDateTime == null && appSystemDateObj instanceof java.sql.Date) {
					appSystemDateTime = ((java.sql.Date) appSystemDateObj).getTime();
				}
				if (appSystemDateTime == null && appSystemDateObj instanceof java.util.Date) {
					appSystemDateTime = ((java.util.Date) appSystemDateObj).getTime();
				}
				if (appSystemDateTime == null && appSystemDateObj instanceof OffsetDateTime) {
					appSystemDateTime = ((OffsetDateTime) appSystemDateObj).toInstant().toEpochMilli();
				}	
				if (appSystemDateTime == null && appSystemDateObj instanceof Long) {
					appSystemDateTime = (Long) appSystemDateObj;
				}							
				if (appSystemDateTime == null && appSystemDateObj != null){
					Logger.systemError(Clock.class,"No support class type - '{}'.",appSystemDateObj.getClass());
					return null;
				}

                gap = Calendar.getInstance().getTimeInMillis() - appSystemDateTime;

				String timeZone = CommonTools.isBlank(appTimeZoneObj)
						? configProperties.getString("DefaultTimeZone", "GMT") : (String) appTimeZoneObj;

				Cache.set(GAP_CACHE_KEY, Long.valueOf(gap), gapCacheLifeSeconds());
				Cache.set(GAP_CACHE_KEY_HOLD, Long.valueOf(gap));
				cachedTimeZone = timeZone;

				Calendar remoteCalendar = Calendar.getInstance();
				TimeZone calTimeZone = TimeZone.getTimeZone(timeZone);
				remoteCalendar.setTimeZone(calTimeZone);
				remoteCalendar.setTimeInMillis(appSystemDateTime);
				return remoteCalendar;
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				Cache.set(GAP_CACHE_KEY, Cache.get(GAP_CACHE_KEY_HOLD), gapCacheLifeSeconds());	
				return null;
			} finally {
				try {
					if (executor != null)
						executor.close();
				} catch (SQLException e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		return null;
	}

	/**
	* Performs the getCalendar operation
	* @return the getCalendar result
	* */
	public Calendar getCalendar() {

		if (calendar != null)
			return calendar;

		calendar = getRemoteCalendar();
		if (calendar == null && !enable) {
			calendar = Calendar.getInstance();
		}
		if(calendar == null && enable){
		    calendar = getDefaultCalendear();
		}
		return calendar;
	}

	/**
	* Performs the getDefaultCalendear operation
	* @return the getDefaultCalendear result
	* */
	private Calendar getDefaultCalendear(){
		String timeZone = cachedTimeZone != null ? cachedTimeZone : configProperties.getString("DefaultTimeZone", "GMT");
		Object cachedGap = Cache.get(GAP_CACHE_KEY);
		long gapMs = (cachedGap instanceof Long) ? ((Long) cachedGap).longValue() : gap;
		return buildRemoteCalendar(System.currentTimeMillis() - gapMs, timeZone);
	}

	/**
	* Builds a Calendar for the given epoch millis and zone label.
	* @param timeMs - the epoch millis to display
	* @param timeZone - the zone label (e.g. "GMT+8"), may be null/blank
	* @return the Calendar instance
	* */
	private static Calendar buildRemoteCalendar(long timeMs, String timeZone) {
		Calendar remoteCalendar = Calendar.getInstance();
		if (!CommonTools.isBlank(timeZone)) {
			remoteCalendar.setTimeZone(TimeZone.getTimeZone(timeZone));
		}
		remoteCalendar.setTimeInMillis(timeMs);
		return remoteCalendar;
	}

	/**
	* Returns the gap-cache life in whole seconds derived from GapCachePeriod
	* (supports duration formats such as 60s, 5s, 1000ms, 1m, 100h).
	* @return the cache life in seconds, at least 1
	* */
	private static int gapCacheLifeSeconds() {
		long periodMs = (configProperties != null) ? configProperties.getMilliSeconds("GapCachePeriod", 60000L) : 60000L;
		return (int) Math.max(1L, periodMs / 1000L);
	}

	public java.util.Date getDate() {
		return new java.util.Date(getCalendar().getTimeInMillis());
	}

	/**
	* Performs the getTime operation
	* @return the getTime result
	* */
	public long getTime() {
		return getCalendar().getTimeInMillis();
	}

	public java.sql.Date getSqlDate() {
		return new java.sql.Date(getTime());
	}

	public java.sql.Time getSqlTime() {
		return new java.sql.Time(getTime());
	}

	/**
	* Performs the getSqlTimestamp operation
	* @return the getSqlTimestamp result
	* */
	public Timestamp getSqlTimestamp() {
		return new Timestamp(getTime());
	}

	/**
	* Performs the getLocalDate operation
	* @return the getLocalDate result
	* */
	public LocalDate getLocalDate() {
		ZoneId zoneId = getCalendar().getTimeZone().toZoneId();
		return Instant.ofEpochMilli(getTime()).atZone(zoneId).toLocalDate();
	}

	/**
	* Performs the getLocalDateTime operation
	* @return the getLocalDateTime result
	* */
	public LocalDateTime getLocalDateTime() {
		ZoneId zoneId = getCalendar().getTimeZone().toZoneId();
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(getTime()), zoneId);
	}

	/**
	* Performs the getDataSource operation
	* @return the getDataSource result
	* */
	public static DriverDataSource getDataSource(){
	    return dataSource;
	}

	/**
	* Performs the getConfigProperties operation
	* @return the getConfigProperties result
	* */
	public static ConfigProperties getConfigProperties(){
	    return configProperties;
	}

}
