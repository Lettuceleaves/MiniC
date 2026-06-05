package minic.web.routes;

import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import minic.uiapi.MiniCDebugApi;
import minic.uiapi.UiDebugStateDto;
import minic.web.MiniCWebSessionRegistry;
import minic.web.dto.WebSessionDtos.BreakpointRequest;
import minic.web.dto.WebSessionDtos.CreateSessionRequest;
import minic.web.dto.WebSessionDtos.DebugSnapshotResponse;

import java.util.Objects;

/**
 * REST routes for independent debug sessions.
 */
public final class DebugSessionRoutes {
    private final MiniCWebSessionRegistry registry;

    public DebugSessionRoutes(MiniCWebSessionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void register(JavalinDefaultRoutingApi routes) {
        routes.post("/api/debug/sessions", this::createSession);
        routes.delete("/api/debug/sessions/{id}", this::closeSession);
        routes.post("/api/debug/sessions/{id}/source", this::updateSource);
        routes.post("/api/debug/sessions/{id}/start", this::startSession);
        routes.post("/api/debug/sessions/{id}/breakpoints", this::addBreakpoint);
        routes.delete("/api/debug/sessions/{id}/breakpoints/{line}", this::removeBreakpoint);
        routes.post("/api/debug/sessions/{id}/commands/{command}", this::runCommand);
        routes.get("/api/debug/sessions/{id}/state", this::state);
        routes.get("/api/debug/sessions/{id}/views/metadata", this::metadataView);
        routes.get("/api/debug/sessions/{id}/views/ast", this::astView);
        routes.get("/api/debug/sessions/{id}/views/ir", this::irView);
        routes.get("/api/debug/sessions/{id}/views/asm", this::asmView);
        routes.get("/api/debug/sessions/{id}/views/data-structure", this::dataStructureView);
        routes.get("/api/debug/sessions/{id}/snapshot", this::snapshot);
    }

    private void createSession(Context context) {
        CreateSessionRequest request = context.bodyAsClass(CreateSessionRequest.class);
        context.status(201).json(registry.createDebugSession(request.sourceName(), request.sourceText()));
    }

    private void closeSession(Context context) {
        context.json(registry.closeDebugSession(sessionId(context)));
    }

    private void updateSource(Context context) {
        CreateSessionRequest request = context.bodyAsClass(CreateSessionRequest.class);
        String sessionId = sessionId(context);
        registry.updateDebugSource(sessionId, request.sourceName(), request.sourceText());
        registry.startDebugSession(sessionId);
        context.json(snapshot(sessionId));
    }

    private void startSession(Context context) {
        String sessionId = sessionId(context);
        registry.startDebugSession(sessionId);
        context.json(snapshot(sessionId));
    }

    private void addBreakpoint(Context context) {
        BreakpointRequest request = context.bodyAsClass(BreakpointRequest.class);
        int line = requirePositiveLine(request.line());
        UiDebugStateDto state = registry.commandDebugSession(sessionId(context), api -> api.setBreakpoint(line));
        context.json(state);
    }

    private void removeBreakpoint(Context context) {
        int line = requirePositiveLine(parseLine(context.pathParam("line")));
        UiDebugStateDto state = registry.commandDebugSession(sessionId(context), api -> api.clearBreakpoint(line));
        context.json(state);
    }

    private void runCommand(Context context) {
        String command = context.pathParam("command");
        UiDebugStateDto state = registry.commandDebugSession(sessionId(context),
                api -> runDebugCommand(api, command));
        context.json(state);
    }

    private void state(Context context) {
        context.json(registry.queryDebugSession(sessionId(context), MiniCDebugApi::currentState));
    }

    private void metadataView(Context context) {
        context.json(registry.queryDebugSession(sessionId(context), MiniCDebugApi::metadataView));
    }

    private void astView(Context context) {
        context.json(registry.queryDebugSession(sessionId(context), MiniCDebugApi::astDebugView));
    }

    private void irView(Context context) {
        context.json(registry.queryDebugSession(sessionId(context), MiniCDebugApi::irDebugView));
    }

    private void asmView(Context context) {
        context.json(registry.queryDebugSession(sessionId(context), MiniCDebugApi::asmDebugView));
    }

    private void dataStructureView(Context context) {
        context.json(registry.queryDebugSession(sessionId(context), MiniCDebugApi::dataStructureDebugView));
    }

    private void snapshot(Context context) {
        context.json(snapshot(sessionId(context)));
    }

    private DebugSnapshotResponse snapshot(String sessionId) {
        return registry.queryDebugSession(sessionId, api -> new DebugSnapshotResponse(
                api.currentState(),
                api.metadataView(),
                api.astDebugView(),
                api.irDebugView(),
                api.asmDebugView(),
                api.dataStructureDebugView()
        ));
    }

    private UiDebugStateDto runDebugCommand(MiniCDebugApi api, String command) {
        return switch (command) {
            case "run-to-breakpoint" -> api.runToBreakpoint();
            case "run-to-end" -> api.runToEnd();
            case "fast-forward" -> api.fastForward();
            case "step-over" -> api.stepOver();
            case "step-into" -> api.stepInto();
            case "step-out" -> api.stepOut();
            case "pause" -> api.pause();
            case "restart" -> api.restart();
            case "close" -> api.close();
            case "step-back" -> api.stepBack();
            case "step-back-over" -> api.stepBackOver();
            case "back-to-breakpoint" -> api.backToBreakpoint();
            case "back-to-call-site" -> api.backToCallSite();
            default -> throw new IllegalArgumentException("unknown debug command: " + command);
        };
    }

    private int parseLine(String rawLine) {
        try {
            return Integer.parseInt(rawLine);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("line must be an integer");
        }
    }

    private int requirePositiveLine(int line) {
        if (line < 1) {
            throw new IllegalArgumentException("line must be 1-based");
        }
        return line;
    }

    private String sessionId(Context context) {
        return context.pathParam("id");
    }
}
