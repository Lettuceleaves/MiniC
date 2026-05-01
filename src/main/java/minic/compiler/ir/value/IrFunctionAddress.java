package minic.compiler.ir.value;

import minic.compiler.ir.model.IrType;

import java.util.Objects;

/**
 * 指向用户函数或外部函数入口的地址值。
 *
 * @param functionName 函数名
 */
public record IrFunctionAddress(String functionName) implements IrValue {
    /**
     * 创建函数地址值。
     *
     * @param functionName 函数名
     */
    public IrFunctionAddress {
        Objects.requireNonNull(functionName, "functionName");
        if (functionName.isBlank()) {
            throw new IllegalArgumentException("functionName must not be blank");
        }
    }

    @Override
    public IrType type() {
        return IrType.POINTER;
    }
}
