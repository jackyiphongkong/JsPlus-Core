/*******************************************************************************
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

package com.killcoding.tpl;

import java.util.Locale;

/**
 * Minifies HTML with a single forward scan, without a DOM.
 *
 * <p>
 * The output is the input text with the noise removed, not a re-serialised
 * document: comments are dropped, {@code <style>} bodies are run through
 * {@link SimpleCssCompressor}, {@code <script>} bodies through
 * {@link SimpleJsCompressor}, and whitespace that merely formats the markup is
 * collapsed or removed. Element order, attribute order, attribute quoting and
 * tag spelling are left exactly as the caller wrote them.
 *
 * <p>
 * This is the deliberate counterpart of the jsoup-based compressor that the
 * upper layer ({@code killcoding-core}) keeps: that class holds a full
 * parse/serialise round trip (and therefore repairs broken
 * markup and normalises it, at the cost of emitting its own tag forms such as
 * {@code <link></link>} and CDATA-wrapped script bodies), while this class
 * changes nothing it was not asked to change. Because no tree is built, a
 * caller that hands in broken markup gets broken - but smaller - markup back.
 *
 * <p>
 * Raw-text elements are recognised by name: {@code style} and {@code script}
 * have their bodies minified, {@code pre} and {@code textarea} keep theirs
 * verbatim. A {@code </style>} or {@code </script>} sequence inside a string
 * literal of that body is therefore treated as the closing tag - keep such
 * content out of inline blocks, or quote the terminator as the HTML spec
 * requires.
 *
 * <p>
 * Whitespace rules, all of which only ever touch runs that abut a tag:
 * <ul>
 * <li>a run containing a line break that sits next to a tag is removed;</li>
 * <li>a run without a line break collapses to one space;</li>
 * <li>a run inside text content collapses to one space, so words never join.</li>
 * </ul>
 */
public class SimpleHtmlScanner {

	/** Elements whose body must not be whitespace-collapsed. */
	private static final String TAG_PRE = "pre";

	/** Second element whose body must not be whitespace-collapsed. */
	private static final String TAG_TEXTAREA = "textarea";

	/** Element whose body is minified as CSS. */
	private static final String TAG_STYLE = "style";

	/** Element whose body is minified as JavaScript. */
	private static final String TAG_SCRIPT = "script";

	/** The HTML source being compressed. */
	private String source;

	/**
	 * Wraps the given HTML source.
	 *
	 * @param source HTML text to compress
	 */
	public SimpleHtmlScanner(String source) {
		super();
		this.source = source;
	}

	/**
	 * Returns the wrapped source.
	 *
	 * @return the original HTML text
	 */
	public String getSource() {
		return source;
	}

	/**
	 * Compresses the wrapped source.
	 *
	 * @return the minified HTML, or {@code null} when the source is {@code null}
	 * @throws Exception never thrown for a well-formed scan; kept for signature
	 *             parity with the jsoup-based {@code SimpleHtmlCompressor#compressor()}
	 *             the upper layer keeps
	 */
	public String compressor() throws Exception {
		return compress(source);
	}

	/**
	 * Convenience one-shot over the given source.
	 *
	 * @param source HTML text to compress
	 * @return the minified HTML, or {@code null} when the source is {@code null}
	 * @throws Exception never thrown for a well-formed scan
	 */
	public static String compressor(String source) throws Exception {
		return new SimpleHtmlScanner(source).compressor();
	}

	/**
	 * Runs the scan.
	 *
	 * @param src source text
	 * @return minified text
	 * @throws Exception propagated from a body compressor
	 */
	private String compress(String src) throws Exception {
		if (src == null) {
			return null;
		}
		StringBuilder out = new StringBuilder(src.length());
		StringBuilder ws = new StringBuilder();
		int i = 0;
		int n = src.length();
		while (i < n) {
			char c = src.charAt(i);
			if (isTagStart(src, i)) {
				flushBeforeTag(out, ws);
				if (src.startsWith("<!--", i)) {
					int end = src.indexOf("-->", i + 4);
					i = (end < 0) ? n : end + 3;
					continue;
				}
				if (src.startsWith("<![CDATA[", i)) {
					int end = src.indexOf("]]>", i + 9);
					int stop = (end < 0) ? n : end + 3;
					out.append(src, i, stop);
					i = stop;
					continue;
				}
				if (startsWithAny(src, i, "<!", "<?", "</>")) {
					int end = src.indexOf('>', i + 1);
					int stop = (end < 0) ? n : end + 1;
					out.append(src, i, stop);
					i = stop;
					continue;
				}
				int end = findTagEnd(src, i);
				if (end < 0) {
					out.append('<');
					i++;
					continue;
				}
				out.append(src, i, end + 1);
				String name = tagName(src, i);
				boolean closing = src.charAt(i + 1) == '/';
				i = end + 1;
				if (!closing && !isSelfClosing(src, end) && isRawText(name)) {
					int body = i;
					int close = indexOfIgnoreCase(src, "</" + name, body);
					if (close < 0) {
						out.append(rawBody(name, src.substring(body)));
						i = n;
					} else {
						out.append(rawBody(name, src.substring(body, close)));
						i = close;
					}
				}
				continue;
			}
			if (isWhitespace(c)) {
				ws.append(c);
				i++;
				continue;
			}
			flushBeforeText(out, ws);
			out.append(c);
			i++;
		}
		return out.toString();
	}

	/**
	 * Reports whether a tag starts here.
	 *
	 * @param src source text
	 * @param index position of the character
	 * @return {@code true} when the character begins a tag, comment or declaration
	 */
	private static boolean isTagStart(String src, int index) {
		if (src.charAt(index) != '<') {
			return false;
		}
		if (index + 1 >= src.length()) {
			return false;
		}
		char next = src.charAt(index + 1);
		if (next == '!' || next == '?' || next == '/') {
			return true;
		}
		return !isWhitespace(next) && next != '=';
	}

	/**
	 * Checks whether the text starts with any of the given prefixes.
	 *
	 * @param src source text
	 * @param index start position
	 * @param prefixes prefixes to test
	 * @return {@code true} at the first match
	 */
	private static boolean startsWithAny(String src, int index, String... prefixes) {
		for (int i = 0; i < prefixes.length; i++) {
			if (src.startsWith(prefixes[i], index)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Finds the {@code >} closing a tag, honouring quoted attribute values.
	 *
	 * @param src source text
	 * @param from index of the opening {@code <}
	 * @return index of {@code >}, or {@code -1} when the tag is unterminated
	 */
	private static int findTagEnd(String src, int from) {
		char quote = 0;
		for (int j = from + 1; j < src.length(); j++) {
			char ch = src.charAt(j);
			if (quote != 0) {
				if (ch == quote) {
					quote = 0;
				}
				continue;
			}
			if (ch == '"' || ch == '\'') {
				quote = ch;
				continue;
			}
			if (ch == '>') {
				return j;
			}
		}
		return -1;
	}

	/**
	 * Reads the element name of a tag.
	 *
	 * @param src source text
	 * @param from index of the opening {@code <}
	 * @return the lower-cased name, empty when the tag has none
	 */
	private static String tagName(String src, int from) {
		int p = from + 1;
		if (p < src.length() && src.charAt(p) == '/') {
			p++;
		}
		int q = p;
		while (q < src.length()) {
			char ch = src.charAt(q);
			if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == ':') {
				q++;
				continue;
			}
			break;
		}
		if (q <= p) {
			return "";
		}
		return src.substring(p, q).toLowerCase(Locale.ROOT);
	}

	/**
	 * Reports whether the tag closes itself with {@code />}.
	 *
	 * @param src source text
	 * @param end index of the closing {@code >}
	 * @return {@code true} when a {@code /} precedes it
	 */
	private static boolean isSelfClosing(String src, int end) {
		int p = end - 1;
		while (p > 0 && isWhitespace(src.charAt(p))) {
			p--;
		}
		return p > 0 && src.charAt(p) == '/';
	}

	/**
	 * Reports whether the element body is raw text that must not be
	 * whitespace-collapsed.
	 *
	 * @param name lower-cased element name
	 * @return {@code true} for style, script, pre and textarea
	 */
	private static boolean isRawText(String name) {
		return TAG_STYLE.equals(name) || TAG_SCRIPT.equals(name)
			|| TAG_PRE.equals(name) || TAG_TEXTAREA.equals(name);
	}

	/**
	 * Produces the body for a raw-text element.
	 *
	 * @param name lower-cased element name
	 * @param body text between the open and close tags
	 * @return the body to emit
	 * @throws Exception propagated from a body compressor
	 */
	private String rawBody(String name, String body) throws Exception {
		if (TAG_PRE.equals(name) || TAG_TEXTAREA.equals(name)) {
			return body;
		}
		String trimmed = body.trim();
		if (trimmed.length() == 0) {
			return body;
		}
		try {
			if (TAG_STYLE.equals(name)) {
				return new SimpleCssCompressor(trimmed).compressor();
			}
			return new SimpleJsCompressor(trimmed).compressor();
		} catch (Exception e) {
			return body;
		}
	}

	/**
	 * Finds a substring case-insensitively.
	 *
	 * @param src text to search
	 * @param needle lower-cased needle
	 * @param from start position
	 * @return index of the match, or {@code -1}
	 */
	private static int indexOfIgnoreCase(String src, String needle, int from) {
		int limit = src.length() - needle.length();
		for (int i = from; i <= limit; i++) {
			if (src.regionMatches(true, i, needle, 0, needle.length())) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Reports whether the character is HTML whitespace.
	 *
	 * @param c character to test
	 * @return {@code true} for space, tab, CR, LF and form feed
	 */
	private static boolean isWhitespace(char c) {
		return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f';
	}

	/**
	 * Emits the pending whitespace run that sits in front of a tag.
	 *
	 * @param out output buffer
	 * @param ws pending whitespace
	 */
	private static void flushBeforeTag(StringBuilder out, StringBuilder ws) {
		flush(out, ws, true);
	}

	/**
	 * Emits the pending whitespace run that sits in front of text content.
	 *
	 * @param out output buffer
	 * @param ws pending whitespace
	 */
	private static void flushBeforeText(StringBuilder out, StringBuilder ws) {
		boolean afterTag = out.length() > 0 && out.charAt(out.length() - 1) == '>';
		flush(out, ws, afterTag);
	}

	/**
	 * Applies the whitespace rules to one pending run.
	 *
	 * @param out output buffer
	 * @param ws pending whitespace
	 * @param abutsTag whether the run touches a tag boundary
	 */
	private static void flush(StringBuilder out, StringBuilder ws, boolean abutsTag) {
		if (ws.length() == 0) {
			return;
		}
		boolean hasBreak = ws.indexOf("\n") >= 0 || ws.indexOf("\r") >= 0;
		boolean drop = out.length() == 0 || (hasBreak && abutsTag);
		if (!drop) {
			out.append(' ');
		}
		ws.setLength(0);
	}

}
