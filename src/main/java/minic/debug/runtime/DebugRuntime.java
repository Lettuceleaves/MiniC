package minic.debug;

import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.IrTemporary;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

/** 解释器的可变运行空间；历史状态由 Debugger 保存为不可变 RuntimeState。 */
public final class DebugRuntime {
    static final int DEFAULT_HEAP_CAPACITY = 16 * 1024 * 1024;
    private static final int STRERROR_BUFFER_SIZE = 256;
    private static final int LOCALE_CATEGORY_COUNT = 6;
    private static final int LCONV_POINTER_FIELD_COUNT = 10;
    private static final int LCONV_CHAR_FIELD_COUNT = 8;
    private static final int LCONV_SIZE = LCONV_POINTER_FIELD_COUNT * Long.BYTES
            + LCONV_CHAR_FIELD_COUNT;
    private final DebugProgram code;
    private final DebugTimeSource timeSource;
    private final int heapCapacity;
    final ArrayList<Frame> stack = new ArrayList<>();
    private final NavigableMap<Long, Allocation> memory = new TreeMap<>();
    private final Map<String, Long> symbols = new LinkedHashMap<>();
    private final Map<Long, String> functions = new LinkedHashMap<>();
    private long nextAddress = 0x10000;
    private final byte[] input;
    private int inputOffset;
    private int inputPushback = -1;
    private boolean inputEof;
    private long standardStreamsAddress;
    private final java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    private final java.io.ByteArrayOutputStream errorOutput = new java.io.ByteArrayOutputStream();
    private int errno;
    private long errnoPointer;
    private long randomState = 1;
    private long strtokCursor;
    private long strerrorPointer;
    private String strerrorMessage = "";
    private long clockTicks;
    private int clockReads;
    private long epochSeconds;
    private int timeReads;
    private long timeStructPointer;
    private final String[] localeCategories = {
            "C", "C", "C", "C", "C", "C"
    };
    private int localeCalls;
    private int localeSetCalls;
    private int localeConventionCalls;
    private long localeNamePointer;
    private long localeConventionPointer;
    private TerminationState termination = new TerminationState(TerminationKind.RUNNING, null, "");
    Value returnValue;

    DebugRuntime(DebugProgram code) {
        this(code, "");
    }

    DebugRuntime(DebugProgram code, String input) {
        this(code, input, DebugTimeSource.system());
    }

    DebugRuntime(DebugProgram code, String input, DebugTimeSource timeSource) {
        this(code, input, timeSource, DEFAULT_HEAP_CAPACITY);
    }

    DebugRuntime(DebugProgram code, String input, DebugTimeSource timeSource, int heapCapacity) {
        this.code = code;
        this.input = Objects.requireNonNull(input, "input").replace("\r\n", "\n")
                .getBytes(StandardCharsets.UTF_8);
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
        if (heapCapacity < 0) {
            throw new IllegalArgumentException("heapCapacity must not be negative");
        }
        this.heapCapacity = heapCapacity;
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
            byte[] bytes = string.bytes();
            long address = allocate(bytes.length, "static", string.label());
            Allocation allocation = memory.get(address);
            System.arraycopy(bytes, 0, allocation.bytes, 0, bytes.length);
            allocation.initialized.set(0, bytes.length);
            symbols.put(string.label(), address);
        }
        for (IrGlobalData global : code.ir().globalData()) {
            byte[] bytes = global.bytes();
            long address = allocate(bytes.length, global.alignment(), "global", global.label());
            Allocation allocation = memory.get(address);
            System.arraycopy(bytes, 0, allocation.bytes, 0, bytes.length);
            allocation.initialized.set(0, bytes.length);
            symbols.put(global.label(), address);
        }
    }

    public DebugProgram code() { return code; }
    public String stdout() { return output.toString(StandardCharsets.UTF_8); }
    public String stderr() { return errorOutput.toString(StandardCharsets.UTF_8); }
    public int stdinCursor() { return inputOffset; }
    public int errno() {
        return errnoPointer == 0
                ? errno
                : (int) read(errnoPointer, IrType.INT).integer();
    }
    public long randomState() { return randomState; }
    public long strtokCursor() { return strtokCursor; }
    public long strerrorPointer() { return strerrorPointer; }
    public String strerrorMessage() { return strerrorMessage; }
    public long clockTicks() { return clockTicks; }
    public int clockReads() { return clockReads; }
    public long epochSeconds() { return epochSeconds; }
    public int timeReads() { return timeReads; }
    public long timeStructPointer() { return timeStructPointer; }
    public List<String> localeCategories() { return List.of(localeCategories.clone()); }
    public int localeCalls() { return localeCalls; }
    public int localeSetCalls() { return localeSetCalls; }
    public int localeConventionCalls() { return localeConventionCalls; }
    public long localeNamePointer() { return localeNamePointer; }
    public long localeConventionPointer() { return localeConventionPointer; }
    public TerminationState termination() { return termination; }
    public Value returnValue() { return returnValue; }

    /** 返回只读的当前栈视图；地址随调用帧分配，递归调用不会共用局部变量。 */
    public List<StackFrame> stack() {
        return stack.stream().map(f -> new StackFrame(code.ir().displayName(f.function.name()), f.block, f.pc,
                displayedNames(parameterValues(f)), displayedNames(f.locals), Map.copyOf(f.temps))).toList();
    }

    private Map<String, Value> parameterValues(Frame frame) {
        Map<String, Value> values = new LinkedHashMap<>();
        frame.parameters.keySet().forEach(name -> values.put(name, parameter(frame, name)));
        return values;
    }

    /** Rendering never mutates executable keys; distinct slots must survive equal source names. */
    private <T> Map<String, T> displayedNames(Map<String, T> values) {
        Map<String, T> displayed = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String original = code.ir().displayName(key);
            String unique = original;
            for (int occurrence = 2; displayed.containsKey(unique); occurrence++) {
                unique = original + " [" + occurrence + "]";
            }
            displayed.put(unique, value);
        });
        return Collections.unmodifiableMap(displayed);
    }

    public List<MemoryBlock> heap() { return blocks("heap"); }
    public List<MemoryBlock> stackMemory() { return blocks("stack"); }
    public List<MemoryBlock> libraryMemory() { return blocks("library"); }
    public List<MemoryBlock> globalMemory() { return blocks("global"); }

    /** 将当前堆、栈和输出合成为一个与后续执行完全隔离的运行时对象。 */
    public RuntimeState snapshot() {
        return new RuntimeState(
                stack(),
                stackMemory(),
                heap(),
                libraryMemory(),
                globalMemory(),
                stdout(),
                returnValue(),
                stdinCursor(),
                stderr(),
                errno(),
                randomState(),
                strtokCursor(),
                strerrorPointer(),
                strerrorMessage(),
                clockTicks(),
                clockReads(),
                epochSeconds(),
                timeReads(),
                timeStructPointer(),
                localeCategories(),
                localeCalls(),
                localeSetCalls(),
                localeConventionCalls(),
                localeNamePointer(),
                localeConventionPointer(),
                termination(),
                new StdioState(standardStreamsAddress, inputPushback, inputEof,
                        HexFormat.of().formatHex(output.toByteArray()), HexFormat.of().formatHex(errorOutput.toByteArray()))
        );
    }

    private List<MemoryBlock> blocks(String segment) {
        return memory.values().stream().filter(a -> a.segment.equals(segment))
                .map(a -> new MemoryBlock(a.address, a.bytes.length, code.ir().displayName(a.label),
                        HexFormat.of().formatHex(a.bytes), a.initialized.cardinality())).toList();
    }

    long allocate(int size, String segment, String label) {
        return allocate(size, 1, segment, label);
    }

    long allocate(int size, int alignment, String segment, String label) {
        return allocate(size, alignment, segment, label, 0);
    }

    private long allocate(int size, int alignment, String segment, String label, int replacedHeapBytes) {
        if (size <= 0 || size > DEFAULT_HEAP_CAPACITY) {
            if (segment.equals("heap") && size > 0) {
                throw new HeapAllocationException(size);
            }
            throw new IllegalStateException("Invalid allocation size: " + size);
        }
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0)
            throw new IllegalStateException("Invalid allocation alignment: " + alignment);
        if (segment.equals("heap")) {
            long allocated = memory.values().stream()
                    .filter(allocation -> allocation.segment.equals("heap"))
                    .mapToLong(allocation -> allocation.bytes.length)
                    .sum();
            if (allocated - replacedHeapBytes + size > heapCapacity) {
                throw new HeapAllocationException(size);
            }
        }
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

    long reallocate(long address, int size) {
        if (address == 0) {
            return allocate(size, 16, "heap", "realloc");
        }
        Allocation previous = memory.get(address);
        if (previous == null || !previous.segment.equals("heap")) {
            throw new IllegalStateException("Invalid heap realloc: " + address);
        }
        if (previous.bytes.length == size) {
            return address;
        }
        long replacementAddress = allocate(size, 16, "heap", "realloc", previous.bytes.length);
        Allocation replacement = memory.get(replacementAddress);
        int copied = Math.min(previous.bytes.length, size);
        System.arraycopy(previous.bytes, 0, replacement.bytes, 0, copied);
        replacement.initialized.or(previous.initialized.get(0, copied));
        memory.remove(address);
        return replacementAddress;
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

    void writeCString(long address, String value, long maximumSize) {
        Objects.requireNonNull(value, "value");
        if (maximumSize == 0) {
            return;
        }
        int copied = value.length();
        long available = maximumSize - 1;
        if (Long.compareUnsigned(available, Integer.toUnsignedLong(copied)) < 0) {
            copied = Math.toIntExact(available);
        }
        for (int index = 0; index < copied; index++) {
            char character = value.charAt(index);
            if (character > 0xff) {
                throw new IllegalStateException("stdio narrow output contains a non-byte character");
            }
            writeByte(address + index, character);
        }
        writeByte(address + copied, 0);
    }

    int readUnsignedByte(long address) {
        return (int) read(address, IrType.UNSIGNED_CHAR).integer();
    }

    void writeByte(long address, int value) {
        write(address, Value.of(IrType.UNSIGNED_CHAR, value));
    }

    void fill(long address, int value, int size) {
        if (size == 0) {
            return;
        }
        Allocation allocation = allocation(address, size);
        if (allocation.segment.equals("static")) {
            throw new IllegalStateException("Write to read-only data");
        }
        int offset = (int) (address - allocation.address);
        Arrays.fill(allocation.bytes, offset, offset + size, (byte) value);
        allocation.initialized.set(offset, offset + size);
    }

    void appendOutput(String text) {
        output.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    void appendError(String text) {
        errorOutput.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    void appendOutputByte(int character, boolean error) {
        (error ? errorOutput : output).write(character & 0xff);
    }

    long standardStreamsAddress() {
        if (standardStreamsAddress == 0)
            standardStreamsAddress = allocateZeroed(3 * 48, Long.BYTES, "library", "standard FILE streams");
        return standardStreamsAddress;
    }

    int standardStream(long pointer) {
        long offset = pointer - standardStreamsAddress();
        if (offset < 0 || offset > 96 || offset % 48 != 0)
            throw new IllegalStateException("Invalid or unsupported FILE stream");
        return (int) (offset / 48);
    }

    void setErrno(int value) {
        errno = value;
        if (errnoPointer != 0) {
            write(errnoPointer, Value.of(IrType.INT, value));
        }
    }

    int removeFile(String fileName) {
        try {
            Path path = Path.of(fileName);
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                setErrno(DebugLibrarySupport.EACCES);
                return -1;
            }
            Files.delete(path);
            return 0;
        } catch (RuntimeException | IOException exception) {
            setErrno(fileErrno(exception));
            return -1;
        }
    }

    int renameFile(String oldName, String newName) {
        try {
            Path source = Path.of(oldName);
            Path target = Path.of(newName);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                setErrno(DebugLibrarySupport.EEXIST);
                return -1;
            }
            Files.move(source, target);
            return 0;
        } catch (RuntimeException | IOException exception) {
            setErrno(fileErrno(exception));
            return -1;
        }
    }

    private int fileErrno(Exception exception) {
        if (exception instanceof NoSuchFileException) {
            return DebugLibrarySupport.ENOENT;
        }
        if (exception instanceof FileAlreadyExistsException) {
            return DebugLibrarySupport.EEXIST;
        }
        if (exception instanceof AccessDeniedException || exception instanceof SecurityException) {
            return DebugLibrarySupport.EACCES;
        }
        if (exception instanceof InvalidPathException) {
            return DebugLibrarySupport.EINVAL;
        }
        return DebugLibrarySupport.EIO;
    }

    long errnoAddress() {
        if (errnoPointer == 0) {
            errnoPointer = allocateZeroed(
                    IrType.INT.sizeBytes(),
                    IrType.INT.sizeBytes(),
                    "library",
                    "errno"
            );
            write(errnoPointer, Value.of(IrType.INT, errno));
        }
        return errnoPointer;
    }

    void seedRandom(long seed) {
        randomState = seed & 0xffff_ffffL;
    }

    int nextRandom() {
        // Windows msvcrt/ucrt rand 使用 32-bit LCG；默认种子按 C 约定为 1。
        randomState = (randomState * 214013 + 2531011) & 0xffff_ffffL;
        return (int) ((randomState >>> 16) & 0x7fff);
    }

    long readClockTicks() {
        clockTicks = timeSource.clockTicks();
        clockReads++;
        return clockTicks;
    }

    long readEpochSeconds() {
        epochSeconds = timeSource.epochSeconds();
        timeReads++;
        return epochSeconds;
    }

    java.time.ZoneId localTimeZone() {
        return timeSource.localZone();
    }

    long timeStructAddress() {
        if (timeStructPointer == 0) {
            timeStructPointer = allocateZeroed(9 * IrType.INT.sizeBytes(), 4, "library", "struct tm");
        }
        return timeStructPointer;
    }

    long setLocale(int category, long localePointer) {
        localeCalls++;
        if (category < 0 || category >= LOCALE_CATEGORY_COUNT) {
            return 0;
        }
        if (localePointer == 0) {
            return localeNameAddress();
        }

        String requested = readCString(localePointer);
        if (!requested.equals("C")) {
            // The Debug runtime deliberately has no dependency on the host locale/code page.
            // In particular, the implementation-defined empty-string locale remains deferred.
            return 0;
        }
        if (category == 0) {
            Arrays.fill(localeCategories, "C");
        } else {
            localeCategories[category] = "C";
        }
        localeSetCalls++;
        return localeNameAddress();
    }

    long localeConventionAddress() {
        localeConventionCalls++;
        if (localeConventionPointer != 0) {
            return localeConventionPointer;
        }

        long decimalPoint = allocateLibraryCString(".", "locale decimal_point");
        long empty = allocateLibraryCString("", "locale empty string");
        localeConventionPointer = allocateZeroed(LCONV_SIZE, Long.BYTES, "library", "struct lconv");
        for (int field = 0; field < LCONV_POINTER_FIELD_COUNT; field++) {
            long value = field == 0 ? decimalPoint : empty;
            write(localeConventionPointer + field * (long) Long.BYTES, Value.of(IrType.POINTER, value));
        }
        for (int field = 0; field < LCONV_CHAR_FIELD_COUNT; field++) {
            writeByte(localeConventionPointer + LCONV_POINTER_FIELD_COUNT * (long) Long.BYTES + field, 127);
        }
        return localeConventionPointer;
    }

    private long localeNameAddress() {
        if (localeNamePointer == 0) {
            localeNamePointer = allocateLibraryCString("C", "locale name");
        }
        return localeNamePointer;
    }

    private long allocateLibraryCString(String value, String label) {
        int size = value.getBytes(StandardCharsets.ISO_8859_1).length + 1;
        long address = allocateZeroed(size, 1, "library", label);
        writeCString(address, value, size);
        return address;
    }

    void setStrtokCursor(long address) {
        strtokCursor = address;
    }

    long setStrerrorMessage(String message) {
        Objects.requireNonNull(message, "message");
        byte[] bytes = (message + '\0').getBytes(StandardCharsets.UTF_8);
        if (bytes.length > STRERROR_BUFFER_SIZE) {
            throw new IllegalStateException("strerror message is too long");
        }
        Allocation allocation = strerrorPointer == 0 ? null : memory.get(strerrorPointer);
        if (allocation == null) {
            strerrorPointer = allocate(STRERROR_BUFFER_SIZE, 1, "library", "strerror");
            allocation = memory.get(strerrorPointer);
        }
        Arrays.fill(allocation.bytes, (byte) 0);
        System.arraycopy(bytes, 0, allocation.bytes, 0, bytes.length);
        allocation.initialized.clear();
        allocation.initialized.set(0, allocation.bytes.length);
        strerrorMessage = message;
        return strerrorPointer;
    }

    void skipInputWhitespace() {
        while (inputWhitespace(peekInputCharacter())) readInputCharacter();
    }

    String readInputToken(int maximumLength) {
        skipInputWhitespace();
        StringBuilder token = new StringBuilder();
        while (token.length() < maximumLength && peekInputCharacter() >= 0
                && !inputWhitespace(peekInputCharacter())) token.append((char) readInputCharacter());
        return token.toString();
    }

    private static boolean inputWhitespace(int character) {
        return character == ' ' || character >= '\t' && character <= '\r';
    }

    private int peekInputCharacter() {
        return inputPushback >= 0 ? inputPushback : inputOffset >= input.length ? -1 : input[inputOffset] & 0xff;
    }

    int readInputCharacter() {
        if (inputPushback >= 0) { int value = inputPushback; inputPushback = -1; return value; }
        if (inputOffset >= input.length) { inputEof = true; return -1; }
        return input[inputOffset++] & 0xff;
    }

    int unreadInputCharacter(int character) {
        if (character == -1 || inputPushback >= 0) return -1;
        inputPushback = character & 0xff;
        inputEof = false;
        return inputPushback;
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
            throw new IllegalStateException("Read of uninitialized memory: " + code.ir().displayName(allocation.label));
        ByteBuffer buffer = ByteBuffer.wrap(allocation.bytes).order(ByteOrder.LITTLE_ENDIAN);
        return switch (type) {
            case BOOL, CHAR, SIGNED_CHAR -> Value.of(type, buffer.get(offset));
            case UNSIGNED_CHAR -> Value.of(type, Byte.toUnsignedInt(buffer.get(offset)));
            case SHORT -> Value.of(type, buffer.getShort(offset));
            case UNSIGNED_SHORT -> Value.of(type, Short.toUnsignedInt(buffer.getShort(offset)));
            case INT, LONG -> Value.of(type, buffer.getInt(offset));
            case UNSIGNED_INT, UNSIGNED_LONG -> Value.of(
                    type,
                    Integer.toUnsignedLong(buffer.getInt(offset))
            );
            case LONG_LONG, UNSIGNED_LONG_LONG, POINTER -> Value.of(type, buffer.getLong(offset));
            case FLOAT -> Value.of(type, buffer.getFloat(offset));
            case DOUBLE -> Value.of(type, buffer.getDouble(offset));
        };
    }

    void write(long address, Value value) {
        Allocation allocation = allocation(address, value.type.sizeBytes());
        if (allocation.segment.equals("static")) throw new IllegalStateException("Write to read-only data");
        int offset = (int) (address - allocation.address);
        ByteBuffer buffer = ByteBuffer.wrap(allocation.bytes).order(ByteOrder.LITTLE_ENDIAN);
        switch (value.type) {
            case BOOL, CHAR, SIGNED_CHAR, UNSIGNED_CHAR ->
                    buffer.put(offset, (byte) value.integer());
            case SHORT, UNSIGNED_SHORT -> buffer.putShort(offset, (short) value.integer());
            case INT, UNSIGNED_INT, LONG, UNSIGNED_LONG ->
                    buffer.putInt(offset, (int) value.integer());
            case LONG_LONG, UNSIGNED_LONG_LONG, POINTER ->
                    buffer.putLong(offset, value.integer());
            case FLOAT -> buffer.putFloat(offset, (float) value.real());
            case DOUBLE -> buffer.putDouble(offset, value.real());
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
        if (address == null) throw new IllegalStateException("Unknown symbol: " + code.ir().displayName(name));
        return address;
    }

    String function(long address) {
        String name = functions.get(address);
        if (name == null) throw new IllegalStateException("Invalid function pointer: " + address);
        return name;
    }

    void push(IrFunction function, List<Value> arguments, IrTemporary target) {
        int fixedCount = function.parameters().size();
        if (arguments.size() < fixedCount || (!function.variadic() && arguments.size() != fixedCount))
            throw new IllegalStateException("Argument count: " + code.ir().displayName(function.name()));
        Frame frame = new Frame(function, target, arguments);
        for (int i = 0; i < fixedCount; i++) frame.parameters.put(function.parameters().get(i).name(), arguments.get(i));
        stack.add(frame);
    }

    void pop(Value value) {
        Frame frame = stack.removeLast();
        releaseFrame(frame);
        if (stack.isEmpty()) {
            returnValue = value;
            int status = value == null ? 0 : (int) value.integer();
            termination = new TerminationState(TerminationKind.RETURNED, status, "");
        } else if (frame.target != null) {
            if (value == null) {
                throw new IllegalStateException("Value-returning call completed without a value");
            }
            stack.getLast().temps.put(frame.target.name(), value.cast(frame.target.type()));
        }
    }

    void terminate(int status, String reason) {
        stack.forEach(this::releaseFrame);
        stack.clear();
        returnValue = Value.of(IrType.INT, status);
        termination = new TerminationState(TerminationKind.EXITED, status, reason);
    }

    private void releaseFrame(Frame frame) {
        frame.locals.values().forEach(memory::remove);
        frame.parameterAddresses.values().forEach(memory::remove);
        if (frame.incomingArgumentAreaAddress != 0) memory.remove(frame.incomingArgumentAreaAddress);
    }

    void fail(String message) {
        termination = new TerminationState(TerminationKind.FAILED, null, message);
    }

    /** A repeated declaration starts a new lifetime while retaining its frame storage identity. */
    void declareLocal(Frame frame, IrLocal local) {
        long address = local(frame, local);
        if (local.incomingArgumentArea()) return; // Borrowed ABI storage is not a new local object.
        Allocation allocation = allocation(address, local.sizeBytes());
        int offset = (int) (address - allocation.address);
        allocation.initialized.clear(offset, offset + local.sizeBytes());
    }

    long local(Frame frame, IrLocal local) {
        if (local.incomingArgumentArea() && frame.function.variadic()) {
            return frame.locals.computeIfAbsent(local.name(), key -> {
                int index = local.incomingArgumentIndex();
                if (index > frame.arguments.size())
                    throw new IllegalStateException("Invalid incoming argument index: " + index);
                if (frame.incomingArgumentAreaAddress == 0) {
                    // va_start may point one past the final actual argument. Keep that slot
                    // uninitialized so an invalid va_arg is diagnosed by the ordinary reader.
                    int bytes = Math.multiplyExact(Math.addExact(frame.arguments.size(), 1), Long.BYTES);
                    frame.incomingArgumentAreaAddress = allocate(bytes, Long.BYTES, "stack", local.sourceName());
                    for (int i = 0; i < frame.arguments.size(); i++)
                        write(frame.incomingArgumentAreaAddress + (long) i * Long.BYTES, frame.arguments.get(i));
                }
                return frame.incomingArgumentAreaAddress + (long) index * Long.BYTES;
            });
        }
        return frame.locals.computeIfAbsent(local.name(), key -> allocate(
                local.sizeBytes(),
                local.alignmentBytes(),
                "stack",
                local.sourceName()
        ));
    }

    /** Untouched parameters need no debug allocation; addressed parameters share one slot. */
    long parameterAddress(Frame frame, String name) {
        return frame.parameterAddresses.computeIfAbsent(name, key -> {
            IrParameter parameter = frame.function.parameters().stream().filter(p -> p.name().equals(key))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Unknown parameter: " + key));
            long address = allocate(parameter.type().sizeBytes(), parameter.type().sizeBytes(), "stack", key);
            write(address, frame.parameters.get(key).cast(parameter.type()));
            return address;
        });
    }

    Value parameter(Frame frame, String name) {
        Value incoming = frame.parameters.get(name);
        if (incoming == null) throw new IllegalStateException("Unknown parameter: " + name);
        Long address = frame.parameterAddresses.get(name);
        return address == null ? incoming : read(address, incoming.type());
    }

    public record Value(IrType type, Number number) {
        static Value of(IrType type, Number number) {
            Number normalized = switch (type) {
                case BOOL -> Long.valueOf(number.doubleValue() == 0 ? 0 : 1);
                case CHAR, SIGNED_CHAR -> Long.valueOf(number.byteValue());
                case UNSIGNED_CHAR -> Long.valueOf(number.longValue() & 0xffL);
                case SHORT -> Long.valueOf(number.shortValue());
                case UNSIGNED_SHORT -> Long.valueOf(number.longValue() & 0xffffL);
                case INT, LONG -> Long.valueOf(number.intValue());
                case UNSIGNED_INT, UNSIGNED_LONG ->
                        Long.valueOf(number.longValue() & 0xffff_ffffL);
                case LONG_LONG, UNSIGNED_LONG_LONG, POINTER -> Long.valueOf(number.longValue());
                case FLOAT -> Float.valueOf(number.floatValue());
                case DOUBLE -> Double.valueOf(number.doubleValue());
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
            List<MemoryBlock> libraryMemory,
            List<MemoryBlock> globalMemory,
            String stdout,
            Value returnValue,
            int stdinCursor,
            String stderr,
            int errno,
            long randomState,
            long strtokCursor,
            long strerrorPointer,
            String strerrorMessage,
            long clockTicks,
            int clockReads,
            long epochSeconds,
            int timeReads,
            long timeStructPointer,
            List<String> localeCategories,
            int localeCalls,
            int localeSetCalls,
            int localeConventionCalls,
            long localeNamePointer,
            long localeConventionPointer,
            TerminationState termination,
            StdioState stdio
    ) {
        public RuntimeState {
            stack = List.copyOf(stack);
            stackMemory = List.copyOf(stackMemory);
            heap = List.copyOf(heap);
            libraryMemory = List.copyOf(libraryMemory);
            globalMemory = List.copyOf(globalMemory);
            localeCategories = List.copyOf(localeCategories);
            Objects.requireNonNull(stdout, "stdout");
            Objects.requireNonNull(stderr, "stderr");
            Objects.requireNonNull(strerrorMessage, "strerrorMessage");
            Objects.requireNonNull(termination, "termination");
            Objects.requireNonNull(stdio, "stdio");
        }
    }

    /** Immutable byte state keeps partial UTF-8 and ungetc visible in debug history. */
    public record StdioState(long standardStreamsAddress, int inputPushback, boolean inputEof,
                             String stdoutBytes, String stderrBytes) { }

    public enum TerminationKind {
        RUNNING,
        RETURNED,
        EXITED,
        FAILED
    }

    public record TerminationState(TerminationKind kind, Integer status, String detail) {
        public TerminationState {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(detail, "detail");
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
        final List<Value> arguments;
        long incomingArgumentAreaAddress;
        final Map<String, Value> parameters = new LinkedHashMap<>(), temps = new LinkedHashMap<>();
        final Map<String, Long> locals = new LinkedHashMap<>();
        final Map<String, Long> parameterAddresses = new LinkedHashMap<>();
        int block, pc, lastLine = -1;
        Frame(IrFunction function, IrTemporary target, List<Value> arguments) {
            this.function = function;
            this.target = target;
            this.arguments = List.copyOf(arguments);
        }
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

    static final class HeapAllocationException extends IllegalStateException {
        HeapAllocationException(int size) {
            super("Debug heap capacity exceeded by allocation: " + size);
        }
    }
}
