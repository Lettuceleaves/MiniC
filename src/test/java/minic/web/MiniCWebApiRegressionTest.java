package minic.web;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWebApiRegressionTest {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("\"sessionId\"\\s*:\\s*\"([^\"]+)\"");

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
    void servesCompileSessionWorkflow() throws Exception {
        try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start();
             HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> created = post(server, client, "/api/compile/sessions",
                    "{\"sourceName\":\"main.mc\",\"sourceText\":\"int main() { return 0; }\"}");
            assertThat(created.statusCode()).isEqualTo(201);
            String sessionId = sessionId(created.body());

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
            assertThat(visual.body()).contains("\"visualType\":\"lexer\"").contains("\"lexerTokens\"");

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

    private static HttpRequest.Builder jsonRequest(URI uri) {
        return HttpRequest.newBuilder(uri).header("Content-Type", "application/json");
    }

    private static String sessionId(String body) {
        Matcher matcher = SESSION_ID_PATTERN.matcher(body);
        assertThat(matcher.find()).as(body).isTrue();
        return matcher.group(1);
    }
}
