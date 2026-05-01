package minic.compiler.ir.lowering;

import minic.compiler.ir.model.IrType;

import java.util.List;
import java.util.Objects;

record IrFunctionSignature(IrType returnType, List<IrType> parameterTypes) {
    IrFunctionSignature {
        Objects.requireNonNull(returnType, "returnType");
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        parameterTypes = List.copyOf(parameterTypes);
    }
}
