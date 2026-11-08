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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;

/**
* Thread-confined {@link ScriptConsole} pool: works around the fact that a
* GraalVM {@code Context} cannot be entered by two threads at the same time.
* <p>
* A {@link ScriptConsole} is a single-thread console - {@code evalScript}
* enters its context under {@code synchronized(context)}. Sharing one console
* between concurrent jobs therefore serialises them, and any nested entry
* fails with a GraalVM multi-threaded-access error. This pool hands every
* thread its own console (each built by {@link ScriptConsole#newBuilder(boolean)},
* i.e. its own GraalVM context), so parallel jobs really run in parallel.
* <p>
* Thread rule: a console may not be <b>entered concurrently</b>. It is safe
* for another thread to use it later in sequence, which is why a returned
* console goes back to the idle set and may be acquired by any thread.
* <p>
* Usage:
* <pre>
*   Object r = ScriptConsolePool.getInstance().evalScriptOn("1 + 1");
* </pre>
* or, when several calls must share one context (globals, console variables):
* <pre>
*   ScriptConsole sc = ScriptConsolePool.getInstance().getConsole();
*   try { sc.evalScript("var x = 1;"); sc.evalScript("x + 1"); }
*   finally { ScriptConsolePool.getInstance().freed(); }
* </pre>
* {@code freed()} must always run in a {@code finally} block, otherwise that
* thread keeps its console forever and the pool eventually runs dry.
*/
public class ScriptConsolePool {

	private static final Logger log = LoggerFactory.getLogger(ScriptConsolePool.class);

	/** External pool-size key: env var first, then system property. */
	private static final String POOL_SIZE_KEY = "jsplus.scriptconsole.poolsize";

	/** External engine-name key: env var first, then system property. */
	private static final String ENGINE_NAME_KEY = "jsplus.scriptconsole.engine";

	/** External allow-all-access key: env var first, then system property. */
	private static final String ALLOW_ALL_ACCESS_KEY = "jsplus.scriptconsole.allowallaccess";

	/** Shared daemon scheduler for delayed console return (freed): fixed-size,
	 *  never grows per call, and never blocks JVM exit. Every pool instance
	 *  posts its returns here; the scheduled task captures the owning pool so
	 *  consoles always come back to their own pool. */
	private static final ScheduledExecutorService FREED_SCHEDULER = Executors.newScheduledThreadPool(
			Math.max(2, Runtime.getRuntime().availableProcessors()),
			r -> {
				Thread t = new Thread(r, "jsplus-scriptconsole-freed");
				t.setDaemon(true);
				return t;
			});

	/** The shared default pool. */
	private static final ScriptConsolePool DEFAULT = new ScriptConsolePool();

	/** Sequence for the async worker thread names. */
	private static final AtomicLong ASYNC_SEQ = new AtomicLong();

	// ---- instance pool state (each ScriptConsolePool is one independent pool) ----

	private final Map<Integer, ScriptConsole> consolesPool = new ConcurrentHashMap<Integer, ScriptConsole>();
	private final Map<Long, ScriptConsole> consolesUsingMark = new ConcurrentHashMap<Long, ScriptConsole>();
	private final Map<Long, Integer> consolesThreadMark = new ConcurrentHashMap<Long, Integer>();

	private int poolSize = 100;

	private int currentPoolSize = 0;

	private long freedDelayMs = 10L;

	private final String defaultEngineName = "graalvm.js";

	private String engineName = "graalvm.js";

	private boolean allowAllAccess = true;

	private ScriptConsole masterConsole = null;

	/**
	* Constructs an independent pool.
	* */
	public ScriptConsolePool() {
		super();
	}

	/**
	* Returns the shared default pool.
	* @return the default pool
	* */
	public static ScriptConsolePool getInstance() {
		return DEFAULT;
	}

	/**
	* Whether this pool's master console (and thus its script engine) is initialized.
	* @return true when initialized
	* */
	public synchronized boolean isEngineLoaded() {
		return masterConsole != null;
	}

	/** Applies the external engine name when present; blank values are rejected
	 *  and the current name is kept. */
	private void applyExternalEngineName() {
		try {
			String v = System.getenv(ENGINE_NAME_KEY) == null
					? System.getProperty(ENGINE_NAME_KEY)
					: System.getenv(ENGINE_NAME_KEY);
			if (v != null && !v.trim().isEmpty()) {
				engineName = v.trim();
				Logger.systemMark(ScriptConsolePool.class,
						"ScriptConsolePool engine name from '{}' = '{}'.", ENGINE_NAME_KEY, engineName);
			}
		} catch (Exception e) {
			log.warn("Invalid {} value - keeping engine name {}.", ENGINE_NAME_KEY, engineName);
		}
	}

	/** Applies the external pool size when present; malformed or non-positive
	 *  values are rejected with a warning and the current value is kept. */
	private void applyExternalPoolSize() {
		try {
			String v = System.getenv(POOL_SIZE_KEY) == null
					? System.getProperty(POOL_SIZE_KEY)
					: System.getenv(POOL_SIZE_KEY);
			if (v != null && !v.trim().isEmpty()) {
				int n = Integer.parseInt(v.trim());
				if (n > 0) {
					poolSize = n;
					Logger.systemMark(ScriptConsolePool.class,
							"ScriptConsolePool pool size from '{}' = '{}'.", POOL_SIZE_KEY, n);
				} else {
					log.warn("Ignoring non-positive {} value '{}' - keeping pool size {}.",
							POOL_SIZE_KEY, v, poolSize);
				}
			}
		} catch (NumberFormatException e) {
			log.warn("Invalid {} value - keeping pool size {}.", POOL_SIZE_KEY, poolSize);
		}
	}

	/** Applies the external allow-all-access flag when present; malformed values
	 *  are rejected with a warning and the current value is kept. */
	private void applyExternalAllowAllAccess() {
		try {
			String v = System.getenv(ALLOW_ALL_ACCESS_KEY) == null
					? System.getProperty(ALLOW_ALL_ACCESS_KEY)
					: System.getenv(ALLOW_ALL_ACCESS_KEY);
			if (v != null && !v.trim().isEmpty()) {
				boolean b = Boolean.parseBoolean(v.trim());
				allowAllAccess = b;
				Logger.systemMark(ScriptConsolePool.class,
						"ScriptConsolePool allowAllAccess from '{}' = '{}'.", ALLOW_ALL_ACCESS_KEY, b);
			}
		} catch (Exception e) {
			log.warn("Invalid {} value - keeping allowAllAccess {}.", ALLOW_ALL_ACCESS_KEY, allowAllAccess);
		}
	}

	/**
	* Initializes this pool with the configured engine and pre-warms the console
	* pool (at least 10 consoles, growing to {@code poolSize}).
	* @throws Exception when the engine cannot be initialized
	* */
	public synchronized void init() throws Exception {
		init(getEngine());
	}

	/**
	* Initializes this pool with the given engine name, then builds the initial
	* console pool. No-op when already initialized.
	* @param initEngineName engine id (e.g. {@code graalvm.js}); blank falls back to the default engine
	* @throws Exception when the engine cannot be initialized
	* */
	public synchronized void init(String initEngineName) throws Exception {
		if (masterConsole != null) {
			return;
		}
		ConfigProperties consoleVariables = new ConfigProperties();
		if (!CommonTools.isBlank(initEngineName)) {
			engineName = initEngineName;
		} else if (CommonTools.isBlank(engineName)) {
			engineName = defaultEngineName;
		}
		applyExternalPoolSize();
		applyExternalEngineName();
		applyExternalAllowAllAccess();
		consoleVariables.putAndReturnSelf("ScriptEngine", engineName);
		ScriptConsole mc = new ScriptConsole();
		mc.setUseScriptEnv();
		mc.setConsoleVariables(consoleVariables);
		mc.initScriptEngineManager(allowAllAccess);
		masterConsole = mc;
		currentPoolSize = getMaxPoolSize();
		if (currentPoolSize < 10)
			currentPoolSize = 10;

		for (int i = 0; i < currentPoolSize; i++) {
			consolesPool.put(i, masterConsole.newBuilder(allowAllAccess));
		}
	}

	/**
	* Acquires a console for the current thread (one console per thread, tracked
	* by thread id). Grows the pool when {@code poolSize} was raised; throws when
	* every console is in use. Initializes this pool on first use.
	*
	* @return the console bound to the current thread
	* @throws Exception when the pool is exhausted
	* */
	public synchronized ScriptConsole getConsole() throws Exception {
		if (!isEngineLoaded()) {
			init();
		}
		int newPoolSize = getMaxPoolSize();
		if (newPoolSize > currentPoolSize) {
			Logger.systemMark(ScriptConsolePool.class, "ScriptConsolePool changed pool size to '{}'.", newPoolSize);
			for (int i = currentPoolSize; i < newPoolSize; i++) {
				consolesPool.put(i, masterConsole.newBuilder(allowAllAccess));
			}
			currentPoolSize = newPoolSize;
		}
		long tid = Thread.currentThread().getId();
		Integer threadMark = consolesThreadMark.get(tid);
		if (threadMark == null) {
			List<Integer> list = list();
			int idleSize = list.size();
			double usage = usage();
			if (usage >= 0.8D) {
				log.warn("The script console pool usage has reached {} ({}/{}).", "80%",
						(currentPoolSize - idleSize), currentPoolSize);
			}
			if (idleSize > 0) {
				Integer first = list.get(0);
				ScriptConsole sc = consolesPool.get(first);
				consolesUsingMark.put(tid, sc);
				consolesThreadMark.put(tid, first);
				consolesPool.remove(first);
			} else {
				throw new Exception("Full ScriptConsole Pool.");
			}
		}
		return consolesUsingMark.get(tid);
	}

	/** Pool usage ratio (0.0 = all idle, 1.0 = all in use). */
	public synchronized double usage() {
		int idleSize = list().size();
		double usage = (currentPoolSize - idleSize) / (currentPoolSize * 1.0D);
		return usage;
	}

	/** Idle console indices, ascending. */
	private synchronized List<Integer> list() {
		List<Integer> list = new ArrayList<Integer>();
		List<Integer> klist = consolesPool.keySet()
				.stream()
				.sorted()
				.collect(Collectors.toList());
		for (Object k : klist) {
			list.add((Integer) k);
		}
		return list;
	}

	/**
	* Returns the current thread's console to this pool after
	* {@code freedDelayMs}; no-op when the thread never held a console.
	* */
	public synchronized void freed() {
		try {
			long tid = Thread.currentThread().getId();
			if (consolesUsingMark.get(tid) != null) {
				clearValue();
			}
			final ScriptConsole sc = consolesUsingMark.get(tid);
			if (sc != null) {
				final Integer useIndex = consolesThreadMark.get(tid);
				consolesThreadMark.remove(tid);
				consolesUsingMark.remove(tid);
				long delayMs = getFreedDelayMs();
				FREED_SCHEDULER.schedule(new Runnable() {
					@Override
					public void run() {
						try {
							consolesPool.put(useIndex, sc);
						} catch (Exception e) {
							log.info(e.getMessage(), e);
						}
					}
				}, delayMs, TimeUnit.MILLISECONDS);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
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
	* Runs a script file on a background thread and returns immediately: the
	* worker borrows its own console, evaluates the file, and returns the
	* console in a {@code finally} block. Use this for long-running
	* synchronous scripts (backtests: a {@code while} loop inside one call)
	* where the caller must not be blocked.
	* <p>
	* Parameters cross the thread boundary as a context value: the worker puts
	* {@code params} into its console context before evaluating, so the script
	* reads them with {@code Console.getContext().get()}.
	*
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

	/** Puts a value into the current thread's console context. */
	public void putValue(Object params) throws Exception {
		getConsole().getContext().put(params);
	}

	/** Clears the current thread's console context. */
	public void clearValue() throws Exception {
		getConsole().getContext().remove();
	}

	/** Reads the current thread's console context. */
	public Object getValue() throws Exception {
		return getConsole().getContext().get();
	}

	/** This pool's master console - owns the engine init and template settings. */
	public ScriptConsole getMasterConsole() {
		return masterConsole;
	}

	/** Sets how long a freed console stays out of this pool before return (ms). */
	public void setFreedDelayMs(long freedDelayMs) {
		this.freedDelayMs = freedDelayMs;
	}

	/** Returns the freed-console return delay (ms). */
	public long getFreedDelayMs() {
		return this.freedDelayMs;
	}

	/** Sets this pool's maximum console size (grows on demand up to this). */
	public void setMaxPoolSize(int poolSize) {
		this.poolSize = poolSize;
	}

	/** Returns this pool's configured maximum console size. */
	public int getMaxPoolSize() {
		return poolSize;
	}

	/** Actual number of consoles currently idle in this pool. */
	public int idleCount() {
		return consolesPool.size();
	}

	/** Sets the script-engine id used to init this pool's master console. */
	public void setEngine(String engineName) {
		this.engineName = engineName;
	}

	/** Returns the script-engine id (e.g. {@code graalvm.js}). */
	public String getEngine() {
		return this.engineName;
	}

	/** Sets the allow-all-access security flag applied when the pool initialises. */
	public void setAllowAllAccess(boolean allowAllAccess) {
		this.allowAllAccess = allowAllAccess;
	}

	/** Returns this pool's allow-all-access security flag (default {@code true}). */
	public boolean isAllowAllAccess() {
		return allowAllAccess;
	}

}
