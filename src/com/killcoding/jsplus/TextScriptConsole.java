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
* Thread-confined {@link TextScript} console pool: a fixed pool of
* {@link ScriptConsole} instances (one per concurrent renderer) with a master
* console that owns the data source, script-engine init and JVM-wide settings.
* Mirrors JsPlus-Tomcat {@code TomcatScriptConsole}.
* <p>
* Architecture: <b>this class is the pool itself</b> - each instance is one
* independent pool, and the {@code allowAllAccess} security policy is fixed per
* pool instance at construction time (constructor argument, not runtime
* configuration). Two pre-built named instances express the two policies:
* <ul>
* <li>{@link AllowAllAccessPool#getInstance()} - permissive profile (GraalVM
*     host/Java access allowed, {@code allowAllAccess=true});</li>
* <li>{@link NotAllowAccessPool#getInstance()} - restricted profile (host
*     access closed, {@code allowAllAccess=false}).</li>
* </ul>
* A shared default pool is exposed through the static API (this class), and
* {@link TextScript} constructors let the caller pick a pool explicitly:
* {@code new TextScript(template, true)} (permissive) /
* {@code new TextScript(template, false)} (restricted) - the boolean policy
* selects the named instance - or {@code new TextScript(template)} for the
* shared default pool. Any caller may also build further pools directly:
* {@code new TextScriptConsole(false)}.
* <p>
* Every thread acquires a pooled console via {@link #getConsole()}, renders
* TextScript templates through {@link #newTextScript(String)} /
* {@link #renderOn(String, Map)} and returns it via {@link #freed()} (delayed
* by {@code freedDelayMs} on a shared daemon scheduler). The pool size is
* configurable and grows on demand up to {@code poolSize}.
* <p>
* External-config parameterisation (same contract as JsPlus-Tomcat
* {@code TomcatScriptConsole}): the system property / environment-variable
* values below are applied when the <b>default pool</b> initialises and take
* precedence over the code-level defaults/arguments:
* <ul>
* <li>pool size: {@code JSPLUS_TEXSCRIPT_POOLSIZE} env (read first) or
*     {@code -Djsplus.textscript.poolsize=n};</li>
* <li>engine name: {@code JSPLUS_TEXSCRIPT_ENGINE} env or
*     {@code -Djsplus.textscript.engine=...};</li>
* <li>allow-all-access: {@code JSPLUS_TEXSCRIPT_ALLOWALLACCESS} env or
*     {@code -Djsplus.textscript.allowallaccess=true|false}.</li>
* </ul>
* Pre-built strategy pools ({@link AllowAllAccessPool} /
* {@link NotAllowAccessPool}) and any pool built via
* {@link #TextScriptConsole(boolean)} are code-constructed with a fixed policy
* and are NOT overridden by the external allow-all-access value.
* <p>
* Thread model: GraalJS allows at most one thread inside a context at a time.
* The pool hands each console to at most one thread at a time (removed from the
* idle pool while in use), so concurrent renderers never enter the same
* GraalVM context simultaneously - each TextScript is bound to the acquiring
* thread's own context via {@link TextScript#setContext(JsPlusContext)}.
*/
public class TextScriptConsole {

	private static final Logger log = LoggerFactory.getLogger(TextScriptConsole.class);

	/** External pool-size key: read as env var first, then as system property
	 *  (Core JS_PLUS_PATH-style lookup). Value is a positive integer. */
	private static final String POOL_SIZE_KEY = "jsplus.textscript.poolsize";

	/** External engine-name key: read as env var first, then as system
	 *  property (same standard as {@link #POOL_SIZE_KEY}). Applied at pool
	 *  init and takes precedence over code-level defaults/arguments. */
	private static final String ENGINE_NAME_KEY = "jsplus.textscript.engine";

	/** External allow-all-access key: read as env var first, then as system
	 *  property (same standard as {@link #POOL_SIZE_KEY}). Applied when the
	 *  default pool initialises; subclass pools fix their own policy in their
	 *  constructor and are never overridden by this value. */
	private static final String ALLOW_ALL_ACCESS_KEY = "jsplus.textscript.allowallaccess";

	/** Shared daemon scheduler for delayed console return (freed): fixed-size,
	 *  non-leaking (never grows per call), and never blocks JVM exit. Every
	 *  pool instance posts its returns here; the scheduled task captures the
	 *  owning pool so consoles always come back to their own pool. */
	private static final ScheduledExecutorService FREED_SCHEDULER = Executors.newScheduledThreadPool(
			Math.max(2, Runtime.getRuntime().availableProcessors()),
			r -> {
				Thread t = new Thread(r, "jsplus-textscript-console-freed");
				t.setDaemon(true);
				return t;
			});

	/** The shared default pool backing the static API (external config applies
	 *  to this one). TextScript() without an explicit pool uses this pool. */
	private static final TextScriptConsole DEFAULT = new TextScriptConsole();

	// ---- instance pool state (each TextScriptConsole is one independent pool) ----

	private final Map<Integer, ScriptConsole> consolesPool = new ConcurrentHashMap<Integer, ScriptConsole>();
	private final Map<Long, ScriptConsole> consolesUsingMark = new ConcurrentHashMap<Long, ScriptConsole>();
	private final Map<Long, Integer> consolesThreadMark = new ConcurrentHashMap<Long, Integer>();

	private int poolSize = 100;

	private int currentPoolSize = 0;

	private long freedDelayMs = 10L;

	private final String defaultEngineName = "graalvm.js";

	private String engineName = "graalvm.js";

	private boolean allowAllAccess = true;

	/** true for code-constructed strategy pools: the security policy is fixed
	 *  at construction and cannot be changed afterwards. */
	private final boolean allowAllAccessLocked;

	private ScriptConsole MasterConsole = null;

	/**
	* Constructs the shared default pool. The external configuration
	* (env / system property) applies when this pool initialises.
	*/
	public TextScriptConsole() {
		this(true, true);
	}

	/**
	* Constructs an independent pool with a fixed allow-all-access policy.
	* The external allow-all-access configuration never overrides pools built
	* this way - the policy is fixed at construction time. See also the
	* pre-built named instances {@link AllowAllAccessPool#getInstance()} and
	* {@link NotAllowAccessPool#getInstance()}.
	*
	* @param allowAllAccess the fixed security policy for every pooled console
	*/
	public TextScriptConsole(boolean allowAllAccess) {
		this(allowAllAccess, false);
	}

	private TextScriptConsole(boolean allowAllAccess, boolean applyExternalConfig) {
		this.allowAllAccess = allowAllAccess;
		this.allowAllAccessLocked = !applyExternalConfig;
	}

	/** Returns the shared default pool (backs the static API). */
	public static TextScriptConsole getDefaultPool() {
		return DEFAULT;
	}

	/** Whether this pool's master console (and thus its script engine) is
	 *  initialized. */
	public synchronized boolean isEngineLoaded() {
		return MasterConsole != null;
	}

	/** Applies the external engine name ({@code JSPLUS_TEXSCRIPT_ENGINE} env
	 *  var or {@code -Djsplus.textscript.engine} system property) when present;
	 *  blank values are rejected and the current name is kept. Called only
	 *  while the pool initialises, after the code-level default/argument has
	 *  been applied, so the external deployment config wins. */
	private void applyExternalEngineName() {
		try {
			String v = System.getenv(ENGINE_NAME_KEY) == null
					? System.getProperty(ENGINE_NAME_KEY)
					: System.getenv(ENGINE_NAME_KEY);
			if (v != null && !v.trim().isEmpty()) {
				engineName = v.trim();
				Logger.systemMark(TextScriptConsole.class,
						"TextScriptConsole engine name from '{}' = '{}'.", ENGINE_NAME_KEY, engineName);
			}
		} catch (Exception e) {
			log.warn("Invalid {} value - keeping engine name {}.", ENGINE_NAME_KEY, engineName);
		}
	}

	/** Applies the external pool size ({@code JSPLUS_TEXSCRIPT_POOLSIZE} env
	 *  var or {@code -Djsplus.textscript.poolsize} system property) when
	 *  present; malformed or non-positive values are rejected with a warning
	 *  and the configured/current value is kept. Called only while the pool
	 *  initialises, so it never races {@link #setMaxPoolSize(int)}. */
	private void applyExternalPoolSize() {
		try {
			String v = System.getenv(POOL_SIZE_KEY) == null
					? System.getProperty(POOL_SIZE_KEY)
					: System.getenv(POOL_SIZE_KEY);
			if (v != null && !v.trim().isEmpty()) {
				int n = Integer.parseInt(v.trim());
				if (n > 0) {
					poolSize = n;
					Logger.systemMark(TextScriptConsole.class,
							"TextScriptConsole pool size from '{}' = '{}'.", POOL_SIZE_KEY, n);
				} else {
					log.warn("Ignoring non-positive {} value '{}' - keeping pool size {}.", POOL_SIZE_KEY, v, poolSize);
				}
			}
		} catch (NumberFormatException e) {
			log.warn("Invalid {} value - keeping pool size {}.", POOL_SIZE_KEY, poolSize);
		}
	}

	/** Applies the external allow-all-access flag ({@code JSPLUS_TEXSCRIPT_ALLOWALLACCESS}
	 *  env var or {@code -Djsplus.textscript.allowallaccess} system property)
	 *  when present; malformed values are rejected with a warning and the
	 *  configured/current value is kept. Only the shared default pool applies
	 *  this (subclass pools fix their policy in the constructor). */
	private void applyExternalAllowAllAccess() {
		try {
			String v = System.getenv(ALLOW_ALL_ACCESS_KEY) == null
					? System.getProperty(ALLOW_ALL_ACCESS_KEY)
					: System.getenv(ALLOW_ALL_ACCESS_KEY);
			if (v != null && !v.trim().isEmpty()) {
				boolean b = Boolean.parseBoolean(v.trim());
				allowAllAccess = b;
				Logger.systemMark(TextScriptConsole.class,
						"TextScriptConsole allowAllAccess from '{}' = '{}'.", ALLOW_ALL_ACCESS_KEY, b);
			}
		} catch (Exception e) {
			log.warn("Invalid {} value - keeping allowAllAccess {}.", ALLOW_ALL_ACCESS_KEY, allowAllAccess);
		}
	}

	/**
	* Initializes this pool's master console with the configured engine and
	* pre-warms the console pool (at least 10 consoles, growing to
	* {@code poolSize}).
	*/
	public synchronized void init() throws Exception {
		init(getEngine());
	}

	/**
	* Initializes this pool's master console with the given engine name, then
	* builds the initial console pool. No-op when already initialized.
	*
	* @param initEngineName engine id (e.g. {@code graalvm.js}); blank falls
	*                        back to the default engine
	*/
	public synchronized void init(String initEngineName) throws Exception {
		if (MasterConsole == null) {
			ConfigProperties consoleVariables = new ConfigProperties();
			if (!CommonTools.isBlank(initEngineName)) {
				engineName = initEngineName;
			} else if (CommonTools.isBlank(engineName)) {
				engineName = defaultEngineName;
			}
			if (!allowAllAccessLocked) {
				applyExternalPoolSize();
				applyExternalEngineName();
				applyExternalAllowAllAccess();
			}
			consoleVariables.putAndReturnSelf("ScriptEngine", engineName);
			MasterConsole = new ScriptConsole();
			MasterConsole.setUseScriptEnv();
			MasterConsole.setConsoleVariables(consoleVariables);
			MasterConsole.initScriptEngineManager(allowAllAccess);
			currentPoolSize = getMaxPoolSize();

			if (currentPoolSize < 10)
				currentPoolSize = 10;

			for (int i = 0; i < currentPoolSize; i++) {
				consolesPool.put(i, MasterConsole.newBuilder(allowAllAccess));
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
	private synchronized ScriptConsole getConsole() throws Exception {
		int newPoolSize = getMaxPoolSize();
		if (newPoolSize > currentPoolSize) {
			Logger.systemMark(TextScriptConsole.class, "TextScriptConsole changed pool size to '{}'.", newPoolSize);
			for (int i = currentPoolSize; i < newPoolSize; i++) {
				consolesPool.put(i, MasterConsole.newBuilder(allowAllAccess));
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
				log.warn("The console pool usage has reached {} ({}/{}).", "80%", (currentPoolSize - idleSize), currentPoolSize);
			}
			if (idleSize > 0) {
				Integer first = list.get(0);
				ScriptConsole sc = consolesPool.get(first);
				consolesUsingMark.put(tid, sc);
				consolesThreadMark.put(tid, first);
				consolesPool.remove(first);
			} else {
				throw new Exception("Full Console Pool.");
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
	*/
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

	/**
	* Returns the current thread's pooled console context (acquiring a console
	* if this thread never held one). Used by {@link TextScript#useEngine(String)}
	* so the engine name renders on a thread-confined pooled context - real
	* parallelism, no cross-thread access. Initializes this pool on first use
	* when {@link #init(String)} was never called.
	*
	* @return the context bound to the current thread
	* @throws Exception when the pool is exhausted
	*/
	public JsPlusContext getThreadContext() throws Exception {
		if (!isEngineLoaded()) {
			init(defaultEngineName);
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
	public TextScript newTextScript(String template) throws Exception {
		TextScript ts = new TextScript(template, this);
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
	public String renderOn(String template, Map<String,Object> params) throws Exception {
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

	/** This pool's master console - owns the data source and engine init. */
	public ScriptConsole getMasterConsole() {
		return MasterConsole;
	}

	/** Sets how long a freed console stays out of this pool before return (ms). */
	public void setFreedDelayMs(long freedDelayMs){
		this.freedDelayMs = freedDelayMs;
	}

	/** Returns the freed-console return delay (ms). */
	public long getFreedDelayMs(){
		return this.freedDelayMs;
	}

	/** Sets this pool's maximum console size (grows on demand up to this). A
	 *  positive external value ({@code jsplus.textscript.poolsize}) applied
	 *  during {@link #init(String)} takes precedence over a value set before
	 *  init; raising it after init grows the pool on next acquire. */
	public void setMaxPoolSize(int poolSize) {
		this.poolSize = poolSize;
	}

	/** Returns this pool's configured maximum console size. */
	public int getMaxPoolSize() {
		return poolSize;
	}

	/** Actual number of consoles currently idle in this pool (vs the
	 *  configured max) - a hard number for tests to confirm the pool returns
	 *  to full after traffic. */
	public int idleCount() {
		return consolesPool.size();
	}

	/** Sets the script-engine id used to init this pool's master console. A
	 *  non-blank external value ({@code jsplus.textscript.engine}) applied
	 *  during {@link #init(String)} takes precedence over this. */
	public void setEngine(String engineName) {
		this.engineName = engineName;
	}

	/** Returns the script-engine id (e.g. {@code graalvm.js}). */
	public String getEngine() {
		return this.engineName;
	}

	/** Sets the allow-all-access security flag applied to this pool's master
	 *  console and every pooled console when the pool initialises. Ignored on
	 *  subclass pools ({@link AllowAllAccessPool} / {@link NotAllowAccessPool})
	 *  whose policy is fixed in the constructor. An external value
	 *  ({@code jsplus.textscript.allowallaccess}) applied during
	 *  {@link #init(String)} takes precedence over this. */
	public void setAllowAllAccess(boolean allowAllAccess) {
		if (allowAllAccessLocked) {
			log.warn("AllowAllAccess is fixed for this pool type - ignoring setAllowAllAccess({}).", allowAllAccess);
			return;
		}
		this.allowAllAccess = allowAllAccess;
	}

	/** Returns this pool's allow-all-access security flag (default {@code true}).
	 *  Mirrors {@link com.killcoding.jsplus.JsPlusContext#isAllowAllAccess()}. */
	public boolean isAllowAllAccess() {
		return allowAllAccess;
	}

	// ================= static facade over the shared default pool =================

	/** Static: whether the default pool's master console is initialized. */
	public synchronized static boolean isLoadedScriptEngine() {
		return DEFAULT.isEngineLoaded();
	}

	/** Static: initializes the default pool (external config applies). */
	public static void initTextScriptConsole() throws Exception {
		DEFAULT.init();
	}

	/** Static: initializes the default pool with the given engine name. */
	public static void initTextScriptConsole(String initEngineName) throws Exception {
		DEFAULT.init(initEngineName);
	}

	/** Static: renders on the default pool's thread-confined context. */
	public static String render(String template, Map<String,Object> params) throws Exception {
		return DEFAULT.renderOn(template, params);
	}

	/** Static: sets the default pool's maximum pool size. */
	public static void setPoolSize(int poolSize) {
		DEFAULT.setMaxPoolSize(poolSize);
	}

	/** Static: returns the default pool's configured maximum pool size. */
	public static int getPoolSize() {
		return DEFAULT.getMaxPoolSize();
	}

	/** Static: the default pool's actual idle console count. */
	public static int getConsolePoolSize() {
		return DEFAULT.idleCount();
	}

	/** Static: returns the default pool's engine id. */
	public static String getEngineName() {
		return DEFAULT.getEngine();
	}

}
