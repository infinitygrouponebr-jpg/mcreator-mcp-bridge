package net.mcreator.mcpbridge;

import net.mcreator.ui.MCreator;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.Deque;

/** Small local control surface; it intentionally never displays the full shared token. */
final class McpControlPanel {
    private final MCreator mcreator;
    private final McpAccessController access;
    private final McpBuildService build;
    private final McpHttpServer http;
    private final Deque<String> traffic = new ArrayDeque<>();
    private final JTextArea trafficArea = new JTextArea(14, 72);
    private JDialog dialog;
    private JLabel status;

    McpControlPanel(MCreator mcreator, McpAccessController access, McpBuildService build, McpHttpServer http) {
        this.mcreator = mcreator; this.access = access; this.build = build; this.http = http;
    }

    void recordTraffic(String line) {
        synchronized (traffic) {
            traffic.addLast(LocalTime.now().withNano(0) + "  " + line);
            while (traffic.size() > 200) traffic.removeFirst();
        }
        SwingUtilities.invokeLater(this::refreshTraffic);
    }

    void show() {
        if (dialog == null) create();
        refresh(); dialog.setLocationRelativeTo(mcreator); dialog.setVisible(true);
    }

    private void create() {
        dialog = new JDialog(mcreator, "MCreator MCP Bridge", false);
        dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(8, 8)); content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        status = new JLabel(); content.add(status, BorderLayout.NORTH);
        JPanel settings = new JPanel(); settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
        JCheckBox readOnly = new JCheckBox("Read-only mode (block every MCP write)", access.isReadOnly());
        readOnly.addActionListener(e -> access.setReadOnly(readOnly.isSelected()));
        JCheckBox confirmations = new JCheckBox("Confirm critical overwrite/update/delete actions", access.confirmationsEnabled());
        confirmations.addActionListener(e -> access.setConfirmationsEnabled(confirmations.isSelected()));
        settings.add(readOnly); settings.add(confirmations);
        settings.add(Box.createVerticalStrut(8)); settings.add(new JLabel("Traffic log (token values are never logged):"));
        trafficArea.setEditable(false); trafficArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        settings.add(new JScrollPane(trafficArea));
        content.add(settings, BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh"); refresh.addActionListener(e -> refresh());
        content.add(refresh, BorderLayout.SOUTH);
        dialog.setContentPane(content); dialog.pack();
    }
    private void refresh() {
        if (status == null) return;
        String httpStatus = http == null ? "disabled" : (http.isRunning() ? "running" : "stopped");
        String endpoint = http == null ? "-" : "127.0.0.1:" + http.getPort() + "/mcp";
        String token = http == null ? "-" : http.getTokenPreview() + " (" + http.getTokenOrigin() + ")";
        status.setText("HTTP: " + httpStatus + " | endpoint: " + endpoint + " | token: " + token
                + " | requests: " + (http == null ? 0 : http.getRequestCount()) + " | build: " + (build.isRunning() ? "running" : "idle"));
        refreshTraffic();
    }
    private void refreshTraffic() {
        if (trafficArea == null) return;
        StringBuilder text = new StringBuilder(); synchronized (traffic) { traffic.forEach(line -> text.append(line).append('\n')); }
        trafficArea.setText(text.toString()); trafficArea.setCaretPosition(trafficArea.getDocument().getLength());
    }
}
