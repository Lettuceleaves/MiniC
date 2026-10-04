package craken.compiler.parser.node;

import craken.compiler.parser.node.Statement.BlockStmt;
import craken.compiler.type.CrakenType;
import craken.SourceRange;
import craken.compiler.type.TemplateArgument;
import craken.compiler.parser.node.Expression.InitializerSyntax;

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
                       List<Declaration> declarations, SourceRange range) {
            this(structs, enums, typedefs, globals, functions, declarations, "main", range);
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

    /** One declaration's ordered declarators; it introduces no scope of its own. */
    record DeclGroupDecl(List<Declaration> declarations,SourceRange range) implements Declaration {
        public DeclGroupDecl {
            declarations=List.copyOf(declarations);
            if(declarations.isEmpty())throw new IllegalArgumentException("Empty declaration group");
            Objects.requireNonNull(range,"range");
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
            CrakenType type,
            craken.compiler.parser.node.Expression initializer,
            boolean external,
            List<AlignmentSpec> alignmentSpecs,
            InitializerSyntax initializerSyntax,
            SourceRange range,boolean constexprSpecifier
    ) implements Declaration {
        public GlobalVarDecl(String name,CrakenType type,Expression initializer,boolean external,List<AlignmentSpec> alignmentSpecs,InitializerSyntax initializerSyntax,SourceRange range){this(name,type,initializer,external,alignmentSpecs,initializerSyntax,range,false);}
        public GlobalVarDecl withConstexprSpecifier(boolean value){return new GlobalVarDecl(name,value?CrakenType.qualified(type,java.util.Set.of(CrakenType.TypeQualifier.CONST)):type,initializer,external,alignmentSpecs,initializerSyntax,range,value);}
        public GlobalVarDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(alignmentSpecs, "alignmentSpecs");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            alignmentSpecs = List.copyOf(alignmentSpecs);
            if (initializerSyntax != null && !initializerSyntax.isCompatibilityProjection(initializer)) {
                throw new IllegalArgumentException("Initializer syntax operands must match the core initializer");
            }
        }

        public GlobalVarDecl(String name, CrakenType type, Expression initializer, boolean external,
                             List<AlignmentSpec> alignmentSpecs, SourceRange range) {
            this(name, type, initializer, external, alignmentSpecs, null, range);
        }

        public Optional<craken.compiler.parser.node.Expression> initializerOptional() {
            return Optional.ofNullable(initializer);
        }
    }

    record TypedefDecl(String name, CrakenType type, SourceRange range) implements Declaration {
        public TypedefDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(range, "range");
        }
    }

    /** One _Alignas/alignas requirement, expressed either as a constant or as a type. */
    record AlignmentSpec(Integer constant, CrakenType type, SourceRange range) implements Declaration {
        public AlignmentSpec {
            Objects.requireNonNull(range, "range");
            if ((constant == null) == (type == null)) {
                throw new IllegalArgumentException("alignment spec must contain exactly one operand");
            }
        }

        public static AlignmentSpec constant(int value, SourceRange range) {
            return new AlignmentSpec(value, null, range);
        }

        public static AlignmentSpec type(CrakenType type, SourceRange range) {
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
            CrakenType returnType,
            List<Parameter> parameters,
            boolean variadic,
            BlockStmt body,
            boolean external,
            boolean noReturn,
            SourceRange range,
            OperatorName operatorName,
            ConversionName conversionName,
            DefinitionKind definitionKind,
            CrakenType.ExceptionSpecification exceptionSpecification,boolean constexprSpecifier
    ) implements Declaration {
        public FunctionDecl(String name,CrakenType returnType,List<Parameter> parameters,boolean variadic,BlockStmt body,boolean external,boolean noReturn,SourceRange range,OperatorName operatorName,ConversionName conversionName,DefinitionKind definitionKind,CrakenType.ExceptionSpecification exceptionSpecification){this(name,returnType,parameters,variadic,body,external,noReturn,range,operatorName,conversionName,definitionKind,exceptionSpecification,false);}
        public FunctionDecl withConstexprSpecifier(boolean value){return new FunctionDecl(name,returnType,parameters,variadic,body,external,noReturn,range,operatorName,conversionName,definitionKind,exceptionSpecification,value);}
        public FunctionDecl(String name,CrakenType returnType,List<Parameter> parameters,boolean variadic,BlockStmt body,
                            boolean external,boolean noReturn,SourceRange range,OperatorName operatorName,ConversionName conversionName,DefinitionKind definitionKind) {
            this(name,returnType,parameters,variadic,body,external,noReturn,range,operatorName,conversionName,definitionKind,CrakenType.ExceptionSpecification.UNSPECIFIED);
        }
        public FunctionDecl withExceptionSpecification(CrakenType.ExceptionSpecification specification) {
            return new FunctionDecl(name,returnType,parameters,variadic,body,external,noReturn,range,operatorName,conversionName,definitionKind,specification,constexprSpecifier);
        }
        public FunctionDecl(String name,CrakenType returnType,List<Parameter> parameters,boolean variadic,BlockStmt body,
                            boolean external,boolean noReturn,SourceRange range,OperatorName operatorName,ConversionName conversionName) {
            this(name,returnType,parameters,variadic,body,external,noReturn,range,operatorName,conversionName,DefinitionKind.ORDINARY);
        }
        public FunctionDecl withDefinitionKind(DefinitionKind kind) {
            return new FunctionDecl(name,returnType,parameters,variadic,body,external,noReturn,range,operatorName,conversionName,kind,exceptionSpecification,constexprSpecifier);
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

        public FunctionDecl(String name, CrakenType returnType, List<Parameter> parameters, boolean variadic,
                            BlockStmt body, boolean external, boolean noReturn, SourceRange range, OperatorName operatorName) {
            this(name, returnType, parameters, variadic, body, external, noReturn, range, operatorName, null);
        }

        public FunctionDecl(String name, CrakenType returnType, List<Parameter> parameters, boolean variadic,
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
                CrakenType returnType,
                List<Parameter> parameters,
                boolean variadic,
                BlockStmt body,
                boolean external,
                SourceRange range
        ) {
            this(name, returnType, parameters, variadic, body, external, false, range);
        }
    }

    record Parameter(String name, CrakenType type, Expression defaultValue, SourceRange range) implements Declaration {
        public Parameter(String name,CrakenType type,SourceRange range){this(name,type,null,range);}
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

    /** Source-only class information; member order determines effective access later. */
    record BaseSpecifier(CrakenType type, Access access, boolean virtualBase, SourceRange range) implements AstNode {}

    record RecordInfo(RecordKey key, List<RecordMember> members, List<BaseSpecifier> bases, SourceRange keyRange) {
        public RecordInfo(RecordKey key,List<RecordMember> members,SourceRange keyRange){this(key,members,List.of(),keyRange);}
        public RecordInfo {
            Objects.requireNonNull(key, "key");
            members = List.copyOf(members);
            bases = List.copyOf(bases);
            Objects.requireNonNull(keyRange, "keyRange");
        }
    }

    sealed interface RecordMember extends AstNode permits FieldMember, StaticFieldMember, MethodMember, ConstructorMember, DestructorMember, AccessLabel, MemberTypedef, TemplateMethodMember, TemplateConstructorMember, StaticAssertDecl {}

    record TemplateMethodMember(List<ClassTemplateDecl.Parameter> parameters,MethodMember method) implements RecordMember {
        public TemplateMethodMember {parameters=List.copyOf(parameters);Objects.requireNonNull(method);}
        @Override public SourceRange range(){return method.range();}
    }
    record TemplateConstructorMember(List<ClassTemplateDecl.Parameter> parameters,ConstructorMember constructor) implements RecordMember {
        public TemplateConstructorMember {parameters=List.copyOf(parameters);Objects.requireNonNull(constructor);}
        @Override public SourceRange range(){return constructor.range();}
    }

    record MemberTypedef(TypedefDecl declaration) implements RecordMember {
        public MemberTypedef { Objects.requireNonNull(declaration); }
        @Override public SourceRange range(){return declaration.range();}
    }

    /** Transparent source wrapper: layout and member views retain the same field node. */
    record FieldMember(StructField field, InitializerSyntax defaultInitializer) implements RecordMember {
        public FieldMember(StructField field) { this(field, null); }
        public FieldMember { Objects.requireNonNull(field, "field"); }
        @Override public SourceRange range() { return field.range(); }
    }

    /** A static data member belongs to class lookup but has no instance layout slot. */
    record StaticFieldMember(GlobalVarDecl declaration) implements RecordMember {
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
                             CrakenType.ExceptionSpecification exceptionSpecification,boolean constexprSpecifier) implements RecordMember {
        public ConstructorMember(String name,List<Parameter> parameters,boolean variadic,List<MemberInitializer> initializers,BlockStmt body,SourceRange nameRange,SourceRange range,boolean explicitSpecifier,DefinitionKind definitionKind,CrakenType.ExceptionSpecification exceptionSpecification){this(name,parameters,variadic,initializers,body,nameRange,range,explicitSpecifier,definitionKind,exceptionSpecification,false);}
        public ConstructorMember withConstexprSpecifier(boolean value){return new ConstructorMember(name,parameters,variadic,initializers,body,nameRange,range,explicitSpecifier,definitionKind,exceptionSpecification,value);}
        public ConstructorMember(String name,List<Parameter> parameters,boolean variadic,List<MemberInitializer> initializers,
                                 BlockStmt body,SourceRange nameRange,SourceRange range,boolean explicitSpecifier,DefinitionKind definitionKind) {
            this(name,parameters,variadic,initializers,body,nameRange,range,explicitSpecifier,definitionKind,CrakenType.ExceptionSpecification.UNSPECIFIED);
        }
        public ConstructorMember withExceptionSpecification(CrakenType.ExceptionSpecification specification) {
            return new ConstructorMember(name,parameters,variadic,initializers,body,nameRange,range,explicitSpecifier,definitionKind,specification,constexprSpecifier);
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
                            CrakenType.ExceptionSpecification exceptionSpecification) implements RecordMember {
        public DestructorMember(String name,BlockStmt body,SourceRange nameRange,SourceRange range,DefinitionKind definitionKind) {
            this(name,body,nameRange,range,definitionKind,CrakenType.ExceptionSpecification.UNSPECIFIED);
        }
        public DestructorMember withExceptionSpecification(CrakenType.ExceptionSpecification specification) {
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
    record MemberInitializer(QualifiedName target, InitializerSyntax initializer, SourceRange range) implements AstNode {
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

    record MethodMember(FunctionDecl method, boolean constQualified, boolean staticMember, SourceRange nameRange) implements RecordMember {
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

    record AccessLabel(Access access, SourceRange range) implements RecordMember {
        public AccessLabel {
            Objects.requireNonNull(access, "access");
            Objects.requireNonNull(range, "range");
        }
    }

    record StructDecl(String name, List<StructField> fields, boolean definition, boolean union,
                      RecordInfo recordInfo, SourceRange range) implements Declaration {
        public StructDecl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(fields, "fields");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            fields = List.copyOf(fields);
            if (recordInfo != null) {
                int index = 0;
                for (RecordMember member : recordInfo.members()) {
                    if (!(member instanceof FieldMember field)) continue;
                    if (index >= fields.size() || fields.get(index++) != field.field()) {
                        throw new IllegalArgumentException("Record fields must be the field members in member order");
                    }
                }
                if (index != fields.size()) throw new IllegalArgumentException("Record fields are missing a field member");
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
            CrakenType type,
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

        public StructField(String name, CrakenType type, SourceRange range) {
            this(name, type, false, List.of(), range);
        }

        public StructField(String name, CrakenType type, List<AlignmentSpec> alignmentSpecs, SourceRange range) {
            this(name, type, false, alignmentSpecs, range);
        }
    }

    /** A generic source record; only concrete specializations may enter the core record indexes. */
    record ClassTemplateDecl(List<Parameter> parameters, Declaration.StructDecl record,
                             List<craken.compiler.type.TemplateArgument> specializationArguments, boolean specialization, SourceRange range) implements Declaration {
        public ClassTemplateDecl(List<Parameter> parameters, Declaration.StructDecl record, List<craken.compiler.type.TemplateArgument> arguments, SourceRange range) { this(parameters,record,arguments,!arguments.isEmpty(),range); }
        public ClassTemplateDecl(java.util.Collection<? extends Parameter> parameters, Declaration.StructDecl record, SourceRange range) {
            this(List.copyOf(parameters),record,List.of(),range);
        }
        public ClassTemplateDecl {
            parameters = List.copyOf(parameters);
            specializationArguments = List.copyOf(specializationArguments);
            Objects.requireNonNull(record, "record");
            Objects.requireNonNull(range, "range");
            if (parameters.isEmpty() && !specialization) throw new IllegalArgumentException("a primary template needs parameters");
            for (int index = 0; index < parameters.size(); index++) {
                CrakenType.TemplateParameterType type = parameters.get(index).type();
                if (!type.owner().equals(record.name()) || type.index() != index)
                    throw new IllegalArgumentException("template parameter identity does not match its owner");
            }
        }

        public sealed interface Parameter extends AstNode permits TypeParameter, ValueParameter {
            String name();
            CrakenType.TemplateParameterType type();
            boolean pack();
            default CrakenType defaultType() { return null; }
        }

        public record ValueParameter(String name, CrakenType.TemplateParameterType type, CrakenType valueType,
                                     Expression defaultValue, boolean pack, SourceRange range) implements Parameter {
            public ValueParameter(String name, CrakenType.TemplateParameterType type, CrakenType valueType, Expression defaultValue, SourceRange range) { this(name,type,valueType,defaultValue,false,range); }
            public ValueParameter {
                Objects.requireNonNull(name); Objects.requireNonNull(type);
                Objects.requireNonNull(valueType); Objects.requireNonNull(range);
            }
        }

        public record TypeParameter(String name, CrakenType.TemplateParameterType type, CrakenType defaultType, boolean pack, SourceRange range) implements Parameter {
            public TypeParameter(String name, CrakenType.TemplateParameterType type, CrakenType defaultType, SourceRange range) { this(name,type,defaultType,false,range); }
            public TypeParameter(String name, CrakenType.TemplateParameterType type, SourceRange range) {
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

    /** A primary function template or an explicit specialization, before core binding. */
    record FunctionTemplateDecl(List<ClassTemplateDecl.Parameter> parameters, Declaration.FunctionDecl function,
                                List<TemplateArgument> specializationArguments, QualifiedName qualifiedName, SourceRange range) implements Declaration {
        public FunctionTemplateDecl(List<ClassTemplateDecl.Parameter> parameters,Declaration.FunctionDecl function,SourceRange range) {
            this(parameters,function,null,null,range);
        }
        public FunctionTemplateDecl {
            parameters=List.copyOf(parameters);Objects.requireNonNull(function);Objects.requireNonNull(range);
            specializationArguments=specializationArguments==null?null:List.copyOf(specializationArguments);
        }
        public boolean specialization(){return parameters.isEmpty();}
    }

    /** Source assertion, discarded only after constant evaluation. */
    record StaticAssertDecl(Expression condition,Expression.StringLiteralExpr message,SourceRange range)
                            implements Declaration,Statement,Declaration.RecordMember {
        public StaticAssertDecl{Objects.requireNonNull(condition);Objects.requireNonNull(range);}
    }

    /** Source declaration; the backing object and binding references are produced by name binding. */
    record StructuredBindingDecl(CrakenType type, List<BindingName> names,
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

    /** A member definition with a structural class-template owner, before instantiation. */
    record TemplateMemberDefinitionDecl(List<ClassTemplateDecl.Parameter> parameters,
                                        CrakenType.TemplateIdType ownerType, Declaration declaration, SourceRange range) implements Declaration {
        public TemplateMemberDefinitionDecl {
            parameters=List.copyOf(parameters);Objects.requireNonNull(ownerType);Objects.requireNonNull(declaration);Objects.requireNonNull(range);
        }
    }
}
