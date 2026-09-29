package minic.compiler.semantic.manager;

import minic.compiler.parser.node.Expression.AssignmentExpr;
import minic.compiler.parser.node.Expression.BinaryExpr;
import minic.compiler.parser.node.Expression.BoolLiteralExpr;
import minic.compiler.parser.node.Expression.CallExpr;
import minic.compiler.parser.node.Expression.CharLiteralExpr;
import minic.compiler.parser.node.Expression.ConditionalExpr;
import minic.compiler.parser.node.Expression.CastExpr;
import minic.compiler.parser.node.Expression.CommaExpr;
import minic.compiler.parser.node.Expression.DoubleLiteralExpr;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.FieldAccessExpr;
import minic.compiler.parser.node.Expression.FloatLiteralExpr;
import minic.compiler.parser.node.Expression.GroupingExpr;
import minic.compiler.parser.node.Expression.IndexExpr;
import minic.compiler.parser.node.Expression.IntegerLiteralExpr;
import minic.compiler.parser.node.Expression.IntegerConstantExpr;
import minic.compiler.parser.node.Expression.LongLiteralExpr;
import minic.compiler.parser.node.Expression.NameExpr;
import minic.compiler.parser.node.Expression.NullLiteralExpr;
import minic.compiler.parser.node.Expression.SizeofExpr;
import minic.compiler.parser.node.Expression.StringLiteralExpr;
import minic.compiler.parser.node.Expression.AggregateInitExpr;
import minic.compiler.parser.node.Expression.DesignatedInitExpr;
import minic.compiler.parser.node.Expression.Designator;
import minic.compiler.parser.node.Expression.UnaryExpr;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.semantic.model.Scope;
import minic.compiler.semantic.model.StructLayout.StructFieldLayout;
import minic.compiler.semantic.model.Symbol.SymbolKind;
import minic.source.SourceRange;
import minic.diagnostics.Diagnostic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ExpressionSemanticAnalyzer {
    private final FunctionRegistry functionRegistry;
    private final StructRegistry structRegistry;
    private final List<Diagnostic> diagnostics;
    private final Map<Expression, MiniType> expressionTypes;
    private Set<String> currentParameterNames = Set.of();
    private MiniType aggregateInitTargetType;

    ExpressionSemanticAnalyzer(
            FunctionRegistry functionRegistry,
            StructRegistry structRegistry,
            List<Diagnostic> diagnostics,
            Map<Expression, MiniType> expressionTypes
    ) {
        this.functionRegistry = functionRegistry;
        this.structRegistry = structRegistry;
        this.diagnostics = diagnostics;
        this.expressionTypes = expressionTypes;
    }

    void setCurrentParameterNames(Collection<String> parameterNames) {
        currentParameterNames = Set.copyOf(parameterNames);
    }

    void setAggregateInitTargetType(MiniType type) {
        this.aggregateInitTargetType = type;
    }

    MiniType analyzeExpression(Expression expression, Scope scope) {
        MiniType type = switch (expression) {
            case BoolLiteralExpr ignored -> MiniType.BOOL;
            case CharLiteralExpr ignored -> MiniType.CHAR;
            case IntegerLiteralExpr ignored -> MiniType.INT;
            case IntegerConstantExpr integerConstantExpr -> integerConstantExpr.type();
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
                    report(binaryExpr.range(), "二元表达式操作数类型不匹配");
                }
                yield TypeCompatibility.binaryResultType(leftType, rightType, binaryExpr.operator());
            }
            case GroupingExpr groupingExpr -> analyzeExpression(groupingExpr.expression(), scope);
            case IndexExpr indexExpr -> analyzeIndex(indexExpr, scope);
            case FieldAccessExpr fieldAccessExpr -> analyzeFieldAccess(fieldAccessExpr, scope);
            case UnaryExpr unaryExpr -> analyzeUnary(unaryExpr, scope);
            case ConditionalExpr conditionalExpr -> analyzeConditional(conditionalExpr, scope);
            case CastExpr castExpr -> analyzeCast(castExpr, scope);
            case CommaExpr commaExpr -> analyzeComma(commaExpr, scope);
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
            case AggregateInitExpr aggregateInitExpr -> analyzeAggregateInit(aggregateInitExpr, scope);
            case DesignatedInitExpr designated -> {
                report(designated.range(), "指定初始化器只能出现在初始化列表中");
                yield analyzeExpression(designated.value(), scope);
            }
            default -> throw new IllegalArgumentException("unsupported expression: "
                    + expression.getClass().getSimpleName());
        };
        expressionTypes.put(expression, type);
        return type;
    }

    private MiniType analyzeCast(CastExpr castExpr, Scope scope) {
        MiniType source = TypeCompatibility.decay(analyzeExpression(castExpr.operand(), scope));
        MiniType target = castExpr.targetType();
        boolean sourceScalar = source.isScalar() || source.isPointer() || source.isNullPointer();
        boolean targetScalar = target.isScalar() || target.isPointer();
        if (!target.isVoid() && !(sourceScalar && targetScalar)) {
            report(castExpr.range(), "类型转换要求标量、指针或 void 目标类型");
        }
        return target;
    }

    private MiniType analyzeComma(CommaExpr commaExpr, Scope scope) {
        MiniType type = MiniType.INT;
        for (Expression expression : commaExpr.expressions()) {
            type = analyzeExpression(expression, scope);
        }
        return type;
    }

    private MiniType analyzeUnary(UnaryExpr unaryExpr, Scope scope) {
        if (unaryExpr.operator() == TokenType.AMPERSAND) {
            MiniType operandType = analyzeAddressOperand(unaryExpr.operand(), scope, unaryExpr.range());
            return operandType.pointerTo();
        }
        MiniType operandType = analyzeExpression(unaryExpr.operand(), scope);
        if (unaryExpr.operator() == TokenType.STAR) {
            if (!operandType.isPointer()) {
                report(unaryExpr.range(), "解引用操作数必须是指针");
                return MiniType.INT;
            }
            return operandType.pointee();
        }
        if (unaryExpr.operator() == TokenType.BANG) {
            if (!TypeCompatibility.isConditionCompatible(operandType)) {
                report(unaryExpr.range(), "! 操作数必须是标量或指针");
            }
            return MiniType.INT;
        }
        if (unaryExpr.operator() == TokenType.TILDE) {
            if (!operandType.isIntegerScalar()) {
                report(unaryExpr.range(), "~ 操作数必须是整数类型");
            }
            return operandType.isIntegerScalar() ? TypeCompatibility.integerPromotion(operandType) : MiniType.INT;
        }
        if (unaryExpr.operator() == TokenType.PLUS_PLUS || unaryExpr.operator() == TokenType.MINUS_MINUS) {
            MiniType targetType = analyzeAssignmentTarget(unaryExpr.operand(), scope, unaryExpr.range());
            if ((!targetType.isScalar() && !targetType.isPointer())
                    || (targetType.isPointer() && targetType.pointee().isFunction())) {
                report(unaryExpr.range(), "自增自减操作数必须是标量或对象指针");
            }
            return targetType;
        }
        if (unaryExpr.operator() == TokenType.MINUS || unaryExpr.operator() == TokenType.PLUS) {
            if (!operandType.isScalar()) {
                report(unaryExpr.range(), "一元 +/- 操作数必须是标量类型");
            }
            return operandType.isIntegerScalar()
                    ? TypeCompatibility.integerPromotion(operandType)
                    : operandType.isScalar() ? operandType : MiniType.INT;
        }
        throw new IllegalArgumentException("unsupported unary operator: " + unaryExpr.operator());
    }

    private MiniType analyzeAddressOperand(Expression operand, Scope scope, SourceRange range) {
        if (operand instanceof GroupingExpr groupingExpr) {
            return analyzeAddressOperand(groupingExpr.expression(), scope, range);
        }
        if (operand instanceof NameExpr nameExpr) {
            scope.resolve(nameExpr.name()).ifPresent(symbol -> {
                if (symbol.kind() == SymbolKind.VARIABLE && currentParameterNames.contains(nameExpr.name())) {
                    report(range, "暂不支持对参数取址：" + nameExpr.name());
                }
                if (symbol.kind() == SymbolKind.FUNCTION) {
                    report(range, "取址操作数必须是变量");
                }
            });
            return analyzeExpression(operand, scope);
        }
        if (operand instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenType.STAR) {
            return analyzeExpression(operand, scope);
        }
        if (operand instanceof IndexExpr || operand instanceof FieldAccessExpr) {
            return analyzeExpression(operand, scope);
        }
        MiniType operandType = analyzeExpression(operand, scope);
        report(range, "取址操作数必须是变量");
        return operandType;
    }

    private MiniType analyzeAssignment(AssignmentExpr assignmentExpr, Scope scope) {
        if (assignmentExpr.target() instanceof NameExpr nameExpr) {
            scope.resolve(nameExpr.name()).ifPresent(symbol -> {
                if (symbol.kind() == SymbolKind.FUNCTION) {
                    report(assignmentExpr.range(), "赋值左侧不能是函数名");
                }
                if (symbol.type().isArray()) {
                    report(assignmentExpr.range(), "数组不能整体赋值");
                }
            });
        }
        MiniType targetType = analyzeAssignmentTarget(assignmentExpr.target(), scope, assignmentExpr.range());
        MiniType valueType = analyzeExpression(assignmentExpr.value(), scope);
        if (targetType.isArray()) {
            report(assignmentExpr.range(), "数组不能整体赋值");
        }
        if (targetType.isStruct() || valueType.isStruct()) {
            if (!targetType.equals(valueType)) {
                report(assignmentExpr.range(), "结构体赋值类型不匹配");
            }
            if (assignmentExpr.compoundBinaryOperator().isPresent()) {
                report(assignmentExpr.range(), "结构体不支持复合赋值运算");
            }
            return targetType;
        }
        if (assignmentExpr.compoundBinaryOperator().isPresent()) {
            TokenType binaryOperator = assignmentExpr.compoundBinaryOperator().orElseThrow();
            if (!TypeCompatibility.isBinaryCompatible(targetType, valueType, binaryOperator)) {
                report(assignmentExpr.range(), "复合赋值操作数类型不匹配");
            }
            MiniType resultType = TypeCompatibility.binaryResultType(targetType, valueType, binaryOperator);
            if (!TypeCompatibility.isAssignmentCompatible(targetType, resultType)) {
                report(assignmentExpr.range(), "复合赋值结果类型不匹配");
            }
        } else if (!TypeCompatibility.isAssignmentCompatible(targetType, valueType)) {
            report(assignmentExpr.range(), "赋值类型不匹配");
        }
        return targetType;
    }

    private MiniType analyzeConditional(ConditionalExpr conditionalExpr, Scope scope) {
        MiniType conditionType = analyzeExpression(conditionalExpr.condition(), scope);
        MiniType thenType = analyzeExpression(conditionalExpr.thenExpression(), scope);
        MiniType elseType = analyzeExpression(conditionalExpr.elseExpression(), scope);
        if (!TypeCompatibility.isConditionCompatible(conditionType)) {
            report(conditionalExpr.condition().range(), "条件表达式必须是标量或指针类型");
        }
        if (!TypeCompatibility.isConditionalBranchCompatible(thenType, elseType)) {
            report(conditionalExpr.range(), "条件表达式分支类型不匹配");
        }
        return TypeCompatibility.conditionalResultType(thenType, elseType);
    }

    private MiniType analyzeSizeof(SizeofExpr sizeofExpr, Scope scope) {
        MiniType queriedType = sizeofExpr.queriedTypeOptional().orElse(null);
        if (queriedType == null) {
            queriedType = analyzeExpression(sizeofExpr.expressionOptional().orElseThrow(), scope);
        }
        if (!TypeLayout.hasFixedLayout(queriedType) && !hasStructLayout(queriedType)) {
            report(sizeofExpr.range(), "sizeof 只支持固定布局类型");
        }
        return MiniType.UNSIGNED_LONG_LONG;
    }

    private boolean hasStructLayout(MiniType type) {
        if (type instanceof MiniType.StructType structType) {
            return structRegistry.hasLayout(structType.name());
        }
        if (type instanceof MiniType.ArrayType arrayType) {
            return hasStructLayout(arrayType.elementType());
        }
        return false;
    }

    private MiniType analyzeAggregateInit(AggregateInitExpr initializer, Scope scope) {
        return analyzeAggregateInit(initializer, scope, aggregateInitTargetType);
    }

    private MiniType analyzeAggregateInit(AggregateInitExpr initializer, Scope scope, MiniType targetType) {
        if (targetType == null || (!targetType.isStruct() && !targetType.isArray())) {
            report(initializer.range(), "大括号初始化只能用于结构体或数组");
            expressionTypes.put(initializer, MiniType.INT);
            return MiniType.INT;
        }
        if (targetType instanceof MiniType.ArrayType arrayType) {
            analyzeArrayInit(initializer, scope, arrayType);
            expressionTypes.put(initializer, targetType);
            return targetType;
        }
        analyzeStructFields(initializer, scope, (MiniType.StructType) targetType);
        expressionTypes.put(initializer, targetType);
        return targetType;
    }

    private void analyzeArrayInit(AggregateInitExpr initializer, Scope scope, MiniType.ArrayType arrayType) {
        if (initializer.values().size() > arrayType.length()) {
            report(initializer.range(),
                    "数组初始化值过多：容量 " + arrayType.length()
                            + " 个，实际 " + initializer.values().size() + " 个");
        }
        int current = 0;
        for (Expression value : initializer.values()) {
            if (value instanceof DesignatedInitExpr designated
                    && designated.designators().getFirst() instanceof Designator.Index index) {
                current = index.index();
            }
            if (current >= arrayType.length()) {
                report(value.range(), "指定初始化数组下标越界：" + current);
            } else {
                analyzeDesignatedValue(value, scope, arrayType.elementType(), 1, "数组元素");
            }
            current++;
        }
    }

    private void analyzeStructFields(
            AggregateInitExpr initializer,
            Scope scope,
            MiniType.StructType targetStruct
    ) {
        java.util.List<StructFieldLayout> fields = structRegistry.fields(targetStruct.name());
        if (fields == null) {
            report(initializer.range(), "未知结构体类型：" + targetStruct.name());
            return;
        }
        if (initializer.values().size() > fields.size()) {
            report(initializer.range(),
                    "结构体初始化值过多：字段 " + fields.size()
                            + " 个，实际 " + initializer.values().size() + " 个");
        }
        int current = 0;
        for (Expression value : initializer.values()) {
            if (value instanceof DesignatedInitExpr designated
                    && designated.designators().getFirst() instanceof Designator.Field fieldDesignator) {
                current = -1;
                for (int index = 0; index < fields.size(); index++) {
                    if (fields.get(index).name().equals(fieldDesignator.name())) { current = index; break; }
                }
                if (current < 0) {
                    report(value.range(), "未知结构体字段：" + fieldDesignator.name());
                    continue;
                }
            }
            if (current >= fields.size()) {
                report(value.range(), "结构体初始化值过多");
                continue;
            }
            StructFieldLayout field = fields.get(current);
            analyzeDesignatedValue(value, scope, field.type(), 1, "结构体字段 " + field.name());
            current++;
        }
    }

    private void analyzeDesignatedValue(Expression expression, Scope scope, MiniType targetType,
                                        int consumedDesignators, String subject) {
        if (!(expression instanceof DesignatedInitExpr designated)) {
            analyzeInitializerValue(expression, scope, targetType, subject);
            return;
        }
        MiniType nestedTarget = targetType;
        for (int index = consumedDesignators; index < designated.designators().size(); index++) {
            Designator designator = designated.designators().get(index);
            if (designator instanceof Designator.Index arrayIndex && nestedTarget instanceof MiniType.ArrayType array) {
                if (arrayIndex.index() >= array.length()) report(arrayIndex.range(), "指定初始化数组下标越界");
                nestedTarget = array.elementType();
            } else if (designator instanceof Designator.Field field && nestedTarget instanceof MiniType.StructType) {
                var layout = structRegistry.field(nestedTarget, field.name());
                if (layout.isEmpty()) { report(field.range(), "未知结构体字段：" + field.name()); return; }
                nestedTarget = layout.orElseThrow().type();
            } else {
                report(designator.range(), "指定初始化路径与目标类型不匹配");
                return;
            }
        }
        analyzeInitializerValue(designated.value(), scope, nestedTarget, subject);
        expressionTypes.put(designated, nestedTarget);
    }

    private void analyzeInitializerValue(
            Expression value,
            Scope scope,
            MiniType targetType,
            String subject
    ) {
        if (value instanceof AggregateInitExpr nested) {
            analyzeAggregateInit(nested, scope, targetType);
            return;
        }
        MiniType valueType = analyzeExpression(value, scope);
        if (!TypeCompatibility.isAssignmentCompatible(targetType, valueType)) {
            report(value.range(), subject + "初始化类型不匹配");
        }
    }

    private MiniType analyzeAssignmentTarget(Expression target, Scope scope, SourceRange range) {
        if (target instanceof NameExpr) {
            return analyzeExpression(target, scope);
        }
        if (target instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenType.STAR) {
            return analyzeExpression(target, scope);
        }
        if (target instanceof IndexExpr || target instanceof FieldAccessExpr) {
            return analyzeExpression(target, scope);
        }
        report(range, "赋值左侧必须是变量或解引用表达式");
        return MiniType.INT;
    }

    private MiniType analyzeIndex(IndexExpr indexExpr, Scope scope) {
        MiniType targetType = analyzeExpression(indexExpr.target(), scope);
        MiniType indexType = analyzeExpression(indexExpr.index(), scope);
        if (!TypeCompatibility.isIndexCompatible(indexType)) {
            report(indexExpr.index().range(), "数组下标必须是整数类型");
        }
        if (targetType.isArray()) {
            return targetType.elementType();
        }
        if (targetType.isPointer()) {
            if (targetType.pointee().isFunction()) {
                report(indexExpr.range(), "函数指针不能执行下标运算");
                return MiniType.INT;
            }
            return targetType.pointee();
        }
        report(indexExpr.range(), "下标访问目标必须是数组或指针");
        return MiniType.INT;
    }

    private MiniType analyzeFieldAccess(FieldAccessExpr fieldAccessExpr, Scope scope) {
        MiniType targetType = analyzeExpression(fieldAccessExpr.target(), scope);
        MiniType structType = targetType;
        if (fieldAccessExpr.viaPointer()) {
            if (!targetType.isPointer() || !(targetType.pointee() instanceof MiniType.StructType)) {
                report(fieldAccessExpr.range(), "指针字段访问目标必须是结构体指针");
                return MiniType.INT;
            }
            structType = targetType.pointee();
        } else if (!(targetType instanceof MiniType.StructType)) {
            report(fieldAccessExpr.range(), "字段访问目标必须是结构体");
            return MiniType.INT;
        }
        return structRegistry.field(structType, fieldAccessExpr.fieldName())
                .map(StructFieldLayout::type)
                .orElseGet(() -> {
                    report(fieldAccessExpr.range(), "未知结构体字段：" + fieldAccessExpr.fieldName());
                    return MiniType.INT;
                });
    }

    private MiniType resolveVariable(Scope scope, String name, SourceRange range) {
        var symbol = scope.resolve(name).filter(candidate -> candidate.kind() == SymbolKind.VARIABLE);
        if (symbol.isPresent()) {
            return symbol.orElseThrow().type();
        }
        if (scope.resolve(name).filter(candidate -> candidate.kind() == SymbolKind.FUNCTION).isPresent()) {
            return functionRegistry.resolveFunctionAddress(name, range);
        }
        report(range, "未解析变量：" + name);
        return MiniType.INT;
    }

    private MiniType resolveFunctionPointerCall(CallExpr callExpr, Scope scope, ArrayList<MiniType> argumentTypes) {
        MiniType calleeType = analyzeExpression(callExpr.callee(), scope);
        if (!calleeType.isPointer() || !calleeType.pointee().isFunction()) {
            report(callExpr.range(), "函数指针调用目标必须是函数指针");
            return MiniType.INT;
        }
        MiniType functionType = calleeType.pointee();
        MiniType.FunctionType signature = (MiniType.FunctionType) functionType;
        if ((!signature.variadic() && signature.parameterTypes().size() != argumentTypes.size())
                || (signature.variadic() && argumentTypes.size() < signature.parameterTypes().size())) {
            report(callExpr.range(), "函数指针调用实参数量不匹配");
        } else {
            for (int index = 0; index < signature.parameterTypes().size(); index++) {
                if (!TypeCompatibility.isArgumentCompatible(
                        signature.parameterTypes().get(index),
                        argumentTypes.get(index)
                )) {
                    report(callExpr.arguments().get(index).range(), "函数指针调用实参类型不匹配");
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

    private void report(SourceRange range, String message) {
        diagnostics.add(new Diagnostic("SEM001", Diagnostic.Severity.ERROR, message, range));
    }
}
