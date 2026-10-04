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

    static final class Program extends AbstractAstNode implements Declaration {
        private final List<StructDecl> structs;
        private final List<EnumDecl> enums;
        private final List<TypedefDecl> typedefs;
        private final List<GlobalVarDecl> globals;
        private final List<FunctionDecl> functions;
        private final List<Declaration> declarations;
        private final String entryFunction;
        private final SourceRange range;

        @AstNodeConstructor({"structs", "enums", "typedefs", "globals", "functions", "declarations", "entryFunction", "range"})
        public Program(List<StructDecl> structs, List<EnumDecl> enums, List<TypedefDecl> typedefs, List<GlobalVarDecl> globals, List<FunctionDecl> functions, List<Declaration> declarations, String entryFunction, SourceRange range) {
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

            this.structs = structs;
            this.enums = enums;
            this.typedefs = typedefs;
            this.globals = globals;
            this.functions = functions;
            this.declarations = declarations;
            this.entryFunction = entryFunction;
            this.range = range;
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

        public List<StructDecl> structs() { return structs; }
        public List<EnumDecl> enums() { return enums; }
        public List<TypedefDecl> typedefs() { return typedefs; }
        public List<GlobalVarDecl> globals() { return globals; }
        public List<FunctionDecl> functions() { return functions; }
        public List<Declaration> declarations() { return declarations; }
        public String entryFunction() { return entryFunction; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Program that)) return false;
            return Objects.equals(structs, that.structs)
                    && Objects.equals(enums, that.enums)
                    && Objects.equals(typedefs, that.typedefs)
                    && Objects.equals(globals, that.globals)
                    && Objects.equals(functions, that.functions)
                    && Objects.equals(declarations, that.declarations)
                    && Objects.equals(entryFunction, that.entryFunction)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(structs);
            result = 31 * result + Objects.hashCode(enums);
            result = 31 * result + Objects.hashCode(typedefs);
            result = 31 * result + Objects.hashCode(globals);
            result = 31 * result + Objects.hashCode(functions);
            result = 31 * result + Objects.hashCode(declarations);
            result = 31 * result + Objects.hashCode(entryFunction);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "Program[structs=" + structs + ", enums=" + enums + ", typedefs=" + typedefs + ", globals=" + globals + ", functions=" + functions + ", declarations=" + declarations + ", entryFunction=" + entryFunction + ", range=" + range + "]";
        }
    }

    /** One declaration's ordered declarators; it introduces no scope of its own. */
    static final class DeclGroupDecl extends AbstractAstNode implements Declaration {
        private final List<Declaration> declarations;
        private final SourceRange range;

        @AstNodeConstructor({"declarations", "range"})
        public DeclGroupDecl(List<Declaration> declarations, SourceRange range) {
            declarations=List.copyOf(declarations);
            if(declarations.isEmpty())throw new IllegalArgumentException("Empty declaration group");
            Objects.requireNonNull(range,"range");

            this.declarations = declarations;
            this.range = range;
        }

        public List<Declaration> declarations() { return declarations; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DeclGroupDecl that)) return false;
            return Objects.equals(declarations, that.declarations)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(declarations);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "DeclGroupDecl[declarations=" + declarations + ", range=" + range + "]";
        }
    }

    /** Namespace-scope static is linkage metadata, distinct from block static storage duration. */
    static final class InternalLinkageDecl extends AbstractAstNode implements Declaration {
        private final Declaration declaration;
        private final SourceRange range;

        @AstNodeConstructor({"declaration", "range"})
        public InternalLinkageDecl(Declaration declaration, SourceRange range) {
            Objects.requireNonNull(declaration, "declaration");
            Objects.requireNonNull(range, "range");
            if (!(declaration instanceof GlobalVarDecl) && !(declaration instanceof FunctionDecl))
                throw new IllegalArgumentException("static linkage requires a variable or function");

            this.declaration = declaration;
            this.range = range;
        }

        public Declaration declaration() { return declaration; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InternalLinkageDecl that)) return false;
            return Objects.equals(declaration, that.declaration)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(declaration);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "InternalLinkageDecl[declaration=" + declaration + ", range=" + range + "]";
        }
    }

    /** A namespace occurrence is retained separately, including repeated openings of the same name. */
    static final class NamespaceDecl extends AbstractAstNode implements Declaration {
        private final QualifiedName name;
        private final List<Declaration> declarations;
        private final SourceRange range;

        @AstNodeConstructor({"name", "declarations", "range"})
        public NamespaceDecl(QualifiedName name, List<Declaration> declarations, SourceRange range) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(range, "range");
            declarations = List.copyOf(declarations);
            if (name.global()) throw new IllegalArgumentException("Namespace definitions cannot start with ::");

            this.name = name;
            this.declarations = declarations;
            this.range = range;
        }

        public QualifiedName name() { return name; }
        public List<Declaration> declarations() { return declarations; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NamespaceDecl that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(declarations, that.declarations)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(declarations);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "NamespaceDecl[name=" + name + ", declarations=" + declarations + ", range=" + range + "]";
        }
    }

    /** Both namespace-scope and block-scope using declarations preserve their original position. */
    static final class UsingDecl extends AbstractAstNode implements Declaration, Statement {
        private final QualifiedName target;
        private final boolean namespaceDirective;
        private final SourceRange range;

        @AstNodeConstructor({"target", "namespaceDirective", "range"})
        public UsingDecl(QualifiedName target, boolean namespaceDirective, SourceRange range) {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(range, "range");

            this.target = target;
            this.namespaceDirective = namespaceDirective;
            this.range = range;
        }

        public QualifiedName target() { return target; }
        public boolean namespaceDirective() { return namespaceDirective; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof UsingDecl that)) return false;
            return Objects.equals(target, that.target)
                    && namespaceDirective == that.namespaceDirective
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(target);
            result = 31 * result + Boolean.hashCode(namespaceDirective);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "UsingDecl[target=" + target + ", namespaceDirective=" + namespaceDirective + ", range=" + range + "]";
        }
    }

    /** 文件作用域对象声明；initializer 为 null 时按 C 规则零初始化。 */
    static final class GlobalVarDecl extends AbstractAstNode implements Declaration {
        private final String name;
        private final CrakenType type;
        private final craken.compiler.parser.node.Expression initializer;
        private final boolean external;
        private final List<AlignmentSpec> alignmentSpecs;
        private final InitializerSyntax initializerSyntax;
        private final SourceRange range;
        private final boolean constexprSpecifier;

        public GlobalVarDecl(String name,CrakenType type,Expression initializer,boolean external,List<AlignmentSpec> alignmentSpecs,InitializerSyntax initializerSyntax,SourceRange range){this(name,type,initializer,external,alignmentSpecs,initializerSyntax,range,false);}
        public GlobalVarDecl withConstexprSpecifier(boolean value){return new GlobalVarDecl(name,value?CrakenType.qualified(type,java.util.Set.of(CrakenType.TypeQualifier.CONST)):type,initializer,external,alignmentSpecs,initializerSyntax,range,value);}
        @AstNodeConstructor({"name", "type", "initializer", "external", "alignmentSpecs", "initializerSyntax", "range", "constexprSpecifier"})
        public GlobalVarDecl(String name, CrakenType type, craken.compiler.parser.node.Expression initializer, boolean external, List<AlignmentSpec> alignmentSpecs, InitializerSyntax initializerSyntax, SourceRange range, boolean constexprSpecifier) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(alignmentSpecs, "alignmentSpecs");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            alignmentSpecs = List.copyOf(alignmentSpecs);
            if (initializerSyntax != null && !initializerSyntax.isCompatibilityProjection(initializer)) {
                throw new IllegalArgumentException("Initializer syntax operands must match the core initializer");
            }

            this.name = name;
            this.type = type;
            this.initializer = initializer;
            this.external = external;
            this.alignmentSpecs = alignmentSpecs;
            this.initializerSyntax = initializerSyntax;
            this.range = range;
            this.constexprSpecifier = constexprSpecifier;
        }

        public GlobalVarDecl(String name, CrakenType type, Expression initializer, boolean external,
                             List<AlignmentSpec> alignmentSpecs, SourceRange range) {
            this(name, type, initializer, external, alignmentSpecs, null, range);
        }

        public Optional<craken.compiler.parser.node.Expression> initializerOptional() {
            return Optional.ofNullable(initializer);
        }

        public String name() { return name; }
        public CrakenType type() { return type; }
        public craken.compiler.parser.node.Expression initializer() { return initializer; }
        public boolean external() { return external; }
        public List<AlignmentSpec> alignmentSpecs() { return alignmentSpecs; }
        public InitializerSyntax initializerSyntax() { return initializerSyntax; }
        public SourceRange range() { return range; }
        public boolean constexprSpecifier() { return constexprSpecifier; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof GlobalVarDecl that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(type, that.type)
                    && Objects.equals(initializer, that.initializer)
                    && external == that.external
                    && Objects.equals(alignmentSpecs, that.alignmentSpecs)
                    && Objects.equals(initializerSyntax, that.initializerSyntax)
                    && Objects.equals(range, that.range)
                    && constexprSpecifier == that.constexprSpecifier;
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(initializer);
            result = 31 * result + Boolean.hashCode(external);
            result = 31 * result + Objects.hashCode(alignmentSpecs);
            result = 31 * result + Objects.hashCode(initializerSyntax);
            result = 31 * result + Objects.hashCode(range);
            result = 31 * result + Boolean.hashCode(constexprSpecifier);
            return result;
        }

        @Override public String toString() {
            return "GlobalVarDecl[name=" + name + ", type=" + type + ", initializer=" + initializer + ", external=" + external + ", alignmentSpecs=" + alignmentSpecs + ", initializerSyntax=" + initializerSyntax + ", range=" + range + ", constexprSpecifier=" + constexprSpecifier + "]";
        }
    }

    static final class TypedefDecl extends AbstractAstNode implements Declaration {
        private final String name;
        private final CrakenType type;
        private final SourceRange range;

        @AstNodeConstructor({"name", "type", "range"})
        public TypedefDecl(String name, CrakenType type, SourceRange range) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(range, "range");

            this.name = name;
            this.type = type;
            this.range = range;
        }

        public String name() { return name; }
        public CrakenType type() { return type; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TypedefDecl that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(type, that.type)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "TypedefDecl[name=" + name + ", type=" + type + ", range=" + range + "]";
        }
    }

    /** One _Alignas/alignas requirement, expressed either as a constant or as a type. */
    static final class AlignmentSpec extends AbstractAstNode implements Declaration {
        private final Integer constant;
        private final CrakenType type;
        private final SourceRange range;

        @AstNodeConstructor({"constant", "type", "range"})
        public AlignmentSpec(Integer constant, CrakenType type, SourceRange range) {
            Objects.requireNonNull(range, "range");
            if ((constant == null) == (type == null)) {
                throw new IllegalArgumentException("alignment spec must contain exactly one operand");
            }

            this.constant = constant;
            this.type = type;
            this.range = range;
        }

        public static AlignmentSpec constant(int value, SourceRange range) {
            return new AlignmentSpec(value, null, range);
        }

        public static AlignmentSpec type(CrakenType type, SourceRange range) {
            return new AlignmentSpec(null, Objects.requireNonNull(type, "type"), range);
        }

        public Integer constant() { return constant; }
        public CrakenType type() { return type; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AlignmentSpec that)) return false;
            return Objects.equals(constant, that.constant)
                    && Objects.equals(type, that.type)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(constant);
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "AlignmentSpec[constant=" + constant + ", type=" + type + ", range=" + range + "]";
        }
    }

    static final class EnumDecl extends AbstractAstNode implements Declaration {
        private final String name;
        private final List<Enumerator> enumerators;
        private final SourceRange range;

        @AstNodeConstructor({"name", "enumerators", "range"})
        public EnumDecl(String name, List<Enumerator> enumerators, SourceRange range) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(enumerators, "enumerators");
            Objects.requireNonNull(range, "range");
            enumerators = List.copyOf(enumerators);

            this.name = name;
            this.enumerators = enumerators;
            this.range = range;
        }

        public String name() { return name; }
        public List<Enumerator> enumerators() { return enumerators; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof EnumDecl that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(enumerators, that.enumerators)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(enumerators);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "EnumDecl[name=" + name + ", enumerators=" + enumerators + ", range=" + range + "]";
        }
    }

    static final class Enumerator extends AbstractAstNode implements Declaration {
        private final String name;
        private final long value;
        private final SourceRange range;

        @AstNodeConstructor({"name", "value", "range"})
        public Enumerator(String name, long value, SourceRange range) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(range, "range");

            this.name = name;
            this.value = value;
            this.range = range;
        }

        public String name() { return name; }
        public long value() { return value; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Enumerator that)) return false;
            return Objects.equals(name, that.name)
                    && value == that.value
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Long.hashCode(value);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "Enumerator[name=" + name + ", value=" + value + ", range=" + range + "]";
        }
    }

    enum DefinitionKind { ORDINARY, DEFAULTED, DELETED }

    static final class FunctionDecl extends AbstractAstNode implements Declaration {
        private final String name;
        private final CrakenType returnType;
        private final List<Parameter> parameters;
        private final boolean variadic;
        private final BlockStmt body;
        private final boolean external;
        private final boolean noReturn;
        private final SourceRange range;
        private final OperatorName operatorName;
        private final ConversionName conversionName;
        private final DefinitionKind definitionKind;
        private final CrakenType.ExceptionSpecification exceptionSpecification;
        private final boolean constexprSpecifier;

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
        @AstNodeConstructor({"name", "returnType", "parameters", "variadic", "body", "external", "noReturn", "range", "operatorName", "conversionName", "definitionKind", "exceptionSpecification", "constexprSpecifier"})
        public FunctionDecl(String name, CrakenType returnType, List<Parameter> parameters, boolean variadic, BlockStmt body, boolean external, boolean noReturn, SourceRange range, OperatorName operatorName, ConversionName conversionName, DefinitionKind definitionKind, CrakenType.ExceptionSpecification exceptionSpecification, boolean constexprSpecifier) {
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

            this.name = name;
            this.returnType = returnType;
            this.parameters = parameters;
            this.variadic = variadic;
            this.body = body;
            this.external = external;
            this.noReturn = noReturn;
            this.range = range;
            this.operatorName = operatorName;
            this.conversionName = conversionName;
            this.definitionKind = definitionKind;
            this.exceptionSpecification = exceptionSpecification;
            this.constexprSpecifier = constexprSpecifier;
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

        public String name() { return name; }
        public CrakenType returnType() { return returnType; }
        public List<Parameter> parameters() { return parameters; }
        public boolean variadic() { return variadic; }
        public BlockStmt body() { return body; }
        public boolean external() { return external; }
        public boolean noReturn() { return noReturn; }
        public SourceRange range() { return range; }
        public OperatorName operatorName() { return operatorName; }
        public ConversionName conversionName() { return conversionName; }
        public DefinitionKind definitionKind() { return definitionKind; }
        public CrakenType.ExceptionSpecification exceptionSpecification() { return exceptionSpecification; }
        public boolean constexprSpecifier() { return constexprSpecifier; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FunctionDecl that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(returnType, that.returnType)
                    && Objects.equals(parameters, that.parameters)
                    && variadic == that.variadic
                    && Objects.equals(body, that.body)
                    && external == that.external
                    && noReturn == that.noReturn
                    && Objects.equals(range, that.range)
                    && Objects.equals(operatorName, that.operatorName)
                    && Objects.equals(conversionName, that.conversionName)
                    && Objects.equals(definitionKind, that.definitionKind)
                    && Objects.equals(exceptionSpecification, that.exceptionSpecification)
                    && constexprSpecifier == that.constexprSpecifier;
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(returnType);
            result = 31 * result + Objects.hashCode(parameters);
            result = 31 * result + Boolean.hashCode(variadic);
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Boolean.hashCode(external);
            result = 31 * result + Boolean.hashCode(noReturn);
            result = 31 * result + Objects.hashCode(range);
            result = 31 * result + Objects.hashCode(operatorName);
            result = 31 * result + Objects.hashCode(conversionName);
            result = 31 * result + Objects.hashCode(definitionKind);
            result = 31 * result + Objects.hashCode(exceptionSpecification);
            result = 31 * result + Boolean.hashCode(constexprSpecifier);
            return result;
        }

        @Override public String toString() {
            return "FunctionDecl[name=" + name + ", returnType=" + returnType + ", parameters=" + parameters + ", variadic=" + variadic + ", body=" + body + ", external=" + external + ", noReturn=" + noReturn + ", range=" + range + ", operatorName=" + operatorName + ", conversionName=" + conversionName + ", definitionKind=" + definitionKind + ", exceptionSpecification=" + exceptionSpecification + ", constexprSpecifier=" + constexprSpecifier + "]";
        }
    }

    static final class Parameter extends AbstractAstNode implements Declaration {
        private final String name;
        private final CrakenType type;
        private final Expression defaultValue;
        private final SourceRange range;

        public Parameter(String name,CrakenType type,SourceRange range){this(name,type,null,range);}
        @AstNodeConstructor({"name", "type", "defaultValue", "range"})
        public Parameter(String name, CrakenType type, Expression defaultValue, SourceRange range) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }

            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.range = range;
        }

        public String name() { return name; }
        public CrakenType type() { return type; }
        public Expression defaultValue() { return defaultValue; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Parameter that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(type, that.type)
                    && Objects.equals(defaultValue, that.defaultValue)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(defaultValue);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "Parameter[name=" + name + ", type=" + type + ", defaultValue=" + defaultValue + ", range=" + range + "]";
        }
    }

    /** A qualified member definition retains its source owner until semantic binding. */
    static final class OutOfLineMethodDecl extends AbstractAstNode implements Declaration {
        private final QualifiedName qualifiedName;
        private final FunctionDecl method;
        private final boolean constQualified;
        private final SourceRange nameRange;

        @AstNodeConstructor({"qualifiedName", "method", "constQualified", "nameRange"})
        public OutOfLineMethodDecl(QualifiedName qualifiedName, FunctionDecl method, boolean constQualified, SourceRange nameRange) {
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(nameRange, "nameRange");
            if (qualifiedName.segments().size() < 2
                    || !qualifiedName.segments().getLast().equals(method.name())) {
                throw new IllegalArgumentException("qualified member definition requires its owner and simple method name");
            }

            this.qualifiedName = qualifiedName;
            this.method = method;
            this.constQualified = constQualified;
            this.nameRange = nameRange;
        }
        @Override public SourceRange range() { return method.range(); }

        public QualifiedName qualifiedName() { return qualifiedName; }
        public FunctionDecl method() { return method; }
        public boolean constQualified() { return constQualified; }
        public SourceRange nameRange() { return nameRange; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof OutOfLineMethodDecl that)) return false;
            return Objects.equals(qualifiedName, that.qualifiedName)
                    && Objects.equals(method, that.method)
                    && constQualified == that.constQualified
                    && Objects.equals(nameRange, that.nameRange);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(qualifiedName);
            result = 31 * result + Objects.hashCode(method);
            result = 31 * result + Boolean.hashCode(constQualified);
            result = 31 * result + Objects.hashCode(nameRange);
            return result;
        }

        @Override public String toString() {
            return "OutOfLineMethodDecl[qualifiedName=" + qualifiedName + ", method=" + method + ", constQualified=" + constQualified + ", nameRange=" + nameRange + "]";
        }
    }

    enum RecordKey { STRUCT, CLASS }

    enum Access { PUBLIC, PROTECTED, PRIVATE }

    /** Source-only class information; member order determines effective access later. */
    static final class BaseSpecifier extends AbstractAstNode implements AstNode {
        private final CrakenType type;
        private final Access access;
        private final boolean virtualBase;
        private final SourceRange range;

        @AstNodeConstructor({"type", "access", "virtualBase", "range"})
        public BaseSpecifier(CrakenType type, Access access, boolean virtualBase, SourceRange range) {
            this.type = type;
            this.access = access;
            this.virtualBase = virtualBase;
            this.range = range;
        }

        public CrakenType type() { return type; }
        public Access access() { return access; }
        public boolean virtualBase() { return virtualBase; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BaseSpecifier that)) return false;
            return Objects.equals(type, that.type)
                    && Objects.equals(access, that.access)
                    && virtualBase == that.virtualBase
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(access);
            result = 31 * result + Boolean.hashCode(virtualBase);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "BaseSpecifier[type=" + type + ", access=" + access + ", virtualBase=" + virtualBase + ", range=" + range + "]";
        }
    }

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

    static final class TemplateMethodMember extends AbstractAstNode implements RecordMember {
        private final List<ClassTemplateDecl.Parameter> parameters;
        private final MethodMember method;

        @AstNodeConstructor({"parameters", "method"})
        public TemplateMethodMember(List<ClassTemplateDecl.Parameter> parameters, MethodMember method) {parameters=List.copyOf(parameters);Objects.requireNonNull(method);
            this.parameters = parameters;
            this.method = method;
        }
        @Override public SourceRange range(){return method.range();}

        public List<ClassTemplateDecl.Parameter> parameters() { return parameters; }
        public MethodMember method() { return method; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TemplateMethodMember that)) return false;
            return Objects.equals(parameters, that.parameters)
                    && Objects.equals(method, that.method);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(parameters);
            result = 31 * result + Objects.hashCode(method);
            return result;
        }

        @Override public String toString() {
            return "TemplateMethodMember[parameters=" + parameters + ", method=" + method + "]";
        }
    }
    static final class TemplateConstructorMember extends AbstractAstNode implements RecordMember {
        private final List<ClassTemplateDecl.Parameter> parameters;
        private final ConstructorMember constructor;

        @AstNodeConstructor({"parameters", "constructor"})
        public TemplateConstructorMember(List<ClassTemplateDecl.Parameter> parameters, ConstructorMember constructor) {parameters=List.copyOf(parameters);Objects.requireNonNull(constructor);
            this.parameters = parameters;
            this.constructor = constructor;
        }
        @Override public SourceRange range(){return constructor.range();}

        public List<ClassTemplateDecl.Parameter> parameters() { return parameters; }
        public ConstructorMember constructor() { return constructor; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TemplateConstructorMember that)) return false;
            return Objects.equals(parameters, that.parameters)
                    && Objects.equals(constructor, that.constructor);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(parameters);
            result = 31 * result + Objects.hashCode(constructor);
            return result;
        }

        @Override public String toString() {
            return "TemplateConstructorMember[parameters=" + parameters + ", constructor=" + constructor + "]";
        }
    }

    static final class MemberTypedef extends AbstractAstNode implements RecordMember {
        private final TypedefDecl declaration;

        @AstNodeConstructor({"declaration"})
        public MemberTypedef(TypedefDecl declaration) { Objects.requireNonNull(declaration);
            this.declaration = declaration;
        }
        @Override public SourceRange range(){return declaration.range();}

        public TypedefDecl declaration() { return declaration; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MemberTypedef that)) return false;
            return Objects.equals(declaration, that.declaration);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(declaration);
            return result;
        }

        @Override public String toString() {
            return "MemberTypedef[declaration=" + declaration + "]";
        }
    }

    /** Transparent source wrapper: layout and member views retain the same field node. */
    static final class FieldMember extends AbstractAstNode implements RecordMember {
        private final StructField field;
        private final InitializerSyntax defaultInitializer;

        public FieldMember(StructField field) { this(field, null); }
        @AstNodeConstructor({"field", "defaultInitializer"})
        public FieldMember(StructField field, InitializerSyntax defaultInitializer) { Objects.requireNonNull(field, "field");
            this.field = field;
            this.defaultInitializer = defaultInitializer;
        }
        @Override public SourceRange range() { return field.range(); }

        public StructField field() { return field; }
        public InitializerSyntax defaultInitializer() { return defaultInitializer; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FieldMember that)) return false;
            return Objects.equals(field, that.field)
                    && Objects.equals(defaultInitializer, that.defaultInitializer);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(field);
            result = 31 * result + Objects.hashCode(defaultInitializer);
            return result;
        }

        @Override public String toString() {
            return "FieldMember[field=" + field + ", defaultInitializer=" + defaultInitializer + "]";
        }
    }

    /** A static data member belongs to class lookup but has no instance layout slot. */
    static final class StaticFieldMember extends AbstractAstNode implements RecordMember {
        private final GlobalVarDecl declaration;

        @AstNodeConstructor({"declaration"})
        public StaticFieldMember(GlobalVarDecl declaration) { Objects.requireNonNull(declaration, "declaration");
            this.declaration = declaration;
        }
        @Override public SourceRange range() { return declaration.range(); }

        public GlobalVarDecl declaration() { return declaration; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StaticFieldMember that)) return false;
            return Objects.equals(declaration, that.declaration);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(declaration);
            return result;
        }

        @Override public String toString() {
            return "StaticFieldMember[declaration=" + declaration + "]";
        }
    }

    static final class OutOfLineStaticFieldDecl extends AbstractAstNode implements Declaration {
        private final QualifiedName qualifiedName;
        private final GlobalVarDecl declaration;
        private final SourceRange nameRange;

        @AstNodeConstructor({"qualifiedName", "declaration", "nameRange"})
        public OutOfLineStaticFieldDecl(QualifiedName qualifiedName, GlobalVarDecl declaration, SourceRange nameRange) {
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(declaration, "declaration");
            Objects.requireNonNull(nameRange, "nameRange");
            if (qualifiedName.segments().size() < 2 || !qualifiedName.segments().getLast().equals(declaration.name()))
                throw new IllegalArgumentException("static data definition requires its class owner");

            this.qualifiedName = qualifiedName;
            this.declaration = declaration;
            this.nameRange = nameRange;
        }
        @Override public SourceRange range() { return declaration.range(); }

        public QualifiedName qualifiedName() { return qualifiedName; }
        public GlobalVarDecl declaration() { return declaration; }
        public SourceRange nameRange() { return nameRange; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof OutOfLineStaticFieldDecl that)) return false;
            return Objects.equals(qualifiedName, that.qualifiedName)
                    && Objects.equals(declaration, that.declaration)
                    && Objects.equals(nameRange, that.nameRange);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(qualifiedName);
            result = 31 * result + Objects.hashCode(declaration);
            result = 31 * result + Objects.hashCode(nameRange);
            return result;
        }

        @Override public String toString() {
            return "OutOfLineStaticFieldDecl[qualifiedName=" + qualifiedName + ", declaration=" + declaration + ", nameRange=" + nameRange + "]";
        }
    }

    /** Constructor spelling and signature are source syntax, not an ordinary named method. */
    static final class ConstructorMember extends AbstractAstNode implements RecordMember {
        private final String name;
        private final List<Parameter> parameters;
        private final boolean variadic;
        private final List<MemberInitializer> initializers;
        private final BlockStmt body;
        private final SourceRange nameRange;
        private final SourceRange range;
        private final boolean explicitSpecifier;
        private final DefinitionKind definitionKind;
        private final CrakenType.ExceptionSpecification exceptionSpecification;
        private final boolean constexprSpecifier;

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
        @AstNodeConstructor({"name", "parameters", "variadic", "initializers", "body", "nameRange", "range", "explicitSpecifier", "definitionKind", "exceptionSpecification", "constexprSpecifier"})
        public ConstructorMember(String name, List<Parameter> parameters, boolean variadic, List<MemberInitializer> initializers, BlockStmt body, SourceRange nameRange, SourceRange range, boolean explicitSpecifier, DefinitionKind definitionKind, CrakenType.ExceptionSpecification exceptionSpecification, boolean constexprSpecifier) {
            Objects.requireNonNull(definitionKind,"definitionKind");
            Objects.requireNonNull(exceptionSpecification,"exceptionSpecification");
            if(body!=null&&definitionKind!=DefinitionKind.ORDINARY)throw new IllegalArgumentException("Special definition cannot have a body");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(nameRange, "nameRange");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("Constructor spelling must not be blank");
            parameters = List.copyOf(parameters);
            initializers = List.copyOf(initializers);

            this.name = name;
            this.parameters = parameters;
            this.variadic = variadic;
            this.initializers = initializers;
            this.body = body;
            this.nameRange = nameRange;
            this.range = range;
            this.explicitSpecifier = explicitSpecifier;
            this.definitionKind = definitionKind;
            this.exceptionSpecification = exceptionSpecification;
            this.constexprSpecifier = constexprSpecifier;
        }

        public String name() { return name; }
        public List<Parameter> parameters() { return parameters; }
        public boolean variadic() { return variadic; }
        public List<MemberInitializer> initializers() { return initializers; }
        public BlockStmt body() { return body; }
        public SourceRange nameRange() { return nameRange; }
        public SourceRange range() { return range; }
        public boolean explicitSpecifier() { return explicitSpecifier; }
        public DefinitionKind definitionKind() { return definitionKind; }
        public CrakenType.ExceptionSpecification exceptionSpecification() { return exceptionSpecification; }
        public boolean constexprSpecifier() { return constexprSpecifier; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ConstructorMember that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(parameters, that.parameters)
                    && variadic == that.variadic
                    && Objects.equals(initializers, that.initializers)
                    && Objects.equals(body, that.body)
                    && Objects.equals(nameRange, that.nameRange)
                    && Objects.equals(range, that.range)
                    && explicitSpecifier == that.explicitSpecifier
                    && Objects.equals(definitionKind, that.definitionKind)
                    && Objects.equals(exceptionSpecification, that.exceptionSpecification)
                    && constexprSpecifier == that.constexprSpecifier;
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(parameters);
            result = 31 * result + Boolean.hashCode(variadic);
            result = 31 * result + Objects.hashCode(initializers);
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Objects.hashCode(nameRange);
            result = 31 * result + Objects.hashCode(range);
            result = 31 * result + Boolean.hashCode(explicitSpecifier);
            result = 31 * result + Objects.hashCode(definitionKind);
            result = 31 * result + Objects.hashCode(exceptionSpecification);
            result = 31 * result + Boolean.hashCode(constexprSpecifier);
            return result;
        }

        @Override public String toString() {
            return "ConstructorMember[name=" + name + ", parameters=" + parameters + ", variadic=" + variadic + ", initializers=" + initializers + ", body=" + body + ", nameRange=" + nameRange + ", range=" + range + ", explicitSpecifier=" + explicitSpecifier + ", definitionKind=" + definitionKind + ", exceptionSpecification=" + exceptionSpecification + ", constexprSpecifier=" + constexprSpecifier + "]";
        }
    }

    /** A destructor has neither a return type nor parameters; nameRange includes the '~'. */
    static final class DestructorMember extends AbstractAstNode implements RecordMember {
        private final String name;
        private final BlockStmt body;
        private final SourceRange nameRange;
        private final SourceRange range;
        private final DefinitionKind definitionKind;
        private final CrakenType.ExceptionSpecification exceptionSpecification;

        public DestructorMember(String name,BlockStmt body,SourceRange nameRange,SourceRange range,DefinitionKind definitionKind) {
            this(name,body,nameRange,range,definitionKind,CrakenType.ExceptionSpecification.UNSPECIFIED);
        }
        public DestructorMember withExceptionSpecification(CrakenType.ExceptionSpecification specification) {
            return new DestructorMember(name,body,nameRange,range,definitionKind,specification);
        }
        public DestructorMember(String name,BlockStmt body,SourceRange nameRange,SourceRange range){this(name,body,nameRange,range,DefinitionKind.ORDINARY);}
        public boolean hasDefinition(){return body!=null||definitionKind!=DefinitionKind.ORDINARY;}
        @AstNodeConstructor({"name", "body", "nameRange", "range", "definitionKind", "exceptionSpecification"})
        public DestructorMember(String name, BlockStmt body, SourceRange nameRange, SourceRange range, DefinitionKind definitionKind, CrakenType.ExceptionSpecification exceptionSpecification) {
            Objects.requireNonNull(definitionKind,"definitionKind");
            Objects.requireNonNull(exceptionSpecification,"exceptionSpecification");
            if(body!=null&&definitionKind!=DefinitionKind.ORDINARY)throw new IllegalArgumentException("Special definition cannot have a body");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(nameRange, "nameRange");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("Destructor spelling must not be blank");

            this.name = name;
            this.body = body;
            this.nameRange = nameRange;
            this.range = range;
            this.definitionKind = definitionKind;
            this.exceptionSpecification = exceptionSpecification;
        }

        public String name() { return name; }
        public BlockStmt body() { return body; }
        public SourceRange nameRange() { return nameRange; }
        public SourceRange range() { return range; }
        public DefinitionKind definitionKind() { return definitionKind; }
        public CrakenType.ExceptionSpecification exceptionSpecification() { return exceptionSpecification; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DestructorMember that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(body, that.body)
                    && Objects.equals(nameRange, that.nameRange)
                    && Objects.equals(range, that.range)
                    && Objects.equals(definitionKind, that.definitionKind)
                    && Objects.equals(exceptionSpecification, that.exceptionSpecification);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Objects.hashCode(nameRange);
            result = 31 * result + Objects.hashCode(range);
            result = 31 * result + Objects.hashCode(definitionKind);
            result = 31 * result + Objects.hashCode(exceptionSpecification);
            return result;
        }

        @Override public String toString() {
            return "DestructorMember[name=" + name + ", body=" + body + ", nameRange=" + nameRange + ", range=" + range + ", definitionKind=" + definitionKind + ", exceptionSpecification=" + exceptionSpecification + "]";
        }
    }

    /** The final qualified segment retains '~T', rather than masquerading as an ordinary method. */
    static final class OutOfLineDestructorDecl extends AbstractAstNode implements Declaration {
        private final QualifiedName qualifiedName;
        private final DestructorMember destructor;
        private final SourceRange nameRange;

        @AstNodeConstructor({"qualifiedName", "destructor", "nameRange"})
        public OutOfLineDestructorDecl(QualifiedName qualifiedName, DestructorMember destructor, SourceRange nameRange) {
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(destructor, "destructor");
            Objects.requireNonNull(nameRange, "nameRange");
            if (qualifiedName.segments().size() < 2
                    || !qualifiedName.segments().getLast().equals("~" + destructor.name())) {
                throw new IllegalArgumentException("Qualified destructor requires its owner and matching destructor spelling");
            }

            this.qualifiedName = qualifiedName;
            this.destructor = destructor;
            this.nameRange = nameRange;
        }
        @Override public SourceRange range() { return destructor.range(); }

        public QualifiedName qualifiedName() { return qualifiedName; }
        public DestructorMember destructor() { return destructor; }
        public SourceRange nameRange() { return nameRange; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof OutOfLineDestructorDecl that)) return false;
            return Objects.equals(qualifiedName, that.qualifiedName)
                    && Objects.equals(destructor, that.destructor)
                    && Objects.equals(nameRange, that.nameRange);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(qualifiedName);
            result = 31 * result + Objects.hashCode(destructor);
            result = 31 * result + Objects.hashCode(nameRange);
            return result;
        }

        @Override public String toString() {
            return "OutOfLineDestructorDecl[qualifiedName=" + qualifiedName + ", destructor=" + destructor + ", nameRange=" + nameRange + "]";
        }
    }

    /** Written order is retained separately from the declaration order used for execution. */
    static final class MemberInitializer extends AbstractAstNode implements AstNode {
        private final QualifiedName target;
        private final InitializerSyntax initializer;
        private final SourceRange range;

        @AstNodeConstructor({"target", "initializer", "range"})
        public MemberInitializer(QualifiedName target, InitializerSyntax initializer, SourceRange range) {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(range, "range");

            this.target = target;
            this.initializer = initializer;
            this.range = range;
        }

        public QualifiedName target() { return target; }
        public InitializerSyntax initializer() { return initializer; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MemberInitializer that)) return false;
            return Objects.equals(target, that.target)
                    && Objects.equals(initializer, that.initializer)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(target);
            result = 31 * result + Objects.hashCode(initializer);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "MemberInitializer[target=" + target + ", initializer=" + initializer + ", range=" + range + "]";
        }
    }

    static final class OutOfLineConstructorDecl extends AbstractAstNode implements Declaration {
        private final QualifiedName qualifiedName;
        private final ConstructorMember constructor;
        private final SourceRange nameRange;

        @AstNodeConstructor({"qualifiedName", "constructor", "nameRange"})
        public OutOfLineConstructorDecl(QualifiedName qualifiedName, ConstructorMember constructor, SourceRange nameRange) {
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(constructor, "constructor");
            Objects.requireNonNull(nameRange, "nameRange");
            if (qualifiedName.segments().size() < 2
                    || !qualifiedName.segments().getLast().equals(constructor.name())) {
                throw new IllegalArgumentException("Qualified constructor requires its owner and matching constructor spelling");
            }

            this.qualifiedName = qualifiedName;
            this.constructor = constructor;
            this.nameRange = nameRange;
        }
        @Override public SourceRange range() { return constructor.range(); }

        public QualifiedName qualifiedName() { return qualifiedName; }
        public ConstructorMember constructor() { return constructor; }
        public SourceRange nameRange() { return nameRange; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof OutOfLineConstructorDecl that)) return false;
            return Objects.equals(qualifiedName, that.qualifiedName)
                    && Objects.equals(constructor, that.constructor)
                    && Objects.equals(nameRange, that.nameRange);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(qualifiedName);
            result = 31 * result + Objects.hashCode(constructor);
            result = 31 * result + Objects.hashCode(nameRange);
            return result;
        }

        @Override public String toString() {
            return "OutOfLineConstructorDecl[qualifiedName=" + qualifiedName + ", constructor=" + constructor + ", nameRange=" + nameRange + "]";
        }
    }

    static final class MethodMember extends AbstractAstNode implements RecordMember {
        private final FunctionDecl method;
        private final boolean constQualified;
        private final boolean staticMember;
        private final SourceRange nameRange;

        public MethodMember(FunctionDecl method, boolean constQualified, SourceRange nameRange) {
            this(method, constQualified, false, nameRange);
        }
        public MethodMember(FunctionDecl method, SourceRange nameRange) {
            this(method, false, nameRange);
        }
        @AstNodeConstructor({"method", "constQualified", "staticMember", "nameRange"})
        public MethodMember(FunctionDecl method, boolean constQualified, boolean staticMember, SourceRange nameRange) {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(nameRange, "nameRange");

            this.method = method;
            this.constQualified = constQualified;
            this.staticMember = staticMember;
            this.nameRange = nameRange;
        }
        @Override public SourceRange range() { return method.range(); }

        public FunctionDecl method() { return method; }
        public boolean constQualified() { return constQualified; }
        public boolean staticMember() { return staticMember; }
        public SourceRange nameRange() { return nameRange; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MethodMember that)) return false;
            return Objects.equals(method, that.method)
                    && constQualified == that.constQualified
                    && staticMember == that.staticMember
                    && Objects.equals(nameRange, that.nameRange);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(method);
            result = 31 * result + Boolean.hashCode(constQualified);
            result = 31 * result + Boolean.hashCode(staticMember);
            result = 31 * result + Objects.hashCode(nameRange);
            return result;
        }

        @Override public String toString() {
            return "MethodMember[method=" + method + ", constQualified=" + constQualified + ", staticMember=" + staticMember + ", nameRange=" + nameRange + "]";
        }
    }

    static final class AccessLabel extends AbstractAstNode implements RecordMember {
        private final Access access;
        private final SourceRange range;

        @AstNodeConstructor({"access", "range"})
        public AccessLabel(Access access, SourceRange range) {
            Objects.requireNonNull(access, "access");
            Objects.requireNonNull(range, "range");

            this.access = access;
            this.range = range;
        }

        public Access access() { return access; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AccessLabel that)) return false;
            return Objects.equals(access, that.access)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(access);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "AccessLabel[access=" + access + ", range=" + range + "]";
        }
    }

    static final class StructDecl extends AbstractAstNode implements Declaration {
        private final String name;
        private final List<StructField> fields;
        private final boolean definition;
        private final boolean union;
        private final RecordInfo recordInfo;
        private final SourceRange range;

        @AstNodeConstructor({"name", "fields", "definition", "union", "recordInfo", "range"})
        public StructDecl(String name, List<StructField> fields, boolean definition, boolean union, RecordInfo recordInfo, SourceRange range) {
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

            this.name = name;
            this.fields = fields;
            this.definition = definition;
            this.union = union;
            this.recordInfo = recordInfo;
            this.range = range;
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

        public String name() { return name; }
        public List<StructField> fields() { return fields; }
        public boolean definition() { return definition; }
        public boolean union() { return union; }
        public RecordInfo recordInfo() { return recordInfo; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StructDecl that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(fields, that.fields)
                    && definition == that.definition
                    && union == that.union
                    && Objects.equals(recordInfo, that.recordInfo)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(fields);
            result = 31 * result + Boolean.hashCode(definition);
            result = 31 * result + Boolean.hashCode(union);
            result = 31 * result + Objects.hashCode(recordInfo);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "StructDecl[name=" + name + ", fields=" + fields + ", definition=" + definition + ", union=" + union + ", recordInfo=" + recordInfo + ", range=" + range + "]";
        }
    }

    static final class StructField extends AbstractAstNode implements Declaration {
        private final String name;
        private final CrakenType type;
        private final boolean anonymous;
        private final List<AlignmentSpec> alignmentSpecs;
        private final SourceRange range;

        @AstNodeConstructor({"name", "type", "anonymous", "alignmentSpecs", "range"})
        public StructField(String name, CrakenType type, boolean anonymous, List<AlignmentSpec> alignmentSpecs, SourceRange range) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(alignmentSpecs, "alignmentSpecs");
            Objects.requireNonNull(range, "range");
            if (name.isBlank() && !anonymous) {
                throw new IllegalArgumentException("name must not be blank");
            }
            alignmentSpecs = List.copyOf(alignmentSpecs);

            this.name = name;
            this.type = type;
            this.anonymous = anonymous;
            this.alignmentSpecs = alignmentSpecs;
            this.range = range;
        }

        public StructField(String name, CrakenType type, SourceRange range) {
            this(name, type, false, List.of(), range);
        }

        public StructField(String name, CrakenType type, List<AlignmentSpec> alignmentSpecs, SourceRange range) {
            this(name, type, false, alignmentSpecs, range);
        }

        public String name() { return name; }
        public CrakenType type() { return type; }
        public boolean anonymous() { return anonymous; }
        public List<AlignmentSpec> alignmentSpecs() { return alignmentSpecs; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StructField that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(type, that.type)
                    && anonymous == that.anonymous
                    && Objects.equals(alignmentSpecs, that.alignmentSpecs)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Boolean.hashCode(anonymous);
            result = 31 * result + Objects.hashCode(alignmentSpecs);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "StructField[name=" + name + ", type=" + type + ", anonymous=" + anonymous + ", alignmentSpecs=" + alignmentSpecs + ", range=" + range + "]";
        }
    }

    /** A generic source record; only concrete specializations may enter the core record indexes. */
    static final class ClassTemplateDecl extends AbstractAstNode implements Declaration {
        private final List<Parameter> parameters;
        private final Declaration.StructDecl record;
        private final List<craken.compiler.type.TemplateArgument> specializationArguments;
        private final boolean specialization;
        private final SourceRange range;

        public ClassTemplateDecl(List<Parameter> parameters, Declaration.StructDecl record, List<craken.compiler.type.TemplateArgument> arguments, SourceRange range) { this(parameters,record,arguments,!arguments.isEmpty(),range); }
        public ClassTemplateDecl(java.util.Collection<? extends Parameter> parameters, Declaration.StructDecl record, SourceRange range) {
            this(List.copyOf(parameters),record,List.of(),range);
        }
        @AstNodeConstructor({"parameters", "record", "specializationArguments", "specialization", "range"})
        public ClassTemplateDecl(List<Parameter> parameters, Declaration.StructDecl record, List<craken.compiler.type.TemplateArgument> specializationArguments, boolean specialization, SourceRange range) {
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

            this.parameters = parameters;
            this.record = record;
            this.specializationArguments = specializationArguments;
            this.specialization = specialization;
            this.range = range;
        }

        public sealed interface Parameter extends AstNode permits TypeParameter, ValueParameter {
            String name();
            CrakenType.TemplateParameterType type();
            boolean pack();
            default CrakenType defaultType() { return null; }
        }

        public static final class ValueParameter extends AbstractAstNode implements Parameter {
            private final String name;
            private final CrakenType.TemplateParameterType type;
            private final CrakenType valueType;
            private final Expression defaultValue;
            private final boolean pack;
            private final SourceRange range;

            public ValueParameter(String name, CrakenType.TemplateParameterType type, CrakenType valueType, Expression defaultValue, SourceRange range) { this(name,type,valueType,defaultValue,false,range); }
            @AstNodeConstructor({"name", "type", "valueType", "defaultValue", "pack", "range"})
            public ValueParameter(String name, CrakenType.TemplateParameterType type, CrakenType valueType, Expression defaultValue, boolean pack, SourceRange range) {
                Objects.requireNonNull(name); Objects.requireNonNull(type);
                Objects.requireNonNull(valueType); Objects.requireNonNull(range);

                this.name = name;
                this.type = type;
                this.valueType = valueType;
                this.defaultValue = defaultValue;
                this.pack = pack;
                this.range = range;
            }

            public String name() { return name; }
            public CrakenType.TemplateParameterType type() { return type; }
            public CrakenType valueType() { return valueType; }
            public Expression defaultValue() { return defaultValue; }
            public boolean pack() { return pack; }
            public SourceRange range() { return range; }

            @Override public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof ValueParameter that)) return false;
                return Objects.equals(name, that.name)
                        && Objects.equals(type, that.type)
                        && Objects.equals(valueType, that.valueType)
                        && Objects.equals(defaultValue, that.defaultValue)
                        && pack == that.pack
                        && Objects.equals(range, that.range);
            }

            @Override public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(name);
                result = 31 * result + Objects.hashCode(type);
                result = 31 * result + Objects.hashCode(valueType);
                result = 31 * result + Objects.hashCode(defaultValue);
                result = 31 * result + Boolean.hashCode(pack);
                result = 31 * result + Objects.hashCode(range);
                return result;
            }

            @Override public String toString() {
                return "ValueParameter[name=" + name + ", type=" + type + ", valueType=" + valueType + ", defaultValue=" + defaultValue + ", pack=" + pack + ", range=" + range + "]";
            }
        }

        public static final class TypeParameter extends AbstractAstNode implements Parameter {
            private final String name;
            private final CrakenType.TemplateParameterType type;
            private final CrakenType defaultType;
            private final boolean pack;
            private final SourceRange range;

            public TypeParameter(String name, CrakenType.TemplateParameterType type, CrakenType defaultType, SourceRange range) { this(name,type,defaultType,false,range); }
            public TypeParameter(String name, CrakenType.TemplateParameterType type, SourceRange range) {
                this(name, type, null, range);
            }
            @AstNodeConstructor({"name", "type", "defaultType", "pack", "range"})
            public TypeParameter(String name, CrakenType.TemplateParameterType type, CrakenType defaultType, boolean pack, SourceRange range) {
                Objects.requireNonNull(name, "name");
                Objects.requireNonNull(type, "type");
                Objects.requireNonNull(range, "range");
                if (name.isBlank()) throw new IllegalArgumentException("template parameter needs a name");

                this.name = name;
                this.type = type;
                this.defaultType = defaultType;
                this.pack = pack;
                this.range = range;
            }

            public String name() { return name; }
            public CrakenType.TemplateParameterType type() { return type; }
            public CrakenType defaultType() { return defaultType; }
            public boolean pack() { return pack; }
            public SourceRange range() { return range; }

            @Override public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof TypeParameter that)) return false;
                return Objects.equals(name, that.name)
                        && Objects.equals(type, that.type)
                        && Objects.equals(defaultType, that.defaultType)
                        && pack == that.pack
                        && Objects.equals(range, that.range);
            }

            @Override public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(name);
                result = 31 * result + Objects.hashCode(type);
                result = 31 * result + Objects.hashCode(defaultType);
                result = 31 * result + Boolean.hashCode(pack);
                result = 31 * result + Objects.hashCode(range);
                return result;
            }

            @Override public String toString() {
                return "TypeParameter[name=" + name + ", type=" + type + ", defaultType=" + defaultType + ", pack=" + pack + ", range=" + range + "]";
            }
        }

        public List<Parameter> parameters() { return parameters; }
        public Declaration.StructDecl record() { return record; }
        public List<craken.compiler.type.TemplateArgument> specializationArguments() { return specializationArguments; }
        public boolean specialization() { return specialization; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ClassTemplateDecl that)) return false;
            return Objects.equals(parameters, that.parameters)
                    && Objects.equals(record, that.record)
                    && Objects.equals(specializationArguments, that.specializationArguments)
                    && specialization == that.specialization
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(parameters);
            result = 31 * result + Objects.hashCode(record);
            result = 31 * result + Objects.hashCode(specializationArguments);
            result = 31 * result + Boolean.hashCode(specialization);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "ClassTemplateDecl[parameters=" + parameters + ", record=" + record + ", specializationArguments=" + specializationArguments + ", specialization=" + specialization + ", range=" + range + "]";
        }
    }

    /** A primary function template or an explicit specialization, before core binding. */
    static final class FunctionTemplateDecl extends AbstractAstNode implements Declaration {
        private final List<ClassTemplateDecl.Parameter> parameters;
        private final Declaration.FunctionDecl function;
        private final List<TemplateArgument> specializationArguments;
        private final QualifiedName qualifiedName;
        private final SourceRange range;

        public FunctionTemplateDecl(List<ClassTemplateDecl.Parameter> parameters,Declaration.FunctionDecl function,SourceRange range) {
            this(parameters,function,null,null,range);
        }
        @AstNodeConstructor({"parameters", "function", "specializationArguments", "qualifiedName", "range"})
        public FunctionTemplateDecl(List<ClassTemplateDecl.Parameter> parameters, Declaration.FunctionDecl function, List<TemplateArgument> specializationArguments, QualifiedName qualifiedName, SourceRange range) {
            parameters=List.copyOf(parameters);Objects.requireNonNull(function);Objects.requireNonNull(range);
            specializationArguments=specializationArguments==null?null:List.copyOf(specializationArguments);

            this.parameters = parameters;
            this.function = function;
            this.specializationArguments = specializationArguments;
            this.qualifiedName = qualifiedName;
            this.range = range;
        }
        public boolean specialization(){return parameters.isEmpty();}

        public List<ClassTemplateDecl.Parameter> parameters() { return parameters; }
        public Declaration.FunctionDecl function() { return function; }
        public List<TemplateArgument> specializationArguments() { return specializationArguments; }
        public QualifiedName qualifiedName() { return qualifiedName; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FunctionTemplateDecl that)) return false;
            return Objects.equals(parameters, that.parameters)
                    && Objects.equals(function, that.function)
                    && Objects.equals(specializationArguments, that.specializationArguments)
                    && Objects.equals(qualifiedName, that.qualifiedName)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(parameters);
            result = 31 * result + Objects.hashCode(function);
            result = 31 * result + Objects.hashCode(specializationArguments);
            result = 31 * result + Objects.hashCode(qualifiedName);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "FunctionTemplateDecl[parameters=" + parameters + ", function=" + function + ", specializationArguments=" + specializationArguments + ", qualifiedName=" + qualifiedName + ", range=" + range + "]";
        }
    }

    /** Source assertion, discarded only after constant evaluation. */
    static final class StaticAssertDecl extends AbstractAstNode
                            implements Declaration,Statement,Declaration.RecordMember {
        private final Expression condition;
        private final Expression.StringLiteralExpr message;
        private final SourceRange range;

        @AstNodeConstructor({"condition", "message", "range"})
        public StaticAssertDecl(Expression condition, Expression.StringLiteralExpr message, SourceRange range) {Objects.requireNonNull(condition);Objects.requireNonNull(range);
            this.condition = condition;
            this.message = message;
            this.range = range;
        }

        public Expression condition() { return condition; }
        public Expression.StringLiteralExpr message() { return message; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StaticAssertDecl that)) return false;
            return Objects.equals(condition, that.condition)
                    && Objects.equals(message, that.message)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(condition);
            result = 31 * result + Objects.hashCode(message);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "StaticAssertDecl[condition=" + condition + ", message=" + message + ", range=" + range + "]";
        }
    }

    /** Source declaration; the backing object and binding references are produced by name binding. */
    static final class StructuredBindingDecl extends AbstractAstNode
                                 implements Declaration, Statement {
        private final CrakenType type;
        private final List<BindingName> names;
        private final InitializerSyntax initializer;
        private final SourceRange range;

        @AstNodeConstructor({"type", "names", "initializer", "range"})
        public StructuredBindingDecl(CrakenType type, List<BindingName> names, InitializerSyntax initializer, SourceRange range) {
            Objects.requireNonNull(type); names=List.copyOf(names); Objects.requireNonNull(range);
            if(names.isEmpty())throw new IllegalArgumentException("A structured binding needs names");

            this.type = type;
            this.names = names;
            this.initializer = initializer;
            this.range = range;
        }
        public static final class BindingName extends AbstractAstNode implements AstNode {
            private final String name;
            private final SourceRange range;

            @AstNodeConstructor({"name", "range"})
            public BindingName(String name, SourceRange range) {Objects.requireNonNull(name);Objects.requireNonNull(range);
                this.name = name;
                this.range = range;
            }

            public String name() { return name; }
            public SourceRange range() { return range; }

            @Override public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof BindingName that)) return false;
                return Objects.equals(name, that.name)
                        && Objects.equals(range, that.range);
            }

            @Override public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(name);
                result = 31 * result + Objects.hashCode(range);
                return result;
            }

            @Override public String toString() {
                return "BindingName[name=" + name + ", range=" + range + "]";
            }
        }

        public CrakenType type() { return type; }
        public List<BindingName> names() { return names; }
        public InitializerSyntax initializer() { return initializer; }

        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StructuredBindingDecl that)) return false;
            return Objects.equals(type, that.type)
                    && Objects.equals(names, that.names)
                    && Objects.equals(initializer, that.initializer)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(names);
            result = 31 * result + Objects.hashCode(initializer);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "StructuredBindingDecl[type=" + type + ", names=" + names + ", initializer=" + initializer + ", range=" + range + "]";
        }
    }

    /** A member definition with a structural class-template owner, before instantiation. */
    static final class TemplateMemberDefinitionDecl extends AbstractAstNode implements Declaration {
        private final List<ClassTemplateDecl.Parameter> parameters;
        private final CrakenType.TemplateIdType ownerType;
        private final Declaration declaration;
        private final SourceRange range;

        @AstNodeConstructor({"parameters", "ownerType", "declaration", "range"})
        public TemplateMemberDefinitionDecl(List<ClassTemplateDecl.Parameter> parameters, CrakenType.TemplateIdType ownerType, Declaration declaration, SourceRange range) {
            parameters=List.copyOf(parameters);Objects.requireNonNull(ownerType);Objects.requireNonNull(declaration);Objects.requireNonNull(range);

            this.parameters = parameters;
            this.ownerType = ownerType;
            this.declaration = declaration;
            this.range = range;
        }

        public List<ClassTemplateDecl.Parameter> parameters() { return parameters; }
        public CrakenType.TemplateIdType ownerType() { return ownerType; }
        public Declaration declaration() { return declaration; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TemplateMemberDefinitionDecl that)) return false;
            return Objects.equals(parameters, that.parameters)
                    && Objects.equals(ownerType, that.ownerType)
                    && Objects.equals(declaration, that.declaration)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(parameters);
            result = 31 * result + Objects.hashCode(ownerType);
            result = 31 * result + Objects.hashCode(declaration);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "TemplateMemberDefinitionDecl[parameters=" + parameters + ", ownerType=" + ownerType + ", declaration=" + declaration + ", range=" + range + "]";
        }
    }
}
