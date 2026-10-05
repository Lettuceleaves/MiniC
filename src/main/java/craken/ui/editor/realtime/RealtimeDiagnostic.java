package craken.ui.editor.realtime;

import craken.SourceRange;
import craken.compiler.Diagnostic;

import java.util.Objects;

/** Frozen correction diagnostic: code, severity, reason, repair advice and source range. */
public record RealtimeDiagnostic(
        String code,
        Diagnostic.Severity severity,
        String message,
        String solution,
        SourceRange range
) {
    public RealtimeDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(range, "range");
    }

    public static RealtimeDiagnostic from(Diagnostic diagnostic) {
        return new RealtimeDiagnostic(diagnostic.code(), diagnostic.severity(), diagnostic.message(),
                diagnostic.solution(), diagnostic.range());
    }
}
