package minic.compiler.semantic;

import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.expr.StringLiteralExpr;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.Map;

final class ExpressionSemanticAnalyzer {
    private final FunctionRegistry functionRegistry;
    private final SemanticReporter reporter;
    private final Map<Expression, MiniType> expressionTypes;

    ExpressionSemanticAnalyzer(
            FunctionRegistry functionRegistry,
            SemanticReporter reporter,
            Map<Expression, MiniType> expressionTypes
    ) {
        this.functionRegistry = functionRegistry;
        this.reporter = reporter;
        this.expressionTypes = expressionTypes;
    }

    MiniType analyzeExpression(Expression expression, Scope scope) {
        MiniType type = switch (expression) {
            case IntegerLiteralExpr ignored -> MiniType.INT;
            case StringLiteralExpr ignored -> MiniType.INT.pointerTo();
            case NameExpr nameExpr -> resolveVariable(scope, nameExpr.name(), nameExpr.range());
            case AssignmentExpr assignmentExpr -> {
                resolveVariable(scope, assignmentExpr.targetName(), assignmentExpr.range());
                analyzeExpression(assignmentExpr.value(), scope);
                yield MiniType.INT;
            }
            case BinaryExpr binaryExpr -> {
                analyzeExpression(binaryExpr.left(), scope);
                analyzeExpression(binaryExpr.right(), scope);
                yield MiniType.INT;
            }
            case GroupingExpr groupingExpr -> analyzeExpression(groupingExpr.expression(), scope);
            case CallExpr callExpr -> {
                MiniType returnType = functionRegistry.resolveFunction(callExpr);
                for (Expression argument : callExpr.arguments()) {
                    analyzeExpression(argument, scope);
                }
                yield returnType;
            }
            default -> throw new IllegalArgumentException("unsupported expression: "
                    + expression.getClass().getSimpleName());
        };
        expressionTypes.put(expression, type);
        return type;
    }

    private MiniType resolveVariable(Scope scope, String name, SourceRange range) {
        var symbol = scope.resolve(name).filter(candidate -> candidate.kind() == SymbolKind.VARIABLE);
        if (symbol.isEmpty()) {
            reporter.report(range, "未解析变量：" + name);
            return MiniType.INT;
        }
        return symbol.orElseThrow().type();
    }
}
