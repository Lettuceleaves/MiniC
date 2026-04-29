package minic.compiler.semantic;

import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.source.SourceRange;

final class ExpressionSemanticAnalyzer {
    private final FunctionRegistry functionRegistry;
    private final SemanticReporter reporter;

    ExpressionSemanticAnalyzer(FunctionRegistry functionRegistry, SemanticReporter reporter) {
        this.functionRegistry = functionRegistry;
        this.reporter = reporter;
    }

    void analyzeExpression(Expression expression, Scope scope) {
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
                functionRegistry.resolveFunction(callExpr);
                for (Expression argument : callExpr.arguments()) {
                    analyzeExpression(argument, scope);
                }
            }
            default -> throw new IllegalArgumentException("unsupported expression: "
                    + expression.getClass().getSimpleName());
        }
    }

    private void resolveVariable(Scope scope, String name, SourceRange range) {
        if (scope.resolve(name).filter(symbol -> symbol.kind() == SymbolKind.VARIABLE).isEmpty()) {
            reporter.report(range, "未解析变量：" + name);
        }
    }
}
