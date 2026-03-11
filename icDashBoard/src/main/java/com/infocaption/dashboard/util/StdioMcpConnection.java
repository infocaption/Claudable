package com.infocaption.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * MCP connection over stdio (subprocess transport).
 * Launches a subprocess and communicates via stdin/stdout using line-delimited JSON-RPC.
 */
public class StdioMcpConnection implements McpConnection {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpConnection.class);

    private final String command;
    private final List<String> args;
    private final int timeoutSeconds;
    private Process process;
    private BufferedWriter processStdin;
    private BufferedReader processStdout;
    private volatile boolean closed = false;
    private final Object lock = new Object();

    public StdioMcpConnection(String command, List<String> args, int timeoutSeconds) {
        this.command = command;
        this.args = args != null ? args : Collections.emptyList();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
    }

    /**
     * Validate the command against the whitelist and start the subprocess.
     */
    public void start() throws IOException {
        validateCommand(command);

        List<String> cmdList = new ArrayList<>();
        cmdList.add(command);
        cmdList.addAll(args);

        log.info("Starting stdio MCP process: {}", String.join(" ", cmdList));

        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.redirectErrorStream(false); // Keep stderr separate
        process = pb.start();

        processStdin = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        processStdout = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        // Read stderr in background thread for debugging
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader stderr = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stderr.readLine()) != null) {
                    log.debug("[MCP stdio stderr] {}: {}", command, line);
                }
            } catch (IOException e) {
                if (!closed) log.debug("Stderr reader ended for {}: {}", command, e.getMessage());
            }
        }, "mcp-stderr-" + command);
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    @Override
    public String sendRequest(String jsonRpcRequest) throws IOException {
        if (closed) throw new IOException("Connection is closed");
        if (process == null || !process.isAlive()) throw new IOException("Process not running");

        synchronized (lock) {
            try {
                // Write request as a single line
                processStdin.write(jsonRpcRequest);
                processStdin.newLine();
                processStdin.flush();

                // Read response with timeout
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(() -> {
                    String line = processStdout.readLine();
                    if (line == null) throw new IOException("Process closed stdout");
                    return line;
                });

                try {
                    String response = future.get(timeoutSeconds, TimeUnit.SECONDS);

                    // Check response size
                    int maxSize = AppConfig.getInt("mcp.maxResponseSize", 5242880);
                    if (response.length() > maxSize) {
                        throw new IOException("Response exceeds max size (" + maxSize + " bytes)");
                    }

                    return response;
                } catch (TimeoutException e) {
                    future.cancel(true);
                    throw new IOException("Stdio response timeout after " + timeoutSeconds + "s");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException) throw (IOException) cause;
                    throw new IOException("Error reading response", cause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for response", e);
                } finally {
                    executor.shutdownNow();
                }
            } catch (IOException e) {
                if (!closed) log.warn("Stdio send error for {}: {}", command, e.getMessage());
                throw e;
            }
        }
    }

    @Override
    public String listTools() throws IOException {
        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        return sendRequest(request);
    }

    @Override
    public boolean isAlive() {
        return !closed && process != null && process.isAlive();
    }

    @Override
    public void close() {
        closed = true;
        if (process != null) {
            try {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try { if (processStdin != null) processStdin.close(); } catch (IOException ignored) {}
        try { if (processStdout != null) processStdout.close(); } catch (IOException ignored) {}
    }

    @Override
    public String getTransportType() {
        return "stdio";
    }

    /**
     * Validate the command against the whitelist.
     * Blocks path traversal and non-whitelisted commands.
     */
    public static void validateCommand(String cmd) throws IOException {
        if (cmd == null || cmd.trim().isEmpty()) {
            throw new IOException("Empty command");
        }

        // Block path traversal
        if (cmd.contains("/") || cmd.contains("\\") || cmd.contains("..")) {
            throw new IOException("Command must not contain path separators: " + cmd);
        }

        // Check against whitelist
        String allowedStr = AppConfig.get("mcp.stdio.allowedCommands", "npx,node,python,uv,docker");
        Set<String> allowed = new HashSet<>(Arrays.asList(allowedStr.split(",")));
        allowed.removeIf(String::isEmpty);

        String cmdName = cmd.trim().toLowerCase();
        boolean found = false;
        for (String a : allowed) {
            if (a.trim().toLowerCase().equals(cmdName)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IOException("Command not in whitelist: " + cmd +
                    " (allowed: " + allowedStr + ")");
        }
    }
}
