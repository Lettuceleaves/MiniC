package minic.compiler.semantic.cpp;

import minic.SourceRange;
import minic.compiler.Diagnostic;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.CppInitializer;
import minic.compiler.parser.node.CppRangeForStmt;
import minic.compiler.parser.node.CppLambdaExpr;
import minic.compiler.parser.node.ConversionName;
import minic.compiler.parser.node.CppConstructionExpr;
import minic.compiler.parser.node.CppTypeQueryExpr;
import minic.compiler.parser.node.CppDestructorCallExpr;
import minic.compiler.parser.node.CppNewExpr;
import minic.compiler.parser.node.CppTypeMemberExpr;
import minic.compiler.parser.node.ClassTemplateDecl;
import minic.compiler.parser.node.FunctionTemplateDecl;
import minic.compiler.parser.node.CppTemplateIdExpr;
import minic.compiler.parser.node.CleanupScopeStmt;
import minic.compiler.parser.node.Declaration;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.QualifiedName;
import minic.compiler.parser.node.OperatorName;
import minic.compiler.parser.node.Statement;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.type.MiniType;
import minic.compiler.type.TemplateArgument;
import minic.compiler.type.TemplateValues;
import minic.compiler.parser.node.CppTemplateValueExpr;
import minic.compiler.semantic.manager.TypeCompatibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Binds the supported non-template C++ value names before the C semantic/IR passes.
 * The source AST remains unchanged. All generated names are ordinary core identifiers;
 * no downstream pass needs to interpret namespace syntax or repeat C++ lookup.
 */
public final class CppNameBinder {
    private CppNameBinder() {}

    /** Removed namespace/using containers have no executable counterpart in sourceToCore. */
    public record Result(Program program, List<Diagnostic> diagnostics,
                         Map<AstNode, AstNode> sourceToCore, Map<String, String> displayNames) {
        public Result {
            Objects.requireNonNull(program, "program");
            diagnostics = List.copyOf(diagnostics);
            sourceToCore = Collections.unmodifiableMap(new IdentityHashMap<>(sourceToCore));
            displayNames = Collections.unmodifiableMap(new LinkedHashMap<>(displayNames));
        }
    }

    public static Result bind(Program source) {
        Objects.requireNonNull(source, "source");
        return new Binding(source).run();
    }

    private enum Kind { VARIABLE, FUNCTION, ENUM_CONSTANT }

    private interface Candidate {}

    /** A snapshot of visible functions; later namespace declarations cannot change a using import. */
    private record OverloadSet(List<Entity> functions) implements Candidate {
        OverloadSet { functions = List.copyOf(functions); }
    }

    /** Class identity is independent from value identity; aliases carry the resolved core type. */
    private static final class TypeEntity implements Candidate {
        final String name;
        final String canonicalName;
        final MiniType type;
        final boolean classType;
        final boolean union;
        final Namespace owner;
        boolean complete;
        List<StructField> fields = List.of();
        final Map<StructField, Access> fieldAccess = new IdentityHashMap<>();
        final Map<String, MethodSet> methods = new LinkedHashMap<>();
        final Map<String, MiniType> memberTypes = new LinkedHashMap<>();
        final Map<String, Access> memberTypeAccess = new LinkedHashMap<>();
        final Map<String, StaticField> staticFields = new LinkedHashMap<>();
        StructDecl sourceRecord;
        TypeEntity markerBase;
        final List<Constructor> constructors = new ArrayList<>();
        final Map<String, Entity> defaultInitializers = new LinkedHashMap<>();
        Constructor aggregateInitializer;
        Constructor implicitCopy;
        boolean movePlanned;
        Constructor implicitMove;
        CppCopyConstructorPlan.Result<Constructor> movePlan;
        boolean moveEmitted,movePrototypeEmitted;
        Method implicitMoveAssignment;
        AssignmentPlan moveAssignmentPlan;
        boolean moveAssignmentEmitted,moveAssignmentPrototypeEmitted;
        CppCopyConstructorPlan.Result<Constructor> copyPlan;
        boolean copyEmitted;
        boolean copyPrototypeEmitted;
        boolean assignmentPlanned;
        Method implicitAssignment;
        AssignmentPlan assignmentPlan;
        boolean assignmentEmitted;
        boolean assignmentPrototypeEmitted;
        Destructor destructor;

        TypeEntity(String name, String canonicalName, MiniType type, boolean classType,
                   boolean union, Namespace owner, boolean complete) {
            this.name = name;
            this.canonicalName = canonicalName;
            this.type = type;
            this.classType = classType;
            this.union = union;
            this.owner = owner;
            this.complete = complete;
        }
    }

    /** A method belongs to a class identity, never to the enclosing namespace's value table. */
    private record Method(TypeEntity owner, MethodMember source, Access access, Entity function,
                          MiniType returnType, List<MiniType> parameterTypes) implements Candidate { }

    private record MethodSet(List<Method> methods) implements Candidate {
        MethodSet { methods = List.copyOf(methods); }
    }

    private record Constructor(TypeEntity owner, ConstructorMember source, Access access, Entity function,
                               List<MiniType> parameterTypes, boolean implicit) { }

    private record Destructor(TypeEntity owner, DestructorMember source, Access access, Entity function,
                              boolean implicit) { }

    private record AssignmentEntry(StructField field, List<Integer> dimensions, Method method) { }
    private record AssignmentPlan(MiniType parameterType, List<AssignmentEntry> entries, List<String> problems,
                                  boolean trivial) { }

    private static final class StaticField {
        final TypeEntity owner;
        final GlobalVarDecl source;
        final Access access;
        final Entity entity;
        Expression constant;
        boolean defining;
        final List<SourceRange> uses = new ArrayList<>();
        StaticField(TypeEntity owner, GlobalVarDecl source, Access access, Entity entity) {
            this.owner = owner; this.source = source; this.access = access; this.entity = entity;
        }
    }

    private record ImplicitField(TypeEntity owner, String name) implements Candidate { }
    private record UnevaluatedLambdaLocal(Entity entity) implements Candidate { }
    private record LambdaCapture(String name,MiniType type,Expression initializer,boolean reference,
                                 CppLambdaExpr.Capture source) { }
    private static final class LambdaInfo {
        final CppLambdaExpr source;
        final TypeEntity type;
        final Local lexicalScope;
        final TypeEntity lexicalClass;
        final Entity lexicalThis;
        final Namespace namespace;
        final Map<String,LambdaCapture> captures=new LinkedHashMap<>();
        boolean complete;
        boolean generic;
        final Set<MiniType> pointerConversions=new HashSet<>();
        final String thisCaptureName;
        LambdaInfo(CppLambdaExpr source,TypeEntity type,Local scope,TypeEntity outerClass,Entity outerThis,Namespace namespace,String thisCaptureName) {
            this.thisCaptureName=thisCaptureName;this.source=source;this.type=type;this.lexicalScope=scope;this.lexicalClass=outerClass;this.lexicalThis=outerThis;this.namespace=namespace;
        }
    }

    /** An entity survives redeclarations and using aliases; candidate deduplication uses identity. */
    private static final class Entity implements Candidate {
        final String name;
        final String coreName;
        final Kind kind;
        final Namespace owner;
        MiniType type;
        final Long enumValue;
        boolean defined;

        Entity(String name, String coreName, Kind kind, Namespace owner, MiniType type,
               Long enumValue, boolean defined) {
            this.name = name;
            this.coreName = coreName;
            this.kind = kind;
            this.owner = owner;
            this.type = type;
            this.enumValue = enumValue;
            this.defined = defined;
        }
    }

    private static final class Namespace implements Candidate {
        final Namespace parent;
        final String name;
        final Map<String, Namespace> children = new LinkedHashMap<>();
        final Map<String, Candidate> values = new LinkedHashMap<>();
        final Map<String, TypeEntity> typedefs = new LinkedHashMap<>();
        final Map<String, TypeEntity> tags = new LinkedHashMap<>();
        final List<Namespace> directives = new ArrayList<>();

        Namespace(Namespace parent, String name) { this.parent = parent; this.name = name; }
        String qualify(String value) {
            return parent == null ? value : parent.qualify(name) + "::" + value;
        }
    }

    private static final class Local {
        final Local parent;
        final Namespace namespace;
        final Map<String, Candidate> values = new LinkedHashMap<>();
        final Map<String, TypeEntity> typedefs = new LinkedHashMap<>();
        final List<Namespace> directives = new ArrayList<>();
        Local(Local parent, Namespace namespace) { this.parent = parent; this.namespace = namespace; }
    }

    private static final class Binding {
        private final Program source;
        private final Namespace root = new Namespace(null, "");
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private final IdentityHashMap<AstNode, AstNode> origins = new IdentityHashMap<>();
        private final Map<String, String> displayNames = new LinkedHashMap<>();
        private final Set<String> reserved = new HashSet<>();
        private final Map<String, TypeEntity> canonicalTypes = new LinkedHashMap<>();
        private final Map<String, TypeEntity> coreTypes = new LinkedHashMap<>();
        private final Map<String, Entity> coreValues = new LinkedHashMap<>();
        private final Map<Entity, StaticField> staticFields = new IdentityHashMap<>();
        private final Map<Entity, Method> staticMethods = new IdentityHashMap<>();
        private final IdentityHashMap<Expression, MiniType> declaredExpressionTypes = new IdentityHashMap<>();
        private final IdentityHashMap<Expression, CppValueCategory> valueCategories = new IdentityHashMap<>();
        private final Set<Expression> implicitMoveSources=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Expression> temporaryAddressPaths = Collections.newSetFromMap(new IdentityHashMap<>());
        private int unevaluatedDepth;
        private final Map<CppLambdaExpr,LambdaInfo> lambdaExpressions=new IdentityHashMap<>();
        private final Map<TypeEntity,LambdaInfo> lambdaTypes=new IdentityHashMap<>();
        private final Set<Entity> staticLocalEntities=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Constructor,CppCopyConstructorPlan.Result<Constructor>> defaultedCopyPlans=new IdentityHashMap<>();
        private final Map<Method,AssignmentPlan> defaultedAssignmentPlans=new IdentityHashMap<>();
        private final Set<Entity> emittedTransfers=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Entity> transferPrototypes=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Entity> emittedAssignments=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Entity> assignmentPrototypes=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Entity> deletedFunctions=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Entity> userProvidedDefaulted=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Entity, SourceRange> specialDefinitionRanges=new IdentityHashMap<>();
        private final Map<Entity, List<Diagnostic>> deletedConstructors = new IdentityHashMap<>();
        private final Map<Entity, List<Diagnostic>> deletedDestructors = new IdentityHashMap<>();
        private final Map<Statement, Expression> localCleanups = new IdentityHashMap<>();
        private final Map<Statement, List<VarDeclStmt>> localPreludes = new IdentityHashMap<>();
        private final List<Declaration> declarations = new ArrayList<>();
        private final List<StructDecl> structs = new ArrayList<>();
        private final List<EnumDecl> enums = new ArrayList<>();
        private final List<TypedefDecl> typedefs = new ArrayList<>();
        private final List<GlobalVarDecl> globals = new ArrayList<>();
        private final List<FunctionDecl> functions = new ArrayList<>();
        private int nextName = 1;
        private TypeEntity currentClass;
        private Entity currentThis;
        private MiniType currentReturnType;
        private static final class AutoReturnContext {
            final Entity function; final MiniType pattern; MiniType deduced;
            AutoReturnContext(Entity function, MiniType pattern) { this.function=function; this.pattern=pattern; }
        }
        private AutoReturnContext currentAutoReturn;
        private final Map<Entity, List<FunctionDecl>> pendingAutoDeclarations = new IdentityHashMap<>();
        private final Map<Entity, MiniType> autoReturnPatterns = new IdentityHashMap<>();
        private Expression fullExpressionOwner;
        private Expression decltypeOperand;
        private final Map<Expression, PreparedArguments> bracedArguments = new IdentityHashMap<>();
        private record ListStorage(MiniType type, MiniType element, List<Expression> values, SourceRange range) { }
        private final Map<Expression, ListStorage> listStorage = new IdentityHashMap<>();
        private final CppOverloadResolver.UserConversionProvider conversions = new CppOverloadResolver.UserConversionProvider() {
            public CppOverloadResolver.UserConversion find(Object candidate, CppOverloadResolver.Argument source, MiniType target) {
                return source.braced() ? listUserConversion(source, target) : implicitUserConversion(candidate, source, target);
            }
            public MiniType initializerListElement(MiniType target) { return Binding.this.initializerListElement(target); }
            public int baseDistance(MiniType source,MiniType target) { return Binding.this.baseDistance(source,target); }
        };
        private record NamespaceView(Map<String, Candidate> values, Map<String, Namespace> children,
                                     Map<String, TypeEntity> typedefs, Map<String, TypeEntity> tags,
                                     List<Namespace> directives) {}
        private record TemplateDefinition(ClassTemplateDecl source, Namespace owner,
                                          Map<Namespace, NamespaceView> lookup) {}
        private record DefaultArgument(Expression source,Namespace owner,TypeEntity record,Map<Namespace,NamespaceView> lookup) {}
        private final Map<Entity,List<DefaultArgument>> functionDefaults=new IdentityHashMap<>();
        private record FunctionTemplateDefinition(List<ClassTemplateDecl.Parameter> parameters, FunctionDecl source,
                Namespace owner, TypeEntity record, MethodMember method, ConstructorMember constructor, Access access,
                Map<Namespace, NamespaceView> lookup) {}
        private record FunctionTemplateKey(Entity declaration,List<TemplateArgument> arguments) {}
        private record FunctionTemplateInstance(FunctionTemplateDefinition definition,CppTemplateDeduction.Bindings bindings,FunctionDecl function,
                Method method,Constructor constructor) {}
        private final Map<Entity,FunctionTemplateDefinition> functionTemplates=new IdentityHashMap<>();
        private final Map<FunctionTemplateKey,Entity> functionTemplateCache=new LinkedHashMap<>();
        private final Map<Entity,FunctionTemplateInstance> functionTemplateInstances=new IdentityHashMap<>();
        private final Set<Entity> requestedFunctionTemplates=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Entity,Entity> functionTemplateDeclarations=new IdentityHashMap<>();
        private int functionTemplateDepth;
        private final Set<Entity> emittedFunctionTemplates=Collections.newSetFromMap(new IdentityHashMap<>());

        private final Map<String, TemplateDefinition> templates = new LinkedHashMap<>();
        private final Map<String,List<TemplateDefinition>> templateSpecializations=new LinkedHashMap<>();
        private record TemplateSelection(TemplateDefinition definition,CppTemplateDeduction.Bindings bindings) {}
        private final Map<MiniType.TemplateIdType, TypeEntity> templateInstances = new LinkedHashMap<>();
        private final Map<TypeEntity, MiniType.TemplateIdType> instanceKeys = new IdentityHashMap<>();
        private final Map<TypeEntity, Map<Namespace, NamespaceView>> instanceLookup = new IdentityHashMap<>();
        private final Set<TypeEntity> instantiating = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Entity, Method> pendingTemplateMethods = new IdentityHashMap<>();
        private final Map<Entity, Constructor> pendingTemplateConstructors = new IdentityHashMap<>();
        private final Map<Entity, Destructor> pendingTemplateDestructors = new IdentityHashMap<>();
        private final Map<AstNode, AstNode> templateOrigins = new IdentityHashMap<>();
        private final Map<String, String> templateDisplay = new LinkedHashMap<>();
        private Map<Namespace, NamespaceView> currentTemplateLookup;
        private boolean staticInitialization;
        private boolean internalDeclaration;
        private final IdentityHashMap<Entity, Boolean> internalLinkages = new IdentityHashMap<>();
        private final Set<Entity> libraryExitFunctions = Collections.newSetFromMap(new IdentityHashMap<>());
        private final CppStaticLifetime staticLifetime;

        Binding(Program source) {
            this.source = source;
            reserveNames(source);
            staticLifetime = new CppStaticLifetime(new CppStaticLifetime.Context() {
                public String fresh(String display) { return freshName(display); }
                public void global(GlobalVarDecl variable) { addStaticGlobal(variable); }
                public void function(FunctionDecl function) { addStaticFunction(function); }
            }, source.range());
        }

        private void addStaticGlobal(GlobalVarDecl variable) {
            globals.add(variable); declarations.add(variable);
            coreValues.putIfAbsent(variable.name(), new Entity(variable.name(), variable.name(), Kind.VARIABLE,
                    root, variable.type(), null, true));
        }

        private void addStaticFunction(FunctionDecl function) {
            functions.add(function); declarations.add(function);
            coreValues.putIfAbsent(function.name(), new Entity(function.name(), function.name(), Kind.FUNCTION, root,
                    MiniType.function(function.returnType(), function.parameters().stream().map(Parameter::type).toList(), function.variadic()),
                    null, function.hasBody()));
        }

        Result run() {
            bindDeclarations(source.declarations(), root);
            while(true) {
                List<Entity> pending=requestedFunctionTemplates.stream().filter(e->!emittedFunctionTemplates.contains(e)).toList();
                if(pending.isEmpty())break;
                int before=emittedFunctionTemplates.size();
                for(Entity entity:pending)instantiateFunctionTemplate(entity);
                if(before==emittedFunctionTemplates.size()) {
                    for(Entity entity:pending)report("CPP004",functionTemplateInstances.get(entity).definition.source.range(),"Used function template has no definition: "+entity.name);
                    break;
                }
            }
            for (StaticField field : staticFields.values()) if (!field.entity.defined)
                for (SourceRange use : field.uses) report("CPP004", use,
                        "ODR-used static data member has no definition: " + field.owner.canonicalName + "::" + field.entity.name);
            // The compatibility constructor deliberately produces a core C Program.
            Program core = mapped(source, new Program(structs, enums, typedefs, globals, functions,
                    declarations, minic.compiler.LanguageMode.C, staticLifetime.finish(), source.range()));
            return new Result(core, diagnostics, origins, displayNames);
        }

        private void bindDeclarations(List<Declaration> input, Namespace namespace) {
            for (Declaration declaration : input) {
                switch (declaration) {
                    case ClassTemplateDecl node -> declareTemplate(node, namespace);
                    case FunctionTemplateDecl node -> declareFunctionTemplate(node,namespace);
                    case InternalLinkageDecl node -> {
                        boolean saved = internalDeclaration;
                        internalDeclaration = true;
                        try { bindDeclarations(List.of(node.declaration()), namespace); }
                        finally { internalDeclaration = saved; }
                    }
                    case NamespaceDecl node -> {
                        Namespace target = namespace;
                        for (String name : node.name().segments()) {
                            if (target.values.containsKey(name) || target.typedefs.containsKey(name) || target.tags.containsKey(name)) {
                                report("CPP004", node.range(), "命名空间与已有声明冲突：" + name);
                            }
                            Namespace parent = target;
                            target = target.children.computeIfAbsent(name, ignored -> new Namespace(parent, name));
                        }
                        bindDeclarations(node.declarations(), target);
                    }
                    case UsingDecl node -> bindUsing(node, namespace, null);
                    case GlobalVarDecl node -> bindGlobal(node, namespace);
                    case FunctionDecl node -> bindFunction(node, namespace);
                    case OutOfLineMethodDecl node -> bindOutOfLineMethod(node, namespace);
                    case OutOfLineStaticFieldDecl node -> bindStaticFieldDefinition(node, namespace);
                    case OutOfLineConstructorDecl node -> bindOutOfLineConstructor(node, namespace);
                    case OutOfLineDestructorDecl node -> bindOutOfLineDestructor(node, namespace);
                    case StructDecl node -> bindStruct(node, namespace);
                    case TypedefDecl node -> {
                        MiniType type = normalizeType(node.type(), namespace, null, node.range());
                        declareTypedef(node.name(), type, namespace, null, node.range());
                        String name = namespace == root ? node.name() : freshName(namespace.qualify(node.name()));
                        TypedefDecl core = mapped(node, new TypedefDecl(name, coreType(type), node.range()));
                        typedefs.add(core); declarations.add(core);
                    }
                    case EnumDecl node -> {
                        if (rejectNamespaceType(namespace, node)) continue;
                        if (namespace.children.containsKey(node.name())) report("CPP004", node.range(),
                                "类型声明与命名空间冲突：" + node.name());
                        // Existing unscoped root enums keep their C representation in this slice.
                        namespace.tags.put(node.name(), new TypeEntity(node.name(), "::" + namespace.qualify(node.name()),
                                MiniType.INT, false, false, namespace, true));
                        for (Enumerator item : node.enumerators()) {
                            if (namespace.values.containsKey(item.name()) || namespace.children.containsKey(item.name())) {
                                report("CPP004", item.range(), "枚举名称重复或冲突：" + item.name());
                            } else {
                                namespace.values.put(item.name(), new Entity(item.name(), item.name(), Kind.ENUM_CONSTANT,
                                        namespace, MiniType.INT, item.value(), true));
                            }
                            mapped(item, item);
                        }
                        enums.add(node); declarations.add(node); mapped(node, node);
                    }
                    default -> report("CPP005", declaration.range(), "尚未支持此 C++ 声明：" + declaration.getClass().getSimpleName());
                }
            }
        }

        private boolean rejectNamespaceType(Namespace namespace, Declaration node) {
            if (namespace == root) return false;
            report("CPP005", node.range(), "尚未支持命名空间内的类型声明；类型名称绑定将在后续实现。");
            return true;
        }


        private void declareFunctionTemplate(FunctionTemplateDecl declaration,Namespace namespace) {
            FunctionDecl function=declaration.function();
            if(declaration.parameters().isEmpty()) {report("CPP005",declaration.range(),"Explicit function template specialization requires a primary template-id");return;}
            Candidate prior=namespace.values.get(function.name());
            if(namespace.children.containsKey(function.name())||namespace.typedefs.containsKey(function.name())) {
                report("CPP004",declaration.range(),"Function template conflicts with a namespace or type name");return;
            }
            if(prior!=null&&!(prior instanceof OverloadSet)) {report("CPP004",declaration.range(),"Function template conflicts with a non-function declaration");return;}
            var visible=new ArrayList<Entity>(prior instanceof OverloadSet set?set.functions:List.of());
            var signature=MiniType.function(function.returnType(),function.parameters().stream().map(Parameter::type).toList(),function.variadic());
            for(Entity previous:visible) {
                FunctionTemplateDefinition old=functionTemplates.get(previous);
                if(old==null||!sameFunctionTemplate(old,declaration.parameters(),function))continue;
                if(old.source.hasDefinition()&&function.hasDefinition())report("CPP004",declaration.range(),"Function template is defined more than once");
                else if(function.hasDefinition())functionTemplates.put(previous,new FunctionTemplateDefinition(declaration.parameters(),function,namespace,null,null,null,null,snapshotLookup()));
                return;
            }
            Entity entity=new Entity(function.name(),freshName(namespace.qualify(function.name())),Kind.FUNCTION,namespace,signature,null,function.hasDefinition());
            visible.add(entity);namespace.values.put(function.name(),new OverloadSet(visible));
            functionTemplates.put(entity,new FunctionTemplateDefinition(declaration.parameters(),function,namespace,null,null,null,null,snapshotLookup()));
            validateFunctionTemplateNames(function,namespace);
        }
        private boolean sameFunctionTemplate(FunctionTemplateDefinition old,List<ClassTemplateDecl.Parameter> parameters,FunctionDecl source) {
            if(old.parameters.size()!=parameters.size()||old.source.variadic()!=source.variadic())return false;
            var types=new LinkedHashMap<MiniType.TemplateParameterType,MiniType>();
            var values=new LinkedHashMap<MiniType.TemplateParameterType,Expression>();
            for(int i=0;i<parameters.size();i++) {
                var from=old.parameters.get(i);var to=parameters.get(i);
                if(from.getClass()!=to.getClass())return false;
                if(from instanceof ClassTemplateDecl.TypeParameter)types.put(from.type(),to.type());
                else values.put(from.type(),new CppTemplateValueExpr(to.type(),((ClassTemplateDecl.ValueParameter)to).valueType(),to.range()));
            }
            return old.source.parameters().stream().map(p->p.type().substituteTemplateParameters(types,values)).toList()
                    .equals(source.parameters().stream().map(Parameter::type).toList());
        }
        private void validateFunctionTemplateNames(FunctionDecl function,Namespace namespace) {
            if(function.body()==null)return;
            Local scope=new Local(null,namespace);Set<String> dependent=new HashSet<>();
            for(Parameter parameter:function.parameters()) {
                scope.values.put(parameter.name(),new Entity(parameter.name(),parameter.name(),Kind.VARIABLE,null,parameter.type(),null,true));
                if(parameter.type().isDependentTemplate())dependent.add(parameter.name());
            }
            validateTemplateNames(function.body(),scope,dependent);
        }
        private void declareMemberTemplate(TypeEntity owner,List<ClassTemplateDecl.Parameter> parameters,
                                           MethodMember method,ConstructorMember constructor,Access access) {
            FunctionDecl source=method!=null?method.method():new FunctionDecl(owner.name,MiniType.VOID,constructor.parameters(),constructor.variadic(),constructor.body(),false,constructor.range()).withDefinitionKind(constructor.definitionKind());
            if(source.definitionKind()==DefinitionKind.DEFAULTED){report("CPP004",source.range(),"A function template cannot be a defaulted special member");return;}
            List<MiniType> argumentTypes=source.parameters().stream().map(Parameter::type).toList();
            var abi=new ArrayList<MiniType>();
            if(method==null||!method.staticMember())abi.add(method==null?owner.type.pointerTo():methodThisType(owner,method));
            abi.addAll(argumentTypes);
            Entity entity=new Entity(source.name(),freshName(owner.canonicalName+"::"+source.name()),Kind.FUNCTION,owner.owner,
                    MiniType.function(source.returnType(),abi,source.variadic()),null,source.hasDefinition());
            functionTemplates.put(entity,new FunctionTemplateDefinition(parameters,source,owner.owner,owner,method,constructor,access,currentTemplateLookup==null?snapshotLookup():currentTemplateLookup));
            if(method!=null) {
                var previous=owner.methods.get(source.name());
                var overloads=new ArrayList<Method>(previous==null?List.of():previous.methods);
                overloads.add(new Method(owner,method,access,entity,source.returnType(),argumentTypes));
                owner.methods.put(source.name(),new MethodSet(overloads));
            } else owner.constructors.add(new Constructor(owner,constructor,access,entity,argumentTypes,false));
        }
        private List<TemplateArgument> normalizeExplicitArguments(List<TemplateArgument> source,Namespace namespace,Local local,SourceRange range) {
            return source.stream().map(argument->argument instanceof TemplateArgument.Type type
                    ?(TemplateArgument)new TemplateArgument.Type(normalizeType(type.type(),namespace,local,range))
                    :argument instanceof TemplateArgument.Value value?new TemplateArgument.Value(expression(value.expression(),namespace,local)):argument).toList();
        }
        private MiniType functionTemplatePattern(MiniType type,Namespace namespace,SourceRange range) {
            if(!type.isDependentTemplate())return normalizeType(type,namespace,null,range);
            if(type instanceof MiniType.PointerType pointer)return functionTemplatePattern(pointer.pointee(),namespace,range).pointerTo();
            if(type instanceof MiniType.ReferenceType reference)return functionTemplatePattern(reference.referent(),namespace,range).referenceTo(reference.kind());
            if(type instanceof MiniType.QualifiedType qualified)return MiniType.qualified(functionTemplatePattern(qualified.baseType(),namespace,range),qualified.qualifiers());
            if(type instanceof MiniType.ArrayType array)return functionTemplatePattern(array.elementType(),namespace,range).arrayOf(array.length());
            if(type instanceof MiniType.DependentArrayType array)return new MiniType.DependentArrayType(functionTemplatePattern(array.elementType(),namespace,range),array.bound());
            if(type instanceof MiniType.FunctionType function)return MiniType.function(functionTemplatePattern(function.returnType(),namespace,range),function.parameterTypes().stream().map(p->functionTemplatePattern(p,namespace,range)).toList(),function.variadic());
            return type;
        }
        private Entity deduceFunctionTemplate(Entity declaration,List<MiniType> actual,List<TemplateArgument> explicit,SourceRange range) {
            return deduceFunctionTemplateShapes(declaration,actual.stream().map(t->t==null?null:
                    new CppOverloadResolver.Argument(t,CppValueCategory.PRVALUE,false)).toList(),explicit,range);
        }
        private Entity deduceFunctionTemplateShapes(Entity declaration,List<CppOverloadResolver.Argument> actual,List<TemplateArgument> explicit,SourceRange range) {
            FunctionTemplateDefinition definition=functionTemplates.get(declaration);
            if(definition==null)return explicit==null?declaration:null;
            int required=definition.source.parameters().size();
            while(required>0&&definition.source.parameters().get(required-1).defaultValue()!=null)required--;
            if(actual.size()<required||!definition.source.variadic()&&actual.size()>definition.source.parameters().size())return null;
            var savedLookup=currentTemplateLookup;TypeEntity savedClass=currentClass;Entity savedThis=currentThis;
            currentTemplateLookup=definition.lookup;currentClass=definition.record;currentThis=null;
            int errors=diagnostics.size();
            try {
                List<MiniType> pattern=definition.source.parameters().stream().map(p->functionTemplatePattern(p.type(),definition.owner,p.range())).toList();
                var bindings=CppFunctionTemplateDeduction.deduceShapes(definition.parameters,pattern,actual,explicit==null?List.of():explicit,this::expandTemplateType,
                        t->normalizeType(t,definition.owner,null,range),e->evaluateTemplateConstant(expression(e,definition.owner,null)),this::initializerListElementPattern);
                if(bindings==null)return null;
                var arguments=new ArrayList<TemplateArgument>();
                for(var parameter:definition.parameters)arguments.add(parameter instanceof ClassTemplateDecl.TypeParameter
                        ?new TemplateArgument.Type(normalizeType(bindings.types().get(parameter.type()),definition.owner,null,range)):bindings.values().get(parameter.type()));
                var key=new FunctionTemplateKey(declaration,List.copyOf(arguments));
                Entity existing=functionTemplateCache.get(key);if(existing!=null)return existing;
                CppTemplateSubstitution substitution=functionSubstitution(definition,bindings);
                FunctionDecl original=definition.source;
                FunctionDecl header=new FunctionDecl(original.name(),original.returnType(),original.parameters(),original.variadic(),null,
                        original.external(),original.noReturn(),original.range(),original.operatorName(),original.conversionName(),original.definitionKind());
                FunctionDecl instance=substitution.instantiate(header);
                List<MiniType> parameters=instance.parameters().stream().map(p->normalizeType(p.type(),definition.owner,null,p.range())).toList();
                MiniType result=normalizeReturnType(instance.returnType(),instance.parameters(),parameters,definition.owner,definition.record,definition.method,instance.range());
                if(result.containsTemplateType()||parameters.stream().anyMatch(MiniType::containsTemplateType)||diagnostics.size()!=errors)return null;
                var abi=new ArrayList<MiniType>();
                if(definition.constructor!=null)abi.add(definition.record.type.pointerTo());
                else if(definition.method!=null&&!definition.method.staticMember())abi.add(methodThisType(definition.record,definition.method));
                parameters.stream().map(MiniType::unqualified).forEach(abi::add);
                String display=(definition.record==null?definition.owner.qualify(original.name()):definition.record.canonicalName+"::"+original.name())
                        +"<"+String.join(",",arguments.stream().map(Object::toString).toList())+">";
                Entity entity=new Entity(original.name(),freshName(display),Kind.FUNCTION,definition.owner,
                        MiniType.function(result,abi,original.variadic()),null,original.hasDefinition());
                Method method=definition.method==null?null:new Method(definition.record,
                        new MethodMember(instance,definition.method.constQualified(),definition.method.staticMember(),definition.method.nameRange()),definition.access,entity,result,parameters);
                Constructor constructor=null;
                if(definition.constructor!=null) {
                    var originalConstructor=definition.constructor;
                    var member=new ConstructorMember(originalConstructor.name(),instance.parameters(),originalConstructor.variadic(),List.of(),null,
                            originalConstructor.nameRange(),originalConstructor.range(),originalConstructor.explicitSpecifier(),originalConstructor.definitionKind());
                    constructor=new Constructor(definition.record,member,definition.access,entity,parameters,false);
                }
                registerSpecialDefinition(entity,instance.definitionKind(),instance.range());
                functionTemplateCache.put(key,entity);functionTemplateDeclarations.put(entity,declaration);coreValues.put(entity.coreName,entity);
                functionTemplateInstances.put(entity,new FunctionTemplateInstance(definition,bindings,instance,method,constructor));
                recordDefaultArguments(entity,instance.parameters(),definition.owner,definition.record);
                // A prototype permits recursion. The selected body is still instantiated lazily.
                declareTemplatePrototype(entity,range);
                return entity;
            } catch(IllegalArgumentException error) {return null;}
            finally {
                // Only signature substitution is an immediate context. Body errors are never caught here.
                diagnostics.subList(errors,diagnostics.size()).clear();
                currentTemplateLookup=savedLookup;currentClass=savedClass;currentThis=savedThis;
            }
        }
        private CppTemplateSubstitution functionSubstitution(FunctionTemplateDefinition definition,CppTemplateDeduction.Bindings bindings) {
            String owner=definition.record==null?"::"+definition.owner.qualify(definition.source.name()):definition.record.canonicalName;
            return new CppTemplateSubstitution(bindings.types(),CppFunctionTemplateDeduction.expressions(bindings.values(),definition.parameters),owner,owner);
        }
        private void instantiateFunctionTemplate(Entity entity) {
            var instance=functionTemplateInstances.get(entity);
            if(instance==null||unevaluatedDepth>0&&!((MiniType.FunctionType)entity.type).returnType().containsAuto()||emittedFunctionTemplates.contains(entity))return;
            requestedFunctionTemplates.add(entity);
            var definition=functionTemplates.getOrDefault(functionTemplateDeclarations.get(entity),instance.definition);
            if(definition.source.definitionKind()==DefinitionKind.DELETED){deletedFunctions.add(entity);return;}
            if(!definition.source.hasBody())return;
            if(functionTemplateDepth>=128){report("CPP004",definition.source.range(),"Function template instantiation depth exceeds 128");emittedFunctionTemplates.add(entity);return;}
            var bindings=instance.bindings;
            if(definition!=instance.definition) {
                var types=new LinkedHashMap<MiniType.TemplateParameterType,MiniType>();var values=new LinkedHashMap<MiniType.TemplateParameterType,TemplateArgument>();
                for(int index=0;index<definition.parameters.size();index++) {
                    var old=instance.definition.parameters.get(index);var current=definition.parameters.get(index);
                    if(current instanceof ClassTemplateDecl.TypeParameter)types.put(current.type(),bindings.types().get(old.type()));
                    else values.put(current.type(),bindings.values().get(old.type()));
                }
                bindings=new CppTemplateDeduction.Bindings(types,values);
            }
            emittedFunctionTemplates.add(entity);functionTemplateDepth++;
            int savedUnevaluatedDepth=unevaluatedDepth;unevaluatedDepth=0;
            var savedLookup=currentTemplateLookup;TypeEntity savedClass=currentClass;Entity savedThis=currentThis;
            currentTemplateLookup=definition.lookup;currentClass=definition.record;currentThis=null;
            try {
                var substitution=functionSubstitution(definition,bindings);
                if(instance.constructor!=null) {
                    ConstructorMember source=substitution.instantiate(definition.constructor);
                    templateOrigins.putAll(substitution.origins());
                    bindConstructor(new Constructor(definition.record,source,definition.access,entity,instance.constructor.parameterTypes,false));
                } else {
                    FunctionDecl source=substitution.instantiate(definition.source);
                    templateOrigins.putAll(substitution.origins());
                    if(instance.method!=null)bindMethod(new Method(definition.record,
                            new MethodMember(source,definition.method.constQualified(),definition.method.staticMember(),definition.method.nameRange()),
                            definition.access,entity,instance.method.returnType,instance.method.parameterTypes),definition.owner);
                    else bindFunction(source,definition.owner,entity);
                }
            } catch(IllegalArgumentException error){report("CPP004",definition.source.range(),"Cannot instantiate selected function template: "+error.getMessage());}
            finally {currentTemplateLookup=savedLookup;currentClass=savedClass;currentThis=savedThis;functionTemplateDepth--;unevaluatedDepth=savedUnevaluatedDepth;}
        }
        private Entity deduceFunctionTemplateForTarget(Entity declaration,MiniType.FunctionType target,List<TemplateArgument> explicit,SourceRange range) {
            FunctionTemplateDefinition definition=functionTemplates.get(declaration);
            if(definition==null)return explicit==null?declaration:null;
            if(explicit!=null&&!explicit.isEmpty())return deduceFunctionTemplate(declaration,target.parameterTypes(),explicit,range);
            var pattern=new ArrayList<MiniType>();pattern.add(definition.source.returnType());pattern.addAll(definition.source.parameters().stream().map(Parameter::type).toList());
            var actual=new ArrayList<MiniType>();actual.add(target.returnType());actual.addAll(target.parameterTypes());
            var bindings=CppTemplateDeduction.deduce(pattern,actual,definition.parameters,Map.of(),Map.of(),this::expandTemplateType);
            if(bindings==null)return null;
            var arguments=new ArrayList<TemplateArgument>();
            for(var parameter:definition.parameters) {
                TemplateArgument argument=parameter instanceof ClassTemplateDecl.TypeParameter?new TemplateArgument.Type(bindings.types().getOrDefault(parameter.type(),parameter.type())):bindings.values().get(parameter.type());
                if(argument==null||argument instanceof TemplateArgument.Type type&&type.type().isDependentTemplate())return null;
                arguments.add(argument);
            }
            return deduceFunctionTemplate(declaration,target.parameterTypes(),arguments,range);
        }
        private List<Entity> expandFunctionTemplates(java.util.Collection<Entity> candidates,List<Expression> values,List<TemplateArgument> explicit,SourceRange range) {
            List<CppOverloadResolver.Argument> types=values.stream().map(value->value==null?null:argumentShape(value,value)).toList();
            var result=new ArrayList<Entity>();
            for(Entity candidate:candidates){Entity concrete=deduceFunctionTemplateShapes(candidate,types,explicit,range);if(concrete!=null)result.add(concrete);}
            return result;
        }
        private List<Method> expandMethodTemplates(List<Method> candidates,List<Expression> values,List<TemplateArgument> explicit,SourceRange range) {
            List<CppOverloadResolver.Argument> types=values.stream().map(value->value==null?null:argumentShape(value,value)).toList();
            var result=new ArrayList<Method>();
            for(Method candidate:candidates){Entity concrete=deduceFunctionTemplateShapes(candidate.function,types,explicit,range);
                if(concrete!=null)result.add(concrete==candidate.function?candidate:functionTemplateInstances.get(concrete).method);}
            return result;
        }
        private List<Constructor> expandConstructorTemplates(List<Constructor> candidates,List<Expression> values,SourceRange range) {
            List<CppOverloadResolver.Argument> types=values.stream().map(value->value==null?null:argumentShape(value,value)).toList();
            return expandConstructorTemplateShapes(candidates,types,range);
        }
        private List<Constructor> expandConstructorTemplateShapes(List<Constructor> candidates,List<CppOverloadResolver.Argument> types,SourceRange range) {
            var result=new ArrayList<Constructor>();
            for(Constructor candidate:candidates){Entity concrete=deduceFunctionTemplateShapes(candidate.function,types,null,range);
                if(concrete!=null)result.add(concrete==candidate.function?candidate:functionTemplateInstances.get(concrete).constructor);}
            return result;
        }
        private Entity templateCandidateEntity(Object candidate) {
            return candidate instanceof Entity e?e:candidate instanceof Method m?m.function:candidate instanceof Constructor c?c.function
                    :candidate instanceof OperatorCandidate operator?operator.function:null;
        }
        private boolean betterTemplateCandidate(Object first,Object second) {
            FunctionTemplateInstance a=functionTemplateInstances.get(templateCandidateEntity(first)),b=functionTemplateInstances.get(templateCandidateEntity(second));
            if(a==null||b==null)return a==null&&b!=null;
            var pa=a.definition.source.parameters().stream().map(Parameter::type).map(t->t.isReference()?t.referent():t.unqualified()).toList();
            var pb=b.definition.source.parameters().stream().map(Parameter::type).map(t->t.isReference()?t.referent():t.unqualified()).toList();
            boolean acceptsA=CppTemplateDeduction.deduce(pb,pa,b.definition.parameters,Map.of(),Map.of(),this::expandTemplateType)!=null;
            boolean acceptsB=CppTemplateDeduction.deduce(pa,pb,a.definition.parameters,Map.of(),Map.of(),this::expandTemplateType)!=null;
            return acceptsA&&!acceptsB;
        }

        private void declareTemplate(ClassTemplateDecl node, Namespace namespace) {
            if(!node.specializationArguments().isEmpty()) {
                if(!templates.containsKey(node.record().name())){report("CPP003",node.range(),"Template specialization needs a primary declaration");return;}
                var specializations=templateSpecializations.computeIfAbsent(node.record().name(),ignored->new ArrayList<>());
                var definition=new TemplateDefinition(node,namespace,snapshotLookup());
                var pattern=templatePattern(definition);
                var primary=templates.get(node.record().name());
                if(!node.parameters().isEmpty() && CppTemplateDeduction.match(templatePattern(primary),pattern,primary.source.parameters(),this::expandTemplateType)!=null
                        && CppTemplateDeduction.match(pattern,templatePattern(primary),node.parameters(),this::expandTemplateType)!=null) {
                    report("CPP004",node.range(),"Partial specialization must specialize the primary arguments");return;
                }
                if(!node.parameters().isEmpty() && CppTemplateDeduction.match(pattern,pattern,node.parameters(),this::expandTemplateType)==null) {
                    report("CPP004",node.range(),"Partial specialization has undeducible parameters");return;
                }
                for(int index=0;index<specializations.size();index++) {
                    var old=specializations.get(index);
                    if(CppTemplateDeduction.match(templatePattern(old),pattern,old.source.parameters(),this::expandTemplateType)!=null
                            &&CppTemplateDeduction.match(pattern,templatePattern(old),node.parameters(),this::expandTemplateType)!=null) {
                        if(old.source.record().definition()&&node.record().definition())report("CPP004",node.range(),"Duplicate template specialization");
                        else if(node.record().definition())specializations.set(index,definition);
                        return;
                    }
                }
                for(var entry:templateInstances.entrySet())if(entry.getKey().templateName().equals(node.record().name())&&entry.getValue().complete
                        &&CppTemplateDeduction.match(pattern,entry.getKey().arguments(),node.parameters(),this::expandTemplateType)!=null)
                    report("CPP004",node.range(),"Template specialization appears after instantiation");
                specializations.add(definition);
                if(node.record().definition())validateTemplateNames(node.record(),namespace);
                return;
            }
            TemplateDefinition previous = templates.get(node.record().name());
            if (previous != null && previous.source.record().definition() && node.record().definition()) {
                report("CPP004", node.range(), "Duplicate class template definition: " + node.record().name());
                return;
            }
            if (previous == null || node.record().definition())
                templates.put(node.record().name(), new TemplateDefinition(node, namespace, snapshotLookup()));
            if (node.record().definition()) validateTemplateNames(node.record(), namespace);
        }

        /** Nondependent names are checked even when no specialization is requested. */
        private void validateTemplateNames(StructDecl record, Namespace namespace) {
            if (record.cppInfo() == null) return;
            Set<String> memberNames = new HashSet<>();
            Set<String> dependent = new HashSet<>();
            for (CppMember member : record.cppInfo().members()) {
                if (member instanceof FieldMember field) {
                    memberNames.add(field.field().name());
                    if (field.field().type().containsTemplateType()) dependent.add(field.field().name());
                } else if (member instanceof StaticFieldMember field) {
                    memberNames.add(field.declaration().name());
                    if (field.declaration().type().containsTemplateType()) dependent.add(field.declaration().name());
                } else if (member instanceof MethodMember method) memberNames.add(method.method().name());
                else if(member instanceof TemplateMethodMember method)memberNames.add(method.method().method().name());
            }
            for (CppMember originalMember : record.cppInfo().members()) {
                CppMember member=originalMember instanceof TemplateMethodMember m?m.method():originalMember instanceof TemplateConstructorMember c?c.constructor():originalMember;
                List<Parameter> parameters = member instanceof MethodMember method ? method.method().parameters()
                        : member instanceof ConstructorMember constructor ? constructor.parameters() : List.of();
                Local scope = new Local(null, namespace);
                for (String name : memberNames) scope.values.put(name,
                        new Entity(name, name, Kind.VARIABLE, null, MiniType.INT, null, true));
                Set<String> dependentNames = new HashSet<>(dependent);
                for (Parameter parameter : parameters) {
                    if (parameter.name().isEmpty()) continue;
                    scope.values.put(parameter.name(), new Entity(parameter.name(), parameter.name(), Kind.VARIABLE, null, parameter.type(), null, true));
                    if (parameter.type().containsTemplateType()) dependentNames.add(parameter.name());
                }
                for (AstNode child : AstChildren.of(member)) validateTemplateNames(child, scope, dependentNames);
            }
        }

        private boolean dependentTemplateExpression(AstNode node, Set<String> names) {
            if (node instanceof ThisExpr || node instanceof CppTemplateValueExpr) return true;
            if (node instanceof CppTypeQueryExpr query && query.arguments().stream().anyMatch(argument -> argument.type().containsTemplateType())) return true;
            if(node instanceof CppTemplateIdExpr id && id.arguments().stream().anyMatch(a->a instanceof TemplateArgument.Type t?t.type().isDependentTemplate():a instanceof TemplateArgument.Value v&&TemplateValues.dependent(v.expression())))return true;
            if (node instanceof CppTypeMemberExpr member && member.ownerType().containsTemplateType()) return true;
            if (node instanceof NameExpr name && names.contains(name.name())) return true;
            if (node instanceof CppConstructionExpr construction && construction.type().containsTemplateType()) return true;
            for (AstNode child : AstChildren.of(node)) if (dependentTemplateExpression(child, names)) return true;
            return false;
        }

        private void validateTemplateNames(AstNode node, Local scope, Set<String> dependent) {
            if (node instanceof BlockStmt block) {
                Local nested = new Local(scope, scope.namespace);
                Set<String> names = new HashSet<>(dependent);
                for (Statement statement : block.statements()) validateTemplateNames(statement, nested, names);
                return;
            }
            if (node instanceof ForStmt loop) {
                Local nested = new Local(scope, scope.namespace);
                Set<String> names = new HashSet<>(dependent);
                for (AstNode child : AstChildren.of(loop)) validateTemplateNames(child, nested, names);
                return;
            }
            if (node instanceof VarDeclStmt variable) {
                scope.values.put(variable.name(), new Entity(variable.name(), variable.name(), Kind.VARIABLE, null, variable.type(), null, true));
                if (variable.type().containsTemplateType()) dependent.add(variable.name());
            } else if (node instanceof TypedefStmt alias) {
                scope.typedefs.put(alias.name(), new TypeEntity(alias.name(), alias.name(), alias.type(), alias.type().isStruct(), false, scope.namespace, true));
            } else if (node instanceof UsingDecl using) {
                bindUsing(using, scope.namespace, scope);
            } else if (node instanceof NameExpr name) {
                lookupName(name.name(), scope.namespace, scope, name.range());
            } else if (node instanceof QualifiedNameExpr name) {
                resolveQualifiedName(name.name(), scope.namespace, scope);
            } else if (node instanceof CallExpr call && call.callee() instanceof NameExpr
                    && call.arguments().stream().anyMatch(argument -> dependentTemplateExpression(argument, dependent))) {
                // Dependent unqualified calls retain their syntax for instantiation/ADL.
                for (Expression argument : call.arguments()) validateTemplateNames(argument, scope, dependent);
                return;
            }
            for (AstNode child : AstChildren.of(node)) validateTemplateNames(child, scope, dependent);
        }

        private Map<Namespace, NamespaceView> snapshotLookup() {
            Map<Namespace, NamespaceView> result = new IdentityHashMap<>();
            var pending = new java.util.ArrayDeque<Namespace>(); pending.add(root);
            while (!pending.isEmpty()) {
                Namespace namespace = pending.removeFirst();
                if (result.containsKey(namespace)) continue;
                result.put(namespace, new NamespaceView(Map.copyOf(namespace.values), Map.copyOf(namespace.children),
                        Map.copyOf(namespace.typedefs), Map.copyOf(namespace.tags), List.copyOf(namespace.directives)));
                pending.addAll(namespace.children.values());
            }
            return result;
        }

        private NamespaceView visible(Namespace namespace) {
            if (currentTemplateLookup == null)
                return new NamespaceView(namespace.values, namespace.children, namespace.typedefs, namespace.tags, namespace.directives);
            return currentTemplateLookup.getOrDefault(namespace, new NamespaceView(Map.of(), Map.of(), Map.of(), Map.of(), List.of()));
        }

        private MiniType templateType(MiniType.TemplateIdType source, Namespace namespace, Local local, SourceRange range) {
            TemplateDefinition template = templates.get(source.templateName());
            if (template == null) {
                report("CPP003", range, "Class template is not declared: " + source.templateName());
                return MiniType.INT;
            }
            var arguments = new ArrayList<TemplateArgument>();
            Map<MiniType.TemplateParameterType,MiniType> typeArguments=new LinkedHashMap<>();
            if(source.arguments().size()!=template.source.parameters().size()) {
                report("CPP004",range,"Class template argument count mismatch");return MiniType.INT;
            }
            try {
                for(int index=0;index<source.arguments().size();index++) {
                    var parameter=template.source.parameters().get(index);
                    var sourceArgument=source.arguments().get(index);
                    if(parameter instanceof ClassTemplateDecl.TypeParameter) {
                        if(!(sourceArgument instanceof TemplateArgument.Type supplied))throw new IllegalArgumentException("Expected type template argument");
                        MiniType actual=normalizeType(supplied.type(),namespace,local,range);
                        if(actual.containsTemplateType())throw new IllegalArgumentException("Unsubstituted type template argument");
                        arguments.add(new TemplateArgument.Type(actual));typeArguments.put(parameter.type(),actual);
                    } else {
                        var valueParameter=(ClassTemplateDecl.ValueParameter)parameter;
                        MiniType target=normalizeType(valueParameter.valueType().substituteTemplateParameters(typeArguments),namespace,local,range);
                        var integral=sourceArgument instanceof TemplateArgument.Integral value?value:
                                sourceArgument instanceof TemplateArgument.Value value?evaluateTemplateConstant(expression(value.expression(),namespace,local)):null;
                        if(integral==null)throw new IllegalArgumentException("Expected integral template argument");
                        arguments.add(TemplateValues.convert(integral,target,true));
                    }
                }
            } catch(IllegalArgumentException error) {
                report("CPP004",range,"Invalid template argument: "+error.getMessage());return MiniType.INT;
            }
            var key = new MiniType.TemplateIdType(source.templateName(), arguments);
            TypeEntity existing = templateInstances.get(key);
            if (existing != null) return existing.type;
            String display = source.templateName().substring(2) + "<"
                    + String.join(", ", arguments.stream().map(argument -> argument instanceof TemplateArgument.Type t ? templateTypeDisplay(t.type()) : argument.toString()).toList()) + ">";
            String name = freshName(display);
            TypeEntity entity = declareClass(name, false, template.owner, range);
            templateInstances.put(key, entity);
            instanceKeys.put(entity, key);
            templateDisplay.put(entity.canonicalName.substring(2), display);
            templateDisplay.put(name, display.substring(display.lastIndexOf("::") + 2));
            displayNames.put(((MiniType.StructType) entity.type).name(), display);
            var forward = new StructDecl(((MiniType.StructType) entity.type).name(), List.of(), false, false, range);
            structs.add(forward); declarations.add(forward);
            return entity.type;
        }

        private String templateTypeDisplay(MiniType type) {
            if (type instanceof MiniType.StructType record) return displayNames.getOrDefault(record.name(), record.name());
            if (type instanceof MiniType.PointerType pointer) return templateTypeDisplay(pointer.pointee()) + "*";
            if (type instanceof MiniType.ReferenceType reference) return templateTypeDisplay(reference.referent()) + (reference.kind()==MiniType.ReferenceKind.RVALUE?"&&":"&");
            return type.toString();
        }

        private MiniType expandTemplateType(MiniType type) {
            if(type instanceof MiniType.StructType record) {
                TypeEntity entity=coreTypes.get(record.name());
                if(entity!=null && instanceKeys.containsKey(entity))return instanceKeys.get(entity);
            }
            return type;
        }

        private MiniType templatePatternType(MiniType type,TemplateDefinition definition) {
            if(type instanceof MiniType.StructType)return normalizeType(type,definition.owner,null,definition.source.range());
            if(type instanceof MiniType.PointerType pointer)return templatePatternType(pointer.pointee(),definition).pointerTo();
            if(type instanceof MiniType.ReferenceType reference)return templatePatternType(reference.referent(),definition).referenceTo(reference.kind());
            if(type instanceof MiniType.QualifiedType qualified)return MiniType.qualified(templatePatternType(qualified.baseType(),definition),qualified.qualifiers());
            if(type instanceof MiniType.ArrayType array)return templatePatternType(array.elementType(),definition).arrayOf(array.length());
            if(type instanceof MiniType.TemplateIdType id)return new MiniType.TemplateIdType(id.templateName(),id.arguments().stream().map(a->a instanceof TemplateArgument.Type t?(TemplateArgument)new TemplateArgument.Type(templatePatternType(t.type(),definition)):a).toList());
            return type;
        }
        private List<TemplateArgument> templatePattern(TemplateDefinition definition) {
            if(definition.source.specializationArguments().isEmpty())return definition.source.parameters().stream().map(p->p instanceof ClassTemplateDecl.TypeParameter
                    ?(TemplateArgument)new TemplateArgument.Type(p.type()):new TemplateArgument.Value(new CppTemplateValueExpr(p.type(),((ClassTemplateDecl.ValueParameter)p).valueType(),p.range()))).toList();
            return definition.source.specializationArguments().stream().map(a->a instanceof TemplateArgument.Type t
                    ?(TemplateArgument)new TemplateArgument.Type(templatePatternType(t.type(),definition)):a).toList();
        }
        private TemplateSelection selectTemplate(MiniType.TemplateIdType key,SourceRange range) {
            var matches=new ArrayList<TemplateSelection>();
            for(var candidate:templateSpecializations.getOrDefault(key.templateName(),List.of())) {
                var bindings=CppTemplateDeduction.match(templatePattern(candidate),key.arguments(),candidate.source.parameters(),this::expandTemplateType);
                if(bindings!=null)matches.add(new TemplateSelection(candidate,bindings));
            }
            if(matches.isEmpty()) {
                var primary=templates.get(key.templateName());
                var bindings=CppTemplateDeduction.match(templatePattern(primary),key.arguments(),primary.source.parameters(),this::expandTemplateType);
                if(bindings==null){report("CPP004",range,"Template parameters could not be deduced");return null;}
                return new TemplateSelection(primary,bindings);
            }
            var full=matches.stream().filter(m->m.definition.source.parameters().isEmpty()).toList();
            if(full.size()==1)return full.getFirst();
            if(full.size()>1){report("CPP004",range,"Ambiguous explicit class specialization");return null;}
            TemplateSelection best=null;
            for(var candidate:matches) {
                boolean dominates=true;
                for(var other:matches)if(other!=candidate) {
                    boolean otherAccepts=CppTemplateDeduction.match(templatePattern(other.definition),templatePattern(candidate.definition),other.definition.source.parameters(),this::expandTemplateType)!=null;
                    boolean candidateAccepts=CppTemplateDeduction.match(templatePattern(candidate.definition),templatePattern(other.definition),candidate.definition.source.parameters(),this::expandTemplateType)!=null;
                    if(!otherAccepts||candidateAccepts){dominates=false;break;}
                }
                if(dominates){best=candidate;break;}
            }
            if(best==null)report("CPP004",range,"Ambiguous class template partial specializations");
            return best;
        }

        private void completeTemplate(TypeEntity entity, SourceRange range) {
            MiniType.TemplateIdType key = instanceKeys.get(entity);
            if (key == null || entity.complete || instantiating.contains(entity)) return;
            TemplateSelection selection=selectTemplate(key,range);
            if(selection==null)return;
            TemplateDefinition definition=selection.definition();
            if (!definition.source.record().definition()) return;
            Map<MiniType.TemplateParameterType, MiniType> arguments = new LinkedHashMap<>(selection.bindings().types());
            Map<MiniType.TemplateParameterType,Expression> values=new LinkedHashMap<>();
            for(var entry:selection.bindings().values().entrySet()) {
                var argument=entry.getValue();
                if(argument instanceof TemplateArgument.Integral v)values.put(entry.getKey(),new IntegerConstantExpr(v.value(),v.type(),v.toString(),range));
                else if(argument instanceof TemplateArgument.Value v)values.put(entry.getKey(),v.expression());
            }
            CppTemplateSubstitution substitution = new CppTemplateSubstitution(arguments, values, definition.source.record().name(), entity.canonicalName);
            Map<Namespace, NamespaceView> saved = currentTemplateLookup;
            instantiating.add(entity);
            instanceLookup.put(entity, definition.lookup);
            currentTemplateLookup = definition.lookup;
            try {
                StructDecl instantiated = substitution.instantiate(definition.source.record());
                templateOrigins.putAll(substitution.origins());
                bindStruct(instantiated, definition.owner);
            } catch (IllegalArgumentException error) {
                report("CPP004", range, "Cannot instantiate " + templateTypeDisplay(entity.type) + ": " + error.getMessage());
            } finally {
                currentTemplateLookup = saved;
                instantiating.remove(entity);
            }
        }

        /** Empty tag hierarchies need source inheritance but no runtime layout or dispatch machinery. */
        private boolean markerRecord(StructDecl node) {
            return node!=null&&node.definition()&&!node.union()&&node.fields().isEmpty()
                    &&(node.cppInfo()==null||node.cppInfo().members().stream().allMatch(AccessLabel.class::isInstance));
        }
        private void bindMarkerBase(TypeEntity entity,StructDecl node,Namespace namespace) {
            if(node.cppInfo()==null||node.cppInfo().bases().isEmpty())return;
            if(node.cppInfo().bases().size()!=1||!markerRecord(node)) {
                report("CPP005",node.range(),"Inheritance currently requires a single public base and empty marker classes.");return;
            }
            CppBase base=node.cppInfo().bases().getFirst();
            if(base.virtualBase()||base.access()!=Access.PUBLIC) {
                report("CPP005",base.range(),"Only public non-virtual marker inheritance is supported.");return;
            }
            MiniType type=normalizeType(base.type(),namespace,null,base.range());
            TypeEntity parent=objectType(type);
            if(parent==null||parent==entity) {report("CPP004",base.range(),"A base must be a distinct complete class.");return;}
            completeTemplate(parent,base.range());
            if(!parent.complete){report("CPP004",base.range(),"A base class must be complete.");return;}
            if(!markerRecord(parent.sourceRecord)){report("CPP005",base.range(),"A marker base cannot contain members or user-defined operations.");return;}
            entity.markerBase=parent;
        }
        private int baseDistance(MiniType source,MiniType target) {
            TypeEntity from=objectType(source),to=objectType(target);
            if(from==null||to==null||from==to)return 0;
            int distance=0;
            for(TypeEntity parent=from.markerBase;parent!=null;parent=parent.markerBase) {
                ++distance;if(parent==to)return distance;
            }
            return 0;
        }
        private boolean standardViable(CppOverloadResolver.Argument source,MiniType target) {
            return CppOverloadResolver.standardViable(source,target,conversions);
        }
        private Expression markerBaseValue(MiniType target,Expression source,SourceRange range) {
            String destination=freshName("base_value");
            Expression slot=typed(new UnaryExpr(TokenType.STAR,typed(new NameExpr(destination,range),target.pointerTo()),range),target);
            Expression initialize=new InitializeExpr(slot,new AggregateInitExpr(List.of(),range),range);
            Expression action=new CommaExpr(List.of(source,initialize),range);
            return typed(new ObjectInitExpr(coreType(target),destination,action,range),target);
        }

        private void instantiateMethod(Method method) {
            if(defaultedAssignmentPlans.containsKey(method)){emitAssignment(method,defaultedAssignmentPlans.get(method));return;}
            if(method==method.owner.implicitAssignment){emitImplicitAssignment(method.owner);return;}
            if(method==method.owner.implicitMoveAssignment){emitImplicitMoveAssignment(method.owner);return;}
            if(functionTemplateInstances.containsKey(method.function)){instantiateFunctionTemplate(method.function);return;}
            if (unevaluatedDepth > 0 && !methodReturnType(method).containsAuto()) return;
            if (pendingTemplateMethods.remove(method.function) == null) return;
            Map<Namespace, NamespaceView> saved = currentTemplateLookup;
            currentTemplateLookup = instanceLookup.get(method.owner);
            try { bindMethod(method, method.owner.owner); }
            finally { currentTemplateLookup = saved; }
        }

        private void declareTemplateMethodPrototype(Method method) {
            if (methodReturnType(method).containsAuto()) return;
            FunctionDecl source = method.source.method();
            var parameters = new ArrayList<Parameter>();
            if (!method.source.staticMember()) parameters.add(new Parameter(freshName("this"), methodThisType(method.owner, method.source), method.source.nameRange()));
            for (int index = 0; index < source.parameters().size(); index++) {
                Parameter parameter = source.parameters().get(index);
                parameters.add(new Parameter(freshName(parameter.name()), coreType(method.parameterTypes.get(index)), parameter.range()));
            }
            var prototype = new FunctionDecl(method.function.coreName, coreType(methodReturnType(method)), parameters,
                    source.variadic(), null, false, source.noReturn(), source.range());
            functions.add(prototype); declarations.add(prototype);
        }

        private void declareTemplatePrototype(Entity function, SourceRange range) {
            MiniType.FunctionType signature = (MiniType.FunctionType) function.type;
            if(signature.returnType().containsAuto())return;
            var parameters = new ArrayList<Parameter>();
            for (int index = 0; index < signature.parameterTypes().size(); index++)
                parameters.add(new Parameter(freshName(index == 0 ? "this" : "argument" + index),
                        coreType(signature.parameterTypes().get(index)), range));
            var prototype = new FunctionDecl(function.coreName, coreType(signature.returnType()), parameters,
                    signature.variadic(), null, false, range);
            functions.add(prototype); declarations.add(prototype);
        }

        private void instantiateConstructor(Constructor constructor) {
            if(defaultedCopyPlans.containsKey(constructor)){emitTransfer(constructor,defaultedCopyPlans.get(constructor),false);return;}
            if(constructor==constructor.owner.implicitCopy){emitImplicitCopy(constructor.owner);return;}
            if(constructor==constructor.owner.implicitMove){emitImplicitMove(constructor.owner);return;}
            if(functionTemplateInstances.containsKey(constructor.function)){instantiateFunctionTemplate(constructor.function);return;}
            if (unevaluatedDepth > 0 || pendingTemplateConstructors.remove(constructor.function) == null) return;
            Map<Namespace, NamespaceView> saved = currentTemplateLookup;
            currentTemplateLookup = instanceLookup.get(constructor.owner);
            try { bindConstructor(constructor); }
            finally { currentTemplateLookup = saved; }
        }

        private void instantiateDestructor(Destructor destructor) {
            if (unevaluatedDepth > 0 || pendingTemplateDestructors.remove(destructor.function) == null) return;
            Map<Namespace, NamespaceView> saved = currentTemplateLookup;
            currentTemplateLookup = instanceLookup.get(destructor.owner);
            try { bindDestructor(destructor); }
            finally { currentTemplateLookup = saved; }
        }

        private void bindStruct(StructDecl node, Namespace namespace) {
            String sourceName = simpleTagName(node.name());
            if (node.name().contains("<block")) {
                report("CPP005", node.range(), "尚未支持具名局部类型声明的作用域保存。");
                return;
            }
            TypeEntity entity = declareClass(sourceName, node.union(), namespace, node.range());
            if (node.definition() && entity.complete) report("CPP004", node.range(), "重复类型定义：" + entity.canonicalName);
            Map<StructField, Access> access = new IdentityHashMap<>();
            Map<MethodMember, Access> methodAccess = new IdentityHashMap<>();
            Map<StaticFieldMember, Access> staticAccess = new IdentityHashMap<>();
            Map<ConstructorMember, Access> constructorAccess = new IdentityHashMap<>();
            Map<DestructorMember, Access> destructorAccess = new IdentityHashMap<>();
            Map<CppMember,Access> templateAccess=new IdentityHashMap<>();
            if (node.cppInfo() != null) {
                Access current = node.cppInfo().key() == RecordKey.CLASS ? Access.PRIVATE : Access.PUBLIC;
                for (CppMember member : node.cppInfo().members()) {
                    if (member instanceof AccessLabel label) current = label.access();
                    else if (member instanceof FieldMember field) {
                        access.put(field.field(), current);
                    }
                    else if (member instanceof MemberTypedef alias) {
                        MiniType type=normalizeType(alias.declaration().type(),namespace,null,alias.range());
                        entity.memberTypes.put(alias.declaration().name(),type);
                        entity.memberTypeAccess.put(alias.declaration().name(),current);
                    }
                    else if (member instanceof StaticFieldMember field) staticAccess.put(field, current);
                    else if (member instanceof MethodMember method) methodAccess.put(method, current);
                    else if (member instanceof ConstructorMember constructor) constructorAccess.put(constructor, current);
                    else if (member instanceof DestructorMember destructor) destructorAccess.put(destructor, current);
                    else if(member instanceof TemplateMethodMember || member instanceof TemplateConstructorMember)templateAccess.put(member,current);
                    else report("CPP005", member.range(), "This C++ record member is not supported yet: " + member.getClass().getSimpleName());
                }
            }
            List<StructField> fields = new ArrayList<>();
            List<StructField> sourceFields = new ArrayList<>();
            for (StructField field : node.fields()) {
                MiniType type = normalizeType(field.type(), namespace, null, field.range());
                requireComplete(type, field.range());
                StructField coreField = mapped(field, new StructField(field.name(), coreType(type), field.anonymous(),
                        normalizeAlignments(field.alignmentSpecs(), namespace, null), field.range()));
                fields.add(coreField);
                // Member lookup retains source callable signatures, including references
                // nested inside callback fields; layout receives only pointer ABI types.
                StructField sourceField = new StructField(field.name(), type, field.anonymous(),
                        coreField.alignmentSpecs(), field.range());
                sourceFields.add(sourceField);
                if (node.definition()) entity.fieldAccess.put(sourceField, access.getOrDefault(field, Access.PUBLIC));
            }
            StructDecl core = mapped(node, new StructDecl(((MiniType.StructType) entity.type).name(),
                    fields, node.definition(), node.union(), node.range()));
            entity.complete |= node.definition();
            if (node.definition()) {
                entity.fields = List.copyOf(sourceFields);
                entity.sourceRecord = node;
                bindMarkerBase(entity,node,namespace);
            }
            structs.add(core); declarations.add(core);
            if (node.definition()) {
                List<CppMember> members = node.cppInfo() == null ? List.of() : node.cppInfo().members();
                List<Method> methods = new ArrayList<>();
                List<Constructor> constructors = new ArrayList<>();
                Destructor destructor = null;
                for (CppMember member : members) {
                    if(member instanceof TemplateMethodMember method) {
                        declareMemberTemplate(entity,method.parameters(),method.method(),null,templateAccess.get(member));
                    } else if(member instanceof TemplateConstructorMember constructor) {
                        declareMemberTemplate(entity,constructor.parameters(),null,constructor.constructor(),templateAccess.get(member));
                    } else if (member instanceof StaticFieldMember field) {
                        declareStaticField(entity, field, staticAccess.get(field), namespace);
                    } else if (member instanceof MethodMember method) {
                        Method registered = declareMethod(entity, method, methodAccess.get(method), namespace);
                        if (registered != null) methods.add(registered);
                    } else if (member instanceof ConstructorMember constructor) {
                        Constructor registered = declareConstructor(entity, constructor, constructorAccess.get(constructor), false);
                        if (registered != null) constructors.add(registered);
                    } else if (member instanceof DestructorMember memberDestructor) {
                        Destructor registered = declareDestructor(entity, memberDestructor, destructorAccess.get(memberDestructor), false);
                        if (registered != null) destructor = registered;
                    }
                }
                if (destructor == null && entity.fields.stream().anyMatch(field -> needsDestruction(field.type()))) {
                    DestructorMember synthetic = new DestructorMember(entity.name, new BlockStmt(List.of(), node.range()), node.range(), node.range());
                    destructor = declareDestructor(entity, synthetic, Access.PUBLIC, true);
                }
                for (CppMember member : members) {
                    if (member instanceof FieldMember field && field.defaultInitializer() != null) bindDefaultMember(entity, field);
                }
                if (constructors.isEmpty() && entity.constructors.isEmpty() && needsConstruction(entity)) {
                    ConstructorMember synthetic = new ConstructorMember(entity.name, List.of(), false, List.of(),
                            new BlockStmt(List.of(), node.range()), node.range(), node.range());
                    Constructor registered = declareConstructor(entity, synthetic, Access.PUBLIC, true);
                    if (registered != null) constructors.add(registered);
                }
                if (instanceKeys.containsKey(entity))
                    for (Method method : methods) {
                        pendingTemplateMethods.put(method.function, method);
                        declareTemplateMethodPrototype(method);
                    }
                // Complete-class lookup applies to bodies, without exposing later namespace declarations.
                for (Constructor constructor : constructors) {
                    if (instanceKeys.containsKey(entity) && !constructor.implicit && constructor.source.definitionKind()==DefinitionKind.ORDINARY) {
                        pendingTemplateConstructors.put(constructor.function, constructor);
                        declareTemplatePrototype(constructor.function, constructor.source.range());
                    } else bindConstructor(constructor);
                }
                if (entity.constructors.size() == 1 && entity.constructors.getFirst().implicit && !nonAggregate(entity)) {
                    Constructor original = entity.constructors.getFirst();
                    Entity function = new Entity(entity.name, freshName(entity.canonicalName.substring(2) + "::" + entity.name),
                            Kind.FUNCTION, namespace, original.function.type, null, true);
                    coreValues.put(function.coreName, function);
                    entity.aggregateInitializer = new Constructor(entity, original.source, Access.PUBLIC, function, List.of(), true);
                    bindConstructor(entity.aggregateInitializer, true);
                }
                if (destructor != null) {
                    if (instanceKeys.containsKey(entity) && !destructor.implicit && destructor.source.definitionKind()==DefinitionKind.ORDINARY) {
                        pendingTemplateDestructors.put(destructor.function, destructor);
                        declareTemplatePrototype(destructor.function, destructor.source.range());
                    } else bindDestructor(destructor);
                }
                ensureImplicitCopy(entity);
                ensureImplicitAssignment(entity);
                ensureImplicitMove(entity);
                if (!instanceKeys.containsKey(entity)) for (Method method : methods) bindMethod(method, namespace);
            }
        }

        private boolean needsDestruction(MiniType type) {
            if (type == null || type.isReference()) return false;
            if (type.isArray()) return needsDestruction(type.elementType());
            TypeEntity owner = objectType(type);
            return owner != null && owner.destructor != null;
        }

        private Destructor declareDestructor(TypeEntity owner, DestructorMember member, Access access, boolean implicit) {
            if (owner.destructor != null) {
                report("CPP004", member.nameRange(), "Duplicate destructor declaration: " + owner.canonicalName);
                return null;
            }
            Entity function = new Entity("~" + owner.name, freshName(owner.canonicalName.substring(2) + "::~" + owner.name),
                    Kind.FUNCTION, owner.owner, MiniType.function(MiniType.VOID, List.of(owner.type.pointerTo()), false), null, member.hasDefinition());
            coreValues.put(function.coreName, function);
            Destructor destructor = new Destructor(owner, member, access, function, implicit);
            owner.destructor = destructor;
            registerSpecialDefinition(function,member.definitionKind(),member.range());
            if(member.definitionKind()==DefinitionKind.DELETED)deletedDestructors.put(function,deletedReason(member.range()));
            return destructor;
        }

        private void bindOutOfLineDestructor(OutOfLineDestructorDecl node, Namespace namespace) {
            QualifiedName path = node.qualifiedName();
            TypeEntity owner = resolveMethodOwner(new QualifiedName(path.global(),
                    path.segments().subList(0, path.segments().size() - 1), path.range()), namespace);
            if (owner == null) return;
            boolean enclosing = false;
            for (Namespace at = owner.owner; at != null; at = at.parent) enclosing |= at == namespace;
            Destructor previous = owner.destructor;
            if (!enclosing || !node.destructor().hasDefinition()) {
                report("CPP004", node.nameRange(), "A destructor definition must be in its enclosing namespace and have a body.");
            } else if (previous == null || previous.implicit) {
                report("CPP004", node.nameRange(), "No matching user-declared destructor: " + owner.canonicalName);
            } else if (previous.function.defined) {
                report("CPP004", node.nameRange(), "Duplicate destructor definition: " + owner.canonicalName);
            } else {
                if(node.destructor().definitionKind()==DefinitionKind.DELETED){report("CPP004",node.range(),"A deleted definition must be the first declaration");return;}
                previous.function.defined = true;
                Destructor replacement=new Destructor(owner,node.destructor(),previous.access,previous.function,false);owner.destructor=replacement;
                if(node.destructor().definitionKind()==DefinitionKind.DEFAULTED)userProvidedDefaulted.add(replacement.function);
                bindDestructor(replacement);
            }
        }

        private void bindDestructor(Destructor destructor) {
            if(destructor.source.definitionKind()==DefinitionKind.DELETED)return;
            if(destructor.source.definitionKind()==DefinitionKind.DEFAULTED){
                DestructorMember source=destructor.source;
                bindDestructor(new Destructor(destructor.owner,new DestructorMember(source.name(),new BlockStmt(List.of(),source.range()),source.nameRange(),source.range()),
                        destructor.access,destructor.function,!userProvidedDefaulted.contains(destructor.function)));return;
            }
            TypeEntity owner = destructor.owner;
            DestructorMember original = destructor.source;
            Entity self = constructorThis(owner, original.nameRange());
            TypeEntity savedClass = currentClass;
            Entity savedThis = currentThis;
            MiniType savedReturn = currentReturnType;
            AutoReturnContext savedAutoReturn=currentAutoReturn; currentAutoReturn=null;
            currentClass = owner; currentThis = self; currentReturnType = MiniType.VOID;
            int diagnosticStart = diagnostics.size();
            try {
                BlockStmt body = null;
                if (original.body() != null) {
                    if (owner.union && destructor.implicit) report("CPP004", original.nameRange(),
                            "A union with a nontrivial variant member has a deleted implicit destructor.");
                    body = block(original.body(), new Local(null, owner.owner), false);
                    List<Expression> memberCleanups = new ArrayList<>();
                    for (int index = owner.fields.size() - 1; index >= 0; index--) {
                        StructField field = owner.fields.get(index);
                        if (!needsDestruction(field.type())) continue;
                        Expression member = typed(new FieldAccessExpr(thisValue(field.range()), field.name(), true, field.range()), field.type());
                        Expression address = typed(new UnaryExpr(TokenType.AMPERSAND, member, field.range()), coreType(field.type()).pointerTo());
                        Expression cleanup = destruction(field.type(), address, field.range());
                        if (cleanup != null) memberCleanups.add(cleanup);
                    }
                    if (!memberCleanups.isEmpty()) {
                        Expression cleanup = memberCleanups.size() == 1 ? memberCleanups.getFirst()
                                : typed(new CommaExpr(memberCleanups, original.range()), MiniType.VOID);
                        body = new BlockStmt(List.of(new CleanupScopeStmt(body, cleanup, original.range())), original.body().range());
                    }
                }
                if (destructor.implicit && diagnostics.size() > diagnosticStart) {
                    deletedDestructors.put(destructor.function, List.copyOf(diagnostics.subList(diagnosticStart, diagnostics.size())));
                    diagnostics.subList(diagnosticStart, diagnostics.size()).clear();
                    destructor.function.defined = false;
                    body = null;
                }
                FunctionDecl core = mapped(original, new FunctionDecl(destructor.function.coreName, MiniType.VOID,
                        List.of(new Parameter(self.coreName, self.type, original.nameRange())), false, body, false, original.range()));
                functions.add(core); declarations.add(core);
            } finally { currentClass = savedClass; currentThis = savedThis; currentReturnType = savedReturn; currentAutoReturn=savedAutoReturn; }
        }

        private Expression destruction(MiniType type, Expression address, SourceRange range) {
            if (type != null && type.isArray() && needsDestruction(type)) return arrayDestruction(type, address, range);
            Destructor destructor = destructorForUse(type, range);
            if (destructor == null) return null;
            instantiateDestructor(destructor);
            TypeEntity owner = destructor.owner;
            // cv-qualification ceases to apply while the object's destructor executes.
            Expression receiver = typed(new CastExpr(owner.type.pointerTo(), address, range), owner.type.pointerTo());
            return typed(new CallExpr(new NameExpr(destructor.function.coreName, range), List.of(receiver), range), MiniType.VOID);
        }

        private Destructor destructorForUse(MiniType type, SourceRange range) {
            if (!needsDestruction(type)) return null;
            if (type.isArray()) {
                return destructorForUse(elementType(type), range);
            }
            TypeEntity owner = objectType(type);
            Destructor destructor = owner.destructor;
            if (destructor.access != Access.PUBLIC && !classAccess(owner)) {
                report("CPP004", range, "Destructor is not accessible: " + owner.canonicalName);
            }
            if (deletedDestructors.containsKey(destructor.function)) {
                Diagnostic reason = deletedDestructors.get(destructor.function).getFirst();
                report(reason.code().equals("CPP005") ? "CPP005" : "CPP004", range,
                        "The implicit destructor is unavailable: " + reason.message());
            }
            return destructor;
        }

        private void requireSupportedCallLifetime(MiniType returnType, List<MiniType> parameters, SourceRange range) {
            // The caller owns each by-value parameter object through its full expression.
            // Array-valued parameters/results are outside the supported object ABI.
            if (returnType != null && returnType.isArray() && needsDestruction(returnType)
                    || parameters.stream().anyMatch(type -> type.isArray() && needsDestruction(type))) {
                report("CPP005", range, "Array parameter/result lifetimes require array object support.");
            }
        }

        private boolean needsConstruction(TypeEntity owner) {
            return needsConstruction(owner, new HashSet<>());
        }

        private boolean needsConstruction(TypeEntity owner, Set<TypeEntity> visited) {
            if (!visited.add(owner)) return false;
            return !owner.constructors.isEmpty() || !owner.defaultInitializers.isEmpty()
                    || owner.fields.stream().anyMatch(field -> needsConstructedType(field.type(), visited));
        }

        private boolean needsConstructedType(MiniType type) {
            return needsConstructedType(type, new HashSet<>());
        }

        private boolean needsConstructedType(MiniType type, Set<TypeEntity> visited) {
            if (type.isArray()) return needsConstructedType(type.elementType(), visited);
            TypeEntity nested = objectType(type);
            return nested != null && needsConstruction(nested, visited);
        }

        private List<Diagnostic> deletedReason(SourceRange range) {
            return List.of(new Diagnostic("CPP004",Diagnostic.Severity.ERROR,"Explicitly deleted function",range));
        }
        private void registerSpecialDefinition(Entity function,DefinitionKind kind,SourceRange range) {
            if(kind==DefinitionKind.ORDINARY)return;
            specialDefinitionRanges.put(function,range);
            if(kind==DefinitionKind.DELETED)deletedFunctions.add(function);
        }
        private boolean isDeleted(Entity function) {
            if(deletedFunctions.contains(function)||deletedConstructors.containsKey(function)||deletedDestructors.containsKey(function))return true;
            for(var entry:defaultedAssignmentPlans.entrySet())if(entry.getKey().function==function&&!entry.getValue().problems.isEmpty())return true;
            for(TypeEntity owner:coreTypes.values()) {
                if(owner.implicitAssignment!=null&&owner.implicitAssignment.function==function&&owner.assignmentPlan!=null&&!owner.assignmentPlan.problems.isEmpty())return true;
                if(owner.implicitMoveAssignment!=null&&owner.implicitMoveAssignment.function==function&&owner.moveAssignmentPlan!=null&&!owner.moveAssignmentPlan.problems.isEmpty())return true;
            }
            return false;
        }
        private boolean validDefaultedConstructor(Constructor constructor) {
            boolean valid=!constructor.source.variadic()&&constructor.source.parameters().stream().noneMatch(p->p.defaultValue()!=null)
                    && (constructor.parameterTypes.isEmpty()||isCopyConstructor(constructor)||isMoveConstructor(constructor));
            if(valid&&!constructor.parameterTypes.isEmpty()){
                MiniType parameter=constructor.parameterTypes.getFirst();
                valid=isMoveConstructor(constructor)?parameter.equals(constructor.owner.type.rvalueReferenceTo())
                        :parameter.isLvalueReference()&&!parameter.referent().isVolatileQualified();
            }
            if(!valid)report("CPP004",constructor.source.range(),"A defaulted constructor must match its implicit special-member signature and have no default arguments");
            return valid;
        }
        private boolean validDefaultedAssignment(Method method) {
            boolean valid=!method.source.constQualified()&&!method.source.staticMember()&&!method.source.method().variadic()
                    &&method.source.method().parameters().stream().noneMatch(p->p.defaultValue()!=null)
                    &&method.returnType.equals(method.owner.type.referenceTo())&&(isCopyAssignment(method)||isMoveAssignment(method));
            if(valid){MiniType parameter=method.parameterTypes.getFirst();valid=parameter.isReference()&&!parameter.referent().isVolatileQualified()
                    &&(!isMoveAssignment(method)||parameter.equals(method.owner.type.rvalueReferenceTo()));}
            if(!valid)report("CPP004",method.source.range(),"Defaulted assignment must match its implicit special-member signature");
            return valid;
        }
        private boolean defaulted(Constructor constructor){return constructor!=null&&constructor.source.definitionKind()==DefinitionKind.DEFAULTED;}
        private boolean defaulted(Method method){return method!=null&&method.source.method().definitionKind()==DefinitionKind.DEFAULTED;}

        private Constructor declareConstructor(TypeEntity owner, ConstructorMember member, Access access, boolean implicit) {
            if (owner.union) {
                report("CPP005", member.nameRange(), "Union construction is not supported yet.");
                return null;
            }
            List<MiniType> parameters = member.parameters().stream()
                    .map(p -> normalizeType(p.type(), owner.owner, null, p.range())).toList();
            if (parameters.size() == 1 && parameters.getFirst().unqualified().equals(owner.type)) {
                report("CPP004", member.nameRange(), "A constructor cannot take its own class as its only by-value parameter.");
                return null;
            }
            List<MiniType> signatureParameters = new ArrayList<>();
            signatureParameters.add(owner.type.pointerTo());
            parameters.stream().map(MiniType::unqualified).forEach(signatureParameters::add);
            MiniType signature = MiniType.function(MiniType.VOID, signatureParameters, member.variadic());
            for (Constructor previous : owner.constructors) {
                if (previous.function.type.equals(signature)) {
                    report("CPP004", member.nameRange(), "Duplicate constructor declaration: " + owner.canonicalName);
                    return null;
                }
            }
            Entity function = new Entity(owner.name, freshName(owner.canonicalName.substring(2) + "::" + owner.name),
                    Kind.FUNCTION, owner.owner, signature, null, member.hasDefinition());
            coreValues.put(function.coreName, function);
            Constructor constructor = new Constructor(owner, member, access, function, parameters, implicit);
            if(defaulted(constructor)&&!validDefaultedConstructor(constructor))return null;
            owner.constructors.add(constructor);
            registerSpecialDefinition(function,member.definitionKind(),member.range());
            if(member.definitionKind()==DefinitionKind.DELETED)deletedConstructors.put(function,deletedReason(member.range()));
            recordDefaultArguments(function,member.parameters(),owner.owner,owner);
            return constructor;
        }

        private void bindOutOfLineConstructor(OutOfLineConstructorDecl node, Namespace namespace) {
            QualifiedName path = node.qualifiedName();
            TypeEntity owner = resolveMethodOwner(new QualifiedName(path.global(),
                    path.segments().subList(0, path.segments().size() - 1), path.range()), namespace);
            if (owner == null) return;
            boolean enclosing = false;
            for (Namespace at = owner.owner; at != null; at = at.parent) enclosing |= at == namespace;
            if (!enclosing || !node.constructor().hasDefinition()) {
                report("CPP004", node.nameRange(), "A constructor definition must be in its enclosing namespace and have a body.");
                return;
            }
            List<MiniType> parameters = node.constructor().parameters().stream()
                    .map(p -> normalizeType(p.type(), owner.owner, null, p.range())).toList();
            Constructor previous = owner.constructors.stream().filter(c -> c.source.variadic() == node.constructor().variadic()
                    && c.parameterTypes.stream().map(MiniType::unqualified).toList()
                    .equals(parameters.stream().map(MiniType::unqualified).toList())).findFirst().orElse(null);
            if (previous == null || previous.function.defined) {
                report("CPP004", node.nameRange(), previous == null ? "No matching constructor declaration." : "Duplicate constructor definition.");
                return;
            }
            if(node.constructor().definitionKind()==DefinitionKind.DELETED){report("CPP004",node.range(),"A deleted definition must be the first declaration");return;}
            previous.function.defined = true;
            Constructor replacement=new Constructor(owner,node.constructor(),previous.access,previous.function,parameters,false);
            if(defaulted(replacement)&&!validDefaultedConstructor(replacement))return;
            owner.constructors.set(owner.constructors.indexOf(previous),replacement);
            if(defaulted(replacement)){
                userProvidedDefaulted.add(replacement.function);registerSpecialDefinition(replacement.function,DefinitionKind.DEFAULTED,node.range());
                owner.copyPlan=null;owner.movePlanned=false;
            }
            bindConstructor(replacement);
            if(defaulted(replacement)){
                ensureImplicitCopy(owner);ensureImplicitMove(owner);
                if(isDeleted(replacement.function))report("CPP004",node.range(),"A defaulted definition after the first declaration cannot be deleted");
                else if(replacement==owner.implicitCopy)emitImplicitCopy(owner);else if(replacement==owner.implicitMove)emitImplicitMove(owner);
            }
        }

        private Entity constructorThis(TypeEntity owner, SourceRange range) {
            Entity self = new Entity("this", freshName("this"), Kind.VARIABLE, null, owner.type.pointerTo(), null, true);
            coreValues.put(self.coreName, self);
            return self;
        }

        /** DMI lookup belongs to the complete class, never to constructor parameter/definition scope. */
        private void bindDefaultMember(TypeEntity owner, FieldMember member) {
            Entity self = constructorThis(owner, member.range());
            Entity function = new Entity(member.field().name(), freshName(owner.canonicalName.substring(2) + "::" + member.field().name() + "$init"),
                    Kind.FUNCTION, owner.owner, MiniType.function(MiniType.VOID, List.of(owner.type.pointerTo()), false), null, true);
            coreValues.put(function.coreName, function);
            owner.defaultInitializers.put(member.field().name(), function);
            TypeEntity savedClass = currentClass;
            Entity savedThis = currentThis;
            MiniType savedReturn = currentReturnType;
            AutoReturnContext savedAutoReturn=currentAutoReturn; currentAutoReturn=null;
            currentClass = owner; currentThis = self; currentReturnType = MiniType.VOID;
            try {
                int index = owner.sourceRecord.fields().indexOf(member.field());
                StructField field = owner.fields.get(index);
                Local scope = new Local(null, owner.owner);
                Expression action = initializeField(owner, field, member.defaultInitializer(), scope, member.range());
                BlockStmt body = new BlockStmt(action == null ? List.of() : List.of(new ExprStmt(action, member.range())), member.range());
                FunctionDecl core = new FunctionDecl(function.coreName, MiniType.VOID,
                        List.of(new Parameter(self.coreName, self.type, member.range())), false, body, false, member.range());
                functions.add(core); declarations.add(core);
            } finally { currentClass = savedClass; currentThis = savedThis; currentReturnType = savedReturn; currentAutoReturn=savedAutoReturn; }
        }

        private void bindConstructor(Constructor constructor) {
            bindConstructor(constructor, false);
        }

        private void bindConstructor(Constructor constructor, boolean aggregateList) {
            if(constructor.source.definitionKind()==DefinitionKind.DELETED)return;
            if(constructor.source.definitionKind()==DefinitionKind.DEFAULTED){
                if(!validDefaultedConstructor(constructor))return;
                if(!constructor.parameterTypes.isEmpty())return;
                ConstructorMember source=constructor.source;
                var bodySource=new ConstructorMember(source.name(),source.parameters(),false,List.of(),new BlockStmt(List.of(),source.range()),
                        source.nameRange(),source.range(),source.explicitSpecifier());
                bindConstructor(new Constructor(constructor.owner,bodySource,constructor.access,constructor.function,List.of(),
                        !userProvidedDefaulted.contains(constructor.function)),aggregateList);return;
            }
            TypeEntity owner = constructor.owner;
            ConstructorMember original = constructor.source;
            requireSupportedCallLifetime(MiniType.VOID, constructor.parameterTypes, original.range());
            Local scope = new Local(null, owner.owner);
            Entity self = constructorThis(owner, original.nameRange());
            List<Parameter> parameters = new ArrayList<>();
            parameters.add(new Parameter(self.coreName, self.type, original.nameRange()));
            for (int index = 0; index < original.parameters().size(); index++) {
                Parameter sourceParameter = original.parameters().get(index);
                MiniType type = constructor.parameterTypes.get(index);
                Entity value = declareLocal(sourceParameter.name(), type, scope, sourceParameter.range());
                parameters.add(mapped(sourceParameter, new Parameter(value.coreName, coreType(type), sourceParameter.range())));
            }
            TypeEntity savedClass = currentClass;
            Entity savedThis = currentThis;
            MiniType savedReturn = currentReturnType;
            AutoReturnContext savedAutoReturn=currentAutoReturn; currentAutoReturn=null;
            currentClass = owner; currentThis = self; currentReturnType = MiniType.VOID;
            int diagnosticStart = diagnostics.size();
            try {
                BlockStmt body = null;
                if (original.body() != null) {
                    var plan = CppMemberInitializationPlan.plan(owner.sourceRecord, original);
                    diagnostics.addAll(plan.diagnostics());
                    List<Statement> statements = new ArrayList<>();
                    for (var entry : plan.entries()) {
                        StructField field = owner.fields.get(owner.sourceRecord.fields().indexOf(entry.field()));
                        Expression action;
                        if (entry.origin() == CppMemberInitializationPlan.Origin.DEFAULT_MEMBER) {
                            Entity helper = owner.defaultInitializers.get(field.name());
                            action = typed(new CallExpr(new NameExpr(helper.coreName, entry.source().range()),
                                    List.of(thisValue(entry.source().range())), entry.source().range()), MiniType.VOID);
                        } else {
                            SourceRange range = entry.origin() == CppMemberInitializationPlan.Origin.DEFAULT
                                    ? original.nameRange() : entry.source().range();
                            CppInitializer syntax = aggregateList && entry.origin() == CppMemberInitializationPlan.Origin.DEFAULT
                                    ? new CppInitializer(CppInitializer.Kind.DIRECT_LIST, List.of(), range) : entry.initializer();
                            action = initializeField(owner, field, syntax, scope, range);
                            if (action != null && entry.origin() == CppMemberInitializationPlan.Origin.EXPLICIT) mapped(entry.source(), action);
                        }
                        if (action != null) statements.add(new ExprStmt(action, action.range()));
                    }
                    statements.addAll(block(original.body(), scope, false).statements());
                    body = new BlockStmt(statements, original.body().range());
                }
                if (constructor.implicit && diagnostics.size() > diagnosticStart) {
                    deletedConstructors.put(constructor.function, List.copyOf(diagnostics.subList(diagnosticStart, diagnostics.size())));
                    diagnostics.subList(diagnosticStart, diagnostics.size()).clear();
                    constructor.function.defined = false;
                    body = null;
                }
                FunctionDecl core = mapped(original, new FunctionDecl(constructor.function.coreName, MiniType.VOID,
                        parameters, original.variadic(), body, false, original.range()));
                functions.add(core); declarations.add(core);
            } finally { currentClass = savedClass; currentThis = savedThis; currentReturnType = savedReturn; currentAutoReturn=savedAutoReturn; }
        }

        private Expression initializeField(TypeEntity owner, StructField field, CppInitializer initialization, Local scope, SourceRange range) {
            Expression value = variableInitializer(field.type(), initialization, null, owner.owner, scope, range);
            if (value == null) return null;
            if (initializerListElement(field.type()) != null && hasListStorage(value))
                report("CPP004", range, "An initializer_list member cannot retain a temporary backing array from its constructor initializer.");
            if (field.type().isReference() && refersToTemporaryStorage(value)) {
                report("CPP004", range, "A reference data member cannot bind to a temporary in a constructor initializer.");
            }
            Expression slot = typed(new FieldAccessExpr(thisValue(range), field.name(), true, range), coreType(field.type()));
            return fullExpression(typed(new InitializeExpr(slot, value, range), MiniType.VOID), false, initialization);
        }

        private boolean refersToTemporaryStorage(Expression value) {
            return switch (value) {
                case MaterializeExpr ignored -> true;
                case GroupingExpr group -> refersToTemporaryStorage(group.expression());
                case UnaryExpr unary when unary.operator() == TokenType.AMPERSAND || temporaryAddressPaths.contains(unary) ->
                        refersToTemporaryStorage(unary.operand());
                case FieldAccessExpr field when !field.viaPointer() -> refersToTemporaryStorage(field.target());
                case IndexExpr index when declaredExpressionType(index.target()) != null && declaredExpressionType(index.target()).isArray() ->
                        refersToTemporaryStorage(index.target());
                case CommaExpr comma -> refersToTemporaryStorage(comma.expressions().getLast());
                case ConditionalExpr conditional -> refersToTemporaryStorage(conditional.thenExpression()) || refersToTemporaryStorage(conditional.elseExpression());
                case CastExpr cast when cast.operand() instanceof UnaryExpr unary && unary.operator() == TokenType.AMPERSAND ->
                        refersToTemporaryStorage(cast.operand());
                default -> false;
            };
        }

        private void declareStaticField(TypeEntity owner, StaticFieldMember member, Access access, Namespace namespace) {
            GlobalVarDecl node = member.declaration();
            if (owner.staticFields.containsKey(node.name()) || owner.methods.containsKey(node.name())
                    || fieldPath(owner.type, node.name(), new HashSet<>()) != null || node.name().equals(owner.name)) {
                report("CPP004", node.range(), "Duplicate or conflicting static data member: " + node.name());
                return;
            }
            MiniType type = normalizeType(node.type(), namespace, null, node.range());
            Entity entity = new Entity(node.name(), freshName(owner.canonicalName.substring(2) + "::" + node.name()),
                    Kind.VARIABLE, namespace, type, null, false);
            StaticField field = new StaticField(owner, node, access, entity);
            owner.staticFields.put(node.name(), field); staticFields.put(entity, field); coreValues.put(entity.coreName, entity);
            if (node.initializer() != null) {
                if (!type.isConstQualified() || type.isVolatileQualified() || !type.isIntegerScalar()) {
                    report("CPP004", node.range(), "Only a const integral static member can have an in-class initializer without inline/constexpr.");
                } else {
                    TypeEntity savedClass = currentClass; Entity savedThis = currentThis;
                    boolean complete = owner.complete;
                    currentClass = owner; currentThis = null; owner.complete = false;
                    try {
                        Expression value = variableInitializer(type, node.cppInitializer(), node.initializer(), namespace, null, node.range(), node.name());
                        if (value == null || !constantInitializer(value)) report("CPP004", node.range(), "A static const integral member requires a constant initializer.");
                        else field.constant = value;
                    } finally { currentClass = savedClass; currentThis = savedThis; owner.complete = complete; }
                }
            }
            // A declaration alone allocates no object. Non-ODR constant reads are lowered separately.
            GlobalVarDecl core = mapped(node, new GlobalVarDecl(entity.coreName, coreType(type), null, true,
                    normalizeAlignments(node.alignmentSpecs(), namespace, null), node.range()));
            globals.add(core); declarations.add(core);
        }

        private void bindStaticFieldDefinition(OutOfLineStaticFieldDecl node, Namespace namespace) {
            QualifiedName path = node.qualifiedName();
            TypeEntity owner = resolveMethodOwner(new QualifiedName(path.global(),
                    path.segments().subList(0, path.segments().size() - 1), path.range()), namespace);
            if (owner == null) return;
            StaticField field = owner.staticFields.get(node.declaration().name());
            if (field == null) { report("CPP004", node.nameRange(), "No matching static data member declaration."); return; }
            boolean enclosing = false;
            for (Namespace at = owner.owner; at != null; at = at.parent) enclosing |= at == namespace;
            if (!enclosing) report("CPP004", node.nameRange(), "Static data definition must be in an enclosing namespace.");
            GlobalVarDecl source = node.declaration();
            MiniType type = normalizeType(source.type(), namespace, null, source.range());
            if (!type.equals(field.entity.type)) report("CPP004", node.nameRange(), "Static data member definition has a different type.");
            if (field.entity.defined) { report("CPP004", node.nameRange(), "Duplicate static data member definition."); return; }
            field.entity.defined = true;
            if (field.constant != null && source.initializer() != null)
                report("CPP004", source.range(), "A static member initializer cannot be specified twice.");
            TypeEntity savedClass = currentClass; Entity savedThis = currentThis;
            currentClass = owner; currentThis = null; field.defining = true;
            try {
                requireComplete(type, source.range());
                Expression value = field.constant != null ? field.constant
                        : bindStaticInitializer(type, source.cppInitializer(), source.initializer(), owner.owner, null, source, source.name());
                boolean constant = value == null || constantInitializer(value);
                GlobalVarDecl core = mapped(source, new GlobalVarDecl(field.entity.coreName, coreType(type), constant ? value : null,
                        false, normalizeAlignments(source.alignmentSpecs(), namespace, null), source.range()));
                globals.add(core); declarations.add(core);
                for (Statement action : staticActions(field.entity, type, constant ? null : value, source.range())) staticLifetime.startup(action);
            } finally { field.defining = false; currentClass = savedClass; currentThis = savedThis; }
        }

        private void requireStaticAccess(StaticField field, SourceRange range) {
            if (field.access != Access.PUBLIC && !classAccess(field.owner))
                report("CPP004", range, "Cannot access " + field.access.name().toLowerCase(java.util.Locale.ROOT)
                        + " static data member: " + field.owner.canonicalName + "::" + field.entity.name);
        }

        private Expression staticFieldReference(StaticField field, SourceRange range, boolean addressDemand) {
            requireStaticAccess(field, range);
            if (!addressDemand && field.constant != null && !field.defining) {
                Expression value = typed(new GroupingExpr(field.constant, range), field.entity.type);
                valueCategories.put(value, CppValueCategory.LVALUE);
                return value;
            }
            if (unevaluatedDepth == 0) field.uses.add(range);
            return referenceStorage(field.entity.name, range, field.entity);
        }

        private StaticField staticField(MiniType type, String name) {
            TypeEntity owner = objectType(type);
            return owner == null ? null : owner.staticFields.get(name);
        }

        private Expression evaluateReceiver(Expression receiver, Expression result, SourceRange range) {
            if (receiver == null) return result;
            Expression sequence = builtinComma(new CastExpr(MiniType.VOID, receiver, receiver.range()), result, range);
            valueCategories.put(sequence, valueCategory(result));
            return sequence;
        }

        private Method declareMethod(TypeEntity owner, MethodMember member, Access access, Namespace namespace) {
            if (unsupportedOperator(member.method())) return null;
            FunctionDecl sourceMethod = member.method();
            String name = sourceMethod.name();
            if (fieldPath(owner.type, name, new HashSet<>()) != null || owner.staticFields.containsKey(name)) {
                report("CPP004", member.nameRange(), "成员函数与数据成员名称冲突：" + name);
                return null;
            }
            List<MiniType> parameterTypes = sourceMethod.parameters().stream()
                    .map(parameter -> normalizeType(parameter.type(), namespace, null, parameter.range())).toList();
            MiniType returnType = normalizeReturnType(sourceMethod.returnType(),sourceMethod.parameters(),parameterTypes,namespace,owner,member,sourceMethod.range());
            if (member.staticMember() && (member.constQualified() || sourceMethod.operatorName() != null)) {
                report("CPP004", member.nameRange(), "C++17 static member functions cannot have cv qualifiers or overloaded operator names.");
                return null;
            }
            if (!validOperator(sourceMethod, parameterTypes, true)) return null;
            List<MiniType> coreParameters = new ArrayList<>();
            if (!member.staticMember()) coreParameters.add(methodThisType(owner, member));
            parameterTypes.stream().map(MiniType::unqualified).forEach(coreParameters::add);
            MiniType signature = MiniType.function(returnType, coreParameters, sourceMethod.variadic());
            List<Method> previous = owner.methods.containsKey(name) ? owner.methods.get(name).methods : List.of();
            for (Method method : previous) {
                MiniType.FunctionType earlier = (MiniType.FunctionType) method.function.type;
                MiniType.FunctionType declared = (MiniType.FunctionType) signature;
                boolean sameArguments = method.parameterTypes.stream().map(MiniType::unqualified).toList()
                        .equals(parameterTypes.stream().map(MiniType::unqualified).toList());
                if (sameArguments && (member.staticMember() || method.source.staticMember())
                        || earlier.parameterTypes().equals(declared.parameterTypes()) && earlier.variadic() == declared.variadic()) {
                    report("CPP004", member.nameRange(), "类内成员函数重复声明或返回类型冲突：" + name);
                    return null;
                }
            }
            Entity function = new Entity(name, freshName(owner.canonicalName.substring(2) + "::" + name),
                    Kind.FUNCTION, namespace, signature, null, sourceMethod.hasDefinition());
            coreValues.put(function.coreName, function);
            Method method = new Method(owner, member, access, function, returnType, parameterTypes);
            registerSpecialDefinition(function,sourceMethod.definitionKind(),sourceMethod.range());
            if(defaulted(method)&&!validDefaultedAssignment(method))return null;
            if (member.staticMember()) staticMethods.put(function, method);
            List<Method> methods = new ArrayList<>(previous);
            methods.add(method);
            owner.methods.put(name, new MethodSet(methods));
            recordDefaultArguments(function,sourceMethod.parameters(),namespace,owner);
            return method;
        }

        private MiniType methodReturnType(Method method) { return ((MiniType.FunctionType) method.function.type).returnType(); }
        private void publishAutoReturn(AutoReturnContext context, MiniType result) {
            MiniType.FunctionType signature = (MiniType.FunctionType) context.function.type;
            context.function.type = MiniType.function(result, signature.parameterTypes(), signature.variadic());
            context.deduced = result;
            currentReturnType = result;
        }
        private void deduceReturn(ReturnStmt node, Namespace namespace, Local scope) {
            if (currentAutoReturn == null) return;
            MiniType deduced;
            if (node.expression() == null) deduced = MiniType.VOID;
            else if (isBraced(node.expression())) {
                report("CPP004", node.range(), "An auto return type cannot be deduced from a braced-init-list."); deduced=MiniType.INT;
            } else if (autoPlaceholder(currentAutoReturn.pattern).decltypeAuto()) {
                if (!currentAutoReturn.pattern.equals(MiniType.DECLTYPE_AUTO)) report("CPP004", node.range(), "decltype(auto) must stand alone.");
                deduced=decltypeType(node.expression(),namespace,scope);
            } else {
                Expression value=unevaluatedExpression(node.expression(),namespace,scope);
                MiniType actual=checkedDeduced(declaredExpressionType(value),node.range());
                MiniType adjusted=currentAutoReturn.pattern.isReference()?actual:TypeCompatibility.decay(actual).unqualified();
                if(isForwardingAuto(currentAutoReturn.pattern)&&valueCategory(value)==CppValueCategory.LVALUE)adjusted=adjusted.referenceTo();
                deduced=deducePattern(currentAutoReturn.pattern,adjusted);
                if(deduced==null){report("CPP004",node.range(),"Return expression does not match the auto return declarator.");deduced=MiniType.INT;}
            }
            if (deduced.isVoid() && !(currentAutoReturn.pattern.unqualified() instanceof MiniType.AutoType))
                report("CPP004",node.range(),"This auto return declarator cannot deduce void.");
            if(currentAutoReturn.deduced!=null&&!currentAutoReturn.deduced.equals(deduced))
                report("CPP004",node.range(),"All non-discarded returns must deduce the same type.");
            else publishAutoReturn(currentAutoReturn,deduced);
        }
        private MiniType finishAutoReturn(AutoReturnContext context,SourceRange range) {
            if(context.deduced==null){
                if(!(context.pattern.unqualified() instanceof MiniType.AutoType))report("CPP004",range,"An auto reference/pointer return requires a return expression.");
                publishAutoReturn(context,MiniType.VOID);
            }
            return context.deduced;
        }
        private void emitAutoPrototypes(Entity function,MiniType result) {
            List<FunctionDecl> pending=pendingAutoDeclarations.remove(function);
            if(pending==null)return;
            MiniType.FunctionType signature=(MiniType.FunctionType)function.type;
            for(FunctionDecl original:pending){
                List<Parameter> parameters=new ArrayList<>();
                for(int index=0;index<signature.parameterTypes().size();index++)parameters.add(new Parameter(
                        freshName("parameter"),coreType(signature.parameterTypes().get(index)),original.range()));
                FunctionDecl core=mapped(original,new FunctionDecl(function.coreName,coreType(result),parameters,original.variadic(),null,
                        original.external(),original.noReturn(),original.range()));
                functions.add(core);declarations.add(core);
            }
        }

        private void bindMethod(Method method, Namespace namespace) {
            if(method.source.method().definitionKind()==DefinitionKind.DELETED)return;
            if(method.source.method().definitionKind()==DefinitionKind.DEFAULTED){
                if(!isCopyAssignment(method)&&!isMoveAssignment(method))report("CPP004",method.source.range(),"Only a special member function can be defaulted");
                return;
            }
            FunctionDecl original = method.source.method();
            MiniType returnPattern=methodReturnType(method);
            if(returnPattern.containsAuto()&&!original.hasBody()) { autoReturnPatterns.put(method.function,returnPattern); return; }
            requireSupportedCallLifetime(methodReturnType(method), method.parameterTypes, original.range());
            Local scope = new Local(null, namespace);
            LambdaInfo lambda=lambdaTypes.get(method.owner);
            if(lambda!=null) {
                List<Local> lexical=new ArrayList<>();for(Local at=lambda.lexicalScope;at!=null;at=at.parent)lexical.addFirst(at);
                for(Local at:lexical){scope.typedefs.putAll(at.typedefs);scope.directives.addAll(at.directives);}
            }
            Entity self = method.source.staticMember() ? null : new Entity("this", freshName("this"), Kind.VARIABLE, null,
                    methodThisType(method.owner, method.source), null, true);
            if (self != null) coreValues.put(self.coreName, self);
            List<Parameter> parameters = new ArrayList<>();
            if (self != null) parameters.add(new Parameter(self.coreName, self.type, method.source.nameRange()));
            for (int index = 0; index < original.parameters().size(); index++) {
                Parameter parameter = original.parameters().get(index);
                MiniType type = method.parameterTypes.get(index);
                Entity value = declareLocal(parameter.name(), type, scope, parameter.range());
                parameters.add(mapped(parameter, new Parameter(value.coreName, coreType(type), parameter.range())));
            }
            TypeEntity savedClass = currentClass;
            Entity savedThis = currentThis;
            MiniType savedReturnType = currentReturnType;
            AutoReturnContext savedAutoReturn=currentAutoReturn;
            currentAutoReturn=returnPattern.containsAuto()?new AutoReturnContext(method.function,returnPattern):null;
            if(currentAutoReturn!=null)autoReturnPatterns.put(method.function,returnPattern);
            currentClass = method.owner;
            currentThis = self;
            currentReturnType = methodReturnType(method);
            try {
                if (original.hasBody()) {
                    if(!returnPattern.containsAuto()) requireComplete(methodReturnType(method), original.range());
                    method.parameterTypes.forEach(type -> requireComplete(type, original.range()));
                }
                BlockStmt body = original.body() == null ? null : block(original.body(), scope, false);
                if(currentAutoReturn!=null)finishAutoReturn(currentAutoReturn,original.range());
                FunctionDecl core = mapped(original, new FunctionDecl(method.function.coreName, coreType(methodReturnType(method)),
                        parameters, original.variadic(), body, false, original.noReturn(), original.range()));
                functions.add(core); declarations.add(core);
            } finally {
                currentClass = savedClass;
                currentThis = savedThis;
                currentReturnType = savedReturnType;
                currentAutoReturn=savedAutoReturn;
            }
        }

        private void bindOutOfLineMethod(OutOfLineMethodDecl node, Namespace namespace) {
            if (unsupportedOperator(node.method())) return;
            QualifiedName path = node.qualifiedName();
            QualifiedName ownerName = new QualifiedName(path.global(),
                    path.segments().subList(0, path.segments().size() - 1), path.range());
            TypeEntity owner = resolveMethodOwner(ownerName, namespace);
            if (owner == null) return;
            boolean enclosing = false;
            for (Namespace at = owner.owner; at != null; at = at.parent) enclosing |= at == namespace;
            if (!enclosing) {
                report("CPP004", node.nameRange(), "成员定义必须位于所属类的外围命名空间：" + spelling(path));
                return;
            }
            FunctionDecl definition = node.method();
            MethodSet overloads = owner.methods.get(definition.name());
            if (!owner.complete || overloads == null) {
                report("CPP004", node.nameRange(), "类中尚未声明此成员函数：" + spelling(path));
                return;
            }
            if (!definition.hasDefinition()) {
                report("CPP004", node.nameRange(), "类外成员声明必须提供函数定义：" + spelling(path));
                return;
            }
            List<MiniType> parameterTypes = definition.parameters().stream()
                    .map(p -> normalizeType(p.type(), owner.owner, null, p.range())).toList();
            MiniType returnType = normalizeReturnType(definition.returnType(),definition.parameters(),parameterTypes,owner.owner,owner,
                    new MethodMember(definition,node.constQualified(),node.nameRange()),definition.range());
            Method previous = overloads.methods.stream().filter(method ->
                    method.parameterTypes.stream().map(MiniType::unqualified).toList()
                            .equals(parameterTypes.stream().map(MiniType::unqualified).toList())
                    && method.source.method().variadic() == definition.variadic()
                    && method.source.constQualified() == node.constQualified()
                    && methodReturnType(method).equals(returnType)).findFirst().orElse(null);
            if (previous == null) {
                report("CPP004", node.nameRange(), "类外定义与成员函数声明的签名不匹配：" + spelling(path));
                return;
            }
            if (previous.function.defined) {
                report("CPP004", node.nameRange(), "成员函数重复定义：" + spelling(path));
                return;
            }
            if(definition.definitionKind()==DefinitionKind.DELETED){report("CPP004",node.range(),"A deleted definition must be the first declaration");return;}
            previous.function.defined = true;
            MethodMember member = new MethodMember(definition, node.constQualified(), previous.source.staticMember(), node.nameRange());
            Method replacement=new Method(owner,member,previous.access,previous.function,returnType,parameterTypes);
            if(defaulted(replacement)&&!validDefaultedAssignment(replacement))return;
            var updated=new ArrayList<>(overloads.methods);updated.set(updated.indexOf(previous),replacement);owner.methods.put(definition.name(),new MethodSet(updated));
            if(defaulted(replacement)){
                userProvidedDefaulted.add(replacement.function);registerSpecialDefinition(replacement.function,DefinitionKind.DEFAULTED,node.range());
                owner.assignmentPlanned=false;owner.movePlanned=false;ensureImplicitAssignment(owner);ensureImplicitMove(owner);
                if(isDeleted(replacement.function))report("CPP004",node.range(),"A defaulted definition after the first declaration cannot be deleted");
                else if(replacement==owner.implicitAssignment)emitImplicitAssignment(owner);else if(replacement==owner.implicitMoveAssignment)emitImplicitMoveAssignment(owner);
            } else bindMethod(replacement,owner.owner);
        }

        private TypeEntity resolveMethodOwner(QualifiedName name, Namespace namespace) {
            Set<Candidate> candidates = new LinkedHashSet<>();
            String last = name.segments().getLast();
            if (name.global() || name.segments().size() > 1) {
                Namespace at = name.segments().size() == 1 ? root : resolveNamespace(new QualifiedName(name.global(),
                        name.segments().subList(0, name.segments().size() - 1), name.range()), namespace, null);
                if (at == null) return null;
                candidates.addAll(qualifiedOwnerCandidates(at, last, new HashSet<>()));
            } else {
                Map<Namespace, Set<Namespace>> nominated = nominations(namespace, null);
                for (Namespace at = namespace; at != null; at = at.parent) {
                    candidates.addAll(directOwnerCandidates(at, last));
                    for (Namespace target : nominated.getOrDefault(at, Set.of())) candidates.addAll(directOwnerCandidates(target, last));
                    if (!candidates.isEmpty()) break;
                }
            }
            Candidate candidate = candidates.isEmpty() ? null : selectCandidate(candidates, spelling(name), name.range());
            if (candidate instanceof Namespace) {
                report("CPP005", name.range(), "尚未支持命名空间自由函数的类外限定定义：" + spelling(name));
                return null;
            }
            TypeEntity owner = candidate instanceof TypeEntity type ? objectType(type.type) : null;
            if (owner == null) report("CPP003", name.range(), "成员定义需要已声明的类类型：" + spelling(name));
            return owner;
        }

        private Set<Candidate> directOwnerCandidates(Namespace namespace, String name) {
            Set<Candidate> result = new LinkedHashSet<>();
            if (namespace.children.containsKey(name)) result.add(namespace.children.get(name));
            TypeEntity alias = namespace.typedefs.get(name);
            if (alias != null && alias.type.unqualified().isStruct()) result.add(alias);
            else if (namespace.tags.containsKey(name)) result.add(namespace.tags.get(name));
            return result;
        }

        private Set<Candidate> qualifiedOwnerCandidates(Namespace namespace, String name, Set<Namespace> visited) {
            if (!visited.add(namespace)) return Set.of();
            Set<Candidate> result = directOwnerCandidates(namespace, name);
            if (!result.isEmpty()) return result;
            for (Namespace target : namespace.directives) result.addAll(qualifiedOwnerCandidates(target, name, visited));
            return result;
        }

        private MiniType methodThisType(TypeEntity owner, MethodMember sourceMethod) {
            MiniType object = sourceMethod.constQualified()
                    ? MiniType.qualified(owner.type, Set.of(MiniType.TypeQualifier.CONST)) : owner.type;
            return object.pointerTo();
        }

        private TypeEntity declareClass(String name, boolean union, Namespace namespace, SourceRange range) {
            if (namespace.children.containsKey(name)) report("CPP004", range, "类型声明与命名空间冲突：" + name);
            TypeEntity existing = namespace.tags.get(name);
            if (existing == null && namespace.typedefs.containsKey(name) && namespace.typedefs.get(name).classType) {
                existing = namespace.typedefs.get(name);
            }
            if (existing != null && existing.classType) {
                if (existing.union != union) report("CPP004", range, "struct/union 类型声明不一致：" + name);
                return existing;
            }
            if (existing != null || namespace.typedefs.containsKey(name)) {
                report("CPP004", range, "类型声明与已有类型别名冲突：" + name);
            }
            String canonical = "::" + namespace.qualify(name);
            String coreName = freshName(namespace.qualify(name));
            if (union) {
                String display = displayNames.remove(coreName);
                coreName = "$union$" + coreName;
                displayNames.put(coreName, display);
            }
            TypeEntity entity = new TypeEntity(name, canonical, MiniType.struct(coreName), true, union, namespace, false);
            namespace.tags.put(name, entity);
            canonicalTypes.put(canonical, entity);
            coreTypes.put(coreName, entity);
            return entity;
        }

        private MiniType normalizeReturnType(MiniType type,List<Parameter> parameters,List<MiniType> parameterTypes,
                                             Namespace namespace,TypeEntity owner,MethodMember member,SourceRange range) {
            if (!(type instanceof MiniType.TrailingReturnType trailing)) return normalizeType(type,namespace,null,range);
            Local scope=new Local(null,namespace);
            for(int index=0;index<parameters.size();index++)declareLocal(parameters.get(index).name(),parameterTypes.get(index),scope,parameters.get(index).range());
            TypeEntity savedClass=currentClass;Entity savedThis=currentThis;
            if(owner!=null){currentClass=owner;currentThis=member!=null&&member.staticMember()?null:
                    new Entity("this",freshName("this"),Kind.VARIABLE,null,methodThisType(owner,member),null,true);
                if(currentThis!=null)coreValues.put(currentThis.coreName,currentThis);}
            try{return normalizeType(trailing.type(),namespace,scope,range);}
            finally{currentClass=savedClass;currentThis=savedThis;}
        }
        private Expression decltypeExpression(Expression operand,Namespace namespace,Local local) {
            Expression saved=decltypeOperand;Expression ungrouped=operand;
            while(ungrouped instanceof GroupingExpr group)ungrouped=group.expression();
            decltypeOperand=ungrouped;
            try{return unevaluatedExpression(operand,namespace,local);}finally{decltypeOperand=saved;}
        }
        private MiniType decltypeType(Expression operand, Namespace namespace, Local local) {
            rejectUnevaluatedLambdas(operand);
            Candidate named;
            unevaluatedDepth++;
            try {named = operand instanceof NameExpr name ? lookupName(name.name(), namespace, local, name.range())
                    : operand instanceof QualifiedNameExpr name ? resolveQualifiedName(name.name(), namespace, local) : null;}
            finally {unevaluatedDepth--;}
            if(named instanceof UnevaluatedLambdaLocal unavailable)return checkedDeduced(unavailable.entity.type,operand.range());
            if (named instanceof Entity entity) { decltypeExpression(operand,namespace,local); return checkedDeduced(entity.type, operand.range()); }
            if (named instanceof OverloadSet overloads && overloads.functions.size() == 1) {
                decltypeExpression(operand,namespace,local); return checkedDeduced(overloads.functions.getFirst().type, operand.range()); }
            if (named instanceof ImplicitField field) {
                FieldPath path = fieldPath(field.owner.type, field.name, new HashSet<>());
                if (path != null) return checkedDeduced(path.type(), operand.range());
            }
            if (operand instanceof FieldAccessExpr field) {
                Expression receiver = unevaluatedExpression(field.target(), namespace, local);
                MiniType ownerType = declaredExpressionType(receiver);
                if (field.viaPointer()) ownerType = elementType(ownerType);
                if (ownerType != null) {
                    FieldPath path = requireAccessible(ownerType.unqualified(), field.fieldName(), field.range(), "decltype member");
                    if (path != null) return checkedDeduced(path.type(), operand.range());
                }
            }
            Expression value = decltypeExpression(operand, namespace, local);
            MiniType type = checkedDeduced(declaredExpressionType(value), operand.range());
            return valueCategory(value) == CppValueCategory.LVALUE ? type.referenceTo()
                    : valueCategory(value)==CppValueCategory.XVALUE?type.rvalueReferenceTo():type;
        }
        private MiniType checkedDeduced(MiniType type, SourceRange range) {
            if (type == null || type.containsPlaceholder()) {
                report("CPP004", range, "The expression has no deduced type at this point."); return MiniType.INT;
            }
            return type;
        }
        private MiniType.AutoType autoPlaceholder(MiniType type) {
            return switch (type.unqualified()) {
                case MiniType.AutoType placeholder -> placeholder;
                case MiniType.ReferenceType reference -> autoPlaceholder(reference.referent());
                case MiniType.PointerType pointer -> autoPlaceholder(pointer.pointee());
                case MiniType.ArrayType array -> autoPlaceholder(array.elementType());
                case MiniType.FunctionType function -> autoPlaceholder(function.returnType());
                default -> null;
            };
        }
        private MiniType deduceVariableType(MiniType pattern, CppInitializer syntax, Expression legacy,
                                            Namespace namespace, Local local, SourceRange range) {
            List<Expression> arguments = syntax == null ? legacy == null ? List.of() : List.of(legacy) : syntax.arguments();
            MiniType.AutoType placeholder = autoPlaceholder(pattern);
            if (arguments.isEmpty()) { report("CPP004", range, "An auto declaration requires an initializer."); return MiniType.INT; }
            boolean copyList = syntax != null && syntax.kind() == CppInitializer.Kind.COPY_LIST;
            if (placeholder != null && placeholder.decltypeAuto()) {
                if (!pattern.equals(MiniType.DECLTYPE_AUTO)) report("CPP004", range, "decltype(auto) cannot have additional declarator or cv qualifiers.");
                if (copyList || arguments.size() != 1 || isBraced(arguments.getFirst())) {
                    report("CPP004", range, "decltype(auto) requires one expression, not a braced list."); return MiniType.INT;
                }
                return decltypeType(arguments.getFirst(), namespace, local);
            }
            MiniType actual;CppValueCategory category=CppValueCategory.PRVALUE;
            if (copyList) {
                MiniType element = null;
                for (Expression item : arguments) {
                    if (isBraced(item)) { report("CPP004", item.range(), "auto cannot deduce an element type from nested braces."); return MiniType.INT; }
                    MiniType candidate = TypeCompatibility.decay(checkedDeduced(declaredExpressionType(unevaluatedExpression(item, namespace, local)), item.range())).unqualified();
                    if (element != null && !element.equals(candidate)) report("CPP004", item.range(), "All elements of an auto initializer_list must deduce the same type.");
                    element = candidate;
                }
                if (element == null) { report("CPP004", range, "auto cannot deduce an empty initializer_list."); return MiniType.INT; }
                actual = templateType(new MiniType.TemplateIdType("::std::initializer_list", List.of(new TemplateArgument.Type(element))), namespace, local, range);
            } else {
                if (arguments.size() != 1 || isBraced(arguments.getFirst())) {
                    report("CPP004", range, "Direct-list auto deduction requires exactly one expression."); return MiniType.INT;
                }
                Expression value = unevaluatedExpression(arguments.getFirst(), namespace, local);
                actual = checkedDeduced(declaredExpressionType(value), arguments.getFirst().range());
                category=valueCategory(value);
            }
            if (actual.isVoid()) { report("CPP004", range, "An auto object cannot have void type."); return MiniType.INT; }
            MiniType adjusted = pattern.isReference() ? actual : TypeCompatibility.decay(actual).unqualified();
            if(isForwardingAuto(pattern)&&category==CppValueCategory.LVALUE)adjusted=adjusted.referenceTo();
            MiniType result = deducePattern(pattern, adjusted);
            if (result == null) { report("CPP004", range, "The initializer does not match the auto declarator pattern."); return MiniType.INT; }
            return result;
        }
        private boolean isForwardingAuto(MiniType pattern) {
            return pattern.isRvalueReference()&&pattern.referent() instanceof MiniType.AutoType auto&&!auto.decltypeAuto();
        }
        private MiniType deducePattern(MiniType pattern, MiniType actual) {
            if (pattern instanceof MiniType.QualifiedType qualified) {
                MiniType result = deducePattern(qualified.baseType(), actual);
                return result == null ? null : MiniType.qualified(result, qualified.qualifiers());
            }
            if (pattern instanceof MiniType.AutoType) return actual;
            if (pattern instanceof MiniType.ReferenceType reference) {
                MiniType result = deducePattern(reference.referent(), actual);
                return result == null ? null : result.referenceTo(reference.kind());
            }
            if (pattern instanceof MiniType.PointerType pointer && actual.isPointer()) {
                MiniType result = deducePattern(pointer.pointee(), actual.pointee());
                return result == null ? null : result.pointerTo();
            }
            if (pattern instanceof MiniType.ArrayType array && actual.isArray() && array.length() == actual.arrayLength()) {
                MiniType result = deducePattern(array.elementType(), actual.elementType());
                return result == null ? null : result.arrayOf(array.length());
            }
            if (pattern instanceof MiniType.FunctionType function && actual.unqualified() instanceof MiniType.FunctionType sourceFunction
                    && function.parameterTypes().equals(sourceFunction.parameterTypes()) && function.variadic() == sourceFunction.variadic()) {
                MiniType result = deducePattern(function.returnType(), sourceFunction.returnType());
                return result == null ? null : MiniType.function(result, function.parameterTypes(), function.variadic());
            }
            return pattern.equals(actual) ? pattern : null;
        }

        /** Frontend canonical names are source identities, never linker/layout identities. */

        private TemplateArgument.Integral evaluateTemplateConstant(Expression expression) {
            return TemplateValues.evaluate(resolveTemplateQueries(expression));
        }
        private Expression resolveTemplateQueries(Expression expression) {
            if(expression instanceof SizeofExpr || expression instanceof AlignofExpr) {
                MiniType type=expression instanceof SizeofExpr size?size.queriedType():((AlignofExpr)expression).queriedType();
                Expression operand=expression instanceof SizeofExpr size?size.expression():((AlignofExpr)expression).expression();
                if(type==null)type=declaredExpressionType(operand);
                type=objectTypeOfReference(type);if(type==null)throw new IllegalArgumentException("Cannot determine constant layout query type");
                requireComplete(type,expression.range());
                var layoutDiagnostics=new ArrayList<Diagnostic>();
                var registry=new minic.compiler.semantic.manager.StructRegistry(new minic.compiler.semantic.model.Scope(),layoutDiagnostics);
                registry.defineStructs(new Program(structs,List.of(),source.range()));
                int value=expression instanceof SizeofExpr?registry.completeObjectSize(coreType(type)):registry.completeObjectAlignment(coreType(type));
                if(!layoutDiagnostics.isEmpty())throw new IllegalArgumentException(layoutDiagnostics.getFirst().message());
                return new IntegerConstantExpr(value,MiniType.UNSIGNED_LONG_LONG,Integer.toString(value),expression.range());
            }
            if(expression instanceof GroupingExpr group)return new GroupingExpr(resolveTemplateQueries(group.expression()),group.range());
            if(expression instanceof UnaryExpr unary)return new UnaryExpr(unary.operator(),resolveTemplateQueries(unary.operand()),unary.range());
            if(expression instanceof BinaryExpr binary)return new BinaryExpr(resolveTemplateQueries(binary.left()),binary.operator(),resolveTemplateQueries(binary.right()),binary.range());
            if(expression instanceof ConditionalExpr conditional)return new ConditionalExpr(resolveTemplateQueries(conditional.condition()),resolveTemplateQueries(conditional.thenExpression()),resolveTemplateQueries(conditional.elseExpression()),conditional.range());
            if(expression instanceof CastExpr cast)return new CastExpr(cast.targetType(),resolveTemplateQueries(cast.operand()),cast.range());
            return expression;
        }

        private MiniType normalizeType(MiniType type, Namespace namespace, Local local, SourceRange range) {
            if (type == null) return null;
            if(type instanceof MiniType.DependentArrayType array) {
                MiniType element=normalizeType(array.elementType(),namespace,local,range);
                try {
                    long length=evaluateTemplateConstant(expression(array.bound(),namespace,local)).value();
                    if(length<=0||length>Integer.MAX_VALUE)throw new IllegalArgumentException("Array bound must be in 1..2147483647");
                    return element.arrayOf((int)length);
                } catch(IllegalArgumentException error){report("CPP004",range,error.getMessage());return element.arrayOf(1);}
            }
            if (type instanceof MiniType.DecltypeType query) return decltypeType(query.expression(), namespace, local);
            if (type instanceof MiniType.MemberType member) {
                MiniType ownerType=normalizeType(member.owner(),namespace,local,range);
                TypeEntity owner=objectType(ownerType);
                if(owner!=null)completeTemplate(owner,range);
                MiniType result=owner==null?null:owner.memberTypes.get(member.name());
                if(result==null) {report("CPP003",range,"No member type "+member.name()+" in "+ownerType);return MiniType.INT;}
                if(owner.memberTypeAccess.getOrDefault(member.name(),Access.PUBLIC)!=Access.PUBLIC && currentClass!=owner)
                    report("CPP004",range,"Member type is inaccessible: "+member.name());
                return result;
            }
            if (type instanceof MiniType.TemplateIdType id) return templateType(id, namespace, local, range);
            if (type instanceof MiniType.TemplateParameterType parameter) {
                report("CPP004", range, "Unsubstituted template parameter: " + parameter);
                return MiniType.INT;
            }
            if (type instanceof MiniType.ReferenceType reference) {
                MiniType referent = normalizeType(reference.referent(), namespace, local, range);
                if (referent.isVoid()) report("CPP004", range, "引用不能指向 void。");
                return referent.referenceTo(reference.kind());
            }
            if (type instanceof MiniType.QualifiedType qualified) {
                return MiniType.qualified(normalizeType(qualified.baseType(), namespace, local, range), qualified.qualifiers());
            }
            if (type instanceof MiniType.PointerType pointer) {
                if (pointer.pointee().isReference()) report("CPP004", range, "不能声明指向引用的指针。");
                return normalizeType(pointer.pointee(), namespace, local, range).pointerTo();
            }
            if (type instanceof MiniType.ArrayType array) {
                if (array.elementType().isReference()) report("CPP004", range, "数组元素不能是引用。");
                return normalizeType(array.elementType(), namespace, local, range).arrayOf(array.length());
            }
            if (type instanceof MiniType.FunctionType function) return MiniType.function(
                    normalizeType(function.returnType(), namespace, local, range),
                    function.parameterTypes().stream().map(t -> normalizeType(t, namespace, local, range)).toList(), function.variadic());
            if (!(type instanceof MiniType.StructType struct)) return type;
            if (coreTypes.containsKey(struct.name())) return type;
            if (coreTypes.containsKey(struct.name())) return type;
            boolean union = struct.name().startsWith("$union$");
            String name = union ? struct.name().substring("$union$".length()) : struct.name();
            TypeEntity entity = null;
            if (name.startsWith("::")) entity = canonicalTypes.get(name);
            else {
                // Legacy ASTs store an elaborated 'struct S' reference without qualification.
                int beforeLookup = diagnostics.size();
                entity = lookupLegacyTag(name, namespace, local, range);
                if (entity == null && diagnostics.size() == beforeLookup && !name.contains("::")) {
                    if (local != null) {
                        report("CPP005", range, "尚未支持具名局部类型声明的作用域保存。");
                        return type;
                    }
                    // An unqualified elaborated specifier can introduce an incomplete class.
                    entity = declareClass(name, union, namespace, range);
                    var forward = new StructDecl(((MiniType.StructType) entity.type).name(), List.of(), false, union, range);
                    structs.add(forward); declarations.add(forward);
                }
            }
            if (entity == null) {
                report("CPP003", range, "此位置尚未声明类型：" + name);
                return type;
            }
            if (!entity.classType || entity.union != union) {
                report("CPP004", range, "struct/union 类型名称不匹配：" + name);
                return type;
            }
            return entity.type;
        }

        /** Source callable signatures retain references; only emitted core nodes use pointer ABI. */
        private MiniType coreType(MiniType type) {
            if (type == null) return null;
            if (type.containsPlaceholder()) { report("CPP004", source.range(), "A source placeholder has not been deduced in this declaration."); return MiniType.INT; }
            return switch (type) {
                case MiniType.ReferenceType reference -> coreType(reference.referent()).pointerTo();
                case MiniType.QualifiedType qualified -> MiniType.qualified(coreType(qualified.baseType()), qualified.qualifiers());
                case MiniType.PointerType pointer -> coreType(pointer.pointee()).pointerTo();
                case MiniType.ArrayType array -> coreType(array.elementType()).arrayOf(array.length());
                case MiniType.FunctionType function -> MiniType.function(coreType(function.returnType()),
                        function.parameterTypes().stream().map(this::coreType).toList(), function.variadic());
                default -> type;
            };
        }

        private MiniType objectTypeOfReference(MiniType type) {
            return type != null && type.isReference() ? type.referent() : type;
        }

        private TypeEntity lookupLegacyTag(String name, Namespace namespace, Local local, SourceRange range) {
            for (Local scope = local; scope != null; scope = scope.parent) {
                TypeEntity type = scope.typedefs.get(name);
                if (type != null && type.classType) return type;
            }
            Map<Namespace, Set<Namespace>> nominated = nominations(namespace, local);
            for (Namespace scope = namespace; scope != null; scope = scope.parent) {
                Set<Candidate> candidates = new LinkedHashSet<>(directTags(scope, name));
                for (Namespace target : nominated.getOrDefault(scope, Set.of())) candidates.addAll(directTags(target, name));
                if (!candidates.isEmpty()) {
                    Candidate candidate = selectCandidate(candidates, name, range);
                    return candidate instanceof TypeEntity type ? type : null;
                }
            }
            return null;
        }

        private Set<Candidate> directTags(Namespace namespace, String name) {
            TypeEntity tag = visible(namespace).tags.get(name);
            if (tag != null) return Set.of(tag);
            TypeEntity imported = visible(namespace).typedefs.get(name);
            return imported != null && imported.classType ? Set.of(imported) : Set.of();
        }

        private List<AlignmentSpec> normalizeAlignments(List<AlignmentSpec> specs, Namespace namespace, Local local) {
            return specs.stream().map(spec -> {
                if (spec.type() == null) return mapped(spec, spec);
                MiniType type = objectTypeOfReference(normalizeType(spec.type(), namespace, local, spec.range()));
                requireComplete(type, spec.range());
                return mapped(spec, AlignmentSpec.type(coreType(type), spec.range()));
            }).toList();
        }

        private void requireComplete(MiniType type, SourceRange range) {
            if (type == null) return; // Unknown scalar expression types are checked by the core semantic pass.
            if (type.containsPlaceholder()) { report("CPP004", range, "The type must be deduced before it is used."); return; }
            type = type.unqualified();
            if (type instanceof MiniType.ArrayType array) requireComplete(array.elementType(), range);
            else if (type instanceof MiniType.StructType struct) {
                TypeEntity entity = coreTypes.get(struct.name());
                if (entity != null) completeTemplate(entity, range);
                if (entity != null && !entity.complete) report("CPP005", range, "此位置需要完整对象类型，但类型仍不完整：" + entity.canonicalName);
            }
        }

        private String simpleTagName(String name) {
            if (name.startsWith("$union$")) name = name.substring("$union$".length());
            int separator = name.lastIndexOf("::");
            return separator < 0 ? name : name.substring(separator + 2);
        }

        private void bindGlobal(GlobalVarDecl node, Namespace namespace) {
            if (namespace != root && node.external() && !existingInternal(namespace, node.name())) report("CPP005", node.range(),
                    "尚未支持命名空间中的外部对象链接：" + namespace.qualify(node.name()));
            MiniType type = normalizeType(node.type(), namespace, null, node.range());
            boolean defined = !node.external() || node.initializer() != null;
            Entity entity = declareNamespaceValue(node.name(), Kind.VARIABLE, type, defined, namespace, node.range());
            if (type.containsAuto()) { type = deduceVariableType(type, node.cppInitializer(), node.initializer(), namespace, null, node.range()); entity.type = type; }
            if (defined) requireComplete(type, node.range());
            recordLinkage(entity, node.range());
            Expression value = defined ? bindStaticInitializer(type, node.cppInitializer(), node.initializer(),
                    namespace, null, node, node.name()) : null;
            boolean constant = value == null || constantInitializer(value);
            GlobalVarDecl core = mapped(node, new GlobalVarDecl(entity.coreName, coreType(type), constant ? value : null,
                    node.external() && !defined && !internalLinkages.getOrDefault(entity, false), normalizeAlignments(node.alignmentSpecs(), namespace, null), node.range()));
            globals.add(core); declarations.add(core);
            if (defined) for (Statement action : staticActions(entity, type, constant ? null : value, node.range()))
                staticLifetime.startup(action);
        }

        private Expression bindStaticInitializer(MiniType type, CppInitializer syntax, Expression legacy,
                                                 Namespace namespace, Local scope, AstNode sourceNode, String name) {
            boolean previous = staticInitialization;
            staticInitialization = true;
            try {
                Expression value = variableInitializer(type, syntax, legacy, namespace, scope, sourceNode.range(), name);
                if (type.isReference() && value != null) value = extendTemporaryLifetime(value,
                        new TemporaryLifetime(TemporaryLifetime.Kind.REFERENCE_SCOPE, sourceNode));
                if (initializerListElement(type) != null && value != null) value = extendListLifetime(value,
                        new TemporaryLifetime(TemporaryLifetime.Kind.REFERENCE_SCOPE, sourceNode));
                return value;
            } finally { staticInitialization = previous; }
        }

        private List<Statement> staticActions(Entity entity, MiniType type, Expression initialized, SourceRange range) {
            List<Expression> actions = new ArrayList<>();
            Expression storage = typed(new NameExpr(entity.coreName, range), coreType(type));
            if (initialized != null) actions.add(new InitializeExpr(storage, initialized, range));
            Expression cleanup = destruction(type, address(storage), range);
            Expression registration = staticLifetime.register(cleanup, range);
            if (registration != null) actions.add(registration);
            if (actions.isEmpty()) return List.of();
            Expression action = actions.size() == 1 ? actions.getFirst() : new CommaExpr(actions, range);
            boolean saved = staticInitialization;
            staticInitialization = true;
            try {
                // Store the initialized result and register its lifetime before the initializer's
                // temporaries are destroyed (a temporary destructor may itself call exit).
                return List.of(new ExprStmt(fullExpression(action, false, action), range));
            } finally { staticInitialization = saved; }
        }

        private Statement bindLocalStatic(VarDeclStmt node, Namespace namespace, Local scope, MiniType type) {
            Entity entity = declareLocal(node.name(), type, scope, node.range());
            staticLocalEntities.add(entity);
            if (type.containsAuto()) { type = deduceVariableType(type, node.cppInitializer(), node.initializer(), namespace, scope, node.range()); entity.type = type; }
            requireComplete(type, node.range());
            Expression value = bindStaticInitializer(type, node.cppInitializer(), node.initializer(), namespace, scope, node, node.name());
            boolean constant = value == null || constantInitializer(value);
            addStaticGlobal(new GlobalVarDecl(entity.coreName, coreType(type), constant ? value : null, false,
                    normalizeAlignments(node.alignmentSpecs(), namespace, scope), node.range()));
            List<Statement> actions = staticActions(entity, type, constant ? null : value, node.range());
            return actions.isEmpty() ? new BlockStmt(List.of(), node.range()) : staticLifetime.once(actions, node.range());
        }

        private Expression staticMaterialization(MaterializeExpr temporary) {
            String name = freshName("static_reference_temporary");
            MiniType type = temporary.type();
            addStaticGlobal(new GlobalVarDecl(name, type, null, false, List.of(), temporary.range()));
            Expression storage = typed(new NameExpr(name, temporary.range()), type);
            Expression result = address(storage);
            List<Expression> actions = new ArrayList<>();
            // The caller already rewrote this initializer. Keep its pending full-expression
            // temporary registrations in that enclosing traversal instead of lowering it twice.
            actions.add(new InitializeExpr(storage, temporary.initializer(), temporary.range()));
            Expression registration = staticLifetime.register(destruction(type, result, temporary.range()), temporary.range());
            if (registration != null) actions.add(registration);
            actions.add(result);
            return typed(new CommaExpr(actions, temporary.range()), type.pointerTo());
        }

        private boolean unsupportedOperator(FunctionDecl node) {
            if (node.operatorName() == null) return false;
            if (switch (node.operatorName().kind()) {
                case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, BIT_XOR, BIT_AND, BIT_OR,
                        BIT_NOT, LOGICAL_NOT, LESS, GREATER, SHIFT_LEFT, SHIFT_RIGHT, EQUAL, NOT_EQUAL,
                        LESS_EQUAL, GREATER_EQUAL, INCREMENT, DECREMENT, CALL, SUBSCRIPT, MEMBER_ACCESS,
                        LOGICAL_AND, LOGICAL_OR, COMMA, ASSIGN, ADD_ASSIGN, SUBTRACT_ASSIGN,
                        MULTIPLY_ASSIGN, DIVIDE_ASSIGN, REMAINDER_ASSIGN, XOR_ASSIGN, AND_ASSIGN,
                        OR_ASSIGN, SHIFT_LEFT_ASSIGN, SHIFT_RIGHT_ASSIGN -> true;
                default -> false;
            }) return false;
            report("CPP005", node.operatorName().range(), "运算符重载声明已解析；重载选择和执行语义尚未实现。");
            return true;
        }

        private boolean validOperator(FunctionDecl node, List<MiniType> parameters, boolean member) {
            if (node.operatorName() == null) return true;
            OperatorName.Kind kind = node.operatorName().kind();
            int count = parameters.size() + (member ? 1 : 0);
            boolean valid = switch (kind) {
                case CALL -> member;
                case MEMBER_ACCESS -> member && count == 1;
                case SUBSCRIPT, ASSIGN -> member && count == 2;
                case ADD, SUBTRACT, MULTIPLY, BIT_AND -> count == 1 || count == 2;
                case BIT_NOT, LOGICAL_NOT -> count == 1;
                case INCREMENT, DECREMENT -> count == 1 || count == 2
                        && parameters.getLast().unqualified().equals(MiniType.INT);
                default -> count == 2;
            };
            valid &= !node.variadic() || kind == OperatorName.Kind.CALL;
            if (!member) valid &= parameters.stream().anyMatch(type -> objectType(objectTypeOfReference(type)) != null);
            if (!valid) report("CPP004", node.operatorName().range(), "运算符声明的成员形式、参数个数或参数类型不合法。");
            return valid;
        }

        private void validateAllocationFunction(FunctionDecl node, Namespace namespace,
                                                MiniType returnType, List<MiniType> parameters) {
            var kind = node.operatorName().kind();
            boolean allocating = kind == minic.compiler.parser.node.OperatorName.Kind.NEW
                    || kind == minic.compiler.parser.node.OperatorName.Kind.NEW_ARRAY;
            MiniType expectedReturn = allocating ? MiniType.VOID.pointerTo() : MiniType.VOID;
            MiniType expectedFirst = allocating ? MiniType.UNSIGNED_LONG_LONG : MiniType.VOID.pointerTo();
            if (namespace != root) report("CPP004", node.operatorName().range(),
                    "A nonmember allocation or deallocation function must be declared in global scope.");
            if (!returnType.unqualified().equals(expectedReturn)) report("CPP004", node.range(),
                    "Invalid allocation or deallocation function return type.");
            if (parameters.isEmpty() || !parameters.getFirst().unqualified().equals(expectedFirst))
                report("CPP004", node.range(), "Invalid first allocation or deallocation parameter type.");
        }

        private void recordDefaultArguments(Entity function,List<Parameter> parameters,Namespace namespace,TypeEntity record) {
            List<DefaultArgument> prior=functionDefaults.getOrDefault(function,List.of());
            var merged=new ArrayList<DefaultArgument>();
            for(int index=0;index<parameters.size();index++) {
                Parameter parameter=parameters.get(index);
                DefaultArgument old=index<prior.size()?prior.get(index):null;
                if(parameter.defaultValue()!=null) {
                    if(old!=null)report("CPP004",parameter.range(),"Default argument is declared more than once");
                    old=new DefaultArgument(parameter.defaultValue(),namespace,record,currentTemplateLookup==null?snapshotLookup():currentTemplateLookup);
                }
                merged.add(old);
            }
            functionDefaults.put(function,Collections.unmodifiableList(merged));
        }
        private int requiredParameters(Entity function,int size) {
            List<DefaultArgument> defaults=functionDefaults.getOrDefault(function,List.of());
            int required=size;
            while(required>0&&required<=defaults.size()&&defaults.get(required-1)!=null)required--;
            return required;
        }
        private List<Expression> lowerSelectedArguments(Entity function,List<MiniType> parameters,List<Expression> sourceArguments,
                                                       List<Expression> values,Namespace namespace,Local local) {
            return lowerSelectedArguments(function,parameters,sourceArguments,values,namespace,local,false);
        }
        private List<Expression> lowerSelectedArguments(Entity function,List<MiniType> parameters,List<Expression> sourceArguments,
                                                       List<Expression> values,Namespace namespace,Local local,boolean listNarrowing) {
            var result=new ArrayList<>(lowerSelectedArguments(parameters,sourceArguments,values,namespace,local,listNarrowing));
            result.addAll(defaultArguments(function,parameters,sourceArguments.size()));return List.copyOf(result);
        }
        private List<Expression> defaultArguments(Entity function,List<MiniType> parameters,int supplied) {
            var result=new ArrayList<Expression>();var defaults=functionDefaults.getOrDefault(function,List.of());
            for(int index=supplied;index<parameters.size();index++) {
                DefaultArgument argument=index<defaults.size()?defaults.get(index):null;
                if(argument==null){report("CPP004",source.range(),"Missing required function argument");break;}
                var savedLookup=currentTemplateLookup;var savedClass=currentClass;var savedThis=currentThis;
                currentTemplateLookup=argument.lookup;currentClass=argument.record;currentThis=null;
                try {
                    MiniType target=parameters.get(index);
                    if(target.isReference()) result.add(bindReference(target,argument.source,argument.owner,null,argument.source.range()));
                    else {
                        Expression value=isBraced(argument.source)?bindListValue(target,argument.source,argument.owner,null)
                                :expressionForTarget(target,argument.source,argument.owner,null);
                        result.add(convertCallValue(target,value,argument.source));
                    }
                } finally {currentTemplateLookup=savedLookup;currentClass=savedClass;currentThis=savedThis;}
            }
            return List.copyOf(result);
        }

        private void bindFunction(FunctionDecl node, Namespace namespace) { bindFunction(node,namespace,null); }

        private void bindFunction(FunctionDecl node, Namespace namespace,Entity instantiated) {
            if(node.definitionKind()==DefinitionKind.DEFAULTED){report("CPP004",node.range(),"Only a special member function can be defaulted");return;}
            if (node.conversionName() != null) {
                report("CPP004", node.range(), "转换函数必须是非静态类成员。"); return;
            }
            boolean allocation = node.operatorName() != null && node.operatorName().kind().allocation();
            if (!allocation && unsupportedOperator(node)) return;
            if (namespace != root && node.external() && !existingInternal(namespace, node.name())) report("CPP005", node.range(),
                    "尚未支持命名空间中的外部函数链接：" + namespace.qualify(node.name()));
            List<MiniType> parameterTypes = node.parameters().stream()
                    .map(p -> normalizeType(p.type(), namespace, null, p.range())).toList();
            MiniType returnType = normalizeReturnType(node.returnType(),node.parameters(),parameterTypes,namespace,null,null,node.range());
            if (!allocation && !validOperator(node, parameterTypes, false)) return;
            if (allocation) validateAllocationFunction(node, namespace, returnType, parameterTypes);
            if (node.hasBody()) {
                if (!returnType.containsAuto()) requireComplete(returnType, node.range());
                for (int i = 0; i < parameterTypes.size(); i++) requireComplete(parameterTypes.get(i), node.parameters().get(i).range());
            }
            requireSupportedCallLifetime(returnType, parameterTypes, node.range());
            MiniType.FunctionType signature = (MiniType.FunctionType) MiniType.function(returnType, parameterTypes.stream()
                    .map(MiniType::unqualified).toList(), node.variadic());
            if(instantiated==null&&node.definitionKind()==DefinitionKind.DELETED&&namespace.values.get(node.name()) instanceof OverloadSet previous
                    &&previous.functions.stream().anyMatch(f->!functionTemplates.containsKey(f)&&f.type instanceof MiniType.FunctionType type
                            &&type.parameterTypes().equals(signature.parameterTypes())&&type.variadic()==signature.variadic()))
                report("CPP004",node.range(),"A deleted definition must be the first declaration");
            Entity entity = instantiated!=null?instantiated:declareNamespaceFunction(node.name(), signature, node.hasDefinition(), namespace, node.range(), node.operatorName() != null);
            if(instantiated==null)recordDefaultArguments(entity,node.parameters(),namespace,null);
            recordLinkage(entity, node.range());
            if(node.definitionKind()==DefinitionKind.DELETED){
                registerSpecialDefinition(entity,node.definitionKind(),node.range());return;
            }
            if(returnType.containsAuto()) {
                autoReturnPatterns.putIfAbsent(entity,returnType);
                if(!node.hasBody()) {
                    if(((MiniType.FunctionType)entity.type).returnType().containsAuto())pendingAutoDeclarations.computeIfAbsent(entity,key->new ArrayList<>()).add(node);
                    else { pendingAutoDeclarations.computeIfAbsent(entity,key->new ArrayList<>()).add(node); emitAutoPrototypes(entity,((MiniType.FunctionType)entity.type).returnType()); }
                    return;
                }
            }
            if (node.external() && namespace == root && node.name().equals("exit")
                    && !internalLinkages.getOrDefault(entity, false)) libraryExitFunctions.add(entity);
            Local scope = new Local(null, namespace);
            List<Parameter> parameters = new ArrayList<>();
            for (int i = 0; i < node.parameters().size(); i++) {
                Parameter parameter = node.parameters().get(i);
                MiniType type = parameterTypes.get(i);
                Entity value = declareLocal(parameter.name(), type, scope, parameter.range());
                parameters.add(mapped(parameter, new Parameter(value.coreName, coreType(type), parameter.range())));
            }
            MiniType savedReturnType = currentReturnType;
            AutoReturnContext savedAutoReturn=currentAutoReturn;
            currentAutoReturn=returnType.containsAuto()?new AutoReturnContext(entity,returnType):null;
            currentReturnType = returnType;
            try {
                BlockStmt body = node.body() == null ? null : block(node.body(), scope, false);
                if(currentAutoReturn!=null) { returnType=finishAutoReturn(currentAutoReturn,node.range()); emitAutoPrototypes(entity,returnType); }
                FunctionDecl core = mapped(node, new FunctionDecl(entity.coreName, coreType(returnType), parameters,
                        node.variadic(), body, node.external() && !internalLinkages.getOrDefault(entity, false), node.noReturn(), node.range()));
                functions.add(core); declarations.add(core);
            } finally { currentReturnType = savedReturnType; currentAutoReturn=savedAutoReturn; }
        }

        private Entity declareNamespaceValue(String name, Kind kind, MiniType type, boolean definition,
                                             Namespace namespace, SourceRange range) {
            if (namespace.children.containsKey(name) || namespace.typedefs.containsKey(name) && !namespace.typedefs.get(name).classType) {
                report("CPP004", range, "名称与命名空间或类型别名冲突：" + name);
            }
            Candidate previous = namespace.values.get(name);
            Entity existing = previous instanceof Entity entity ? entity : null;
            if (previous != null && existing == null) report("CPP004", range, "名称与已有函数声明冲突：" + name);
            if (existing != null) {
                if (existing.owner != namespace || existing.kind != kind) {
                    report("CPP004", range, "名称与已有声明或 using 声明冲突：" + name);
                } else if (!existing.type.equals(type)) {
                    report(kind == Kind.FUNCTION ? "CPP005" : "CPP004", range,
                            kind == Kind.FUNCTION ? "尚未支持函数重载或不同签名的重声明：" + name
                                    : "重复声明的类型不一致：" + name);
                } else if (definition && existing.defined) {
                    report("CPP004", range, "重复定义：" + name);
                }
                existing.defined |= definition;
                return existing;
            }
            // Root declarations retain their ABI names. Every local is renamed, so ::name cannot be captured.
            String coreName = namespace == root ? name : freshName(namespace.qualify(name));
            displayNames.put(coreName, namespace.qualify(name));
            Entity entity = new Entity(name, coreName, kind, namespace, type, null, definition);
            namespace.values.put(name, entity);
            coreValues.put(coreName, entity);
            return entity;
        }

        private Entity declareNamespaceFunction(String name, MiniType.FunctionType type, boolean definition,
                                                Namespace namespace, SourceRange range, boolean operator) {
            Candidate previous = namespace.values.get(name);
            if (namespace.children.containsKey(name) || namespace.typedefs.containsKey(name)
                    && !namespace.typedefs.get(name).classType || previous != null && !(previous instanceof OverloadSet)) {
                report("CPP004", range, "函数名称与已有声明冲突：" + name);
            }
            List<Entity> visible = previous instanceof OverloadSet set ? set.functions : List.of();
            for (Entity function : visible) {
                if(functionTemplates.containsKey(function))continue;
                MiniType.FunctionType signature = (MiniType.FunctionType) function.type;
                if (!signature.parameterTypes().equals(type.parameterTypes()) || signature.variadic() != type.variadic()) continue;
                if (function.owner != namespace) report("CPP004", range, "函数声明与 using 引入的函数冲突：" + name);
                if (!signature.returnType().equals(type.returnType())
                        && !Objects.equals(autoReturnPatterns.get(function),type.returnType())) report("CPP004", range, "函数重声明的返回类型不一致：" + name);
                if (definition && function.defined) report("CPP004", range, "重复函数定义：" + name);
                function.defined |= definition;
                return function;
            }
            if (namespace == root && name.equals("main") && !visible.isEmpty()) report("CPP004", range, "main 不能重载。");
            String coreName = namespace == root && visible.isEmpty() && !operator ? name : freshName(namespace.qualify(name));
            displayNames.put(coreName, namespace.qualify(name));
            Entity entity = new Entity(name, coreName, Kind.FUNCTION, namespace, type, null, definition);
            List<Entity> functions = new ArrayList<>(visible);
            functions.add(entity);
            namespace.values.put(name, new OverloadSet(functions));
            coreValues.put(coreName, entity);
            return entity;
        }

        private Entity declareLocal(String name, MiniType type, Local scope, SourceRange range) {
            if (scope.values.containsKey(name) || scope.typedefs.containsKey(name) && !scope.typedefs.get(name).classType) {
                report("CPP004", range, "局部名称重复或与 using 声明冲突：" + name);
            }
            Entity entity = new Entity(name, freshName(name), Kind.VARIABLE, null, type, null, true);
            scope.values.put(name, entity);
            coreValues.put(entity.coreName, entity);
            return entity;
        }

        private void declareTypedef(String name, MiniType type, Namespace namespace, Local local, SourceRange range) {
            Map<String, Candidate> values = local == null ? namespace.values : local.values;
            if (values.containsKey(name) || (local == null && namespace.children.containsKey(name))) {
                report("CPP004", range, "类型别名与已有名称冲突：" + name);
            }
            Map<String, TypeEntity> aliases = local == null ? namespace.typedefs : local.typedefs;
            TypeEntity previous = aliases.get(name);
            TypeEntity tag = local == null ? namespace.tags.get(name) : null;
            if (previous != null && !previous.type.equals(type) || tag != null && !tag.type.equals(type)) {
                report("CPP004", range, "类型别名与已有类型声明冲突：" + name);
            }
            aliases.put(name, new TypeEntity(name, "::" + namespace.qualify(name), type, false, false, namespace, true));
        }

        private void bindUsing(UsingDecl node, Namespace namespace, Local local) {
            if (node.namespaceDirective()) {
                Namespace target = resolveNamespace(node.target(), namespace, local);
                if (target != null) (local == null ? namespace.directives : local.directives).add(target);
            } else {
                Candidate candidate = resolveQualifiedName(node.target(), namespace, local);
                if (candidate instanceof TypeEntity type) {
                    String name = node.target().segments().getLast();
                    Map<String, TypeEntity> aliases = local == null ? namespace.typedefs : local.typedefs;
                    TypeEntity previous = aliases.putIfAbsent(name, type);
                    TypeEntity tag = local == null ? namespace.tags.get(name) : null;
                    if (previous != null && !previous.type.equals(type.type)
                            || tag != null && !tag.type.equals(type.type)
                            || !type.classType && (local == null ? namespace.values : local.values).containsKey(name)
                            || local == null && namespace.children.containsKey(name)) {
                        report("CPP004", node.range(), "using 类型声明与已有名称冲突：" + name);
                    }
                    return;
                }
                Candidate target = candidate instanceof OverloadSet ? candidate : requireValue(candidate, spelling(node.target()), node.range());
                if (target == null) return;
                String name = node.target().segments().getLast();
                Map<String, Candidate> values = local == null ? namespace.values : local.values;
                Candidate previous = values.putIfAbsent(name, target);
                if (previous instanceof OverloadSet first && target instanceof OverloadSet second) {
                    Set<Entity> merged = new LinkedHashSet<>(first.functions);
                    merged.addAll(second.functions);
                    values.put(name, new OverloadSet(new ArrayList<>(merged)));
                    previous = target;
                }
                TypeEntity type = (local == null ? namespace.typedefs : local.typedefs).get(name);
                if (previous != null && previous != target
                        || type != null && !type.classType
                        || (local == null && namespace.children.containsKey(name))) {
                    report("CPP004", node.range(), "using 声明与已有名称冲突：" + name);
                }
            }
        }

        private BlockStmt block(BlockStmt node, Local scope, boolean child) {
            Local context = child ? new Local(scope, scope.namespace) : scope;
            return mapped(node, new BlockStmt(statements(node.statements(), context), node.range()));
        }

        private List<Statement> statements(List<Statement> nodes, Local scope) {
            List<Statement> result = new ArrayList<>();
            for (Statement node : nodes) {
                if (node instanceof UsingDecl using) bindUsing(using, scope.namespace, scope);
                else {
                    Statement bound = statement(node, scope);
                    result.addAll(localPreludes.getOrDefault(bound, List.of()));
                    result.add(bound);
                }
            }
            return withLocalCleanups(result);
        }

        private List<Statement> withLocalCleanups(List<Statement> nodes) {
            List<Statement> result = new ArrayList<>();
            for (int index = 0; index < nodes.size(); index++) {
                Statement node = nodes.get(index);
                result.add(node);
                Expression cleanup = localCleanups.get(node);
                if (cleanup != null) {
                    Statement rest = new BlockStmt(withLocalCleanups(nodes.subList(index + 1, nodes.size())), node.range());
                    result.add(new CleanupScopeStmt(rest, cleanup, node.range()));
                    break;
                }
            }
            return List.copyOf(result);
        }

        private Statement body(Statement node, Local scope) {
            if (node == null) return null;
            Local child = new Local(scope, scope.namespace);
            if (node instanceof BlockStmt) return statement(node, child);
            return new BlockStmt(statements(List.of(node), child), node.range());
        }

        private Statement statement(Statement node, Local scope) {
            if (node == null) return null;
            Namespace namespace = scope.namespace;
            Statement core = switch (node) {
                case BlockStmt n -> block(n, scope, true);
                case VarDeclStmt n -> {
                    MiniType type = normalizeType(n.type(), namespace, scope, n.range());
                    if (n.staticStorage()) yield bindLocalStatic(n, namespace, scope, type);
                    Entity value = declareLocal(n.name(), type, scope, n.range());
                    if (type.containsAuto()) { type = deduceVariableType(type, n.cppInitializer(), n.initializer(), namespace, scope, n.range()); value.type = type; }
                    requireComplete(type, n.range());
                    Expression initialized = variableInitializer(type, n.cppInitializer(), n.initializer(), namespace, scope, n.range(), n.name());
                    if (type.isReference() && initialized != null) {
                        initialized = extendTemporaryLifetime(initialized,
                                new TemporaryLifetime(TemporaryLifetime.Kind.REFERENCE_SCOPE, n));
                    }
                    if (initializerListElement(type) != null && initialized != null) initialized = extendListLifetime(initialized,
                            new TemporaryLifetime(TemporaryLifetime.Kind.REFERENCE_SCOPE, n));
                    var lifetime = lowerLifetime(initialized, type.isStruct() || type.isArray(), n);
                    VarDeclStmt variable = new VarDeclStmt(value.coreName, coreType(type), lifetime.expression(),
                            normalizeAlignments(n.alignmentSpecs(), namespace, scope), n.range());
                    if (!lifetime.declarations().isEmpty()) localPreludes.put(variable, lifetime.declarations());
                    Expression address = typed(new UnaryExpr(TokenType.AMPERSAND,
                            typed(new NameExpr(value.coreName, n.range()), coreType(type)), n.range()), coreType(type).pointerTo());
                    Expression cleanup = destruction(type, address, n.range());
                    if (lifetime.scopeCleanup() != null) cleanup = cleanup == null ? lifetime.scopeCleanup()
                            : new CommaExpr(List.of(cleanup, lifetime.scopeCleanup()), n.range());
                    if (cleanup != null) localCleanups.put(variable, cleanup);
                    yield variable;
                }
                case TypedefStmt n -> {
                    MiniType type = normalizeType(n.type(), namespace, scope, n.range());
                    declareTypedef(n.name(), type, namespace, scope, n.range());
                    yield new TypedefStmt(n.name(), coreType(type), n.range());
                }
                case UsingDecl n -> {
                    bindUsing(n, namespace, scope);
                    yield new BlockStmt(List.of(), n.range());
                }
                case ExprStmt n -> new ExprStmt(fullExpression(expression(n.expression(), namespace, scope), false, n), n.range());
                case ReturnStmt n -> {
                    deduceReturn(n,namespace,scope);
                    // Accessibility is required even when guaranteed copy elision leaves the
                    // returned object's eventual destruction to the caller.
                    if (currentReturnType != null && currentReturnType.isStruct()) destructorForUse(currentReturnType, n.range());
                    yield new ReturnStmt(fullExpression(currentReturnType != null && currentReturnType.isReference()
                            ? bindReference(currentReturnType, n.expression(), namespace, scope, n.range())
                            : returnValue(n.expression(),namespace,scope),
                            currentReturnType != null && currentReturnType.isStruct(), n), n.range());
                }
                case IfStmt n -> new IfStmt(fullExpression(contextualBool(expression(n.condition(), namespace, scope)), false, n),
                        body(n.thenBranch(), scope), body(n.elseBranch(), scope), n.range());
                case WhileStmt n -> new WhileStmt(fullExpression(contextualBool(expression(n.condition(), namespace, scope)), false, n), body(n.body(), scope), n.range());
                case DoWhileStmt n -> new DoWhileStmt(body(n.body(), scope), fullExpression(contextualBool(expression(n.condition(), namespace, scope)), false, n), n.range());
                case CppRangeForStmt n -> rangeFor(n, scope);
                case ForStmt n -> {
                    Local loop = new Local(scope, namespace);
                    Statement initializer = statement(n.initializer(), loop);
                    Expression condition = fullExpression(contextualBool(expression(n.condition(), namespace, loop)), false, n);
                    Expression step = fullExpression(expression(n.step(), namespace, loop), false, n);
                    // C++ forbids redeclaring the for-init name in the body's outermost block.
                    Statement loopBody = n.body() instanceof BlockStmt b ? block(b, loop, false) : body(n.body(), loop);
                    if (localCleanups.containsKey(initializer)) {
                        ForStmt loopStatement = new ForStmt(null, condition, step, loopBody, n.range());
                        List<Statement> sequence = new ArrayList<>(localPreludes.getOrDefault(initializer, List.of()));
                        sequence.add(initializer);
                        sequence.add(new CleanupScopeStmt(loopStatement, localCleanups.get(initializer), n.range()));
                        yield new BlockStmt(sequence, n.range());
                    }
                    yield new ForStmt(initializer, condition, step, loopBody, n.range());
                }
                case SwitchStmt n -> {
                    Expression selector = fullExpression(contextualIntegral(expression(n.selector(), namespace, scope)), false, n);
                    Local casesScope = new Local(scope, namespace);
                    List<SwitchCase> cases = new ArrayList<>();
                    boolean crossesInitialization = false;
                    for (SwitchCase item : n.cases()) {
                        if (crossesInitialization) report("CPP005", item.range(),
                                "case/default 跳转会跳过同一 switch 作用域的局部初始化；请用显式块限制变量作用域。");
                        Expression value = expression(item.value(), namespace, casesScope);
                        cases.add(mapped(item, new SwitchCase(value, statements(item.statements(), casesScope), item.range())));
                        crossesInitialization |= item.statements().stream()
                                .anyMatch(s -> s instanceof VarDeclStmt v && (v.initializer() != null || localCleanups.containsKey(origins.get(v))));
                    }
                    yield new SwitchStmt(selector, cases, n.range());
                }
                case BreakStmt n -> n;
                case ContinueStmt n -> n;
                default -> {
                    report("CPP005", node.range(), "尚未支持此 C++ 语句：" + node.getClass().getSimpleName());
                    yield node;
                }
            };
            return mapped(node, core);
        }

        private void rejectUnevaluatedLambdas(AstNode source) {
            if(source==null)return;
            if(source instanceof CppLambdaExpr) {report("CPP004",source.range(),"Lambda expressions in unevaluated operands require C++20.");return;}
            for(AstNode child:AstChildren.of(source))rejectUnevaluatedLambdas(child);
        }

        private boolean classAccess(TypeEntity owner) {
            for(TypeEntity at=currentClass;at!=null;) {
                if(at==owner)return true;
                LambdaInfo lambda=lambdaTypes.get(at);if(lambda==null)break;at=lambda.lexicalClass;
            }
            return false;
        }

        private <T> T lambdaOuter(LambdaInfo lambda,java.util.function.Supplier<T> action) {
            TypeEntity savedClass=currentClass;Entity savedThis=currentThis;
            currentClass=lambda.lexicalClass;currentThis=lambda.lexicalThis;
            try{return action.get();}finally{currentClass=savedClass;currentThis=savedThis;}
        }

        private Expression implicitReceiver(TypeEntity owner,SourceRange range) {
            if(currentClass==owner && currentThis!=null)return thisValue(range);
            LambdaInfo lambda=lambdaTypes.get(currentClass);
            if(lambda==null)return null;
            Expression receiver;
            if(unevaluatedDepth>0&&!lambda.captures.containsKey(lambda.thisCaptureName)) {
                MiniType pointer=lambda.lexicalThis!=null?lambda.lexicalThis.type:owner.type.pointerTo();
                receiver=typed(new CastExpr(coreType(pointer),new IntegerLiteralExpr(0,"0",range),range),pointer);
            } else receiver=lambdaThis(lambda,range);
            return receiver!=null&&objectType(elementType(declaredExpressionType(receiver)))==owner?receiver:null;
        }

        private Candidate lambdaLookup(LambdaInfo lambda,String name,SourceRange range) {
            Candidate candidate=lambdaOuter(lambda,()->lookupName(name,lambda.namespace,lambda.lexicalScope,range));
            if(candidate instanceof Entity entity && entity.kind==Kind.VARIABLE && entity.owner==null&&!staticLocalEntities.contains(entity)) {
                if(unevaluatedDepth>0)return new UnevaluatedLambdaLocal(entity);
                return ensureLambdaCapture(lambda,name,null,range);
            }
            if(candidate instanceof ImplicitField field) {
                if(lambdaTypes.containsKey(field.owner))return ensureLambdaCapture(lambda,name,null,range);
                if(unevaluatedDepth==0)lambdaThis(lambda,range);return candidate;
            }
            if(candidate instanceof MethodSet methods && unevaluatedDepth==0 && methods.methods.stream().anyMatch(method->!method.source.staticMember()))lambdaThis(lambda,range);
            return candidate;
        }

        private Candidate ensureLambdaCapture(LambdaInfo lambda,String name,CppLambdaExpr.Capture explicit,SourceRange range) {
            if(lambda.captures.containsKey(name))return new ImplicitField(lambda.type,name);
            if(explicit==null && lambda.source.captureDefault()==CppLambdaExpr.CaptureDefault.NONE) {
                report("CPP004",range,"An automatic variable must be captured before it is used in a lambda: "+name);
                Candidate candidate=lambdaOuter(lambda,()->lookupName(name,lambda.namespace,lambda.lexicalScope,range));
                return candidate instanceof Entity entity?new UnevaluatedLambdaLocal(entity):candidate;
            }
            if(lambda.complete) {report("CPP004",range,"A lambda cannot acquire captures after its closure type is complete.");return null;}
            boolean byReference=explicit!=null?explicit.kind()==CppLambdaExpr.CaptureKind.REFERENCE
                    :lambda.source.captureDefault()==CppLambdaExpr.CaptureDefault.REFERENCE;
            Expression source=explicit!=null&&explicit.initializer()!=null?explicit.initializer():new NameExpr(name,range);
            MiniType type;
            if(explicit!=null&&explicit.initializer()!=null) {
                type=lambdaOuter(lambda,()->deduceVariableType(byReference?MiniType.AUTO.referenceTo():MiniType.AUTO,
                        explicit.initializer(),explicit.initializer(),lambda.namespace,lambda.lexicalScope,range));
            } else {
                Expression value=lambdaOuter(lambda,()->expression(source,lambda.namespace,lambda.lexicalScope,true));
                type=checkedDeduced(declaredExpressionType(value),range);
                if(byReference)type=type.referenceTo();
            }
            requireComplete(type,range);
            var capture=new LambdaCapture(name,type,source,byReference,explicit);
            lambda.captures.put(name,capture);
            var fields=new ArrayList<>(lambda.type.fields);var field=new StructField(name,type,false,List.of(),range);fields.add(field);
            lambda.type.fields=List.copyOf(fields);lambda.type.fieldAccess.put(field,Access.PRIVATE);
            return new ImplicitField(lambda.type,name);
        }

        private Expression lambdaThis(LambdaInfo lambda,SourceRange range) {
            final String captureName=lambda.thisCaptureName;
            LambdaCapture capture=lambda.captures.get(captureName);
            if(capture==null) {
                if(lambda.source.captureDefault()==CppLambdaExpr.CaptureDefault.NONE) {
                    report("CPP004",range,"The enclosing this object is not captured by this lambda.");
                    return typed(new CastExpr(MiniType.VOID.pointerTo(),new IntegerLiteralExpr(0,"0",range),range),MiniType.VOID.pointerTo());
                }
                captureLambdaThis(lambda,false,null,range);capture=lambda.captures.get(captureName);
            }
            if(capture==null)return null;
            Expression value=fieldReference(thisValue(range),captureName,true,currentThis.type.pointee(),range);
            return capture.type.isPointer()?value:address(value);
        }

        private void captureLambdaThis(LambdaInfo lambda,boolean copy,CppLambdaExpr.Capture source,SourceRange range) {
            if(lambda.captures.containsKey(lambda.thisCaptureName)) {if(source!=null)report("CPP004",range,"Duplicate this capture.");return;}
            Expression value=lambdaOuter(lambda,()->lambdaTypes.containsKey(currentClass)
                    ?lambdaThis(lambdaTypes.get(currentClass),range):currentThis==null?null:thisValue(range));
            if(value==null) {report("CPP004",range,"There is no enclosing this object to capture.");return;}
            MiniType type=declaredExpressionType(value);
            if(copy)type=type.pointee();
            var capture=new LambdaCapture(lambda.thisCaptureName,type,new ThisExpr(range),false,source);
            lambda.captures.put(capture.name,capture);
            var fields=new ArrayList<>(lambda.type.fields);var field=new StructField(capture.name,type,false,List.of(),range);fields.add(field);
            lambda.type.fields=List.copyOf(fields);lambda.type.fieldAccess.put(field,Access.PRIVATE);
        }

        private Expression lambdaExpression(CppLambdaExpr source,Namespace namespace,Local scope) {
            LambdaInfo info=lambdaExpressions.get(source);
            if(info==null) {
                TypeEntity type=declareClass(freshName("lambda"),false,namespace,source.range());
                type.complete=true;
                info=new LambdaInfo(source,type,scope,currentClass,currentThis,namespace,freshName("captured_this"));
                lambdaExpressions.put(source,info);lambdaTypes.put(type,info);
                if(scope==null && (source.captureDefault()!=CppLambdaExpr.CaptureDefault.NONE||source.captures().stream().anyMatch(capture->capture.initializer()==null)))
                    report("CPP004",source.range(),"A non-local lambda cannot have a capture-default or simple capture.");
                Set<String> names=new HashSet<>();
                for(var capture:source.captures()) {
                    if(!names.add(capture.name()))report("CPP004",capture.range(),"Duplicate lambda capture: "+capture.name());
                    if(capture.kind()==CppLambdaExpr.CaptureKind.THIS||capture.kind()==CppLambdaExpr.CaptureKind.THIS_COPY) {
                        if(capture.initializer()!=null)report("CPP004",capture.range(),"A this capture cannot have an initializer.");
                        if(capture.kind()==CppLambdaExpr.CaptureKind.THIS && source.captureDefault()==CppLambdaExpr.CaptureDefault.COPY)
                            report("CPP004",capture.range(),"[=, this] requires a later C++ language version.");
                        captureLambdaThis(info,capture.kind()==CppLambdaExpr.CaptureKind.THIS_COPY,capture,capture.range());continue;
                    }
                    if(capture.initializer()==null) {
                        Candidate candidate=lookupName(capture.name(),namespace,scope,capture.range());
                        if(!(candidate instanceof Entity entity&&entity.kind==Kind.VARIABLE&&entity.owner==null&&!staticLocalEntities.contains(entity))
                                && !(candidate instanceof ImplicitField field&&lambdaTypes.containsKey(field.owner)))
                            report("CPP004",capture.range(),"A simple capture must name an automatic variable.");
                        if(source.captureDefault()==CppLambdaExpr.CaptureDefault.COPY&&capture.kind()==CppLambdaExpr.CaptureKind.COPY
                                ||source.captureDefault()==CppLambdaExpr.CaptureDefault.REFERENCE&&capture.kind()==CppLambdaExpr.CaptureKind.REFERENCE)
                            report("CPP004",capture.range(),"The simple capture duplicates the capture-default.");
                    }
                    ensureLambdaCapture(info,capture.name(),capture,capture.range());
                }
                for(var parameter:source.parameters())if(names.contains(parameter.name()))report("CPP004",parameter.range(),"A lambda parameter cannot redeclare a capture name.");
                boolean generic=source.parameters().stream().anyMatch(parameter->parameter.type().containsAuto());
                info.generic=generic;
                List<ClassTemplateDecl.Parameter> templateParameters=new ArrayList<>();
                List<Parameter> callParameters=new ArrayList<>();
                for(Parameter parameter:source.parameters()) {
                    MiniType parameterType=parameter.type();
                    if(parameterType.containsAuto()) {
                        if(autoPlaceholder(parameterType).decltypeAuto())report("CPP004",parameter.range(),"decltype(auto) cannot be a lambda parameter type.");
                        var identity=new MiniType.TemplateParameterType(type.canonicalName+"::operator()",templateParameters.size());
                        templateParameters.add(new ClassTemplateDecl.TypeParameter(freshName("lambda_type"),identity,parameter.range()));
                        parameterType=lambdaParameterType(parameterType,identity);
                    }
                    callParameters.add(new Parameter(parameter.name().isEmpty()?freshName("lambda_parameter"):parameter.name(),parameterType,parameter.defaultValue(),parameter.range()));
                }
                MiniType resultType=source.returnType()==MiniType.AUTO?MiniType.AUTO:new MiniType.TrailingReturnType(source.returnType());
                var function=new FunctionDecl("operator()",resultType,callParameters,source.variadic(),source.body(),false,false,
                        source.range(),new OperatorName(OperatorName.Kind.CALL,source.range()));
                var methodSource=new MethodMember(function,!source.mutable(),source.range());
                Method method=null;
                if(generic) {
                    Set<String> localNames=new HashSet<>();source.parameters().forEach(parameter->localNames.add(parameter.name()));
                    collectGenericLambdaCaptures(info,source.body(),localNames);
                    declareMemberTemplate(type,templateParameters,methodSource,null,Access.PUBLIC);
                } else {
                    method=declareMethod(type,methodSource,Access.PUBLIC,namespace);
                    if(method!=null) {
                        int savedDepth=unevaluatedDepth;unevaluatedDepth=0;
                        try{bindMethod(method,namespace);}finally{unevaluatedDepth=savedDepth;}
                    }
                }
                info.complete=true;
                List<CppMember> members=new ArrayList<>();for(StructField field:type.fields)members.add(new FieldMember(field));members.add(generic?new TemplateMethodMember(templateParameters,methodSource):methodSource);
                type.sourceRecord=new StructDecl(type.canonicalName,type.fields,true,false,new CppRecordInfo(RecordKey.CLASS,members,source.range()),source.range());
                var coreFields=type.fields.stream().map(field->new StructField(field.name(),coreType(field.type()),false,List.of(),field.range())).toList();
                var record=new StructDecl(((MiniType.StructType)type.type).name(),coreFields,true,false,source.range());
                structs.add(record);declarations.add(record);
                if(type.fields.stream().anyMatch(field->needsDestruction(field.type()))) {
                    var destructor=declareDestructor(type,new DestructorMember(type.name,new BlockStmt(List.of(),source.range()),source.range(),source.range()),Access.PUBLIC,true);
                    if(destructor!=null)bindDestructor(destructor);
                }
                var defaultConstructor=declareConstructor(type,new ConstructorMember(type.name,List.of(),false,List.of(),null,source.range(),source.range()),Access.PUBLIC,true);
                if(defaultConstructor!=null)deletedConstructors.put(defaultConstructor.function,List.of(lambdaDiagnostic(source.range(),"C++17 closure types have no default constructor.")));
                ensureImplicitCopy(type);ensureImplicitAssignment(type);
                if(type.assignmentPlan!=null)type.assignmentPlan=new AssignmentPlan(type.assignmentPlan.parameterType,type.assignmentPlan.entries,
                        List.of("C++17 closure copy assignment is deleted"),false);
                if(source.captureDefault()==CppLambdaExpr.CaptureDefault.NONE&&source.captures().isEmpty()&&method!=null)
                    lambdaFunctionPointer(info,method);
            }
            LambdaInfo lambda=info;
            String destination=freshName("lambda_destination");
            Expression object=typed(new UnaryExpr(TokenType.STAR,typed(new NameExpr(destination,source.range()),lambda.type.type.pointerTo()),source.range()),lambda.type.type);
            List<Expression> initializations=new ArrayList<>();
            for(LambdaCapture capture:lambda.captures.values()) {
                Expression target=typed(new FieldAccessExpr(object,capture.name,false,capture.initializer.range()),coreType(capture.type));
                Expression value=lambdaOuter(lambda,()->{
                    if(capture.name.equals(lambda.thisCaptureName)) {
                        Expression outer=expression(new ThisExpr(capture.initializer.range()),lambda.namespace,lambda.lexicalScope,true);
                        return capture.type.isPointer()?outer:typed(new UnaryExpr(TokenType.STAR,outer,outer.range()),capture.type);
                    }
                    if(capture.reference)return bindReference(capture.type,capture.initializer,lambda.namespace,lambda.lexicalScope,capture.initializer.range());
                    if(capture.source!=null&&capture.source.initializer()!=null)return variableInitializer(capture.type,capture.source.initializer(),
                            capture.source.initializer(),lambda.namespace,lambda.lexicalScope,capture.source.range(),capture.name);
                    return expression(capture.initializer,lambda.namespace,lambda.lexicalScope,true);
                });
                initializations.add(lambdaInitialize(target,capture.type,value,capture.initializer.range()));
            }
            Expression body=initializations.isEmpty()?typed(new CastExpr(MiniType.VOID,new IntegerLiteralExpr(0,"0",source.range()),source.range()),MiniType.VOID)
                    :typed(new CommaExpr(initializations,source.range()),MiniType.VOID);
            return typed(new ObjectInitExpr(lambda.type.type,destination,body,source.range()),lambda.type.type);
        }

        private MiniType lambdaParameterType(MiniType pattern,MiniType replacement) {
            return switch(pattern) {
                case MiniType.AutoType ignored -> replacement;
                case MiniType.QualifiedType qualified -> MiniType.qualified(lambdaParameterType(qualified.baseType(),replacement),qualified.qualifiers());
                case MiniType.ReferenceType reference -> lambdaParameterType(reference.referent(),replacement).referenceTo(reference.kind());
                case MiniType.PointerType pointer -> lambdaParameterType(pointer.pointee(),replacement).pointerTo();
                case MiniType.ArrayType array -> lambdaParameterType(array.elementType(),replacement).arrayOf(array.length());
                default -> pattern;
            };
        }

        /** Potentially evaluated non-dependent captures are fixed before the closure layout is emitted. */
        private void collectGenericLambdaCaptures(LambdaInfo lambda,AstNode node,Set<String> localNames) {
            if(node==null)return;
            switch(node) {
                case SizeofExpr ignored -> {return;}
                case AlignofExpr ignored -> {return;}
                case TypedefStmt alias -> {localNames.add(alias.name());return;}
                case BlockStmt block -> {
                    Set<String> inner=new HashSet<>(localNames);
                    for(Statement statement:block.statements())collectGenericLambdaCaptures(lambda,statement,inner);return;
                }
                case VarDeclStmt variable -> {
                    localNames.add(variable.name());collectGenericLambdaCaptures(lambda,variable.initializer(),localNames);return;
                }
                case IfStmt selection -> {
                    collectGenericLambdaCaptures(lambda,selection.condition(),localNames);
                    collectGenericLambdaCaptures(lambda,selection.thenBranch(),new HashSet<>(localNames));
                    collectGenericLambdaCaptures(lambda,selection.elseBranch(),new HashSet<>(localNames));return;
                }
                case WhileStmt loop -> {collectGenericLambdaCaptures(lambda,loop.condition(),localNames);collectGenericLambdaCaptures(lambda,loop.body(),new HashSet<>(localNames));return;}
                case DoWhileStmt loop -> {collectGenericLambdaCaptures(lambda,loop.body(),new HashSet<>(localNames));collectGenericLambdaCaptures(lambda,loop.condition(),localNames);return;}
                case CallExpr call -> {
                    if(call.callee() instanceof NameExpr name && !localNames.contains(name.name())) {
                        int before=diagnostics.size();Candidate candidate=lambdaOuter(lambda,()->lookupName(name.name(),lambda.namespace,lambda.lexicalScope,name.range()));
                        diagnostics.subList(before,diagnostics.size()).clear();
                        if(candidate!=null)prepareGenericCaptureName(lambda,name.name(),name.range());
                    } else collectGenericLambdaCaptures(lambda,call.callee(),localNames);
                    for(Expression argument:call.arguments())collectGenericLambdaCaptures(lambda,argument,localNames);return;
                }
                case ForStmt loop -> {
                    Set<String> inner=new HashSet<>(localNames);collectGenericLambdaCaptures(lambda,loop.initializer(),inner);
                    collectGenericLambdaCaptures(lambda,loop.condition(),inner);collectGenericLambdaCaptures(lambda,loop.step(),inner);
                    collectGenericLambdaCaptures(lambda,loop.body(),inner);return;
                }
                case CppRangeForStmt loop -> {
                    collectGenericLambdaCaptures(lambda,loop.initializer(),localNames);
                    Set<String> inner=new HashSet<>(localNames);inner.add(loop.declaration().name());collectGenericLambdaCaptures(lambda,loop.body(),inner);return;
                }
                case CppLambdaExpr nested -> {
                    Set<String> inner=new HashSet<>(localNames);
                    for(var capture:nested.captures()) {
                        if(capture.initializer()!=null)collectGenericLambdaCaptures(lambda,capture.initializer(),localNames);
                        else if(capture.kind()==CppLambdaExpr.CaptureKind.THIS||capture.kind()==CppLambdaExpr.CaptureKind.THIS_COPY)prepareGenericThisCapture(lambda,capture.range());
                        else if(!localNames.contains(capture.name()))prepareGenericCaptureName(lambda,capture.name(),capture.range());
                        inner.add(capture.name());
                    }
                    nested.parameters().forEach(parameter->inner.add(parameter.name()));
                    if(nested.captureDefault()!=CppLambdaExpr.CaptureDefault.NONE)collectGenericLambdaCaptures(lambda,nested.body(),inner);return;
                }
                case NameExpr name -> {if(!localNames.contains(name.name()))prepareGenericCaptureName(lambda,name.name(),name.range());return;}
                case IntegerConstantExpr constant -> {
                    if(constant.lexeme().matches("[A-Za-z_][A-Za-z0-9_]*")&&!localNames.contains(constant.lexeme()))prepareGenericCaptureName(lambda,constant.lexeme(),constant.range());return;
                }
                case ThisExpr self -> {prepareGenericThisCapture(lambda,self.range());return;}
                default -> { }
            }
            for(AstNode child:AstChildren.of(node))collectGenericLambdaCaptures(lambda,child,localNames);
        }

        private void prepareGenericCaptureName(LambdaInfo lambda,String name,SourceRange range) {
            if(lambda.captures.containsKey(name))return;
            Candidate candidate=lambdaOuter(lambda,()->lookupName(name,lambda.namespace,lambda.lexicalScope,range));
            if(candidate instanceof Entity entity&&entity.kind==Kind.VARIABLE&&entity.owner==null&&!staticLocalEntities.contains(entity)
                    ||candidate instanceof ImplicitField field&&lambdaTypes.containsKey(field.owner))ensureLambdaCapture(lambda,name,null,range);
            else if(candidate instanceof ImplicitField||candidate instanceof MethodSet methods&&methods.methods.stream().anyMatch(method->!method.source.staticMember()))prepareGenericThisCapture(lambda,range);
        }

        private void prepareGenericThisCapture(LambdaInfo lambda,SourceRange range) {
            if(lambda.captures.containsKey(lambda.thisCaptureName))return;
            if(lambda.source.captureDefault()==CppLambdaExpr.CaptureDefault.NONE)report("CPP004",range,"The enclosing this object is not captured by this generic lambda.");
            else captureLambdaThis(lambda,false,null,range);
        }

        private void prepareGenericLambdaConversion(MiniType source,MiniType target) {
            LambdaInfo lambda=lambdaTypes.get(objectType(source));
            MiniType requested=objectTypeOfReference(target).unqualified();
            if(lambda==null||!lambda.generic||lambda.source.captureDefault()!=CppLambdaExpr.CaptureDefault.NONE||!lambda.source.captures().isEmpty()
                    ||!requested.isPointer()||!(requested.pointee().unqualified() instanceof MiniType.FunctionType signature)
                    ||lambda.pointerConversions.contains(requested))return;
            MethodSet set=lambda.type.methods.get("operator()");if(set==null)return;
            for(Method declaration:set.methods) {
                Entity entity=deduceFunctionTemplate(declaration.function,signature.parameterTypes(),null,lambda.source.range());
                if(entity==null)continue;
                Method method=entity==declaration.function?declaration:functionTemplateInstances.get(entity).method;
                int savedDepth=unevaluatedDepth;unevaluatedDepth=0;
                try{instantiateMethod(method);}finally{unevaluatedDepth=savedDepth;}
                MiniType actual=MiniType.function(methodReturnType(method),method.parameterTypes,method.source.method().variadic());
                if(actual.equals(signature))lambdaFunctionPointer(lambda,method);
            }
        }

        private Diagnostic lambdaDiagnostic(SourceRange range,String message) {
            return new Diagnostic("CPP004",Diagnostic.Severity.ERROR,message,"Use a directly initialized lambda or copy an existing closure.",range);
        }

        private Expression lambdaInitialize(Expression target,MiniType type,Expression value,SourceRange range) {
            if(type.isArray()) {
                List<Expression> elements=new ArrayList<>();
                for(int index=0;index<type.arrayLength();index++) {
                    Expression subscript=new IntegerLiteralExpr(index,Integer.toString(index),range);
                    MiniType element=MiniType.qualified(type.elementType(),type.qualifiers());
                    elements.add(lambdaInitialize(typed(new IndexExpr(target,subscript,range),coreType(element)),element,
                            typed(new IndexExpr(value,subscript,range),element),range));
                }
                return typed(new CommaExpr(elements,range),MiniType.VOID);
            }
            Expression initialized=type.isReference()?value:type.isStruct()?copyInitialize(type,value,range,CppInitializer.Kind.DIRECT_PAREN):convertCallValue(type,value,value);
            return typed(new InitializeExpr(target,initialized,range),MiniType.VOID);
        }

        /** A captureless lambda's conversion points at an ordinary ABI function, never a this-bearing method. */
        private void lambdaFunctionPointer(LambdaInfo lambda,Method method) {
            MiniType pointerType=MiniType.function(methodReturnType(method),method.parameterTypes,method.source.method().variadic()).pointerTo();
            if(lambda.pointerConversions.contains(pointerType))return;
            FunctionDecl call=functions.stream().filter(function->function.name().equals(method.function.coreName)&&function.hasBody()).findFirst().orElse(null);
            if(call==null)return;
            lambda.pointerConversions.add(pointerType);
            MiniType signature=MiniType.function(methodReturnType(method),method.parameterTypes,method.source.method().variadic());
            String name=freshName("lambda_function");
            Entity thunk=new Entity(name,name,Kind.FUNCTION,lambda.namespace,signature,null,true);
            coreValues.put(name,thunk);lambda.namespace.values.put(name,new OverloadSet(List.of(thunk)));
            var function=new FunctionDecl(name,call.returnType(),call.parameters().subList(1,call.parameters().size()),call.variadic(),call.body(),false,false,call.range());
            functions.add(function);declarations.add(function);
            MiniType target=signature.pointerTo();SourceRange range=lambda.source.range();
            ConversionName conversion=new ConversionName(target,false,range);
            var body=new BlockStmt(List.of(new ReturnStmt(new NameExpr(name,range),range)),range);
            var declaration=new FunctionDecl(conversion.spelling(),target,List.of(),false,body,false,false,range,null,conversion);
            Method converted=declareMethod(lambda.type,new MethodMember(declaration,true,range),Access.PUBLIC,lambda.namespace);
            if(converted!=null)bindMethod(converted,lambda.namespace);
        }

        /** C++17 exposition lowering: one range object, one begin/end pair, one scoped element per iteration. */
        private Statement rangeFor(CppRangeForStmt node, Local parent) {
            Namespace namespace=parent.namespace;
            Local loop=new Local(parent,namespace);
            Expression range;
            if(isBraced(node.initializer())) {
                var syntax=new CppInitializer(CppInitializer.Kind.COPY_LIST,listItems(node.initializer()),node.initializer().range());
                MiniType type=deduceVariableType(MiniType.AUTO,syntax,
                        new AggregateInitExpr(syntax.arguments(),syntax.range()),namespace,parent,syntax.range());
                range=bindListValue(type,syntax,namespace,parent);
            } else range=expression(node.initializer(),namespace,parent,true);
            MiniType type=declaredExpressionType(range);
            if(type==null || !(type.isArray()||type.isStruct())) {
                report("CPP004",node.initializer().range(),"A range-for initializer must provide an array or begin/end customization.");
                return new BlockStmt(List.of(),node.range());
            }
            requireComplete(type,node.initializer().range());
            Expression pointer=valueCategory(range)==CppValueCategory.LVALUE || addressableObject(range)
                    ? address(range) : materialize(type,range);
            pointer=extendTemporaryLifetime(pointer,new TemporaryLifetime(TemporaryLifetime.Kind.REFERENCE_SCOPE,node));
            Entity rangeEntity=declareLocal(freshName("range"),valueCategory(range)==CppValueCategory.LVALUE
                    ? type.referenceTo() : type.rvalueReferenceTo(),loop,node.initializer().range());
            Statement rangeVariable=boundRangeLocal(rangeEntity,pointer,node.initializer(),node);
            Expression rangeName=new NameExpr(rangeEntity.name,node.initializer().range());
            Expression begin;
            Expression end;
            if(type.isArray()) {
                MiniType element=MiniType.qualified(type.elementType(),type.qualifiers());
                begin=typed(new CastExpr(coreType(element).pointerTo(),expression(rangeName,namespace,loop),rangeName.range()),element.pointerTo());
                end=typed(new BinaryExpr(expression(rangeName,namespace,loop),TokenType.PLUS,
                        new IntegerLiteralExpr(type.arrayLength(),Integer.toString(type.arrayLength()),rangeName.range()),rangeName.range()),element.pointerTo());
            } else {
                TypeEntity owner=objectType(type);
                boolean members=rangeMember(owner,"begin")&&rangeMember(owner,"end");
                begin=rangeAccessCall("begin",rangeName,type,members,namespace,loop);
                end=rangeAccessCall("end",rangeName,type,members,namespace,loop);
            }
            MiniType beginType=checkedDeduced(declaredExpressionType(begin),node.initializer().range());
            MiniType endType=checkedDeduced(declaredExpressionType(end),node.initializer().range());
            beginType=TypeCompatibility.decay(beginType).unqualified();
            endType=TypeCompatibility.decay(endType).unqualified();
            Entity beginEntity=declareLocal(freshName("range_begin"),beginType,loop,node.range());
            Statement beginVariable=boundRangeLocal(beginEntity,convertCallValue(beginType,begin,node.initializer()),node.initializer(),node);
            Entity endEntity=declareLocal(freshName("range_end"),endType,loop,node.range());
            Statement endVariable=boundRangeLocal(endEntity,convertCallValue(endType,end,node.initializer()),node.initializer(),node);
            var beginName=new NameExpr(beginEntity.name,node.declaration().range());
            var endName=new NameExpr(endEntity.name,node.declaration().range());
            Expression condition=fullExpression(contextualBool(expression(new BinaryExpr(beginName,TokenType.BANG_EQUAL,endName,node.range()),namespace,loop)),false,node);
            Expression step=fullExpression(expression(new UnaryExpr(TokenType.PLUS_PLUS,beginName,node.range()),namespace,loop),false,node);
            Local iteration=new Local(loop,namespace);
            Expression element=new UnaryExpr(TokenType.STAR,beginName,node.declaration().range());
            var declaration=node.declaration();
            var syntax=new CppInitializer(CppInitializer.Kind.COPY,List.of(element),declaration.range());
            var initialized=new VarDeclStmt(declaration.name(),declaration.type(),element,declaration.alignmentSpecs(),syntax,declaration.range());
            Statement variable=statement(initialized,iteration);
            mapped(declaration,variable);
            // Unlike an ordinary nested block, the body's outermost declarations share the range variable scope.
            Statement body=node.body() instanceof BlockStmt block ? block(block,iteration,false) : body(node.body(),iteration);
            List<Statement> iterationStatements=new ArrayList<>(localPreludes.getOrDefault(variable,List.of()));
            iterationStatements.add(variable);iterationStatements.add(body);
            Statement iterationBody=new BlockStmt(withLocalCleanups(iterationStatements),node.body().range());
            Statement forLoop=new ForStmt(null,condition,step,iterationBody,node.range());
            List<Statement> sequence=new ArrayList<>();
            for(Statement variableStatement:List.of(rangeVariable,beginVariable,endVariable)) {
                sequence.addAll(localPreludes.getOrDefault(variableStatement,List.of()));sequence.add(variableStatement);
            }
            sequence.add(forLoop);
            return new BlockStmt(withLocalCleanups(sequence),node.range());
        }

        private Statement boundRangeLocal(Entity entity,Expression initializer,Expression origin,AstNode owner) {
            MiniType type=entity.type;
            var lifetime=lowerLifetime(initializer,type.isStruct()||type.isArray(),owner);
            var variable=new VarDeclStmt(entity.coreName,coreType(type),lifetime.expression(),origin.range());
            if(!lifetime.declarations().isEmpty())localPreludes.put(variable,lifetime.declarations());
            Expression storage=typed(new NameExpr(entity.coreName,origin.range()),coreType(type));
            Expression cleanup=destruction(type,address(storage),origin.range());
            if(lifetime.scopeCleanup()!=null)cleanup=cleanup==null?lifetime.scopeCleanup()
                    :new CommaExpr(List.of(cleanup,lifetime.scopeCleanup()),origin.range());
            if(cleanup!=null)localCleanups.put(variable,cleanup);
            return variable;
        }

        private boolean rangeMember(TypeEntity owner,String name) {
            return owner!=null&&(owner.methods.containsKey(name)||owner.staticFields.containsKey(name)
                    ||owner.memberTypes.containsKey(name)||fieldPath(owner.type,name,new HashSet<>())!=null);
        }

        private Expression rangeAccessCall(String name,Expression range,MiniType type,boolean members,Namespace namespace,Local scope) {
            if(members)return expression(new CallExpr(new FieldAccessExpr(range,name,false,range.range()),List.of(),range.range()),namespace,scope);
            Set<Namespace> associated=new LinkedHashSet<>();
            rangeAssociatedNamespaces(type,associated,new HashSet<>());
            Set<Entity> candidates=new LinkedHashSet<>();
            for(Namespace owner:associated)if(owner.values.get(name) instanceof OverloadSet set)candidates.addAll(set.functions);
            if(candidates.isEmpty()) {
                report("CPP004",range.range(),"No ADL-only "+name+" customization exists for this range.");
                return typed(new IntegerLiteralExpr(0,"0",range.range()),MiniType.INT);
            }
            var callee=new NameExpr(name,range.range());
            var call=new CallExpr(callee,List.of(range),range.range());
            BoundCallee binding=bindOverloadedCall(new OverloadSet(new ArrayList<>(candidates)),callee,callee,call.arguments(),namespace,scope,null,null);
            return bindCallExpression(call,binding,namespace,scope);
        }

        private void rangeAssociatedNamespaces(MiniType type,Set<Namespace> associated,Set<String> seen) {
            associatedNamespaces(type,associated);
            TypeEntity owner=objectType(type);
            if(owner==null||!seen.add(owner.canonicalName))return;
            MiniType.TemplateIdType instance=instanceKeys.get(owner);
            if(instance!=null)for(var argument:instance.arguments())if(argument instanceof TemplateArgument.Type item)
                rangeAssociatedNamespaces(item.type(),associated,seen);
        }

        private Expression fullExpression(Expression value, boolean resultOwned, AstNode owner) {
            var result = lowerLifetime(value, resultOwned, owner);
            if (!result.declarations().isEmpty()) report("CPP005", owner.range(),
                    "This reference temporary cannot be extended outside a local declaration.");
            return result.expression();
        }

        private CppLifetimeLowering.Result lowerLifetime(Expression value, boolean resultOwned, AstNode owner) {
            return new CppLifetimeLowering(new CppLifetimeLowering.Context() {
                public MiniType type(Expression expression) { return declaredExpressionType(expression); }
                public Expression staticMaterialize(MaterializeExpr temporary) {
                    return staticInitialization && temporary.lifetime().kind() == TemporaryLifetime.Kind.REFERENCE_SCOPE
                            ? staticMaterialization(temporary) : null;
                }
                public boolean needsDestruction(MiniType type) { return Binding.this.needsDestruction(type); }
                public Expression destroy(MiniType type, Expression address, SourceRange range) {
                    return destruction(type, address, range);
                }
                public Expression copy(MiniType type, Expression expression, SourceRange range) {
                    Expression copied = copyInitialize(type, expression, range, CppInitializer.Kind.COPY);
                    return ObjectInitExpr.occursInResultOf(copied) ? copied : recordPrvalue(type, copied, range);
                }
                public String freshName(String display) { return Binding.this.freshName(display); }
                public Expression remap(Expression original, Expression result) {
                    if (declaredExpressionTypes.containsKey(original)) declaredExpressionTypes.put(result, declaredExpressionTypes.get(original));
                    if (valueCategories.containsKey(original)) valueCategories.put(result, valueCategories.get(original));
                    if (temporaryAddressPaths.contains(original)) temporaryAddressPaths.add(result);
                    origins.replaceAll((source, core) -> core == original ? result : core);
                    return result;
                }
            }, owner).lower(value, resultOwned);
        }

        private Expression unevaluatedExpression(Expression node, Namespace namespace, Local local) {
            unevaluatedDepth++;
            try { return expression(node, namespace, local); }
            finally { unevaluatedDepth--; }
        }

        private Expression expression(Expression node, Namespace namespace, Local local) {
            return expression(node, namespace, local, false);
        }

        /** Address-demand contexts preserve C++ object identity without an extra value read. */
        private Expression expression(Expression node, Namespace namespace, Local local, boolean addressDemand) {
            if (node == null) return null;
            Expression savedOwner = fullExpressionOwner;
            if (fullExpressionOwner == null) fullExpressionOwner = node;
            try { return expressionWithinFullExpression(node, namespace, local, addressDemand); }
            finally { fullExpressionOwner = savedOwner; }
        }

        private Expression expressionWithinFullExpression(Expression node, Namespace namespace, Local local, boolean addressDemand) {
            Expression core = switch (node) {
                case CppLambdaExpr n -> lambdaExpression(n,namespace,local);
                case CppInitializer n -> {
                    if (!isList(n)) report("CPP004", n.range(), "A naked initializer clause must use braces.");
                    bracedArguments.computeIfAbsent(n, key -> prepareArguments(n.arguments(), namespace, local));
                    yield n;
                }
                case CppConstructionExpr n -> constructionExpression(n, namespace, local);
                case CppTypeQueryExpr n -> typeQuery(n, namespace, local);
                case CppTypeMemberExpr n -> memberReference(typeMember(n, namespace, local), n.memberName(), n.range(), addressDemand);
                case CppDestructorCallExpr n -> explicitDestruction(n, namespace, local);
                case CppNewExpr n -> placementConstruction(n, namespace, local);
                case ThisExpr n -> {
                    if(lambdaTypes.containsKey(currentClass))yield lambdaThis(lambdaTypes.get(currentClass),n.range());
                    if (currentThis == null) {
                        report("CPP004", n.range(), "this 只能用于非静态成员函数体内。");
                        yield n;
                    }
                    yield thisValue(n.range());
                }
                case NameExpr n -> simpleReference(n.name(), n.range(), namespace, local, addressDemand);
                case QualifiedNameExpr n -> memberReference(resolveQualifiedName(n.name(), namespace, local),
                        n.name().segments().getLast(), n.range(), addressDemand);
                case AssignmentExpr n -> {
                    Expression target = expression(n.target(), namespace, local, true);
                    MiniType targetType = declaredExpressionType(target);
                    if (valueCategory(target) != CppValueCategory.LVALUE && (targetType == null || !targetType.isStruct())) {
                        report("CPP004", n.target().range(), "内置赋值要求可修改的左值，临时对象的标量子对象不是左值。");
                    }
                    requireComplete(targetType, n.range());
                    if (n.operator() == TokenType.PLUS_EQUAL || n.operator() == TokenType.MINUS_EQUAL) {
                        requireComplete(elementType(declaredExpressionType(target)), n.range());
                    }
                    Expression value = n.compoundBinaryOperator().isEmpty() && !(objectType(targetType) != null && isBraced(n.value()))
                            ? expressionForTarget(targetType, n.value(), namespace, local) : expression(n.value(), namespace, local);
                    if (objectType(targetType) != null) {
                        if (n.operator() == TokenType.EQUAL)
                            yield operatorExpression("operator=", n, List.of(n.target(), n.value()),
                                    List.of(target, value), namespace, local, true);
                    }
                    if (n.compoundBinaryOperator().isPresent()) {
                        Expression overloaded = operatorExpression(operatorName(n.operator()), n,
                                List.of(n.target(), n.value()), List.of(target, value), namespace, local, false);
                        if (overloaded != null) yield overloaded instanceof AssignmentExpr builtin
                                ? normalizedAssignment(builtin, addressDemand) : overloaded;
                    }
                    if (n.operator() == TokenType.EQUAL) value = convertCallValue(targetType, value, n.value());
                    AssignmentExpr assignment = new AssignmentExpr(target, n.operator(), value, n.range());
                    yield normalizedAssignment(assignment, addressDemand);
                }
                case BinaryExpr n -> {
                    Expression left = expression(n.left(), namespace, local);
                    Expression right = expression(n.right(), namespace, local);
                    Expression overloaded = operatorExpression(operatorName(n.operator()), n,
                            List.of(n.left(), n.right()), List.of(left, right), namespace, local, false);
                    if (overloaded != null) yield overloaded;
                    if (n.operator() == TokenType.PLUS || n.operator() == TokenType.MINUS) {
                        requireComplete(elementType(declaredExpressionType(left)), n.range());
                        requireComplete(elementType(declaredExpressionType(right)), n.range());
                    }
                    Expression operation = new BinaryExpr(left, n.operator(), right, n.range());
                    yield hasBooleanResult(n.operator()) ? booleanResult(operation) : operation;
                }
                case ConditionalExpr n -> {
                    Expression condition = contextualBool(expression(n.condition(), namespace, local));
                    Expression first = expression(n.thenExpression(), namespace, local, addressDemand);
                    Expression second = expression(n.elseExpression(), namespace, local, addressDemand);
                    List<Expression> convertedBranches = conditionalConversions(first, second, n.range());
                    first = convertedBranches.get(0); second = convertedBranches.get(1);
                    MiniType firstType = declaredExpressionType(first), secondType = declaredExpressionType(second);
                    MiniType common = conditionalLvalueType(firstType, secondType);
                    boolean lvalues = valueCategory(first) == CppValueCategory.LVALUE && valueCategory(second) == CppValueCategory.LVALUE;
                    boolean xvalues=valueCategory(first)==CppValueCategory.XVALUE&&valueCategory(second)==CppValueCategory.XVALUE;
                    boolean temporarySubobjects = valueCategory(first) == CppValueCategory.PRVALUE
                            && valueCategory(second) == CppValueCategory.PRVALUE && addressableObject(first) && addressableObject(second);
                    if (common != null && (lvalues || xvalues || temporarySubobjects)) {
                        if (addressDemand || xvalues || common.isArray() || common.isFunction() || common.isStruct()) {
                            // Aggregate values are represented by addresses. In this uncommon
                            // path rebind compound lvalue arms for addresses, never evaluating them.
                            if (!addressableObject(first)) first = expression(n.thenExpression(), namespace, local, true);
                            if (!addressableObject(second)) second = expression(n.elseExpression(), namespace, local, true);
                            if (addressableObject(first) && addressableObject(second)) {
                                Expression selected = new ConditionalExpr(condition, qualifiedAddress(first, common),
                                        qualifiedAddress(second, common), n.range());
                                Expression object = typed(new UnaryExpr(TokenType.STAR, selected, n.range()), common);
                                temporaryAddressPaths.add(object);
                                valueCategories.put(object, lvalues ? CppValueCategory.LVALUE : xvalues?CppValueCategory.XVALUE:CppValueCategory.PRVALUE);
                                yield object;
                            }
                        } else {
                            // Value use of assignment/update must reuse its result, especially
                            // for volatile objects. Keep the narrow glvalue type for sizeof too.
                            Expression selected = new ConditionalExpr(condition, first, second, n.range());
                            Expression value = typed(new CastExpr(coreType(common), selected, n.range()), common);
                            valueCategories.put(value, lvalues ? CppValueCategory.LVALUE : xvalues?CppValueCategory.XVALUE:CppValueCategory.PRVALUE);
                            yield value;
                        }
                    }
                    Expression selected = new ConditionalExpr(condition, first, second, n.range());
                    // C++ keeps a common integer type after lvalue-to-rvalue conversion. Core C
                    // promotes narrow integers here; restore char/short/bool without affecting
                    // mixed-type arithmetic conversions or the glvalue paths above.
                    yield firstType != null && secondType != null && firstType.isIntegerScalar()
                            && firstType.unqualified().equals(secondType.unqualified())
                            ? typed(new CastExpr(firstType.unqualified(), selected, n.range()), firstType.unqualified())
                            : selected;
                }
                case CallExpr n -> bindCallExpression(n, bindCallee(n.callee(), n.arguments(), namespace, local), namespace, local);
                case CastExpr n -> {
                    MiniType type = normalizeType(n.targetType(), namespace, local, n.range());
                    Expression operand = expressionForTarget(type, n.operand(), namespace, local);
                    yield explicitConversion(type, operand, n.range());
                }
                case CommaExpr n -> {
                    Expression folded = expression(n.expressions().getFirst(), namespace, local, true);
                    for (int index = 1; index < n.expressions().size(); index++) {
                        Expression sourceRight = n.expressions().get(index);
                        Expression right = expression(sourceRight, namespace, local, true);
                        Expression prefix = index == 1 ? n.expressions().getFirst()
                                : new CommaExpr(n.expressions().subList(0, index), n.range());
                        Expression overloaded = operatorExpression("operator,", n, List.of(prefix, sourceRight),
                                List.of(folded, right), namespace, local, false);
                        folded = overloaded != null ? overloaded : builtinComma(folded, right, n.range());
                    }
                    yield folded;
                }
                case FieldAccessExpr n -> {
                    Expression target = expression(n.target(), namespace, local, !n.viaPointer());
                    if (n.viaPointer()) target = arrowReceiver(target, n, namespace, local);
                    if (!n.viaPointer()) target = materializedReceiver(target);
                    MiniType owner = declaredExpressionType(target);
                    owner = n.viaPointer() ? elementType(owner) : owner;
                    requireComplete(owner, n.range());
                    StaticField staticField = staticField(owner, n.fieldName());
                    if (staticField != null) yield evaluateReceiver(target, staticFieldReference(staticField, n.range(), addressDemand), n.range());
                    MethodSet methods = memberMethods(owner, n.fieldName());
                    if (methods != null) yield evaluateReceiver(target, methodReference(methods, n.fieldName(), n.range()), n.range());
                    else requireAccessible(owner, n.fieldName(), n.range(), "数据成员访问");
                    Expression field = fieldReference(target, n.fieldName(), n.viaPointer(), owner, n.range());
                    MiniType memberType = declaredFieldType(owner, n.fieldName(), new HashSet<>());
                    valueCategories.put(field, n.viaPointer() || memberType != null && memberType.isReference()
                            ? CppValueCategory.LVALUE : valueCategory(target)==CppValueCategory.PRVALUE?CppValueCategory.XVALUE:valueCategory(target));
                    yield field;
                }
                case GroupingExpr n -> new GroupingExpr(expression(n.expression(), namespace, local, addressDemand), n.range());
                case IndexExpr n -> {
                    Expression target = expression(n.target(), namespace, local, true);
                    Expression index = expression(n.index(), namespace, local);
                    Expression overloaded = operatorExpression("operator[]", n, List.of(n.target(), n.index()),
                            List.of(target, index), namespace, local, true);
                    if (overloaded != null) yield overloaded;
                    requireComplete(elementType(declaredExpressionType(target)), n.range());
                    Expression indexed = new IndexExpr(target, index, n.range());
                    MiniType targetType = declaredExpressionType(target);
                    valueCategories.put(indexed, targetType != null && targetType.isArray()
                            ? valueCategory(target) : CppValueCategory.LVALUE);
                    yield indexed;
                }
                case UnaryExpr n -> {
                    boolean update = n.operator() == TokenType.PLUS_PLUS || n.operator() == TokenType.MINUS_MINUS;
                    Expression operand = expression(n.operand(), namespace, local, update || n.operator() == TokenType.AMPERSAND);
                    Expression overloaded = operatorExpression(operatorName(n.operator()), n,
                            List.of(n.operand()), List.of(operand), namespace, local, false);
                    if (overloaded != null) {
                        if (update && addressDemand && overloaded instanceof UnaryExpr operation
                                && (operation.operator() == TokenType.PLUS_PLUS || operation.operator() == TokenType.MINUS_MINUS))
                            yield addressUpdateResult(operation);
                        yield overloaded;
                    }
                    if (n.operator() == TokenType.AMPERSAND && valueCategory(operand) != CppValueCategory.LVALUE) {
                        report("CPP004", n.range(), "内置取址要求左值或函数，不能对临时对象的子对象取址。");
                    }
                    if (n.operator() == TokenType.PLUS_PLUS || n.operator() == TokenType.MINUS_MINUS) {
                        requireUpdateOperand(operand, n.range());
                    }
                    if (update && addressDemand && addressableObject(operand) && declaredExpressionType(operand) != null) {
                        MiniType type = declaredExpressionType(operand);
                        Capture pointer = capture(type.pointerTo(), address(operand), n.range());
                        Expression target = typed(new UnaryExpr(TokenType.STAR, pointer.name(), operand.range()), type);
                        Expression body = new CommaExpr(List.of(new UnaryExpr(n.operator(), target, n.range()), pointer.name()), n.range());
                        yield typed(new UnaryExpr(TokenType.STAR, pointer.wrap(body), n.range()), type);
                    }
                    if (n.operator() == TokenType.AMPERSAND && operand instanceof CommaExpr comma) {
                        List<Expression> values = new ArrayList<>(comma.expressions());
                        values.set(values.size() - 1, address(values.getLast()));
                        yield typed(new CommaExpr(values, n.range()), declaredExpressionType(operand).pointerTo());
                    }
                    Expression operation = new UnaryExpr(n.operator(), operand, n.range());
                    yield n.operator() == TokenType.BANG ? booleanResult(operation) : operation;
                }
                case PostfixUpdateExpr n -> {
                    Expression target = expression(n.target(), namespace, local, true);
                    Expression zero = new IntegerLiteralExpr(0, "0", n.range());
                    Expression overloaded = operatorExpression(operatorName(n.operator()), n,
                            List.of(n.target(), zero), List.of(target, zero), namespace, local, false);
                    if (overloaded != null) yield overloaded;
                    requireUpdateOperand(target, n.range());
                    yield new PostfixUpdateExpr(target, n.operator(), n.range());
                }
                case CppTemplateIdExpr n -> {
                    OverloadDesignator designator=overloadDesignator(n,namespace,local);
                    if(designator==null){report("CPP004",n.range(),"Template-id does not denote a function template");yield new IntegerLiteralExpr(0,"0",n.range());}
                    var matches=new ArrayList<Entity>();
                    for(Entity candidate:designator.set.functions) {
                        var definition=functionTemplates.get(candidate);if(definition==null)continue;
                        List<MiniType> unknown=new ArrayList<>();for(int i=0;i<definition.source.parameters().size();i++)unknown.add(null);
                        Entity instance=deduceFunctionTemplate(candidate,unknown,designator.explicit,n.range());if(instance!=null)matches.add(instance);
                    }
                    if(matches.size()!=1){report("CPP004",n.range(),"Function template-id needs a unique specialization or target function type");yield new IntegerLiteralExpr(0,"0",n.range());}
                    yield new NameExpr(functionReferenceName(matches.getFirst()),n.range());
                }
                case SizeofExpr n -> {
                    rejectUnevaluatedLambdas(n.expression());
                    MiniType type = objectTypeOfReference(normalizeType(n.queriedType(), namespace, local, n.range()));
                    Expression operand = unevaluatedExpression(n.expression(), namespace, local);
                    requireComplete(type != null ? type : declaredExpressionType(operand), n.range());
                    yield new SizeofExpr(operand, coreType(type), n.range());
                }
                case AlignofExpr n -> {
                    rejectUnevaluatedLambdas(n.expression());
                    MiniType type = objectTypeOfReference(normalizeType(n.queriedType(), namespace, local, n.range()));
                    Expression operand = unevaluatedExpression(n.expression(), namespace, local);
                    requireComplete(type != null ? type : declaredExpressionType(operand), n.range());
                    yield new AlignofExpr(operand, coreType(type), n.range());
                }
                case VaStartExpr n -> new VaStartExpr(expression(n.list(), namespace, local), expression(n.lastParameter(), namespace, local), n.range());
                case VaArgExpr n -> {
                    MiniType type = normalizeType(n.requestedType(), namespace, local, n.range());
                    if (type.containsReference()) report("CPP005", n.range(), "可变参数中的引用类型尚未实现。");
                    requireComplete(type, n.range());
                    yield new VaArgExpr(expression(n.list(), namespace, local), coreType(type), n.range());
                }
                case VaCopyExpr n -> new VaCopyExpr(expression(n.destination(), namespace, local), expression(n.source(), namespace, local), n.range());
                case VaEndExpr n -> new VaEndExpr(expression(n.list(), namespace, local), n.range());
                case AggregateInitExpr n -> new AggregateInitExpr(expressions(n.values(), namespace, local), n.range());
                case DesignatedInitExpr n -> new DesignatedInitExpr(n.designators(), expression(n.value(), namespace, local), n.range());
                case IntegerConstantExpr n -> {
                    // The C parser substitutes enum identifiers early. Restore lexical shadowing in C++.
                    if (n.lexeme().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        yield simpleReference(n.lexeme(), n.range(), namespace, local);
                    }
                    yield n;
                }
                case BoolLiteralExpr n -> n;
                case CharLiteralExpr n -> n;
                case DoubleLiteralExpr n -> n;
                case FloatLiteralExpr n -> n;
                case IntegerLiteralExpr n -> n;
                case LongLiteralExpr n -> n;
                case NullLiteralExpr n -> n;
                case StringLiteralExpr n -> {
                    MiniType array = stringLiteralType(n);
                    // Core storage is still the ordinary static string address. Dereferencing a
                    // pointer-to-array preserves the C++ lvalue type without loading its bytes.
                    // Keep the core pointer literal distinct from its source array node so
                    // typeOf(source) cannot observe the inner pointer's implementation type.
                    Expression bytes = new StringLiteralExpr(n.value(), n.encoding(), n.lexeme(), n.range());
                    Expression storage = new CastExpr(array.pointerTo(), bytes, n.range());
                    yield typed(new UnaryExpr(TokenType.STAR, storage, n.range()), array);
                }
                default -> {
                    report("CPP005", node.range(), "尚未支持此 C++ 表达式：" + node.getClass().getSimpleName());
                    yield node;
                }
            };
            CppValueCategory category = node instanceof AssignmentExpr
                    || node instanceof UnaryExpr unary && (unary.operator() == TokenType.PLUS_PLUS || unary.operator() == TokenType.MINUS_MINUS)
                    ? CppValueCategory.LVALUE : valueCategory(core);
            valueCategories.put(node, category);
            valueCategories.put(core, category);
            return mapped(node, core);
        }

        /** The cast preserves this's prvalue nature while using the ordinary pointer ABI. */
        private Expression thisValue(SourceRange range) {
            return new CastExpr(currentThis.type, new NameExpr(currentThis.coreName, range), range);
        }

        private record Capture(NameExpr name, MiniType type, Expression initializer, SourceRange range) {
            Expression wrap(Expression body) { return new LetExpr(name.name(), type, initializer, body, range); }
            Expression wrap(Expression body, SourceRange sourceRange) {
                return new LetExpr(name.name(), type, initializer, body, sourceRange);
            }
        }

        private Capture capture(MiniType type, Expression initializer, SourceRange range) {
            MiniType valueType = type.unqualified();
            NameExpr name = typed(new NameExpr(freshName("<expression value>"), range), valueType);
            return new Capture(name, coreType(valueType), initializer, range);
        }

        private <T extends Expression> T typed(T expression, MiniType type) {
            declaredExpressionTypes.put(expression, type);
            return expression;
        }

        private static boolean hasBooleanResult(TokenType operator) {
            return switch (operator) {
                case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL, EQUAL_EQUAL, BANG_EQUAL,
                        AMPERSAND_AMPERSAND, PIPE_PIPE -> true;
                default -> false;
            };
        }

        /** A selected callee can also come from ADL-only range customization lookup. */
        private Expression bindCallExpression(CallExpr n, BoundCallee binding, Namespace namespace, Local local) {
                    Expression callee = binding.expression();
                    if (objectType(declaredExpressionType(callee)) != null) {
                        List<Expression> sources = new ArrayList<>(); sources.add(n.callee()); sources.addAll(n.arguments());
                        List<Expression> values = new ArrayList<>(); values.add(callee);
                        values.addAll(expressions(n.arguments(), namespace, local));
                        return operatorExpression("operator()", n, sources, values, namespace, local, true);
                    }
                    MiniType.FunctionType signature = functionSignature(declaredExpressionType(callee));
                    if (signature != null) {
                        if(n!=decltypeOperand) requireComplete(signature.returnType(), n.range());
                        signature.parameterTypes().forEach(t -> requireComplete(t, n.range()));
                        requireSupportedCallLifetime(signature.returnType(), signature.parameterTypes(), n.range());
                        if(n!=decltypeOperand) destructorForUse(signature.returnType(), n.range());
                        signature.parameterTypes().forEach(type -> destructorForUse(type, n.range()));
                    }
                    List<Expression> arguments = new ArrayList<>();
                    if (binding.receiver() != null) arguments.add(binding.receiver());
                    int offset = arguments.size();
                    if (binding.arguments() != null) arguments.addAll(binding.arguments());
                    else for (int index = 0; index < n.arguments().size(); index++) {
                        Expression argument = n.arguments().get(index);
                        MiniType parameter = signature != null && index + offset < signature.parameterTypes().size()
                                ? signature.parameterTypes().get(index + offset) : null;
                        arguments.add(parameter != null && parameter.isReference()
                                ? bindReference(parameter, argument, namespace, local, argument.range())
                                : convertCallValue(parameter, expressionForTarget(parameter, argument, namespace, local), argument));
                    }
                    List<Integer> evaluationOrder = new ArrayList<>();
                    if (binding.receiver() != null) evaluationOrder.add(0);
                    for (int index : n.argumentEvaluationOrder()) evaluationOrder.add(index + offset);
                    for(int index=n.arguments().size()+offset;index<arguments.size();index++)evaluationOrder.add(index);
                    CallExpr call = new CallExpr(callee, arguments, evaluationOrder, n.range());
                    if (signature != null) declaredExpressionTypes.put(call, signature.returnType().isReference()
                            ? coreType(signature.returnType()) : signature.returnType());
                    if (signature != null && signature.returnType().isReference()) {
                        return referenceResult(signature.returnType(),call,n.range());
                    }
                    return signature != null && signature.returnType().isStruct()
                            ? recordPrvalue(signature.returnType(), call, n.range()) : call;
        }

        private MiniType stringLiteralType(StringLiteralExpr literal) {
            MiniType element = switch (literal.encoding()) {
                case ORDINARY, UTF8 -> MiniType.CHAR;
                case UTF16 -> MiniType.UNSIGNED_SHORT;
                case UTF32 -> MiniType.UNSIGNED_INT;
            };
            // Match StringLiteralRegistry's encoding, counting code units plus the terminator.
            int units = switch (literal.encoding()) {
                case ORDINARY, UTF8 -> literal.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                case UTF16 -> literal.value().getBytes(java.nio.charset.StandardCharsets.UTF_16LE).length / 2;
                case UTF32 -> literal.value().codePointCount(0, literal.value().length());
            };
            return MiniType.qualified(element, Set.of(MiniType.TypeQualifier.CONST)).arrayOf(units + 1);
        }

        /** Preserve the source bool type while retaining core operand validation and short-circuit IR. */
        private Expression booleanResult(Expression operation) {
            return typed(new CastExpr(MiniType.BOOL, operation, operation.range()), MiniType.BOOL);
        }

        private Expression address(Expression value) {
            MiniType type = declaredExpressionType(value);
            return typed(new UnaryExpr(TokenType.AMPERSAND, value, value.range()), type == null ? null : type.pointerTo());
        }

        private Expression explicitDestruction(CppDestructorCallExpr source, Namespace namespace, Local local) {
            Expression receiver = expression(source.receiver(), namespace, local, !source.viaPointer());
            if (source.viaPointer()) receiver = arrowReceiver(receiver, source, namespace, local);
            MiniType receiverType = declaredExpressionType(receiver);
            MiniType object = source.viaPointer() ? elementType(receiverType) : receiverType;
            if (object == null || object.isVoid() || object.isArray() || object.isFunction()
                    || !(object.isStruct() || object.isScalar() || object.isPointer() || object.isNullPointer())) {
                report("CPP004", source.nameRange(), "A destructor call requires an object or a pointer to an object.");
                return typed(new CastExpr(MiniType.VOID, receiver, source.range()), MiniType.VOID);
            }
            TypeEntity owner = objectType(object);
            // [basic.lookup.classref]: either lookup may match. A surrounding T naming
            // another type must not hide the receiver class's injected T.
            boolean injected = owner != null && source.destructorName().segments().size() == 1
                    && owner.name.equals(source.destructorName().segments().getFirst());
            MiniType named = injected ? object : normalizeType(source.ownerType(), namespace, local, source.nameRange());
            if (named == null || !named.unqualified().equals(object.unqualified())) {
                report("CPP004", source.nameRange(), "The destructor name must denote the receiver object's type.");
            }
            if (object.isStruct() && (owner == null || !owner.complete)) {
                report("CPP004", source.nameRange(), "An explicit destructor call requires a complete class type.");
            }
            Expression evaluated;
            if (source.viaPointer()) evaluated = receiver;
            else if (object.isStruct()) evaluated = address(materializedReceiver(receiver));
            else evaluated = valueCategory(receiver) == CppValueCategory.LVALUE && addressableObject(receiver)
                        ? address(receiver) : receiver;
            Expression cleanup = object.isStruct() ? destruction(object, evaluated, source.range()) : null;
            // Trivial and pseudo-destructors still evaluate the postfix receiver once.
            // Scalar lvalues do not undergo an invented lvalue-to-rvalue conversion.
            return cleanup != null ? cleanup : typed(new CastExpr(MiniType.VOID, evaluated, source.range()), MiniType.VOID);
        }

        private Expression qualifiedAddress(Expression value, MiniType commonType) {
            Expression address = address(value);
            return commonType.equals(declaredExpressionType(value)) ? address
                    : typed(new CastExpr(coreType(commonType).pointerTo(), address, value.range()), commonType.pointerTo());
        }

        /** Capture the RHS before the LHS address; the original assignment still checks conversions/operators. */
        private Expression normalizedAssignment(AssignmentExpr assignment, boolean addressDemand) {
            MiniType targetType = declaredExpressionType(assignment.target());
            MiniType valueType = declaredExpressionType(assignment.value());
            if (targetType == null || valueType == null || !addressableObject(assignment.target())) return assignment;
            boolean aggregate = valueType.isStruct();
            if (aggregate && !addressDemand && assignment.compoundBinaryOperator().isEmpty()) return assignment;
            Expression assignedValue = aggregate ? materializedReceiver(assignment.value()) : assignment.value();
            Expression value = aggregate ? address(assignedValue) : assignedValue;
            MiniType capturedType = aggregate ? valueType.pointerTo()
                    : assignment.compoundBinaryOperator().isEmpty() && !targetType.isArray()
                    ? targetType.unqualified() : TypeCompatibility.decay(valueType);
            Capture rhs = capture(capturedType, value, assignment.value().range());
            if (!addressDemand && assignment.compoundBinaryOperator().isEmpty()) {
                return typed(rhs.wrap(new AssignmentExpr(assignment.target(), assignment.operator(), rhs.name(), assignment.range()),
                        assignment.range()), targetType);
            }
            Capture lhs = capture(targetType.pointerTo(), address(assignment.target()), assignment.target().range());
            Expression target = typed(new UnaryExpr(TokenType.STAR, lhs.name(), assignment.target().range()), targetType);
            Expression rhsValue = aggregate ? typed(new UnaryExpr(TokenType.STAR, rhs.name(), value.range()), valueType) : rhs.name();
            // Keep the compound operator for core validation; a cast must not turn
            // an invalid implicit write-back (for example int += pointer) into a legal one.
            Expression store = new AssignmentExpr(target, assignment.operator(), rhsValue, assignment.range());
            Expression body = addressDemand ? new CommaExpr(List.of(store, lhs.name()), assignment.range()) : store;
            Expression result = rhs.wrap(lhs.wrap(body), assignment.range());
            return addressDemand ? typed(new UnaryExpr(TokenType.STAR, result, assignment.range()), targetType)
                    : typed(result, targetType);
        }

        private Expression simpleReference(String name, SourceRange range, Namespace namespace, Local local) {
            return simpleReference(name, range, namespace, local, false);
        }

        private Expression simpleReference(String name, SourceRange range, Namespace namespace, Local local, boolean addressDemand) {
            return memberReference(lookupName(name, namespace, local, range), name, range, addressDemand);
        }

        private Expression memberReference(Candidate candidate, String name, SourceRange range, boolean addressDemand) {
            if(candidate instanceof UnevaluatedLambdaLocal unavailable) {
                MiniType type=unavailable.entity.type.isReference()?unavailable.entity.type.referent():unavailable.entity.type;
                Expression pointer=typed(new CastExpr(coreType(type).pointerTo(),new IntegerLiteralExpr(0,"0",range),range),type.pointerTo());
                return typed(new UnaryExpr(TokenType.STAR,pointer,range),type);
            }
            if (candidate instanceof ImplicitField field) {
                Expression receiver=implicitReceiver(field.owner,range);
                if (receiver == null) {
                    report("CPP004", range, "A non-static data member requires an object: " + name);
                    return new IntegerLiteralExpr(0, "0", range);
                }
                requireAccessible(declaredExpressionType(receiver).pointee(), name, range, "数据成员访问");
                return fieldReference(receiver, name, true, declaredExpressionType(receiver).pointee(), range);
            }
            if (candidate instanceof MethodSet methods) return methodReference(methods, name, range);
            return reference(name, range, requireValue(candidate, name, range), addressDemand);
        }

        private Expression methodReference(MethodSet methods, String name, SourceRange range) {
            List<Method> statics = methods.methods.stream().filter(method -> method.source.staticMember()).toList();
            if (statics.size() != 1 || methods.methods.size() != 1) {
                report("CPP005", range, "A non-static or overloaded method requires a call or contextual function type: " + name);
                return new NameExpr(methods.methods.getFirst().function.coreName, range);
            }
            Method method = statics.getFirst(); requireMethodAccess(method, range); instantiateMethod(method);
            return typed(new NameExpr(method.function.coreName, range), method.function.type);
        }


        private record BoundCallee(Expression expression, Expression receiver, List<Expression> arguments) {
            BoundCallee(Expression expression, Expression receiver) { this(expression, receiver, null); }
        }

        private String operatorName(TokenType token) {
            String symbol = switch (token) {
                case PLUS -> "+"; case MINUS -> "-"; case STAR -> "*"; case SLASH -> "/"; case PERCENT -> "%";
                case CARET -> "^"; case AMPERSAND -> "&"; case PIPE -> "|"; case TILDE -> "~"; case BANG -> "!";
                case LESS -> "<"; case GREATER -> ">"; case LESS_LESS -> "<<"; case GREATER_GREATER -> ">>";
                case EQUAL_EQUAL -> "=="; case BANG_EQUAL -> "!="; case LESS_EQUAL -> "<="; case GREATER_EQUAL -> ">=";
                case PLUS_PLUS -> "++"; case MINUS_MINUS -> "--";
                case AMPERSAND_AMPERSAND -> "&&"; case PIPE_PIPE -> "||";
                case PLUS_EQUAL -> "+="; case MINUS_EQUAL -> "-="; case STAR_EQUAL -> "*=";
                case SLASH_EQUAL -> "/="; case PERCENT_EQUAL -> "%="; case CARET_EQUAL -> "^=";
                case AMPERSAND_EQUAL -> "&="; case PIPE_EQUAL -> "|=";
                case LESS_LESS_EQUAL -> "<<="; case GREATER_GREATER_EQUAL -> ">>=";
                default -> null;
            };
            return symbol == null ? null : "operator" + symbol;
        }

        private record OperatorCandidate(Entity function, Method method, List<MiniType> parameters, String builtin) {
            OperatorCandidate(Entity function, Method method, List<MiniType> parameters) { this(function, method, parameters, null); }
        }

        private Expression addressUpdateResult(UnaryExpr operation) {
            Expression operand = operation.operand(); MiniType type = declaredExpressionType(operand);
            if (type == null || !addressableObject(operand)) return operation;
            Capture pointer = capture(type.pointerTo(), address(operand), operation.range());
            Expression target = typed(new UnaryExpr(TokenType.STAR, pointer.name(), operand.range()), type);
            Expression body = new CommaExpr(List.of(new UnaryExpr(operation.operator(), target, operation.range()), pointer.name()), operation.range());
            return typed(new UnaryExpr(TokenType.STAR, pointer.wrap(body), operation.range()), type);
        }

        private Expression builtinComma(Expression first, Expression last, SourceRange range) {
            if (addressableObject(last)) {
                Expression object = typed(new UnaryExpr(TokenType.STAR,
                        new CommaExpr(List.of(first, address(last)), range), range), declaredExpressionType(last));
                temporaryAddressPaths.add(object);
                valueCategories.put(object, valueCategory(last));
                return object;
            }
            return typed(new CommaExpr(List.of(first, last), range), declaredExpressionType(last));
        }

        private record ArrowStep(MiniType type, CppValueCategory category) { }

        /** Each arrow result is reused as the next receiver; lookup never evaluates it twice. */
        private Expression arrowReceiver(Expression receiver, Expression source, Namespace namespace, Local local) {
            Set<ArrowStep> visited = new HashSet<>();
            while (objectType(declaredExpressionType(receiver)) != null) {
                ArrowStep step = new ArrowStep(declaredExpressionType(receiver), valueCategory(receiver));
                if (!visited.add(step)) {
                    report("CPP004", source.range(), "Recursive operator-> does not reach a pointer type.");
                    return receiver;
                }
                Expression next = operatorExpression("operator->", source, List.of(source),
                        List.of(receiver), namespace, local, true);
                if (next == null) break;
                receiver = next;
            }
            MiniType result = declaredExpressionType(receiver);
            if (result == null || !result.isPointer())
                report("CPP004", source.range(), "Arrow member access requires a pointer or a class operator-> returning one.");
            return receiver;
        }

        /** The operand list is shared by member and free candidates; no expression is evaluated during selection. */
        private Expression operatorExpression(String name, Expression original, List<Expression> sources,
                                              List<Expression> values, Namespace namespace, Local local, boolean memberOnly) {
            if (name == null || values.stream().noneMatch(value -> objectType(declaredExpressionType(value)) != null)) return null;
            List<CppOverloadResolver.Candidate<OperatorCandidate>> candidates = new ArrayList<>();
            MethodSet members = memberMethods(declaredExpressionType(values.getFirst()), name);
            if (members != null) for (Method method : expandMethodTemplates(members.methods,values.subList(1,values.size()),null,original.range())) {
                var candidate = new OperatorCandidate(method.function, method, method.parameterTypes);
                candidates.add(new CppOverloadResolver.Candidate<>(candidate, method.parameterTypes,
                        method.source.method().variadic(), methodThisType(method.owner, method.source).pointee(),false,requiredParameters(method.function,method.parameterTypes.size())));
            }
            if (!memberOnly) for (Entity function : expandFunctionTemplates(operatorFunctions(name, values, namespace, local),values,null,original.range())) {
                MiniType.FunctionType type = (MiniType.FunctionType) function.type;
                var candidate = new OperatorCandidate(function, null, type.parameterTypes());
                candidates.add(new CppOverloadResolver.Candidate<>(candidate, type.parameterTypes(), type.variadic()));
            }
            addBuiltinOperators(name, original, values, candidates);
            List<CppOverloadResolver.Argument> arguments = new ArrayList<>();
            for (int index = 0; index < values.size(); index++) {
                Expression value = values.get(index);
                CppOverloadResolver.Argument shape = argumentShape(value, sources.get(index));
                if (shape == null) { report("CPP004", sources.get(index).range(), "无法确定运算符实参类型。");
                    return new IntegerLiteralExpr(0, "0", original.range()); }
                arguments.add(shape);
            }
            var resolution = CppOverloadResolver.resolveOperators(candidates, arguments, conversions, this::betterTemplateCandidate);
            if (resolution.status() != CppOverloadResolver.Status.SELECTED) {
                // C++ unary & falls back to builtin address-of only when no candidate is viable.
                if ((name.equals("operator&") && values.size() == 1 || name.equals("operator,"))
                        && resolution.status() == CppOverloadResolver.Status.NO_VIABLE) return null;
                report("CPP004", original.range(), resolution.status() == CppOverloadResolver.Status.AMBIGUOUS
                        ? "运算符重载具有二义性：" + name : "没有匹配的运算符重载：" + name);
                return new IntegerLiteralExpr(0, "0", original.range());
            }
            OperatorCandidate selected = resolution.winner().identity();
            if (selected.builtin != null) return lowerBuiltinOperator(selected, original, values);
            List<Expression> lowered = new ArrayList<>();
            int offset = selected.method == null ? 0 : 1;
            if (selected.method != null) {
                instantiateMethod(selected.method);
                requireMethodAccess(selected.method, original.range());
                Expression receiver = materializedReceiver(values.getFirst());
                if (!addressableObject(receiver)) {
                    report("CPP005", sources.getFirst().range(), "尚未支持此运算符接收者值类别。");
                    return new IntegerLiteralExpr(0, "0", original.range());
                }
                lowered.add(address(receiver));
            }
            lowered.addAll(lowerSelectedArguments(selected.function,selected.parameters, sources.subList(offset, sources.size()),
                    values.subList(offset, values.size()), namespace, local));
            instantiateFunctionTemplate(selected.function);
            MiniType.FunctionType signature = (MiniType.FunctionType) selected.function.type;
            requireComplete(signature.returnType(), original.range());
            signature.parameterTypes().forEach(type -> requireComplete(type, original.range()));
            requireSupportedCallLifetime(signature.returnType(), signature.parameterTypes(), original.range());
            List<Integer> evaluationOrder = original instanceof AssignmentExpr
                    ? List.of(1, 0) : java.util.stream.IntStream.range(0, lowered.size()).boxed().toList();
            CallExpr call = new CallExpr(new NameExpr(functionReferenceName(selected.function), original.range()), lowered, evaluationOrder, original.range());
            declaredExpressionTypes.put(call, signature.returnType().isReference()
                    ? coreType(signature.returnType()) : signature.returnType());
            if (signature.returnType().isReference())
                return referenceResult(signature.returnType(),call,original.range());
            return signature.returnType().isStruct() ? recordPrvalue(signature.returnType(), call, original.range()) : call;
        }

        private static final List<MiniType> PROMOTED_ARITHMETIC = List.of(MiniType.INT, MiniType.UNSIGNED_INT,
                MiniType.LONG, MiniType.UNSIGNED_LONG, MiniType.LONG_LONG, MiniType.UNSIGNED_LONG_LONG, MiniType.FLOAT, MiniType.DOUBLE);

        private boolean contextualOperator(String name) {
            return "operator!".equals(name) || "operator&&".equals(name) || "operator||".equals(name);
        }

        /** N4659 over.built: candidates describe conversions, then the core checks the actual builtin operands. */
        private void addBuiltinOperators(String name, Expression original, List<Expression> values,
                                          List<CppOverloadResolver.Candidate<OperatorCandidate>> candidates) {
            Set<List<MiniType>> signatures = new LinkedHashSet<>();
            Set<MiniType> pointers = new LinkedHashSet<>();
            Set<MiniType> objects = new LinkedHashSet<>();
            for (Expression value : values) {
                MiniType type = declaredExpressionType(value);
                if (type == null) continue;
                List<MiniType> reachable = new ArrayList<>(); reachable.add(type);
                for (Method conversion : conversionMethods(type)) if (!conversion.source.method().conversionName().explicitSpecifier())
                    reachable.add(objectTypeOfReference(methodReturnType(conversion)));
                for (MiniType result : reachable) {
                    objects.add(result);
                    MiniType decayed = TypeCompatibility.decay(result).unqualified();
                    if (decayed.isPointer()) pointers.add(decayed);
                }
            }
            // Include a composite cv pointer when neither existing spelling contains both qualifications.
            for (MiniType first : List.copyOf(pointers)) for (MiniType second : List.copyOf(pointers))
                if (first.pointee().unqualified().equals(second.pointee().unqualified())) {
                    var qualifiers = new HashSet<>(first.pointee().qualifiers()); qualifiers.addAll(second.pointee().qualifiers());
                    pointers.add(MiniType.qualified(first.pointee().unqualified(), qualifiers).pointerTo());
                }
            if (original instanceof AssignmentExpr assignment) {
                MiniType left = declaredExpressionType(values.getFirst());
                if (left != null && !left.isConstQualified() && objectType(left) == null) {
                    boolean integralOnly = Set.of(TokenType.PERCENT_EQUAL, TokenType.AMPERSAND_EQUAL, TokenType.PIPE_EQUAL,
                            TokenType.CARET_EQUAL, TokenType.LESS_LESS_EQUAL, TokenType.GREATER_GREATER_EQUAL).contains(assignment.operator());
                    if (left.isScalar() && (!integralOnly || left.isIntegerScalar())) {
                        for (MiniType right : PROMOTED_ARITHMETIC) if (!integralOnly || right.isIntegerScalar())
                            signatures.add(List.of(left.referenceTo(), right));
                    } else if (left.isPointer() && !left.pointee().isVoid() && !left.pointee().isFunction()
                            && (assignment.operator() == TokenType.PLUS_EQUAL || assignment.operator() == TokenType.MINUS_EQUAL))
                        signatures.add(List.of(left.referenceTo(), MiniType.LONG_LONG));
                }
            }
            if (name.equals("operator()")) {
                for (MiniType pointer : pointers) if (pointer.pointee().isFunction()) {
                    MiniType.FunctionType signature = (MiniType.FunctionType) pointer.pointee().unqualified();
                    List<MiniType> parameters = new ArrayList<>(); parameters.add(pointer); parameters.addAll(signature.parameterTypes());
                    OperatorCandidate builtin = new OperatorCandidate(null, null, parameters, name);
                    candidates.add(new CppOverloadResolver.Candidate<>(builtin, parameters, signature.variadic()));
                }
                return;
            }
            boolean unary = values.size() == 1;
            boolean update = name.equals("operator++") || name.equals("operator--");
            if (contextualOperator(name)) signatures.add(java.util.Collections.nCopies(values.size(), MiniType.BOOL));
            else if (update) {
                for (MiniType type : objects) if (!type.isConstQualified() && !type.unqualified().equals(MiniType.BOOL)
                        && (type.isScalar() || type.isPointer() && !type.pointee().isVoid() && !type.pointee().isFunction()))
                    signatures.add(unary ? List.of(type.referenceTo()) : List.of(type.referenceTo(), MiniType.INT));
            } else if (unary) {
                if (name.equals("operator+") || name.equals("operator-"))
                    for (MiniType type : PROMOTED_ARITHMETIC) signatures.add(List.of(type));
                if (name.equals("operator~")) for (MiniType type : PROMOTED_ARITHMETIC)
                    if (type.isIntegerScalar()) signatures.add(List.of(type));
                if (name.equals("operator+") || name.equals("operator*")) for (MiniType pointer : pointers)
                    if (!name.equals("operator*") || !pointer.pointee().isVoid()) signatures.add(List.of(pointer));
            } else if (values.size() == 2) {
                boolean comparison = Set.of("operator==", "operator!=", "operator<", "operator>", "operator<=", "operator>=").contains(name);
                boolean arithmetic = comparison || Set.of("operator+", "operator-", "operator*", "operator/", "operator?:").contains(name);
                boolean integral = Set.of("operator%", "operator&", "operator|", "operator^", "operator<<", "operator>>").contains(name);
                if (arithmetic || integral) for (MiniType first : PROMOTED_ARITHMETIC) for (MiniType second : PROMOTED_ARITHMETIC)
                    if (!integral || first.isIntegerScalar() && second.isIntegerScalar()) signatures.add(List.of(first, second));
                for (MiniType pointer : pointers) {
                    if (comparison || name.equals("operator?:")) signatures.add(List.of(pointer, pointer));
                    if (pointer.pointee().isVoid() || pointer.pointee().isFunction()) continue;
                    if (Set.of("operator+", "operator-", "operator[]").contains(name)) signatures.add(List.of(pointer, MiniType.LONG_LONG));
                    if (name.equals("operator+") || name.equals("operator[]")) signatures.add(List.of(MiniType.LONG_LONG, pointer));
                    if (name.equals("operator-")) signatures.add(List.of(pointer, pointer));
                }
                if (name.equals("operator==") || name.equals("operator!=")) signatures.add(List.of(MiniType.NULL, MiniType.NULL));
            }
            for (List<MiniType> parameters : signatures) {
                if (candidates.stream().anyMatch(candidate -> candidate.identity().method == null
                        && candidate.identity().function != null && candidate.parameterTypes().equals(parameters))) continue;
                OperatorCandidate builtin = new OperatorCandidate(null, null, parameters, name);
                candidates.add(new CppOverloadResolver.Candidate<>(builtin, parameters, false));
            }
        }

        private Expression lowerBuiltinOperator(OperatorCandidate selected, Expression original, List<Expression> values) {
            List<Expression> converted = new ArrayList<>();
            for (int index = 0; index < values.size(); index++) {
                Expression value = values.get(index);
                if (index < selected.parameters.size() && objectType(declaredExpressionType(value)) != null) {
                    Expression conversion = userConversion(selected.parameters.get(index), value,
                            contextualOperator(selected.builtin) ? ConversionContext.BOOLEAN : ConversionContext.IMPLICIT, value.range());
                    if (conversion != null) value = conversion;
                }
                // Do not apply the trailing standard sequence: the actual builtin must still
                // reject e.g. pointer + a class converting to double (over.match.oper/7).
                converted.add(contextualOperator(selected.builtin) ? contextualBool(value) : value);
            }
            if (original instanceof AssignmentExpr assignment)
                return new AssignmentExpr(converted.get(0), assignment.operator(), converted.get(1), original.range());
            if (original instanceof CallExpr) {
                Expression callee = converted.getFirst();
                MiniType.FunctionType signature = functionSignature(declaredExpressionType(callee));
                List<Expression> arguments = new ArrayList<>();
                for (int index = 1; index < converted.size(); index++) {
                    Expression argument = converted.get(index);
                    MiniType parameter = index - 1 < signature.parameterTypes().size() ? signature.parameterTypes().get(index - 1) : null;
                    arguments.add(parameter != null && parameter.isReference()
                            ? bindReferenceValue(parameter, argument, values.get(index), argument.range())
                            : convertCallValue(parameter, argument, values.get(index)));
                }
                requireComplete(signature.returnType(), original.range());
                destructorForUse(signature.returnType(), original.range());
                Expression call = typed(new CallExpr(callee, arguments, original.range()), coreType(signature.returnType()));
                if (signature.returnType().isReference()) return referenceResult(signature.returnType(),call,original.range());
                return signature.returnType().isStruct() ? recordPrvalue(signature.returnType(), call, original.range()) : call;
            }
            if (original instanceof BinaryExpr binary) {
                Expression left = converted.get(0), right = converted.get(1);
                if (binary.operator() == TokenType.PLUS || binary.operator() == TokenType.MINUS) {
                    requireComplete(elementType(declaredExpressionType(left)), original.range());
                    requireComplete(elementType(declaredExpressionType(right)), original.range());
                }
                Expression operation = new BinaryExpr(left, binary.operator(), right, original.range());
                return hasBooleanResult(binary.operator()) ? booleanResult(operation) : operation;
            }
            if (original instanceof UnaryExpr unary) {
                Expression value = converted.getFirst();
                if (unary.operator() == TokenType.PLUS_PLUS || unary.operator() == TokenType.MINUS_MINUS) requireUpdateOperand(value, original.range());
                if (unary.operator() == TokenType.PLUS && declaredExpressionType(value).isPointer()) return value;
                Expression operation = new UnaryExpr(unary.operator(), value, original.range());
                return unary.operator() == TokenType.BANG ? booleanResult(operation) : operation;
            }
            if (original instanceof PostfixUpdateExpr update) {
                requireUpdateOperand(converted.getFirst(), original.range());
                return new PostfixUpdateExpr(converted.getFirst(), update.operator(), original.range());
            }
            if (original instanceof IndexExpr) {
                Expression pointer = converted.get(0), index = converted.get(1);
                if (!TypeCompatibility.decay(declaredExpressionType(pointer)).isPointer()) {
                    // a[b] sequences a before b even for the symmetric integer[pointer] builtin.
                    Capture first = capture(declaredExpressionType(pointer), pointer, original.range());
                    MiniType element = elementType(declaredExpressionType(index));
                    requireComplete(element, original.range());
                    Expression indexed = typed(new IndexExpr(index, first.name(), original.range()), element);
                    return typed(new UnaryExpr(TokenType.STAR, first.wrap(address(indexed)), original.range()), element);
                }
                requireComplete(elementType(declaredExpressionType(pointer)), original.range());
                return typed(new IndexExpr(pointer, index, original.range()), elementType(declaredExpressionType(pointer)));
            }
            throw new IllegalStateException("Unsupported builtin operator result: " + selected.builtin);
        }

        /** Ordinary lookup ignores class members; ADL adds only the associated namespace's declarations. */
        private Set<Entity> operatorFunctions(String name, List<Expression> values, Namespace namespace, Local local) {
            Set<Entity> functions = new LinkedHashSet<>();
            Candidate localCandidate = null;
            for (Local at = local; at != null && localCandidate == null; at = at.parent) localCandidate = at.values.get(name);
            if (localCandidate instanceof OverloadSet set) functions.addAll(set.functions);
            if (localCandidate == null) {
                Map<Namespace, Set<Namespace>> nominated = nominations(namespace, local);
                for (Namespace at = namespace; at != null; at = at.parent) {
                    Set<Candidate> found = new LinkedHashSet<>(directCandidates(at, name));
                    for (Namespace target : nominated.getOrDefault(at, Set.of())) found.addAll(directCandidates(target, name));
                    for (Candidate candidate : found) if (candidate instanceof OverloadSet set) functions.addAll(set.functions);
                    if (!found.isEmpty()) break;
                }
            }
            Set<Namespace> associated = new LinkedHashSet<>();
            for (Expression value : values) associatedNamespaces(declaredExpressionType(value), associated);
            for (Namespace at : associated)
                if (at.values.get(name) instanceof OverloadSet set) functions.addAll(set.functions);
            return functions;
        }

        private void associatedNamespaces(MiniType type, Set<Namespace> namespaces) {
            if (type == null) return;
            switch (type.unqualified()) {
                case MiniType.StructType ignored -> {
                    TypeEntity owner = objectType(type);
                    if (owner != null) {
                        namespaces.add(owner.owner);
                        MiniType.TemplateIdType key=instanceKeys.get(owner);
                        if(key!=null)for(TemplateArgument argument:key.arguments())if(argument instanceof TemplateArgument.Type t)associatedNamespaces(t.type(),namespaces);
                    }
                }
                case MiniType.PointerType pointer -> associatedNamespaces(pointer.pointee(), namespaces);
                case MiniType.ReferenceType reference -> associatedNamespaces(reference.referent(), namespaces);
                case MiniType.ArrayType array -> associatedNamespaces(array.elementType(), namespaces);
                case MiniType.FunctionType function -> {
                    associatedNamespaces(function.returnType(), namespaces);
                    function.parameterTypes().forEach(parameter -> associatedNamespaces(parameter, namespaces));
                }
                default -> { }
            }
        }

        private record OverloadDesignator(Expression source, Expression name, OverloadSet set, boolean addressOf,
                                          Expression receiver,List<TemplateArgument> explicit) { }

        private OverloadDesignator overloadDesignator(Expression source, Namespace namespace, Local local) {
            Expression name = source;
            while (name instanceof GroupingExpr group) name = group.expression();
            boolean addressOf = name instanceof UnaryExpr unary && unary.operator() == TokenType.AMPERSAND;
            if (addressOf) name = ((UnaryExpr) name).operand();
            while (name instanceof GroupingExpr group) name = group.expression();
            List<TemplateArgument> explicit=null;
            if(name instanceof CppTemplateIdExpr id){explicit=normalizeExplicitArguments(id.arguments(),namespace,local,id.range());name=id.target();}
            int beforeLookup = diagnostics.size();
            Expression receiver = null;
            Candidate candidate = name instanceof NameExpr simple ? lookupName(simple.name(), namespace, local, name.range())
                    : name instanceof QualifiedNameExpr qualified ? resolveQualifiedName(qualified.name(), namespace, local)
                    : name instanceof CppTypeMemberExpr member ? typeMember(member, namespace, local) : null;
            if (name instanceof FieldAccessExpr field) {
                receiver = expression(field.target(), namespace, local, !field.viaPointer());
                if (field.viaPointer()) receiver = arrowReceiver(receiver, field, namespace, local);
                else receiver = materializedReceiver(receiver);
                MiniType owner = declaredExpressionType(receiver);
                candidate = memberMethods(field.viaPointer() ? elementType(owner) : owner, field.fieldName());
            }
            boolean memberDesignator = candidate instanceof MethodSet;
            if (candidate instanceof MethodSet methods) candidate = new OverloadSet(methods.methods.stream()
                    .filter(method -> method.source.staticMember()).map(Method::function).toList());
            // This is a contextual probe. Ordinary expression binding owns diagnostics when the
            // expression does not denote an overloaded free function.
            if (!(candidate instanceof OverloadSet set) || set.functions.isEmpty()
                    || !memberDesignator && set.functions.size() < 2 && set.functions.stream().noneMatch(functionTemplates::containsKey)) {
                diagnostics.subList(beforeLookup, diagnostics.size()).clear();
                return null;
            }
            return new OverloadDesignator(source, name, set, addressOf, receiver,explicit);
        }

        private MiniType.FunctionType targetFunction(MiniType target) {
            if (target == null) return null;
            MiniType.FunctionType function = functionSignature(objectTypeOfReference(target));
            return function == null ? null : (MiniType.FunctionType) MiniType.function(function.returnType(),
                    function.parameterTypes().stream().map(MiniType::unqualified).toList(), function.variadic());
        }

        private Entity functionForTarget(OverloadDesignator designator, MiniType target) {
            MiniType.FunctionType signature = targetFunction(target);
            if (signature == null) return null;
            Entity selected = null;
            for (Entity declaration : designator.set.functions) {
                Entity candidate=deduceFunctionTemplateForTarget(declaration,signature,designator.explicit,designator.name.range());
                if(candidate==null||!candidate.type.equals(signature))continue;
                if(selected!=null) {
                    if(betterTemplateCandidate(candidate,selected))selected=candidate;
                    else if(!betterTemplateCandidate(selected,candidate))return null;
                } else selected=candidate;
            }
            return selected;
        }

        private Expression expressionForTarget(MiniType target, Expression source, Namespace namespace, Local local) {
            if (source instanceof CppInitializer syntax && isList(syntax) && target != null) return bindListValue(target, source, namespace, local);
            Expression selected = contextualFunctionAddress(target, source, namespace, local);
            return selected != null ? selected : expression(source, namespace, local);
        }

        private Expression contextualFunctionAddress(MiniType target, Expression source, Namespace namespace, Local local) {
            if (source == null || targetFunction(target) == null) return null;
            OverloadDesignator designator = overloadDesignator(source, namespace, local);
            if (designator == null) return null;
            Entity selected = functionForTarget(designator, target);
            if (selected == null) {
                report("CPP004", source.range(), "重载函数名称没有唯一匹配的目标函数类型。");
                return mapped(source, new NameExpr(designator.set.functions.getFirst().coreName, source.range()));
            }
            return rebuildFunctionDesignator(source, designator.name, selected,
                    target.isReference() && target.referent().isFunction(), designator.receiver);
        }

        private Expression rebuildFunctionDesignator(Expression original, Expression name, Entity selected,
                                                     boolean functionReference, Expression receiver) {
            Expression core;
            if (original == name) {
                Method method = staticMethods.get(selected);
                if (method != null) { requireMethodAccess(method, original.range()); instantiateMethod(method); }
                core = new NameExpr(functionReferenceName(selected), original.range());
                if (receiver != null) core = evaluateReceiver(receiver, core, original.range());
            }
            else if (original instanceof GroupingExpr group) core = new GroupingExpr(
                    rebuildFunctionDesignator(group.expression(), name, selected, functionReference, receiver), original.range());
            else if(original instanceof CppTemplateIdExpr id)core=rebuildFunctionDesignator(id.target(),name,selected,functionReference,receiver);
            else if (original instanceof UnaryExpr unary && unary.operator() == TokenType.AMPERSAND) {
                Expression operand = rebuildFunctionDesignator(unary.operand(), name, selected, functionReference, receiver);
                // [over.over] permits an optional & on an unresolved overload designator,
                // including a target of reference-to-function type.
                core = functionReference ? new GroupingExpr(operand, original.range())
                        : new UnaryExpr(TokenType.AMPERSAND, operand, original.range());
            }
            else throw new IllegalArgumentException("invalid function designator");
            return mapped(original, core);
        }

        private BoundCallee bindCallee(Expression sourceCallee, List<Expression> sourceArguments, Namespace namespace, Local local) {
            Expression designator = sourceCallee;
            while (designator instanceof GroupingExpr group) designator = group.expression();
            List<TemplateArgument> explicit=null;
            if(designator instanceof CppTemplateIdExpr id){explicit=normalizeExplicitArguments(id.arguments(),namespace,local,id.range());designator=id.target();}
            MethodSet methods = null;
            Expression receiver = null;
            String sourceName = designator instanceof NameExpr name ? name.name()
                    : designator instanceof IntegerConstantExpr constant
                    && constant.lexeme().matches("[A-Za-z_][A-Za-z0-9_]*") ? constant.lexeme() : null;
            String fallbackName = designator instanceof QualifiedNameExpr qualified ? spelling(qualified.name()) : sourceName;
            if (sourceName != null || designator instanceof QualifiedNameExpr || designator instanceof CppTypeMemberExpr) {
                if (designator instanceof CppTypeMemberExpr member) fallbackName = member.memberName();
                int lookupDiagnostics=diagnostics.size();
                Candidate candidate = designator instanceof CppTypeMemberExpr member ? typeMember(member, namespace, local)
                        : designator instanceof QualifiedNameExpr qualified
                        ? resolveQualifiedName(qualified.name(), namespace, local)
                        : lookupName(sourceName, namespace, local, designator.range());
                if (candidate instanceof TypeEntity type && type.classType) {
                    report("CPP005", designator.range(), "Functional construction expressions are not supported in this slice.");
                    return new BoundCallee(new NameExpr(fallbackName, designator.range()), null);
                }
                if(sourceName!=null && (candidate==null||candidate instanceof OverloadSet)) {
                    diagnostics.subList(lookupDiagnostics,diagnostics.size()).clear();
                    PreparedArguments prepared=prepareArguments(sourceArguments,namespace,local);
                    var candidates=operatorFunctions(sourceName,prepared.values,namespace,local);
                    if(!candidates.isEmpty())return bindOverloadedCall(new OverloadSet(new ArrayList<>(candidates)),sourceCallee,designator,sourceArguments,namespace,local,explicit,prepared);
                    report("CPP003",designator.range(),"No visible function or associated function named "+sourceName);
                    return new BoundCallee(new NameExpr(sourceName,designator.range()),null,recoveryArguments(sourceArguments,prepared.values));
                }
                if (candidate instanceof OverloadSet set) {
                    return bindOverloadedCall(set, sourceCallee, designator, sourceArguments, namespace, local,explicit,null);
                }
                if (candidate instanceof MethodSet members) {
                    methods = members;
                    receiver = implicitReceiver(members.methods.getFirst().owner,designator.range());
                } else {
                    if(explicit!=null)report("CPP004",designator.range(),"Explicit template arguments require a function template");
                    Expression core;
                    core = memberReference(candidate, fallbackName, designator.range(), false);
                    return new BoundCallee(rebuildCalleeGroups(sourceCallee, designator, core), null);
                }
            } else if (designator instanceof FieldAccessExpr field) {
                Expression target = expression(field.target(), namespace, local, !field.viaPointer());
                if (field.viaPointer()) target = arrowReceiver(target, field, namespace, local);
                if (!field.viaPointer()) target = materializedReceiver(target);
                MiniType owner = declaredExpressionType(target);
                owner = field.viaPointer() ? elementType(owner) : owner;
                requireComplete(owner, field.range());
                methods = memberMethods(owner, field.fieldName());
                if (methods == null) {
                    StaticField staticField = staticField(owner, field.fieldName());
                    if (staticField != null) return new BoundCallee(evaluateReceiver(target,
                            staticFieldReference(staticField, field.range(), false), sourceCallee.range()), null);
                    requireAccessible(owner, field.fieldName(), field.range(), "数据成员访问");
                    Expression core = fieldReference(target, field.fieldName(), field.viaPointer(), owner, field.range());
                    return new BoundCallee(rebuildCalleeGroups(sourceCallee, designator, core), null);
                }
                if (field.viaPointer()) receiver = target;
                else {
                    if (!addressableObject(target)) report("CPP005", field.range(), "尚未支持此值类别作为成员函数接收者。");
                    receiver = new UnaryExpr(TokenType.AMPERSAND, target, field.target().range());
                }
            }
            if (methods == null) return new BoundCallee(expression(sourceCallee, namespace, local), null);
            return bindOverloadedMethod(methods, sourceCallee, receiver, sourceArguments, namespace, local,explicit);
        }

        private BoundCallee bindOverloadedCall(OverloadSet set, Expression sourceCallee, Expression designator,
                                              List<Expression> sourceArguments, Namespace namespace, Local local,List<TemplateArgument> explicit,PreparedArguments alreadyPrepared) {
            // Provisional expressions only provide types/categories. Exactly one selected ABI
            // argument list is emitted; reference arguments are rebound with address demand.
            PreparedArguments prepared = alreadyPrepared==null?prepareArguments(sourceArguments, namespace, local):alreadyPrepared;
            List<Expression> values = prepared.values;
            List<CppOverloadResolver.Candidate<Entity>> candidates = expandFunctionTemplates(set.functions,values,explicit,sourceCallee.range()).stream().map(function -> {
                MiniType.FunctionType type = (MiniType.FunctionType) function.type;
                return new CppOverloadResolver.Candidate<>(function, type.parameterTypes(), type.variadic(),null,false,requiredParameters(function,type.parameterTypes().size()));
            }).toList();
            Entity selected = selectOverload(candidates, sourceCallee, sourceArguments, prepared, null);
            if (selected == null) return new BoundCallee(new NameExpr(set.functions.getFirst().coreName, sourceCallee.range()), null, recoveryArguments(sourceArguments, values));
            return new BoundCallee(rebuildCalleeGroups(sourceCallee, designator,
                    new NameExpr(functionReferenceName(selected), designator.range())), null,
                    lowerSelectedArguments(selected,((MiniType.FunctionType) selected.type).parameterTypes(), sourceArguments, values, namespace, local));
        }

        private BoundCallee bindOverloadedMethod(MethodSet set, Expression sourceCallee, Expression receiver,
                                                List<Expression> sourceArguments, Namespace namespace, Local local,List<TemplateArgument> explicit) {
            PreparedArguments prepared = prepareArguments(sourceArguments, namespace, local);
            List<Expression> values = prepared.values;
            List<CppOverloadResolver.Candidate<Method>> candidates = expandMethodTemplates(set.methods,values,explicit,sourceCallee.range()).stream().map(method ->
                    new CppOverloadResolver.Candidate<>(method, method.parameterTypes, method.source.method().variadic(),
                            method.source.staticMember() ? null : methodThisType(method.owner, method.source).pointee(),
                            method.source.staticMember(),requiredParameters(method.function,method.parameterTypes.size()))).toList();
            MiniType object = elementType(declaredExpressionType(receiver));
            if (object == null && receiver != null) {
                report("CPP004", sourceCallee.range(), "无法确定成员函数接收者的类型。");
                return new BoundCallee(new NameExpr(set.methods.getFirst().function.coreName, sourceCallee.range()), receiver, recoveryArguments(sourceArguments, values));
            }
            Method selected = selectOverload(candidates, sourceCallee, sourceArguments, prepared,
                    object == null ? null : new CppOverloadResolver.Argument(object, CppValueCategory.LVALUE, false));
            if (selected == null) return new BoundCallee(new NameExpr(set.methods.getFirst().function.coreName, sourceCallee.range()), receiver, recoveryArguments(sourceArguments, values));
            // Access is checked only after selection. An inaccessible best match does not
            // allow falling back to a public candidate with worse conversions.
            requireMethodAccess(selected, sourceCallee.range());
            instantiateMethod(selected);
            Expression callee = new NameExpr(selected.function.coreName, sourceCallee.range());
            if (selected.source.staticMember()) callee = evaluateReceiver(receiver, callee, sourceCallee.range());
            return new BoundCallee(mapped(sourceCallee, callee), selected.source.staticMember() ? null : receiver,
                    lowerSelectedArguments(selected.function,selected.parameterTypes, sourceArguments, values, namespace, local));
        }

        private boolean isList(CppInitializer syntax) {
            return syntax.kind() == CppInitializer.Kind.DIRECT_LIST || syntax.kind() == CppInitializer.Kind.COPY_LIST;
        }
        private boolean isBraced(Expression source) {
            return source instanceof CppInitializer syntax && isList(syntax) || source instanceof AggregateInitExpr;
        }
        private List<Expression> listItems(Expression source) {
            return source instanceof CppInitializer syntax ? syntax.arguments() : ((AggregateInitExpr) source).values();
        }
        private CppOverloadResolver.Argument argumentShape(Expression value, Expression source) {
            if (isBraced(value)) {
                PreparedArguments prepared = bracedArguments.get(value);
                List<Expression> items = prepared == null ? listItems(value) : prepared.values;
                List<CppOverloadResolver.Argument> shapes = new ArrayList<>();
                for (int index = 0; index < items.size(); index++) {
                    CppOverloadResolver.Argument shape = argumentShape(items.get(index), listItems(value).get(index));
                    if (shape == null) return null;
                    shapes.add(shape);
                }
                return CppOverloadResolver.Argument.braced(shapes);
            }
            MiniType type = declaredExpressionType(value);
            return type == null ? null : new CppOverloadResolver.Argument(type, valueCategory(value), isNullIntegerLiteral(source));
        }
        private MiniType initializerListElementPattern(MiniType type) {
            if(type.isReference())type=type.referent();
            type=type.unqualified();
            if(type instanceof MiniType.TemplateIdType id && id.templateName().equals("::std::initializer_list")
                    && id.arguments().size()==1 && id.arguments().getFirst() instanceof TemplateArgument.Type argument)
                return argument.type();
            return initializerListElement(type);
        }
        private CppOverloadResolver.Candidate<Constructor> constructorCandidate(Constructor c) {
            return new CppOverloadResolver.Candidate<>(c,c.parameterTypes,c.source.variadic(),null,false,requiredParameters(c.function,c.parameterTypes.size()));
        }
        private boolean initializerListConstructor(Constructor c) {
            return !c.parameterTypes.isEmpty() && requiredParameters(c.function,c.parameterTypes.size())<=1
                    && initializerListElement(c.parameterTypes.getFirst())!=null;
        }
        /** Recognition uses the declared standard template identity, never a record's short name. */
        private MiniType initializerListElement(MiniType type) {
            if (type == null) return null;
            if (type.isReference()) type = type.referent();
            TypeEntity entity = objectType(type);
            MiniType.TemplateIdType instance = instanceKeys.get(entity);
            if (instance == null || !instance.templateName().equals("::std::initializer_list") || instance.arguments().size() != 1) return null;
            return instance.arguments().getFirst() instanceof TemplateArgument.Type argument ? argument.type() : null;
        }
        private CppOverloadResolver.UserConversion listUserConversion(CppOverloadResolver.Argument source, MiniType target) {
            MiniType object = objectTypeOfReference(target).unqualified();
            TypeEntity owner = objectType(object);
            if (owner == null) return null;
            completeTemplate(owner, owner.sourceRecord == null ? Binding.this.source.range() : owner.sourceRecord.range());
            List<CppOverloadResolver.Argument> elements = source.listElements();
            var result = new CppOverloadResolver.Argument(object, CppValueCategory.PRVALUE, false);
            if (!nonAggregate(owner)) {
                if (elements.size() == 1 && !elements.getFirst().braced()
                        && elements.getFirst().type().unqualified().equals(object))
                    return new CppOverloadResolver.UserConversion(owner, result, false, true);
                if (elements.size() > owner.fields.size()) return null;
                List<MiniType> fields = owner.fields.subList(0, elements.size()).stream().map(StructField::type).toList();
                var resolution = CppOverloadResolver.resolve(List.of(new CppOverloadResolver.Candidate<>(owner, fields, false)),
                        elements, null, conversions);
                return resolution.status() == CppOverloadResolver.Status.NO_VIABLE ? null
                        : new CppOverloadResolver.UserConversion(owner, result, resolution.status() == CppOverloadResolver.Status.AMBIGUOUS);
            }
            List<Constructor> constructors = allConstructors(owner);
            SourceRange range=owner.sourceRecord==null?Binding.this.source.range():owner.sourceRecord.range();
            List<Constructor> ordinary=expandConstructorTemplateShapes(constructors,elements,range);
            List<CppOverloadResolver.Candidate<Constructor>> candidates=ordinary.stream().map(this::constructorCandidate).toList();
            if (!(elements.isEmpty() && ordinary.stream().anyMatch(c -> requiredParameters(c.function,c.parameterTypes.size())==0))) {
                var listCandidates = expandConstructorTemplateShapes(constructors,List.of(source),range).stream()
                        .filter(this::initializerListConstructor).map(this::constructorCandidate).toList();
                var phase = CppOverloadResolver.resolve(listCandidates, List.of(source), null, conversions,this::betterTemplateCandidate);
                if (phase.status() != CppOverloadResolver.Status.NO_VIABLE)
                    return new CppOverloadResolver.UserConversion(phase.winner() == null ? owner : phase.winner().identity(), result,
                            phase.status() == CppOverloadResolver.Status.AMBIGUOUS);
            }
            var phase = CppOverloadResolver.resolve(candidates, elements, null, conversions,this::betterTemplateCandidate);
            return phase.status() == CppOverloadResolver.Status.NO_VIABLE ? null
                    : new CppOverloadResolver.UserConversion(phase.winner() == null ? owner : phase.winner().identity(), result,
                            phase.status() == CppOverloadResolver.Status.AMBIGUOUS,
                            elements.size() == 1 && !elements.getFirst().braced() && elements.getFirst().type().unqualified().equals(object));
        }
        private Expression bindListValue(MiniType target, Expression source, Namespace namespace, Local local) {
            CppInitializer syntax = new CppInitializer(CppInitializer.Kind.COPY_LIST, listItems(source), source.range());
            if (target.isVoid() || target.isFunction()) {
                report("CPP004", source.range(), "A braced list requires an object or reference target.");
                return new IntegerLiteralExpr(0, "0", source.range());
            }
            if (target.isReference()) return bindReference(target, source, namespace, local, source.range());
            if (target.isArray()) return listArrayValue(target, syntax.arguments(), namespace, local, source.range());
            return mapped(source, variableInitializer(target, syntax, null, namespace, local, source.range()));
        }

        private boolean referenceRelated(MiniType first, MiniType second) {
            if (first.isArray() && second.isArray()) return first.arrayLength() == second.arrayLength()
                    && referenceRelated(first.elementType(), second.elementType());
            return first.unqualified().equals(second.unqualified());
        }
        private Expression convertListElement(MiniType target, Expression value, Expression source) {
            if (objectType(declaredExpressionType(value)) != null && objectType(target) == null) {
                Expression converted = userConversion(target, value, ConversionContext.IMPLICIT, source.range());
                if (converted != null) value = converted;
            }
            requireNonNarrowing(target, value, source.range());
            return convertCallValue(target, value, source);
        }
        private Expression listArrayValue(MiniType target, List<Expression> sources, Namespace namespace, Local local, SourceRange range) {
            if (target.arrayLength() < 0 || sources.size() > target.arrayLength()) {
                report("CPP004", range, "An array list requires a complete bound and no excess elements.");
                return new AggregateInitExpr(List.of(), range);
            }
            MiniType element = MiniType.qualified(target.elementType(), target.qualifiers());
            String destination = freshName("list_array");
            Expression array = typed(new UnaryExpr(TokenType.STAR,
                    typed(new NameExpr(destination, range), coreType(target).pointerTo()), range), target);
            List<Expression> actions = new ArrayList<>();
            for (int index = 0; index < target.arrayLength(); index++) {
                Expression item = index < sources.size() ? sources.get(index) : new CppInitializer(CppInitializer.Kind.COPY_LIST, List.of(), range);
                Expression value = isBraced(item) ? bindListValue(element, item, namespace, local)
                        : expressionForTarget(element, item, namespace, local);
                value = convertListElement(element, value, item);
                Expression slot = typed(new IndexExpr(array, new IntegerLiteralExpr(index, Integer.toString(index), range), range), element);
                actions.add(new InitializeExpr(slot, value, item.range()));
            }
            Expression body = actions.isEmpty() ? new CastExpr(MiniType.VOID, new IntegerLiteralExpr(0, "0", range), range)
                    : new CommaExpr(actions, range);
            return typed(new ObjectInitExpr(coreType(target), destination, body, range), target);
        }

        private Expression initializeList(MiniType target, CppInitializer syntax, Namespace namespace, Local local, SourceRange range) {
            List<Expression> sources = syntax.arguments();
            MiniType element = initializerListElement(target);
            TypeEntity owner = objectType(target);
            requireComplete(target, range);
            if (!sources.isEmpty()) { requireComplete(element, range); destructorForUse(element, range); }
            MiniType constantElement = MiniType.qualified(element, Set.of(MiniType.TypeQualifier.CONST));
            if (owner == null || owner.fields.size() != 2 || !owner.fields.getFirst().type().equals(constantElement.pointerTo())
                    || !owner.fields.get(1).type().isIntegerScalar()) {
                report("CPP004", range, "std::initializer_list must have its library pointer/size representation.");
                return new IntegerLiteralExpr(0, "0", range);
            }
            List<Expression> values = new ArrayList<>();
            for (Expression source : sources) {
                Expression value = isBraced(source) ? bindListValue(element, source, namespace, local)
                        : expressionForTarget(element, source, namespace, local);
                value = convertListElement(element, value, source);
                values.add(value);
            }
            Expression lifetimeOwner = fullExpressionOwner != null ? fullExpressionOwner : syntax;
            return listObject(new ListStorage(target, constantElement, List.copyOf(values), range),
                    new TemporaryLifetime(TemporaryLifetime.Kind.FULL_EXPRESSION, lifetimeOwner));
        }
        private Expression listObject(ListStorage storage, TemporaryLifetime lifetime) {
            TypeEntity owner = objectType(storage.type);
            String destination = freshName("initializer_list");
            Expression pointer = typed(new NameExpr(destination, storage.range), owner.type.pointerTo());
            Expression first;
            if (storage.values.isEmpty()) first = typed(new CastExpr(coreType(storage.element.pointerTo()),
                    new NullLiteralExpr("nullptr", storage.range), storage.range), storage.element.pointerTo());
            else {
                MiniType arrayType = storage.element.arrayOf(storage.values.size());
                List<Expression> elements = storage.values.stream().map(value -> extendListLifetime(value, lifetime)).toList();
                Expression initializer = new AggregateInitExpr(elements, storage.range);
                Expression array = typed(new MaterializeExpr(coreType(arrayType), initializer, lifetime, storage.range), arrayType.pointerTo());
                first = typed(new CastExpr(coreType(storage.element.pointerTo()), array, storage.range), storage.element.pointerTo());
            }
            StructField firstField = owner.fields.getFirst(), countField = owner.fields.get(1);
            Expression firstStore = new InitializeExpr(typed(new FieldAccessExpr(pointer, firstField.name(), true, storage.range),
                    firstField.type()), first, storage.range);
            Expression count = typed(new CastExpr(coreType(countField.type()),
                    new IntegerLiteralExpr(storage.values.size(), Integer.toString(storage.values.size()), storage.range), storage.range), countField.type());
            Expression countStore = new InitializeExpr(typed(new FieldAccessExpr(pointer, countField.name(), true, storage.range),
                    countField.type()), count, storage.range);
            Expression result = typed(new ObjectInitExpr(coreType(storage.type), destination,
                    new CommaExpr(List.of(firstStore, countStore), storage.range), storage.range), storage.type);
            if (!storage.values.isEmpty()) listStorage.put(result, storage);
            return result;
        }
        private boolean hasListStorage(Expression value) {
            if (listStorage.containsKey(value)) return true;
            return value instanceof GroupingExpr group && hasListStorage(group.expression())
                    || value instanceof CommaExpr comma && hasListStorage(comma.expressions().getLast())
                    || value instanceof ConditionalExpr conditional && (hasListStorage(conditional.thenExpression()) || hasListStorage(conditional.elseExpression()));
        }
        /** Extend only an array created for the directly initialized list; a returned/copied list does not own it. */
        private Expression extendListLifetime(Expression value, TemporaryLifetime lifetime) {
            if (value == null) return null;
            Expression result;
            if (listStorage.containsKey(value)) result = listObject(listStorage.get(value), lifetime);
            else result = switch (value) {
                case GroupingExpr group -> new GroupingExpr(extendListLifetime(group.expression(), lifetime), group.range());
                case CommaExpr comma -> {
                    var items = new ArrayList<>(comma.expressions());
                    items.set(items.size() - 1, extendListLifetime(items.getLast(), lifetime));
                    yield new CommaExpr(items, comma.range());
                }
                case ConditionalExpr conditional -> new ConditionalExpr(conditional.condition(),
                        extendListLifetime(conditional.thenExpression(), lifetime), extendListLifetime(conditional.elseExpression(), lifetime), conditional.range());
                case LetExpr let -> new LetExpr(let.name(), let.type(), let.initializer(), extendListLifetime(let.body(), lifetime), let.range());
                default -> value;
            };
            if (result != value) {
                if (declaredExpressionTypes.containsKey(value)) declaredExpressionTypes.put(result, declaredExpressionTypes.get(value));
                if (valueCategories.containsKey(value)) valueCategories.put(result, valueCategories.get(value));
                origins.replaceAll((source, core) -> core == value ? result : core);
            }
            return result;
        }

        private record PreparedArguments(List<Expression> values, Map<Integer, OverloadDesignator> overloads) { }

        private PreparedArguments prepareArguments(List<Expression> source, Namespace namespace, Local local) {
            List<Expression> values = new ArrayList<>();
            Map<Integer, OverloadDesignator> overloads = new LinkedHashMap<>();
            for (int index = 0; index < source.size(); index++) {
                OverloadDesignator designator = overloadDesignator(source.get(index), namespace, local);
                if (designator != null) overloads.put(index, designator);
                if (designator == null && isBraced(source.get(index))) {
                    Expression item = source.get(index);
                    if (!bracedArguments.containsKey(item)) bracedArguments.put(item, prepareArguments(listItems(item), namespace, local));
                    values.add(item);
                } else values.add(designator == null ? expression(source.get(index), namespace, local) : null);
            }
            return new PreparedArguments(Collections.unmodifiableList(values), Map.copyOf(overloads));
        }

        private List<Expression> recoveryArguments(List<Expression> source, List<Expression> values) {
            List<Expression> result = new ArrayList<>(values);
            for (int index = 0; index < result.size(); index++) {
                if (result.get(index) == null) result.set(index, new IntegerLiteralExpr(0, "0", source.get(index).range()));
            }
            return result;
        }

        private <T> T selectOverload(List<CppOverloadResolver.Candidate<T>> candidates, Expression sourceCallee,
                                     List<Expression> sourceArguments, PreparedArguments prepared,
                                     CppOverloadResolver.Argument receiver) {
            List<CppOverloadResolver.Argument> arguments = new ArrayList<>();
            for (int index = 0; index < prepared.values.size(); index++) {
                if (prepared.overloads.containsKey(index)) {
                    arguments.add(new CppOverloadResolver.Argument(MiniType.INT, CppValueCategory.PRVALUE, false));
                    continue;
                }
                Expression value = prepared.values.get(index);
                CppOverloadResolver.Argument shape = argumentShape(value, sourceArguments.get(index));
                if (shape == null) { report("CPP004", sourceArguments.get(index).range(), "无法确定重载实参的类型。"); return null; }
                arguments.add(shape);
            }
            List<CppOverloadResolver.Candidate<T>> contextual = new ArrayList<>();
            for (var candidate : candidates) {
                List<MiniType> parameters = new ArrayList<>(candidate.parameterTypes());
                boolean viable = true;
                for (var entry : prepared.overloads.entrySet()) {
                    int index = entry.getKey();
                    MiniType parameter = index < parameters.size() ? parameters.get(index) : null;
                    OverloadDesignator designator = entry.getValue();
                    Entity selected = functionForTarget(designator, parameter);
                    if (selected == null) { viable = false; break; }
                    boolean pointer = designator.addressOf && !(parameter.isReference() && parameter.referent().isFunction());
                    var argument = new CppOverloadResolver.Argument(pointer ? selected.type.pointerTo() : selected.type,
                            pointer ? CppValueCategory.PRVALUE : CppValueCategory.LVALUE, false);
                    if (CppOverloadResolver.resolve(List.of(new CppOverloadResolver.Candidate<>(selected, List.of(parameter), false)),
                            List.of(argument)).status() != CppOverloadResolver.Status.SELECTED) { viable = false; break; }
                    // Resolving an overloaded designator against its target contributes an exact
                    // match. Use the same neutral conversion across viable outer candidates;
                    // their remaining arguments still compete independently, never by rank sum.
                    parameters.set(index, MiniType.INT);
                }
                if (viable) contextual.add(new CppOverloadResolver.Candidate<>(candidate.identity(), parameters,
                        candidate.variadic(), candidate.implicitObjectType(), candidate.staticMember(),candidate.requiredParameterCount()));
            }
            var resolution = CppOverloadResolver.resolve(contextual, arguments, receiver, conversions, this::betterTemplateCandidate);
            if (resolution.status() == CppOverloadResolver.Status.SELECTED) return resolution.winner().identity();
            report("CPP004", sourceCallee.range(), resolution.status() == CppOverloadResolver.Status.AMBIGUOUS
                    ? "重载函数调用具有二义性。" : "没有与实参匹配的重载函数。");
            return null;
        }

        private List<Expression> lowerSelectedArguments(List<MiniType> parameters, List<Expression> sourceArguments,
                                                        List<Expression> values, Namespace namespace, Local local) {
            return lowerSelectedArguments(parameters, sourceArguments, values, namespace, local, false);
        }
        private List<Expression> lowerSelectedArguments(List<MiniType> parameters, List<Expression> sourceArguments,
                                                        List<Expression> values, Namespace namespace, Local local, boolean listNarrowing) {
            List<Expression> lowered = new ArrayList<>(values);
            for (int index = 0; index < sourceArguments.size() && index < parameters.size(); index++) {
                destructorForUse(parameters.get(index), sourceArguments.get(index).range());
                if (parameters.get(index).isReference()) {
                    Expression reference = bindReference(parameters.get(index), sourceArguments.get(index), namespace, local, sourceArguments.get(index).range());
                    if (listNarrowing) checkReferenceListConversion(parameters.get(index).referent(), reference, sourceArguments.get(index).range());
                    lowered.set(index, reference);
                }
                else {
                    Expression value = isBraced(sourceArguments.get(index))
                            ? bindListValue(parameters.get(index), sourceArguments.get(index), namespace, local)
                            : values.get(index) == null ? expressionForTarget(parameters.get(index), sourceArguments.get(index), namespace, local) : values.get(index);
                    lowered.set(index, listNarrowing ? convertListElement(parameters.get(index), value, sourceArguments.get(index))
                            : convertCallValue(parameters.get(index), value, sourceArguments.get(index)));
                }
            }
            return List.copyOf(lowered);
        }

        /** Single candidates and indirect calls obey the same C++ conversions as overload sets. */
        private Expression convertCallValue(MiniType parameter, Expression value, Expression source) {
            return convertCallValue(parameter, value, source, null);
        }

        private Expression convertCallValue(MiniType parameter, Expression value, Expression source, String initializedVariable) {
            if (parameter == null || value == null) return value; // ellipsis/arity remains a core check
            MiniType actual = declaredExpressionType(value);
            if (actual == null) return value; // unsupported initializer forms retain their existing diagnostics
            if (parameter.isVoid() && actual.isVoid()) return value; // void return with a void expression
            var argument = new CppOverloadResolver.Argument(actual, valueCategory(value), isNullIntegerLiteral(source));
            if (!standardViable(argument, parameter)) {
                Expression converted = userConversion(parameter, value, ConversionContext.IMPLICIT, source.range());
                if (converted != null) {
                    value = converted; actual = declaredExpressionType(value);
                    argument = new CppOverloadResolver.Argument(actual, valueCategory(value), false);
                }
            }
            if (!standardViable(argument,parameter)) {
                report("CPP004", source.range(), initializedVariable == null
                        ? "实参不能按 C++ 标准转换为参数类型。"
                        : "变量“" + initializedVariable + "”的初始化值不能按 C++ 标准转换为目标类型。");
                return value;
            }
            MiniType target = TypeCompatibility.decay(parameter).unqualified();
            MiniType from = TypeCompatibility.decay(actual).unqualified();
            if (target.isStruct() && baseDistance(from,target)>0) return markerBaseValue(target,value,source.range());
            if (target.isStruct() && target.equals(from)) return copyInitialize(target, value, source.range(), CppInitializer.Kind.COPY);
            if (!target.equals(from) && (target.isPointer() || target.equals(MiniType.BOOL) && from.isPointer())) {
                // Core C is narrower for pointer-to-bool and deep qualification conversions.
                // This cast spells only a conversion already approved above.
                return typed(new CastExpr(coreType(target), value, source.range()), target);
            }
            return value;
        }

        private Expression rebuildCalleeGroups(Expression original, Expression designator, Expression core) {
            if (original == designator || original instanceof CppTemplateIdExpr) return mapped(original, core);
            GroupingExpr group = (GroupingExpr) original;
            return mapped(original, new GroupingExpr(rebuildCalleeGroups(group.expression(), designator, core), original.range()));
        }

        private Expression returnValue(Expression source,Namespace namespace,Local scope) {
            Expression value=expressionForTarget(currentReturnType,source,namespace,scope);
            Expression plain=value;while(plain instanceof GroupingExpr group)plain=group.expression();
            if(currentReturnType!=null&&currentReturnType.isStruct()&&plain instanceof NameExpr name) {
                Entity entity=coreValues.get(name.name());
                if(entity!=null&&entity.owner==null&&!staticLocalEntities.contains(entity)&&!entity.type.isReference()&&!entity.type.isVolatileQualified())implicitMoveSources.add(value);
            }
            return convertCallValue(currentReturnType,value,source);
        }
        private Expression referenceResult(MiniType reference,Expression pointer,SourceRange range) {
            Expression object=typed(new UnaryExpr(TokenType.STAR,pointer,range),reference.referent());
            valueCategories.put(object,reference.isRvalueReference()&&!reference.referent().isFunction()?CppValueCategory.XVALUE:CppValueCategory.LVALUE);
            return object;
        }
        private boolean addressableObject(Expression node) {
            if (node instanceof GroupingExpr group) return addressableObject(group.expression());
            if (node instanceof NameExpr) return true;
            if (node instanceof UnaryExpr unary && unary.operator() == TokenType.STAR) return true;
            if (node instanceof FieldAccessExpr field) return field.viaPointer() || addressableObject(field.target());
            if (node instanceof IndexExpr index) {
                MiniType target = declaredExpressionType(index.target());
                return target != null && (target.isPointer() || addressableObject(index.target()));
            }
            return false;
        }

        /** Logical source category is independent of whether core C requires an address rewrite. */
        private CppValueCategory valueCategory(Expression node) {
            CppValueCategory known = valueCategories.get(node);
            if (known != null) return known;
            if (node instanceof GroupingExpr group) return valueCategory(group.expression());
            if (node instanceof CommaExpr comma) return valueCategory(comma.expressions().getLast());
            if (addressableObject(node)) return CppValueCategory.LVALUE;
            return CppValueCategory.PRVALUE;
        }

        private MethodSet memberMethods(MiniType owner, String name) {
            TypeEntity type = objectType(owner);
            if (type != null && name.equals("operator=")) {ensureImplicitAssignment(type);ensureImplicitMove(type);}
            return type == null ? null : type.methods.get(name);
        }

        private void requireMethodAccess(Method method, SourceRange range) {
            if(isDeleted(method.function))report("CPP004",range,"Selected member function is deleted: "+method.source.method().name());
            if(method==method.owner.implicitMoveAssignment)emitImplicitMoveAssignment(method.owner);
            if (method == method.owner.implicitAssignment) {
                if (!method.owner.assignmentPlan.problems.isEmpty())
                    report("CPP004", range, "隐式复制赋值已被删除：" + method.owner.assignmentPlan.problems.getFirst());
                else emitImplicitAssignment(method.owner);
            }
            if (method.access != Access.PUBLIC && !classAccess(method.owner)) {
                report("CPP004", range, "不能访问 " + method.access.name().toLowerCase(java.util.Locale.ROOT)
                        + " 成员函数 " + method.owner.canonicalName + "::" + method.source.method().name());
            }
        }

        private void requireUpdateOperand(Expression operand, SourceRange range) {
            MiniType type = declaredExpressionType(operand);
            if (valueCategory(operand) != CppValueCategory.LVALUE) {
                report("CPP004", range, "自增或自减要求可修改的左值。");
            }
            if (type != null && type.unqualified().equals(MiniType.BOOL)) {
                report("CPP004", range, "C++17 不允许对 bool 进行自增或自减");
            }
            requireComplete(elementType(type), range);
        }

        /**
         * Tracks source types at this declaration position and builtin expression result types
         * needed by value captures. Operator/conversion validity remains a core semantic check.
         * In particular an incomplete pointee is harmless until an operation
         * requires its object layout, and later definitions cannot change an earlier check.
         */
        private MiniType declaredExpressionType(Expression expression) {
            if (expression == null) return null;
            if (declaredExpressionTypes.containsKey(expression)) return declaredExpressionTypes.get(expression);
            MiniType type = switch (expression) {
                case BoolLiteralExpr ignored -> MiniType.BOOL;
                case CharLiteralExpr character -> switch (character.encoding()) {
                    case ORDINARY, UTF8 -> MiniType.CHAR;
                    case UTF16 -> MiniType.UNSIGNED_SHORT;
                    case UTF32 -> MiniType.UNSIGNED_INT;
                };
                case IntegerLiteralExpr ignored -> MiniType.INT;
                case IntegerConstantExpr integer -> integer.type();
                case LongLiteralExpr ignored -> MiniType.LONG;
                case FloatLiteralExpr ignored -> MiniType.FLOAT;
                case DoubleLiteralExpr ignored -> MiniType.DOUBLE;
                case NullLiteralExpr ignored -> MiniType.NULL;
                case StringLiteralExpr string -> stringLiteralType(string);
                case SizeofExpr ignored -> MiniType.UNSIGNED_LONG_LONG;
                case AlignofExpr ignored -> MiniType.UNSIGNED_LONG_LONG;
                case LetExpr capture -> declaredExpressionType(capture.body());
                case NameExpr name -> coreValues.containsKey(name.name()) ? coreValues.get(name.name()).type : null;
                case GroupingExpr group -> declaredExpressionType(group.expression());
                case CastExpr cast -> cast.targetType();
                case AssignmentExpr assignment -> declaredExpressionType(assignment.target());
                case CommaExpr comma -> declaredExpressionType(comma.expressions().getLast());
                case ConditionalExpr conditional -> conditionalDeclaredType(
                        declaredExpressionType(conditional.thenExpression()), declaredExpressionType(conditional.elseExpression()));
                case BinaryExpr binary -> {
                    if (hasBooleanResult(binary.operator())) yield MiniType.BOOL;
                    MiniType left = declaredExpressionType(binary.left());
                    MiniType right = declaredExpressionType(binary.right());
                    boolean leftPointer = elementType(left) != null;
                    boolean rightPointer = elementType(right) != null;
                    if (binary.operator() == TokenType.PLUS && leftPointer != rightPointer) {
                        yield elementType(leftPointer ? left : right).pointerTo();
                    }
                    if (binary.operator() == TokenType.MINUS && leftPointer && !rightPointer) yield elementType(left).pointerTo();
                    yield left == null || right == null ? null : TypeCompatibility.binaryResultType(left, right, binary.operator());
                }
                case UnaryExpr unary -> {
                    MiniType operand = declaredExpressionType(unary.operand());
                    yield switch (unary.operator()) {
                        case STAR -> operand != null && operand.isFunction() ? operand : elementType(operand);
                        case AMPERSAND -> operand == null ? null : operand.pointerTo();
                        case PLUS_PLUS, MINUS_MINUS -> operand;
                        case BANG -> MiniType.BOOL;
                        case TILDE, PLUS, MINUS -> operand == null ? null
                                : operand.isIntegerScalar() ? TypeCompatibility.integerPromotion(operand) : operand;
                        default -> null;
                    };
                }
                case PostfixUpdateExpr update -> declaredExpressionType(update.target());
                case IndexExpr index -> elementType(declaredExpressionType(index.target()));
                case FieldAccessExpr field -> {
                    MiniType owner = declaredExpressionType(field.target());
                    yield declaredFieldType(field.viaPointer() ? elementType(owner) : owner, field.fieldName(), new HashSet<>());
                }
                case CallExpr call -> {
                    MiniType.FunctionType signature = functionSignature(declaredExpressionType(call.callee()));
                    yield signature == null ? null : signature.returnType();
                }
                case VaArgExpr argument -> argument.requestedType();
                default -> null;
            };
            declaredExpressionTypes.put(expression, type);
            return type;
        }

        private MiniType conditionalDeclaredType(MiniType first, MiniType second) {
            if (first == null) return second;
            if (second == null) return first;
            if (first.unqualified().equals(second.unqualified())) return inheritObjectQualifiers(second, first);
            if (first.isScalar() && second.isScalar()) return TypeCompatibility.conditionalResultType(first, second);
            if (first.isPointer() && second.isPointer()) {
                MiniType a = elementType(first), b = elementType(second);
                if (a.isVoid() || b.isVoid()) return MiniType.VOID.pointerTo();
                MiniType common = conditionalDeclaredType(a, b);
                if (common != null) return common.pointerTo();
            }
            return null;
        }

        private MiniType conditionalLvalueType(MiniType first, MiniType second) {
            if (first == null || second == null) return null;
            if (first.isArray() && second.isArray() && first.arrayLength() == second.arrayLength()) {
                MiniType element = conditionalLvalueType(elementType(first), elementType(second));
                return element == null ? null : element.arrayOf(first.arrayLength());
            }
            return first.unqualified().equals(second.unqualified()) ? inheritObjectQualifiers(first, second) : null;
        }

        private MiniType elementType(MiniType type) {
            if (type == null) return null;
            return switch (type.unqualified()) {
                case MiniType.PointerType pointer -> pointer.pointee();
                case MiniType.ArrayType array -> inheritObjectQualifiers(type, array.elementType());
                default -> null;
            };
        }

        private MiniType.FunctionType functionSignature(MiniType type) {
            if (type == null) return null;
            type = type.unqualified();
            if (type instanceof MiniType.PointerType pointer) type = pointer.pointee().unqualified();
            return type instanceof MiniType.FunctionType signature ? signature : null;
        }

        private MiniType declaredFieldType(MiniType owner, String name, Set<String> visited) {
            FieldPath path = fieldPath(owner, name, visited);
            return path == null ? null : path.type();
        }

        private record FieldStep(TypeEntity owner, StructField field, Access access) { }
        private record FieldPath(MiniType type, List<FieldStep> steps) { }

        private FieldPath fieldPath(MiniType owner, String name, Set<String> visited) {
            if (owner == null || !(owner.unqualified() instanceof MiniType.StructType struct) || !visited.add(struct.name())) return null;
            TypeEntity entity = coreTypes.get(struct.name());
            if (entity == null || !entity.complete) return null;
            for (StructField field : entity.fields) {
                if (!field.anonymous() && field.name().equals(name)) return new FieldPath(inheritObjectQualifiers(owner, field.type()),
                        List.of(new FieldStep(entity, field, entity.fieldAccess.getOrDefault(field, Access.PUBLIC))));
            }
            for (StructField field : entity.fields) {
                if (!field.anonymous()) continue;
                FieldPath promoted = fieldPath(inheritObjectQualifiers(owner, field.type()), name, visited);
                if (promoted != null) {
                    List<FieldStep> steps = new ArrayList<>();
                    steps.add(new FieldStep(entity, field, entity.fieldAccess.getOrDefault(field, Access.PUBLIC)));
                    steps.addAll(promoted.steps());
                    return new FieldPath(promoted.type(), List.copyOf(steps));
                }
            }
            return null;
        }

        private FieldPath requireAccessible(MiniType owner, String name, SourceRange range, String operation) {
            if (owner == null) {
                report("CPP005", range, "无法确定" + operation + "的接收者类型：" + name);
                return null;
            }
            FieldPath path = fieldPath(owner, name, new HashSet<>());
            // Unknown fields and non-record operands are diagnosed by the core semantic checker.
            if (path != null) {
                for (FieldStep step : path.steps()) {
                    if (step.access() != Access.PUBLIC && !classAccess(step.owner())) {
                        report("CPP004", range, operation + "不能访问 " + step.access().name().toLowerCase(java.util.Locale.ROOT)
                                + " 成员 " + step.owner().canonicalName + "::" + name);
                        break;
                    }
                }
            }
            return path;
        }

        private MiniType inheritObjectQualifiers(MiniType owner, MiniType member) {
            var qualifiers = java.util.EnumSet.noneOf(MiniType.TypeQualifier.class);
            qualifiers.addAll(member.qualifiers());
            if (owner.isConstQualified()) qualifiers.add(MiniType.TypeQualifier.CONST);
            if (owner.isVolatileQualified()) qualifiers.add(MiniType.TypeQualifier.VOLATILE);
            return MiniType.qualified(member.unqualified(), qualifiers);
        }

        private TypeEntity objectType(MiniType type) {
            return type != null && type.unqualified() instanceof MiniType.StructType struct ? coreTypes.get(struct.name()) : null;
        }

        private boolean nonAggregate(TypeEntity type) {
            if(lambdaTypes.containsKey(type))return true;
            return type != null && (type.constructors.stream().anyMatch(c -> !c.implicit&&(c.source.explicitSpecifier()||c.source.definitionKind()==DefinitionKind.ORDINARY||userProvidedDefaulted.contains(c.function)))
                    || type.fields.stream().anyMatch(f -> type.fieldAccess.getOrDefault(f, Access.PUBLIC) != Access.PUBLIC));
        }

        private Expression fieldReference(Expression receiver, String name, boolean arrow, MiniType owner, SourceRange range) {
            Expression field = new FieldAccessExpr(receiver, name, arrow, range);
            MiniType type = declaredFieldType(owner, name, new HashSet<>());
            if (type == null || !type.isReference()) return field;
            typed(field, coreType(type));
            return typed(new UnaryExpr(TokenType.STAR, field, range), type.referent());
        }

        private boolean recordArray(MiniType type) {
            while (type.isArray()) type = elementType(type);
            return type.isStruct();
        }

        private Expression arrayInitialization(MiniType type, CppInitializer syntax, Namespace namespace,
                                               Local local, SourceRange range) {
            if (type.arrayLength() < 0) {
                report("CPP004", range, "An array object requires a complete bound before construction.");
                return null;
            }
            if (syntax.kind() == CppInitializer.Kind.COPY
                    || syntax.kind() == CppInitializer.Kind.DIRECT_PAREN && !syntax.arguments().isEmpty()) {
                report("CPP004", syntax.range(), "C++17 arrays require a braced initializer list.");
            }
            var cursor = syntax.arguments().listIterator();
            Expression result = arrayValue(type, cursor, syntax.kind() == CppInitializer.Kind.DEFAULT,
                    namespace, local, range);
            if (cursor.hasNext()) report("CPP004", cursor.next().range(), "Too many array initializer elements.");
            return initializerMapping(syntax, result);
        }

        /** A cursor spans brace-elided dimensions; explicit braces start a separate cursor. */
        private Expression arrayValue(MiniType type, java.util.ListIterator<Expression> values, boolean defaultInitialize,
                                      Namespace namespace, Local local, SourceRange range) {
            MiniType element = elementType(type);
            String destination = freshName("array_construction");
            Expression pointer = typed(new NameExpr(destination, range), coreType(type).pointerTo());
            Expression array = typed(new UnaryExpr(TokenType.STAR, pointer, range), coreType(type));
            List<Expression> actions = new ArrayList<>();
            int index = 0;
            while (index < type.arrayLength() && values.hasNext()) {
                Expression source = values.next();
                Expression initialized;
                if (element.isArray() && !isBraced(source)) {
                    values.previous();
                    initialized = arrayValue(element, values, false, namespace, local, source.range());
                } else {
                    CppInitializer syntax = isBraced(source)
                            ? new CppInitializer(CppInitializer.Kind.COPY_LIST, listItems(source), source.range())
                            : new CppInitializer(CppInitializer.Kind.COPY, List.of(source), source.range());
                    initialized = element.isArray() ? arrayInitialization(element, syntax, namespace, local, source.range())
                            : variableInitializer(element, syntax, null, namespace, local, source.range());
                    if (source instanceof AggregateInitExpr && initialized != null) {
                        initialized = mapped(source, typed(new GroupingExpr(initialized, source.range()), element));
                    }
                }
                if (initialized != null) {
                    Expression slot = typed(new IndexExpr(array, new IntegerLiteralExpr(index, Integer.toString(index), range), range), coreType(element));
                    Expression action = typed(new InitializeExpr(slot, initialized, source.range()), MiniType.VOID);
                    // Temporaries belonging to this element finish before the next element starts.
                    actions.add(fullExpression(action, false, source));
                }
                index++;
            }
            if (index < type.arrayLength()) {
                Expression remaining = initializeArrayRemainder(type, pointer, index, defaultInitialize, namespace, local, range);
                if (remaining != null) actions.add(remaining);
            }
            Expression body = actions.isEmpty() ? new CastExpr(MiniType.VOID, new IntegerLiteralExpr(0, "0", range), range)
                    : actions.size() == 1 ? actions.getFirst() : new CommaExpr(actions, range);
            return typed(new ObjectInitExpr(coreType(type), destination, body, range), type);
        }

        /** Default/value initialization uses a core loop rather than duplicating the element body N times. */
        private Expression initializeArrayRemainder(MiniType type, Expression address, int start, boolean defaultInitialize,
                                                    Namespace namespace, Local local, SourceRange range) {
            MiniType element = elementType(type);
            CppInitializer syntax = new CppInitializer(defaultInitialize ? CppInitializer.Kind.DEFAULT
                    : CppInitializer.Kind.COPY_LIST, List.of(), range);
            Expression initialized = element.isArray() ? arrayInitialization(element, syntax, namespace, local, range)
                    : variableInitializer(element, syntax, null, namespace, local, range);
            if (initialized == null) return null;
            String storage = freshName("array_storage"), index = freshName("array_index");
            MiniType pointerType = coreType(element).pointerTo();
            Expression pointer = typed(new NameExpr(storage, range), pointerType);
            Expression i = typed(new NameExpr(index, range), MiniType.INT);
            Expression slot = typed(new IndexExpr(pointer, i, range), coreType(element));
            Expression action = fullExpression(typed(new InitializeExpr(slot, initialized, range), MiniType.VOID), false, syntax);
            Statement loop = new ForStmt(new VarDeclStmt(index, MiniType.INT, new IntegerLiteralExpr(start, "begin", range), range),
                    new BinaryExpr(i, TokenType.LESS, new IntegerLiteralExpr(type.arrayLength(), "length", range), range),
                    new PostfixUpdateExpr(i, TokenType.PLUS_PLUS, range), new ExprStmt(action, range), range);
            String helper = arrayHelper("array_initialize", pointerType, storage, loop, range);
            return typed(new CallExpr(new NameExpr(helper, range),
                    List.of(typed(new CastExpr(pointerType, address, range), pointerType)), range), MiniType.VOID);
        }

        private Expression arrayDestruction(MiniType type, Expression address, SourceRange range) {
            if (type.arrayLength() < 0) {
                report("CPP004", range, "Array destruction requires a complete bound.");
                return null;
            }
            MiniType element = elementType(type);
            String storage = freshName("array_storage"), index = freshName("array_index");
            MiniType pointerType = coreType(element).pointerTo();
            Expression pointer = typed(new NameExpr(storage, range), pointerType);
            Expression i = typed(new NameExpr(index, range), MiniType.INT);
            Expression slot = typed(new IndexExpr(pointer, i, range), coreType(element));
            Expression cleanup = destruction(element, address(slot), range);
            if (cleanup == null) return null;
            Statement loop = new ForStmt(new VarDeclStmt(index, MiniType.INT,
                    new IntegerLiteralExpr(type.arrayLength() - 1, "last", range), range),
                    new BinaryExpr(i, TokenType.GREATER_EQUAL, new IntegerLiteralExpr(0, "0", range), range),
                    new PostfixUpdateExpr(i, TokenType.MINUS_MINUS, range), new ExprStmt(cleanup, range), range);
            String helper = arrayHelper("array_destroy", pointerType, storage, loop, range);
            return typed(new CallExpr(new NameExpr(helper, range),
                    List.of(typed(new CastExpr(pointerType, address, range), pointerType)), range), MiniType.VOID);
        }

        private String arrayHelper(String label, MiniType pointer, String parameter, Statement body, SourceRange range) {
            String name = freshName(label);
            MiniType signature = MiniType.function(MiniType.VOID, List.of(pointer), false);
            coreValues.put(name, new Entity(label, name, Kind.FUNCTION, root, signature, null, true));
            FunctionDecl function = new FunctionDecl(name, MiniType.VOID, List.of(new Parameter(parameter, pointer, range)),
                    false, new BlockStmt(List.of(body), range), false, range);
            functions.add(function); declarations.add(function);
            return name;
        }

        private Expression variableInitializer(MiniType target, CppInitializer syntax, Expression legacy,
                                               Namespace namespace, Local local, SourceRange range) {
            return variableInitializer(target, syntax, legacy, namespace, local, range, null);
        }

        private Expression variableInitializer(MiniType target, CppInitializer syntax, Expression legacy,
                                               Namespace namespace, Local local, SourceRange range, String sourceName) {
            if (syntax == null) return initializer(target, legacy, namespace, local, range);
            if (!target.isReference() && isList(syntax) && initializerListElement(target) != null)
                return initializerMapping(syntax, initializeList(target, syntax, namespace, local, syntax.range()));
            if (target.isArray() && recordArray(target)) return arrayInitialization(target, syntax, namespace, local, range);
            List<Expression> arguments = syntax.arguments();
            boolean list = syntax.kind() == CppInitializer.Kind.DIRECT_LIST || syntax.kind() == CppInitializer.Kind.COPY_LIST;
            if (target.isReference()) {
                Expression argument = legacy != null && !(legacy instanceof CppInitializer) ? legacy
                        : arguments.size() == 1 ? arguments.getFirst() : null;
                if (list && !(argument instanceof AggregateInitExpr)) argument = new AggregateInitExpr(arguments, syntax.range());
                return bindReference(target, argument, namespace, local, range,
                        syntax.kind() == CppInitializer.Kind.COPY || syntax.kind() == CppInitializer.Kind.COPY_LIST
                                ? ConversionContext.IMPLICIT : ConversionContext.EXPLICIT);
            }
            TypeEntity object = objectType(target);
            if (object != null && needsConstruction(object)) {
                Expression value = constructObject(target, syntax, namespace, local, range);
                return initializerMapping(syntax, value);
            }
            if (object != null || target.isArray()) {
                if (syntax.kind() == CppInitializer.Kind.DIRECT_PAREN) {
                    if (object != null && arguments.size() == 1) {
                        Expression value = expression(arguments.getFirst(), namespace, local);
                        MiniType actual = declaredExpressionType(value);
                        if (actual != null && actual.unqualified().equals(target.unqualified()))
                            return initializerMapping(syntax, copyInitialize(target, value, syntax.range(), syntax.kind()));
                        return initializerMapping(syntax, directClassConversion(target, value, syntax.range()));
                    }
                    report("CPP005", syntax.range(), "Parenthesized aggregate initialization requires a constructor in C++17.");
                    return arguments.isEmpty() ? null : expression(arguments.getFirst(), namespace, local);
                }
                if (object != null && object.fields.stream().anyMatch(field -> field.type().isReference())) {
                    report("CPP005", range, "Aggregate initialization of reference data members is not supported yet.");
                }
                Expression source = syntax.kind() == CppInitializer.Kind.DEFAULT ? null
                        : list ? legacy instanceof AggregateInitExpr ? legacy : new AggregateInitExpr(arguments, syntax.range()) : arguments.getFirst();
                return initializer(target, source, namespace, local, range);
            }
            if (syntax.kind() == CppInitializer.Kind.DEFAULT) {
                if (target.isConstQualified()) report("CPP004", range, "A const scalar data member requires initialization.");
                return null;
            }
            if (arguments.isEmpty()) return typed(new CastExpr(coreType(target.unqualified()),
                    new IntegerLiteralExpr(0, "0", syntax.range()), syntax.range()), target.unqualified());
            if (arguments.size() != 1) {
                report("CPP004", syntax.range(), "Scalar initialization requires exactly one value.");
                return expression(arguments.getFirst(), namespace, local);
            }
            Expression value = expressionForTarget(target, arguments.getFirst(), namespace, local);
            if (objectType(declaredExpressionType(value)) != null) {
                ConversionContext mode = syntax.kind() == CppInitializer.Kind.COPY
                        || syntax.kind() == CppInitializer.Kind.COPY_LIST ? ConversionContext.IMPLICIT : ConversionContext.EXPLICIT;
                Expression converted = userConversion(target, value, mode, arguments.getFirst().range());
                if (converted != null) value = converted;
            }
            if (list) requireNonNarrowing(target, value, arguments.getFirst().range());
            return initializerMapping(syntax, convertCallValue(target.unqualified(), value, arguments.getFirst(), sourceName));
        }

        private Expression placementConstruction(CppNewExpr source, Namespace namespace, Local local) {
            MiniType type = normalizeType(source.type(), namespace, local, source.typeRange());
            if (type.isVoid() || type.isReference() || type.isFunction() || type.isArray()) {
                report("CPP004", source.typeRange(), "Placement construction requires a complete non-array object type.");
                return new NullLiteralExpr("nullptr", source.range());
            }
            requireComplete(type, source.typeRange());
            TypeEntity owner = objectType(type);
            if (owner != null && !owner.complete) return new NullLiteralExpr("nullptr", source.range());
            // Class allocation functions remain explicitly rejected at their declarations until
            // their implicit-static lookup and access rules are available. Global lookup never uses ADL.
            List<Expression> arguments = new ArrayList<>();
            arguments.add(new SizeofExpr(null, source.type(), source.typeRange()));
            arguments.addAll(source.placementArguments());
            Expression designator = new QualifiedNameExpr(new QualifiedName(true, List.of("operator new"), source.range()));
            Expression allocation = expression(new CallExpr(designator, arguments, source.range()), namespace, local);
            String storage = freshName("new_storage");
            MiniType pointer = type.pointerTo();
            Expression address = typed(new CastExpr(coreType(pointer),
                    typed(new NameExpr(storage, source.range()), MiniType.VOID.pointerTo()), source.range()), pointer);
            CppInitializer syntax = source.initializer();
            // Empty parentheses value-initialize an aggregate; they do not supply constructor arguments.
            if (owner != null && !needsConstruction(owner) && syntax.kind() == CppInitializer.Kind.DIRECT_PAREN
                    && syntax.arguments().isEmpty()) {
                syntax = new CppInitializer(CppInitializer.Kind.DIRECT_LIST, List.of(), syntax.range());
            }
            Expression value = variableInitializer(type, syntax, null, namespace, local, source.range());
            Expression result = address;
            if (value != null) {
                Expression target = typed(new UnaryExpr(TokenType.STAR, address, source.range()), type);
                Expression initialize = typed(new InitializeExpr(target, value, source.range()), MiniType.VOID);
                result = typed(new CommaExpr(List.of(initialize, address), source.range()), pointer);
            }
            return typed(new LetExpr(storage, MiniType.VOID.pointerTo(), allocation, result, source.range()), pointer);
        }

        /** Type-only immediate-context checks: no invented variables, calls, ODR uses, or body emission. */
        private Expression typeQuery(CppTypeQueryExpr query, Namespace namespace, Local local) {
            if (query.arguments().stream().anyMatch(CppTypeQueryExpr.TypeArgument::packExpansion)) {
                report("CPP005", query.range(), "类型查询中的类型参数包尚未展开。");
                return typed(new BoolLiteralExpr(false, "false", query.range()), MiniType.BOOL);
            }
            List<MiniType> types = new ArrayList<>();
            boolean valid = true;
            for (var argument : query.arguments()) {
                MiniType type = normalizeType(argument.type(), namespace, local, argument.range());
                types.add(type);
                valid &= typeQueryPrecondition(type, argument.range());
            }
            TypeEntity savedClass = currentClass;
            Entity savedThis = currentThis;
            currentClass = null;
            currentThis = null;
            unevaluatedDepth++;
            boolean result = false;
            try {
                if (valid) result = switch (query.kind()) {
                    case CONSTRUCTIBLE -> typeQueryConstructible(types.getFirst(), types.subList(1, types.size()).stream()
                            .map(this::typeQueryArgument).toList(), false, query.range());
                    case ASSIGNABLE -> typeQueryAssignable(typeQueryArgument(types.get(0)), typeQueryArgument(types.get(1)), query.range());
                    case CONVERTIBLE -> types.get(0).isVoid() || types.get(1).isVoid()
                            ? types.get(0).isVoid() && types.get(1).isVoid()
                            : typeQueryConversion(typeQueryArgument(types.get(0)), types.get(1), ConversionContext.IMPLICIT, query.range());
                };
            } finally {
                unevaluatedDepth--;
                currentClass = savedClass;
                currentThis = savedThis;
            }
            return typed(new BoolLiteralExpr(result, Boolean.toString(result), query.range()), MiniType.BOOL);
        }

        private boolean typeQueryPrecondition(MiniType type, SourceRange range) {
            // References and pointers are complete types even when their referred-to class is not.
            if (type.isReference() || type.isPointer() || type.isFunction() || type.isVoid()) return true;
            if (type.isArray()) return typeQueryPrecondition(type.elementType(), range);
            TypeEntity owner = objectType(type);
            if (owner != null) {
                completeTemplate(owner, range);
                if (!owner.complete) {
                    report("CPP004", range, "类型查询要求完整对象类型；不能查询尚未定义的 " + owner.canonicalName);
                    return false;
                }
            }
            if (type.containsTemplateType() || type.containsPlaceholder()) {
                report("CPP005", range, "类型查询的类型实参尚未完成替换。");
                return false;
            }
            return true;
        }

        private CppOverloadResolver.Argument typeQueryArgument(MiniType type) {
            MiniType object = objectTypeOfReference(type);
            // A call returning an rvalue reference to function still yields an lvalue.
            return new CppOverloadResolver.Argument(object,
                    object.isFunction() || type.isReference() && !type.isRvalueReference()
                            ? CppValueCategory.LVALUE : CppValueCategory.XVALUE, false);
        }

        private boolean typeQueryConstructible(MiniType target, List<CppOverloadResolver.Argument> arguments,
                                                boolean copyInitialization, SourceRange range) {
            if (target.isVoid() || target.isFunction() || arguments.stream().anyMatch(argument -> argument.type().isVoid())) return false;
            if (target.isReference()) return arguments.size() == 1
                    && typeQueryConversion(arguments.getFirst(), target,
                    copyInitialization ? ConversionContext.IMPLICIT : ConversionContext.EXPLICIT, range);
            if (target.isArray()) return arguments.isEmpty()
                    && typeQueryConstructible(inheritObjectQualifiers(target, target.elementType()), List.of(), false, range);
            TypeEntity owner = objectType(target);
            if (owner == null) {
                if (arguments.isEmpty()) return true; // () value-initializes a scalar, including const scalars.
                if (arguments.size() != 1) return false;
                var argument = arguments.getFirst();
                // Direct initialization has this special rule; implicit nullptr -> bool does not.
                if (!copyInitialization && target.unqualified().equals(MiniType.BOOL) && argument.type().isNullPointer()) return true;
                return typeQueryConversion(argument, target,
                        copyInitialization ? ConversionContext.IMPLICIT : ConversionContext.EXPLICIT, range);
            }
            completeTemplate(owner, range);
            if (!owner.complete || !typeQueryDestructible(target, range)) return false;
            if (arguments.isEmpty() && owner.constructors.isEmpty()) return typeQueryImplicitDefault(owner, range);
            List<CppOverloadResolver.Candidate<Constructor>> candidates = expandConstructorTemplateShapes(allConstructors(owner), arguments, range).stream()
                    .filter(constructor -> !copyInitialization || !constructor.source.explicitSpecifier())
                    .map(this::constructorCandidate).toList();
            var resolution = CppOverloadResolver.resolve(candidates, arguments, null, conversions, this::betterTemplateCandidate);
            if (resolution.status() != CppOverloadResolver.Status.SELECTED) return false;
            Constructor selected = resolution.winner().identity();
            return selected.access == Access.PUBLIC && !isDeleted(selected.function)
                    && typeQueryParameters(selected.parameterTypes, arguments, range);
        }

        /** Trivial classes have no runtime default-constructor entity; check the same member obligations. */
        private boolean typeQueryImplicitDefault(TypeEntity owner, SourceRange range) {
            if (lambdaTypes.containsKey(owner)) return false;
            if (owner.union && !owner.fields.isEmpty() && owner.fields.stream().allMatch(field -> typeQueryLeaf(field.type()).isConstQualified())) return false;
            for (StructField field : owner.fields) {
                if (owner.defaultInitializers.containsKey(field.name())) continue;
                MiniType type = typeQueryLeaf(field.type());
                if (type.isReference()) return false;
                TypeEntity member = objectType(type);
                if (member == null) {
                    if (!owner.union && type.isConstQualified()) return false;
                } else {
                    // Variant members differ from ordinary const subobjects (N4659 class.ctor/5).
                    if (owner.union && owner.defaultInitializers.isEmpty() && needsConstruction(member)) return false;
                    if (!typeQueryConstructible(type, List.of(), false, range)) return false;
                    if (!owner.union && type.isConstQualified()) {
                        var candidates = expandConstructorTemplateShapes(allConstructors(member), List.of(), range).stream()
                                .map(this::constructorCandidate).toList();
                        var selected = CppOverloadResolver.resolve(candidates, List.of(), null, conversions, this::betterTemplateCandidate);
                        if (selected.status() != CppOverloadResolver.Status.SELECTED || selected.winner().identity().implicit
                                || defaulted(selected.winner().identity()) && !userProvidedDefaulted.contains(selected.winner().identity().function)) return false;
                    }
                }
            }
            return true;
        }

        private MiniType typeQueryLeaf(MiniType type) {
            while (type.isArray()) type = inheritObjectQualifiers(type, type.elementType());
            return type;
        }

        private boolean typeQueryAssignable(CppOverloadResolver.Argument target, CppOverloadResolver.Argument source, SourceRange range) {
            if (target.type().isVoid() || source.type().isVoid() || target.type().isArray() || target.type().isFunction()) return false;
            TypeEntity owner = objectType(target.type());
            if (owner == null) return target.category() == CppValueCategory.LVALUE && !target.type().isConstQualified()
                    && typeQueryConversion(source, target.type().unqualified(), ConversionContext.IMPLICIT, range);
            completeTemplate(owner, range);
            if (!owner.complete) return false;
            MethodSet members = memberMethods(owner.type, "operator=");
            List<CppOverloadResolver.Candidate<Method>> candidates = new ArrayList<>();
            if (members != null) for (Method declaration : members.methods) {
                Entity concrete = deduceFunctionTemplateShapes(declaration.function, List.of(source), null, range);
                if (concrete == null) continue;
                Method method = concrete == declaration.function ? declaration : functionTemplateInstances.get(concrete).method;
                candidates.add(new CppOverloadResolver.Candidate<>(method, method.parameterTypes, method.source.method().variadic(),
                        methodThisType(owner, method.source).pointee(), false, requiredParameters(method.function, method.parameterTypes.size())));
            }
            var resolution = CppOverloadResolver.resolveOperators(candidates, List.of(target, source), conversions, this::betterTemplateCandidate);
            if (resolution.status() != CppOverloadResolver.Status.SELECTED) return false;
            Method selected = resolution.winner().identity();
            return selected.access == Access.PUBLIC && !isDeleted(selected.function)
                    && typeQueryParameters(selected.parameterTypes, List.of(source), range)
                    && (selected.returnType.isVoid() || selected.returnType.isReference() || typeQueryDestructible(selected.returnType, range));
        }

        /** Copy-initialization uses standard conversions or one selected user conversion, never a C-style cast. */
        private boolean typeQueryConversion(CppOverloadResolver.Argument source, MiniType target, ConversionContext mode, SourceRange range) {
            if (source.type().isVoid() || target.isVoid() || !target.isReference() && (target.isArray() || target.isFunction())) return false;
            TypeEntity sourceClass = objectType(source.type());
            TypeEntity targetClass = objectType(objectTypeOfReference(target));
            if (sourceClass != null) completeTemplate(sourceClass, range);
            if (targetClass != null) completeTemplate(targetClass, range);
            if (!target.isReference() && targetClass != null && source.type().unqualified().equals(target.unqualified())) {
                return typeQueryConstructible(target, List.of(source), true, range);
            }
            if (standardViable(source, target)) return true;
            // Reference-related cv/category failures cannot be rescued by inventing a copied scalar.
            if (target.isReference() && sourceClass == null && targetClass == null) return false;
            UserSelection selection = selectUserConversion(target, source, mode);
            if (selection.selected == null) return false;
            UserChoice choice = selection.selected;
            if (choice.method != null) {
                Method method = choice.method;
                if (method.access != Access.PUBLIC || isDeleted(method.function)) return false;
                MiniType returned = methodReturnType(method);
                if (!returned.isReference() && !typeQueryDestructible(returned, range)) return false;
                // A returned reference still needs the target copy/move operation. A same-type
                // prvalue initializes the result directly under C++17 guaranteed elision.
                if (!target.isReference() && targetClass != null && choice.output.category() != CppValueCategory.PRVALUE
                        && !typeQueryConstructible(target, List.of(choice.output), true, range)) return false;
            } else {
                Constructor constructor = choice.constructor;
                if (constructor.access != Access.PUBLIC || isDeleted(constructor.function)
                        || !typeQueryParameters(constructor.parameterTypes, List.of(source), range)
                        || !typeQueryDestructible(constructor.owner.type, range)) return false;
            }
            return !target.isReference() || choice.output.category() != CppValueCategory.PRVALUE
                    || typeQueryDestructible(objectTypeOfReference(target), range);
        }

        private boolean typeQueryParameters(List<MiniType> parameters, List<CppOverloadResolver.Argument> arguments, SourceRange range) {
            for (int index = 0; index < Math.min(parameters.size(), arguments.size()); index++) {
                if (!typeQueryConversion(arguments.get(index), parameters.get(index), ConversionContext.IMPLICIT, range)) return false;
            }
            return true;
        }

        private boolean typeQueryDestructible(MiniType type, SourceRange range) {
            if (type.isReference()) return true;
            if (type.isVoid() || type.isFunction()) return false;
            if (type.isArray()) return typeQueryDestructible(type.elementType(), range);
            TypeEntity owner = objectType(type);
            if (owner == null) return true;
            completeTemplate(owner, range);
            return owner.complete && (owner.destructor == null
                    || owner.destructor.access == Access.PUBLIC && !isDeleted(owner.destructor.function));
        }

        private Expression constructionExpression(CppConstructionExpr source, Namespace namespace, Local local) {
            MiniType type = normalizeType(source.type(), namespace, local, source.typeRange());
            requireComplete(type, source.typeRange());
            // Potential destruction is checked even in an unevaluated operand or elided result.
            if(source!=decltypeOperand) destructorForUse(type, source.range());
            CppInitializer syntax = source.initializer();
            if (type.isArray()) return arrayInitialization(type, syntax, namespace, local, source.range());
            List<Expression> arguments = syntax.arguments();
            if (type.isVoid() && syntax.kind() == CppInitializer.Kind.DIRECT_LIST) {
                report("CPP004", source.range(), "Braced construction requires an object type; void has no object.");
                return new CastExpr(MiniType.VOID, new IntegerLiteralExpr(0, "0", source.range()), source.range());
            }
            if (type.isReference() && arguments.size() == 1) {
                Expression value = expression(arguments.getFirst(), namespace, local, true);
                return explicitConversion(type, value, source.range());
            }
            if (type.isReference() || type.isArray() || type.isFunction()) {
                report("CPP005", source.typeRange(), "Functional construction requires a single argument for a reference and an object result otherwise.");
                return new NullLiteralExpr("nullptr", source.range());
            }
            TypeEntity owner = objectType(type);
            if (owner != null && !owner.complete) return new NullLiteralExpr("nullptr", source.range());
            if (owner != null && needsConstruction(owner)) {
                Expression value = constructObject(type, syntax, namespace, local, source.range());
                return ObjectInitExpr.occursInResultOf(value) ? value : recordPrvalue(type, value, source.range());
            }
            if (owner != null) {
                if (arguments.size() == 1 && !(arguments.getFirst() instanceof CppInitializer)) {
                    Expression value = expression(arguments.getFirst(), namespace, local);
                    MiniType actual = declaredExpressionType(value);
                    if (actual != null && type.unqualified().equals(actual.unqualified())) {
                        Expression copied = copyInitialize(type, value, source.range(), syntax.kind());
                        return ObjectInitExpr.occursInResultOf(copied) ? copied : recordPrvalue(type, copied, source.range());
                    }
                    if (syntax.kind() == CppInitializer.Kind.DIRECT_PAREN) {
                        return directClassConversion(type, value, source.range());
                    }
                }
                String destination = freshName("construction");
                Expression slot = typed(new UnaryExpr(TokenType.STAR,
                        typed(new NameExpr(destination, source.range()), type.unqualified().pointerTo()), source.range()), type.unqualified());
                List<Expression> actions = new ArrayList<>();
                if (arguments.isEmpty()) {
                    actions.add(new InitializeExpr(slot, new AggregateInitExpr(List.of(), syntax.range()), syntax.range()));
                } else {
                    if (syntax.kind() != CppInitializer.Kind.DIRECT_LIST) {
                        report("CPP004", source.range(), "A C++17 aggregate has no matching parenthesized constructor.");
                    }
                    if (nonAggregate(owner) || owner.union) {
                        report("CPP005", source.range(), "This record requires constructor or union initialization rules not supported yet.");
                    }
                    if (arguments.size() > owner.fields.size()) report("CPP004", syntax.range(), "Too many aggregate initializer elements.");
                    for (int index = 0; index < owner.fields.size(); index++) {
                        StructField field = owner.fields.get(index);
                        if (field.type().isArray() || field.type().isStruct() || field.type().isReference()) {
                            report("CPP005", source.range(), "Nested aggregate, array and reference element lists require member-wise initialization support.");
                            continue;
                        }
                        Expression value = index < arguments.size() ? arguments.get(index) : null;
                        CppInitializer fieldSyntax = value instanceof CppInitializer nested ? nested
                                : new CppInitializer(CppInitializer.Kind.DIRECT_LIST, value == null ? List.of() : List.of(value),
                                        value == null ? syntax.range() : value.range());
                        Expression initialized = variableInitializer(field.type(), fieldSyntax, null, namespace, local, fieldSyntax.range());
                        if (initialized != null) actions.add(new InitializeExpr(
                                typed(new FieldAccessExpr(slot, field.name(), false, fieldSyntax.range()), field.type()), initialized, fieldSyntax.range()));
                    }
                }
                Expression body = actions.isEmpty() ? new CastExpr(MiniType.VOID, new IntegerLiteralExpr(0, "0", source.range()), source.range())
                        : actions.size() == 1 ? actions.getFirst() : new CommaExpr(actions, source.range());
                return typed(new ObjectInitExpr(coreType(type), destination, body, source.range()), type);
            }
            if (syntax.kind() == CppInitializer.Kind.DIRECT_LIST) {
                return variableInitializer(type, syntax, null, namespace, local, source.range());
            }
            if (arguments.size() > 1) report("CPP004", syntax.range(), "A scalar functional conversion requires at most one argument.");
            Expression value = arguments.isEmpty() ? new IntegerLiteralExpr(0, "0", source.range())
                    : expression(arguments.getFirst(), namespace, local);
            return explicitConversion(type, value, source.range());
        }

        private enum ConversionContext { IMPLICIT, EXPLICIT, BOOLEAN }
        private record UserChoice(Method method, Constructor constructor, CppOverloadResolver.Argument output,
                                  CppOverloadResolver.Argument input, MiniType inputTarget) {
            Object identity() { return method != null ? method.function : constructor.function; }
        }
        private record UserSelection(List<UserChoice> viable, UserChoice selected) {
            boolean ambiguous() { return selected == null && !viable.isEmpty(); }
        }
        private List<Method> conversionMethods(MiniType type) {
            TypeEntity owner = objectType(type);
            if (owner == null) return List.of();
            return owner.methods.values().stream().flatMap(set -> set.methods.stream())
                    .filter(method -> method.source.method().conversionName() != null).toList();
        }
        private UserSelection selectUserConversion(MiniType target, CppOverloadResolver.Argument source, ConversionContext mode) {
            prepareGenericLambdaConversion(source.type(),target);
            List<UserChoice> choices = new ArrayList<>();
            for (Method method : conversionMethods(source.type())) {
                boolean explicit = method.source.method().conversionName().explicitSpecifier();
                if (explicit && mode == ConversionContext.IMPLICIT) continue;
                // Explicit conversion functions may not acquire an unrelated target via
                // a second arithmetic/pointer conversion (N4659 over.match.conv).
                if (explicit && !CppOverloadResolver.qualificationOnly(methodReturnType(method), target)) continue;
                MiniType receiver = methodThisType(method.owner, method.source).pointee();
                if (!receiver.qualifiers().containsAll(source.type().qualifiers())) continue;
                MiniType result = objectTypeOfReference(methodReturnType(method));
                var output = new CppOverloadResolver.Argument(result, methodReturnType(method).isReference()
                        ? methodReturnType(method).isRvalueReference()?CppValueCategory.XVALUE:CppValueCategory.LVALUE : CppValueCategory.PRVALUE, false);
                if (!standardViable(output, target)) continue;
                var input = new CppOverloadResolver.Argument(source.type(), CppValueCategory.LVALUE, false);
                choices.add(new UserChoice(method, null, output, input, receiver.referenceTo()));
            }
            MiniType objectTarget = objectTypeOfReference(target).unqualified();
            TypeEntity owner = objectType(objectTarget);
            if (owner != null && !objectTarget.equals(source.type().unqualified()) && mode != ConversionContext.BOOLEAN) {
                var output = new CppOverloadResolver.Argument(objectTarget, CppValueCategory.PRVALUE, false);
                if (standardViable(output, target)) for (Constructor declaration : owner.constructors) {
                    Entity concrete=deduceFunctionTemplateShapes(declaration.function,List.of(source),null,definitionRange(declaration));
                    if(concrete==null)continue;
                    Constructor constructor=concrete==declaration.function?declaration:functionTemplateInstances.get(concrete).constructor;
                    if (constructor.parameterTypes.isEmpty() || requiredParameters(constructor.function,constructor.parameterTypes.size())>1
                            || constructor.source.explicitSpecifier() && mode == ConversionContext.IMPLICIT) continue;
                    MiniType parameter = constructor.parameterTypes.getFirst();
                    if (standardViable(source, parameter))
                        choices.add(new UserChoice(null, constructor, output, source, parameter));
                }
            }
            UserChoice winner = null;
            for (UserChoice candidate : choices) {
                boolean best = true;
                for (UserChoice other : choices) if (candidate != other) {
                    int initial = CppOverloadResolver.compareStandard(candidate.input, candidate.inputTarget, other.input, other.inputTarget);
                    int trailing = CppOverloadResolver.compareStandard(candidate.output, target, other.output, target);
                    if (!(initial < 0 || initial == 0 && (trailing < 0 || trailing == 0 && betterTemplateCandidate(candidate.identity(),other.identity())))) { best = false; break; }
                }
                if (best) { winner = candidate; break; }
            }
            return new UserSelection(List.copyOf(choices), winner);
        }
        private CppOverloadResolver.UserConversion implicitUserConversion(Object candidate, CppOverloadResolver.Argument source, MiniType target) {
            ConversionContext mode = candidate instanceof OperatorCandidate operator && contextualOperator(operator.builtin)
                    ? ConversionContext.BOOLEAN : ConversionContext.IMPLICIT;
            UserSelection selection = selectUserConversion(target, source, mode);
            if (selection.viable.isEmpty()) return null;
            if (selection.ambiguous()) return new CppOverloadResolver.UserConversion(selection,
                    new CppOverloadResolver.Argument(objectTypeOfReference(target), target.isReference()
                            ? target.isRvalueReference()?CppValueCategory.XVALUE:CppValueCategory.LVALUE : CppValueCategory.PRVALUE, false), true);
            return new CppOverloadResolver.UserConversion(selection.selected.identity(), selection.selected.output, false);
        }
        /** Emits the chosen user step; the caller applies its validated trailing standard conversion. */
        private Expression userConversion(MiniType target, Expression value, ConversionContext mode, SourceRange range) {
            MiniType actual = declaredExpressionType(value);
            if (actual == null) return null;
            var argument = new CppOverloadResolver.Argument(actual, valueCategory(value), isNullIntegerLiteral(value));
            UserSelection selection = selectUserConversion(target, argument, mode);
            if (selection.viable.isEmpty()) return null;
            if (selection.ambiguous()) { report("CPP004", range, "用户定义转换具有二义性。"); return value; }
            return emitUserConversion(selection.selected, value, range);
        }

        private Expression emitUserConversion(UserChoice selected, Expression value, SourceRange range) {
            if (selected.method != null) {
                Method method = selected.method;
                instantiateMethod(method);
                requireMethodAccess(method, range);
                Expression receiver = materializedReceiver(value);
                if (!addressableObject(receiver)) {
                    report("CPP005", range, "此转换函数接收者尚无可用的对象存储。"); return value;
                }
                destructorForUse(methodReturnType(method), range);
                Expression call = typed(new CallExpr(new NameExpr(method.function.coreName, range), List.of(address(receiver)), range),
                        coreType(methodReturnType(method)));
                if (methodReturnType(method).isReference()) return referenceResult(methodReturnType(method),call,range);
                return methodReturnType(method).isStruct() ? recordPrvalue(methodReturnType(method), call, range) : call;
            }
            Constructor constructor = selected.constructor;
            instantiateConstructor(constructor);
            if (constructor == constructor.owner.implicitCopy) emitImplicitCopy(constructor.owner);
            if (constructor.access != Access.PUBLIC && !classAccess(constructor.owner))
                report("CPP004", range, "转换构造函数不可访问。");
            if (isDeleted(constructor.function)) {
                report("CPP004", range, "转换构造函数已删除或不可用。"); return value;
            }
            MiniType parameter = constructor.parameterTypes.getFirst();
            Expression lowered = parameter.isReference() ? bindReferenceValue(parameter, value, value, range)
                    : convertCallValue(parameter, value, value);
            destructorForUse(constructor.owner.type, range);
            String destination = freshName("conversion");
            var arguments=new ArrayList<Expression>();arguments.add(typed(new NameExpr(destination,range),constructor.owner.type.pointerTo()));arguments.add(lowered);
            arguments.addAll(defaultArguments(constructor.function,constructor.parameterTypes,1));
            Expression call = typed(new CallExpr(new NameExpr(constructor.function.coreName, range), arguments, range), MiniType.VOID);
            return typed(new ObjectInitExpr(constructor.owner.type, destination, call, range), constructor.owner.type);
        }
        private Expression contextualBool(Expression value) {
            if (value == null) return null;
            MiniType type = declaredExpressionType(value);
            if (objectType(type) == null) return value;
            Expression converted = userConversion(MiniType.BOOL, value, ConversionContext.BOOLEAN, value.range());
            if (converted == null) { report("CPP004", value.range(), "条件没有可行的布尔转换。"); return value; }
            return typed(new CastExpr(MiniType.BOOL, converted, value.range()), MiniType.BOOL);
        }

        private SourceRange definitionRange(Constructor constructor){return constructor.source.range();}
        private List<Constructor> allConstructors(TypeEntity owner) {
            List<Constructor> result = new ArrayList<>(owner.constructors);
            for (Constructor copy : copyConstructors(owner)) if (!result.contains(copy)) result.add(copy);
            ensureImplicitMove(owner);
            if(owner.implicitMove!=null&&!deletedConstructors.containsKey(owner.implicitMove.function)&&!result.contains(owner.implicitMove))result.add(owner.implicitMove);
            result.removeIf(c->defaulted(c)&&isMoveConstructor(c)&&isDeleted(c.function));
            return result;
        }

        private Expression directClassConversion(MiniType target, Expression value, SourceRange range) {
            TypeEntity owner = objectType(target);
            if (owner == null) return value;
            List<CppOverloadResolver.Candidate<Constructor>> candidates = expandConstructorTemplates(allConstructors(owner),List.of(value),range).stream()
                    .map(constructor -> new CppOverloadResolver.Candidate<>(constructor, constructor.parameterTypes, constructor.source.variadic(),null,false,requiredParameters(constructor.function,constructor.parameterTypes.size()))).toList();
            var resolution = CppOverloadResolver.resolve(candidates,
                    List.of(new CppOverloadResolver.Argument(declaredExpressionType(value), valueCategory(value), isNullIntegerLiteral(value))),
                    null, conversions, this::betterTemplateCandidate);
            if (resolution.status() != CppOverloadResolver.Status.SELECTED) {
                report("CPP004", range, "直接初始化没有唯一的可行构造函数。"); return value;
            }
            Constructor selected = resolution.winner().identity();
            var source = new CppOverloadResolver.Argument(declaredExpressionType(value), valueCategory(value), isNullIntegerLiteral(value));
            var output = new CppOverloadResolver.Argument(owner.type, CppValueCategory.PRVALUE, false);
            return emitUserConversion(new UserChoice(null, selected, output, source, selected.parameterTypes.getFirst()), value, range);
        }

        private Expression explicitConversion(MiniType target, Expression value, SourceRange range) {
            if (target.isVoid()) return typed(new CastExpr(MiniType.VOID, value, range), MiniType.VOID);
            MiniType actual = declaredExpressionType(value);
            if (actual == null) return value;
            if (target.isReference()) {
                if (!standardViable(new CppOverloadResolver.Argument(actual, valueCategory(value), false), target)) {
                    Expression converted = userConversion(target, value, ConversionContext.EXPLICIT, range);
                    if (converted != null) { value = converted; actual = declaredExpressionType(value); }
                }
                // Cast notation also admits const_cast/reinterpret_cast for addressable objects.
                if ((valueCategory(value) == CppValueCategory.LVALUE || target.isRvalueReference()&&valueCategory(value)==CppValueCategory.XVALUE) && addressableObject(value)
                        && !actual.isVoid() && !target.referent().isVoid()) {
                    Expression pointer = typed(new CastExpr(coreType(target.referent()).pointerTo(), address(value), range),
                            target.referent().pointerTo());
                    return referenceResult(target,pointer,range);
                }
                Expression pointer = bindReferenceValue(target, value, value, range);
                return referenceResult(target,pointer,range);
            }
            if (target.isStruct()) {
                if (actual.unqualified().equals(target.unqualified()))
                    return copyInitialize(target, value, range, CppInitializer.Kind.DIRECT_PAREN);
                return directClassConversion(target, value, range);
            }
            if (objectType(actual) != null) {
                Expression converted = userConversion(target, value, ConversionContext.EXPLICIT, range);
                if (converted == null) { report("CPP004", range, "没有可行的显式用户定义转换。"); return value; }
                value = converted;
                if (target.isStruct()) return convertCallValue(target, value, value);
            }
            requireExplicitConversion(target, value, range);
            return typed(new CastExpr(coreType(target.unqualified()), value, range), target.unqualified());
        }

        private Expression contextualIntegral(Expression value) {
            if (objectType(declaredExpressionType(value)) == null) return value;
            Set<MiniType> targets = new LinkedHashSet<>();
            for (Method method : conversionMethods(declaredExpressionType(value)))
                if (!method.source.method().conversionName().explicitSpecifier()
                        && objectTypeOfReference(methodReturnType(method)).isIntegerScalar())
                    targets.add(objectTypeOfReference(methodReturnType(method)).unqualified());
            if (targets.size() != 1) { report("CPP004", value.range(), "switch 要求唯一的整型转换目标。"); return value; }
            Expression result = userConversion(targets.iterator().next(), value, ConversionContext.IMPLICIT, value.range());
            if (result == null) { report("CPP004", value.range(), "switch 没有可行的整型转换。"); return value; }
            return result;
        }

        private UserSelection conditionalSelection(Expression from, Expression to) {
            MiniType type = declaredExpressionType(to), actual = declaredExpressionType(from);
            if (type == null || actual == null) return new UserSelection(List.of(), null);
            var source = new CppOverloadResolver.Argument(actual, valueCategory(from), isNullIntegerLiteral(from));
            if (valueCategory(to) == CppValueCategory.LVALUE) {
                UserSelection reference = selectUserConversion(type.referenceTo(), source, ConversionContext.IMPLICIT);
                if (reference.ambiguous() || reference.selected != null && reference.selected.output.category() == CppValueCategory.LVALUE)
                    return reference;
            }
            return selectUserConversion(TypeCompatibility.decay(type).unqualified(), source, ConversionContext.IMPLICIT);
        }

        private Expression conditionalConversion(UserChoice choice, Expression source, Expression other, SourceRange range) {
            Expression value = emitUserConversion(choice, source, range);
            MiniType target = declaredExpressionType(other);
            if (valueCategory(other) == CppValueCategory.LVALUE && valueCategory(value) == CppValueCategory.LVALUE
                    && referenceCompatible(target, declaredExpressionType(value))) return value;
            target = TypeCompatibility.decay(target).unqualified();
            value = convertCallValue(target, value, source);
            return target.isStruct() ? value : typed(new CastExpr(coreType(target), value, range), target);
        }

        private List<Expression> conditionalConversions(Expression first, Expression second, SourceRange range) {
            MiniType a = declaredExpressionType(first), b = declaredExpressionType(second);
            if (a == null || b == null || objectType(a) == null && objectType(b) == null
                    || a.unqualified().equals(b.unqualified())) return List.of(first, second);
            UserSelection toSecond = conditionalSelection(first, second), toFirst = conditionalSelection(second, first);
            if (toSecond.ambiguous() || toFirst.ambiguous() || toSecond.selected != null && toFirst.selected != null) {
                report("CPP004", range, "条件运算符两分支的用户转换具有二义性。"); return List.of(first, second);
            }
            if (toSecond.selected != null) return List.of(conditionalConversion(toSecond.selected, first, second, range), second);
            if (toFirst.selected != null) return List.of(first, conditionalConversion(toFirst.selected, second, first, range));
            // If neither class can become the other, choose the builtin common arithmetic/pointer operands.
            List<CppOverloadResolver.Candidate<OperatorCandidate>> candidates = new ArrayList<>();
            addBuiltinOperators("operator?:", null, List.of(first, second), candidates);
            var resolution = CppOverloadResolver.resolveOperators(candidates,
                    List.of(new CppOverloadResolver.Argument(a, valueCategory(first), isNullIntegerLiteral(first)),
                            new CppOverloadResolver.Argument(b, valueCategory(second), isNullIntegerLiteral(second))), conversions);
            if (resolution.status() != CppOverloadResolver.Status.SELECTED) {
                report("CPP004", range, "条件运算符没有唯一的共同类型。"); return List.of(first, second);
            }
            List<MiniType> types = resolution.winner().identity().parameters;
            return List.of(convertCallValue(types.get(0), first, first), convertCallValue(types.get(1), second, second));
        }

        private void requireExplicitConversion(MiniType target, Expression value, SourceRange range) {
            MiniType source = declaredExpressionType(value);
            if (source == null) return;
            switch (CppExplicitConversion.check(source, target)) {
                case INVALID -> report("CPP004", range, "This explicit conversion is not permitted by the C++17 scalar and pointer rules.");
                case OUTSIDE_SUBSET -> report("CPP005", range, "This explicit conversion requires class or reference conversion rules not supported yet.");
                case ALLOWED -> { }
            }
        }

        /** A differently typed assignment overload does not suppress the implicit copy assignment. */
        private boolean isCopyAssignment(Method method) {
            if(functionTemplates.containsKey(method.function)||method.parameterTypes.size()==1&&method.parameterTypes.getFirst().isRvalueReference())return false;
            if (!method.source.method().name().equals("operator=") || method.parameterTypes.size() != 1) return false;
            MiniType parameter = method.parameterTypes.getFirst();
            return objectTypeOfReference(parameter).unqualified().equals(method.owner.type);
        }

        private void ensureImplicitAssignment(TypeEntity owner) {
            if (owner.assignmentPlanned || !owner.complete) return;
            owner.assignmentPlanned = true;
            MethodSet declared = owner.methods.get("operator=");
            Method explicitAssignment=declared==null?null:declared.methods.stream().filter(m->isCopyAssignment(m)&&defaulted(m)).findFirst().orElse(null);
            if (explicitAssignment==null&&declared != null && declared.methods.stream().anyMatch(this::isCopyAssignment)) return;
            boolean constant = true;
            for (StructField field : owner.fields) {
                MiniType leaf = field.type();
                while (leaf.isArray()) leaf = MiniType.qualified(leaf.elementType(), leaf.qualifiers());
                TypeEntity member = objectType(leaf);
                if (member == null) continue;
                ensureImplicitAssignment(member);
                MethodSet methods = member.methods.get("operator=");
                constant &= methods != null && methods.methods.stream().filter(this::isCopyAssignment).anyMatch(method -> {
                    MiniType parameter = method.parameterTypes.getFirst();
                    return !parameter.isReference() || parameter.referent().isConstQualified();
                });
            }
            if(explicitAssignment!=null)constant=explicitAssignment.parameterTypes.getFirst().referent().isConstQualified();
            MiniType sourceOwner = constant ? MiniType.qualified(owner.type, Set.of(MiniType.TypeQualifier.CONST)) : owner.type;
            owner.assignmentPlan=planCopyAssignment(owner,sourceOwner,explicitAssignment==null);
            if(declared!=null)for(Method method:declared.methods)if(isCopyAssignment(method)&&defaulted(method))
                defaultedAssignmentPlans.put(method,planCopyAssignment(owner,method.parameterTypes.getFirst().referent(),false));
            SourceRange range = owner.sourceRecord.range();
            FunctionDecl source = new FunctionDecl("operator=", owner.type.referenceTo(),
                    List.of(new Parameter("other", sourceOwner.referenceTo(), range)), false, null, false, range);
            MethodMember member = new MethodMember(source, false, range);
            Entity function = new Entity("operator=", freshName(owner.canonicalName.substring(2) + "::operator="), Kind.FUNCTION,
                    owner.owner, MiniType.function(owner.type.referenceTo(), List.of(owner.type.pointerTo(), sourceOwner.referenceTo())), null, true);
            coreValues.put(function.coreName, function);
            owner.implicitAssignment = explicitAssignment!=null?explicitAssignment:new Method(owner, member, Access.PUBLIC, function, owner.type.referenceTo(), List.of(sourceOwner.referenceTo()));
            List<Method> methods = new ArrayList<>(declared == null ? List.of() : declared.methods);
            if(!methods.contains(owner.implicitAssignment))methods.add(owner.implicitAssignment); owner.methods.put("operator=", new MethodSet(methods));
        }

        private AssignmentPlan planCopyAssignment(TypeEntity owner,MiniType sourceOwner,boolean deleteForMove) {
            List<AssignmentEntry> entries = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            if(deleteForMove&&userDeclaredMove(owner))problems.add("A user-declared move operation deletes implicit copy assignment");
            boolean trivial = true;
            for (StructField field : owner.fields) {
                MiniType target = field.type();
                List<Integer> dimensions = new ArrayList<>();
                while (target.isArray()) {
                    dimensions.add(((MiniType.ArrayType) target.unqualified()).length());
                    target = MiniType.qualified(target.elementType(), target.qualifiers());
                }
                if (target.isReference()) { problems.add("Reference member '" + field.name() + "' cannot be reseated"); continue; }
                MiniType source = inheritObjectQualifiers(sourceOwner, target);
                TypeEntity member = objectType(target);
                Method selected = null;
                if (member == null) {
                    if (target.isConstQualified()) problems.add("Const member '" + field.name() + "' is not assignable");
                } else {
                    MethodSet methods = member.methods.get("operator=");
                    List<CppOverloadResolver.Candidate<Method>> candidates = methods == null ? List.of()
                            : methods.methods.stream().map(method -> new CppOverloadResolver.Candidate<>(method,
                            method.parameterTypes, false, methodThisType(member, method.source).pointee())).toList();
                    var resolution = CppOverloadResolver.resolveOperators(candidates, List.of(
                            new CppOverloadResolver.Argument(target, CppValueCategory.LVALUE, false),
                            new CppOverloadResolver.Argument(source, CppValueCategory.LVALUE, false)));
                    if (resolution.status() != CppOverloadResolver.Status.SELECTED) {
                        problems.add("No unique member assignment for '" + field.name() + "'"); continue;
                    }
                    selected = resolution.winner().identity();
                    if (selected.access != Access.PUBLIC && selected.owner != owner)
                        problems.add("Assignment of member '" + field.name() + "' is inaccessible");
                    boolean implicit = selected == member.implicitAssignment;
                    if (isDeleted(selected.function)) problems.add("Assignment of member '" + field.name() + "' is deleted");
                    AssignmentPlan selectedPlan=defaultedAssignmentPlans.get(selected);if(selectedPlan==null&&implicit)selectedPlan=member.assignmentPlan;
                    boolean memberTrivial = selectedPlan!=null&&selectedPlan.trivial&&!userProvidedDefaulted.contains(selected.function);
                    if (owner.union && !memberTrivial) problems.add("Union member '" + field.name() + "' has nontrivial assignment");
                    trivial &= memberTrivial;
                }
                entries.add(new AssignmentEntry(field, List.copyOf(dimensions), selected));
            }
            return new AssignmentPlan(sourceOwner.referenceTo(), List.copyOf(entries), List.copyOf(problems), trivial);
        }

        private void emitImplicitAssignment(TypeEntity owner) {emitImplicitAssignment(owner,false);}
        private void emitImplicitMoveAssignment(TypeEntity owner) {emitImplicitAssignment(owner,true);}
        private void emitImplicitAssignment(TypeEntity owner,boolean move) {
            emitAssignment(move?owner.implicitMoveAssignment:owner.implicitAssignment,move?owner.moveAssignmentPlan:owner.assignmentPlan);
        }
        private void emitAssignment(Method operation,AssignmentPlan plan) {
            TypeEntity owner=operation.owner;boolean move=plan.parameterType.isRvalueReference();
            if(emittedAssignments.contains(operation.function)||!plan.problems.isEmpty())return;
            SourceRange range = owner.sourceRecord.range();
            boolean anonymous = owner.fields.stream().anyMatch(StructField::anonymous);
            boolean representationCopy = owner.union || anonymous && plan.trivial
                    && !hasVolatileSubobject(owner.type, new HashSet<>());
            if (anonymous && !representationCopy) {
                report("CPP005", range, "Nontrivial assignment of anonymous aggregate storage requires subobject addressing support.");
                return;
            }
            if (unevaluatedDepth > 0 && assignmentPrototypes.contains(operation.function)) return;
            String self = freshName("this"), other = freshName("other");
            MiniType sourcePointer = coreType(plan.parameterType);
            List<Statement> statements = new ArrayList<>();
            if (unevaluatedDepth == 0) {
                emittedAssignments.add(operation.function);
                if (representationCopy) {
                    Expression to = typed(new UnaryExpr(TokenType.STAR, typed(new NameExpr(self, range), owner.type.pointerTo()), range), owner.type);
                    Expression from = typed(new UnaryExpr(TokenType.STAR, typed(new NameExpr(other, range), sourcePointer), range), plan.parameterType.referent());
                    statements.add(new ExprStmt(new AssignmentExpr(to, TokenType.EQUAL, from, range), range));
                } else for (AssignmentEntry entry : plan.entries) {
                    Expression target = typed(new FieldAccessExpr(typed(new NameExpr(self, range), owner.type.pointerTo()), entry.field.name(), true, range), entry.field.type());
                    MiniType sourceType = inheritObjectQualifiers(plan.parameterType.referent(), entry.field.type());
                    Expression source = typed(new FieldAccessExpr(typed(new NameExpr(other, range), sourcePointer), entry.field.name(), true, range), sourceType);
                    if(move)valueCategories.put(source,CppValueCategory.XVALUE);
                    TypeEntity savedClass = currentClass;
                    currentClass = owner;
                    try { statements.add(assignMemberStatement(entry, target, source, entry.field.type(), 0)); }
                    finally { currentClass = savedClass; }
                }
                statements.add(new ReturnStmt(new NameExpr(self, range), range));
            } else {assignmentPrototypes.add(operation.function);}
            FunctionDecl core = new FunctionDecl(operation.function.coreName, owner.type.pointerTo(),
                    List.of(new Parameter(self, owner.type.pointerTo(), range), new Parameter(other, sourcePointer, range)), false,
                    unevaluatedDepth > 0 ? null : new BlockStmt(statements, range), false, range);
            functions.add(core); declarations.add(core);
        }

        private Statement assignMemberStatement(AssignmentEntry entry, Expression target, Expression source, MiniType type, int dimension) {
            SourceRange range = entry.field.range();
            if (dimension < entry.dimensions.size()) {
                String index = freshName("assignment_index"); Expression i = typed(new NameExpr(index, range), MiniType.INT);
                MiniType element = MiniType.qualified(type.elementType(), type.qualifiers());
                Expression to = typed(new IndexExpr(target, i, range), element);
                Expression from = typed(new IndexExpr(source, i, range), inheritObjectQualifiers(declaredExpressionType(source), element));
                valueCategories.put(from,valueCategory(source));
                return new ForStmt(new VarDeclStmt(index, MiniType.INT, new IntegerLiteralExpr(0,"0",range),range),
                        new BinaryExpr(i,TokenType.LESS,new IntegerLiteralExpr(entry.dimensions.get(dimension),"length",range),range),
                        new PostfixUpdateExpr(i,TokenType.PLUS_PLUS,range),assignMemberStatement(entry,to,from,element,dimension+1),range);
            }
            if (entry.method == null) return new ExprStmt(new AssignmentExpr(target, TokenType.EQUAL, source, range), range);
            Method selected = entry.method;
            if (selected == selected.owner.implicitAssignment) emitImplicitAssignment(selected.owner);
            else if(selected==selected.owner.implicitMoveAssignment)emitImplicitMoveAssignment(selected.owner);
            else instantiateMethod(selected);
            MiniType parameter = selected.parameterTypes.getFirst();
            Expression argument = parameter.isReference() ? address(source)
                    : copyInitialize(parameter, source, range, CppInitializer.Kind.COPY);
            destructorForUse(parameter, range);
            destructorForUse(methodReturnType(selected), range);
            Expression call = typed(new CallExpr(new NameExpr(selected.function.coreName, range),
                    List.of(address(target), argument), List.of(1,0), range), coreType(methodReturnType(selected)));
            if (methodReturnType(selected).isStruct()) call = recordPrvalue(methodReturnType(selected), call, range);
            return new ExprStmt(fullExpression(call, false, entry.field), range);
        }

        private boolean isCopyConstructor(Constructor constructor) {
            if(functionTemplates.containsKey(constructor.function)||constructor.parameterTypes.isEmpty()
                    ||requiredParameters(constructor.function,constructor.parameterTypes.size())>1)return false;
            return CppCopyConstructorPlan.classify(constructor.owner.type,List.of(constructor.parameterTypes.getFirst()))==CppCopyConstructorPlan.Classification.COPY;
        }
        private boolean isMoveConstructor(Constructor constructor) {
            return !functionTemplates.containsKey(constructor.function)&&!constructor.parameterTypes.isEmpty()
                    &&requiredParameters(constructor.function,constructor.parameterTypes.size())<=1
                    &&CppCopyConstructorPlan.classify(constructor.owner.type,List.of(constructor.parameterTypes.getFirst()))==CppCopyConstructorPlan.Classification.MOVE;
        }
        private boolean isMoveAssignment(Method method) {
            return !functionTemplates.containsKey(method.function)&&method.source.method().name().equals("operator=")&&method.parameterTypes.size()==1
                    &&method.parameterTypes.getFirst().isRvalueReference()&&method.parameterTypes.getFirst().referent().unqualified().equals(method.owner.type);
        }
        private boolean userDeclaredMove(TypeEntity owner) {
            return owner.constructors.stream().anyMatch(this::isMoveConstructor)
                    ||owner.methods.getOrDefault("operator=",new MethodSet(List.of())).methods.stream().anyMatch(m->m!=owner.implicitMoveAssignment&&isMoveAssignment(m));
        }
        private boolean trivialTransfer(Constructor constructor) {
            TypeEntity owner=constructor.owner;
            var explicit=defaultedCopyPlans.get(constructor);
            return !userProvidedDefaulted.contains(constructor.function)&&(explicit!=null?explicit.trivial():constructor==owner.implicitCopy&&owner.copyPlan.trivial()||constructor==owner.implicitMove&&owner.movePlan.trivial());
        }

        private List<Constructor> copyConstructors(TypeEntity owner) {
            List<Constructor> declared = owner.constructors.stream().filter(this::isCopyConstructor).toList();
            ensureImplicitCopy(owner);
            if (!declared.isEmpty()) return declared;
            return owner.implicitCopy == null ? List.of() : List.of(owner.implicitCopy);
        }


        private boolean suppressImplicitMove(TypeEntity owner) {
            return owner.constructors.stream().anyMatch(c->isCopyConstructor(c)||isMoveConstructor(c))
                    ||owner.methods.getOrDefault("operator=",new MethodSet(List.of())).methods.stream()
                        .anyMatch(m->m!=owner.implicitAssignment&&m!=owner.implicitMoveAssignment&&(isCopyAssignment(m)||isMoveAssignment(m)))
                    ||owner.destructor!=null&&!owner.destructor.implicit;
        }
        private void ensureImplicitMove(TypeEntity owner) {
            if(owner.movePlanned||!owner.complete)return;
            owner.movePlanned=true;
            Constructor explicitMove=owner.constructors.stream().filter(c->isMoveConstructor(c)&&defaulted(c)).findFirst().orElse(null);
            Method explicitAssignment=owner.methods.getOrDefault("operator=",new MethodSet(List.of())).methods.stream().filter(m->isMoveAssignment(m)&&defaulted(m)).findFirst().orElse(null);
            boolean suppressed=suppressImplicitMove(owner);
            if(suppressed&&explicitMove==null){if(explicitAssignment!=null)planImplicitMoveAssignment(owner);return;}
            SourceRange range=owner.sourceRecord.range();
            owner.movePlan=CppCopyConstructorPlan.planMove(owner.type,owner.fields,owner.union,false,memberType->{
                TypeEntity member=objectType(memberType);
                Expression source=typed(new NameExpr("__move_member_source",range),memberType);
                valueCategories.put(source,CppValueCategory.XVALUE);
                var candidates=expandConstructorTemplates(allConstructors(member),List.of(source),range).stream()
                        .filter(c->!c.parameterTypes.isEmpty()&&c.parameterTypes.getFirst().isReference()&&requiredParameters(c.function,c.parameterTypes.size())<=1)
                        .map(c->new CppCopyConstructorPlan.Constructor<>(c,c.parameterTypes.getFirst(),c.access==Access.PUBLIC||c.owner==owner,
                                isDeleted(c.function),trivialTransfer(c))).toList();
                var destructor=member.destructor==null?CppCopyConstructorPlan.Destructor.AVAILABLE
                        :deletedDestructors.containsKey(member.destructor.function)?CppCopyConstructorPlan.Destructor.DELETED
                        :member.destructor.access==Access.PUBLIC||member==owner?CppCopyConstructorPlan.Destructor.AVAILABLE:CppCopyConstructorPlan.Destructor.INACCESSIBLE;
                return new CppCopyConstructorPlan.Operations<>(candidates,destructor);
            });
            MiniType parameter=owner.type.rvalueReferenceTo();
            ConstructorMember source=new ConstructorMember(owner.name,List.of(new Parameter("other",parameter,range)),false,List.of(),new BlockStmt(List.of(),range),range,range);
            Entity function=new Entity(owner.name,freshName(owner.canonicalName+"::move"),Kind.FUNCTION,owner.owner,
                    MiniType.function(MiniType.VOID,List.of(owner.type.pointerTo(),parameter)),null,owner.movePlan.status()==CppCopyConstructorPlan.Status.AVAILABLE);
            coreValues.put(function.coreName,function);
            owner.implicitMove=explicitMove!=null?explicitMove:new Constructor(owner,source,Access.PUBLIC,function,List.of(parameter),true);
            if(explicitMove!=null)function=explicitMove.function;
            if(owner.movePlan.status()==CppCopyConstructorPlan.Status.DELETED)deletedConstructors.put(function,owner.movePlan.problems().stream()
                    .map(problem->new Diagnostic("CPP004",Diagnostic.Severity.ERROR,"Implicit move of member '"+problem.field().name()+"' is unavailable: "+problem.reason(),problem.field().range())).toList());
            if(!suppressed||explicitAssignment!=null)planImplicitMoveAssignment(owner);
        }
        private void planImplicitMoveAssignment(TypeEntity owner) {
            Method explicitAssignment=owner.methods.getOrDefault("operator=",new MethodSet(List.of())).methods.stream().filter(m->isMoveAssignment(m)&&defaulted(m)).findFirst().orElse(null);
            List<AssignmentEntry> entries=new ArrayList<>();List<String> problems=new ArrayList<>();boolean trivial=true;
            for(StructField field:owner.fields) {
                MiniType target=field.type();var dimensions=new ArrayList<Integer>();
                while(target.isArray()){dimensions.add(target.arrayLength());target=MiniType.qualified(target.elementType(),target.qualifiers());}
                if(target.isReference()){problems.add("Reference member '"+field.name()+"' cannot be reseated");continue;}
                TypeEntity member=objectType(target);Method selected=null;
                if(member==null) {if(target.isConstQualified())problems.add("Const member '"+field.name()+"' is not assignable");}
                else {
                    ensureImplicitAssignment(member);ensureImplicitMove(member);
                    var methods=member.methods.get("operator=");
                    Expression source=typed(new NameExpr("__move_assignment_source",field.range()),target);valueCategories.put(source,CppValueCategory.XVALUE);
                    var overloads=methods==null?List.<Method>of():expandMethodTemplates(methods.methods,List.of(source),null,field.range());
                    var candidates=overloads.stream().map(m->new CppOverloadResolver.Candidate<>(m,m.parameterTypes,false,methodThisType(member,m.source).pointee())).toList();
                    var resolution=CppOverloadResolver.resolveOperators(candidates,List.of(new CppOverloadResolver.Argument(target,CppValueCategory.LVALUE,false),
                            new CppOverloadResolver.Argument(target,CppValueCategory.XVALUE,false)),this::implicitUserConversion,this::betterTemplateCandidate);
                    if(resolution.status()!=CppOverloadResolver.Status.SELECTED){problems.add("No unique member move assignment for '"+field.name()+"'");continue;}
                    selected=resolution.winner().identity();
                    if(selected.access!=Access.PUBLIC&&selected.owner!=owner)problems.add("Move assignment of member '"+field.name()+"' is inaccessible");
                    AssignmentPlan plan=defaultedAssignmentPlans.get(selected);if(plan==null)plan=selected==member.implicitAssignment?member.assignmentPlan:selected==member.implicitMoveAssignment?member.moveAssignmentPlan:null;
                    if(isDeleted(selected.function))problems.add("Move assignment of member '"+field.name()+"' is deleted");
                    boolean memberTrivial=plan!=null&&plan.trivial&&!userProvidedDefaulted.contains(selected.function);
                    if(owner.union&&!memberTrivial)problems.add("Union member '"+field.name()+"' has nontrivial move assignment");
                    trivial&=memberTrivial;
                }
                entries.add(new AssignmentEntry(field,List.copyOf(dimensions),selected));
            }
            owner.moveAssignmentPlan=new AssignmentPlan(owner.type.rvalueReferenceTo(),List.copyOf(entries),List.copyOf(problems),trivial);
            // A deleted implicitly declared move operation is ignored by overload resolution.
            if(!problems.isEmpty()&&explicitAssignment==null)return;
            SourceRange range=owner.sourceRecord.range();MiniType parameter=owner.type.rvalueReferenceTo();
            FunctionDecl source=new FunctionDecl("operator=",owner.type.referenceTo(),List.of(new Parameter("other",parameter,range)),false,null,false,range);
            MethodMember member=new MethodMember(source,false,range);
            Entity function=new Entity("operator=",freshName(owner.canonicalName+"::operator=(move)"),Kind.FUNCTION,owner.owner,
                    MiniType.function(owner.type.referenceTo(),List.of(owner.type.pointerTo(),parameter)),null,true);
            coreValues.put(function.coreName,function);
            owner.implicitMoveAssignment=explicitAssignment!=null?explicitAssignment:new Method(owner,member,Access.PUBLIC,function,owner.type.referenceTo(),List.of(parameter));
            var methods=new ArrayList<>(owner.methods.getOrDefault("operator=",new MethodSet(List.of())).methods);if(!methods.contains(owner.implicitMoveAssignment))methods.add(owner.implicitMoveAssignment);
            if(!problems.isEmpty())methods.remove(owner.implicitMoveAssignment);
            owner.methods.put("operator=",new MethodSet(methods));
        }

        private void ensureImplicitCopy(TypeEntity owner) {
            if (owner.copyPlan != null || !owner.complete) return;
            Constructor explicitCopy=owner.constructors.stream().filter(c->isCopyConstructor(c)&&defaulted(c)).findFirst().orElse(null);
            boolean declared = owner.constructors.stream().anyMatch(this::isCopyConstructor);
            java.util.function.Function<MiniType,CppCopyConstructorPlan.Operations<Constructor>> operations=memberType -> {
                TypeEntity member = objectType(memberType);
                List<CppCopyConstructorPlan.Constructor<Constructor>> candidates = copyConstructors(member).stream()
                        .map(c -> new CppCopyConstructorPlan.Constructor<>(c, c.parameterTypes.getFirst(),
                                c.access == Access.PUBLIC || c.owner == owner, isDeleted(c.function),
                                trivialTransfer(c))).toList();
                CppCopyConstructorPlan.Destructor destructor = member.destructor == null
                        ? CppCopyConstructorPlan.Destructor.AVAILABLE
                        : deletedDestructors.containsKey(member.destructor.function) ? CppCopyConstructorPlan.Destructor.DELETED
                        : member.destructor.access == Access.PUBLIC || member == owner ? CppCopyConstructorPlan.Destructor.AVAILABLE
                        : CppCopyConstructorPlan.Destructor.INACCESSIBLE;
                return new CppCopyConstructorPlan.Operations<>(candidates, destructor);
            };
            owner.copyPlan=explicitCopy==null?CppCopyConstructorPlan.plan(owner.type,owner.fields,owner.union,declared,operations)
                    :CppCopyConstructorPlan.planCopy(owner.type,owner.fields,owner.union,explicitCopy.parameterTypes.getFirst().referent().isConstQualified(),operations);
            for(Constructor candidate:owner.constructors)if(isCopyConstructor(candidate)&&defaulted(candidate)) {
                var plan=CppCopyConstructorPlan.planCopy(owner.type,owner.fields,owner.union,candidate.parameterTypes.getFirst().referent().isConstQualified(),operations);
                defaultedCopyPlans.put(candidate,plan);
                if(plan.status()==CppCopyConstructorPlan.Status.DELETED)deletedConstructors.put(candidate.function,plan.problems().stream().map(problem->new Diagnostic("CPP004",Diagnostic.Severity.ERROR,
                        "Defaulted copy of member '"+problem.field().name()+"' is unavailable: "+problem.reason(),problem.field().range())).toList());
            }
            if (owner.copyPlan.status() == CppCopyConstructorPlan.Status.SUPPRESSED) return;
            SourceRange range = owner.sourceRecord.range();
            MiniType parameter = owner.copyPlan.parameterType();
            ConstructorMember source = new ConstructorMember(owner.name, List.of(new Parameter("other", parameter, range)),
                    false, List.of(), new BlockStmt(List.of(), range), range, range);
            Entity function = new Entity(owner.name, freshName(owner.canonicalName.substring(2) + "::" + owner.name),
                    Kind.FUNCTION, owner.owner, MiniType.function(MiniType.VOID, List.of(owner.type.pointerTo(), parameter)), null,
                    owner.copyPlan.status() == CppCopyConstructorPlan.Status.AVAILABLE);
            coreValues.put(function.coreName, function);
            owner.implicitCopy = explicitCopy!=null?explicitCopy:new Constructor(owner, source, Access.PUBLIC, function, List.of(parameter), true);
            if(explicitCopy!=null)function=explicitCopy.function;
            if(explicitCopy==null&&userDeclaredMove(owner))deletedConstructors.put(function,List.of(new Diagnostic("CPP004",Diagnostic.Severity.ERROR,
                    "A user-declared move operation deletes the implicit copy constructor",range)));
            else if (owner.copyPlan.status() == CppCopyConstructorPlan.Status.DELETED) {
                deletedConstructors.put(function, owner.copyPlan.problems().stream().map(problem -> new Diagnostic("CPP004",
                        Diagnostic.Severity.ERROR, "Implicit copy of member '" + problem.field().name() + "' is unavailable: "
                                + problem.reason(), problem.field().range())).toList());
            }
        }

        /** A same-class prvalue constructs its result object; an existing source object must be copied. */
        private Expression copyInitialize(MiniType target, Expression value, SourceRange range, CppInitializer.Kind kind) {
            if (target == null || value == null || !target.isStruct()) return value;
            MiniType actual = declaredExpressionType(value);
            if (actual == null || !actual.unqualified().equals(target.unqualified())) return value;
            CppValueCategory category = valueCategory(value);
            // A materialized temporary's class subobject has its own address: it is not the
            // new result object and cannot use guaranteed prvalue copy elision.
            if (category == CppValueCategory.PRVALUE && !copySourceHasStorage(value)) return value;
            TypeEntity owner = objectType(target);
            boolean implicitMove=implicitMoveSources.contains(value);
            if(implicitMove)valueCategories.put(value,CppValueCategory.XVALUE);
            List<CppOverloadResolver.Candidate<Constructor>> candidates=transferCandidates(owner,value,kind,range);
            var resolution = CppOverloadResolver.resolve(candidates,
                    List.of(new CppOverloadResolver.Argument(actual, implicitMove?CppValueCategory.XVALUE:category, false)),null,null,this::betterTemplateCandidate);
            if(implicitMove) {
                valueCategories.put(value,category);
                if(resolution.status()!=CppOverloadResolver.Status.SELECTED||!resolution.winner().identity().parameterTypes.getFirst().isRvalueReference()) {
                    candidates=transferCandidates(owner,value,kind,range);
                    resolution=CppOverloadResolver.resolve(candidates,List.of(new CppOverloadResolver.Argument(actual,category,false)),null,null,this::betterTemplateCandidate);
                }
            }
            if (resolution.status() != CppOverloadResolver.Status.SELECTED) {
                report("CPP004", range, resolution.status() == CppOverloadResolver.Status.AMBIGUOUS
                        ? "Copy constructor selection is ambiguous." : "No viable copy constructor for this source object.");
                return value;
            }
            Constructor selected = resolution.winner().identity();
            instantiateConstructor(selected);
            if (kind == CppInitializer.Kind.COPY_LIST && selected.source.explicitSpecifier())
                report("CPP004", range, "Copy-list initialization cannot select an explicit copy constructor.");
            if (selected.access != Access.PUBLIC && !classAccess(owner))
                report("CPP004", range, "Copy constructor is not accessible: " + owner.canonicalName);
            if (isDeleted(selected.function)) {
                report("CPP004", range, "Copy constructor is deleted: " + deletedConstructors.getOrDefault(selected.function,deletedReason(range)).getFirst().message());
                return value;
            }
            if (trivialTransfer(selected) && !hasVolatileSubobject(owner.type, new HashSet<>())) return value;
            if (selected == owner.implicitCopy) emitImplicitCopy(owner);
            String destination = freshName("copy_destination");
            Expression address = typed(new NameExpr(destination, range), owner.type.pointerTo());
            Expression source = copySourceAddress(value);
            var transferArguments=new ArrayList<Expression>();transferArguments.add(address);transferArguments.add(source);
            transferArguments.addAll(defaultArguments(selected.function,selected.parameterTypes,1));
            Expression call = typed(new CallExpr(new NameExpr(selected.function.coreName, range),transferArguments,range), MiniType.VOID);
            return typed(new ObjectInitExpr(coreType(target), destination, call, range), target);
        }

        private List<CppOverloadResolver.Candidate<Constructor>> transferCandidates(TypeEntity owner,Expression value,CppInitializer.Kind kind,SourceRange range) {
            return expandConstructorTemplates(allConstructors(owner),List.of(value),range).stream()
                    .filter(c->kind!=CppInitializer.Kind.COPY||!c.source.explicitSpecifier())
                    .map(c->new CppOverloadResolver.Candidate<>(c,c.parameterTypes,c.source.variadic(),null,false,requiredParameters(c.function,c.parameterTypes.size()))).toList();
        }
        private boolean copySourceHasStorage(Expression value) {
            if (value instanceof GroupingExpr group) return copySourceHasStorage(group.expression());
            if (value instanceof CommaExpr comma) return copySourceHasStorage(comma.expressions().getLast());
            if (value instanceof LetExpr let) return copySourceHasStorage(let.body());
            return addressableObject(value);
        }

        private Expression copySourceAddress(Expression value) {
            MiniType type = declaredExpressionType(value);
            if (value instanceof GroupingExpr group)
                return typed(new GroupingExpr(copySourceAddress(group.expression()), value.range()), type.pointerTo());
            if (value instanceof CommaExpr comma) {
                var values = new ArrayList<>(comma.expressions());
                values.set(values.size() - 1, copySourceAddress(values.getLast()));
                return typed(new CommaExpr(values, value.range()), type.pointerTo());
            }
            if (value instanceof LetExpr let)
                return typed(new LetExpr(let.name(), let.type(), let.initializer(), copySourceAddress(let.body()), let.range()), type.pointerTo());
            if (value instanceof AssignmentExpr assignment) return address(normalizedAssignment(assignment, true));
            return address(value);
        }

        private boolean hasVolatileSubobject(MiniType type, Set<TypeEntity> visited) {
            if (type.isReference()) return false;
            if (type.isVolatileQualified()) return true;
            if (type.isArray()) return hasVolatileSubobject(type.elementType(), visited);
            TypeEntity owner = objectType(type);
            return owner != null && visited.add(owner) && owner.fields.stream().anyMatch(f -> hasVolatileSubobject(f.type(), visited));
        }

        /** Laziness avoids requiring the definition of an unused member copy constructor. */
        private void emitImplicitCopy(TypeEntity owner) {emitImplicitTransfer(owner,false);}
        private void emitImplicitMove(TypeEntity owner) {emitImplicitTransfer(owner,true);}
        private void emitImplicitTransfer(TypeEntity owner,boolean move) {
            emitTransfer(move?owner.implicitMove:owner.implicitCopy,move?owner.movePlan:owner.copyPlan,move);
        }
        private void emitTransfer(Constructor constructor,CppCopyConstructorPlan.Result<Constructor> plan,boolean move) {
            TypeEntity owner=constructor.owner;
            if(emittedTransfers.contains(constructor.function)||plan.status()!=CppCopyConstructorPlan.Status.AVAILABLE)return;
            if (!plan.objectRepresentation() && owner.fields.stream().anyMatch(StructField::anonymous)) {
                report("CPP005", owner.sourceRecord.range(), "Nontrivial copying of anonymous aggregate storage requires subobject initialization support.");
                return;
            }
            if (unevaluatedDepth > 0) {
                if (!transferPrototypes.contains(constructor.function)) {
                    SourceRange range = owner.sourceRecord.range();
                    FunctionDecl declaration = new FunctionDecl(constructor.function.coreName, MiniType.VOID,
                            List.of(new Parameter(freshName("this"), owner.type.pointerTo(), range),
                                    new Parameter(freshName("other"), coreType(plan.parameterType()), range)),
                            false, null, false, range);
                    functions.add(declaration); declarations.add(declaration);
                    transferPrototypes.add(constructor.function);
                }
                return;
            }
            emittedTransfers.add(constructor.function);
            SourceRange range = owner.sourceRecord.range();
            String self = freshName("this"), source = freshName("other");
            MiniType sourcePointer = coreType(plan.parameterType());
            List<Statement> statements = new ArrayList<>();
            if (plan.objectRepresentation()) {
                Expression to = new UnaryExpr(TokenType.STAR, new NameExpr(self, range), range);
                Expression from = new UnaryExpr(TokenType.STAR, new NameExpr(source, range), range);
                statements.add(new ExprStmt(new InitializeExpr(to, from, range), range));
            }
            for (var entry : plan.entries()) {
                Expression destination = typed(new FieldAccessExpr(typed(new NameExpr(self, range), owner.type.pointerTo()),
                        entry.field().name(), true, entry.field().range()), coreType(entry.field().type()));
                MiniType sourceFieldType = entry.field().type().isReference() ? coreType(entry.field().type())
                        : inheritObjectQualifiers(plan.parameterType().referent(), entry.field().type());
                Expression from = typed(new FieldAccessExpr(typed(new NameExpr(source, range), sourcePointer),
                        entry.field().name(), true, entry.field().range()), sourceFieldType);
                statements.add(copyMemberStatement(entry, destination, from, entry.field().type(), 0));
            }
            var core = new FunctionDecl(constructor.function.coreName, MiniType.VOID,
                    List.of(new Parameter(self, owner.type.pointerTo(), range), new Parameter(source, sourcePointer, range)),
                    false, new BlockStmt(statements, range), false, range);
            mapped(constructor.source, core);functions.add(core);declarations.add(core);
        }

        private Statement copyMemberStatement(CppCopyConstructorPlan.Entry<Constructor> entry, Expression destination,
                                               Expression source, MiniType fieldType, int dimension) {
            SourceRange range = entry.field().range();
            if (dimension < entry.arrayDimensions().size()) {
                String index = freshName("copy_index");
                Expression i = typed(new NameExpr(index, range), MiniType.INT);
                MiniType element = MiniType.qualified(fieldType.elementType(), fieldType.qualifiers());
                Expression to = typed(new IndexExpr(destination, i, range), coreType(element));
                MiniType fromType = MiniType.qualified(element, declaredExpressionType(source).qualifiers());
                Expression from = typed(new IndexExpr(source, i, range), fromType);
                Statement body = copyMemberStatement(entry, to, from, element, dimension + 1);
                return new ForStmt(new VarDeclStmt(index, MiniType.INT, new IntegerLiteralExpr(0,"0",range),range),
                        new BinaryExpr(i, TokenType.LESS, new IntegerLiteralExpr(entry.arrayDimensions().get(dimension),"length",range),range),
                        new PostfixUpdateExpr(i,TokenType.PLUS_PLUS,range),body,range);
            }
            Expression value = source;
            if (entry.action() == CppCopyConstructorPlan.Action.CONSTRUCTOR) {
                Constructor selected = entry.constructor();
                instantiateConstructor(selected);
                if (trivialTransfer(selected)
                        && !hasVolatileSubobject(selected.owner.type, new HashSet<>()))
                    return new ExprStmt(new InitializeExpr(destination, source, range), range);
                if (selected == selected.owner.implicitCopy) emitImplicitCopy(selected.owner);
                var arguments=new ArrayList<Expression>();arguments.add(address(destination));arguments.add(address(source));
                arguments.addAll(defaultArguments(selected.function,selected.parameterTypes,1));
                Expression call = new CallExpr(new NameExpr(selected.function.coreName, range),arguments,range);
                return new ExprStmt(call,range);
            }
            return new ExprStmt(new InitializeExpr(destination,value,range),range);
        }

        private Expression recordPrvalue(MiniType type, Expression value, SourceRange range) {
            String destination = freshName("record_result");
            Expression slot = typed(new UnaryExpr(TokenType.STAR,
                    typed(new NameExpr(destination, range), type.unqualified().pointerTo()), range), type.unqualified());
            return typed(new ObjectInitExpr(coreType(type), destination, new InitializeExpr(slot, value, range), range), type);
        }

        private Expression initializerMapping(CppInitializer syntax, Expression value) {
            return mapped(syntax, typed(new GroupingExpr(value, syntax.range()), declaredExpressionType(value)));
        }

        private void requireNonNarrowing(MiniType target, Expression value, SourceRange range) {
            MiniType actual = declaredExpressionType(value);
            if (actual == null) return;
            if (target.isReference()) target = target.referent();
            if (target.unqualified().equals(MiniType.BOOL) && (actual.isPointer() || actual.isArray())) {
                report("CPP004", range, "List initialization cannot narrow a pointer to bool.");
                return;
            }
            if (!target.isScalar() || !actual.isScalar()) return;
            switch (CppListNarrowing.check(actual, target, literalNumericValue(value))) {
                case NARROWING -> report("CPP004", range, "List initialization requires a non-narrowing conversion.");
                case NEEDS_CONSTANT -> report("CPP005", range, "This list conversion requires constant-expression evaluation not supported yet.");
                default -> { }
            }
        }

        private Expression constructObject(MiniType target, CppInitializer syntax, Namespace namespace, Local local, SourceRange range) {
            TypeEntity owner = objectType(target);
            List<Expression> arguments = syntax.arguments();
            boolean list = isList(syntax);
            if (list && initializerListElement(target) != null)
                return initializeList(target, syntax, namespace, local, range);
            if (list && !arguments.isEmpty() && !nonAggregate(owner)) {
                report("CPP005", range, "Nonempty aggregate lists with default member initialization require member-wise list binding.");
            }
            PreparedArguments prepared = prepareArguments(arguments, namespace, local);
            if (syntax.kind() == CppInitializer.Kind.COPY && arguments.size() == 1 && prepared.values.getFirst() != null) {
                Expression value = prepared.values.getFirst();
                MiniType actual = declaredExpressionType(value);
                if (actual != null && !actual.unqualified().equals(target.unqualified())) {
                    Expression converted = userConversion(target, value, ConversionContext.IMPLICIT, range);
                    if (converted != null) return convertCallValue(target, converted, arguments.getFirst());
                    report("CPP004", range, "复制初始化没有唯一可行的单次用户定义转换。");
                    return value;
                }
            }
            boolean listPhase = false;
            if(list)bracedArguments.put(syntax, prepared);
            List<CppOverloadResolver.Candidate<Constructor>> listCandidates = list
                    ? expandConstructorTemplates(allConstructors(owner),List.of(syntax),range).stream()
                        .filter(this::initializerListConstructor).map(this::constructorCandidate).toList() : List.of();
            if (list && !(arguments.isEmpty() && expandConstructorTemplates(allConstructors(owner),List.of(),range).stream()
                    .anyMatch(c -> requiredParameters(c.function,c.parameterTypes.size())==0))) {
                CppOverloadResolver.Argument shape = argumentShape(syntax, syntax);
                if (shape == null) { report("CPP004", syntax.range(), "Cannot determine initializer-list element types.");
                    return new IntegerLiteralExpr(0, "0", syntax.range()); }
                var probe = CppOverloadResolver.resolve(listCandidates, List.of(shape), null, conversions,this::betterTemplateCandidate);
                if (probe.status() != CppOverloadResolver.Status.NO_VIABLE) {
                    arguments = List.of(syntax);
                    prepared = new PreparedArguments(arguments, Map.of());
                    listPhase = true;
                }
            }
            if (!listPhase && arguments.size() == 1 && prepared.values.getFirst() != null) {
                Expression value = prepared.values.getFirst();
                MiniType sourceType = declaredExpressionType(value);
                if (sourceType != null && target.unqualified().equals(sourceType.unqualified()))
                    return copyInitialize(target, value, range, syntax.kind());
            }
            List<CppOverloadResolver.Candidate<Constructor>> candidates = listPhase ? listCandidates : expandConstructorTemplates(allConstructors(owner),prepared.values,range).stream()
                    .filter(c -> syntax.kind() != CppInitializer.Kind.COPY || !c.source.explicitSpecifier())
                    .map(c -> new CppOverloadResolver.Candidate<>(c, c.parameterTypes, c.source.variadic(),null,false,requiredParameters(c.function,c.parameterTypes.size()))).toList();
            Constructor selected = selectOverload(candidates, new NameExpr(owner.name, range), arguments, prepared, null);
            if (selected != null) {
                instantiateConstructor(selected);
                if (selected == owner.implicitCopy) emitImplicitCopy(owner);
            }
            if (selected == null) return typed(new ObjectInitExpr(coreType(target), freshName("construction"),
                    new CastExpr(MiniType.VOID, new IntegerLiteralExpr(0, "0", range), range), range), target);
            if (list && arguments.isEmpty() && selected.implicit && owner.aggregateInitializer != null) selected = owner.aggregateInitializer;
            if (syntax.kind() == CppInitializer.Kind.COPY_LIST && selected.source.explicitSpecifier()) {
                report("CPP004", range, "Copy-list-initialization cannot select an explicit constructor: " + owner.canonicalName);
            }
            if (selected.access != Access.PUBLIC && !classAccess(owner)) {
                report("CPP004", range, "Constructor is not accessible: " + owner.canonicalName);
            }
            if (isDeleted(selected.function)) {
                Diagnostic reason = deletedConstructors.getOrDefault(selected.function,deletedReason(range)).getFirst();
                report(reason.code().equals("CPP005") ? "CPP005" : "CPP004", range, "The implicit default constructor is unavailable: " + reason.message());
            }
            if (list) for (int index = 0; index < arguments.size() && index < selected.parameterTypes.size(); index++) {
                if (prepared.values.get(index) != null) requireNonNarrowing(selected.parameterTypes.get(index), prepared.values.get(index), arguments.get(index).range());
            }
            List<Expression> lowered = new ArrayList<>();
            String destination = freshName("construction");
            lowered.add(typed(new NameExpr(destination, range), owner.type.pointerTo()));
            lowered.addAll(lowerSelectedArguments(selected.function, selected.parameterTypes, arguments, prepared.values, namespace, local, list && !listPhase));
            Expression call = typed(new CallExpr(new NameExpr(selected.function.coreName, range), lowered, range), MiniType.VOID);
            if (arguments.isEmpty() && (selected.implicit||defaulted(selected)&&!userProvidedDefaulted.contains(selected.function))
                    && (syntax.kind() == CppInitializer.Kind.DIRECT_PAREN || list && nonAggregate(owner))) {
                Expression slot = typed(new UnaryExpr(TokenType.STAR, typed(new NameExpr(destination, range), owner.type.pointerTo()), range), owner.type);
                call = new CommaExpr(List.of(new InitializeExpr(slot, new AggregateInitExpr(List.of(), range), range), call), range);
            }
            return typed(new ObjectInitExpr(coreType(target), destination, call, range), target);
        }

        private Expression initializer(MiniType target, Expression sourceNode, Namespace namespace, Local local, SourceRange range) {
            if (target.isReference()) return bindReference(target, sourceNode, namespace, local, range);
            if (sourceNode == null) {
                requireImplicitInitialization(target, false, range);
                return null;
            }
            return checkInitializer(target, sourceNode, expressionForTarget(target, sourceNode, namespace, local));
        }

        /** Forms an address without reading the referred-to object or changing source expression identity. */
        private Expression bindReference(MiniType reference, Expression sourceNode, Namespace namespace, Local local, SourceRange range) {
            return bindReference(reference, sourceNode, namespace, local, range, ConversionContext.IMPLICIT);
        }

        private Expression bindReference(MiniType reference, Expression sourceNode, Namespace namespace, Local local,
                                         SourceRange range, ConversionContext mode) {
            if (sourceNode == null) {
                report("CPP004", range, "引用必须绑定到初始化表达式。");
                return new NullLiteralExpr("nullptr", range);
            }
            if (isBraced(sourceNode)) {
                List<Expression> items = listItems(sourceNode);
                MiniType target = reference.referent();
                if (items.size() == 1 && !isBraced(items.getFirst())) {
                    Expression only = expression(items.getFirst(), namespace, local, true);
                    MiniType actual = declaredExpressionType(only);
                    if (actual != null && referenceRelated(target, actual)) {
                        Expression address = bindReferenceValue(reference, only, items.getFirst(), range, mode);
                        checkReferenceListConversion(target, address, sourceNode.range());
                        return mapped(sourceNode, new GroupingExpr(address, sourceNode.range()));
                    }
                }
                if (!target.isArray() && !target.isStruct() && items.size() == 1) {
                    Expression address = bindReference(reference, items.getFirst(), namespace, local, range, mode);
                    checkReferenceListConversion(target, address, sourceNode.range());
                    return mapped(sourceNode, new GroupingExpr(address, sourceNode.range()));
                }
                Expression value = bindListValue(target, sourceNode, namespace, local);
                return bindReferenceValue(reference, value, sourceNode, range, mode);
            }
            Expression savedOwner = fullExpressionOwner;
            if (fullExpressionOwner == null) fullExpressionOwner = sourceNode;
            try {
                Expression value = contextualFunctionAddress(reference, sourceNode, namespace, local);
                if (value == null) value = expression(sourceNode, namespace, local, true);
                return bindReferenceValue(reference, value, sourceNode, range, mode);
            } finally { fullExpressionOwner = savedOwner; }
        }

        private Expression bindReferenceValue(MiniType reference, Expression value, Expression sourceNode, SourceRange range) {
            return bindReferenceValue(reference, value, sourceNode, range, ConversionContext.IMPLICIT);
        }

        private Expression bindReferenceValue(MiniType reference, Expression value, Expression sourceNode,
                                              SourceRange range, ConversionContext mode) {
                MiniType target = reference.referent();
                MiniType actual = declaredExpressionType(value);
                if (actual != null && !standardViable(
                        new CppOverloadResolver.Argument(actual, valueCategory(value), isNullIntegerLiteral(sourceNode)), reference)) {
                    Expression converted = userConversion(reference, value, mode, range);
                    if (converted != null) { value = converted; actual = declaredExpressionType(value); }
                }
                CppValueCategory category = valueCategory(value);
                boolean compatible = actual != null && referenceCompatible(target, actual);
                boolean direct = compatible && addressableObject(value) && standardViable(
                        new CppOverloadResolver.Argument(actual,category,isNullIntegerLiteral(sourceNode)),reference);
                if (!direct) {
                    boolean viable = actual != null && standardViable(
                            new CppOverloadResolver.Argument(actual, category, isNullIntegerLiteral(sourceNode)),reference);
                    if (!viable) {
                        report("CPP004", sourceNode.range(), "引用不能绑定到此类型或值类别，或绑定会丢弃 const/volatile 限定符。");
                        return address(value);
                    }
                    // A materialized class subobject already has storage; binding extends its
                    // whole owner. Conversions and scalar prvalues need their own object.
                    if (!compatible || !addressableObject(value)) {
                        if(compatible&&baseDistance(actual,target)>0) {
                            Expression storage=materialize(actual,value);
                            return typed(new CastExpr(coreType(target).pointerTo(),storage,range),target.pointerTo());
                        }
                        requireComplete(target, sourceNode.range());
                        if (!target.unqualified().equals(actual.unqualified()) && !(compatible && target.isArray())) {
                            // Viability above validates C++ implicit conversion rules. Spell the
                            // approved conversion explicitly where core C is more restrictive.
                            value = typed(new CastExpr(coreType(target.unqualified()), value, value.range()), target.unqualified());
                        }
                        return materialize(target, value);
                    }
                }
                Expression address = address(value);
                // Array CV resides on elements; the checked C++ qualification conversion is
                // represented explicitly for the core pointer-to-array ABI.
                if (actual != null && compatible && (target.isArray()&&!target.equals(actual)||baseDistance(actual,target)>0)) {
                    address = typed(new CastExpr(coreType(target).pointerTo(), address, sourceNode.range()), target.pointerTo());
                }
                return address;
        }

        private boolean isNullIntegerLiteral(Expression source) {
            while (source instanceof GroupingExpr group) source = group.expression();
            return switch (source) {
                case IntegerLiteralExpr integer -> integer.value() == 0;
                case LongLiteralExpr integer -> integer.value() == 0;
                case IntegerConstantExpr integer -> integer.value() == 0
                        && !integer.lexeme().matches("[A-Za-z_][A-Za-z0-9_]*");
                default -> false;
            };
        }

        /** List reference initialization must not turn an otherwise-valid value conversion into narrowing. */
        private void checkReferenceListConversion(MiniType target, Expression address, SourceRange range) {
            if (!(address instanceof MaterializeExpr temporary) || !(temporary.initializer() instanceof CastExpr cast)) return;
            MiniType source = declaredExpressionType(cast.operand());
            if (!(target.unqualified() instanceof MiniType.ScalarType to) || source == null) return;
            if (!(source.unqualified() instanceof MiniType.ScalarType from)) {
                if (to.kind() == MiniType.ScalarKind.BOOL) report("CPP004", range, "列表初始化不能将指针窄化为 bool。");
                return;
            }
            var a = from.kind();
            var b = to.kind();
            if (a.floating() && b.integer()) {
                report("CPP004", range, "列表初始化不能将浮点数窄化为整数。");
                return;
            }
            if (a == b || a.floating() && b.floating() && b.sizeBytes() >= a.sizeBytes()) return;
            if (a.integer() && b.integer() && b != MiniType.ScalarKind.BOOL
                    && (b.sizeBytes() > a.sizeBytes() && (b.signed() || !a.signed())
                    || b.sizeBytes() == a.sizeBytes() && b.signed() == a.signed())) return;
            java.math.BigDecimal constant = literalNumericValue(cast.operand());
            if (constant == null) {
                report("CPP005", range, "此列表引用转换需要常量表达式窄化检查；当前仅支持可直接证明不窄化的类型或数值字面量。");
                return;
            }
            boolean representable;
            if (b.integer()) {
                int bits = b == MiniType.ScalarKind.BOOL ? 1 : b.sizeBytes() * 8;
                java.math.BigInteger min = b.signed() ? java.math.BigInteger.ONE.shiftLeft(bits - 1).negate() : java.math.BigInteger.ZERO;
                java.math.BigInteger max = java.math.BigInteger.ONE.shiftLeft(b.signed() ? bits - 1 : bits).subtract(java.math.BigInteger.ONE);
                representable = constant.compareTo(new java.math.BigDecimal(min)) >= 0
                        && constant.compareTo(new java.math.BigDecimal(max)) <= 0;
            } else {
                double converted = b == MiniType.ScalarKind.FLOAT ? (double) constant.floatValue() : constant.doubleValue();
                // C++17 permits an in-range constant floating-to-floating conversion
                // to round. Integer-to-floating conversion must still be exact.
                representable = Double.isFinite(converted)
                        && (a.floating() || new java.math.BigDecimal(converted).compareTo(constant) == 0);
            }
            if (!representable) report("CPP004", range, "列表初始化的值不能由引用临时对象类型精确表示。");
        }

        private java.math.BigDecimal literalNumericValue(Expression expression) {
            return switch (expression) {
                case GroupingExpr group -> literalNumericValue(group.expression());
                case IntegerLiteralExpr integer -> java.math.BigDecimal.valueOf(integer.value());
                case LongLiteralExpr integer -> java.math.BigDecimal.valueOf(integer.value());
                case IntegerConstantExpr integer -> integer.type().isUnsignedIntegerScalar()
                        ? new java.math.BigDecimal(Long.toUnsignedString(integer.value())) : java.math.BigDecimal.valueOf(integer.value());
                case BoolLiteralExpr value -> java.math.BigDecimal.valueOf(value.value() ? 1 : 0);
                case CharLiteralExpr value -> java.math.BigDecimal.valueOf(value.value());
                case FloatLiteralExpr value -> Float.isFinite(value.value()) ? new java.math.BigDecimal((double) value.value()) : null;
                case DoubleLiteralExpr value -> Double.isFinite(value.value()) ? new java.math.BigDecimal(value.value()) : null;
                case UnaryExpr unary when unary.operator() == TokenType.MINUS || unary.operator() == TokenType.PLUS -> {
                    var constant = literalNumericValue(unary.operand());
                    if (constant == null || unary.operator() == TokenType.PLUS) yield constant;
                    constant = constant.negate();
                    MiniType result = declaredExpressionType(unary);
                    if (result != null && result.isUnsignedIntegerScalar()
                            && result.unqualified() instanceof MiniType.ScalarType scalar) {
                        constant = new java.math.BigDecimal(constant.toBigIntegerExact()
                                .mod(java.math.BigInteger.ONE.shiftLeft(scalar.kind().sizeBytes() * 8)));
                    }
                    yield constant;
                }
                default -> null;
            };
        }

        private Expression materialize(MiniType type, Expression value) {
            Expression owner = fullExpressionOwner != null ? fullExpressionOwner : value;
            return typed(new MaterializeExpr(coreType(type), value,
                    new TemporaryLifetime(TemporaryLifetime.Kind.FULL_EXPRESSION, owner), value.range()), type.pointerTo());
        }

        private Expression materializedReceiver(Expression value) {
            MiniType type = declaredExpressionType(value);
            if (type == null || !type.isStruct() || addressableObject(value)) return value;
            Expression object = typed(new UnaryExpr(TokenType.STAR, materialize(type, value), value.range()), type);
            temporaryAddressPaths.add(object);
            // Core storage is addressable, but a temporary is not a C++ lvalue.
            valueCategories.put(object, CppValueCategory.PRVALUE);
            return object;
        }

        /** Follow only the bound object's address, never calls, arithmetic, or a temporary's initializer. */
        private Expression extendTemporaryLifetime(Expression expression, TemporaryLifetime lifetime) {
            Expression extended = switch (expression) {
                case MaterializeExpr temporary -> new MaterializeExpr(temporary.type(),
                        extendListLifetime(temporary.initializer(), lifetime), lifetime, temporary.range());
                case GroupingExpr group -> new GroupingExpr(extendTemporaryLifetime(group.expression(), lifetime), group.range());
                case UnaryExpr unary when unary.operator() == TokenType.AMPERSAND || temporaryAddressPaths.contains(unary) ->
                        new UnaryExpr(unary.operator(), extendTemporaryLifetime(unary.operand(), lifetime), unary.range());
                case FieldAccessExpr field when !field.viaPointer() -> new FieldAccessExpr(
                        extendTemporaryLifetime(field.target(), lifetime), field.fieldName(), false, field.range());
                case IndexExpr index when declaredExpressionType(index.target()) != null && declaredExpressionType(index.target()).isArray() ->
                        new IndexExpr(extendTemporaryLifetime(index.target(), lifetime), index.index(), index.range());
                case CommaExpr comma -> {
                    var values = new ArrayList<>(comma.expressions());
                    values.set(values.size() - 1, extendTemporaryLifetime(values.getLast(), lifetime));
                    yield new CommaExpr(values, comma.range());
                }
                case ConditionalExpr conditional -> new ConditionalExpr(conditional.condition(),
                        extendTemporaryLifetime(conditional.thenExpression(), lifetime),
                        extendTemporaryLifetime(conditional.elseExpression(), lifetime), conditional.range());
                case CastExpr cast when cast.operand() instanceof UnaryExpr unary && unary.operator() == TokenType.AMPERSAND ->
                        new CastExpr(cast.targetType(), extendTemporaryLifetime(cast.operand(), lifetime), cast.range());
                default -> expression;
            };
            if (extended != expression) {
                if (declaredExpressionTypes.containsKey(expression)) declaredExpressionTypes.put(extended, declaredExpressionTypes.get(expression));
                if (valueCategories.containsKey(expression)) valueCategories.put(extended, valueCategories.get(expression));
                if (temporaryAddressPaths.contains(expression)) temporaryAddressPaths.add(extended);
                origins.replaceAll((source, core) -> core == expression ? extended : core);
            }
            return extended;
        }

        private boolean referenceCompatible(MiniType target, MiniType actual) {
            if (target.isArray() && actual.isArray()) {
                return target.arrayLength() == actual.arrayLength()
                        && referenceCompatible(elementType(target), elementType(actual));
            }
            if (!target.qualifiers().containsAll(actual.qualifiers())) return false;
            return target.unqualified().equals(actual.unqualified())||baseDistance(actual,target)>0;
        }

        private void requireImplicitInitialization(MiniType target, boolean valueInitialization, SourceRange range) {
            if (needsConstConstructionRules(target, valueInitialization, new HashSet<>())) {
                report("CPP005", range, "尚未支持含 const 子对象的隐式构造初始化规则；不能直接按 C 聚合零填。");
            }
        }

        private boolean needsConstConstructionRules(MiniType type, boolean valueInitialization, Set<String> visited) {
            if (type == null) return false;
            if (type.unqualified() instanceof MiniType.ArrayType array) {
                return needsConstConstructionRules(array.elementType(), valueInitialization, visited);
            }
            TypeEntity object = objectType(type);
            if (object == null || !visited.add(object.canonicalName)) return false;
            if ((!valueInitialization || nonAggregate(object)) && hasConstSubobject(type, new HashSet<>())) return true;
            List<StructField> fields = object.union && !object.fields.isEmpty() ? List.of(object.fields.getFirst()) : object.fields;
            return fields.stream().anyMatch(field -> needsConstConstructionRules(field.type(), valueInitialization, visited));
        }

        private boolean hasConstSubobject(MiniType type, Set<String> visited) {
            if (type.isConstQualified()) return true;
            if (type.unqualified() instanceof MiniType.ArrayType array) return hasConstSubobject(array.elementType(), visited);
            TypeEntity object = objectType(type); // Do not follow pointer pointees.
            return object != null && visited.add(object.canonicalName)
                    && object.fields.stream().anyMatch(field -> hasConstSubobject(field.type(), visited));
        }

        private boolean hasReferenceSubobject(MiniType type, Set<String> visited) {
            if (type.isReference()) return true;
            if (type.unqualified() instanceof MiniType.ArrayType array) return hasReferenceSubobject(array.elementType(), visited);
            TypeEntity object = objectType(type);
            return object != null && visited.add(object.canonicalName)
                    && object.fields.stream().anyMatch(field -> hasReferenceSubobject(field.type(), visited));
        }

        /** Validates C++ list initialization before the C aggregate initializer can write fields. */
        private Expression checkInitializer(MiniType target, Expression sourceNode, Expression bound) {
            return checkInitializer(target, sourceNode, bound, false);
        }
        private Expression checkInitializer(MiniType target, Expression sourceNode, Expression bound, boolean listElement) {
            if (target == null) return bound;
            TypeEntity object = objectType(target);
            if (!(bound instanceof AggregateInitExpr list)) {
                return listElement ? convertListElement(target, bound, sourceNode) : convertCallValue(target, bound, sourceNode);
            }
            AggregateInitExpr original = (AggregateInitExpr) sourceNode;
            if (object != null && list.values().size() == 1) {
                Expression value = list.values().getFirst();
                MiniType valueType = declaredExpressionType(value);
                if(valueType!=null&&baseDistance(valueType,target)>0)
                    return mapped(sourceNode,markerBaseValue(target.unqualified(),value,list.range()));
                if (valueType != null && target.unqualified().equals(valueType.unqualified())) {
                    // C++17 permits {sameTypeObject}, including implicit copies of non-aggregates.
                    return mapped(sourceNode, new GroupingExpr(copyInitialize(target, value, list.range(), CppInitializer.Kind.COPY_LIST), list.range()));
                }
            }
            if (nonAggregate(object) && !list.values().isEmpty()) {
                report("CPP004", sourceNode.range(), "含有非 public 数据成员的类型不能使用成员值列表进行聚合初始化：" + object.canonicalName);
                return bound;
            }
            List<Expression> values = new ArrayList<>();
            Set<Integer> initialized = new HashSet<>();
            int position = 0;
            for (int i = 0; i < list.values().size(); i++) {
                Expression value = list.values().get(i), originalValue = original.values().get(i);
                MiniType element;
                if (value instanceof DesignatedInitExpr designated) {
                    var sourceDesignated = (DesignatedInitExpr) originalValue;
                    if (designated.designators().size() > 1) {
                        report("CPP005", designated.range(), "C++17 模式尚未支持多层路径指定初始化。");
                    }
                    element = designatedTarget(target, designated.designators());
                    if (designated.designators().getFirst() instanceof Designator.Index index) position = index.index();
                    else if (object != null && designated.designators().getFirst() instanceof Designator.Field field) {
                        for (int j = 0; j < object.fields.size(); j++) if (object.fields.get(j).name().equals(field.name())) position = j;
                    }
                    values.add(mapped(originalValue, new DesignatedInitExpr(designated.designators(),
                            checkInitializer(element, sourceDesignated.value(), designated.value(), true), designated.range())));
                } else {
                    element = target.unqualified() instanceof MiniType.ArrayType array ? array.elementType()
                            : object != null && position < object.fields.size() ? object.fields.get(position).type() : null;
                    values.add(checkInitializer(element, originalValue, value, true));
                }
                initialized.add(position);
                position++;
            }
            if (target.unqualified() instanceof MiniType.ArrayType array && initialized.size() < array.length()) {
                requireImplicitInitialization(array.elementType(), true, list.range());
            } else if (object != null) {
                if (list.values().isEmpty() && nonAggregate(object)) requireImplicitInitialization(target, true, list.range());
                else for (int i = 0; i < object.fields.size(); i++) {
                    if (object.union && (i > 0 || !list.values().isEmpty())) break;
                    if (!initialized.contains(i)) requireImplicitInitialization(object.fields.get(i).type(), true, list.range());
                }
            }
            return mapped(sourceNode, new AggregateInitExpr(values, list.range()));
        }

        private MiniType designatedTarget(MiniType target, List<Designator> designators) {
            MiniType current = target;
            for (Designator designator : designators) {
                if (current == null) return null;
                if (nonAggregate(objectType(current))) {
                    report("CPP004", designator.range(), "非聚合类型不能通过指定初始化展开其数据成员。");
                    return null;
                }
                if (designator instanceof Designator.Index && current.unqualified() instanceof MiniType.ArrayType array) {
                    current = array.elementType();
                } else if (designator instanceof Designator.Field field) {
                    FieldPath path = requireAccessible(current, field.name(), field.range(), "指定初始化访问");
                    current = path == null ? null : path.type();
                } else return null;
            }
            return current;
        }

        private List<Expression> expressions(List<Expression> nodes, Namespace namespace, Local local) {
            return nodes.stream().map(n -> expression(n, namespace, local)).toList();
        }

        private boolean existingInternal(Namespace namespace, String name) {
            Candidate candidate = namespace.values.get(name);
            if (candidate instanceof Entity entity) return internalLinkages.getOrDefault(entity, false);
            return candidate instanceof OverloadSet set && set.functions.stream()
                    .allMatch(entity -> internalLinkages.getOrDefault(entity, false));
        }

        private void recordLinkage(Entity entity, SourceRange range) {
            if (internalDeclaration && entity.kind == Kind.FUNCTION && entity.owner == root && entity.name.equals("main"))
                report("CPP004", range, "main 不能具有内部链接。");
            Boolean previous = internalLinkages.putIfAbsent(entity, internalDeclaration);
            if (internalDeclaration && Boolean.FALSE.equals(previous))
                report("CPP004", range, "static 声明不能改变先前声明的外部链接：" + entity.name);
        }

        private String functionReferenceName(Entity entity) {
            instantiateFunctionTemplate(entity);
            if(isDeleted(entity))report("CPP004",specialDefinitionRanges.getOrDefault(entity,source.range()),"Use of deleted function: "+entity.name);
            if (libraryExitFunctions.contains(entity)) {
                String name = staticLifetime.exitFunction();
                coreValues.putIfAbsent(name, new Entity("exit", name, Kind.FUNCTION, root, entity.type, null, true));
                return name;
            }
            return entity.coreName;
        }

        private Expression reference(String fallback, SourceRange range, Entity entity) {
            return reference(fallback, range, entity, false);
        }

        private Expression reference(String fallback, SourceRange range, Entity entity, boolean addressDemand) {
            StaticField field = staticFields.get(entity);
            if (field != null) return staticFieldReference(field, range, addressDemand);
            return referenceStorage(fallback, range, entity);
        }

        private Expression referenceStorage(String fallback, SourceRange range, Entity entity) {
            if (entity == null) return new NameExpr(fallback, range);
            if (entity.kind == Kind.ENUM_CONSTANT) return new IntegerConstantExpr(entity.enumValue, MiniType.INT, entity.name, range);
            NameExpr name = new NameExpr(functionReferenceName(entity), range);
            if (!entity.type.isReference()) return name;
            declaredExpressionTypes.put(name, coreType(entity.type));
            UnaryExpr object = new UnaryExpr(TokenType.STAR, name, range);
            declaredExpressionTypes.put(object, entity.type.referent());
            return object;
        }

        private Entity lookupValue(String name, Namespace namespace, Local local, SourceRange range) {
            return requireValue(lookupName(name, namespace, local, range), name, range);
        }

        private Candidate lookupName(String name, Namespace namespace, Local local, SourceRange range) {
            for (Local scope = local; scope != null; scope = scope.parent) {
                Candidate value = scope.values.get(name);
                if (value != null) return value;
                if (scope.typedefs.containsKey(name)) return scope.typedefs.get(name);
            }
            if (currentClass != null) {
                StaticField field = currentClass.staticFields.get(name);
                if (field != null) return field.entity;
                MethodSet methods = currentClass.methods.get(name);
                if (methods != null) return methods;
                if (fieldPath(currentClass.type, name, new HashSet<>()) != null) return new ImplicitField(currentClass, name);
            }
            LambdaInfo lambda=lambdaTypes.get(currentClass);
            if(lambda!=null)return lambdaLookup(lambda,name,range);
            Map<Namespace, Set<Namespace>> nominated = nominations(namespace, local);
            for (Namespace scope = namespace; scope != null; scope = scope.parent) {
                Set<Candidate> candidates = new LinkedHashSet<>(directCandidates(scope, name));
                for (Namespace target : nominated.getOrDefault(scope, Set.of())) {
                    candidates.addAll(directCandidates(target, name));
                }
                if (!candidates.isEmpty()) return selectCandidate(candidates, name, range);
            }
            report("CPP003", range, "此位置尚未声明名称：" + name);
            return null;
        }

        private Set<Candidate> directCandidates(Namespace namespace, String name) {
            NamespaceView view = visible(namespace);
            Candidate value = view.values.get(name);
            if (value != null) return Set.of(value); // An ordinary value can hide the injected class name.
            Set<Candidate> result = new LinkedHashSet<>();
            if (view.children.containsKey(name)) result.add(view.children.get(name));
            if (view.typedefs.containsKey(name)) result.add(view.typedefs.get(name));
            else if (view.tags.containsKey(name)) result.add(view.tags.get(name));
            return result;
        }

        /** Using directives inject candidates at the common ancestor, not into a local symbol table. */
        private Map<Namespace, Set<Namespace>> nominations(Namespace namespace, Local local) {
            Map<Namespace, Set<Namespace>> result = new IdentityHashMap<>();
            for (Local scope = local; scope != null; scope = scope.parent) {
                for (Namespace target : scope.directives) nominate(scope.namespace, target, result, new HashSet<>());
            }
            for (Namespace scope = namespace; scope != null; scope = scope.parent) {
                for (Namespace target : visible(scope).directives) nominate(scope, target, result, new HashSet<>());
            }
            return result;
        }

        private void nominate(Namespace origin, Namespace target, Map<Namespace, Set<Namespace>> result, Set<Namespace> visited) {
            if (!visited.add(target)) return;
            result.computeIfAbsent(commonAncestor(origin, target), ignored -> new LinkedHashSet<>()).add(target);
            for (Namespace next : visible(target).directives) nominate(origin, next, result, visited);
        }

        private Namespace commonAncestor(Namespace first, Namespace second) {
            Set<Namespace> ancestors = new HashSet<>();
            for (Namespace at = first; at != null; at = at.parent) ancestors.add(at);
            for (Namespace at = second; at != null; at = at.parent) if (ancestors.contains(at)) return at;
            throw new IllegalStateException("namespace trees do not share a root");
        }

        private Entity resolveQualified(QualifiedName name, Namespace namespace, Local local) {
            return requireValue(resolveQualifiedName(name, namespace, local), spelling(name), name.range());
        }

        private Candidate classMember(TypeEntity owner, String name, SourceRange range) {
            if (owner == null) { report("CPP004", range, "Member qualifier must denote a class type."); return null; }
            StaticField field = owner.staticFields.get(name);
            if (field != null) return field.entity;
            MethodSet method = owner.methods.get(name);
            if (method != null) return method;
            if (fieldPath(owner.type, name, new HashSet<>()) != null) return new ImplicitField(owner, name);
            report("CPP003", range, "Class member is not declared: " + owner.canonicalName + "::" + name);
            return null;
        }

        private Candidate typeMember(CppTypeMemberExpr node, Namespace namespace, Local local) {
            MiniType type = normalizeType(node.ownerType(), namespace, local, node.range());
            return classMember(objectType(type), node.memberName(), node.nameRange());
        }

        private TypeEntity probeClassQualifier(QualifiedName prefix, Namespace namespace, Local local) {
            if (!prefix.global() && prefix.segments().size() == 1) {
                String name = prefix.segments().getFirst();
                for (Local at = local; at != null; at = at.parent) {
                    TypeEntity type = at.typedefs.get(name);
                    if (type != null && objectType(type.type) != null) return objectType(type.type);
                }
                if (currentClass != null && currentClass.name.equals(name)) return currentClass;
            }
            int before = diagnostics.size();
            TypeEntity owner = resolveMethodOwner(prefix, namespace);
            diagnostics.subList(before, diagnostics.size()).clear();
            return owner;
        }

        private Candidate resolveQualifiedName(QualifiedName name, Namespace namespace, Local local) {
            List<String> segments = name.segments();
            if (segments.size() > 1) {
                QualifiedName prefix = new QualifiedName(name.global(), segments.subList(0, segments.size() - 1), name.range());
                TypeEntity type = probeClassQualifier(prefix, namespace, local);
                if (type != null) return classMember(type, segments.getLast(), name.range());
            }
            Namespace owner;
            if (segments.size() == 1) {
                if (!name.global()) return lookupName(segments.getFirst(), namespace, local, name.range());
                owner = root;
            } else {
                owner = resolveNamespace(new QualifiedName(name.global(), segments.subList(0, segments.size() - 1), name.range()), namespace, local);
            }
            if (owner == null) return null;
            Set<Candidate> values = qualifiedValues(owner, segments.getLast(), new HashSet<>());
            if (values.isEmpty()) {
                report("CPP003", name.range(), "此位置尚未声明限定名称：" + spelling(name));
                return null;
            }
            return selectCandidate(values, spelling(name), name.range());
        }

        private Set<Candidate> qualifiedValues(Namespace namespace, String name, Set<Namespace> visited) {
            if (!visited.add(namespace)) return Set.of();
            Set<Candidate> result = new LinkedHashSet<>(directCandidates(namespace, name));
            if (!result.isEmpty()) return result;
            for (Namespace target : visible(namespace).directives) result.addAll(qualifiedValues(target, name, visited));
            return result;
        }

        private Namespace resolveNamespace(QualifiedName name, Namespace namespace, Local local) {
            if (name.global() && rejectsTypeQualifier(root, name.segments().getFirst(), name.range())) return null;
            Namespace at = name.global() ? selectNamespace(qualifiedNamespaces(root, name.segments().getFirst(), new HashSet<>()), name)
                    : lookupNamespace(name.segments().getFirst(), namespace, local, name);
            for (int i = 1; at != null && i < name.segments().size(); i++) {
                if (rejectsTypeQualifier(at, name.segments().get(i), name.range())) return null;
                at = selectNamespace(qualifiedNamespaces(at, name.segments().get(i), new HashSet<>()), name);
            }
            return at;
        }

        private Namespace lookupNamespace(String name, Namespace namespace, Local local, QualifiedName sourceName) {
            for (Local scope = local; scope != null; scope = scope.parent) {
                if (scope.typedefs.containsKey(name) && scope.typedefs.get(name).type.unqualified().isStruct()) {
                    report("CPP005", sourceName.range(), "尚未支持类型限定名称：" + name);
                    return null;
                }
            }
            Map<Namespace, Set<Namespace>> nominated = nominations(namespace, local);
            for (Namespace scope = namespace; scope != null; scope = scope.parent) {
                if (rejectsTypeQualifier(scope, name, sourceName.range())) return null;
                Set<Namespace> candidates = new LinkedHashSet<>();
                if (visible(scope).children.containsKey(name)) candidates.add(visible(scope).children.get(name));
                for (Namespace target : nominated.getOrDefault(scope, Set.of())) {
                    if (visible(target).children.containsKey(name)) candidates.add(visible(target).children.get(name));
                }
                if (!candidates.isEmpty()) return selectNamespace(candidates, sourceName);
            }
            return selectNamespace(Set.of(), sourceName);
        }

        private boolean rejectsTypeQualifier(Namespace namespace, String name, SourceRange range) {
            TypeEntity type = visible(namespace).typedefs.get(name);
            if (type == null) type = visible(namespace).tags.get(name);
            if (type == null || !type.type.unqualified().isStruct()) return false;
            report("CPP005", range, "尚未支持类型限定名称：" + name);
            return true;
        }

        private Set<Namespace> qualifiedNamespaces(Namespace namespace, String name, Set<Namespace> visited) {
            if (!visited.add(namespace)) return Set.of();
            Namespace direct = visible(namespace).children.get(name);
            if (direct != null) return Set.of(direct);
            Set<Namespace> result = new LinkedHashSet<>();
            for (Namespace target : visible(namespace).directives) result.addAll(qualifiedNamespaces(target, name, visited));
            return result;
        }

        private Namespace selectNamespace(Set<Namespace> values, QualifiedName name) {
            if (values.size() == 1) return values.iterator().next();
            report("CPP003", name.range(), values.isEmpty() ? "此位置尚未声明命名空间：" + spelling(name)
                    : "命名空间查找具有二义性：" + spelling(name));
            return null;
        }

        private Candidate selectCandidate(Set<Candidate> candidates, String name, SourceRange range) {
            if (candidates.size() == 1) return candidates.iterator().next();
            if (!candidates.isEmpty() && candidates.stream().allMatch(OverloadSet.class::isInstance)) {
                Set<Entity> merged = new LinkedHashSet<>();
                candidates.stream().map(OverloadSet.class::cast).forEach(set -> merged.addAll(set.functions));
                return new OverloadSet(new ArrayList<>(merged));
            }
            if (!candidates.isEmpty() && candidates.stream().allMatch(TypeEntity.class::isInstance)) {
                TypeEntity first = (TypeEntity) candidates.iterator().next();
                if (candidates.stream().map(TypeEntity.class::cast).allMatch(t -> t.type.equals(first.type))) return first;
            }
            report("CPP003", range, "名称查找具有二义性：" + name);
            return null;
        }

        private Entity requireValue(Candidate candidate, String name, SourceRange range) {
            if(candidate instanceof OverloadSet set && set.functions.size()==1 && functionTemplates.containsKey(set.functions.getFirst())) {
                report("CPP004",range,"Function template requires deduction or explicit template arguments: "+name);return null;
            }
            if (candidate == null) return null;
            if (candidate instanceof Entity entity) return entity;
            if (candidate instanceof OverloadSet set) {
                if (set.functions.size() == 1) return set.functions.getFirst();
                report("CPP003", range, "重载函数名称需要调用实参或目标函数类型：" + name);
                return null;
            }
            report("CPP003", range, (candidate instanceof TypeEntity ? "类型" : "命名空间") + "不能作为值使用：" + name);
            return null;
        }

        private boolean constantInitializer(Expression node) {
            return switch (node) {
                case BoolLiteralExpr ignored -> true;
                case CharLiteralExpr ignored -> true;
                case DoubleLiteralExpr ignored -> true;
                case FloatLiteralExpr ignored -> true;
                case IntegerLiteralExpr ignored -> true;
                case IntegerConstantExpr ignored -> true;
                case LongLiteralExpr ignored -> true;
                case NullLiteralExpr ignored -> true;
                case SizeofExpr ignored -> true;
                case AlignofExpr ignored -> true;
                case GroupingExpr n -> constantInitializer(n.expression());
                case CastExpr n -> constantInitializer(n.operand());
                case CommaExpr n -> n.expressions().stream().allMatch(this::constantInitializer);
                case UnaryExpr n -> Set.of(TokenType.PLUS, TokenType.MINUS, TokenType.TILDE, TokenType.BANG).contains(n.operator())
                        && constantInitializer(n.operand());
                case BinaryExpr n -> constantInitializer(n.left()) && constantInitializer(n.right());
                case AggregateInitExpr n -> n.values().stream().allMatch(this::constantInitializer);
                case DesignatedInitExpr n -> constantInitializer(n.value());
                default -> false;
            };
        }

        private void reserveNames(AstNode node) {
            switch (node) {
                case NamespaceDecl n -> reserved.addAll(n.name().segments());
                case UsingDecl n -> reserved.addAll(n.target().segments());
                case FunctionDecl n -> { reserved.add(n.name()); n.parameters().forEach(this::reserveNames); }
                case GlobalVarDecl n -> reserved.add(n.name());
                case VarDeclStmt n -> reserved.add(n.name());
                case Parameter n -> reserved.add(n.name());
                case TypedefDecl n -> reserved.add(n.name());
                case TypedefStmt n -> reserved.add(n.name());
                case StructDecl n -> { reserved.add(n.name()); n.fields().forEach(this::reserveNames); }
                case StructField n -> reserved.add(n.name());
                case EnumDecl n -> { reserved.add(n.name()); n.enumerators().forEach(this::reserveNames); }
                case Enumerator n -> reserved.add(n.name());
                case NameExpr n -> reserved.add(n.name());
                case QualifiedNameExpr n -> reserved.addAll(n.name().segments());
                case FieldAccessExpr n -> reserved.add(n.fieldName());
                default -> { }
            }
            AstChildren.of(node).forEach(this::reserveNames);
        }

        private String freshName(String displayName) {
            String candidate;
            do { candidate = "minicCppSymbol" + nextName++; } while (!reserved.add(candidate));
            for (var entry : templateDisplay.entrySet()) displayName = displayName.replaceAll(
                    "(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(entry.getKey()) + "(?![A-Za-z0-9_])",
                    java.util.regex.Matcher.quoteReplacement(entry.getValue()));
            displayNames.put(candidate, displayName);
            return candidate;
        }

        private <T extends AstNode> T mapped(AstNode from, T to) {
            origins.put(from, to);
            if (templateOrigins.containsKey(from)) origins.putIfAbsent(templateOrigins.get(from), to);
            return to;
        }
        private String spelling(QualifiedName name) { return (name.global() ? "::" : "") + String.join("::", name.segments()); }
        private void report(String code, SourceRange range, String message) {
            diagnostics.add(new Diagnostic(code, Diagnostic.Severity.ERROR, message, range));
        }
    }
}
