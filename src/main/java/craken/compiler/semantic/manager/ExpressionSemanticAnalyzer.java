package craken.compiler.semantic.manager;

import craken.compiler.parser.node.Expression.AssignmentExpr;
import craken.compiler.parser.node.Expression.BinaryExpr;
import craken.compiler.parser.node.Expression.BoolLiteralExpr;
import craken.compiler.parser.node.Expression.CallExpr;
import craken.compiler.parser.node.Expression.CharLiteralExpr;
import craken.compiler.parser.node.Expression.ConditionalExpr;
import craken.compiler.parser.node.Expression.CastExpr;
import craken.compiler.parser.node.Expression.CommaExpr;
import craken.compiler.parser.node.Expression.DoubleLiteralExpr;
import craken.compiler.parser.node.Expression;
import craken.compiler.parser.node.Expression.FieldAccessExpr;
import craken.compiler.parser.node.Expression.FloatLiteralExpr;
import craken.compiler.parser.node.Expression.GroupingExpr;
import craken.compiler.parser.node.Expression.IndexExpr;
import craken.compiler.parser.node.Expression.IntegerLiteralExpr;
import craken.compiler.parser.node.Expression.IntegerConstantExpr;
import craken.compiler.parser.node.Expression.LongLiteralExpr;
import craken.compiler.parser.node.Expression.NameExpr;
import craken.compiler.parser.node.Expression.NullLiteralExpr;
import craken.compiler.parser.node.Expression.SizeofExpr;
import craken.compiler.parser.node.Expression.StringLiteralExpr;
import craken.compiler.parser.node.Expression.AggregateInitExpr;
import craken.compiler.parser.node.Expression.AlignofExpr;
import craken.compiler.parser.node.Expression.DesignatedInitExpr;
import craken.compiler.parser.node.Expression.Designator;
import craken.compiler.parser.node.Expression.UnaryExpr;
import craken.compiler.parser.node.Expression.VaArgExpr;
import craken.compiler.parser.node.Expression.VaCopyExpr;
import craken.compiler.parser.node.Expression.VaEndExpr;
import craken.compiler.parser.node.Expression.VaStartExpr;
import craken.compiler.parser.node.Declaration.FunctionDecl;
import craken.compiler.type.CrakenType;
import craken.compiler.type.TypeLayout;
import craken.compiler.lexer.token.TokenType;
import craken.compiler.semantic.model.Scope;
import craken.compiler.semantic.model.StructLayout.StructFieldLayout;
import craken.compiler.semantic.model.Symbol.SymbolKind;
import craken.SourceRange;
import craken.compiler.Diagnostic;
import craken.compiler.parser.node.Expression.CleanupExpr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ExpressionSemanticAnalyzer {
    private final FunctionRegistry functionRegistry;
    private final StructRegistry structRegistry;
    private final List<Diagnostic> diagnostics;
    private final Map<Expression, CrakenType> expressionTypes;
    private Set<String> currentParameterNames = Set.of();
    private FunctionDecl currentFunction;
    private CrakenType aggregateInitTargetType;
    private int unevaluatedDepth;
    private final Set<craken.compiler.semantic.model.Symbol> valueCaptures = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    ExpressionSemanticAnalyzer(
            FunctionRegistry functionRegistry,
            StructRegistry structRegistry,
            List<Diagnostic> diagnostics,
            Map<Expression, CrakenType> expressionTypes
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
                : function.parameters().stream().map(craken.compiler.parser.node.Declaration.Parameter::name).toList());
    }

    void setAggregateInitTargetType(CrakenType type) {
        this.aggregateInitTargetType = type;
    }

    CrakenType analyzeUnevaluated(Expression expression, Scope scope) {
        unevaluatedDepth++;
        try { return analyzeExpression(expression,scope); }
        finally { unevaluatedDepth--; }
    }

    CrakenType analyzeExpression(Expression expression, Scope scope) {
        CrakenType type = switch (expression) {
            case BoolLiteralExpr ignored -> CrakenType.BOOL;
            case CharLiteralExpr value -> switch (value.encoding()) {
                case ORDINARY, UTF8 -> CrakenType.CHAR;
                case UTF16 -> CrakenType.UNSIGNED_SHORT;
                case UTF32 -> CrakenType.UNSIGNED_INT;
            };
            case IntegerLiteralExpr ignored -> CrakenType.INT;
            case IntegerConstantExpr integerConstantExpr -> integerConstantExpr.type();
            case LongLiteralExpr ignored -> CrakenType.LONG;
            case FloatLiteralExpr ignored -> CrakenType.FLOAT;
            case DoubleLiteralExpr literal -> literal.literalType();
            case NullLiteralExpr ignored -> CrakenType.NULL;
            case StringLiteralExpr value -> switch (value.encoding()) {
                case ORDINARY, UTF8 -> CrakenType.CHAR.pointerTo();
                case UTF16 -> CrakenType.UNSIGNED_SHORT.pointerTo();
                case UTF32 -> CrakenType.UNSIGNED_INT.pointerTo();
            };
            case NameExpr nameExpr -> resolveVariable(scope, nameExpr.name(), nameExpr.range());
            case Expression.LetExpr capture -> analyzeCapture(capture, scope);
            case craken.compiler.parser.node.Expression.CleanupExpr cleanup -> {
                CrakenType valueType = analyzeExpression(cleanup.value(), scope);
                if (!analyzeExpression(cleanup.cleanup(), scope).isVoid())
                    report(cleanup.cleanup().range(), "表达式清理必须是 void 表达式");
                yield valueType;
            }
            case Expression.MaterializeExpr temporary -> analyzeMaterialization(temporary, scope);
            case Expression.ObjectInitExpr construction -> analyzeObjectInitialization(construction, scope);
            case Expression.InitializeExpr initialization -> analyzeInitialization(initialization, scope);
            case AssignmentExpr assignmentExpr -> analyzeAssignment(assignmentExpr, scope);
            case BinaryExpr binaryExpr -> {
                CrakenType leftType = analyzeExpression(binaryExpr.left(), scope);
                CrakenType rightType = analyzeExpression(binaryExpr.right(), scope);
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
                ArrayList<CrakenType> argumentTypes = new ArrayList<>();
                for (Expression argument : callExpr.arguments()) {
                    argumentTypes.add(analyzeExpression(argument, scope));
                }
                CrakenType returnType = isDirectFunctionCall(callExpr, scope)
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

    private CrakenType analyzeCast(CastExpr castExpr, Scope scope) {
        CrakenType source = TypeCompatibility.decay(analyzeExpression(castExpr.operand(), scope));
        CrakenType target = castExpr.targetType();
        boolean sourceScalar = source.isScalar() || source.isPointer() || source.isNullPointer();
        boolean targetScalar = target.isScalar() || target.isPointer();
        if (!target.isVoid() && !(sourceScalar && targetScalar)) {
            report(castExpr.range(), "类型转换要求标量、指针或 void 目标类型");
        }
        return target;
    }

    private CrakenType analyzeCapture(Expression.LetExpr capture, Scope scope) {
        CrakenType value = analyzeExpression(capture.initializer(), scope);
        if ((!capture.type().isScalar() && !capture.type().isPointer())
                || !TypeCompatibility.isAssignmentCompatible(capture.type(), value, capture.initializer())) {
            report(capture.range(), "表达式的值与目标类型不兼容");
        }
        Scope bodyScope = Scope.detachedChild(scope, capture.range());
        var symbol = new craken.compiler.semantic.model.Symbol(capture.name(), SymbolKind.VARIABLE, capture.range(),
                CrakenType.qualified(capture.type(), Set.of(CrakenType.TypeQualifier.CONST)), null);
        bodyScope.define(symbol);
        valueCaptures.add(symbol);
        return analyzeExpression(capture.body(), bodyScope);
    }

    private CrakenType analyzeMaterialization(Expression.MaterializeExpr temporary, Scope scope) {
        CrakenType type = temporary.type();
        if ((!type.isScalar() && !type.isPointer() && !type.isStruct() && !type.isArray())
                || (!TypeLayout.hasFixedLayout(type) && !hasStructLayout(type))) {
            report(temporary.range(), "临时对象需要完整的标量、指针、记录或数组类型");
        }
        // The temporary is newly created storage. Its explicit type supplies the
        // initializer-list target, just as InitializeExpr does for a subobject.
        analyzeFirstInitialization(temporary.initializer(), type, scope);
        return type.pointerTo();
    }

    private CrakenType analyzeInitialization(Expression.InitializeExpr initialization, Scope scope) {
        CrakenType target = analyzeAddressOperand(initialization.target(), scope, initialization.range());
        expressionTypes.put(initialization.target(), target);
        if ((!target.isScalar() && !target.isPointer() && !target.isStruct() && !target.isArray())
                || (!TypeLayout.hasFixedLayout(target) && !hasStructLayout(target))) {
            report(initialization.target().range(), "初始化目标需要完整的标量、指针、记录或数组对象");
        }
        analyzeFirstInitialization(initialization.value(), target, scope);
        return CrakenType.VOID;
    }

    private void analyzeFirstInitialization(Expression value, CrakenType target, Scope scope) {
        if (value instanceof GroupingExpr group) {
            analyzeFirstInitialization(group.expression(), target, scope);
            expressionTypes.put(group, expressionTypes.get(group.expression()));
            return;
        }
        if (!(value instanceof AggregateInitExpr aggregate)) {
            CrakenType actual = analyzeExpression(value, scope);
            if (!isArrayConstruction(target, actual, value)
                    && (target.isArray() || !TypeCompatibility.isAssignmentCompatible(target, actual, value)))
                report(value.range(), "对象初始化类型不兼容");
            return;
        }
        expressionTypes.put(aggregate, target);
        CrakenType raw = target.unqualified();
        List<CrakenType> members;
        if (raw instanceof CrakenType.ArrayType array) {
            if (array.length() < 0) { report(value.range(), "数组初始化目标必须具有完整长度"); return; }
            members = java.util.Collections.nCopies(array.length(), array.elementType());
        } else if (raw instanceof CrakenType.StructType record) {
            var fields = structRegistry.fields(record.name());
            if (fields == null) { report(value.range(), "记录初始化目标必须具有完整布局"); return; }
            if (!aggregate.values().isEmpty() && structRegistry.isUnion(record.name())) {
                report(value.range(), "union 的显式子对象初始化尚需活跃成员规则");
                return;
            }
            members = fields.stream().map(StructFieldLayout::type).toList();
        } else if (target.isScalar() || target.isPointer()) {
            members = List.of(target);
        } else {
            report(value.range(), "初始化列表目标必须是对象类型");
            return;
        }
        if (aggregate.values().size() > members.size()) report(value.range(), "对象初始化列表中的值过多");
        for (int index = 0; index < aggregate.values().size() && index < members.size(); index++) {
            Expression member = aggregate.values().get(index);
            if (member instanceof DesignatedInitExpr) {
                report(member.range(), "显式首次初始化只接受按声明顺序排列的初始化列表");
            } else {
                analyzeFirstInitialization(member, members.get(index), scope);
            }
        }
    }

    private CrakenType analyzeObjectInitialization(Expression.ObjectInitExpr construction, Scope scope) {
        CrakenType type = construction.type();
        if ((!type.isStruct() && !type.isArray())
                || (!TypeLayout.hasFixedLayout(type) && !hasStructLayout(type))) {
            report(construction.range(), "原地构造需要完整的记录类型");
        }
        Scope bodyScope = Scope.detachedChild(scope, construction.range());
        var symbol = new craken.compiler.semantic.model.Symbol(construction.destinationName(), SymbolKind.VARIABLE,
                construction.range(), CrakenType.qualified(type.unqualified().pointerTo(), Set.of(CrakenType.TypeQualifier.CONST)), null);
        bodyScope.define(symbol);
        valueCaptures.add(symbol);
        if (!analyzeExpression(construction.body(), bodyScope).isVoid()) {
            report(construction.body().range(), "原地构造操作必须具有 void 类型");
        }
        return type;
    }

    private boolean isArrayConstruction(CrakenType target, CrakenType actual, Expression value) {
        return target.isArray() && sameArrayObjectType(target, actual)
                && Expression.ObjectInitExpr.occursInResultOf(value);
    }

    private boolean sameArrayObjectType(CrakenType first, CrakenType second) {
        if (first.isArray() && second.isArray()) return first.arrayLength() == second.arrayLength()
                && sameArrayObjectType(first.elementType(), second.elementType());
        return first.unqualified().equals(second.unqualified());
    }

    private CrakenType analyzeComma(CommaExpr commaExpr, Scope scope) {
        CrakenType type = CrakenType.INT;
        for (Expression expression : commaExpr.expressions()) {
            type = analyzeExpression(expression, scope);
        }
        return type;
    }

    private CrakenType analyzeUnary(UnaryExpr unaryExpr, Scope scope) {
        if (unaryExpr.operator() == TokenType.AMPERSAND) {
            CrakenType operandType = analyzeAddressOperand(unaryExpr.operand(), scope, unaryExpr.range());
            return operandType.pointerTo();
        }
        if (unaryExpr.operator() == TokenType.PLUS_PLUS || unaryExpr.operator() == TokenType.MINUS_MINUS) {
            return analyzeUpdate(unaryExpr.operand(), unaryExpr.range(), scope);
        }
        CrakenType operandType = analyzeExpression(unaryExpr.operand(), scope);
        if (unaryExpr.operator() == TokenType.STAR) {
            operandType = TypeCompatibility.decay(operandType);
            if (!operandType.isPointer()) {
                report(unaryExpr.range(), "解引用操作数必须是指针");
                return CrakenType.INT;
            }
            return operandType.pointee();
        }
        if (unaryExpr.operator() == TokenType.BANG) {
            if (!TypeCompatibility.isConditionCompatible(operandType)) {
                report(unaryExpr.range(), "! 操作数必须是标量或指针");
            }
            return CrakenType.INT;
        }
        if (unaryExpr.operator() == TokenType.TILDE) {
            if (!operandType.isIntegerScalar()) {
                report(unaryExpr.range(), "~ 操作数必须是整数类型");
            }
            return operandType.isIntegerScalar() ? TypeCompatibility.integerPromotion(operandType) : CrakenType.INT;
        }
        if (unaryExpr.operator() == TokenType.MINUS || unaryExpr.operator() == TokenType.PLUS) {
            if (!operandType.isScalar()) {
                report(unaryExpr.range(), "一元 +/- 操作数必须是标量类型");
            }
            return operandType.isIntegerScalar()
                    ? TypeCompatibility.integerPromotion(operandType)
                    : operandType.isScalar() ? operandType : CrakenType.INT;
        }
        throw new IllegalArgumentException("unsupported unary operator: " + unaryExpr.operator());
    }

    private CrakenType analyzeUpdate(Expression target, SourceRange range, Scope scope) {
        CrakenType targetType = analyzeAssignmentTarget(target, scope, range);
        if ((!targetType.isScalar() && !targetType.isPointer())
                || (targetType.isPointer() && (targetType.pointee().isFunction() || targetType.pointee().isVoid()))) {
            report(range, "自增自减操作数必须是标量或对象指针");
        } else if (targetType.isPointer() && !TypeLayout.hasFixedLayout(targetType.pointee())
                && !hasStructLayout(targetType.pointee())) {
            report(range, "自增自减要求指向完整对象类型的指针");
        }
        return targetType;
    }

    private CrakenType analyzeAddressOperand(Expression operand, Scope scope, SourceRange range) {
        if (operand instanceof GroupingExpr groupingExpr) {
            return analyzeAddressOperand(groupingExpr.expression(), scope, range);
        }
        if (operand instanceof NameExpr nameExpr) {
            if (scope.resolve(nameExpr.name()).filter(valueCaptures::contains).isPresent()) {
                report(range, "内部值捕获不是可取址对象");
            }
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
        CrakenType operandType = analyzeExpression(operand, scope);
        report(range, "取址操作数必须是变量");
        return operandType;
    }

    private CrakenType analyzeAssignment(AssignmentExpr assignmentExpr, Scope scope) {
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
        CrakenType targetType = analyzeAssignmentTarget(assignmentExpr.target(), scope, assignmentExpr.range());
        CrakenType valueType = analyzeExpression(assignmentExpr.value(), scope);
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
            CrakenType resultType = TypeCompatibility.binaryResultType(targetType, valueType, binaryOperator);
            if (!TypeCompatibility.isAssignmentCompatible(targetType, resultType)) {
                report(assignmentExpr.range(), "复合赋值结果类型不匹配");
            }
        } else if (!TypeCompatibility.isAssignmentCompatible(targetType, valueType, assignmentExpr.value())) {
            report(assignmentExpr.range(), "赋值类型不匹配");
        }
        return targetType;
    }

    private CrakenType analyzeConditional(ConditionalExpr conditionalExpr, Scope scope) {
        CrakenType conditionType = analyzeExpression(conditionalExpr.condition(), scope);
        CrakenType thenType = analyzeExpression(conditionalExpr.thenExpression(), scope);
        CrakenType elseType = analyzeExpression(conditionalExpr.elseExpression(), scope);
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

    private CrakenType analyzeSizeof(SizeofExpr sizeofExpr, Scope scope) {
        CrakenType queriedType = sizeofExpr.queriedTypeOptional().orElse(null);
        if (queriedType == null) {
            queriedType = analyzeTypeQueryOperand(sizeofExpr.expressionOptional().orElseThrow(), scope);
        }
        if (!TypeLayout.hasFixedLayout(queriedType) && !hasStructLayout(queriedType)) {
            report(sizeofExpr.range(), "sizeof 只支持固定布局类型");
        }
        return CrakenType.UNSIGNED_LONG_LONG;
    }

    private CrakenType analyzeAlignof(AlignofExpr alignofExpr, Scope scope) {
        CrakenType queriedType = alignofExpr.queriedTypeOptional().orElse(null);
        if (queriedType == null) {
            queriedType = analyzeTypeQueryOperand(alignofExpr.expressionOptional().orElseThrow(), scope);
        }
        CrakenType alignedElement=queriedType;
        while(alignedElement.isArray())alignedElement=alignedElement.elementType();
        if (!TypeLayout.hasFixedLayout(alignedElement) && !hasStructLayout(alignedElement)) {
            report(alignofExpr.range(), "alignof 只支持具有完整布局的对象类型");
        }
        return CrakenType.UNSIGNED_LONG_LONG;
    }

    /** Queries still type-check operands, but do not require definitions of unused functions. */
    private CrakenType analyzeTypeQueryOperand(Expression operand, Scope scope) {
        unevaluatedDepth++;
        try {
            CrakenType type = analyzeExpression(operand, scope);
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

    private CrakenType analyzeVaStart(VaStartExpr expression, Scope scope) {
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
        return CrakenType.VOID;
    }

    private CrakenType analyzeVaArg(VaArgExpr expression, Scope scope) {
        requireVariadicFunction(expression.range(), "va_arg");
        requireVaListLocal(expression.list(), scope, "va_arg");
        CrakenType requested = expression.requestedType().unqualified();
        if (!isSupportedVaArgType(requested)) {
            if (requested.equals(CrakenType.FLOAT)) {
                report(expression.range(), "va_arg 不能请求 float：可变参数默认提升为 double");
            } else if (requested.equals(CrakenType.CHAR)
                    || requested.equals(CrakenType.SIGNED_CHAR)
                    || requested.equals(CrakenType.UNSIGNED_CHAR)
                    || requested.equals(CrakenType.SHORT)
                    || requested.equals(CrakenType.UNSIGNED_SHORT)
                    || requested.equals(CrakenType.BOOL)) {
                report(expression.range(), "va_arg 请求的窄整数类型会默认提升为 int");
            } else {
                report(expression.range(), "va_arg 暂不支持该类型：" + expression.requestedType());
            }
            return CrakenType.INT;
        }
        return requested;
    }

    private CrakenType analyzeVaCopy(VaCopyExpr expression, Scope scope) {
        requireVariadicFunction(expression.range(), "va_copy");
        requireVaListLocal(expression.destination(), scope, "va_copy");
        requireVaListLocal(expression.source(), scope, "va_copy");
        return CrakenType.VOID;
    }

    private CrakenType analyzeVaEnd(VaEndExpr expression, Scope scope) {
        requireVariadicFunction(expression.range(), "va_end");
        requireVaListLocal(expression.list(), scope, "va_end");
        return CrakenType.VOID;
    }

    private void requireVariadicFunction(SourceRange range, String intrinsic) {
        if (currentFunction == null || !currentFunction.variadic()) {
            report(range, intrinsic + " 只能在 variadic 函数中使用");
        }
    }

    private void requireVaListLocal(Expression operand, Scope scope, String intrinsic) {
        CrakenType type = analyzeExpression(operand, scope);
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

    private boolean isSupportedVaArgType(CrakenType type) {
        if (type.isPointer()) {
            return true;
        }
        return type.equals(CrakenType.INT)
                || type.equals(CrakenType.UNSIGNED_INT)
                || type.equals(CrakenType.LONG)
                || type.equals(CrakenType.UNSIGNED_LONG)
                || type.equals(CrakenType.LONG_LONG)
                || type.equals(CrakenType.UNSIGNED_LONG_LONG)
                || type.equals(CrakenType.DOUBLE) || type.equals(CrakenType.LONG_DOUBLE);
    }

    private boolean hasStructLayout(CrakenType type) {
        type = type.unqualified();
        if (type instanceof CrakenType.StructType structType) {
            return structRegistry.hasLayout(structType.name());
        }
        if (type instanceof CrakenType.ArrayType arrayType) {
            return arrayType.length()>0 && hasStructLayout(arrayType.elementType());
        }
        return false;
    }

    private CrakenType analyzeAggregateInit(AggregateInitExpr initializer, Scope scope) {
        return analyzeAggregateInit(initializer, scope, aggregateInitTargetType);
    }

    private CrakenType analyzeAggregateInit(AggregateInitExpr initializer, Scope scope, CrakenType targetType) {
        if (targetType == null || (!targetType.isStruct() && !targetType.isArray())) {
            report(initializer.range(), "大括号初始化只能用于结构体或数组");
            expressionTypes.put(initializer, CrakenType.INT);
            return CrakenType.INT;
        }
        CrakenType unqualifiedTarget = targetType.unqualified();
        if (unqualifiedTarget instanceof CrakenType.ArrayType arrayType) {
            analyzeArrayInit(initializer, scope, arrayType);
            expressionTypes.put(initializer, targetType);
            return targetType;
        }
        analyzeStructFields(initializer, scope, (CrakenType.StructType) unqualifiedTarget);
        expressionTypes.put(initializer, targetType);
        return targetType;
    }

    private void analyzeArrayInit(AggregateInitExpr initializer, Scope scope, CrakenType.ArrayType arrayType) {
        if (initializer.values().size() > arrayType.length()
                && initializer.values().stream().noneMatch(DesignatedInitExpr.class::isInstance)) {
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
            CrakenType.StructType targetStruct
    ) {
        java.util.List<StructFieldLayout> fields = structRegistry.fields(targetStruct.name());
        if (fields == null) {
            report(initializer.range(), "未知结构体类型：" + targetStruct.name());
            return;
        }
        if (initializer.values().size() > fields.size()
                && initializer.values().stream().noneMatch(DesignatedInitExpr.class::isInstance)) {
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

    private void analyzeDesignatedValue(Expression expression, Scope scope, CrakenType targetType,
                                        int consumedDesignators, String subject) {
        if (!(expression instanceof DesignatedInitExpr designated)) {
            analyzeInitializerValue(expression, scope, targetType, subject);
            return;
        }
        CrakenType nestedTarget = targetType;
        for (int index = consumedDesignators; index < designated.designators().size(); index++) {
            Designator designator = designated.designators().get(index);
            CrakenType unqualifiedNestedTarget = nestedTarget.unqualified();
            if (designator instanceof Designator.Index arrayIndex && unqualifiedNestedTarget instanceof CrakenType.ArrayType array) {
                if (arrayIndex.index() >= array.length()) report(arrayIndex.range(), "指定初始化数组下标越界");
                nestedTarget = array.elementType();
            } else if (designator instanceof Designator.Field field && unqualifiedNestedTarget instanceof CrakenType.StructType) {
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
            CrakenType targetType,
            String subject
    ) {
        if (value instanceof AggregateInitExpr nested) {
            analyzeAggregateInit(nested, scope, targetType);
            return;
        }
        if (Expression.ObjectInitExpr.occursInResultOf(value)) {
            report(value.range(), "聚合列表中的原地构造尚需独立的子对象初始化规则，不能沿用 C 聚合整体零填充");
        }
        CrakenType valueType = analyzeExpression(value, scope);
        if (!TypeCompatibility.isAssignmentCompatible(targetType, valueType, value)) {
            report(value.range(), subject + "初始化类型不匹配");
        }
    }

    private CrakenType analyzeAssignmentTarget(Expression target, Scope scope, SourceRange range) {
        CrakenType type;
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
            return CrakenType.INT;
        }
        if (type.isConstQualified()) {
            report(range, "不能修改 const 左值");
        }
        return type;
    }

    private CrakenType analyzeIndex(IndexExpr indexExpr, Scope scope) {
        CrakenType targetType = analyzeExpression(indexExpr.target(), scope);
        CrakenType indexType = analyzeExpression(indexExpr.index(), scope);
        if (!TypeCompatibility.isIndexCompatible(indexType)) {
            report(indexExpr.index().range(), "数组下标必须是整数类型");
        }
        if (targetType.isArray()) {
            return inheritObjectQualifiers(targetType, targetType.elementType());
        }
        if (targetType.isPointer()) {
            if (targetType.pointee().isFunction()) {
                report(indexExpr.range(), "函数指针不能执行下标运算");
                return CrakenType.INT;
            }
            return targetType.pointee();
        }
        report(indexExpr.range(), "下标访问目标必须是数组或指针");
        return CrakenType.INT;
    }

    private CrakenType analyzeFieldAccess(FieldAccessExpr fieldAccessExpr, Scope scope) {
        CrakenType targetType = analyzeExpression(fieldAccessExpr.target(), scope);
        CrakenType structType = targetType;
        if (fieldAccessExpr.viaPointer()) {
            if (!targetType.isPointer() || !(targetType.pointee().unqualified() instanceof CrakenType.StructType)) {
                report(fieldAccessExpr.range(), "指针字段访问目标必须是结构体指针");
                return CrakenType.INT;
            }
            structType = targetType.pointee();
        } else if (!(targetType.unqualified() instanceof CrakenType.StructType)) {
            report(fieldAccessExpr.range(), "字段访问目标必须是结构体");
            return CrakenType.INT;
        }
        CrakenType owningObjectType = structType;
        return structRegistry.field(structType, fieldAccessExpr.fieldName())
                .map(StructFieldLayout::type)
                .map(fieldType -> inheritObjectQualifiers(owningObjectType, fieldType))
                .orElseGet(() -> {
                    report(fieldAccessExpr.range(), "未知结构体字段：" + fieldAccessExpr.fieldName());
                    return CrakenType.INT;
                });
    }

    private CrakenType resolveVariable(Scope scope, String name, SourceRange range) {
        var symbol = scope.resolve(name).filter(candidate -> candidate.kind() == SymbolKind.VARIABLE);
        if (symbol.isPresent()) {
            return symbol.orElseThrow().type();
        }
        if (scope.resolve(name).filter(candidate -> candidate.kind() == SymbolKind.FUNCTION).isPresent()) {
            return functionRegistry.resolveFunctionAddress(name, range, unevaluatedDepth == 0);
        }
        report(range, "未解析变量：" + name);
        return CrakenType.INT;
    }

    private CrakenType resolveFunctionPointerCall(CallExpr callExpr, Scope scope, ArrayList<CrakenType> argumentTypes) {
        CrakenType calleeType = TypeCompatibility.decay(analyzeExpression(callExpr.callee(), scope));
        if (!calleeType.isPointer() || !calleeType.pointee().isFunction()) {
            report(callExpr.range(), "函数指针调用目标必须是函数指针");
            return CrakenType.INT;
        }
        CrakenType functionType = calleeType.pointee();
        CrakenType.FunctionType signature = (CrakenType.FunctionType) functionType.unqualified();
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

    private CrakenType inheritObjectQualifiers(CrakenType ownerType, CrakenType memberType) {
        java.util.EnumSet<CrakenType.TypeQualifier> qualifiers = java.util.EnumSet.noneOf(CrakenType.TypeQualifier.class);
        qualifiers.addAll(memberType.qualifiers());
        if (ownerType.isConstQualified()) {
            qualifiers.add(CrakenType.TypeQualifier.CONST);
        }
        if (ownerType.isVolatileQualified()) {
            qualifiers.add(CrakenType.TypeQualifier.VOLATILE);
        }
        return CrakenType.qualified(memberType.unqualified(), qualifiers);
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
