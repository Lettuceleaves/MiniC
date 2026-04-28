package minic.compiler.ir;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 局部变量槽位。
 *
 * @param name IR 内唯一局部变量名
 * @param sourceName 源码中的变量名
 * @param type 局部变量类型
 * @param range 局部变量声明对应的源码范围
 */
public record IrLocal(String name, String sourceName, IrType type, SourceRange range) {
    /**
     * 创建 IR 局部变量槽位。
     *
     * @param name IR 内唯一局部变量名
     * @param sourceName 源码中的变量名
     * @param type 局部变量类型
     * @param range 局部变量声明对应的源码范围
     */
    public IrLocal {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
    }
}
