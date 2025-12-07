package org.shareandimprove.cjj;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;

/**
 * An {@link HttpHandler} for processing search API requests.
 * It handles POST requests to "/api/search", parses the query parameter,
 * converts it to a SQL query, executes it, and returns the results as a JSON response.
 */
public class SearchAPIHandler implements HttpHandler{
    /**
     * Handles the incoming HTTP request for the search API.
     * @param exchange The {@link HttpExchange} object.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException{
        String requestMethod = exchange.getRequestMethod();
        URI requestURI = exchange.getRequestURI();
        String path = requestURI.getPath();

        if ("POST".equals(requestMethod)) {
            if ("/api/search".equals(path)) {
                // 1. receives POST to /api/search?q=someURIEncodedQueryString;
                String queryString = requestURI.getQuery();
                String queryParam = null;

                if (queryString != null) {
                    // Parse query parameters more robustly
                    String[] params = queryString.split("&");
                    for (String param : params) {
                        String[] pair = param.split("=", 2);
                        if (pair.length == 2 && "q".equals(pair[0])) {
                            // The query string is already decoded by the server
                            queryParam = pair[1];
                            break; // Found 'q', no need to check further
                        }
                    }
                }

                if (queryParam == null || queryParam.isEmpty()) {
                    // Bad request: missing or empty 'q' parameter
                    sendResponse(exchange, 400, "Missing or empty 'q' query parameter.");
                    return;
                }

                try {
                    // 2. decodeURIComponent();
                    // java has automatically decoded the query string

                    // 3. call QueryHandler.query2SQL(String query) to convert to SQL;
                    String sqlQuery = QueryHandler.query2SQL(queryParam);
                    Log.println(sqlQuery);

                    // 4. call asJSON to convert to JSON;
                    String jsonResponse = asJSON(DBHandler.SQL(sqlQuery));

                    // 5. return json
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    sendResponse(exchange, 200, jsonResponse);

                } catch (Exception e) {
                    // Log the exception for debugging
                    Log.println("Error during search: " + e.getMessage());
                    sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
                }
            } else {
                // POST request to a path other than /api/search
                sendResponse(exchange, 404, "Not Found");
            }
        } else {
            // Not a POST request
            sendResponse(exchange, 405, "Method Not Allowed");
        }
    }

    /**
     * Helper method to send HTTP responses.
     * @param exchange The HttpExchange object.
     * @param statusCode The HTTP status code to send.
     * @param responseBody The response body string.
     * @throws IOException If an I/O error occurs.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    /**
     * Executes a SQL query and returns the result as a JSON string.
     * @param mapList The list of maps representing the query result.
     * @return A JSON string representing an array of result objects.
     */
    public static String asJSON(List<Map<String, Object>> mapList) {
        return new Gson().toJson(mapList);
    }
}
