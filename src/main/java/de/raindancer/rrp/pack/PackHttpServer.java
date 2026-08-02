package de.raindancer.rrp.pack;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

/**
 * A tiny read-only HTTP server for the combined pack.
 *
 * <p>A merged pack only exists on this machine, so the client needs somewhere to download it
 * from. Rather than requiring an external web server, RRP can serve exactly one directory —
 * the merge output folder — over plain HTTP, using the JDK's built-in server. No shading, no
 * dependency.
 *
 * <p>It answers {@code GET} and {@code HEAD} for {@code /packs/<file>.zip} and nothing else:
 * no directory listing, no traversal (the resolved path must stay inside the folder), no writes.
 */
public final class PackHttpServer {

    /** The one path prefix under which packs are published. */
    public static final String PREFIX = "/packs/";

    private final Logger log;
    private final Path folder;
    private final String bind;
    private final int port;

    private HttpServer server;
    private ExecutorService executor;

    public PackHttpServer(Path folder, String bind, int port, Logger log) {
        this.folder = folder.toAbsolutePath().normalize();
        this.bind = bind;
        this.port = port;
        this.log = log;
    }

    /**
     * Starts the server.
     *
     * @throws IOException when the port is taken or the address cannot be bound
     */
    public void start() throws IOException {
        Files.createDirectories(folder);
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext(PREFIX, this::handle);
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "rrp-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        log.info("Serving combined packs on http://{}:{}{}", bind, port, PREFIX);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        // The executor is ours, not the server's: stopping the HttpServer does not touch it, so without
        // this every reload left two more threads behind for the life of the JVM.
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public int port() {
        return port;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            if (!method.equals("GET") && !method.equals("HEAD")) {
                respondEmpty(exchange, 405);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith(PREFIX)) {
                respondEmpty(exchange, 404);
                return;
            }
            String name = path.substring(PREFIX.length());
            if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) {
                respondEmpty(exchange, 404);
                return;
            }
            Path file = folder.resolve(name).normalize();
            // Belt and braces: even with the checks above, never serve outside the folder.
            if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
                respondEmpty(exchange, 404);
                return;
            }

            long size = Files.size(file);
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.getResponseHeaders().add("Cache-Control", "public, max-age=31536000, immutable");
            if (method.equals("HEAD")) {
                exchange.getResponseHeaders().add("Content-Length", Long.toString(size));
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, size);
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(file, out);
            }
        } catch (IOException e) {
            log.warn("Serving a pack failed: {}", e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private static void respondEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }
}
