package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 分支、跳转、返回与控制流检查相关的 IR 指令。
 */
public sealed interface ControlInstruction extends IrInstruction {
    /** 仅插入调试用 IR 副本；停止发生在后续指令执行之前。 */
    record IrTrapInstruction(TrapKind kind, SourceRange range) implements ControlInstruction {
        public IrTrapInstruction {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(range, "range");
        }
    }

    enum TrapKind { LINE, CALL }

    record IrBranchInstruction(IrValue condition, String thenLabel, String elseLabel, SourceRange range)
            implements ControlInstruction {
        public IrBranchInstruction {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(thenLabel, "thenLabel");
            Objects.requireNonNull(elseLabel, "elseLabel");
            Objects.requireNonNull(range, "range");
            if (thenLabel.isBlank() || elseLabel.isBlank()) {
                throw new IllegalArgumentException("branch labels must not be blank");
            }
        }
    }

    record IrJumpInstruction(String targetLabel, SourceRange range) implements ControlInstruction {
        public IrJumpInstruction {
            Objects.requireNonNull(targetLabel, "targetLabel");
            Objects.requireNonNull(range, "range");
            if (targetLabel.isBlank()) {
                throw new IllegalArgumentException("targetLabel must not be blank");
            }
        }
    }

    record IrReturnInstruction(IrValue value, SourceRange range) implements ControlInstruction {
        public IrReturnInstruction {
            Objects.requireNonNull(range, "range");
        }

        public java.util.Optional<IrValue> valueOptional() {
            return java.util.Optional.ofNullable(value);
        }
    }

    record IrCheckNonZeroInstruction(IrValue value, SourceRange range) implements ControlInstruction {
        public IrCheckNonZeroInstruction {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
        }
    }
}
