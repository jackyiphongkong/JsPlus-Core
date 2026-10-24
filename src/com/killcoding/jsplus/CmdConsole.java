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

public class CmdConsole extends ScriptConsole {

    private final boolean enableSystemOut;
    private final String[] shellPrefix;
    private final ExecutorService monitorExecutor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Long, ProcessInfo> processes = new ConcurrentHashMap<>();

    public CmdConsole() {
        this(true);
    }

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
                    Thread.sleep(200);
                } catch (Exception e) {
                    LoggerFactory.getLogger(CmdConsole.class).error("Monitor error", e);
                }
            }
        });
    }

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