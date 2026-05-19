package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.ForStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ast.stmt.WhileStmt;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.model.IrType;
import minic.compiler.semantic.StructLayout;
import minic.compiler.type.MiniType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 可逐语句推进的函数 IR lowering。
 */
final class IncrementalIrFunctionLowerer {
    private final FunctionDecl function;
    private final IrFunctionBuilder builder;
    private final StatementLowerer statementLowerer;
    private final ArrayList<IrParameter> parameters = new ArrayList<>();
    private final List<AstStep> astSteps;
    private int nextAstNodeIndex;
    private boolean begun;
    private boolean completed;

    IncrementalIrFunctionLowerer(
            FunctionDecl function,
            StringLiteralRegistry stringLiteralRegistry,
            Map<String, StructLayout> structLayouts,
            Map<Expression, MiniType> expressionTypes,
            Map<String, IrFunctionSignature> functionSignatures
    ) {
        this.function = Objects.requireNonNull(function, "function");
        builder = new IrFunctionBuilder(structLayouts);
        boolean structReturn = function.returnType().isStruct();
        IrType irReturnType = structReturn ? IrType.POINTER : IrTypeLowerer.lower(function.returnType());
        statementLowerer = new StatementLowerer(
                builder,
                Objects.requireNonNull(stringLiteralRegistry, "stringLiteralRegistry"),
                expressionTypes,
                functionSignatures,
                irReturnType
        );
        if (structReturn) {
            statementLowerer.setStructReturn(((MiniType.StructType) function.returnType()).name());
        }
        astSteps = function.bodyOptional()
                .map(IncrementalIrFunctionLowerer::astNodes)
                .orElse(List.of());
    }

    IrLoweringAction begin() {
        if (begun) {
            throw new IllegalStateException("function lowering already begun");
        }
        begun = true;
        if (function.returnType().isStruct()) {
            IrParameter retPtr = new IrParameter("__retptr", IrType.POINTER, function.range());
            parameters.add(retPtr);
            builder.defineParameter("__retptr", retPtr.ref());
        }
        for (Parameter parameter : function.parameters()) {
            IrType paramIrType = parameter.type().isStruct()
                    ? IrType.POINTER
                    : IrTypeLowerer.lower(parameter.type());
            IrParameter irParameter = new IrParameter(
                    parameter.name(),
                    paramIrType,
                    parameter.range()
            );
            parameters.add(irParameter);
            builder.defineParameter(parameter.name(), irParameter.ref());
        }
        builder.pushLocalScope();
        return new IrLoweringAction(IrLoweringActionKind.BEGIN_FUNCTION, function.name());
    }

    boolean hasNextAstNode() {
        return nextAstNodeIndex < astSteps.size();
    }

    IrLoweringAction lowerNextAstNode() {
        if (!begun || completed) {
            throw new IllegalStateException("function lowering is not active");
        }
        AstStep step = astSteps.get(nextAstNodeIndex++);
        Object node = step.node();
        if (step.emitsIr() && node instanceof Statement statement) {
            statementLowerer.lowerStatement(statement);
        }
        return new IrLoweringAction(
                step.emitsIr() ? IrLoweringActionKind.LOWER_STATEMENT : IrLoweringActionKind.LOWER_AST_NODE,
                function.name() + " " + node.getClass().getSimpleName(),
                node
        );
    }

    IrFunction complete() {
        if (!begun || completed || hasNextAstNode()) {
            throw new IllegalStateException("function lowering cannot complete yet");
        }
        completed = true;
        builder.popLocalScope();
        return new IrFunction(function.name(), parameters, builder.buildBlocks(), function.range());
    }

    private static List<AstStep> astNodes(BlockStmt body) {
        ArrayList<AstStep> nodes = new ArrayList<>();
        for (Statement statement : body.statements()) {
            appendStatement(statement, nodes, true);
        }
        return List.copyOf(nodes);
    }

    private static void appendStatement(Statement statement, ArrayList<AstStep> nodes, boolean emitsIr) {
        nodes.add(new AstStep(statement, emitsIr));
        switch (statement) {
            case BlockStmt blockStmt -> blockStmt.statements().forEach(child -> appendStatement(child, nodes, false));
            case VarDeclStmt varDeclStmt -> varDeclStmt.initializerOptional().ifPresent(expression -> appendExpression(expression, nodes));
            case minic.compiler.ast.stmt.ReturnStmt returnStmt -> returnStmt.expressionOptional()
                    .ifPresent(expression -> appendExpression(expression, nodes));
            case minic.compiler.ast.stmt.ExprStmt exprStmt -> appendExpression(exprStmt.expression(), nodes);
            case IfStmt ifStmt -> {
                appendExpression(ifStmt.condition(), nodes);
                appendStatement(ifStmt.thenBranch(), nodes, false);
                ifStmt.elseBranchOptional().ifPresent(child -> appendStatement(child, nodes, false));
            }
            case WhileStmt whileStmt -> {
                appendExpression(whileStmt.condition(), nodes);
                appendStatement(whileStmt.body(), nodes, false);
            }
            case ForStmt forStmt -> {
                forStmt.initializerOptional().ifPresent(child -> appendStatement(child, nodes, false));
                forStmt.conditionOptional().ifPresent(expression -> appendExpression(expression, nodes));
                forStmt.stepOptional().ifPresent(expression -> appendExpression(expression, nodes));
                appendStatement(forStmt.body(), nodes, false);
            }
            default -> {
            }
        }
    }

    private static void appendExpression(Expression expression, ArrayList<AstStep> nodes) {
        nodes.add(new AstStep(expression, false));
        switch (expression) {
            case minic.compiler.ast.expr.AssignmentExpr assignmentExpr -> {
                appendExpression(assignmentExpr.target(), nodes);
                appendExpression(assignmentExpr.value(), nodes);
            }
            case minic.compiler.ast.expr.BinaryExpr binaryExpr -> {
                appendExpression(binaryExpr.left(), nodes);
                appendExpression(binaryExpr.right(), nodes);
            }
            case minic.compiler.ast.expr.CallExpr callExpr -> {
                appendExpression(callExpr.callee(), nodes);
                callExpr.arguments().forEach(argument -> appendExpression(argument, nodes));
            }
            case minic.compiler.ast.expr.FieldAccessExpr fieldAccessExpr -> appendExpression(fieldAccessExpr.target(), nodes);
            case minic.compiler.ast.expr.GroupingExpr groupingExpr -> appendExpression(groupingExpr.expression(), nodes);
            case minic.compiler.ast.expr.IndexExpr indexExpr -> {
                appendExpression(indexExpr.target(), nodes);
                appendExpression(indexExpr.index(), nodes);
            }
            case minic.compiler.ast.expr.UnaryExpr unaryExpr -> appendExpression(unaryExpr.operand(), nodes);
            default -> {
            }
        }
    }

    private record AstStep(Object node, boolean emitsIr) {
    }
}
