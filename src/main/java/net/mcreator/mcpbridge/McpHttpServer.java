package net.mcreator.mcpbridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Stateless MCP Streamable HTTP transport, deliberately bound to 127.0.0.1 only. */
final class McpHttpServer {
    static final int DEFAULT_PORT = 39217;
    private static final int MAX_BODY_BYTES = 1_048_576;
    private final McpJsonRpcHandler handler;
    private final int port;
    private final String token;
    private final Consumer<String> log;
    private HttpServer server;
    private ExecutorService executor;

    McpHttpServer(ToolBridge tools, int port, String token, Consumer<String> log) {
        this.handler = new McpJsonRpcHandler(Objects.requireNonNull(tools));
        this.port = port;
        this.token = Objects.requireNonNull(token);
        this.log = Objects.requireNonNull(log);
    }

    static McpHttpServer fromSystemProperties(ToolBridge tools, Consumer<String> log) {
        int port = parsePort(System.getProperty("mcreator.mcp.http.port"), DEFAULT_PORT, log);
        String token = resolveToken(log);
        return new McpHttpServer(tools, port, token, log);
    }

    /** Resolves the token without ever changing the persisted value when an explicit JVM property exists. */
    private static String resolveToken(Consumer<String> log) {
        String configured = System.getProperty("mcreator.mcp.http.token");
        if (configured != null && !configured.isBlank()) {
            log.accept("[MCreator MCP Bridge] Using MCP token from system property");
            return configured;
        }

        Path tokenFile = persistedTokenPath();
        try {
            if (Files.isRegularFile(tokenFile)) {
                String persisted = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
                if (!persisted.isEmpty()) {
                    restrictPosixPermissions(tokenFile, log);
                    log.accept("[MCreator MCP Bridge] Using persisted MCP token from " + tokenFile + ": " + persisted);
                    return persisted;
                }
            }

            String generated = generateToken();
            Files.createDirectories(tokenFile.getParent());
            Files.writeString(tokenFile, generated, StandardCharsets.UTF_8);
            restrictPosixPermissions(tokenFile, log);
            log.accept("[MCreator MCP Bridge] Generated and saved MCP token to " + tokenFile + ": " + generated);
            return generated;
        } catch (IOException | SecurityException error) {
            String generated = generateToken();
            log.accept("[MCreator MCP Bridge] Could not read or save MCP token at " + tokenFile + ": "
                    + error.getMessage() + ". Using a non-persisted token for this run: " + generated);
            return generated;
        }
    }

    private static Path persistedTokenPath() {
        return Path.of(System.getProperty("user.home"), ".mcreator", "mcp-bridge", "token.txt").toAbsolutePath();
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Applies rw------- when the file system exposes POSIX permissions; Windows uses its user-profile ACLs. */
    private static void restrictPosixPermissions(Path tokenFile, Consumer<String> log) {
        try {
            if (Files.getFileStore(tokenFile).supportsFileAttributeView(PosixFileAttributeView.class)) {
                Files.setPosixFilePermissions(tokenFile, PosixFilePermissions.fromString("rw-------"));
            }
        } catch (IOException | UnsupportedOperationException | SecurityException error) {
            log.accept("[MCreator MCP Bridge] Could not restrict permissions on MCP token file " + tokenFile
                    + ": " + error.getMessage());
        }
    }

    synchronized boolean start() {
        if (server != null) return true;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
            server.createContext("/mcp", this::handle);
            executor = Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "mcreator-mcp-http");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.start();
            log.accept("MCP HTTP server listening at http://127.0.0.1:" + port + "/mcp");
            return true;
        } catch (BindException error) {
            log.accept("MCP HTTP server was not started: port 127.0.0.1:" + port + " is already in use.");
        } catch (IOException error) {
            log.accept("MCP HTTP server was not started: " + error.getMessage());
        }
        stop();
        return false;
    }

    synchronized void stop() {
        if (server != null) { server.stop(0); server = null; }
        if (executor != null) { executor.shutdownNow(); executor = null; }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"/mcp".equals(exchange.getRequestURI().getPath())) { send(exchange, 404, "text/plain", "Not found"); return; }
            if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) { send(exchange, 403, "text/plain", "Local connections only"); return; }
            if (!validOrigin(exchange.getRequestHeaders().getFirst("Origin"))) { send(exchange, 403, "text/plain", "Invalid Origin"); return; }
            if (!authorized(exchange)) { exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer"); send(exchange, 401, "text/plain", "Missing or invalid MCP token"); return; }

            if ("GET".equals(exchange.getRequestMethod())) {
                // This stateless server does not emit server-initiated messages. Add a text/event-stream
                // response here when progress notifications, sessions, or subscriptions need SSE.
                exchange.getResponseHeaders().set("Allow", "POST");
                send(exchange, 405, "text/plain", "SSE stream is not implemented");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) { exchange.getResponseHeaders().set("Allow", "POST"); send(exchange, 405, "text/plain", "Method not allowed"); return; }
            if (contentLengthTooLarge(exchange)) { send(exchange, 413, "text/plain", "Request body too large"); return; }

            byte[] body;
            try { body = readBody(exchange); }
            catch (BodyTooLargeException error) { send(exchange, 413, "text/plain", "Request body too large"); return; }
            String response = handler.handle(new String(body, StandardCharsets.UTF_8));
            if (response == null) { exchange.sendResponseHeaders(202, -1); return; }
            send(exchange, 200, "application/json; charset=utf-8", response);
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String supplied = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : exchange.getRequestHeaders().getFirst("X-MCreator-MCP-Token");
        return supplied != null && constantTimeEquals(token, supplied);
    }

    private boolean validOrigin(String origin) {
        if (origin == null || origin.isBlank()) return true; // Non-browser MCP clients generally omit Origin.
        try {
            URI uri = URI.create(origin);
            if (!"http".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            return ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) && uri.getPort() == port;
        } catch (IllegalArgumentException error) { return false; }
    }

    private static boolean contentLengthTooLarge(HttpExchange exchange) {
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        try { return length != null && Long.parseLong(length) > MAX_BODY_BYTES; }
        catch (NumberFormatException ignored) { return true; }
    }
    private static byte[] readBody(HttpExchange exchange) throws IOException, BodyTooLargeException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) throw new BodyTooLargeException();
        return body;
    }
    private static void send(HttpExchange exchange, int status, String contentType, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    private static boolean constantTimeEquals(String expected, String supplied) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8), right = supplied.getBytes(StandardCharsets.UTF_8);
        int difference = left.length ^ right.length;
        for (int i = 0; i < Math.max(left.length, right.length); i++) difference |= (i < left.length ? left[i] : 0) ^ (i < right.length ? right[i] : 0);
        return difference == 0;
    }
    private static int parsePort(String configured, int fallback, Consumer<String> log) {
        if (configured == null || configured.isBlank()) return fallback;
        try { int port = Integer.parseInt(configured); if (port > 0 && port <= 65535) return port; } catch (NumberFormatException ignored) { }
        log.accept("Invalid mcreator.mcp.http.port; using " + fallback + ".");
        return fallback;
    }
    private static final class BodyTooLargeException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
