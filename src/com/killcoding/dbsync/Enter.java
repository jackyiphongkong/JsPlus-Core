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

package com.killcoding.dbsync;

import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
* DBSync entry point - configures the engine (JS-first via configure(ConfigProperties))
* and starts source-to-target sync rounds (start(String configFile)); the started
* workers are tracked so stop() can terminate them all.
* */
public class Enter {

	private final static Logger log = LoggerFactory.getLogger(Enter.class);

	public static String ROOT_PATH;

	/**
	* Per-instance DBSync configuration store (Source./Target. database keys and
	* Table.* keys). Private: callers must go through {@link #getPorps()} so the
	* instance owns its config and the field stays encapsulated.
	* */
	private ConfigProperties ENTER_PORPS;

	/**
	* Returns the per-instance DBSync configuration store. Equivalent to reading
	* the private field directly (same object, never a copy); returns non-null
	* after the constructor (init() initialises an empty store).
	* @return the instance DBSync ConfigProperties
	* */
	public ConfigProperties getPorps() {
		return ENTER_PORPS;
	}

	protected List<Database> started = new ArrayList<Database>();

	/**
	* Initialises the engine root path from the JS_PLUS_PATH system property
	* (falls back to the jar location) and resets the instance config store.
	* */
	public void init() {
		ENTER_PORPS = new ConfigProperties();
		try {
			ROOT_PATH = System.getProperty("JS_PLUS_PATH");
			if (CommonTools.isBlank(ROOT_PATH)) {
				ROOT_PATH = new File(Enter.class.getProtectionDomain().getCodeSource().getLocation().toURI())
						.getParentFile().getPath();
			}
			log.mark("ROOT_PATH = {}",ROOT_PATH);
		    System.setProperty("ROOT_PATH",ROOT_PATH);
		} catch (Exception e) {
			log.error(e.getMessage(),e);
		} 
	}
	
	private void load(String enterCnf) {
		FileInputStream fis = null;
		try {
			String enterPropsPath = String.format("%s/config/%s", ROOT_PATH,enterCnf);
			Logger.systemDebug(Enter.class,"Enter -> {}",enterCnf);
			File enterPropsFile = new File(enterPropsPath);

			if (enterPropsFile.exists()) {
				fis = new FileInputStream(enterPropsFile);
				ENTER_PORPS = new ConfigProperties();
				ENTER_PORPS.load(fis);
				
				Logger.systemDebug(Enter.class,"Configure Enter: {}",ENTER_PORPS);
			} else {
				Logger.systemDebug(Enter.class,"Not found, {}",enterPropsFile.getPath());
			}
		} catch (Exception e) {
			log.error(e.getMessage(),e);
			throw new RuntimeException("DBSync config load failed: " + e.getMessage(), e);
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					log.error(e.getMessage(),e);
				}
			}
		}
	}	

	/**
	* Creates an Enter instance with a ready-to-use engine: ROOT_PATH is detected and
	* ENTER_PORPS is initialised (empty) by init(), so configure(ConfigProperties) can
	* be called immediately. init() may be called again explicitly to reset the instance.
	* */
	public Enter() {
		super();
		init();
	}

	/**
	* Configures the DBSync engine from JS / code instead of a properties file - the
	* recommended entry when DBSync runs inside JsPlus-Core. Build a ConfigProperties in
	* JS, fill the "Source."/"Target." database keys and the "Table.*" keys, then pass it
	* here; Database / Table read the same per-instance ENTER_PORPS store, so the config
	* source is transparent. Example:
	* <pre>
	* var enter = new Enter();
	* var cp = new ConfigProperties();
	* cp.setProperty("Source.Database.DataSource", "JsPlus_Ds_0.properties");
	* cp.setProperty("Source.Database.Schema", "PUBLIC");
	* cp.setProperty("Table.Inserter.Period", "5000");
	* enter.configure(cp);
	* </pre>
	* @param props - the DBSync configuration (a null value resets to an empty config)
	* */
	public void configure(ConfigProperties props) {
		ENTER_PORPS = props == null ? new ConfigProperties() : props;
		log.mark("Configured DBSync from JS: {}",ENTER_PORPS);
	}

	/**
	* Starts a DBSync round from the given config file name - the programmatic entry
	* (callable from JS or another main). Loads the enter config from
	* <ROOT_PATH>/config, builds Source/Target databases (with this instance's config)
	* and pushes tables; when the "Reverse" flag is set the sync also runs
	* target-to-source. Every started database is tracked so stop() can terminate
	* all its workers; calling start() again first stops the previous run
	* (idempotent).
	* @param enterCnf - enter config file name
	* */
	public void start(String enterCnf) {
		try {
		    stop();
			load(enterCnf);
			boolean reverse = ENTER_PORPS.getBoolean("Reverse",false);
			Database source = new Database("Source", ENTER_PORPS);
			Database target = new Database("Target", ENTER_PORPS);
			started.add(source);
			started.add(target);
			source.syncTo(target);
			if (reverse) {
				Database rSource = new Database("Source", ENTER_PORPS);
				Database rTarget = new Database("Target", ENTER_PORPS);
				started.add(rSource);
				started.add(rTarget);
				rTarget.syncTo(rSource);
			}
			log.mark("Enter started: {}",enterCnf);
		} catch (Exception e) {
			log.error(e.getMessage(),e);
			throw new RuntimeException("DBSync start failed: " + e.getMessage(), e);
		}
	}

	/**
	* Stops every sync worker started by start(...) on this Enter instance
	* (both directions when Reverse was used). Safe to call when nothing is
	* running; repeated calls are no-ops.
	* */
	public void stop() {
		for (Database db : started) {
			if (db != null) {
				db.stopSync();
			}
		}
		started.clear();
		log.mark("Enter stopped");
	}

}