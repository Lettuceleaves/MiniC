package minic.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.BadRequestResponse;
import io.javalin.router.JavalinDefaultRoutingApi;

/**
 * Maps server exceptions to structured browser-safe errors.
 */
public final class MiniCWebErrorMapper {
    private MiniCWebErrorMapper() {
    }

    public static void register(JavalinDefaultRoutingApi routes) {
        routes.exception(MiniCWebSessionRegistry.SessionNotFoundException.class, (exception, context) ->
                context.status(404).json(new WebError("session-not-found", exception.getMessage(), 404)));
        routes.exception(BadRequestResponse.class, (exception, context) ->
                context.status(400).json(new WebError("bad-request", exception.getMessage(), 400)));
        routes.exception(JsonProcessingException.class, (exception, context) ->
                context.status(400).json(new WebError("bad-request", "invalid JSON request body", 400)));
        routes.exception(IllegalArgumentException.class, (exception, context) ->
                context.status(400).json(new WebError("bad-request", exception.getMessage(), 400)));
        routes.exception(IllegalStateException.class, (exception, context) ->
                context.status(409).json(new WebError("session-conflict", exception.getMessage(), 409)));
        routes.exception(Exception.class, (exception, context) ->
                context.status(500).json(new WebError("internal-error", "MiniC web adapter failed", 500)));
    }

    public record WebError(String code, String message, int status) {
    }
}
