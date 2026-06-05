package minic.web;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWebSocketRegressionTest {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("\"sessionId\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void emitsCompileDebugCloseAndPingEvents() throws Exception {
        try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start();
             HttpClient client = HttpClient.newHttpClient()) {
            WebSocketMessages messages = new WebSocketMessages();
            WebSocket socket = client.newWebSocketBuilder()
                    .buildAsync(wsUri(server), messages)
                    .join();

            socket.sendText("{\"type\":\"ping\"}", true).join();
            assertThat(messages.await("pong")).contains("\"type\":\"pong\"");

            String compileSessionId = sessionId(post(server, client, "/api/compile/sessions",
                    "{\"sourceName\":\"main.mc\",\"sourceText\":\"int main() { return 0; }\"}").body());
            assertThat(post(server, client, "/api/compile/sessions/" + compileSessionId + "/start", "")
                    .statusCode()).isEqualTo(200);

            subscribe(socket, messages, "compile", compileSessionId);
            assertThat(post(server, client, "/api/compile/sessions/" + compileSessionId + "/commands/next", "")
                    .statusCode()).isEqualTo(200);
            assertThat(messages.await("compile.state.changed"))
                    .contains("\"sessionId\":\"" + compileSessionId + "\"")
                    .contains("\"version\":");

            assertThat(delete(server, client, "/api/compile/sessions/" + compileSessionId).statusCode()).isEqualTo(200);
            assertThat(messages.await("session.closed"))
                    .contains("\"scope\":\"compile\"")
                    .contains("\"sessionId\":\"" + compileSessionId + "\"");

            String debugSessionId = sessionId(post(server, client, "/api/debug-sessions",
                    "{\"sourceName\":\"debug.mc\",\"sourceText\":\"int main() { return 0; }\"}").body());
            assertThat(post(server, client, "/api/debug-sessions/" + debugSessionId + "/start", "")
                    .statusCode()).isEqualTo(200);

            subscribe(socket, messages, "debug", debugSessionId);
            assertThat(post(server, client, "/api/debug-sessions/" + debugSessionId + "/step-over", "")
                    .statusCode()).isEqualTo(200);
            assertThat(messages.await("debug.state.changed"))
                    .contains("\"sessionId\":\"" + debugSessionId + "\"")
                    .contains("\"version\":");

            socket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete").join();
            assertThat(post(server, client, "/api/debug-sessions/" + debugSessionId + "/step-back", "")
                    .statusCode()).isEqualTo(200);
        }
    }

    private static void subscribe(
            WebSocket socket,
            WebSocketMessages messages,
            String scope,
            String sessionId
    ) {
        socket.sendText("{\"type\":\"subscribe\",\"scope\":\"" + scope + "\",\"sessionId\":\"" + sessionId + "\"}", true)
                .join();
        assertThat(messages.await("subscribed"))
                .contains("\"scope\":\"" + scope + "\"")
                .contains("\"sessionId\":\"" + sessionId + "\"");
    }

    private static URI wsUri(MiniCWebServer server) {
        URI httpUri = server.uri("/ws");
        return URI.create("ws://" + httpUri.getHost() + ":" + httpUri.getPort() + httpUri.getPath());
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

    private static final class WebSocketMessages implements WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (last) {
                messages.add(data.toString());
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        private String await(String type) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                try {
                    String message = messages.poll(100, TimeUnit.MILLISECONDS);
                    if (message != null && message.contains("\"type\":\"" + type + "\"")) {
                        return message;
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("interrupted while waiting for websocket message", exception);
                }
            }
            throw new AssertionError("timed out waiting for websocket message type: " + type);
        }
    }
}
