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
import java.util.ArrayDeque;
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
    private int traceIndex = -1;
    private ParserTraceEvent currentTraceEvent;
    private Object currentBuiltNode;
    private String currentRevealLabel = "";
    private final ArrayDeque<Object> pendingRevealNodes = new ArrayDeque<>();
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
        return !pendingRevealNodes.isEmpty() || traceIndex < buildEvents.size() - 1;
    }

    @Override
    public StepResult next() {
        ensurePreview();
        if (!prepareNextReveal()) {
            lastResult = StepResult.cannotAdvance(CompileStage.PARSER, "递归下降过程已完成", "没有更多 parser trace 事件。");
            return lastResult;
        }
        currentBuiltNode = pendingRevealNodes.removeFirst();
        revealedNodeSet.add(currentBuiltNode);
        revealedNodeList.add(currentBuiltNode);
        currentRevealLabel = "build " + nodeSummary(currentBuiltNode);
        revealedNodeLabels.add(currentRevealLabel);
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
        return countRevealableNodes();
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
        while (pendingRevealNodes.isEmpty() && traceIndex < buildEvents.size() - 1) {
            traceIndex++;
            currentTraceEvent = buildEvents.get(traceIndex);
            Object node = currentTraceEvent.node();
            if (node != null) {
                enqueueRevealPath(node);
            }
        }
        return !pendingRevealNodes.isEmpty();
    }

    private void enqueueRevealPath(Object targetNode) {
        List<Object> path = pathTo(previewProgram, targetNode);
        if (path.isEmpty()) {
            if (!revealedNodeSet.contains(targetNode)) {
                pendingRevealNodes.add(targetNode);
            }
            return;
        }
        for (Object node : path) {
            if (node instanceof Program || revealedNodeSet.contains(node) || pendingRevealNodes.contains(node)) {
                continue;
            }
            pendingRevealNodes.addLast(node);
        }
    }

    private static List<Object> pathTo(Object current, Object target) {
        if (current == target) {
            return List.of(current);
        }
        for (Object child : astChildren(current)) {
            List<Object> childPath = pathTo(child, target);
            if (!childPath.isEmpty()) {
                ArrayList<Object> path = new ArrayList<>();
                path.add(current);
                path.addAll(childPath);
                return path;
            }
        }
        return List.of();
    }

    private long countRevealableNodes() {
        Set<Object> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ParserTraceEvent event : buildEvents) {
            List<Object> path = pathTo(previewProgram, event.node());
            for (Object node : path) {
                if (!(node instanceof Program)) {
                    nodes.add(node);
                }
            }
        }
        return nodes.size();
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
