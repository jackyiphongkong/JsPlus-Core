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

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

import com.killcoding.log.LoggerFactory;
import com.killcoding.tool.ConfigProperties;

/**
* Thread-confined {@link ScriptConsole} pool used by the JS runtime: a fixed
* pool of consoles (one per concurrent script) plus a master console that owns
* the engine init and JVM-wide settings.
* <p>
* The pool mechanics (acquisition, delayed return, external config, sizing) are
* shared with {@link TextScriptConsole} through {@link AbstractConsolePool};
* this class adds the {@code runAsync} background-script API and keeps its own
* external-config prefix ({@code jsplus.scriptconsole.*}) and its own console
* set. A {@code ScriptConsolePool} and a {@code TextScriptConsole} are still
* two independent pools.
* <p>
* Typical use, always returning the console in a {@code finally} block:
* <pre>
*   try { sc.evalScript("var x = 1;"); sc.evalScript("x + 1"); }
*   finally { ScriptConsolePool.getInstance().freed(); }
* </pre>
* {@code freed()} must always run in a {@code finally} block, otherwise that
* thread keeps its console forever and the pool eventually runs dry.
*/
public class ScriptConsolePool extends AbstractConsolePool {

	/** Shared default pool backing the static-style access pattern. */
	private static final ScriptConsolePool DEFAULT = new ScriptConsolePool();

	private static final AtomicLong ASYNC_SEQ = new AtomicLong();

	/**
	* Constructs the pool. External configuration
	* ({@code jsplus.scriptconsole.poolsize} / {@code .engine} /
	* {@code .allowallaccess}) is applied when this pool initialises.
	*/
	public ScriptConsolePool() {
		super("jsplus.scriptconsole", "ScriptConsolePool", "Full ScriptConsole Pool.",
				LoggerFactory.getLogger(ScriptConsolePool.class), true, true);
	}

	/**
	* Constructs an independent pool with a fixed allow-all-access policy.
	* The external allow-all-access configuration
	* ({@code jsplus.scriptconsole.allowallaccess}) never overrides pools built
	* this way - the policy is fixed at construction time, the same contract
	* {@link TextScriptConsole#TextScriptConsole(boolean)} has.
	*
	* @param allowAllAccess the fixed security policy for every pooled console
	*/
	public ScriptConsolePool(boolean allowAllAccess) {
		super("jsplus.scriptconsole", "ScriptConsolePool", "Full ScriptConsole Pool.",
				LoggerFactory.getLogger(ScriptConsolePool.class), allowAllAccess, false);
	}

	/** Returns the shared default pool. */
	public static ScriptConsolePool getInstance() {
		return DEFAULT;
	}

	/**
	* Acquires a console for the current thread, initializing this pool on first
	* use. One console per thread, tracked by thread id; grows the pool when
	* {@code poolSize} was raised; throws when every console is in use.
	*
	* @return the console bound to the current thread
	* @throws Exception when the pool is exhausted
	* */
	@Override
	public synchronized ScriptConsole getConsole() throws Exception {
		if (!isEngineLoaded()) {
			init();
		}
		return super.getConsole();
	}

	/**
	* Runs a script on the current thread's console and returns it in a
	* {@code finally} block.
	* @param script - the script source
	* @return the script result
	* @throws Exception when the pool is exhausted or the script fails
	* */
	public Object evalScriptOn(String script) throws Exception {
		ScriptConsole sc = getConsole();
		try {
			return sc.evalScript(script);
		} finally {
			freed();
		}
	}

	/**
	* Runs a script file on the current thread's console and returns it in a
	* {@code finally} block.
	* @param scriptPath - the script path
	* @return the script result
	* @throws Exception when the pool is exhausted or the script fails
	* */
	public Object evalFileOn(String scriptPath) throws Exception {
		ScriptConsole sc = getConsole();
		try {
			return sc.eval(scriptPath);
		} finally {
			freed();
		}
	}

	/**
	* Runs a script file on the current thread's console and returns it in a
	* {@code finally} block.
	* @param scriptFile - the script file
	* @return the script result
	* @throws Exception when the pool is exhausted or the script fails
	* */
	public Object evalFileOn(File scriptFile) throws Exception {
		ScriptConsole sc = getConsole();
		try {
			return sc.eval(scriptFile);
		} finally {
			freed();
		}
	}

	/**
	* Runs a script file on a background thread and returns immediately.
	* @param scriptPath - the script path
	* @return the background worker thread (already started)
	* @throws Exception when the pool cannot be initialized
	* */
	public Thread runAsync(String scriptPath) throws Exception {
		return runAsync(new File(scriptPath), null);
	}

	/**
	* Runs a script file on a background thread and returns immediately.
	* @param scriptFile - the script file
	* @return the background worker thread (already started)
	* @throws Exception when the pool cannot be initialized
	* */
	public Thread runAsync(File scriptFile) throws Exception {
		return runAsync(scriptFile, null);
	}

	/**
	* Runs a script file on a background thread with a context parameter object
	* and returns immediately.
	* <p>
	* Parameters cross the thread boundary as a context value: the worker puts
	* {@code params} into its console context before evaluating, so the script
	* reads them with {@code Console.getContext().get()}.
	*
	* @param scriptFile - the script file
	* @param params - context value put before the script runs (may be null)
	* @return the background worker thread (already started)
	* @throws Exception when the pool cannot be initialized
	* */
	public Thread runAsync(final File scriptFile, final ConfigProperties params) throws Exception {
		if (scriptFile == null) {
			throw new IllegalArgumentException("scriptFile must not be null.");
		}
		init();
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				ScriptConsole sc = null;
				try {
					sc = getConsole();
					if (params != null) {
						sc.getContext().put(params);
					}
					sc.eval(scriptFile);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				} finally {
					try {
						if (sc != null) {
							freed();
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}
			}
		}, "jsplus-scriptconsole-async-" + ASYNC_SEQ.incrementAndGet());
		t.setDaemon(false);
		t.start();
		return t;
	}

}
