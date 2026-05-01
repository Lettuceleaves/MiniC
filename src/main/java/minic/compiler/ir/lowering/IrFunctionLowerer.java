package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.semantic.StructLayout;
import minic.compiler.type.MiniType;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

final class IrFunctionLowerer {
    private final FunctionDecl function;
    private final StringLiteralRegistry stringLiteralRegistry;
    private final Map<String, StructLayout> structLayouts;
    private final Map<Expression, MiniType> expressionTypes;

    IrFunctionLowerer(
            FunctionDecl function,
            StringLiteralRegistry stringLiteralRegistry,
            Map<String, StructLayout> structLayouts,
            Map<Expression, MiniType> expressionTypes
    ) {
        this.function = Objects.requireNonNull(function, "function");
        this.stringLiteralRegistry = Objects.requireNonNull(stringLiteralRegistry, "stringLiteralRegistry");
        this.structLayouts = Map.copyOf(structLayouts);
        this.expressionTypes = Map.copyOf(expressionTypes);
    }

    IrFunction lower() {
        IrFunctionBuilder builder = new IrFunctionBuilder(structLayouts);
        ArrayList<IrParameter> parameters = new ArrayList<>();
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
        new StatementLowerer(builder, stringLiteralRegistry, expressionTypes)
                .lowerBlock(function.bodyOptional().orElseThrow(), false);
        builder.popLocalScope();
        return new IrFunction(function.name(), parameters, builder.buildBlocks(), function.range());
    }
}
