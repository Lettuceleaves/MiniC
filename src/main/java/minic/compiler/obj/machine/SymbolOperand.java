package minic.compiler.obj.machine;

import java.util.Objects;

/**
 * 符号或标签引用操作数。
 *
 * @param symbol 符号名
 */
public record SymbolOperand(String symbol) implements MachineOperand {
    public SymbolOperand {
        Objects.requireNonNull(symbol, "symbol");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
    }
}
