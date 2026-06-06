package minic.uiapi.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import minic.uiapi.MiniCDebugApi;
import minic.uiapi.MiniCObservationApi;
import minic.uiapi.MiniCRealtimeAnalysisApi;
import minic.uiapi.UiDebugStateDto;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Request router for the browser-facing UIAPI HTTP transport.
 */
public final class MiniCUiApiRouter implements HttpHandler {
    private final MiniCUiApiJson json;
    private final MiniCUiApiSessionStore sessions;
    private final MiniCRealtimeAnalysisApi realtimeAnalysisApi = new MiniCRealtimeAnalysisApi();

    public MiniCUiApiRouter(MiniCUiApiJson json, MiniCUiApiSessionStore sessions) {
        this.json = json;
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        try {
            Object response = route(exchange);
            writeJson(exchange, 200, response);
        } catch (ApiHttpException exception) {
            writeError(exchange, exception.status(), exception.getMessage());
        } catch (MiniCUiApiSessionStore.SessionNotFoundException exception) {
            writeError(exchange, 404, exception.getMessage());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            writeError(exchange, 400, exception.getMessage());
        } catch (IllegalStateException exception) {
            writeError(exchange, 409, exception.getMessage());
        } catch (RuntimeException exception) {
            writeError(exchange, 500, exception.getMessage());
        }
    }

    private Object route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        List<String> segments = pathSegments(exchange);
        if (segments.size() < 2 || !"api".equals(segments.get(0))) {
            throw notFound(exchange);
        }
        return switch (segments.get(1)) {
            case "health" -> routeHealth(method, segments);
            case "realtime" -> routeRealtime(exchange, method, segments);
            case "observation" -> routeObservation(exchange, method, segments);
            case "debug" -> routeDebug(exchange, method, segments);
            default -> throw notFound(exchange);
        };
    }

    private Object routeHealth(String method, List<String> segments) {
        requireMethod(method, "GET");
        requireSize(segments, 2);
        return new HealthResponse("ok");
    }

    private Object routeRealtime(HttpExchange exchange, String method, List<String> segments) throws IOException {
        requireMethod(method, "POST");
        requireSize(segments, 3);
        return switch (segments.get(2)) {
            case "analyze" -> {
                AnalyzeRequest request = read(exchange, AnalyzeRequest.class);
                yield realtimeAnalysisApi.analyze(
                        required(request.sourceName(), "sourceName"),
                        required(request.sourceText(), "sourceText"),
                        request.version()
                );
            }
            case "tokenize" -> {
                TokenizeRequest request = read(exchange, TokenizeRequest.class);
                yield realtimeAnalysisApi.tokenize(
                        required(request.sourceName(), "sourceName"),
                        required(request.sourceText(), "sourceText")
                );
            }
            default -> throw notFound(exchange);
        };
    }

    private Object routeObservation(HttpExchange exchange, String method, List<String> segments) throws IOException {
        if (segments.size() == 3 && "sessions".equals(segments.get(2))) {
            requireMethod(method, "POST");
            return new SessionResponse(sessions.createObservationSession());
        }
        if (segments.size() < 4) {
            throw notFound(exchange);
        }
        String id = segments.get(2);
        MiniCObservationApi api = sessions.observationSession(id);
        return switch (segments.get(3)) {
            case "source" -> {
                requireMethod(method, "POST");
                requireSize(segments, 4);
                SourceRequest request = read(exchange, SourceRequest.class);
                api.loadSource(required(request.sourceName(), "sourceName"), required(request.sourceText(), "sourceText"));
                yield new StatusResponse("loaded");
            }
            case "start" -> {
                requireMethod(method, "POST");
                requireSize(segments, 4);
                api.startSession();
                yield api.currentState();
            }
            case "next" -> control(method, segments, api.next());
            case "next-stage" -> control(method, segments, api.nextStage());
            case "play" -> control(method, segments, api.play());
            case "play-fast" -> control(method, segments, api.playFast());
            case "tick" -> control(method, segments, api.tick());
            case "pause" -> control(method, segments, api.pause());
            case "confirm-input" -> {
                requireMethod(method, "POST");
                requireSize(segments, 4);
                ExecutionInputRequest request = read(exchange, ExecutionInputRequest.class);
                yield api.confirmExecutionInput(required(request.standardInput(), "standardInput"));
            }
            case "previous" -> control(method, segments, api.previous());
            case "reverse-play" -> control(method, segments, api.reversePlay());
            case "state" -> query(method, segments, api.currentState());
            case "stage-data" -> query(method, segments, api.currentStageData());
            case "visual" -> routeObservationVisual(exchange, method, segments, api);
            case "global" -> query(method, segments, api.globalData());
            default -> throw notFound(exchange);
        };
    }

    private Object routeObservationVisual(
            HttpExchange exchange,
            String method,
            List<String> segments,
            MiniCObservationApi api
    ) {
        requireMethod(method, "GET");
        requireSize(segments, 5);
        return switch (segments.get(4)) {
            case "current" -> api.currentStageVisualData();
            case "lexer" -> api.lexerVisualData();
            case "ast" -> api.astVisualData();
            case "semantic" -> api.semanticVisualData();
            case "codegen" -> api.codegenVisualData();
            default -> throw notFound(exchange);
        };
    }

    private Object routeDebug(HttpExchange exchange, String method, List<String> segments) throws IOException {
        if (segments.size() == 3 && "sessions".equals(segments.get(2))) {
            requireMethod(method, "POST");
            return new SessionResponse(sessions.createDebugSession());
        }
        if (segments.size() < 4) {
            throw notFound(exchange);
        }
        String id = segments.get(2);
        MiniCDebugApi api = sessions.debugSession(id);
        if ("breakpoints".equals(segments.get(3))) {
            requireSize(segments, 5);
            int line = line(segments.get(4));
            if ("POST".equals(method)) {
                return api.setBreakpoint(line);
            }
            if ("DELETE".equals(method)) {
                return api.clearBreakpoint(line);
            }
            throw methodNotAllowed(method, "POST, DELETE");
        }
        return switch (segments.get(3)) {
            case "source" -> {
                requireMethod(method, "POST");
                requireSize(segments, 4);
                SourceRequest request = read(exchange, SourceRequest.class);
                api.loadSource(required(request.sourceName(), "sourceName"), required(request.sourceText(), "sourceText"));
                yield new StatusResponse("loaded");
            }
            case "start" -> debugControl(method, segments, api.startDebug());
            case "run-to-breakpoint" -> debugControl(method, segments, api.runToBreakpoint());
            case "run-to-end" -> debugControl(method, segments, api.runToEnd());
            case "fast-forward" -> debugControl(method, segments, api.fastForward());
            case "step-over" -> debugControl(method, segments, api.stepOver());
            case "step-into" -> debugControl(method, segments, api.stepInto());
            case "step-out" -> debugControl(method, segments, api.stepOut());
            case "pause" -> debugControl(method, segments, api.pause());
            case "restart" -> debugControl(method, segments, api.restart());
            case "close" -> {
                requireMethod(method, "POST");
                requireSize(segments, 4);
                UiDebugStateDto state = api.close();
                sessions.removeDebugSession(id);
                yield state;
            }
            case "step-back" -> debugControl(method, segments, api.stepBack());
            case "step-back-over" -> debugControl(method, segments, api.stepBackOver());
            case "back-to-breakpoint" -> debugControl(method, segments, api.backToBreakpoint());
            case "back-to-call-site" -> debugControl(method, segments, api.backToCallSite());
            case "state" -> query(method, segments, api.currentState());
            case "metadata" -> query(method, segments, api.metadataView());
            case "data-structure" -> query(method, segments, api.dataStructureDebugView());
            case "ast" -> query(method, segments, api.astDebugView());
            case "ir" -> query(method, segments, api.irDebugView());
            case "asm" -> query(method, segments, api.asmDebugView());
            default -> throw notFound(exchange);
        };
    }

    private Object control(String method, List<String> segments, Object result) {
        requireMethod(method, "POST");
        requireSize(segments, 4);
        return result;
    }

    private Object debugControl(String method, List<String> segments, Object result) {
        requireMethod(method, "POST");
        requireSize(segments, 4);
        return result;
    }

    private Object query(String method, List<String> segments, Object result) {
        requireMethod(method, "GET");
        requireSize(segments, 4);
        return result;
    }

    private <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
        return json.read(exchange.getRequestBody(), type);
    }

    private void writeJson(HttpExchange exchange, int status, Object response) throws IOException {
        byte[] bytes = json.writeBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private void writeError(HttpExchange exchange, int status, String message) throws IOException {
        String path = exchange.getRequestURI().getPath();
        writeJson(exchange, status, new ErrorResponse(status, exchange.getRequestMethod(), path, message));
    }

    private List<String> pathSegments(HttpExchange exchange) {
        return Arrays.stream(exchange.getRequestURI().getPath().split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> URLDecoder.decode(segment, StandardCharsets.UTF_8))
                .toList();
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void requireMethod(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw methodNotAllowed(actual, expected);
        }
    }

    private void requireSize(List<String> segments, int expected) {
        if (segments.size() != expected) {
            throw new ApiHttpException(404, "route not found");
        }
    }

    private String required(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private int line(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("line must be an integer: " + value, exception);
        }
    }

    private ApiHttpException notFound(HttpExchange exchange) {
        return new ApiHttpException(404, "route not found: " + exchange.getRequestURI().getPath());
    }

    private ApiHttpException methodNotAllowed(String actual, String allowed) {
        return new ApiHttpException(405, "method " + actual + " is not allowed; expected " + allowed);
    }

    public record HealthResponse(String status) {
    }

    public record SessionResponse(String sessionId) {
    }

    public record StatusResponse(String status) {
    }

    public record ErrorResponse(int status, String method, String path, String message) {
    }

    public record SourceRequest(String sourceName, String sourceText) {
    }

    public record AnalyzeRequest(String sourceName, String sourceText, long version) {
    }

    public record TokenizeRequest(String sourceName, String sourceText) {
    }

    public record ExecutionInputRequest(String standardInput) {
    }

    private static final class ApiHttpException extends RuntimeException {
        private final int status;

        private ApiHttpException(int status, String message) {
            super(message);
            this.status = status;
        }

        private int status() {
            return status;
        }
    }
}
