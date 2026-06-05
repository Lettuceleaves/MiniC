package minic.web;

/**
 * Immutable configuration for the MiniC web adapter.
 */
public record MiniCWebConfig(String host, int port) {
    public MiniCWebConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
    }

    public static MiniCWebConfig development() {
        return new MiniCWebConfig("127.0.0.1", 8080);
    }

    public static MiniCWebConfig testing() {
        return new MiniCWebConfig("127.0.0.1", 0);
    }
}
