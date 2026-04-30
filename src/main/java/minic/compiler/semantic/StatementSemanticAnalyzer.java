package minic.compiler.semantic;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.BreakStmt;
import minic.compiler.ast.stmt.ContinueStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ForStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ast.stmt.WhileStmt;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.Map;

final class StatementSemanticAnalyzer {
    private final Scope globalScope;
    private final SemanticReporter reporter;
    private final ExpressionSemanticAnalyzer expressionAnalyzer;
    private int loopDepth;

    StatementSemanticAnalyzer(
            Scope globalScope,
            FunctionRegistry functionRegistry,
            SemanticReporter reporter,
            Map<Expression, MiniType> expressionTypes
    ) {
        this.globalScope = globalScope;
        this.reporter = reporter;
        expressionAnalyzer = new ExpressionSemanticAnalyzer(functionRegistry, reporter, expressionTypes);
    }

    void analyzeFunction(FunctionDecl functionDecl) {
        Scope functionScope = new Scope(globalScope);
        for (Parameter parameter : functionDecl.parameters()) {
            defineVariable(functionScope, parameter.name(), parameter.range(), parameter.type());
        }
        analyzeBlock(functionDecl.bodyOptional().orElseThrow(), functionScope, false);
    }

    private void analyzeBlock(BlockStmt blockStmt, Scope parentScope, boolean createChildScope) {
        Scope scope = createChildScope ? new Scope(parentScope) : parentScope;
        for (Statement statement : blockStmt.statements()) {
            analyzeStatement(statement, scope);
        }
    }

    private void analyzeStatement(Statement statement, Scope scope) {
        switch (statement) {
            case BlockStmt blockStmt -> analyzeBlock(blockStmt, scope, true);
            case VarDeclStmt varDeclStmt -> {
                if (varDeclStmt.type().isArray() && varDeclStmt.initializerOptional().isPresent()) {
                    reporter.report(varDeclStmt.range(), "数组声明暂不支持初始化表达式");
                }
                varDeclStmt.initializerOptional()
                        .ifPresent(initializer -> expressionAnalyzer.analyzeExpression(initializer, scope));
                defineVariable(scope, varDeclStmt.name(), varDeclStmt.range(), varDeclStmt.type());
            }
            case ReturnStmt returnStmt -> {
                if (returnStmt.expressionOptional().isEmpty()) {
                    reporter.report(returnStmt.range(), "int 函数中 return 必须包含表达式");
                } else {
                    expressionAnalyzer.analyzeExpression(returnStmt.expressionOptional().orElseThrow(), scope);
                }
            }
            case ExprStmt exprStmt -> expressionAnalyzer.analyzeExpression(exprStmt.expression(), scope);
            case BreakStmt breakStmt -> {
                if (loopDepth == 0) {
                    reporter.report(breakStmt.range(), "break 只能在循环内使用");
                }
            }
            case ContinueStmt continueStmt -> {
                if (loopDepth == 0) {
                    reporter.report(continueStmt.range(), "continue 只能在循环内使用");
                }
            }
            case IfStmt ifStmt -> {
                expressionAnalyzer.analyzeExpression(ifStmt.condition(), scope);
                analyzeBranch(ifStmt.thenBranch(), scope);
                ifStmt.elseBranchOptional().ifPresent(elseBranch -> analyzeBranch(elseBranch, scope));
            }
            case WhileStmt whileStmt -> {
                expressionAnalyzer.analyzeExpression(whileStmt.condition(), scope);
                analyzeLoopBranch(whileStmt.body(), scope);
            }
            case ForStmt forStmt -> analyzeFor(forStmt, scope);
            default -> throw new IllegalArgumentException("unsupported statement: "
                    + statement.getClass().getSimpleName());
        }
    }

    private void analyzeFor(ForStmt forStmt, Scope parentScope) {
        Scope scope = new Scope(parentScope);
        forStmt.initializerOptional().ifPresent(initializer -> analyzeStatement(initializer, scope));
        forStmt.conditionOptional().ifPresent(condition -> expressionAnalyzer.analyzeExpression(condition, scope));
        forStmt.stepOptional().ifPresent(step -> expressionAnalyzer.analyzeExpression(step, scope));
        analyzeLoopBranch(forStmt.body(), scope);
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
            analyzeStatement(statement, new Scope(parentScope));
        }
    }

    private void defineVariable(Scope scope, String name, SourceRange range, MiniType type) {
        Symbol symbol = new Symbol(name, SymbolKind.VARIABLE, range, type, null);
        if (!scope.define(symbol)) {
            reporter.report(range, "重复局部变量定义：" + name);
        }
    }
}
