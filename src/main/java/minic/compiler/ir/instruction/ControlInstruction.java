package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 分支、跳转、返回与控制流检查相关的 IR 指令。
 */
public sealed interface ControlInstruction extends IrInstruction {
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
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrCheckNonZeroInstruction(IrValue value, SourceRange range) implements ControlInstruction {
        public IrCheckNonZeroInstruction {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
        }
    }
}
