package minic.web.dto;

/**
 * Web session request and response records.
 */
public final class WebSessionDtos {
    private WebSessionDtos() {
    }

    public record CreateSessionRequest(String sourceName, String sourceText) {
    }

    public record SessionCreatedResponse(String sessionId, long version) {
    }

    public record SessionClosedResponse(String sessionId, long version, boolean closed) {
    }
}
