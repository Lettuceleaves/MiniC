package minic.compiler.ir.manager;

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
import minic.compiler.parser.node.Expression.UnaryExpr;
import minic.compiler.ir.instruction.MemoryInstruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrCastInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrMemCopyInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrUnaryInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrUnaryOperator;
import minic.compiler.ir.instruction.ControlInstruction.IrBranchInstruction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue.IrConstant;
import minic.compiler.ir.value.IrValue.IrFloatConstant;
import minic.compiler.ir.value.IrValue.IrFunctionAddress;
import minic.compiler.ir.value.IrValue.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.type.MiniType;

import java.util.ArrayList;
import java.util.Map;

final class ExpressionLowerer {
    private final IrFunctionBuilder builder;
    private final StringLiteralRegistry stringLiteralRegistry;
    private final Map<Expression, MiniType> expressionTypes;
    private final Map<String, IrFunctionSignature> functionSignatures;

    ExpressionLowerer(
            IrFunctionBuilder builder,
            StringLiteralRegistry stringLiteralRegistry,
            Map<Expression, MiniType> expressionTypes,
            Map<String, IrFunctionSignature> functionSignatures
    ) {
        this.builder = builder;
        this.stringLiteralRegistry = stringLiteralRegistry;
        this.expressionTypes = java.util.Collections.unmodifiableMap(
                new java.util.IdentityHashMap<>(expressionTypes)
        );
        this.functionSignatures = Map.copyOf(functionSignatures);
    }

    IrValue lowerExpression(Expression expression) {
        if (expression instanceof BoolLiteralExpr boolLiteralExpr) {
            return new IrConstant(boolLiteralExpr.value() ? 1 : 0, IrType.BOOL);
        }
        if (expression instanceof CharLiteralExpr charLiteralExpr) {
            return new IrConstant(charLiteralExpr.value(), IrType.CHAR);
        }
        if (expression instanceof IntegerLiteralExpr integerLiteralExpr) {
            return new IrConstant(integerLiteralExpr.value());
        }
        if (expression instanceof IntegerConstantExpr integerConstantExpr) {
            return new IrConstant(integerConstantExpr.value(), IrTypeLowerer.lower(integerConstantExpr.type()));
        }
        if (expression instanceof LongLiteralExpr longLiteralExpr) {
            return new IrConstant(longLiteralExpr.value(), IrType.LONG);
        }
        if (expression instanceof FloatLiteralExpr floatLiteralExpr) {
            return new IrFloatConstant(floatLiteralExpr.value(), IrType.FLOAT);
        }
        if (expression instanceof DoubleLiteralExpr doubleLiteralExpr) {
            return new IrFloatConstant(doubleLiteralExpr.value(), IrType.DOUBLE);
        }
        if (expression instanceof NullLiteralExpr) {
            return new IrConstant(0, IrType.POINTER);
        }
        if (expression instanceof StringLiteralExpr stringLiteralExpr) {
            return stringLiteralRegistry.define(stringLiteralExpr.value());
        }
        if (expression instanceof CastExpr castExpr) {
            IrValue operand = lowerExpression(castExpr.operand());
            if (castExpr.targetType().isVoid()) return new IrConstant(0);
            return castIfNeeded(operand, IrTypeLowerer.lower(castExpr.targetType()), castExpr.range());
        }
        if (expression instanceof CommaExpr commaExpr) {
            IrValue result = new IrConstant(0);
            for (Expression item : commaExpr.expressions()) result = lowerExpression(item);
            return result;
        }
        if (expression instanceof NameExpr nameExpr) {
            MiniType nameType = expressionTypes.get(nameExpr);
            if (nameType != null && nameType.isPointer() && nameType.pointee().isFunction()) {
                IrLocal local = builder.resolveLocal(nameExpr.name());
                if (local == null && builder.findParameter(nameExpr.name()) == null) {
                    return new IrFunctionAddress(nameExpr.name());
                }
            }
            IrLocal local = builder.resolveLocal(nameExpr.name());
            if (local != null) {
                if (local.aggregate()) {
                    return lowerAddress(nameExpr);
                }
                builder.addInstruction(new IrCheckInitializedInstruction(local, nameExpr.range()));
                IrTemporary result = builder.newTemporary(local.type());
                builder.addInstruction(new IrLoadLocalInstruction(result, local, nameExpr.range()));
                return result;
            }
            return builder.resolveParameter(nameExpr.name());
        }
        if (expression instanceof GroupingExpr groupingExpr) {
            return lowerExpression(groupingExpr.expression());
        }
        if (expression instanceof AssignmentExpr assignmentExpr) {
            IrValue value = lowerAssignmentValue(assignmentExpr);
            lowerStore(assignmentExpr.target(), value, assignmentExpr.range());
            return value;
        }
        if (expression instanceof UnaryExpr unaryExpr) {
            return lowerUnary(unaryExpr);
        }
        if (expression instanceof IndexExpr indexExpr) {
            IrValue address = lowerElementAddress(indexExpr);
            if (isAggregateType(expressionTypes.get(indexExpr))) {
                return address;
            }
            IrTemporary result = builder.newTemporary(irTypeOf(indexExpr));
            builder.addInstruction(new IrLoadPointerInstruction(result, address, indexExpr.range()));
            return result;
        }
        if (expression instanceof FieldAccessExpr fieldAccessExpr) {
            IrValue address = lowerFieldAddress(fieldAccessExpr);
            if (isAggregateType(expressionTypes.get(fieldAccessExpr))) {
                return address;
            }
            IrTemporary result = builder.newTemporary(irTypeOf(fieldAccessExpr));
            builder.addInstruction(new IrLoadPointerInstruction(result, address, fieldAccessExpr.range()));
            return result;
        }
        if (expression instanceof BinaryExpr binaryExpr) {
            if (binaryExpr.operator() == TokenType.AMPERSAND_AMPERSAND || binaryExpr.operator() == TokenType.PIPE_PIPE) {
                return lowerLogicalBinary(binaryExpr);
            }
            IrValue left = lowerExpression(binaryExpr.left());
            IrValue right = lowerExpression(binaryExpr.right());
            MiniType leftType = expressionTypes.get(binaryExpr.left());
            MiniType rightType = expressionTypes.get(binaryExpr.right());
            IrValue pointerResult = lowerPointerBinary(
                    leftType,
                    rightType,
                    binaryExpr.operator(),
                    left,
                    right,
                    binaryExpr.range()
            );
            if (pointerResult != null) {
                return pointerResult;
            }
            IrTemporary result = builder.newTemporary(irTypeOf(binaryExpr));
            IrType operandType = arithmeticOperandType(
                    left.type(), right.type(), result.type(), binaryExpr.operator());
            left = castIfNeeded(left, operandType, binaryExpr.left().range());
            right = castIfNeeded(right, operandType, binaryExpr.right().range());
            if (binaryExpr.operator() == TokenType.SLASH || binaryExpr.operator() == TokenType.PERCENT) {
                builder.addInstruction(new IrCheckNonZeroInstruction(right, binaryExpr.range()));
            }
            builder.addInstruction(new IrBinaryInstruction(
                    result,
                    IrOperatorLowerer.lower(binaryExpr.operator()),
                    left,
                    right,
                    binaryExpr.range()
            ));
            return result;
        }
        if (expression instanceof ConditionalExpr conditionalExpr) {
            return lowerConditional(conditionalExpr);
        }
        if (expression instanceof SizeofExpr sizeofExpr) {
            MiniType queriedType = sizeofExpr.queriedTypeOptional()
                    .orElseGet(() -> expressionTypes.get(sizeofExpr.expressionOptional().orElseThrow()));
            int size = sizeOfType(queriedType);
            return new IrConstant(size, IrType.UNSIGNED_LONG_LONG);
        }
        if (expression instanceof CallExpr callExpr) {
            MiniType callResultType = expressionTypes.get(callExpr);
            boolean structReturn = callResultType != null && callResultType.isStruct();

            ArrayList<IrValue> arguments = new ArrayList<>();
            IrValue returnSlotAddress = null;
            if (structReturn) {
                IrLocal returnSlot = builder.declareAnonymousLocal(callResultType, callExpr.range());
                builder.addInstruction(new IrDeclareLocalInstruction(returnSlot, callExpr.range()));
                IrTemporary addr = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrAddressOfLocalInstruction(addr, returnSlot, callExpr.range()));
                returnSlotAddress = addr;
                arguments.add(returnSlotAddress);
            }

            for (int i = 0; i < callExpr.arguments().size(); i++) {
                Expression argument = callExpr.arguments().get(i);
                IrValue argValue = lowerExpression(argument);
                MiniType argType = expressionTypes.get(argument);
                if (argType != null && argType.isStruct()) {
                    argValue = copyStructForArg(argValue, (MiniType.StructType) argType, callExpr.range());
                }
                arguments.add(argValue);
            }
            boolean returnsVoid = callResultType != null && callResultType.isVoid();
            IrTemporary result = returnsVoid
                    ? null
                    : builder.newTemporary(structReturn ? IrType.POINTER : irTypeOf(callExpr));
            if (isDirectFunctionCall(callExpr)) {
                boolean variadic = isVariadicDirectCall(callExpr.calleeName());
                arguments = castArguments(callExpr.calleeName(), arguments, callExpr);
                builder.addInstruction(new IrCallInstruction(
                        result,
                        callExpr.calleeName(),
                        arguments,
                        variadic,
                        callExpr.range()
                ));
            } else {
                IrValue calleeAddress = lowerExpression(callExpr.callee());
                boolean variadic = isVariadicIndirectCall(callExpr);
                arguments = castArguments(callExpr, arguments);
                builder.addInstruction(new IrIndirectCallInstruction(
                        result,
                        calleeAddress,
                        arguments,
                        variadic,
                        callExpr.range()
                ));
            }
            if (structReturn) {
                return returnSlotAddress;
            }
            // void 调用只作为副作用表达式存在；零常量仅满足 lowering 方法的统一返回协议，
            // 不会成为调用指令的结果或占用栈槽。
            return result == null ? new IrConstant(0) : result;
        }
        throw new IllegalArgumentException("unsupported expression: " + expression.getClass().getSimpleName());
    }

    private IrValue lowerAssignmentValue(AssignmentExpr assignmentExpr) {
        IrValue value = lowerExpression(assignmentExpr.value());
        if (assignmentExpr.compoundBinaryOperator().isEmpty()) {
            return value;
        }
        IrValue currentValue = lowerExpression(assignmentExpr.target());
        TokenType binaryOperator = assignmentExpr.compoundBinaryOperator().orElseThrow();
        IrValue pointerResult = lowerPointerBinary(
                expressionTypes.get(assignmentExpr.target()),
                expressionTypes.get(assignmentExpr.value()),
                binaryOperator,
                currentValue,
                value,
                assignmentExpr.range()
        );
        if (pointerResult != null) {
            return pointerResult;
        }
        IrTemporary result = builder.newTemporary(irTypeOf(assignmentExpr));
        IrType operandType = arithmeticOperandType(currentValue.type(), value.type(), result.type(), binaryOperator);
        currentValue = castIfNeeded(currentValue, operandType, assignmentExpr.target().range());
        value = castIfNeeded(value, operandType, assignmentExpr.value().range());
        if (binaryOperator == TokenType.SLASH || binaryOperator == TokenType.PERCENT) {
            builder.addInstruction(new IrCheckNonZeroInstruction(value, assignmentExpr.range()));
        }
        builder.addInstruction(new IrBinaryInstruction(
                result,
                IrOperatorLowerer.lower(binaryOperator),
                currentValue,
                value,
                assignmentExpr.range()
        ));
        return result;
    }

    private IrValue lowerLogicalBinary(BinaryExpr binaryExpr) {
        if (binaryExpr.operator() == TokenType.AMPERSAND_AMPERSAND) {
            return lowerLogicalAnd(binaryExpr);
        }
        return lowerLogicalOr(binaryExpr);
    }

    private IrValue lowerLogicalAnd(BinaryExpr binaryExpr) {
        IrValue left = lowerExpression(binaryExpr.left());
        IrTemporary result = builder.newTemporary(IrType.INT);
        String rightLabel = builder.newBlockLabel("logical_and_rhs");
        String falseLabel = builder.newBlockLabel("logical_and_false");
        String mergeLabel = builder.newBlockLabel("logical_and_merge");
        builder.addInstruction(new IrBranchInstruction(left, rightLabel, falseLabel, binaryExpr.left().range()));

        builder.switchToBlock(rightLabel);
        IrValue right = lowerExpression(binaryExpr.right());
        IrTemporary rightTruth = builder.newTemporary(IrType.INT);
        builder.addInstruction(new IrBinaryInstruction(
                rightTruth,
                minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator.NOT_EQUAL,
                right,
                zeroOf(right.type()),
                binaryExpr.range()
        ));
        builder.addInstruction(new IrMoveInstruction(result, rightTruth, binaryExpr.range()));
        builder.addJumpIfOpen(mergeLabel, binaryExpr.range());

        builder.switchToBlock(falseLabel);
        builder.addInstruction(new IrMoveInstruction(result, new IrConstant(0), binaryExpr.range()));
        builder.addJumpIfOpen(mergeLabel, binaryExpr.range());

        builder.switchToBlock(mergeLabel);
        return result;
    }

    private IrValue lowerLogicalOr(BinaryExpr binaryExpr) {
        IrValue left = lowerExpression(binaryExpr.left());
        IrTemporary result = builder.newTemporary(IrType.INT);
        String trueLabel = builder.newBlockLabel("logical_or_true");
        String rightLabel = builder.newBlockLabel("logical_or_rhs");
        String mergeLabel = builder.newBlockLabel("logical_or_merge");
        builder.addInstruction(new IrBranchInstruction(left, trueLabel, rightLabel, binaryExpr.left().range()));

        builder.switchToBlock(trueLabel);
        builder.addInstruction(new IrMoveInstruction(result, new IrConstant(1), binaryExpr.range()));
        builder.addJumpIfOpen(mergeLabel, binaryExpr.range());

        builder.switchToBlock(rightLabel);
        IrValue right = lowerExpression(binaryExpr.right());
        IrTemporary rightTruth = builder.newTemporary(IrType.INT);
        builder.addInstruction(new IrBinaryInstruction(
                rightTruth,
                minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator.NOT_EQUAL,
                right,
                zeroOf(right.type()),
                binaryExpr.range()
        ));
        builder.addInstruction(new IrMoveInstruction(result, rightTruth, binaryExpr.range()));
        builder.addJumpIfOpen(mergeLabel, binaryExpr.range());

        builder.switchToBlock(mergeLabel);
        return result;
    }

    private IrValue lowerConditional(ConditionalExpr conditionalExpr) {
        IrValue condition = lowerExpression(conditionalExpr.condition());
        IrTemporary result = builder.newTemporary(irTypeOf(conditionalExpr));
        String thenLabel = builder.newBlockLabel("conditional_then");
        String elseLabel = builder.newBlockLabel("conditional_else");
        String mergeLabel = builder.newBlockLabel("conditional_merge");
        builder.addInstruction(new IrBranchInstruction(condition, thenLabel, elseLabel, conditionalExpr.condition().range()));

        builder.switchToBlock(thenLabel);
        IrValue thenValue = castIfNeeded(
                lowerExpression(conditionalExpr.thenExpression()),
                result.type(),
                conditionalExpr.thenExpression().range()
        );
        builder.addInstruction(new IrMoveInstruction(result, thenValue, conditionalExpr.thenExpression().range()));
        builder.addJumpIfOpen(mergeLabel, conditionalExpr.thenExpression().range());

        builder.switchToBlock(elseLabel);
        IrValue elseValue = castIfNeeded(
                lowerExpression(conditionalExpr.elseExpression()),
                result.type(),
                conditionalExpr.elseExpression().range()
        );
        builder.addInstruction(new IrMoveInstruction(result, elseValue, conditionalExpr.elseExpression().range()));
        builder.addJumpIfOpen(mergeLabel, conditionalExpr.elseExpression().range());

        builder.switchToBlock(mergeLabel);
        return result;
    }

    private IrValue zeroOf(IrType type) {
        if (type.isFloatingScalar()) {
            return new IrFloatConstant(0.0, type);
        }
        if (type == IrType.POINTER) {
            return new IrConstant(0, IrType.POINTER);
        }
        if (type.isIntegerScalar()) {
            return new IrConstant(0, type);
        }
        return new IrConstant(0);
    }

    IrValue castForTarget(IrValue value, IrType targetType, minic.source.SourceRange range) {
        return castIfNeeded(value, targetType, range);
    }

    private IrValue lowerUnary(UnaryExpr unaryExpr) {
        if (unaryExpr.operator() == TokenType.AMPERSAND) {
            return lowerAddress(unaryExpr.operand());
        }
        if (unaryExpr.operator() == TokenType.STAR) {
            IrValue address = lowerExpression(unaryExpr.operand());
            if (isAggregateType(expressionTypes.get(unaryExpr))) {
                return address;
            }
            IrTemporary result = builder.newTemporary(irTypeOf(unaryExpr));
            builder.addInstruction(new IrLoadPointerInstruction(result, address, unaryExpr.range()));
            return result;
        }
        if (unaryExpr.operator() == TokenType.BANG || unaryExpr.operator() == TokenType.TILDE) {
            IrValue operand = lowerExpression(unaryExpr.operand());
            IrTemporary result = builder.newTemporary(irTypeOf(unaryExpr));
            builder.addInstruction(new IrUnaryInstruction(
                    result,
                    unaryExpr.operator() == TokenType.BANG ? IrUnaryOperator.LOGICAL_NOT : IrUnaryOperator.BITWISE_NOT,
                    operand,
                    unaryExpr.range()
            ));
            return result;
        }
        if (unaryExpr.operator() == TokenType.MINUS) {
            IrValue operand = lowerExpression(unaryExpr.operand());
            IrTemporary result = builder.newTemporary(irTypeOf(unaryExpr));
            builder.addInstruction(new IrUnaryInstruction(
                    result,
                    IrUnaryOperator.NEGATE,
                    operand,
                    unaryExpr.range()
            ));
            return result;
        }
        if (unaryExpr.operator() == TokenType.PLUS) {
            return lowerExpression(unaryExpr.operand());
        }
        if (unaryExpr.operator() == TokenType.PLUS_PLUS || unaryExpr.operator() == TokenType.MINUS_MINUS) {
            IrValue currentValue = lowerExpression(unaryExpr.operand());
            MiniType operandType = expressionTypes.get(unaryExpr.operand());
            long amount = operandType != null && operandType.isPointer()
                    ? sizeOfType(operandType.pointee())
                    : 1;
            IrConstant one = new IrConstant(amount, currentValue.type() == IrType.POINTER
                    ? IrType.LONG_LONG
                    : currentValue.type());
            IrTemporary updated = builder.newTemporary(currentValue.type());
            builder.addInstruction(new IrBinaryInstruction(
                    updated,
                    unaryExpr.operator() == TokenType.PLUS_PLUS
                            ? minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator.ADD
                            : minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator.SUBTRACT,
                    currentValue,
                    one,
                    unaryExpr.range()
            ));
            lowerStore(unaryExpr.operand(), updated, unaryExpr.range());
            return updated;
        }
        throw new IllegalArgumentException("unsupported unary expression: " + unaryExpr.operator());
    }

    /**
     * 按 C 的元素步长降低指针运算。返回 {@code null} 表示这不是指针算术。
     */
    private IrValue lowerPointerBinary(
            MiniType leftType,
            MiniType rightType,
            TokenType operator,
            IrValue left,
            IrValue right,
            minic.source.SourceRange range
    ) {
        leftType = decay(leftType);
        rightType = decay(rightType);
        if (operator == TokenType.MINUS
                && leftType != null && leftType.isPointer()
                && rightType != null && rightType.isPointer()) {
            IrTemporary byteDifference = builder.newTemporary(IrType.LONG_LONG);
            builder.addInstruction(new IrBinaryInstruction(
                    byteDifference,
                    minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator.SUBTRACT,
                    left,
                    right,
                    range
            ));
            int stride = sizeOfType(leftType.pointee());
            if (stride == 1) {
                return byteDifference;
            }
            IrTemporary elementDifference = builder.newTemporary(IrType.LONG_LONG);
            builder.addInstruction(new IrBinaryInstruction(
                    elementDifference,
                    minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator.DIVIDE,
                    byteDifference,
                    new IrConstant(stride, IrType.LONG_LONG),
                    range
            ));
            return elementDifference;
        }

        boolean leftPointer = leftType != null && leftType.isPointer();
        boolean rightPointer = rightType != null && rightType.isPointer();
        boolean addition = operator == TokenType.PLUS && leftPointer != rightPointer;
        boolean subtraction = operator == TokenType.MINUS && leftPointer && !rightPointer;
        if (!addition && !subtraction) {
            return null;
        }

        MiniType pointerType = leftPointer ? leftType : rightType;
        IrValue pointer = leftPointer ? left : right;
        IrValue index = leftPointer ? right : left;
        IrValue scaledIndex = scalePointerIndex(index, sizeOfType(pointerType.pointee()), range);
        IrTemporary result = builder.newTemporary(IrType.POINTER);
        builder.addInstruction(new IrBinaryInstruction(
                result,
                subtraction
                        ? minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator.SUBTRACT
                        : minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator.ADD,
                pointer,
                scaledIndex,
                range
        ));
        return result;
    }

    private IrValue scalePointerIndex(IrValue index, int stride, minic.source.SourceRange range) {
        IrValue wideIndex = castIfNeeded(index, IrType.LONG_LONG, range);
        if (stride == 1) {
            return wideIndex;
        }
        IrTemporary scaled = builder.newTemporary(IrType.LONG_LONG);
        builder.addInstruction(new IrBinaryInstruction(
                scaled,
                minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator.MULTIPLY,
                wideIndex,
                new IrConstant(stride, IrType.LONG_LONG),
                range
        ));
        return scaled;
    }

    private void lowerStore(Expression target, IrValue value, minic.source.SourceRange range) {
        MiniType targetType = expressionTypes.get(target);
        if (targetType != null && targetType.isStruct()) {
            IrValue destAddress = lowerAddress(target);
            IrValue srcAddress = value;
            String structName = ((MiniType.StructType) targetType).name();
            int size = builder.structSize(structName);
            builder.addInstruction(new IrMemCopyInstruction(destAddress, srcAddress, size, range));
            return;
        }
        if (target instanceof NameExpr nameExpr) {
            IrLocal local = builder.resolveLocal(nameExpr.name());
            if (local == null) {
                throw new IllegalArgumentException("assignment target must be a local variable: " + nameExpr.name());
            }
            builder.addInstruction(new IrStoreLocalInstruction(local, castIfNeeded(value, local.type(), range), range));
            return;
        }
        if (target instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenType.STAR) {
            IrValue address = lowerExpression(unaryExpr.operand());
            builder.addInstruction(new IrStorePointerInstruction(address, value, range));
            return;
        }
        if (target instanceof IndexExpr indexExpr) {
            IrValue address = lowerElementAddress(indexExpr);
            builder.addInstruction(new IrStorePointerInstruction(address, value, range));
            return;
        }
        if (target instanceof FieldAccessExpr fieldAccessExpr) {
            IrValue address = lowerFieldAddress(fieldAccessExpr);
            builder.addInstruction(new IrStorePointerInstruction(address, value, range));
            return;
        }
        throw new IllegalArgumentException("unsupported assignment target: " + target.getClass().getSimpleName());
    }

    private IrValue lowerElementAddress(IndexExpr indexExpr) {
        MiniType targetType = expressionTypes.get(indexExpr.target());
        IrValue baseAddress = targetType != null && targetType.isPointer()
                ? lowerExpression(indexExpr.target())
                : lowerAddress(indexExpr.target());
        IrValue index = lowerExpression(indexExpr.index());
        IrTemporary result = builder.newTemporary(IrType.POINTER);
        MiniType elementType = elementType(indexExpr);
        builder.addInstruction(new IrElementAddressInstruction(
                result,
                baseAddress,
                index,
                elementType,
                sizeOfType(elementType),
                indexExpr.range()
        ));
        return result;
    }

    private MiniType elementType(IndexExpr indexExpr) {
        MiniType targetType = expressionTypes.get(indexExpr.target());
        if (targetType != null && targetType.isArray()) {
            return targetType.elementType();
        }
        if (targetType != null && targetType.isPointer()) {
            return targetType.pointee();
        }
        return MiniType.INT;
    }

    private int sizeOfType(MiniType type) {
        return builder.sizeOf(type);
    }

    private IrValue copyStructForArg(IrValue srcAddress, MiniType.StructType structType, minic.source.SourceRange range) {
        int size = sizeOfType(structType);
        IrLocal copy = builder.declareAnonymousLocal(structType, range);
        builder.addInstruction(new IrDeclareLocalInstruction(copy, range));
        IrTemporary destAddress = builder.newTemporary(IrType.POINTER);
        builder.addInstruction(new IrAddressOfLocalInstruction(destAddress, copy, range));
        builder.addInstruction(new IrMemCopyInstruction(destAddress, srcAddress, size, range));
        return destAddress;
    }

    private IrValue lowerFieldAddress(FieldAccessExpr fieldAccessExpr) {
        IrValue baseAddress = fieldAccessExpr.viaPointer()
                ? lowerExpression(fieldAccessExpr.target())
                : lowerAddress(fieldAccessExpr.target());
        String structName = structName(fieldAccessExpr.target());
        int fieldIndex = builder.fieldIndex(structName, fieldAccessExpr.fieldName());
        var field = builder.fieldLayout(structName, fieldIndex);
        IrTemporary result = builder.newTemporary(IrType.POINTER);
        builder.addInstruction(new IrFieldAddressInstruction(
                result,
                baseAddress,
                structName,
                fieldAccessExpr.fieldName(),
                field.offset(),
                field.type(),
                fieldAccessExpr.range()
        ));
        return result;
    }

    private IrValue lowerAddress(Expression expression) {
        if (expression instanceof GroupingExpr groupingExpr) {
            return lowerAddress(groupingExpr.expression());
        }
        if (expression instanceof NameExpr nameExpr) {
            IrLocal local = builder.resolveLocal(nameExpr.name());
            if (local == null) {
                return builder.resolveParameter(nameExpr.name());
            }
            IrTemporary result = builder.newTemporary(IrType.POINTER);
            builder.addInstruction(new IrAddressOfLocalInstruction(result, local, nameExpr.range()));
            return result;
        }
        if (expression instanceof IndexExpr indexExpr) {
            return lowerElementAddress(indexExpr);
        }
        if (expression instanceof FieldAccessExpr fieldAccessExpr) {
            return lowerFieldAddress(fieldAccessExpr);
        }
        if (expression instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenType.STAR) {
            return lowerExpression(unaryExpr.operand());
        }
        return lowerExpression(expression);
    }

    private String structName(Expression expression) {
        MiniType type = expressionTypes.get(expression);
        if (type instanceof MiniType.StructType structType) {
            return structType.name();
        }
        if (type != null && type.isPointer() && type.pointee() instanceof MiniType.StructType structType) {
            return structType.name();
        }
        throw new IllegalArgumentException("unsupported field access target: " + expression.getClass().getSimpleName());
    }

    private IrType irTypeOf(Expression expression) {
        MiniType type = expressionTypes.get(expression);
        if (type != null) {
            return IrTypeLowerer.lower(type);
        }
        return IrType.INT;
    }

    private boolean isAggregateType(MiniType type) {
        return type != null && (type.isArray() || type.isStruct());
    }

    private MiniType decay(MiniType type) {
        return type instanceof MiniType.ArrayType arrayType
                ? arrayType.elementType().pointerTo()
                : type;
    }

    private IrType arithmeticOperandType(
            IrType leftType,
            IrType rightType,
            IrType resultType,
            TokenType operator
    ) {
        if (resultType.isFloatingScalar()) {
            return resultType;
        }
        if (resultType == IrType.INT && (leftType.isFloatingScalar() || rightType.isFloatingScalar())) {
            if (leftType == IrType.DOUBLE || rightType == IrType.DOUBLE) {
                return IrType.DOUBLE;
            }
            return IrType.FLOAT;
        }
        if (leftType == IrType.POINTER || rightType == IrType.POINTER) {
            return IrType.UNSIGNED_LONG_LONG;
        }
        if (leftType.isIntegerScalar() && rightType.isIntegerScalar()) {
            if (operator == TokenType.LESS_LESS || operator == TokenType.GREATER_GREATER) {
                return integerPromotion(leftType);
            }
            return usualIntegerType(leftType, rightType);
        }
        return resultType;
    }

    private IrType usualIntegerType(IrType leftType, IrType rightType) {
        leftType = integerPromotion(leftType);
        rightType = integerPromotion(rightType);
        if (leftType == rightType) return leftType;
        if (leftType.isSignedInteger() == rightType.isSignedInteger()) {
            return integerRank(leftType) >= integerRank(rightType) ? leftType : rightType;
        }
        IrType unsignedType = leftType.isUnsignedInteger() ? leftType : rightType;
        IrType signedType = leftType.isSignedInteger() ? leftType : rightType;
        if (integerRank(unsignedType) >= integerRank(signedType)) return unsignedType;
        if (signedType.sizeBytes() > unsignedType.sizeBytes()) return signedType;
        return unsignedCounterpart(signedType);
    }

    private IrType integerPromotion(IrType type) {
        return integerRank(type) < integerRank(IrType.INT) ? IrType.INT : type;
    }

    private int integerRank(IrType type) {
        return switch (type) {
            case BOOL -> 0;
            case CHAR, SIGNED_CHAR, UNSIGNED_CHAR -> 1;
            case SHORT, UNSIGNED_SHORT -> 2;
            case INT, UNSIGNED_INT -> 3;
            case LONG, UNSIGNED_LONG -> 4;
            case LONG_LONG, UNSIGNED_LONG_LONG -> 5;
            default -> throw new IllegalArgumentException("not an integer type: " + type);
        };
    }

    private IrType unsignedCounterpart(IrType type) {
        return switch (type) {
            case CHAR, SIGNED_CHAR -> IrType.UNSIGNED_CHAR;
            case SHORT -> IrType.UNSIGNED_SHORT;
            case INT -> IrType.UNSIGNED_INT;
            case LONG -> IrType.UNSIGNED_LONG;
            case LONG_LONG -> IrType.UNSIGNED_LONG_LONG;
            default -> type;
        };
    }

    private IrValue castIfNeeded(IrValue value, IrType targetType, minic.source.SourceRange range) {
        if (value.type() == targetType || value.type() == IrType.POINTER || targetType == IrType.POINTER) {
            return value;
        }
        if (!value.type().isScalar() || !targetType.isScalar()) {
            return value;
        }
        IrTemporary result = builder.newTemporary(targetType);
        builder.addInstruction(new IrCastInstruction(result, value, range));
        return result;
    }

    private boolean isDirectFunctionCall(CallExpr callExpr) {
        if (!callExpr.hasDirectCalleeName()) {
            return false;
        }
        if (builder.resolveLocal(callExpr.calleeName()) != null) {
            return false;
        }
        if (builder.findParameter(callExpr.calleeName()) != null) {
            return false;
        }
        return !expressionTypes.containsKey(callExpr.callee());
    }

    private ArrayList<IrValue> castArguments(String functionName, ArrayList<IrValue> arguments, CallExpr callExpr) {
        IrFunctionSignature signature = functionSignatures.get(functionName);
        if (signature == null) {
            return arguments;
        }
        return castArguments(arguments, signature.parameterTypes(), signature.variadic(), callExpr);
    }

    private ArrayList<IrValue> castArguments(CallExpr callExpr, ArrayList<IrValue> arguments) {
        MiniType calleeType = expressionTypes.get(callExpr.callee());
        if (calleeType == null || !calleeType.isPointer() || !calleeType.pointee().isFunction()) {
            return arguments;
        }
        java.util.ArrayList<IrType> parameterTypes = new java.util.ArrayList<>();
        for (MiniType parameterType : calleeType.pointee().parameterTypes()) {
            parameterTypes.add(IrTypeLowerer.lower(parameterType));
        }
        return castArguments(
                arguments,
                parameterTypes,
                ((MiniType.FunctionType) calleeType.pointee()).variadic(),
                callExpr
        );
    }

    private ArrayList<IrValue> castArguments(
            ArrayList<IrValue> arguments,
            java.util.List<IrType> parameterTypes,
            boolean variadic,
            CallExpr callExpr
    ) {
        if (arguments.size() < parameterTypes.size()) {
            return arguments;
        }
        ArrayList<IrValue> casted = new ArrayList<>();
        for (int index = 0; index < parameterTypes.size(); index++) {
            minic.source.SourceRange range = index < callExpr.arguments().size()
                    ? callExpr.arguments().get(index).range()
                    : callExpr.range();
            casted.add(castIfNeeded(arguments.get(index), parameterTypes.get(index), range));
        }
        for (int index = parameterTypes.size(); index < arguments.size(); index++) {
            IrValue argument = arguments.get(index);
            IrType promotedType = variadic ? defaultArgumentPromotion(argument.type()) : argument.type();
            minic.source.SourceRange range = index < callExpr.arguments().size()
                    ? callExpr.arguments().get(index).range()
                    : callExpr.range();
            casted.add(castIfNeeded(argument, promotedType, range));
        }
        return casted;
    }

    private IrType defaultArgumentPromotion(IrType type) {
        return switch (type) {
            case BOOL, CHAR, SIGNED_CHAR, UNSIGNED_CHAR, SHORT, UNSIGNED_SHORT -> IrType.INT;
            case FLOAT -> IrType.DOUBLE;
            default -> type;
        };
    }

    private boolean isVariadicDirectCall(String functionName) {
        IrFunctionSignature signature = functionSignatures.get(functionName);
        return signature != null && signature.variadic();
    }

    private boolean isVariadicIndirectCall(CallExpr callExpr) {
        MiniType calleeType = expressionTypes.get(callExpr.callee());
        return calleeType != null
                && calleeType.isPointer()
                && calleeType.pointee() instanceof MiniType.FunctionType functionType
                && functionType.variadic();
    }
}
