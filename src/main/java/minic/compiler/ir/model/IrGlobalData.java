package minic.compiler.ir.model;

import minic.compiler.type.MiniType;

import java.util.Arrays;
import java.util.Objects;

/** A writable, statically initialized file-scope object. */
public record IrGlobalData(String label, MiniType declaredType, byte[] bytes, int alignment) {
    public IrGlobalData {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(declaredType, "declaredType");
        Objects.requireNonNull(bytes, "bytes");
        if (label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (bytes.length == 0) throw new IllegalArgumentException("global object must occupy storage");
        if (alignment <= 0) throw new IllegalArgumentException("alignment must be positive");
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
}
