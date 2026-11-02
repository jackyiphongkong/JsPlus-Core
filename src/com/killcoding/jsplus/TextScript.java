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

package com.killcoding.jsplus;

import java.io.File;


import com.killcoding.file.BaseFile;
import java.util.Scanner;

import java.io.ByteArrayInputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.killcoding.log.Logger;

import java.util.Map;
import java.util.HashMap;
import com.killcoding.tool.CommonTools;
import java.util.Set;
import com.killcoding.file.DiskFile;

/**
* Text-script loader: reads script source from a file / stream / string and feeds it to the
* console for evaluation.
* */
public class TextScript {

    public static String DEFAULT_ENGINE_NAME = "graalvm.js";

    protected String prefix = "#";

    protected String varName = "theContent";

    protected JsPlusContext context = null;

    /** The console pool this TextScript renders through. The default
     *  constructor uses the shared default pool; callers may pick a
     *  security-profile pool explicitly via the boolean strategy
     *  constructors (true = {@link AllowAllAccessPool}, false =
     *  {@link NotAllowAccessPool}). */
    protected TextScriptConsole console = null;

    protected Scanner scanner = null;

    /**
    * Constructs a new TextScript on the shared default pool
    * ({@link TextScriptConsole#getDefaultPool()}).
    * */
    public TextScript() throws Exception{
        super();
        this.console = TextScriptConsole.getDefaultPool();
    }

    /**
    * Constructs a new TextScript on the security-profile pool selected by the
    * boolean policy: {@code true} = {@link AllowAllAccessPool} (permissive),
    * {@code false} = {@link NotAllowAccessPool} (restricted). The policy is
    * fixed at construction time.
    * @param allowAllAccess the security policy for the pool
    * */
    public TextScript(boolean allowAllAccess) throws Exception{
        super();
        this.console = resolveStrategyPool(allowAllAccess);
    }

    /**
    * Constructs a new TextScript on the shared default pool, loading the
    * script source from a file.
    * @param scriptPath the script file
    * */
    public TextScript(File scriptPath) throws Exception{
        super();

        if(scriptPath == null) throw new NullPointerException("The 'scriptPath' is null.");

        byte[] scriptBytes = new DiskFile(scriptPath).readAllBytes();
        scanner = new Scanner(new ByteArrayInputStream(scriptBytes), BaseFile.CHARSET);
        this.console = TextScriptConsole.getDefaultPool();
    }

    /**
    * Constructs a new TextScript on the security-profile pool selected by the
    * boolean policy, loading the script source from a file.
    * @param scriptPath the script file
    * @param allowAllAccess the security policy for the pool ({@code true} =
    *                       {@link AllowAllAccessPool}, {@code false} =
    *                       {@link NotAllowAccessPool})
    * */
    public TextScript(File scriptPath, boolean allowAllAccess) throws Exception{
        this(scriptPath);
        this.console = resolveStrategyPool(allowAllAccess);
    }

    /**
    * Constructs a new TextScript on the shared default pool from script source.
    * @param script the script
    * */
    public TextScript(String script) throws Exception{
        super();

        if(script == null) throw new NullPointerException("The 'script' is null.");

        byte[] scriptBytes = script.getBytes(BaseFile.CHARSET);
        scanner = new Scanner(new ByteArrayInputStream(scriptBytes), BaseFile.CHARSET);
        this.console = TextScriptConsole.getDefaultPool();
    }

    /**
    * Constructs a new TextScript on the security-profile pool selected by the
    * boolean policy, loading the script source from a string.
    * @param script the script
    * @param allowAllAccess the security policy for the pool ({@code true} =
    *                       {@link AllowAllAccessPool}, {@code false} =
    *                       {@link NotAllowAccessPool})
    * */
    public TextScript(String script, boolean allowAllAccess) throws Exception{
        this(script);
        this.console = resolveStrategyPool(allowAllAccess);
    }

    /**
    * Constructs a new TextScript bound to an explicit pool instance. Internal
    * (same-package) use by {@link TextScriptConsole#newTextScript(String)} so
    * a pool hands out a TextScript bound to its own instance.
    * @param script the script
    * @param console the pool to render through
    * */
    protected TextScript(String script, TextScriptConsole console) throws Exception{
        this(script);
        this.console = console != null ? console : TextScriptConsole.getDefaultPool();
    }

    /** Resolves the named strategy pool instance for a boolean policy:
     *  {@code true} = permissive ({@link AllowAllAccessPool#getInstance()}),
     *  {@code false} = restricted ({@link NotAllowAccessPool#getInstance()}). */
    private static TextScriptConsole resolveStrategyPool(boolean allowAllAccess) {
        return allowAllAccess
                ? AllowAllAccessPool.getInstance()
                : NotAllowAccessPool.getInstance();
    }

    /**
    * Performs the isEnd operation
    * @param line - the line
    * @return the isEnd result
    * */
    protected boolean isEnd(String line){
	    return isMatched(getEndRegex(),line);
    }

    /**
    * Performs the parseEnd operation
    * @param line - the line
    * @return the parseEnd result
    * */
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

    /**
    * Performs the isBlock operation
    * @param line - the line
    * @return the isBlock result
    * */
    protected boolean isBlock(String line){
	    return isMatched(getBlockRegex(),line);
    }

    /**
    * Performs the isDefault operation
    * @param line - the line
    * @return the isDefault result
    * */
    protected boolean isDefault(String line){
        return isMatched(getDefaultRegex(),line);
    }

    /**
    * Performs the parseDefault operation
    * @param line - the line
    * @return the parseDefault result
    * */
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

    /**
    * Performs the isCase operation
    * @param line - the line
    * @return the isCase result
    * */
    protected boolean isCase(String line){
        return isMatched(getCaseRegex(),line);
    }

    /**
    * Performs the parseCase operation
    * @param line - the line
    * @return the parseCase result
    * */
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

    /**
    * Performs the isCode operation
    * @param line - the line
    * @return the isCode result
    * */
    protected boolean isCode(String line){
        return isMatched(getCodeRegex(),line);
    }

    /**
    * Performs the parseCode operation
    * @param line - the line
    * @return the parseCode result
    * */
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


    /**
    * Performs the isJump operation
    * @param line - the line
    * @return the isJump result
    * */
    protected boolean isJump(String line){
        return isMatched(getJumpRegex(),line);
    }

    /**
    * Performs the parseJump operation
    * @param line - the line
    * @return the parseJump result
    * */
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

    /**
    * Performs the isElseIf operation
    * @param line - the line
    * @return the isElseIf result
    * */
    protected boolean isElseIf(String line){
        return isMatched(getElseIfRegex(),line);
    }

    /**
    * Performs the parseElseIf operation
    * @param line - the line
    * @return the parseElseIf result
    * */
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

    /**
    * Performs the isElse operation
    * @param line - the line
    * @return the isElse result
    * */
    protected boolean isElse(String line){
        return isMatched(getElseRegex(),line);
    }

    /**
    * Performs the parseElse operation
    * @param line - the line
    * @return the parseElse result
    * */
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

    /**
    * Performs the isComment operation
    * @param line - the line
    * @return the isComment result
    * */
    protected boolean isComment(String line){
	    return isMatched(getCommentRegex(),line);
    }

    /**
    * Performs the isLogic operation
    * @param line - the line
    * @return the isLogic result
    * */
    protected boolean isLogic(String line){
	    return isMatched(getLogicRegex(),line);
    }

    /**
    * Performs the parseLogic operation
    * @param line - the line
    * @return the parseLogic result
    * */
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

    /**
    * Performs the isExpression operation
    * @param line - the line
    * @return the isExpression result
    * */
    protected boolean isExpression(String line){
	   return isMatched(getExpressionRegex(),line);
    }

    /**
    * Performs the isMatched operation
    * @param regex - the regex
    * @param line - the line
    * @return the isMatched result
    * */
    protected boolean isMatched(String regex,String line){
	    Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(line);
        return matcher.find();
    }

    /**
    * Performs the parseExpression operation
    * @param line - the line
    * @return the parseExpression result
    * */
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

    /**
    * Performs the parseRawText operation
    * @param line - the line
    * @return the parseRawText result
    * */
    protected String parseRawText(String line){
        String quoteLine = line.replaceAll("'","\\\\'");
        return "'" + quoteLine + "'";
    }

    /**
    * Performs the parse operation
    * @return the parse result
    * */
    public String parse() throws Exception {
        return parse(false);
    }

    /**
    * Performs the parse operation
    * @param showScript - the showScript
    * @return the parse result
    * */
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

    /**
    * Performs the evalScript operation
    * @param script - the script
    * @return the evalScript result
    * */
    public Object evalScript(String script) throws Exception {
        if(context == null){
            useEngine(DEFAULT_ENGINE_NAME);
        }
	    return context.evalScript(script);
    }

    /**
    * Performs the eval operation
    * @param scriptPath - the scriptPath
    * @return the eval result
    * */
    public Object eval(File scriptPath) throws Exception {
        if(context == null){
            useEngine(DEFAULT_ENGINE_NAME);
        }
	    return context.eval(scriptPath);
    }

    /**
    * Performs the addParams operation
    * @param params - the params
    * @return the addParams result
    * */
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

    /**
    * Thread-safe one-step render built on the pooled console pool (pool
    * concept, mirrors JsPlus-Tomcat TomcatScriptConsole): the DEFAULT engine
    * name resolves to the current thread's own pooled context, so concurrent
    * callers never share a context and need no locks - every thread renders on
    * its own context with real parallelism. The pooled console is returned to
    * the pool (freed) when the render completes.
    * @param template the TextScript template source
    * @param params the params injected via addParams (may be null)
    * @return the rendered output
    * @throws Exception when rendering fails
    * */
    public static String render(String template, Map<String,Object> params) throws Exception {
        TextScript ts = new TextScript(template);
        try {
            ts.useEngine(DEFAULT_ENGINE_NAME);
            if(params != null){
                Set<String> keys = params.keySet();
                for(String key : keys){
                    ts.context.put(key, params.get(key));
                }
            }
            return ts.parse();
        } finally {
            ts.freed();
        }
    }

    /**
    * Returns the current thread's pooled console context back to this
    * TextScript's pool (delayed by TextScriptConsole's freed delay). No-op
    * for contexts that were not acquired from the pool (e.g. bound via
    * {@link #setContext(JsPlusContext)}).
    * @throws Exception when returning fails
    * */
    public void freed() throws Exception {
        if(context != null){
            console.freed();
            context = null;
        }
    }

    /**
    * Performs the useEngine operation. Pool concept (mirrors JsPlus-Tomcat
    * TomcatScriptConsole): EVERY engine name - default or custom - resolves to
    * the current thread's OWN pooled console context via this TextScript's
    * pool ({@link TextScriptConsole#getThreadContext()} on the bound pool).
    * Isolation is guaranteed by the thread-confined pool (one console per
    * thread), so the former static CONTEXT_MAPPING is gone and no locks are
    * needed. The name is kept purely for API compatibility/identification.
    * @param engineName - the engineName
    * @return the useEngine result
    * */
    public TextScript useEngine(String engineName) throws Exception {
        this.context = console.getThreadContext();
        return this;
    }

    /**
    * Binds this TextScript to an explicit engine context for thread-confined
    * rendering - e.g. the per-thread console context handed out by
    * {@link TextScriptConsole} (mirrors JsPlus-Tomcat TomcatScriptConsole).
    * @param ctx the JsPlusContext to bind (must not be null)
    * @return this TextScript (chainable)
    * */
    public TextScript setContext(JsPlusContext ctx){
        if(ctx == null) throw new NullPointerException("The 'context' is null.");
        this.context = ctx;
        return this;
    }

    /**
    * Initialises the script engine for TextScript rendering. Since the pool
    * concept took over (CONTEXT_MAPPING was removed), this delegates to
    * {@link TextScriptConsole#initTextScriptConsole(String)}: it initialises
    * the master console + pool once, and every subsequent engine name simply
    * resolves to the current thread's pooled context. The allowAllAccess /
    * engine / pool-size settings are all owned by the pool's
    * {@link TextScriptConsole} (external config, see its javadoc).
    * @param engineName - the engineName
    * @return the initEngine result
    * */
    public static boolean initEngine(String engineName) throws Exception {
        if(!TextScriptConsole.isLoadedScriptEngine()){
            TextScriptConsole.initTextScriptConsole(engineName);
            Logger.systemMark(TextScript.class, "TextScript engine '{}' initialised via TextScriptConsole pool.", engineName);
            return true;
        }
        Logger.systemDebug(TextScript.class,"TextScriptConsole engine already initialised ({}); initEngine({}) is a no-op.", TextScriptConsole.getEngineName(), engineName);
        return false;
    }

    /**
    * Performs the getContext operation
    * @return the getContext result
    * */
    public JsPlusContext getContext() throws Exception {
        if(context == null){
            useEngine(DEFAULT_ENGINE_NAME);
        }
        return context;
    }

    /**
    * Performs the setPrefix operation
    * @param prefix - the prefix
    * */
    public void setPrefix(String prefix){
        this.prefix = prefix;
    }

    /**
    * Performs the getPrefix operation
    * @return the getPrefix result
    * */
    public String getPrefix(){
        return prefix;
    }

    /**
    * Performs the setVarName operation
    * @param varName - the varName
    * */
    public void setVarName(String varName){
        this.varName = varName;
    }

    /**
    * Performs the getVarName operation
    * @return the getVarName result
    * */
    public String getVarName(){
        return varName;
    }

    /**
    * Performs the getExpressionRegex operation
    * @return the getExpressionRegex result
    * */
    protected String getExpressionRegex(){
        return "(" + getPrefix() + "\\{[^\\S]*([^\\}]*)[^\\S]*\\})";
    }

    /**
    * Performs the getLogicRegex operation
    * @return the getLogicRegex result
    * */
    protected String getLogicRegex(){
        return "([^\\S]*" + getPrefix() +"((if|switch|for|do|while){1}[^\\S]*\\([^\\S]*(.*))[^\\S]*)";
    }

    /**
    * Performs the getElseRegex operation
    * @return the getElseRegex result
    * */
    protected String getElseRegex(){
        return "([^\\S]*" + getPrefix() + "(else){1}[^\\S]*)$";
    }

    /**
    * Performs the getElseIfRegex operation
    * @return the getElseIfRegex result
    * */
    protected String getElseIfRegex(){
        return "([^\\S]*" + getPrefix() + "((else[^\\S]+if){1}[^\\S]*\\([^\\S]*(.*))[^\\S]*)";
    }

    /**
    * Performs the getEndRegex operation
    * @return the getEndRegex result
    * */
    protected String getEndRegex(){
        return "([^\\S]*" + getPrefix() +"(end){1}[^\\S]*)$";
    }

    /**
    * Performs the getJumpRegex operation
    * @return the getJumpRegex result
    * */
    protected String getJumpRegex(){
        return "([^\\S]*" + getPrefix() + "(break|continue){1}[^\\S]*)$";
    }

    /**
    * Performs the getCaseRegex operation
    * @return the getCaseRegex result
    * */
    protected String getCaseRegex(){
        return "([^\\S]*" + getPrefix() + "(case[^\\S]+.*))";
    }

    /**
    * Performs the getDefaultRegex operation
    * @return the getDefaultRegex result
    * */
    protected String getDefaultRegex(){
        return "([^\\S]*" + getPrefix() + "(default[^\\S]*[:]?)[^\\S|$]?)";
    }

    /**
    * Performs the getCodeRegex operation
    * @return the getCodeRegex result
    * */
    protected String getCodeRegex(){
        return "([^\\S]*" + getPrefix() + "code[^\\S]+(.*))";
    }

    /**
    * Performs the getCommentRegex operation
    * @return the getCommentRegex result
    * */
    protected String getCommentRegex(){
        return "([^\\S]*" + getPrefix() + "comment[^\\S]+(.*))";
    }

    /**
    * Performs the getBlockRegex operation
    * @return the getBlockRegex result
    * */
    protected String getBlockRegex(){
        return "([^\\S]*" + getPrefix() +"(block){1}[^\\S]*)$";
    }

}
