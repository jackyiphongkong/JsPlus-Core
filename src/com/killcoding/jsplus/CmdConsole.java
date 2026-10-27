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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.Process;
import java.lang.Runtime;
import java.lang.System;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.killcoding.log.LoggerFactory;

/**
* Command-line console: an interactive REPL that reads scripts / commands from stdin or command
* arguments, with monitor polling (MONITOR_POLL_MS) to watch for input and terminate on exit.
* */
public class CmdConsole extends ScriptConsole {

    /** Process monitor polling interval. */
    private static final long MONITOR_POLL_MS = 200L;

    private final boolean enableSystemOut;
    private final String[] shellPrefix;
    private final ExecutorService monitorExecutor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Long, ProcessInfo> processes = new ConcurrentHashMap<>();

    /**
    * Constructs a new CmdConsole
    * */
    public CmdConsole() {
        this(true);
    }

    /**
    * Constructs a new CmdConsole
    * @param enableSystemOut - the enableSystemOut
    * */
    public CmdConsole(boolean enableSystemOut) {
        super();
        this.enableSystemOut = enableSystemOut;
        setUseCmdEnv();
        String os = System.getProperty("os.name").toUpperCase();
        if (os.contains("WINDOWS")) {
            shellPrefix = new String[]{"cmd.exe", "/c"};
        } else {
            shellPrefix = new String[]{"bash", "-c"};
        }
        startMonitor();
    }

    /**
    * Performs the startMonitor operation
    * */
    private void startMonitor() {
        monitorExecutor.execute(() -> {
            while (true) {
                try {
                    for (ProcessInfo info : processes.values()) {
                        if (info.process.isAlive()) {
                            continue;
                        }
                        info.waitForAndCallback();
                        processes.remove(info.pid);
                    }
                    Thread.sleep(MONITOR_POLL_MS);
                } catch (Exception e) {
                    LoggerFactory.getLogger(CmdConsole.class).error("Monitor error", e);
                }
            }
        });
    }

    /**
    * Performs the submit operation
    * @param cmd - the cmd
    * @param success - the success
    * @param fail - the fail
    * @return the submit result
    * */
    public Process submit(String cmd, Runnable success, Runnable fail) {
        try {
            String[] fullCmd = new String[shellPrefix.length + 1];
            System.arraycopy(shellPrefix, 0, fullCmd, 0, shellPrefix.length);
            fullCmd[shellPrefix.length] = cmd;

            LoggerFactory.getLogger(CmdConsole.class).info("Command: {}", String.join(" ", fullCmd));

            Process process = Runtime.getRuntime().exec(fullCmd);
            long pid = process.pid();
            ProcessInfo info = new ProcessInfo(pid, process, success, fail);
            processes.put(pid, info);
            return process;
        } catch (Exception e) {
            LoggerFactory.getLogger(CmdConsole.class).error("ErrorCommand: {}", cmd);
            LoggerFactory.getLogger(CmdConsole.class).error(e.getMessage(), e);
            return null;
        }
    }

    /**
    * Performs the getCurrentPid operation
    * @return the getCurrentPid result
    * */
    public Long getCurrentPid() {
        return processes.keys().nextElement();
    }

    private class ProcessInfo {
        final long pid;
        final Process process;
        final Runnable successCallback;
        final Runnable failCallback;
        boolean callbackTriggered = false;

        ProcessInfo(long pid, Process process, Runnable success, Runnable fail) {
            this.pid = pid;
            this.process = process;
            this.successCallback = success;
            this.failCallback = fail;
        }

        /**
        * Performs the waitForAndCallback operation
        * */
        void waitForAndCallback() {
            if (callbackTriggered) return;
            synchronized (this) {
                if (callbackTriggered) return;
                callbackTriggered = true;
            }

            try {
                try (BufferedReader stdoutReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = stdoutReader.readLine()) != null) {
                        if (enableSystemOut) System.out.println(line);
                    }
                }

                boolean hasError = false;
                try (BufferedReader stderrReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = stderrReader.readLine()) != null) {
                        hasError = true;
                        if (enableSystemOut) System.err.println(line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0 && !hasError && successCallback != null) {
                    successCallback.run();
                } else if (failCallback != null) {
                    failCallback.run();
                }
            } catch (Exception e) {
                LoggerFactory.getLogger(CmdConsole.class).error(e.getMessage(), e);
                if (failCallback != null) failCallback.run();
            }
        }
    }
}