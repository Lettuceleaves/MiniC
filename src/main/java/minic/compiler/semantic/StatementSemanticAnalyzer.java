package minic.compiler.semantic;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.source.SourceRange;

final class StatementSemanticAnalyzer {
    private final Scope globalScope;
    private final SemanticReporter reporter;
    private final ExpressionSemanticAnalyzer expressionAnalyzer;

    StatementSemanticAnalyzer(Scope globalScope, FunctionRegistry functionRegistry, SemanticReporter reporter) {
        this.globalScope = globalScope;
        this.reporter = reporter;
        expressionAnalyzer = new ExpressionSemanticAnalyzer(functionRegistry, reporter);
    }

    void analyzeFunction(FunctionDecl functionDecl) {
        Scope functionScope = new Scope(globalScope);
        for (Parameter parameter : functionDecl.parameters()) {
            defineVariable(functionScope, parameter.name(), parameter.range());
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
                varDeclStmt.initializerOptional()
                        .ifPresent(initializer -> expressionAnalyzer.analyzeExpression(initializer, scope));
                defineVariable(scope, varDeclStmt.name(), varDeclStmt.range());
            }
            case ReturnStmt returnStmt -> {
                if (returnStmt.expressionOptional().isEmpty()) {
                    reporter.report(returnStmt.range(), "int 函数中 return 必须包含表达式");
                } else {
                    expressionAnalyzer.analyzeExpression(returnStmt.expressionOptional().orElseThrow(), scope);
                }
            }
            case ExprStmt exprStmt -> expressionAnalyzer.analyzeExpression(exprStmt.expression(), scope);
            case IfStmt ifStmt -> {
                expressionAnalyzer.analyzeExpression(ifStmt.condition(), scope);
                analyzeBranch(ifStmt.thenBranch(), scope);
                ifStmt.elseBranchOptional().ifPresent(elseBranch -> analyzeBranch(elseBranch, scope));
            }
            default -> throw new IllegalArgumentException("unsupported statement: "
                    + statement.getClass().getSimpleName());
        }
    }

    private void analyzeBranch(Statement statement, Scope parentScope) {
        if (statement instanceof BlockStmt blockStmt) {
            analyzeBlock(blockStmt, parentScope, true);
        } else {
            analyzeStatement(statement, new Scope(parentScope));
        }
    }

    private void defineVariable(Scope scope, String name, SourceRange range) {
        Symbol symbol = new Symbol(name, SymbolKind.VARIABLE, range);
        if (!scope.define(symbol)) {
            reporter.report(range, "重复局部变量定义：" + name);
        }
    }
}
