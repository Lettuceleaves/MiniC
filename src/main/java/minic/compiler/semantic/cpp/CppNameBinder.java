package minic.compiler.semantic.cpp;

import minic.SourceRange;
import minic.compiler.Diagnostic;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.CppInitializer;
import minic.compiler.parser.node.CppConstructionExpr;
import minic.compiler.parser.node.CppDestructorCallExpr;
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
        StructDecl sourceRecord;
        final List<Constructor> constructors = new ArrayList<>();
        final Map<String, Entity> defaultInitializers = new LinkedHashMap<>();
        Constructor aggregateInitializer;
        Constructor implicitCopy;
        CppCopyConstructorPlan.Result<Constructor> copyPlan;
        boolean copyEmitted;
        boolean copyPrototypeEmitted;
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

    private record ImplicitField(TypeEntity owner, String name) implements Candidate { }

    /** An entity survives redeclarations and using aliases; candidate deduplication uses identity. */
    private static final class Entity implements Candidate {
        final String name;
        final String coreName;
        final Kind kind;
        final Namespace owner;
        final MiniType type;
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
        private final IdentityHashMap<Expression, MiniType> declaredExpressionTypes = new IdentityHashMap<>();
        private final IdentityHashMap<Expression, CppValueCategory> valueCategories = new IdentityHashMap<>();
        private final Set<Expression> temporaryAddressPaths = Collections.newSetFromMap(new IdentityHashMap<>());
        private int unevaluatedDepth;
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
        private Expression fullExpressionOwner;

        Binding(Program source) { this.source = source; reserveNames(source); }

        Result run() {
            bindDeclarations(source.declarations(), root);
            // The compatibility constructor deliberately produces a core C Program.
            Program core = mapped(source, new Program(structs, enums, typedefs, globals, functions,
                    declarations, source.range()));
            return new Result(core, diagnostics, origins, displayNames);
        }

        private void bindDeclarations(List<Declaration> input, Namespace namespace) {
            for (Declaration declaration : input) {
                switch (declaration) {
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
            Map<ConstructorMember, Access> constructorAccess = new IdentityHashMap<>();
            Map<DestructorMember, Access> destructorAccess = new IdentityHashMap<>();
            if (node.cppInfo() != null) {
                Access current = node.cppInfo().key() == RecordKey.CLASS ? Access.PRIVATE : Access.PUBLIC;
                for (CppMember member : node.cppInfo().members()) {
                    if (member instanceof AccessLabel label) current = label.access();
                    else if (member instanceof FieldMember field) {
                        access.put(field.field(), current);
                    }
                    else if (member instanceof MethodMember method) methodAccess.put(method, current);
                    else if (member instanceof ConstructorMember constructor) constructorAccess.put(constructor, current);
                    else if (member instanceof DestructorMember destructor) destructorAccess.put(destructor, current);
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
            }
            structs.add(core); declarations.add(core);
            if (node.definition()) {
                List<CppMember> members = node.cppInfo() == null ? List.of() : node.cppInfo().members();
                List<Method> methods = new ArrayList<>();
                List<Constructor> constructors = new ArrayList<>();
                Destructor destructor = null;
                for (CppMember member : members) {
                    if (member instanceof MethodMember method) {
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
                if (constructors.isEmpty() && needsConstruction(entity)) {
                    ConstructorMember synthetic = new ConstructorMember(entity.name, List.of(), false, List.of(),
                            new BlockStmt(List.of(), node.range()), node.range(), node.range());
                    Constructor registered = declareConstructor(entity, synthetic, Access.PUBLIC, true);
                    if (registered != null) constructors.add(registered);
                }
                // Complete-class lookup applies to bodies, without exposing later namespace declarations.
                for (Constructor constructor : constructors) bindConstructor(constructor);
                if (entity.constructors.size() == 1 && entity.constructors.getFirst().implicit && !nonAggregate(entity)) {
                    Constructor original = entity.constructors.getFirst();
                    Entity function = new Entity(entity.name, freshName(entity.canonicalName.substring(2) + "::" + entity.name),
                            Kind.FUNCTION, namespace, original.function.type, null, true);
                    coreValues.put(function.coreName, function);
                    entity.aggregateInitializer = new Constructor(entity, original.source, Access.PUBLIC, function, List.of(), true);
                    bindConstructor(entity.aggregateInitializer, true);
                }
                if (destructor != null) bindDestructor(destructor);
                ensureImplicitCopy(entity);
                for (Method method : methods) bindMethod(method, namespace);
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
                    Kind.FUNCTION, owner.owner, MiniType.function(MiniType.VOID, List.of(owner.type.pointerTo()), false), null, member.body() != null);
            coreValues.put(function.coreName, function);
            Destructor destructor = new Destructor(owner, member, access, function, implicit);
            owner.destructor = destructor;
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
            if (!enclosing || node.destructor().body() == null) {
                report("CPP004", node.nameRange(), "A destructor definition must be in its enclosing namespace and have a body.");
            } else if (previous == null || previous.implicit) {
                report("CPP004", node.nameRange(), "No matching user-declared destructor: " + owner.canonicalName);
            } else if (previous.function.defined) {
                report("CPP004", node.nameRange(), "Duplicate destructor definition: " + owner.canonicalName);
            } else {
                previous.function.defined = true;
                bindDestructor(new Destructor(owner, node.destructor(), previous.access, previous.function, false));
            }
        }

        private void bindDestructor(Destructor destructor) {
            TypeEntity owner = destructor.owner;
            DestructorMember original = destructor.source;
            Entity self = constructorThis(owner, original.nameRange());
            TypeEntity savedClass = currentClass;
            Entity savedThis = currentThis;
            MiniType savedReturn = currentReturnType;
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
            } finally { currentClass = savedClass; currentThis = savedThis; currentReturnType = savedReturn; }
        }

        private Expression destruction(MiniType type, Expression address, SourceRange range) {
            Destructor destructor = destructorForUse(type, range);
            if (destructor == null) return null;
            TypeEntity owner = destructor.owner;
            // cv-qualification ceases to apply while the object's destructor executes.
            Expression receiver = typed(new CastExpr(owner.type.pointerTo(), address, range), owner.type.pointerTo());
            return typed(new CallExpr(new NameExpr(destructor.function.coreName, range), List.of(receiver), range), MiniType.VOID);
        }

        private Destructor destructorForUse(MiniType type, SourceRange range) {
            if (!needsDestruction(type)) return null;
            if (type.isArray()) {
                report("CPP005", range, "Array element destruction requires array lifetime support.");
                return null;
            }
            TypeEntity owner = objectType(type);
            Destructor destructor = owner.destructor;
            if (destructor.access != Access.PUBLIC && currentClass != owner) {
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
                    Kind.FUNCTION, owner.owner, signature, null, member.body() != null);
            coreValues.put(function.coreName, function);
            Constructor constructor = new Constructor(owner, member, access, function, parameters, implicit);
            owner.constructors.add(constructor);
            return constructor;
        }

        private void bindOutOfLineConstructor(OutOfLineConstructorDecl node, Namespace namespace) {
            QualifiedName path = node.qualifiedName();
            TypeEntity owner = resolveMethodOwner(new QualifiedName(path.global(),
                    path.segments().subList(0, path.segments().size() - 1), path.range()), namespace);
            if (owner == null) return;
            boolean enclosing = false;
            for (Namespace at = owner.owner; at != null; at = at.parent) enclosing |= at == namespace;
            if (!enclosing || node.constructor().body() == null) {
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
            previous.function.defined = true;
            bindConstructor(new Constructor(owner, node.constructor(), previous.access, previous.function, parameters, false));
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
            } finally { currentClass = savedClass; currentThis = savedThis; currentReturnType = savedReturn; }
        }

        private void bindConstructor(Constructor constructor) {
            bindConstructor(constructor, false);
        }

        private void bindConstructor(Constructor constructor, boolean aggregateList) {
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
            } finally { currentClass = savedClass; currentThis = savedThis; currentReturnType = savedReturn; }
        }

        private Expression initializeField(TypeEntity owner, StructField field, CppInitializer initialization, Local scope, SourceRange range) {
            Expression value = variableInitializer(field.type(), initialization, null, owner.owner, scope, range);
            if (value == null) return null;
            if (field.type().isArray() || value instanceof AggregateInitExpr aggregate && !aggregate.values().isEmpty()) {
                report("CPP005", range, "Array or nonempty aggregate member initialization requires subobject initialization support.");
                return null;
            }
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

        private Method declareMethod(TypeEntity owner, MethodMember member, Access access, Namespace namespace) {
            if (unsupportedOperator(member.method())) return null;
            FunctionDecl sourceMethod = member.method();
            String name = sourceMethod.name();
            if (fieldPath(owner.type, name, new HashSet<>()) != null) {
                report("CPP004", member.nameRange(), "成员函数与数据成员名称冲突：" + name);
                return null;
            }
            MiniType returnType = normalizeType(sourceMethod.returnType(), namespace, null, sourceMethod.range());
            List<MiniType> parameterTypes = sourceMethod.parameters().stream()
                    .map(parameter -> normalizeType(parameter.type(), namespace, null, parameter.range())).toList();
            if (!validOperator(sourceMethod, parameterTypes, true)) return null;
            List<MiniType> coreParameters = new ArrayList<>();
            coreParameters.add(methodThisType(owner, member));
            parameterTypes.stream().map(MiniType::unqualified).forEach(coreParameters::add);
            MiniType signature = MiniType.function(returnType.unqualified(), coreParameters, sourceMethod.variadic());
            List<Method> previous = owner.methods.containsKey(name) ? owner.methods.get(name).methods : List.of();
            for (Method method : previous) {
                MiniType.FunctionType earlier = (MiniType.FunctionType) method.function.type;
                MiniType.FunctionType declared = (MiniType.FunctionType) signature;
                if (earlier.parameterTypes().equals(declared.parameterTypes()) && earlier.variadic() == declared.variadic()) {
                    report("CPP004", member.nameRange(), "类内成员函数重复声明或返回类型冲突：" + name);
                    return null;
                }
            }
            Entity function = new Entity(name, freshName(owner.canonicalName.substring(2) + "::" + name),
                    Kind.FUNCTION, namespace, signature, null, sourceMethod.hasBody());
            coreValues.put(function.coreName, function);
            Method method = new Method(owner, member, access, function, returnType, parameterTypes);
            List<Method> methods = new ArrayList<>(previous);
            methods.add(method);
            owner.methods.put(name, new MethodSet(methods));
            return method;
        }

        private void bindMethod(Method method, Namespace namespace) {
            FunctionDecl original = method.source.method();
            requireSupportedCallLifetime(method.returnType, method.parameterTypes, original.range());
            Local scope = new Local(null, namespace);
            Entity self = new Entity("this", freshName("this"), Kind.VARIABLE, null,
                    methodThisType(method.owner, method.source), null, true);
            coreValues.put(self.coreName, self);
            List<Parameter> parameters = new ArrayList<>();
            parameters.add(new Parameter(self.coreName, self.type, method.source.nameRange()));
            for (int index = 0; index < original.parameters().size(); index++) {
                Parameter parameter = original.parameters().get(index);
                MiniType type = method.parameterTypes.get(index);
                Entity value = declareLocal(parameter.name(), type, scope, parameter.range());
                parameters.add(mapped(parameter, new Parameter(value.coreName, coreType(type), parameter.range())));
            }
            TypeEntity savedClass = currentClass;
            Entity savedThis = currentThis;
            MiniType savedReturnType = currentReturnType;
            currentClass = method.owner;
            currentThis = self;
            currentReturnType = method.returnType;
            try {
                if (original.hasBody()) {
                    requireComplete(method.returnType, original.range());
                    method.parameterTypes.forEach(type -> requireComplete(type, original.range()));
                }
                BlockStmt body = original.body() == null ? null : block(original.body(), scope, false);
                FunctionDecl core = mapped(original, new FunctionDecl(method.function.coreName, coreType(method.returnType),
                        parameters, original.variadic(), body, false, original.noReturn(), original.range()));
                functions.add(core); declarations.add(core);
            } finally {
                currentClass = savedClass;
                currentThis = savedThis;
                currentReturnType = savedReturnType;
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
            if (!definition.hasBody()) {
                report("CPP004", node.nameRange(), "类外成员声明必须提供函数定义：" + spelling(path));
                return;
            }
            MethodMember member = new MethodMember(definition, node.constQualified(), node.nameRange());
            MiniType returnType = normalizeType(definition.returnType(), namespace, null, definition.range());
            List<MiniType> parameterTypes = definition.parameters().stream()
                    .map(p -> normalizeType(p.type(), owner.owner, null, p.range())).toList();
            List<MiniType> coreParameters = new ArrayList<>();
            coreParameters.add(methodThisType(owner, member));
            parameterTypes.stream().map(MiniType::unqualified).forEach(coreParameters::add);
            MiniType signature = MiniType.function(returnType.unqualified(), coreParameters, definition.variadic());
            Method previous = overloads.methods.stream().filter(method -> method.function.type.equals(signature)
                    && method.returnType.equals(returnType)).findFirst().orElse(null);
            if (previous == null) {
                report("CPP004", node.nameRange(), "类外定义与成员函数声明的签名不匹配：" + spelling(path));
                return;
            }
            if (previous.function.defined) {
                report("CPP004", node.nameRange(), "成员函数重复定义：" + spelling(path));
                return;
            }
            previous.function.defined = true;
            bindMethod(new Method(owner, member, previous.access, previous.function, returnType, parameterTypes), owner.owner);
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

        /** Frontend canonical names are source identities, never linker/layout identities. */
        private MiniType normalizeType(MiniType type, Namespace namespace, Local local, SourceRange range) {
            if (type == null) return null;
            if (type instanceof MiniType.ReferenceType reference) {
                MiniType referent = normalizeType(reference.referent(), namespace, local, range);
                if (referent.isVoid()) report("CPP004", range, "引用不能指向 void。");
                return referent.referenceTo();
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
            TypeEntity tag = namespace.tags.get(name);
            if (tag != null) return Set.of(tag);
            TypeEntity imported = namespace.typedefs.get(name);
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
            type = type.unqualified();
            if (type instanceof MiniType.ArrayType array) requireComplete(array.elementType(), range);
            else if (type instanceof MiniType.StructType struct) {
                TypeEntity entity = coreTypes.get(struct.name());
                if (entity != null && !entity.complete) report("CPP005", range, "此位置需要完整对象类型，但类型仍不完整：" + entity.canonicalName);
            }
        }

        private String simpleTagName(String name) {
            if (name.startsWith("$union$")) name = name.substring("$union$".length());
            int separator = name.lastIndexOf("::");
            return separator < 0 ? name : name.substring(separator + 2);
        }

        private void bindGlobal(GlobalVarDecl node, Namespace namespace) {
            if (namespace != root && node.external()) report("CPP005", node.range(),
                    "尚未支持命名空间中的外部对象链接：" + namespace.qualify(node.name()));
            MiniType type = normalizeType(node.type(), namespace, null, node.range());
            if (type.isReference()) report("CPP005", node.range(), "全局引用的静态地址初始化尚未实现。");
            if ((!node.external() || node.initializer() != null) && needsDestruction(type)) {
                report("CPP005", node.range(), "Global object destruction requires static lifetime support.");
            }
            if ((!node.external() || node.initializer() != null) && needsConstructedType(type)) {
                report("CPP005", node.range(), "Global object construction requires dynamic initialization support.");
            }
            if (!node.external() || node.initializer() != null) requireComplete(type, node.range());
            Entity entity = declareNamespaceValue(node.name(), Kind.VARIABLE, type,
                    !node.external() || node.initializer() != null, namespace, node.range());
            Expression initializer = node.external() && node.initializer() == null ? null
                    : initializer(type, node.initializer(), namespace, null, node.range());
            if (initializer != null && !constantInitializer(initializer)) {
                report("CPP005", node.initializer().range(), "尚未支持动态或地址形式的全局初始化；此阶段仅支持可直接写入数据段的常量初始化。");
            }
            GlobalVarDecl core = mapped(node, new GlobalVarDecl(entity.coreName, coreType(type), initializer,
                    node.external(), normalizeAlignments(node.alignmentSpecs(), namespace, null), node.range()));
            globals.add(core); declarations.add(core);
        }

        private boolean unsupportedOperator(FunctionDecl node) {
            if (node.conversionName() != null) {
                report("CPP005", node.conversionName().range(), "转换函数声明已解析；转换选择和执行语义尚未实现。");
                return true;
            }
            if (node.operatorName() == null) return false;
            if (switch (node.operatorName().kind()) {
                case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, BIT_XOR, BIT_AND, BIT_OR,
                        BIT_NOT, LOGICAL_NOT, LESS, GREATER, SHIFT_LEFT, SHIFT_RIGHT, EQUAL, NOT_EQUAL,
                        LESS_EQUAL, GREATER_EQUAL, INCREMENT, DECREMENT, CALL, SUBSCRIPT -> true;
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
                case SUBSCRIPT -> member && count == 2;
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

        private void bindFunction(FunctionDecl node, Namespace namespace) {
            if (unsupportedOperator(node)) return;
            if (namespace != root && node.external()) report("CPP005", node.range(),
                    "尚未支持命名空间中的外部函数链接：" + namespace.qualify(node.name()));
            MiniType returnType = normalizeType(node.returnType(), namespace, null, node.range());
            List<MiniType> parameterTypes = node.parameters().stream()
                    .map(p -> normalizeType(p.type(), namespace, null, p.range())).toList();
            if (!validOperator(node, parameterTypes, false)) return;
            if (node.hasBody()) {
                requireComplete(returnType, node.range());
                for (int i = 0; i < parameterTypes.size(); i++) requireComplete(parameterTypes.get(i), node.parameters().get(i).range());
            }
            requireSupportedCallLifetime(returnType, parameterTypes, node.range());
            MiniType.FunctionType signature = (MiniType.FunctionType) MiniType.function(returnType, parameterTypes.stream()
                    .map(MiniType::unqualified).toList(), node.variadic());
            Entity entity = declareNamespaceFunction(node.name(), signature, node.hasBody(), namespace, node.range(), node.operatorName() != null);
            Local scope = new Local(null, namespace);
            List<Parameter> parameters = new ArrayList<>();
            for (int i = 0; i < node.parameters().size(); i++) {
                Parameter parameter = node.parameters().get(i);
                MiniType type = parameterTypes.get(i);
                Entity value = declareLocal(parameter.name(), type, scope, parameter.range());
                parameters.add(mapped(parameter, new Parameter(value.coreName, coreType(type), parameter.range())));
            }
            MiniType savedReturnType = currentReturnType;
            currentReturnType = returnType;
            try {
                BlockStmt body = node.body() == null ? null : block(node.body(), scope, false);
                FunctionDecl core = mapped(node, new FunctionDecl(entity.coreName, coreType(returnType), parameters,
                        node.variadic(), body, node.external(), node.noReturn(), node.range()));
                functions.add(core); declarations.add(core);
            } finally { currentReturnType = savedReturnType; }
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
                MiniType.FunctionType signature = (MiniType.FunctionType) function.type;
                if (!signature.parameterTypes().equals(type.parameterTypes()) || signature.variadic() != type.variadic()) continue;
                if (function.owner != namespace) report("CPP004", range, "函数声明与 using 引入的函数冲突：" + name);
                if (!signature.returnType().equals(type.returnType())) report("CPP004", range, "函数重声明的返回类型不一致：" + name);
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
                    requireComplete(type, n.range());
                    Entity value = declareLocal(n.name(), type, scope, n.range());
                    Expression initialized = variableInitializer(type, n.cppInitializer(), n.initializer(), namespace, scope, n.range());
                    if (type.isReference() && initialized != null) {
                        initialized = extendTemporaryLifetime(initialized,
                                new TemporaryLifetime(TemporaryLifetime.Kind.REFERENCE_SCOPE, n));
                    }
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
                    // Accessibility is required even when guaranteed copy elision leaves the
                    // returned object's eventual destruction to the caller.
                    if (currentReturnType != null && currentReturnType.isStruct()) destructorForUse(currentReturnType, n.range());
                    yield new ReturnStmt(fullExpression(currentReturnType != null && currentReturnType.isReference()
                            ? bindReference(currentReturnType, n.expression(), namespace, scope, n.range())
                            : copyInitialize(currentReturnType, expressionForTarget(currentReturnType, n.expression(), namespace, scope),
                                    n.range(), CppInitializer.Kind.COPY),
                            currentReturnType != null && currentReturnType.isStruct(), n), n.range());
                }
                case IfStmt n -> new IfStmt(fullExpression(expression(n.condition(), namespace, scope), false, n),
                        body(n.thenBranch(), scope), body(n.elseBranch(), scope), n.range());
                case WhileStmt n -> new WhileStmt(fullExpression(expression(n.condition(), namespace, scope), false, n), body(n.body(), scope), n.range());
                case DoWhileStmt n -> new DoWhileStmt(body(n.body(), scope), fullExpression(expression(n.condition(), namespace, scope), false, n), n.range());
                case ForStmt n -> {
                    Local loop = new Local(scope, namespace);
                    Statement initializer = statement(n.initializer(), loop);
                    Expression condition = fullExpression(expression(n.condition(), namespace, loop), false, n);
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
                    Expression selector = fullExpression(expression(n.selector(), namespace, scope), false, n);
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

        private Expression fullExpression(Expression value, boolean resultOwned, AstNode owner) {
            var result = lowerLifetime(value, resultOwned, owner);
            if (!result.declarations().isEmpty()) report("CPP005", owner.range(),
                    "This reference temporary cannot be extended outside a local declaration.");
            return result.expression();
        }

        private CppLifetimeLowering.Result lowerLifetime(Expression value, boolean resultOwned, AstNode owner) {
            return new CppLifetimeLowering(new CppLifetimeLowering.Context() {
                public MiniType type(Expression expression) { return declaredExpressionType(expression); }
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
                case CppConstructionExpr n -> constructionExpression(n, namespace, local);
                case CppDestructorCallExpr n -> explicitDestruction(n, namespace, local);
                case ThisExpr n -> {
                    if (currentThis == null) {
                        report("CPP004", n.range(), "this 只能用于非静态成员函数体内。");
                        yield n;
                    }
                    yield thisValue(n.range());
                }
                case NameExpr n -> simpleReference(n.name(), n.range(), namespace, local);
                case QualifiedNameExpr n -> reference(n.name().segments().getLast(), n.range(),
                        resolveQualified(n.name(), namespace, local));
                case AssignmentExpr n -> {
                    Expression target = expression(n.target(), namespace, local, true);
                    MiniType targetType = declaredExpressionType(target);
                    if (valueCategory(target) != CppValueCategory.LVALUE && (targetType == null || !targetType.isStruct())) {
                        report("CPP004", n.target().range(), "内置赋值要求可修改的左值，临时对象的标量子对象不是左值。");
                    }
                    requireComplete(targetType, n.range());
                    if (objectType(targetType) != null && (hasConstSubobject(targetType, new HashSet<>())
                            || hasReferenceSubobject(targetType, new HashSet<>()))) {
                        report("CPP005", n.range(), "尚未支持含 const 子对象的整体赋值所需的特殊成员函数规则。");
                    }
                    if (n.operator() == TokenType.PLUS_EQUAL || n.operator() == TokenType.MINUS_EQUAL) {
                        requireComplete(elementType(declaredExpressionType(target)), n.range());
                    }
                    Expression value = n.compoundBinaryOperator().isEmpty()
                            ? expressionForTarget(targetType, n.value(), namespace, local) : expression(n.value(), namespace, local);
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
                    Expression condition = expression(n.condition(), namespace, local);
                    Expression first = expression(n.thenExpression(), namespace, local, addressDemand);
                    Expression second = expression(n.elseExpression(), namespace, local, addressDemand);
                    MiniType firstType = declaredExpressionType(first), secondType = declaredExpressionType(second);
                    MiniType common = conditionalLvalueType(firstType, secondType);
                    boolean lvalues = valueCategory(first) == CppValueCategory.LVALUE && valueCategory(second) == CppValueCategory.LVALUE;
                    boolean temporarySubobjects = valueCategory(first) == CppValueCategory.PRVALUE
                            && valueCategory(second) == CppValueCategory.PRVALUE && addressableObject(first) && addressableObject(second);
                    if (common != null && (lvalues || temporarySubobjects)) {
                        if (addressDemand || common.isArray() || common.isFunction() || common.isStruct()) {
                            // Aggregate values are represented by addresses. In this uncommon
                            // path rebind compound lvalue arms for addresses, never evaluating them.
                            if (!addressableObject(first)) first = expression(n.thenExpression(), namespace, local, true);
                            if (!addressableObject(second)) second = expression(n.elseExpression(), namespace, local, true);
                            if (addressableObject(first) && addressableObject(second)) {
                                Expression selected = new ConditionalExpr(condition, qualifiedAddress(first, common),
                                        qualifiedAddress(second, common), n.range());
                                Expression object = typed(new UnaryExpr(TokenType.STAR, selected, n.range()), common);
                                temporaryAddressPaths.add(object);
                                valueCategories.put(object, lvalues ? CppValueCategory.LVALUE : CppValueCategory.PRVALUE);
                                yield object;
                            }
                        } else {
                            // Value use of assignment/update must reuse its result, especially
                            // for volatile objects. Keep the narrow glvalue type for sizeof too.
                            Expression selected = new ConditionalExpr(condition, first, second, n.range());
                            Expression value = typed(new CastExpr(coreType(common), selected, n.range()), common);
                            valueCategories.put(value, lvalues ? CppValueCategory.LVALUE : CppValueCategory.PRVALUE);
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
                case CallExpr n -> {
                    BoundCallee binding = bindCallee(n.callee(), n.arguments(), namespace, local);
                    Expression callee = binding.expression();
                    if (objectType(declaredExpressionType(callee)) != null) {
                        List<Expression> sources = new ArrayList<>(); sources.add(n.callee()); sources.addAll(n.arguments());
                        List<Expression> values = new ArrayList<>(); values.add(callee);
                        values.addAll(expressions(n.arguments(), namespace, local));
                        yield operatorExpression("operator()", n, sources, values, namespace, local, true);
                    }
                    MiniType.FunctionType signature = functionSignature(declaredExpressionType(callee));
                    if (signature != null) {
                        requireComplete(signature.returnType(), n.range());
                        signature.parameterTypes().forEach(t -> requireComplete(t, n.range()));
                        requireSupportedCallLifetime(signature.returnType(), signature.parameterTypes(), n.range());
                        destructorForUse(signature.returnType(), n.range());
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
                    CallExpr call = new CallExpr(callee, arguments, n.range());
                    if (signature != null) declaredExpressionTypes.put(call, signature.returnType().isReference()
                            ? coreType(signature.returnType()) : signature.returnType());
                    if (signature != null && signature.returnType().isReference()) {
                        UnaryExpr object = new UnaryExpr(TokenType.STAR, call, n.range());
                        declaredExpressionTypes.put(object, signature.returnType().referent());
                        yield object;
                    }
                    yield signature != null && signature.returnType().isStruct()
                            ? recordPrvalue(signature.returnType(), call, n.range()) : call;
                }
                case CastExpr n -> {
                    MiniType type = normalizeType(n.targetType(), namespace, local, n.range());
                    if (type.isReference()) report("CPP005", n.range(), "引用类型显式转换的值类别规则尚未实现。");
                    Expression operand = expressionForTarget(type, n.operand(), namespace, local);
                    requireExplicitConversion(type, operand, n.range());
                    CastExpr cast = new CastExpr(coreType(type), operand, n.range());
                    declaredExpressionTypes.put(cast, type);
                    yield cast;
                }
                case CommaExpr n -> {
                    List<Expression> values = new ArrayList<>();
                    for (int index = 0; index < n.expressions().size(); index++) {
                        values.add(expression(n.expressions().get(index), namespace, local,
                                index == n.expressions().size() - 1 && addressDemand));
                    }
                    Expression last = values.getLast();
                    MiniType lastType = declaredExpressionType(last);
                    if (addressableObject(last) && (addressDemand || lastType != null && lastType.isFunction())) {
                        values.set(values.size() - 1, address(last));
                        Expression object = typed(new UnaryExpr(TokenType.STAR, new CommaExpr(values, n.range()), n.range()),
                                declaredExpressionType(last));
                        temporaryAddressPaths.add(object);
                        valueCategories.put(object, valueCategory(last));
                        yield object;
                    }
                    yield new CommaExpr(values, n.range());
                }
                case FieldAccessExpr n -> {
                    Expression target = expression(n.target(), namespace, local, !n.viaPointer());
                    if (!n.viaPointer()) target = materializedReceiver(target);
                    MiniType owner = declaredExpressionType(target);
                    owner = n.viaPointer() ? elementType(owner) : owner;
                    requireComplete(owner, n.range());
                    MethodSet methods = memberMethods(owner, n.fieldName());
                    if (methods != null) {
                        report("CPP005", n.range(), "成员函数只能作为调用目标使用；尚未支持成员函数指针：" + n.fieldName());
                    } else requireAccessible(owner, n.fieldName(), n.range(), "数据成员访问");
                    Expression field = fieldReference(target, n.fieldName(), n.viaPointer(), owner, n.range());
                    MiniType memberType = declaredFieldType(owner, n.fieldName(), new HashSet<>());
                    valueCategories.put(field, n.viaPointer() || memberType != null && memberType.isReference()
                            ? CppValueCategory.LVALUE : valueCategory(target));
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
                    if (overloaded != null) yield overloaded;
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
                case SizeofExpr n -> {
                    MiniType type = objectTypeOfReference(normalizeType(n.queriedType(), namespace, local, n.range()));
                    Expression operand = unevaluatedExpression(n.expression(), namespace, local);
                    requireComplete(type != null ? type : declaredExpressionType(operand), n.range());
                    yield new SizeofExpr(operand, coreType(type), n.range());
                }
                case AlignofExpr n -> {
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
            Candidate candidate = lookupName(name, namespace, local, range);
            if (candidate instanceof ImplicitField field) {
                requireAccessible(currentThis.type.pointee(), name, range, "数据成员访问");
                return fieldReference(thisValue(range), name, true, currentThis.type.pointee(), range);
            }
            if (candidate instanceof MethodSet methods) {
                report("CPP005", range, "成员函数只能作为调用目标使用；尚未支持成员函数指针：" + name);
                return new NameExpr(methods.methods.getFirst().function.coreName, range);
            }
            return reference(name, range, requireValue(candidate, name, range));
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
                default -> null;
            };
            return symbol == null ? null : "operator" + symbol;
        }

        private record OperatorCandidate(Entity function, Method method, List<MiniType> parameters) { }

        /** The operand list is shared by member and free candidates; no expression is evaluated during selection. */
        private Expression operatorExpression(String name, Expression original, List<Expression> sources,
                                              List<Expression> values, Namespace namespace, Local local, boolean memberOnly) {
            if (name == null || values.stream().noneMatch(value -> objectType(declaredExpressionType(value)) != null)) return null;
            List<CppOverloadResolver.Candidate<OperatorCandidate>> candidates = new ArrayList<>();
            MethodSet members = memberMethods(declaredExpressionType(values.getFirst()), name);
            if (members != null) for (Method method : members.methods) {
                var candidate = new OperatorCandidate(method.function, method, method.parameterTypes);
                candidates.add(new CppOverloadResolver.Candidate<>(candidate, method.parameterTypes,
                        method.source.method().variadic(), methodThisType(method.owner, method.source).pointee()));
            }
            if (!memberOnly) for (Entity function : operatorFunctions(name, values, namespace, local)) {
                MiniType.FunctionType type = (MiniType.FunctionType) function.type;
                var candidate = new OperatorCandidate(function, null, type.parameterTypes());
                candidates.add(new CppOverloadResolver.Candidate<>(candidate, type.parameterTypes(), type.variadic()));
            }
            List<CppOverloadResolver.Argument> arguments = new ArrayList<>();
            for (int index = 0; index < values.size(); index++) {
                Expression value = values.get(index);
                MiniType type = declaredExpressionType(value);
                if (type == null) {
                    report("CPP004", sources.get(index).range(), "无法确定运算符实参类型。");
                    return new IntegerLiteralExpr(0, "0", original.range());
                }
                arguments.add(new CppOverloadResolver.Argument(type, valueCategory(value), isNullIntegerLiteral(sources.get(index))));
            }
            var resolution = CppOverloadResolver.resolveOperators(candidates, arguments);
            if (resolution.status() != CppOverloadResolver.Status.SELECTED) {
                // C++ unary & falls back to builtin address-of only when no candidate is viable.
                if (name.equals("operator&") && values.size() == 1
                        && resolution.status() == CppOverloadResolver.Status.NO_VIABLE) return null;
                report("CPP004", original.range(), resolution.status() == CppOverloadResolver.Status.AMBIGUOUS
                        ? "运算符重载具有二义性：" + name : "没有匹配的运算符重载：" + name);
                return new IntegerLiteralExpr(0, "0", original.range());
            }
            OperatorCandidate selected = resolution.winner().identity();
            List<Expression> lowered = new ArrayList<>();
            int offset = selected.method == null ? 0 : 1;
            if (selected.method != null) {
                requireMethodAccess(selected.method, original.range());
                Expression receiver = materializedReceiver(values.getFirst());
                if (!addressableObject(receiver)) {
                    report("CPP005", sources.getFirst().range(), "尚未支持此运算符接收者值类别。");
                    return new IntegerLiteralExpr(0, "0", original.range());
                }
                lowered.add(address(receiver));
            }
            lowered.addAll(lowerSelectedArguments(selected.parameters, sources.subList(offset, sources.size()),
                    values.subList(offset, values.size()), namespace, local));
            MiniType.FunctionType signature = (MiniType.FunctionType) selected.function.type;
            requireComplete(signature.returnType(), original.range());
            signature.parameterTypes().forEach(type -> requireComplete(type, original.range()));
            requireSupportedCallLifetime(signature.returnType(), signature.parameterTypes(), original.range());
            CallExpr call = new CallExpr(new NameExpr(selected.function.coreName, original.range()), lowered, original.range());
            declaredExpressionTypes.put(call, signature.returnType().isReference()
                    ? coreType(signature.returnType()) : signature.returnType());
            if (signature.returnType().isReference())
                return typed(new UnaryExpr(TokenType.STAR, call, original.range()), signature.returnType().referent());
            return signature.returnType().isStruct() ? recordPrvalue(signature.returnType(), call, original.range()) : call;
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
                    if (owner != null) namespaces.add(owner.owner);
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

        private record OverloadDesignator(Expression source, Expression name, OverloadSet set, boolean addressOf) { }

        private OverloadDesignator overloadDesignator(Expression source, Namespace namespace, Local local) {
            Expression name = source;
            while (name instanceof GroupingExpr group) name = group.expression();
            boolean addressOf = name instanceof UnaryExpr unary && unary.operator() == TokenType.AMPERSAND;
            if (addressOf) name = ((UnaryExpr) name).operand();
            while (name instanceof GroupingExpr group) name = group.expression();
            int beforeLookup = diagnostics.size();
            Candidate candidate = name instanceof NameExpr simple ? lookupName(simple.name(), namespace, local, name.range())
                    : name instanceof QualifiedNameExpr qualified ? resolveQualifiedName(qualified.name(), namespace, local) : null;
            // This is a contextual probe. Ordinary expression binding owns diagnostics when the
            // expression does not denote an overloaded free function.
            if (!(candidate instanceof OverloadSet set) || set.functions.size() < 2) {
                diagnostics.subList(beforeLookup, diagnostics.size()).clear();
                return null;
            }
            return new OverloadDesignator(source, name, set, addressOf);
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
            for (Entity candidate : designator.set.functions) {
                if (!candidate.type.equals(signature)) continue;
                if (selected != null) return null;
                selected = candidate;
            }
            return selected;
        }

        private Expression expressionForTarget(MiniType target, Expression source, Namespace namespace, Local local) {
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
                    target.isReference() && target.referent().isFunction());
        }

        private Expression rebuildFunctionDesignator(Expression original, Expression name, Entity selected, boolean functionReference) {
            Expression core;
            if (original == name) core = new NameExpr(selected.coreName, original.range());
            else if (original instanceof GroupingExpr group) core = new GroupingExpr(
                    rebuildFunctionDesignator(group.expression(), name, selected, functionReference), original.range());
            else if (original instanceof UnaryExpr unary && unary.operator() == TokenType.AMPERSAND) {
                Expression operand = rebuildFunctionDesignator(unary.operand(), name, selected, functionReference);
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
            MethodSet methods = null;
            Expression receiver = null;
            String sourceName = designator instanceof NameExpr name ? name.name()
                    : designator instanceof IntegerConstantExpr constant
                    && constant.lexeme().matches("[A-Za-z_][A-Za-z0-9_]*") ? constant.lexeme() : null;
            String fallbackName = designator instanceof QualifiedNameExpr qualified ? spelling(qualified.name()) : sourceName;
            if (sourceName != null || designator instanceof QualifiedNameExpr) {
                Candidate candidate = designator instanceof QualifiedNameExpr qualified
                        ? resolveQualifiedName(qualified.name(), namespace, local)
                        : lookupName(sourceName, namespace, local, designator.range());
                if (candidate instanceof TypeEntity type && type.classType) {
                    report("CPP005", designator.range(), "Functional construction expressions are not supported in this slice.");
                    return new BoundCallee(new NameExpr(fallbackName, designator.range()), null);
                }
                if (candidate instanceof OverloadSet set && set.functions.size() > 1) {
                    return bindOverloadedCall(set, sourceCallee, designator, sourceArguments, namespace, local);
                }
                if (candidate instanceof MethodSet members) {
                    methods = members;
                    receiver = thisValue(designator.range());
                } else {
                    Expression core;
                    if (candidate instanceof ImplicitField field) {
                        requireAccessible(currentThis.type.pointee(), field.name, designator.range(), "数据成员访问");
                        core = fieldReference(thisValue(designator.range()), field.name, true, currentThis.type.pointee(), designator.range());
                    } else core = reference(fallbackName, designator.range(), requireValue(candidate, fallbackName, designator.range()));
                    return new BoundCallee(rebuildCalleeGroups(sourceCallee, designator, core), null);
                }
            } else if (designator instanceof FieldAccessExpr field) {
                Expression target = expression(field.target(), namespace, local, !field.viaPointer());
                if (!field.viaPointer()) target = materializedReceiver(target);
                MiniType owner = declaredExpressionType(target);
                owner = field.viaPointer() ? elementType(owner) : owner;
                requireComplete(owner, field.range());
                methods = memberMethods(owner, field.fieldName());
                if (methods == null) {
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
            if (methods.methods.size() > 1) return bindOverloadedMethod(methods, sourceCallee, receiver, sourceArguments, namespace, local);
            Method method = methods.methods.getFirst();
            requireMethodAccess(method, sourceCallee.range());
            MiniType object = elementType(declaredExpressionType(receiver));
            if (object != null && (object.isVolatileQualified()
                    || object.isConstQualified() && !method.source.constQualified())) {
                report("CPP004", sourceCallee.range(), "成员函数限定符与接收者不匹配，不能丢弃 const/volatile："
                        + method.source.method().name());
            }
            // The member designator and its parentheses are compile-time lookup syntax. Only
            // the outer callee has an executable counterpart, keeping reverse origins unique.
            Expression core = mapped(sourceCallee, new NameExpr(method.function.coreName, sourceCallee.range()));
            return new BoundCallee(core, receiver);
        }

        private BoundCallee bindOverloadedCall(OverloadSet set, Expression sourceCallee, Expression designator,
                                              List<Expression> sourceArguments, Namespace namespace, Local local) {
            // Provisional expressions only provide types/categories. Exactly one selected ABI
            // argument list is emitted; reference arguments are rebound with address demand.
            PreparedArguments prepared = prepareArguments(sourceArguments, namespace, local);
            List<Expression> values = prepared.values;
            List<CppOverloadResolver.Candidate<Entity>> candidates = set.functions.stream().map(function -> {
                MiniType.FunctionType type = (MiniType.FunctionType) function.type;
                return new CppOverloadResolver.Candidate<>(function, type.parameterTypes(), type.variadic());
            }).toList();
            Entity selected = selectOverload(candidates, sourceCallee, sourceArguments, prepared, null);
            if (selected == null) return new BoundCallee(new NameExpr(set.functions.getFirst().coreName, sourceCallee.range()), null, recoveryArguments(sourceArguments, values));
            return new BoundCallee(rebuildCalleeGroups(sourceCallee, designator,
                    new NameExpr(selected.coreName, designator.range())), null,
                    lowerSelectedArguments(((MiniType.FunctionType) selected.type).parameterTypes(), sourceArguments, values, namespace, local));
        }

        private BoundCallee bindOverloadedMethod(MethodSet set, Expression sourceCallee, Expression receiver,
                                                List<Expression> sourceArguments, Namespace namespace, Local local) {
            PreparedArguments prepared = prepareArguments(sourceArguments, namespace, local);
            List<Expression> values = prepared.values;
            List<CppOverloadResolver.Candidate<Method>> candidates = set.methods.stream().map(method ->
                    new CppOverloadResolver.Candidate<>(method, method.parameterTypes, method.source.method().variadic(),
                            methodThisType(method.owner, method.source).pointee())).toList();
            MiniType object = elementType(declaredExpressionType(receiver));
            if (object == null) {
                report("CPP004", sourceCallee.range(), "无法确定成员函数接收者的类型。");
                return new BoundCallee(new NameExpr(set.methods.getFirst().function.coreName, sourceCallee.range()), receiver, recoveryArguments(sourceArguments, values));
            }
            Method selected = selectOverload(candidates, sourceCallee, sourceArguments, prepared,
                    new CppOverloadResolver.Argument(object, CppValueCategory.LVALUE, false));
            if (selected == null) return new BoundCallee(new NameExpr(set.methods.getFirst().function.coreName, sourceCallee.range()), receiver, recoveryArguments(sourceArguments, values));
            // Access is checked only after selection. An inaccessible best match does not
            // allow falling back to a public candidate with worse conversions.
            requireMethodAccess(selected, sourceCallee.range());
            return new BoundCallee(mapped(sourceCallee, new NameExpr(selected.function.coreName, sourceCallee.range())), receiver,
                    lowerSelectedArguments(selected.parameterTypes, sourceArguments, values, namespace, local));
        }

        private record PreparedArguments(List<Expression> values, Map<Integer, OverloadDesignator> overloads) { }

        private PreparedArguments prepareArguments(List<Expression> source, Namespace namespace, Local local) {
            List<Expression> values = new ArrayList<>();
            Map<Integer, OverloadDesignator> overloads = new LinkedHashMap<>();
            for (int index = 0; index < source.size(); index++) {
                OverloadDesignator designator = overloadDesignator(source.get(index), namespace, local);
                if (designator != null) overloads.put(index, designator);
                values.add(designator == null ? expression(source.get(index), namespace, local) : null);
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
                MiniType type = declaredExpressionType(value);
                if (type == null) {
                    report("CPP004", sourceArguments.get(index).range(), "无法确定重载实参的类型。");
                    return null;
                }
                arguments.add(new CppOverloadResolver.Argument(type, valueCategory(value), isNullIntegerLiteral(sourceArguments.get(index))));
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
                        candidate.variadic(), candidate.implicitObjectType()));
            }
            var resolution = CppOverloadResolver.resolve(contextual, arguments, receiver);
            if (resolution.status() == CppOverloadResolver.Status.SELECTED) return resolution.winner().identity();
            report("CPP004", sourceCallee.range(), resolution.status() == CppOverloadResolver.Status.AMBIGUOUS
                    ? "重载函数调用具有二义性。" : "没有与实参匹配的重载函数。");
            return null;
        }

        private List<Expression> lowerSelectedArguments(List<MiniType> parameters, List<Expression> sourceArguments,
                                                        List<Expression> values, Namespace namespace, Local local) {
            List<Expression> lowered = new ArrayList<>(values);
            for (int index = 0; index < sourceArguments.size() && index < parameters.size(); index++) {
                destructorForUse(parameters.get(index), sourceArguments.get(index).range());
                if (parameters.get(index).isReference()) lowered.set(index, bindReference(parameters.get(index),
                        sourceArguments.get(index), namespace, local, sourceArguments.get(index).range()));
                else {
                    Expression value = values.get(index) == null
                            ? expressionForTarget(parameters.get(index), sourceArguments.get(index), namespace, local) : values.get(index);
                    lowered.set(index, convertCallValue(parameters.get(index), value, sourceArguments.get(index)));
                }
            }
            return List.copyOf(lowered);
        }

        /** Single candidates and indirect calls obey the same C++ conversions as overload sets. */
        private Expression convertCallValue(MiniType parameter, Expression value, Expression source) {
            if (parameter == null || value == null) return value; // ellipsis/arity remains a core check
            MiniType actual = declaredExpressionType(value);
            if (actual == null) return value; // unsupported initializer forms retain their existing diagnostics
            var argument = new CppOverloadResolver.Argument(actual, valueCategory(value), isNullIntegerLiteral(source));
            if (CppOverloadResolver.resolve(List.of(new CppOverloadResolver.Candidate<>("argument", List.of(parameter), false)),
                    List.of(argument)).status() != CppOverloadResolver.Status.SELECTED) {
                report("CPP004", source.range(), "实参不能按 C++ 标准转换为参数类型。");
                return value;
            }
            MiniType target = TypeCompatibility.decay(parameter).unqualified();
            MiniType from = TypeCompatibility.decay(actual).unqualified();
            if (target.isStruct() && target.equals(from)) return copyInitialize(target, value, source.range(), CppInitializer.Kind.COPY);
            if (!target.equals(from) && (target.isPointer() || target.equals(MiniType.BOOL) && from.isPointer())) {
                // Core C is narrower for pointer-to-bool and deep qualification conversions.
                // This cast spells only a conversion already approved above.
                return typed(new CastExpr(coreType(target), value, source.range()), target);
            }
            return value;
        }

        private Expression rebuildCalleeGroups(Expression original, Expression designator, Expression core) {
            if (original == designator) return mapped(original, core);
            GroupingExpr group = (GroupingExpr) original;
            return mapped(original, new GroupingExpr(rebuildCalleeGroups(group.expression(), designator, core), original.range()));
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
            return type == null ? null : type.methods.get(name);
        }

        private void requireMethodAccess(Method method, SourceRange range) {
            if (method.access != Access.PUBLIC && currentClass != method.owner) {
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
                    if (step.access() != Access.PUBLIC && currentClass != step.owner()) {
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
            return type != null && (type.constructors.stream().anyMatch(c -> !c.implicit)
                    || type.fields.stream().anyMatch(f -> type.fieldAccess.getOrDefault(f, Access.PUBLIC) != Access.PUBLIC));
        }

        private Expression fieldReference(Expression receiver, String name, boolean arrow, MiniType owner, SourceRange range) {
            Expression field = new FieldAccessExpr(receiver, name, arrow, range);
            MiniType type = declaredFieldType(owner, name, new HashSet<>());
            if (type == null || !type.isReference()) return field;
            typed(field, coreType(type));
            return typed(new UnaryExpr(TokenType.STAR, field, range), type.referent());
        }

        private Expression variableInitializer(MiniType target, CppInitializer syntax, Expression legacy,
                                               Namespace namespace, Local local, SourceRange range) {
            if (syntax == null) return initializer(target, legacy, namespace, local, range);
            List<Expression> arguments = syntax.arguments();
            boolean list = syntax.kind() == CppInitializer.Kind.DIRECT_LIST || syntax.kind() == CppInitializer.Kind.COPY_LIST;
            if (target.isReference()) {
                Expression argument = legacy != null && !(legacy instanceof CppInitializer) ? legacy
                        : arguments.size() == 1 ? arguments.getFirst() : null;
                if (list && !(argument instanceof AggregateInitExpr)) argument = new AggregateInitExpr(arguments, syntax.range());
                return bindReference(target, argument, namespace, local, range);
            }
            TypeEntity object = objectType(target);
            if (object != null && needsConstruction(object)) {
                Expression value = constructObject(target, syntax, namespace, local, range);
                return initializerMapping(syntax, value);
            }
            if (object != null || target.isArray()) {
                if (target.isArray() && needsConstructedType(target)) {
                    report("CPP005", range, "Array element construction is not supported in this slice.");
                    return null;
                }
                if (syntax.kind() == CppInitializer.Kind.DIRECT_PAREN) {
                    if (object != null && arguments.size() == 1) {
                        Expression value = expression(arguments.getFirst(), namespace, local);
                        MiniType actual = declaredExpressionType(value);
                        if (actual != null && actual.unqualified().equals(target.unqualified()))
                            return initializerMapping(syntax, copyInitialize(target, value, syntax.range(), syntax.kind()));
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
            if (list) requireNonNarrowing(target, value, arguments.getFirst().range());
            return initializerMapping(syntax, convertCallValue(target.unqualified(), value, arguments.getFirst()));
        }

        private Expression constructionExpression(CppConstructionExpr source, Namespace namespace, Local local) {
            MiniType type = normalizeType(source.type(), namespace, local, source.typeRange());
            requireComplete(type, source.typeRange());
            // Potential destruction is checked even in an unevaluated operand or elided result.
            destructorForUse(type, source.range());
            CppInitializer syntax = source.initializer();
            List<Expression> arguments = syntax.arguments();
            if (type.isVoid() && syntax.kind() == CppInitializer.Kind.DIRECT_LIST) {
                report("CPP004", source.range(), "Braced construction requires an object type; void has no object.");
                return new CastExpr(MiniType.VOID, new IntegerLiteralExpr(0, "0", source.range()), source.range());
            }
            if (type.isReference() || type.isArray() || type.isFunction()) {
                report("CPP005", source.typeRange(), "Functional construction of reference, array or function types is not supported yet.");
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
            requireExplicitConversion(type, value, source.range());
            return typed(new CastExpr(coreType(type.unqualified()), value, source.range()), type.unqualified());
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

        /** Copy construction is selected from source signatures before reference erasure. */
        private boolean isCopyConstructor(Constructor constructor) {
            return CppCopyConstructorPlan.classify(constructor.owner.type, constructor.parameterTypes)
                    == CppCopyConstructorPlan.Classification.COPY;
        }

        private List<Constructor> copyConstructors(TypeEntity owner) {
            List<Constructor> declared = owner.constructors.stream().filter(this::isCopyConstructor).toList();
            if (!declared.isEmpty()) return declared;
            ensureImplicitCopy(owner);
            return owner.implicitCopy == null ? List.of() : List.of(owner.implicitCopy);
        }

        private void ensureImplicitCopy(TypeEntity owner) {
            if (owner.copyPlan != null || !owner.complete) return;
            boolean declared = owner.constructors.stream().anyMatch(this::isCopyConstructor);
            owner.copyPlan = CppCopyConstructorPlan.plan(owner.type, owner.fields, owner.union, declared, memberType -> {
                TypeEntity member = objectType(memberType);
                List<CppCopyConstructorPlan.Constructor<Constructor>> candidates = copyConstructors(member).stream()
                        .map(c -> new CppCopyConstructorPlan.Constructor<>(c, c.parameterTypes.getFirst(),
                                c.access == Access.PUBLIC || c.owner == owner, deletedConstructors.containsKey(c.function),
                                c == member.implicitCopy && member.copyPlan != null && member.copyPlan.trivial())).toList();
                CppCopyConstructorPlan.Destructor destructor = member.destructor == null
                        ? CppCopyConstructorPlan.Destructor.AVAILABLE
                        : deletedDestructors.containsKey(member.destructor.function) ? CppCopyConstructorPlan.Destructor.DELETED
                        : member.destructor.access == Access.PUBLIC || member == owner ? CppCopyConstructorPlan.Destructor.AVAILABLE
                        : CppCopyConstructorPlan.Destructor.INACCESSIBLE;
                return new CppCopyConstructorPlan.Operations<>(candidates, destructor);
            });
            if (owner.copyPlan.status() == CppCopyConstructorPlan.Status.SUPPRESSED) return;
            SourceRange range = owner.sourceRecord.range();
            MiniType parameter = owner.copyPlan.parameterType();
            ConstructorMember source = new ConstructorMember(owner.name, List.of(new Parameter("other", parameter, range)),
                    false, List.of(), new BlockStmt(List.of(), range), range, range);
            Entity function = new Entity(owner.name, freshName(owner.canonicalName.substring(2) + "::" + owner.name),
                    Kind.FUNCTION, owner.owner, MiniType.function(MiniType.VOID, List.of(owner.type.pointerTo(), parameter)), null,
                    owner.copyPlan.status() == CppCopyConstructorPlan.Status.AVAILABLE);
            coreValues.put(function.coreName, function);
            owner.implicitCopy = new Constructor(owner, source, Access.PUBLIC, function, List.of(parameter), true);
            if (owner.copyPlan.status() == CppCopyConstructorPlan.Status.DELETED) {
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
            List<CppOverloadResolver.Candidate<Constructor>> candidates = copyConstructors(owner).stream()
                    .filter(c -> kind != CppInitializer.Kind.COPY || !c.source.explicitSpecifier())
                    .map(c -> new CppOverloadResolver.Candidate<>(c, c.parameterTypes, c.source.variadic())).toList();
            var resolution = CppOverloadResolver.resolve(candidates,
                    List.of(new CppOverloadResolver.Argument(actual, category, false)));
            if (resolution.status() != CppOverloadResolver.Status.SELECTED) {
                report("CPP004", range, resolution.status() == CppOverloadResolver.Status.AMBIGUOUS
                        ? "Copy constructor selection is ambiguous." : "No viable copy constructor for this source object.");
                return value;
            }
            Constructor selected = resolution.winner().identity();
            if (kind == CppInitializer.Kind.COPY_LIST && selected.source.explicitSpecifier())
                report("CPP004", range, "Copy-list initialization cannot select an explicit copy constructor.");
            if (selected.access != Access.PUBLIC && currentClass != owner)
                report("CPP004", range, "Copy constructor is not accessible: " + owner.canonicalName);
            if (deletedConstructors.containsKey(selected.function)) {
                report("CPP004", range, "Copy constructor is deleted: " + deletedConstructors.get(selected.function).getFirst().message());
                return value;
            }
            if (selected == owner.implicitCopy && owner.copyPlan.trivial() && !hasVolatileSubobject(owner.type, new HashSet<>())) return value;
            if (selected == owner.implicitCopy) emitImplicitCopy(owner);
            String destination = freshName("copy_destination");
            Expression address = typed(new NameExpr(destination, range), owner.type.pointerTo());
            Expression source = copySourceAddress(value);
            Expression call = typed(new CallExpr(new NameExpr(selected.function.coreName, range), List.of(address, source), range), MiniType.VOID);
            return typed(new ObjectInitExpr(coreType(target), destination, call, range), target);
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
        private void emitImplicitCopy(TypeEntity owner) {
            if (owner.copyEmitted || owner.copyPlan.status() != CppCopyConstructorPlan.Status.AVAILABLE) return;
            Constructor constructor = owner.implicitCopy;
            if (!owner.copyPlan.objectRepresentation() && owner.fields.stream().anyMatch(StructField::anonymous)) {
                report("CPP005", owner.sourceRecord.range(), "Nontrivial copying of anonymous aggregate storage requires subobject initialization support.");
                return;
            }
            if (unevaluatedDepth > 0) {
                if (!owner.copyPrototypeEmitted) {
                    SourceRange range = owner.sourceRecord.range();
                    FunctionDecl declaration = new FunctionDecl(constructor.function.coreName, MiniType.VOID,
                            List.of(new Parameter(freshName("this"), owner.type.pointerTo(), range),
                                    new Parameter(freshName("other"), coreType(owner.copyPlan.parameterType()), range)),
                            false, null, false, range);
                    functions.add(declaration); declarations.add(declaration);
                    owner.copyPrototypeEmitted = true;
                }
                return;
            }
            owner.copyEmitted = true;
            SourceRange range = owner.sourceRecord.range();
            String self = freshName("this"), source = freshName("other");
            MiniType sourcePointer = coreType(owner.copyPlan.parameterType());
            List<Statement> statements = new ArrayList<>();
            if (owner.copyPlan.objectRepresentation()) {
                Expression to = new UnaryExpr(TokenType.STAR, new NameExpr(self, range), range);
                Expression from = new UnaryExpr(TokenType.STAR, new NameExpr(source, range), range);
                statements.add(new ExprStmt(new InitializeExpr(to, from, range), range));
            }
            for (var entry : owner.copyPlan.entries()) {
                Expression destination = typed(new FieldAccessExpr(typed(new NameExpr(self, range), owner.type.pointerTo()),
                        entry.field().name(), true, entry.field().range()), coreType(entry.field().type()));
                MiniType sourceFieldType = entry.field().type().isReference() ? coreType(entry.field().type())
                        : inheritObjectQualifiers(owner.copyPlan.parameterType().referent(), entry.field().type());
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
                if (selected == selected.owner.implicitCopy && selected.owner.copyPlan.trivial()
                        && !hasVolatileSubobject(selected.owner.type, new HashSet<>()))
                    return new ExprStmt(new InitializeExpr(destination, source, range), range);
                if (selected == selected.owner.implicitCopy) emitImplicitCopy(selected.owner);
                Expression call = new CallExpr(new NameExpr(selected.function.coreName, range),
                        List.of(address(destination),address(source)),range);
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
            boolean list = syntax.kind() == CppInitializer.Kind.DIRECT_LIST || syntax.kind() == CppInitializer.Kind.COPY_LIST;
            if (list && !arguments.isEmpty() && !nonAggregate(owner)) {
                report("CPP005", range, "Nonempty aggregate lists with default member initialization require member-wise list binding.");
            }
            PreparedArguments prepared = prepareArguments(arguments, namespace, local);
            if (arguments.size() == 1 && prepared.values.getFirst() != null) {
                Expression value = prepared.values.getFirst();
                MiniType sourceType = declaredExpressionType(value);
                if (sourceType != null && target.unqualified().equals(sourceType.unqualified()))
                    return copyInitialize(target, value, range, syntax.kind());
            }
            List<CppOverloadResolver.Candidate<Constructor>> candidates = owner.constructors.stream()
                    .filter(c -> syntax.kind() != CppInitializer.Kind.COPY || !c.source.explicitSpecifier())
                    .map(c -> new CppOverloadResolver.Candidate<>(c, c.parameterTypes, c.source.variadic())).toList();
            Constructor selected = selectOverload(candidates, new NameExpr(owner.name, range), arguments, prepared, null);
            if (selected == null) return typed(new ObjectInitExpr(coreType(target), freshName("construction"),
                    new CastExpr(MiniType.VOID, new IntegerLiteralExpr(0, "0", range), range), range), target);
            if (list && arguments.isEmpty() && selected.implicit && owner.aggregateInitializer != null) selected = owner.aggregateInitializer;
            if (syntax.kind() == CppInitializer.Kind.COPY_LIST && selected.source.explicitSpecifier()) {
                report("CPP004", range, "Copy-list-initialization cannot select an explicit constructor: " + owner.canonicalName);
            }
            if (selected.access != Access.PUBLIC && currentClass != owner) {
                report("CPP004", range, "Constructor is not accessible: " + owner.canonicalName);
            }
            if (deletedConstructors.containsKey(selected.function)) {
                Diagnostic reason = deletedConstructors.get(selected.function).getFirst();
                report(reason.code().equals("CPP005") ? "CPP005" : "CPP004", range, "The implicit default constructor is unavailable: " + reason.message());
            }
            if (list) for (int index = 0; index < arguments.size() && index < selected.parameterTypes.size(); index++) {
                if (prepared.values.get(index) != null) requireNonNarrowing(selected.parameterTypes.get(index), prepared.values.get(index), arguments.get(index).range());
            }
            List<Expression> lowered = new ArrayList<>();
            String destination = freshName("construction");
            lowered.add(typed(new NameExpr(destination, range), owner.type.pointerTo()));
            lowered.addAll(lowerSelectedArguments(selected.parameterTypes, arguments, prepared.values, namespace, local));
            Expression call = typed(new CallExpr(new NameExpr(selected.function.coreName, range), lowered, range), MiniType.VOID);
            if (list && arguments.isEmpty() && selected.implicit && nonAggregate(owner)) {
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
            if (sourceNode == null) {
                report("CPP004", range, "引用必须绑定到初始化表达式。");
                return new NullLiteralExpr("nullptr", range);
            }
            if (sourceNode instanceof AggregateInitExpr list) {
                if (list.values().size() != 1) {
                    report("CPP004", list.range(), "引用列表初始化要求一个元素。");
                    return new NullLiteralExpr("nullptr", range);
                }
                Expression address = bindReference(reference, list.values().getFirst(), namespace, local, range);
                checkReferenceListConversion(reference.referent(), address, list.range());
                return mapped(list, new GroupingExpr(address, list.range()));
            }
            Expression savedOwner = fullExpressionOwner;
            if (fullExpressionOwner == null) fullExpressionOwner = sourceNode;
            try {
                Expression value = contextualFunctionAddress(reference, sourceNode, namespace, local);
                if (value == null) value = expression(sourceNode, namespace, local, true);
                MiniType target = reference.referent();
                MiniType actual = declaredExpressionType(value);
                CppValueCategory category = valueCategory(value);
                boolean compatible = actual != null && referenceCompatible(target, actual);
                boolean direct = category == CppValueCategory.LVALUE && compatible && addressableObject(value);
                if (!direct) {
                    boolean viable = actual != null && CppOverloadResolver.resolve(
                            List.of(new CppOverloadResolver.Candidate<>("reference", List.of(reference), false)),
                            List.of(new CppOverloadResolver.Argument(actual, category, isNullIntegerLiteral(sourceNode))))
                            .status() == CppOverloadResolver.Status.SELECTED;
                    if (!viable) {
                        report("CPP004", sourceNode.range(), "引用不能绑定到此类型或值类别，或绑定会丢弃 const/volatile 限定符。");
                        return address(value);
                    }
                    // A materialized class subobject already has storage; binding extends its
                    // whole owner. Conversions and scalar prvalues need their own object.
                    if (!compatible || !addressableObject(value)) {
                        requireComplete(target, sourceNode.range());
                        if (!target.unqualified().equals(actual.unqualified())) {
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
                if (actual != null && target.isArray() && compatible && !target.equals(actual)) {
                    address = typed(new CastExpr(coreType(target).pointerTo(), address, sourceNode.range()), target.pointerTo());
                }
                return address;
            } finally { fullExpressionOwner = savedOwner; }
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
                case MaterializeExpr temporary -> new MaterializeExpr(temporary.type(), temporary.initializer(), lifetime, temporary.range());
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
            return target.unqualified().equals(actual.unqualified());
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
            if (target == null) return bound;
            TypeEntity object = objectType(target);
            if (!(bound instanceof AggregateInitExpr list)) {
                MiniType value = declaredExpressionType(bound);
                if (nonAggregate(object) && (value == null || !target.unqualified().equals(value.unqualified()))) {
                    report("CPP005", sourceNode.range(), "尚未支持对此非聚合类型省略花括号或转换形式的初始化：" + object.canonicalName);
                }
                return copyInitialize(target, bound, sourceNode.range(), CppInitializer.Kind.COPY);
            }
            AggregateInitExpr original = (AggregateInitExpr) sourceNode;
            if (object != null && list.values().size() == 1) {
                Expression value = list.values().getFirst();
                MiniType valueType = declaredExpressionType(value);
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
                            checkInitializer(element, sourceDesignated.value(), designated.value()), designated.range())));
                } else {
                    element = target.unqualified() instanceof MiniType.ArrayType array ? array.elementType()
                            : object != null && position < object.fields.size() ? object.fields.get(position).type() : null;
                    values.add(checkInitializer(element, originalValue, value));
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

        private Expression reference(String fallback, SourceRange range, Entity entity) {
            if (entity == null) return new NameExpr(fallback, range);
            if (entity.kind == Kind.ENUM_CONSTANT) return new IntegerConstantExpr(entity.enumValue, MiniType.INT, entity.name, range);
            NameExpr name = new NameExpr(entity.coreName, range);
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
                MethodSet methods = currentClass.methods.get(name);
                if (methods != null) return methods;
                if (fieldPath(currentThis.type.pointee(), name, new HashSet<>()) != null) return new ImplicitField(currentClass, name);
            }
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
            Candidate value = namespace.values.get(name);
            if (value != null) return Set.of(value); // An ordinary value can hide the injected class name.
            Set<Candidate> result = new LinkedHashSet<>();
            if (namespace.children.containsKey(name)) result.add(namespace.children.get(name));
            if (namespace.typedefs.containsKey(name)) result.add(namespace.typedefs.get(name));
            else if (namespace.tags.containsKey(name)) result.add(namespace.tags.get(name));
            return result;
        }

        /** Using directives inject candidates at the common ancestor, not into a local symbol table. */
        private Map<Namespace, Set<Namespace>> nominations(Namespace namespace, Local local) {
            Map<Namespace, Set<Namespace>> result = new IdentityHashMap<>();
            for (Local scope = local; scope != null; scope = scope.parent) {
                for (Namespace target : scope.directives) nominate(scope.namespace, target, result, new HashSet<>());
            }
            for (Namespace scope = namespace; scope != null; scope = scope.parent) {
                for (Namespace target : scope.directives) nominate(scope, target, result, new HashSet<>());
            }
            return result;
        }

        private void nominate(Namespace origin, Namespace target, Map<Namespace, Set<Namespace>> result, Set<Namespace> visited) {
            if (!visited.add(target)) return;
            result.computeIfAbsent(commonAncestor(origin, target), ignored -> new LinkedHashSet<>()).add(target);
            for (Namespace next : target.directives) nominate(origin, next, result, visited);
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

        private Candidate resolveQualifiedName(QualifiedName name, Namespace namespace, Local local) {
            List<String> segments = name.segments();
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
            for (Namespace target : namespace.directives) result.addAll(qualifiedValues(target, name, visited));
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
                if (scope.children.containsKey(name)) candidates.add(scope.children.get(name));
                for (Namespace target : nominated.getOrDefault(scope, Set.of())) {
                    if (target.children.containsKey(name)) candidates.add(target.children.get(name));
                }
                if (!candidates.isEmpty()) return selectNamespace(candidates, sourceName);
            }
            return selectNamespace(Set.of(), sourceName);
        }

        private boolean rejectsTypeQualifier(Namespace namespace, String name, SourceRange range) {
            TypeEntity type = namespace.typedefs.get(name);
            if (type == null) type = namespace.tags.get(name);
            if (type == null || !type.type.unqualified().isStruct()) return false;
            report("CPP005", range, "尚未支持类型限定名称：" + name);
            return true;
        }

        private Set<Namespace> qualifiedNamespaces(Namespace namespace, String name, Set<Namespace> visited) {
            if (!visited.add(namespace)) return Set.of();
            Namespace direct = namespace.children.get(name);
            if (direct != null) return Set.of(direct);
            Set<Namespace> result = new LinkedHashSet<>();
            for (Namespace target : namespace.directives) result.addAll(qualifiedNamespaces(target, name, visited));
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
            displayNames.put(candidate, displayName);
            return candidate;
        }

        private <T extends AstNode> T mapped(AstNode from, T to) { origins.put(from, to); return to; }
        private String spelling(QualifiedName name) { return (name.global() ? "::" : "") + String.join("::", name.segments()); }
        private void report(String code, SourceRange range, String message) {
            diagnostics.add(new Diagnostic(code, Diagnostic.Severity.ERROR, message, range));
        }
    }
}
