package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.IrTemporary;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 运算、转换与值选择相关的 IR 指令。
 */
public sealed interface ComputeInstruction extends IrInstruction {
    record IrBinaryInstruction(
            IrTemporary result,
            IrBinaryOperator operator,
            IrValue left,
            IrValue right,
            SourceRange range
    ) implements ComputeInstruction {
        public IrBinaryInstruction {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrUnaryInstruction(
            IrTemporary result,
            IrUnaryOperator operator,
            IrValue operand,
            SourceRange range
    ) implements ComputeInstruction {
        public IrUnaryInstruction {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(operand, "operand");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrCastInstruction(IrTemporary result, IrValue value, SourceRange range)
            implements ComputeInstruction {
        public IrCastInstruction {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrMoveInstruction(IrTemporary result, IrValue value, SourceRange range)
            implements ComputeInstruction {
        public IrMoveInstruction {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrSelectInstruction(
            IrTemporary result,
            IrValue condition,
            IrValue thenValue,
            IrValue elseValue,
            SourceRange range
    ) implements ComputeInstruction {
        public IrSelectInstruction {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(thenValue, "thenValue");
            Objects.requireNonNull(elseValue, "elseValue");
            Objects.requireNonNull(range, "range");
        }
    }

    enum IrBinaryOperator {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        MODULO,
        BITWISE_AND,
        BITWISE_OR,
        BITWISE_XOR,
        SHIFT_LEFT,
        SHIFT_RIGHT,
        LOGICAL_AND,
        LOGICAL_OR,
        EQUAL,
        NOT_EQUAL,
        LESS_THAN,
        LESS_EQUAL,
        GREATER_THAN,
        GREATER_EQUAL
    }

    enum IrUnaryOperator {
        LOGICAL_NOT,
        BITWISE_NOT,
        NEGATE
    }
}
