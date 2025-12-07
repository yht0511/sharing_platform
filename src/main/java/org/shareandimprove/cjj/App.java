package org.shareandimprove.cjj;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
/**
 * The main application class that sets up and starts the HTTP server.
 * It configures all the necessary context handlers and filters for the application endpoints.
 */
public class App {
    private static final int PORT = 8080;
    private static final String FILE_ROOT_DIR = "./files";
    private static final String STATIC_DIR = "./static";

    /**
     * The main entry point of the application.
     * It initializes and starts the HttpServer, binding handlers to their respective paths.
     *
     * @param args Command line arguments (not used).
     * @throws IOException if the server cannot be started.
     */
    public static void main(String[] args) throws IOException{
        if(!new File(FILE_ROOT_DIR).exists()||!new File(STATIC_DIR).exists()){
            System.err.println("Either of the following directory does not exist: \n"+FILE_ROOT_DIR+"\n"+STATIC_DIR);
            System.exit(0);
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        DownloadHandler downloadHandler = new DownloadHandler(FILE_ROOT_DIR);

        // 1. /login for authentication
        server.createContext("/login", new LoginHandler(STATIC_DIR));
        // 2. /api/search for POST queries
        server.createContext("/api/search", new SearchAPIHandler()).getFilters().add(new AuthFilter());
        // 3. /api/download and /download/ for file downloading
        server.createContext("/api/download", downloadHandler).getFilters().add(new AuthFilter());
        server.createContext("/download/", downloadHandler).getFilters().add(new AuthFilter());
        // 4. / to serve ./static/ (should be last to not override other contexts)
        server.createContext("/", new StaticFileHandler(STATIC_DIR));

        server.start();
        System.out.println("Server started on port " + PORT);
    }
}