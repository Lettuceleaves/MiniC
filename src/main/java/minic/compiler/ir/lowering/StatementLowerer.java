package minic.compiler.ir.lowering;

import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.BreakStmt;
import minic.compiler.ast.stmt.ContinueStmt;
import minic.compiler.ast.stmt.DoWhileStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ForStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.SwitchCase;
import minic.compiler.ast.stmt.SwitchStmt;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.LongLiteralExpr;
import minic.compiler.ast.expr.CharLiteralExpr;
import minic.compiler.ast.expr.BoolLiteralExpr;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBinaryOperator;
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
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.type.MiniType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

final class StatementLowerer {
    private final IrFunctionBuilder builder;
    private final ExpressionLowerer expressionLowerer;
    private final IrType returnType;
    private final Deque<LoopTarget> loopTargets = new ArrayDeque<>();
    private final Deque<String> switchBreakTargets = new ArrayDeque<>();

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

    void lowerStatement(Statement statement) {
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
            String breakLabel = !loopTargets.isEmpty() ? loopTargets.peek().breakLabel() : switchBreakTargets.peek();
            builder.addInstruction(new IrJumpInstruction(breakLabel, breakStmt.range()));
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
        if (statement instanceof DoWhileStmt doWhileStmt) {
            lowerDoWhile(doWhileStmt);
            return;
        }
        if (statement instanceof ForStmt forStmt) {
            lowerFor(forStmt);
            return;
        }
        if (statement instanceof SwitchStmt switchStmt) {
            lowerSwitch(switchStmt);
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

    private void lowerDoWhile(DoWhileStmt doWhileStmt) {
        String bodyLabel = builder.newBlockLabel("do_body");
        String conditionLabel = builder.newBlockLabel("do_condition");
        String exitLabel = builder.newBlockLabel("do_exit");

        builder.addJumpIfOpen(bodyLabel, doWhileStmt.range());

        builder.switchToBlock(bodyLabel);
        lowerLoopBranch(doWhileStmt.body(), exitLabel, conditionLabel);
        builder.addJumpIfOpen(conditionLabel, doWhileStmt.body().range());

        builder.switchToBlock(conditionLabel);
        IrValue condition = expressionLowerer.lowerExpression(doWhileStmt.condition());
        builder.addInstruction(new IrBranchInstruction(condition, bodyLabel, exitLabel, doWhileStmt.condition().range()));

        builder.switchToBlock(exitLabel);
    }

    private void lowerSwitch(SwitchStmt switchStmt) {
        IrValue selector = expressionLowerer.lowerExpression(switchStmt.selector());
        String exitLabel = builder.newBlockLabel("switch_exit");
        java.util.List<String> caseLabels = new java.util.ArrayList<>();
        for (int index = 0; index < switchStmt.cases().size(); index++) {
            caseLabels.add(builder.newBlockLabel(switchStmt.cases().get(index).defaultCase() ? "switch_default" : "switch_case"));
        }
        String defaultLabel = exitLabel;
        for (int index = 0; index < switchStmt.cases().size(); index++) {
            if (switchStmt.cases().get(index).defaultCase()) {
                defaultLabel = caseLabels.get(index);
                break;
            }
        }
        for (int index = 0; index < switchStmt.cases().size(); index++) {
            SwitchCase switchCase = switchStmt.cases().get(index);
            if (switchCase.defaultCase()) {
                continue;
            }
            IrValue caseValue = lowerCaseConstant(switchCase.valueOptional().orElseThrow());
            IrTemporary comparison = builder.newTemporary(IrType.INT);
            builder.addInstruction(new IrBinaryInstruction(
                    comparison,
                    IrBinaryOperator.EQUAL,
                    selector,
                    caseValue,
                    switchCase.range()
            ));
            String nextCheckLabel = builder.newBlockLabel("switch_check");
            String elseLabel = hasLaterNonDefaultCase(switchStmt, index) ? nextCheckLabel : defaultLabel;
            builder.addInstruction(new IrBranchInstruction(comparison, caseLabels.get(index), elseLabel, switchCase.range()));
            if (hasLaterNonDefaultCase(switchStmt, index)) {
                builder.switchToBlock(nextCheckLabel);
            }
        }
        builder.addJumpIfOpen(defaultLabel, switchStmt.range());

        switchBreakTargets.push(exitLabel);
        try {
            for (int index = 0; index < switchStmt.cases().size(); index++) {
                SwitchCase switchCase = switchStmt.cases().get(index);
                builder.switchToBlock(caseLabels.get(index));
                for (Statement statement : switchCase.statements()) {
                    lowerStatement(statement);
                }
                String fallthrough = index + 1 < switchStmt.cases().size() ? caseLabels.get(index + 1) : exitLabel;
                builder.addJumpIfOpen(fallthrough, switchCase.range());
            }
        } finally {
            switchBreakTargets.pop();
        }
        builder.switchToBlock(exitLabel);
    }

    private boolean hasLaterNonDefaultCase(SwitchStmt switchStmt, int currentIndex) {
        for (int index = currentIndex + 1; index < switchStmt.cases().size(); index++) {
            if (!switchStmt.cases().get(index).defaultCase()) {
                return true;
            }
        }
        return false;
    }

    private IrValue lowerCaseConstant(Expression expression) {
        if (expression instanceof IntegerLiteralExpr integerLiteralExpr) {
            return new minic.compiler.ir.value.IrConstant(integerLiteralExpr.value());
        }
        if (expression instanceof LongLiteralExpr longLiteralExpr) {
            return new minic.compiler.ir.value.IrConstant(longLiteralExpr.value(), IrType.LONG);
        }
        if (expression instanceof CharLiteralExpr charLiteralExpr) {
            return new minic.compiler.ir.value.IrConstant(charLiteralExpr.value(), IrType.CHAR);
        }
        if (expression instanceof BoolLiteralExpr boolLiteralExpr) {
            return new minic.compiler.ir.value.IrConstant(boolLiteralExpr.value() ? 1 : 0, IrType.BOOL);
        }
        throw new IllegalArgumentException("unsupported case constant: " + expression.getClass().getSimpleName());
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
