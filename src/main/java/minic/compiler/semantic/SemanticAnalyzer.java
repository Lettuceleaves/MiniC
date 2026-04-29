package minic.compiler.semantic;

import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.VarDeclStmt;
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
    private static final int MAX_USER_FUNCTION_PARAMETERS = 4;
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
        validateMain(program);
        for (FunctionDecl functionDecl : program.functions()) {
            analyzeFunction(functionDecl);
        }
        return new SemanticResult(globalScope, diagnostics);
    }

    private void defineFunctions(Program program) {
        for (FunctionDecl functionDecl : program.functions()) {
            validateFunctionName(functionDecl);
            validateFunctionSignature(functionDecl);
            Symbol symbol = new Symbol(
                    functionDecl.name(),
                    SymbolKind.FUNCTION,
                    functionDecl.range(),
                    functionDecl.parameters().size()
            );
            if (!globalScope.define(symbol)) {
                report(functionDecl.range(), "重复函数签名：" + functionSignature(functionDecl));
            }
        }
    }

    private void validateMain(Program program) {
        if (globalScope.resolve("main").filter(symbol -> symbol.kind() == SymbolKind.FUNCTION).isEmpty()) {
            report(program.range(), "缺少 main 函数");
        }
    }

    private void validateFunctionName(FunctionDecl functionDecl) {
        String name = functionDecl.name();
        if (!isValidFunctionName(name)) {
            report(functionDecl.range(), "非法函数名：" + name);
        }
    }

    private boolean isValidFunctionName(String name) {
        if (name.isEmpty() || name.charAt(0) == '_') {
            return false;
        }
        if (!isAsciiLetter(name.charAt(0))) {
            return false;
        }
        for (int index = 1; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!isAsciiLetter(character) && !isAsciiDigit(character) && character != '_') {
                return false;
            }
        }
        return true;
    }

    private boolean isAsciiLetter(char character) {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
    }

    private boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private void validateFunctionSignature(FunctionDecl functionDecl) {
        if ("main".equals(functionDecl.name()) && !functionDecl.parameters().isEmpty()) {
            report(functionDecl.range(), "非法 main 函数签名：main 必须无参数");
        }
        if (!"main".equals(functionDecl.name())
                && functionDecl.parameters().size() > MAX_USER_FUNCTION_PARAMETERS) {
            report(functionDecl.range(), "非法函数签名：" + functionDecl.name() + " 参数数量不能超过 "
                    + MAX_USER_FUNCTION_PARAMETERS);
        }
    }

    private String functionSignature(FunctionDecl functionDecl) {
        return functionDecl.name() + "/" + functionDecl.parameters().size();
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
            case ReturnStmt returnStmt -> {
                if (returnStmt.expressionOptional().isEmpty()) {
                    report(returnStmt.range(), "int 函数中 return 必须包含表达式");
                } else {
                    analyzeExpression(returnStmt.expressionOptional().orElseThrow(), scope);
                }
            }
            case ExprStmt exprStmt -> analyzeExpression(exprStmt.expression(), scope);
            default -> throw new IllegalArgumentException("unsupported statement: "
                    + statement.getClass().getSimpleName());
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
            default -> throw new IllegalArgumentException("unsupported expression: "
                    + expression.getClass().getSimpleName());
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
        var functionSymbol = globalScope.resolve(callExpr.calleeName())
                .filter(symbol -> symbol.kind() == SymbolKind.FUNCTION);
        if (functionSymbol.isEmpty()) {
            report(callExpr.range(), "未解析函数调用：" + callExpr.calleeName());
            return;
        }
        Integer arity = functionSymbol.orElseThrow().arity();
        if (arity != null && arity != callExpr.arguments().size()) {
            report(callExpr.range(), "函数调用实参数量不匹配：" + callExpr.calleeName());
        }
    }

    private void report(SourceRange range, String message) {
        diagnostics.add(new Diagnostic("SEM001", DiagnosticSeverity.ERROR, message, range));
    }
}
