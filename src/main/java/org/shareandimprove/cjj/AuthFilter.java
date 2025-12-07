package org.shareandimprove.cjj;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A {@link Filter} that enforces authentication for HTTP requests.
 * It checks for a valid JWT in the "token" cookie. If the token is missing or invalid,
 * it rejects the request with a 401 Unauthorized status.
 */
public class AuthFilter extends Filter {
    private final JWTVerifier verifier;

    /**
     * Constructs an AuthFilter and initializes the JWT verifier.
     */
    public AuthFilter() {
        this.verifier = JWT.require(LoginHandler.JWT_ALGORITHM)
                .withIssuer("sharing_platform")
                .build();
    }

    @Override
    /**
     * Performs the authentication check on an incoming request.
     * @param exchange The {@link HttpExchange} object.
     * @param chain The filter chain.
     * @throws IOException if an I/O error occurs.
     */
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        Optional<String> token = getCookie(exchange, "token");
        String requestLog = exchange.getRemoteAddress().toString() // Source IP
                            + " "
                            + exchange.getRequestMethod()
                            + " "
                            + exchange.getRequestURI().toString();

        if (isValidToken(token)) {
            Log.println(requestLog + " - Authenticated");
            chain.doFilter(exchange);
            return;
        }

        // If token is invalid or not present
        Log.println(requestLog + " - 401 Unauthorized");

        String response = "401 Unauthorized: A valid token is required. Please login.";
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        // Redirect to login page for browsers, or just send 401 for API calls.
        // For simplicity, we just send 401. A more advanced implementation could check 'Accept' header.
        exchange.sendResponseHeaders(401, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * Extracts a cookie value by its name from the request headers.
     * @param exchange The HttpExchange object.
     * @param cookieName The name of the cookie to find.
     * @return An {@link Optional} containing the cookie value if found, otherwise an empty Optional.
     */
    private Optional<String> getCookie(HttpExchange exchange, String cookieName) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return Optional.empty();

        for (String cookie : cookieHeader.split(";")) {
            String[] pair = cookie.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals(cookieName)) {
                return Optional.of(pair[1]);
            }
        }
        return Optional.empty();
    }

    /**
     * Validates a JWT string.
     * @param token An {@link Optional} that may contain the token string.
     * @return {@code true} if the token is present and valid, {@code false} otherwise.
     */
    private boolean isValidToken(Optional<String> token) {
        if (token.isEmpty()) return false;
        try {
            verifier.verify(token.get());
            return true;
        } catch (JWTVerificationException e) {
            // Log the verification failure for debugging purposes.
            Log.println("JWT Verification failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    /**
     * @return A description of this filter.
     */
    public String description() {
        return "A filter to authenticate users based on a JWT in an HttpOnly cookie.";
    }
}
