package minic.compiler.codegen.machine;

import java.util.Objects;

/**
 * section 内部标签。
 *
 * @param name 标签名
 * @param global 是否导出为全局符号
 */
public record MachineLabel(String name, boolean global) implements MachineItem {
    public MachineLabel {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("label name must not be blank");
        }
    }
}
