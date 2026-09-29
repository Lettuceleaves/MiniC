package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.IrTemporary;
import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * 直接与间接函数调用 IR 指令。
 */
public sealed interface CallInstruction extends IrInstruction {
    record IrCallInstruction(
            IrTemporary result,
            String calleeName,
            List<IrValue> arguments,
            boolean variadic,
            SourceRange range
    ) implements CallInstruction {
        public IrCallInstruction {
            Objects.requireNonNull(calleeName, "calleeName");
            Objects.requireNonNull(arguments, "arguments");
            Objects.requireNonNull(range, "range");
            if (calleeName.isBlank()) {
                throw new IllegalArgumentException("calleeName must not be blank");
            }
            arguments = List.copyOf(arguments);
        }

        public java.util.Optional<IrTemporary> resultOptional() {
            return java.util.Optional.ofNullable(result);
        }
    }

    record IrIndirectCallInstruction(
            IrTemporary result,
            IrValue calleeAddress,
            List<IrValue> arguments,
            boolean variadic,
            SourceRange range
    ) implements CallInstruction {
        public IrIndirectCallInstruction {
            Objects.requireNonNull(calleeAddress, "calleeAddress");
            Objects.requireNonNull(arguments, "arguments");
            Objects.requireNonNull(range, "range");
            arguments = List.copyOf(arguments);
        }

        public java.util.Optional<IrTemporary> resultOptional() {
            return java.util.Optional.ofNullable(result);
        }
    }
}
