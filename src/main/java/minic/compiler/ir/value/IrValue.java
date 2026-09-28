package minic.compiler.ir.value;

import minic.compiler.ir.model.IrType;

import java.util.Objects;

/**
 * IR 值的基接口。
 */
public sealed interface IrValue {
    /**
     * 返回该值的类型。
     *
     * @return IR 值类型
     */
    IrType type();

    record IrConstant(long value, IrType type) implements IrValue {
        public IrConstant {
            Objects.requireNonNull(type, "type");
            if (!type.isIntegerScalar() && type != IrType.POINTER) {
                throw new IllegalArgumentException("constant type must be integer scalar or pointer");
            }
        }

        public IrConstant(int value) {
            this(value, IrType.INT);
        }

        @Override
        public String toString() {
            return "IrConstant[value=" + value + ", type=" + type + "]";
        }
    }

    record IrFloatConstant(double value, IrType type) implements IrValue {
        public IrFloatConstant {
            Objects.requireNonNull(type, "type");
            if (!type.isFloatingScalar()) {
                throw new IllegalArgumentException("floating constant type must be float or double");
            }
        }

        public IrFloatConstant(float value) {
            this(value, IrType.FLOAT);
        }
    }

    record IrFunctionAddress(String functionName) implements IrValue {
        public IrFunctionAddress {
            Objects.requireNonNull(functionName, "functionName");
            if (functionName.isBlank()) {
                throw new IllegalArgumentException("functionName must not be blank");
            }
        }

        @Override
        public IrType type() {
            return IrType.POINTER;
        }
    }

    record IrParameterRef(String name, IrType type) implements IrValue {
        public IrParameterRef {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }

    record IrStringLiteral(String label) implements IrValue {
        public IrStringLiteral {
            Objects.requireNonNull(label, "label");
            if (label.isBlank()) {
                throw new IllegalArgumentException("label must not be blank");
            }
        }

        @Override
        public IrType type() {
            return IrType.POINTER;
        }
    }

    record IrTemporary(String name, IrType type) implements IrValue {
        public IrTemporary {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }
}
