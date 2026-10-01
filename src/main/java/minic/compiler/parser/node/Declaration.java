package minic.compiler.parser.node;

import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.type.MiniType;
import minic.compiler.LanguageMode;
import minic.SourceRange;

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
            List<Declaration> declarations,
            LanguageMode languageMode,
            SourceRange range
    ) implements Declaration {
        public Program {
            Objects.requireNonNull(structs, "structs");
            Objects.requireNonNull(enums, "enums");
            Objects.requireNonNull(typedefs, "typedefs");
            Objects.requireNonNull(globals, "globals");
            Objects.requireNonNull(functions, "functions");
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(languageMode, "languageMode");
            structs = List.copyOf(structs);
            enums = List.copyOf(enums);
            typedefs = List.copyOf(typedefs);
            globals = List.copyOf(globals);
            functions = List.copyOf(functions);
            declarations = List.copyOf(declarations);
        }

        /** Compatibility for existing C AST producers; the parser supplies exact source order. */
        public Program(List<StructDecl> structs, List<EnumDecl> enums, List<TypedefDecl> typedefs,
                       List<GlobalVarDecl> globals, List<FunctionDecl> functions,
                       List<Declaration> declarations, SourceRange range) {
            this(structs, enums, typedefs, globals, functions, declarations, LanguageMode.C, range);
        }

        public Program(List<StructDecl> structs, List<EnumDecl> enums, List<TypedefDecl> typedefs,
                       List<GlobalVarDecl> globals, List<FunctionDecl> functions, SourceRange range) {
            this(structs, enums, typedefs, globals, functions,
                    sourceOrder(structs, enums, typedefs, globals, functions), range);
        }

        @SafeVarargs private static List<Declaration> sourceOrder(List<? extends Declaration>... groups) {
            var result = new java.util.ArrayList<Declaration>();
            for (var group : groups) result.addAll(group);
            result.sort(java.util.Comparator.comparingInt((Declaration d) -> d.range().startLine())
                    .thenComparingInt(d -> d.range().startByte()));
            return List.copyOf(result);
        }

        public Program(List<StructDecl> structs, List<FunctionDecl> functions, SourceRange range) {
            this(structs, List.of(), List.of(), List.of(), functions, range);
        }

        public Program(List<StructDecl> structs, List<EnumDecl> enums, List<FunctionDecl> functions, SourceRange range) {
            this(structs, enums, List.of(), List.of(), functions, range);
        }
    }

    /** A namespace occurrence is retained separately, including repeated openings of the same name. */
    record NamespaceDecl(QualifiedName name, List<Declaration> declarations, SourceRange range) implements Declaration {
        public NamespaceDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(range, "range");
            declarations = List.copyOf(declarations);
            if (name.global()) throw new IllegalArgumentException("Namespace definitions cannot start with ::");
        }
    }

    /** Both namespace-scope and block-scope using declarations preserve their original position. */
    record UsingDecl(QualifiedName target, boolean namespaceDirective, SourceRange range) implements Declaration, Statement {
        public UsingDecl {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(range, "range");
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

    enum RecordKey { STRUCT, CLASS }

    enum Access { PUBLIC, PROTECTED, PRIVATE }

    /** Source-only C++ record information; member order determines effective access later. */
    record CppRecordInfo(RecordKey key, List<CppMember> members, SourceRange keyRange) {
        public CppRecordInfo {
            Objects.requireNonNull(key, "key");
            members = List.copyOf(members);
            Objects.requireNonNull(keyRange, "keyRange");
        }
    }

    sealed interface CppMember extends AstNode permits FieldMember, MethodMember, AccessLabel {}

    /** Transparent source wrapper: layout and member views retain the same field node. */
    record FieldMember(StructField field) implements CppMember {
        public FieldMember { Objects.requireNonNull(field, "field"); }
        @Override public SourceRange range() { return field.range(); }
    }

    record MethodMember(FunctionDecl method, SourceRange nameRange) implements CppMember {
        public MethodMember {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(nameRange, "nameRange");
        }
        @Override public SourceRange range() { return method.range(); }
    }

    record AccessLabel(Access access, SourceRange range) implements CppMember {
        public AccessLabel {
            Objects.requireNonNull(access, "access");
            Objects.requireNonNull(range, "range");
        }
    }

    record StructDecl(String name, List<StructField> fields, boolean definition, boolean union,
                      CppRecordInfo cppInfo, SourceRange range) implements Declaration {
        public StructDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(fields, "fields");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            fields = List.copyOf(fields);
            if (cppInfo != null) {
                int index = 0;
                for (CppMember member : cppInfo.members()) {
                    if (!(member instanceof FieldMember field)) continue;
                    if (index >= fields.size() || fields.get(index++) != field.field()) {
                        throw new IllegalArgumentException("C++ field projection must use the same field nodes in member order");
                    }
                }
                if (index != fields.size()) throw new IllegalArgumentException("C++ field projection is missing a field member");
            }
        }

        public StructDecl(String name, List<StructField> fields, boolean definition, boolean union, SourceRange range) {
            this(name, fields, definition, union, null, range);
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
