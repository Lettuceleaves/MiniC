package minic.compiler.type;

import java.util.Objects;

/**
 * MiniC 类型大小和对齐规则。
 */
public final class TypeLayout {
    /**
     * 目标平台指针字节数。
     */
    public static final int POINTER_SIZE_BYTES = 8;

    /**
     * 目标平台指针对齐字节数。
     */
    public static final int POINTER_ALIGNMENT_BYTES = 8;

    private TypeLayout() {
    }

    /**
     * 返回类型占用字节数；命名结构体必须由调用方提供布局。
     *
     * @param type 类型
     * @return 字节数
     */
    public static int sizeOf(MiniType type) {
        Objects.requireNonNull(type, "type");
        if (type instanceof MiniType.ScalarType scalarType) {
            return scalarType.kind().sizeBytes();
        }
        if (type.isPointer()) {
            return POINTER_SIZE_BYTES;
        }
        if (type.isArray()) {
            return sizeOf(type.elementType()) * type.arrayLength();
        }
        if (type.isNullPointer()) {
            return POINTER_SIZE_BYTES;
        }
        throw new IllegalArgumentException("type requires contextual layout: " + type);
    }

    /**
     * 返回类型对齐字节数；命名结构体必须由调用方提供布局。
     *
     * @param type 类型
     * @return 对齐字节数
     */
    public static int alignmentOf(MiniType type) {
        Objects.requireNonNull(type, "type");
        if (type instanceof MiniType.ScalarType scalarType) {
            return scalarType.kind().alignmentBytes();
        }
        if (type.isPointer()) {
            return POINTER_ALIGNMENT_BYTES;
        }
        if (type.isArray()) {
            return alignmentOf(type.elementType());
        }
        if (type.isNullPointer()) {
            return POINTER_ALIGNMENT_BYTES;
        }
        throw new IllegalArgumentException("type requires contextual layout: " + type);
    }

    /**
     * 判断类型是否具备无上下文固定布局。
     *
     * @param type 类型
     * @return 非结构体类型返回 {@code true}
     */
    public static boolean hasFixedLayout(MiniType type) {
        Objects.requireNonNull(type, "type");
        if (type.isStruct()) {
            return false;
        }
        if (type.isArray()) {
            return hasFixedLayout(type.elementType());
        }
        return true;
    }
}
