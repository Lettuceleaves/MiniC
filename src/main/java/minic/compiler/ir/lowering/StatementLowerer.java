package minic.compiler.ir.lowering;

import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.BreakStmt;
import minic.compiler.ast.stmt.ContinueStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ForStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ast.stmt.WhileStmt;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrJumpInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue;
import minic.compiler.type.MiniType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

final class StatementLowerer {
    private final IrFunctionBuilder builder;
    private final ExpressionLowerer expressionLowerer;
    private final IrType returnType;
    private final Deque<LoopTarget> loopTargets = new ArrayDeque<>();

    StatementLowerer(
            IrFunctionBuilder builder,
            StringLiteralRegistry stringLiteralRegistry,
            Map<Expression, MiniType> expressionTypes,
            Map<String, IrFunctionSignature> functionSignatures,
            IrType returnType
    ) {
        this.builder = builder;
        this.returnType = returnType;
        expressionLowerer = new ExpressionLowerer(builder, stringLiteralRegistry, expressionTypes, functionSignatures);
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
            IrValue value = expressionLowerer.lowerExpression(expression);
            builder.addInstruction(new IrReturnInstruction(
                    expressionLowerer.castForTarget(value, returnType, returnStmt.range()),
                    returnStmt.range()
            ));
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
            if (!varDeclStmt.type().isArray()) {
                varDeclStmt.initializerOptional().ifPresent(initializer -> {
                    IrValue value = expressionLowerer.lowerExpression(initializer);
                    builder.addInstruction(new IrStoreLocalInstruction(
                            local,
                            expressionLowerer.castForTarget(value, local.type(), varDeclStmt.range()),
                            varDeclStmt.range()
                    ));
                });
            }
            return;
        }
        if (statement instanceof BreakStmt breakStmt) {
            builder.addInstruction(new IrJumpInstruction(loopTargets.peek().breakLabel(), breakStmt.range()));
            return;
        }
        if (statement instanceof ContinueStmt continueStmt) {
            builder.addInstruction(new IrJumpInstruction(loopTargets.peek().continueLabel(), continueStmt.range()));
            return;
        }
        if (statement instanceof IfStmt ifStmt) {
            lowerIf(ifStmt);
            return;
        }
        if (statement instanceof WhileStmt whileStmt) {
            lowerWhile(whileStmt);
            return;
        }
        if (statement instanceof ForStmt forStmt) {
            lowerFor(forStmt);
            return;
        }
        throw new IllegalArgumentException("unsupported statement: " + statement.getClass().getSimpleName());
    }

    private void lowerIf(IfStmt ifStmt) {
        IrValue condition = expressionLowerer.lowerExpression(ifStmt.condition());
        String thenLabel = builder.newBlockLabel("then");
        String elseLabel = ifStmt.elseBranchOptional().isPresent()
                ? builder.newBlockLabel("else")
                : builder.newBlockLabel("merge");
        String mergeLabel = ifStmt.elseBranchOptional().isPresent()
                ? builder.newBlockLabel("merge")
                : elseLabel;
        builder.addInstruction(new IrBranchInstruction(condition, thenLabel, elseLabel, ifStmt.condition().range()));

        builder.switchToBlock(thenLabel);
        lowerBranch(ifStmt.thenBranch());
        builder.addJumpIfOpen(mergeLabel, ifStmt.thenBranch().range());

        ifStmt.elseBranchOptional().ifPresent(elseBranch -> {
            builder.switchToBlock(elseLabel);
            lowerBranch(elseBranch);
            builder.addJumpIfOpen(mergeLabel, elseBranch.range());
        });

        builder.switchToBlock(mergeLabel);
    }

    private void lowerWhile(WhileStmt whileStmt) {
        String conditionLabel = builder.newBlockLabel("while_condition");
        String bodyLabel = builder.newBlockLabel("while_body");
        String exitLabel = builder.newBlockLabel("while_exit");

        builder.addJumpIfOpen(conditionLabel, whileStmt.range());

        builder.switchToBlock(conditionLabel);
        IrValue condition = expressionLowerer.lowerExpression(whileStmt.condition());
        builder.addInstruction(new IrBranchInstruction(condition, bodyLabel, exitLabel, whileStmt.condition().range()));

        builder.switchToBlock(bodyLabel);
        lowerLoopBranch(whileStmt.body(), exitLabel, conditionLabel);
        builder.addJumpIfOpen(conditionLabel, whileStmt.body().range());

        builder.switchToBlock(exitLabel);
    }

    private void lowerFor(ForStmt forStmt) {
        String conditionLabel = builder.newBlockLabel("for_condition");
        String bodyLabel = builder.newBlockLabel("for_body");
        String stepLabel = builder.newBlockLabel("for_step");
        String exitLabel = builder.newBlockLabel("for_exit");

        builder.pushLocalScope();
        forStmt.initializerOptional().ifPresent(this::lowerStatement);
        builder.addJumpIfOpen(conditionLabel, forStmt.range());

        builder.switchToBlock(conditionLabel);
        if (forStmt.conditionOptional().isPresent()) {
            Expression condition = forStmt.conditionOptional().orElseThrow();
            IrValue conditionValue = expressionLowerer.lowerExpression(condition);
            builder.addInstruction(new IrBranchInstruction(conditionValue, bodyLabel, exitLabel, condition.range()));
        } else {
            builder.addJumpIfOpen(bodyLabel, forStmt.range());
        }

        builder.switchToBlock(bodyLabel);
        lowerLoopBranch(forStmt.body(), exitLabel, stepLabel);
        builder.addJumpIfOpen(stepLabel, forStmt.body().range());

        builder.switchToBlock(stepLabel);
        forStmt.stepOptional().ifPresent(expressionLowerer::lowerExpression);
        builder.addJumpIfOpen(conditionLabel, forStmt.range());

        builder.switchToBlock(exitLabel);
        builder.popLocalScope();
    }

    private void lowerLoopBranch(Statement statement, String breakLabel, String continueLabel) {
        loopTargets.push(new LoopTarget(breakLabel, continueLabel));
        try {
            lowerBranch(statement);
        } finally {
            loopTargets.pop();
        }
    }

    private void lowerBranch(Statement statement) {
        if (statement instanceof BlockStmt blockStmt) {
            lowerBlock(blockStmt, true);
        } else {
            builder.pushLocalScope();
            lowerStatement(statement);
            builder.popLocalScope();
        }
    }

    private record LoopTarget(String breakLabel, String continueLabel) {
    }
}
