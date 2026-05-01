package minic.compiler.semantic;

import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.FieldAccessExpr;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IndexExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.expr.StringLiteralExpr;
import minic.compiler.ast.expr.UnaryExpr;
import minic.compiler.type.MiniType;
import minic.compiler.lexer.TokenKind;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.Map;

final class ExpressionSemanticAnalyzer {
    private final FunctionRegistry functionRegistry;
    private final StructRegistry structRegistry;
    private final SemanticReporter reporter;
    private final Map<Expression, MiniType> expressionTypes;

    ExpressionSemanticAnalyzer(
            FunctionRegistry functionRegistry,
            StructRegistry structRegistry,
            SemanticReporter reporter,
            Map<Expression, MiniType> expressionTypes
    ) {
        this.functionRegistry = functionRegistry;
        this.structRegistry = structRegistry;
        this.reporter = reporter;
        this.expressionTypes = expressionTypes;
    }

    MiniType analyzeExpression(Expression expression, Scope scope) {
        MiniType type = switch (expression) {
            case IntegerLiteralExpr ignored -> MiniType.INT;
            case StringLiteralExpr ignored -> MiniType.INT.pointerTo();
            case NameExpr nameExpr -> resolveVariable(scope, nameExpr.name(), nameExpr.range());
            case AssignmentExpr assignmentExpr -> {
                MiniType targetType = analyzeAssignmentTarget(assignmentExpr.target(), scope, assignmentExpr.range());
                MiniType valueType = analyzeExpression(assignmentExpr.value(), scope);
                if (targetType.isStruct() || valueType.isStruct()) {
                    reporter.report(assignmentExpr.range(), "暂不支持结构体整体赋值");
                }
                if (!isAssignmentCompatible(targetType, valueType)) {
                    reporter.report(assignmentExpr.range(), "赋值类型不匹配");
                }
                yield targetType;
            }
            case BinaryExpr binaryExpr -> {
                analyzeExpression(binaryExpr.left(), scope);
                analyzeExpression(binaryExpr.right(), scope);
                yield MiniType.INT;
            }
            case GroupingExpr groupingExpr -> analyzeExpression(groupingExpr.expression(), scope);
            case IndexExpr indexExpr -> analyzeIndex(indexExpr, scope);
            case FieldAccessExpr fieldAccessExpr -> analyzeFieldAccess(fieldAccessExpr, scope);
            case UnaryExpr unaryExpr -> analyzeUnary(unaryExpr, scope);
            case CallExpr callExpr -> {
                ArrayList<MiniType> argumentTypes = new ArrayList<>();
                for (Expression argument : callExpr.arguments()) {
                    argumentTypes.add(analyzeExpression(argument, scope));
                }
                MiniType returnType = functionRegistry.resolveFunction(callExpr, argumentTypes);
                yield returnType;
            }
            default -> throw new IllegalArgumentException("unsupported expression: "
                    + expression.getClass().getSimpleName());
        };
        expressionTypes.put(expression, type);
        return type;
    }

    private MiniType analyzeUnary(UnaryExpr unaryExpr, Scope scope) {
        MiniType operandType = analyzeExpression(unaryExpr.operand(), scope);
        if (unaryExpr.operator() == TokenKind.AMPERSAND) {
            if (!(unaryExpr.operand() instanceof NameExpr)) {
                reporter.report(unaryExpr.range(), "取址操作数必须是变量");
            }
            return operandType.pointerTo();
        }
        if (unaryExpr.operator() == TokenKind.STAR) {
            if (!operandType.isPointer()) {
                reporter.report(unaryExpr.range(), "解引用操作数必须是指针");
                return MiniType.INT;
            }
            return operandType.pointee();
        }
        throw new IllegalArgumentException("unsupported unary operator: " + unaryExpr.operator());
    }

    private MiniType analyzeAssignmentTarget(Expression target, Scope scope, SourceRange range) {
        if (target instanceof NameExpr) {
            return analyzeExpression(target, scope);
        }
        if (target instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenKind.STAR) {
            return analyzeExpression(target, scope);
        }
        if (target instanceof IndexExpr || target instanceof FieldAccessExpr) {
            return analyzeExpression(target, scope);
        }
        reporter.report(range, "赋值左侧必须是变量或解引用表达式");
        return MiniType.INT;
    }

    private MiniType analyzeIndex(IndexExpr indexExpr, Scope scope) {
        MiniType targetType = analyzeExpression(indexExpr.target(), scope);
        analyzeExpression(indexExpr.index(), scope);
        if (targetType.isArray()) {
            return targetType.elementType();
        }
        if (targetType.isPointer()) {
            return targetType.pointee();
        }
        reporter.report(indexExpr.range(), "下标访问目标必须是数组或指针");
        return MiniType.INT;
    }

    private MiniType analyzeFieldAccess(FieldAccessExpr fieldAccessExpr, Scope scope) {
        MiniType targetType = analyzeExpression(fieldAccessExpr.target(), scope);
        MiniType structType = targetType;
        if (fieldAccessExpr.viaPointer()) {
            if (!targetType.isPointer() || !(targetType.pointee() instanceof MiniType.StructType)) {
                reporter.report(fieldAccessExpr.range(), "指针字段访问目标必须是结构体指针");
                return MiniType.INT;
            }
            structType = targetType.pointee();
        } else if (!(targetType instanceof MiniType.StructType)) {
            reporter.report(fieldAccessExpr.range(), "字段访问目标必须是结构体");
            return MiniType.INT;
        }
        return structRegistry.field(structType, fieldAccessExpr.fieldName())
                .map(StructFieldLayout::type)
                .orElseGet(() -> {
                    reporter.report(fieldAccessExpr.range(), "未知结构体字段：" + fieldAccessExpr.fieldName());
                    return MiniType.INT;
                });
    }

    private MiniType resolveVariable(Scope scope, String name, SourceRange range) {
        var symbol = scope.resolve(name).filter(candidate -> candidate.kind() == SymbolKind.VARIABLE);
        if (symbol.isPresent()) {
            MiniType type = symbol.orElseThrow().type();
            if (type.isArray()) {
                return type.elementType().pointerTo();
            }
            return type;
        }
        if (scope.resolve(name).filter(candidate -> candidate.kind() == SymbolKind.FUNCTION).isPresent()) {
            return functionRegistry.resolveFunctionAddress(name, range);
        }
        reporter.report(range, "未解析变量：" + name);
        return MiniType.INT;
    }

    private boolean isAssignmentCompatible(MiniType targetType, MiniType valueType) {
        if (targetType.equals(valueType)) {
            return true;
        }
        if (targetType.isPointer() && targetType.pointee().isFunction()) {
            return false;
        }
        return !targetType.isPointer();
    }
}
