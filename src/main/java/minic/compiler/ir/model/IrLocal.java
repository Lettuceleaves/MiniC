package minic.compiler.ir.model;

import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 局部存储槽位。
 *
 * <p>{@code declaredType} 始终保留完整递归源码类型；{@code type} 只描述一条
 * 机器值指令如何访问该槽位。数组和结构体本身不再伪装成 IR 标量，它们在表达式中
 * 以 {@link IrType#POINTER} 地址传递。</p>
 *
 * @param name IR 内唯一局部变量名
 * @param sourceName 源码中的变量名
 * @param declaredType 完整源码声明类型
 * @param type 标量访问类型；聚合存储为 POINTER（仅表示其地址）
 * @param sizeBytes 完整存储大小
 * @param alignmentBytes 存储对齐
 * @param range 声明源码范围
 */
public record IrLocal(
        String name,
        String sourceName,
        MiniType declaredType,
        IrType type,
        int sizeBytes,
        int alignmentBytes,
        SourceRange range
) {
    public IrLocal {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(declaredType, "declaredType");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(range, "range");
        if (name.isBlank() || sourceName.isBlank()) {
            throw new IllegalArgumentException("local names must not be blank");
        }
        if (sizeBytes <= 0 || alignmentBytes <= 0) {
            throw new IllegalArgumentException("local layout must be positive");
        }
    }

    /** 聚合对象只能通过地址、字段地址、元素地址和内存复制指令访问。 */
    public boolean aggregate() {
        return declaredType.isArray() || declaredType.isStruct();
    }
}
