package org.shareandimprove.cjj;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * An {@link HttpHandler} that serves static files from a specified directory.
 * It handles requests for files and includes a security check to prevent path traversal attacks.
 * If a request is made to the root path ("/"), it serves "index.html".
 */
public class StaticFileHandler implements HttpHandler {
    private final String staticDir;

    /**
     * Constructs a new StaticFileHandler.
     *
     * @param staticDir The root directory from which to serve static files.
     */
    public StaticFileHandler(String staticDir) {
        this.staticDir = Paths.get(staticDir).toAbsolutePath().normalize().toString();
    }

    /**
     * Handles an incoming HTTP request by attempting to serve a static file.
     *
     * @param exchange The {@link HttpExchange} representing the request and response.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String path = uri.getPath().equals("/") ? "/index.html" : uri.getPath();
        Path filePath = Paths.get(staticDir, path).toAbsolutePath().normalize();

        // Security check to prevent path traversal
        if (!filePath.startsWith(Paths.get(staticDir).toAbsolutePath())) {
            String response = "403 Forbidden";
            exchange.sendResponseHeaders(403, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
            return;
        }

        File file = filePath.toFile();
        if (file.exists() && !file.isDirectory()) {
            exchange.sendResponseHeaders(200, file.length());
            try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(file)) {
                final byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } else {
            String response = "404 Not Found";
            exchange.sendResponseHeaders(404, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}