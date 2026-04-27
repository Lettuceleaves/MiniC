package minic.compiler.semantic;

import minic.compiler.ast.AssignmentExpr;
import minic.compiler.ast.BinaryExpr;
import minic.compiler.ast.BlockStmt;
import minic.compiler.ast.CallExpr;
import minic.compiler.ast.ExprStmt;
import minic.compiler.ast.Expression;
import minic.compiler.ast.FunctionDecl;
import minic.compiler.ast.GroupingExpr;
import minic.compiler.ast.IntegerLiteralExpr;
import minic.compiler.ast.NameExpr;
import minic.compiler.ast.Parameter;
import minic.compiler.ast.Program;
import minic.compiler.ast.ReturnStmt;
import minic.compiler.ast.Statement;
import minic.compiler.ast.VarDeclStmt;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MiniC 语义分析器。
 */
public final class SemanticAnalyzer {
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private Scope globalScope;

    /**
     * 分析程序中的函数和变量引用。
     *
     * @param program 待分析程序 AST
     * @return 语义分析结果
     */
    public SemanticResult analyze(Program program) {
        Objects.requireNonNull(program, "program");
        diagnostics.clear();
        globalScope = new Scope();
        defineFunctions(program);
        for (FunctionDecl functionDecl : program.functions()) {
            analyzeFunction(functionDecl);
        }
        return new SemanticResult(globalScope, diagnostics);
    }

    private void defineFunctions(Program program) {
        for (FunctionDecl functionDecl : program.functions()) {
            Symbol symbol = new Symbol(functionDecl.name(), SymbolKind.FUNCTION, functionDecl.range());
            if (!globalScope.define(symbol)) {
                report(functionDecl.range(), "重复函数定义：" + functionDecl.name());
            }
        }
    }

    private void analyzeFunction(FunctionDecl functionDecl) {
        Scope functionScope = new Scope(globalScope);
        for (Parameter parameter : functionDecl.parameters()) {
            defineVariable(functionScope, parameter.name(), parameter.range());
        }
        analyzeBlock(functionDecl.body(), functionScope, false);
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
                varDeclStmt.initializerOptional().ifPresent(initializer -> analyzeExpression(initializer, scope));
                defineVariable(scope, varDeclStmt.name(), varDeclStmt.range());
            }
            case ReturnStmt returnStmt -> returnStmt.expressionOptional()
                    .ifPresent(expression -> analyzeExpression(expression, scope));
            case ExprStmt exprStmt -> analyzeExpression(exprStmt.expression(), scope);
        }
    }

    private void analyzeExpression(Expression expression, Scope scope) {
        switch (expression) {
            case IntegerLiteralExpr ignored -> {
            }
            case NameExpr nameExpr -> resolveVariable(scope, nameExpr.name(), nameExpr.range());
            case AssignmentExpr assignmentExpr -> {
                resolveVariable(scope, assignmentExpr.targetName(), assignmentExpr.range());
                analyzeExpression(assignmentExpr.value(), scope);
            }
            case BinaryExpr binaryExpr -> {
                analyzeExpression(binaryExpr.left(), scope);
                analyzeExpression(binaryExpr.right(), scope);
            }
            case GroupingExpr groupingExpr -> analyzeExpression(groupingExpr.expression(), scope);
            case CallExpr callExpr -> {
                resolveFunction(callExpr);
                for (Expression argument : callExpr.arguments()) {
                    analyzeExpression(argument, scope);
                }
            }
        }
    }

    private void defineVariable(Scope scope, String name, SourceRange range) {
        Symbol symbol = new Symbol(name, SymbolKind.VARIABLE, range);
        if (!scope.define(symbol)) {
            report(range, "重复局部变量定义：" + name);
        }
    }

    private void resolveVariable(Scope scope, String name, SourceRange range) {
        if (scope.resolve(name).filter(symbol -> symbol.kind() == SymbolKind.VARIABLE).isEmpty()) {
            report(range, "未解析变量：" + name);
        }
    }

    private void resolveFunction(CallExpr callExpr) {
        if (globalScope.resolve(callExpr.calleeName()).filter(symbol -> symbol.kind() == SymbolKind.FUNCTION).isEmpty()) {
            report(callExpr.range(), "未解析函数调用：" + callExpr.calleeName());
        }
    }

    private void report(SourceRange range, String message) {
        diagnostics.add(new Diagnostic("SEM001", DiagnosticSeverity.ERROR, message, range));
    }
}
