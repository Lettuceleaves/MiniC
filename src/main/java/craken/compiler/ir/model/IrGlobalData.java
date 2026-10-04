package craken.compiler.ir.model;

import craken.SourceRange;
import craken.compiler.type.CrakenType;

import java.util.Arrays;
import java.util.Objects;

/**
 * A writable, statically initialized file-scope object.
 *
 * @param label source-level global name used as the static symbol
 * @param declaredType full recursive source type
 * @param bytes little-endian writable data image
 * @param alignment storage alignment in bytes
 * @param addresses resolved static relocations sorted by offset
 * @param range declaration source range; identifies the definition in the debugger
 */
public record IrGlobalData(String label, CrakenType declaredType, byte[] bytes, int alignment,
                           java.util.List<Address> addresses, SourceRange range) {
    public enum AddressKind { OBJECT, FUNCTION, STRING }
    /** An absolute target address plus a byte offset, resolved after all static objects exist. */
    public record Address(int offset,String symbol,long addend,AddressKind kind) {
        public Address {
            Objects.requireNonNull(symbol);Objects.requireNonNull(kind);
            if(offset<0||symbol.isBlank())throw new IllegalArgumentException("Invalid static address relocation");
        }
    }
    public IrGlobalData {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(declaredType, "declaredType");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(range, "range");
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
