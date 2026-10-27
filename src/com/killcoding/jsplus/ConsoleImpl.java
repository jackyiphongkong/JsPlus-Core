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
package com.killcoding.jsplus;

/**
* Core console implementation of com.killcoding.jsplus.Console: binds the script engine and
* delegates evaluation to ScriptConsole. Also carries the build version constant which build.xml
* injects on every build (replace-version target).
* */
public interface ConsoleImpl {
    
    public final static String VERSION = "${BuildVersion.VERSION}(${BuildVersion.BUILD_DATE})";
    
    public final static String CONSOLE_FUN_REGEX = "^(Console\\.)([A-Za-z0-9_]+)[\s]*\\((.*)\\)[\s]*$";
	public final static String CONSOLE_VAR_REGEX = "^(Console\\.)[A-Za-z0-9_]+[\s]*\\=.*$";
	public final static String SELECT_REGEX = "^(SELECT|SHOW|DESC|EXPLAIN|VALUES)[\s]{1,}.*$";
	public final static String CALL_REGEX =	"^(CALL)[\s]{1,}.*$";

    public final static Integer DEFAULT_QueryCursorStart = 0;
	public final static Integer DEFAULT_QueryMaxRows = 1000;
	public final static Integer DEFAULT_ColumnWidth = 10;
	public final static String DEFAULT_CommandEnd = ";";
	public final static String DEFAULT_CommandCancel = "!";
	public final static String DEFAULT_CommandHistory = "#";
	public final static Integer DEFAULT_ColumnCut = 100;
	public final static Boolean DEFAULT_WriteHistory = true;
	public final static Boolean DEFAULT_AutoCommit = false;
	public final static Boolean DEFAULT_ShowResultHeader = true;
	public final static Boolean DEFAULT_ShowResultBody = true;
	public final static Boolean DEFAULT_ShowResultFooter = true;
	public final static String DEFAULT_DateTimeFormat = "yyyy.MM.dd HH:mm:ss[.SSSSSSSSS]";
	public final static String DEFAULT_DateFormat = "yyyy.MM.dd";
	public final static String DEFAULT_TimeFormat = "HH:mm:ss[.SSSSSSSSS]";
	public final static String DEFAULT_Name = "jdbc";
	public final static Long DEFAULT_TaskTimer = 0L;
	public final static String DEFAULT_Task = null;
	public final static Boolean DEFAULT_ShowTaskLog = true;
	public final static Integer DEFAULT_ExportCursorStart = 0;
	public final static Integer DEFAULT_ExportMaxFileSize = 100;
	public final static Integer DEFAULT_ExportMaxRows = 50000000;
	public final static String DEFAULT_ExportUpperOrLowerCase = "Original";
	public final static Integer DEFAULT_BatchSize = 100;
	public final static Long DEFAULT_ExportTimer = 1000L;
	public final static String DEFAULT_Quote = "%s";
	public final static String DEFAULT_CallStoredProcedureFun = "callAndReturnBoolean";
	public final static String DEFAULT_ScriptEngine = "nashorn";
	public final static String DEFAULT_ExportOrderBy = "";
	public final static String DEFAULT_ExportSplit = ",";
	public final static String DEFAULT_ImportSplit = ",";
	
	public final static String DEFAULT_Jdbc_Cipher_AesKey = "";
	public final static String DEFAULT_Script_Cipher_AesKey = "";
	public final static String DEFAULT_Config_Cipher_AesKey = "";
	

}