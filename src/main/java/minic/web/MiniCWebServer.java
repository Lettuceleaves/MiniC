package minic.web;

import io.javalin.Javalin;

import java.net.URI;
import java.util.Objects;

/**
 * Runtime handle for the MiniC web server.
 */
public final class MiniCWebServer implements AutoCloseable {
    private final Javalin app;
    private final String host;

    MiniCWebServer(Javalin app, String host) {
        this.app = Objects.requireNonNull(app, "app");
        this.host = Objects.requireNonNull(host, "host");
    }

    public static void main(String[] args) {
        MiniCWebApplication.create(MiniCWebConfig.development()).start();
    }

    public URI uri(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create("http://" + host + ":" + port() + normalizedPath);
    }

    public int port() {
        return app.port();
    }

    @Override
    public void close() {
        app.stop();
    }
}
