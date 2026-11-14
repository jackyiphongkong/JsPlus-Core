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
package com.killcoding.tpl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
* Minifies HTML while repairing broken tags, with no DOM library and no
* third-party jar - the drop-in replacement for the jsoup-based compressor.
*
* <p>
* Unlike {@link SimpleHtmlScanner}, which deliberately never builds structure
* ("broken markup in, smaller broken markup out"), this class keeps an element
* stack while it scans, so every unbalanced situation is resolved deterministically:
* <ul>
* <li>an unclosed element is closed where the next sibling or its parent
*     requires it, or at end of input;</li>
* <li>a stray end tag (one whose element is not open) is dropped;</li>
* <li>{@code void} elements ({@code br}, {@code img}, ...) never expect an end
*     tag and never receive one;</li>
* <li>optional end tags ({@code li}, {@code td}, {@code tr}, {@code p},
*     {@code option}, {@code dt}/{@code dd}) are closed when the next tag
*     supersedes them, following the HTML5 supersession rules;</li>
* <li>a block element closes an open {@code p} first, as browsers do.</li>
* </ul>
*
* <p>
* The same input always produces the same output: no external state, no
* randomness, no locale-sensitive comparison ({@link Locale#ROOT} throughout).
* Malformed input is therefore "broken the same way every time".
*
* <p>
* Output is normalised on the way out: element and attribute names are lower-cased,
* attribute values are double-quoted, {@code void} elements are emitted without a
* trailing slash, and a self-closing non-void element ({@code <div/>}) becomes
* {@code <div></div>}. Element order, attribute order and text content are never
* reordered.
*
* <p>
* Whitespace rules are those of {@link SimpleHtmlScanner}: a run touching a tag
* boundary is dropped when it contains a line break, otherwise collapsed to one
* space; {@code pre} and {@code textarea} bodies are kept verbatim.
* {@code script} and {@code style} bodies are minified through
* {@link SimpleJsCompressor} and {@link SimpleCssCompressor}.
*
* <p>
* Not attempted: HTML5 foster parenting (content outside {@code td} inside
* {@code table} stays where it is), implicit {@code html}/{@code head}/{@code body}
* synthesis, and attribute-syntax repairs beyond quoting. Those need a full
* HTML5 tree builder; this class covers the repairs that occur in practice.
*/
public class SimpleHtmlCompressor {

	/** Elements that never have an end tag. */
	private static final String[] VOID_ELEMENTS = {
			"area", "base", "br", "col", "embed", "hr", "img", "input",
			"link", "meta", "param", "source", "track", "wbr"
	};

	/** Block elements that implicitly close an open {@code p} (HTML5 "in body"). */
	private static final String[] BLOCK_CLOSES_P = {
			"address", "article", "aside", "blockquote", "div", "dl", "fieldset",
			"footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header",
			"hgroup", "hr", "main", "nav", "ol", "p", "pre", "section", "table", "ul"
	};

	/** Element whose body is minified as CSS. */
	private static final String TAG_STYLE = "style";

	/** Element whose body is minified as JavaScript. */
	private static final String TAG_SCRIPT = "script";

	/** Element whose body must be kept verbatim. */
	private static final String TAG_PRE = "pre";

	/** Second element whose body must be kept verbatim. */
	private static final String TAG_TEXTAREA = "textarea";

	/** The HTML source being compressed. */
	private String source;

	/**
	* Wraps the given HTML source.
	*
	* @param source HTML text to compress and repair
	*/
	public SimpleHtmlCompressor(String source) {
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
	* Compresses and repairs the wrapped source.
	*
	* @return the minified, balanced HTML, or {@code null} when the source is {@code null}
	* @throws Exception propagated from a body compressor
	*/
	public String compressor() throws Exception {
		return compress(source);
	}

	/**
	* Convenience one-shot over the given source.
	*
	* @param source HTML text to compress and repair
	* @return the minified, balanced HTML, or {@code null} when the source is {@code null}
	* @throws Exception propagated from a body compressor
	*/
	public static String compressor(String source) throws Exception {
		return new SimpleHtmlCompressor(source).compressor();
	}

	/**
	* Runs the scan.
	*
	* @param src source text
	* @return minified, balanced text
	* @throws Exception propagated from a body compressor
	*/
	private String compress(String src) throws Exception {
		if (src == null) {
			return null;
		}
		StringBuilder out = new StringBuilder(src.length());
		StringBuilder ws = new StringBuilder();
		Deque<String> stack = new ArrayDeque<String>();
		int i = 0;
		int n = src.length();

		while (i < n) {
			char c = src.charAt(i);
			if (!isTagStart(src, i)) {
				if (isWhitespace(c)) {
					ws.append(c);
					i++;
					continue;
				}
				flushBeforeText(out, ws);
				out.append(c);
				i++;
				continue;
			}

			flushBeforeTag(out, ws);

			if (src.startsWith("<!--", i)) {
				// Comments carry no rendering effect; dropping them is part of the minification.
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
			if (startsWithAny(src, i, "<!", "<?")) {
				int end = src.indexOf('>', i + 1);
				int stop = (end < 0) ? n : end + 1;
				out.append(src, i, stop);
				i = stop;
				continue;
			}

			int end = findTagEnd(src, i);
			if (end < 0) {
				// Unterminated tag: emit the '<' as text and keep scanning.
				out.append('<');
				i++;
				continue;
			}

			boolean closing = src.charAt(i + 1) == '/';
			String name = tagName(src, i);

			if (closing) {
				int idx = lastIndexOf(stack, name);
				if (idx < 0) {
					// Stray end tag - nothing of that name is open.
					i = end + 1;
					continue;
				}
				while (stack.size() > idx + 1) {
					emitClose(out, stack.removeLast());
				}
				stack.removeLast();
				out.append("</").append(name).append('>');
				i = end + 1;
				continue;
			}

			if (name.length() == 0) {
				out.append(src, i, end + 1);
				i = end + 1;
				continue;
			}

			// Supersession: close whatever the new element implicitly ends.
			while (!stack.isEmpty() && closesBefore(name, stack.peekLast())) {
				emitClose(out, stack.removeLast());
			}

			boolean self = isSelfClosing(src, end);
			boolean empty = isVoid(name);

			out.append(openTag(src, i, end, name));
			i = end + 1;

			if (empty) {
				continue;
			}
			if (self) {
				// <div/> is not void - normalise it to an explicit empty element.
				emitClose(out, name);
				continue;
			}
			if (isRawText(name)) {
				int close = indexOfIgnoreCase(src, "</" + name, i);
				if (close < 0) {
					out.append(rawBody(name, src.substring(i)));
					i = n;
				} else {
					out.append(rawBody(name, src.substring(i, close)));
					int gt = src.indexOf('>', close);
					out.append("</").append(name).append('>');
					i = (gt < 0) ? n : gt + 1;
				}
				continue;
			}
			stack.addLast(name);
		}

		flush(out, ws, true);
		while (!stack.isEmpty()) {
			emitClose(out, stack.removeLast());
		}
		return out.toString();
	}

	/**
	* Emits an end tag for the given element.
	*
	* @param out output buffer
	* @param name lower-cased element name
	*/
	private static void emitClose(StringBuilder out, String name) {
		out.append("</").append(name).append('>');
	}

	/**
	* Normalises one start tag: lower-cased name, quoted attribute values.
	*
	* @param src source text
	* @param from index of the opening {@code <}
	* @param end index of the closing {@code >}
	* @param name lower-cased element name
	* @return the normalised start tag
	*/
	private static String openTag(String src, int from, int end, String name) {
		StringBuilder sb = new StringBuilder(end - from + 4);
		sb.append('<').append(name);
		int p = from + 1 + name.length();
		while (p < end) {
			char ch = src.charAt(p);
			if (isWhitespace(ch) || ch == '/') {
				p++;
				continue;
			}
			int nameStart = p;
			while (p < end) {
				char ch2 = src.charAt(p);
				if (isWhitespace(ch2) || ch2 == '=' || ch2 == '/') {
					break;
				}
				p++;
			}
			if (p == nameStart) {
				p++;
				continue;
			}
			String attribute = src.substring(nameStart, p).toLowerCase(Locale.ROOT);
			while (p < end && isWhitespace(src.charAt(p))) {
				p++;
			}
			if (p < end && src.charAt(p) == '=') {
				p++;
				while (p < end && isWhitespace(src.charAt(p))) {
					p++;
				}
				String value;
				if (p < end && (src.charAt(p) == '"' || src.charAt(p) == '\'')) {
					char quote = src.charAt(p);
					p++;
					int valueStart = p;
					while (p < end && src.charAt(p) != quote) {
						p++;
					}
					value = src.substring(valueStart, Math.min(p, end));
					if (p < end) {
						p++;
					}
				} else {
					int valueStart = p;
					while (p < end && !isWhitespace(src.charAt(p)) && src.charAt(p) != '/') {
						p++;
					}
					value = src.substring(valueStart, p);
				}
				sb.append(' ').append(attribute).append("=\"")
						.append(value.replace("\"", "&quot;")).append('"');
			} else {
				sb.append(' ').append(attribute);
			}
		}
		sb.append('>');
		return sb.toString();
	}

	/**
	* Reports whether the new element implicitly closes the currently open one.
	*
	* @param name new element name
	* @param top currently open element name
	* @return {@code true} when the open element must be closed first
	*/
	private static boolean closesBefore(String name, String top) {
		if ("li".equals(name)) {
			return "li".equals(top);
		}
		if ("dt".equals(name) || "dd".equals(name)) {
			return "dt".equals(top) || "dd".equals(top);
		}
		if ("p".equals(name)) {
			return "p".equals(top);
		}
		if ("tr".equals(name)) {
			return "tr".equals(top);
		}
		if ("td".equals(name) || "th".equals(name)) {
			return "td".equals(top) || "th".equals(top);
		}
		if ("option".equals(name)) {
			return "option".equals(top);
		}
		if ("thead".equals(name) || "tbody".equals(name) || "tfoot".equals(name)) {
			return "thead".equals(top) || "tbody".equals(top) || "tfoot".equals(top);
		}
		if ("p".equals(top)) {
			for (int i = 0; i < BLOCK_CLOSES_P.length; i++) {
				if (BLOCK_CLOSES_P[i].equals(name)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	* Finds the position of an open element, from the top of the stack down.
	*
	* @param stack open elements
	* @param name element name
	* @return the index counted from the bottom, or {@code -1}
	*/
	private static int lastIndexOf(Deque<String> stack, String name) {
		int index = 0;
		int found = -1;
		for (String open : stack) {
			if (open.equals(name)) {
				// Keep the topmost match: a nested element of the same name must
				// be the one that this end tag closes.
				found = index;
			}
			index++;
		}
		return found;
	}

	/**
	* Reports whether the element never has an end tag.
	*
	* @param name lower-cased element name
	* @return {@code true} for the void elements
	*/
	private static boolean isVoid(String name) {
		for (int i = 0; i < VOID_ELEMENTS.length; i++) {
			if (VOID_ELEMENTS[i].equals(name)) {
				return true;
			}
		}
		return false;
	}

	/**
	* Reports whether the element body is raw text.
	*
	* @param name lower-cased element name
	* @return {@code true} for script, style, pre and textarea
	*/
	private static boolean isRawText(String name) {
		return TAG_SCRIPT.equals(name) || TAG_STYLE.equals(name)
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
