package minic.web.routes;

import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import minic.uiapi.MiniCRealtimeAnalysisApi;

/**
 * JavaFX-free realtime source analysis routes.
 */
public final class AnalysisRoutes {
    public void register(JavalinDefaultRoutingApi routes) {
        routes.post("/api/analysis/realtime", this::analyzeRealtime);
    }

    private void analyzeRealtime(Context context) {
        RealtimeAnalysisRequest request = context.bodyAsClass(RealtimeAnalysisRequest.class);
        context.json(MiniCRealtimeAnalysisApi.analyzeNow(request.sourceName(), request.sourceText(), request.version()));
    }

    public record RealtimeAnalysisRequest(String sourceName, String sourceText, long version) {
        public RealtimeAnalysisRequest {
            if (sourceName == null || sourceName.isBlank()) {
                throw new IllegalArgumentException("sourceName is required");
            }
            if (sourceText == null) {
                throw new IllegalArgumentException("sourceText is required");
            }
        }
    }
}
