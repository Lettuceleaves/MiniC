package minic.web;

import io.javalin.Javalin;

import java.util.Map;
import java.util.Objects;

/**
 * Creates and wires the browser/server MiniC web adapter.
 */
public final class MiniCWebApplication {
    private final MiniCWebConfig config;

    private MiniCWebApplication(MiniCWebConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public static MiniCWebApplication create(MiniCWebConfig config) {
        return new MiniCWebApplication(config);
    }

    public MiniCWebServer start() {
        Javalin app = Javalin.create(javalinConfig ->
                javalinConfig.routes.get("/api/health", ctx -> ctx.json(Map.of("status", "ok"))));
        app.start(config.host(), config.port());
        return new MiniCWebServer(app, config.host());
    }
}
