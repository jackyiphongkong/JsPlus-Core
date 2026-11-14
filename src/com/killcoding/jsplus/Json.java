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

import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;
import com.killcoding.tool.CommonTools;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* Ordered map that renders itself as JSON and parses JSON text back into
* maps/lists. This is the dependency-free replacement for
* {@code com.killcoding.ext.Json}: serialization is done here in plain Java
* (the original only ever concatenated strings) and PARSING is delegated to the
* embedded JavaScript engine through the {@link TextScriptConsole} pool, so
* {@code JSON.parse} does the work and no org.json / commons-text jar is needed.
* <p>
* Produced by controllers for {@code application/x-json} responses and used as a
* fluent JSON builder. Null/blank values can be skipped on output via
* {@link #skipNull(boolean)} and {@link #skipBlank(boolean)}; dates are
* formatted with the patterns on {@link Foramts}.
*
* @param <K> the entry key type
* @param <V> the entry value type
*/
@SuppressWarnings("serial")
public class Json<K,V> extends LinkedHashMap<K,V> {

	/** When {@code true}, {@code null} values are omitted from the rendered output. */
	protected boolean skipNull = false;
	/** When {@code true}, blank values are omitted from the rendered output. */
	protected boolean skipBlank = false;

	/** Binding name the JSON text is published under before the JS parse runs. */
	protected final static String PARSE_INPUT_NAME = "__jsplus_json_input";
	/** Binding name of the Java result box handed to the JS side. */
	protected final static String PARSE_BOX_NAME = "__jsplus_json_box";
	/** Key the converted result is stored under inside {@link #PARSE_BOX_NAME}. */
	protected final static String PARSE_RESULT_KEY = "v";
	/** Global name the pre-warmed converter function is published under. */
	protected final static String PARSE_FN_NAME = "__jsplus_json_conv";

	/**
	* Defines {@link #PARSE_FN_NAME} (and the Java class handles it closes over)
	* ONCE per context, instead of on every call.
	* <p>
	* Why this matters: measured on an interpreter-only GraalJS runtime, a
	* single-shot script that re-runs the seven {@code Java.type(...)} lookups
	* per call cost ~240-600us - more than {@code JSON.parse} itself. A top-level
	* {@code var}/{@code function} declaration becomes a global in both
	* graalvm.js and nashorn.js, so no engine-specific global object is touched
	* and no engine-specific type is imported.
	*/
	protected final static String PARSE_DEFINE_SCRIPT =
		"var LHM=Java.type('java.util.LinkedHashMap');"
		+ "var AL=Java.type('java.util.ArrayList');"
		+ "var INT=Java.type('java.lang.Integer');"
		+ "var LNG=Java.type('java.lang.Long');"
		+ "var DBL=Java.type('java.lang.Double');"
		+ "var STR=Java.type('java.lang.String');"
		+ "var BLN=Java.type('java.lang.Boolean');"
		+ "function " + PARSE_FN_NAME + "(v){"
		+ "if(v===null||v===undefined)return null;"
		+ "var t=typeof v;"
		+ "if(t==='number'){"
		+ "var d=v*1;"
		+ "if(d===Math.floor(d)&&d>=-2147483648&&d<=2147483647)return INT.valueOf(d);"
		+ "if(d===Math.floor(d))return LNG.valueOf(d);"
		+ "return DBL.valueOf(d);"
		+ "}"
		+ "if(t==='string')return new STR(v);"
		+ "if(t==='boolean')return BLN.valueOf(v);"
		+ "if(Object.prototype.toString.call(v)==='[object Array]'){"
		+ "var l=new AL();for(var i=0;i<v.length;i++)l.add(" + PARSE_FN_NAME + "(v[i]));return l;"
		+ "}"
		+ "var m=new LHM();var ks=Object.keys(v);"
		+ "for(var i=0;i<ks.length;i++)m.put(ks[i]," + PARSE_FN_NAME + "(v[ks[i]]));"
		+ "return m;"
		+ "};";

	/**
	* The per-call script: a single call into the pre-warmed converter, storing
	* the already-Java result into the box. Short on purpose - the engine only
	* has to evaluate this one line, not the whole converter definition.
	*/
	protected final static String PARSE_CALL_SCRIPT =
		PARSE_BOX_NAME + ".put('" + PARSE_RESULT_KEY + "',"
		+ PARSE_FN_NAME + "(JSON.parse(" + PARSE_INPUT_NAME + ")));";

	/** Guards dedicated-console creation and every parse call made through it. */
	private static final Object CONSOLE_LOCK = new Object();
	/** Result box handed to the JS side; reused across calls under the lock. */
	private static final Map<String,Object> RESULT_BOX = new LinkedHashMap<String,Object>(4);
	/** Long-lived console owned by {@link #parse(String, String)} (null until first use). */
	private static ScriptConsole DEDICATED = null;
	/** Whether {@link #PARSE_FN_NAME} is already defined in {@link #DEDICATED}. */
	private static boolean converterReady = false;
	/**
	* Returns the SHARED TextScript console pool.
	* <p>
	* Reusing the application's pool instead of a private one is deliberate:
	* {@link TextScriptConsole#init} pre-builds at least ten consoles, so a
	* private pool would pay for ten JS contexts (and a multi-second warm-up on
	* the first parse) purely for JSON parsing. Sharing means the engine is
	* already up wherever templates are rendered.
	*
	* @return the shared default pool
	* */
	protected static TextScriptConsole pool(){
		return TextScriptConsole.getDefaultPool();
	}

	/**
	* Returns the console dedicated to JSON parsing, creating it on first use.
	* <p>
	* A deliberate departure from the pooled path. Measured on the same runtime,
	* {@code getThreadContext()} + {@code freed()} costs about 785us per
	* {@code parse} call: two {@code stream+sort} allocations over the idle set
	* ({@link TextScriptConsole#getConsole}) and a context switch every time the
	* pool hands back a different console - which also throws away the engine's
	* inline caches. The parse itself is ~100us once the converter is
	* pre-warmed, so the round-trip is paid for nothing.
	* <p>
	* Holding one console removes that round-trip, at two costs: parse calls are
	* serialised behind {@link #CONSOLE_LOCK}, and this console does not take
	* part in the pool's fairness scheme. Memory stays at one extra JS context
	* per JVM (created lazily), never the ten-plus a private pool would pre-build.
	*
	* @return the context of the dedicated console
	* @throws Exception when the pool or engine cannot be initialised
	* */
	protected static JsPlusContext console() throws Exception {
		synchronized (CONSOLE_LOCK) {
			if (DEDICATED == null) {
				TextScriptConsole pool = pool();
				if (!pool.isEngineLoaded()) {
					pool.init();
				}
				DEDICATED = pool.getMasterConsole().newBuilder(true);
				converterReady = false;
			}
			return DEDICATED.getContext();
		}
	}

	/**
	* Date/time patterns used when serializing {@link java.sql.Timestamp},
	* {@link java.sql.Date} and {@link java.sql.Time} values into JSON strings.
	* (The class name keeps the original spelling so existing references still compile.)
	*/
	public final static class Foramts {
		/** Pattern for timestamps; milliseconds are included. */
		public static String Datetime = "yyyy-MM-dd HH:mm:ss.SSS";
		/** Pattern for dates. */
		public static String Date = "yyyy-MM-dd";
		/** Pattern for times; milliseconds are included. */
		public static String Time = "HH:mm:ss.SSS";
	}

	/** Creates an empty JSON object. */
	public Json(){
		super();
	}

	/**
	* Creates a JSON object holding one initial entry.
	* @param key   entry key
	* @param value entry value
	* */
	public Json(K key,V value){
		super();
		put(key,value);
	}

	/**
	* Creates a JSON object seeded from an existing map.
	* @param m initial entries
	* */
	public Json(Map<K,V> m){
		super();
		if (m != null) {
			putAll(m);
		}
	}

	/**
	* Creates an empty JSON object with the given initial capacity.
	* @param initialCapacity passed to the underlying map
	* */
	public Json(int initialCapacity) {
		super(initialCapacity);
	}

	/**
	* Escapes text for safe embedding in a JSON string literal (quote, backslash,
	* control characters, and the two line separators that break JS parsers).
	* @param value the raw text
	* @return the escaped text
	* */
	protected static String escapeJson(String value){
		if (value == null)
			return "null";

		final int size = value.length();
		StringBuilder s = new StringBuilder(size + 16);
		for (int i = 0; i < size; i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"': s.append("\\\""); break;
				case '\\': s.append("\\\\"); break;
				case '\b': s.append("\\b"); break;
				case '\f': s.append("\\f"); break;
				case '\n': s.append("\\n"); break;
				case '\r': s.append("\\r"); break;
				case '\t': s.append("\\t"); break;
				default:
					if (c < 0x20 || c == 0x7F || c == '\u2028' || c == '\u2029') {
						s.append(String.format("\\u%04x", (int) c));
					} else {
						s.append(c);
					}
			}
		}
		return s.toString();
	}

	/**
	* Renders a string as a quoted, escaped JSON string.
	* @param value the string; {@code null} renders as {@code null}
	* @return the JSON literal
	* */
	protected String quote(String value){
		if (value == null)
			return "null";

		return "\"" + escapeJson(value) + "\"";
	}

	/**
	* Renders a character as a quoted JSON string.
	* @param value the character; {@code null} renders as {@code null}
	* @return the JSON literal
	* */
	protected String quote(Character value){
		if (value == null)
			return "null";
		else
			return quote(value.toString());
	}

	/**
	* Renders a nested {@link Json} object as its JSON text.
	* @param value the nested object; {@code null} renders as {@code null}
	* @return the JSON object text
	* */
	protected String quote(Json<K,V> value){
		if (value == null)
			return "null";
		else
			return value.toString();
	}

	/**
	* Renders an object array as a JSON array literal.
	* @param value the array; {@code null} renders as {@code null}
	* @return the JSON array text
	* */
	protected String quote(Object[] value){
		if (value == null)
			return "null";

		return quotes(Arrays.asList(value));
	}

	/**
	* Renders a list as a JSON array literal, quoting each element.
	* @param value the list; {@code null} renders as {@code null}
	* @return the JSON array text
	* */
	protected String quote(List<Object> value){
		if (value == null)
			return "null";

		final int size = value.size();
		StringBuilder s = new StringBuilder("[");
		s.append(debugSequence());
		for (int i = 0; i < size; i++) {
			s.append(quotes(value.get(i)));
			if (i < size - 1)
				s.append(",");

			s.append(debugSequence());
		}
		s.append("]");
		return s.toString();
	}

	/**
	* Renders a map as a JSON object literal, quoting each key/value pair.
	* @param value the map; {@code null} renders as {@code null}
	* @return the JSON object text
	* */
	protected String quote(Map<K,V> value){
		if (value == null)
			return "null";

		StringBuilder s = new StringBuilder("{");
		s.append(debugSequence());
		List<K> keys = new ArrayList<K>(value.keySet());
		int size = keys.size();
		for (int i = 0; i < size; i++) {
			K key = keys.get(i);
			V v = value.get(key);
			s.append(quote(key,v));
			if (i < size - 1)
				s.append(",");

			s.append(debugSequence());
		}
		s.append("}");
		return s.toString();
	}

	/**
	* Renders a number as its raw JSON literal.
	* @param value the number; {@code null} renders as {@code null}
	* @return the JSON number text
	* */
	protected String quote(Number value){
		if(value == null)
			return "null";
		else
			return value.toString();
	}

	/**
	* Renders a boolean as {@code true}/{@code false}.
	* @param value the boolean; {@code null} renders as {@code null}
	* @return the JSON literal
	* */
	protected String quote(Boolean value){
		if(value == null)
			return "null";
		else
			return value.toString();
	}

	/**
	* Renders an arbitrary object by converting it to a string first.
	* @param value the object; {@code null} renders as {@code null}
	* @return the JSON literal
	* */
	protected String quote(Object value){
		if(value == null)
			return "null";
		else
			return quote(value.toString());
	}

	/**
	* Formats a timestamp with {@link Foramts#Datetime} and quotes it.
	* @param value the timestamp; {@code null} renders as {@code null}
	* @return the quoted JSON string
	* */
	protected String quote(Timestamp value){
		if(value == null)
			return "null";
		else{
			SimpleDateFormat f = new SimpleDateFormat(Foramts.Datetime);
			return quote(f.format((Timestamp)value));
		}
	}

	/**
	* Formats a date with {@link Foramts#Date} and quotes it.
	* @param value the date; {@code null} renders as {@code null}
	* @return the quoted JSON string
	* */
	protected String quote(Date value){
		if(value == null)
			return "null";
		else{
			SimpleDateFormat f = new SimpleDateFormat(Foramts.Date);
			return quote(f.format((Date)value));
		}
	}

	/**
	* Formats a time with {@link Foramts#Time} and quotes it.
	* @param value the time; {@code null} renders as {@code null}
	* @return the quoted JSON string
	* */
	protected String quote(Time value){
		if(value == null)
			return "null";
		else{
			SimpleDateFormat f = new SimpleDateFormat(Foramts.Time);
			return quote(f.format((Time)value));
		}
	}

	/**
	* Serializes any value to its JSON representation using a throwaway instance.
	* @param value the value to serialize
	* @return the JSON text
	* */
	public static String format(Object value){
		return new Json<Object,Object>().quotes(value);
	}

	/**
	* Dispatches a value to the matching {@code quote} overload by runtime type.
	* @param value the value; {@code null} renders as {@code null}
	* @return the JSON literal
	* */
	@SuppressWarnings("unchecked")
	protected String quotes(Object value){
		if(value == null)
			return "null";

		if(value instanceof String){
			return quote(value.toString());
		}else if(value instanceof Character){
			return quote((Character)value);
		}else if(value instanceof Number){
			return quote((Number)value);
		}else if(value instanceof Boolean){
			return quote((Boolean)value);
		}else if(value instanceof Json){
			return quote((Json<K,V>)value);
		}else if(value instanceof Map){
			return quote((Map<K,V>)value);
		}else if(value instanceof List){
			return quote((List<Object>)value);
		}else if(value instanceof Timestamp){
			return quote((Timestamp)value);
		}else if(value instanceof Date){
			return quote((Date)value);
		}else if(value instanceof Time){
			return quote((Time)value);
		}else if(value instanceof Object[]){
			return quote((Object[])value);
		}else{
			return quote(value);
		}
	}

	/**
	* Quotes a map key as a JSON string.
	* @param key the key
	* @return the quoted key
	* */
	protected String quoteKey(K key){
		return quote(key);
	}

	/**
	* Renders one {@code "key":value} pair.
	* @param key   object key
	* @param value object value
	* @return the quoted pair
	* */
	protected String quote(K key, V value) {
		return quoteKey(key) + ":" + quotes(value);
	}

	/**
	* Serializes this map to a JSON object, honouring {@link #skipNull} and
	* {@link #skipBlank}; the comma is driven by "anything already written" so a
	* skipped entry never leaves a trailing comma.
	* @return the JSON object text
	* */
	public final String generate(){
		StringBuilder s = new StringBuilder("{");
		s.append(debugSequence());
		// The separator is driven by "was anything already written", not by the
		// raw index: skipNull/skipBlank can drop entries, and an index-based
		// comma left a trailing "," that made the output invalid JSON.
		boolean written = false;
		for (Map.Entry<K, V> entry : entrySet()) {
			K key = entry.getKey();
			V value = entry.getValue();
			if(skipNull && value == null)
				continue;

			if(skipBlank && CommonTools.isBlank(value))
				continue;

			if(written)
				s.append(",");

			s.append(quote(key,value));
			s.append(debugSequence());
			written = true;
		}
		s.append("}");
		return s.toString();
	}

	/**
	* Returns the JSON rendering of this object.
	* @return {@link #generate()}
	* */
	@Override
	public String toString(){
		return generate();
	}

	/**
	* Adds one entry and returns this object for chaining.
	* @param key   entry key
	* @param value entry value
	* @return this JSON object, for chaining
	* */
	public Json<K,V> append(K key,V value){
		put(key, value);
		return this;
	}

	/**
	* Adds all entries from a map and returns this object for chaining.
	* @param m entries to add
	* @return this JSON object, for chaining
	* */
	public Json<K,V> append(Map<K,V> m){
		if (m != null) {
			putAll(m);
		}
		return this;
	}

	/**
	* Sets whether {@code null} values are omitted on output.
	* @param skipNull {@code true} to omit null values
	* @return this JSON object, for chaining
	* */
	public Json<K,V> skipNull(boolean skipNull){
		this.skipNull = skipNull;
		return this;
	}

	/**
	* Sets whether blank values are omitted on output.
	* @param skipBlank {@code true} to omit blank values
	* @return this JSON object, for chaining
	* */
	public Json<K,V> skipBlank(boolean skipBlank){
		this.skipBlank = skipBlank;
		return this;
	}

	/**
	* Returns a shallow copy of this object as a plain map.
	* @return a new {@link java.util.HashMap} holding the same entries
	* */
	public Map<K,V> toMap(){
		return new HashMap<K,V>(this);
	}

	/**
	* Returns a shallow copy holding the same entries.
	* @return a new {@link Json} with this object's entries
	* */
	@Override
	public Json<K, V> clone() {
		return new Json<K,V>(this);
	}

	/**
	* Returns a newline when error logging is disabled (one entry per line) or an
	* empty string when it is enabled (compact output).
	* @return {@code "\n"} or {@code ""}
	* */
	protected static String debugSequence() {
		if(!LoggerFactory.getLogger(Json.class).isErrorEnabled()){
			return "\n";
		}else{
			return "";
		}
	}

	/**
	* Parses a JSON object or array string, defaulting to a blank input.
	* @param source JSON object or array text (starts with an opening brace or bracket)
	* @param <T>    expected result type
	* @return the parsed map/list, or {@code null} on parse failure
	* */
	public static <T extends Object> T parse(String source){
		return Json.parse(source," ");
	}

	/**
	* Parses a JSON object or array string, substituting {@code def} for a blank
	* input. The result is a {@code Map} for object text and a {@code List} for
	* array text. Parsing runs in the embedded JS engine ({@code JSON.parse})
	* through a dedicated console, so no third-party JSON jar is required.
	* <p>
	* The converter function is defined once and only called afterwards, and the
	* console is held rather than borrowed per call - see {@link #console()} for
	* why both matter.
	*
	* @param source JSON object or array text (starts with an opening brace or bracket)
	* @param def    fallback used when {@code source} is blank
	* @param <T>    expected result type
	* @return the parsed map/list, or {@code null} on parse failure
	* */
	@SuppressWarnings("unchecked")
	public static <T extends Object> T parse(String source,String def){
		Logger log = LoggerFactory.getLogger(Json.class);
		try{
			String text = CommonTools.isBlank(source) ? def : source;
			if(CommonTools.isBlank(text))
				return null;

			char head = text.trim().charAt(0);
			if(head != '{' && head != '[')
				return null;

			synchronized (CONSOLE_LOCK) {
				JsPlusContext context = console();
				if (!converterReady) {
					// Publish the box and define the converter once per context;
					// both stay bound between calls.
					context.put(PARSE_BOX_NAME, RESULT_BOX);
					context.evalScript(PARSE_DEFINE_SCRIPT);
					converterReady = true;
				}
				RESULT_BOX.remove(PARSE_RESULT_KEY);
				context.put(PARSE_INPUT_NAME, text);
				context.evalScript(PARSE_CALL_SCRIPT);
				// The JS side already converted the tree to Java objects and stored
				// it in the box, so nothing engine-specific crosses back here.
				return (T)RESULT_BOX.get(PARSE_RESULT_KEY);
			}
		}catch(Exception e){
			// A syntax error is bad input, not a broken context: only re-arm the
			// converter definition and keep the console for the next caller.
			synchronized (CONSOLE_LOCK) {
				converterReady = false;
			}
			log.warn(e.getMessage(),e);
			return null;
		}
	}

}
