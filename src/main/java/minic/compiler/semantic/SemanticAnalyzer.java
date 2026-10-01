package minic.compiler.semantic;

import minic.compiler.CompilerApi;
import minic.compiler.Stage;
import minic.compiler.LanguageMode;
import minic.compiler.SymbolNames;
import minic.compiler.semantic.cpp.CppNameBinder;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.AssignmentExpr;
import minic.compiler.parser.node.Expression.BinaryExpr;
import minic.compiler.parser.node.Expression.CallExpr;
import minic.compiler.parser.node.Expression.FieldAccessExpr;
import minic.compiler.parser.node.Expression.GroupingExpr;
import minic.compiler.parser.node.Expression.IndexExpr;
import minic.compiler.parser.node.Expression.UnaryExpr;
import minic.compiler.parser.node.Expression.VaArgExpr;
import minic.compiler.parser.node.Expression.VaCopyExpr;
import minic.compiler.parser.node.Expression.VaEndExpr;
import minic.compiler.parser.node.Expression.VaStartExpr;
import minic.compiler.parser.node.Statement;
import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.parser.node.Statement.ExprStmt;
import minic.compiler.parser.node.Statement.ForStmt;
import minic.compiler.parser.node.Statement.IfStmt;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.parser.node.Statement.VarDeclStmt;
import minic.compiler.parser.node.Statement.WhileStmt;
import minic.compiler.semantic.manager.FunctionRegistry;
import minic.compiler.semantic.manager.StatementSemanticAnalyzer;
import minic.compiler.semantic.manager.StructRegistry;
import minic.compiler.semantic.model.Scope;
import minic.compiler.semantic.model.SemanticAction;
import minic.compiler.semantic.model.SemanticAction.SemanticActionKind;
import minic.compiler.semantic.model.StructLayout;
import minic.compiler.type.MiniType;
import minic.compiler.Diagnostic;
import minic.SourceRange;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MiniC 语义分析阶段。
 *
 * <p>SemanticAnalyzer 自身持有语义分析上下文。每次调用 {@link #step()} 只执行
 * 一个语义动作；最后一次调用是不执行语义动作的结束步骤。</p>
 */
public final class SemanticAnalyzer extends Stage {
    private final Parser parser;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private Program program;
    private Program sourceProgram;
    private Map<AstNode, AstNode> sourceToCore = Map.of();
    private Map<AstNode, AstNode> coreToSource = Map.of();
    private Map<String, String> displayNames = Map.of();
    private boolean bindingFailed;
    private Scope globalScope;
    private Map<Expression, MiniType> expressionTypes;
    private StructRegistry structRegistry;
    private FunctionRegistry functionRegistry;
    private StatementSemanticAnalyzer statementAnalyzer;
    private Map<String, StructLayout> structLayouts = Map.of();
    private List<PlannedAction> plannedActions = List.of();
    private SemanticAction currentAction;
    private SemanticResult semanticResult;
    private int nextActionIndex;
    private boolean initialized;
    private boolean completed;
    private long stepCount;

    /** 创建等待 {@link #analyze(Program)} 提供输入的语义分析器。 */
    public SemanticAnalyzer() {
        parser = null;
    }

    /** 创建独立运行的语义分析阶段。 */
    public SemanticAnalyzer(Program program) {
        parser = null;
        initialize(program);
    }

    /** 创建由 Parser 提供输入的语义分析阶段。 */
    public SemanticAnalyzer(Parser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    /** 重置分析器并设置 AST 输入。 */
    public void begin(Program program) {
        if (parser != null) {
            throw new IllegalStateException("parser-backed semantic analyzer cannot accept another program");
        }
        initialize(program);
    }

    /** 循环执行完整语义分析。 */
    public SemanticResult analyze(Program program) {
        begin(program);
        return analyze();
    }

    /** 循环执行当前输入的完整语义分析。 */
    public SemanticResult analyze() {
        new CompilerApi(List.of(this)).run();
        return semanticResult();
    }

    @Override
    public SourceRange step() {
        if (!canNext()) {
            throw new IllegalStateException("semantic analyzer is already completed");
        }
        ensureInitialized();
        currentAction = null;

        if (bindingFailed) {
            completed = true;
            stepCount++;
            semanticResult = buildResult();
            return finishStep(diagnostics.getFirst().range(), "CPP_NAME_BINDING_ERROR", diagnostics, () -> semanticResult);
        }

        if (nextActionIndex >= actionCount()) {
            semanticResult = buildResult();
            completed = true;
            stepCount++;
            return finishStep(null, "", diagnostics, () -> semanticResult);
        }

        int diagnosticsBefore = diagnostics.size();
        currentAction = executeAction(nextActionIndex);
        nextActionIndex++;
        for (int i = diagnosticsBefore; i < diagnostics.size(); i++) {
            Diagnostic item = diagnostics.get(i);
            diagnostics.set(i, new Diagnostic(item.code(), item.severity(),
                    SymbolNames.displayText(item.message(), displayNames),
                    SymbolNames.displayText(item.solution(), displayNames), item.range()));
        }
        if (diagnostics.size() > diagnosticsBefore) {
            Diagnostic diagnostic = diagnostics.getLast();
            currentAction = SemanticAction.diagnostic(
                    currentAction.subject(),
                    diagnostic,
                    currentAction.astNode(),
                    currentAction.scope()
            );
        }
        if (currentAction.astNode() instanceof AstNode core && coreToSource.containsKey(core)) {
            currentAction = new SemanticAction(currentAction.kind(), currentAction.subject(), currentAction.diagnostic(),
                    coreToSource.get(core), currentAction.scope());
        }
        currentAction = new SemanticAction(currentAction.kind(),
                SymbolNames.displayText(currentAction.subject(), displayNames), currentAction.diagnostic(),
                currentAction.astNode(), currentAction.scope());
        stepCount++;
        SourceRange range;
        if (currentAction.astNode() instanceof AstNode astNode) {
            range = astNode.range();
        } else if (currentAction.diagnosticOptional().isPresent()) {
            range = currentAction.diagnosticOptional().orElseThrow().range();
        } else {
            range = program.range();
        }
        return finishStep(range, currentAction.kind().name(), diagnostics, this::buildResult);
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    /** 返回最终语义结果。 */
    public SemanticResult semanticResult() {
        if (semanticResult == null) {
            throw new IllegalStateException("semantic result is not available before analysis completes");
        }
        return semanticResult;
    }

    /** 返回全局作用域。 */
    public Scope globalScope() {
        ensureInitialized();
        return globalScope;
    }

    /** 返回当前分析的 AST 程序。 */
    public Program program() {
        ensureInitialized();
        return program;
    }

    /** 返回已记录的表达式类型数量。 */
    public int expressionTypeCount() {
        ensureInitialized();
        return expressionTypes.size();
    }

    /** 返回已经执行的步骤数，包含最后的空结束步骤。 */
    public long stepCount() {
        return stepCount;
    }

    /** 返回已完成的语义动作数。 */
    public int completedActionCount() {
        return nextActionIndex;
    }

    /** 返回语义动作总数，不包含最后的空结束步骤。 */
    public int actionCount() {
        ensureInitialized();
        return 5 + plannedActions.size();
    }

    private void initialize(Program sourceProgram) {
        program = Objects.requireNonNull(sourceProgram, "program");
        this.sourceProgram = sourceProgram;
        diagnostics.clear();
        sourceToCore = Map.of();
        coreToSource = new IdentityHashMap<>();
        displayNames = Map.of();
        if (sourceProgram.languageMode() == LanguageMode.CPP17_ALGORITHM) {
            var binding = CppNameBinder.bind(sourceProgram);
            program = binding.program();
            sourceToCore = binding.sourceToCore();
            sourceToCore.forEach((original, core) -> coreToSource.put(core, original));
            displayNames = binding.displayNames();
            diagnostics.addAll(binding.diagnostics());
        } else {
            AstNode cppNode = minic.compiler.parser.node.AstChildren.firstCppSyntax(program);
            if (cppNode != null) diagnostics.add(new Diagnostic("CPP002", Diagnostic.Severity.ERROR,
                    "C 模式的 AST 不能包含 C++ 名称、类成员元数据或 this 表达式。",
                    "请使用 CPP17_ALGORITHM 模式解析和分析源程序。",
                    cppNode instanceof minic.compiler.parser.node.Declaration.StructDecl record
                            ? record.cppInfo().keyRange() : cppNode.range()));
        }
        bindingFailed = !diagnostics.isEmpty();
        globalScope = new Scope();
        expressionTypes = new IdentityHashMap<>();
        structRegistry = new StructRegistry(globalScope, diagnostics);
        functionRegistry = new FunctionRegistry(globalScope, diagnostics);
        statementAnalyzer = new StatementSemanticAnalyzer(
                globalScope,
                functionRegistry,
                structRegistry,
                diagnostics,
                expressionTypes
        );
        structLayouts = Map.of();
        plannedActions = planActions(program);
        currentAction = null;
        semanticResult = null;
        nextActionIndex = 0;
        stepCount = 0;
        completed = false;
        initialized = true;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        if (parser == null) {
            throw new IllegalStateException("semantic analyzer has no program input");
        }
        if (parser.canNext()) {
            throw new IllegalStateException("parser has not completed");
        }
        if (!parser.succeeded()) {
            throw new IllegalStateException("parser did not succeed");
        }
        initialize(parser.result().program());
    }

    private SemanticResult buildResult() {
        return new SemanticResult(
                program,
                globalScope,
                SemanticResult.ScopeSnapshot.from(globalScope, displayNames),
                expressionTypes,
                structLayouts,
                currentAction,
                sourceProgram,
                sourceToCore,
                displayNames
        );
    }

    private SemanticAction executeAction(int actionIndex) {
        if (actionIndex < 5) {
            return switch (actionIndex) {
                case 0 -> {
                    structRegistry.defineStructs(program);
                    yield SemanticAction.of(SemanticActionKind.REGISTER_STRUCTS, "structs=" + program.structs().size(), program, globalScope);
                }
                case 1 -> {
                    structRegistry.validateProgramTypes(program);
                    yield SemanticAction.of(SemanticActionKind.CHECK_TYPES, "program types", program, globalScope);
                }
                case 2 -> {
                    structLayouts = diagnostics.isEmpty() ? structRegistry.computeLayouts() : Map.of();
                    yield SemanticAction.of(SemanticActionKind.COMPUTE_STRUCT_LAYOUTS, "struct layouts", program, globalScope);
                }
                case 3 -> {
                    functionRegistry.defineFunctions(program);
                    statementAnalyzer.analyzeGlobals(program.globals());
                    yield SemanticAction.of(SemanticActionKind.REGISTER_FUNCTIONS,
                            "globals=" + program.globals().size() + ", functions=" + program.functions().size(),
                            program, globalScope);
                }
                case 4 -> {
                    functionRegistry.validateMain(program);
                    yield SemanticAction.of(SemanticActionKind.VALIDATE_MAIN, "main", program, globalScope);
                }
                default -> throw new IllegalArgumentException("unsupported semantic action: " + actionIndex);
            };
        }
        return executePlannedAction(plannedActions.get(actionIndex - 5));
    }

    private SemanticAction executePlannedAction(PlannedAction action) {
        return switch (action.kind()) {
            case ANALYZE_FUNCTION_BODY -> {
                statementAnalyzer.beginFunction(action.functionDecl());
                yield SemanticAction.of(
                        SemanticActionKind.ANALYZE_FUNCTION_BODY,
                        action.functionDecl().name(),
                        action.functionDecl().bodyOptional().orElseThrow(),
                        statementAnalyzer.currentFunctionScope()
                );
            }
            case ANALYZE_STATEMENT -> {
                statementAnalyzer.analyzeCurrentFunctionTopLevelStatement(action.statement());
                yield SemanticAction.of(
                        SemanticActionKind.ANALYZE_STATEMENT,
                        statementSubject(action),
                        action.statement(),
                        innermostScopeFor(action.statement().range(), statementAnalyzer.currentFunctionScope())
                );
            }
            case VISIT_AST_NODE -> SemanticAction.of(
                    SemanticActionKind.VISIT_AST_NODE,
                    nodeSubject(action.astNode()),
                    action.astNode(),
                    innermostScopeFor(rangeOf(action.astNode()), statementAnalyzer.currentFunctionScope())
            );
            case VALIDATE_FUNCTION_RETURN -> {
                try {
                    statementAnalyzer.validateCurrentFunctionReturn();
                    yield SemanticAction.of(
                            SemanticActionKind.VALIDATE_FUNCTION_RETURN,
                            action.functionDecl().name(),
                            action.functionDecl(),
                            statementAnalyzer.currentFunctionScope()
                    );
                } finally {
                    statementAnalyzer.endFunction();
                }
            }
            default -> throw new IllegalArgumentException("unsupported planned action: " + action.kind());
        };
    }

    private List<PlannedAction> planActions(Program sourceProgram) {
        ArrayList<PlannedAction> actions = new ArrayList<>();
        for (FunctionDecl functionDecl : sourceProgram.functions().stream().filter(FunctionDecl::hasBody).toList()) {
            actions.add(PlannedAction.function(functionDecl));
            for (Statement statement : functionDecl.bodyOptional().orElseThrow().statements()) {
                actions.add(PlannedAction.statement(functionDecl, statement));
                visitChildren(statement).stream()
                        .map(node -> PlannedAction.visit(functionDecl, node))
                        .forEach(actions::add);
            }
            actions.add(PlannedAction.returnCheck(functionDecl));
        }
        return List.copyOf(actions);
    }

    private String statementSubject(PlannedAction action) {
        return action.functionDecl().name() + " " + action.statement().getClass().getSimpleName();
    }

    private String nodeSubject(Object node) {
        return node.getClass().getSimpleName();
    }

    private List<Object> visitChildren(Object node) {
        ArrayList<Object> nodes = new ArrayList<>();
        appendChildNodes(node, nodes);
        return List.copyOf(nodes);
    }

    private void appendChildNodes(Object node, ArrayList<Object> nodes) {
        switch (node) {
            case BlockStmt blockStmt -> blockStmt.statements().forEach(statement -> appendVisitNode(statement, nodes));
            case VarDeclStmt varDeclStmt -> varDeclStmt.initializerOptional().ifPresent(expression -> appendVisitNode(expression, nodes));
            case ReturnStmt returnStmt -> returnStmt.expressionOptional().ifPresent(expression -> appendVisitNode(expression, nodes));
            case ExprStmt exprStmt -> appendVisitNode(exprStmt.expression(), nodes);
            case IfStmt ifStmt -> {
                appendVisitNode(ifStmt.condition(), nodes);
                appendVisitNode(ifStmt.thenBranch(), nodes);
                ifStmt.elseBranchOptional().ifPresent(statement -> appendVisitNode(statement, nodes));
            }
            case WhileStmt whileStmt -> {
                appendVisitNode(whileStmt.condition(), nodes);
                appendVisitNode(whileStmt.body(), nodes);
            }
            case ForStmt forStmt -> {
                forStmt.initializerOptional().ifPresent(statement -> appendVisitNode(statement, nodes));
                forStmt.conditionOptional().ifPresent(expression -> appendVisitNode(expression, nodes));
                forStmt.stepOptional().ifPresent(expression -> appendVisitNode(expression, nodes));
                appendVisitNode(forStmt.body(), nodes);
            }
            case AssignmentExpr assignmentExpr -> {
                appendVisitNode(assignmentExpr.target(), nodes);
                appendVisitNode(assignmentExpr.value(), nodes);
            }
            case BinaryExpr binaryExpr -> {
                appendVisitNode(binaryExpr.left(), nodes);
                appendVisitNode(binaryExpr.right(), nodes);
            }
            case GroupingExpr groupingExpr -> appendVisitNode(groupingExpr.expression(), nodes);
            case IndexExpr indexExpr -> {
                appendVisitNode(indexExpr.target(), nodes);
                appendVisitNode(indexExpr.index(), nodes);
            }
            case FieldAccessExpr fieldAccessExpr -> appendVisitNode(fieldAccessExpr.target(), nodes);
            case UnaryExpr unaryExpr -> appendVisitNode(unaryExpr.operand(), nodes);
            case VaStartExpr vaStartExpr -> {
                appendVisitNode(vaStartExpr.list(), nodes);
                appendVisitNode(vaStartExpr.lastParameter(), nodes);
            }
            case VaArgExpr vaArgExpr -> appendVisitNode(vaArgExpr.list(), nodes);
            case VaCopyExpr vaCopyExpr -> {
                appendVisitNode(vaCopyExpr.destination(), nodes);
                appendVisitNode(vaCopyExpr.source(), nodes);
            }
            case VaEndExpr vaEndExpr -> appendVisitNode(vaEndExpr.list(), nodes);
            case CallExpr callExpr -> {
                appendVisitNode(callExpr.callee(), nodes);
                callExpr.arguments().forEach(argument -> appendVisitNode(argument, nodes));
            }
            default -> {
                // Leaf AST nodes do not enqueue additional visit actions.
            }
        }
    }

    private void appendVisitNode(Object node, ArrayList<Object> nodes) {
        nodes.add(node);
        appendChildNodes(node, nodes);
    }

    private Scope innermostScopeFor(SourceRange range, Scope root) {
        Scope best = root;
        for (Scope child : root.children()) {
            if (contains(child, range)) {
                best = innermostScopeFor(range, child);
            }
        }
        return best;
    }

    private boolean contains(Scope scope, SourceRange range) {
        return scope.range()
                .filter(scopeRange -> scopeRange.contains(range))
                .isPresent();
    }

    private SourceRange rangeOf(Object node) {
        return switch (node) {
            case Statement statement -> statement.range();
            case Expression expression -> expression.range();
            case FunctionDecl functionDecl -> functionDecl.range();
            case Program sourceProgram -> sourceProgram.range();
            default -> throw new IllegalArgumentException("unsupported AST node: " + node.getClass().getSimpleName());
        };
    }

    private record PlannedAction(SemanticActionKind kind, FunctionDecl functionDecl, Statement statement, Object astNode) {
        private PlannedAction {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(functionDecl, "functionDecl");
        }

        private static PlannedAction function(FunctionDecl functionDecl) {
            return new PlannedAction(SemanticActionKind.ANALYZE_FUNCTION_BODY, functionDecl, null, null);
        }

        private static PlannedAction statement(FunctionDecl functionDecl, Statement statement) {
            return new PlannedAction(
                    SemanticActionKind.ANALYZE_STATEMENT,
                    functionDecl,
                    Objects.requireNonNull(statement, "statement"),
                    null
            );
        }

        private static PlannedAction visit(FunctionDecl functionDecl, Object astNode) {
            return new PlannedAction(
                    SemanticActionKind.VISIT_AST_NODE,
                    functionDecl,
                    null,
                    Objects.requireNonNull(astNode, "astNode")
            );
        }

        private static PlannedAction returnCheck(FunctionDecl functionDecl) {
            return new PlannedAction(SemanticActionKind.VALIDATE_FUNCTION_RETURN, functionDecl, null, null);
        }
    }
}
