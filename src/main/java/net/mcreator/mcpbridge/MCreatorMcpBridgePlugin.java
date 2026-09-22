package net.mcreator.mcpbridge;

import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicReference;

/** Entry point loaded by MCreator. */
public final class MCreatorMcpBridgePlugin extends JavaPlugin {
    private static final Logger LOG = LogManager.getLogger("MCreator MCP Bridge");
    private final McpStdioServer stdioServer;
    private McpHttpServer httpServer;
    private final MCreatorToolBridge tools;
    private final AtomicReference<McpControlPanel> controlPanel = new AtomicReference<>();

    public MCreatorMcpBridgePlugin(Plugin plugin) {
        super(plugin);
        tools = new MCreatorToolBridge();
        addListener(MCreatorLoadedEvent.class, event -> {
            tools.setMCreator(event.getMCreator());
            SwingUtilities.invokeLater(() -> {
                McpControlPanel panel = new McpControlPanel(event.getMCreator(), tools.accessController(), tools.buildService(), httpServer);
                controlPanel.set(panel);
                event.getMCreator().getToolBar().addToRightToolbar(new AbstractAction("MCP") {
                    @Override public void actionPerformed(ActionEvent actionEvent) { panel.show(); }
                }).setToolTipText("Open MCreator MCP Bridge control panel");
            });
        });
        stdioServer = new McpStdioServer(tools);

        // stdio is opt-in: MCreator normally owns the process stdin/stdout. Set this JVM property
        // only when a MCP client is deliberately attached to the MCreator process.
        if (Boolean.getBoolean("mcreator.mcp.stdio.enabled")) {
            stdioServer.start();
            LOG.info("MCP stdio server started");
        } else {
            LOG.info("MCP stdio server is disabled; use -Dmcreator.mcp.stdio.enabled=true to enable it");
        }

        // HTTP is enabled by default because it binds exclusively to 127.0.0.1 and requires a token.
        if (Boolean.parseBoolean(System.getProperty("mcreator.mcp.http.enabled", "true"))) {
            httpServer = McpHttpServer.fromSystemProperties(tools, message -> LOG.info(message),
                    message -> { McpControlPanel panel = controlPanel.get(); if (panel != null) panel.recordTraffic(message); });
            httpServer.start();
        } else {
            httpServer = null;
            LOG.info("MCP HTTP server is disabled; use -Dmcreator.mcp.http.enabled=true to enable it");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "mcreator-mcp-shutdown"));
    }

    /**
     * Call this from MCreator's plugin-unload/closing lifecycle callback once its exact API is confirmed.
     * TODO: verificar o nome exato do evento/callback de encerramento no código-fonte do MCreator.
     */
    public void shutdown() {
        stdioServer.stop();
        if (httpServer != null) httpServer.stop();
        tools.buildService().shutdown();
    }
}
