package craken.visualization.layout;

public final class LayoutException extends RuntimeException {
    public enum Code { INVALID_TOPOLOGY, RUNTIME_UNAVAILABLE, TIMEOUT, PROCESS_FAILED, INVALID_RESULT, CANCELLED }
    private final Code code;
    public LayoutException(Code code, String message) { super(message); this.code = code; }
    public LayoutException(Code code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public Code code() { return code; }
}
