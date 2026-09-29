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
            List<TypedefDecl> typedefs,
            List<GlobalVarDecl> globals,
            List<FunctionDecl> functions,
            SourceRange range
    ) implements Declaration {
        public Program {
            Objects.requireNonNull(structs, "structs");
            Objects.requireNonNull(enums, "enums");
            Objects.requireNonNull(typedefs, "typedefs");
            Objects.requireNonNull(globals, "globals");
            Objects.requireNonNull(functions, "functions");
            Objects.requireNonNull(range, "range");
            structs = List.copyOf(structs);
            enums = List.copyOf(enums);
            typedefs = List.copyOf(typedefs);
            globals = List.copyOf(globals);
            functions = List.copyOf(functions);
        }

        public Program(List<StructDecl> structs, List<FunctionDecl> functions, SourceRange range) {
            this(structs, List.of(), List.of(), List.of(), functions, range);
        }

        public Program(List<StructDecl> structs, List<EnumDecl> enums, List<FunctionDecl> functions, SourceRange range) {
            this(structs, enums, List.of(), List.of(), functions, range);
        }
    }

    /** 文件作用域对象声明；initializer 为 null 时按 C 规则零初始化。 */
    record GlobalVarDecl(
            String name,
            MiniType type,
            minic.compiler.parser.node.Expression initializer,
            boolean external,
            List<AlignmentSpec> alignmentSpecs,
            SourceRange range
    ) implements Declaration {
        public GlobalVarDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(alignmentSpecs, "alignmentSpecs");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            alignmentSpecs = List.copyOf(alignmentSpecs);
        }

        public Optional<minic.compiler.parser.node.Expression> initializerOptional() {
            return Optional.ofNullable(initializer);
        }
    }

    record TypedefDecl(String name, MiniType type, SourceRange range) implements Declaration {
        public TypedefDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(range, "range");
        }
    }

    /** One _Alignas/alignas requirement, expressed either as a constant or as a type. */
    record AlignmentSpec(Integer constant, MiniType type, SourceRange range) implements Declaration {
        public AlignmentSpec {
            Objects.requireNonNull(range, "range");
            if ((constant == null) == (type == null)) {
                throw new IllegalArgumentException("alignment spec must contain exactly one operand");
            }
        }

        public static AlignmentSpec constant(int value, SourceRange range) {
            return new AlignmentSpec(value, null, range);
        }

        public static AlignmentSpec type(MiniType type, SourceRange range) {
            return new AlignmentSpec(null, Objects.requireNonNull(type, "type"), range);
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
            boolean noReturn,
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

        public FunctionDecl(
                String name,
                MiniType returnType,
                List<Parameter> parameters,
                boolean variadic,
                BlockStmt body,
                boolean external,
                SourceRange range
        ) {
            this(name, returnType, parameters, variadic, body, external, false, range);
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

    record StructField(
            String name,
            MiniType type,
            boolean anonymous,
            List<AlignmentSpec> alignmentSpecs,
            SourceRange range
    ) implements Declaration {
        public StructField {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(alignmentSpecs, "alignmentSpecs");
            Objects.requireNonNull(range, "range");
            if (name.isBlank() && !anonymous) {
                throw new IllegalArgumentException("name must not be blank");
            }
            alignmentSpecs = List.copyOf(alignmentSpecs);
        }

        public StructField(String name, MiniType type, SourceRange range) {
            this(name, type, false, List.of(), range);
        }

        public StructField(String name, MiniType type, List<AlignmentSpec> alignmentSpecs, SourceRange range) {
            this(name, type, false, alignmentSpecs, range);
        }
    }
}
