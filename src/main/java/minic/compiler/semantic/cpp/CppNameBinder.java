package minic.compiler.semantic.cpp;

import minic.SourceRange;
import minic.compiler.Diagnostic;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.QualifiedName;
import minic.compiler.parser.node.Statement;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.type.MiniType;

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
        return new Binding(Objects.requireNonNull(source, "source")).run();
    }

    private enum Kind { VARIABLE, FUNCTION, ENUM_CONSTANT }

    private interface Candidate {}

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
        final Map<String, Method> methods = new LinkedHashMap<>();

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
        final Map<String, Entity> values = new LinkedHashMap<>();
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
        final Map<String, Entity> values = new LinkedHashMap<>();
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
        private final List<Declaration> declarations = new ArrayList<>();
        private final List<StructDecl> structs = new ArrayList<>();
        private final List<EnumDecl> enums = new ArrayList<>();
        private final List<TypedefDecl> typedefs = new ArrayList<>();
        private final List<GlobalVarDecl> globals = new ArrayList<>();
        private final List<FunctionDecl> functions = new ArrayList<>();
        private int nextName = 1;
        private TypeEntity currentClass;
        private Entity currentThis;

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
                    case StructDecl node -> bindStruct(node, namespace);
                    case TypedefDecl node -> {
                        MiniType type = normalizeType(node.type(), namespace, null, node.range());
                        declareTypedef(node.name(), type, namespace, null, node.range());
                        String name = namespace == root ? node.name() : freshName(namespace.qualify(node.name()));
                        TypedefDecl core = mapped(node, new TypedefDecl(name, type, node.range()));
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
            if (node.cppInfo() != null) {
                Access current = node.cppInfo().key() == RecordKey.CLASS ? Access.PRIVATE : Access.PUBLIC;
                for (CppMember member : node.cppInfo().members()) {
                    if (member instanceof AccessLabel label) current = label.access();
                    else if (member instanceof FieldMember field) access.put(field.field(), current);
                    else if (member instanceof MethodMember method) methodAccess.put(method, current);
                }
            }
            List<StructField> fields = new ArrayList<>();
            for (StructField field : node.fields()) {
                MiniType type = normalizeType(field.type(), namespace, null, field.range());
                requireComplete(type, field.range());
                StructField coreField = mapped(field, new StructField(field.name(), type, field.anonymous(),
                        normalizeAlignments(field.alignmentSpecs(), namespace, null), field.range()));
                fields.add(coreField);
                if (node.definition()) entity.fieldAccess.put(coreField, access.getOrDefault(field, Access.PUBLIC));
            }
            StructDecl core = mapped(node, new StructDecl(((MiniType.StructType) entity.type).name(),
                    fields, node.definition(), node.union(), node.range()));
            entity.complete |= node.definition();
            if (node.definition()) entity.fields = List.copyOf(fields);
            structs.add(core); declarations.add(core);
            if (node.cppInfo() != null && node.definition()) {
                List<Method> methods = new ArrayList<>();
                for (CppMember member : node.cppInfo().members()) {
                    if (member instanceof MethodMember method) {
                        Method registered = declareMethod(entity, method, methodAccess.get(method), namespace);
                        if (registered != null) methods.add(registered);
                    }
                }
                // Complete-class lookup applies to bodies, without exposing later namespace declarations.
                for (Method method : methods) bindMethod(method, namespace);
            }
        }

        private Method declareMethod(TypeEntity owner, MethodMember member, Access access, Namespace namespace) {
            FunctionDecl sourceMethod = member.method();
            String name = sourceMethod.name();
            if (fieldPath(owner.type, name, new HashSet<>()) != null) {
                report("CPP004", member.nameRange(), "成员函数与数据成员名称冲突：" + name);
                return null;
            }
            MiniType returnType = normalizeType(sourceMethod.returnType(), namespace, null, sourceMethod.range());
            List<MiniType> parameterTypes = sourceMethod.parameters().stream()
                    .map(parameter -> normalizeType(parameter.type(), namespace, null, parameter.range())).toList();
            List<MiniType> coreParameters = new ArrayList<>();
            coreParameters.add(owner.type.pointerTo());
            parameterTypes.stream().map(MiniType::unqualified).forEach(coreParameters::add);
            MiniType signature = MiniType.function(returnType.unqualified(), coreParameters, sourceMethod.variadic());
            Method previous = owner.methods.get(name);
            if (previous != null) {
                report(previous.function.type.equals(signature) ? "CPP004" : "CPP005", member.nameRange(),
                        previous.function.type.equals(signature) ? "类内成员函数重复声明：" + name : "尚未支持成员函数重载：" + name);
                return null;
            }
            Entity function = new Entity(name, freshName(owner.canonicalName.substring(2) + "::" + name),
                    Kind.FUNCTION, namespace, signature, null, sourceMethod.hasBody());
            coreValues.put(function.coreName, function);
            Method method = new Method(owner, member, access, function, returnType, parameterTypes);
            owner.methods.put(name, method);
            return method;
        }

        private void bindMethod(Method method, Namespace namespace) {
            FunctionDecl original = method.source.method();
            Local scope = new Local(null, namespace);
            Entity self = new Entity("this", freshName("this"), Kind.VARIABLE, null,
                    method.owner.type.pointerTo(), null, true);
            coreValues.put(self.coreName, self);
            List<Parameter> parameters = new ArrayList<>();
            parameters.add(new Parameter(self.coreName, self.type, method.source.nameRange()));
            for (int index = 0; index < original.parameters().size(); index++) {
                Parameter parameter = original.parameters().get(index);
                MiniType type = method.parameterTypes.get(index);
                Entity value = declareLocal(parameter.name(), type, scope, parameter.range());
                parameters.add(mapped(parameter, new Parameter(value.coreName, type, parameter.range())));
            }
            TypeEntity savedClass = currentClass;
            Entity savedThis = currentThis;
            currentClass = method.owner;
            currentThis = self;
            try {
                if (original.hasBody()) {
                    requireComplete(method.returnType, original.range());
                    method.parameterTypes.forEach(type -> requireComplete(type, original.range()));
                }
                BlockStmt body = original.body() == null ? null : block(original.body(), scope, false);
                FunctionDecl core = mapped(original, new FunctionDecl(method.function.coreName, method.returnType,
                        parameters, original.variadic(), body, false, original.noReturn(), original.range()));
                functions.add(core); declarations.add(core);
            } finally {
                currentClass = savedClass;
                currentThis = savedThis;
            }
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
            if (type instanceof MiniType.QualifiedType qualified) {
                return MiniType.qualified(normalizeType(qualified.baseType(), namespace, local, range), qualified.qualifiers());
            }
            if (type instanceof MiniType.PointerType pointer) return normalizeType(pointer.pointee(), namespace, local, range).pointerTo();
            if (type instanceof MiniType.ArrayType array) return normalizeType(array.elementType(), namespace, local, range).arrayOf(array.length());
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
                MiniType type = normalizeType(spec.type(), namespace, local, spec.range());
                requireComplete(type, spec.range());
                return mapped(spec, AlignmentSpec.type(type, spec.range()));
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
            if (!node.external() || node.initializer() != null) requireComplete(type, node.range());
            Entity entity = declareNamespaceValue(node.name(), Kind.VARIABLE, type,
                    !node.external() || node.initializer() != null, namespace, node.range());
            Expression initializer = node.external() && node.initializer() == null ? null
                    : initializer(type, node.initializer(), namespace, null, node.range());
            if (initializer != null && !constantInitializer(initializer)) {
                report("CPP005", node.initializer().range(), "尚未支持动态或地址形式的全局初始化；此阶段仅支持可直接写入数据段的常量初始化。");
            }
            GlobalVarDecl core = mapped(node, new GlobalVarDecl(entity.coreName, type, initializer,
                    node.external(), normalizeAlignments(node.alignmentSpecs(), namespace, null), node.range()));
            globals.add(core); declarations.add(core);
        }

        private void bindFunction(FunctionDecl node, Namespace namespace) {
            if (namespace != root && node.external()) report("CPP005", node.range(),
                    "尚未支持命名空间中的外部函数链接：" + namespace.qualify(node.name()));
            MiniType returnType = normalizeType(node.returnType(), namespace, null, node.range());
            List<MiniType> parameterTypes = node.parameters().stream()
                    .map(p -> normalizeType(p.type(), namespace, null, p.range())).toList();
            if (node.hasBody()) {
                requireComplete(returnType, node.range());
                for (int i = 0; i < parameterTypes.size(); i++) requireComplete(parameterTypes.get(i), node.parameters().get(i).range());
            }
            MiniType signature = MiniType.function(returnType.unqualified(), parameterTypes.stream()
                    .map(MiniType::unqualified).toList(), node.variadic());
            Entity entity = declareNamespaceValue(node.name(), Kind.FUNCTION, signature, node.hasBody(), namespace, node.range());
            Local scope = new Local(null, namespace);
            List<Parameter> parameters = new ArrayList<>();
            for (int i = 0; i < node.parameters().size(); i++) {
                Parameter parameter = node.parameters().get(i);
                MiniType type = parameterTypes.get(i);
                Entity value = declareLocal(parameter.name(), type, scope, parameter.range());
                parameters.add(mapped(parameter, new Parameter(value.coreName, type, parameter.range())));
            }
            BlockStmt body = node.body() == null ? null : block(node.body(), scope, false);
            FunctionDecl core = mapped(node, new FunctionDecl(entity.coreName, returnType, parameters,
                    node.variadic(), body, node.external(), node.noReturn(), node.range()));
            functions.add(core); declarations.add(core);
        }

        private Entity declareNamespaceValue(String name, Kind kind, MiniType type, boolean definition,
                                             Namespace namespace, SourceRange range) {
            if (namespace.children.containsKey(name) || namespace.typedefs.containsKey(name) && !namespace.typedefs.get(name).classType) {
                report("CPP004", range, "名称与命名空间或类型别名冲突：" + name);
            }
            Entity existing = namespace.values.get(name);
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
            Map<String, Entity> values = local == null ? namespace.values : local.values;
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
                Entity target = requireValue(candidate, spelling(node.target()), node.range());
                if (target == null) return;
                String name = node.target().segments().getLast();
                Map<String, Entity> values = local == null ? namespace.values : local.values;
                Entity previous = values.putIfAbsent(name, target);
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
                else result.add(statement(node, scope));
            }
            return List.copyOf(result);
        }

        private Statement body(Statement node, Local scope) {
            return node == null ? null : statement(node, new Local(scope, scope.namespace));
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
                    yield new VarDeclStmt(value.coreName, type, initializer(type, n.initializer(), namespace, scope, n.range()),
                            normalizeAlignments(n.alignmentSpecs(), namespace, scope), n.range());
                }
                case TypedefStmt n -> {
                    MiniType type = normalizeType(n.type(), namespace, scope, n.range());
                    declareTypedef(n.name(), type, namespace, scope, n.range());
                    yield new TypedefStmt(n.name(), type, n.range());
                }
                case UsingDecl n -> {
                    bindUsing(n, namespace, scope);
                    yield new BlockStmt(List.of(), n.range());
                }
                case ExprStmt n -> new ExprStmt(expression(n.expression(), namespace, scope), n.range());
                case ReturnStmt n -> new ReturnStmt(expression(n.expression(), namespace, scope), n.range());
                case IfStmt n -> new IfStmt(expression(n.condition(), namespace, scope),
                        body(n.thenBranch(), scope), body(n.elseBranch(), scope), n.range());
                case WhileStmt n -> new WhileStmt(expression(n.condition(), namespace, scope), body(n.body(), scope), n.range());
                case DoWhileStmt n -> new DoWhileStmt(body(n.body(), scope), expression(n.condition(), namespace, scope), n.range());
                case ForStmt n -> {
                    Local loop = new Local(scope, namespace);
                    Statement initializer = statement(n.initializer(), loop);
                    Expression condition = expression(n.condition(), namespace, loop);
                    Expression step = expression(n.step(), namespace, loop);
                    // C++ forbids redeclaring the for-init name in the body's outermost block.
                    Statement loopBody = n.body() instanceof BlockStmt b ? block(b, loop, false) : statement(n.body(), loop);
                    yield new ForStmt(initializer, condition, step, loopBody, n.range());
                }
                case SwitchStmt n -> {
                    Expression selector = expression(n.selector(), namespace, scope);
                    Local casesScope = new Local(scope, namespace);
                    List<SwitchCase> cases = new ArrayList<>();
                    boolean crossesInitialization = false;
                    for (SwitchCase item : n.cases()) {
                        if (crossesInitialization) report("CPP005", item.range(),
                                "case/default 跳转会跳过同一 switch 作用域的局部初始化；请用显式块限制变量作用域。");
                        Expression value = expression(item.value(), namespace, casesScope);
                        cases.add(mapped(item, new SwitchCase(value, statements(item.statements(), casesScope), item.range())));
                        crossesInitialization |= item.statements().stream()
                                .anyMatch(s -> s instanceof VarDeclStmt v && v.initializer() != null);
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

        private Expression expression(Expression node, Namespace namespace, Local local) {
            if (node == null) return null;
            Expression core = switch (node) {
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
                    Expression target = expression(n.target(), namespace, local);
                    MiniType targetType = declaredExpressionType(target);
                    requireComplete(targetType, n.range());
                    if (objectType(targetType) != null && hasConstSubobject(targetType, new HashSet<>())) {
                        report("CPP005", n.range(), "尚未支持含 const 子对象的整体赋值所需的特殊成员函数规则。");
                    }
                    if (n.operator() == TokenType.PLUS_EQUAL || n.operator() == TokenType.MINUS_EQUAL) {
                        requireComplete(elementType(declaredExpressionType(target)), n.range());
                    }
                    yield new AssignmentExpr(target, n.operator(), expression(n.value(), namespace, local), n.range());
                }
                case BinaryExpr n -> {
                    Expression left = expression(n.left(), namespace, local);
                    Expression right = expression(n.right(), namespace, local);
                    if (n.operator() == TokenType.PLUS || n.operator() == TokenType.MINUS) {
                        requireComplete(elementType(declaredExpressionType(left)), n.range());
                        requireComplete(elementType(declaredExpressionType(right)), n.range());
                    }
                    yield new BinaryExpr(left, n.operator(), right, n.range());
                }
                case ConditionalExpr n -> new ConditionalExpr(expression(n.condition(), namespace, local), expression(n.thenExpression(), namespace, local), expression(n.elseExpression(), namespace, local), n.range());
                case CallExpr n -> {
                    BoundCallee binding = bindCallee(n.callee(), namespace, local);
                    Expression callee = binding.expression();
                    MiniType.FunctionType signature = functionSignature(declaredExpressionType(callee));
                    if (signature != null) {
                        requireComplete(signature.returnType(), n.range());
                        signature.parameterTypes().forEach(t -> requireComplete(t, n.range()));
                    }
                    List<Expression> arguments = new ArrayList<>();
                    if (binding.receiver() != null) arguments.add(binding.receiver());
                    arguments.addAll(expressions(n.arguments(), namespace, local));
                    yield new CallExpr(callee, arguments, n.range());
                }
                case CastExpr n -> new CastExpr(normalizeType(n.targetType(), namespace, local, n.range()), expression(n.operand(), namespace, local), n.range());
                case CommaExpr n -> new CommaExpr(expressions(n.expressions(), namespace, local), n.range());
                case FieldAccessExpr n -> {
                    Expression target = expression(n.target(), namespace, local);
                    MiniType owner = declaredExpressionType(target);
                    owner = n.viaPointer() ? elementType(owner) : owner;
                    requireComplete(owner, n.range());
                    Method method = memberMethod(owner, n.fieldName());
                    if (method != null) {
                        requireMethodAccess(method, n.range());
                        report("CPP005", n.range(), "成员函数只能作为调用目标使用；尚未支持成员函数指针：" + n.fieldName());
                    } else requireAccessible(owner, n.fieldName(), n.range(), "数据成员访问");
                    yield new FieldAccessExpr(target, n.fieldName(), n.viaPointer(), n.range());
                }
                case GroupingExpr n -> new GroupingExpr(expression(n.expression(), namespace, local), n.range());
                case IndexExpr n -> {
                    Expression target = expression(n.target(), namespace, local);
                    requireComplete(elementType(declaredExpressionType(target)), n.range());
                    yield new IndexExpr(target, expression(n.index(), namespace, local), n.range());
                }
                case UnaryExpr n -> {
                    Expression operand = expression(n.operand(), namespace, local);
                    if (n.operator() == TokenType.PLUS_PLUS || n.operator() == TokenType.MINUS_MINUS) {
                        requireUpdateOperand(operand, n.range());
                    }
                    yield new UnaryExpr(n.operator(), operand, n.range());
                }
                case PostfixUpdateExpr n -> {
                    Expression target = expression(n.target(), namespace, local);
                    requireUpdateOperand(target, n.range());
                    yield new PostfixUpdateExpr(target, n.operator(), n.range());
                }
                case SizeofExpr n -> {
                    MiniType type = normalizeType(n.queriedType(), namespace, local, n.range());
                    Expression operand = expression(n.expression(), namespace, local);
                    requireComplete(type != null ? type : declaredExpressionType(operand), n.range());
                    yield new SizeofExpr(operand, type, n.range());
                }
                case AlignofExpr n -> {
                    MiniType type = normalizeType(n.queriedType(), namespace, local, n.range());
                    Expression operand = expression(n.expression(), namespace, local);
                    requireComplete(type != null ? type : declaredExpressionType(operand), n.range());
                    yield new AlignofExpr(operand, type, n.range());
                }
                case VaStartExpr n -> new VaStartExpr(expression(n.list(), namespace, local), expression(n.lastParameter(), namespace, local), n.range());
                case VaArgExpr n -> {
                    MiniType type = normalizeType(n.requestedType(), namespace, local, n.range());
                    requireComplete(type, n.range());
                    yield new VaArgExpr(expression(n.list(), namespace, local), type, n.range());
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
                case StringLiteralExpr n -> n;
                default -> {
                    report("CPP005", node.range(), "尚未支持此 C++ 表达式：" + node.getClass().getSimpleName());
                    yield node;
                }
            };
            return mapped(node, core);
        }

        /** The cast preserves this's prvalue nature while using the ordinary pointer ABI. */
        private Expression thisValue(SourceRange range) {
            return new CastExpr(currentThis.type, new NameExpr(currentThis.coreName, range), range);
        }

        private Expression simpleReference(String name, SourceRange range, Namespace namespace, Local local) {
            Candidate candidate = lookupName(name, namespace, local, range);
            if (candidate instanceof ImplicitField field) {
                requireAccessible(field.owner.type, name, range, "数据成员访问");
                return new FieldAccessExpr(thisValue(range), name, true, range);
            }
            if (candidate instanceof Method method) {
                requireMethodAccess(method, range);
                report("CPP005", range, "成员函数只能作为调用目标使用；尚未支持成员函数指针：" + name);
                return new NameExpr(method.function.coreName, range);
            }
            return reference(name, range, requireValue(candidate, name, range));
        }

        private record BoundCallee(Expression expression, Expression receiver) { }

        private BoundCallee bindCallee(Expression sourceCallee, Namespace namespace, Local local) {
            Expression designator = sourceCallee;
            while (designator instanceof GroupingExpr group) designator = group.expression();
            Method method = null;
            Expression receiver = null;
            String sourceName = designator instanceof NameExpr name ? name.name()
                    : designator instanceof IntegerConstantExpr constant
                    && constant.lexeme().matches("[A-Za-z_][A-Za-z0-9_]*") ? constant.lexeme() : null;
            if (sourceName != null) {
                Candidate candidate = lookupName(sourceName, namespace, local, designator.range());
                if (candidate instanceof Method member) {
                    method = member;
                    receiver = thisValue(designator.range());
                } else {
                    Expression core;
                    if (candidate instanceof ImplicitField field) {
                        requireAccessible(field.owner.type, field.name, designator.range(), "数据成员访问");
                        core = new FieldAccessExpr(thisValue(designator.range()), field.name, true, designator.range());
                    } else core = reference(sourceName, designator.range(), requireValue(candidate, sourceName, designator.range()));
                    return new BoundCallee(rebuildCalleeGroups(sourceCallee, designator, core), null);
                }
            } else if (designator instanceof FieldAccessExpr field) {
                Expression target = expression(field.target(), namespace, local);
                MiniType owner = declaredExpressionType(target);
                owner = field.viaPointer() ? elementType(owner) : owner;
                requireComplete(owner, field.range());
                method = memberMethod(owner, field.fieldName());
                if (method == null) {
                    requireAccessible(owner, field.fieldName(), field.range(), "数据成员访问");
                    Expression core = new FieldAccessExpr(target, field.fieldName(), field.viaPointer(), field.range());
                    return new BoundCallee(rebuildCalleeGroups(sourceCallee, designator, core), null);
                }
                if (owner.isConstQualified() || owner.isVolatileQualified()) {
                    report("CPP004", field.range(), "非 const/volatile 成员函数不能通过 const/volatile 对象调用：" + field.fieldName());
                }
                if (field.viaPointer()) receiver = target;
                else {
                    if (!addressableObject(target)) report("CPP005", field.range(), "尚未支持临时对象或此值类别作为成员函数接收者。");
                    receiver = new UnaryExpr(TokenType.AMPERSAND, target, field.target().range());
                }
            }
            if (method == null) return new BoundCallee(expression(sourceCallee, namespace, local), null);
            requireMethodAccess(method, sourceCallee.range());
            // The member designator and its parentheses are compile-time lookup syntax. Only
            // the outer callee has an executable counterpart, keeping reverse origins unique.
            Expression core = mapped(sourceCallee, new NameExpr(method.function.coreName, sourceCallee.range()));
            return new BoundCallee(core, receiver);
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

        private Method memberMethod(MiniType owner, String name) {
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
            if (type != null && type.unqualified().equals(MiniType.BOOL)) {
                report("CPP004", range, "C++17 不允许对 bool 进行自增或自减");
            }
            requireComplete(elementType(type), range);
        }

        /**
         * Follows declared aggregate/pointer identity at this source position. This is not the
         * scalar expression typer: promotions, conversions and invalid operators remain core
         * semantic checks. In particular an incomplete pointee is harmless until an operation
         * requires its object layout, and later definitions cannot change an earlier check.
         */
        private MiniType declaredExpressionType(Expression expression) {
            if (expression == null) return null;
            if (declaredExpressionTypes.containsKey(expression)) return declaredExpressionTypes.get(expression);
            MiniType type = switch (expression) {
                case NameExpr name -> coreValues.containsKey(name.name()) ? coreValues.get(name.name()).type : null;
                case GroupingExpr group -> declaredExpressionType(group.expression());
                case CastExpr cast -> cast.targetType();
                case AssignmentExpr assignment -> declaredExpressionType(assignment.target());
                case CommaExpr comma -> declaredExpressionType(comma.expressions().getLast());
                case ConditionalExpr conditional -> conditionalDeclaredType(
                        declaredExpressionType(conditional.thenExpression()), declaredExpressionType(conditional.elseExpression()));
                case BinaryExpr binary -> {
                    MiniType left = declaredExpressionType(binary.left());
                    MiniType right = declaredExpressionType(binary.right());
                    boolean leftPointer = elementType(left) != null;
                    boolean rightPointer = elementType(right) != null;
                    if (binary.operator() == TokenType.PLUS && leftPointer != rightPointer) {
                        yield elementType(leftPointer ? left : right).pointerTo();
                    }
                    yield binary.operator() == TokenType.MINUS && leftPointer && !rightPointer ? elementType(left).pointerTo() : null;
                }
                case UnaryExpr unary -> {
                    MiniType operand = declaredExpressionType(unary.operand());
                    yield switch (unary.operator()) {
                        case STAR -> elementType(operand);
                        case AMPERSAND -> operand == null ? null : operand.pointerTo();
                        case PLUS_PLUS, MINUS_MINUS -> operand;
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
            if (first.isPointer() && second.isPointer()) {
                MiniType a = elementType(first), b = elementType(second);
                if (a.isVoid() || b.isVoid()) return MiniType.VOID.pointerTo();
                MiniType common = conditionalDeclaredType(a, b);
                if (common != null) return common.pointerTo();
            }
            return null;
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
            return type != null && type.fields.stream().anyMatch(f -> type.fieldAccess.getOrDefault(f, Access.PUBLIC) != Access.PUBLIC);
        }

        private Expression initializer(MiniType target, Expression sourceNode, Namespace namespace, Local local, SourceRange range) {
            if (sourceNode == null) {
                requireImplicitInitialization(target, false, range);
                return null;
            }
            return checkInitializer(target, sourceNode, expression(sourceNode, namespace, local));
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

        /** Validates C++ list initialization before the C aggregate initializer can write fields. */
        private Expression checkInitializer(MiniType target, Expression sourceNode, Expression bound) {
            if (target == null) return bound;
            TypeEntity object = objectType(target);
            if (!(bound instanceof AggregateInitExpr list)) {
                MiniType value = declaredExpressionType(bound);
                if (nonAggregate(object) && (value == null || !target.unqualified().equals(value.unqualified()))) {
                    report("CPP005", sourceNode.range(), "尚未支持对此非聚合类型省略花括号或转换形式的初始化：" + object.canonicalName);
                }
                return bound;
            }
            AggregateInitExpr original = (AggregateInitExpr) sourceNode;
            if (object != null && list.values().size() == 1) {
                Expression value = list.values().getFirst();
                MiniType valueType = declaredExpressionType(value);
                if (valueType != null && target.unqualified().equals(valueType.unqualified())) {
                    // C++17 permits {sameTypeObject}, including implicit copies of non-aggregates.
                    return mapped(sourceNode, new GroupingExpr(value, list.range()));
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
            return new NameExpr(entity.coreName, range);
        }

        private Entity lookupValue(String name, Namespace namespace, Local local, SourceRange range) {
            return requireValue(lookupName(name, namespace, local, range), name, range);
        }

        private Candidate lookupName(String name, Namespace namespace, Local local, SourceRange range) {
            for (Local scope = local; scope != null; scope = scope.parent) {
                Entity value = scope.values.get(name);
                if (value != null) return value;
                if (scope.typedefs.containsKey(name)) return scope.typedefs.get(name);
            }
            if (currentClass != null) {
                Method method = currentClass.methods.get(name);
                if (method != null) return method;
                if (fieldPath(currentClass.type, name, new HashSet<>()) != null) return new ImplicitField(currentClass, name);
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
            Entity value = namespace.values.get(name);
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
