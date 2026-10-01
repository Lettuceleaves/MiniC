package minic.compiler.type;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.EnumSet;

/**
 * MiniC 前端类型。
 */
public sealed interface MiniType permits
        MiniType.NamedType,
        MiniType.CombinationType,
        MiniType.QualifiedType,
        MiniType.NullPointerType,
        MiniType.VoidType {
    /**
     * 具有源码名称的叶子节点。标量名和 {@code struct Name} 都在这里终止递归。
     */
    sealed interface NamedType extends MiniType permits ScalarType, StructType, VaListType {
    }

    /**
     * 组合节点。每个节点只描述一层组合，因此指针、数组和函数可以任意递归嵌套。
     */
    sealed interface CombinationType extends MiniType permits PointerType, ArrayType, FunctionType, ReferenceType {
    }
    /**
     * MiniC bool 类型。
     */
    MiniType BOOL = new ScalarType(ScalarKind.BOOL);

    /**
     * MiniC 有符号 char 类型。
     */
    MiniType CHAR = new ScalarType(ScalarKind.CHAR);

    MiniType SIGNED_CHAR = new ScalarType(ScalarKind.SIGNED_CHAR);

    MiniType UNSIGNED_CHAR = new ScalarType(ScalarKind.UNSIGNED_CHAR);

    MiniType SHORT = new ScalarType(ScalarKind.SHORT);

    MiniType UNSIGNED_SHORT = new ScalarType(ScalarKind.UNSIGNED_SHORT);

    /**
     * MiniC int 类型。
     */
    MiniType INT = new ScalarType(ScalarKind.INT);

    MiniType UNSIGNED_INT = new ScalarType(ScalarKind.UNSIGNED_INT);

    /**
     * MiniC long 类型。
     */
    MiniType LONG = new ScalarType(ScalarKind.LONG);

    MiniType UNSIGNED_LONG = new ScalarType(ScalarKind.UNSIGNED_LONG);

    MiniType LONG_LONG = new ScalarType(ScalarKind.LONG_LONG);

    MiniType UNSIGNED_LONG_LONG = new ScalarType(ScalarKind.UNSIGNED_LONG_LONG);

    /**
     * MiniC float 类型。
     */
    MiniType FLOAT = new ScalarType(ScalarKind.FLOAT);

    /**
     * MiniC double 类型。
     */
    MiniType DOUBLE = new ScalarType(ScalarKind.DOUBLE);

    /**
     * 无值类型，只能用于函数返回类型或作为指针的被指向类型。
     */
    MiniType VOID = new VoidType();

    /**
     * NULL 空指针常量类型。
     */
    MiniType NULL = new NullPointerType();

    /** Opaque Windows x64 variadic cursor exposed by {@code stdarg.mh}. */
    MiniType VA_LIST = new VaListType();

    /**
     * 返回指向当前类型的指针类型。
     *
     * @return 指针类型
     */
    default MiniType pointerTo() {
        return new PointerType(this);
    }

    /** C++ source lvalue reference; normalization must remove this type before core lowering. */
    default MiniType referenceTo() {
        return isReference() ? unqualified() : new ReferenceType(this);
    }

    default boolean isReference() {
        return unqualified() instanceof ReferenceType;
    }

    default MiniType referent() {
        if (unqualified() instanceof ReferenceType reference) return reference.referent();
        throw new IllegalStateException("type is not a reference: " + this);
    }

    /** Includes references nested in declarators and callable signatures. */
    default boolean containsReference() {
        return switch (unqualified()) {
            case ReferenceType ignored -> true;
            case PointerType pointer -> pointer.pointee().containsReference();
            case ArrayType array -> array.elementType().containsReference();
            case FunctionType function -> function.returnType().containsReference()
                    || function.parameterTypes().stream().anyMatch(MiniType::containsReference);
            default -> false;
        };
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
        return new FunctionType(returnType, parameterTypes, false);
    }

    /**
     * 创建函数签名类型。
     *
     * @param returnType 返回类型
     * @param parameterTypes 固定参数类型列表
     * @param variadic 是否接受可变参数
     * @return 函数签名类型
     */
    static MiniType function(MiniType returnType, List<MiniType> parameterTypes, boolean variadic) {
        return new FunctionType(returnType, parameterTypes, variadic);
    }

    /** Apply C type qualifiers to exactly this type layer. */
    static MiniType qualified(MiniType type, Set<TypeQualifier> qualifiers) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(qualifiers, "qualifiers");
        if (qualifiers.isEmpty()) {
            return type;
        }
        // CV applied to a typedef naming a reference does not qualify its referent.
        if (type.isReference()) return type.unqualified();
        if (type instanceof QualifiedType qualifiedType) {
            EnumSet<TypeQualifier> merged = EnumSet.copyOf(qualifiedType.qualifiers());
            merged.addAll(qualifiers);
            return new QualifiedType(qualifiedType.baseType(), merged);
        }
        return new QualifiedType(type, qualifiers);
    }

    /** Return this layer without its qualifiers. */
    default MiniType unqualified() {
        return this instanceof QualifiedType qualifiedType ? qualifiedType.baseType() : this;
    }

    /** Return qualifiers attached to exactly this type layer. */
    default Set<TypeQualifier> qualifiers() {
        return this instanceof QualifiedType qualifiedType ? qualifiedType.qualifiers() : Set.of();
    }

    default boolean isConstQualified() {
        return qualifiers().contains(TypeQualifier.CONST);
    }

    default boolean isVolatileQualified() {
        return qualifiers().contains(TypeQualifier.VOLATILE);
    }

    default boolean isRestrictQualified() {
        return qualifiers().contains(TypeQualifier.RESTRICT);
    }

    /**
     * 判断当前类型是否为指针。
     *
     * @return 指针类型返回 {@code true}
     */
    default boolean isPointer() {
        return unqualified() instanceof PointerType;
    }

    /**
     * 判断当前类型是否为数组。
     *
     * @return 数组类型返回 {@code true}
     */
    default boolean isArray() {
        return unqualified() instanceof ArrayType;
    }

    /**
     * 判断当前类型是否为结构体。
     *
     * @return 结构体类型返回 {@code true}
     */
    default boolean isStruct() {
        return unqualified() instanceof StructType;
    }

    /**
     * 判断当前类型是否为函数签名。
     *
     * @return 函数签名类型返回 {@code true}
     */
    default boolean isFunction() {
        return unqualified() instanceof FunctionType;
    }

    /**
     * 判断当前类型是否为基础标量。
     *
     * @return 基础标量返回 {@code true}
     */
    default boolean isScalar() {
        return unqualified() instanceof ScalarType;
    }

    /**
     * 判断当前类型是否为整数标量。
     *
     * @return bool、char、int、long 返回 {@code true}
     */
    default boolean isIntegerScalar() {
        return unqualified() instanceof ScalarType scalarType && scalarType.kind().integer();
    }

    /** @return 当前类型是否为有符号整数标量。 */
    default boolean isSignedIntegerScalar() {
        return unqualified() instanceof ScalarType scalarType
                && scalarType.kind().integer()
                && scalarType.kind().signed();
    }

    /** @return 当前类型是否为无符号整数标量（bool 除外）。 */
    default boolean isUnsignedIntegerScalar() {
        return unqualified() instanceof ScalarType scalarType
                && scalarType.kind().integer()
                && !scalarType.kind().signed()
                && scalarType.kind() != ScalarKind.BOOL;
    }

    /**
     * 判断当前类型是否为浮点标量。
     *
     * @return float、double 返回 {@code true}
     */
    default boolean isFloatingScalar() {
        return unqualified() instanceof ScalarType scalarType && scalarType.kind().floating();
    }

    /**
     * 判断当前类型是否为空指针常量类型。
     *
     * @return NULL 类型返回 {@code true}
     */
    default boolean isNullPointer() {
        return unqualified() instanceof NullPointerType;
    }

    /** @return 当前类型是否为 {@code void}。 */
    default boolean isVoid() {
        return unqualified() instanceof VoidType;
    }

    /** @return whether this is the opaque stdarg cursor type. */
    default boolean isVaList() {
        return unqualified() instanceof VaListType;
    }

    /**
     * 返回当前类型的指向元素类型。
     *
     * @return 指向元素类型
     * @throws IllegalStateException 当前类型不是指针时抛出
     */
    default MiniType pointee() {
        if (unqualified() instanceof PointerType pointerType) {
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
        if (unqualified() instanceof ArrayType arrayType) {
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
        if (unqualified() instanceof ArrayType arrayType) {
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
        if (unqualified() instanceof FunctionType functionType) {
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
        if (unqualified() instanceof FunctionType functionType) {
            return functionType.parameterTypes();
        }
        throw new IllegalStateException("type is not a function: " + this);
    }

    enum ScalarKind {
        BOOL("bool", 1, 1, false, true, false, 0),
        CHAR("char", 1, 1, true, true, false, 1),
        SIGNED_CHAR("signed char", 1, 1, true, true, false, 1),
        UNSIGNED_CHAR("unsigned char", 1, 1, false, true, false, 1),
        SHORT("short", 2, 2, true, true, false, 2),
        UNSIGNED_SHORT("unsigned short", 2, 2, false, true, false, 2),
        INT("int", 4, 4, true, true, false, 3),
        UNSIGNED_INT("unsigned int", 4, 4, false, true, false, 3),
        LONG("long", 4, 4, true, true, false, 4),
        UNSIGNED_LONG("unsigned long", 4, 4, false, true, false, 4),
        LONG_LONG("long long", 8, 8, true, true, false, 5),
        UNSIGNED_LONG_LONG("unsigned long long", 8, 8, false, true, false, 5),
        FLOAT("float", 4, 4, true, false, true, -1),
        DOUBLE("double", 8, 8, true, false, true, -1);

        private final String displayName;
        private final int sizeBytes;
        private final int alignmentBytes;
        private final boolean signed;
        private final boolean integer;
        private final boolean floating;
        private final int integerRank;

        ScalarKind(
                String displayName,
                int sizeBytes,
                int alignmentBytes,
                boolean signed,
                boolean integer,
                boolean floating,
                int integerRank
        ) {
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
            this.alignmentBytes = alignmentBytes;
            this.signed = signed;
            this.integer = integer;
            this.floating = floating;
            this.integerRank = integerRank;
        }

        public String displayName() {
            return displayName;
        }

        public int sizeBytes() {
            return sizeBytes;
        }

        public int alignmentBytes() {
            return alignmentBytes;
        }

        public boolean signed() {
            return signed;
        }

        public boolean integer() {
            return integer;
        }

        public boolean floating() {
            return floating;
        }

        /** C 整数转换等级；非整数类型返回 -1。 */
        public int integerRank() {
            return integerRank;
        }
    }

    enum TypeQualifier {
        CONST("const"),
        VOLATILE("volatile"),
        RESTRICT("restrict");

        private final String spelling;

        TypeQualifier(String spelling) {
            this.spelling = spelling;
        }

        public String spelling() {
            return spelling;
        }
    }

    /** Qualifiers wrap one precise type layer, so pointer and pointee qualifiers remain distinct. */
    record QualifiedType(MiniType baseType, Set<TypeQualifier> qualifiers) implements MiniType {
        public QualifiedType {
            Objects.requireNonNull(baseType, "baseType");
            Objects.requireNonNull(qualifiers, "qualifiers");
            if (baseType instanceof QualifiedType) {
                throw new IllegalArgumentException("qualified types must be flattened");
            }
            if (qualifiers.isEmpty()) {
                throw new IllegalArgumentException("qualified type requires at least one qualifier");
            }
            qualifiers = Set.copyOf(qualifiers);
        }

        @Override
        public String toString() {
            String prefix = String.join(" ", qualifiers.stream()
                    .map(TypeQualifier::spelling)
                    .sorted()
                    .toList());
            return prefix + " " + baseType;
        }
    }

    /**
     * MiniC 基础标量类型。
     *
     * @param kind 标量种类
     */
    record ScalarType(ScalarKind kind) implements NamedType {
        /**
         * 创建基础标量类型。
         *
         * @param kind 标量种类
         */
        public ScalarType {
            Objects.requireNonNull(kind, "kind");
        }

        @Override
        public String toString() {
            return kind.displayName();
        }
    }

    /**
     * MiniC NULL 空指针常量类型。
     */
    record NullPointerType() implements MiniType {
        @Override
        public String toString() {
            return "NULL";
        }
    }

    /** Compiler-owned va_list representation; its native representation is one pointer. */
    record VaListType() implements NamedType {
        @Override
        public String toString() {
            return "va_list";
        }
    }

    /** MiniC {@code void} 类型。 */
    record VoidType() implements MiniType {
        @Override
        public String toString() {
            return "void";
        }
    }

    /**
     * MiniC 指针类型。
     *
     * @param pointee 指向的元素类型
     */
    record PointerType(MiniType pointee) implements CombinationType {
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

    /** Source-only C++ lvalue reference. Alias composition collapses nested lvalue references. */
    record ReferenceType(MiniType referent) implements CombinationType {
        public ReferenceType {
            Objects.requireNonNull(referent, "referent");
            if (referent.unqualified() instanceof ReferenceType reference) referent = reference.referent();
        }

        @Override
        public String toString() {
            return referent + "&";
        }
    }

    /**
     * MiniC 固定长度数组类型。
     *
     * @param elementType 元素类型
     * @param length 数组长度
     */
    record ArrayType(MiniType elementType, int length) implements CombinationType {
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
    record StructType(String name) implements NamedType {
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
     * @param parameterTypes 固定参数类型列表
     * @param variadic 是否接受可变参数
     */
    record FunctionType(
            MiniType returnType,
            List<MiniType> parameterTypes,
            boolean variadic
    ) implements CombinationType {
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
            String parameters = String.join(", ", parameterTypes.stream()
                    .map(Object::toString)
                    .toList());
            if (variadic) {
                parameters = parameters.isEmpty() ? "..." : parameters + ", ...";
            }
            return returnType + " (" + parameters + ")";
        }
    }
}
