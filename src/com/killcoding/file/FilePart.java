package com.killcoding.file;

import java.sql.Timestamp;
import java.io.IOException;

public abstract class FilePart {
    
    protected abstract void process(int partIndex,byte[] buffer) throws IOException;
    
    protected abstract void completed(int lastPartIndex,Timestamp modifyTime,long fileSize) throws IOException;
    
    protected abstract void ended(int lastPartIndex,long fileSize);

	public FilePart() {
		super();
	}

}