package minic.compiler.semantic.manager;

import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Parameter;
import minic.compiler.parser.node.Declaration.GlobalVarDecl;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement.CleanupScopeStmt;
import minic.compiler.parser.node.Expression.AggregateInitExpr;
import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.parser.node.Statement.BreakStmt;
import minic.compiler.parser.node.Statement.ContinueStmt;
import minic.compiler.parser.node.Statement.DoWhileStmt;
import minic.compiler.parser.node.Statement.ExprStmt;
import minic.compiler.parser.node.Statement.ForStmt;
import minic.compiler.parser.node.Statement.IfStmt;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.parser.node.Statement;
import minic.compiler.parser.node.Statement.SwitchCase;
import minic.compiler.parser.node.Statement.SwitchStmt;
import minic.compiler.parser.node.Statement.VarDeclStmt;
import minic.compiler.parser.node.Statement.TypedefStmt;
import minic.compiler.parser.node.Statement.WhileStmt;
import minic.compiler.type.MiniType;
import minic.compiler.semantic.model.Scope;
import minic.compiler.semantic.model.Symbol;
import minic.compiler.semantic.model.Symbol.SymbolKind;
import minic.SourceRange;
import minic.compiler.Diagnostic;

import java.util.List;
import java.util.Map;

public final class StatementSemanticAnalyzer {
    private final Scope globalScope;
    private final FunctionRegistry functionRegistry;
    private final StructRegistry structRegistry;
    private final List<Diagnostic> diagnostics;
    private final ExpressionSemanticAnalyzer expressionAnalyzer;
    private FunctionDecl currentFunction;
    private int loopDepth;
    private int switchDepth;

    public StatementSemanticAnalyzer(
            Scope globalScope,
            FunctionRegistry functionRegistry,
            StructRegistry structRegistry,
            List<Diagnostic> diagnostics,
            Map<Expression, MiniType> expressionTypes
    ) {
        this.globalScope = globalScope;
        this.functionRegistry = functionRegistry;
        this.structRegistry = structRegistry;
        this.diagnostics = diagnostics;
        expressionAnalyzer = new ExpressionSemanticAnalyzer(functionRegistry, structRegistry, diagnostics, expressionTypes);
    }

    public void analyzeFunction(FunctionDecl functionDecl) {
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

    public FunctionContext beginFunction(FunctionDecl functionDecl) {
        FunctionDecl previousFunction = currentFunction;
        currentFunction = functionDecl;
        expressionAnalyzer.setCurrentFunction(functionDecl);
        Scope functionScope = new Scope(globalScope, functionDecl.bodyOptional().map(BlockStmt::range).orElse(functionDecl.range()));
        for (Parameter parameter : functionDecl.parameters()) {
            defineVariable(functionScope, parameter.name(), parameter.range(), parameter.type());
        }
        currentContext = new FunctionContext(previousFunction, functionScope);
        return currentContext;
    }

    public void analyzeCurrentFunctionTopLevelStatement(Statement statement) {
        analyzeStatement(statement, currentFunctionContextScope());
    }

    /** 注册并检查文件作用域对象，使所有函数共享同一组全局符号。 */
    public void analyzeGlobals(List<GlobalVarDecl> globals) {
        java.util.LinkedHashMap<String, GlobalVarDecl> declarations = new java.util.LinkedHashMap<>();
        java.util.HashSet<String> initialized = new java.util.HashSet<>();
        for (GlobalVarDecl global : globals) {
            GlobalVarDecl previous = declarations.putIfAbsent(global.name(), global);
            if (previous != null && !previous.type().equals(global.type())) {
                report(global.range(), "全局对象重复声明的类型不兼容：" + global.name());
            }
            if (global.initializerOptional().isPresent() && !initialized.add(global.name())) {
                report(global.range(), "全局对象只能定义一次：" + global.name());
            }
            structRegistry.resolveAlignment(global.alignmentSpecs(), global.type(), global.range());
        }
        declarations.values().forEach(global -> {
            if (!globalScope.define(new Symbol(global.name(), SymbolKind.VARIABLE,
                    global.range(), global.type(), null))) {
                report(global.range(), "全局名称重复：" + global.name());
            }
        });
        for (GlobalVarDecl global : globals) {
            global.initializerOptional().ifPresent(initializer -> {
                boolean aggregate = initializer instanceof AggregateInitExpr;
                if (aggregate) expressionAnalyzer.setAggregateInitTargetType(global.type());
                MiniType initializerType;
                try {
                    initializerType = expressionAnalyzer.analyzeExpression(initializer, globalScope);
                } finally {
                    expressionAnalyzer.setAggregateInitTargetType(null);
                }
                if (!aggregate && !global.type().isArray()
                        && !isInitializerCompatible(global.type(), initializerType, initializer)) {
                    report(global.range(), "全局变量初始化类型不匹配：" + global.name());
                }
            });
        }
    }

    public Scope currentFunctionScope() {
        return currentFunctionContextScope();
    }

    public void validateCurrentFunctionReturn() {
        BlockStmt body = currentFunction.bodyOptional().orElseThrow();
        if (currentFunctionIsNoReturn()) {
            if (!neverReturns(body)) {
                report(currentFunction.range(), "noreturn 函数可能返回：" + currentFunction.name());
            }
        } else if (!currentFunction.returnType().isVoid() && !alwaysReturns(body) && !neverReturns(body)) {
            report(currentFunction.range(), "函数必须在所有路径返回值：" + currentFunction.name());
        }
    }

    public void endFunction() {
        FunctionDecl previousFunction = currentContext == null ? null : currentContext.previousFunction();
        currentFunction = previousFunction;
        currentContext = null;
        expressionAnalyzer.setCurrentFunction(previousFunction);
    }

    private FunctionContext currentContext;

    private Scope currentFunctionContextScope() {
        if (currentContext == null) {
            throw new IllegalStateException("function context is not active");
        }
        return currentContext.functionScope();
    }

    public record FunctionContext(FunctionDecl previousFunction, Scope functionScope) {
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
            case Statement.DeclGroupStmt group -> group.statements().forEach(child->analyzeStatement(child,scope));
            case CleanupScopeStmt cleanup -> {
                // Bind the action before entering the body; later local shadows must not change it.
                MiniType actionType = expressionAnalyzer.analyzeExpression(cleanup.cleanup(), scope);
                if (!actionType.isVoid()) report(cleanup.cleanup().range(), "作用域清理必须是 void 表达式");
                analyzeBranch(cleanup.body(), scope);
            }
            case VarDeclStmt varDeclStmt -> {
                structRegistry.validateDeclaredType(varDeclStmt.type(), varDeclStmt.range());
                structRegistry.resolveAlignment(
                        varDeclStmt.alignmentSpecs(),
                        varDeclStmt.type(),
                        varDeclStmt.range()
                );
                // The declarator introduces its name before its initializer is analyzed.
                defineVariable(scope, varDeclStmt.name(), varDeclStmt.range(), varDeclStmt.type());
                varDeclStmt.initializerOptional()
                        .ifPresent(initializer -> {
                            boolean aggregateInitializer = initializer instanceof AggregateInitExpr;
                            if (varDeclStmt.type().isArray() && !aggregateInitializer
                                    && !Expression.ObjectInitExpr.occursInResultOf(initializer)) {
                                report(varDeclStmt.range(), "数组必须使用大括号初始化");
                            }
                            if (aggregateInitializer) {
                                expressionAnalyzer.setAggregateInitTargetType(varDeclStmt.type());
                            }
                            MiniType initializerType;
                            try {
                                initializerType = expressionAnalyzer.analyzeExpression(initializer, scope);
                            } finally {
                                expressionAnalyzer.setAggregateInitTargetType(null);
                            }
                            if (!aggregateInitializer
                                    && !varDeclStmt.type().isArray()
                                    && !isInitializerCompatible(varDeclStmt.type(), initializerType, initializer)) {
                                report(varDeclStmt.range(), "变量初始化类型不匹配：" + varDeclStmt.name());
                            }
                        });
            }
            case TypedefStmt typedefStmt -> structRegistry.validateTypedefType(
                    typedefStmt.type(),
                    typedefStmt.range()
            );
            case ReturnStmt returnStmt -> {
                if (currentFunctionIsNoReturn()) {
                    report(returnStmt.range(), "noreturn 函数不能执行 return：" + currentFunction.name());
                }
                if (currentFunction.returnType().isVoid()) {
                    if (returnStmt.expressionOptional().isPresent()) {
                        expressionAnalyzer.analyzeExpression(returnStmt.expressionOptional().orElseThrow(), scope);
                        report(returnStmt.range(), "void 函数的 return 不能包含表达式");
                    }
                } else if (returnStmt.expressionOptional().isEmpty()) {
                    report(returnStmt.range(), "非 void 函数中 return 必须包含表达式");
                } else {
                    MiniType returnType = expressionAnalyzer.analyzeExpression(
                            returnStmt.expressionOptional().orElseThrow(),
                            scope
                    );
                    if (!TypeCompatibility.isAssignmentCompatible(currentFunction.returnType(), returnType,
                            returnStmt.expressionOptional().orElseThrow())) {
                        report(returnStmt.range(), "return 类型不匹配");
                    }
                }
            }
            case ExprStmt exprStmt -> expressionAnalyzer.analyzeExpression(exprStmt.expression(), scope);
            case BreakStmt breakStmt -> {
                if (loopDepth == 0 && switchDepth == 0) {
                    report(breakStmt.range(), "break 只能在循环或 switch 内使用");
                }
            }
            case ContinueStmt continueStmt -> {
                if (loopDepth == 0) {
                    report(continueStmt.range(), "continue 只能在循环内使用");
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
            report(switchStmt.selector().range(), "switch selector 必须是整数类型");
        }
        boolean defaultSeen = false;
        // Case labels do not introduce scopes; only the switch body and explicit blocks do.
        Scope switchScope = new Scope(scope, switchStmt.range());
        switchDepth++;
        try {
            for (SwitchCase switchCase : switchStmt.cases()) {
                if (switchCase.defaultCase()) {
                    if (defaultSeen) {
                        report(switchCase.range(), "switch 只能包含一个 default");
                    }
                    defaultSeen = true;
                } else {
                    Expression value = switchCase.valueOptional().orElseThrow();
                    MiniType caseType = expressionAnalyzer.analyzeExpression(value, switchScope);
                    if (!caseType.isIntegerScalar()) {
                        report(value.range(), "case 表达式必须是整数常量");
                    }
                    if (!isSupportedCaseConstant(value)) {
                        report(value.range(), "case 表达式必须是整数常量");
                    }
                }
                analyzeSwitchCaseStatements(switchCase, switchScope);
            }
        } finally {
            switchDepth--;
        }
    }

    private void analyzeSwitchCaseStatements(SwitchCase switchCase, Scope scope) {
        for (Statement statement : switchCase.statements()) {
            analyzeStatement(statement, scope);
        }
    }

    private boolean isSupportedCaseConstant(Expression expression) {
        return expression instanceof minic.compiler.parser.node.Expression.IntegerLiteralExpr
                || expression instanceof minic.compiler.parser.node.Expression.IntegerConstantExpr
                || expression instanceof minic.compiler.parser.node.Expression.LongLiteralExpr
                || expression instanceof minic.compiler.parser.node.Expression.CharLiteralExpr
                || expression instanceof minic.compiler.parser.node.Expression.BoolLiteralExpr;
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
            report(condition.range(), "条件表达式必须是标量或指针类型");
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
            report(range, "重复局部变量定义：" + name);
        }
    }

    private boolean isInitializerCompatible(MiniType targetType, MiniType initializerType, Expression initializer) {
        return TypeCompatibility.isAssignmentCompatible(targetType, initializerType, initializer);
    }

    private boolean currentFunctionIsNoReturn() {
        return currentFunction.noReturn() || functionRegistry.isNoReturn(currentFunction.name());
    }

    private boolean alwaysReturns(Statement statement) {
        if (statement instanceof CleanupScopeStmt cleanup) return alwaysReturns(cleanup.body());
        if (statement instanceof SwitchStmt selection) return exhaustiveSwitchTerminates(selection);
        if (statement instanceof ReturnStmt) {
            return true;
        }
        if(statement instanceof Statement.DeclGroupStmt group)return group.statements().stream().anyMatch(this::alwaysReturns);
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

    private boolean neverReturns(Statement statement) {
        if(statement instanceof Statement.DeclGroupStmt group)return group.statements().stream().anyMatch(this::neverReturns);
        if (statement instanceof SwitchStmt selection) return exhaustiveSwitchTerminates(selection);
        if (statement instanceof CleanupScopeStmt cleanup)
            return neverReturns(cleanup.body()) || isNoReturnExpression(cleanup.cleanup());
        if (statement instanceof ReturnStmt) {
            // A return still terminates control flow, but is diagnosed separately for noreturn.
            return true;
        }
        if (statement instanceof BlockStmt blockStmt) {
            for (Statement child : blockStmt.statements()) {
                if (neverReturns(child)) {
                    return true;
                }
            }
            return false;
        }
        if (statement instanceof IfStmt ifStmt) {
            return ifStmt.elseBranchOptional()
                    .map(elseBranch -> neverReturns(ifStmt.thenBranch()) && neverReturns(elseBranch))
                    .orElse(false);
        }
        if (statement instanceof WhileStmt whileStmt) {
            return isNonZeroIntegerConstant(whileStmt.condition())
                    && !canBreakCurrentLoop(whileStmt.body(), 0);
        }
        if (statement instanceof DoWhileStmt doWhileStmt) {
            return isNonZeroIntegerConstant(doWhileStmt.condition())
                    && !canBreakCurrentLoop(doWhileStmt.body(), 0);
        }
        if (statement instanceof ForStmt forStmt) {
            return (forStmt.conditionOptional().isEmpty()
                    || isNonZeroIntegerConstant(forStmt.conditionOptional().orElseThrow()))
                    && !canBreakCurrentLoop(forStmt.body(), 0);
        }
        if (statement instanceof ExprStmt exprStmt) {
            return isNoReturnExpression(exprStmt.expression());
        }
        return false;
    }

    /** Every case label is a possible entry; fallthrough inherits the following case's completion. */
    private boolean exhaustiveSwitchTerminates(SwitchStmt selection) {
        if(selection.cases().stream().noneMatch(item->item.value()==null))return false;
        boolean followingTerminates=false;
        for(int index=selection.cases().size()-1;index>=0;index--) {
            followingTerminates=switchSequenceTerminates(selection.cases().get(index).statements(),followingTerminates);
            if(!followingTerminates)return false;
        }
        return true;
    }

    private boolean switchSequenceTerminates(List<Statement> statements,boolean continuation) {
        for(int index=statements.size()-1;index>=0;index--)continuation=switchStatementTerminates(statements.get(index),continuation);
        return continuation;
    }

    private boolean switchStatementTerminates(Statement statement,boolean continuation) {
        if(statement instanceof ReturnStmt)return true;
        if(statement instanceof BreakStmt||statement instanceof ContinueStmt)return false;
        if(statement instanceof CleanupScopeStmt cleanup)return switchStatementTerminates(cleanup.body(),continuation);
        if(statement instanceof BlockStmt block)return switchSequenceTerminates(block.statements(),continuation);
        if(statement instanceof IfStmt condition)return switchStatementTerminates(condition.thenBranch(),continuation)
                &&condition.elseBranchOptional().map(branch->switchStatementTerminates(branch,continuation)).orElse(continuation);
        return neverReturns(statement)||continuation;
    }

    private boolean isNoReturnExpression(Expression expression) {
        if (expression instanceof minic.compiler.parser.node.Expression.CallExpr callExpr
                && callExpr.hasDirectCalleeName()) {
            return functionRegistry.isNoReturn(callExpr.calleeName());
        }
        if (expression instanceof minic.compiler.parser.node.Expression.GroupingExpr groupingExpr) {
            return isNoReturnExpression(groupingExpr.expression());
        }
        if (expression instanceof minic.compiler.parser.node.Expression.CommaExpr commaExpr) {
            return !commaExpr.expressions().isEmpty()
                    && isNoReturnExpression(commaExpr.expressions().getLast());
        }
        if (expression instanceof minic.compiler.parser.node.Expression.ConditionalExpr conditionalExpr) {
            return isNoReturnExpression(conditionalExpr.thenExpression())
                    && isNoReturnExpression(conditionalExpr.elseExpression());
        }
        return false;
    }

    private boolean canBreakCurrentLoop(Statement statement, int nestedLoopDepth) {
        if(statement instanceof Statement.DeclGroupStmt group)return group.statements().stream().anyMatch(child->canBreakCurrentLoop(child,nestedLoopDepth));
        if (statement instanceof CleanupScopeStmt cleanup)
            return canBreakCurrentLoop(cleanup.body(), nestedLoopDepth);
        if (statement instanceof BreakStmt) {
            return nestedLoopDepth == 0;
        }
        if (statement instanceof BlockStmt blockStmt) {
            return blockStmt.statements().stream()
                    .anyMatch(child -> canBreakCurrentLoop(child, nestedLoopDepth));
        }
        if (statement instanceof IfStmt ifStmt) {
            return canBreakCurrentLoop(ifStmt.thenBranch(), nestedLoopDepth)
                    || ifStmt.elseBranchOptional()
                    .map(branch -> canBreakCurrentLoop(branch, nestedLoopDepth))
                    .orElse(false);
        }
        if (statement instanceof WhileStmt whileStmt) {
            return canBreakCurrentLoop(whileStmt.body(), nestedLoopDepth + 1);
        }
        if (statement instanceof DoWhileStmt doWhileStmt) {
            return canBreakCurrentLoop(doWhileStmt.body(), nestedLoopDepth + 1);
        }
        if (statement instanceof ForStmt forStmt) {
            return canBreakCurrentLoop(forStmt.body(), nestedLoopDepth + 1);
        }
        if (statement instanceof SwitchStmt) {
            // A break in a switch exits the switch, not the surrounding loop.
            return false;
        }
        return false;
    }

    private boolean isNonZeroIntegerConstant(Expression expression) {
        return switch (expression) {
            case minic.compiler.parser.node.Expression.IntegerLiteralExpr literal -> literal.value() != 0;
            case minic.compiler.parser.node.Expression.IntegerConstantExpr literal -> literal.value() != 0;
            case minic.compiler.parser.node.Expression.LongLiteralExpr literal -> literal.value() != 0;
            case minic.compiler.parser.node.Expression.CharLiteralExpr literal -> literal.value() != 0;
            case minic.compiler.parser.node.Expression.BoolLiteralExpr literal -> literal.value();
            case minic.compiler.parser.node.Expression.GroupingExpr grouping ->
                    isNonZeroIntegerConstant(grouping.expression());
            default -> false;
        };
    }

    private void report(SourceRange range, String message) {
        diagnostics.add(new Diagnostic("SEM001", Diagnostic.Severity.ERROR, message, range));
    }
}
