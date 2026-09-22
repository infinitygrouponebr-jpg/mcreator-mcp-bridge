package net.mcreator.mcpbridge;

/** Starts the protocol server without MCreator; useful for validating framing and client configuration. */
public final class McpStdioLauncher {
    private McpStdioLauncher() { }
    public static void main(String[] args) {
        McpStdioServer server = new McpStdioServer(new MCreatorToolBridge());
        server.start();
        server.awaitStop();
    }
}
