package minic.compiler.ir.lowering;

import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.value.IrValue;

final class StatementLowerer {
    private final IrFunctionBuilder builder;
    private final ExpressionLowerer expressionLowerer;

    StatementLowerer(IrFunctionBuilder builder) {
        this.builder = builder;
        expressionLowerer = new ExpressionLowerer(builder);
    }

    void lowerBlock(BlockStmt block, boolean createChildScope) {
        if (createChildScope) {
            builder.pushLocalScope();
        }
        for (Statement statement : block.statements()) {
            lowerStatement(statement);
        }
        if (createChildScope) {
            builder.popLocalScope();
        }
    }

    private void lowerStatement(Statement statement) {
        if (statement instanceof ReturnStmt returnStmt) {
            Expression expression = returnStmt.expressionOptional()
                    .orElseThrow(() -> new IllegalArgumentException("return statement must have a value"));
            builder.addInstruction(new IrReturnInstruction(expressionLowerer.lowerExpression(expression), returnStmt.range()));
            return;
        }
        if (statement instanceof BlockStmt blockStmt) {
            lowerBlock(blockStmt, true);
            return;
        }
        if (statement instanceof ExprStmt exprStmt) {
            expressionLowerer.lowerExpression(exprStmt.expression());
            return;
        }
        if (statement instanceof VarDeclStmt varDeclStmt) {
            IrLocal local = builder.declareLocal(varDeclStmt);
            builder.addInstruction(new IrDeclareLocalInstruction(local, varDeclStmt.range()));
            varDeclStmt.initializerOptional().ifPresent(initializer -> {
                IrValue value = expressionLowerer.lowerExpression(initializer);
                builder.addInstruction(new IrStoreLocalInstruction(local, value, varDeclStmt.range()));
            });
            return;
        }
        throw new IllegalArgumentException("unsupported statement: " + statement.getClass().getSimpleName());
    }
}
