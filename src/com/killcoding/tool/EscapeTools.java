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
package com.killcoding.tool;

/**
* Facade over CodeEscape / CodeEscapeUtil exposing Java escape / unescape and CSV single-line
* escaping for callers that prefer a single static entry point.
* */
public class EscapeTools {

	/**
	* Constructs a new EscapeTools
	* */
	public EscapeTools() {
		super();
	}

	/**
	* Performs the escapeToSingleLineForCsv operation
	* @param str - the str
	* @return the escapeToSingleLineForCsv result
	* */
	public static String escapeToSingleLineForCsv(String str){
	    return CodeEscape.escapeToSingleLineForCsv(str);
	}

	/**
	* Performs the escape operation
	* @param str - the str
	* @param escapeLevel - the escapeLevel
	* @return the escape result
	* */
	public static String escape(String str,CodeEscapeLevel escapeLevel){
	    return CodeEscapeUtil.escape(str,escapeLevel);
	}

	/**
	* Performs the unescape operation
	* @param encodeStr - the encodeStr
	* @return the unescape result
	* */
	public static String unescape(String encodeStr){
	    return CodeEscapeUtil.unescape(encodeStr);
	}

}
