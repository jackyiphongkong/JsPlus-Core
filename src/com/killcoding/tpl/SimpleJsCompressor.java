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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based minifier for JavaScript source: removes block and line
 * comments, collapses blank lines, then rejoins lines that end (or
 * start) on an operator character so the output sheds newlines without
 * breaking expression continuation.
 *
 * <p>
 * Lines that still contain a {@code //} comment keep a trailing newline.
 * It is a whitespace/comment compressor, not a parser: string and regex
 * literals are not understood, so it targets already-trusted, generated
 * scripts.
 */
public class SimpleJsCompressor {

	/** The JavaScript source being compressed. */
	private String source;

	/**
	 * Wraps the given JavaScript source.
	 *
	 * @param source JS text to compress
	 */
	public SimpleJsCompressor(String source) {
		super();
		this.source = source;
	}

	/**
	 * Returns the wrapped source.
	 *
	 * @return the original JS text
	 */
	public String getSource() {
		return source;
	}

	/**
	 * Runs the compression pipeline on the wrapped source, rethrowing any
	 * exception.
	 *
	 * @return the minified JS
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
	 * The pipeline: block comments, line comments, blank lines, trailing
	 * spaces, brace spacing, then line joining.
	 *
	 * @param source JS text to compress
	 * @return the minified JS
	 */
	private String compressor(String source) {
		source = removeBatchCommentCompressor(source);
		source = removeSingleCommentCompressor(source);
		source = removeBlankLinesCompressor(source);
		source = removeAllEmptyLinesCompressor(source);
		source = removeEndEmptyCompressor(source);
		source = removeLeftBigParantheses(source);
		source = loopLinesCompressor(source);
		return source;
	}

	/**
	 * Drops the whitespace immediately before each opening brace.
	 *
	 * @param source JS text
	 * @return the text with brace-leading spaces removed
	 */
	private String removeLeftBigParantheses(String source) {
		return source.replaceAll("[^\\S]*\\{", "{");
	}

	/**
	 * Trims trailing whitespace off every line.
	 *
	 * @param source JS text
	 * @return the trimmed text
	 */
	private String removeEndEmptyCompressor(String source) {
		return source.replaceAll("[^\\S]*[ \t]*\r?\n", "\n");
	}

	/**
	 * Deletes whitespace-only and leading empty lines.
	 *
	 * @param source JS text
	 * @return the text without empty lines
	 */
	private String removeAllEmptyLinesCompressor(String source) {
		return source.replaceAll("(?m)^[ \t]*\r?\n|^[^\\S]*", "");
	}

	/**
	 * Collapses runs of blank lines down to a single newline.
	 *
	 * @param source JS text
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
	 * Removes leading C-style block comments, replacing each with a newline.
	 *
	 * @param source JS text
	 * @return the text with block comments removed
	 */
	private String removeBatchCommentCompressor(String source) {
		StringBuffer sb = new StringBuffer();
		String regex = "^\s*\\/\\*((?!\\*\\/).)*\\*\\/";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = pattern.matcher(source);
		while (matcher.find()) {
			matcher.appendReplacement(sb, "\n");
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Removes full-line {@code //} comments, replacing each with a newline.
	 *
	 * @param source JS text
	 * @return the text with line comments removed
	 */
	private String removeSingleCommentCompressor(String source) {
		StringBuffer sb = new StringBuffer();
		String regex = "^[^\\S]*//[^\n]*";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = pattern.matcher(source);
		while (matcher.find()) {
			matcher.appendReplacement(sb, "\n");
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

// 	private String loopLinesCompressorForLastChar(String source) {
// 		List<String> list = Arrays.asList(new String[] { "+", "-", "*", "/", "(", "[", "{", "&", "|", ";", ",", "%",
// 				"!", "=", "<", ">", "!", "?", ":", "." });
// 		StringBuffer sb = new StringBuffer();
// 		String[] lines = source.split("\r?\n");
// 		for (String line : lines) {
// 			if (line.contains("//")) {
// 				sb.append(String.format("%s\n", line));
// 			} else {
// 				int len = line.trim().length();
// 				String lastChar = line.trim().substring(len - 1);
// 				if (list.contains(lastChar)) {
// 					sb.append(line.trim());
// 				} else {
// 					sb.append(String.format("%s\n", line));
// 				}
// 			}

// 		}
// 		return sb.toString();
// 	}

	/**
	 * Trims every line and rejoins them, keeping a newline only when the
	 * line is a comment or neither ends nor starts on an operator character.
	 *
	 * @param source JS text
	 * @return the rejoined, minified text
	 */
	private String loopLinesCompressor(String source) {
		List<String> lastCharList = Arrays.asList(new String[] { "+", "-", "*", "/", "(", "[", "{", "&", "|", ";", ",",
				"%", "!", "=", "<", ">", "!", "?", ":", "." });
		List<String> firstCharList = Arrays.asList(new String[] { "+", "-", "*", "/", "(", ")", "[", "]", "{", "}", "&",
				"|", ";", ",", "%", "!", "=", "<", ">", "!", "?", ":", "." });
		StringBuffer sb = new StringBuffer();
		String[] lines = source.split("\r?\n");
		int rows = lines.length;
		for (int i = 0; i < rows; i++) {
			String line = lines[i];
			int nextRowIndex = i + 1;
			if (nextRowIndex < rows) {
				String nextLine = lines[nextRowIndex];
				String firstChar = nextLine.trim().substring(0, 1);
				String lastChar = line.trim().substring(line.trim().length() - 1);
				boolean lineIsComment = line.contains("//");
				boolean lineIsJoinChar = lastCharList.contains(lastChar);
				boolean nextLineIsJoinChar = firstCharList.contains(firstChar);
				if (lineIsComment) {
					lines[i] = String.format("%s\n", line.trim());
				} else if (lineIsJoinChar) {
					lines[i] = line.trim();
				} else if (nextLineIsJoinChar) {
					lines[i] = line.trim();
				} else {
					lines[i] = String.format("%s\n", line.trim());

				}

			}
		}

		for (int i = 0; i < rows; i++) {
			String line = lines[i];
			sb.append(line);
		}
		return sb.toString();
	}

}