package minic.debug;

import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.IrTemporary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 解释器的可变运行空间；历史状态由 Debugger 保存为不可变 RuntimeState。 */
public final class DebugRuntime {
    private final DebugProgram code;
    final ArrayList<Frame> stack = new ArrayList<>();
    private final NavigableMap<Long, Allocation> memory = new TreeMap<>();
    private final Map<String, Long> symbols = new LinkedHashMap<>();
    private final Map<Long, String> functions = new LinkedHashMap<>();
    private long nextAddress = 0x10000;
    private final String input;
    private int inputOffset;
    final StringBuilder output = new StringBuilder();
    Value returnValue;

    DebugRuntime(DebugProgram code) {
        this(code, "");
    }

    DebugRuntime(DebugProgram code, String input) {
        this.code = code;
        this.input = Objects.requireNonNull(input, "input");
        long functionAddress = 0x1000;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        code.ir().functions().forEach(f -> names.add(f.name()));
        names.addAll(code.ir().externalFunctionNames());
        for (String name : names) {
            symbols.put(name, functionAddress);
            functions.put(functionAddress, name);
            functionAddress += 8;
        }
        for (IrStringData string : code.ir().stringData()) {
            byte[] bytes = (string.value() + '\0').getBytes(StandardCharsets.UTF_8);
            long address = allocate(bytes.length, "static", string.label());
            Allocation allocation = memory.get(address);
            System.arraycopy(bytes, 0, allocation.bytes, 0, bytes.length);
            allocation.initialized.set(0, bytes.length);
            symbols.put(string.label(), address);
        }
    }

    public DebugProgram code() { return code; }
    public String stdout() { return output.toString(); }
    public Value returnValue() { return returnValue; }

    /** 返回只读的当前栈视图；地址随调用帧分配，递归调用不会共用局部变量。 */
    public List<StackFrame> stack() {
        return stack.stream().map(f -> new StackFrame(f.function.name(), f.block, f.pc,
                Map.copyOf(f.parameters), Map.copyOf(f.locals), Map.copyOf(f.temps))).toList();
    }

    public List<MemoryBlock> heap() { return blocks("heap"); }
    public List<MemoryBlock> stackMemory() { return blocks("stack"); }

    /** 将当前堆、栈和输出合成为一个与后续执行完全隔离的运行时对象。 */
    public RuntimeState snapshot() {
        return new RuntimeState(stack(), stackMemory(), heap(), stdout(), returnValue());
    }

    private List<MemoryBlock> blocks(String segment) {
        return memory.values().stream().filter(a -> a.segment.equals(segment))
                .map(a -> new MemoryBlock(a.address, a.bytes.length, a.label,
                        HexFormat.of().formatHex(a.bytes), a.initialized.cardinality())).toList();
    }

    long allocate(int size, String segment, String label) {
        return allocate(size, 1, segment, label);
    }

    long allocate(int size, int alignment, String segment, String label) {
        if (size <= 0 || size > 16 * 1024 * 1024) throw new IllegalStateException("Invalid allocation size: " + size);
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0)
            throw new IllegalStateException("Invalid allocation alignment: " + alignment);
        nextAddress = (nextAddress + alignment - 1) & -alignment;
        long address = nextAddress;
        nextAddress = Math.addExact(nextAddress, size + 16L);
        memory.put(address, new Allocation(address, size, segment, label));
        return address;
    }

    long allocateZeroed(int size, int alignment, String segment, String label) {
        long address = allocate(size, alignment, segment, label);
        memory.get(address).initialized.set(0, size);
        return address;
    }

    void release(long address) {
        if (address == 0) {
            return;
        }
        Allocation allocation = memory.get(address);
        if (allocation == null || !allocation.segment.equals("heap")) {
            throw new IllegalStateException("Invalid heap free: " + address);
        }
        memory.remove(address);
    }

    String readCString(long address) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < 1024 * 1024; index++) {
            int character = (int) read(address + index, IrType.CHAR).integer() & 0xff;
            if (character == 0) {
                return value.toString();
            }
            value.append((char) character);
        }
        throw new IllegalStateException("String is not null terminated");
    }

    void appendOutput(String text) {
        output.append(text);
    }

    void skipInputWhitespace() {
        while (inputOffset < input.length() && Character.isWhitespace(input.charAt(inputOffset))) {
            inputOffset++;
        }
    }

    String readInputToken(int maximumLength) {
        skipInputWhitespace();
        int start = inputOffset;
        while (inputOffset < input.length()
                && !Character.isWhitespace(input.charAt(inputOffset))
                && inputOffset - start < maximumLength) {
            inputOffset++;
        }
        return input.substring(start, inputOffset);
    }

    int readInputCharacter() {
        if (inputOffset >= input.length()) {
            return -1;
        }
        return input.charAt(inputOffset++);
    }

    private Allocation allocation(long address, int size) {
        Map.Entry<Long, Allocation> entry = memory.floorEntry(address);
        if (entry == null || size < 0 || address - entry.getKey() > entry.getValue().bytes.length - (long) size)
            throw new IllegalStateException("Invalid memory access: " + address + " (" + size + " bytes)");
        return entry.getValue();
    }

    public Value read(long address, IrType type) {
        Allocation allocation = allocation(address, type.sizeBytes());
        int offset = (int) (address - allocation.address);
        if (allocation.initialized.nextClearBit(offset) < offset + type.sizeBytes())
            throw new IllegalStateException("Read of uninitialized memory: " + allocation.label);
        ByteBuffer buffer = ByteBuffer.wrap(allocation.bytes).order(ByteOrder.LITTLE_ENDIAN);
        return switch (type) {
            case BOOL, CHAR -> Value.of(type, buffer.get(offset));
            case INT -> Value.of(type, buffer.getInt(offset));
            case LONG, POINTER -> Value.of(type, buffer.getLong(offset));
            case FLOAT -> Value.of(type, buffer.getFloat(offset));
            case DOUBLE -> Value.of(type, buffer.getDouble(offset));
            default -> throw new IllegalStateException("Aggregate must be accessed by address");
        };
    }

    void write(long address, Value value) {
        Allocation allocation = allocation(address, value.type.sizeBytes());
        if (allocation.segment.equals("static")) throw new IllegalStateException("Write to read-only data");
        int offset = (int) (address - allocation.address);
        ByteBuffer buffer = ByteBuffer.wrap(allocation.bytes).order(ByteOrder.LITTLE_ENDIAN);
        switch (value.type) {
            case BOOL, CHAR -> buffer.put(offset, (byte) value.integer());
            case INT -> buffer.putInt(offset, (int) value.integer());
            case LONG, POINTER -> buffer.putLong(offset, value.integer());
            case FLOAT -> buffer.putFloat(offset, (float) value.real());
            case DOUBLE -> buffer.putDouble(offset, value.real());
            default -> throw new IllegalStateException("Aggregate must be copied by address");
        }
        allocation.initialized.set(offset, offset + value.type.sizeBytes());
    }

    void copy(long destination, long source, int size) {
        Allocation from = allocation(source, size), to = allocation(destination, size);
        if (to.segment.equals("static")) throw new IllegalStateException("Write to read-only data");
        int src = (int) (source - from.address), dst = (int) (destination - to.address);
        BitSet initialized = from.initialized.get(src, src + size);
        System.arraycopy(from.bytes, src, to.bytes, dst, size);
        to.initialized.clear(dst, dst + size);
        for (int i = initialized.nextSetBit(0); i >= 0; i = initialized.nextSetBit(i + 1)) to.initialized.set(dst + i);
    }

    long symbol(String name) {
        Long address = symbols.get(name);
        if (address == null) throw new IllegalStateException("Unknown symbol: " + name);
        return address;
    }

    String function(long address) {
        String name = functions.get(address);
        if (name == null) throw new IllegalStateException("Invalid function pointer: " + address);
        return name;
    }

    void push(IrFunction function, List<Value> arguments, IrTemporary target) {
        if (arguments.size() != function.parameters().size()) throw new IllegalStateException("Argument count: " + function.name());
        Frame frame = new Frame(function, target);
        for (int i = 0; i < arguments.size(); i++) frame.parameters.put(function.parameters().get(i).name(), arguments.get(i));
        stack.add(frame);
    }

    void pop(Value value) {
        Frame frame = stack.removeLast();
        frame.locals.values().forEach(memory::remove);
        if (stack.isEmpty()) returnValue = value;
        else if (frame.target != null) {
            if (value == null) {
                throw new IllegalStateException("Value-returning call completed without a value");
            }
            stack.getLast().temps.put(frame.target.name(), value.cast(frame.target.type()));
        }
    }

    long local(Frame frame, IrLocal local) {
        return frame.locals.computeIfAbsent(local.name(), key -> allocate(
                local.sizeBytes(),
                local.alignmentBytes(),
                "stack",
                local.sourceName()
        ));
    }

    public record Value(IrType type, Number number) {
        static Value of(IrType type, Number number) {
            Number normalized = switch (type) {
                case BOOL -> Long.valueOf(number.doubleValue() == 0 ? 0 : 1);
                case CHAR -> Long.valueOf(number.byteValue());
                case INT -> Long.valueOf(number.intValue());
                case LONG, POINTER -> Long.valueOf(number.longValue());
                case FLOAT -> Float.valueOf(number.floatValue());
                case DOUBLE -> Double.valueOf(number.doubleValue());
                default -> throw new IllegalStateException("Not a scalar value: " + type);
            };
            return new Value(type, normalized);
        }
        public long integer() { return number.longValue(); }
        public double real() { return number.doubleValue(); }
        boolean truth() { return number.doubleValue() != 0; }
        Value cast(IrType type) { return of(type, number); }
        @Override public String toString() { return number.toString(); }
    }

    public record RuntimeState(
            List<StackFrame> stack,
            List<MemoryBlock> stackMemory,
            List<MemoryBlock> heap,
            String stdout,
            Value returnValue
    ) {
        public RuntimeState {
            stack = List.copyOf(stack);
            stackMemory = List.copyOf(stackMemory);
            heap = List.copyOf(heap);
        }
    }

    public record StackFrame(String function, int block, int instruction,
                             Map<String, Value> parameters,
                             Map<String, Long> locals,
                             Map<String, Value> temporaries) {}
    public record MemoryBlock(long address, int size, String label, String bytes, int initializedBytes) {}

    static final class Frame {
        final IrFunction function;
        final IrTemporary target;
        final Map<String, Value> parameters = new LinkedHashMap<>(), temps = new LinkedHashMap<>();
        final Map<String, Long> locals = new LinkedHashMap<>();
        int block, pc, lastLine = -1;
        Frame(IrFunction function, IrTemporary target) { this.function = function; this.target = target; }
        void jump(String label) {
            for (int i = 0; i < function.blocks().size(); i++) {
                if (!function.blocks().get(i).label().equals(label)) continue;
                // 回边开始新一轮执行；即便循环全部写在同一行，也要再次命中行 trap。
                if (i <= block) lastLine = -1;
                block = i;
                pc = 0;
                return;
            }
            throw new IllegalStateException("Unknown block: " + label);
        }
    }

    private static final class Allocation {
        final long address;
        final byte[] bytes;
        final BitSet initialized = new BitSet();
        final String segment, label;
        Allocation(long address, int size, String segment, String label) {
            this.address = address; this.bytes = new byte[size]; this.segment = segment; this.label = label;
        }
    }
}
