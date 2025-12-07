package org.shareandimprove.cjj;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.CheckedInputStream;
/**
 * An {@link HttpHandler} for managing file downloads.
 * It supports two main operations:
 * 1. POST to /api/download: Generates a temporary, single-use download link for one or more files.
 *    It responds with a 302 redirect to the temporary link.
 * 2. GET to /download/{id}: Serves the file(s) associated with the temporary link ID.
 *    It provides a single file directly or a ZIP archive for multiple files.
 * Temporary links expire and are cleaned up periodically.
 */
public class DownloadHandler implements HttpHandler {
    // A simple data class to hold the link data and its creation time.
    private static class ExpiringLink {
        final String[] hashes;
        final long creationTime;

        ExpiringLink(String[] hashes) {
            this.hashes = hashes;
            this.creationTime = System.currentTimeMillis();
        }
    }

    protected final String fileRootDir;
    private static final ConcurrentHashMap<String, ExpiringLink> downloadLinks = new ConcurrentHashMap<>();
    private static final long LINK_TTL_MS = TimeUnit.MINUTES.toMillis(5); // Links expire after 5 minutes.
    
    // Static initializer to ensure the cleanup scheduler is created only once per class load.
    static {
        ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DownloadLink-Cleanup");
            t.setDaemon(true); // Allow the JVM to exit without waiting for this thread.
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(DownloadHandler::cleanupExpiredLinks, LINK_TTL_MS, LINK_TTL_MS, TimeUnit.MILLISECONDS);
    }


    /**
     * Constructs a DownloadHandler.
     * @param fileRootDir The root directory where files to be downloaded are stored.
     */
    public DownloadHandler(String fileRootDir) {
        this.fileRootDir = Paths.get(fileRootDir).toAbsolutePath().normalize().toString();
    }

    /**
     * Handles an incoming HTTP request by routing it to either the link generation or file download logic.
     * @param exchange The {@link HttpExchange} representing the request and response.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        URI requestURI = exchange.getRequestURI();
        String path = requestURI.getPath();

        if ("/api/download".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            handleLinkGeneration(exchange);
        } else if (path.startsWith("/download/") && "GET".equals(exchange.getRequestMethod())) {
            handleFileDownload(exchange);
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }

    /**
     * Handles the generation of a temporary download link.
     * @param exchange The HttpExchange object.
     * @throws IOException if an I/O error occurs.
     */
    private void handleLinkGeneration(HttpExchange exchange) throws IOException {
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }

        if (body == null || body.trim().isEmpty()) {
            exchange.sendResponseHeaders(400, -1); // Bad Request
            return;
        }

        // The body from the form submission is in 'hashes=hash1,hash2,...' format.
        // We need to extract the value part.
        String hashData = body;
        if (body.startsWith("hashes=")) {
            // A simple parser for a single key-value pair.
            hashData = URLDecoder.decode(body.substring("hashes=".length()), StandardCharsets.UTF_8);
        }

        String[] hashes = hashData.split(",");
        String downloadId = UUID.randomUUID().toString();
        downloadLinks.put(downloadId, new ExpiringLink(hashes));
        
        // Respond with a 302 redirect to the one-time download link
        exchange.getResponseHeaders().add("Location", "/download/" + downloadId);
        exchange.sendResponseHeaders(302, -1);
    }

    /**
     * Handles the actual file download using a temporary link ID.
     * @param exchange The HttpExchange object.
     * @throws IOException if an I/O error occurs.
     */
    private void handleFileDownload(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String downloadId = path.substring(path.lastIndexOf('/') + 1);

        // Atomically retrieve and remove the link to ensure it's used only once
        ExpiringLink link = downloadLinks.remove(downloadId);

        if (link == null || link.hashes.length == 0) {
            String response = "410 Gone: This download link has expired or is invalid.";
            exchange.sendResponseHeaders(410, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
            return;
        }

        String[] hashes = link.hashes;
        if (link.hashes.length == 1) {
            // Single file download
            String fileHash = link.hashes[0];
            Path filePath = Paths.get(fileRootDir, fileHash).toAbsolutePath().normalize();
            File file = filePath.toFile();

            if (!file.exists() || !filePath.startsWith(Paths.get(fileRootDir).toAbsolutePath())) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            try {
                // RFC 5987 specifies that the value for filename* should be URL-encoded.
                String encodedFilename = URLEncoder.encode(DBHandler.getNameByHash(fileHash), StandardCharsets.UTF_8).replace("+", "%20");
                String twiceEncodedFilename = URLEncoder.encode(encodedFilename, StandardCharsets.UTF_8);
                // Provide both a safe fallback `filename` for older clients and the full `filename*` for modern clients.
                exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + twiceEncodedFilename + "\"; filename*=UTF-8''" + encodedFilename);
            } catch (IllegalArgumentException e) {
                // If the hash is not found, we can't set a filename. We can proceed with a generic name or fail.
                // Failing is safer to avoid confusion.
                Log.println("Could not find filename for hash: " + fileHash + ". Error: " + e.getMessage());
                exchange.sendResponseHeaders(404, -1); // Not Found
                return;
            }

            exchange.sendResponseHeaders(200, file.length());
            try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } else {
            // Bulk (zip) download
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmssXXX");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"download_" + sdf.format(new Date()) + ".zip\"");
            exchange.sendResponseHeaders(200, 0); // Use chunked encoding

            try (OutputStream os = exchange.getResponseBody(); ZipOutputStream zipOut = new ZipOutputStream(os)) {
                byte[] buffer = new byte[8192]; // Use a larger buffer for I/O
                for (String hash : hashes) {
                    Path filePath = Paths.get(fileRootDir, hash).toAbsolutePath().normalize();
                    File file = filePath.toFile();
                    if (!file.exists() || !filePath.startsWith(Paths.get(fileRootDir).toAbsolutePath())) {
                        continue; // Skip non-existent or invalid files
                    }

                    ZipEntry zipEntry = new ZipEntry(DBHandler.getNameByHash(hash));
                    zipEntry.setMethod(ZipEntry.STORED);
                    zipEntry.setSize(file.length());
                    zipEntry.setCompressedSize(file.length());

                    // Calculate CRC-32 checksum
                    CRC32 crc = new CRC32();
                    try (CheckedInputStream cis = new CheckedInputStream(new FileInputStream(file), crc)) {
                        while (cis.read(buffer) != -1) {
                            // Read file to compute CRC
                        }
                    }
                    zipEntry.setCrc(crc.getValue());

                    zipOut.putNextEntry(zipEntry);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            zipOut.write(buffer, 0, bytesRead);
                        }
                        zipOut.closeEntry();
                    }
                }
            }
        }
    }

    /**
     * Periodically cleans up expired download links from the map.
     */
    private static void cleanupExpiredLinks() {
        long now = System.currentTimeMillis();
        int initialSize = downloadLinks.size();
        downloadLinks.entrySet().removeIf(entry -> (now - entry.getValue().creationTime) > LINK_TTL_MS);
        int finalSize = downloadLinks.size();
        if (initialSize > finalSize) {
            Log.println("Cleaned up "+String.valueOf(initialSize - finalSize)+" expired download links.");
        }
    }
}