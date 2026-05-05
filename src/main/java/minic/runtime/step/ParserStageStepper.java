package minic.runtime.step;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.decl.StructDecl;
import minic.compiler.lexer.Token;
import minic.compiler.parser.ParserStep;
import minic.compiler.parser.ParserStepState;
import minic.compiler.parser.ParserTraceEvent;
import minic.diagnostics.Diagnostic;
import minic.source.SourceRange;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Parser 阶段统一兼容层适配器。
 */
public final class ParserStageStepper implements StageStepper {
    private final ParserStepState parserState;
    private Program previewProgram;
    private List<ParserTraceEvent> traceEvents = List.of();
    private List<ParserTraceEvent> buildEvents = List.of();
    private List<Object> revealPlan = List.of();
    private List<ParserTraceEvent> revealEventPlan = List.of();
    private int revealIndex = -1;
    private ParserTraceEvent currentTraceEvent;
    private Object currentBuiltNode;
    private String currentRevealLabel = "";
    private final Set<Object> revealedNodeSet = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayList<Object> revealedNodeList = new ArrayList<>();
    private final ArrayList<String> revealedNodeLabels = new ArrayList<>();
    private long globalStepIndex;
    private StepResult lastResult;

    /**
     * 创建 Parser 阶段适配器。
     *
     * @param tokens lexer 产出的 token 列表
     */
    public ParserStageStepper(List<Token> tokens) {
        parserState = new ParserStepState(Objects.requireNonNull(tokens, "tokens"), true);
        lastResult = StepResult.advanced(CompileStage.PARSER, "语法分析待开始", "等待解析第一个顶层声明。");
    }

    @Override
    public CompileStage stage() {
        return CompileStage.PARSER;
    }

    @Override
    public boolean canNext() {
        ensurePreview();
        return revealIndex < revealPlan.size() - 1;
    }

    @Override
    public StepResult next() {
        ensurePreview();
        if (!prepareNextReveal()) {
            lastResult = StepResult.cannotAdvance(CompileStage.PARSER, "递归下降过程已完成", "没有更多 parser trace 事件。");
            return lastResult;
        }
        currentBuiltNode = revealPlan.get(revealIndex);
        if (revealedNodeSet.add(currentBuiltNode)) {
            revealedNodeList.add(currentBuiltNode);
        }
        currentRevealLabel = "build " + nodeSummary(currentBuiltNode);
        if (revealedNodeLabels.isEmpty() || !revealedNodeLabels.getLast().equals(currentRevealLabel)) {
            revealedNodeLabels.add(currentRevealLabel);
        }
        globalStepIndex++;
        lastResult = !canNext()
                ? StepResult.stageCompleted(CompileStage.PARSER, "递归下降过程完成", currentRevealLabel)
                : StepResult.advanced(CompileStage.PARSER, "递归下降构建 AST", currentRevealLabel);
        return lastResult;
    }

    @Override
    public CurrentStepState snapshot() {
        return new CurrentStepState(
                sourceName(),
                CompileStage.PARSER,
                globalStepIndex,
                completedSteps(),
                PlaybackMode.PAUSED,
                Duration.ofSeconds(1),
                currentRange(),
                lastResult.title(),
                lastResult.description(),
                lastResult.diagnostics(),
                new StepCapabilities(canNext(), false, canNext(), canNext(), true, false)
        );
    }

    @Override
    public StageStepData data() {
        return new StageStepData(
                CompileStage.PARSER,
                new StageProgress(completedSteps(), totalSteps(), !canNext()),
                tokenSummary(),
                !currentRevealLabel.isBlank()
                        ? currentRevealLabel
                        : currentTraceEventOptional().map(ParserTraceEvent::label)
                        .or(() -> parserState.currentNode().map(ParserStageStepper::nodeSummary))
                        .orElseGet(() -> parserState.diagnostics().isEmpty() ? "" : diagnosticSummary(parserState.diagnostics().getLast())),
                accumulatedOutput(),
                parserState.diagnostics()
        );
    }

    /**
     * 返回底层 parser 状态，供后续阶段读取解析结果。
     *
     * @return parser 状态
     */
    public ParserStepState parserState() {
        return parserState;
    }

    /**
     * 返回 Parser 阶段用于 UI 预览的完整 AST。
     *
     * @return AST 程序
     */
    public Program previewProgram() {
        ensurePreview();
        return previewProgram;
    }

    /**
     * 返回当前 AST 观察节点。
     *
     * @return 当前观察节点 Optional
     */
    public java.util.Optional<Object> currentObservationNode() {
        return java.util.Optional.ofNullable(currentBuiltNode);
    }

    /**
     * 返回当前已经显示在 AST 构建视图中的节点。
     *
     * @return 已显示节点列表
     */
    public List<Object> revealedAstNodes() {
        ensurePreview();
        return List.copyOf(revealedNodeList);
    }

    /**
     * 返回当前递归下降 trace 事件。
     *
     * @return trace 事件 Optional
     */
    public java.util.Optional<ParserTraceEvent> currentTraceEvent() {
        return currentTraceEventOptional();
    }

    private String sourceName() {
        if (parserState.input().tokens().isEmpty()) {
            return "<unknown>";
        }
        return parserState.input().tokens().getFirst().range().sourceFile().path();
    }

    private SourceRange currentRange() {
        SourceRange builtNodeRange = currentBuiltNode == null ? null : nodeRange(currentBuiltNode);
        if (builtNodeRange != null) {
            return builtNodeRange;
        }
        return parserState.currentNode()
                .map(ParserStageStepper::nodeRange)
                .or(() -> currentTraceEventOptional().map(ParserTraceEvent::range))
                .or(() -> parserState.diagnostics().isEmpty()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(parserState.diagnostics().getLast().range()))
                .orElse(null);
    }

    private void ensurePreview() {
        if (previewProgram != null) {
            return;
        }
        while (parserState.canNext()) {
            parseNextTopLevelNode();
        }
        previewProgram = parserState.toParseResult().program();
        traceEvents = parserState.traceEvents();
        buildEvents = traceEvents.stream()
                .filter(event -> event.node() != null)
                .toList();
        buildRevealPlan();
    }

    private void parseNextTopLevelNode() {
        int diagnosticsBefore = parserState.diagnostics().size();
        ParserStep step = parserState.next();
        List<Diagnostic> newDiagnostics = parserState.diagnostics().stream()
                .skip(diagnosticsBefore)
                .toList();
        if (!newDiagnostics.isEmpty()) {
            lastResult = StepResult.failed(
                    CompileStage.PARSER,
                    "语法诊断",
                    newDiagnostics.getLast().message(),
                    newDiagnostics
            );
            return;
        }
        if (step.nodeOptional().isEmpty()) {
            lastResult = StepResult.advanced(CompileStage.PARSER, "跳过无效声明", "错误恢复推进到下一个顶层声明。");
        }
    }

    private long completedSteps() {
        return revealedNodeList.size();
    }

    private long totalSteps() {
        ensurePreview();
        return revealPlan.size();
    }

    private List<String> accumulatedOutput() {
        ensurePreview();
        return List.copyOf(revealedNodeLabels);
    }

    private java.util.Optional<ParserTraceEvent> currentTraceEventOptional() {
        return java.util.Optional.ofNullable(currentTraceEvent);
    }

    private List<String> tokenSummary() {
        List<Token> tokens = parserState.input().tokens();
        return List.of(
                "tokens=" + tokens.size(),
                "first=" + tokenSummary(tokens.getFirst()),
                "last=" + tokenSummary(tokens.getLast())
        );
    }

    private static String tokenSummary(Token token) {
        String lexeme = token.lexeme().isEmpty() ? "<empty>" : token.lexeme();
        return token.kind() + " " + lexeme;
    }

    private static String nodeSummary(Object node) {
        if (node instanceof Program program) {
            return "Program decls=" + (program.structs().size() + program.functions().size());
        }
        if (node instanceof StructDecl structDecl) {
            return "StructDecl " + structDecl.name() + " fields=" + structDecl.fields().size();
        }
        if (node instanceof FunctionDecl functionDecl) {
            String kind = functionDecl.external() ? "extern" : functionDecl.hasBody() ? "function" : "declaration";
            return "FunctionDecl " + functionDecl.name() + " " + kind + " params=" + functionDecl.parameters().size();
        }
        String kind = node.getClass().getSimpleName();
        for (String componentName : List.of("name", "operator", "value", "literalValue")) {
            Object value = componentValue(node, componentName);
            if (value != null) {
                return kind + " " + value;
            }
        }
        return node.getClass().getSimpleName();
    }

    private boolean prepareNextReveal() {
        if (revealIndex >= revealPlan.size() - 1) {
            return false;
        }
        revealIndex++;
        currentTraceEvent = revealEventPlan.get(revealIndex);
        return true;
    }

    private void buildRevealPlan() {
        ArrayList<Object> nodes = new ArrayList<>();
        ArrayList<ParserTraceEvent> events = new ArrayList<>();
        Set<Object> plannedNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        IdentityHashMap<Object, Object> parents = parentIndex(previewProgram);
        for (ParserTraceEvent event : buildEvents) {
            List<Object> path = pathTo(event.node(), parents);
            if (path.isEmpty()) {
                addPlannedNode(nodes, events, plannedNodes, event.node(), event);
            } else {
                for (Object node : path) {
                    if (!(node instanceof Program)) {
                        addPlannedNode(nodes, events, plannedNodes, node, event);
                    }
                }
            }
        }
        revealPlan = List.copyOf(nodes);
        revealEventPlan = List.copyOf(events);
    }

    private static IdentityHashMap<Object, Object> parentIndex(Object root) {
        IdentityHashMap<Object, Object> parents = new IdentityHashMap<>();
        indexParents(root, null, parents);
        return parents;
    }

    private static void indexParents(Object node, Object parent, IdentityHashMap<Object, Object> parents) {
        if (node == null || parents.containsKey(node)) {
            return;
        }
        parents.put(node, parent);
        for (Object child : astChildren(node)) {
            indexParents(child, node, parents);
        }
    }

    private static List<Object> pathTo(Object target, IdentityHashMap<Object, Object> parents) {
        if (!parents.containsKey(target)) {
            return List.of();
        }
        ArrayList<Object> reversed = new ArrayList<>();
        Object current = target;
        while (current != null) {
            reversed.add(current);
            current = parents.get(current);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static void addPlannedNode(
            ArrayList<Object> nodes,
            ArrayList<ParserTraceEvent> events,
            Set<Object> plannedNodes,
            Object node,
            ParserTraceEvent event
    ) {
        if (plannedNodes.add(node)) {
            nodes.add(node);
            events.add(event);
        }
    }

    private static Object componentValue(Object node, String name) {
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            if (component.getName().equals(name)) {
                Object value = read(component, node);
                if (value != null && !(value instanceof SourceRange) && !isAstNode(value) && !(value instanceof List<?>)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Collection<Object> astChildren(Object node) {
        ArrayList<Object> children = new ArrayList<>();
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            Object value = read(component, node);
            if (isAstNode(value)) {
                children.add(value);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (isAstNode(item)) {
                        children.add(item);
                    }
                }
            }
        }
        return children;
    }

    private static Object read(RecordComponent component, Object node) {
        try {
            return component.getAccessor().invoke(node);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("cannot read AST component: " + component.getName(), exception);
        }
    }

    private static boolean isAstNode(Object value) {
        return value != null && value.getClass().getPackageName().startsWith("minic.compiler.ast.");
    }

    private static SourceRange nodeRange(Object node) {
        if (node instanceof StructDecl structDecl) {
            return structDecl.range();
        }
        if (node instanceof FunctionDecl functionDecl) {
            return functionDecl.range();
        }
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            if (component.getName().equals("range")) {
                Object value = read(component, node);
                if (value instanceof SourceRange sourceRange) {
                    return sourceRange;
                }
            }
        }
        return null;
    }

    private static String diagnosticSummary(Diagnostic diagnostic) {
        return diagnostic.code() + " " + diagnostic.message();
    }
}
