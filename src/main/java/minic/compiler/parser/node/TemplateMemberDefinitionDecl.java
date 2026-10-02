package minic.compiler.parser.node;

import java.util.List;
import java.util.Objects;
import minic.SourceRange;
import minic.compiler.type.MiniType;

/** A member definition with a structural class-template owner, before instantiation. */
public record TemplateMemberDefinitionDecl(List<ClassTemplateDecl.Parameter> parameters,
        MiniType.TemplateIdType ownerType, Declaration declaration, SourceRange range) implements Declaration {
    public TemplateMemberDefinitionDecl {
        parameters=List.copyOf(parameters);Objects.requireNonNull(ownerType);Objects.requireNonNull(declaration);Objects.requireNonNull(range);
    }
}
