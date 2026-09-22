package net.mcreator.mcpbridge;

import java.util.concurrent.CountDownLatch;

/** Standalone launcher for manual HTTP transport checks, without MCreator. */
public final class McpHttpLauncher {
    private McpHttpLauncher() { }
    public static void main(String[] args) throws InterruptedException {
        McpHttpServer server = McpHttpServer.fromSystemProperties(new MCreatorToolBridge(), System.err::println);
        if (!server.start()) return;
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "mcreator-mcp-http-shutdown"));
        new CountDownLatch(1).await();
    }
}
