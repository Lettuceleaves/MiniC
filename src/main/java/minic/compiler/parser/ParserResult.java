package minic.compiler.parser;

import minic.compiler.parser.node.Declaration.Program;
import minic.diagnostics.Diagnostic;

import java.util.List;
import java.util.Objects;

/** 一次语法分析的最终结果。 */
public record ParserResult(Program program, List<Diagnostic> diagnostics) {
    public ParserResult {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }
}
