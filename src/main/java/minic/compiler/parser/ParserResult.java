package minic.compiler.parser;

import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.Stage;

import java.util.Objects;

/** 一次语法分析的最终结果。 */
public record ParserResult(Program program) implements Stage.Context {
    public ParserResult {
        Objects.requireNonNull(program, "program");
    }
}
