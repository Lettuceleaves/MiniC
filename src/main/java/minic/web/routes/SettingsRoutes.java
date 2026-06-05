package minic.web.routes;

import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import minic.color.ThemeManager;
import minic.settings.MiniCSettings;
import minic.web.MiniCWebSocketHub;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Browser/server settings routes backed by existing MiniC settings.
 */
public final class SettingsRoutes {
    private static final String SETTINGS_SCOPE = "settings";
    private static final String SETTINGS_SESSION_ID = "global";

    private final MiniCWebSocketHub webSocketHub;
    private final AtomicLong version = new AtomicLong();

    public SettingsRoutes(MiniCWebSocketHub webSocketHub) {
        this.webSocketHub = Objects.requireNonNull(webSocketHub, "webSocketHub");
        MiniCSettings.load();
    }

    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/settings", context -> context.json(snapshot()));
        routes.patch("/api/settings", this::updateSettings);
        routes.get("/api/settings/themes", context -> context.json(themeList()));
    }

    private void updateSettings(Context context) {
        SettingsUpdateRequest request = context.bodyAsClass(SettingsUpdateRequest.class);
        if (request.theme() != null) {
            if (request.theme().isBlank()) {
                throw new IllegalArgumentException("theme must not be blank");
            }
            MiniCSettings.setTheme(request.theme());
        }
        if (request.frameIntervalMillis() != null) {
            MiniCSettings.setFrameIntervalMillis(request.frameIntervalMillis());
        }
        if (request.uiScale() != null) {
            if (!Double.isFinite(request.uiScale())) {
                throw new IllegalArgumentException("uiScale must be finite");
            }
            MiniCSettings.setUiScale(request.uiScale());
        }
        long nextVersion = version.incrementAndGet();
        webSocketHub.publish("settings.changed", SETTINGS_SCOPE, SETTINGS_SESSION_ID, nextVersion);
        context.json(snapshot());
    }

    private SettingsSnapshot snapshot() {
        return new SettingsSnapshot(
                MiniCSettings.theme(),
                MiniCSettings.frameIntervalMillis(),
                MiniCSettings.uiScale()
        );
    }

    private ThemeListResponse themeList() {
        return new ThemeListResponse(MiniCSettings.theme(), ThemeManager.availableThemes());
    }

    public record SettingsSnapshot(String theme, long frameIntervalMillis, double uiScale) {
    }

    public record SettingsUpdateRequest(String theme, Long frameIntervalMillis, Double uiScale) {
    }

    public record ThemeListResponse(String currentTheme, List<String> themes) {
        public ThemeListResponse {
            themes = List.copyOf(themes);
        }
    }
}
