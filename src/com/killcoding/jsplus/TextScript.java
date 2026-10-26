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

package com.killcoding.jsplus;

import java.io.File;


import com.killcoding.file.BaseFile;
import java.util.Scanner;

import java.nio.charset.Charset;
import java.io.ByteArrayInputStream;
import com.killcoding.jsplus.JsPlusContext;
import com.killcoding.jsplus.JsContext;
import com.killcoding.jsplus.GraalVmContext;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.killcoding.log.Logger;

import java.util.Map;
import java.util.HashMap;
import com.killcoding.tool.CommonTools;
import java.util.Set;
import com.killcoding.file.DiskFile;
import java.util.concurrent.ConcurrentHashMap;

public class TextScript {
    
    public static String DEFAULT_ENGINE_NAME = "nashorn";
    
    protected boolean allowAllAccess = true;
    
    protected final static Map<String,JsPlusContext> CONTEXT_MAPPING = new ConcurrentHashMap<String,JsPlusContext>();
    
    protected String prefix = "#";
    
    protected String varName = "theContent";

    protected JsPlusContext context = null;
    
    protected Scanner scanner = null;
    
    public TextScript() throws Exception{
        super();
    }
    
    public TextScript(File scriptPath) throws Exception{
        super();
        
        if(scriptPath == null) throw new NullPointerException("The 'scriptPath' is null.");

        byte[] scriptBytes = new DiskFile(scriptPath).readAllBytes();
        scanner = new Scanner(new ByteArrayInputStream(scriptBytes), BaseFile.CHARSET);
    }
    
    public TextScript(String script) throws Exception{
        super();
        
        if(script == null) throw new NullPointerException("The 'script' is null.");
        
        byte[] scriptBytes = script.getBytes(BaseFile.CHARSET);
        scanner = new Scanner(new ByteArrayInputStream(scriptBytes), BaseFile.CHARSET);
    }  
    
    protected boolean isEnd(String line){
	    return isMatched(getEndRegex(),line);
    } 
    
    protected String parseEnd(String line){
	    Pattern pattern = Pattern.compile(getEndRegex());
        Matcher matcher = pattern.matcher(line);
        Map<String,String> fullMapping = new HashMap<String,String>();
        Map<String,String> scriptMapping = new HashMap<String,String>();
        StringBuffer sb = new StringBuffer();
        while(matcher.find()){
            String full = matcher.group(1);
            String script = matcher.group(2);
            String replaceToken = "#" + CommonTools.generateId(16) + "#";
            matcher.appendReplacement(sb,replaceToken);
            fullMapping.put(replaceToken,full);
            scriptMapping.put(replaceToken,script);
        }
        matcher.appendTail(sb);
        String quoteLine = sb.toString();
        Set<String> tokens = fullMapping.keySet();
        for(String token : tokens){
            quoteLine = quoteLine.replaceAll(token," } ");
        }
        return quoteLine;
    } 

    protected boolean isBlock(String line){
	    return isMatched(getBlockRegex(),line);
    }  
    
    protected boolean isDefault(String line){
        return isMatched(getDefaultRegex(),line);
    }  
    
    protected String parseDefault(String line){
	    Pattern pattern = Pattern.compile(getDefaultRegex());
        Matcher matcher = pattern.matcher(line);
        Map<String,String> fullMapping = new HashMap<String,String>();
        Map<String,String> scriptMapping = new HashMap<String,String>();
        StringBuffer sb = new StringBuffer();
        while(matcher.find()){
            String full = matcher.group(1);
            String script = matcher.group(2);
            String replaceToken = "#" + CommonTools.generateId(16) + "#";
            matcher.appendReplacement(sb,replaceToken);
            fullMapping.put(replaceToken,full);
            scriptMapping.put(replaceToken,script);
        }
        matcher.appendTail(sb);
        String quoteLine = sb.toString();
        Set<String> tokens = fullMapping.keySet();
        for(String token : tokens){
            quoteLine = quoteLine.replaceAll(token, scriptMapping.get(token).replaceAll(":","").trim() + ": ");
        }
        return quoteLine;
    }     
    
    protected boolean isCase(String line){
        return isMatched(getCaseRegex(),line);
    }  
    
    protected String parseCase(String line){
	    Pattern pattern = Pattern.compile(getCaseRegex());
        Matcher matcher = pattern.matcher(line);
        Map<String,String> fullMapping = new HashMap<String,String>();
        Map<String,String> scriptMapping = new HashMap<String,String>();
        StringBuffer sb = new StringBuffer();
        while(matcher.find()){
            String full = matcher.group(1);
            String script = matcher.group(2);
            String replaceToken = "#" + CommonTools.generateId(16) + "#";
            matcher.appendReplacement(sb,replaceToken);
            fullMapping.put(replaceToken,full);
            scriptMapping.put(replaceToken,script);
        }
        matcher.appendTail(sb);
        String quoteLine = sb.toString();
        Set<String> tokens = fullMapping.keySet();
        for(String token : tokens){
            quoteLine = quoteLine.replaceAll(token, scriptMapping.get(token));
        }
        return quoteLine;
    }    
    
    protected boolean isCode(String line){
        return isMatched(getCodeRegex(),line);
    }  
    
    protected String parseCode(String line){
	    Pattern pattern = Pattern.compile(getCodeRegex());
        Matcher matcher = pattern.matcher(line);
        Map<String,String> fullMapping = new HashMap<String,String>();
        Map<String,String> scriptMapping = new HashMap<String,String>();
        StringBuffer sb = new StringBuffer();
        while(matcher.find()){
            String full = matcher.group(1);
            String script = matcher.group(2);
            String replaceToken = "#" + CommonTools.generateId(16) + "#";
            matcher.appendReplacement(sb,replaceToken);
            fullMapping.put(replaceToken,full);
            scriptMapping.put(replaceToken,script);
        }
        matcher.appendTail(sb);
        String quoteLine = sb.toString();
        Set<String> tokens = fullMapping.keySet();
        for(String token : tokens){
            quoteLine = quoteLine.replaceAll(token, scriptMapping.get(token));
        }
        return quoteLine;
    }       
        
    
    protected boolean isJump(String line){
        return isMatched(getJumpRegex(),line);
    }  
    
    protected String parseJump(String line){
	    Pattern pattern = Pattern.compile(getJumpRegex());
        Matcher matcher = pattern.matcher(line);
        Map<String,String> fullMapping = new HashMap<String,String>();
        Map<String,String> scriptMapping = new HashMap<String,String>();
        StringBuffer sb = new StringBuffer();
        while(matcher.find()){
            String full = matcher.group(1);
            String script = matcher.group(2);
            String replaceToken = "#" + CommonTools.generateId(16) + "#";
            matcher.appendReplacement(sb,replaceToken);
            fullMapping.put(replaceToken,full);
            scriptMapping.put(replaceToken,script);
        }
        matcher.appendTail(sb);
        String quoteLine = sb.toString();
        Set<String> tokens = fullMapping.keySet();
        for(String token : tokens){
            quoteLine = quoteLine.replaceAll(token, scriptMapping.get(token) + ";");
        }
        return quoteLine;
    }       
    
    protected boolean isElseIf(String line){
        return isMatched(getElseIfRegex(),line);
    }  
    
    protected String parseElseIf(String line){
	    Pattern pattern = Pattern.compile(getElseIfRegex());
        Matcher matcher = pattern.matcher(line);
        Map<String,String> fullMapping = new HashMap<String,String>();
        Map<String,String> scriptMapping = new HashMap<String,String>();
        StringBuffer sb = new StringBuffer();
        while(matcher.find()){
            String full = matcher.group(1);
            String script = matcher.group(2);
            String replaceToken = "#" + CommonTools.generateId(16) + "#";
            matcher.appendReplacement(sb,replaceToken);
            fullMapping.put(replaceToken,full);
            scriptMapping.put(replaceToken,script);
        }
        matcher.appendTail(sb);
        String quoteLine = sb.toString();
        Set<String> tokens = fullMapping.keySet();
        for(String token : tokens){
            quoteLine = quoteLine.replaceAll(token, " } " + scriptMapping.get(token) + " { ");
        }
        return quoteLine;
    }     
    
    protected boolean isElse(String line){
        return isMatched(getElseRegex(),line);
    }  
    
    protected String parseElse(String line){
	    Pattern pattern = Pattern.compile(getElseRegex());
        Matcher matcher = pattern.matcher(line);
        Map<String,String> fullMapping = new HashMap<String,String>();
        Map<String,String> scriptMapping = new HashMap<String,String>();
        StringBuffer sb = new StringBuffer();
        while(matcher.find()){
            String full = matcher.group(1);
            String script = matcher.group(2);
            String replaceToken = "#" + CommonTools.generateId(16) + "#";
            matcher.appendReplacement(sb,replaceToken);
            fullMapping.put(replaceToken,full);
            scriptMapping.put(replaceToken,script);
        }
        matcher.appendTail(sb);
        String quoteLine = sb.toString();
        Set<String> tokens = fullMapping.keySet();
        for(String token : tokens){
            quoteLine = quoteLine.replaceAll(token, " } " + scriptMapping.get(token) + " { ");
        }
        return quoteLine;
    }      
    
    protected boolean isComment(String line){
	    return isMatched(getCommentRegex(),line);
    }   
    
    protected boolean isLogic(String line){
	    return isMatched(getLogicRegex(),line);
    }    
    
    protected String parseLogic(String line){
	    Pattern pattern = Pattern.compile(getLogicRegex());
        Matcher matcher = pattern.matcher(line);
        Map<String,String> fullMapping = new HashMap<String,String>();
        Map<String,String> scriptMapping = new HashMap<String,String>();
        StringBuffer sb = new StringBuffer();
        while(matcher.find()){
            String full = matcher.group(1);
            String script = matcher.group(2);
            String replaceToken = "#" + CommonTools.generateId(16) + "#";
            matcher.appendReplacement(sb,replaceToken);
            fullMapping.put(replaceToken,full);
            scriptMapping.put(replaceToken,script);
        }
        matcher.appendTail(sb);
        String quoteLine = sb.toString();
        Set<String> tokens = fullMapping.keySet();
        for(String token : tokens){
            quoteLine = quoteLine.replaceAll(token, scriptMapping.get(token) + " { ");
        }
        return quoteLine;
    }    
    
    protected boolean isExpression(String line){
	   return isMatched(getExpressionRegex(),line);
    }
    
    protected boolean isMatched(String regex,String line){
	    Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(line);
        return matcher.find();
    }    
    
    protected String parseExpression(String line){
	    Pattern pattern = Pattern.compile(getExpressionRegex());
        Matcher matcher = pattern.matcher(line);
        Map<String,String> fullMapping = new HashMap<String,String>();
        Map<String,String> scriptMapping = new HashMap<String,String>();
        StringBuffer sb = new StringBuffer();
        while(matcher.find()){
            String full = matcher.group(1);
            String script = matcher.group(2);
            String replaceToken = "#" + CommonTools.generateId(16) + "#";
            matcher.appendReplacement(sb,replaceToken);
            fullMapping.put(replaceToken,full);
            scriptMapping.put(replaceToken,script);
        }
        matcher.appendTail(sb);
        String quoteLine = parseRawText(sb.toString());
        Set<String> tokens = fullMapping.keySet();
        for(String token : tokens){
            quoteLine = quoteLine.replaceAll(token,"' + " + scriptMapping.get(token) + " + '");
        }
        return quoteLine;
    }    
    
    protected String parseRawText(String line){
        String quoteLine = line.replaceAll("'","\\\\'");
        return "'" + quoteLine + "'";
    }  
    
    public String parse() throws Exception {
        return parse(false);
    }
    
    public String parse(boolean showScript) throws Exception {
        boolean isWin = System.getProperty("os.name").toUpperCase().contains("WINDOWS");
        StringBuffer blockScript = new StringBuffer();
        StringBuffer all = new StringBuffer();
        boolean haveBlock = false;
        try{
        	while (scanner.hasNextLine()) {
        	    boolean needlineBreak = false;
        	    String line = scanner.nextLine();
        	    
        	    if(!haveBlock && isBlock(line)){
        	        haveBlock = true;
        	    }
        	    
        	    if(haveBlock && !isBlock(line) && !isEnd(line)){
        	        blockScript.append(line);
        	        
        	        if(isWin) blockScript.append("\r\n");
                    
                    if(!isWin) blockScript.append("\n");
                    
                    continue;
                    
        	    }
        	    
        	    if(haveBlock) {
        	        haveBlock = !isEnd(line);
        	        
        	        if(!haveBlock) continue;
        	    }
        	    
        	    if(isBlock(line)) continue;
     
        	    if(isComment(line)) continue;
        	    
                if(isExpression(line)){
                    String parseLine = parseExpression(line);
                    all.append(varName + " += " + parseLine + ";");
                    needlineBreak = true;
                }else if(isLogic(line)){
                    all.append(parseLogic(line));
                    needlineBreak = false;
                }else if(isElse(line)){
                    all.append(parseElse(line));
                    needlineBreak = false;
                }else if(isElseIf(line)){
                    all.append(parseElseIf(line));
                    needlineBreak = false;
                }else if(isJump(line)){
                    all.append(parseJump(line));
                    needlineBreak = false;
                }else if(isCode(line)){
                    all.append(parseCode(line));
                    needlineBreak = false;
                }else if(isCase(line)){
                    all.append(parseCase(line));
                    needlineBreak = false;
                }else if(isDefault(line)){
                    all.append(parseDefault(line));
                    needlineBreak = false;
                }else if(isEnd(line)){
                    all.append(parseEnd(line));
                    needlineBreak = false;
                }else{
                    all.append(varName + " += " + parseRawText(line) + ";");
                    needlineBreak = true;
                }
                
                if(!needlineBreak){
                    if(isWin) all.append("\r\n");
                    
                    if(!isWin) all.append("\n");
                }              
                
                if(needlineBreak && scanner.hasNextLine()){
                    if(isWin) all.append(varName + " += '\\r\\n';\r\n");
                    
                    if(!isWin) all.append(varName + " += '\\n';\n");
                }            
        	}
        }finally{
            scanner.close();
        }
    	String allStr = all.toString();
        String functionScript = String.format("(function(){%s var %s = '';%s return %s;})()",blockScript.toString(),varName,allStr,varName);
        if(showScript){
            return functionScript;
        }else{
            if(context == null){
                useEngine(DEFAULT_ENGINE_NAME);
            }
    	    return context.evalScript(functionScript).toString();
        }
    }
    
    public Object evalScript(String script) throws Exception {
        if(context == null){
            useEngine(DEFAULT_ENGINE_NAME);
        }
	    return context.evalScript(script);
    }
    
    public Object eval(File scriptPath) throws Exception {
        if(context == null){
            useEngine(DEFAULT_ENGINE_NAME);
        }
	    return context.eval(scriptPath);
    }    
    
    public TextScript addParams(Map<String,Object> params) throws Exception {
        if(context == null){
            useEngine(DEFAULT_ENGINE_NAME);
        }        
        Set<String> keys = params.keySet();
        for(String key : keys){
            this.context.put(key,params.get(key));
        }
        return this;
    }
    
    public TextScript useEngine(String engineName) throws Exception {
	    this.context = CONTEXT_MAPPING.get(engineName);
	    if(this.context == null){
	        TextScript.initEngine(allowAllAccess,engineName);
	        this.context = CONTEXT_MAPPING.get(engineName);
    	    if(this.context == null){
    	        TextScript.initEngine(allowAllAccess,engineName);
    	        throw new Exception("The context is null, please init engine (e.g. TextScript.initEngine(engineName)).");
    	    }	        
	    }
	    	    
	    return this;
    }      
    
    public static boolean initEngine(String engineName) throws Exception {   
        return initEngine(true,engineName);
    }
    
    public static boolean initEngine(boolean allowAllAccess,String engineName) throws Exception {
        JsPlusContext _context = CONTEXT_MAPPING.get(engineName);
        if(_context == null){
    	    boolean isGraalJs = JsPlusContext.isGraalJs(engineName);
    	    boolean isNashornJs = JsPlusContext.isNashornJs(engineName);
    	    if(isGraalJs){
    	        _context = new GraalVmContext(engineName);
    	    }else if(isNashornJs){
                _context = new NashornContext(engineName);
	        }else{
                _context = new JsContext(engineName);
    	    }
    	    _context.setAllowAllAccess(allowAllAccess);
    	    _context.initScriptEngineManager();
    	    CONTEXT_MAPPING.put(engineName,_context);
    	    return true;
        }else{
            Logger.systemDebug(TextScript.class,"This engine '{}' already exists.",engineName);
            return false;
        }
    }
    
    public JsPlusContext getContext() throws Exception {
        if(context == null){
            useEngine(DEFAULT_ENGINE_NAME);
        }        
        return context;
    }
    
    public void setPrefix(String prefix){
        this.prefix = prefix;   
    }
    
    public String getPrefix(){
        return prefix;
    }
    
    public void setVarName(String varName){
        this.varName = varName;   
    }
    
    public String getVarName(){
        return varName;
    }    
    
    protected String getExpressionRegex(){
        return "(" + getPrefix() + "\\{[^\\S]*([^\\}]*)[^\\S]*\\})";
    }
    
    protected String getLogicRegex(){
        return "([^\\S]*" + getPrefix() +"((if|switch|for|do|while){1}[^\\S]*\\([^\\S]*(.*))[^\\S]*)";
    }    
    
    protected String getElseRegex(){
        return "([^\\S]*" + getPrefix() + "(else){1}[^\\S]*)$";
    }      
    
    protected String getElseIfRegex(){
        return "([^\\S]*" + getPrefix() + "((else[^\\S]+if){1}[^\\S]*\\([^\\S]*(.*))[^\\S]*)";
    } 
   
    protected String getEndRegex(){
        return "([^\\S]*" + getPrefix() +"(end){1}[^\\S]*)$";
    }  

    protected String getJumpRegex(){
        return "([^\\S]*" + getPrefix() + "(break|continue){1}[^\\S]*)$";
    }  

    protected String getCaseRegex(){
        return "([^\\S]*" + getPrefix() + "(case[^\\S]+.*))";
    }  
    
    protected String getDefaultRegex(){
        return "([^\\S]*" + getPrefix() + "(default[^\\S]*[:]?)[^\\S|$]?)";    
    }      

    protected String getCodeRegex(){
        return "([^\\S]*" + getPrefix() + "code[^\\S]+(.*))"; 
    }   
    
    protected String getCommentRegex(){
        return "([^\\S]*" + getPrefix() + "comment[^\\S]+(.*))"; 
    }      
    
    protected String getBlockRegex(){
        return "([^\\S]*" + getPrefix() +"(block){1}[^\\S]*)$";
    }  
    
    public void setAllowAllAccess(boolean allowAllAccess){
        this.allowAllAccess = allowAllAccess;
    }
    
    public boolean isAllowAllAccess(){
        return this.allowAllAccess;
    }
    
}