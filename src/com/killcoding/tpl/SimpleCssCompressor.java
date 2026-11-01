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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based minifier for CSS source: strips comments, collapses blank
 * lines, drops whitespace around braces and repairs declarations missing a
 * semicolon before all newlines are removed.
 *
 * <p>
 * It is a line/whitespace compressor, not a real parser: it does not
 * rename selectors, but semicolon repair and brace-spacing removal keep
 * the result syntactically valid CSS.
 */
public class SimpleCssCompressor {

	/** The CSS source being compressed. */
	private String source;

	/**
	 * Wraps the given CSS source.
	 *
	 * @param source CSS text to compress
	 */
	public SimpleCssCompressor(String source) {
		super();
		this.source = source;
	}

	/**
	 * Returns the wrapped source.
	 *
	 * @return the original CSS text
	 */
	public String getSource() {
		return source;
	}

	/**
	 * Runs the compression pipeline on the wrapped source, rethrowing any
	 * exception.
	 *
	 * @return the minified CSS
	 */
	public String compressor() {
		try {
			String minify = compressor(source);
			return minify;
		} catch (Exception e) {
			throw e;
		}
	}

	/**
	 * The pipeline: comments, blank lines, trailing spaces, brace spacing,
	 * semicolon repair, then newline removal.
	 *
	 * @param source CSS text to compress
	 * @return the minified CSS
	 */
	private String compressor(String source) {
		source = removeCommentCompressor(source);
		source = removeBlankLinesCompressor(source);
		source = removeAllEmptyLinesCompressor(source);
		source = removeEndEmptyCompressor(source);
		source = removeLeftBigParantheses(source);
		source = fixSemicolonCompressor(source);
		source = source.replaceAll("\r?\n", "");
		return source;
	}

	/**
	 * Appends a semicolon to declarations that end a line without one.
	 *
	 * @param source CSS text
	 * @return the text with repaired terminators
	 */
	private String fixSemicolonCompressor(String source) {
		StringBuffer sb = new StringBuffer();
		String regex = "(((?![\\{|\\}|;|,]).){1}(\r?\n){1})";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = pattern.matcher(source);
		while (matcher.find()) {
			String group = matcher.group(1);
			if (group != null) {
				String replaced = String.format("%s;\n", group.trim());
				matcher.appendReplacement(sb, replaced);
			}
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Drops the whitespace immediately before each opening brace.
	 *
	 * @param source CSS text
	 * @return the text with brace-leading spaces removed
	 */
	private String removeLeftBigParantheses(String source) {
		return source.replaceAll("[^\\S]*\\{", "{");
	}

	/**
	 * Trims trailing whitespace off every line.
	 *
	 * @param source CSS text
	 * @return the trimmed text
	 */
	private String removeEndEmptyCompressor(String source) {
		return source.replaceAll("[^\\S]*[ \t]*\r?\n", "\n");
	}

	/**
	 * Deletes whitespace-only and leading empty lines.
	 *
	 * @param source CSS text
	 * @return the text without empty lines
	 */
	private String removeAllEmptyLinesCompressor(String source) {
		return source.replaceAll("(?m)^[ \t]*\r?\n|^[^\\S]*", "");
	}

	/**
	 * Collapses runs of blank lines down to a single newline.
	 *
	 * @param source CSS text
	 * @return the text with single blank-line spacing
	 */
	private String removeBlankLinesCompressor(String source) {
		StringBuffer sb = new StringBuffer();
		String regex = "(\r\n){2,}|\n{2,}";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = pattern.matcher(source);
		while (matcher.find()) {
			matcher.appendReplacement(sb, "\n");
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Removes every C-style block comment, replacing each with a newline.
	 *
	 * @param source CSS text
	 * @return the text with block comments removed
	 */
	private String removeCommentCompressor(String source) {
		StringBuffer sb = new StringBuffer();
		String regex = "\\/\\*((?!\\*\\/).)*\\*\\/";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = pattern.matcher(source);
		while (matcher.find()) {
			matcher.appendReplacement(sb, "\n");
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

}