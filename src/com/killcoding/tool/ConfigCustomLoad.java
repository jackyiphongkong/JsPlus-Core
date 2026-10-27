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

/**
* Custom configuration loader: attaches a Runnable to ConfigProperties so callers can post-process
* the parsed configuration (file + properties) after the standard load completes.
* */
public class ConfigCustomLoad {
    
    protected Runnable loadRun = null;
    protected ConfigProperties config = null;
    protected File file = null;
    protected boolean updated = false;
    protected boolean useCustom = true;
    
    /**
    * Constructs a new ConfigCustomLoad
    * */
    public ConfigCustomLoad(){
        super();
    }
    
    /**
    * Performs the load operation
    * @param loadRun - the loadRun
    * */
    public void load(Runnable loadRun){
        this.loadRun = loadRun;
    }
    
    /**
    * Performs the load operation
    * */
    public void load(){
         
    }
    
    /**
    * Performs the load operation
    * @param file - the file
    * @param config - the config
    * */
    protected void load(File file,ConfigProperties config){
        this.file = file;
        this.config = config;
        
        if(this.loadRun != null) this.loadRun.run();
        
        load();
    }
    
    /**
    * Performs the getConfig operation
    * @return the getConfig result
    * */
    public ConfigProperties getConfig(){
        return config;
    }
    
    /**
    * Performs the getFile operation
    * @return the getFile result
    * */
    public File getFile(){
        return file;
    }
    
    /**
    * Performs the setUpdated operation
    * @param updated - the updated
    * */
    public void setUpdated(boolean updated){
        this.updated = updated;
    }

    /**
    * Performs the isUpdated operation
    * @return the isUpdated result
    * */
    public boolean isUpdated(){
        return this.updated;
    }   
    
    /**
    * Performs the setUseCustom operation
    * @param useCustom - the useCustom
    * */
    public void setUseCustom(boolean useCustom){
        this.useCustom = useCustom;
    }
    
    /**
    * Performs the isUseCustom operation
    * @return the isUseCustom result
    * */
    public boolean isUseCustom(){
        return useCustom;
    }
    
}
