package minic.compiler.ir.lowering;

import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.BoolLiteralExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.CharLiteralExpr;
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
import minic.compiler.ast.expr.StringLiteralExpr;
import minic.compiler.ast.expr.UnaryExpr;
import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrStorePointerInstruction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrConstant;
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

    ExpressionLowerer(
            IrFunctionBuilder builder,
            StringLiteralRegistry stringLiteralRegistry,
            Map<Expression, MiniType> expressionTypes
    ) {
        this.builder = builder;
        this.stringLiteralRegistry = stringLiteralRegistry;
        this.expressionTypes = Map.copyOf(expressionTypes);
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
        if (expression instanceof FloatLiteralExpr || expression instanceof DoubleLiteralExpr) {
            throw new IllegalArgumentException("floating lowering is scheduled for A144");
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
                if (local.type() == IrType.INT_ARRAY) {
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
            IrValue value = lowerExpression(assignmentExpr.value());
            lowerStore(assignmentExpr.target(), value, assignmentExpr.range());
            return value;
        }
        if (expression instanceof UnaryExpr unaryExpr) {
            return lowerUnary(unaryExpr);
        }
        if (expression instanceof IndexExpr indexExpr) {
            IrValue address = lowerElementAddress(indexExpr);
            IrTemporary result = builder.newTemporary(IrType.INT);
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
            IrValue left = lowerExpression(binaryExpr.left());
            IrValue right = lowerExpression(binaryExpr.right());
            IrTemporary result = builder.newTemporary(irTypeOf(binaryExpr));
            if (binaryExpr.operator() == TokenKind.SLASH) {
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
        if (expression instanceof CallExpr callExpr) {
            ArrayList<IrValue> arguments = new ArrayList<>();
            for (Expression argument : callExpr.arguments()) {
                arguments.add(lowerExpression(argument));
            }
            IrTemporary result = builder.newTemporary(irTypeOf(callExpr));
            if (isDirectFunctionCall(callExpr)) {
                builder.addInstruction(new IrCallInstruction(result, callExpr.calleeName(), arguments, callExpr.range()));
            } else {
                IrValue calleeAddress = lowerExpression(callExpr.callee());
                builder.addInstruction(new IrIndirectCallInstruction(result, calleeAddress, arguments, callExpr.range()));
            }
            return result;
        }
        throw new IllegalArgumentException("unsupported expression: " + expression.getClass().getSimpleName());
    }

    private IrValue lowerUnary(UnaryExpr unaryExpr) {
        if (unaryExpr.operator() == TokenKind.AMPERSAND && unaryExpr.operand() instanceof NameExpr nameExpr) {
            IrLocal local = builder.resolveLocal(nameExpr.name());
            if (local == null) {
                throw new IllegalArgumentException("address-of target must be a local variable: " + nameExpr.name());
            }
            IrTemporary result = builder.newTemporary(IrType.POINTER);
            builder.addInstruction(new IrAddressOfLocalInstruction(result, local, unaryExpr.range()));
            return result;
        }
        if (unaryExpr.operator() == TokenKind.STAR) {
            IrValue address = lowerExpression(unaryExpr.operand());
            IrTemporary result = builder.newTemporary(IrType.INT);
            builder.addInstruction(new IrLoadPointerInstruction(result, address, unaryExpr.range()));
            return result;
        }
        throw new IllegalArgumentException("unsupported unary expression: " + unaryExpr.operator());
    }

    private void lowerStore(Expression target, IrValue value, minic.source.SourceRange range) {
        if (target instanceof NameExpr nameExpr) {
            IrLocal local = builder.resolveLocal(nameExpr.name());
            if (local == null) {
                throw new IllegalArgumentException("assignment target must be a local variable: " + nameExpr.name());
            }
            builder.addInstruction(new IrStoreLocalInstruction(local, value, range));
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
        builder.addInstruction(new IrElementAddressInstruction(result, baseAddress, index, indexExpr.range()));
        return result;
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
        if (expression instanceof FieldAccessExpr fieldAccessExpr) {
            return lowerFieldAddress(fieldAccessExpr);
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
}
