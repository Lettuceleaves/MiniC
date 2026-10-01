package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.MiniType;
import java.util.List;
import java.util.Objects;

/** A generic source record; only concrete specializations may enter the core record indexes. */
public record ClassTemplateDecl(List<Parameter> parameters, Declaration.StructDecl record,
                                SourceRange range) implements Declaration {
    public ClassTemplateDecl(java.util.Collection<? extends Parameter> parameters, Declaration.StructDecl record, SourceRange range) {
        this(List.copyOf(parameters),record,range);
    }
    public ClassTemplateDecl {
        parameters = List.copyOf(parameters);
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(range, "range");
        if (parameters.isEmpty()) throw new IllegalArgumentException("a primary template needs parameters");
        for (int index = 0; index < parameters.size(); index++) {
            MiniType.TemplateParameterType type = parameters.get(index).type();
            if (!type.owner().equals(record.name()) || type.index() != index)
                throw new IllegalArgumentException("template parameter identity does not match its owner");
        }
    }

    public sealed interface Parameter extends AstNode permits TypeParameter, ValueParameter {
        String name();
        MiniType.TemplateParameterType type();
        default MiniType defaultType() { return null; }
    }

    public record ValueParameter(String name, MiniType.TemplateParameterType type, MiniType valueType,
                                 Expression defaultValue, SourceRange range) implements Parameter {
        public ValueParameter {
            Objects.requireNonNull(name); Objects.requireNonNull(type);
            Objects.requireNonNull(valueType); Objects.requireNonNull(range);
        }
    }

    public record TypeParameter(String name, MiniType.TemplateParameterType type, MiniType defaultType, SourceRange range) implements Parameter {
        public TypeParameter(String name, MiniType.TemplateParameterType type, SourceRange range) {
            this(name, type, null, range);
        }
        public TypeParameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("template parameter needs a name");
        }
    }
}
