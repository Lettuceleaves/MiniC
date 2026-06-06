package minic.uiapi.web;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server that exposes Java UIAPI facades to browser clients.
 */
public final class MiniCUiApiServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;

    private MiniCUiApiServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    public static MiniCUiApiServer create(int port) throws IOException {
        MiniCUiApiJson json = new MiniCUiApiJson();
        MiniCUiApiSessionStore sessions = new MiniCUiApiSessionStore();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        ExecutorService executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "minic-uiapi-http");
            thread.setDaemon(true);
            return thread;
        });
        server.createContext("/", new MiniCUiApiRouter(json, sessions));
        server.setExecutor(executor);
        return new MiniCUiApiServer(server, executor);
    }

    public void start() {
        server.start();
    }

    public URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
