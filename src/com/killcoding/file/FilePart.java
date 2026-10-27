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

package com.killcoding.file;

import java.sql.Timestamp;
import java.io.IOException;

/**
* Abstract chunk processor callback for large-file splitting: subclasses receive every part via
* process(int,byte[]), a completion notice (completed) with modify time and total size, and a
* final end signal (ended) with the last part index.
* */
public abstract class FilePart {

    /**
    * Performs the process operation
    * @param partIndex - the partIndex
    * @param buffer - the buffer
    * */
    protected abstract void process(int partIndex,byte[] buffer) throws IOException;

    /**
    * Performs the completed operation
    * @param lastPartIndex - the lastPartIndex
    * @param modifyTime - the modifyTime
    * @param fileSize - the fileSize
    * */
    protected abstract void completed(int lastPartIndex,Timestamp modifyTime,long fileSize) throws IOException;

    /**
    * Performs the ended operation
    * @param lastPartIndex - the lastPartIndex
    * @param fileSize - the fileSize
    * */
    protected abstract void ended(int lastPartIndex,long fileSize);

	/**
	* Constructs a new FilePart
	* */
	public FilePart() {
		super();
	}

}
