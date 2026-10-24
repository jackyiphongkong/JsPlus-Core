package com.killcoding.datasource;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.Date;
import java.sql.Timestamp;
import java.io.File;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;
import com.killcoding.datasource.DriverDataSource;
import java.sql.Time;
import com.killcoding.datasource.CacheDriverExecutor;
import java.sql.Connection;
import java.sql.SQLException;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import java.util.Map;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;

public final class Clock {

	private Calendar calendar = null;

	public static File APP_CLOCK = null;
	private static boolean enable = false;
	private static ConfigProperties configProperties = null;
	private static DriverDataSource dataSource = null;
	private static String dataSourcePath = null;
	private Long gap = 0L;

	private Logger log = LoggerFactory.getLogger(Clock.class);

	public Clock() {
		super();
		init();
	}

	private synchronized void init() {
		if (dataSource == null) {
			try {
				if (APP_CLOCK == null) {
					APP_CLOCK = CommonTools.getSystemProperties(Clock.class, "APP_CLOCK", "Clock.properties");
				}
				if (APP_CLOCK != null && APP_CLOCK.exists() && configProperties == null) {
					configProperties = new ConfigProperties();
					Runnable updatedRun = new Runnable(){
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

	private void reload() {
		if (configProperties != null) {
			enable = configProperties.getBoolean("Enable", false);
			String reloadDataSourcePath = configProperties.getString("DataSource");
			
			boolean isReload = false;
			if(!CommonTools.isBlank(reloadDataSourcePath)){
			    isReload = !dataSourcePath.equals(reloadDataSourcePath);
			}
			if(dataSource != null && isReload){
			    // May be referenced when expansion is needed
			    // dataSource.closeAll();
			    dataSourcePath = reloadDataSourcePath;
			}
			// May be referenced when expansion is needed
			// if(!enable && dataSource != null){
			//    dataSource.closeAll();
			// }
			if (enable && isReload) {
				if (!CommonTools.isBlank(dataSourcePath)) {
					dataSource = new DriverDataSource(new File(dataSourcePath));
				}
			}
		}
	}

	private Calendar getRemoteCalendar() {
		if (enable && dataSource != null) {
			Connection conn = null;
			CacheDriverExecutor executor = null;
			try {
				String getAppClockSql = configProperties.getString("GetAppClockSql");

				if (CommonTools.isBlank(getAppClockSql))
					getAppClockSql = dataSource.getConfigProperties().getString("GetAppClockSql");

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
				if (appSystemDateTime == null && appSystemDateObj instanceof Long) {
					appSystemDateTime = (Long) appSystemDateObj;
				}
				if (appSystemDateTime == null)
					return null;

                gap = Calendar.getInstance().getTimeInMillis() - appSystemDateTime;
                
				String timeZone = CommonTools.isBlank(appTimeZoneObj)
						? configProperties.getString("DefaultTimeZone", "GMT") : (String) appTimeZoneObj;

				Calendar remoteCalendar = Calendar.getInstance();
				TimeZone calTimeZone = TimeZone.getTimeZone(timeZone);
				remoteCalendar.setTimeZone(calTimeZone);
				remoteCalendar.setTimeInMillis(appSystemDateTime);
				return remoteCalendar;
			} catch (Exception e) {
				log.error(e.getMessage(), e);
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
	
	private Calendar getDefaultCalendear(){
		String timeZone = configProperties.getString("DefaultTimeZone", "GMT");
        long msGap = Calendar.getInstance().getTimeInMillis() + gap;
		Calendar defaultCalendar = Calendar.getInstance();
		TimeZone calTimeZone = TimeZone.getTimeZone(timeZone);
		defaultCalendar.setTimeZone(calTimeZone);
		defaultCalendar.setTimeInMillis(msGap);
		return defaultCalendar;
	}

	public java.util.Date getDate() {
		return new java.util.Date(getCalendar().getTimeInMillis());
	}

	public long getTime() {
		return getCalendar().getTimeInMillis();
	}

	public java.sql.Date getSqlDate() {
		return new java.sql.Date(getTime());
	}

	public java.sql.Time getSqlTime() {
		return new java.sql.Time(getTime());
	}

	public Timestamp getSqlTimestamp() {
		return new Timestamp(getTime());
	}

	public LocalDate getLocalDate() {
		ZoneId zoneId = getCalendar().getTimeZone().toZoneId();
		return Instant.ofEpochMilli(getTime()).atZone(zoneId).toLocalDate();
	}

	public LocalDateTime getLocalDateTime() {
		ZoneId zoneId = getCalendar().getTimeZone().toZoneId();
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(getTime()), zoneId);
	}
	
	public static DriverDataSource getDataSource(){
	    return dataSource;
	}
	
	public static ConfigProperties getConfigProperties(){
	    return configProperties;
	}

}