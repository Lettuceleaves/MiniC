package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.model.IrType;
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
    private final Map<String, IrFunctionSignature> functionSignatures;

    IrFunctionLowerer(
            FunctionDecl function,
            StringLiteralRegistry stringLiteralRegistry,
            Map<String, StructLayout> structLayouts,
            Map<Expression, MiniType> expressionTypes,
            Map<String, IrFunctionSignature> functionSignatures
    ) {
        this.function = Objects.requireNonNull(function, "function");
        this.stringLiteralRegistry = Objects.requireNonNull(stringLiteralRegistry, "stringLiteralRegistry");
        this.structLayouts = Map.copyOf(structLayouts);
        this.expressionTypes = Map.copyOf(expressionTypes);
        this.functionSignatures = Map.copyOf(functionSignatures);
    }

    IrFunction lower() {
        IrFunctionBuilder builder = new IrFunctionBuilder(structLayouts);
        ArrayList<IrParameter> parameters = new ArrayList<>();
        boolean structReturn = function.returnType().isStruct();

        if (structReturn) {
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

        IrType irReturnType = structReturn ? IrType.POINTER : IrTypeLowerer.lower(function.returnType());
        builder.pushLocalScope();
        StatementLowerer statementLowerer = new StatementLowerer(
                builder, stringLiteralRegistry, expressionTypes, functionSignatures, irReturnType);
        if (structReturn) {
            statementLowerer.setStructReturn(
                    ((MiniType.StructType) function.returnType()).name());
        }
        statementLowerer.lowerBlock(function.bodyOptional().orElseThrow(), false);
        builder.popLocalScope();
        return new IrFunction(function.name(), parameters, builder.buildBlocks(), function.range());
    }
}
