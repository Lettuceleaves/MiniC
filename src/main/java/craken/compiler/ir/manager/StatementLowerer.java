package craken.compiler.ir.manager;

import craken.compiler.parser.node.Expression;
import craken.compiler.parser.node.Statement.CleanupScopeStmt;
import craken.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import craken.compiler.ir.instruction.ComputeInstruction.IrCastInstruction;
import craken.compiler.parser.node.Statement.BlockStmt;
import craken.compiler.parser.node.Statement.BreakStmt;
import craken.compiler.parser.node.Statement.ContinueStmt;
import craken.compiler.parser.node.Statement.DoWhileStmt;
import craken.compiler.parser.node.Statement.ExprStmt;
import craken.compiler.parser.node.Statement.ForStmt;
import craken.compiler.parser.node.Statement.IfStmt;
import craken.compiler.parser.node.Statement.ReturnStmt;
import craken.compiler.parser.node.Statement;
import craken.compiler.parser.node.Statement.SwitchCase;
import craken.compiler.parser.node.Statement.SwitchStmt;
import craken.compiler.parser.node.Expression.IntegerLiteralExpr;
import craken.compiler.parser.node.Expression.IntegerConstantExpr;
import craken.compiler.parser.node.Expression.LongLiteralExpr;
import craken.compiler.parser.node.Expression.CharLiteralExpr;
import craken.compiler.parser.node.Expression.BoolLiteralExpr;
import craken.compiler.parser.node.Expression.AggregateInitExpr;
import craken.compiler.parser.node.Expression.DesignatedInitExpr;
import craken.compiler.parser.node.Expression.Designator;
import craken.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import craken.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator;
import craken.compiler.parser.node.Statement.VarDeclStmt;
import craken.compiler.parser.node.Statement.TypedefStmt;
import craken.compiler.parser.node.Statement.WhileStmt;
import craken.compiler.ir.instruction.ControlInstruction.IrBranchInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrAddressOfLocalInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrElementAddressInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrFieldAddressInstruction;
import craken.compiler.ir.instruction.ControlInstruction.IrJumpInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrMemCopyInstruction;
import craken.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrStoreLocalInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import craken.compiler.ir.model.IrLocal;
import craken.compiler.ir.model.IrType;
import craken.compiler.ir.value.IrValue;
import craken.compiler.ir.value.IrValue.IrConstant;
import craken.compiler.ir.value.IrValue.IrTemporary;
import craken.compiler.type.CrakenType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

final class StatementLowerer {
    private final IrFunctionBuilder builder;
    private final ExpressionLowerer expressionLowerer;
    private final IrType returnType;
    private final Deque<JumpTarget> continueTargets = new ArrayDeque<>();
    private final Deque<JumpTarget> breakTargets = new ArrayDeque<>();
    private final Deque<CleanupAction> cleanups = new ArrayDeque<>();
    private String structReturnName;

    StatementLowerer(
            IrFunctionBuilder builder,
            StringLiteralRegistry stringLiteralRegistry,
            Map<Expression, CrakenType> expressionTypes,
            Map<String, IrFunctionSignature> functionSignatures,
            Map<String, CrakenType> globalTypes,
            IrType returnType,
            boolean variadicFunction,
            int firstVariadicArgumentIndex
    ) {
        this.builder = builder;
        this.returnType = returnType;
        expressionLowerer = new ExpressionLowerer(
                builder,
                stringLiteralRegistry,
                expressionTypes,
                functionSignatures,
                globalTypes,
                variadicFunction,
                firstVariadicArgumentIndex
        );
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
        // Dead tails must not register or emit additional cleanup actions after a transfer.
        if (builder.currentBlockIsTerminated()) return;
        if (statement instanceof CleanupScopeStmt cleanup) {
            var action = new CleanupAction(cleanup.cleanup(), builder.snapshotLocalScopes());
            cleanups.push(action);
            try {
                lowerBranch(cleanup.body());
                if (!builder.currentBlockIsTerminated()) emitCleanup(action);
            } finally {
                cleanups.pop();
            }
            return;
        }
        if (statement instanceof ReturnStmt returnStmt) {
            if (returnStmt.expressionOptional().isEmpty()) {
                emitCleanupsToDepth(0);
                builder.addInstruction(new IrReturnInstruction(null, returnStmt.range()));
                return;
            }
            Expression expression = returnStmt.expressionOptional().orElseThrow();
            if (structReturnName != null && Expression.ObjectInitExpr.occursInResultOf(expression)) {
                IrValue destination = builder.resolveParameter("__retptr");
                expressionLowerer.initializeObjectAt(expression, destination);
                destination = snapshotReturnValue(destination, returnStmt.range());
                emitCleanupsToDepth(0);
                builder.addInstruction(new IrReturnInstruction(destination, returnStmt.range()));
                return;
            }
            IrValue value = expressionLowerer.lowerExpression(expression);
            if (structReturnName != null) {
                IrValue retPtr = builder.resolveParameter("__retptr");
                int size = builder.structSize(structReturnName);
                builder.addInstruction(new IrMemCopyInstruction(retPtr, value, size, returnStmt.range()));
                retPtr = snapshotReturnValue(retPtr, returnStmt.range());
                emitCleanupsToDepth(0);
                builder.addInstruction(new IrReturnInstruction(retPtr, returnStmt.range()));
            } else {
                value = expressionLowerer.castForTarget(value, returnType, returnStmt.range());
                value = snapshotReturnValue(value, returnStmt.range());
                emitCleanupsToDepth(0);
                builder.addInstruction(new IrReturnInstruction(value, returnStmt.range()));
            }
            return;
        }
        if(statement instanceof Statement.DeclGroupStmt group){group.statements().forEach(this::lowerStatement);return;}
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
            if (Expression.ObjectInitExpr.occursInResultOf(varDeclStmt.initializer())) {
                IrTemporary address = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrAddressOfLocalInstruction(address, local, varDeclStmt.range()));
                expressionLowerer.initializeObjectAt(varDeclStmt.initializer(), address);
                return;
            }
            if (!varDeclStmt.type().isArray() && !varDeclStmt.type().isStruct()) {
                varDeclStmt.initializerOptional().ifPresent(initializer -> {
                    IrValue value = expressionLowerer.lowerExpression(initializer);
                    builder.addInstruction(new IrStoreLocalInstruction(
                            local,
                            expressionLowerer.castForTarget(value, local.type(), varDeclStmt.range()),
                            local.declaredType().isVolatileQualified(),
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
                                varDeclStmt.type().isVolatileQualified(),
                                varDeclStmt.range()
                        ));
                    }
                });
            }
            return;
        }
        if (statement instanceof TypedefStmt) {
            // Typedefs are compile-time scope entries and emit no runtime instruction.
            return;
        }
        if (statement instanceof BreakStmt breakStmt) {
            JumpTarget target = breakTargets.peek();
            emitCleanupsToDepth(target.cleanupDepth());
            builder.addInstruction(new IrJumpInstruction(target.label(), breakStmt.range()));
            return;
        }
        if (statement instanceof ContinueStmt continueStmt) {
            JumpTarget target = continueTargets.peek();
            emitCleanupsToDepth(target.cleanupDepth());
            builder.addInstruction(new IrJumpInstruction(target.label(), continueStmt.range()));
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
        addLoopCondition(condition, bodyLabel, exitLabel, whileStmt.condition().range());

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
            addLoopCondition(conditionValue, bodyLabel, exitLabel, condition.range());
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
        addLoopCondition(condition, bodyLabel, exitLabel, doWhileStmt.condition().range());

        builder.switchToBlock(exitLabel);
    }

    /** Preserve the no-fallthrough contract of literal infinite loops in the CFG.
     * The condition has already been lowered, including any comma-expression effects. */
    private void addLoopCondition(IrValue condition, String body, String exit, craken.SourceRange range) {
        if (condition instanceof IrConstant constant) {
            builder.addInstruction(new IrJumpInstruction(constant.value() != 0 ? body : exit, range));
        } else {
            builder.addInstruction(new IrBranchInstruction(condition, body, exit, range));
        }
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

        // All case labels share the switch body scope, which ends after the switch.
        builder.pushLocalScope();
        breakTargets.push(new JumpTarget(exitLabel, cleanups.size()));
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
            breakTargets.pop();
            builder.popLocalScope();
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
            return new craken.compiler.ir.value.IrValue.IrConstant(integerLiteralExpr.value());
        }
        if (expression instanceof IntegerConstantExpr integerConstantExpr) {
            return new craken.compiler.ir.value.IrValue.IrConstant(
                    integerConstantExpr.value(), IrTypeLowerer.lower(integerConstantExpr.type()));
        }
        if (expression instanceof LongLiteralExpr longLiteralExpr) {
            return new craken.compiler.ir.value.IrValue.IrConstant(longLiteralExpr.value(), IrType.LONG);
        }
        if (expression instanceof CharLiteralExpr charLiteralExpr) {
            return new craken.compiler.ir.value.IrValue.IrConstant(charLiteralExpr.value(), IrType.CHAR);
        }
        if (expression instanceof BoolLiteralExpr boolLiteralExpr) {
            return new craken.compiler.ir.value.IrValue.IrConstant(boolLiteralExpr.value() ? 1 : 0, IrType.BOOL);
        }
        throw new IllegalArgumentException("unsupported case constant: " + expression.getClass().getSimpleName());
    }

    private void lowerLoopBranch(Statement statement, String breakLabel, String continueLabel) {
        breakTargets.push(new JumpTarget(breakLabel, cleanups.size()));
        continueTargets.push(new JumpTarget(continueLabel, cleanups.size()));
        try {
            lowerBranch(statement);
        } finally {
            continueTargets.pop();
            breakTargets.pop();
        }
    }

    private IrValue snapshotReturnValue(IrValue value, craken.SourceRange range) {
        if (!cleanups.isEmpty() && value instanceof IrValue.IrParameterRef) {
            IrTemporary snapshot = builder.newTemporary(value.type());
            builder.addInstruction(new IrMoveInstruction(snapshot, value, range));
            return snapshot;
        }
        return value;
    }

    private void emitCleanupsToDepth(int targetDepth) {
        int remaining = cleanups.size() - targetDepth;
        for (CleanupAction action : cleanups) {
            if (remaining-- <= 0) break;
            emitCleanup(action);
        }
    }

    private void emitCleanup(CleanupAction action) {
        builder.withLocalScopes(action.scope(), () -> expressionLowerer.lowerExpression(action.expression()));
    }

    private record JumpTarget(String label, int cleanupDepth) { }
    private record CleanupAction(Expression expression, IrFunctionBuilder.LocalScopeSnapshot scope) { }

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
        zeroInitializeAt(baseAddress, varDeclStmt.type(), varDeclStmt.range());
        lowerAggregateInitAt(
                baseAddress,
                varDeclStmt.type(),
                initializer,
                varDeclStmt.range()
        );
    }

    private void lowerAggregateInitAt(
            IrValue baseAddress,
            CrakenType aggregateType,
            AggregateInitExpr initializer,
            craken.SourceRange range
    ) {
        CrakenType unqualifiedAggregateType = aggregateType.unqualified();
        if (unqualifiedAggregateType instanceof CrakenType.ArrayType arrayType) {
            int index = 0;
            for (Expression value : initializer.values()) {
                if (value instanceof DesignatedInitExpr designated
                        && designated.designators().getFirst() instanceof Designator.Index selected) {
                    index = selected.index();
                }
                if (index >= arrayType.length()) break;
                CrakenType elementType = inheritObjectQualifiers(aggregateType, arrayType.elementType());
                IrTemporary elementAddress = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrElementAddressInstruction(
                        elementAddress,
                        baseAddress,
                        new IrConstant(index),
                        elementType,
                        builder.sizeOf(elementType),
                        range
                ));
                lowerDesignatedAt(elementAddress, elementType, value, 1, range);
                index++;
            }
            return;
        }

        CrakenType.StructType structType = (CrakenType.StructType) unqualifiedAggregateType;
        String structName = structType.name();
        int i = 0;
        for (Expression value : initializer.values()) {
            if (value instanceof DesignatedInitExpr designated
                    && designated.designators().getFirst() instanceof Designator.Field selected) {
                var selectedField = builder.fieldLayout(structName, selected.name());
                IrTemporary selectedAddress = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrFieldAddressInstruction(selectedAddress, baseAddress, structName,
                        selectedField.name(), selectedField.offset(), selectedField.type(), range));
                lowerDesignatedAt(selectedAddress,
                        inheritObjectQualifiers(aggregateType, selectedField.type()), value, 1, range);
                for (int index = 0; index < builder.fieldCount(structName); index++) {
                    if (builder.fieldLayout(structName, index).name().equals(selectedField.name())) {
                        i = index + 1;
                        break;
                    }
                }
                continue;
            }
            if (i >= builder.fieldCount(structName)) break;
            var field = builder.fieldLayout(structName, i);
            CrakenType fieldType = inheritObjectQualifiers(aggregateType, field.type());
            IrTemporary fieldAddr = builder.newTemporary(IrType.POINTER);
            builder.addInstruction(new IrFieldAddressInstruction(
                    fieldAddr,
                    baseAddress,
                    structName,
                    field.name(),
                    field.offset(),
                    fieldType,
                    range
            ));
            lowerDesignatedAt(fieldAddr, fieldType, value, 1, range);
            i++;
        }
    }

    private void zeroInitializeAt(IrValue address, CrakenType type, craken.SourceRange range) {
        ObjectZeroInitializer.emit(builder, address, type, range, false);
    }

    private void lowerDesignatedAt(IrValue address, CrakenType targetType, Expression initializer,
                                   int consumed, craken.SourceRange range) {
        if (!(initializer instanceof DesignatedInitExpr designated)) {
            lowerInitializerAt(address, targetType, initializer, range);
            return;
        }
        IrValue currentAddress = address;
        CrakenType currentType = targetType;
        for (int index = consumed; index < designated.designators().size(); index++) {
            Designator designator = designated.designators().get(index);
            CrakenType unqualifiedCurrentType = currentType.unqualified();
            if (designator instanceof Designator.Index arrayIndex
                    && unqualifiedCurrentType instanceof CrakenType.ArrayType array) {
                CrakenType elementType = inheritObjectQualifiers(currentType, array.elementType());
                IrTemporary nested = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrElementAddressInstruction(nested, currentAddress,
                        new IrConstant(arrayIndex.index()), elementType, builder.sizeOf(elementType), range));
                currentAddress = nested;
                currentType = elementType;
            } else if (designator instanceof Designator.Field field
                    && unqualifiedCurrentType instanceof CrakenType.StructType struct) {
                var layout = builder.fieldLayout(struct.name(), field.name());
                IrTemporary nested = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrFieldAddressInstruction(nested, currentAddress, struct.name(),
                        layout.name(), layout.offset(), layout.type(), range));
                currentAddress = nested;
                currentType = inheritObjectQualifiers(currentType, layout.type());
            }
        }
        lowerInitializerAt(currentAddress, currentType, designated.value(), range);
    }

    private void lowerInitializerAt(
            IrValue address,
            CrakenType targetType,
            Expression initializer,
            craken.SourceRange range
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
                    targetType.isVolatileQualified(),
                    range
            ));
        } else {
            // Pointer stores derive their width and representation from the value,
            // so initializer conversion must precede the store (including null pointers).
            IrType storageType = IrTypeLowerer.lower(targetType);
            if (!targetType.isArray() && value.type() != storageType) {
                IrTemporary converted = builder.newTemporary(storageType);
                builder.addInstruction(new IrCastInstruction(converted, value, range));
                value = converted;
            }
            builder.addInstruction(new IrStorePointerInstruction(
                    address, value, targetType.isVolatileQualified(), range));
        }
    }

    private CrakenType inheritObjectQualifiers(CrakenType ownerType, CrakenType memberType) {
        java.util.EnumSet<CrakenType.TypeQualifier> qualifiers =
                java.util.EnumSet.noneOf(CrakenType.TypeQualifier.class);
        qualifiers.addAll(memberType.qualifiers());
        if (ownerType.isConstQualified()) qualifiers.add(CrakenType.TypeQualifier.CONST);
        if (ownerType.isVolatileQualified()) qualifiers.add(CrakenType.TypeQualifier.VOLATILE);
        return CrakenType.qualified(memberType.unqualified(), qualifiers);
    }

}
