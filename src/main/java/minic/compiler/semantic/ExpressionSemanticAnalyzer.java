package minic.compiler.semantic;

import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.BoolLiteralExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.CharLiteralExpr;
import minic.compiler.ast.expr.ConditionalExpr;
import minic.compiler.ast.expr.DoubleLiteralExpr;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.FieldAccessExpr;
import minic.compiler.ast.expr.FloatLiteralExpr;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IndexExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.LongLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.expr.NullLiteralExpr;
import minic.compiler.ast.expr.SizeofExpr;
import minic.compiler.ast.expr.StringLiteralExpr;
import minic.compiler.ast.expr.StructInitExpr;
import minic.compiler.ast.expr.UnaryExpr;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import minic.compiler.lexer.TokenKind;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

final class ExpressionSemanticAnalyzer {
    private final FunctionRegistry functionRegistry;
    private final StructRegistry structRegistry;
    private final SemanticReporter reporter;
    private final Map<Expression, MiniType> expressionTypes;
    private Set<String> currentParameterNames = Set.of();
    private MiniType structInitTargetType;

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

    void setCurrentParameterNames(Collection<String> parameterNames) {
        currentParameterNames = Set.copyOf(parameterNames);
    }

    void setStructInitTargetType(MiniType type) {
        this.structInitTargetType = type;
    }

    MiniType analyzeExpression(Expression expression, Scope scope) {
        MiniType type = switch (expression) {
            case BoolLiteralExpr ignored -> MiniType.BOOL;
            case CharLiteralExpr ignored -> MiniType.CHAR;
            case IntegerLiteralExpr ignored -> MiniType.INT;
            case LongLiteralExpr ignored -> MiniType.LONG;
            case FloatLiteralExpr ignored -> MiniType.FLOAT;
            case DoubleLiteralExpr ignored -> MiniType.DOUBLE;
            case NullLiteralExpr ignored -> MiniType.NULL;
            case StringLiteralExpr ignored -> MiniType.CHAR.pointerTo();
            case NameExpr nameExpr -> resolveVariable(scope, nameExpr.name(), nameExpr.range());
            case AssignmentExpr assignmentExpr -> analyzeAssignment(assignmentExpr, scope);
            case BinaryExpr binaryExpr -> {
                MiniType leftType = analyzeExpression(binaryExpr.left(), scope);
                MiniType rightType = analyzeExpression(binaryExpr.right(), scope);
                if (!TypeCompatibility.isBinaryCompatible(leftType, rightType, binaryExpr.operator())) {
                    reporter.report(binaryExpr.range(), "二元表达式操作数类型不匹配");
                }
                yield TypeCompatibility.binaryResultType(leftType, rightType, binaryExpr.operator());
            }
            case GroupingExpr groupingExpr -> analyzeExpression(groupingExpr.expression(), scope);
            case IndexExpr indexExpr -> analyzeIndex(indexExpr, scope);
            case FieldAccessExpr fieldAccessExpr -> analyzeFieldAccess(fieldAccessExpr, scope);
            case UnaryExpr unaryExpr -> analyzeUnary(unaryExpr, scope);
            case ConditionalExpr conditionalExpr -> analyzeConditional(conditionalExpr, scope);
            case SizeofExpr sizeofExpr -> analyzeSizeof(sizeofExpr, scope);
            case CallExpr callExpr -> {
                ArrayList<MiniType> argumentTypes = new ArrayList<>();
                for (Expression argument : callExpr.arguments()) {
                    argumentTypes.add(analyzeExpression(argument, scope));
                }
                MiniType returnType = isDirectFunctionCall(callExpr, scope)
                        ? functionRegistry.resolveFunction(callExpr, argumentTypes)
                        : resolveFunctionPointerCall(callExpr, scope, argumentTypes);
                yield returnType;
            }
            case StructInitExpr structInitExpr -> analyzeStructInit(structInitExpr, scope);
            default -> throw new IllegalArgumentException("unsupported expression: "
                    + expression.getClass().getSimpleName());
        };
        expressionTypes.put(expression, type);
        return type;
    }

    private MiniType analyzeUnary(UnaryExpr unaryExpr, Scope scope) {
        if (unaryExpr.operator() == TokenKind.AMPERSAND) {
            MiniType operandType = analyzeAddressOperand(unaryExpr.operand(), scope, unaryExpr.range());
            return operandType.pointerTo();
        }
        MiniType operandType = analyzeExpression(unaryExpr.operand(), scope);
        if (unaryExpr.operator() == TokenKind.STAR) {
            if (!operandType.isPointer()) {
                reporter.report(unaryExpr.range(), "解引用操作数必须是指针");
                return MiniType.INT;
            }
            return operandType.pointee();
        }
        if (unaryExpr.operator() == TokenKind.BANG) {
            if (!TypeCompatibility.isConditionCompatible(operandType)) {
                reporter.report(unaryExpr.range(), "! 操作数必须是标量或指针");
            }
            return MiniType.INT;
        }
        if (unaryExpr.operator() == TokenKind.TILDE) {
            if (!operandType.isIntegerScalar()) {
                reporter.report(unaryExpr.range(), "~ 操作数必须是整数类型");
            }
            return operandType.isIntegerScalar() ? operandType : MiniType.INT;
        }
        if (unaryExpr.operator() == TokenKind.PLUS_PLUS || unaryExpr.operator() == TokenKind.MINUS_MINUS) {
            MiniType targetType = analyzeAssignmentTarget(unaryExpr.operand(), scope, unaryExpr.range());
            if (!targetType.isScalar() && !targetType.isPointer()) {
                reporter.report(unaryExpr.range(), "自增自减操作数必须是标量或指针");
            }
            return targetType;
        }
        if (unaryExpr.operator() == TokenKind.MINUS || unaryExpr.operator() == TokenKind.PLUS) {
            if (!operandType.isScalar()) {
                reporter.report(unaryExpr.range(), "一元 +/- 操作数必须是标量类型");
            }
            return operandType.isScalar() ? operandType : MiniType.INT;
        }
        throw new IllegalArgumentException("unsupported unary operator: " + unaryExpr.operator());
    }

    private MiniType analyzeAddressOperand(Expression operand, Scope scope, SourceRange range) {
        if (operand instanceof NameExpr nameExpr) {
            scope.resolve(nameExpr.name()).ifPresent(symbol -> {
                if (symbol.kind() == SymbolKind.VARIABLE && currentParameterNames.contains(nameExpr.name())) {
                    reporter.report(range, "暂不支持对参数取址：" + nameExpr.name());
                }
                if (symbol.kind() == SymbolKind.FUNCTION) {
                    reporter.report(range, "取址操作数必须是变量");
                }
            });
            return analyzeExpression(operand, scope);
        }
        if (operand instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenKind.STAR) {
            return analyzeExpression(operand, scope);
        }
        if (operand instanceof IndexExpr || operand instanceof FieldAccessExpr) {
            return analyzeExpression(operand, scope);
        }
        MiniType operandType = analyzeExpression(operand, scope);
        reporter.report(range, "取址操作数必须是变量");
        return operandType;
    }

    private MiniType analyzeAssignment(AssignmentExpr assignmentExpr, Scope scope) {
        if (assignmentExpr.target() instanceof NameExpr nameExpr) {
            scope.resolve(nameExpr.name()).ifPresent(symbol -> {
                if (symbol.kind() == SymbolKind.FUNCTION) {
                    reporter.report(assignmentExpr.range(), "赋值左侧不能是函数名");
                }
                if (symbol.type().isArray()) {
                    reporter.report(assignmentExpr.range(), "数组不能整体赋值");
                }
            });
        }
        MiniType targetType = analyzeAssignmentTarget(assignmentExpr.target(), scope, assignmentExpr.range());
        MiniType valueType = analyzeExpression(assignmentExpr.value(), scope);
        if (targetType.isArray()) {
            reporter.report(assignmentExpr.range(), "数组不能整体赋值");
        }
        if (targetType.isStruct() || valueType.isStruct()) {
            if (!targetType.equals(valueType)) {
                reporter.report(assignmentExpr.range(), "结构体赋值类型不匹配");
            }
            if (assignmentExpr.compoundBinaryOperator().isPresent()) {
                reporter.report(assignmentExpr.range(), "结构体不支持复合赋值运算");
            }
            return targetType;
        }
        if (assignmentExpr.compoundBinaryOperator().isPresent()) {
            TokenKind binaryOperator = assignmentExpr.compoundBinaryOperator().orElseThrow();
            if (!TypeCompatibility.isBinaryCompatible(targetType, valueType, binaryOperator)) {
                reporter.report(assignmentExpr.range(), "复合赋值操作数类型不匹配");
            }
            MiniType resultType = TypeCompatibility.binaryResultType(targetType, valueType, binaryOperator);
            if (!TypeCompatibility.isAssignmentCompatible(targetType, resultType)) {
                reporter.report(assignmentExpr.range(), "复合赋值结果类型不匹配");
            }
        } else if (!TypeCompatibility.isAssignmentCompatible(targetType, valueType)) {
            reporter.report(assignmentExpr.range(), "赋值类型不匹配");
        }
        return targetType;
    }

    private MiniType analyzeConditional(ConditionalExpr conditionalExpr, Scope scope) {
        MiniType conditionType = analyzeExpression(conditionalExpr.condition(), scope);
        MiniType thenType = analyzeExpression(conditionalExpr.thenExpression(), scope);
        MiniType elseType = analyzeExpression(conditionalExpr.elseExpression(), scope);
        if (!TypeCompatibility.isConditionCompatible(conditionType)) {
            reporter.report(conditionalExpr.condition().range(), "条件表达式必须是标量或指针类型");
        }
        if (!TypeCompatibility.isConditionalBranchCompatible(thenType, elseType)) {
            reporter.report(conditionalExpr.range(), "条件表达式分支类型不匹配");
        }
        return TypeCompatibility.conditionalResultType(thenType, elseType);
    }

    private MiniType analyzeSizeof(SizeofExpr sizeofExpr, Scope scope) {
        MiniType queriedType = sizeofExpr.queriedTypeOptional().orElse(null);
        if (queriedType == null) {
            queriedType = analyzeExpression(sizeofExpr.expressionOptional().orElseThrow(), scope);
        }
        if (!TypeLayout.hasFixedLayout(queriedType) && !hasStructLayout(queriedType)) {
            reporter.report(sizeofExpr.range(), "sizeof 只支持固定布局类型");
        }
        return MiniType.LONG;
    }

    private boolean hasStructLayout(MiniType type) {
        if (type instanceof MiniType.StructType structType) {
            return structRegistry.hasLayout(structType.name());
        }
        if (type.isArray() && type.elementType() instanceof MiniType.StructType structType) {
            return structRegistry.hasLayout(structType.name());
        }
        return false;
    }

    private MiniType analyzeStructInit(StructInitExpr structInitExpr, Scope scope) {
        if (structInitTargetType == null || !structInitTargetType.isStruct()) {
            reporter.report(structInitExpr.range(), "大括号初始化只能用于结构体变量");
            return MiniType.INT;
        }
        MiniType.StructType targetStruct = (MiniType.StructType) structInitTargetType;
        java.util.List<StructFieldLayout> fields = structRegistry.fields(targetStruct.name());
        if (fields == null) {
            reporter.report(structInitExpr.range(), "未知结构体类型：" + targetStruct.name());
            return structInitTargetType;
        }
        if (structInitExpr.values().size() != fields.size()) {
            reporter.report(structInitExpr.range(),
                    "结构体初始化值数量不匹配：期望 " + fields.size() + " 个，实际 " + structInitExpr.values().size() + " 个");
        }
        int count = Math.min(structInitExpr.values().size(), fields.size());
        for (int i = 0; i < count; i++) {
            MiniType valueType = analyzeExpression(structInitExpr.values().get(i), scope);
            MiniType fieldType = fields.get(i).type();
            if (!TypeCompatibility.isAssignmentCompatible(fieldType, valueType)) {
                reporter.report(structInitExpr.values().get(i).range(),
                        "结构体字段 " + fields.get(i).name() + " 初始化类型不匹配");
            }
        }
        return structInitTargetType;
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
        MiniType indexType = analyzeExpression(indexExpr.index(), scope);
        if (!TypeCompatibility.isIndexCompatible(indexType)) {
            reporter.report(indexExpr.index().range(), "数组下标必须是整数类型");
        }
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

    private MiniType resolveFunctionPointerCall(CallExpr callExpr, Scope scope, ArrayList<MiniType> argumentTypes) {
        MiniType calleeType = analyzeExpression(callExpr.callee(), scope);
        if (!calleeType.isPointer() || !calleeType.pointee().isFunction()) {
            reporter.report(callExpr.range(), "函数指针调用目标必须是函数指针");
            return MiniType.INT;
        }
        MiniType functionType = calleeType.pointee();
        if (functionType.parameterTypes().size() != argumentTypes.size()) {
            reporter.report(callExpr.range(), "函数指针调用实参数量不匹配");
        } else {
            for (int index = 0; index < argumentTypes.size(); index++) {
                if (!TypeCompatibility.isArgumentCompatible(
                        functionType.parameterTypes().get(index),
                        argumentTypes.get(index)
                )) {
                    reporter.report(callExpr.arguments().get(index).range(), "函数指针调用实参类型不匹配");
                }
            }
        }
        return functionType.returnType();
    }

    private boolean isDirectFunctionCall(CallExpr callExpr, Scope scope) {
        if (!callExpr.hasDirectCalleeName()) {
            return false;
        }
        return scope.resolve(callExpr.calleeName())
                .map(symbol -> symbol.kind() == SymbolKind.FUNCTION)
                .orElse(true);
    }
}
