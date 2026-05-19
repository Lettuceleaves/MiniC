package minic.compiler.semantic;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.BreakStmt;
import minic.compiler.ast.stmt.ContinueStmt;
import minic.compiler.ast.stmt.DoWhileStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ForStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.SwitchCase;
import minic.compiler.ast.stmt.SwitchStmt;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ast.stmt.WhileStmt;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.List;
import java.util.Map;

final class StatementSemanticAnalyzer {
    private final Scope globalScope;
    private final StructRegistry structRegistry;
    private final SemanticReporter reporter;
    private final ExpressionSemanticAnalyzer expressionAnalyzer;
    private FunctionDecl currentFunction;
    private int loopDepth;
    private int switchDepth;

    StatementSemanticAnalyzer(
            Scope globalScope,
            FunctionRegistry functionRegistry,
            StructRegistry structRegistry,
            SemanticReporter reporter,
            Map<Expression, MiniType> expressionTypes
    ) {
        this.globalScope = globalScope;
        this.structRegistry = structRegistry;
        this.reporter = reporter;
        expressionAnalyzer = new ExpressionSemanticAnalyzer(functionRegistry, structRegistry, reporter, expressionTypes);
    }

    void analyzeFunction(FunctionDecl functionDecl) {
        beginFunction(functionDecl);
        try {
            for (Statement statement : functionDecl.bodyOptional().orElseThrow().statements()) {
                analyzeCurrentFunctionTopLevelStatement(statement);
            }
            validateCurrentFunctionReturn();
        } finally {
            endFunction();
        }
    }

    FunctionContext beginFunction(FunctionDecl functionDecl) {
        FunctionDecl previousFunction = currentFunction;
        currentFunction = functionDecl;
        expressionAnalyzer.setCurrentParameterNames(functionDecl.parameters().stream()
                .map(Parameter::name)
                .toList());
        Scope functionScope = new Scope(globalScope, functionDecl.bodyOptional().map(BlockStmt::range).orElse(functionDecl.range()));
        for (Parameter parameter : functionDecl.parameters()) {
            defineVariable(functionScope, parameter.name(), parameter.range(), parameter.type());
        }
        currentContext = new FunctionContext(previousFunction, functionScope);
        return currentContext;
    }

    void analyzeCurrentFunctionTopLevelStatement(Statement statement) {
        analyzeStatement(statement, currentFunctionContextScope());
    }

    Scope currentFunctionScope() {
        return currentFunctionContextScope();
    }

    void validateCurrentFunctionReturn() {
        BlockStmt body = currentFunction.bodyOptional().orElseThrow();
        if (!alwaysReturns(body)) {
            reporter.report(currentFunction.range(), "函数必须在所有路径返回值：" + currentFunction.name());
        }
    }

    void endFunction() {
        FunctionDecl previousFunction = currentContext == null ? null : currentContext.previousFunction();
        currentFunction = previousFunction;
        currentContext = null;
        expressionAnalyzer.setCurrentParameterNames(previousFunction == null
                ? List.of()
                : previousFunction.parameters().stream().map(Parameter::name).toList());
    }

    private FunctionContext currentContext;

    private Scope currentFunctionContextScope() {
        if (currentContext == null) {
            throw new IllegalStateException("function context is not active");
        }
        return currentContext.functionScope();
    }

    record FunctionContext(FunctionDecl previousFunction, Scope functionScope) {
    }

    private void analyzeBlock(BlockStmt blockStmt, Scope parentScope, boolean createChildScope) {
        Scope scope = createChildScope ? new Scope(parentScope, blockStmt.range()) : parentScope;
        for (Statement statement : blockStmt.statements()) {
            analyzeStatement(statement, scope);
        }
    }

    private void analyzeStatement(Statement statement, Scope scope) {
        switch (statement) {
            case BlockStmt blockStmt -> analyzeBlock(blockStmt, scope, true);
            case VarDeclStmt varDeclStmt -> {
                boolean unsupportedArrayInitializer = varDeclStmt.type().isArray()
                        && varDeclStmt.initializerOptional().isPresent();
                if (varDeclStmt.type().isArray() && varDeclStmt.initializerOptional().isPresent()) {
                    reporter.report(varDeclStmt.range(), "数组声明暂不支持初始化表达式");
                }
                varDeclStmt.initializerOptional()
                        .ifPresent(initializer -> {
                            MiniType initializerType = expressionAnalyzer.analyzeExpression(initializer, scope);
                            if (!unsupportedArrayInitializer
                                    && !isInitializerCompatible(varDeclStmt.type(), initializerType)) {
                                reporter.report(varDeclStmt.range(), "变量初始化类型不匹配：" + varDeclStmt.name());
                            }
                        });
                structRegistry.validateDeclaredType(varDeclStmt.type(), varDeclStmt.range());
                defineVariable(scope, varDeclStmt.name(), varDeclStmt.range(), varDeclStmt.type());
            }
            case ReturnStmt returnStmt -> {
                if (returnStmt.expressionOptional().isEmpty()) {
                    reporter.report(returnStmt.range(), "int 函数中 return 必须包含表达式");
                } else {
                    MiniType returnType = expressionAnalyzer.analyzeExpression(
                            returnStmt.expressionOptional().orElseThrow(),
                            scope
                    );
                    if (!TypeCompatibility.isAssignmentCompatible(currentFunction.returnType(), returnType)) {
                        reporter.report(returnStmt.range(), "return 类型不匹配");
                    }
                }
            }
            case ExprStmt exprStmt -> expressionAnalyzer.analyzeExpression(exprStmt.expression(), scope);
            case BreakStmt breakStmt -> {
                if (loopDepth == 0 && switchDepth == 0) {
                    reporter.report(breakStmt.range(), "break 只能在循环或 switch 内使用");
                }
            }
            case ContinueStmt continueStmt -> {
                if (loopDepth == 0) {
                    reporter.report(continueStmt.range(), "continue 只能在循环内使用");
                }
            }
            case IfStmt ifStmt -> {
                analyzeCondition(ifStmt.condition(), scope);
                analyzeBranch(ifStmt.thenBranch(), scope);
                ifStmt.elseBranchOptional().ifPresent(elseBranch -> analyzeBranch(elseBranch, scope));
            }
            case WhileStmt whileStmt -> {
                analyzeCondition(whileStmt.condition(), scope);
                analyzeLoopBranch(whileStmt.body(), scope);
            }
            case DoWhileStmt doWhileStmt -> {
                analyzeLoopBranch(doWhileStmt.body(), scope);
                analyzeCondition(doWhileStmt.condition(), scope);
            }
            case ForStmt forStmt -> analyzeFor(forStmt, scope);
            case SwitchStmt switchStmt -> analyzeSwitch(switchStmt, scope);
            default -> throw new IllegalArgumentException("unsupported statement: "
                    + statement.getClass().getSimpleName());
        }
    }

    private void analyzeSwitch(SwitchStmt switchStmt, Scope scope) {
        MiniType selectorType = expressionAnalyzer.analyzeExpression(switchStmt.selector(), scope);
        if (!selectorType.isIntegerScalar()) {
            reporter.report(switchStmt.selector().range(), "switch selector 必须是整数类型");
        }
        boolean defaultSeen = false;
        switchDepth++;
        try {
            for (SwitchCase switchCase : switchStmt.cases()) {
                if (switchCase.defaultCase()) {
                    if (defaultSeen) {
                        reporter.report(switchCase.range(), "switch 只能包含一个 default");
                    }
                    defaultSeen = true;
                } else {
                    Expression value = switchCase.valueOptional().orElseThrow();
                    MiniType caseType = expressionAnalyzer.analyzeExpression(value, scope);
                    if (!caseType.isIntegerScalar()) {
                        reporter.report(value.range(), "case 表达式必须是整数常量");
                    }
                    if (!isSupportedCaseConstant(value)) {
                        reporter.report(value.range(), "case 表达式必须是整数常量");
                    }
                }
                analyzeSwitchCaseStatements(switchCase, scope);
            }
        } finally {
            switchDepth--;
        }
    }

    private void analyzeSwitchCaseStatements(SwitchCase switchCase, Scope parentScope) {
        Scope scope = new Scope(parentScope, switchCase.range());
        for (Statement statement : switchCase.statements()) {
            analyzeStatement(statement, scope);
        }
    }

    private boolean isSupportedCaseConstant(Expression expression) {
        return expression instanceof minic.compiler.ast.expr.IntegerLiteralExpr
                || expression instanceof minic.compiler.ast.expr.LongLiteralExpr
                || expression instanceof minic.compiler.ast.expr.CharLiteralExpr
                || expression instanceof minic.compiler.ast.expr.BoolLiteralExpr;
    }

    private void analyzeFor(ForStmt forStmt, Scope parentScope) {
        Scope scope = new Scope(parentScope, forStmt.range());
        forStmt.initializerOptional().ifPresent(initializer -> analyzeStatement(initializer, scope));
        forStmt.conditionOptional().ifPresent(condition -> analyzeCondition(condition, scope));
        forStmt.stepOptional().ifPresent(step -> expressionAnalyzer.analyzeExpression(step, scope));
        analyzeLoopBranch(forStmt.body(), scope);
    }

    private void analyzeCondition(Expression condition, Scope scope) {
        MiniType conditionType = expressionAnalyzer.analyzeExpression(condition, scope);
        if (!TypeCompatibility.isConditionCompatible(conditionType)) {
            reporter.report(condition.range(), "条件表达式必须是标量或指针类型");
        }
    }

    private void analyzeLoopBranch(Statement statement, Scope parentScope) {
        loopDepth++;
        try {
            analyzeBranch(statement, parentScope);
        } finally {
            loopDepth--;
        }
    }

    private void analyzeBranch(Statement statement, Scope parentScope) {
        if (statement instanceof BlockStmt blockStmt) {
            analyzeBlock(blockStmt, parentScope, true);
        } else {
            analyzeStatement(statement, new Scope(parentScope, statement.range()));
        }
    }

    private void defineVariable(Scope scope, String name, SourceRange range, MiniType type) {
        Symbol symbol = new Symbol(name, SymbolKind.VARIABLE, range, type, null);
        if (!scope.define(symbol)) {
            reporter.report(range, "重复局部变量定义：" + name);
        }
    }

    private boolean isInitializerCompatible(MiniType targetType, MiniType initializerType) {
        return TypeCompatibility.isAssignmentCompatible(targetType, initializerType);
    }

    private boolean alwaysReturns(Statement statement) {
        if (statement instanceof ReturnStmt) {
            return true;
        }
        if (statement instanceof BlockStmt blockStmt) {
            return blockStmt.statements().stream().anyMatch(this::alwaysReturns);
        }
        if (statement instanceof IfStmt ifStmt) {
            return ifStmt.elseBranchOptional()
                    .map(elseBranch -> alwaysReturns(ifStmt.thenBranch()) && alwaysReturns(elseBranch))
                    .orElse(false);
        }
        return false;
    }
}
