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

import com.killcoding.jsplus.ScriptConsole;


import com.killcoding.log.Logger;
import com.killcoding.datasource.CacheDriverExecutor;
import java.sql.Connection;

import com.killcoding.datasource.DriverDataSource;

import com.killcoding.cache.CacheArray;
import java.util.HashMap;
import java.util.Map;

import com.killcoding.tool.ConfigProperties;
import java.io.File;

import com.killcoding.datasource.Clock;
import java.sql.Timestamp;
import com.killcoding.tool.CommonTools;

/*
*
CREATE TABLE ghost_remote_command (
id CHAR(16) PRIMARY KEY,
ghost_name VARCHAR(200),
request_script VARCHAR(500),
response_message VARCHAR(500),
processed INT,
requested INT,
responsed INT,
created_at TIMESTAMP,
updated_at TIMESTAMP,
deleted_at TIMESTAMP,
deleted SMALLINT
);
*
 * */
/**
* Ghost-mode console for daemon / service sessions: headless command processing without an
* interactive prompt, with a response-line delay (RESPONSE_LINE_DELAY_MS) between command outputs.
* Used e.g. by script/StartH2.js with -GhostName H2Ghost.
* */
public class GhostConsole extends ScriptConsole {

    public static int RESULT_SIZE = 500;

    /** Delay between printing response lines (per-line throttle). */
    private static final long RESPONSE_LINE_DELAY_MS = 200L;

    private static String GHOST_REQUESTED_SQL = "SELECT * FROM %s WHERE deleted = 0 AND requested = 1 AND responsed = 0 AND processed = 0 AND ghost_name = :ghost_name ORDER BY created_at ASC";
    private static String GHOST_GET_PROCESSED_SQL = "SELECT * FROM %s WHERE deleted = 0 AND requested = 1 AND responsed = 0 AND processed = 1 AND ghost_name = :ghost_name ORDER BY created_at ASC";
    private static String GHOST_PROCESSED_SQL = "UPDATE %s SET responsed = 0,processed = 1,response_message = :response_message,updated_at = :updated_at WHERE id = :id";
    private static String GHOST_RESPONSED_SQL = "UPDATE %s SET responsed = 1,updated_at = :updated_at WHERE id = :id";
    private static String GHOST_REQUEST_SQL = "INSERT INTO %s (id,ghost_name,request_script,response_message,requested,responsed,processed,created_at,updated_at,deleted_at,deleted) VALUES (:id,:ghost_name,:request_script,:response_message,:requested,:responsed,:processed,:created_at,:updated_at,:deleted_at,:deleted)";
    private static String GHOST_CLEAN_SQL = "DELETE FROM %s WHERE responsed = 1 AND processed = 1 AND deleted = 0 AND ghost_name = :ghost_name";
    private static String GHOST_CLEAN_ALL_SQL = "DELETE FROM %s WHERE ghost_name = :ghost_name";

    private ConfigProperties config = null;
    private DriverDataSource ghostDs = null;

    private String ghostTable = null;
    private String ghostName = null;
    private long ghostTimer = 200L;
    private CacheArray requestRows = null;
    private CacheArray responseRows = null;
    private ScriptConsole console = null;
    private boolean autoClean = true;

    private boolean exit = false;
    private boolean firstStart = true;

    /**
    * Constructs a new GhostConsole
    * @param console - the console
    * @param ghostName - the ghostName
    * */
    @SuppressWarnings("this-escape")
    public GhostConsole(ScriptConsole console,String ghostName) {
        super();
        this.console = console;
        this.ghostName = ghostName;
        setUseGhostEnv();
        CacheDriverExecutor.COLUMN_NAME_CASE_MODE = CacheDriverExecutor.COLUMN_NAME_CASE_LOWER;
        String ghostDsPropFile = ScriptConsole.getJsPlusPath() + "/config/GhostDataSource.properties";
        init(ghostDsPropFile);
    }

    /**
    * Performs the init operation
    * @param ghostDsPropFile - the ghostDsPropFile
    * */
    private void init(String ghostDsPropFile) {
        try{
            config = new ConfigProperties();
            ghostDs = new DriverDataSource(new File(ghostDsPropFile));
            config = ghostDs.getConfigProperties();
            ghostTable = config.getString("GhostTable","ghost_remote_command");
            ghostTimer = config.getMilliSeconds("GhostTimer",200L);
        }catch(Exception e){
            Logger.systemError(GhostConsole.class,e.getMessage(),e);
        }
    }

    /**
    * Performs the isAutoClean operation
    * @return the isAutoClean result
    * */
    public boolean isAutoClean(){
        return this.autoClean;
    }

    /**
    * Performs the submit operation
    * @param executeCommand - the executeCommand
    * */
    public void submit(String executeCommand){
        String sql = String.format(GHOST_REQUEST_SQL,ghostTable);
        CacheDriverExecutor cde = null;
        try{
            Map<String,Object> params = new HashMap<String,Object>();
            Timestamp currentTime = new Clock().getSqlTimestamp();
            params.put("ghost_name",ghostName);
            params.put("requested",1);
            params.put("responsed",0);
            params.put("processed",0);
            params.put("request_script",executeCommand);
            params.put("response_message",null);
            params.put("created_at",currentTime);
            params.put("updated_at",currentTime);
            params.put("deleted",0);
            params.put("id",CommonTools.generateId(16));
            Connection conn = ghostDs.getConnection();
            cde = new CacheDriverExecutor(conn);
            cde.execute(sql,params);
        }catch(Exception e){
           Logger.systemError(GhostConsole.class,e.getMessage(),e);
        }finally{
           if(cde != null) try{cde.close();}catch(Exception e){Logger.systemDebug(GhostConsole.class,"cde.close failed: {}",e.getMessage(),e);}
        }
    }

    /**
    * Performs the getExecuteRun operation
    * @return the getExecuteRun result
    * */
    private Runnable getExecuteRun(){
        return new Runnable(){
            @Override
            /*
            * Performs the run operation
            * */
            @SuppressWarnings("unchecked")
            public void run(){
                CacheDriverExecutor cde = null;
                try{
                    Object o = requestRows.getObject();
                    if(o != null){
                        Map<String,Object> ghostRecord = (Map<String,Object>)o;
                        String requestScript = (String)ghostRecord.get("request_script");
                        Object id = ghostRecord.get("id");
                        Object result = null;
                        try{
                            result = console.evalScript(requestScript);
                        }catch(Exception e){
                            result = e.getMessage();
                            Logger.systemError(GhostConsole.class,e.getMessage(),e);
                        }
                        result = (result == null ? null : result + "");
                        if(result != null && result.toString().length() > RESULT_SIZE){
                            result = result.toString().substring(0,RESULT_SIZE - 1);
                        }
                        Map<String,Object> params = new HashMap<String,Object>();
                        String sql = String.format(GHOST_PROCESSED_SQL,ghostTable);
                        params = new HashMap<String,Object>();
                        params.put("response_message",result);
                        params.put("updated_at",new Clock().getSqlTimestamp());
                        params.put("id",id);
                        Connection conn = ghostDs.getConnection();
                        cde = new CacheDriverExecutor(conn);
                        cde.execute(sql,params);
                    }
                }catch(Exception e){
                    Logger.systemError(GhostConsole.class,e.getMessage(),e);
                }finally{
                    if(cde != null) try{cde.close();}catch(Exception e){Logger.systemDebug(GhostConsole.class,"cde.close failed: {}",e.getMessage(),e);}
                }
            }
        };
    }

    /**
    * Performs the getCompletedRun operation
    * @return the getCompletedRun result
    * */
    private Runnable getCompletedRun(){
        final GhostConsole the = this;
        return new Runnable(){
            @Override
            /*
            * Performs the run operation
            * */
            public void run(){
                try{
                    the.requestRows = null;

                    if(!exit) Thread.sleep(ghostTimer);

                    if(!exit) the.start(the.autoClean);
                }catch(Exception e){
                    Logger.systemError(GhostConsole.class,e.getMessage(),e);

                    if(!exit) try {Thread.sleep(ghostTimer);} catch(Exception ee){Logger.systemDebug(GhostConsole.class,"ghostTimer sleep interrupted: {}",ee.getMessage(),ee);};

                    if(!exit) the.start(the.autoClean);
                }
            }
        };
    }

   /**
   * Performs the getDetectExecuteRun operation
   * @return the getDetectExecuteRun result
   * */
   private Runnable getDetectExecuteRun(){
        return new Runnable(){
            @Override
            /*
            * Performs the run operation
            * */
            @SuppressWarnings("unchecked")
            public void run(){
                CacheDriverExecutor cde = null;
                try{
                    Object o = responseRows.getObject();
                    if(o != null){
                        Map<String,Object> ghostRecord = (Map<String,Object>)o;
                        String responseMessge = (String)ghostRecord.get("response_message");
                        Object id = ghostRecord.get("id");
                        String sql = String.format(GHOST_RESPONSED_SQL,ghostTable);
                        Map<String,Object> params = new HashMap<String,Object>();
                        params.put("response_message",null);
                        params.put("updated_at",new Clock().getSqlTimestamp());
                        params.put("id",id);
                        Connection conn = ghostDs.getConnection();
                        cde = new CacheDriverExecutor(conn);
                        cde.execute(sql,params);
                        if((responseMessge + "").equals("undefined")){
                            responseMessge = null;
                        }
                        if(responseMessge == null){
                             System.out.println("The command has been executed remotely but no message has been returned.");
                        }else{
                             String[] responseMessgeArray = responseMessge.split("\n");
                             int len = responseMessgeArray.length;
                             for(int i = 0;i < len;i++){
                                 System.out.println(responseMessgeArray[i]);
                                 Thread.sleep(RESPONSE_LINE_DELAY_MS);
                             }
                        }
                    }
                }catch(Exception e){
                    Logger.systemError(GhostConsole.class,e.getMessage(),e);
                }finally{
                    if(cde != null) try{cde.close();}catch(Exception e){Logger.systemDebug(GhostConsole.class,"cde.close failed: {}",e.getMessage(),e);}
                }
            }
        };
    }

    /**
    * Performs the getDetectCompletedRun operation
    * @return the getDetectCompletedRun result
    * */
    private Runnable getDetectCompletedRun(){
        final GhostConsole the = this;
        return new Runnable(){
            @Override
            /*
            * Performs the run operation
            * */
            public void run(){
                try{
                    the.responseRows = null;

                    if(!exit) Thread.sleep(ghostTimer);

                    if(!exit) the.detect();
                }catch(Exception e){
                    Logger.systemError(GhostConsole.class,e.getMessage(),e);

                    if(!exit) try {Thread.sleep(ghostTimer);} catch(Exception ee){Logger.systemDebug(GhostConsole.class,"ghostTimer sleep interrupted: {}",ee.getMessage(),ee);};

                    if(!exit) the.detect();
                }
            }
        };
    }

    /**
    * Performs the detect operation
    * */
    public synchronized void detect() {
        GhostConsole the = this;
        if(responseRows == null){
            CacheDriverExecutor cde = null;
            try{
                String sql = String.format(GHOST_GET_PROCESSED_SQL,ghostTable);
                responseRows = new CacheArray();
                Runnable executeRun = getDetectExecuteRun();
                Runnable completedRun = getDetectCompletedRun();
                responseRows.filter(ghostTimer,executeRun,completedRun,null);
                Map<String,Object> params = new HashMap<String,Object>();
                params.put("ghost_name",ghostName);
                Connection conn = ghostDs.getConnection();
                cde = new CacheDriverExecutor(conn);
                cde.find(0,100,sql,params,responseRows);
            }catch(Exception e){
                Logger.systemError(GhostConsole.class,e.getMessage(),e);

                if(!exit) try {Thread.sleep(ghostTimer);} catch(Exception ee){Logger.systemDebug(GhostConsole.class,"ghostTimer sleep interrupted: {}",ee.getMessage(),ee);};

                if(!exit) the.detect();
            }finally{
                if(cde != null) try {cde.close();}catch(Exception ee){Logger.systemDebug(GhostConsole.class,"cde.close failed: {}",ee.getMessage(),ee);};
            }
        }
    }

    /**
    * Performs the start operation
    * */
    public void start() throws Exception {
        start(true);
    }

    /**
    * Performs the start operation
    * @param autoClean - the autoClean
    * */
    public synchronized void start(boolean autoClean) {
        this.autoClean = autoClean;
        GhostConsole the = this;
        if(requestRows == null){
            CacheDriverExecutor cde = null;
            try{
                String sql = String.format(GHOST_REQUESTED_SQL,ghostTable);
                String cleanSql = String.format(GHOST_CLEAN_SQL,ghostTable);
                requestRows = new CacheArray();
                Runnable executeRun = getExecuteRun();
                Runnable completedRun = getCompletedRun();
                requestRows.filter(ghostTimer,executeRun,completedRun,null);
                Map<String,Object> params = new HashMap<String,Object>();
                params.put("ghost_name",ghostName);
                Connection conn = ghostDs.getConnection();
                cde = new CacheDriverExecutor(conn);
                if(firstStart){
                    firstStart = false;
                    if(this.autoClean){
                        String cleanAllSql = String.format(GHOST_CLEAN_ALL_SQL,ghostTable);
                        cde.execute(cleanAllSql,params);
                    }
                }
                cde.execute(cleanSql,params);
                cde.find(0,100,sql,params,requestRows);
            }catch(Exception e){
                Logger.systemError(GhostConsole.class,e.getMessage(),e);

                if(!exit) try {Thread.sleep(ghostTimer);} catch(Exception ee){Logger.systemDebug(GhostConsole.class,"ghostTimer sleep interrupted: {}",ee.getMessage(),ee);};

                if(!exit) the.start(the.autoClean);
            }finally{
                if(cde != null) try {cde.close();}catch(Exception ee){Logger.systemDebug(GhostConsole.class,"cde.close failed: {}",ee.getMessage(),ee);};
            }
        }
    }

    /**
    * Performs the stop operation
    * */
    public synchronized void stop(){
        exit = true;
    }
}
