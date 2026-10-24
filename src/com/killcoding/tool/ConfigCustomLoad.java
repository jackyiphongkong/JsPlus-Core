package com.killcoding.tool;

import java.io.File;

public class ConfigCustomLoad {
    
    protected Runnable loadRun = null;
    protected ConfigProperties config = null;
    protected File file = null;
    protected boolean updated = false;
    protected boolean useCustom = true;
    
    public ConfigCustomLoad(){
        super();
    }
    
    public void load(Runnable loadRun){
        this.loadRun = loadRun;
    }
    
    public void load(){
         
    }
    
    protected void load(File file,ConfigProperties config){
        this.file = file;
        this.config = config;
        
        if(this.loadRun != null) this.loadRun.run();
        
        load();
    }
    
    public ConfigProperties getConfig(){
        return config;
    }
    
    public File getFile(){
        return file;
    }
    
    public void setUpdated(boolean updated){
        this.updated = updated;
    }

    public boolean isUpdated(){
        return this.updated;
    }   
    
    public void setUseCustom(boolean useCustom){
        this.useCustom = useCustom;
    }
    
    public boolean isUseCustom(){
        return useCustom;
    }
    
}