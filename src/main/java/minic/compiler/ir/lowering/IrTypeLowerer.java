package minic.compiler.ir.lowering;

import minic.compiler.ir.model.IrType;
import minic.compiler.type.MiniType;

final class IrTypeLowerer {
    private IrTypeLowerer() {
    }

    static IrType lower(MiniType type) {
        if (type.isArray()) {
            return IrType.INT_ARRAY;
        }
        if (type.isPointer()) {
            return IrType.POINTER;
        }
        return IrType.INT;
    }

    static int elementCount(MiniType type) {
        if (type.isArray()) {
            return type.arrayLength();
        }
        return 1;
    }
}
