package minic.compiler.obj.machine;

import java.util.Locale;
import java.util.Objects;

/**
 * 寄存器操作数。
 *
 * @param name Intel 寄存器名称
 */
public record RegisterOperand(String name) implements MachineOperand {
    public RegisterOperand {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("register name must not be blank");
        }
        name = name.toLowerCase(Locale.ROOT);
    }
}
