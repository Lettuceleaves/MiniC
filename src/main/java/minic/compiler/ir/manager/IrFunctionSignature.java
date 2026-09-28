package minic.compiler.ir.manager;

import minic.compiler.ir.model.IrType;

import java.util.List;
import java.util.Objects;

public record IrFunctionSignature(IrType returnType, List<IrType> parameterTypes) {
    public IrFunctionSignature {
        Objects.requireNonNull(returnType, "returnType");
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        parameterTypes = List.copyOf(parameterTypes);
    }
}
