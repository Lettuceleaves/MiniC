package minic.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import minic.web.routes.AnalysisRoutes;
import minic.web.routes.CompileSessionRoutes;
import minic.web.routes.DebugSessionRoutes;
import minic.web.routes.SettingsRoutes;

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
        MiniCWebSessionRegistry registry = new MiniCWebSessionRegistry();
        MiniCWebSocketHub webSocketHub = new MiniCWebSocketHub();
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/minic/web";
                staticFiles.hostedPath = "/";
                staticFiles.location = Location.CLASSPATH;
            });
            MiniCWebErrorMapper.register(javalinConfig.routes);
            javalinConfig.routes.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));
            javalinConfig.routes.ws("/ws", webSocketHub::register);
            new AnalysisRoutes().register(javalinConfig.routes);
            new SettingsRoutes(webSocketHub).register(javalinConfig.routes);
            new CompileSessionRoutes(registry, webSocketHub).register(javalinConfig.routes);
            new DebugSessionRoutes(registry, webSocketHub).register(javalinConfig.routes);
        });

        app.start(config.host(), config.port());
        return new MiniCWebServer(app, config.host());
    }
}
