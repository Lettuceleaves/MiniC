package minic.compiler.parser.node;

import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 声明节点及其全部具体类型。
 */
public interface Declaration extends AstNode {

    record Program(
            List<StructDecl> structs,
            List<EnumDecl> enums,
            List<FunctionDecl> functions,
            SourceRange range
    ) implements Declaration {
        public Program {
            Objects.requireNonNull(structs, "structs");
            Objects.requireNonNull(enums, "enums");
            Objects.requireNonNull(functions, "functions");
            Objects.requireNonNull(range, "range");
            structs = List.copyOf(structs);
            enums = List.copyOf(enums);
            functions = List.copyOf(functions);
        }

        public Program(List<StructDecl> structs, List<FunctionDecl> functions, SourceRange range) {
            this(structs, List.of(), functions, range);
        }
    }

    record EnumDecl(String name, List<Enumerator> enumerators, SourceRange range) implements Declaration {
        public EnumDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(enumerators, "enumerators");
            Objects.requireNonNull(range, "range");
            enumerators = List.copyOf(enumerators);
        }
    }

    record Enumerator(String name, long value, SourceRange range) implements Declaration {
        public Enumerator {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(range, "range");
        }
    }

    record FunctionDecl(
            String name,
            MiniType returnType,
            List<Parameter> parameters,
            boolean variadic,
            BlockStmt body,
            boolean external,
            SourceRange range
    ) implements Declaration {
        public FunctionDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(returnType, "returnType");
            Objects.requireNonNull(parameters, "parameters");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            parameters = List.copyOf(parameters);
        }

        public Optional<BlockStmt> bodyOptional() {
            return Optional.ofNullable(body);
        }

        public boolean hasBody() {
            return body != null;
        }
    }

    record Parameter(String name, MiniType type, SourceRange range) implements Declaration {
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }

    record StructDecl(String name, List<StructField> fields, boolean definition, boolean union, SourceRange range) implements Declaration {
        public StructDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(fields, "fields");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            fields = List.copyOf(fields);
        }

        public StructDecl(String name, List<StructField> fields, SourceRange range) {
            this(name, fields, true, false, range);
        }

        public StructDecl(String name, List<StructField> fields, boolean definition, SourceRange range) {
            this(name, fields, definition, false, range);
        }
    }

    record StructField(String name, MiniType type, SourceRange range) implements Declaration {
        public StructField {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }
}
