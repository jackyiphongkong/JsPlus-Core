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

import java.util.Map;

import com.killcoding.log.LoggerFactory;

/**
* Thread-confined {@link TextScript} console pool: a fixed pool of
* {@link ScriptConsole} instances (one per concurrent renderer) with a master
* console that owns the data source, script-engine init and JVM-wide settings.
* Mirrors JsPlus-Tomcat {@code TomcatScriptConsole}.
* <p>
* The pool mechanics - acquisition, delayed return, external config, sizing -
* live in {@link AbstractConsolePool} and are shared with
* {@link ScriptConsolePool}. This class keeps its own consoles, its own
* external-config prefix ({@code jsplus.textscript.*}) and the TextScript
* rendering API on top.
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
* <li>pool size: {@code jsplus.textscript.poolsize};</li>
* <li>engine name: {@code jsplus.textscript.engine};</li>
* <li>allow-all-access: {@code jsplus.textscript.allowallaccess=true|false}.</li>
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
public class TextScriptConsole extends AbstractConsolePool {

	/** The shared default pool backing the static API (external config applies
	*  to this one). TextScript() without an explicit pool uses this pool. */
	private static final TextScriptConsole DEFAULT = new TextScriptConsole();

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
		super("jsplus.textscript", "TextScriptConsole", "Full Console Pool.",
				LoggerFactory.getLogger(TextScriptConsole.class), allowAllAccess, applyExternalConfig);
	}

	/** Returns the shared default pool (backs the static API). */
	public static TextScriptConsole getDefaultPool() {
		return DEFAULT;
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
