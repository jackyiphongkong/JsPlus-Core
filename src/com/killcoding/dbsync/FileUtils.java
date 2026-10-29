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

package com.killcoding.dbsync;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;


/**
* Small file write helper used by heartbeat logging.
* */
public final class FileUtils {

	/**
	* Writes (or appends) a message to a file, creating parent directories as
	* needed. The file is always written as UTF-8.
	* @param file - the target file
	* @param msg - the content to write
	* @param append - true appends, false truncates and rewrites
	* */
	public static void write(File file, String msg, boolean append) throws IOException {
		OpenOption[] options = null;
		if (append) {
			options = new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					StandardOpenOption.APPEND };
		} else {
			options = new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING };
		}
		BufferedWriter writer = null;
		try {

			Path path = Paths.get(file.toURI());
			Path parentFile = path.toAbsolutePath().getParent();
			if (!Files.isDirectory(parentFile)) {
				Files.createDirectories(parentFile);
			}
			writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, options);
			if (append) {
				writer.append(msg);
			} else {
				writer.write(msg);
			}
			writer.flush();
		} finally {
			if (writer != null)
				writer.close();
		}
	}

}