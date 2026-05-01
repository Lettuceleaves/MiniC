package minic.compiler.type;

import java.util.List;
import java.util.Objects;

/**
 * MiniC 前端类型。
 */
public sealed interface MiniType permits
        MiniType.IntType,
        MiniType.PointerType,
        MiniType.ArrayType,
        MiniType.StructType,
        MiniType.FunctionType {
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
     * 返回当前类型的固定长度数组类型。
     *
     * @param length 数组长度
     * @return 数组类型
     */
    default MiniType arrayOf(int length) {
        return new ArrayType(this, length);
    }

    /**
     * 创建命名结构体类型。
     *
     * @param name 结构体名
     * @return 结构体类型
     */
    static MiniType struct(String name) {
        return new StructType(name);
    }

    /**
     * 创建函数签名类型。
     *
     * @param returnType 返回类型
     * @param parameterTypes 参数类型列表
     * @return 函数签名类型
     */
    static MiniType function(MiniType returnType, List<MiniType> parameterTypes) {
        return new FunctionType(returnType, parameterTypes);
    }

    /**
     * 判断当前类型是否为指针。
     *
     * @return 指针类型返回 {@code true}
     */
    default boolean isPointer() {
        return this instanceof PointerType;
    }

    /**
     * 判断当前类型是否为数组。
     *
     * @return 数组类型返回 {@code true}
     */
    default boolean isArray() {
        return this instanceof ArrayType;
    }

    /**
     * 判断当前类型是否为结构体。
     *
     * @return 结构体类型返回 {@code true}
     */
    default boolean isStruct() {
        return this instanceof StructType;
    }

    /**
     * 判断当前类型是否为函数签名。
     *
     * @return 函数签名类型返回 {@code true}
     */
    default boolean isFunction() {
        return this instanceof FunctionType;
    }

    /**
     * 返回当前类型的指向元素类型。
     *
     * @return 指向元素类型
     * @throws IllegalStateException 当前类型不是指针时抛出
     */
    default MiniType pointee() {
        if (this instanceof PointerType pointerType) {
            return pointerType.pointee();
        }
        throw new IllegalStateException("type is not a pointer: " + this);
    }

    /**
     * 返回数组元素类型。
     *
     * @return 数组元素类型
     * @throws IllegalStateException 当前类型不是数组时抛出
     */
    default MiniType elementType() {
        if (this instanceof ArrayType arrayType) {
            return arrayType.elementType();
        }
        throw new IllegalStateException("type is not an array: " + this);
    }

    /**
     * 返回数组长度。
     *
     * @return 数组长度
     * @throws IllegalStateException 当前类型不是数组时抛出
     */
    default int arrayLength() {
        if (this instanceof ArrayType arrayType) {
            return arrayType.length();
        }
        throw new IllegalStateException("type is not an array: " + this);
    }

    /**
     * 返回函数返回类型。
     *
     * @return 函数返回类型
     * @throws IllegalStateException 当前类型不是函数签名时抛出
     */
    default MiniType returnType() {
        if (this instanceof FunctionType functionType) {
            return functionType.returnType();
        }
        throw new IllegalStateException("type is not a function: " + this);
    }

    /**
     * 返回函数参数类型列表。
     *
     * @return 函数参数类型列表
     * @throws IllegalStateException 当前类型不是函数签名时抛出
     */
    default List<MiniType> parameterTypes() {
        if (this instanceof FunctionType functionType) {
            return functionType.parameterTypes();
        }
        throw new IllegalStateException("type is not a function: " + this);
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

    /**
     * MiniC 固定长度数组类型。
     *
     * @param elementType 元素类型
     * @param length 数组长度
     */
    record ArrayType(MiniType elementType, int length) implements MiniType {
        /**
         * 创建固定长度数组类型。
         *
         * @param elementType 元素类型
         * @param length 数组长度
         */
        public ArrayType {
            Objects.requireNonNull(elementType, "elementType");
            if (length <= 0) {
                throw new IllegalArgumentException("length must be positive");
            }
        }

        @Override
        public String toString() {
            return elementType + "[" + length + "]";
        }
    }

    /**
     * MiniC 命名结构体类型。
     *
     * @param name 结构体名
     */
    record StructType(String name) implements MiniType {
        /**
         * 创建命名结构体类型。
         *
         * @param name 结构体名
         */
        public StructType {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }

        @Override
        public String toString() {
            return "struct " + name;
        }
    }

    /**
     * MiniC 函数签名类型。
     *
     * @param returnType 返回类型
     * @param parameterTypes 参数类型列表
     */
    record FunctionType(MiniType returnType, List<MiniType> parameterTypes) implements MiniType {
        /**
         * 创建函数签名类型。
         *
         * @param returnType 返回类型
         * @param parameterTypes 参数类型列表
         */
        public FunctionType {
            Objects.requireNonNull(returnType, "returnType");
            Objects.requireNonNull(parameterTypes, "parameterTypes");
            parameterTypes = List.copyOf(parameterTypes);
        }

        @Override
        public String toString() {
            return returnType + " (" + String.join(", ", parameterTypes.stream()
                    .map(Object::toString)
                    .toList()) + ")";
        }
    }
}
