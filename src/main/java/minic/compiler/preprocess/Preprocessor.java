package minic.compiler.preprocess;

import minic.compiler.CompilerApi;
import minic.compiler.Stage;
import minic.diagnostics.Diagnostic;
import minic.compiler.SourceFile;
import minic.source.SourceRange;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * MiniC 轻量预编译器，负责逐行扫描和三个预编译 Manager 的统一调度。
 */
public final class Preprocessor extends Stage {
    private final ConditionalCompilationManager conditionalCompilationManager = new ConditionalCompilationManager();
    private final TextReplacementManager textReplacementManager = new TextReplacementManager();
    private final IncludeManager includeManager = new IncludeManager(this);

    private SourceFile sourceFile;
    private Path currentDirectory;
    private Set<Path> includeStack;
    private Work work;
    private StringBuilder output;
    private int initialConditionDepth;
    private int lineStart;
    private long stepCount;
    private String currentOperation = "";
    private Diagnostic currentDiagnostic;
    private PreprocessResult preprocessResult;
    private boolean sourceCompleted;
    private boolean completed;

    /** 创建未绑定源码的预编译器。 */
    public Preprocessor() {
    }

    /**
     * 创建可逐步执行的预编译器。
     *
     * @param sourceFile 原始源码
     * @param options 预编译选项
     */
    public Preprocessor(SourceFile sourceFile, Options options) {
        begin(sourceFile, options);
    }

    /**
     * 对源码执行默认预编译。
     *
     * @param sourceFile 原始源码
     * @return 预编译结果
     */
    public PreprocessResult preprocess(SourceFile sourceFile) {
        return preprocess(sourceFile, Options.defaults());
    }

    /**
     * 对源码执行预编译。该方法只负责循环调用 {@link #step()}。
     *
     * @param sourceFile 原始源码
     * @param options 预编译选项
     * @return 预编译结果
     */
    public PreprocessResult preprocess(SourceFile sourceFile, Options options) {
        begin(sourceFile, options);
        new CompilerApi(List.of(this)).run();
        return preprocessResult();
    }

    /**
     * 重置并开始一次新的预编译。
     *
     * @param sourceFile 原始源码
     * @param options 预编译选项
     */
    public void begin(SourceFile sourceFile, Options options) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(options, "options");
        currentDirectory = sourceDirectory(sourceFile);
        includeStack = new HashSet<>();
        work = new Work(options);
        output = new StringBuilder();
        initialConditionDepth = work.conditionStack.size();
        lineStart = 0;
        stepCount = 0;
        currentOperation = "";
        currentDiagnostic = null;
        preprocessResult = null;
        sourceCompleted = false;
        completed = false;
    }

    /**
     * 每次处理一行源码。源码处理完成后先单独生成结果，
     * 下一次再发出当前 Stage 的最后空步骤。
     */
    @Override
    public SourceRange step() {
        ensureReady();
        if (!canNext()) {
            throw new IllegalStateException("preprocessor step is already completed");
        }
        currentOperation = "";
        currentDiagnostic = null;

        if (sourceCompleted) {
            completed = true;
            stepCount++;
            return null;
        }

        String content = sourceFile.content();
        if (lineStart >= content.length()) {
            int beforeDiagnostics = work.diagnostics.size();
            conditionalCompilationManager.closeUnterminatedConditions(work, initialConditionDepth);
            captureDiagnostic(beforeDiagnostics);
            preprocessResult = buildResult();
            sourceCompleted = true;
            currentOperation = "COMPLETE_PREPROCESS";
            stepCount++;
            return sourceFile.range(content.length(), content.length());
        }

        LogicalLine logicalLine = readLogicalLine(sourceFile, lineStart);
        int currentLineStart = logicalLine.startOffset();
        int beforeDiagnostics = work.diagnostics.size();
        if (logicalLine.danglingContinuation()) {
            work.diagnostics.add(diagnostic(
                    sourceFile,
                    Math.max(currentLineStart, logicalLine.endOffset() - 1),
                    logicalLine.endOffset(),
                    "反斜杠续行缺少下一行"
            ));
        }
        currentOperation = processLine(
                sourceFile,
                currentDirectory,
                includeStack,
                work,
                output,
                currentLineStart,
                logicalLine.endOffset(),
                logicalLine.directiveText(),
                logicalLine.outputText(),
                logicalLine.sourceOffsets(),
                true
        );
        lineStart = logicalLine.endOffset();
        captureDiagnostic(beforeDiagnostics);
        stepCount++;
        return sourceFile.range(currentLineStart, logicalLine.endOffset());
    }

    /** @return 当前是否还可以执行下一步 */
    @Override
    public boolean canNext() {
        return !completed;
    }

    @Override
    public boolean succeeded() {
        return completed && preprocessResult != null && preprocessResult.diagnostics().isEmpty();
    }

    /** @return 已执行的步数 */
    public long stepCount() {
        return stepCount;
    }

    /** @return 最近一步的操作名 */
    public String currentOperation() {
        return currentOperation;
    }

    /** @return 最近一步产生的诊断 */
    public Optional<Diagnostic> currentDiagnostic() {
        return Optional.ofNullable(currentDiagnostic);
    }

    /** @return 当前诊断列表 */
    public List<Diagnostic> diagnostics() {
        return work == null ? List.of() : List.copyOf(work.diagnostics);
    }

    /** @return 预编译结果是否已生成 */
    public boolean resultReady() {
        return preprocessResult != null;
    }

    /**
     * 返回预编译结果。
     *
     * @return 预编译结果
     */
    public PreprocessResult preprocessResult() {
        if (preprocessResult == null) {
            throw new IllegalStateException("preprocess result is not ready");
        }
        return preprocessResult;
    }

    /** @return 当前预编译任务对应的 IDE 原始源码 */
    public SourceFile sourceFile() {
        ensureReady();
        return sourceFile;
    }

    void expandSource(
            SourceFile sourceFile,
            Path currentDirectory,
            Set<Path> includeStack,
            Work work,
            StringBuilder output,
            boolean mapToThisSource
    ) {
        int initialDepth = work.conditionStack.size();
        int nestedLineStart = 0;
        String content = sourceFile.content();
        while (nestedLineStart < content.length()) {
            LogicalLine logicalLine = readLogicalLine(sourceFile, nestedLineStart);
            if (logicalLine.danglingContinuation()) {
                work.diagnostics.add(diagnostic(
                        sourceFile,
                        Math.max(nestedLineStart, logicalLine.endOffset() - 1),
                        logicalLine.endOffset(),
                        "反斜杠续行缺少下一行"
                ));
            }
            processLine(
                    sourceFile,
                    currentDirectory,
                    includeStack,
                    work,
                    output,
                    nestedLineStart,
                    logicalLine.endOffset(),
                    logicalLine.directiveText(),
                    logicalLine.outputText(),
                    logicalLine.sourceOffsets(),
                    mapToThisSource
            );
            nestedLineStart = logicalLine.endOffset();
        }
        conditionalCompilationManager.closeUnterminatedConditions(work, initialDepth);
    }

    private String processLine(
            SourceFile sourceFile,
            Path currentDirectory,
            Set<Path> includeStack,
            Work work,
            StringBuilder output,
            int lineStart,
            int nextLineStart,
            String directiveLine,
            String outputLine,
            int[] sourceOffsets,
            boolean mapToThisSource
    ) {
        if (conditionalCompilationManager.handleDirective(
                sourceFile, work, lineStart, nextLineStart, directiveLine, textReplacementManager)) {
            return "CONDITIONAL_COMPILATION";
        }
        if (!conditionalCompilationManager.isActive(work)) {
            return "SKIP_INACTIVE_BRANCH";
        }
        if (includeManager.handleDirective(
                sourceFile,
                currentDirectory,
                includeStack,
                work,
                output,
                lineStart,
                nextLineStart,
                directiveLine,
                mapToThisSource
        )) {
            return "INCLUDE";
        }
        if (textReplacementManager.handleDirective(sourceFile, work, lineStart, nextLineStart, directiveLine)) {
            return "TEXT_REPLACEMENT_DIRECTIVE";
        }
        textReplacementManager.appendExpandedLine(
                output,
                work,
                sourceFile,
                outputLine,
                sourceOffsets,
                mapToThisSource
        );
        return "TEXT_REPLACEMENT";
    }

    private void captureDiagnostic(int beforeDiagnostics) {
        if (work.diagnostics.size() > beforeDiagnostics) {
            currentDiagnostic = work.diagnostics.getLast();
        }
    }

    private PreprocessResult buildResult() {
        SourceFile preprocessedSource = new SourceFile(sourceFile.path(), output.toString());
        return new PreprocessResult(
                preprocessedSource,
                work.diagnostics,
                work.includes,
                work.macroSummaries,
                work.sourceMap()
        );
    }

    private void ensureReady() {
        if (sourceFile == null || work == null || output == null) {
            throw new IllegalStateException("preprocessor source is not initialized");
        }
    }

    private Path sourceDirectory(SourceFile sourceFile) {
        Path sourcePath = Path.of(sourceFile.path());
        Path parent = sourcePath.getParent();
        if (parent == null) {
            return null;
        }
        return parent.toAbsolutePath().normalize();
    }

    /** Translation phase 2: delete every backslash-newline pair before directive handling. */
    private LogicalLine readLogicalLine(SourceFile sourceFile, int startOffset) {
        String content = sourceFile.content();
        StringBuilder text = new StringBuilder();
        ArrayList<Integer> offsets = new ArrayList<>();
        int cursor = startOffset;
        int directiveLength;
        int endOffset;
        boolean danglingContinuation = false;

        while (true) {
            int newlineOffset = content.indexOf('\n', cursor);
            int physicalEnd = newlineOffset < 0 ? content.length() : newlineOffset;
            int bodyEnd = physicalEnd;
            if (newlineOffset >= 0 && bodyEnd > cursor && content.charAt(bodyEnd - 1) == '\r') {
                bodyEnd--;
            }
            boolean continued = newlineOffset >= 0
                    && bodyEnd > cursor
                    && content.charAt(bodyEnd - 1) == '\\';
            int retainedEnd = continued ? bodyEnd - 1 : bodyEnd;
            appendOriginal(text, offsets, content, cursor, retainedEnd);

            if (continued) {
                cursor = newlineOffset + 1;
                continue;
            }

            directiveLength = text.length();
            if (newlineOffset >= 0) {
                appendOriginal(text, offsets, content, bodyEnd, newlineOffset + 1);
                endOffset = newlineOffset + 1;
            } else {
                endOffset = content.length();
                danglingContinuation = bodyEnd > cursor && content.charAt(bodyEnd - 1) == '\\';
            }
            break;
        }

        int[] sourceOffsets = new int[offsets.size()];
        for (int index = 0; index < offsets.size(); index++) {
            sourceOffsets[index] = offsets.get(index);
        }
        return new LogicalLine(
                startOffset,
                endOffset,
                text.substring(0, directiveLength),
                text.toString(),
                sourceOffsets,
                danglingContinuation
        );
    }

    private void appendOriginal(
            StringBuilder target,
            List<Integer> offsets,
            String content,
            int startOffset,
            int endOffset
    ) {
        for (int offset = startOffset; offset < endOffset; offset++) {
            target.append(content.charAt(offset));
            offsets.add(offset);
        }
    }

    static Diagnostic diagnostic(SourceFile sourceFile, int startOffset, int endOffset, String message) {
        return new Diagnostic(
                "PRE001",
                Diagnostic.Severity.ERROR,
                message,
                sourceFile.range(startOffset, endOffset)
        );
    }

    static final class Work {
        final Options options;
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final List<PreprocessResult.IncludeSummary> includes = new ArrayList<>();
        final Map<String, TextReplacementManager.MacroDefinition> macros = new LinkedHashMap<>();
        final List<PreprocessResult.MacroSummary> macroSummaries = new ArrayList<>();
        final ArrayList<ConditionalCompilationManager.ConditionFrame> conditionStack = new ArrayList<>();
        final ArrayList<Integer> sourceMap = new ArrayList<>();

        Work(Options options) {
            this.options = options;
        }

        int[] sourceMap() {
            int[] result = new int[sourceMap.size()];
            for (int index = 0; index < sourceMap.size(); index++) {
                result[index] = sourceMap.get(index);
            }
            return result;
        }
    }

    private record LogicalLine(
            int startOffset,
            int endOffset,
            String directiveText,
            String outputText,
            int[] sourceOffsets,
            boolean danglingContinuation
    ) {
    }

    /**
     * 预编译阶段选项。
     *
     * @param includeRoots 显式 include 根目录
     */
    public record Options(List<Path> includeRoots) {
        public Options {
            Objects.requireNonNull(includeRoots, "includeRoots");
            includeRoots = includeRoots.stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .toList();
        }

        /**
         * 创建默认预编译选项。
         *
         * @return 默认选项
         */
        public static Options defaults() {
            return new Options(List.of());
        }
    }
}
