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
package com.killcoding.jsplus;

import java.util.Arrays;
import java.util.List;
import com.killcoding.tool.CommonTools;
import java.text.SimpleDateFormat;
import java.io.File;
import com.killcoding.tool.FileTools;
import java.util.Map;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.Method;
import com.killcoding.tool.ResultMap;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.Scanner;
import com.killcoding.cache.CacheArray;
import com.killcoding.cache.CacheArrayFilter;
import com.killcoding.cache.DataObject;
import com.killcoding.tool.CipherTools;
import com.killcoding.tool.Base64Tools;
import com.killcoding.file.DiskFile;
import com.killcoding.file.BaseFile;
import java.util.Date;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalTime;

/**
* ScriptConsoleJdbcBase - the JDBC console layer shared by ScriptConsole and JdbcConsole.
*
* Owns the SQL command executor (executeViaJdbc / executeJdbcBatch), the result-rendering
* CacheArrayFilter used by SELECT, the metadata / DDL helpers (desc / showTables /
* showColumnTypes / showColumnClasses / genCreateTables / genCreateTable / getDataType),
* the value converter (conver / converToSqlFromMap) used by CSV import, the whole
* import / export pipeline (importFrom / importTables / exportTables / exportTo /
* getCacheArrayFilterForExport) and the 11 JDBC-side Console functions (List<String>
* overloads) that executeViaJdbc dispatches to by reflection.
* */
public class ScriptConsoleJdbcBase extends ScriptConsoleBase {

	public List<Map<String, Object>> desc(String schema, String tableName) throws Exception {
		String schemaTableName = null;
		if (CommonTools.isBlank(schema)) {
			schemaTableName = tableName;
		} else {
			schemaTableName = String.format("%s.%s", schema, tableName);
		}
		return getCacheDriverExecutor(true).desc(schemaTableName);
	}

	public List<Map<String, Object>> showTables() throws Exception {
		return showTables((String)null);
	}

	public List<Map<String, Object>> showTables(String schema) throws Exception {
		if (schema == null) {
			return getCacheDriverExecutor(true).getAllTables();
		} else {
			return getCacheDriverExecutor(true).getAllTables(schema);
		}
	}

	public Map<String, Object> showColumnTypes(String tableName) throws Exception {
		return getCacheDriverExecutor(true).getColumnTypes(tableName);

	}

	public Map<String, Object> showColumnClasses(String tableName) throws Exception {
		return getCacheDriverExecutor(true).getColumnClasses(tableName);
	}

	/**
	* Performs the genCreateTables operation
	* @param schema - the schema
	* @param sqlFilePath - the sqlFilePath
	* @return the genCreateTables result
	* */
	public String genCreateTables(String schema, String sqlFilePath) {
		try {
			StringBuffer errMsg = null;
			StringBuffer sql = new StringBuffer();
			List<Map<String, Object>> tables = getCacheDriverExecutor(true).getAllTables();
			File sqlFile = new File(sqlFilePath);
			if (tables.size() > 0) {
				if (sqlFile.exists())
					sqlFile.delete();
			}
			for (Map<String, Object> table : tables) {
				try {
					String tableSchema = (String) table.get("TABLE_SCHEMA");
					String tableName = (String) table.get("TABLE_NAME");
					if (CommonTools.isBlank(schema) && CommonTools.isBlank(tableSchema)) {
						boolean append = sqlFile.exists();
						StringBuffer ts = new StringBuffer(genCreateTable(null, tableName));
						ts.append(System.lineSeparator());
						ts.append(System.lineSeparator());
						FileTools.write(sqlFile, ts.toString(), append);
					}
					if (!CommonTools.isBlank(schema) && !CommonTools.isBlank(tableSchema)) {
						if (schema.equalsIgnoreCase(tableSchema)) {
							boolean append = sqlFile.exists();
							StringBuffer ts = new StringBuffer(genCreateTable(tableSchema, tableName));
							ts.append(System.lineSeparator());
							ts.append(System.lineSeparator());
							FileTools.write(sqlFile, ts.toString(), append);
						}
					}
				} catch (Exception ee) {
					log.error(ee);
					if (errMsg == null)
						errMsg = new StringBuffer();

					errMsg.append(ee.getMessage());
					errMsg.append(System.lineSeparator());
				}
			}
			if (errMsg == null)
				return "Successfully";
			else
				return errMsg.toString();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return String.format("ERROR: %s", e.getMessage());
		}
	}

	/**
	* Performs the genCreateTable operation
	* @param schema - the schema
	* @param tableName - the tableName
	* @return the genCreateTable result
	* */
	public String genCreateTable(String schema, String tableName) throws Exception {
		String schemaTableName = null;
		if (CommonTools.isBlank(schema)) {
			schemaTableName = tableName;
		} else {
			schemaTableName = String.format("%s.%s", schema, tableName);
		}
		String commandEnd = consoleVariables.getString("CommandEnd", DEFAULT_CommandEnd);
		StringBuffer sql = new StringBuffer(String.format("CREATE TABLE %s", quote(exportUpperOrLowerCase(tableName))));
		sql.append(System.lineSeparator());
		sql.append("(");
		sql.append(System.lineSeparator());
		List<Map<String, Object>> list = getCacheDriverExecutor(true).desc(schemaTableName);
		int columnSize = list.size();
		List<String> pkeys = new ArrayList<String>();
		for (int i = 0; i < columnSize; i++) {
			Map<String, Object> tableInfo = list.get(i);
			Boolean isPrimaryKey = tableInfo.get("PRIMARY_KEY").equals("Y");

			if (!isPrimaryKey)
				continue;

			String name = (String) tableInfo.get("NAME");
			pkeys.add(quote(exportUpperOrLowerCase(name)));
		}
		String pkeysStr = null;
		if (pkeys.size() > 0) {
			pkeysStr = String.format(",%sPRIMARY KEY (%s)", System.lineSeparator(), String.join(",", pkeys));
		}
		for (int i = 0; i < columnSize; i++) {
			Map<String, Object> tableInfo = list.get(i);
			String name = exportUpperOrLowerCase((String) tableInfo.get("NAME"));
			String javaType = (String) tableInfo.get("JAVA_TYPE");
			String dataType = (String) tableInfo.get("DATA_TYPE");
			Integer precision = (Integer) tableInfo.get("PRECISION");
			Boolean isAllowNullable = tableInfo.get("ALLOW_NULLABLE").equals("Y");
			String allowNullableStr = isAllowNullable ? "" : "NOT NULL";
			boolean isId = name.matches("^.*[\\_]{0,1}id$") && dataType.equals("CHAR") && precision == 16;
			String tableDataType = getDataType(isId, javaType, precision);
			sql.append(String.format("%s %s %s", quote(name), tableDataType, allowNullableStr).trim());
			if (i < columnSize - 1) {
				sql.append(",");
				sql.append(System.lineSeparator());
			}
		}

		if (pkeysStr != null)
			sql.append(pkeysStr);

		sql.append(System.lineSeparator());
		sql.append(")");
		sql.append(commandEnd);
		return sql.toString();
	}

	/**
	* Performs the getDataType operation
	* @param isId - the isId
	* @param javaType - the javaType
	* @param precision - the precision
	* @return the getDataType result
	* */
	public String getDataType(boolean isId, String javaType, int precision) {
		if (isId) {
			return "CHAR(16)";
		}
		if (javaType.equals(java.sql.Timestamp.class.getName())) {
			return String.format("DATETIME(%s)", 6);
		} else if (javaType.equals(java.util.Date.class.getName())) {
			return String.format("DATETIME(%s)", 6);
		} else if (javaType.equals(java.sql.Date.class.getName())) {
			return "DATE";
		} else if (javaType.equals(java.sql.Time.class.getName())) {
			return "TIME";
		} else if (javaType.equals(java.lang.Boolean.class.getName())) {
			return String.format("TINYINT(%s)", 1);
		} else if (javaType.equals(java.lang.Byte.class.getName())) {
			return "INT";
		} else if (javaType.equals(java.lang.Short.class.getName())) {
			return "INT";
		} else if (javaType.equals(java.lang.Integer.class.getName())) {
			return "INT";
		} else if (javaType.equals(java.lang.Long.class.getName())) {
			return "BIGINT";
		} else if (javaType.equals(java.lang.Float.class.getName())) {
			return "FLOAT";
		} else if (javaType.equals(java.lang.Double.class.getName())) {
			return "DOUBLE";
		} else if (javaType.equals(java.math.BigDecimal.class.getName())) {
			return "DOUBLE";
		} else if (javaType.equals(java.math.BigInteger.class.getName())) {
			return "BIGINT";
		} else if (javaType.equals(java.lang.String.class.getName())) {
			if (precision <= 500) {
				return String.format("VARCHAR(%s)", precision);
			} else {
				return "TEXT";
			}
		}
		return "TEXT";
	}

	/**
	* Performs the converToSqlFromMap operation
	* @param schema - the schema
	* @param tableName - the tableName
	* @param columns - the columns
	* @return the converToSqlFromMap result
	* */
	public String converToSqlFromMap(String schema, String tableName, List<String> columns) {
		int columnSize = columns.size();
		StringBuffer sql = new StringBuffer();
		String fullTableName = schema == null ? tableName : String.format("%s.%s", schema, tableName);
		sql.append(String.format("INSERT INTO %s ", quote(fullTableName)));
		sql.append("(");
		for (int i = 0; i < columnSize; i++) {
			String column = columns.get(i);
			sql.append(quote(column));
			if (i < columnSize - 1) {
				sql.append(",");
			}
		}
		sql.append(") VALUES (");
		for (int i = 0; i < columnSize; i++) {
			String column = columns.get(i);
			sql.append(":" + column);
			if (i < columnSize - 1) {
				sql.append(",");
			}
		}
		sql.append(")");
		return sql.toString();
	}

	/**
	* Performs the conver operation
	* @param tableObjectFolder - the tableObjectFolder
	* @param javaType - the javaType
	* @param encryptCell - the encryptCell
	* @return the conver result
	* */
	public Object conver(String tableObjectFolder, String javaType, String encryptCell) throws Exception {
		String _aesKey = consoleVariables.getString("JdbcCipherAesKey");

		Object objectValue = null;

		if (CommonTools.isBlank(encryptCell)) {
			return "";
		}

		boolean useEncrypt = !CommonTools.isBlank(_aesKey);

		String cell = null;

		if (useEncrypt) {
			byte[] by = Base64Tools.decode(encryptCell);
			cell = new String(CipherTools.AESDecrypt(_aesKey, by),BaseFile.CHARSET);
		} else {
			cell = encryptCell;
		}

		if (cell.toUpperCase().equals("NULL")) {
			return null;
		}

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(consoleVariables.getString("DateTimeFormat"));
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(consoleVariables.getString("DateFormat"));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(consoleVariables.getString("TimeFormat"));

        if (javaType.equals(java.lang.String.class.getName())) {
            objectValue = unescapeCsv(cell);
        } else if (javaType.equals(java.util.Date.class.getName())) {
            LocalDateTime ldt = LocalDateTime.parse(cell, dateTimeFormatter);
            objectValue = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        } else if (javaType.equals(java.sql.Date.class.getName())) {
            LocalDate ld = LocalDate.parse(cell, dateFormatter);
            objectValue = java.sql.Date.valueOf(ld);
        } else if (javaType.equals(java.sql.Timestamp.class.getName())) {
            LocalDateTime ldt = LocalDateTime.parse(cell, dateTimeFormatter);
            objectValue = java.sql.Timestamp.valueOf(ldt);
        } else if (javaType.equals(java.sql.Time.class.getName())) {
            LocalTime lt = LocalTime.parse(cell, timeFormatter);
            objectValue = java.sql.Time.valueOf(lt);
        }else if (javaType.equals(java.math.BigDecimal.class.getName())) {
			objectValue = Double.parseDouble(cell);
		} else if (javaType.equals(java.math.BigInteger.class.getName())) {
			objectValue = Long.parseLong(cell);
		} else if (javaType.equals(java.lang.Short.class.getName())) {
			objectValue = Short.parseShort(cell);
		} else if (javaType.equals(java.lang.Integer.class.getName())) {
			objectValue = Integer.parseInt(cell);
		} else if (javaType.equals(java.lang.Long.class.getName())) {
			objectValue = Long.parseLong(cell);
		} else if (javaType.equals(java.lang.Float.class.getName())) {
			objectValue = Float.parseFloat(cell);
		} else if (javaType.equals(java.lang.Double.class.getName())) {
			objectValue = Double.parseDouble(cell);
		} else if (javaType.equals(java.sql.Clob.class.getName())) {
			String objPath = String.format("%s/%s", tableObjectFolder, cell);
			DataObject dob = new DataObject(_aesKey, objPath);
			objectValue = (byte[]) dob.getData();
		} else if (javaType.equals(java.sql.Blob.class.getName())) {
			String objPath = String.format("%s/%s", tableObjectFolder, cell);
			DataObject dob = new DataObject(_aesKey, objPath);
			objectValue = (byte[]) dob.getData();
		} else if (javaType.equals("[B")) {
			String objPath = String.format("%s/%s", tableObjectFolder, cell);
			DataObject dob = new DataObject(_aesKey, objPath);
			objectValue = (byte[]) dob.getData();
		} else {
			//Not support the type.
			objectValue = null;
		}
		return objectValue;
	}

	/**
	* Performs the exportTo operation
	* @param _lastSelectCommand - the _lastSelectCommand
	* @param folder - the folder
	* @param filePrefix - the filePrefix
	* */
	public void exportTo(String _lastSelectCommand,File folder,String filePrefix) throws Exception {
		exportTo(_lastSelectCommand,String.format("%s/%s",folder.getAbsolutePath(),filePrefix));
	}

	/**
	* Performs the exportTo operation
	* @param folder - the folder
	* @param filePrefix - the filePrefix
	* */
	public void exportTo(File folder,String filePrefix) throws Exception {
		exportTo(String.format("%s/%s",folder.getAbsolutePath(),filePrefix));
	}

	/**
	* Performs the exportTo operation
	* @param _lastSelectCommand - the _lastSelectCommand
	* @param file - the file
	* */
	public synchronized void exportTo(String _lastSelectCommand,String file) throws Exception {
	    setLastSelectCommand(_lastSelectCommand);
		Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
		if (CommonTools.isBlank(lastSelectCommand)) {
			throw new Exception("ERROR: Invalid blank select command");
		}
		Integer exportCursorStart = consoleVariables.getInteger("ExportCursorStart", DEFAULT_ExportCursorStart);
		Integer exportMaxRows = consoleVariables.getInteger("ExportMaxRows", DEFAULT_ExportMaxRows);
		CacheArray cacheArray = new CacheArray();
		cacheArray.filter(getCacheArrayFilterForExport(showTaskLog, file));
		getCacheDriverExecutor(true).find(exportCursorStart, exportMaxRows, lastSelectCommand, cacheArray);
	}

	/**
	* Performs the exportTo operation
	* @param file - the file
	* */
	public synchronized void exportTo(String file) throws Exception {
		Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
		if (CommonTools.isBlank(lastSelectCommand)) {
			throw new Exception("ERROR: Invalid blank select command");
		}
		Integer exportCursorStart = consoleVariables.getInteger("ExportCursorStart", DEFAULT_ExportCursorStart);
		Integer exportMaxRows = consoleVariables.getInteger("ExportMaxRows", DEFAULT_ExportMaxRows);
		CacheArray cacheArray = new CacheArray();
		cacheArray.filter(getCacheArrayFilterForExport(showTaskLog, file));
		getCacheDriverExecutor(true).find(exportCursorStart, exportMaxRows, lastSelectCommand, cacheArray);
	}

	/**
	* Performs the getCacheArrayFilterForExport operation
	* @param showTaskLog - the showTaskLog
	* @param file - the file
	* @return the getCacheArrayFilterForExport result
	* */
	public CacheArrayFilter getCacheArrayFilterForExport(boolean showTaskLog, String file) {
	    return getCacheArrayFilterForExport(showTaskLog,file,null,null);
	}

	/**
	* Performs the exportTo operation
	* @param _lastSelectCommand - the _lastSelectCommand
	* @param folder - the folder
	* @param filePrefix - the filePrefix
	* @param completedRun - the completedRun
	* @param terminatedRun - the terminatedRun
	* */
	public void exportTo(String _lastSelectCommand,File folder,String filePrefix,Runnable completedRun,Runnable terminatedRun) throws Exception {
	    setLastSelectCommand(_lastSelectCommand);
		Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
		if (CommonTools.isBlank(lastSelectCommand)) {
			throw new Exception("ERROR: Invalid blank select command");
		}
		Integer exportCursorStart = consoleVariables.getInteger("ExportCursorStart", DEFAULT_ExportCursorStart);
		Integer exportMaxRows = consoleVariables.getInteger("ExportMaxRows", DEFAULT_ExportMaxRows);
		CacheArray cacheArray = new CacheArray();
		String file = String.format("%s/%s",folder.getAbsolutePath(),filePrefix);
		cacheArray.filter(getCacheArrayFilterForExport(showTaskLog, file,completedRun,terminatedRun));
		getCacheDriverExecutor(true).find(exportCursorStart, exportMaxRows, lastSelectCommand, cacheArray);
	}

	/**
	* Performs the getCacheArrayFilterForExport operation
	* @param showTaskLog - the showTaskLog
	* @param file - the file
	* @param completedRun - the completedRun
	* @param terminatedRun - the terminatedRun
	* @return the getCacheArrayFilterForExport result
	* */
	public CacheArrayFilter getCacheArrayFilterForExport(boolean showTaskLog, String file,Runnable completedRun,Runnable terminatedRun) {
		Integer exportMaxFileSize = consoleVariables.getInteger("ExportMaxFileSize", DEFAULT_ExportMaxFileSize);
		Long exportTimer = consoleVariables.getLong("ExportTimer", DEFAULT_ExportTimer);
		String exportSplit = consoleVariables.getString("ExportSplit", DEFAULT_ExportSplit);
		final java.util.Date beginTime = new java.util.Date();

		return new CacheArrayFilter(0L,exportTimer) {
			int outIndex = 0;
			int exportedIndex = 0;
			DiskFile csvFile = null;
			File outFile = new File(file);
			String outFolder = outFile.getParent();
			String outFileName = exportUpperOrLowerCase(outFile.getName().replaceFirst("\\..*", ""));
			final List<String> columns = new ArrayList<String>();
			final StringBuffer header = new StringBuffer();

			@Override

			/*
			* Performs the executeBatch operation
			* @param index - the index
			* @param batch - the batch
			* */
			public void executeBatch(Integer index,List<?> batch) {
                if (showTaskLog) {
    				System.out.println(String.format("Exporting table %s (%s -> %s).", outFileName,index,batch.size()));
    			}

			    for(Object o : batch){
    				try {
    					String tableObjectFolder = String.format("%s/%s_objects", outFolder, outFileName);
    					@SuppressWarnings("unchecked")
Map<String, Object> cacheRow = (Map<String, Object>) o;
    					if (csvFile == null) {
    						String outIndexFormat = String.format("%09d", outIndex);
    						csvFile = new DiskFile(String.format("%s/%s.%s.csv", outFolder, outFileName, outIndexFormat));
    						if (csvFile.exists()) {
    							csvFile.delete();
    						}
    						if (showTaskLog) {
    							System.out.println(String.format("Exporting table %s", outFileName));
    						}
    						columns.addAll(cacheRow.keySet());
    						int columsSize = columns.size();
    						for (int i = 0; i < columsSize; i++) {
    							header.append(exportUpperOrLowerCase(columns.get(i)));
    							if (i < columsSize - 1)
    								header.append(exportSplit);
    						}
    						header.append(System.lineSeparator());
    						csvFile.manualOpen(true);
    						csvFile.manualWrite(header.toString());
    					}
    					if (index >= 0) {
    						StringBuffer cells = new StringBuffer();
    						int columsSize = columns.size();
    						for (int j = 0; j < columsSize; j++) {
    							String column = columns.get(j);
    							Object objValue = cacheRow.get(column);
    							String value = formatForExport(tableObjectFolder, objValue);
    							cells.append(value);
    							if (j < columsSize - 1)
    								cells.append(exportSplit);
    						}
    						cells.append(System.lineSeparator());
    						Path path = Paths.get(csvFile.getPath());
    						long sizeInBytes = Files.size(path);
    						long sizeInMb = sizeInBytes / (1024 * 1024);
    						if (sizeInMb >= exportMaxFileSize) {
    							outIndex++;
    							String outIndexFormat = String.format("%09d", outIndex);

    							csvFile.manualClose();

    							csvFile = new DiskFile(String.format("%s/%s.%s.csv", outFolder, outFileName, outIndexFormat));
    							if (csvFile.exists()) {
    								csvFile.delete();
    							}

    							csvFile.manualOpen(true);
    							csvFile.manualWrite(header.toString());
    						}
    						csvFile.manualWrite(cells.toString());
    					}

    				} catch (Exception e) {
    					log.error(e.getMessage(), e);
    				}
			    }
			}

			@Override

			/*
			* Performs the execute operation
			* @param index - the index
			* @param o - the o
			* */
			public void execute(Integer index, Object o) {

			}

			@Override

			/*
			* Performs the completed operation
			* @param size - the size
			* */
			public void completed(Integer size) {
				try {
					String outIndexFormat = String.format("%09d", 0);
				    if(csvFile != null){
				        csvFile.manualClose();
				    }
					if (csvFile == null) {
					    csvFile = new DiskFile(String.format("%s/%s.%s.csv", outFolder, outFileName, outIndexFormat));
						csvFile.write("", false);
					}
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
				if (showTaskLog) {
					long timeCostMs = new java.util.Date().getTime() - beginTime.getTime();
					System.out
							.println(String.format("Table %s %s rows exported (%sms)", outFileName, size, timeCostMs));
				}

				if(completedRun != null) completedRun.run();
			}

			@Override

			/*
			* Performs the terminated operation
			* */
			public void terminated(){
			    if(csvFile != null){
			        try{
				        csvFile.manualClose();
			        }catch(Exception e){
			            log.error(e.getMessage(), e);
			        }
				}

				if(terminatedRun != null) terminatedRun.run();
			}
		};
	}

	/**
	* Performs the executeJdbcBatch operation
	* @param _batchFile - the _batchFile
	* @return the executeJdbcBatch result
	* */
	protected String executeJdbcBatch(String _batchFile) throws Exception {
		try {
		    File batchFile = new File(_batchFile);
			if (!batchFile.exists()) {
				return String.format("ERROR: Batch file not found '%s'", batchFile);
			}
			String commandLines = decryptScript(batchFile);
			if (!CommonTools.isBlank(commandLines)) {
				String commandEnd = consoleVariables.getString("CommandEnd", DEFAULT_CommandEnd);
				List<String> commandList = Arrays.asList(commandLines.split(commandEnd + "[^\\S]*\n"));
				int row = 0;
				for (String cl : commandList) {
					if (!CommonTools.isBlank(cl)) {
						String command = cl.trim();
						if (command.indexOf("-") != 0) {
							System.out.println(
									String.format("%s) %s",row,command));

							executeViaJdbc(true, command);
						}
					}
					row++;
				}
			}
			return null;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return e.getMessage();
		}
	}

	/**
	* Performs the executeViaJdbc operation
	* @param commands - the commands
	* @return the executeViaJdbc result
	* */
	public boolean executeViaJdbc(String commands) {
		return executeViaJdbc(true, commands);
	}

	/**
	* Performs the executeViaJdbc operation
	* @param enableSystemOut - the enableSystemOut
	* @param commands - the commands
	* @return the executeViaJdbc result
	* */
	public boolean executeViaJdbc(Boolean enableSystemOut,String commands) {
	    String commandEnd = consoleVariables.getString("CommandEnd",DEFAULT_CommandEnd);
	    String executeCommand = commands.toString().replaceFirst("(" + commandEnd + "){1,}[\s]*$","").trim();

		if (executeCommand.trim().equals("")) {
			System.out.println("ERROR: Invalid blank command");
			return false;
		}

		boolean isCancel = isCommandCancel(executeCommand);

	    if(isCancel) return true;

        Boolean writeHistory = consoleVariables.getBoolean("WriteHistory",DEFAULT_WriteHistory);
        String commandHistory = consoleVariables.getString("CommandHistory", DEFAULT_CommandHistory);
		try {

		    log.info(executeCommand);

		    if(writeHistory && enableSystemOut && executeCommand.indexOf(commandHistory) != 0){
		        if(!CommonTools.isBlank(executeCommand))
		            addHistory(executeCommand.trim());
		    }

			if (executeCommand.toUpperCase().equals("EXIT")) {
				if(cacheDriverExecutor != null){
                    if(!cacheDriverExecutor.isClosed()){
                        System.out.println("ERROR: The connection is in use, please close the connection first.");
                    }
				}

				if(cacheDriverExecutor == null)
				    exitConsole(0);

				return true;
			}


            if(executeCommand.toUpperCase().equals("SCRIPT")){
		        setUseScriptEnv();
		        consoleVariables.putAndReturnSelf("CommandEnd","/");
		        if(enableSystemOut){
		            System.out.println("Changed CommandEnd to '/'");
		        }
		        set("Name",displayEngineName);
		    } else if(executeCommand.toUpperCase().equals("CMD")){
		        setUseCmdEnv();
		        consoleVariables.putAndReturnSelf("CommandEnd","/");
		        if(enableSystemOut){
		            System.out.println("Changed CommandEnd to '/'");
		        }
		        set("Name","cmd");
		    }else if(executeCommand.toUpperCase().equals("GHOST")){
    	        setUseGhostEnv();
    	        consoleVariables.putAndReturnSelf("CommandEnd","/");
    	        set("Name","ghost");
    	        if(enableSystemOut){
		            System.out.println("Changed CommandEnd to '/'");
    	        }
        	}else if(executeCommand.indexOf(commandHistory) == 0){
			    String hiscmd = executeCommand.replaceFirst("^" + commandHistory,"");
		        showHistory(hiscmd);
		    }else if(executeCommand.toUpperCase().equals("CLEAR")){
		        clear(enableSystemOut);
		    }else if (executeCommand.toUpperCase().equals("COMMIT")) {
                commit(enableSystemOut);
			} else if (executeCommand.toUpperCase().equals("ROLLBACK")) {
				rollback(enableSystemOut);
			} else if (executeCommand.toUpperCase().equals("CLOSE")) {
				close(enableSystemOut);
			} else if (executeCommand.toUpperCase().equals("ABORT")) {
				abort(enableSystemOut);
			}else if (executeCommand.toUpperCase().equals("OPEN")) {
				open(enableSystemOut);
			} else if (executeCommand.matches(CONSOLE_VAR_REGEX)){
			    String[] keyValue = executeCommand.split("=",2);
			    if(keyValue.length == 2){
			        String key = keyValue[0].trim().replaceFirst("^Console\\.","");
			        String value = keyValue[1].trim();
			        consoleVariables.putAndReturnSelf(key,value);
			        if(enableSystemOut) System.out.println(String.format("Successfully Console.%s=%s",key,value));
			    }else{
			        if(enableSystemOut) System.out.println("ERROR: Invalid console variable");
			    }
			} else if (executeCommand.matches(CONSOLE_FUN_REGEX)){
			    open(enableSystemOut);
    	        Pattern pattern = Pattern.compile(CONSOLE_FUN_REGEX);
                Matcher matcher = pattern.matcher(executeCommand);
                if(matcher.find()){
                    String fun = matcher.group(2);
                    String paramsStr = matcher.group(3).trim();
                    List<String> params = new ArrayList<String>();
                    if(!CommonTools.isBlank(paramsStr)){
                        params = Arrays.asList(paramsStr.split(","));
                    }
                    try{
                        Method method = JdbcConsole.class.getMethod(fun,List.class);
                        Object msg = method.invoke(this,params);
                        if(enableSystemOut) {
                            if(msg != null){
                                System.out.println(msg.toString());
                            }
                        }
                    }catch(Exception e){
                        log.error(e.getMessage(),e);
                        if(enableSystemOut){
                            System.out.println(String.format("ERROR: Invalid method '%s'",fun));
                            System.out.println(error(e));
                        }
                    }
                }
			} else {
			    try{
			        Integer queryCursorStart = consoleVariables.getInteger("QueryCursorStart", DEFAULT_QueryCursorStart);
			        Integer queryMaxRows = consoleVariables.getInteger("QueryMaxRows",DEFAULT_QueryMaxRows);
    				if (executeCommand.toUpperCase().matches(SELECT_REGEX)) {
    				    open(enableSystemOut);
    				    lastSelectCommand = executeCommand;
    				    CacheArray cacheArray = new CacheArray();
    				    cacheArray.filter(getCacheArrayFilter(enableSystemOut));
    				    cacheDriverExecutor.find(queryCursorStart,queryMaxRows,executeCommand,cacheArray);
    				} else if (executeCommand.toUpperCase().matches(CALL_REGEX)) {
    				    open(enableSystemOut);
    				    lastSelectCommand = executeCommand;
    				    String callStoredProcedureFun = consoleVariables.getString("CallStoredProcedureFun",DEFAULT_CallStoredProcedureFun);
    				    if(callStoredProcedureFun.equals("callAndReturnList")){
        				    CacheArray cacheArray = new CacheArray();
        				    cacheArray.filter(getCacheArrayFilter(enableSystemOut));
        				    cacheDriverExecutor.callAndReturnList(queryCursorStart,queryMaxRows,executeCommand,cacheArray);
    				    }
    					if(callStoredProcedureFun.equals("callAndReturnRows")){
        					int callRows = cacheDriverExecutor.callAndReturnRows(executeCommand);
        					setIntoValue(callRows);
        					if(enableSystemOut) System.out.println(callRows);
    					}
    					if(callStoredProcedureFun.equals("callAndReturnBoolean")){
        					Boolean callBoolean = cacheDriverExecutor.callAndReturnBoolean(executeCommand);
        					setIntoValue(callBoolean);
        					if(enableSystemOut) System.out.println(callBoolean);
    					}
    				}else {
    				    open(enableSystemOut);
    				    lastSelectCommand = executeCommand;
    				    final java.util.Date beginTime = new java.util.Date();
    					int rows = cacheDriverExecutor.execute(executeCommand);
    					long timeCostMs = new java.util.Date().getTime() - beginTime.getTime();
    					setIntoValue(rows);
    					if(enableSystemOut) {
    					    System.out.println(String.format("%s row executed (%sms)",rows,timeCostMs));
    					}
    				}
			    }catch(Exception ee){
			        setIntoValue(null);
			        throw ee;
			    }
			}
		} catch (Exception e) {
            System.out.println(error(e));
            log.info(executeCommand);
            log.error(e.getMessage(),e);
            if(writeHistory && enableSystemOut && executeCommand.indexOf(commandHistory) != 0){
                addHistory(" <INVALID");
            }
            return false;
		} finally{
    		if(writeHistory && enableSystemOut && executeCommand.indexOf(commandHistory) != 0){
    	    	addHistory(System.lineSeparator());
    		}
		}
		return true;
	}

	/**
	* Performs the getCacheArrayFilter operation
	* @param enableSystemOut - the enableSystemOut
	* @return the getCacheArrayFilter result
	* */
	public CacheArrayFilter getCacheArrayFilter(Boolean enableSystemOut) {
		return new CacheArrayFilter(1L,1L) {
			final java.util.Date beginTime = new java.util.Date();
			final List<String> columns = new ArrayList<String>();
			String format = "%" + consoleVariables.getInteger("ColumnWidth", DEFAULT_ColumnWidth) + "s";

			@Override

			/*
			* Performs the execute operation
			* @param index - the index
			* @param o - the o
			* */
			@SuppressWarnings("unchecked")
			public void execute(Integer index, Object o) {
				if (index == 0) {
					StringBuffer header = new StringBuffer();
					boolean showResultHeader = consoleVariables.getBoolean("ShowResultHeader",
							DEFAULT_ShowResultHeader);
					Map<String, Object> firstRow = (Map<String, Object>) o;
					columns.addAll(firstRow.keySet());
					int columsSize = columns.size();
					for (int i = 0; i < columsSize; i++) {
						header.append(String.format(format, columns.get(i)));
						if (i < columsSize - 1)
							header.append("\t");
					}
					if(enableSystemOut){
					    try{
					        Thread.sleep(500);
					        System.out.println();
					    }catch(Exception e){
					        log.error(e.getMessage(),e);
					    }
					}
					if (enableSystemOut && showResultHeader) {
						System.out.println(header);
					}
				}
				if (index >= 0) {
					int columsSize = columns.size();
					@SuppressWarnings("unchecked")
					StringBuffer body = new StringBuffer();
					boolean showResultBody = consoleVariables.getBoolean("ShowResultBody", DEFAULT_ShowResultBody);
					Map<String, Object> row = (Map<String, Object>) o;
					for (int i = 0; i < columsSize; i++) {
						String column = columns.get(i);
						Object objValue = row.get(column);
						String value = format(objValue);
						if (index == 0 && i == 0) {
							setIntoValue(objValue);
						}
						body.append(value);
						if (i < columsSize - 1)
							body.append("\t");
					}
					if (enableSystemOut && showResultBody) {
						System.out.println(body);
					}
				}
			}

			@Override

			/*
			* Performs the completed operation
			* @param size - the size
			* */
			public void completed(Integer size) {
				boolean showResultFooter = consoleVariables.getBoolean("ShowResultFooter", DEFAULT_ShowResultFooter);
				if (enableSystemOut && showResultFooter) {
					long timeCostMs = new java.util.Date().getTime() - beginTime.getTime();
					String footer = String.format("%s rows selected (%sms)", size, timeCostMs);
					System.out.println(System.lineSeparator());
					System.out.println(footer);
					Integer queryMaxRows = consoleVariables.getInteger("QueryMaxRows",DEFAULT_QueryMaxRows);
					System.out.println(String.format("Current QueryMaxRows=%s",queryMaxRows));
				}
			}
		};
	}

	/**
	* Performs the importFrom operation
	* @param schema - the schema
	* @param tableName - the tableName
	* @param csv - the csv
	* @return the importFrom result
	* */
	public String importFrom(String schema, String tableName, String csv) throws Exception {
		InputStream inputStream = null;
		Scanner sc = null;
		int updatedRows = 0;
		try {
			String tableObjectFolder = String.format("%s/%s_objects", new File(csv).getParent(), tableName);
			Integer batchSize = consoleVariables.getInteger("BatchSize", DEFAULT_BatchSize);
			String importSplit = consoleVariables.getString("ImportSplit", DEFAULT_ImportSplit);
			batchSize = batchSize < 10 ? 10 : batchSize;
			Map<String, Object> classes = cacheDriverExecutor.getColumnClasses(tableName);
			inputStream = new FileInputStream(csv);
			sc = new Scanner(inputStream, BaseFile.CHARSET);
			int r = 0;
			String sql = null;
			final java.util.Date beginTime = new java.util.Date();
			List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
			List<String> header = new ArrayList<String>();
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (r == 0) {
                    if (line.length() > 0 && line.charAt(0) == '\uFEFF') {
                        line = line.substring(1);
                    }
					String[] hs = line.split(importSplit);
					for (String h : hs) {
						if (!CommonTools.isBlank(h)) {
							header.add(h.trim());
						}
					}
					sql = converToSqlFromMap(schema, tableName, header);
				}
				if (r > 0) {
					if (!CommonTools.isBlank(line)) {
						Map<String, Object> record = new ResultMap<String, Object>();
						String[] cells = line.split(importSplit);
						int cellSize = cells.length;
						for (int c = 0; c < cellSize; c++) {
							String cell = cells[c];
							String column = header.get(c);
							String javaType = (String) classes.get(column);
							if (javaType == null) {
								javaType = (String) classes.get(column.toLowerCase());
							}
							if (javaType == null) {
								javaType = (String) classes.get(column.toUpperCase());
							}
							if (javaType == null) {
								throw new Exception(
										String.format("Cloumn %s not matched the table %s", column, tableName));
							}
							record.put(column, conver(tableObjectFolder, javaType, cell));
						}
						records.add(record);
					}
				}
				int mod = r % batchSize;
				if (r > 0 && mod == 0) {
					updatedRows += cacheDriverExecutor.executeBatch(sql, records);
					records.clear();
				}
				r++;
			}

			if (records.size() > 0) {
				updatedRows += cacheDriverExecutor.executeBatch(sql, records);
				records.clear();
			}
			long timeCostMs = new java.util.Date().getTime() - beginTime.getTime();
			return String.format("Table %s %s rows inserted (%sms)", tableName, updatedRows, timeCostMs);
		} finally {
			if (sc != null)
				sc.close();
			if (inputStream != null)
				inputStream.close();
		}
	}

	/**
	* Performs the importTables operation
	* @param schema - the schema
	* @param folder - the folder
	* @return the importTables result
	* */
	public String importTables(String schema, String folder) {
		Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
		int imported = 0;
		int failed = 0;
		final java.util.Date beginTime = new java.util.Date();
		if (showTaskLog) {
			String beginTimeFormat = new SimpleDateFormat(DEFAULT_DateTimeFormat).format(beginTime);
			System.out.println(String.format("Importing tables at %s", beginTimeFormat));
		}
		StringBuffer importResult = new StringBuffer();
		File folderFile = new File(folder);
		File[] list = folderFile.listFiles();
		Arrays.sort(list);
		for (File file : list) {

			if (!file.exists() || !file.isFile())
				continue;

			String fileName = file.getName();
			if (fileName.toUpperCase().endsWith(".CSV")) {
				String tableName = fileName.replaceFirst("\\..*$", "");
				try {
					if (showTaskLog)
						System.out.println(String.format("Table %s from %s", tableName, file.getAbsolutePath()));

					String msg = importFrom(schema, tableName, file.getAbsolutePath());
					imported++;
					if (showTaskLog)
						System.out.println(msg);
				} catch (Exception e) {
					failed++;
					log.error(e.getMessage(), e);
					if (showTaskLog)
						System.out.println(e.getMessage());
				}
			}
		}
		java.util.Date completedTime = new java.util.Date();
		if (showTaskLog) {
			String completedTimeFormat = new SimpleDateFormat(DEFAULT_DateTimeFormat).format(completedTime);
			System.out.println(String.format("Imported tables at %s", completedTimeFormat));
		}
		long timeCostMs = completedTime.getTime() - beginTime.getTime();
		return String.format("%s tables imported and %s tables failed (%sms)", imported, failed, timeCostMs);
	}

	/**
	* Performs the exportTables operation
	* @param schema - the schema
	* @param exportToFolder - the exportToFolder
	* @return the exportTables result
	* */
	public String exportTables(String schema, String exportToFolder) {
		Boolean showTaskLog = consoleVariables.getBoolean("ShowTaskLog", DEFAULT_ShowTaskLog);
		int exported = 0;
		int failed = 0;
		final java.util.Date beginTime = new java.util.Date();
		if (showTaskLog) {
			String beginTimeFormat = new SimpleDateFormat(DEFAULT_DateTimeFormat).format(beginTime);
			System.out.println(String.format("Exporting tables at %s", beginTimeFormat));
		}
		try {
			final List<String> tables = new ArrayList<String>();
			if (CommonTools.isBlank(schema)) {
				List<Map<String, Object>> list = cacheDriverExecutor.getAllTables();
				for (Map<String, Object> map : list) {
					String tableSchema = (String) map.get("TABLE_SCHEMA");
					String tableName = (String) map.get("TABLE_NAME");
					String schemaTableName = tableName;
					if (!CommonTools.isBlank(tableSchema)) {
						schemaTableName = String.format("%s.%s", tableSchema, tableName);
					}
					if (!tables.contains(schemaTableName))
						tables.add(schemaTableName);
				}
			} else {
				List<Map<String, Object>> list = cacheDriverExecutor.getAllTables();
				for (Map<String, Object> map : list) {
					String tableSchema = (String) map.get("TABLE_SCHEMA");
					String tableName = (String) map.get("TABLE_NAME");
					if (!CommonTools.isBlank(tableSchema)) {
						if (schema.equalsIgnoreCase(tableSchema)) {
							String schemaTableName = String.format("%s.%s", tableSchema, tableName);

							if (!tables.contains(schemaTableName))
								tables.add(schemaTableName);
						}
					}
				}
			}
			String exportOrderBy = consoleVariables.getString("ExportOrderBy", DEFAULT_ExportOrderBy);
			for (String table : tables) {
				String command = String.format("SELECT * FROM %s %s", quote(table), exportOrderBy);
				String fileName = String.format("%s/%s", exportToFolder,
						exportUpperOrLowerCase(table.replaceFirst("\\.", "/")));
				File file = new File(fileName);
				boolean success = false;
				try {
				    exportTo(command,file.getAbsolutePath());
					exported++;
				} catch (Exception ee) {
					log.error(ee);
					failed++;
				}
			}
			java.util.Date completedTime = new java.util.Date();
			if (showTaskLog) {
				String completedTimeFormat = new SimpleDateFormat(DEFAULT_DateTimeFormat).format(completedTime);
				System.out.println(String.format("Exported tables at %s", completedTimeFormat));
			}
			long timeCostMs = completedTime.getTime() - beginTime.getTime();
			return String.format("*********%s tables exported and %s tables failed (%sms)*********", exported, failed,
					timeCostMs);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return e.getMessage();
		}
	}

	/*For jdbc console*/

	/**
	* Performs the importTables operation
	* @param params - the params
	* @return the importTables result
	* */
	public Object importTables(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 && params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.importTables(csvFolder) or Console.importTables(schema,csvFolder)'";
		}
		String schema = null;
		String csvFolder = null;
		if (params.size() == 1) {
			schema = null;
			csvFolder = formatCommand(params.get(0).trim());
		}
		if (params.size() == 2) {
			schema = formatCommand(params.get(0).trim());
			csvFolder = formatCommand(params.get(1).trim());
		}
		return importTables(schema, csvFolder);
	}

	/**
	* Performs the clearLastSelectCommand operation
	* @param params - the params
	* @return the clearLastSelectCommand result
	* */
	public Object clearLastSelectCommand(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() > 0) {
			return "ERROR: Invalid params, eg.'Console.clearLastSelectCommand()'";
		}
		clearLastSelectCommand();
		return "Cleared last select command";
	}

	/**
	* Performs the exportTables operation
	* @param params - the params
	* @return the exportTables result
	* */
	public Object exportTables(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.exportTables(schema,toCsvFolder) or Console.exportTables(toCsvFolder)'";
		}

		String _aesKey = consoleVariables.getString("JdbcCipherAesKey");
		boolean useEncrypt = !CommonTools.isBlank(_aesKey);
		if (useEncrypt) {
			if (_aesKey.length() < 16 || _aesKey.length() > 256) {
				return "ERROR: Invalid Console.JdbcCipherAesKey (Required 16 - 256 chars)";
			}
			System.out.println("*********************************************");
			System.out.println("Please remember the info(For import),");
			System.out.println(String.format("Console.JdbcCipherAesKey=%s;", _aesKey));
			System.out.println("*********************************************");
		}

		try {
			String schema = null;
			String exportToFolder = null;
			if (params.size() == 1) {
				schema = null;
				exportToFolder = formatCommand(params.get(0).trim());
			}
			if (params.size() == 2) {
				schema = formatCommand(params.get(0).trim()).toUpperCase().equals("NULL") ? null : params.get(0).trim();
				exportToFolder = formatCommand(params.get(1).trim());
			}
			return exportTables(schema, exportToFolder);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the desc operation
	* @param params - the params
	* @return the desc result
	* */
	public Object desc(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.desc(tableName) or Console.desc(schema,tableName)'";
		}
		try {
			String schema = null;
			String tableName = null;
			if (params.size() == 1) {
				tableName = formatCommand(params.get(0).trim());
			}
			if (params.size() == 2) {
				schema = formatCommand(params.get(0).trim());
				tableName = formatCommand(params.get(1).trim());
			}
			List<Map<String, Object>> list = desc(schema, tableName);
			String lines = toResultList(list);
			return lines;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the showTables operation
	* @param params - the params
	* @return the showTables result
	* */
	public Object showTables(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.showTables() or Console.showTables(schema)'";
		}
		try {
			String schema = null;
			List<Map<String, Object>> tables = new ArrayList<Map<String, Object>>();
			if (params.size() == 1) {
				schema = formatCommand(params.get(0).trim());
			}
			tables.addAll(showTables(schema));
			String lines = toResultList(tables);
			return lines;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the showColumnTypes operation
	* @param params - the params
	* @return the showColumnTypes result
	* */
	public Object showColumnTypes(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 || params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.showColumnTypes(tableName)'";
		}
		try {
			String tableName = formatCommand(params.get(0).trim());
			Map<String, Object> result = showColumnTypes(tableName);
			List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
			list.add(result);
			String lines = toResultList(list);
			return lines;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the showColumnClasses operation
	* @param params - the params
	* @return the showColumnClasses result
	* */
	public Object showColumnClasses(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 || params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.showColumnClasses(tableName)'";
		}
		try {
			String tableName = formatCommand(params.get(0).trim());
			Map<String, Object> result = showColumnClasses(tableName);
			List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
			list.add(result);
			String lines = toResultList(list);
			return lines;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the exportTo operation
	* @param params - the params
	* @return the exportTo result
	* */
	public Object exportTo(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.exportTo(path)'";
		}
		try {
		    if(params.size() == 1){
    			String file = formatCommand(params.get(0).trim());
    			if (CommonTools.isBlank(file)) {
    				return "ERROR: Invalid params, eg.'Console.exportTo(path)'";
    			}
    			exportTo(file);
		    }
		    if(params.size() == 2){
		        String folder = formatCommand(params.get(0).trim());
    			String filePrefix = formatCommand(params.get(1).trim());
    			if (CommonTools.isBlank(folder) || CommonTools.isBlank(filePrefix)) {
    				return "ERROR: Invalid params, eg.'Console.exportTo(folder,filePrefix)'";
    			}
    			exportTo(new File(folder),filePrefix);
		    }
			return "Successfully";
		} catch (Exception e) {
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the importFrom operation
	* @param params - the params
	* @return the importFrom result
	* */
	public Object importFrom(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 || params.size() == 1 || params.size() > 3) {
			return "ERROR: Invalid params, eg.'Console.importFrom(schema,tableName,csv) or Console.importFrom(tableName,csv)'";
		}
		try {
			String schema = null;
			String tableName = null;
			Integer batchSize = consoleVariables.getInteger("BatchSize", DEFAULT_BatchSize);
			String csv = null;
			if (params.size() == 2) {
				tableName = formatCommand(params.get(0).trim());
				csv = params.get(1).trim();
			}
			if (params.size() == 3) {
				schema = formatCommand(params.get(0).trim()).toUpperCase().equals("NULL") ? null : params.get(0).trim();
				tableName = formatCommand(params.get(1).trim());
				csv = params.get(2).trim();
			}
			return importFrom(schema, tableName, csv);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the setAutoCommit operation
	* @param params - the params
	* @return the setAutoCommit result
	* */
	public Object setAutoCommit(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 || params.size() > 1) {
			return "ERROR: Invalid params, eg.'Console.setAutoCommit(true/false)'";
		}
		Boolean autoCommit = null;
		try {
			if (params.size() == 1) {
				autoCommit = Boolean.parseBoolean(formatCommand(params.get(0).trim()));
			}
			Boolean b = setAutoCommit(autoCommit);
			return b ? "Successfully" : "Connection is null";
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the genCreateTables operation
	* @param params - the params
	* @return the genCreateTables result
	* */
	public Object genCreateTables(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.genCreateTables(schema,sqlFilePath) or Console.genCreateTable(sqlFilePath)'";
		}
		String schema = null;
		String sqlFilePath = null;
		try {
			if (params.size() == 1) {
				sqlFilePath = formatCommand(params.get(0).trim());
			}
			if (params.size() == 2) {
				schema = formatCommand(params.get(0).trim());
				sqlFilePath = formatCommand(params.get(1).trim());
			}
			return genCreateTables(schema, sqlFilePath);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

	/**
	* Performs the genCreateTable operation
	* @param params - the params
	* @return the genCreateTable result
	* */
	public Object genCreateTable(List<String> params) {
	    if(!isUseJdbcEnv()){
	        return "ERROR: JDBC environment is supported only.";
	    }
		if (params.size() == 0 || params.size() > 2) {
			return "ERROR: Invalid params, eg.'Console.genCreateTable(schema,tableName) or Console.genCreateTable(tableName)'";
		}
		String schema = null;
		String tableName = null;
		try {
			if (params.size() == 1) {
				tableName = formatCommand(params.get(0).trim());
			}
			if (params.size() == 2) {
				schema = formatCommand(params.get(0).trim());
				tableName = formatCommand(params.get(1).trim());
			}
			return genCreateTable(schema, tableName);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return ScriptConsoleBase.error(e);
		}
	}

}
