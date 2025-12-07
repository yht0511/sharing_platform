package org.shareandimprove.cjj;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple, static logging utility that writes timestamped messages to a file and to the console.
 * The logger is initialized statically and includes a shutdown hook to close resources gracefully.
 */
public class Log {
    private static PrintWriter writer;
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    
    static {
        try {
            // Ensure the log directory exists
            File logDir = new File("log");
            if (!logDir.exists()) {
                logDir.mkdirs(); // Create the directory if it doesn't exist
            }
            writer = new PrintWriter(new FileWriter("log/runtime.log", true));
            INITIALIZED.set(true);
            // Add a shutdown hook to ensure the writer is closed when the JVM exits
            Runtime.getRuntime().addShutdownHook(new Thread(Log::close));
        } catch (IOException e) {
            System.err.println("Failed to initialize log file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Logs a message to both the console and the log file with a timestamp.
     * This method is thread-safe.
     *
     * @param message The message to be logged.
     */
    public static void println(String message) {
        if (!INITIALIZED.get() || writer == null) {
            System.err.println("Log writer not initialized. Message not logged: " + message);
            return;
        }
        // Create a new SimpleDateFormat instance for each call to ensure thread safety.
        SimpleDateFormat threadSafeDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String timestamp = threadSafeDateFormat.format(new Date());
        String wholeMessage = "[" + timestamp + "] " + message;
        try {
            writer.println(wholeMessage);
            writer.flush(); // Flush immediately to ensure logs are written
            System.out.println(wholeMessage);
        } catch (Exception e) {
            System.err.println("Error writing to log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Closes the log file writer. This is typically called by the shutdown hook.
     */
    public static void close() {
        if (writer != null) {
            writer.close();
            writer = null; // Mark as closed
            INITIALIZED.set(false);
        }
    }
}
