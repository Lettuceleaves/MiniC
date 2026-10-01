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
import minic.compiler.parser.node.Expression.AlignofExpr;
import minic.compiler.parser.node.Expression.DesignatedInitExpr;
import minic.compiler.parser.node.Expression.Designator;
import minic.compiler.parser.node.Expression.UnaryExpr;
import minic.compiler.parser.node.Expression.VaArgExpr;
import minic.compiler.parser.node.Expression.VaCopyExpr;
import minic.compiler.parser.node.Expression.VaEndExpr;
import minic.compiler.parser.node.Expression.VaStartExpr;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.semantic.model.Scope;
import minic.compiler.semantic.model.StructLayout.StructFieldLayout;
import minic.compiler.semantic.model.Symbol.SymbolKind;
import minic.SourceRange;
import minic.compiler.Diagnostic;

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
    private FunctionDecl currentFunction;
    private MiniType aggregateInitTargetType;
    private int unevaluatedDepth;

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

    void setCurrentFunction(FunctionDecl function) {
        currentFunction = function;
        setCurrentParameterNames(function == null
                ? List.of()
                : function.parameters().stream().map(minic.compiler.parser.node.Declaration.Parameter::name).toList());
    }

    void setAggregateInitTargetType(MiniType type) {
        this.aggregateInitTargetType = type;
    }

    MiniType analyzeExpression(Expression expression, Scope scope) {
        MiniType type = switch (expression) {
            case BoolLiteralExpr ignored -> MiniType.BOOL;
            case CharLiteralExpr value -> switch (value.encoding()) {
                case ORDINARY, UTF8 -> MiniType.CHAR;
                case UTF16 -> MiniType.UNSIGNED_SHORT;
                case UTF32 -> MiniType.UNSIGNED_INT;
            };
            case IntegerLiteralExpr ignored -> MiniType.INT;
            case IntegerConstantExpr integerConstantExpr -> integerConstantExpr.type();
            case LongLiteralExpr ignored -> MiniType.LONG;
            case FloatLiteralExpr ignored -> MiniType.FLOAT;
            case DoubleLiteralExpr ignored -> MiniType.DOUBLE;
            case NullLiteralExpr ignored -> MiniType.NULL;
            case StringLiteralExpr value -> switch (value.encoding()) {
                case ORDINARY, UTF8 -> MiniType.CHAR.pointerTo();
                case UTF16 -> MiniType.UNSIGNED_SHORT.pointerTo();
                case UTF32 -> MiniType.UNSIGNED_INT.pointerTo();
            };
            case NameExpr nameExpr -> resolveVariable(scope, nameExpr.name(), nameExpr.range());
            case AssignmentExpr assignmentExpr -> analyzeAssignment(assignmentExpr, scope);
            case BinaryExpr binaryExpr -> {
                MiniType leftType = analyzeExpression(binaryExpr.left(), scope);
                MiniType rightType = analyzeExpression(binaryExpr.right(), scope);
                if (binaryExpr.operator() == TokenType.EQUAL_EQUAL || binaryExpr.operator() == TokenType.BANG_EQUAL) {
                    leftType = TypeCompatibility.pointerContextType(rightType, leftType, binaryExpr.left());
                    rightType = TypeCompatibility.pointerContextType(leftType, rightType, binaryExpr.right());
                }
                if (!TypeCompatibility.isBinaryCompatible(leftType, rightType, binaryExpr.operator())) {
                    report(binaryExpr.range(), "二元表达式操作数类型不匹配");
                }
                yield TypeCompatibility.binaryResultType(leftType, rightType, binaryExpr.operator());
            }
            case GroupingExpr groupingExpr -> analyzeExpression(groupingExpr.expression(), scope);
            case IndexExpr indexExpr -> analyzeIndex(indexExpr, scope);
            case FieldAccessExpr fieldAccessExpr -> analyzeFieldAccess(fieldAccessExpr, scope);
            case UnaryExpr unaryExpr -> analyzeUnary(unaryExpr, scope);
            case Expression.PostfixUpdateExpr update -> analyzeUpdate(update.target(), update.range(), scope);
            case ConditionalExpr conditionalExpr -> analyzeConditional(conditionalExpr, scope);
            case CastExpr castExpr -> analyzeCast(castExpr, scope);
            case CommaExpr commaExpr -> analyzeComma(commaExpr, scope);
            case SizeofExpr sizeofExpr -> analyzeSizeof(sizeofExpr, scope);
            case AlignofExpr alignofExpr -> analyzeAlignof(alignofExpr, scope);
            case VaStartExpr vaStartExpr -> analyzeVaStart(vaStartExpr, scope);
            case VaArgExpr vaArgExpr -> analyzeVaArg(vaArgExpr, scope);
            case VaCopyExpr vaCopyExpr -> analyzeVaCopy(vaCopyExpr, scope);
            case VaEndExpr vaEndExpr -> analyzeVaEnd(vaEndExpr, scope);
            case CallExpr callExpr -> {
                ArrayList<MiniType> argumentTypes = new ArrayList<>();
                for (Expression argument : callExpr.arguments()) {
                    argumentTypes.add(analyzeExpression(argument, scope));
                }
                MiniType returnType = isDirectFunctionCall(callExpr, scope)
                        ? functionRegistry.resolveFunction(callExpr, argumentTypes, unevaluatedDepth == 0)
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
        if (unaryExpr.operator() == TokenType.PLUS_PLUS || unaryExpr.operator() == TokenType.MINUS_MINUS) {
            return analyzeUpdate(unaryExpr.operand(), unaryExpr.range(), scope);
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

    private MiniType analyzeUpdate(Expression target, SourceRange range, Scope scope) {
        MiniType targetType = analyzeAssignmentTarget(target, scope, range);
        if ((!targetType.isScalar() && !targetType.isPointer())
                || (targetType.isPointer() && (targetType.pointee().isFunction() || targetType.pointee().isVoid()))) {
            report(range, "自增自减操作数必须是标量或对象指针");
        } else if (targetType.isPointer() && !TypeLayout.hasFixedLayout(targetType.pointee())
                && !hasStructLayout(targetType.pointee())) {
            report(range, "自增自减要求指向完整对象类型的指针");
        }
        return targetType;
    }

    private MiniType analyzeAddressOperand(Expression operand, Scope scope, SourceRange range) {
        if (operand instanceof GroupingExpr groupingExpr) {
            return analyzeAddressOperand(groupingExpr.expression(), scope, range);
        }
        if (operand instanceof NameExpr nameExpr) {
            if (scope.resolve(nameExpr.name()).filter(s -> s.kind() == SymbolKind.FUNCTION).isPresent()) {
                // Ordinary expression lookup already decays functions to pointers. Address-of
                // suppresses that decay so &f has the same type as the expression f.
                return analyzeExpression(operand, scope).pointee();
            }
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
            if (!targetType.unqualified().equals(valueType.unqualified())) {
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
        } else if (!TypeCompatibility.isAssignmentCompatible(targetType, valueType, assignmentExpr.value())) {
            report(assignmentExpr.range(), "赋值类型不匹配");
        }
        return targetType;
    }

    private MiniType analyzeConditional(ConditionalExpr conditionalExpr, Scope scope) {
        MiniType conditionType = analyzeExpression(conditionalExpr.condition(), scope);
        MiniType thenType = analyzeExpression(conditionalExpr.thenExpression(), scope);
        MiniType elseType = analyzeExpression(conditionalExpr.elseExpression(), scope);
        thenType = TypeCompatibility.pointerContextType(elseType, thenType, conditionalExpr.thenExpression());
        elseType = TypeCompatibility.pointerContextType(thenType, elseType, conditionalExpr.elseExpression());
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
            queriedType = analyzeTypeQueryOperand(sizeofExpr.expressionOptional().orElseThrow(), scope);
        }
        if (!TypeLayout.hasFixedLayout(queriedType) && !hasStructLayout(queriedType)) {
            report(sizeofExpr.range(), "sizeof 只支持固定布局类型");
        }
        return MiniType.UNSIGNED_LONG_LONG;
    }

    private MiniType analyzeAlignof(AlignofExpr alignofExpr, Scope scope) {
        MiniType queriedType = alignofExpr.queriedTypeOptional().orElse(null);
        if (queriedType == null) {
            queriedType = analyzeTypeQueryOperand(alignofExpr.expressionOptional().orElseThrow(), scope);
        }
        if (!TypeLayout.hasFixedLayout(queriedType) && !hasStructLayout(queriedType)) {
            report(alignofExpr.range(), "alignof 只支持具有完整布局的对象类型");
        }
        return MiniType.UNSIGNED_LONG_LONG;
    }

    /** Queries still type-check operands, but do not require definitions of unused functions. */
    private MiniType analyzeTypeQueryOperand(Expression operand, Scope scope) {
        unevaluatedDepth++;
        try {
            MiniType type = analyzeExpression(operand, scope);
            Expression ungrouped = unwrapGrouping(operand);
            if (ungrouped instanceof NameExpr name) {
                var function = scope.resolve(name.name()).filter(symbol -> symbol.kind() == SymbolKind.FUNCTION);
                if (function.isPresent()) {
                    // sizeof/alignof suppress function-to-pointer conversion at the operand root.
                    type = function.orElseThrow().type();
                    expressionTypes.put(operand, type);
                }
            }
            return type;
        } finally {
            unevaluatedDepth--;
        }
    }

    private MiniType analyzeVaStart(VaStartExpr expression, Scope scope) {
        requireVariadicFunction(expression.range(), "va_start");
        requireVaListLocal(expression.list(), scope, "va_start");
        analyzeExpression(expression.lastParameter(), scope);
        String expectedLast = currentFunction == null || currentFunction.parameters().isEmpty()
                ? null
                : currentFunction.parameters().getLast().name();
        Expression last = unwrapGrouping(expression.lastParameter());
        if (!(last instanceof NameExpr nameExpr) || expectedLast == null || !expectedLast.equals(nameExpr.name())) {
            report(expression.lastParameter().range(),
                    "va_start 的 last 参数必须是最后一个命名参数");
        }
        return MiniType.VOID;
    }

    private MiniType analyzeVaArg(VaArgExpr expression, Scope scope) {
        requireVariadicFunction(expression.range(), "va_arg");
        requireVaListLocal(expression.list(), scope, "va_arg");
        MiniType requested = expression.requestedType().unqualified();
        if (!isSupportedVaArgType(requested)) {
            if (requested.equals(MiniType.FLOAT)) {
                report(expression.range(), "va_arg 不能请求 float：可变参数默认提升为 double");
            } else if (requested.equals(MiniType.CHAR)
                    || requested.equals(MiniType.SIGNED_CHAR)
                    || requested.equals(MiniType.UNSIGNED_CHAR)
                    || requested.equals(MiniType.SHORT)
                    || requested.equals(MiniType.UNSIGNED_SHORT)
                    || requested.equals(MiniType.BOOL)) {
                report(expression.range(), "va_arg 请求的窄整数类型会默认提升为 int");
            } else {
                report(expression.range(), "va_arg 暂不支持该类型：" + expression.requestedType());
            }
            return MiniType.INT;
        }
        return requested;
    }

    private MiniType analyzeVaCopy(VaCopyExpr expression, Scope scope) {
        requireVariadicFunction(expression.range(), "va_copy");
        requireVaListLocal(expression.destination(), scope, "va_copy");
        requireVaListLocal(expression.source(), scope, "va_copy");
        return MiniType.VOID;
    }

    private MiniType analyzeVaEnd(VaEndExpr expression, Scope scope) {
        requireVariadicFunction(expression.range(), "va_end");
        requireVaListLocal(expression.list(), scope, "va_end");
        return MiniType.VOID;
    }

    private void requireVariadicFunction(SourceRange range, String intrinsic) {
        if (currentFunction == null || !currentFunction.variadic()) {
            report(range, intrinsic + " 只能在 variadic 函数中使用");
        }
    }

    private void requireVaListLocal(Expression operand, Scope scope, String intrinsic) {
        MiniType type = analyzeExpression(operand, scope);
        if (!type.isVaList()) {
            report(operand.range(), intrinsic + " 操作数必须是 va_list");
            return;
        }
        Expression unwrapped = unwrapGrouping(operand);
        if (!(unwrapped instanceof NameExpr nameExpr)) {
            report(operand.range(), intrinsic + " 当前只支持局部 va_list 变量");
        } else if (currentParameterNames.contains(nameExpr.name())) {
            report(operand.range(), intrinsic + " 当前不支持 va_list 形参");
        }
    }

    private Expression unwrapGrouping(Expression expression) {
        Expression current = expression;
        while (current instanceof GroupingExpr groupingExpr) {
            current = groupingExpr.expression();
        }
        return current;
    }

    private boolean isSupportedVaArgType(MiniType type) {
        if (type.isPointer()) {
            return true;
        }
        return type.equals(MiniType.INT)
                || type.equals(MiniType.UNSIGNED_INT)
                || type.equals(MiniType.LONG)
                || type.equals(MiniType.UNSIGNED_LONG)
                || type.equals(MiniType.LONG_LONG)
                || type.equals(MiniType.UNSIGNED_LONG_LONG)
                || type.equals(MiniType.DOUBLE);
    }

    private boolean hasStructLayout(MiniType type) {
        type = type.unqualified();
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
        MiniType unqualifiedTarget = targetType.unqualified();
        if (unqualifiedTarget instanceof MiniType.ArrayType arrayType) {
            analyzeArrayInit(initializer, scope, arrayType);
            expressionTypes.put(initializer, targetType);
            return targetType;
        }
        analyzeStructFields(initializer, scope, (MiniType.StructType) unqualifiedTarget);
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
                    var promoted = structRegistry.field(targetStruct, fieldDesignator.name());
                    if (promoted.isEmpty()) {
                        report(value.range(), "未知结构体字段：" + fieldDesignator.name());
                        continue;
                    }
                    analyzeDesignatedValue(value, scope, promoted.orElseThrow().type(), 1,
                            "结构体字段 " + fieldDesignator.name());
                    current = fields.size();
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
            MiniType unqualifiedNestedTarget = nestedTarget.unqualified();
            if (designator instanceof Designator.Index arrayIndex && unqualifiedNestedTarget instanceof MiniType.ArrayType array) {
                if (arrayIndex.index() >= array.length()) report(arrayIndex.range(), "指定初始化数组下标越界");
                nestedTarget = array.elementType();
            } else if (designator instanceof Designator.Field field && unqualifiedNestedTarget instanceof MiniType.StructType) {
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
        if (!TypeCompatibility.isAssignmentCompatible(targetType, valueType, value)) {
            report(value.range(), subject + "初始化类型不匹配");
        }
    }

    private MiniType analyzeAssignmentTarget(Expression target, Scope scope, SourceRange range) {
        MiniType type;
        if (target instanceof GroupingExpr groupingExpr) {
            type = analyzeAssignmentTarget(groupingExpr.expression(), scope, range);
            expressionTypes.put(target, type);
        } else if (target instanceof NameExpr
                || target instanceof IndexExpr
                || target instanceof FieldAccessExpr
                || target instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenType.STAR) {
            type = analyzeExpression(target, scope);
        } else {
            report(range, "赋值左侧必须是变量或解引用表达式");
            return MiniType.INT;
        }
        if (type.isConstQualified()) {
            report(range, "不能修改 const 左值");
        }
        return type;
    }

    private MiniType analyzeIndex(IndexExpr indexExpr, Scope scope) {
        MiniType targetType = analyzeExpression(indexExpr.target(), scope);
        MiniType indexType = analyzeExpression(indexExpr.index(), scope);
        if (!TypeCompatibility.isIndexCompatible(indexType)) {
            report(indexExpr.index().range(), "数组下标必须是整数类型");
        }
        if (targetType.isArray()) {
            return inheritObjectQualifiers(targetType, targetType.elementType());
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
            if (!targetType.isPointer() || !(targetType.pointee().unqualified() instanceof MiniType.StructType)) {
                report(fieldAccessExpr.range(), "指针字段访问目标必须是结构体指针");
                return MiniType.INT;
            }
            structType = targetType.pointee();
        } else if (!(targetType.unqualified() instanceof MiniType.StructType)) {
            report(fieldAccessExpr.range(), "字段访问目标必须是结构体");
            return MiniType.INT;
        }
        MiniType owningObjectType = structType;
        return structRegistry.field(structType, fieldAccessExpr.fieldName())
                .map(StructFieldLayout::type)
                .map(fieldType -> inheritObjectQualifiers(owningObjectType, fieldType))
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
            return functionRegistry.resolveFunctionAddress(name, range, unevaluatedDepth == 0);
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
        MiniType.FunctionType signature = (MiniType.FunctionType) functionType.unqualified();
        if ((!signature.variadic() && signature.parameterTypes().size() != argumentTypes.size())
                || (signature.variadic() && argumentTypes.size() < signature.parameterTypes().size())) {
            report(callExpr.range(), "函数指针调用实参数量不匹配");
        } else {
            for (int index = 0; index < signature.parameterTypes().size(); index++) {
                if (!TypeCompatibility.isArgumentCompatible(
                        signature.parameterTypes().get(index),
                        argumentTypes.get(index),
                        callExpr.arguments().get(index)
                )) {
                    report(callExpr.arguments().get(index).range(), "函数指针调用实参类型不匹配");
                }
            }
        }
        return functionType.returnType();
    }

    private MiniType inheritObjectQualifiers(MiniType ownerType, MiniType memberType) {
        java.util.EnumSet<MiniType.TypeQualifier> qualifiers = java.util.EnumSet.noneOf(MiniType.TypeQualifier.class);
        qualifiers.addAll(memberType.qualifiers());
        if (ownerType.isConstQualified()) {
            qualifiers.add(MiniType.TypeQualifier.CONST);
        }
        if (ownerType.isVolatileQualified()) {
            qualifiers.add(MiniType.TypeQualifier.VOLATILE);
        }
        return MiniType.qualified(memberType.unqualified(), qualifiers);
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
