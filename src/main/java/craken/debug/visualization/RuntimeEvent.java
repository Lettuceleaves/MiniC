package craken.debug.visualization;

import java.util.Objects;

/** Successful VM memory operations. Addresses are qualified by a session allocation identity. */
public sealed interface RuntimeEvent {
    long sequence();

    record MemoryRange(long allocationId, long address, int size) {
        public MemoryRange {
            if (allocationId < 1 || address < 0 || size < 0) throw new IllegalArgumentException("Invalid memory range");
        }
    }

    record Allocated(long sequence, MemoryRange range, String storage, String label) implements RuntimeEvent {
        public Allocated {
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(label, "label");
        }
    }

    enum Access { READ, WRITE, INITIALIZED, UNINITIALIZED }

    record Accessed(long sequence, MemoryRange range, Access access, String valueType) implements RuntimeEvent {
        public Accessed {
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(access, "access");
            Objects.requireNonNull(valueType, "valueType");
        }
    }

    record Copied(long sequence, MemoryRange source, MemoryRange destination) implements RuntimeEvent {
        public Copied {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
            if (source.size() != destination.size()) throw new IllegalArgumentException("Copy sizes differ");
        }
    }

    record Released(long sequence, MemoryRange range) implements RuntimeEvent {
        public Released { Objects.requireNonNull(range, "range"); }
    }

    /** A null previous range denotes realloc(NULL, size); an unchanged range denotes a no-op resize. */
    record Reallocated(long sequence, MemoryRange previous, MemoryRange replacement, int copiedBytes)
            implements RuntimeEvent {
        public Reallocated {
            Objects.requireNonNull(replacement, "replacement");
            if (copiedBytes < 0 || copiedBytes > replacement.size()
                    || previous != null && copiedBytes > previous.size()) throw new IllegalArgumentException("Invalid copied size");
        }
    }
}
