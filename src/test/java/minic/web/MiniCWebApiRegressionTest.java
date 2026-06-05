package minic.web;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWebApiRegressionTest {
    @Test
    void servesHealthEndpointWithoutStartingJavaFx() throws Exception {
        try (MiniCWebServer server = MiniCWebApplication.create(MiniCWebConfig.testing()).start()) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(server.uri("/api/health")).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"ok\"");
        }
    }
}
