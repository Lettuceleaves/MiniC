package minic.compiler.ir.model;

import minic.compiler.type.MiniType;

import java.util.Arrays;
import java.util.Objects;

/** A writable, statically initialized file-scope object. */
public record IrGlobalData(String label, MiniType declaredType, byte[] bytes, int alignment,
                           java.util.List<Address> addresses) {
    public enum AddressKind { OBJECT, FUNCTION, STRING }
    /** An absolute target address plus a byte offset, resolved after all static objects exist. */
    public record Address(int offset,String symbol,long addend,AddressKind kind) {
        public Address {
            Objects.requireNonNull(symbol);Objects.requireNonNull(kind);
            if(offset<0||symbol.isBlank())throw new IllegalArgumentException("Invalid static address relocation");
        }
    }
    public IrGlobalData(String label,MiniType declaredType,byte[] bytes,int alignment) {
        this(label,declaredType,bytes,alignment,java.util.List.of());
    }
    public IrGlobalData {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(declaredType, "declaredType");
        Objects.requireNonNull(bytes, "bytes");
        if (label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (bytes.length == 0) throw new IllegalArgumentException("global object must occupy storage");
        if (alignment <= 0) throw new IllegalArgumentException("alignment must be positive");
        bytes = Arrays.copyOf(bytes, bytes.length);
        addresses=addresses.stream().sorted(java.util.Comparator.comparingInt(Address::offset)).toList();
        int end=0;
        for(Address address:addresses) {
            if(address.offset()<end||address.offset()>bytes.length-Long.BYTES)
                throw new IllegalArgumentException("Static address relocation overlaps or exceeds object storage");
            end=address.offset()+Long.BYTES;
        }
    }

    @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
}
