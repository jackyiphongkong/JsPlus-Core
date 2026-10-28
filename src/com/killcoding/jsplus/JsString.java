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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
* JsString - a GraalJS-friendly wrapper around java.lang.String.
*
* WHY IT EXISTS
* -------------
* GraalJS interop automatically converts every java.lang.String that crosses
* the language boundary into a JS primitive string. A JS primitive string has
* no Java methods, so chains like String.valueOf(s).getBytes("UTF-8") fail
* with "getBytes is not a function" - the caller only ever sees the primitive.
*
* JsString wraps the real java.lang.String inside a plain object. GraalJS never
* converts the wrapper, so every public String method stays callable from JS:
*
*   var JsString = Java.type("com.killcoding.jsplus.JsString");
*   var j = JsString.valueOf("JsPlus-Core 加密");
*   var bytes = j.getBytes("UTF-8");            // -> real byte[], passes to
*                                               //    CipherTools.AESEncrypt etc.
*   var tail = j.substring(3);                  // chained Java String methods
*
* The wrapper deliberately exposes the full public surface of java.lang.String
* (length / charAt / substring / indexOf / replace / split / getBytes / ...)
* plus the common static helpers (valueOf / format / join / copyValueOf).
* */
public class JsString {

	/** The wrapped value. Never null - null inputs become an empty string. */
	private final String value;

	/**
	* Performs the JsString construction operation
	* @param value - the raw java.lang.String to wrap (null becomes empty)
	* */
	public JsString(String value) {
		this.value = (value == null ? "" : value);
	}

	/**
	* Performs the JsString construction operation from a character array
	* @param chars - the source character array
	* */
	public JsString(char[] chars) {
		this(chars == null ? "" : new String(chars));
	}

	/**
	* Performs the JsString construction operation from bytes using the given charset
	* @param bytes - the source byte array
	* @param charsetName - the charset name, e.g. "UTF-8" (null -> UTF-8)
	* */
	public JsString(byte[] bytes, String charsetName) {
		this(bytes == null ? "" : decode(bytes, charsetName));
	}

	/**
	* Performs the JsString construction operation from bytes using the given charset
	* @param bytes - the source byte array
	* @param charset - the charset, e.g. StandardCharsets.UTF_8 (null -> UTF-8)
	* */
	public JsString(byte[] bytes, Charset charset) {
		this(bytes == null ? "" : new String(bytes, charset == null ? StandardCharsets.UTF_8 : charset));
	}

	/**
	* Performs the JsString construction operation from a StringBuilder
	* @param builder - the source builder (null becomes empty)
	* */
	public JsString(StringBuilder builder) {
		this(builder == null ? "" : builder.toString());
	}

	/**
	* Performs the JsString construction operation from a StringBuffer
	* @param buffer - the source buffer (null becomes empty)
	* */
	public JsString(StringBuffer buffer) {
		this(buffer == null ? "" : buffer.toString());
	}

	/**
	* Performs the decode operation - bytes to string with charset fallback
	* @param bytes - the source byte array
	* @param charsetName - the charset name to decode with
	* @return the decoded string
	* */
	private static String decode(byte[] bytes, String charsetName) {
		try {
			return (charsetName == null || charsetName.length() == 0)
					? new String(bytes, StandardCharsets.UTF_8)
					: new String(bytes, charsetName);
		} catch (Exception e) {
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	/* ------------------------------------------------------------------ *
	 *  Static factories (JS entry points)                                *
	 * ------------------------------------------------------------------ */
	/**
	* Performs the wrap operation - the null-safe JS entry point. Unlike valueOf,
	* this factory has a single Object overload, so a JS null resolves without the
	* GraalJS "Multiple applicable overloads found" ambiguity that afflicts the
	* constructor and the primitive valueOf overloads.
	* @param o - the object to wrap (null -> empty string)
	* @return the JsString result
	* */
	public static JsString wrap(Object o) {
		return new JsString(o == null ? "" : String.valueOf(o));
	}

	/**
	* Performs the valueOf operation - wraps an Object as JsString (null -> empty)
	* @param o - the object to wrap
	* @return the JsString result
	* */
	public static JsString valueOf(Object o) {
		return new JsString(o == null ? "" : String.valueOf(o));
	}

	/**
	* Performs the valueOf operation - wraps a char array
	* @param chars - the character array
	* @return the JsString result
	* */
	public static JsString valueOf(char[] chars) {
		return new JsString(chars);
	}

	/**
	* Performs the valueOf operation - wraps a char array slice
	* @param chars - the character array
	* @param offset - the starting offset
	* @param count - the number of characters
	* @return the JsString result
	* */
	public static JsString valueOf(char[] chars, int offset, int count) {
		if (chars == null) {
			return new JsString("");
		}
		try {
			return new JsString(new String(chars, offset, count));
		} catch (Exception e) {
			return new JsString("");
		}
	}

	/**
	* Performs the valueOf operation - wraps a boolean
	* @param b - the boolean value
	* @return the JsString result
	* */
	public static JsString valueOf(boolean b) {
		return new JsString(String.valueOf(b));
	}

	/**
	* Performs the valueOf operation - wraps a character
	* @param c - the character value
	* @return the JsString result
	* */
	public static JsString valueOf(char c) {
		return new JsString(String.valueOf(c));
	}

	/**
	* Performs the valueOf operation - wraps an int
	* @param i - the int value
	* @return the JsString result
	* */
	public static JsString valueOf(int i) {
		return new JsString(String.valueOf(i));
	}

	/**
	* Performs the valueOf operation - wraps a long
	* @param l - the long value
	* @return the JsString result
	* */
	public static JsString valueOf(long l) {
		return new JsString(String.valueOf(l));
	}

	/**
	* Performs the valueOf operation - wraps a float
	* @param f - the float value
	* @return the JsString result
	* */
	public static JsString valueOf(float f) {
		return new JsString(String.valueOf(f));
	}

	/**
	* Performs the valueOf operation - wraps a double
	* @param d - the double value
	* @return the JsString result
	* */
	public static JsString valueOf(double d) {
		return new JsString(String.valueOf(d));
	}

	/**
	* Performs the format operation - printf-style string formatting
	* @param format - the format string
	* @param args - the format arguments
	* @return the JsString result
	* */
	public static JsString format(String format, Object... args) {
		return new JsString(String.format(format, args));
	}

	/**
	* Performs the join operation - joins the elements with a delimiter
	* @param delimiter - the delimiter between elements
	* @param elements - the elements to join
	* @return the JsString result
	* */
	public static JsString join(CharSequence delimiter, CharSequence... elements) {
		return new JsString(String.join(delimiter, elements));
	}

	/**
	* Performs the copyValueOf operation - copies a char array into a JsString
	* @param chars - the character array
	* @return the JsString result
	* */
	public static JsString copyValueOf(char[] chars) {
		return new JsString(String.copyValueOf(chars));
	}

	/* ------------------------------------------------------------------ *
	 *  Instance methods - full java.lang.String public surface            *
	 * ------------------------------------------------------------------ */
	/**
	* Performs the length operation
	* @return the number of characters
	* */
	public int length() {
		return value.length();
	}

	/**
	* Performs the isEmpty operation
	* @return true when the wrapped string has zero length
	* */
	public boolean isEmpty() {
		return value.isEmpty();
	}

	/**
	* Performs the charAt operation
	* @param index - the character index
	* @return the character at the index
	* */
	public char charAt(int index) {
		return value.charAt(index);
	}

	/**
	* Performs the codePointAt operation
	* @param index - the character index
	* @return the Unicode code point at the index
	* */
	public int codePointAt(int index) {
		return value.codePointAt(index);
	}

	/**
	* Performs the getBytes operation - encodes with the platform default charset
	* @return the encoded byte array
	* */
	public byte[] getBytes() {
		return value.getBytes();
	}

	/**
	* Performs the getBytes operation - encodes with the given charset name
	* @param charsetName - the charset name, e.g. "UTF-8"
	* @return the encoded byte array
	* */
	public byte[] getBytes(String charsetName) {
		try {
			return (charsetName == null || charsetName.length() == 0)
					? value.getBytes(StandardCharsets.UTF_8)
					: value.getBytes(charsetName);
		} catch (Exception e) {
			return value.getBytes(StandardCharsets.UTF_8);
		}
	}

	/**
	* Performs the getBytes operation - encodes with the given charset
	* @param charset - the charset (null -> UTF-8)
	* @return the encoded byte array
	* */
	public byte[] getBytes(Charset charset) {
		return value.getBytes(charset == null ? StandardCharsets.UTF_8 : charset);
	}

	/**
	* Performs the equals operation - compares against another JsString or String
	* @param other - the other JsString, String or Object
	* @return true when the wrapped values are equal
	* */
	public boolean equals(Object other) {
		if (other instanceof JsString) {
			return value.equals(((JsString) other).value);
		}
		if (other instanceof String) {
			return value.equals(other);
		}
		return value.equals(other == null ? null : String.valueOf(other));
	}

	/**
	* Performs the equalsIgnoreCase operation
	* @param other - the other JsString or String
	* @return true when the wrapped values are equal ignoring case
	* */
	public boolean equalsIgnoreCase(JsString other) {
		return value.equalsIgnoreCase(other == null ? null : other.value);
	}

	/**
	* Performs the equalsIgnoreCase operation against a native String
	* @param other - the other String (also accepts a JS string directly)
	* @return true when the wrapped values are equal ignoring case
	* */
	public boolean equalsIgnoreCase(String other) {
		return value.equalsIgnoreCase(other);
	}

	/**
	* Performs the compareTo operation
	* @param other - the other JsString
	* @return the lexicographic comparison result
	* */
	public int compareTo(JsString other) {
		return value.compareTo(other == null ? "" : other.value);
	}

	/**
	* Performs the compareTo operation against a native String
	* @param other - the other String (also accepts a JS string directly)
	* @return the lexicographic comparison result
	* */
	public int compareTo(String other) {
		return value.compareTo(other == null ? "" : other);
	}

	/**
	* Performs the compareToIgnoreCase operation
	* @param other - the other JsString
	* @return the case-insensitive comparison result
	* */
	public int compareToIgnoreCase(JsString other) {
		return value.compareToIgnoreCase(other == null ? "" : other.value);
	}

	/**
	* Performs the compareToIgnoreCase operation against a native String
	* @param other - the other String (also accepts a JS string directly)
	* @return the case-insensitive comparison result
	* */
	public int compareToIgnoreCase(String other) {
		return value.compareToIgnoreCase(other == null ? "" : other);
	}

	/**
	* Performs the indexOf operation - searches for a JsString
	* @param search - the string to find
	* @return the first index or -1
	* */
	public int indexOf(JsString search) {
		return value.indexOf(search == null ? null : search.value);
	}

	/**
	* Performs the indexOf operation - searches for a String
	* @param search - the string to find
	* @return the first index or -1
	* */
	public int indexOf(String search) {
		return value.indexOf(search);
	}

	/**
	* Performs the indexOf operation - searches from a start index
	* @param search - the string to find
	* @param fromIndex - the starting index
	* @return the first index at or after fromIndex, or -1
	* */
	public int indexOf(JsString search, int fromIndex) {
		return value.indexOf(search == null ? null : search.value, fromIndex);
	}

	/**
	* Performs the indexOf operation - searches a native String from a start index
	* @param search - the string to find (also accepts a JS string directly)
	* @param fromIndex - the starting index
	* @return the first index at or after fromIndex, or -1
	* */
	public int indexOf(String search, int fromIndex) {
		return value.indexOf(search, fromIndex);
	}

	/**
	* Performs the indexOf operation - searches for a character
	* @param ch - the character to find
	* @return the first index or -1
	* */
	public int indexOf(int ch) {
		return value.indexOf(ch);
	}

	/**
	* Performs the indexOf operation - searches for a character from a start index
	* @param ch - the character to find
	* @param fromIndex - the starting index
	* @return the first index at or after fromIndex, or -1
	* */
	public int indexOf(int ch, int fromIndex) {
		return value.indexOf(ch, fromIndex);
	}

	/**
	* Performs the lastIndexOf operation - searches backwards for a String
	* @param search - the string to find
	* @return the last index or -1
	* */
	public int lastIndexOf(String search) {
		return value.lastIndexOf(search);
	}

	/**
	* Performs the lastIndexOf operation - searches backwards for a JsString
	* @param search - the string to find
	* @return the last index or -1
	* */
	public int lastIndexOf(JsString search) {
		return value.lastIndexOf(search == null ? null : search.value);
	}

	/**
	* Performs the lastIndexOf operation - searches backwards from an index
	* @param search - the string to find
	* @param fromIndex - the starting index
	* @return the last index at or before fromIndex, or -1
	* */
	public int lastIndexOf(String search, int fromIndex) {
		return value.lastIndexOf(search, fromIndex);
	}

	/**
	* Performs the lastIndexOf operation - searches backwards for a character
	* @param ch - the character to find
	* @return the last index or -1
	* */
	public int lastIndexOf(int ch) {
		return value.lastIndexOf(ch);
	}

	/**
	* Performs the lastIndexOf operation - searches backwards for a character from an index
	* @param ch - the character to find
	* @param fromIndex - the starting index
	* @return the last index at or before fromIndex, or -1
	* */
	public int lastIndexOf(int ch, int fromIndex) {
		return value.lastIndexOf(ch, fromIndex);
	}

	/**
	* Performs the substring operation from an index
	* @param beginIndex - the start index
	* @return the JsString tail from beginIndex
	* */
	public JsString substring(int beginIndex) {
		return new JsString(value.substring(beginIndex));
	}

	/**
	* Performs the substring operation between two indices
	* @param beginIndex - the start index
	* @param endIndex - the end index (exclusive)
	* @return the JsString slice
	* */
	public JsString substring(int beginIndex, int endIndex) {
		return new JsString(value.substring(beginIndex, endIndex));
	}

	/**
	* Performs the subSequence operation
	* @param beginIndex - the start index
	* @param endIndex - the end index (exclusive)
	* @return the JsString slice
	* */
	public JsString subSequence(int beginIndex, int endIndex) {
		return new JsString(value.substring(beginIndex, endIndex));
	}

	/**
	* Performs the concat operation
	* @param other - the string to append
	* @return the concatenated JsString
	* */
	public JsString concat(String other) {
		return new JsString(value.concat(other == null ? "" : other));
	}

	/**
	* Performs the replace operation - single character replacement
	* @param oldChar - the character to replace
	* @param newChar - the replacement character
	* @return the replaced JsString
	* */
	public JsString replace(char oldChar, char newChar) {
		return new JsString(value.replace(oldChar, newChar));
	}

	/**
	* Performs the replace operation - substring replacement
	* @param target - the substring to replace
	* @param replacement - the replacement substring
	* @return the replaced JsString
	* */
	public JsString replace(CharSequence target, CharSequence replacement) {
		return new JsString(value.replace(target, replacement));
	}

	/**
	* Performs the replaceAll operation - regex replacement
	* @param regex - the regular expression
	* @param replacement - the replacement string
	* @return the replaced JsString
	* */
	public JsString replaceAll(String regex, String replacement) {
		return new JsString(value.replaceAll(regex, replacement));
	}

	/**
	* Performs the replaceFirst operation - first regex match replacement
	* @param regex - the regular expression
	* @param replacement - the replacement string
	* @return the replaced JsString
	* */
	public JsString replaceFirst(String regex, String replacement) {
		return new JsString(value.replaceFirst(regex, replacement));
	}

	/**
	* Performs the matches operation
	* @param regex - the regular expression
	* @return true when the whole string matches the regex
	* */
	public boolean matches(String regex) {
		return value.matches(regex);
	}

	/**
	* Performs the contains operation
	* @param search - the substring to look for
	* @return true when the substring is present
	* */
	public boolean contains(CharSequence search) {
		return value.contains(search);
	}

	/**
	* Performs the split operation on the regex
	* @param regex - the regular expression
	* @return the split String array
	* */
	public String[] split(String regex) {
		return value.split(regex);
	}

	/**
	* Performs the split operation with a limit
	* @param regex - the regular expression
	* @param limit - the result limit
	* @return the split String array
	* */
	public String[] split(String regex, int limit) {
		return value.split(regex, limit);
	}

	/**
	* Performs the toLowerCase operation
	* @return the lower-cased JsString
	* */
	public JsString toLowerCase() {
		return new JsString(value.toLowerCase(Locale.ROOT));
	}

	/**
	* Performs the toUpperCase operation
	* @return the upper-cased JsString
	* */
	public JsString toUpperCase() {
		return new JsString(value.toUpperCase(Locale.ROOT));
	}

	/**
	* Performs the trim operation
	* @return the trimmed JsString
	* */
	public JsString trim() {
		return new JsString(value.trim());
	}

	/**
	* Performs the strip operation
	* @return the whitespace-stripped JsString
	* */
	public JsString strip() {
		return new JsString(value.strip());
	}

	/**
	* Performs the stripLeading operation
	* @return the leading-whitespace-stripped JsString
	* */
	public JsString stripLeading() {
		return new JsString(value.stripLeading());
	}

	/**
	* Performs the stripTrailing operation
	* @return the trailing-whitespace-stripped JsString
	* */
	public JsString stripTrailing() {
		return new JsString(value.stripTrailing());
	}

	/**
	* Performs the startsWith operation
	* @param prefix - the prefix to check
	* @return true when the string starts with the prefix
	* */
	public boolean startsWith(String prefix) {
		return value.startsWith(prefix);
	}

	/**
	* Performs the startsWith operation at an offset
	* @param prefix - the prefix to check
	* @param offset - the offset to start checking
	* @return true when the prefix occurs at the offset
	* */
	public boolean startsWith(String prefix, int offset) {
		return value.startsWith(prefix, offset);
	}

	/**
	* Performs the endsWith operation
	* @param suffix - the suffix to check
	* @return true when the string ends with the suffix
	* */
	public boolean endsWith(String suffix) {
		return value.endsWith(suffix);
	}

	/**
	* Performs the regionMatches operation
	* @param ignoreCase - whether to ignore case
	* @param toffset - this string's offset
	* @param other - the other string
	* @param ooffset - the other string's offset
	* @param len - the number of characters to compare
	* @return true when the regions match
	* */
	public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {
		return value.regionMatches(ignoreCase, toffset, other, ooffset, len);
	}

	/**
	* Performs the toCharArray operation
	* @return the wrapped string as a char array
	* */
	public char[] toCharArray() {
		return value.toCharArray();
	}

	/**
	* Performs the hashCode operation
	* @return the hash code of the wrapped string
	* */
	public int hashCode() {
		return value.hashCode();
	}

	/**
	* Performs the intern operation
	* @return the interned JsString
	* */
	public JsString intern() {
		return new JsString(value.intern());
	}

	/**
	* Performs the toString operation - unwraps the native Java String
	* @return the wrapped java.lang.String
	* */
	@Override
	public String toString() {
		return value;
	}

	/**
	* Performs the asString operation - alias of toString, explicit unwrap
	* @return the wrapped java.lang.String
	* */
	public String asString() {
		return value;
	}
}
