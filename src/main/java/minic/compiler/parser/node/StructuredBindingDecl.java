package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.MiniType;
import java.util.List;
import java.util.Objects;

/** Source declaration; the backing object and binding references are produced by C++ binding. */
public record StructuredBindingDecl(MiniType type, List<BindingName> names,
                                       InitializerSyntax initializer, SourceRange range)
        implements Declaration, Statement {
    public StructuredBindingDecl {
        Objects.requireNonNull(type); names=List.copyOf(names); Objects.requireNonNull(range);
        if(names.isEmpty())throw new IllegalArgumentException("A structured binding needs names");
    }
    public record BindingName(String name,SourceRange range) implements AstNode {
        public BindingName {Objects.requireNonNull(name);Objects.requireNonNull(range);}
    }
}
