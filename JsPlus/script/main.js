var Runnable = Java.type("java.lang.Runnable");
var Thread = Java.type("java.lang.Thread");
var Logger = Java.type("com.killcoding.log.Logger");
var LoggerFactory = Java.type("com.killcoding.log.LoggerFactory");
var LogRecord = Java.type("com.killcoding.log.LogRecord");
var DiskFile = Java.type("com.killcoding.file.DiskFile");
var Exception = Java.type("java.lang.Exception");
var SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
var DriverDataSource = Java.type("com.killcoding.datasource.DriverDataSource");
var System = Java.type("java.lang.System");
var ScriptConsole = Java.type("com.killcoding.jsplus.ScriptConsole");
var GhostConsole = Java.type("com.killcoding.jsplus.GhostConsole");
var String = Java.type("java.lang.String");
var Thread = Java.type("java.lang.Thread");
var Runnable = Java.type("java.lang.Runnable");
var HashMap = Java.type("java.util.HashMap");


var log = LoggerFactory.getLogger("main.js");
Logger.systemInfo("main.js","Starting...");
var jsPlusPath = System.getProperty("JS_PLUS_PATH");

print("Hello World!");
