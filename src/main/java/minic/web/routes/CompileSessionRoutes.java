package minic.web.routes;

import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import minic.uiapi.MiniCObservationApi;
import minic.uiapi.UiControlResultDto;
import minic.web.MiniCWebSessionRegistry;
import minic.web.MiniCWebSocketHub;
import minic.web.dto.WebSessionDtos.CommandInputRequest;
import minic.web.dto.WebSessionDtos.CompileSnapshotResponse;
import minic.web.dto.WebSessionDtos.CreateSessionRequest;

import java.util.Objects;

/**
 * REST routes for compile observation sessions.
 */
public final class CompileSessionRoutes {
    private static final int RUN_TO_EXECUTION_GUARD = 10000;

    private final MiniCWebSessionRegistry registry;
    private final MiniCWebSocketHub webSocketHub;

    public CompileSessionRoutes(MiniCWebSessionRegistry registry, MiniCWebSocketHub webSocketHub) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.webSocketHub = Objects.requireNonNull(webSocketHub, "webSocketHub");
    }

    public void register(JavalinDefaultRoutingApi routes) {
        routes.post("/api/compile/sessions", this::createSession);
        routes.delete("/api/compile/sessions/{id}", this::closeSession);
        routes.post("/api/compile/sessions/{id}/source", this::updateSource);
        routes.post("/api/compile/sessions/{id}/start", this::startSession);
        routes.post("/api/compile/sessions/{id}/commands/{command}", this::runCommand);
        routes.get("/api/compile/sessions/{id}/state", this::state);
        routes.get("/api/compile/sessions/{id}/stage", this::stage);
        routes.get("/api/compile/sessions/{id}/global", this::global);
        routes.get("/api/compile/sessions/{id}/visual/current", this::visual);
        routes.get("/api/compile/sessions/{id}/snapshot", this::snapshot);
    }

    private void createSession(Context context) {
        CreateSessionRequest request = context.bodyAsClass(CreateSessionRequest.class);
        context.status(201).json(registry.createCompileSession(request.sourceName(), request.sourceText()));
    }

    private void closeSession(Context context) {
        String sessionId = sessionId(context);
        var closed = registry.closeCompileSession(sessionId);
        webSocketHub.publish("session.closed", "compile", sessionId, closed.version());
        context.json(closed);
    }

    private void updateSource(Context context) {
        CreateSessionRequest request = context.bodyAsClass(CreateSessionRequest.class);
        String sessionId = sessionId(context);
        registry.updateCompileSource(sessionId, request.sourceName(), request.sourceText());
        registry.startCompileSession(sessionId);
        webSocketHub.publish("compile.state.changed", "compile", sessionId, registry.compileVersion(sessionId));
        context.json(snapshot(sessionId));
    }

    private void startSession(Context context) {
        String sessionId = sessionId(context);
        registry.startCompileSession(sessionId);
        webSocketHub.publish("compile.state.changed", "compile", sessionId, registry.compileVersion(sessionId));
        context.json(snapshot(sessionId));
    }

    private void runCommand(Context context) {
        String command = context.pathParam("command");
        CommandInputRequest input = commandInput(context);
        String sessionId = sessionId(context);
        UiControlResultDto result = registry.commandCompileSession(sessionId,
                api -> runCompileCommand(api, command, input));
        webSocketHub.publish("compile.state.changed", "compile", sessionId, registry.compileVersion(sessionId));
        context.json(result);
    }

    private void state(Context context) {
        context.json(registry.queryCompileSession(sessionId(context), MiniCObservationApi::currentState));
    }

    private void stage(Context context) {
        context.json(registry.queryCompileSession(sessionId(context), MiniCObservationApi::currentStageData));
    }

    private void global(Context context) {
        context.json(registry.queryCompileSession(sessionId(context), MiniCObservationApi::globalData));
    }

    private void visual(Context context) {
        context.json(registry.queryCompileSession(sessionId(context), MiniCObservationApi::currentStageVisualData));
    }

    private void snapshot(Context context) {
        context.json(snapshot(sessionId(context)));
    }

    private CompileSnapshotResponse snapshot(String sessionId) {
        return registry.queryCompileSession(sessionId, api -> new CompileSnapshotResponse(
                api.currentState(),
                api.currentStageData(),
                api.globalData(),
                api.currentStageVisualData()
        ));
    }

    private UiControlResultDto runCompileCommand(
            MiniCObservationApi api,
            String command,
            CommandInputRequest input
    ) {
        return switch (command) {
            case "next" -> api.next();
            case "next-stage" -> api.nextStage();
            case "run-to-execution" -> runToExecution(api);
            case "play" -> api.play();
            case "play-fast" -> api.playFast();
            case "tick" -> api.tick();
            case "pause" -> api.pause();
            case "execution-input" -> api.confirmExecutionInput(input.standardInput());
            default -> throw new IllegalArgumentException("unknown compile command: " + command);
        };
    }

    private UiControlResultDto runToExecution(MiniCObservationApi api) {
        UiControlResultDto result = api.currentState().canNext()
                ? api.nextStage()
                : api.next();
        int guard = 0;
        while (!"execution".equals(api.currentState().currentStage())
                && api.currentState().canNext()
                && guard++ < RUN_TO_EXECUTION_GUARD) {
            result = api.nextStage();
            if ("FAILED".equals(result.outcome()) || "CANNOT_ADVANCE".equals(result.outcome())) {
                return result;
            }
        }
        return result;
    }

    private CommandInputRequest commandInput(Context context) {
        if (context.body().isBlank()) {
            return new CommandInputRequest("");
        }
        CommandInputRequest request = context.bodyAsClass(CommandInputRequest.class);
        return new CommandInputRequest(request.standardInput() == null ? "" : request.standardInput());
    }

    private String sessionId(Context context) {
        return context.pathParam("id");
    }
}
