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