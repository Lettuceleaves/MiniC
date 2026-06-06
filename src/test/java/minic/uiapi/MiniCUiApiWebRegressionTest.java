package minic.uiapi;

import minic.uiapi.web.MiniCUiApiJson;
import minic.uiapi.web.MiniCUiApiRouter;
import minic.uiapi.web.MiniCUiApiServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCUiApiWebRegressionTest {
    private static final String OBSERVATION_SOURCE = """
            #define VALUE 4
            int main() { return VALUE; }
            """;
    private static final String DEBUG_SOURCE = """
            int add(int x) {
                int y = x + 1;
                return y;
            }
            int main() {
                int a = 1;
                int b = add(a);
                return b;
            }
            """;
    private static final String CONCURRENT_VISUAL_SOURCE = """
            int add(int a, int b) {
                return a + b;
            }

            int main() {
                int x = 0;
                x = add(x, 1);
                x = add(x, 2);
                x = add(x, 3);
                x = add(x, 4);
                x = add(x, 5);
                x = add(x, 6);
                x = add(x, 7);
                x = add(x, 8);
                return x;
            }
            """;

    private MiniCUiApiServer server;
    private HttpClient client;
    private MiniCUiApiJson json;

    @BeforeEach
    void startServer() throws Exception {
        server = MiniCUiApiServer.create(0);
        server.start();
        client = HttpClient.newHttpClient();
        json = new MiniCUiApiJson();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void healthRealtimeAnalyzeAndTokenizeUseRealUiApiDtos() throws Exception {
        assertThat(get("/api/health", MiniCUiApiRouter.HealthResponse.class).status()).isEqualTo("ok");

        MiniCRealtimeAnalysisApi direct = new MiniCRealtimeAnalysisApi();
        UiRealtimeAnalysisDto expected = direct.analyze("realtime.mc", OBSERVATION_SOURCE, 7);
        UiRealtimeAnalysisDto actual = post("/api/realtime/analyze", Map.of(
                "sourceName", "realtime.mc",
                "sourceText", OBSERVATION_SOURCE,
                "version", 7
        ), UiRealtimeAnalysisDto.class);

        assertThat(actual).isEqualTo(expected);

        List<UiLexerTokenVisualDto> expectedTokens = direct.tokenize("guide.mc", "int main() { return 0; }");
        List<UiLexerTokenVisualDto> actualTokens = postList("/api/realtime/tokenize", Map.of(
                "sourceName", "guide.mc",
                "sourceText", "int main() { return 0; }"
        ), UiLexerTokenVisualDto.class);

        assertThat(actualTokens).isEqualTo(expectedTokens);
    }

    @Test
    void observationEndpointsMirrorDirectFacadeStateVisualsAndGlobalData() throws Exception {
        MiniCObservationApi direct = new MiniCObservationApi();
        direct.loadSource("observe.mc", OBSERVATION_SOURCE);
        direct.startSession();

        String sessionId = post("/api/observation/sessions", Map.of(), MiniCUiApiRouter.SessionResponse.class)
                .sessionId();
        post("/api/observation/" + sessionId + "/source", sourceBody("observe.mc", OBSERVATION_SOURCE),
                MiniCUiApiRouter.StatusResponse.class);

        assertThat(post("/api/observation/" + sessionId + "/start", Map.of(), UiCurrentStateDto.class))
                .isEqualTo(direct.currentState());

        assertThat(post("/api/observation/" + sessionId + "/next", Map.of(), UiControlResultDto.class))
                .isEqualTo(direct.next());
        advanceObservationToLexer(sessionId, direct);

        assertThat(get("/api/observation/" + sessionId + "/state", UiCurrentStateDto.class))
                .isEqualTo(direct.currentState());
        assertThat(get("/api/observation/" + sessionId + "/stage-data", UiStageDataDto.class))
                .isEqualTo(direct.currentStageData());
        assertThat(get("/api/observation/" + sessionId + "/visual/current", UiStageVisualDto.class))
                .isEqualTo(direct.currentStageVisualData());
        assertThat(get("/api/observation/" + sessionId + "/visual/lexer", UiStageVisualDto.class))
                .isEqualTo(direct.lexerVisualData());
        assertThat(get("/api/observation/" + sessionId + "/global", UiGlobalDataDto.class))
                .isEqualTo(direct.globalData());
        assertThat(post("/api/observation/" + sessionId + "/previous", Map.of(), UiControlResultDto.class))
                .isEqualTo(direct.previous());
        assertThat(post("/api/observation/" + sessionId + "/reverse-play", Map.of(), UiControlResultDto.class))
                .isEqualTo(direct.reversePlay());
    }

    @Test
    void observationSessionSerializesConcurrentVisualQueriesWithoutCorruptingStageState() throws Exception {
        String sessionId = post("/api/observation/sessions", Map.of(), MiniCUiApiRouter.SessionResponse.class)
                .sessionId();
        post("/api/observation/" + sessionId + "/source",
                sourceBody("concurrent-visual.mc", CONCURRENT_VISUAL_SOURCE),
                MiniCUiApiRouter.StatusResponse.class);
        post("/api/observation/" + sessionId + "/start", Map.of(), UiCurrentStateDto.class);
        post("/api/observation/" + sessionId + "/next", Map.of(), UiControlResultDto.class);
        post("/api/observation/" + sessionId + "/next-stage", Map.of(), UiControlResultDto.class);

        List<String> concurrentPaths = List.of(
                "/state",
                "/stage-data",
                "/global",
                "/visual/current",
                "/visual/lexer",
                "/visual/ast",
                "/visual/semantic",
                "/visual/codegen"
        );
        List<CompletableFuture<HttpResponse<String>>> futures = IntStream.range(0, 10)
                .boxed()
                .flatMap(_iteration -> concurrentPaths.stream())
                .map(path -> rawAsync("GET", "/api/observation/" + sessionId + path, null))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        for (CompletableFuture<HttpResponse<String>> future : futures) {
            HttpResponse<String> response = future.join();
            assertThat(response.statusCode()).isBetween(200, 299);
        }

        UiCurrentStateDto state = get("/api/observation/" + sessionId + "/state", UiCurrentStateDto.class);
        for (int guard = 0; !"execution".equals(state.currentStage()) && guard < 20; guard++) {
            post("/api/observation/" + sessionId + "/next-stage", Map.of(), UiControlResultDto.class);
            state = get("/api/observation/" + sessionId + "/state", UiCurrentStateDto.class);
        }

        assertThat(state.currentStage()).isEqualTo("execution");
        assertThat(get("/api/observation/" + sessionId + "/global", UiGlobalDataDto.class).diagnostics()).isEmpty();
    }

    @Test
    void debugEndpointsMirrorDirectFacadeViewsAndBackToCallSiteControl() throws Exception {
        MiniCDebugApi direct = debugAtCalleeSecondStatement();
        MiniCDebugApi wrongControl = debugAtCalleeSecondStatement();
        UiDebugStateDto expectedBackToCallSite = direct.backToCallSite();
        UiDebugStateDto wrongBackOver = wrongControl.stepBackOver();
        assertThat(expectedBackToCallSite.currentSnapshot().snapshotId())
                .isNotEqualTo(wrongBackOver.currentSnapshot().snapshotId());

        String sessionId = post("/api/debug/sessions", Map.of(), MiniCUiApiRouter.SessionResponse.class)
                .sessionId();
        post("/api/debug/" + sessionId + "/source", sourceBody("debug.mc", DEBUG_SOURCE),
                MiniCUiApiRouter.StatusResponse.class);
        post("/api/debug/" + sessionId + "/start", Map.of(), UiDebugStateDto.class);
        post("/api/debug/" + sessionId + "/breakpoints/7", Map.of(), UiDebugStateDto.class);
        post("/api/debug/" + sessionId + "/run-to-breakpoint", Map.of(), UiDebugStateDto.class);
        post("/api/debug/" + sessionId + "/step-into", Map.of(), UiDebugStateDto.class);
        post("/api/debug/" + sessionId + "/step-over", Map.of(), UiDebugStateDto.class);

        assertThat(post("/api/debug/" + sessionId + "/back-to-call-site", Map.of(), UiDebugStateDto.class))
                .isEqualTo(expectedBackToCallSite);
        assertThat(get("/api/debug/" + sessionId + "/state", UiDebugStateDto.class))
                .isEqualTo(expectedBackToCallSite);
        assertThat(get("/api/debug/" + sessionId + "/metadata", UiDebugMetadataViewDto.class))
                .isEqualTo(direct.metadataView());
        assertThat(get("/api/debug/" + sessionId + "/ast", UiDebugAstViewDto.class))
                .isEqualTo(direct.astDebugView());
        assertThat(get("/api/debug/" + sessionId + "/ir", UiDebugIrViewDto.class))
                .isEqualTo(direct.irDebugView());
        assertThat(get("/api/debug/" + sessionId + "/asm", UiDebugAsmViewDto.class))
                .isEqualTo(direct.asmDebugView());
        assertThat(get("/api/debug/" + sessionId + "/data-structure", UiDebugDataStructureViewDto.class))
                .isEqualTo(direct.dataStructureDebugView());
        assertThat(delete("/api/debug/" + sessionId + "/breakpoints/7", UiDebugStateDto.class))
                .isEqualTo(direct.clearBreakpoint(7));

        post("/api/debug/" + sessionId + "/close", Map.of(), UiDebugStateDto.class);
        HttpResponse<String> closedResponse = raw("GET", "/api/debug/" + sessionId + "/state", null);
        assertThat(closedResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void errorsAreStructuredAndNeverSwallowed() throws Exception {
        HttpResponse<String> missing = raw("GET", "/api/observation/missing/state", null);
        assertThat(missing.statusCode()).isEqualTo(404);
        MiniCUiApiRouter.ErrorResponse missingError = json.read(missing.body(),
                MiniCUiApiRouter.ErrorResponse.class);
        assertThat(missingError.status()).isEqualTo(404);
        assertThat(missingError.method()).isEqualTo("GET");
        assertThat(missingError.path()).isEqualTo("/api/observation/missing/state");
        assertThat(missingError.message()).contains("missing");

        String sessionId = post("/api/debug/sessions", Map.of(), MiniCUiApiRouter.SessionResponse.class)
                .sessionId();
        post("/api/debug/" + sessionId + "/source",
                sourceBody("bad.mc", "int main() { return missing; }"),
                MiniCUiApiRouter.StatusResponse.class);
        HttpResponse<String> badSource = raw("POST", "/api/debug/" + sessionId + "/start", "{}");
        assertThat(badSource.statusCode()).isEqualTo(409);
        MiniCUiApiRouter.ErrorResponse badSourceError = json.read(badSource.body(),
                MiniCUiApiRouter.ErrorResponse.class);
        assertThat(badSourceError.status()).isEqualTo(409);
        assertThat(badSourceError.message()).contains("semantic diagnostics");
    }

    private MiniCDebugApi debugAtCalleeSecondStatement() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("debug.mc", DEBUG_SOURCE);
        api.startDebug();
        api.setBreakpoint(7);
        api.runToBreakpoint();
        api.stepInto();
        api.stepOver();
        return api;
    }

    private void advanceObservationToLexer(String sessionId, MiniCObservationApi direct) throws Exception {
        for (int guard = 0; !direct.currentState().currentStage().equals("lexer") && guard < 1000; guard++) {
            assertThat(post("/api/observation/" + sessionId + "/next", Map.of(), UiControlResultDto.class))
                    .isEqualTo(direct.next());
        }
        assertThat(direct.currentState().currentStage()).isEqualTo("lexer");
    }

    private Map<String, String> sourceBody(String sourceName, String sourceText) {
        return Map.of("sourceName", sourceName, "sourceText", sourceText);
    }

    private <T> T get(String path, Class<T> responseType) throws Exception {
        HttpResponse<String> response = raw("GET", path, null);
        assertThat(response.statusCode()).isBetween(200, 299);
        return json.read(response.body(), responseType);
    }

    private <T> T post(String path, Object body, Class<T> responseType) throws Exception {
        HttpResponse<String> response = raw("POST", path, json.write(body));
        assertThat(response.statusCode()).isBetween(200, 299);
        return json.read(response.body(), responseType);
    }

    private <T> List<T> postList(String path, Object body, Class<T> elementType) throws Exception {
        HttpResponse<String> response = raw("POST", path, json.write(body));
        assertThat(response.statusCode()).isBetween(200, 299);
        return json.readList(response.body(), elementType);
    }

    private <T> T delete(String path, Class<T> responseType) throws Exception {
        HttpResponse<String> response = raw("DELETE", path, null);
        assertThat(response.statusCode()).isBetween(200, 299);
        return json.read(response.body(), responseType);
    }

    private HttpResponse<String> raw(String method, String path, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(server.baseUri().resolve(URI.create(path)))
                .method(method, publisher)
                .header("Content-Type", "application/json")
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private CompletableFuture<HttpResponse<String>> rawAsync(String method, String path, String body) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(server.baseUri().resolve(URI.create(path)))
                .method(method, publisher)
                .header("Content-Type", "application/json")
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
}
