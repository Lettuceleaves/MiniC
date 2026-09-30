package minic.compiler.ir;

import minic.compiler.CompilerApi;
import minic.compiler.Stage;
import minic.compiler.ir.manager.FunctionManager;
import minic.compiler.ir.manager.IrFunctionSignature;
import minic.compiler.ir.manager.IrTypeLowerer;
import minic.compiler.ir.manager.GlobalDataLowerer;
import minic.compiler.ir.manager.StringLiteralRegistry;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.model.IrGlobalData;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.compiler.semantic.model.StructLayout;
import minic.compiler.type.MiniType;
import minic.SourceRange;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 可逐步执行的 IR lowering 阶段。
 */
public final class IrLowerer extends Stage {
    private final SemanticAnalyzer semanticAnalyzer;
    private Input input;
    private Work work;
    private String currentOperationName = "";
    private String currentSubject = "";
    private AstNode currentAstNode;
    private IrResult result;
    private int nextFunctionIndex;
    private FunctionManager currentFunctionManager;
    private FunctionDecl currentFunction;
    private List<Statement> currentStatements = List.of();
    private int nextStatementIndex;
    private int completedStepCount;
    private boolean completed;

    /**
     * 创建 IR lowering 状态。
     *
     * @param program AST 程序
     * @param semanticResult 语义结果
     */
    public IrLowerer() {
        semanticAnalyzer = null;
    }

    /** 创建由 SemanticAnalyzer 提供输入的 IR 阶段。 */
    public IrLowerer(SemanticAnalyzer semanticAnalyzer) {
        this.semanticAnalyzer = Objects.requireNonNull(semanticAnalyzer, "semanticAnalyzer");
    }

    public IrLowerer(Program program, SemanticResult semanticResult) {
        semanticAnalyzer = null;
        initialize(program, semanticResult);
    }

    /**
     * 创建 IR lowering 状态。
     *
     * @param program AST 程序
     * @param structLayouts 结构体布局
     * @param expressionTypes 表达式类型
     */
    public IrLowerer(
            Program program,
            Map<String, StructLayout> structLayouts,
            Map<Expression, MiniType> expressionTypes
    ) {
        semanticAnalyzer = null;
        initialize(program, structLayouts, expressionTypes);
    }

    /** 使用指定 AST 和语义结果执行完整 IR lowering。 */
    public IrResult lower(Program program, SemanticResult semanticResult) {
        initialize(program, semanticResult);
        return lower();
    }

    /** 不携带语义映射地执行完整 IR lowering。 */
    public IrResult lower(Program program) {
        initialize(program, Map.of(), Map.of());
        return lower();
    }

    /** 执行当前输入的完整 IR lowering。 */
    public IrResult lower() {
        new CompilerApi(List.of(this)).run();
        return result();
    }

    public Input input() {
        ensureInitialized();
        return input;
    }

    public Work work() {
        ensureInitialized();
        return work;
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    /**
     * 推进一个 IR lowering 结构动作。
     *
     */
    @Override
    public SourceRange step() {
        if (!canNext()) {
            throw new IllegalStateException("IR lowering is already completed");
        }
        ensureInitialized();
        clearCurrentOperation();
        if (currentFunctionManager != null) {
            if (nextStatementIndex < currentStatements.size()) {
                Statement statement = currentStatements.get(nextStatementIndex++);
                currentFunctionManager.lower(statement);
                setCurrentOperation(
                        "LOWER_STATEMENT",
                        currentFunction.name() + " " + statement.getClass().getSimpleName(),
                        statement
                );
                completedStepCount++;
                SourceRange range = statement.range();
                return finishStep(range, currentOperationName, List.of(), this::currentResult);
            }
            SourceRange functionRange = currentFunction.range();
            work.functions.add(currentFunctionManager.complete());
            setCurrentOperation("COMPLETE_FUNCTION", currentFunction.name(), currentFunction);
            currentFunctionManager = null;
            currentFunction = null;
            currentStatements = List.of();
            nextStatementIndex = 0;
            nextFunctionIndex++;
            completedStepCount++;
            return finishStep(functionRange, currentOperationName, List.of(), this::currentResult);
        }
        if (nextFunctionIndex < input.program.functions().size()) {
            FunctionDecl function = input.program.functions().get(nextFunctionIndex);
            if (function.hasBody()) {
                currentFunction = function;
                currentFunctionManager = new FunctionManager(
                        function,
                        work.stringLiteralRegistry,
                        input.structLayouts,
                        input.expressionTypes,
                        work.functionSignatures,
                        collectGlobalTypes(input.program)
                );
                currentFunctionManager.begin();
                currentStatements = function.bodyOptional().orElseThrow().statements();
                nextStatementIndex = 0;
                setCurrentOperation("BEGIN_FUNCTION", function.name(), function);
                completedStepCount++;
                SourceRange range = function.range();
                return finishStep(range, currentOperationName, List.of(), this::currentResult);
            }
            boolean definedInProgram = input.program.functions().stream()
                    .anyMatch(candidate -> candidate.name().equals(function.name()) && candidate.hasBody());
            if (definedInProgram) {
                setCurrentOperation("REGISTER_DECLARATION", function.name(), function);
            } else {
                work.externalFunctionNames.add(function.name());
                setCurrentOperation("REGISTER_EXTERNAL", function.name(), function);
            }
            nextFunctionIndex++;
            completedStepCount++;
            SourceRange range = function.range();
            return finishStep(range, currentOperationName, List.of(), this::currentResult);
        }
        result = buildResult();
        completed = true;
        completedStepCount++;
        return finishStep(null, "", List.of(), () -> result);
    }

    /**
     * 返回 IR 阶段最终结果。
     *
     * @return IR 阶段最终结果
     */
    public IrResult result() {
        if (result == null) {
            throw new IllegalStateException("IR result is not available before lowering completes");
        }
        return result;
    }

    /**
     * 返回当前已产出的 IR 结果快照，不推进 lowering 状态。
     *
     * @return 当前 IR 结果快照
     */
    public IrResult currentResult() {
        ensureInitialized();
        ArrayList<IrFunction> functions = new ArrayList<>(work.functions);
        if (currentFunctionManager != null) {
            functions.add(currentFunctionManager.snapshot());
        }
        return new IrResult(
                functions,
                work.stringLiteralRegistry.stringData(),
                work.globalData,
                work.externalFunctionNames,
                work.externalObjectNames,
                input.structLayouts,
                currentAstNode,
                currentSubject,
                input.displayNames
        );
    }

    public int completedStepCount() {
        return completedStepCount;
    }

    public int plannedStepCount() {
        ensureInitialized();
        return plannedActionCount();
    }

    private void initialize(
            Program program,
            Map<String, StructLayout> structLayouts,
            Map<Expression, MiniType> expressionTypes
    ) {
        initialize(program, structLayouts, expressionTypes, Map.of(), Map.of());
    }

    private void initialize(Program source, SemanticResult semantic) {
        if (source != semantic.program() && source != semantic.sourceProgram()) {
            throw new IllegalArgumentException("semantic result belongs to a different program");
        }
        var coreToSource = new java.util.IdentityHashMap<AstNode, AstNode>();
        semantic.sourceToCore().forEach((original, core) -> coreToSource.put(core, original));
        initialize(semantic.program(), semantic.structLayouts(), semantic.expressionTypes(),
                semantic.displayNames(), coreToSource);
    }

    private void initialize(Program program, Map<String, StructLayout> structLayouts,
                            Map<Expression, MiniType> expressionTypes, Map<String, String> displayNames,
                            Map<AstNode, AstNode> coreToSource) {
        if (program.languageMode() != minic.compiler.LanguageMode.C) {
            throw new IllegalArgumentException("C++ AST requires name binding; provide its SemanticResult");
        }
        input = new Input(program, structLayouts, expressionTypes, displayNames, coreToSource);
        work = new Work(collectFunctionSignatures(program),
                new GlobalDataLowerer(structLayouts).lower(program.globals()));
        work.externalObjectNames.addAll(collectExternalObjectNames(program));
        clearCurrentOperation();
        result = null;
        nextFunctionIndex = 0;
        currentFunctionManager = null;
        currentFunction = null;
        currentStatements = List.of();
        nextStatementIndex = 0;
        completedStepCount = 0;
        completed = false;
    }

    private void ensureInitialized() {
        if (input != null) {
            return;
        }
        if (semanticAnalyzer == null) {
            throw new IllegalStateException("IR lowerer has no semantic input");
        }
        if (semanticAnalyzer.canNext()) {
            throw new IllegalStateException("semantic analyzer has not completed");
        }
        if (!semanticAnalyzer.succeeded()) {
            throw new IllegalStateException("semantic analyzer did not succeed");
        }
        SemanticResult semanticResult = semanticAnalyzer.semanticResult();
        initialize(semanticAnalyzer.program(), semanticResult);
    }

    private IrResult buildResult() {
        IrReachability.Result reachable = IrReachability.prune(
                work.functions,
                work.externalFunctionNames
        );
        return new IrResult(
                reachable.functions(),
                work.stringLiteralRegistry.stringData(),
                work.globalData,
                reachable.externalFunctionNames(),
                work.externalObjectNames,
                input.structLayouts,
                null,
                "",
                input.displayNames
        );
    }

    private void setCurrentOperation(String operationName, String subject, AstNode astNode) {
        currentOperationName = Objects.requireNonNull(operationName, "operationName");
        currentSubject = minic.compiler.SymbolNames.displayText(Objects.requireNonNull(subject, "subject"), input.displayNames);
        currentAstNode = input.coreToSource.getOrDefault(astNode, astNode);
        work.loweringLog.add(operationName + " " + currentSubject);
    }

    private void clearCurrentOperation() {
        currentOperationName = "";
        currentSubject = "";
        currentAstNode = null;
    }

    private static Map<String, IrFunctionSignature> collectFunctionSignatures(Program program) {
        java.util.LinkedHashMap<String, IrFunctionSignature> signatures = new java.util.LinkedHashMap<>();
        for (FunctionDecl function : program.functions()) {
            ArrayList<IrType> parameterTypes = new ArrayList<>();
            boolean structReturn = function.returnType().isStruct();
            if (structReturn) {
                parameterTypes.add(IrType.POINTER);
            }
            for (var parameter : function.parameters()) {
                parameterTypes.add(parameter.type().isStruct()
                        ? IrType.POINTER
                        : IrTypeLowerer.lower(parameter.type()));
            }
            IrType irReturnType = structReturn ? IrType.POINTER : IrTypeLowerer.lower(function.returnType());
            signatures.put(function.name(), new IrFunctionSignature(
                    irReturnType,
                    parameterTypes,
                    function.variadic(),
                    function.returnType().isVoid()
            ));
        }
        return signatures;
    }

    private static Map<String, MiniType> collectGlobalTypes(Program program) {
        java.util.LinkedHashMap<String, MiniType> globals = new java.util.LinkedHashMap<>();
        program.globals().forEach(global -> globals.put(global.name(), global.type()));
        return Map.copyOf(globals);
    }

    private static Set<String> collectExternalObjectNames(Program program) {
        java.util.LinkedHashMap<String, List<minic.compiler.parser.node.Declaration.GlobalVarDecl>> groups =
                new java.util.LinkedHashMap<>();
        program.globals().forEach(global -> groups.computeIfAbsent(global.name(), ignored -> new ArrayList<>()).add(global));
        LinkedHashSet<String> result = new LinkedHashSet<>();
        groups.forEach((name, declarations) -> {
            boolean hasDefinition = declarations.stream().anyMatch(global -> !global.external()
                    || global.initializerOptional().isPresent());
            if (!hasDefinition) result.add(name);
        });
        return Set.copyOf(result);
    }

    private int plannedActionCount() {
        int count = 1;
        for (FunctionDecl function : input.program.functions()) {
            if (function.hasBody()) {
                count += 2 + function.bodyOptional().orElseThrow().statements().size();
            } else {
                count++;
            }
        }
        return count;
    }

    /**
     * IR 阶段输入数据。
     *
     * @param program AST 程序
     * @param structLayouts 结构体布局
     * @param expressionTypes 表达式类型
     */
    public record Input(
            Program program,
            Map<String, StructLayout> structLayouts,
            Map<Expression, MiniType> expressionTypes,
            Map<String, String> displayNames,
            Map<AstNode, AstNode> coreToSource
    ) {
        /**
         * 创建输入数据。
         *
         * @param program AST 程序
         * @param structLayouts 结构体布局
         * @param expressionTypes 表达式类型
         */
        public Input {
            Objects.requireNonNull(program, "program");
            Objects.requireNonNull(structLayouts, "structLayouts");
            Objects.requireNonNull(expressionTypes, "expressionTypes");
            structLayouts = Map.copyOf(structLayouts);
            expressionTypes = java.util.Collections.unmodifiableMap(
                    new java.util.IdentityHashMap<>(expressionTypes)
            );
            displayNames = Map.copyOf(displayNames);
            coreToSource = java.util.Collections.unmodifiableMap(new java.util.IdentityHashMap<>(coreToSource));
        }

        public Input(Program program, Map<String, StructLayout> structLayouts, Map<Expression, MiniType> expressionTypes) {
            this(program, structLayouts, expressionTypes, Map.of(), Map.of());
        }
    }

    /**
     * IR 阶段内部工作数据。
     */
    public static final class Work {
        private final ArrayList<IrFunction> functions = new ArrayList<>();
        private final LinkedHashSet<String> externalFunctionNames = new LinkedHashSet<>();
        private final ArrayList<String> loweringLog = new ArrayList<>();
        private final StringLiteralRegistry stringLiteralRegistry = new StringLiteralRegistry();
        private final Map<String, IrFunctionSignature> functionSignatures;
        private final List<IrGlobalData> globalData;
        private final Set<String> externalObjectNames = new LinkedHashSet<>();

        private Work(Map<String, IrFunctionSignature> functionSignatures, List<IrGlobalData> globalData) {
            this.functionSignatures = Map.copyOf(functionSignatures);
            this.globalData = List.copyOf(globalData);
        }

        /**
         * 返回已产出函数数量。
         *
         * @return 函数数量
         */
        public int functionCount() {
            return functions.size();
        }

        /**
         * 返回已注册外部函数数量。
         *
         * @return 外部函数数量
         */
        public int externalFunctionCount() {
            return externalFunctionNames.size();
        }

        /**
         * 返回已产出 IR 函数摘要。
         *
         * @return IR 函数摘要
         */
        public List<String> functionSummaries() {
            return functions.stream()
                    .map(function -> function.name()
                            + " blocks=" + function.blocks().size()
                            + " instructions=" + function.blocks().stream()
                            .mapToInt(block -> block.instructions().size())
                            .sum())
                    .toList();
        }

        /**
         * 返回已注册外部函数名称。
         *
         * @return 外部函数名称
         */
        public List<String> externalFunctionNames() {
            return List.copyOf(externalFunctionNames);
        }

        /**
         * 返回逐步 lowering 输出。
         *
         * @return lowering 输出
         */
        public List<String> loweringLog() {
            return List.copyOf(loweringLog);
        }
    }

}
