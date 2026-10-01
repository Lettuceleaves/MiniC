package minic.compiler.obj.machine;

import java.util.Arrays;
import java.util.Objects;

/**
 * section 中的原始数据。
 *
 * @param bytes 数据字节
 * @param alignment 起始对齐
 */
public record MachineData(byte[] bytes, int alignment,java.util.List<Address> addresses) implements MachineItem {
    public record Address(int offset,String symbol,long addend) {
        public Address { Objects.requireNonNull(symbol);if(offset<0||symbol.isBlank())throw new IllegalArgumentException("Invalid data address"); }
    }
    public MachineData(byte[] bytes,int alignment) {this(bytes,alignment,java.util.List.of());}
    public MachineData {
        Objects.requireNonNull(bytes, "bytes");
        if (alignment <= 0 || Integer.bitCount(alignment) != 1) {
            throw new IllegalArgumentException("alignment must be a positive power of two");
        }
        bytes = Arrays.copyOf(bytes, bytes.length);
        addresses=addresses.stream().sorted(java.util.Comparator.comparingInt(Address::offset)).toList();
        int end=0;
        for(Address address:addresses) {
            if(address.offset()<end||address.offset()>bytes.length-Long.BYTES)throw new IllegalArgumentException("Data relocation exceeds or overlaps storage");
            end=address.offset()+Long.BYTES;
        }
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
