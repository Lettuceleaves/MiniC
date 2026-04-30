package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.model.IrType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class IrFunctionLowerer {
    private final FunctionDecl function;

    IrFunctionLowerer(FunctionDecl function) {
        this.function = Objects.requireNonNull(function, "function");
    }

    IrFunction lower() {
        IrFunctionBuilder builder = new IrFunctionBuilder();
        ArrayList<IrParameter> parameters = new ArrayList<>();
        for (Parameter parameter : function.parameters()) {
            IrParameter irParameter = new IrParameter(parameter.name(), IrType.INT, parameter.range());
            parameters.add(irParameter);
            builder.defineParameter(parameter.name(), irParameter.ref());
        }

        builder.pushLocalScope();
        new StatementLowerer(builder).lowerBlock(function.bodyOptional().orElseThrow(), false);
        builder.popLocalScope();
        return new IrFunction(function.name(), parameters, builder.buildBlocks(), function.range());
    }
}
