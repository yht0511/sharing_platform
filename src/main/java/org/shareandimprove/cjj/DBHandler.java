package org.shareandimprove.cjj;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A handler for interacting with the SQLite database.
 * Provides methods to execute SQL queries.
 */
public class DBHandler {
    private static final String DB_URL = "jdbc:sqlite:./db/platform.db";

    /**
     * Executes a given SQL query string and returns the results.
     * <p>
     * <b>Security Warning:</b> This method is vulnerable to SQL injection because it directly executes
     * a raw query string. It should only be used with SQL strings that are constructed from
     * trusted, validated, and sanitized sources.
     *
     * @param queryString The SQL query to execute.
     * @return A list of maps, where each map represents a row with column names as keys.
     * @throws IllegalArgumentException if a {@link SQLException} occurs.
     */
    public static List<Map<String, Object>> SQL(String queryString) throws IllegalArgumentException{
        List<Map<String, Object>> results = new ArrayList<>();

        // Using try-with-resources to ensure database resources are closed automatically.
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(queryString)) {

            ResultSetMetaData md = rs.getMetaData();
            int columns = md.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columns; i++) {
                    row.put(md.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
            // Depending on requirements, you might want to re-throw as a custom exception.
            throw new IllegalArgumentException(e);
        }

        return results;
    }

    /**
     * Retrieves the full filename (name + extension) for a given file hash.
     *
     * @param fileHash The hash of the file to look up.
     * @return The full filename as a string (e.g., "document.pdf").
     * @throws IllegalArgumentException if the file hash is not found in the database or if the hash is malformed.
     */
    public static String getNameByHash(String fileHash) throws IllegalArgumentException{
        List<Map<String, Object>> results = SQL("SELECT FileName, FileType FROM FILE WHERE FileHash = \""+fileHash +"\"");
        if(results.size() < 1){
            throw new IllegalArgumentException(fileHash+": File not found in database");
        }
        return results.get(0).get("FileName").toString() + "." + results.get(0).get("FileType").toString();
    }
}
