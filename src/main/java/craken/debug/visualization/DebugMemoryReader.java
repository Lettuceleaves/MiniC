package craken.debug.visualization;

import craken.debug.DebugRuntime.MemoryBlock;
import craken.debug.DebugRuntime.RuntimeState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Stream;

/** Reads an immutable stop snapshot; it cannot execute VM operations or publish runtime events. */
public final class DebugMemoryReader {
    public record Address(long allocationId, int offset) {
        public Address {
            if (allocationId <= 0 || offset < 0) throw new IllegalArgumentException("Invalid allocation identity or offset");
        }
    }

    public enum ScalarType {
        SIGNED8(1), UNSIGNED8(1), SIGNED16(2), UNSIGNED16(2), SIGNED32(4), UNSIGNED32(4),
        SIGNED64(8), UNSIGNED64(8), FLOAT32(4), FLOAT64(8), POINTER(8);
        private final int size;
        ScalarType(int size) { this.size = size; }
        public int sizeBytes() { return size; }
    }

    public enum Reason { UNKNOWN_IDENTITY, UNKNOWN_ADDRESS, OUT_OF_BOUNDS, UNINITIALIZED, MALFORMED_SNAPSHOT }

    public static final class MemoryReadException extends IllegalArgumentException {
        private final Reason reason;
        private MemoryReadException(Reason reason, String detail) { super(detail); this.reason = reason; }
        public Reason reason() { return reason; }
    }

    private record Image(MemoryBlock block, byte[] bytes, BitSet initialized) {}
    private final Map<Long, Image> identities;
    private final List<Image> images;
    private final NavigableMap<Long, Image> addresses;

    public DebugMemoryReader(RuntimeState state) {
        this(Stream.of(state.stackMemory(), state.heap(), state.libraryMemory(), state.globalMemory())
                .flatMap(Collection::stream).toList());
    }

    public DebugMemoryReader(List<MemoryBlock> blocks) {
        Map<Long, Image> byIdentity = new LinkedHashMap<>();
        List<Image> loaded = new ArrayList<>();
        for (MemoryBlock block : blocks) {
            try {
                if (block.size() <= 0 || block.address() < 0 || block.address() > Long.MAX_VALUE - block.size()
                        || block.allocationId() < 0) throw new IllegalArgumentException("Invalid allocation range");
                byte[] bytes = HexFormat.of().parseHex(block.bytes());
                if (bytes.length != block.size()) throw new IllegalArgumentException("Byte count differs from allocation size");
                BitSet initialized = BitSet.valueOf(HexFormat.of().parseHex(block.initializedMask()));
                // Legacy blocks have no usable object identity. Their bytes remain inspectable through their accessors.
                if (block.allocationId() > 0 && (initialized.length() > block.size()
                        || initialized.cardinality() != block.initializedBytes())) {
                    throw new IllegalArgumentException("Initialization mask differs from allocation metadata");
                }
                Image image = new Image(block, bytes, initialized);
                if (block.allocationId() > 0 && byIdentity.putIfAbsent(block.allocationId(), image) != null) {
                    throw new IllegalArgumentException("Duplicate allocation identity");
                }
                loaded.add(image);
            } catch (RuntimeException failure) {
                throw new MemoryReadException(Reason.MALFORMED_SNAPSHOT, "Malformed memory snapshot: " + failure.getMessage());
            }
        }
        loaded.sort(Comparator.comparingLong(image -> image.block().address()));
        for (int i = 1; i < loaded.size(); i++) {
            MemoryBlock previous = loaded.get(i - 1).block(), current = loaded.get(i).block();
            if (previous.address() + previous.size() > current.address()) {
                throw new MemoryReadException(Reason.MALFORMED_SNAPSHOT, "Overlapping memory allocations");
            }
        }
        identities = Collections.unmodifiableMap(byIdentity);
        images = List.copyOf(loaded);
        NavigableMap<Long, Image> byAddress = new TreeMap<>();
        loaded.forEach(image -> byAddress.put(image.block().address(), image));
        addresses = Collections.unmodifiableNavigableMap(byAddress);
    }

    public List<MemoryBlock> allocations() { return images.stream().map(Image::block).toList(); }

    public Address resolve(long address) {
        Map.Entry<Long, Image> candidate = addresses.floorEntry(address);
        if (candidate != null) {
            MemoryBlock block = candidate.getValue().block();
            if (address - block.address() < block.size()) {
                if (block.allocationId() <= 0) throw new MemoryReadException(Reason.UNKNOWN_IDENTITY, "Legacy allocation has no generation identity");
                return new Address(block.allocationId(), (int) (address - block.address()));
            }
        }
        throw new MemoryReadException(Reason.UNKNOWN_ADDRESS, "Address is not allocated: " + address);
    }

    public MemoryBlock allocation(long id) { return image(id).block(); }
    public long absolute(Address address) {
        Image image = image(address.allocationId());
        range(image, address.offset(), 1);
        return image.block().address() + address.offset();
    }

    public byte[] readBytes(Address address, int fieldOffset, int size) {
        Image image = image(address.allocationId());
        long offset = (long) address.offset() + fieldOffset;
        if (fieldOffset < 0) throw new MemoryReadException(Reason.OUT_OF_BOUNDS, "Negative field offset");
        range(image, offset, size);
        if (image.initialized().nextClearBit((int) offset) < offset + size) {
            throw new MemoryReadException(Reason.UNINITIALIZED, "Registered field contains uninitialized bytes");
        }
        return Arrays.copyOfRange(image.bytes(), (int) offset, (int) offset + size);
    }

    public String readScalar(Address address, int offset, ScalarType type) {
        ByteBuffer bytes = ByteBuffer.wrap(readBytes(address, offset, type.sizeBytes())).order(ByteOrder.LITTLE_ENDIAN);
        return switch (type) {
            case SIGNED8 -> Byte.toString(bytes.get());
            case UNSIGNED8 -> Integer.toString(Byte.toUnsignedInt(bytes.get()));
            case SIGNED16 -> Short.toString(bytes.getShort());
            case UNSIGNED16 -> Integer.toString(Short.toUnsignedInt(bytes.getShort()));
            case SIGNED32 -> Integer.toString(bytes.getInt());
            case UNSIGNED32 -> Long.toString(Integer.toUnsignedLong(bytes.getInt()));
            case SIGNED64 -> Long.toString(bytes.getLong());
            case UNSIGNED64 -> Long.toUnsignedString(bytes.getLong());
            case FLOAT32 -> Float.toString(bytes.getFloat());
            case FLOAT64 -> Double.toString(bytes.getDouble());
            case POINTER -> "0x" + Long.toUnsignedString(bytes.getLong(), 16).toUpperCase(Locale.ROOT);
        };
    }

    public long readPointer(Address address, int fieldOffset) {
        return ByteBuffer.wrap(readBytes(address, fieldOffset, Long.BYTES)).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private Image image(long id) {
        Image image = identities.get(id);
        if (image == null) throw new MemoryReadException(Reason.UNKNOWN_IDENTITY, "Allocation identity is not live: " + id);
        return image;
    }

    private static void range(Image image, long offset, int size) {
        if (size < 0 || offset < 0 || offset > image.block().size() || size > image.block().size() - offset) {
            throw new MemoryReadException(Reason.OUT_OF_BOUNDS, "Registered field exceeds its allocation");
        }
    }
}
