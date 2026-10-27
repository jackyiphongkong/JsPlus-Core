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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.nio.file.Files;
import java.io.IOException;

import com.killcoding.file.DiskFile;

/**
* File utility helpers (static): URI / path resolution and filesystem operations used by the file
* and jsplus packages.
* */
public final class FileTools {

	/**
	* Performs the deleteDirectory operation
	* @param directoryToBeDeleted - the directoryToBeDeleted
	* @return the deleteDirectory result
	* */
	public static boolean deleteDirectory(File directoryToBeDeleted) throws IOException {
	    return new DiskFile(directoryToBeDeleted.getAbsolutePath()).delete();
	}

	/**
	* Performs the text operation
	* @param filePath - the filePath
	* @return the text result
	* */
	public static String text(String filePath) throws IOException {
        return new DiskFile(filePath).readAllString();
	}

	/**
	* Performs the text operation
	* @param file - the file
	* @return the text result
	* */
	public static String text(File file) throws IOException {
		return text(file.getAbsolutePath());
	}

	/**
	* Performs the size operation
	* @param filePath - the filePath
	* @return the size result
	* */
	public static long size(String filePath) throws IOException {
		return size(new File(filePath));
	}

	/**
	* Performs the size operation
	* @param file - the file
	* @return the size result
	* */
	public static long size(File file) throws IOException {
		if (file.exists()) {
			Path path = Paths.get(file.getAbsolutePath());
			return Files.size(path);
		} else {
			return 0L;
		}
	}

	/**
	* Performs the write operation
	* @param filepath - the filepath
	* @param msg - the msg
	* @param append - the append
	* */
	public static void write(String filepath, String msg, boolean append) throws IOException {
		write(new File(filepath), msg, append);
	}

	/**
	* Performs the write operation
	* @param file - the file
	* @param msg - the msg
	* @param append - the append
	* */
	public static void write(File file, String msg, boolean append) throws IOException {
	    writeTo(file, msg, append);
	}

	/**
	* Performs the writeTo operation
	* @param file - the file
	* @param msg - the msg
	* @param append - the append
	* */
	private static void writeTo(File file, String msg, boolean append) throws IOException {
	    DiskFile df = new DiskFile(file.getAbsolutePath());
	    df.write(msg,append);
	}

}
