package minic.compiler.ir.lowering;

import minic.compiler.ir.model.IrType;
import minic.compiler.type.MiniType;

final class IrTypeLowerer {
    private IrTypeLowerer() {
    }

    static IrType lower(MiniType type) {
        if (type.isPointer()) {
            return IrType.POINTER;
        }
        return IrType.INT;
    }
}
