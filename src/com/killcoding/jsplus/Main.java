package com.killcoding.jsplus;

import java.io.File;
import com.killcoding.tool.CommonTools;
import com.killcoding.datasource.DriverDataSource;
import java.util.Map;
import java.util.HashMap;
import com.killcoding.tool.FileTools;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Files;
import com.killcoding.cache.CacheArray;
import java.util.Scanner;
import java.nio.file.Paths;
import com.killcoding.tool.ConfigProperties;
import com.killcoding.jsplus.GhostConsole;

public final class Main { 
    
    private static ScriptConsole mainConsole = null;
    
    private static char[] password = null;
	private static String command = null;
	private static StringBuffer commands = null;
	private static boolean isEnd = false;
	private static boolean isCancel = false;
    
    public Main(){
        super();
    }
    
    public static void main(String[] args){
        appMain(args);
    }
    
	public static void appMain(String[] args) {
		Map<String, Object> params = ScriptConsole.parseArgs(args);
		if (params == null) {
			params = new HashMap<String, Object>();
		}
		
		String env = (String) params.get("Env");
		
        if(env.toUpperCase().equals("GHOST")) {
            ScriptConsole ghostScriptConsole = new ScriptConsole();
            ghostScriptConsole.setUseScriptEnv();
            String ghostName = (String)params.get("GhostName");
            if(CommonTools.isBlank(ghostName)){
                ghostName = "DefaultGhost";
            }
            mainConsole = new GhostConsole(ghostScriptConsole,ghostName);
            mainConsole.setUseGhostEnv();
        }
        
        if(env.toUpperCase().equals("JDBC")) {
            mainConsole = new JdbcConsole();
            mainConsole.setUseJdbcEnv();
        }
		
		if(env.toUpperCase().equals("CMD")) {
		    mainConsole = new CmdConsole();
		    mainConsole.setUseCmdEnv();
		}
		
		if(env.toUpperCase().equals("SCRIPT")) {
		    mainConsole = new ScriptConsole();
		    mainConsole.setUseScriptEnv();
		}
		
		if(mainConsole == null) {
		    System.out.println(String.format("Invalid Env '%s'.",env));
		    System.exit(1);
		}	
		
		int connectHashCode = mainConsole.hashCode();

        System.out.println("New Console: " + connectHashCode);
        
        ScriptConsole.CONNECT_CONSOLES.put(connectHashCode,mainConsole);
        mainConsole.connect(connectHashCode);
        
		if (params.containsKey("help")) {
			System.out.println(mainConsole.help());
			System.exit(0);
		}
		if (params.containsKey("version")) {
			System.out.println(mainConsole.version());
			System.exit(0);
		}
		if (params.containsKey("exit")) {
			System.out.println(ScriptConsole.exit());
			System.exit(0);
		}
		
		File datasourceConfigFile = new File(ScriptConsole.getJsPlusPath() + "/config/DataSource.properties");
		if(!datasourceConfigFile.exists()){
		    datasourceConfigFile = new File(ScriptConsole.getJsPlusPath() + "/config/Datasource.properties");
		}
	
		if (datasourceConfigFile.exists()) {
			try {
				mainConsole.datasourceVariables.load(datasourceConfigFile);
			} catch (Exception e) {
			    e.printStackTrace();
				System.err.println(ScriptConsole.error(e));
			}
		}

		if (params != null) {
			mainConsole.consoleVariables.putAllAndReturnSlef(params);
			mainConsole.consoleVariables.putAndReturnSlef("Task", params.get("Task"));
			if (params.get("TaskTimer") != null) {
				mainConsole.consoleVariables.putAndReturnSlef("TaskTimer", params.get("TaskTimer"));
			}
			if (mainConsole.consoleVariables.containsKey("CacheArrayInitPool")) {
			    CacheArray.initPool(mainConsole.consoleVariables.getInteger("CacheArrayInitPool",100));
		    }
		}

		mainConsole.checkHistory();

		commands = new StringBuffer();
		String line = null;
		Scanner scanner = new Scanner(System.in);
		String scriptEngine = (String) params.get("ScriptEngine");
		
		if (CommonTools.isBlank(scriptEngine)) {
			scriptEngine = "nashorn";
		}
		
		mainConsole.consoleVariables.putAndReturnSlef("ScriptEngine", scriptEngine);

		boolean havaTask = !CommonTools.isBlank(mainConsole.consoleVariables.getString("Task"));
		
		if (mainConsole.isUseJdbcEnv() && havaTask) {
			if (!mainConsole.open()) {
				System.exit(1);
			}
			mainConsole.consoleVariables.putAndReturnSlef("CommandEnd", ";");
			System.out.println("Changed CommandEnd to ';'");				
			mainConsole.taskExecute();
		}
		
		if (mainConsole.isUseCmdEnv()) {
			mainConsole.consoleVariables.putAndReturnSlef("CommandEnd", "/");
			System.out.println("Changed CommandEnd to '/'");	
			
			if(havaTask)
			    mainConsole.taskExecute();
		}		

		if (mainConsole.isUseScriptEnv()) {
			mainConsole.consoleVariables.putAndReturnSlef("CommandEnd", "/");
			System.out.println("Changed CommandEnd to '/'");
			try {
			    boolean allowAllAccess = true;
			    boolean havaAllowAllAccess = params.containsKey("AllowAllAccess");
			    if(havaAllowAllAccess){
			        allowAllAccess = Boolean.parseBoolean(params.get("AllowAllAccess") +"");
			    }
				mainConsole.initScriptEngineManager(allowAllAccess);
				if (havaTask) {
					mainConsole.taskExecute();
				}
			} catch (Exception e) {
			    e.printStackTrace();
				System.err.println(ScriptConsole.error(e));
			}
		}
		
		if (mainConsole.isUseGhostEnv()) {
			mainConsole.consoleVariables.putAndReturnSlef("CommandEnd", "/");
			System.out.println("Changed CommandEnd to '/'");
			try {
				if (havaTask) {
					mainConsole.taskExecute();
				}
			} catch (Exception e) {
			    e.printStackTrace();
				System.err.println(ScriptConsole.error(e));
			}
		}		

		if (mainConsole.isUseJdbcEnv() && !mainConsole.datasourceVariables.containsKey("DriverJar")){
			System.out.print("DriverJar? ");
		}
    
        try{
    		while ((line = scanner.nextLine()) != null) {
    		    
    		    ScriptConsole newConsole = mainConsole.getConsole();
    		    if(newConsole.hashCode() != mainConsole.hashCode()){
    		        mainConsole = newConsole;
    		        System.out.print(String.format("%s>",mainConsole.getDisplayEngineName()));
    		    }
    			if (mainConsole.isUseJdbcEnv()) {
    
    				if (env.toUpperCase().equals("SCRIPT")
    						&& !mainConsole.datasourceVariables.containsKey("DriverJar")) {
    					env = "jdbc";
    					System.out.print("DriverJar? ");
    					continue;
    				}
    				if (!mainConsole.datasourceVariables.containsKey("DriverJar")) {
    					String driverJar = line;
    					if (driverJar == null) {
    						System.out.print("DriverJar? ");
    						continue;
    					}
    					mainConsole.datasourceVariables.putAndReturnSlef("DriverJar", driverJar);
    					if (!mainConsole.datasourceVariables.containsKey("Driver")) {
    						System.out.print("Driver? ");
    						continue;
    					}
    				}
    
    				if (!mainConsole.datasourceVariables.containsKey("Driver")) {
    					String driver = line;
    					if (CommonTools.isBlank(driver)) {
    						System.out.print("Driver? ");
    						continue;
    					}
    					mainConsole.datasourceVariables.putAndReturnSlef("Driver", driver);
    					if (!mainConsole.datasourceVariables.containsKey("Url")) {
    						System.out.print("Url? ");
    						continue;
    					}
    				}
    
    				if (!mainConsole.datasourceVariables.containsKey("Url")) {
    					String url = line;
    					if (CommonTools.isBlank(url)) {
    						System.out.print("Url? ");
    						continue;
    					}
    					mainConsole.datasourceVariables.putAndReturnSlef("Url", url);
    					if (!mainConsole.datasourceVariables.containsKey("Username")) {
    						System.out.print("Username? ");
    						continue;
    					}
    				}
    
    				if (!mainConsole.datasourceVariables.containsKey("Username")) {
    					String username = line;
    					if (CommonTools.isBlank(username)) {
    						System.out.print("Username? ");
    						continue;
    					}
    					mainConsole.datasourceVariables.putAndReturnSlef("Username", username);
    					if (!mainConsole.datasourceVariables.containsKey("Password")) {
    						password = System.console().readPassword("Password?");
    						String passwordText = scanner.nextLine();
    						if (passwordText != null) {
    							passwordText = passwordText.trim();
    						}
    						if (password != null && password.length > 0) {
    							mainConsole.datasourceVariables.putAndReturnSlef("Password",
    									new String(password).trim());
    						} else if (passwordText.length() > 0) {
    							mainConsole.datasourceVariables.putAndReturnSlef("Password", passwordText);
    						}
    						continue;
    					}
    				}
    
    				if (!mainConsole.datasourceVariables.containsKey("Password")) {
    					if (password == null || password.length == 0) {
    						password = System.console().readPassword("Password?");
    						String passwordText = scanner.nextLine();
    						if (passwordText != null) {
    							passwordText = passwordText.trim();
    						}
    						if (password != null && password.length > 0) {
    							mainConsole.datasourceVariables.putAndReturnSlef("Password",
    									new String(password).trim());
    						} else if (passwordText.length() > 0) {
    							mainConsole.datasourceVariables.putAndReturnSlef("Password", passwordText);
    						}
    						continue;
    					}
    				}
    
    				if (!mainConsole.datasourceVariables.containsKey("Password") && password != null) {
    					mainConsole.datasourceVariables.putAndReturnSlef("Password", new String(password));
    				}
    
    				if (mainConsole.firstOpen) {
    					if (env.toUpperCase().equals("JDBC") && !mainConsole.open()) {
    						System.exit(1);
    					}
    				}
    
    			}
    
    			if (command == null && !isEnd && !isCancel) {
    				System.out.print(String.format("%s> ",mainConsole.getDisplayEngineName()));
    			}
    
    			command = line;
    			isCancel = mainConsole.isCommandCancel(command);
    			if (isCancel) {
    				commands = new StringBuffer();
    				command = null;
    				System.out.println("Canceled");
    				System.out.print(String.format("%s> ",mainConsole.getDisplayEngineName()));
    			} else {
    				String commandCancel = mainConsole.consoleVariables.getString("CommandCancel",
    						mainConsole.DEFAULT_CommandCancel);
    				isEnd = mainConsole.isCommandEnd(command);
    				commands.append(command);
    				commands.append(" ");
    				if (isEnd) {
    					if (mainConsole.isUseScriptEnv()) {
    						mainConsole.executeViaScript(commands.toString());
    					} else if (mainConsole.isUseJdbcEnv()){
    						mainConsole.executeViaJdbc(true, commands.toString());
    					} else if (mainConsole.isUseCmdEnv()) {
    						mainConsole.executeViaCmd(true,commands.toString());
    					} else if (mainConsole.isUseGhostEnv()) {
    						mainConsole.executeViaGhost(true,commands.toString());
    					} 
    					System.out.print(String.format("%s> ",mainConsole.getDisplayEngineName()));
    					commands = new StringBuffer();
    					command = null;
    				}
    			}
    			continue;
    		}
        }catch(Exception e){
            System.err.println("Current scanner exit.");
        }
	}
}