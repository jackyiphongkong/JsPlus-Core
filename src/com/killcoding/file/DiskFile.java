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
package com.killcoding.file;

import java.io.File;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import com.killcoding.log.Logger;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.nio.channels.SeekableByteChannel;
import java.io.ByteArrayOutputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;
import java.nio.file.DirectoryStream;
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import com.killcoding.tool.CommonTools;
import com.killcoding.file.RemoteFile;
import com.killcoding.log.LoggerFactory;
import java.util.concurrent.Future;
import com.killcoding.tool.ConfigProperties;
import com.killcoding.datasource.Clock;
import java.util.concurrent.Callable;
import com.killcoding.file.DiskFile;
import java.nio.file.attribute.FileAttribute;
import java.text.SimpleDateFormat;
import com.killcoding.cache.CacheArray;
import com.killcoding.cache.CacheArrayFilter;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.Date;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.TimeZone;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.nio.file.FileAlreadyExistsException;
import java.util.LinkedList;

public class DiskFile extends BaseFile {
    
    public static final int RETRY_OPEN_LIMITED = 100;

	protected static Integer MAX_POOL_SIZE = 100;
	private static final Map<String, Long> QUEUE_PATH_MAPPING = new ConcurrentHashMap<String, Long>();

	private static ExecutorService splitPool = null;

	protected boolean copyStructureOnly = false;
	private FileChannel channel = null;
	private FileLock lock = null;
	private long modifyTimeMs = 0L;

	public DiskFile(String path) {
		super(path);
	}
	
	public DiskFile(File origin) {
		super(origin);
	}	

	public static synchronized void initPool(int poolSize) {
		if (splitPool == null) {
			MAX_POOL_SIZE = poolSize;
			LoggerFactory.getLogger(DiskFile.class).mark("MAX_POOL_SIZE={}", MAX_POOL_SIZE);
			splitPool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
		}
	}

	private static synchronized void initDefaultPool() {
		if (splitPool == null) {
			LoggerFactory.getLogger(DiskFile.class).mark("MAX_POOL_SIZE={}", MAX_POOL_SIZE);
			splitPool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
		}
	}

	public void split(int partSize, FilePart filePart) throws IOException {
		if (isLink()) {
			throw new IOException(String.format("The disk file '%s' is a link.", origin.getAbsolutePath()));
		}
		if (isDir()) {
			throw new IOException(String.format("The disk file '%s' is a folder.", origin.getAbsolutePath()));
		}
		if (!exists()) {
			throw new IOException(String.format("The disk file '%s' does not exist.", origin.getAbsolutePath()));
		}
		initDefaultPool();
		splitPool.execute(new Runnable() {
			@Override
			public void run() {
				SeekableByteChannel ch = null;
				int partIndex = -1;
				long fileSize = 0L;
				try {
					Path originPath = Paths.get(origin.toURI());
					Thread.currentThread().setName(String.format("DiskFile-split-%s", origin.getName()));
					ch = Files.newByteChannel(originPath, StandardOpenOption.READ);
					ByteBuffer bf = ByteBuffer.allocate(partSize);
					while (ch.read(bf) != -1) {
						bf.flip();
						partIndex += 1;
						List<Byte> parts = new ArrayList<Byte>();
						while (bf.hasRemaining()) {
							parts.add(bf.get());
						}
						int partsSize = parts.size();
						fileSize += partsSize;
						byte[] part = new byte[partsSize];
						for (int i = 0; i < partsSize; i++) {
							part[i] = parts.get(i);
						}
						filePart.process(partIndex, part);
						bf.clear();
					}
					filePart.completed(partIndex, getModifyTimeForClock(), fileSize);
				} catch (Exception e) {
					LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
				} finally {
					if (ch != null) {
						try {
							ch.close();
						} catch (IOException e) {
							LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
						}
					}
					filePart.ended(partIndex, fileSize);
				}
			}
		});
	}

	@Override
	public void copyTo(String toPath) throws IOException {
		Path originPath = Paths.get(origin.toURI());
		Path destPath = Paths.get(toPath);
		LoggerFactory.getLogger(DiskFile.class).debug("CopyTo: {} -> {}", originPath, destPath);
		Files.copy(originPath, destPath);
	}

	@Override
	public void moveTo(String toPath) throws IOException {
		Path originPath = Paths.get(origin.toURI());
		Path destPath = Paths.get(toPath);
		LoggerFactory.getLogger(DiskFile.class).debug("MoveTo: {} -> {}", originPath, destPath);
		if (Files.exists(destPath)) {
			Files.delete(destPath);
		}
		Files.move(originPath, destPath);
	}

	public RemoteFile writeToRemote(RemoteFile remoteFile) throws IOException {
		return writeToRemote(remoteFile, new Runnable() {
			@Override
			public void run() {

			}
		});
	}

	public RemoteFile writeToRemote(RemoteFile remoteFile, Runnable completedCallback) throws IOException {
		if (remoteFile.exists(true)) {
			throw new IOException(String.format("The remote file '%s' already exists.", remoteFile.getPath()));
		} else {
			remoteFile.copyFrom(copyStructureOnly, this);
			if (isDir()) {
				startFullAsync(0, this.getPath() + "/", remoteFile.getPath() + "/", completedCallback, null);
			}
		}
		return remoteFile;
	}

	protected boolean isLogicModify() throws Exception {
		boolean b = false;
		String tmpPath = toModifyTmpFilePath();

		DiskFile df = null;
		Path logicPath = Paths.get(tmpPath);
		if (Files.exists(logicPath)) {
			df = new DiskFile(tmpPath);
			DiskFile.copyAttrs(df, this.copyStructureOnly, this.syncRoot);
			Timestamp dfModifyTime = df.getModifyTimeForClock();

			long diffMs = Calendar.getInstance().getTimeInMillis() - dfModifyTime.getTime();

			b = diffMs < LOGIC_TIMEOUT_MS;
		}
		return b;
	}

	protected DiskFile logicModify() throws Exception {
		return logicModify(null);
	}

	protected DiskFile logicModify(String msg) throws Exception {
		if (getParentFile().exists()) {
			String tmpLogicPath = toModifyTmpFilePath();

			if (tmpLogicPath == null)
				return null;

			Path logicPath = Paths.get(tmpLogicPath);
			DiskFile df = new DiskFile(tmpLogicPath);
			DiskFile.copyAttrs(df, this.copyStructureOnly, this.syncRoot);

			if (msg == null) {
				df.write(String.format("%s-%s", Thread.currentThread().getName(), Thread.currentThread().getId()),
						false);
			} else {
				df.write(msg, false);
			}

			Files.setLastModifiedTime(logicPath, FileTime.fromMillis(Calendar.getInstance().getTimeInMillis()));
			return df;
		}
		return null;
	}

	protected void removeLogicModify() {
		try {
			String tmpPath = toModifyTmpFilePath();
			if (tmpPath != null) {
				DiskFile df = new DiskFile(tmpPath);
				DiskFile.copyAttrs(df, this.copyStructureOnly, this.syncRoot);
				if (df.exists()) {
					df.delete();
				}
			}
		} catch (Exception e) {
			LoggerFactory.getLogger(DiskFile.class).warn(e.getMessage(), e);
		}
	}

	protected boolean isLogicAccess() throws Exception {
	    boolean b = false;
		String tmpPath = toAccessTmpFilePath();
		DiskFile df = null;
		Path logicPath = Paths.get(tmpPath);
		if (Files.exists(logicPath)) {
			df = new DiskFile(tmpPath);
			DiskFile.copyAttrs(df, this.copyStructureOnly, this.syncRoot);
			Timestamp dfModifyTime = df.getModifyTimeForClock();

			long diffMs = Calendar.getInstance().getTimeInMillis() - dfModifyTime.getTime();

			b = diffMs < LOGIC_ACCESS_TIMEOUT_MS;
		}
		return b;
	}

	protected DiskFile logicAccess() throws Exception {
		return logicAccess(null);
	}

	protected DiskFile logicAccess(String msg) throws Exception {
		if (getParentFile().exists()) {
			String tmpLogicPath = toAccessTmpFilePath();
			Path logicPath = Paths.get(tmpLogicPath);
			DiskFile df = new DiskFile(tmpLogicPath);
			DiskFile.copyAttrs(df, this.copyStructureOnly, this.syncRoot);
			if (msg == null) {
				df.write(String.format("%s-%s", Thread.currentThread().getName(), Thread.currentThread().getId()),
						false);
			} else {
				df.write(msg, false);
			}

			Files.setLastModifiedTime(logicPath, FileTime.fromMillis(Calendar.getInstance().getTimeInMillis()));
			return df;
		}
		return null;
	}

	protected void removeLogicAccess() {
		try {
			String tmpPath = toAccessTmpFilePath();
			if (tmpPath != null) {
				DiskFile df = new DiskFile(tmpPath);
				DiskFile.copyAttrs(df, this.copyStructureOnly, this.syncRoot);
				if (df.exists()) {
					df.delete();
				}
			}
		} catch (Exception e) {
			LoggerFactory.getLogger(DiskFile.class).warn(e.getMessage(), e);
		}
	}

	protected boolean isLogicCheck() throws Exception {
		boolean b = false;
		String tmpPath = toCheckTmpFilePath();
		
		DiskFile df = null;
		Path logicPath = Paths.get(tmpPath);
		if (Files.exists(logicPath)) {
			df = new DiskFile(tmpPath);
			DiskFile.copyAttrs(df, this.copyStructureOnly, this.syncRoot);
			Timestamp dfModifyTime = df.getModifyTimeForClock();
				
			long diffMs = Calendar.getInstance().getTimeInMillis() - dfModifyTime.getTime();

			b = diffMs < LOGIC_CHECK_TIMEOUT_MS;
		}
		return b;
	}

	protected DiskFile logicCheck() throws Exception {
		return logicCheck(null);
	}

	public DiskFile logicCheck(String msg) throws Exception {
    	if (getParentFile().exists()) {
    		String tmpLogicPath = toCheckTmpFilePath();
    		if (tmpLogicPath == null)
    			return null;
    
    		Path logicPath = Paths.get(tmpLogicPath);
    		DiskFile df = new DiskFile(tmpLogicPath);
    		DiskFile.copyAttrs(df, this.copyStructureOnly, this.syncRoot);
    		if (msg == null) {
    			df.write(String.format("%s-%s", Thread.currentThread().getName(), Thread.currentThread().getId()),
    					false);
    		} else {
    			df.write(msg, false);
    		}
    
    		Files.setLastModifiedTime(logicPath, FileTime.fromMillis(Calendar.getInstance().getTimeInMillis()));
    		return df;
    	}
		return null;
	}

	protected void removeLogicCheck() {
	    try{
    		String tmpPath = toCheckTmpFilePath();
    		if (tmpPath != null) {
    			DiskFile df = new DiskFile(tmpPath);
    			DiskFile.copyAttrs(df, this.copyStructureOnly, this.syncRoot);
    			if (df.exists()) {
    				df.delete();
    				DiskFile pdf = new DiskFile(this.getOrigin().getParent());
    				DiskFile.copyAttrs(pdf, this.copyStructureOnly, this.syncRoot);
    				if(pdf.isLogicCheck()){
    				    pdf.removeLogicCheck();
    				}
    			}
    		}
	    }catch(Exception e){
	        LoggerFactory.getLogger(DiskFile.class).warn(e.getMessage(), e);
	    }
	}

	@Override
	public boolean beforeDelete(boolean realDeleted) {
		return true;
	}

	@Override
	public void afterDelete(boolean realDeleted) {

	}

	@Override
	public boolean beforeMkdirs() {
		return true;
	}

	@Override
	public void afterMkdirs() {

	}

	@Override
	public boolean beforeCreateLink(String target) {
		return true;
	}

	@Override
	public void afterCreateLink(String target) {

	}

	@Override
	public boolean beforeWrite() {
		return true;
	}

	@Override
	public void afterWrite() {

	}

	@Override
	public boolean delete() throws IOException {
		return deleteFrom();
	}

	private boolean deleteFrom() throws IOException {
		boolean allowed = beforeDelete(true);
		if (allowed) {
			LoggerFactory.getLogger(DiskFile.class).debug("Delete: {}", getPath());
			if (isFile()) {
				Path originPath = Paths.get(origin.toURI());
				boolean exists = Files.exists(originPath);
				if (exists) {
					Files.deleteIfExists(Paths.get(origin.toURI()));
					afterDelete(true);
					return true;
				}
			}
			if (isDir()) {
				Path originPath = Paths.get(origin.toURI());
				boolean exists = Files.exists(originPath);
				if (exists) {
					Files.walk(originPath).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
					afterDelete(true);
					return true;
				}
			}
			if (isLink()) {
				Path originPath = Paths.get(origin.toURI());
				boolean exists = Files.exists(originPath);
				if (exists) {
					Files.deleteIfExists(Paths.get(origin.toURI()));
					afterDelete(true);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean isFile() throws IOException {
		return this.origin.isFile() && !isLink();
	}

	@Override
	public boolean isDirectory() throws IOException {
		return this.origin.isDirectory() && !isLink();
	}

	@Override
	public boolean isLink() throws IOException {
		return Files.isSymbolicLink(Paths.get(this.origin.toURI()));
	}

	@Override
	public boolean exists() throws IOException {
		return this.origin.exists();
	}

	@Override
	public boolean mkdirs() throws IOException {
		boolean allowed = beforeMkdirs();
		if (allowed) {
			LoggerFactory.getLogger(DiskFile.class).debug("Mkidrs: {}", getPath());
			boolean b = this.origin.mkdirs();
			if (b && modifyTimeMs > 0) {
				setModifyTime(new Timestamp(modifyTimeMs));
			}
			afterMkdirs();
			return b;
		}
		return false;
	}

	@Override
	public boolean write(byte[] data, boolean append) throws IOException {

		boolean allowed = beforeWrite();

		if (!allowed)
			return false;

		if (data == null)
			data = new byte[] {};

		try {

			OpenOption[] options = null;
			if (append) {
				options = new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
						StandardOpenOption.APPEND };
			} else {
				options = new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING };
			}

			File parent = origin.getParentFile();
			if (!parent.exists()) {
				parent.mkdirs();
			}
			int retryCount = 0;
			Path path = Paths.get(origin.toURI());
			while(retryCount < RETRY_OPEN_LIMITED){
			    retryCount++;
			    try{
    			    channel = FileChannel.open(path, options);
    			    if(channel != null){
            			lock = channel.lock();
            			if (lock != null) {
            				ByteBuffer buf = ByteBuffer.wrap(data);
            				channel.write(buf);
            				if (modifyTimeMs > 0) {
            					setModifyTime(new Timestamp(modifyTimeMs));
            				}
            				return true;
            			}
    			    }
			    }catch(Exception e){
			        LoggerFactory.getLogger(DiskFile.class).debug(e.getMessage(),e);
			        
			        if(retryCount >= RETRY_OPEN_LIMITED) throw new IOException(e.getMessage(),e);
			        
			    }
			}
			return false;
		} catch (Exception e) {
			throw e;
		} finally {
			close();
		}
	}

	public boolean manualOpen(boolean append) throws IOException, FileAlreadyExistsException {

		boolean allowed = beforeWrite();

		if (!allowed)
			return false;

		OpenOption[] options = null;
		if (append) {
			options = new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					StandardOpenOption.APPEND };
		} else {
			options = new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING };
		}
		Path path = Paths.get(origin.toURI());
		File parent = origin.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs()) {
			throw new IOException(String.format("Failed to create parent directory: %s", parent));
		}
		if (!Files.exists(path)) {
			try {
				Files.createFile(path);
			} catch (FileAlreadyExistsException e) {
				throw e;
			}
		}
		int retryCount = 0;
		while(retryCount < RETRY_OPEN_LIMITED){
		    retryCount++;
		    try{
		        channel = FileChannel.open(path, options);
		        lock = channel.lock();
		        return lock != null;
		    }catch(Exception e){
		        LoggerFactory.getLogger(DiskFile.class).debug(e.getMessage(),e);
		        
		        if(retryCount >= RETRY_OPEN_LIMITED) throw new IOException(e.getMessage(),e);
		    }
		}
		return false;
	}

	public void manualWrite(byte[] data) throws IOException {
		if (data == null)
			data = new byte[] {};

		if (lock != null) {
			ByteBuffer buf = ByteBuffer.wrap(data);
			channel.write(buf);
			if (modifyTimeMs > 0) {
				setModifyTime(new Timestamp(modifyTimeMs));
			}
		}
	}

	public void manualWrite(String data) throws IOException {
		manualWrite(data.getBytes(CHARSET));
	}

	public void manualClose() throws IOException {
		close();
	}

	@Override
	public boolean write(byte[] data) throws IOException {
		return write(data, false);
	}

	@Override
	public boolean write(String data, boolean append) throws IOException {
		return write(data.getBytes(CHARSET), append);
	}

	public boolean write(String data, String charset, boolean append) throws IOException {
		return write(data.getBytes(charset), append);
	}

	@Override
	public boolean write(String data) throws IOException {
		return write(data.getBytes(CHARSET));
	}

	public boolean write(String data, String charset) throws IOException {
		return write(data.getBytes(charset));
	}

	@Override
	public boolean createLink(String target) throws IOException {
		boolean allowed = beforeCreateLink(target);
		if (allowed) {
			LoggerFactory.getLogger(DiskFile.class).debug("CreateLink: {}", getPath());
			try {
				Path link = Paths.get(origin.toURI());
				Path targetPath = Paths.get(target);
				Files.createSymbolicLink(link, targetPath);
				if (modifyTimeMs > 0) {
					setModifyTime(new Timestamp(modifyTimeMs));
				}
				afterCreateLink(target);
				return true;
			} catch (IOException e) {
				LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
			}
		}
		return false;
	}

	@Override
	public String readLink() throws IOException {
		Path link = Paths.get(origin.toURI());
		return Files.readSymbolicLink(link).toString();
	}

	@Override
	public byte[] readAllBytes() throws IOException {
		return readAllBytesFrom();
	}

	private byte[] readAllBytesFrom() throws IOException {
		if (isLink()) {
			throw new IOException(String.format("The disk file '%s' is a link.", origin.getAbsolutePath()));
		}
		if (isDir()) {
			throw new IOException(String.format("The disk file '%s' is a folder.", origin.getAbsolutePath()));
		}
		if (!exists()) {
			throw new IOException(String.format("The disk file '%s' does not exist.", origin.getAbsolutePath()));
		}
		return Files.readAllBytes(Paths.get(getPath()));
	}

	@Override
	public String readAllString() throws IOException {
		return readAllString(BaseFile.CHARSET);
	}

	public String readAllString(String charset) throws IOException {
		ByteArrayOutputStream baos = null;
		try {
			byte[] bytes = readAllBytes();
			baos = new ByteArrayOutputStream();
			baos.write(bytes);
			baos.flush();
			return baos.toString(charset);
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					Logger.systemError(DiskFile.class, e.getMessage(), e);
					LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
				}
			}
		}
	}

	@Override
	public long size() throws IOException {
		if (isFile()) {
			Path path = Paths.get(origin.getAbsolutePath());
			return Files.size(path);
		}
		return 0L;
	}

	private void close() {
		if (lock != null) {
			try {
				lock.release();
			} catch (IOException e) {
				Logger.systemError(DiskFile.class, e.getMessage(), e);
			}
			try {
				lock.close();
			} catch (IOException e) {
				Logger.systemError(DiskFile.class, e.getMessage(), e);
			} finally {
				lock = null;
			}
		}

		if (channel != null) {
			try {
				channel.close();
			} catch (IOException e) {
				Logger.systemError(DiskFile.class, e.getMessage(), e);
			} finally {
				channel = null;
			}
		}
		afterWrite();
	}

	@Override
	public boolean complete() throws IOException {
		close();
		return true;
	}

	public void setModifyTimeMs(long modifyTimeMs) throws IOException {
		this.modifyTimeMs = modifyTimeMs;
	}

	public void setModifyTimeMsFromClock(long modifyTimeMsFromClock) throws IOException {
		String remoteTzId = new Clock().getCalendar().getTimeZone().getID();
		String localTzId = Calendar.getInstance().getTimeZone().getID();
		Timestamp fileLocalTimestamp = timeZoneConver(localTzId, remoteTzId, new Timestamp(modifyTimeMsFromClock));
		this.modifyTimeMs = fileLocalTimestamp.getTime();
	}

	@Override
	public void setModifyTime(Timestamp modifyTime) throws IOException {
		if (exists()) {
			Path originPath = Paths.get(origin.toURI());
			Files.setLastModifiedTime(originPath, FileTime.fromMillis(modifyTime.getTime()));
		}
	}

	public void setModifyTimeFromClock(Timestamp modifyTimeFromClock) throws IOException {
		String remoteTzId = new Clock().getCalendar().getTimeZone().getID();
		String localTzId = Calendar.getInstance().getTimeZone().getID();
		Timestamp fileLocalTimestamp = timeZoneConver(localTzId, remoteTzId, modifyTimeFromClock);
		setModifyTime(fileLocalTimestamp);
	}

	@Override
	public Timestamp getModifyTime() throws IOException {
		Path originPath = Paths.get(origin.toURI());
		if (Files.exists(originPath)) {
			BasicFileAttributes attr = Files.readAttributes(originPath, BasicFileAttributes.class);
			FileTime fileTime = attr.lastModifiedTime();
			Timestamp fileLocalTimestamp = new Timestamp(fileTime.toMillis());
			return fileLocalTimestamp;
		}
		return null;
	}

	public Timestamp getModifyTimeForClock() throws IOException {
		Path originPath = Paths.get(origin.toURI());
		if (Files.exists(originPath)) {
			BasicFileAttributes attr = Files.readAttributes(originPath, BasicFileAttributes.class);
			FileTime fileTime = attr.lastModifiedTime();
			Timestamp fileLocalTimestamp = new Timestamp(fileTime.toMillis());
			String remoteTzId = new Clock().getCalendar().getTimeZone().getID();
			String localTzId = Calendar.getInstance().getTimeZone().getID();
			Timestamp fileRemoteTimestamp = timeZoneConver(remoteTzId, localTzId, fileLocalTimestamp);
			return fileRemoteTimestamp;
		}
		return null;
	}

	public void startFullAsync(long timerMs, String replaceTo) throws IOException {
		startFullAsync(timerMs, getPath() + "/", replaceTo, null, null);
	}

	public void startFullAsync(long timerMs, String replaceTo, Runnable completedCallback) throws IOException {
		startFullAsync(timerMs, getPath() + "/", replaceTo, completedCallback, null);
	}

	public void startFullAsync(long timerMs, String replaceTo, Runnable completedCallback,
			Runnable checkDeleteCompletedCallback) throws IOException {
		startFullAsync(timerMs, getPath() + "/", replaceTo, completedCallback, checkDeleteCompletedCallback);
	}

	private void startFullAsync(long timerMs, String replaceRootPath, String replaceTo, Runnable completedCallback,
			Runnable checkDeleteCompletedCallback) throws IOException {

		if (!RemoteFile.FIRST_LOADED) {
			RemoteFile firstLoaded = new RemoteFile(replaceTo);
		}

		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					startFullAsyncThread(timerMs, replaceRootPath, replaceTo, completedCallback);
				} catch (Exception e) {
					LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
				}
			}
		};
		Executors.newFixedThreadPool(1).execute(runnable);

		runnable = new Runnable() {
			@Override
			public void run() {
				try {
					startFullSyncForDelete(-1, timerMs, replaceTo, replaceRootPath, checkDeleteCompletedCallback);
				} catch (Exception e) {
					LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
				}
			}
		};
		Executors.newFixedThreadPool(1).execute(runnable);
	}

	private synchronized void startFullSyncForDelete(final Integer filePartDataTable, final long timerMs,
			final String replaceTo, final String replaceRootPath, final Runnable completedCallback) throws IOException {
		DiskFile the = this;
		RemoteFile rootDir = null;
		if (!CommonTools.isBlank(replaceTo)) {
			rootDir = new RemoteFile(replaceTo);
		}
		if (rootDir == null) {
			throw new IOException("The root folder is null.");
		}
		RemoteFile _rootDir = rootDir;
		Integer maxDataTable = rootDir.getMaxFilePartDataTable();

		Integer _filePartDataTable = filePartDataTable;

		if (filePartDataTable > maxDataTable)
			_filePartDataTable = -1;

		final Integer finalFilePartDataTable = _filePartDataTable;

		final CacheArray rows = new CacheArray();

		rows.filter(getScanDeleteFilter(rootDir, finalFilePartDataTable, timerMs, replaceTo, replaceRootPath,
				completedCallback));

		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					_rootDir.listAllForScanDelete(finalFilePartDataTable, rows);
				} catch (Exception e) {
					LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
				}
			}
		};
		Executors.newFixedThreadPool(1).execute(runnable);
	}

	private CacheArrayFilter getScanDeleteFilter(final RemoteFile rootDir, final Integer filePartDataTable,
			final long timerMs, final String replaceTo, final String replaceRootPath,
			final Runnable completedCallback) {
		DiskFile the = this;
		CacheArrayFilter filter = new CacheArrayFilter(CACHE_ARRAY_FILTER_TIMER) {
			@Override
			public void execute(Integer index, Object o) {

				try {
					Map<String, Object> item = (Map<String, Object>) o;
					Object id = item.get("id");
					String parent = (String) item.get("file_parent_path");
					String fileName = (String) item.get("file_name");
					String pf = String.format("%s/%s", parent, fileName);
					RemoteFile rf = new RemoteFile(pf);
					Thread.currentThread()
							.setName(String.format("DiskFile-startFullSyncForDelete-(DataTable=%s Index=%s)-%s",
									filePartDataTable, index, fileName));

					if (memoryUsage() < RemoteFile.MAX_MEMORY_USAGE) {
						String toDf = pf;
						if (!CommonTools.isBlank(replaceRootPath) && !CommonTools.isBlank(replaceTo)) {
							toDf = pf.replaceFirst(replaceTo + "[/]*", replaceRootPath + "/");
						}

						DiskFile df = new DiskFile(toDf);
						DiskFile.copyAttrs(df, the.copyStructureOnly, the.syncRoot);
						boolean isDebug = isDebug(rf);
						boolean isRoot = rootDir.getPath().equals(rf.getPath());
						boolean isSyncedHost = rf.isSyncedOnHostname(id);
						boolean rfExists = rf.exists();

						boolean allowedForceDelete = rootDir.exists() && !isRoot && isSyncedHost && rfExists
								&& !df.exists() && !df.isLogicModify();

						if (allowedForceDelete) {
							df.logicModify();
							if (isDebug) {
								LoggerFactory.getLogger(DiskFile.class).mark("ForceRemoteDelete - {}", rf.getPath());
							}
							rf.forceDeleteAll(true);
							df.removeLogicModify();
						}
					} else {
						LoggerFactory.getLogger(DiskFile.class).warn("Over max memory usage 'MAX_MEMORY_USAGE={}'.",
								RemoteFile.MAX_MEMORY_USAGE);
					}
				} catch (Exception e) {
					LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
				}
			}

			@Override
			public void completed(Integer size) {
				if (isStopSync()) {
					LoggerFactory.getLogger(DiskFile.class).mark("Stoped start full sync for delete.");
				}
				if (timerMs <= 0) {
					LoggerFactory.getLogger(DiskFile.class).mark("Completed start full sync for delete.");
				}

				boolean isRootCompleted = the.getPath().equals(new DiskFile(replaceRootPath).getPath());

				if (isRootCompleted && completedCallback != null) {
					completedCallback.run();
				}

				if (!isStopSync() && timerMs > 0) {
					while (true) {

						if (isStopSync())
							break;

						try {
							Thread.sleep(timerMs);
						} catch (InterruptedException e) {
							LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
						}
						boolean allowed = checkUsage(rootDir);
						if (allowed) {
							try {
								if (!isStopSync()) {
									startFullSyncForDelete(filePartDataTable + 1, timerMs, replaceTo, replaceRootPath,
											completedCallback);
								}
							} catch (IOException e) {
								LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
							}
							break;
						}
					}
				}
			}
		};
		return filter;
	}

	private void startFullAsyncThread(final long timerMs, String replaceRootPath, String replaceTo,
			final Runnable completedCallback) {
		stopSync = false;
		this.setCopyStructureOnly(RemoteFile.COPY_STRUCTURE_ONLY);
		final DiskFile the = this;
		Thread.currentThread().setName(String.format("DiskFile-startFullAsyncThread-%s", the.getOrigin().getName()));
		replaceRootPath = replacePath(replaceRootPath);
		replaceTo = replacePath(replaceTo);
		DiskFile rootDisk = new DiskFile(replaceRootPath);
		boolean rootExists = false;
		try {
			rootExists = rootDisk.exists();
		} catch (Exception e) {
			LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
		}
		if (!rootExists) {
			Logger.systemError(DiskFile.class, "The root path '{}' does not exist.", rootDisk.getPath());
		}
		boolean isRoot = the.getPath().equals(rootDisk.getPath());
		LoggerFactory.getLogger(DiskFile.class).debug("IsRoot: {} - {}", isRoot, the.getPath());
		LoggerFactory.getLogger(DiskFile.class).debug("CacheArrayUsage: {}", CacheArray.getUsage());
		if (rootExists) {

			DirectoryStream<Path> stream = null;
			try {
				if (syncRoot == null) {
					File replaceRootFile = new File(replaceRootPath);
					if (replaceRootFile.getParent() == null) {
						throw new IOException("Cannot sync the root path.");
					}
					String replaceRootParent = replacePath(replaceRootFile.getParent());
					syncRoot = new File(String.format("%s/%s", replaceRootParent, replaceRootFile.getName()));
				}

				if (!CommonTools.isBlank(replaceTo)) {
					RemoteFile rootDir = new RemoteFile(replaceTo);
					if (!rootDir.exists()) {
						rootDir.setModifyTime(this.getModifyTimeForClock());
						rootDir.mkdirs();
					}
				}
				Path originPath = Paths.get(origin.toURI());
				CacheArray rows = new CacheArray();
				CacheArrayFilter filter = getFullAsyncFilter(the, timerMs, replaceRootPath, replaceTo,
						completedCallback);
				rows.filter(filter);
				stream = Files.newDirectoryStream(originPath);
				for (Path p : stream) {
					if (p != null) {
						File f = p.toFile();
						boolean isFile = f.isFile();
						if (isFile) {
							boolean isWritingDf = f.getName().matches(TMP_WRITING_SWP);
							if (isWritingDf) {
								BasicFileAttributes attributes = Files.readAttributes(p, BasicFileAttributes.class);
								FileTime creationTime = attributes.creationTime();
								boolean timeouted = (Calendar.getInstance().getTimeInMillis()
										- creationTime.toMillis()) > (RemoteFile.BATCH_SIZE * LOGIC_TIMEOUT_MS);
								if (timeouted) {
									try {
										boolean exists = Files.exists(p);
										if (exists) {
											Files.deleteIfExists(p);
										}
									} catch (Exception e) {
										LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
									}
								}
							}
						}
						boolean allowedPath = the.checkSyncPathAvailable(p.toString());
						if (!allowedPath) {
							LoggerFactory.getLogger(DiskFile.class).debug("Ignored: {}", p.toString());
						}
						if (allowedPath) {
							rows.add(p);
						}
					}
				}
				rows.add(null);
			} catch (Exception e) {
				LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
				removeLogicAccess();
				if (!(e instanceof NoSuchFileException)) {
					if (!isStopSync() && timerMs > 0) {
						try {
							Thread.sleep(timerMs);
						} catch (InterruptedException ee) {
							LoggerFactory.getLogger(DiskFile.class).error(ee.getMessage(), ee);
						}
						if (!isStopSync()) {
							startFullAsyncThread(timerMs, replaceRootPath, replaceTo, completedCallback);
						}
					}
				}
			} finally {
				if (stream != null) {
					try {
						stream.close();
					} catch (IOException e) {
						LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
					}
				}
			}
		}
	}

	private CacheArrayFilter getFullAsyncFilter(DiskFile the, long timerMs, String replaceRootPath, String replaceTo,
			Runnable completedCallback) {

		return new CacheArrayFilter(CACHE_ARRAY_FILTER_TIMER) {
			RemoteFile rf = null;
			final CacheArrayFilter theFilter = this;

			@Override
			public void completed(Integer size) {
				boolean isRootCompleted = the.getPath().equals(new DiskFile(replaceRootPath).getPath());

				if (isRootCompleted) {
					try {
						if (rf != null && the.isStopSync() && isDebug(rf)) {
							LoggerFactory.getLogger(DiskFile.class).mark("StopedRootScan - {}", the.getPath());
						}
						if (!the.isStopSync() && timerMs > 0) {
							Thread.sleep(timerMs);
							if (!the.isStopSync()) {
								if (rf != null && isDebug(rf)) {
									LoggerFactory.getLogger(DiskFile.class).mark("RootScaning - {}", the.getPath());
								}
								the.startFullAsyncThread(timerMs, replaceRootPath, replaceTo, completedCallback);
							}
						}
						if (completedCallback != null) {
							completedCallback.run();
						}
					} catch (Exception e) {
						LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
					}
				}
			}

			@Override
			public void execute(Integer index, Object o) {
				DiskFile df = null;
				try {
					Path p = (Path) o;
					String pf = replacePath(p.toFile().getAbsolutePath());
					Thread.currentThread().setName(
							String.format("DiskFile-getFullAsyncFilter-%s", p.toFile().getParentFile().getName()));
					String toRf = pf;
					if (!CommonTools.isBlank(replaceRootPath) && !CommonTools.isBlank(replaceTo)) {
						toRf = pf.replaceFirst(replaceRootPath + "[/]*", replaceTo + "/");
					}

					df = new DiskFile(pf);
					DiskFile.copyAttrs(df, the.copyStructureOnly, the.syncRoot);
					rf = new RemoteFile(toRf);

					boolean isRoot = the.getPath().equals(new DiskFile(replaceRootPath).getPath());

					boolean allowedConn = checkUsage(rf);
					String fileName = p.toFile().getName();
					boolean isDebug = isDebug(rf);

					boolean timeouted = rf.isTimeout();
					if (timeouted && RemoteFile.addQueuePathMapping(rf.getPath()) && allowedConn) {
						if (isDebug) {
							LoggerFactory.getLogger(DiskFile.class).mark("ForceDelete(Timeouted) - {}", rf.getPath());
						}
						rf.forceDeleteFile(false);
						RemoteFile.removeQueuePathMapping(rf.getPath());
					}
					Timestamp modifyTime = df.getModifyTimeForClock();
					boolean allowedModifyTime = modifyTime != null;
					boolean allowed = DiskFile.checkQueuePathMapping() && !isStopSync() && allowedModifyTime
							&& allowedConn;

					if (isDebug) {
						debugLog("CheckUsage", rf, this);
					}

					if (!isRoot && allowed) {
						allowed = !df.isLogicCheck();
					}

					if (allowed) {

						if (!isRoot)
							df.logicCheck();

						rf.setModifyTime(modifyTime);
						long dfModifyTimeMs = modifyTime.getTime();
						boolean blocked = false;
						boolean isRealDelete = rf.isLastOperateRealDelete();

						if (isRealDelete) {
							String clientHostname = CommonTools.getHostname();
							String delHostname = rf.getLastSourceHostname();
							boolean isOwnerDel = clientHostname.equals(delHostname);
							if (isOwnerDel) {
								Timestamp deletedTime = rf.getLastOperateTime();
								if (deletedTime != null) {
									boolean waiting = new Clock().getTime() - deletedTime.getTime() <= LOGIC_TIMEOUT_MS;
									if (waiting) {
										blocked = true;
									}
								}
							} else {
								Timestamp deletedTime = rf.getLastOperateTime();
								if (deletedTime != null) {
									boolean olded = modifyTime.getTime() < deletedTime.getTime();
									if (olded) {
										blocked = true;
									}
								}
							}
						}

						boolean haveParent = rf.getParentFile().exists();
						if (!blocked && haveParent) {
							if (df.isLink()) {
								if (!df.isLogicAccess()) {
									df.logicAccess();
									if (rf.exists()) {
										if (rf.isLink()) {
											long rfModifyTimeMs = rf.getModifyTime().getTime();
											boolean changedTime = dfModifyTimeMs > rfModifyTimeMs;
											boolean changedValue = !rf.readLink().equals(df.readLink());
											boolean changed = changedTime && changedValue;
											if (changed) {
												RemoteFile prf = rf.getParentFile();
												if (prf != null && prf.exists()) {
													if (isDebug) {
														LoggerFactory.getLogger(DiskFile.class).mark("CreateLink - {}",
																rf.getPath());
													}
													rf.forceDeleteLink(false);
													rf.createLink(df.readLink());
												}
											}
										} else {
											RemoteFile prf = rf.getParentFile();
											if (prf != null && prf.exists()) {
												if (isDebug) {
													LoggerFactory.getLogger(DiskFile.class).mark("CreateLink - {}",
															rf.getPath());
												}
												rf.forceDeleteLink(false);
												rf.createLink(df.readLink());
											}
										}
									} else {
										RemoteFile prf = rf.getParentFile();
										if (prf != null && prf.exists()) {
											if (isDebug) {
												LoggerFactory.getLogger(DiskFile.class).mark("CreateLink - {}",
														rf.getPath());
											}
											rf.createLink(df.readLink());
										}
									}
								}
							} else if (df.isFile()) {
								if (!df.isLogicModify() && !df.isLogicAccess()) {
									if (rf.exists()) {
										if (rf.isFile()) {
											long rfModifyTimeMs = rf.getModifyTime().getTime();
											if (dfModifyTimeMs > rfModifyTimeMs) {
												RemoteFile prf = rf.getParentFile();
												if (prf != null && prf.exists()) {
													if (DiskFile.addQueuePathMapping(df.getPath())) {
														if (isDebug) {
															LoggerFactory.getLogger(DiskFile.class)
																	.mark("RemoteQueuing - {}", df.getPath());
														}
														df.logicAccess();
														rf.forceDeleteFile(false);
														df.writeToRemote(rf);
													} else {
														df.removeLogicAccess();
														df.removeLogicCheck();
													}
												}
											}
										} else {
											rf.forceDeleteFile(false);
										}
									} else {
										RemoteFile prf = rf.getParentFile();
										if (prf != null && prf.exists()) {
											if (DiskFile.addQueuePathMapping(df.getPath())) {
												if (isDebug) {
													LoggerFactory.getLogger(DiskFile.class).mark("RemoteQueuing - {}",
															df.getPath());
												}
												df.logicAccess();
												df.writeToRemote(rf);
											} else {
												df.removeLogicAccess();
												df.removeLogicCheck();
											}
										}
									}
								}
							} else if (df.isDir()) {
								if (isStopSync() && isDebug) {
									LoggerFactory.getLogger(DiskFile.class).mark("Stopping queue");
								}
								if (!isStopSync()) {
									boolean allowedPoolSize = theFilter.getCacheArray().getUsingPoolSize() < theFilter
											.getCacheArray().getMaxPoolSize() && checkUsage(rf);
									if (isDebug && allowedPoolSize) {
										LoggerFactory.getLogger(DiskFile.class).debug("Scaning - {}", df.getPath());
									}
									if (isDebug && !allowedPoolSize) {
										LoggerFactory.getLogger(DiskFile.class).debug("Skiped - {}", df.getPath());
									}
									if (rf.exists()) {
										if (rf.isDir()) {
											if (isRoot || allowedPoolSize) {
												df.startFullAsyncThread(timerMs, replaceRootPath, replaceTo,
														completedCallback);
											}
										} else {
											if (rf.isDir())
												rf.forceDeleteDir(false);

											if (rf.isFile())
												rf.forceDeleteFile(false);

											if (rf.isLink())
												rf.forceDeleteLink(false);

											RemoteFile prf = rf.getParentFile();
											if (isRoot || (prf != null && prf.exists())) {
												if (isDebug) {
													LoggerFactory.getLogger(DiskFile.class)
															.mark("ReCreateRemoteDir - {}", rf.getPath());
												}
												rf.mkdirs();
												if (isRoot || allowedPoolSize) {
													df.startFullAsyncThread(timerMs, replaceRootPath, replaceTo,
															completedCallback);
												}
											}
										}
									} else {
										RemoteFile prf = rf.getParentFile();
										if (isRoot || (prf != null && prf.exists())) {
											if (isDebug) {
												LoggerFactory.getLogger(DiskFile.class).mark("CreateRemoteDir - {}",
														rf.getPath());
											}
											rf.mkdirs();
											if (isRoot || allowedPoolSize) {
												df.startFullAsyncThread(timerMs, replaceRootPath, replaceTo,
														completedCallback);
											}
										}
									}
									if (!isRoot && !allowedPoolSize) {
										df.removeLogicCheck();
									}
								}
							}
						}
					}
				} catch (Exception e) {
					LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
					if (df != null) {
						df.removeLogicAccess();
					}
				}
			}

		};
	}

	public void setCopyStructureOnly(boolean copyStructureOnly) {
		this.copyStructureOnly = copyStructureOnly;
	}

	public boolean isCopyStructureOnly() {
		return this.copyStructureOnly;
	}

	private boolean isViEditSwp(String fileName) {
		return fileName.matches(TMP_MATCHES_EDIT) || fileName.matches(TMP_MATCHES_VI_SWP);
	}

	protected static DiskFile copyAttrs(DiskFile source, boolean _copyStructureOnly, File _syncRoot) {
		source.setCopyStructureOnly(_copyStructureOnly);
		source.syncRoot = _syncRoot;
		return source;
	}

	public File getParentFile() {
		return new File(getParent());
	}

	protected boolean checkSyncPathAvailable(String path) {
		try {
			return checkSyncPathAvailable(path, RemoteFile.SHOW_HIDDEN, RemoteFile.SHOW_LINK, RemoteFile.MAX_FILE_SZIE,
					SYNC_PATH_ALLOWED, SYNC_PATH_IGNORED);
		} catch (Exception e) {
			LoggerFactory.getLogger(DiskFile.class).error(e.getMessage(), e);
			return false;
		}
	}

	protected static synchronized boolean addQueuePathMapping(String path) {
		Long addTimeMs = null;
		boolean allowed = checkQueuePathMapping();

		if (!allowed)
			return false;

		addTimeMs = QUEUE_PATH_MAPPING.get(path);

		if (addTimeMs == null) {
			QUEUE_PATH_MAPPING.put(path, System.currentTimeMillis());
			return true;
		}

		return false;
	}

	public static synchronized boolean checkQueuePathMapping() {
		Long addTimeMs = null;
		Map<String, Long> copyQueueMap = getQueuePathMapping();
		List<String> keyList = new ArrayList<String>(copyQueueMap.keySet());
		for (String key : keyList) {
			addTimeMs = copyQueueMap.get(key);
			if (addTimeMs != null) {
				boolean timeout = (System.currentTimeMillis() - addTimeMs) >= LOGIC_TIMEOUT_MS;

				if (timeout) {
					QUEUE_PATH_MAPPING.remove(key);
				}
			}
		}

		if (QUEUE_PATH_MAPPING.keySet().size() >= RemoteFile.MAX_QUEUE_SIZE)
			return false;

		return true;
	}

	protected static synchronized void removeQueuePathMapping(String path) {
		QUEUE_PATH_MAPPING.remove(path);
	}

	public static synchronized Map<String, Long> getQueuePathMapping() {
		return Collections.synchronizedMap(QUEUE_PATH_MAPPING);
	}

	private static void debugLog(String point, RemoteFile rf, CacheArrayFilter filter) {
		debugLog(DiskFile.class, point, rf, filter);
	}

}