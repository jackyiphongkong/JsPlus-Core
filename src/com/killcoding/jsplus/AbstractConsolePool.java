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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.killcoding.log.Logger;
import com.killcoding.tool.CommonTools;
import com.killcoding.tool.ConfigProperties;

/**
* Shared console-pool implementation: one instance is one independent pool of
* thread-confined {@link ScriptConsole} objects, each holding its own GraalVM
* context.
* <p>
* This class exists because two pools of the same shape used to be maintained
* as two near-identical copies ({@link TextScriptConsole} and
* {@link ScriptConsolePool}). Every pool still owns its own consoles and its
* own state - the merge is about code, not about sharing consoles: a
* {@code TextScriptConsole} and a {@code ScriptConsolePool} remain two separate
* pools, exactly as before.
* <p>
* A subclass supplies three things: the external-config key prefix (so one pool
* keeps reading {@code jsplus.textscript.*} while the other reads
* {@code jsplus.scriptconsole.*}), a label used in diagnostics, and its own
* logger. Everything else - acquisition, delayed return, generation tracking,
* external config, sizing - lives here.
* <p>
* Thread model: GraalVM allows at most one thread inside a context at a time.
* Each console is handed to at most one thread (removed from the idle set
* while in use), so concurrent callers never enter the same context
* simultaneously.
*/
public abstract class AbstractConsolePool {

	/** Subclass logger - every diagnostic is attributed to the concrete pool. */
	protected final Logger log;

	/** Short pool name used in diagnostics ({@code "TextScriptConsole"}). */
	protected final String poolLabel;

	/** Exact message thrown when every console is in use. Kept per subclass so
	*  the wording callers/tests already match stays unchanged. */
	protected final String fullPoolMessage;

	/** External config keys, derived from the subclass key prefix. */
	private final String poolSizeKey;
	private final String engineNameKey;
	private final String allowAllAccessKey;

	/** Shared daemon scheduler for delayed console return (freed): fixed-size,
	 *  non-leaking (never grows per call), and never blocks JVM exit. Every
	 *  pool instance posts its returns here; the scheduled task captures the
	 *  owning pool so consoles always come back to their own pool. */
	private static final ScheduledExecutorService FREED_SCHEDULER = Executors.newScheduledThreadPool(
			Math.max(2, Runtime.getRuntime().availableProcessors()),
			r -> {
				Thread t = new Thread(r, "jsplus-console-freed");
				t.setDaemon(true);
				return t;
			});

	// ---- instance pool state (each instance is one independent pool) ----

	private final Map<Integer, ScriptConsole> consolesPool = new ConcurrentHashMap<Integer, ScriptConsole>();
	private final Map<Long, ScriptConsole> consolesUsingMark = new ConcurrentHashMap<Long, ScriptConsole>();
	private final Map<Long, Integer> consolesThreadMark = new ConcurrentHashMap<Long, Integer>();

	private int poolSize = 100;

	private int currentPoolSize = 0;

	private long freedDelayMs = 10L;

	/** Monotonic generation counter for delayed returns. A pending return only
	 *  fires when its generation is still the newest one for that thread; a
	 *  larger value means the thread reclaimed its console in the meantime.
	 *  Guarded by {@code synchronized(this)}, so a plain long is enough. */
	private long freedGenerationSeq = 0L;

	/** Per-thread generation of the pending delayed return, keyed by thread id. */
	private final Map<Long,Long> consolesFreedGeneration = new ConcurrentHashMap<Long,Long>();

	/** When this thread first reclaimed a console it had already freed, used to
	 *  bound how long one thread may keep reusing it (wall clock, ms). */
	private final Map<Long,Long> consolesHoldStart = new ConcurrentHashMap<Long,Long>();

	/** How long one thread may keep reclaiming its own console before the pool
	 *  forces a real return (ms); {@code <= 0} removes the bound. Bounds both
	 *  starvation of other threads and how long a console stays out of the idle
	 *  set - without it, a hot thread would pin its console forever. */
	private long reclaimHoldMs = 50L;

	protected final String defaultEngineName = "graalvm.js";

	private String engineName = "graalvm.js";

	private boolean allowAllAccess = true;

	/** true for code-constructed strategy pools: the security policy is fixed
	 *  at construction and cannot be changed afterwards. */
	private final boolean allowAllAccessLocked;

	protected ScriptConsole masterConsole = null;

	/**
	* Builds one independent pool.
	*
	* @param keyPrefix external-config key prefix, e.g. {@code "jsplus.textscript"}
	* @param poolLabel short name used in diagnostics
	* @param fullPoolMessage exception message when the pool is exhausted
	* @param log       the concrete subclass logger
	* @param allowAllAccess initial security policy
	* @param applyExternalConfig when true the external configuration is applied
	*        at {@link #init(String)} and the policy stays mutable; when false the
	*        policy is fixed at construction
	*/
	protected AbstractConsolePool(String keyPrefix, String poolLabel, String fullPoolMessage,
			Logger log, boolean allowAllAccess, boolean applyExternalConfig) {
		this.log = log;
		this.poolLabel = poolLabel;
		this.fullPoolMessage = fullPoolMessage;
		this.poolSizeKey = keyPrefix + ".poolsize";
		this.engineNameKey = keyPrefix + ".engine";
		this.allowAllAccessKey = keyPrefix + ".allowallaccess";
		this.allowAllAccess = allowAllAccess;
		this.allowAllAccessLocked = !applyExternalConfig;
	}

	/** Whether this pool's master console (and thus its script engine) is
	*  initialized. */
	public synchronized boolean isEngineLoaded() {
		return masterConsole != null;
	}

	/** Applies the external engine name when present; blank values are rejected
	*  and the current name is kept. Called only while the pool initialises,
	*  after the code-level default/argument has been applied, so the external
	*  deployment config wins. */
	private void applyExternalEngineName() {
		try {
			String v = System.getenv(engineNameKey) == null
					? System.getProperty(engineNameKey)
					: System.getenv(engineNameKey);
			if (v != null && !v.trim().isEmpty()) {
				engineName = v.trim();
				Logger.systemMark(getClass(),
						poolLabel + " engine name from '{}' = '{}'.", engineNameKey, engineName);
			}
		} catch (Exception e) {
			log.warn("Invalid {} value - keeping engine name {}.", engineNameKey, engineName);
		}
	}

	/** Applies the external pool size when present; malformed or non-positive
	*  values are rejected with a warning and the configured/current value is
	*  kept. Called only while the pool initialises, so it never races
	*  {@link #setMaxPoolSize(int)}. */
	private void applyExternalPoolSize() {
		try {
			String v = System.getenv(poolSizeKey) == null
					? System.getProperty(poolSizeKey)
					: System.getenv(poolSizeKey);
			if (v != null && !v.trim().isEmpty()) {
				int n = Integer.parseInt(v.trim());
				if (n > 0) {
					poolSize = n;
					Logger.systemMark(getClass(),
							poolLabel + " pool size from '{}' = '{}'.", poolSizeKey, n);
				} else {
					log.warn("Ignoring non-positive {} value '{}' - keeping pool size {}.",
							poolSizeKey, v, poolSize);
				}
			}
		} catch (NumberFormatException e) {
			log.warn("Invalid {} value - keeping pool size {}.", poolSizeKey, poolSize);
		}
	}

	/** Applies the external allow-all-access flag when present; malformed values
	*  are rejected with a warning and the configured/current value is kept.
	*  Pools whose policy is fixed at construction never apply this. */
	private void applyExternalAllowAllAccess() {
		try {
			String v = System.getenv(allowAllAccessKey) == null
					? System.getProperty(allowAllAccessKey)
					: System.getenv(allowAllAccessKey);
			if (v != null && !v.trim().isEmpty()) {
				boolean b = Boolean.parseBoolean(v.trim());
				allowAllAccess = b;
				Logger.systemMark(getClass(),
						poolLabel + " allowAllAccess from '{}' = '{}'.", allowAllAccessKey, b);
			}
		} catch (Exception e) {
			log.warn("Invalid {} value - keeping allowAllAccess {}.",
					allowAllAccessKey, allowAllAccess);
		}
	}

	/**
	* Initializes this pool's master console with the configured engine and
	* pre-warms the console pool (at least 10 consoles, growing to
	* {@code poolSize}).
	* @throws Exception when the engine cannot be initialized
	* */
	public synchronized void init() throws Exception {
		init(getEngine());
	}

	/**
	* Initializes this pool's master console with the given engine name, then
	* builds the initial console pool. No-op when already initialized.
	* @param initEngineName engine id (e.g. {@code graalvm.js}); blank falls back
	*        to the default engine
	* @throws Exception when the engine cannot be initialized
	* */
	public synchronized void init(String initEngineName) throws Exception {
		if (masterConsole == null) {
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
	}

	/**
	* Acquires a console for the current thread (one console per thread, tracked
	* by thread id). Grows the pool when {@code poolSize} was raised; throws when
	* every console is in use.
	*
	* @return the console bound to the current thread
	* @throws Exception when the pool is exhausted
	* */
	protected synchronized ScriptConsole getConsole() throws Exception {
		int newPoolSize = getMaxPoolSize();
		if (newPoolSize > currentPoolSize) {
			Logger.systemMark(getClass(),
					poolLabel + " changed pool size to '{}'.", newPoolSize);
			for (int i = currentPoolSize; i < newPoolSize; i++) {
				consolesPool.put(i, masterConsole.newBuilder(allowAllAccess));
			}
			currentPoolSize = newPoolSize;
		}
		long tid = Thread.currentThread().getId();

		// A console freed by this same thread is still bound to it during the
		// freedDelayMs grace window. Reclaiming it beats allocating a fresh one:
		// no idle-set scan, and the engine context (plus its inline caches) stays
		// warm. isHoldExpired() keeps a hot thread from pinning it forever.
		ScriptConsole held = consolesUsingMark.get(tid);
		if (held != null) {
			if (isHoldExpired(tid)) {
				Integer heldIndex = consolesThreadMark.get(tid);
				consolesFreedGeneration.remove(tid);
				consolesThreadMark.remove(tid);
				consolesUsingMark.remove(tid);
				consolesHoldStart.remove(tid);
				if (heldIndex != null) {
					consolesPool.put(heldIndex, held);
				}
			} else {
				consolesFreedGeneration.remove(tid);
				if (!consolesHoldStart.containsKey(tid)) {
					consolesHoldStart.put(tid, Long.valueOf(System.currentTimeMillis()));
				}
				return held;
			}
		}

		Integer threadMark = consolesThreadMark.get(tid);
		if (threadMark == null) {
			List<Integer> list = list();
			int idleSize = list.size();
			double usage = usageOf(idleSize);
			if (usage >= 0.8D) {
				log.warn("The {} usage has reached {} ({}/{}).", poolLabel, "80%",
						(currentPoolSize - idleSize), currentPoolSize);
			}
			if (idleSize > 0) {
				Integer first = list.get(0);
				ScriptConsole sc = consolesPool.get(first);
				consolesUsingMark.put(tid, sc);
				consolesThreadMark.put(tid, first);
				consolesPool.remove(first);
			} else {
				throw new Exception(fullPoolMessage);
			}
		}
		return consolesUsingMark.get(tid);
	}

	/** Whether the current thread has held its reclaimed console long enough
	*  that the pool should force a real return. */
	private boolean isHoldExpired(long tid) {
		long limit = reclaimHoldMs;
		if (limit <= 0L) {
			return false;
		}
		Long start = consolesHoldStart.get(tid);
		return start != null && (System.currentTimeMillis() - start.longValue()) > limit;
	}

	/** Pool usage ratio from an idle count the caller already computed - avoids
	*  a second idle-set scan inside {@link #getConsole()} (the scan streams,
	*  sorts and collects every idle key, so doing it twice per acquire is
	*  wasteful). Returns 0.0 while the pool is not initialized yet. */
	private double usageOf(int idleSize) {
		if (currentPoolSize <= 0) {
			return 0.0D;
		}
		return (currentPoolSize - idleSize) / (currentPoolSize * 1.0D);
	}

	/** Pool usage ratio (0.0 = all idle, 1.0 = all in use). */
	public synchronized double usage() {
		int idleSize = list().size();
		double usage = usageOf(idleSize);
		return usage;
	}

	/** Idle console indices, ascending. */
	private synchronized List<Integer> list() {
		List<Integer> klist = consolesPool.keySet()
				.stream()
				.sorted()
				.collect(Collectors.toList());
		return new ArrayList<Integer>(klist);
	}

	/**
	* Returns the current thread's console to this pool after
	* {@code freedDelayMs}; no-op when the thread never held a console.
	* */
	public synchronized void freed() {
		try {
			long tid = Thread.currentThread().getId();
			final ScriptConsole sc = consolesUsingMark.get(tid);
			if (sc == null) {
				return;
			}
			// Clear this thread's bindings through the console already held.
			// Going back through getConsole() here would count as a reclaim and
			// inflate the hold window.
			sc.getContext().remove();

			final Integer useIndex = consolesThreadMark.get(tid);
			final long gen = ++freedGenerationSeq;
			consolesFreedGeneration.put(tid, Long.valueOf(gen));
			long delayMs = getFreedDelayMs();
			FREED_SCHEDULER.schedule(new Runnable() {
				@Override
				public void run() {
					synchronized (AbstractConsolePool.this) {
						Long current = consolesFreedGeneration.get(tid);
						if (current == null || current.longValue() != gen) {
							// The owning thread reclaimed the console (or freed it
							// again, which posted a newer generation) - there is
							// nothing left to return.
							return;
						}
						consolesFreedGeneration.remove(tid);
						consolesThreadMark.remove(tid);
						consolesUsingMark.remove(tid);
						consolesHoldStart.remove(tid);
						if (useIndex != null) {
							try {
								consolesPool.put(useIndex, sc);
							} catch (Exception e) {
								log.info(e.getMessage(), e);
							}
						}
					}
				}
			}, delayMs, TimeUnit.MILLISECONDS);
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

	/** Sets how long one thread may keep reclaiming its own console before the
	*  pool forces a real return (ms); {@code <= 0} removes the bound. */
	public void setReclaimHoldMs(long reclaimHoldMs) {
		this.reclaimHoldMs = reclaimHoldMs;
	}

	/** Returns the reclaim hold bound (ms). */
	public long getReclaimHoldMs() {
		return this.reclaimHoldMs;
	}

	/** Sets this pool's maximum console size (grows on demand up to this). A
	*  positive external value applied during {@link #init(String)} takes
	*  precedence over a value set before init; raising it after init grows the
	*  pool on next acquire. */
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

	/** Sets the script-engine id used to init this pool's master console. A
	*  non-blank external value applied during {@link #init(String)} takes
	*  precedence over this. */
	public void setEngine(String engineName) {
		this.engineName = engineName;
	}

	/** Returns the script-engine id (e.g. {@code graalvm.js}). */
	public String getEngine() {
		return this.engineName;
	}

	/** Sets the allow-all-access security flag applied to this pool's master
	*  console and every pooled console when the pool initialises. Ignored on
	*  pools whose policy is fixed at construction. An external value applied
	*  during {@link #init(String)} takes precedence over this. */
	public void setAllowAllAccess(boolean allowAllAccess) {
		if (allowAllAccessLocked) {
			log.warn("AllowAllAccess is fixed for this pool type - ignoring setAllowAllAccess({}).",
					allowAllAccess);
			return;
		}
		this.allowAllAccess = allowAllAccess;
	}

	/** Returns this pool's allow-all-access security flag (default {@code true}).
	*  Mirrors {@link com.killcoding.jsplus.JsPlusContext#isAllowAllAccess()}. */
	public boolean isAllowAllAccess() {
		return allowAllAccess;
	}

}
