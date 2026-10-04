package craken.visualization.api;

public record VisualizationError(Code code, String message, int commandIndex) {
    public enum Code {
        UNKNOWN_POSITION, UNRESERVED_POSITION, INVALID_OWNERSHIP, OWNERSHIP_CYCLE,
        PAGE_TYPE_MISMATCH, INVALID_COMMAND, SESSION_CLOSED, COMPOSITION_CONFLICT, NESTING_LIMIT
    }
    public static final class Failure extends RuntimeException {
        private final Code code;
        public Failure(Code code, String message) { super(message); this.code = code; }
        public Code code() { return code; }
    }
}
