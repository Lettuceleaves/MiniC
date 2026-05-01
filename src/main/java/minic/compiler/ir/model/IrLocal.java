package minic.compiler.ir.model;

import minic.compiler.type.TypeLayout;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 局部变量槽位。
 *
 * @param name IR 内唯一局部变量名
 * @param sourceName 源码中的变量名
 * @param type 局部变量类型
 * @param elementCount 局部变量元素数量；标量为 1，数组为数组长度
 * @param sizeBytes 局部变量存储字节数
 * @param range 局部变量声明对应的源码范围
 */
public record IrLocal(
        String name,
        String sourceName,
        IrType type,
        int elementCount,
        int sizeBytes,
        SourceRange range
) {
    /**
     * 创建 IR 局部变量槽位。
     *
     * @param name IR 内唯一局部变量名
     * @param sourceName 源码中的变量名
     * @param type 局部变量类型
     * @param elementCount 局部变量元素数量；标量为 1，数组为数组长度
     * @param sizeBytes 局部变量存储字节数
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
        if (elementCount <= 0) {
            throw new IllegalArgumentException("elementCount must be positive");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }

    /**
     * 创建局部变量。
     *
     * @param name IR 内唯一局部变量名
     * @param sourceName 源码中的变量名
     * @param type 局部变量类型
     * @param elementCount 局部变量元素数量；标量为 1，数组为数组长度
     * @param range 局部变量声明对应的源码范围
     */
    public IrLocal(String name, String sourceName, IrType type, int elementCount, SourceRange range) {
        this(name, sourceName, type, elementCount, defaultSizeBytes(type, elementCount), range);
    }

    /**
     * 创建标量局部变量。
     *
     * @param name IR 内唯一局部变量名
     * @param sourceName 源码中的变量名
     * @param type 局部变量类型
     * @param range 局部变量声明对应的源码范围
     */
    public IrLocal(String name, String sourceName, IrType type, SourceRange range) {
        this(name, sourceName, type, 1, range);
    }

    private static int defaultSizeBytes(IrType type, int elementCount) {
        if (type == IrType.INT_ARRAY) {
            return TypeLayout.sizeOf(minic.compiler.type.MiniType.INT.arrayOf(elementCount));
        }
        if (type == IrType.POINTER) {
            return TypeLayout.POINTER_SIZE_BYTES;
        }
        return TypeLayout.sizeOf(minic.compiler.type.MiniType.INT);
    }
}
