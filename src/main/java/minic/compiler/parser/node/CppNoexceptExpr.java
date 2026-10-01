package minic.compiler.parser.node;

import minic.SourceRange;
import java.util.Objects;

/** The operand is checked for validity but never evaluated or odr-used. */
public record CppNoexceptExpr(Expression operand,SourceRange range) implements Expression {
    public CppNoexceptExpr { Objects.requireNonNull(operand); Objects.requireNonNull(range); }
}
