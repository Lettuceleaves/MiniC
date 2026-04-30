package minic.compiler.type;

import java.util.Objects;

/**
 * MiniC 前端类型。
 */
public sealed interface MiniType permits MiniType.IntType, MiniType.PointerType {
    /**
     * MiniC int 类型。
     */
    MiniType INT = new IntType();

    /**
     * 返回指向当前类型的指针类型。
     *
     * @return 指针类型
     */
    default MiniType pointerTo() {
        return new PointerType(this);
    }

    /**
     * MiniC int 类型。
     */
    record IntType() implements MiniType {
        @Override
        public String toString() {
            return "int";
        }
    }

    /**
     * MiniC 指针类型。
     *
     * @param pointee 指向的元素类型
     */
    record PointerType(MiniType pointee) implements MiniType {
        /**
         * 创建指针类型。
         *
         * @param pointee 指向的元素类型
         */
        public PointerType {
            Objects.requireNonNull(pointee, "pointee");
        }

        @Override
        public String toString() {
            return pointee + "*";
        }
    }
}
