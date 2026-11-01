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

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;

/**
* Script-engine facade for thread-confined {@link TextScript} rendering: a fixed
* pool of {@link ScriptConsole} instances (one per concurrent renderer) with a
* master console that owns the data source, script-engine init and JVM-wide
* settings. Mirrors JsPlus-Tomcat {@code TomcatScriptConsole}.
* <p>
* Every thread acquires a pooled console via {@link #getConsole()}, renders
* TextScript templates through {@link #newTextScript(String)} /
* {@link #render(String, Map)} and returns it via {@link #freed()} (delayed by
* {@code freedDelayMs} on a shared daemon scheduler). The pool size is
* configurable and grows on demand up to {@code poolSize}.
* <p>
* Thread model: GraalJS allows at most one thread inside a context at a time.
* The pool hands each console to at most one thread at a time (removed from the
* idle pool while in use), so concurrent renderers never enter the same
* GraalVM context simultaneously - each TextScript is bound to the acquiring
* thread's own context via {@link TextScript#setContext(JsPlusContext)}.
*/
public class TextScriptConsole {

	private static final Logger log = LoggerFactory.getLogger(TextScriptConsole.class);

	private static final Map<Integer, ScriptConsole> CONSOLES_POOL = new ConcurrentHashMap<Integer, ScriptConsole>();
	private static final Map<Long, ScriptConsole> CONSOLES_USING_MARK = new ConcurrentHashMap<Long, ScriptConsole>();
	private static final Map<Long, Integer> CONSOLES_THREAD_MARK = new ConcurrentHashMap<Long, Integer>();

	/** Shared daemon scheduler for delayed console return (freed): fixed-size,
	 *  non-leaking (never grows per call), and never blocks JVM exit. */
	private static final ScheduledExecutorService FREED_SCHEDULER = Executors.newScheduledThreadPool(
			Math.max(2, Runtime.getRuntime().availableProcessors()),
			r -> {
				Thread t = new Thread(r, "jsplus-textscript-console-freed");
				t.setDaemon(true);
				return t;
			});

	private static int poolSize = 100;

	private static int currentPoolSize = 0;

	private static long freedDelayMs = 10L;

	private static String defaultEngineName = "graalvm.js";

	private static String engineName = "graalvm.js";

	private static ScriptConsole MasterConsole = null;

	/** Whether the master console (and thus the script engine) is initialized. */
	public synchronized static boolean isLoadedScriptEngine() {
		return MasterConsole != null;
	}

	/**
	* Initializes the master console with the configured engine and pre-warms
	* the console pool (at least 10 consoles, growing to {@code poolSize}).
	*/
	public synchronized static void initTextScriptConsole() throws Exception {
		initTextScriptConsole(getEngineName());
	}

	/**
	* Initializes the master console with the given engine name, then builds the
	* initial console pool. No-op when already initialized.
	*
	* @param initEngineName engine id (e.g. {@code graalvm.js}); blank falls back
	*                        to the default engine
	*/
	public synchronized static void initTextScriptConsole(String initEngineName) throws Exception {
		if (MasterConsole == null) {
			ConfigProperties consoleVariables = new ConfigProperties();
			if (!CommonTools.isBlank(initEngineName)) {
				engineName = initEngineName;
			} else if (CommonTools.isBlank(engineName)) {
				engineName = defaultEngineName;
			}
			consoleVariables.putAndReturnSelf("ScriptEngine", engineName);
			MasterConsole = new ScriptConsole();
			MasterConsole.setUseScriptEnv();
			MasterConsole.setConsoleVariables(consoleVariables);
			MasterConsole.initScriptEngineManager();
			currentPoolSize = getPoolSize();

			if (currentPoolSize < 10)
				currentPoolSize = 10;

			for (int i = 0; i < currentPoolSize; i++) {
				CONSOLES_POOL.put(i, MasterConsole.newBuilder());
			}
		}
	}

	/**
	* Acquires a console for the current thread (one console per thread, tracked
	* by thread id). Grows the pool when {@code poolSize} was raised; throws when
	* every console is in use.
	*
	* @return the console bound to the current thread
	* @throws Exception when the pool is exhausted
	*/
	private synchronized static ScriptConsole getConsole() throws Exception {
		int newPoolSize = getPoolSize();
		if (newPoolSize > currentPoolSize) {
			Logger.systemMark(TextScriptConsole.class, "TextScriptConsole changed pool size to '{}'.", newPoolSize);
			for (int i = currentPoolSize; i < newPoolSize; i++) {
				CONSOLES_POOL.put(i, MasterConsole.newBuilder());
			}
			currentPoolSize = newPoolSize;
		}
		long tid = Thread.currentThread().getId();
		Integer threadMark = CONSOLES_THREAD_MARK.get(tid);
		if (threadMark == null) {
			List<Integer> list = list();
			int idleSize = list.size();
			double usage = usage();
			if (usage >= 0.8D) {
				log.warn("The console pool usage has reached {} ({}/{}).", "80%", (currentPoolSize - idleSize), currentPoolSize);
			}
			if (idleSize > 0) {
				Integer first = list.get(0);
				ScriptConsole sc = CONSOLES_POOL.get(first);
				CONSOLES_USING_MARK.put(tid, sc);
				CONSOLES_THREAD_MARK.put(tid, first);
				CONSOLES_POOL.remove(first);
			} else {
				throw new Exception("Full Console Pool.");
			}
		}
		return CONSOLES_USING_MARK.get(tid);
	}

	/** Pool usage ratio (0.0 = all idle, 1.0 = all in use). */
	public synchronized static double usage() {
		int idleSize = list().size();
		double usage = (currentPoolSize - idleSize) / (currentPoolSize * 1.0D);
		return usage;
	}

	/** Idle console indices, ascending. */
	private synchronized static List<Integer> list() {
		List<Integer> list = new ArrayList<Integer>();
		List<Integer> klist = CONSOLES_POOL.keySet()
				.stream()
				.sorted()
				.collect(Collectors.toList());
		for (Object k : klist) {
			list.add((Integer) k);
		}
		return list;
	}

	/**
	* Returns the current thread's console to the pool after
	* {@code freedDelayMs}; no-op when the thread never held a console.
	*/
	public synchronized static void freed() {
		try {
			long tid = Thread.currentThread().getId();
			if (CONSOLES_USING_MARK.get(tid) != null) {
				clear();
			}
			final ScriptConsole sc = CONSOLES_USING_MARK.get(tid);
			if (sc != null) {
				final Integer useIndex = CONSOLES_THREAD_MARK.get(tid);
				CONSOLES_THREAD_MARK.remove(tid);
				CONSOLES_USING_MARK.remove(tid);
				long delayMs = getFreedDelayMs();
				FREED_SCHEDULER.schedule(new Runnable() {
					@Override
					public void run() {
						try {
							CONSOLES_POOL.put(useIndex, sc);
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

	/** Puts a value into the current thread's console context. */
	public static void put(Object params) throws Exception {
		getConsole().getContext().put(params);
	}

	/** Clears the current thread's console context. */
	public static void clear() throws Exception {
		getConsole().getContext().remove();
	}

	/** Reads the current thread's console context. */
	public static Object get() throws Exception {
		return getConsole().getContext().get();
	}

	/**
	* Returns the current thread's pooled console context (acquiring a console
	* if this thread never held one). Used by {@link TextScript#useEngine(String)}
	* so the DEFAULT engine name renders on a thread-confined pooled context -
	* real parallelism, no cross-thread access. Initializes the pool on first
	* use when {@link #initTextScriptConsole(String)} was never called.
	*
	* @return the context bound to the current thread
	* @throws Exception when the pool is exhausted
	*/
	public static JsPlusContext getThreadContext() throws Exception {
		if (!isLoadedScriptEngine()) {
			initTextScriptConsole(defaultEngineName);
		}
		return getConsole().getContext();
	}

	/**
	* Creates a TextScript bound to the current thread's pooled console context
	* (thread-confined - safe for concurrent rendering). The caller must return
	* the console via {@link #freed()} after rendering.
	*
	* @param template the TextScript template source
	* @return a TextScript bound to this thread's own context
	* @throws Exception when the pool is exhausted or the template is invalid
	*/
	public static TextScript newTextScript(String template) throws Exception {
		TextScript ts = new TextScript(template);
		ts.setContext(getConsole().getContext());
		return ts;
	}

	/**
	* Renders a TextScript template on the current thread's pooled console
	* context in one step: binds a fresh TextScript to the acquired console,
	* injects the params, parses, and returns the console to the pool.
	*
	* @param template the TextScript template source
	* @param params the params injected via addParams (may be null)
	* @return the rendered output
	* @throws Exception when the pool is exhausted or rendering fails
	*/
	public static String render(String template, Map<String,Object> params) throws Exception {
		try {
			TextScript ts = newTextScript(template);
			if (params != null) {
				ts.addParams(params);
			}
			return ts.parse();
		} finally {
			freed();
		}
	}

	/** The master console - owns the data source and engine init (singleton). */
	public static ScriptConsole getMasterConsole() {
		return MasterConsole;
	}

	/** Sets how long a freed console stays out of the pool before return (ms). */
	public static void setFreedDelayMs(long freedDelayMs){
		TextScriptConsole.freedDelayMs = freedDelayMs;
	}

	/** Returns the freed-console return delay (ms). */
	public static long getFreedDelayMs(){
		return TextScriptConsole.freedDelayMs;
	}

	/** Sets the maximum console pool size (grows on demand up to this). */
	public static void setPoolSize(int poolSize) {
		TextScriptConsole.poolSize = poolSize;
	}

	/** Returns the configured maximum console pool size. */
	public static int getPoolSize() {
		return poolSize;
	}

	/** Actual number of consoles currently in the pool (vs the configured
	 *  poolSize) - a hard number for tests to confirm the pool returns to
	 *  full after traffic. */
	public static int getConsolePoolSize() {
		return CONSOLES_POOL.size();
	}

	/** Sets the script-engine id used to init the master console. */
	public static void setEngineName(String engineName) {
		TextScriptConsole.engineName = engineName;
	}

	/** Returns the script-engine id (e.g. {@code graalvm.js}). */
	public static String getEngineName() {
		return TextScriptConsole.engineName;
	}

}
