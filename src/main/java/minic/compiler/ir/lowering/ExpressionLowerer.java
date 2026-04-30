package minic.compiler.ir.lowering;

import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.expr.StringLiteralExpr;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.compiler.lexer.TokenKind;

import java.util.ArrayList;

final class ExpressionLowerer {
    private final IrFunctionBuilder builder;
    private final StringLiteralRegistry stringLiteralRegistry;

    ExpressionLowerer(IrFunctionBuilder builder, StringLiteralRegistry stringLiteralRegistry) {
        this.builder = builder;
        this.stringLiteralRegistry = stringLiteralRegistry;
    }

    IrValue lowerExpression(Expression expression) {
        if (expression instanceof IntegerLiteralExpr integerLiteralExpr) {
            return new IrConstant(integerLiteralExpr.value());
        }
        if (expression instanceof StringLiteralExpr stringLiteralExpr) {
            return stringLiteralRegistry.define(stringLiteralExpr.value());
        }
        if (expression instanceof NameExpr nameExpr) {
            IrLocal local = builder.resolveLocal(nameExpr.name());
            if (local != null) {
                builder.addInstruction(new IrCheckInitializedInstruction(local, nameExpr.range()));
                IrTemporary result = builder.newTemporary();
                builder.addInstruction(new IrLoadLocalInstruction(result, local, nameExpr.range()));
                return result;
            }
            return builder.resolveParameter(nameExpr.name());
        }
        if (expression instanceof GroupingExpr groupingExpr) {
            return lowerExpression(groupingExpr.expression());
        }
        if (expression instanceof AssignmentExpr assignmentExpr) {
            IrLocal local = builder.resolveLocal(assignmentExpr.targetName());
            if (local == null) {
                throw new IllegalArgumentException("assignment target must be a local variable: "
                        + assignmentExpr.targetName());
            }
            IrValue value = lowerExpression(assignmentExpr.value());
            builder.addInstruction(new IrStoreLocalInstruction(local, value, assignmentExpr.range()));
            return value;
        }
        if (expression instanceof BinaryExpr binaryExpr) {
            IrValue left = lowerExpression(binaryExpr.left());
            IrValue right = lowerExpression(binaryExpr.right());
            IrTemporary result = builder.newTemporary();
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
            IrTemporary result = builder.newTemporary();
            builder.addInstruction(new IrCallInstruction(result, callExpr.calleeName(), arguments, callExpr.range()));
            return result;
        }
        throw new IllegalArgumentException("unsupported expression: " + expression.getClass().getSimpleName());
    }
}
