package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrParameter;
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
    private int nextStatementIndex;
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
        statementLowerer = new StatementLowerer(
                builder,
                Objects.requireNonNull(stringLiteralRegistry, "stringLiteralRegistry"),
                expressionTypes,
                functionSignatures,
                IrTypeLowerer.lower(function.returnType())
        );
    }

    IrLoweringAction begin() {
        if (begun) {
            throw new IllegalStateException("function lowering already begun");
        }
        begun = true;
        for (Parameter parameter : function.parameters()) {
            IrParameter irParameter = new IrParameter(
                    parameter.name(),
                    IrTypeLowerer.lower(parameter.type()),
                    parameter.range()
            );
            parameters.add(irParameter);
            builder.defineParameter(parameter.name(), irParameter.ref());
        }
        builder.pushLocalScope();
        return new IrLoweringAction(IrLoweringActionKind.BEGIN_FUNCTION, function.name());
    }

    boolean hasNextStatement() {
        return nextStatementIndex < statements().size();
    }

    IrLoweringAction lowerNextStatement() {
        if (!begun || completed) {
            throw new IllegalStateException("function lowering is not active");
        }
        Statement statement = statements().get(nextStatementIndex++);
        statementLowerer.lowerStatement(statement);
        return new IrLoweringAction(
                IrLoweringActionKind.LOWER_STATEMENT,
                function.name() + " " + statement.getClass().getSimpleName()
        );
    }

    IrFunction complete() {
        if (!begun || completed || hasNextStatement()) {
            throw new IllegalStateException("function lowering cannot complete yet");
        }
        completed = true;
        builder.popLocalScope();
        return new IrFunction(function.name(), parameters, builder.buildBlocks(), function.range());
    }

    private List<Statement> statements() {
        return function.bodyOptional().orElseThrow().statements();
    }
}
