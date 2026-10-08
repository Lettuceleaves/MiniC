package craken.compiler.ir.instruction;

import craken.compiler.ir.value.IrValue;
import craken.SourceRange;

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

    /**
     * 仅存在于调试 IR 副本：在访问点记录一次被选中变量的创建/读取/写入/离开作用域。
     * 该指令不参与本机代码生成；编译器与优化器只在未插桩的 IR 上工作。
     */
    record IrCaptureInstruction(Kind kind, SourceRange definition, SourceRange range, String addressTemporary)
            implements ControlInstruction {
        public enum Kind { CREATE, READ, WRITE, REMOVE }
        /** 变量自身的访问（基址事件）；元素访问用带地址临时量的重载。 */
        public IrCaptureInstruction(Kind kind, SourceRange definition, SourceRange range) {
            this(kind, definition, range, "");
        }
        public IrCaptureInstruction {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(range, "range");
            addressTemporary = Objects.requireNonNullElse(addressTemporary, "");
        }
    }

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
