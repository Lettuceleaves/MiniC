package minic.compiler.obj.machine;

import minic.SourceRange;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 结构化 x64 指令。
 *
 * @param mnemonic 指令助记符
 * @param operands 操作数
 * @param sourceRange 可选源码范围
 */
public record MachineInstruction(
        String mnemonic,
        List<MachineOperand> operands,
        SourceRange sourceRange
) implements MachineItem {
    public MachineInstruction {
        Objects.requireNonNull(mnemonic, "mnemonic");
        Objects.requireNonNull(operands, "operands");
        if (mnemonic.isBlank()) {
            throw new IllegalArgumentException("mnemonic must not be blank");
        }
        mnemonic = mnemonic.toLowerCase(Locale.ROOT);
        operands = List.copyOf(operands);
    }

    public MachineInstruction(String mnemonic, MachineOperand... operands) {
        this(mnemonic, List.of(operands), null);
    }

    public Optional<SourceRange> sourceRangeOptional() {
        return Optional.ofNullable(sourceRange);
    }
}
