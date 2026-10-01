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
            String entryFunction,
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
            if (Objects.requireNonNull(entryFunction, "entryFunction").isBlank()) throw new IllegalArgumentException("entryFunction is blank");
            structs = List.copyOf(structs);
            enums = List.copyOf(enums);
            typedefs = List.copyOf(typedefs);
            globals = List.copyOf(globals);
            functions = List.copyOf(functions);
            declarations = List.copyOf(declarations);
        }

        public Program(List<StructDecl> structs, List<EnumDecl> enums, List<TypedefDecl> typedefs,
                       List<GlobalVarDecl> globals, List<FunctionDecl> functions,
                       List<Declaration> declarations, LanguageMode languageMode, SourceRange range) {
            this(structs, enums, typedefs, globals, functions, declarations, languageMode, "main", range);
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

    /** Namespace-scope static is linkage metadata, distinct from block static storage duration. */
    record InternalLinkageDecl(Declaration declaration, SourceRange range) implements Declaration {
        public InternalLinkageDecl {
            Objects.requireNonNull(declaration, "declaration");
            Objects.requireNonNull(range, "range");
            if (!(declaration instanceof GlobalVarDecl) && !(declaration instanceof FunctionDecl))
                throw new IllegalArgumentException("static linkage requires a variable or function");
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
            CppInitializer cppInitializer,
            SourceRange range
    ) implements Declaration {
        public GlobalVarDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(alignmentSpecs, "alignmentSpecs");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            alignmentSpecs = List.copyOf(alignmentSpecs);
            if (cppInitializer != null && !cppInitializer.isCompatibilityProjection(initializer)) {
                throw new IllegalArgumentException("C++ initialization operands must match the compatibility projection");
            }
        }

        public GlobalVarDecl(String name, MiniType type, Expression initializer, boolean external,
                             List<AlignmentSpec> alignmentSpecs, SourceRange range) {
            this(name, type, initializer, external, alignmentSpecs, null, range);
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

    enum DefinitionKind { ORDINARY, DEFAULTED, DELETED }

    record FunctionDecl(
            String name,
            MiniType returnType,
            List<Parameter> parameters,
            boolean variadic,
            BlockStmt body,
            boolean external,
            boolean noReturn,
            SourceRange range,
            OperatorName operatorName,
            ConversionName conversionName,
            DefinitionKind definitionKind,
            MiniType.ExceptionSpecification exceptionSpecification
    ) implements Declaration {
        public FunctionDecl(String name,MiniType returnType,List<Parameter> parameters,boolean variadic,BlockStmt body,
                            boolean external,boolean noReturn,SourceRange range,OperatorName operatorName,ConversionName conversionName,DefinitionKind definitionKind) {
            this(name,returnType,parameters,variadic,body,external,noReturn,range,operatorName,conversionName,definitionKind,MiniType.ExceptionSpecification.UNSPECIFIED);
        }
        public FunctionDecl withExceptionSpecification(MiniType.ExceptionSpecification specification) {
            return new FunctionDecl(name,returnType,parameters,variadic,body,external,noReturn,range,operatorName,conversionName,definitionKind,specification);
        }
        public FunctionDecl(String name,MiniType returnType,List<Parameter> parameters,boolean variadic,BlockStmt body,
                            boolean external,boolean noReturn,SourceRange range,OperatorName operatorName,ConversionName conversionName) {
            this(name,returnType,parameters,variadic,body,external,noReturn,range,operatorName,conversionName,DefinitionKind.ORDINARY);
        }
        public FunctionDecl withDefinitionKind(DefinitionKind kind) {
            return new FunctionDecl(name,returnType,parameters,variadic,body,external,noReturn,range,operatorName,conversionName,kind,exceptionSpecification);
        }
        public boolean hasDefinition(){return body!=null||definitionKind!=DefinitionKind.ORDINARY;}
        public FunctionDecl {
            Objects.requireNonNull(definitionKind,"definitionKind");
            Objects.requireNonNull(exceptionSpecification,"exceptionSpecification");
            if(body!=null&&definitionKind!=DefinitionKind.ORDINARY)throw new IllegalArgumentException("Special definition cannot have a function body");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(returnType, "returnType");
            Objects.requireNonNull(parameters, "parameters");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            parameters = List.copyOf(parameters);
            if (operatorName != null && !name.equals(operatorName.spelling())) {
                throw new IllegalArgumentException("Operator metadata must match the function name");
            }
            if (conversionName != null && (operatorName != null || !name.equals(conversionName.spelling())
                    || !returnType.equals(conversionName.targetType()))) {
                throw new IllegalArgumentException("Conversion metadata must match its name and target type");
            }
        }

        public FunctionDecl(String name, MiniType returnType, List<Parameter> parameters, boolean variadic,
                            BlockStmt body, boolean external, boolean noReturn, SourceRange range, OperatorName operatorName) {
            this(name, returnType, parameters, variadic, body, external, noReturn, range, operatorName, null);
        }

        public FunctionDecl(String name, MiniType returnType, List<Parameter> parameters, boolean variadic,
                            BlockStmt body, boolean external, boolean noReturn, SourceRange range) {
            this(name, returnType, parameters, variadic, body, external, noReturn, range, null);
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

    record Parameter(String name, MiniType type, Expression defaultValue, SourceRange range) implements Declaration {
        public Parameter(String name,MiniType type,SourceRange range){this(name,type,null,range);}
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }

    /** A qualified member definition retains its source owner until semantic binding. */
    record OutOfLineMethodDecl(QualifiedName qualifiedName, FunctionDecl method,
                               boolean constQualified, SourceRange nameRange) implements Declaration {
        public OutOfLineMethodDecl {
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(nameRange, "nameRange");
            if (qualifiedName.segments().size() < 2
                    || !qualifiedName.segments().getLast().equals(method.name())) {
                throw new IllegalArgumentException("qualified member definition requires its owner and simple method name");
            }
        }
        @Override public SourceRange range() { return method.range(); }
    }

    enum RecordKey { STRUCT, CLASS }

    enum Access { PUBLIC, PROTECTED, PRIVATE }

    /** Source-only C++ record information; member order determines effective access later. */
    record CppBase(MiniType type, Access access, boolean virtualBase, SourceRange range) implements AstNode {}

    record CppRecordInfo(RecordKey key, List<CppMember> members, List<CppBase> bases, SourceRange keyRange) {
        public CppRecordInfo(RecordKey key,List<CppMember> members,SourceRange keyRange){this(key,members,List.of(),keyRange);}
        public CppRecordInfo {
            Objects.requireNonNull(key, "key");
            members = List.copyOf(members);
            bases = List.copyOf(bases);
            Objects.requireNonNull(keyRange, "keyRange");
        }
    }

    sealed interface CppMember extends AstNode permits FieldMember, StaticFieldMember, MethodMember, ConstructorMember, DestructorMember, AccessLabel, MemberTypedef, TemplateMethodMember, TemplateConstructorMember {}

    record TemplateMethodMember(List<ClassTemplateDecl.Parameter> parameters,MethodMember method) implements CppMember {
        public TemplateMethodMember {parameters=List.copyOf(parameters);Objects.requireNonNull(method);}
        @Override public SourceRange range(){return method.range();}
    }
    record TemplateConstructorMember(List<ClassTemplateDecl.Parameter> parameters,ConstructorMember constructor) implements CppMember {
        public TemplateConstructorMember {parameters=List.copyOf(parameters);Objects.requireNonNull(constructor);}
        @Override public SourceRange range(){return constructor.range();}
    }

    record MemberTypedef(TypedefDecl declaration) implements CppMember {
        public MemberTypedef { Objects.requireNonNull(declaration); }
        @Override public SourceRange range(){return declaration.range();}
    }

    /** Transparent source wrapper: layout and member views retain the same field node. */
    record FieldMember(StructField field, CppInitializer defaultInitializer) implements CppMember {
        public FieldMember(StructField field) { this(field, null); }
        public FieldMember { Objects.requireNonNull(field, "field"); }
        @Override public SourceRange range() { return field.range(); }
    }

    /** A static data member belongs to class lookup but has no instance layout slot. */
    record StaticFieldMember(GlobalVarDecl declaration) implements CppMember {
        public StaticFieldMember { Objects.requireNonNull(declaration, "declaration"); }
        @Override public SourceRange range() { return declaration.range(); }
    }

    record OutOfLineStaticFieldDecl(QualifiedName qualifiedName, GlobalVarDecl declaration,
                                    SourceRange nameRange) implements Declaration {
        public OutOfLineStaticFieldDecl {
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(declaration, "declaration");
            Objects.requireNonNull(nameRange, "nameRange");
            if (qualifiedName.segments().size() < 2 || !qualifiedName.segments().getLast().equals(declaration.name()))
                throw new IllegalArgumentException("static data definition requires its class owner");
        }
        @Override public SourceRange range() { return declaration.range(); }
    }

    /** Constructor spelling and signature are source syntax, not an ordinary named method. */
    record ConstructorMember(String name, List<Parameter> parameters, boolean variadic,
                             List<MemberInitializer> initializers, BlockStmt body,
                             SourceRange nameRange, SourceRange range, boolean explicitSpecifier,DefinitionKind definitionKind,
                             MiniType.ExceptionSpecification exceptionSpecification) implements CppMember {
        public ConstructorMember(String name,List<Parameter> parameters,boolean variadic,List<MemberInitializer> initializers,
                                 BlockStmt body,SourceRange nameRange,SourceRange range,boolean explicitSpecifier,DefinitionKind definitionKind) {
            this(name,parameters,variadic,initializers,body,nameRange,range,explicitSpecifier,definitionKind,MiniType.ExceptionSpecification.UNSPECIFIED);
        }
        public ConstructorMember withExceptionSpecification(MiniType.ExceptionSpecification specification) {
            return new ConstructorMember(name,parameters,variadic,initializers,body,nameRange,range,explicitSpecifier,definitionKind,specification);
        }
        public ConstructorMember(String name,List<Parameter> parameters,boolean variadic,List<MemberInitializer> initializers,
                                 BlockStmt body,SourceRange nameRange,SourceRange range,boolean explicitSpecifier){
            this(name,parameters,variadic,initializers,body,nameRange,range,explicitSpecifier,DefinitionKind.ORDINARY);
        }
        public boolean hasDefinition(){return body!=null||definitionKind!=DefinitionKind.ORDINARY;}
        public ConstructorMember(String name, List<Parameter> parameters, boolean variadic,
                                 List<MemberInitializer> initializers, BlockStmt body,
                                 SourceRange nameRange, SourceRange range) {
            this(name, parameters, variadic, initializers, body, nameRange, range, false);
        }
        public ConstructorMember {
            Objects.requireNonNull(definitionKind,"definitionKind");
            Objects.requireNonNull(exceptionSpecification,"exceptionSpecification");
            if(body!=null&&definitionKind!=DefinitionKind.ORDINARY)throw new IllegalArgumentException("Special definition cannot have a body");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(nameRange, "nameRange");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("Constructor spelling must not be blank");
            parameters = List.copyOf(parameters);
            initializers = List.copyOf(initializers);
        }
    }

    /** A destructor has neither a return type nor parameters; nameRange includes the '~'. */
    record DestructorMember(String name, BlockStmt body, SourceRange nameRange, SourceRange range,DefinitionKind definitionKind,
                            MiniType.ExceptionSpecification exceptionSpecification) implements CppMember {
        public DestructorMember(String name,BlockStmt body,SourceRange nameRange,SourceRange range,DefinitionKind definitionKind) {
            this(name,body,nameRange,range,definitionKind,MiniType.ExceptionSpecification.UNSPECIFIED);
        }
        public DestructorMember withExceptionSpecification(MiniType.ExceptionSpecification specification) {
            return new DestructorMember(name,body,nameRange,range,definitionKind,specification);
        }
        public DestructorMember(String name,BlockStmt body,SourceRange nameRange,SourceRange range){this(name,body,nameRange,range,DefinitionKind.ORDINARY);}
        public boolean hasDefinition(){return body!=null||definitionKind!=DefinitionKind.ORDINARY;}
        public DestructorMember {
            Objects.requireNonNull(definitionKind,"definitionKind");
            Objects.requireNonNull(exceptionSpecification,"exceptionSpecification");
            if(body!=null&&definitionKind!=DefinitionKind.ORDINARY)throw new IllegalArgumentException("Special definition cannot have a body");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(nameRange, "nameRange");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("Destructor spelling must not be blank");
        }
    }

    /** The final qualified segment retains '~T', rather than masquerading as an ordinary method. */
    record OutOfLineDestructorDecl(QualifiedName qualifiedName, DestructorMember destructor,
                                   SourceRange nameRange) implements Declaration {
        public OutOfLineDestructorDecl {
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(destructor, "destructor");
            Objects.requireNonNull(nameRange, "nameRange");
            if (qualifiedName.segments().size() < 2
                    || !qualifiedName.segments().getLast().equals("~" + destructor.name())) {
                throw new IllegalArgumentException("Qualified destructor requires its owner and matching destructor spelling");
            }
        }
        @Override public SourceRange range() { return destructor.range(); }
    }

    /** Written order is retained separately from the declaration order used for execution. */
    record MemberInitializer(QualifiedName target, CppInitializer initializer, SourceRange range) implements AstNode {
        public MemberInitializer {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(range, "range");
        }
    }

    record OutOfLineConstructorDecl(QualifiedName qualifiedName, ConstructorMember constructor,
                                    SourceRange nameRange) implements Declaration {
        public OutOfLineConstructorDecl {
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(constructor, "constructor");
            Objects.requireNonNull(nameRange, "nameRange");
            if (qualifiedName.segments().size() < 2
                    || !qualifiedName.segments().getLast().equals(constructor.name())) {
                throw new IllegalArgumentException("Qualified constructor requires its owner and matching constructor spelling");
            }
        }
        @Override public SourceRange range() { return constructor.range(); }
    }

    record MethodMember(FunctionDecl method, boolean constQualified, boolean staticMember, SourceRange nameRange) implements CppMember {
        public MethodMember(FunctionDecl method, boolean constQualified, SourceRange nameRange) {
            this(method, constQualified, false, nameRange);
        }
        public MethodMember(FunctionDecl method, SourceRange nameRange) {
            this(method, false, nameRange);
        }
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
