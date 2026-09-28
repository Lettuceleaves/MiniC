package minic.compiler.nativebuild.x64;

import java.util.Objects;

/**
 * section 内的地址修正记录。
 *
 * @param offset 待修正字段相对于 section 开头的偏移
 * @param symbol 目标符号
 * @param kind 修正类型
 * @param addend 附加常量
 */
public record MachineRelocation(int offset, String symbol, MachineRelocationKind kind, long addend) {
    public MachineRelocation {
        if (offset < 0) {
            throw new IllegalArgumentException("relocation offset must not be negative");
        }
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(kind, "kind");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("relocation symbol must not be blank");
        }
    }
}
