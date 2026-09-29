package minic.compiler.ir.manager;

import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.parser.node.Statement.BreakStmt;
import minic.compiler.parser.node.Statement.ContinueStmt;
import minic.compiler.parser.node.Statement.DoWhileStmt;
import minic.compiler.parser.node.Statement.ExprStmt;
import minic.compiler.parser.node.Statement.ForStmt;
import minic.compiler.parser.node.Statement.IfStmt;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.parser.node.Statement;
import minic.compiler.parser.node.Statement.SwitchCase;
import minic.compiler.parser.node.Statement.SwitchStmt;
import minic.compiler.parser.node.Expression.IntegerLiteralExpr;
import minic.compiler.parser.node.Expression.IntegerConstantExpr;
import minic.compiler.parser.node.Expression.LongLiteralExpr;
import minic.compiler.parser.node.Expression.CharLiteralExpr;
import minic.compiler.parser.node.Expression.BoolLiteralExpr;
import minic.compiler.parser.node.Expression.AggregateInitExpr;
import minic.compiler.parser.node.Expression.DesignatedInitExpr;
import minic.compiler.parser.node.Expression.Designator;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator;
import minic.compiler.parser.node.Statement.VarDeclStmt;
import minic.compiler.parser.node.Statement.WhileStmt;
import minic.compiler.ir.instruction.ControlInstruction.IrBranchInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrJumpInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrMemCopyInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.IrConstant;
import minic.compiler.ir.value.IrValue.IrTemporary;
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
    private String structReturnName;

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

    void setStructReturn(String structName) {
        this.structReturnName = structName;
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
            if (returnStmt.expressionOptional().isEmpty()) {
                builder.addInstruction(new IrReturnInstruction(null, returnStmt.range()));
                return;
            }
            Expression expression = returnStmt.expressionOptional().orElseThrow();
            IrValue value = expressionLowerer.lowerExpression(expression);
            if (structReturnName != null) {
                IrValue retPtr = builder.resolveParameter("__retptr");
                int size = builder.structSize(structReturnName);
                builder.addInstruction(new IrMemCopyInstruction(retPtr, value, size, returnStmt.range()));
                builder.addInstruction(new IrReturnInstruction(retPtr, returnStmt.range()));
            } else {
                builder.addInstruction(new IrReturnInstruction(
                        expressionLowerer.castForTarget(value, returnType, returnStmt.range()),
                        returnStmt.range()
                ));
            }
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
            if (!varDeclStmt.type().isArray() && !varDeclStmt.type().isStruct()) {
                varDeclStmt.initializerOptional().ifPresent(initializer -> {
                    IrValue value = expressionLowerer.lowerExpression(initializer);
                    builder.addInstruction(new IrStoreLocalInstruction(
                            local,
                            expressionLowerer.castForTarget(value, local.type(), varDeclStmt.range()),
                            varDeclStmt.range()
                    ));
                });
            } else {
                varDeclStmt.initializerOptional().ifPresent(initializer -> {
                    if (initializer instanceof AggregateInitExpr aggregateInitExpr) {
                        lowerAggregateInit(local, varDeclStmt, aggregateInitExpr);
                    } else if (varDeclStmt.type().isStruct()) {
                        IrValue srcAddress = expressionLowerer.lowerExpression(initializer);
                        IrTemporary destAddress = builder.newTemporary(IrType.POINTER);
                        builder.addInstruction(new IrAddressOfLocalInstruction(destAddress, local, varDeclStmt.range()));
                        builder.addInstruction(new IrMemCopyInstruction(
                                destAddress,
                                srcAddress,
                                builder.sizeOf(varDeclStmt.type()),
                                varDeclStmt.range()
                        ));
                    }
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
            return new minic.compiler.ir.value.IrValue.IrConstant(integerLiteralExpr.value());
        }
        if (expression instanceof IntegerConstantExpr integerConstantExpr) {
            return new minic.compiler.ir.value.IrValue.IrConstant(
                    integerConstantExpr.value(), IrTypeLowerer.lower(integerConstantExpr.type()));
        }
        if (expression instanceof LongLiteralExpr longLiteralExpr) {
            return new minic.compiler.ir.value.IrValue.IrConstant(longLiteralExpr.value(), IrType.LONG);
        }
        if (expression instanceof CharLiteralExpr charLiteralExpr) {
            return new minic.compiler.ir.value.IrValue.IrConstant(charLiteralExpr.value(), IrType.CHAR);
        }
        if (expression instanceof BoolLiteralExpr boolLiteralExpr) {
            return new minic.compiler.ir.value.IrValue.IrConstant(boolLiteralExpr.value() ? 1 : 0, IrType.BOOL);
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

    private void lowerAggregateInit(IrLocal local, VarDeclStmt varDeclStmt, AggregateInitExpr initializer) {
        IrTemporary baseAddress = builder.newTemporary(IrType.POINTER);
        builder.addInstruction(new IrAddressOfLocalInstruction(baseAddress, local, varDeclStmt.range()));
        lowerAggregateInitAt(
                baseAddress,
                varDeclStmt.type(),
                initializer,
                varDeclStmt.range()
        );
    }

    private void lowerAggregateInitAt(
            IrValue baseAddress,
            MiniType aggregateType,
            AggregateInitExpr initializer,
            minic.source.SourceRange range
    ) {
        if (aggregateType instanceof MiniType.ArrayType arrayType) {
            int index = 0;
            for (Expression value : initializer.values()) {
                if (value instanceof DesignatedInitExpr designated
                        && designated.designators().getFirst() instanceof Designator.Index selected) {
                    index = selected.index();
                }
                if (index >= arrayType.length()) break;
                IrTemporary elementAddress = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrElementAddressInstruction(
                        elementAddress,
                        baseAddress,
                        new IrConstant(index),
                        arrayType.elementType(),
                        builder.sizeOf(arrayType.elementType()),
                        range
                ));
                lowerDesignatedAt(elementAddress, arrayType.elementType(), value, 1, range);
                index++;
            }
            return;
        }

        MiniType.StructType structType = (MiniType.StructType) aggregateType;
        String structName = structType.name();
        int i = 0;
        for (Expression value : initializer.values()) {
            if (value instanceof DesignatedInitExpr designated
                    && designated.designators().getFirst() instanceof Designator.Field selected) {
                var selectedField = builder.fieldLayout(structName, selected.name());
                for (int index = 0; ; index++) {
                    if (builder.fieldLayout(structName, index).name().equals(selectedField.name())) { i = index; break; }
                }
            }
            var field = builder.fieldLayout(structName, i);
            IrTemporary fieldAddr = builder.newTemporary(IrType.POINTER);
            builder.addInstruction(new IrFieldAddressInstruction(
                    fieldAddr,
                    baseAddress,
                    structName,
                    field.name(),
                    field.offset(),
                    field.type(),
                    range
            ));
            lowerDesignatedAt(fieldAddr, field.type(), value, 1, range);
            i++;
        }
    }

    private void lowerDesignatedAt(IrValue address, MiniType targetType, Expression initializer,
                                   int consumed, minic.source.SourceRange range) {
        if (!(initializer instanceof DesignatedInitExpr designated)) {
            lowerInitializerAt(address, targetType, initializer, range);
            return;
        }
        IrValue currentAddress = address;
        MiniType currentType = targetType;
        for (int index = consumed; index < designated.designators().size(); index++) {
            Designator designator = designated.designators().get(index);
            if (designator instanceof Designator.Index arrayIndex && currentType instanceof MiniType.ArrayType array) {
                IrTemporary nested = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrElementAddressInstruction(nested, currentAddress,
                        new IrConstant(arrayIndex.index()), array.elementType(), builder.sizeOf(array.elementType()), range));
                currentAddress = nested;
                currentType = array.elementType();
            } else if (designator instanceof Designator.Field field && currentType instanceof MiniType.StructType struct) {
                var layout = builder.fieldLayout(struct.name(), field.name());
                IrTemporary nested = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrFieldAddressInstruction(nested, currentAddress, struct.name(),
                        layout.name(), layout.offset(), layout.type(), range));
                currentAddress = nested;
                currentType = layout.type();
            }
        }
        lowerInitializerAt(currentAddress, currentType, designated.value(), range);
    }

    private void lowerInitializerAt(
            IrValue address,
            MiniType targetType,
            Expression initializer,
            minic.source.SourceRange range
    ) {
        if (initializer instanceof AggregateInitExpr nested) {
            lowerAggregateInitAt(address, targetType, nested, range);
            return;
        }
        IrValue value = expressionLowerer.lowerExpression(initializer);
        if (targetType.isStruct()) {
            builder.addInstruction(new IrMemCopyInstruction(
                    address,
                    value,
                    builder.sizeOf(targetType),
                    range
            ));
        } else {
            builder.addInstruction(new IrStorePointerInstruction(address, value, range));
        }
    }

    private record LoopTarget(String breakLabel, String continueLabel) {
    }
}
