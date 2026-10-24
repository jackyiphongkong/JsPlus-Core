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
import com.killcoding.log.LoggerFactory;
import com.killcoding.log.Logger;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import com.killcoding.tool.CommonTools;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.killcoding.cache.CacheArrayFilter;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.LinkOption;

public abstract class BaseFile {
    
    protected static final String TMP_MATCHES_EDIT = "^.*\\..*~$";
    protected static final String TMP_MATCHES_VI_SWP = "^.*\\..*\\.sw[xp]$";

	protected static final String TMP_EXT_MODIFY_FILE = ".modify.tmp~";
	protected static final String TMP_EXT_ACCESS_FILE = ".access.tmp~";
	protected static final String TMP_EXT_CHECK_FILE = ".check.tmp~";
	
	protected static final String TMP_WRITING_SWP = "^.*\\.writing.tmp~$";
	protected static final String TMP_WRITING_FOR_REPLACE = "\\.writing\\.tmp~";
	protected static final String TMP_WRITING_FILE = ".writing.tmp~";
	
	public static String CHARSET = "UTF-8";

	public abstract boolean isFile() throws IOException;
	
	public abstract boolean isDirectory() throws IOException;
	
	public abstract boolean isLink() throws IOException;

	public abstract boolean delete() throws IOException;

	public abstract boolean exists() throws IOException;

	public abstract boolean mkdirs() throws IOException;
	
	public abstract boolean createLink(String target) throws IOException;
	
	public abstract String readLink() throws IOException;

	public abstract boolean write(byte[] data, boolean append) throws IOException;

	public abstract boolean write(byte[] data) throws IOException;

	public abstract boolean write(String data, boolean append) throws IOException;

	public abstract boolean write(String data) throws IOException;

	public abstract byte[] readAllBytes() throws IOException;
	
	public abstract String readAllString() throws IOException;
	
	public abstract long size() throws IOException;
	
	public abstract boolean complete() throws IOException;
	
	public abstract Timestamp getModifyTime() throws IOException;
	
	public abstract void setModifyTime(Timestamp modifyTime) throws IOException;
	
	public abstract void copyTo(String toPath) throws IOException;
	
	public abstract void moveTo(String toPath) throws IOException;

	public abstract boolean beforeDelete(boolean realDeleted);
	
	public abstract void afterDelete(boolean realDeleted);
		
	public abstract boolean beforeMkdirs();
	
	public abstract void afterMkdirs();
	
	public abstract boolean beforeCreateLink(String target);
	
	public abstract void afterCreateLink(String target);	
	
	public abstract boolean beforeWrite();
	
	public abstract void afterWrite();
	
	public static long CACHE_ARRAY_FILTER_TIMER = 10L;
	
	public static long LOGIC_TIMEOUT_MS = 300000L;
	
	public static long LOGIC_ACCESS_TIMEOUT_MS = 60000L;
	
	public static long LOGIC_CHECK_TIMEOUT_MS = 60000L;
	
	protected static boolean stopSync = true;
	
	protected File syncRoot = null;

	protected File origin;
	
	public static Path TEMP_DIR = null;
	
	private static long debugLogTime = 0L;
	
	public static final List<String> SYNC_PATH_ALLOWED = new ArrayList<String>();
	public static final List<String> SYNC_PATH_IGNORED = new ArrayList<String>();	
	
	static {
	    try{
    		SYNC_PATH_IGNORED.add(TMP_MATCHES_EDIT);
    		SYNC_PATH_IGNORED.add(TMP_MATCHES_VI_SWP);
    		
    		String tmpdir = System.getProperty("java.io.tmpdir");
    		Path tmpBfDir = Paths.get(String.format("%s/BaseFile",tmpdir));
    		if(!Files.exists(tmpBfDir)){
    		    Files.createDirectories(tmpBfDir);
    		}
    		Path pidDir = Paths.get(String.format("%s/%s/",tmpBfDir.toString(),ProcessHandle.current().pid()));
    		if(TEMP_DIR == null) TEMP_DIR = Files.createDirectories(pidDir);
    		
	    }catch(IOException e){
	        Logger.systemError(BaseFile.class,e.getMessage(),e);
	    }
	}

	public BaseFile(String path) {
		super();
		origin = new File(path);
	}
	
	public BaseFile(File origin) {
		super();
		this.origin = origin;
	}	
	
	public static void setTmpDir(String tmpDir) throws IOException {
	    TEMP_DIR = Files.createDirectories(Paths.get(tmpDir));
	}

	public String getPath() {
        return parsePath(String.format("%s/%s",getParent(),origin.getName()));
	}
	
	public File getOrigin(){
	    return origin;
	}
	
	public String getParent() {
		String parent = CommonTools.isBlank(origin.getParent()) ? "/" : origin.getParent();
        return replacePath(parent);
	}
	
	public List<String> getParents() {
	    List<String> parents = new ArrayList<String>();
	    String path = getPath();
	    do{
	        File file = new File(path);
	        path = file.getParent();
	        
	        if(path == null) break;
	        
            parents.add(path);
	    }while(path != null);
	    
	    return parents;
	}
	
	public void write(int partSize,byte[] allBytes) throws IOException {
	    try{
    	    ByteBuffer bf = ByteBuffer.allocate(partSize);
    	    ByteArrayInputStream bais = new ByteArrayInputStream(allBytes);
    	    int partIndex = -1;
    	    while(bais.read() != -1){
    	        bf.flip();
    			partIndex += 1;
    			List<Byte> parts = new ArrayList<Byte>();
    			while (bf.hasRemaining()) {
    				parts.add(bf.get());
    			}
    			bf.clear();
    			int partsSize = parts.size();
    			byte[] part = new byte[partsSize];
    			for (int i = 0; i < partsSize; i++) {
    				part[i] = parts.get(i);
    			}
    			write(part,true);
    	    }
	    }finally{
	        complete();
	    }
	}	
	
    protected String toModifyTmpFilePath() throws IOException {
        if(syncRoot == null) throw new IOException("The 'BaseFile.syncRoot' is null.");
        
        File syncFile = new File(getPath());
        if(syncRoot != null){
            String syncRootParent = replacePath(syncRoot.getParent()) + "/";
            String replacedFilePath = TEMP_DIR.toAbsolutePath() + "/." + replacePath(syncFile.getAbsolutePath()).replaceFirst(syncRootParent,"") + TMP_EXT_MODIFY_FILE;
            return replacedFilePath;
        }
        return null;  
    }  
    
    protected String toAccessTmpFilePath() throws IOException {
        if(syncRoot == null) throw new IOException("The 'BaseFile.syncRoot' is null.");
        
        File syncFile = new File(getPath());
        if(syncRoot != null){
            String syncRootParent = replacePath(syncRoot.getParent()) + "/";
            String replacedFilePath = TEMP_DIR.toAbsolutePath() + "/." + replacePath(syncFile.getAbsolutePath()).replaceFirst(syncRootParent,"") + TMP_EXT_ACCESS_FILE;
            return replacedFilePath;
        }
        return null;            
    }  

    protected String toCheckTmpFilePath() throws IOException {
        if(syncRoot == null) throw new IOException("The 'BaseFile.syncRoot' is null.");
        
        File syncFile = new File(getPath());
        if(syncRoot != null){
            String syncRootParent = replacePath(syncRoot.getParent()) + "/";
            String replacedFilePath = TEMP_DIR.toAbsolutePath() + "/." + replacePath(syncFile.getAbsolutePath()).replaceFirst(syncRootParent,"") + TMP_EXT_CHECK_FILE;
            return replacedFilePath;
        }
        return null;            
    }  
    
	public synchronized static void stopSync() {
		LoggerFactory.getLogger(DiskFile.class).mark("Sending stop command.");
    	stopSync = true;
		for (int i = 0; i < 29; i++) {
    		try{
    		    if(!isStopSync()) {
    		        stopSync();
    		        break;
    		    }
    		    Thread.sleep(1000L);
    		}catch(InterruptedException e){
    		    LoggerFactory.getLogger(BaseFile.class).error(e.getMessage(),e);
    		}
		}
		LoggerFactory.getLogger(DiskFile.class).mark("Sent stop command");
	}
	
    public static boolean isStopSync(){
	    DiskFile.checkQueuePathMapping();
	    RemoteFile.checkQueuePathMapping();        
        return stopSync && DiskFile.getQueuePathMapping().isEmpty() && RemoteFile.getQueuePathMapping().isEmpty();
    }
    
    protected static boolean isDebug(RemoteFile remoteFile){
        boolean debug = false;
        if(remoteFile != null){
            boolean fileDebug = remoteFile.getOrigin().getName().matches("^(\\.{0,1}DEBUG[\\.\\d\\w]*)$");
            boolean logDeubg = remoteFile.getConfigProperties().getBoolean("LogCompletedStatus", false);
    		debug = fileDebug || logDeubg;
        }
        return debug || LoggerFactory.getLogger(BaseFile.class).isDebugEnabled();
    }
    
    public static boolean isHidden(String path) throws Exception{
        Path checkPath = Paths.get(path);

        if(!Files.exists(checkPath)) return false;
        
        while(checkPath != null){

            boolean hide = Files.isHidden(checkPath);
            
            if(hide) return true;
            
            checkPath = checkPath.getParent();
            
            
        }
		return false;
    } 
    
    public static boolean checkSyncPathAvailable(String path, boolean showHidden,boolean showLink,long maxFileSize, List<String> syncPathAllowed,
			List<String> syncPathIgnored) {
			    
		if(maxFileSize <= 0) return false;
		
		try {
			boolean matchedAllowed = true;
			boolean matchedIgnored = false;
			Path fullPath = Paths.get(path);
			
			if(!showHidden){
			    matchedAllowed = !isHidden(path);
			}
			
			if(matchedAllowed && !showLink && Files.exists(fullPath)){
			    boolean isLink = Files.isSymbolicLink(fullPath);
			    matchedAllowed = !isLink;
			}			
		
			if (matchedAllowed && syncPathAllowed != null) {
				for (String regex : syncPathAllowed) {
					if (!CommonTools.isBlank(regex)) {
						matchedAllowed = path.matches(regex);

						if (matchedAllowed)
							break;
					}
				}
			}
			if (matchedAllowed && syncPathIgnored != null) {
				for (String regex : syncPathIgnored) {
					if (!CommonTools.isBlank(regex)) {
						matchedIgnored = path.matches(regex);

						if (matchedIgnored)
							break;
					}
				}
			}

			boolean b = matchedAllowed && !matchedIgnored;
			if (b) {
				Path checkPath = Paths.get(path);
				if (Files.exists(checkPath)) {
				    
				    int parentLength = checkPath.getParent().toString().length();
				    int fileNamelength = checkPath.getFileName().toString().length();
				    
				    b = parentLength <= RemoteFile.MAX_FILE_PARENT_PATH_LENGTH && fileNamelength <= RemoteFile.MAX_FILE_NAME_LENGTH;
				    
					if (b && Files.isRegularFile(checkPath)) {
						long fileSize = Files.size(checkPath);
						b = fileSize <= maxFileSize;
					}
				}else{
				    b = false;
				}
			}
			
			if(b) {
        		Path checkPath = Paths.get(path);
        		if (Files.exists(checkPath)) {
        		    boolean isLink = Files.isSymbolicLink(checkPath);
        		    boolean isFile = checkPath.toFile().isFile();
        		    if(isFile && !isLink){
            		    if(RemoteFile.SYNC_CUTOFF_TIME == null){
            		        b = false;
            		    } else{
            		        long syncCutoffTimeMs = 0L;
            		        syncCutoffTimeMs = RemoteFile.SYNC_CUTOFF_TIME.getTime();
                			BasicFileAttributes attr = Files.readAttributes(checkPath, BasicFileAttributes.class);
                			FileTime fileTime = attr.lastModifiedTime();
                			
                            b = fileTime.toMillis() >= syncCutoffTimeMs;
            		    }
        		    }
        		}else{
        		    b = false;
        		}
			}
			
			if (!b) {
				LoggerFactory.getLogger(BaseFile.class).debug("Unavailable: {}", path);
			}
			return b;
		} catch (Exception e) {
			LoggerFactory.getLogger(BaseFile.class).error(e.getMessage(), e);
			return false;
		}
	}
	
	public boolean isDir() throws IOException {
	    return isDirectory();
	}	
	
	public Timestamp timeZoneConver(String destTimeZone, String srcTimeZone, Timestamp srcDate) throws IOException {
	    try{
    	    String pattern = "yyyy-MM-dd HH:mm:ss.SSS";
    		SimpleDateFormat fullFormat = new SimpleDateFormat(pattern);
    		DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern);	
    		String srcDateStr = fullFormat.format(srcDate);
    		LocalDateTime ldt = LocalDateTime.parse(srcDateStr, DateTimeFormatter.ofPattern(pattern));
    		ZoneId srcZoneId = ZoneId.of(srcTimeZone);
    		ZonedDateTime srcDateTime = ldt.atZone(srcZoneId);
    		ZoneId destZoneId = ZoneId.of(destTimeZone);
    		ZonedDateTime destDateTime = srcDateTime.withZoneSameInstant(destZoneId);
    		return new Timestamp(fullFormat.parse(format.format(destDateTime)).getTime());
	    }catch(Exception e){
	       throw new IOException(e.getMessage(),e);
	    }
	}	
	
	protected static String replacePath(String source){
	    return source.replaceAll("\\\\", "/");
	}
	
	protected static String parsePath(String source){
	    return source.replaceAll("\\\\", "/").replaceAll("//","/");
	}

    public static boolean checkUsage(RemoteFile rf){
        return memoryUsage() < RemoteFile.MAX_MEMORY_USAGE && rf.getDataSourceWriter().getUsage() < RemoteFile.MAX_CONNECT_USAGE && rf.getDataSourceReader().getUsage() < RemoteFile.MAX_CONNECT_USAGE;
    }
    

    public static long memoryUsing(){
        int mb = 1024 * 1024;
		Runtime instance = Runtime.getRuntime();
		long totalMemory = instance.totalMemory() / mb;
		long freeMemory = instance.freeMemory() / mb;
		return  (instance.totalMemory() - instance.freeMemory()) / mb;
    }	

    public static double memoryUsage(long using){
        int mb = 1024 * 1024;
		Runtime instance = Runtime.getRuntime();
		long maxMemory = instance.maxMemory() / mb;
		return (using * 1.0D / maxMemory * 1.0D);
    }	
    
    public static double memoryUsage(){
        return memoryUsage(memoryUsing());
    }    
    
	protected synchronized static void debugLog(Class clazz,String point,RemoteFile rf,CacheArrayFilter filter){
	    long ctm = System.currentTimeMillis();
	    boolean canWrite = (ctm - debugLogTime) >= 60000L;
	    if(canWrite){
	        DiskFile.checkQueuePathMapping();
	        RemoteFile.checkQueuePathMapping();
	        debugLogTime = ctm;
	        long memoryUsing = memoryUsing();
	        String readerUsage = String.format("%.2f%%",rf.getDataSourceReader().getUsage()*100);
	        String writerUsage = String.format("%.2f%%",rf.getDataSourceWriter().getUsage()*100);
	        String memoryUsage = String.format("%.2f%%",memoryUsage(memoryUsing)*100);
	        LoggerFactory.getLogger(clazz).mark("************{}************",point);
    		LoggerFactory.getLogger(clazz).mark("[{}] DiskQueuePathMapping - {}",point,DiskFile.getQueuePathMapping().keySet().size());
    	    LoggerFactory.getLogger(clazz).mark("[{}] RemoteQueuePathMapping - {}",point,RemoteFile.getQueuePathMapping().keySet().size());
    		LoggerFactory.getLogger(clazz).mark("[{}] CacheArrayPoolUsage - {}",point,filter.getCacheArray().getUsingPoolSize());
    		LoggerFactory.getLogger(clazz).mark("[{}] DataSourceReaderUsage - {}",point,readerUsage);	
    		LoggerFactory.getLogger(clazz).mark("[{}] DataSourceWriterUsage - {}",point,writerUsage);
    		LoggerFactory.getLogger(clazz).mark("[{}] MemoryUsage - {}mb ({})",point,memoryUsing,memoryUsage);
	    }
	}	
    
}