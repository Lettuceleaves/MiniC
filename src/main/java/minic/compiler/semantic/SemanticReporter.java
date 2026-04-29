package minic.compiler.semantic;

import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;

final class SemanticReporter {
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    void report(SourceRange range, String message) {
        diagnostics.add(new Diagnostic("SEM001", DiagnosticSeverity.ERROR, message, range));
    }
}
