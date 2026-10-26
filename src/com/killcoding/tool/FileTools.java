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
package com.killcoding.tool;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.nio.file.Files;
import java.io.IOException;

import com.killcoding.file.DiskFile;

public final class FileTools {

	public static boolean deleteDirectory(File directoryToBeDeleted) throws IOException {
	    return new DiskFile(directoryToBeDeleted.getAbsolutePath()).delete();
	}

	public static String text(String filePath) throws IOException {
        return new DiskFile(filePath).readAllString();
	}

	public static String text(File file) throws IOException {
		return text(file.getAbsolutePath());
	}

	public static long size(String filePath) throws IOException {
		return size(new File(filePath));
	}

	public static long size(File file) throws IOException {
		if (file.exists()) {
			Path path = Paths.get(file.getAbsolutePath());
			return Files.size(path);
		} else {
			return 0L;
		}
	}

	public static void write(String filepath, String msg, boolean append) throws IOException {
		write(new File(filepath), msg, append);
	}

	public static void write(File file, String msg, boolean append) throws IOException {
	    writeTo(file, msg, append);
	}

	private static void writeTo(File file, String msg, boolean append) throws IOException {
	    DiskFile df = new DiskFile(file.getAbsolutePath());
	    df.write(msg,append);
	}

}