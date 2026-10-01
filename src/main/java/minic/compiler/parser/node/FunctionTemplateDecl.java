package minic.compiler.parser.node;

import minic.SourceRange;
import java.util.List;
import java.util.Objects;

/** A source function template; only selected concrete specializations enter the core. */
public record FunctionTemplateDecl(List<ClassTemplateDecl.Parameter> parameters, Declaration.FunctionDecl function,
                                   SourceRange range) implements Declaration {
    public FunctionTemplateDecl { parameters=List.copyOf(parameters);Objects.requireNonNull(function);Objects.requireNonNull(range); }
}
