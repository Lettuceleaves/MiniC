package minic.diagnostics;

import minic.source.SourceRange;

import java.util.Objects;

public record Diagnostic(
        String code,
        DiagnosticSeverity severity,
        String message,
        SourceRange range
) {
    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(range, "range");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
