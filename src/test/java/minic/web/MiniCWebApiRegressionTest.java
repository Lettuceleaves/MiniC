package minic.web;

import minic.settings.MiniCSettings;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWebApiRegressionTest {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("\"sessionId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SNAPSHOT_ID_PATTERN = Pattern.compile("\"snapshotId\"\\s*:\\s*(\\d+)");

    @Test
    void servesHealthEndpointWithoutStartingJavaFx() throws Exception {
        try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start();
             HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(server.uri("/api/health")).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"ok\"");
        }
    }

    @Test
    void servesBuiltWorkbenchAssetsFromSameOrigin() throws Exception {
        try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start();
             HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = get(server, client, "/");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body())
                    .contains("<title>MiniC Workbench</title>")
                    .contains("/assets/");
        }
    }

    @Test
    void servesRealtimeAnalysisAndSettingsRoutesWithoutStartingJavaFx() throws Exception {
        withSettingsFile("""
                {
                  "theme": "dark",
                  "frameInterval": 1000,
                  "uiScale": 1.0,
                  "openFiles": []
                }
                """, () -> {
            try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start();
                 HttpClient client = HttpClient.newHttpClient()) {
                HttpResponse<String> invalid = post(server, client, "/api/analysis/realtime",
                        "{\"sourceName\":\"bad.mc\",\"sourceText\":\"int main( {\",\"version\":7}");
                assertThat(invalid.statusCode()).isEqualTo(200);
                assertThat(invalid.body())
                        .contains("\"sourceName\":\"bad.mc\"")
                        .contains("\"version\":7")
                        .contains("\"diagnostics\":[{");

                HttpResponse<String> valid = post(server, client, "/api/analysis/realtime",
                        "{\"sourceName\":\"main.mc\",\"sourceText\":\"int main() { return 0; }\",\"version\":8}");
                assertThat(valid.statusCode()).isEqualTo(200);
                assertThat(valid.body())
                        .contains("\"version\":8")
                        .contains("\"diagnostics\":[]")
                        .contains("\"tokens\":[{");

                HttpResponse<String> settings = get(server, client, "/api/settings");
                assertThat(settings.statusCode()).isEqualTo(200);
                assertThat(settings.body())
                        .contains("\"theme\":\"dark\"")
                        .contains("\"frameIntervalMillis\":1000")
                        .contains("\"uiScale\":1.0");

                HttpResponse<String> themes = get(server, client, "/api/settings/themes");
                assertThat(themes.statusCode()).isEqualTo(200);
                assertThat(themes.body())
                        .contains("\"currentTheme\":\"dark\"")
                        .contains("\"dark\"")
                        .contains("\"light\"");

                HttpResponse<String> clampedLow = patch(server, client, "/api/settings",
                        "{\"theme\":\"light\",\"frameIntervalMillis\":-10,\"uiScale\":9}");
                assertThat(clampedLow.statusCode()).isEqualTo(200);
                assertThat(clampedLow.body())
                        .contains("\"theme\":\"light\"")
                        .contains("\"frameIntervalMillis\":" + MiniCSettings.minFrameInterval())
                        .contains("\"uiScale\":" + MiniCSettings.maxUiScale());

                HttpResponse<String> clampedHigh = patch(server, client, "/api/settings",
                        "{\"frameIntervalMillis\":100000}");
                assertThat(clampedHigh.statusCode()).isEqualTo(200);
                assertThat(clampedHigh.body())
                        .contains("\"frameIntervalMillis\":" + MiniCSettings.maxFrameInterval());
            }
        });
    }

    @Test
    void servesCompileSessionWorkflow() throws Exception {
        try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start();
             HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> created = post(server, client, "/api/compile/sessions",
                    "{\"sourceName\":\"main.mc\",\"sourceText\":\"int main() { return 0; }\"}");
            assertThat(created.statusCode()).isEqualTo(201);
            String sessionId = sessionId(created.body());

            HttpResponse<String> sourceUpdated = post(server, client, "/api/compile/sessions/" + sessionId + "/source",
                    "{\"sourceName\":\"updated.mc\",\"sourceText\":\"int main() { return 1; }\"}");
            assertThat(sourceUpdated.statusCode()).isEqualTo(200);
            assertThat(sourceUpdated.body()).contains("\"sourceName\":\"updated.mc\"").contains("\"state\"");

            HttpResponse<String> started = post(server, client, "/api/compile/sessions/" + sessionId + "/start", "");
            assertThat(started.statusCode()).isEqualTo(200);
            assertThat(started.body())
                    .contains("\"state\"")
                    .contains("\"stage\"")
                    .contains("\"global\"")
                    .contains("\"visual\"");

            assertThat(post(server, client, "/api/compile/sessions/" + sessionId + "/commands/next-stage", "")
                    .statusCode()).isEqualTo(200);
            assertThat(post(server, client, "/api/compile/sessions/" + sessionId + "/commands/next-stage", "")
                    .statusCode()).isEqualTo(200);
            assertThat(post(server, client, "/api/compile/sessions/" + sessionId + "/commands/next", "")
                    .statusCode()).isEqualTo(200);

            HttpResponse<String> state = get(server, client, "/api/compile/sessions/" + sessionId + "/state");
            assertThat(state.statusCode()).isEqualTo(200);
            assertThat(state.body()).contains("\"currentStage\":\"lexer\"");

            HttpResponse<String> stage = get(server, client, "/api/compile/sessions/" + sessionId + "/stage");
            assertThat(stage.statusCode()).isEqualTo(200);
            assertThat(stage.body()).contains("\"stage\":\"lexer\"");

            HttpResponse<String> global = get(server, client, "/api/compile/sessions/" + sessionId + "/global");
            assertThat(global.statusCode()).isEqualTo(200);
            assertThat(global.body()).contains("\"stageSummaries\"");

            HttpResponse<String> visual = get(server, client, "/api/compile/sessions/" + sessionId + "/visual/current");
            assertThat(visual.statusCode()).isEqualTo(200);
            assertThat(visual.body()).contains("\"visualType\":\"lexer\"").contains("\"lexerTokens\":[{");

            HttpResponse<String> snapshot = get(server, client, "/api/compile/sessions/" + sessionId + "/snapshot");
            assertThat(snapshot.statusCode()).isEqualTo(200);
            assertThat(snapshot.body()).contains("\"state\"").contains("\"stage\"").contains("\"global\"").contains("\"visual\"");

            HttpResponse<String> closed = delete(server, client, "/api/compile/sessions/" + sessionId);
            assertThat(closed.statusCode()).isEqualTo(200);
            assertThat(closed.body()).contains("\"closed\":true");

            HttpResponse<String> afterClose = get(server, client, "/api/compile/sessions/" + sessionId + "/state");
            assertThat(afterClose.statusCode()).isEqualTo(404);
            assertThat(afterClose.body()).contains("\"code\":\"session-not-found\"").doesNotContain("Exception");
        }
    }

    @Test
    void returnsStructuredErrorsForInvalidCompileRequests() throws Exception {
        try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start();
             HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> missingBodyFields = post(server, client, "/api/compile/sessions", "{}");
            assertThat(missingBodyFields.statusCode()).isEqualTo(400);
            assertThat(missingBodyFields.body()).contains("\"code\":\"bad-request\"").doesNotContain("Exception");

            HttpResponse<String> missing = get(server, client, "/api/compile/sessions/missing/state");
            assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(missing.body()).contains("\"code\":\"session-not-found\"").doesNotContain("Exception");

            HttpResponse<String> created = post(server, client, "/api/compile/sessions",
                    "{\"sourceName\":\"main.mc\",\"sourceText\":\"int main() { return 0; }\"}");
            String sessionId = sessionId(created.body());

            HttpResponse<String> commandBeforeStart = post(server, client,
                    "/api/compile/sessions/" + sessionId + "/commands/next", "");
            assertThat(commandBeforeStart.statusCode()).isEqualTo(409);
            assertThat(commandBeforeStart.body()).contains("\"code\":\"session-conflict\"").doesNotContain("Exception");

            HttpResponse<String> unknownCommand = post(server, client,
                    "/api/compile/sessions/" + sessionId + "/commands/not-a-command", "");
            assertThat(unknownCommand.statusCode()).isEqualTo(400);
            assertThat(unknownCommand.body()).contains("\"code\":\"bad-request\"").doesNotContain("Exception");

            HttpResponse<String> invalidSource = post(server, client, "/api/compile/sessions",
                    "{\"sourceName\":\"bad.mc\",\"sourceText\":\"int main( {\"}");
            String invalidSessionId = sessionId(invalidSource.body());
            assertThat(post(server, client, "/api/compile/sessions/" + invalidSessionId + "/start", "").statusCode())
                    .isEqualTo(200);

            HttpResponse<String> blocked = post(server, client,
                    "/api/compile/sessions/" + invalidSessionId + "/commands/run-to-execution", "");
            assertThat(blocked.statusCode()).isEqualTo(200);
            assertThat(blocked.body()).contains("\"outcome\":\"FAILED\"").contains("\"diagnostics\":[{");

            HttpResponse<String> blockedState = get(server, client,
                    "/api/compile/sessions/" + invalidSessionId + "/state");
            assertThat(blockedState.statusCode()).isEqualTo(200);
            assertThat(blockedState.body()).doesNotContain("\"currentStage\":\"execution\"");
        }
    }

    @Test
    void servesDebugSessionWorkflow() throws Exception {
        try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start();
             HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> created = post(server, client, "/api/debug/sessions",
                    "{\"sourceName\":\"debug.mc\",\"sourceText\":\"int main() { return 0; }\"}");
            assertThat(created.statusCode()).isEqualTo(201);
            String sessionId = sessionId(created.body());

            HttpResponse<String> sourceUpdated = post(server, client, "/api/debug/sessions/" + sessionId + "/source",
                    "{\"sourceName\":\"debug-updated.mc\",\"sourceText\":\"int main() { int x = 1; return x; }\"}");
            assertThat(sourceUpdated.statusCode()).isEqualTo(200);
            assertThat(sourceUpdated.body()).contains("\"state\"").contains("\"metadata\"");

            HttpResponse<String> started = post(server, client, "/api/debug/sessions/" + sessionId + "/start", "");
            assertThat(started.statusCode()).isEqualTo(200);
            assertThat(started.body())
                    .contains("\"state\"")
                    .contains("\"metadata\"")
                    .contains("\"ast\"")
                    .contains("\"ir\"")
                    .contains("\"asm\"")
                    .contains("\"dataStructure\"");

            HttpResponse<String> breakpoint = post(server, client,
                    "/api/debug/sessions/" + sessionId + "/breakpoints", "{\"line\":1}");
            assertThat(breakpoint.statusCode()).isEqualTo(200);
            assertThat(breakpoint.body()).contains("\"breakpoints\"");

            assertThat(post(server, client, "/api/debug/sessions/" + sessionId + "/commands/step-over", "")
                    .statusCode()).isEqualTo(200);
            long afterStepOverSnapshotId = firstSnapshotId(get(server, client,
                    "/api/debug/sessions/" + sessionId + "/state").body());
            HttpResponse<String> stepBack = post(server, client,
                    "/api/debug/sessions/" + sessionId + "/commands/step-back", "");
            assertThat(stepBack.statusCode()).isEqualTo(200);
            long afterStepBackSnapshotId = firstSnapshotId(stepBack.body());
            assertThat(afterStepBackSnapshotId).isLessThanOrEqualTo(afterStepOverSnapshotId);

            assertJsonOk(server, client, "/api/debug/sessions/" + sessionId + "/state", "\"executionState\"");
            assertJsonOk(server, client, "/api/debug/sessions/" + sessionId + "/views/metadata", "\"timeline\"");
            assertJsonOk(server, client, "/api/debug/sessions/" + sessionId + "/views/ast", "\"root\"");
            assertJsonOk(server, client, "/api/debug/sessions/" + sessionId + "/views/ir", "\"lines\"");
            assertJsonOk(server, client, "/api/debug/sessions/" + sessionId + "/views/asm", "\"lines\"");
            assertJsonOk(server, client, "/api/debug/sessions/" + sessionId + "/views/data-structure", "\"processSpace\"");
            assertJsonOk(server, client, "/api/debug/sessions/" + sessionId + "/snapshot", "\"dataStructure\"");

            HttpResponse<String> removed = delete(server, client,
                    "/api/debug/sessions/" + sessionId + "/breakpoints/1");
            assertThat(removed.statusCode()).isEqualTo(200);

            HttpResponse<String> closed = delete(server, client, "/api/debug/sessions/" + sessionId);
            assertThat(closed.statusCode()).isEqualTo(200);
            assertThat(closed.body()).contains("\"closed\":true");
        }
    }

    @Test
    void supportsSpecDebugSessionAliasesAndBreakpointRunAssertions() throws Exception {
        try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start();
             HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> created = post(server, client, "/api/debug-sessions",
                    "{\"sourceName\":\"debug.mc\",\"sourceText\":\"int main() { int x = 1; x = x + 1; return x; }\"}");
            assertThat(created.statusCode()).isEqualTo(201);
            String sessionId = sessionId(created.body());

            assertThat(post(server, client, "/api/debug-sessions/" + sessionId + "/start", "").statusCode())
                    .isEqualTo(200);
            assertThat(post(server, client, "/api/debug-sessions/" + sessionId + "/breakpoints/1", "").statusCode())
                    .isEqualTo(200);

            HttpResponse<String> breakpointRun = post(server, client,
                    "/api/debug-sessions/" + sessionId + "/run-to-breakpoint", "");
            assertThat(breakpointRun.statusCode()).isEqualTo(200);
            assertThat(breakpointRun.body())
                    .contains("\"currentSnapshot\"")
                    .contains("\"sourceRange\"")
                    .contains("\"startLine\":1");

            long breakpointSnapshotId = firstSnapshotId(breakpointRun.body());
            HttpResponse<String> stepOver = post(server, client,
                    "/api/debug-sessions/" + sessionId + "/step-over", "");
            assertThat(stepOver.statusCode()).isEqualTo(200);
            long stepOverSnapshotId = firstSnapshotId(stepOver.body());
            assertThat(stepOverSnapshotId).isGreaterThanOrEqualTo(breakpointSnapshotId);

            HttpResponse<String> stepBack = post(server, client,
                    "/api/debug-sessions/" + sessionId + "/step-back", "");
            assertThat(stepBack.statusCode()).isEqualTo(200);
            long stepBackSnapshotId = firstSnapshotId(stepBack.body());
            assertThat(stepBackSnapshotId).isLessThanOrEqualTo(stepOverSnapshotId);
        }
    }

    @Test
    void returnsStructuredErrorsForInvalidDebugRequests() throws Exception {
        try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start();
             HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> missingBodyFields = post(server, client, "/api/debug/sessions", "{}");
            assertThat(missingBodyFields.statusCode()).isEqualTo(400);
            assertThat(missingBodyFields.body()).contains("\"code\":\"bad-request\"").doesNotContain("Exception");

            HttpResponse<String> missing = get(server, client, "/api/debug/sessions/missing/state");
            assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(missing.body()).contains("\"code\":\"session-not-found\"").doesNotContain("Exception");

            HttpResponse<String> created = post(server, client, "/api/debug/sessions",
                    "{\"sourceName\":\"debug.mc\",\"sourceText\":\"int main() { return 0; }\"}");
            String sessionId = sessionId(created.body());

            HttpResponse<String> invalidLine = post(server, client,
                    "/api/debug/sessions/" + sessionId + "/breakpoints", "{\"line\":0}");
            assertThat(invalidLine.statusCode()).isEqualTo(400);
            assertThat(invalidLine.body()).contains("\"code\":\"bad-request\"").doesNotContain("Exception");

            HttpResponse<String> invalidSource = post(server, client, "/api/debug/sessions",
                    "{\"sourceName\":\"bad.mc\",\"sourceText\":\"int main( {\"}");
            String invalidSessionId = sessionId(invalidSource.body());

            HttpResponse<String> startInvalid = post(server, client,
                    "/api/debug/sessions/" + invalidSessionId + "/start", "");
            assertThat(startInvalid.statusCode()).isEqualTo(409);
            assertThat(startInvalid.body()).contains("\"code\":\"session-conflict\"").doesNotContain("Exception");
        }
    }

    private static HttpResponse<String> get(MiniCWebServer server, HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(server.uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(
            MiniCWebServer server,
            HttpClient client,
            String path,
            String body
    ) throws Exception {
        HttpRequest.BodyPublisher bodyPublisher = body == null || body.isBlank()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return client.send(jsonRequest(server.uri(path)).POST(bodyPublisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> delete(MiniCWebServer server, HttpClient client, String path) throws Exception {
        return client.send(jsonRequest(server.uri(path)).DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> patch(
            MiniCWebServer server,
            HttpClient client,
            String path,
            String body
    ) throws Exception {
        return client.send(jsonRequest(server.uri(path))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertJsonOk(
            MiniCWebServer server,
            HttpClient client,
            String path,
            String expectedJsonFragment
    ) throws Exception {
        HttpResponse<String> response = get(server, client, path);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(expectedJsonFragment);
    }

    private static HttpRequest.Builder jsonRequest(URI uri) {
        return HttpRequest.newBuilder(uri).header("Content-Type", "application/json");
    }

    private static String sessionId(String body) {
        Matcher matcher = SESSION_ID_PATTERN.matcher(body);
        assertThat(matcher.find()).as(body).isTrue();
        return matcher.group(1);
    }

    private static long firstSnapshotId(String body) {
        Matcher matcher = SNAPSHOT_ID_PATTERN.matcher(body);
        assertThat(matcher.find()).as(body).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static void withSettingsFile(String temporarySettings, ThrowingRunnable action) throws Exception {
        Path settingsFile = Path.of("config", "settings.json");
        String previous = Files.exists(settingsFile)
                ? Files.readString(settingsFile, StandardCharsets.UTF_8)
                : null;
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, temporarySettings, StandardCharsets.UTF_8);
        MiniCSettings.load();
        try {
            action.run();
        } finally {
            if (previous == null) {
                Files.deleteIfExists(settingsFile);
            } else {
                Files.writeString(settingsFile, previous, StandardCharsets.UTF_8);
            }
            MiniCSettings.load();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
