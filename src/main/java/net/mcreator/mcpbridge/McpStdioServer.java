package net.mcreator.mcpbridge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Line-delimited stdio transport. All JSON-RPC processing lives in {@link McpJsonRpcHandler}. */
final class McpStdioServer {
    private final McpJsonRpcHandler handler;
    private final BufferedReader input;
    private final Writer output;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread thread;

    McpStdioServer(ToolBridge tools) {
        this(new McpJsonRpcHandler(tools), new InputStreamReader(System.in, StandardCharsets.UTF_8),
                new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
    }

    McpStdioServer(McpJsonRpcHandler handler, Reader input, Writer output) {
        this.handler = Objects.requireNonNull(handler);
        this.input = new BufferedReader(input);
        this.output = output;
    }

    synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        thread = new Thread(this::run, "mcreator-mcp-stdio");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() { running.set(false); if (thread != null) thread.interrupt(); }
    void awaitStop() { try { if (thread != null) thread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    private void run() {
        try {
            String line;
            while (running.get() && (line = input.readLine()) != null) {
                String response = handler.handle(line);
                if (response != null) synchronized (output) { output.write(response + "\n"); output.flush(); }
            }
        } catch (IOException error) {
            if (running.get()) System.err.println("MCreator MCP stdio error: " + error.getMessage());
        } finally { running.set(false); }
    }
}
