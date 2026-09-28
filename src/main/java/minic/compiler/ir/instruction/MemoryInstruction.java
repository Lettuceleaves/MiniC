package minic.compiler.ir.instruction;

import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.IrTemporary;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 内存、地址与局部变量相关的 IR 指令。
 */
public sealed interface MemoryInstruction extends IrInstruction {
    record IrDeclareLocalInstruction(IrLocal local, SourceRange range) implements MemoryInstruction {
        public IrDeclareLocalInstruction {
            Objects.requireNonNull(local, "local");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrCheckInitializedInstruction(IrLocal local, SourceRange range) implements MemoryInstruction {
        public IrCheckInitializedInstruction {
            Objects.requireNonNull(local, "local");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrAddressOfLocalInstruction(IrTemporary result, IrLocal local, SourceRange range)
            implements MemoryInstruction {
        public IrAddressOfLocalInstruction {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(local, "local");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrLoadLocalInstruction(IrTemporary result, IrLocal local, SourceRange range)
            implements MemoryInstruction {
        public IrLoadLocalInstruction {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(local, "local");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrStoreLocalInstruction(IrLocal local, IrValue value, SourceRange range)
            implements MemoryInstruction {
        public IrStoreLocalInstruction {
            Objects.requireNonNull(local, "local");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrLoadPointerInstruction(IrTemporary result, IrValue address, SourceRange range)
            implements MemoryInstruction {
        public IrLoadPointerInstruction {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrStorePointerInstruction(IrValue address, IrValue value, SourceRange range)
            implements MemoryInstruction {
        public IrStorePointerInstruction {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
        }
    }

    record IrElementAddressInstruction(
            IrTemporary result,
            IrValue baseAddress,
            IrValue index,
            int elementSizeBytes,
            SourceRange range
    ) implements MemoryInstruction {
        public IrElementAddressInstruction {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(baseAddress, "baseAddress");
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(range, "range");
            if (elementSizeBytes <= 0) {
                throw new IllegalArgumentException("elementSizeBytes must be positive");
            }
        }

        public IrElementAddressInstruction(
                IrTemporary result,
                IrValue baseAddress,
                IrValue index,
                SourceRange range
        ) {
            this(result, baseAddress, index, 4, range);
        }
    }

    record IrFieldAddressInstruction(
            IrTemporary result,
            IrValue baseAddress,
            String ownerStructName,
            String fieldName,
            int declaredFieldIndex,
            int pointerFieldIndex,
            int offset,
            String fieldType,
            SourceRange range
    ) implements MemoryInstruction {
        public IrFieldAddressInstruction(
                IrTemporary result,
                IrValue baseAddress,
                String fieldName,
                int offset,
                SourceRange range
        ) {
            this(result, baseAddress, "", fieldName, -1, -1, offset, "", range);
        }

        public IrFieldAddressInstruction {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(baseAddress, "baseAddress");
            Objects.requireNonNull(ownerStructName, "ownerStructName");
            Objects.requireNonNull(fieldName, "fieldName");
            Objects.requireNonNull(fieldType, "fieldType");
            Objects.requireNonNull(range, "range");
            if (fieldName.isBlank()) {
                throw new IllegalArgumentException("fieldName must not be blank");
            }
            if (declaredFieldIndex < -1) {
                throw new IllegalArgumentException("declaredFieldIndex must not be less than -1");
            }
            if (pointerFieldIndex < -1) {
                throw new IllegalArgumentException("pointerFieldIndex must not be less than -1");
            }
            if (offset < 0) {
                throw new IllegalArgumentException("offset must not be negative");
            }
        }
    }

    record IrMemCopyInstruction(IrValue destination, IrValue source, int sizeBytes, SourceRange range)
            implements MemoryInstruction {
        public IrMemCopyInstruction {
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(range, "range");
            if (sizeBytes <= 0) {
                throw new IllegalArgumentException("sizeBytes must be positive: " + sizeBytes);
            }
        }
    }
}
