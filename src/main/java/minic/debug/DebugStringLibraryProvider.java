package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** C17 string.h 的窄字符与原始内存函数，全部访问 DebugRuntime 虚拟内存。 */
final class DebugStringLibraryProvider implements DebugLibraryProvider {
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final Map<Integer, String> ERROR_MESSAGES = Map.ofEntries(
            Map.entry(0, "No error"),
            Map.entry(1, "Operation not permitted"),
            Map.entry(2, "No such file or directory"),
            Map.entry(12, "Not enough memory"),
            Map.entry(13, "Permission denied"),
            Map.entry(17, "File exists"),
            Map.entry(22, "Invalid argument"),
            Map.entry(28, "No space left on device"),
            Map.entry(33, "Domain error"),
            Map.entry(34, "Result too large")
    );

    private final Map<String, DebugLibraryFunction> functions;

    DebugStringLibraryProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        registered.put("memcpy", this::memcpy);
        registered.put("memmove", this::memmove);
        registered.put("memchr", this::memchr);
        registered.put("memcmp", this::memcmp);
        registered.put("memset", this::memset);
        registered.put("strcpy", this::strcpy);
        registered.put("strncpy", this::strncpy);
        registered.put("strcat", this::strcat);
        registered.put("strncat", this::strncat);
        registered.put("strcmp", this::strcmp);
        registered.put("strncmp", this::strncmp);
        registered.put("strcoll", this::strcoll);
        registered.put("strchr", this::strchr);
        registered.put("strrchr", this::strrchr);
        registered.put("strspn", this::strspn);
        registered.put("strcspn", this::strcspn);
        registered.put("strpbrk", this::strpbrk);
        registered.put("strstr", this::strstr);
        registered.put("strtok", this::strtok);
        registered.put("strerror", this::strerror);
        registered.put("strlen", this::strlen);
        registered.put("strxfrm", this::strxfrm);
        functions = Map.copyOf(registered);
    }

    @Override
    public String name() {
        return "string";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private DebugLibraryCallResult memcpy(DebugRuntime runtime, List<Value> arguments) {
        return copy("memcpy", runtime, arguments);
    }

    private DebugLibraryCallResult memmove(DebugRuntime runtime, List<Value> arguments) {
        return copy("memmove", runtime, arguments);
    }

    private DebugLibraryCallResult copy(String name, DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount(name, arguments, 3);
        long destination = pointer(arguments, 0);
        long source = pointer(arguments, 1);
        int size = size(name, arguments, 2);
        if (size > 0) {
            runtime.copy(destination, source, size);
        }
        return pointer(destination);
    }

    private DebugLibraryCallResult memchr(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("memchr", arguments, 3);
        long source = pointer(arguments, 0);
        int expected = (int) arguments.get(1).integer() & 0xff;
        int size = size("memchr", arguments, 2);
        for (int index = 0; index < size; index++) {
            if (runtime.readUnsignedByte(source + index) == expected) {
                return pointer(source + index);
            }
        }
        return pointer(0);
    }

    private DebugLibraryCallResult memcmp(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("memcmp", arguments, 3);
        long left = pointer(arguments, 0);
        long right = pointer(arguments, 1);
        int size = size("memcmp", arguments, 2);
        for (int index = 0; index < size; index++) {
            int difference = runtime.readUnsignedByte(left + index)
                    - runtime.readUnsignedByte(right + index);
            if (difference != 0) {
                return integer(difference);
            }
        }
        return integer(0);
    }

    private DebugLibraryCallResult memset(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("memset", arguments, 3);
        long destination = pointer(arguments, 0);
        int value = (int) arguments.get(1).integer();
        int size = size("memset", arguments, 2);
        runtime.fill(destination, value, size);
        return pointer(destination);
    }

    private DebugLibraryCallResult strcpy(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strcpy", arguments, 2);
        long destination = pointer(arguments, 0);
        long source = pointer(arguments, 1);
        runtime.copy(destination, source, stringLength(runtime, source) + 1);
        return pointer(destination);
    }

    private DebugLibraryCallResult strncpy(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strncpy", arguments, 3);
        long destination = pointer(arguments, 0);
        long source = pointer(arguments, 1);
        int count = size("strncpy", arguments, 2);
        boolean padding = false;
        for (int index = 0; index < count; index++) {
            int value = padding ? 0 : runtime.readUnsignedByte(source + index);
            runtime.writeByte(destination + index, value);
            padding = padding || value == 0;
        }
        return pointer(destination);
    }

    private DebugLibraryCallResult strcat(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strcat", arguments, 2);
        long destination = pointer(arguments, 0);
        long source = pointer(arguments, 1);
        long end = destination + stringLength(runtime, destination);
        runtime.copy(end, source, stringLength(runtime, source) + 1);
        return pointer(destination);
    }

    private DebugLibraryCallResult strncat(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strncat", arguments, 3);
        long destination = pointer(arguments, 0);
        long source = pointer(arguments, 1);
        int count = size("strncat", arguments, 2);
        long end = destination + stringLength(runtime, destination);
        int copied = 0;
        while (copied < count) {
            int value = runtime.readUnsignedByte(source + copied);
            if (value == 0) {
                break;
            }
            runtime.writeByte(end + copied, value);
            copied++;
        }
        runtime.writeByte(end + copied, 0);
        return pointer(destination);
    }

    private DebugLibraryCallResult strcmp(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strcmp", arguments, 2);
        return integer(compareStrings(runtime, pointer(arguments, 0), pointer(arguments, 1), null));
    }

    private DebugLibraryCallResult strncmp(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strncmp", arguments, 3);
        int count = size("strncmp", arguments, 2);
        return integer(compareStrings(runtime, pointer(arguments, 0), pointer(arguments, 1), count));
    }

    private DebugLibraryCallResult strcoll(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strcoll", arguments, 2);
        return integer(compareStrings(runtime, pointer(arguments, 0), pointer(arguments, 1), null));
    }

    private DebugLibraryCallResult strchr(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strchr", arguments, 2);
        long string = pointer(arguments, 0);
        int expected = (int) arguments.get(1).integer() & 0xff;
        for (int index = 0; index <= MAX_STRING_BYTES; index++) {
            int value = runtime.readUnsignedByte(string + index);
            if (value == expected) {
                return pointer(string + index);
            }
            if (value == 0) {
                return pointer(0);
            }
        }
        throw unterminated();
    }

    private DebugLibraryCallResult strrchr(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strrchr", arguments, 2);
        long string = pointer(arguments, 0);
        int expected = (int) arguments.get(1).integer() & 0xff;
        long found = 0;
        for (int index = 0; index <= MAX_STRING_BYTES; index++) {
            int value = runtime.readUnsignedByte(string + index);
            if (value == expected) {
                found = string + index;
            }
            if (value == 0) {
                return pointer(found);
            }
        }
        throw unterminated();
    }

    private DebugLibraryCallResult strspn(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strspn", arguments, 2);
        long string = pointer(arguments, 0);
        long accepted = pointer(arguments, 1);
        int length = 0;
        while (length < MAX_STRING_BYTES) {
            int value = runtime.readUnsignedByte(string + length);
            if (value == 0 || !contains(runtime, accepted, value)) {
                return length(length);
            }
            length++;
        }
        throw unterminated();
    }

    private DebugLibraryCallResult strcspn(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strcspn", arguments, 2);
        long string = pointer(arguments, 0);
        long rejected = pointer(arguments, 1);
        int length = 0;
        while (length < MAX_STRING_BYTES) {
            int value = runtime.readUnsignedByte(string + length);
            if (value == 0 || contains(runtime, rejected, value)) {
                return length(length);
            }
            length++;
        }
        throw unterminated();
    }

    private DebugLibraryCallResult strpbrk(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strpbrk", arguments, 2);
        long string = pointer(arguments, 0);
        long accepted = pointer(arguments, 1);
        for (int index = 0; index < MAX_STRING_BYTES; index++) {
            int value = runtime.readUnsignedByte(string + index);
            if (value == 0) {
                return pointer(0);
            }
            if (contains(runtime, accepted, value)) {
                return pointer(string + index);
            }
        }
        throw unterminated();
    }

    private DebugLibraryCallResult strstr(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strstr", arguments, 2);
        long haystack = pointer(arguments, 0);
        long needle = pointer(arguments, 1);
        int haystackLength = stringLength(runtime, haystack);
        int needleLength = stringLength(runtime, needle);
        if (needleLength == 0) {
            return pointer(haystack);
        }
        for (int start = 0; start <= haystackLength - needleLength; start++) {
            boolean matches = true;
            for (int index = 0; index < needleLength; index++) {
                if (runtime.readUnsignedByte(haystack + start + index)
                        != runtime.readUnsignedByte(needle + index)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return pointer(haystack + start);
            }
        }
        return pointer(0);
    }

    private DebugLibraryCallResult strtok(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strtok", arguments, 2);
        long string = pointer(arguments, 0);
        long delimiters = pointer(arguments, 1);
        long cursor = string == 0 ? runtime.strtokCursor() : string;
        if (cursor == 0) {
            return pointer(0);
        }
        while (contains(runtime, delimiters, runtime.readUnsignedByte(cursor))) {
            cursor++;
        }
        if (runtime.readUnsignedByte(cursor) == 0) {
            runtime.setStrtokCursor(0);
            return pointer(0);
        }
        long token = cursor;
        while (true) {
            int value = runtime.readUnsignedByte(cursor);
            if (value == 0) {
                runtime.setStrtokCursor(0);
                return pointer(token);
            }
            if (contains(runtime, delimiters, value)) {
                runtime.writeByte(cursor, 0);
                runtime.setStrtokCursor(cursor + 1);
                return pointer(token);
            }
            cursor++;
        }
    }

    private DebugLibraryCallResult strerror(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strerror", arguments, 1);
        int error = (int) arguments.getFirst().integer();
        String message = ERROR_MESSAGES.getOrDefault(error, "Unknown error " + error);
        return pointer(runtime.setStrerrorMessage(message));
    }

    private DebugLibraryCallResult strlen(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strlen", arguments, 1);
        return length(stringLength(runtime, pointer(arguments, 0)));
    }

    private DebugLibraryCallResult strxfrm(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strxfrm", arguments, 3);
        long destination = pointer(arguments, 0);
        long source = pointer(arguments, 1);
        int count = size("strxfrm", arguments, 2);
        int sourceLength = stringLength(runtime, source);
        if (count > 0) {
            runtime.copy(destination, source, Math.min(count, sourceLength + 1));
        }
        return length(sourceLength);
    }

    private int compareStrings(DebugRuntime runtime, long left, long right, Integer maximum) {
        int limit = maximum == null ? MAX_STRING_BYTES : maximum;
        for (int index = 0; index < limit; index++) {
            int leftValue = runtime.readUnsignedByte(left + index);
            int rightValue = runtime.readUnsignedByte(right + index);
            if (leftValue != rightValue) {
                return leftValue - rightValue;
            }
            if (leftValue == 0) {
                return 0;
            }
        }
        if (maximum == null) {
            throw unterminated();
        }
        return 0;
    }

    private boolean contains(DebugRuntime runtime, long set, int expected) {
        for (int index = 0; index < MAX_STRING_BYTES; index++) {
            int value = runtime.readUnsignedByte(set + index);
            if (value == 0) {
                return false;
            }
            if (value == expected) {
                return true;
            }
        }
        throw unterminated();
    }

    private int stringLength(DebugRuntime runtime, long address) {
        for (int length = 0; length < MAX_STRING_BYTES; length++) {
            if (runtime.readUnsignedByte(address + length) == 0) {
                return length;
            }
        }
        throw unterminated();
    }

    private int size(String function, List<Value> arguments, int index) {
        long size = arguments.get(index).integer();
        if (size < 0 || size > Integer.MAX_VALUE) {
            throw new IllegalStateException(function + " size is invalid: " + Long.toUnsignedString(size));
        }
        return (int) size;
    }

    private long pointer(List<Value> arguments, int index) {
        return arguments.get(index).integer();
    }

    private Returned pointer(long address) {
        return new Returned(Value.of(IrType.POINTER, address));
    }

    private Returned integer(int value) {
        return new Returned(Value.of(IrType.INT, value));
    }

    private Returned length(int value) {
        return new Returned(Value.of(IrType.UNSIGNED_LONG_LONG, value));
    }

    private IllegalStateException unterminated() {
        return new IllegalStateException("String is not null terminated");
    }
}
