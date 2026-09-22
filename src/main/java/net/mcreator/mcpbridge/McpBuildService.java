package net.mcreator.mcpbridge;

import net.mcreator.gradle.GradleUtils;
import net.mcreator.io.OutputStreamEventHandler;
import net.mcreator.ui.MCreator;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ProjectConnection;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs a single supported Gradle task through MCreator's own configured Tooling connection. */
final class McpBuildService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mcreator-mcp-build"); thread.setDaemon(true); return thread;
    });
    private volatile Map<String, Object> last = Map.of("status", "never_run", "stdout", "", "stderr", "");
    private volatile boolean running;

    synchronized Map<String, Object> start(MCreator mcreator, String action) {
        if (running) throw new IllegalStateException("An MCP Gradle task is already running");
        String task = switch (action) {
            case "build" -> "build";
            case "client" -> mcreator.getGeneratorConfiguration().getGradleTaskFor("run_client");
            case "export" -> "build";
            default -> throw new IllegalArgumentException("Unsupported build action: " + action);
        };
        if (task == null || task.isBlank() || task.startsWith("@")) throw new IllegalStateException("The active generator does not expose a Gradle task for " + action);
        running = true;
        last = new LinkedHashMap<>(Map.of("status", "running", "action", action, "task", task,
                "startedAt", Instant.now().toString(), "stdout", "", "stderr", ""));
        executor.submit(() -> execute(mcreator, action, task));
        return Map.of("started", true, "action", action, "task", task, "status", "running");
    }

    private void execute(MCreator mcreator, String action, String task) {
        StringBuilder out = new StringBuilder(), err = new StringBuilder();
        try {
            // This is the same configured project connection MCreator's GradleConsole uses.
            ProjectConnection connection = GradleUtils.getGradleProjectConnection(mcreator.getWorkspace());
            BuildLauncher launcher = GradleUtils.getGradleTaskLauncher(mcreator.getGeneratorConfiguration(), connection, task);
            launcher.setStandardOutput(new OutputStreamEventHandler(line -> out.append(line).append('\n')));
            launcher.setStandardError(new OutputStreamEventHandler(line -> err.append(line).append('\n')));
            launcher.run();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "success"); result.put("action", action); result.put("task", task);
            result.put("finishedAt", Instant.now().toString()); result.put("stdout", out.toString()); result.put("stderr", err.toString());
            if ("export".equals(action)) result.put("exportFile", mcreator.getGeneratorConfiguration().getGradleTaskFor("export_file"));
            last = result;
        } catch (Exception error) {
            err.append(error).append('\n');
            last = Map.of("status", "failed", "action", action, "task", task, "finishedAt", Instant.now().toString(),
                    "stdout", out.toString(), "stderr", err.toString());
        } finally { running = false; }
    }

    Map<String, Object> lastLog() { return new LinkedHashMap<>(last); }
    boolean isRunning() { return running; }
    void shutdown() { executor.shutdownNow(); }
}
