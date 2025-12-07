package org.shareandimprove.cjj;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * An {@link HttpHandler} for managing user authentication.
 * It serves the login page on GET requests and processes login credentials on POST requests.
 * On successful authentication, it issues a JWT as an HttpOnly cookie.
 */
public class LoginHandler implements HttpHandler {
    private final String staticDir;
    /** A secret key for signing JWTs. In a production environment, this should be managed securely (e.g., environment variables, secrets manager). */
    public static final String JWT_SECRET = "your-very-secret-key-that-is-long-and-secure";
    /** The algorithm used for signing and verifying JWTs. */
    public static final Algorithm JWT_ALGORITHM = Algorithm.HMAC256(JWT_SECRET);

    /**
     * Constructs a LoginHandler.
     * @param staticDir The directory where static assets, including the login page, are located.
     */
    public LoginHandler(String staticDir) {
        this.staticDir = staticDir;
    }

    /**
     * Handles an incoming HTTP request by either serving the login page or processing login credentials.
     * @param exchange The {@link HttpExchange} representing the request and response.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            serveLoginPage(exchange);
        } else if ("POST".equals(exchange.getRequestMethod())) {
            handleLogin(exchange);
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }

    /**
     * Serves the login HTML page.
     * @param exchange The HttpExchange object.
     * @throws IOException if an I/O error occurs.
     */
    private void serveLoginPage(HttpExchange exchange) throws IOException {
        String filePath = Paths.get(staticDir, "login", "index.html").toString();
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, fileBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileBytes);
        }
    }

    /**
     * Handles the POST request for user login.
     * @param exchange The HttpExchange object.
     * @throws IOException if an I/O error occurs.
     */
    private void handleLogin(HttpExchange exchange) throws IOException {
        String formData;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            formData = br.readLine();
        }

        Map<String, String> params = new HashMap<>();
        for (String param : formData.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                params.put(URLDecoder.decode(pair[0], "UTF-8"), URLDecoder.decode(pair[1], "UTF-8"));
            }
        }

        String username = params.get("username");
        String password = params.get("password");

        if ("admin".equals(username) && "admin".equals(password)) {
            Instant now = Instant.now();
            String token = JWT.create()
                    .withIssuer("sharing_platform")
                    .withSubject(username)
                    .withIssuedAt(Date.from(now))
                    .withExpiresAt(Date.from(now.plus(30, ChronoUnit.MINUTES)))
                    .sign(JWT_ALGORITHM);

            exchange.getResponseHeaders().add("Set-Cookie", "token=" + token + "; HttpOnly; Path=/; Max-Age=1800");
            exchange.sendResponseHeaders(200, -1); // Success, no body
        } else {
            exchange.sendResponseHeaders(401, -1); // Unauthorized
        }
    }
}