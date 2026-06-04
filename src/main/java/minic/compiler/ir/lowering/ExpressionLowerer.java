package minic.compiler.ir.lowering;

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
import minic.compiler.ast.expr.UnaryExpr;
import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCastInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.IrMemCopyInstruction;
import minic.compiler.ir.instruction.IrMoveInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrStorePointerInstruction;
import minic.compiler.ir.instruction.IrUnaryInstruction;
import minic.compiler.ir.instruction.IrUnaryOperator;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrFloatConstant;
import minic.compiler.ir.value.IrFunctionAddress;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.compiler.lexer.TokenKind;
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
        this.expressionTypes = Map.copyOf(expressionTypes);
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
                if (local.type() == IrType.INT_ARRAY || local.type() == IrType.STRUCT) {
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
            IrTemporary result = builder.newTemporary(irTypeOf(indexExpr));
            builder.addInstruction(new IrLoadPointerInstruction(result, address, indexExpr.range()));
            return result;
        }
        if (expression instanceof FieldAccessExpr fieldAccessExpr) {
            IrValue address = lowerFieldAddress(fieldAccessExpr);
            IrTemporary result = builder.newTemporary(irTypeOf(fieldAccessExpr));
            builder.addInstruction(new IrLoadPointerInstruction(result, address, fieldAccessExpr.range()));
            return result;
        }
        if (expression instanceof BinaryExpr binaryExpr) {
            if (binaryExpr.operator() == TokenKind.AMPERSAND_AMPERSAND || binaryExpr.operator() == TokenKind.PIPE_PIPE) {
                return lowerLogicalBinary(binaryExpr);
            }
            IrValue left = lowerExpression(binaryExpr.left());
            IrValue right = lowerExpression(binaryExpr.right());
            IrTemporary result = builder.newTemporary(irTypeOf(binaryExpr));
            IrType operandType = arithmeticOperandType(left.type(), right.type(), result.type());
            left = castIfNeeded(left, operandType, binaryExpr.left().range());
            right = castIfNeeded(right, operandType, binaryExpr.right().range());
            if (binaryExpr.operator() == TokenKind.SLASH || binaryExpr.operator() == TokenKind.PERCENT) {
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
            return new IrConstant(size, IrType.LONG);
        }
        if (expression instanceof CallExpr callExpr) {
            MiniType callResultType = expressionTypes.get(callExpr);
            boolean structReturn = callResultType != null && callResultType.isStruct();

            ArrayList<IrValue> arguments = new ArrayList<>();
            IrValue returnSlotAddress = null;
            if (structReturn) {
                int size = sizeOfType(callResultType);
                IrLocal returnSlot = builder.declareAnonymousLocal(IrType.STRUCT, size, callExpr.range());
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
            IrTemporary result = builder.newTemporary(structReturn ? IrType.POINTER : irTypeOf(callExpr));
            if (isDirectFunctionCall(callExpr)) {
                arguments = castArguments(callExpr.calleeName(), arguments, callExpr);
                builder.addInstruction(new IrCallInstruction(result, callExpr.calleeName(), arguments, callExpr.range()));
            } else {
                IrValue calleeAddress = lowerExpression(callExpr.callee());
                arguments = castArguments(callExpr, arguments);
                builder.addInstruction(new IrIndirectCallInstruction(result, calleeAddress, arguments, callExpr.range()));
            }
            return structReturn ? returnSlotAddress : result;
        }
        throw new IllegalArgumentException("unsupported expression: " + expression.getClass().getSimpleName());
    }

    private IrValue lowerAssignmentValue(AssignmentExpr assignmentExpr) {
        IrValue value = lowerExpression(assignmentExpr.value());
        if (assignmentExpr.compoundBinaryOperator().isEmpty()) {
            return value;
        }
        IrValue currentValue = lowerExpression(assignmentExpr.target());
        IrTemporary result = builder.newTemporary(irTypeOf(assignmentExpr));
        IrType operandType = arithmeticOperandType(currentValue.type(), value.type(), result.type());
        currentValue = castIfNeeded(currentValue, operandType, assignmentExpr.target().range());
        value = castIfNeeded(value, operandType, assignmentExpr.value().range());
        TokenKind binaryOperator = assignmentExpr.compoundBinaryOperator().orElseThrow();
        if (binaryOperator == TokenKind.SLASH || binaryOperator == TokenKind.PERCENT) {
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
        if (binaryExpr.operator() == TokenKind.AMPERSAND_AMPERSAND) {
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
                minic.compiler.ir.instruction.IrBinaryOperator.NOT_EQUAL,
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
                minic.compiler.ir.instruction.IrBinaryOperator.NOT_EQUAL,
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
        if (type == IrType.LONG) {
            return new IrConstant(0, IrType.LONG);
        }
        if (type == IrType.BOOL) {
            return new IrConstant(0, IrType.BOOL);
        }
        if (type == IrType.CHAR) {
            return new IrConstant(0, IrType.CHAR);
        }
        return new IrConstant(0);
    }

    IrValue castForTarget(IrValue value, IrType targetType, minic.source.SourceRange range) {
        return castIfNeeded(value, targetType, range);
    }

    private IrValue lowerUnary(UnaryExpr unaryExpr) {
        if (unaryExpr.operator() == TokenKind.AMPERSAND) {
            return lowerAddress(unaryExpr.operand());
        }
        if (unaryExpr.operator() == TokenKind.STAR) {
            IrValue address = lowerExpression(unaryExpr.operand());
            IrTemporary result = builder.newTemporary(irTypeOf(unaryExpr));
            builder.addInstruction(new IrLoadPointerInstruction(result, address, unaryExpr.range()));
            return result;
        }
        if (unaryExpr.operator() == TokenKind.BANG || unaryExpr.operator() == TokenKind.TILDE) {
            IrValue operand = lowerExpression(unaryExpr.operand());
            IrTemporary result = builder.newTemporary(irTypeOf(unaryExpr));
            builder.addInstruction(new IrUnaryInstruction(
                    result,
                    unaryExpr.operator() == TokenKind.BANG ? IrUnaryOperator.LOGICAL_NOT : IrUnaryOperator.BITWISE_NOT,
                    operand,
                    unaryExpr.range()
            ));
            return result;
        }
        if (unaryExpr.operator() == TokenKind.MINUS) {
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
        if (unaryExpr.operator() == TokenKind.PLUS) {
            return lowerExpression(unaryExpr.operand());
        }
        if (unaryExpr.operator() == TokenKind.PLUS_PLUS || unaryExpr.operator() == TokenKind.MINUS_MINUS) {
            IrValue currentValue = lowerExpression(unaryExpr.operand());
            IrConstant one = new IrConstant(1, currentValue.type());
            IrTemporary updated = builder.newTemporary(currentValue.type());
            builder.addInstruction(new IrBinaryInstruction(
                    updated,
                    unaryExpr.operator() == TokenKind.PLUS_PLUS
                            ? minic.compiler.ir.instruction.IrBinaryOperator.ADD
                            : minic.compiler.ir.instruction.IrBinaryOperator.SUBTRACT,
                    currentValue,
                    one,
                    unaryExpr.range()
            ));
            lowerStore(unaryExpr.operand(), updated, unaryExpr.range());
            return updated;
        }
        throw new IllegalArgumentException("unsupported unary expression: " + unaryExpr.operator());
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
        if (target instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenKind.STAR) {
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
        IrValue baseAddress = lowerAddress(indexExpr.target());
        IrValue index = lowerExpression(indexExpr.index());
        IrTemporary result = builder.newTemporary(IrType.POINTER);
        builder.addInstruction(new IrElementAddressInstruction(
                result,
                baseAddress,
                index,
                elementSizeBytes(indexExpr),
                indexExpr.range()
        ));
        return result;
    }

    private int elementSizeBytes(IndexExpr indexExpr) {
        MiniType targetType = expressionTypes.get(indexExpr.target());
        MiniType elementType = MiniType.INT;
        if (targetType != null && targetType.isArray()) {
            elementType = targetType.elementType();
        } else if (targetType != null && targetType.isPointer()) {
            elementType = targetType.pointee();
        }
        return sizeOfType(elementType);
    }

    private int sizeOfType(MiniType type) {
        if (type instanceof MiniType.StructType structType) {
            return builder.structSize(structType.name());
        }
        if (type.isArray() && type.elementType() instanceof MiniType.StructType structType) {
            return builder.structSize(structType.name()) * type.arrayLength();
        }
        return minic.compiler.type.TypeLayout.sizeOf(type);
    }

    private IrValue copyStructForArg(IrValue srcAddress, MiniType.StructType structType, minic.source.SourceRange range) {
        int size = builder.structSize(structType.name());
        IrLocal copy = builder.declareAnonymousLocal(IrType.STRUCT, size, range);
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
        int offset = builder.fieldOffset(structName, fieldAccessExpr.fieldName());
        IrTemporary result = builder.newTemporary(IrType.POINTER);
        builder.addInstruction(new IrFieldAddressInstruction(
                result,
                baseAddress,
                fieldAccessExpr.fieldName(),
                offset,
                fieldAccessExpr.range()
        ));
        return result;
    }

    private IrValue lowerAddress(Expression expression) {
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
        if (expression instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenKind.STAR) {
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

    private IrType arithmeticOperandType(IrType leftType, IrType rightType, IrType resultType) {
        if (resultType.isFloatingScalar()) {
            return resultType;
        }
        if (resultType == IrType.INT && (leftType.isFloatingScalar() || rightType.isFloatingScalar())) {
            if (leftType == IrType.DOUBLE || rightType == IrType.DOUBLE) {
                return IrType.DOUBLE;
            }
            return IrType.FLOAT;
        }
        if (resultType == IrType.INT && (leftType == IrType.LONG || rightType == IrType.LONG
                || leftType == IrType.POINTER || rightType == IrType.POINTER)) {
            return IrType.LONG;
        }
        return resultType;
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
        return castArguments(arguments, signature.parameterTypes(), callExpr);
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
        return castArguments(arguments, parameterTypes, callExpr);
    }

    private ArrayList<IrValue> castArguments(ArrayList<IrValue> arguments, java.util.List<IrType> parameterTypes, CallExpr callExpr) {
        if (arguments.size() != parameterTypes.size()) {
            return arguments;
        }
        ArrayList<IrValue> casted = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            minic.source.SourceRange range = index < callExpr.arguments().size()
                    ? callExpr.arguments().get(index).range()
                    : callExpr.range();
            casted.add(castIfNeeded(arguments.get(index), parameterTypes.get(index), range));
        }
        return casted;
    }
}
