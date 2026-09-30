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
        final Set<String> typedefs = new HashSet<>();
        final Set<String> tags = new HashSet<>();
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
        final Set<String> typedefs = new HashSet<>();
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
        private final List<Declaration> declarations = new ArrayList<>();
        private final List<StructDecl> structs = new ArrayList<>();
        private final List<EnumDecl> enums = new ArrayList<>();
        private final List<TypedefDecl> typedefs = new ArrayList<>();
        private final List<GlobalVarDecl> globals = new ArrayList<>();
        private final List<FunctionDecl> functions = new ArrayList<>();
        private int nextName = 1;

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
                            if (target.values.containsKey(name) || target.typedefs.contains(name) || target.tags.contains(name)) {
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
                    case StructDecl node -> {
                        if (rejectNamespaceType(namespace, node)) continue;
                        if (namespace.children.containsKey(node.name())) report("CPP004", node.range(),
                                "类型声明与命名空间冲突：" + node.name());
                        namespace.tags.add(node.name());
                        structs.add(node); declarations.add(node); retainTree(node);
                    }
                    case TypedefDecl node -> {
                        if (rejectNamespaceType(namespace, node)) continue;
                        declareTypedef(node.name(), namespace, null, node.range());
                        typedefs.add(node); declarations.add(node); retainTree(node);
                    }
                    case EnumDecl node -> {
                        if (rejectNamespaceType(namespace, node)) continue;
                        if (namespace.children.containsKey(node.name())) report("CPP004", node.range(),
                                "类型声明与命名空间冲突：" + node.name());
                        namespace.tags.add(node.name());
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

        private void bindGlobal(GlobalVarDecl node, Namespace namespace) {
            if (namespace != root && node.external()) report("CPP005", node.range(),
                    "尚未支持命名空间中的外部对象链接：" + namespace.qualify(node.name()));
            Entity entity = declareNamespaceValue(node.name(), Kind.VARIABLE, node.type(),
                    !node.external() || node.initializer() != null, namespace, node.range());
            Expression initializer = expression(node.initializer(), namespace, null);
            if (initializer != null && !constantInitializer(initializer)) {
                report("CPP005", node.initializer().range(), "尚未支持动态或地址形式的全局初始化；此阶段仅支持可直接写入数据段的常量初始化。");
            }
            GlobalVarDecl core = mapped(node, new GlobalVarDecl(entity.coreName, node.type(), initializer,
                    node.external(), node.alignmentSpecs(), node.range()));
            retainAlignments(node.alignmentSpecs());
            globals.add(core); declarations.add(core);
        }

        private void bindFunction(FunctionDecl node, Namespace namespace) {
            if (namespace != root && node.external()) report("CPP005", node.range(),
                    "尚未支持命名空间中的外部函数链接：" + namespace.qualify(node.name()));
            MiniType signature = MiniType.function(node.returnType().unqualified(), node.parameters().stream()
                    .map(p -> p.type().unqualified()).toList(), node.variadic());
            Entity entity = declareNamespaceValue(node.name(), Kind.FUNCTION, signature, node.hasBody(), namespace, node.range());
            Local scope = new Local(null, namespace);
            List<Parameter> parameters = new ArrayList<>();
            for (Parameter parameter : node.parameters()) {
                Entity value = declareLocal(parameter.name(), parameter.type(), scope, parameter.range());
                parameters.add(mapped(parameter, new Parameter(value.coreName, parameter.type(), parameter.range())));
            }
            BlockStmt body = node.body() == null ? null : block(node.body(), scope, false);
            FunctionDecl core = mapped(node, new FunctionDecl(entity.coreName, node.returnType(), parameters,
                    node.variadic(), body, node.external(), node.noReturn(), node.range()));
            functions.add(core); declarations.add(core);
        }

        private Entity declareNamespaceValue(String name, Kind kind, MiniType type, boolean definition,
                                             Namespace namespace, SourceRange range) {
            if (namespace.children.containsKey(name) || namespace.typedefs.contains(name)) {
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
            return entity;
        }

        private Entity declareLocal(String name, MiniType type, Local scope, SourceRange range) {
            if (scope.values.containsKey(name) || scope.typedefs.contains(name)) {
                report("CPP004", range, "局部名称重复或与 using 声明冲突：" + name);
            }
            Entity entity = new Entity(name, freshName(name), Kind.VARIABLE, null, type, null, true);
            scope.values.put(name, entity);
            return entity;
        }

        private void declareTypedef(String name, Namespace namespace, Local local, SourceRange range) {
            Map<String, Entity> values = local == null ? namespace.values : local.values;
            if (values.containsKey(name) || (local == null && namespace.children.containsKey(name))) {
                report("CPP004", range, "类型别名与已有名称冲突：" + name);
            }
            (local == null ? namespace.typedefs : local.typedefs).add(name);
        }

        private void bindUsing(UsingDecl node, Namespace namespace, Local local) {
            if (node.namespaceDirective()) {
                Namespace target = resolveNamespace(node.target(), namespace, local);
                if (target != null) (local == null ? namespace.directives : local.directives).add(target);
            } else {
                Entity target = resolveQualified(node.target(), namespace, local);
                if (target == null) return;
                String name = node.target().segments().getLast();
                Map<String, Entity> values = local == null ? namespace.values : local.values;
                Entity previous = values.putIfAbsent(name, target);
                if (previous != null && previous != target
                        || (local == null ? namespace.typedefs : local.typedefs).contains(name)
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
                    Entity value = declareLocal(n.name(), n.type(), scope, n.range());
                    retainAlignments(n.alignmentSpecs());
                    yield new VarDeclStmt(value.coreName, n.type(), expression(n.initializer(), namespace, scope),
                            n.alignmentSpecs(), n.range());
                }
                case TypedefStmt n -> {
                    declareTypedef(n.name(), namespace, scope, n.range());
                    yield n;
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
                case NameExpr n -> reference(n.name(), n.range(), lookupValue(n.name(), namespace, local, n.range()));
                case QualifiedNameExpr n -> reference(n.name().segments().getLast(), n.range(),
                        resolveQualified(n.name(), namespace, local));
                case AssignmentExpr n -> new AssignmentExpr(expression(n.target(), namespace, local), n.operator(), expression(n.value(), namespace, local), n.range());
                case BinaryExpr n -> new BinaryExpr(expression(n.left(), namespace, local), n.operator(), expression(n.right(), namespace, local), n.range());
                case ConditionalExpr n -> new ConditionalExpr(expression(n.condition(), namespace, local), expression(n.thenExpression(), namespace, local), expression(n.elseExpression(), namespace, local), n.range());
                case CallExpr n -> new CallExpr(expression(n.callee(), namespace, local), expressions(n.arguments(), namespace, local), n.range());
                case CastExpr n -> new CastExpr(n.targetType(), expression(n.operand(), namespace, local), n.range());
                case CommaExpr n -> new CommaExpr(expressions(n.expressions(), namespace, local), n.range());
                case FieldAccessExpr n -> new FieldAccessExpr(expression(n.target(), namespace, local), n.fieldName(), n.viaPointer(), n.range());
                case GroupingExpr n -> new GroupingExpr(expression(n.expression(), namespace, local), n.range());
                case IndexExpr n -> new IndexExpr(expression(n.target(), namespace, local), expression(n.index(), namespace, local), n.range());
                case UnaryExpr n -> new UnaryExpr(n.operator(), expression(n.operand(), namespace, local), n.range());
                case PostfixUpdateExpr n -> new PostfixUpdateExpr(expression(n.target(), namespace, local), n.operator(), n.range());
                case SizeofExpr n -> new SizeofExpr(expression(n.expression(), namespace, local), n.queriedType(), n.range());
                case AlignofExpr n -> new AlignofExpr(expression(n.expression(), namespace, local), n.queriedType(), n.range());
                case VaStartExpr n -> new VaStartExpr(expression(n.list(), namespace, local), expression(n.lastParameter(), namespace, local), n.range());
                case VaArgExpr n -> new VaArgExpr(expression(n.list(), namespace, local), n.requestedType(), n.range());
                case VaCopyExpr n -> new VaCopyExpr(expression(n.destination(), namespace, local), expression(n.source(), namespace, local), n.range());
                case VaEndExpr n -> new VaEndExpr(expression(n.list(), namespace, local), n.range());
                case AggregateInitExpr n -> new AggregateInitExpr(expressions(n.values(), namespace, local), n.range());
                case DesignatedInitExpr n -> new DesignatedInitExpr(n.designators(), expression(n.value(), namespace, local), n.range());
                case IntegerConstantExpr n -> {
                    // The C parser substitutes enum identifiers early. Restore lexical shadowing in C++.
                    if (n.lexeme().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        yield reference(n.lexeme(), n.range(), lookupValue(n.lexeme(), namespace, local, n.range()));
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

        private List<Expression> expressions(List<Expression> nodes, Namespace namespace, Local local) {
            return nodes.stream().map(n -> expression(n, namespace, local)).toList();
        }

        private Expression reference(String fallback, SourceRange range, Entity entity) {
            if (entity == null) return new NameExpr(fallback, range);
            if (entity.kind == Kind.ENUM_CONSTANT) return new IntegerConstantExpr(entity.enumValue, MiniType.INT, entity.name, range);
            return new NameExpr(entity.coreName, range);
        }

        private Entity lookupValue(String name, Namespace namespace, Local local, SourceRange range) {
            for (Local scope = local; scope != null; scope = scope.parent) {
                Entity value = scope.values.get(name);
                if (value != null) return value;
                if (scope.typedefs.contains(name)) {
                    report("CPP003", range, "类型别名不能作为值使用：" + name);
                    return null;
                }
            }
            Map<Namespace, Set<Namespace>> nominated = nominations(namespace, local);
            for (Namespace scope = namespace; scope != null; scope = scope.parent) {
                Set<Candidate> candidates = new LinkedHashSet<>();
                if (scope.values.containsKey(name)) candidates.add(scope.values.get(name));
                if (scope.children.containsKey(name)) candidates.add(scope.children.get(name));
                for (Namespace target : nominated.getOrDefault(scope, Set.of())) {
                    if (target.values.containsKey(name)) candidates.add(target.values.get(name));
                    if (target.children.containsKey(name)) candidates.add(target.children.get(name));
                }
                if (!candidates.isEmpty()) return select(candidates, name, range);
                if (scope.typedefs.contains(name)) {
                    report("CPP003", range, "类型别名不能作为值使用：" + name);
                    return null;
                }
            }
            report("CPP003", range, "此位置尚未声明名称：" + name);
            return null;
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
            List<String> segments = name.segments();
            Namespace owner;
            if (segments.size() == 1) {
                if (!name.global()) return lookupValue(segments.getFirst(), namespace, local, name.range());
                owner = root;
            } else {
                owner = resolveNamespace(new QualifiedName(name.global(), segments.subList(0, segments.size() - 1), name.range()), namespace, local);
            }
            if (owner == null) return null;
            Set<Candidate> values = qualifiedValues(owner, segments.getLast(), new HashSet<>());
            if (values.isEmpty()) {
                if (owner.typedefs.contains(segments.getLast()) || owner.tags.contains(segments.getLast())) {
                    report("CPP005", name.range(), "尚未支持 using 或限定名称中的类型绑定：" + spelling(name));
                } else report("CPP003", name.range(), "此位置尚未声明限定名称：" + spelling(name));
                return null;
            }
            return select(values, spelling(name), name.range());
        }

        private Set<Candidate> qualifiedValues(Namespace namespace, String name, Set<Namespace> visited) {
            if (!visited.add(namespace)) return Set.of();
            Set<Candidate> result = new LinkedHashSet<>();
            if (namespace.values.containsKey(name)) result.add(namespace.values.get(name));
            if (namespace.children.containsKey(name)) result.add(namespace.children.get(name));
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
                if (scope.typedefs.contains(name)) {
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
            if (!namespace.typedefs.contains(name) && !namespace.tags.contains(name)) return false;
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

        private Entity select(Set<Candidate> candidates, String name, SourceRange range) {
            if (candidates.size() == 1) {
                if (candidates.iterator().next() instanceof Entity entity) return entity;
                report("CPP003", range, "命名空间不能作为值使用：" + name);
            } else report("CPP003", range, "名称查找具有二义性：" + name);
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

        private void retainTree(AstNode node) {
            mapped(node, node);
            if (node instanceof StructDecl n) {
                for (StructField field : n.fields()) { mapped(field, field); retainAlignments(field.alignmentSpecs()); }
            }
            AstChildren.of(node).forEach(this::retainTree);
        }

        private void retainAlignments(List<AlignmentSpec> specs) { specs.forEach(s -> mapped(s, s)); }
        private <T extends AstNode> T mapped(AstNode from, T to) { origins.put(from, to); return to; }
        private String spelling(QualifiedName name) { return (name.global() ? "::" : "") + String.join("::", name.segments()); }
        private void report(String code, SourceRange range, String message) {
            diagnostics.add(new Diagnostic(code, Diagnostic.Severity.ERROR, message, range));
        }
    }
}
