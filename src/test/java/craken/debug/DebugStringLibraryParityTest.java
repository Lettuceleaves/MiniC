package craken.debug;

import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.model.IrType;
import craken.debug.DebugLibraryCallResult.Returned;
import craken.debug.DebugRuntime.RuntimeState;
import craken.debug.DebugRuntime.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugStringLibraryParityTest {
    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach
    void setUp() {
        SourceFile source = new SourceFile("string-provider.mc", "");
        runtime = new DebugRuntime(new DebugProgram(source, new IrResult(List.of())), "");
        library = new DebugSystemLibrary();
    }

    @Test
    void implementsRawMemoryFunctionsIncludingOverlappingMemmove() {
        long source = bytes(1, 2, 255, 4);
        long destination = zeroed(4);

        assertEquals(destination, call("memcpy", ptr(destination), ptr(source), size(4)).integer());
        assertArrayEquals(new int[]{1, 2, 255, 4}, readBytes(destination, 4));
        assertEquals(0, call("memcmp", ptr(source), ptr(destination), size(4)).integer());
        assertEquals(source + 2, call("memchr", ptr(source), integer(255), size(4)).integer());

        call("memset", ptr(destination + 1), integer(0xaa), size(2));
        assertArrayEquals(new int[]{1, 0xaa, 0xaa, 4}, readBytes(destination, 4));
        assertTrue(call("memcmp", ptr(source), ptr(destination), size(4)).integer() < 0);

        long overlap = stringBuffer("abcdef", 7);
        call("memmove", ptr(overlap + 2), ptr(overlap), size(4));
        assertEquals("ababcd", runtime.readCString(overlap));

        assertEquals(0, call("memcpy", ptr(0), ptr(0), size(0)).integer());
        assertEquals(0, call("memcmp", ptr(0), ptr(0), size(0)).integer());
        assertEquals(0, call("memchr", ptr(0), integer('x'), size(0)).integer());
    }

    @Test
    void memccpyCopiesThroughTheMatchingByteAndReturnsTheFollowingDestination() {
        long source = bytes('a', 'b', 'c', 'd', 'e');
        long matched = bytes(9, 9, 9, 9, 9);

        assertEquals(
                matched + 3,
                call("memccpy", ptr(matched), ptr(source), integer('c'), size(5)).integer()
        );
        assertArrayEquals(new int[]{'a', 'b', 'c', 9, 9}, readBytes(matched, 5));

        long missing = bytes(8, 8, 8, 8, 8);
        assertEquals(0, call("memccpy", ptr(missing), ptr(source), integer('z'), size(5)).integer());
        assertArrayEquals(new int[]{'a', 'b', 'c', 'd', 'e'}, readBytes(missing, 5));

        assertEquals(0, call("memccpy", ptr(0), ptr(0), integer('a'), size(0)).integer());
    }

    @Test
    void strdupReturnsIndependentHeapStorageAndReportsDeterministicOutOfMemory() {
        long source = cString("copy-me");
        long duplicate = call("strdup", ptr(source)).integer();
        assertNotEquals(0, duplicate);
        assertNotEquals(source, duplicate);
        assertEquals("copy-me", runtime.readCString(duplicate));

        runtime.writeByte(source, 'X');
        assertEquals("copy-me", runtime.readCString(duplicate));

        SourceFile file = new SourceFile("strdup-oom.mc", "");
        runtime = new DebugRuntime(
                new DebugProgram(file, new IrResult(List.of())),
                "",
                DebugTimeSource.system(),
                3
        );
        library = new DebugSystemLibrary();
        long tooLarge = runtime.allocateZeroed(4, 1, "library", "strdup source");
        runtime.writeByte(tooLarge, 'o');
        runtime.writeByte(tooLarge + 1, 'o');
        runtime.writeByte(tooLarge + 2, 'm');

        assertEquals(0, call("strdup", ptr(tooLarge)).integer());
        assertEquals(DebugLibrarySupport.ENOMEM, runtime.errno());
        assertTrue(runtime.heap().isEmpty());
    }

    @Test
    void implementsStringCopyAndConcatenationFunctions() {
        long alpha = cString("alpha");
        long destination = zeroed(32);
        assertEquals(destination, call("strcpy", ptr(destination), ptr(alpha)).integer());
        assertEquals("alpha", runtime.readCString(destination));

        long shortSource = cString("x");
        long padded = bytes(9, 9, 9, 9, 9);
        call("strncpy", ptr(padded), ptr(shortSource), size(5));
        assertArrayEquals(new int[]{'x', 0, 0, 0, 0}, readBytes(padded, 5));

        long suffix = cString("-beta");
        call("strcat", ptr(destination), ptr(suffix));
        assertEquals("alpha-beta", runtime.readCString(destination));

        long tail = cString("-gamma");
        call("strncat", ptr(destination), ptr(tail), size(3));
        assertEquals("alpha-beta-ga", runtime.readCString(destination));
    }

    @ParameterizedTest(name = "{0}({1}, {2})")
    @MethodSource("comparisonCases")
    void comparesStringsUsingUnsignedCBytes(
            String function,
            String left,
            String right,
            int count,
            int expectedSign
    ) {
        long leftAddress = cString(left);
        long rightAddress = cString(right);
        long result = function.equals("strncmp")
                ? call(function, ptr(leftAddress), ptr(rightAddress), size(count)).integer()
                : call(function, ptr(leftAddress), ptr(rightAddress)).integer();

        assertEquals(expectedSign, Long.signum(result));
    }

    @ParameterizedTest(name = "{0} finds offset {3}")
    @MethodSource("searchCases")
    void searchesNarrowStrings(
            String function,
            String source,
            String argument,
            int expectedOffset
    ) {
        long sourceAddress = cString(source);
        Value second;
        if (function.equals("strchr") || function.equals("strrchr")) {
            second = integer(argument.charAt(0));
        } else {
            second = ptr(cString(argument));
        }

        long result = call(function, ptr(sourceAddress), second).integer();
        assertEquals(expectedOffset < 0 ? 0 : sourceAddress + expectedOffset, result);
    }

    @Test
    void implementsLengthsSpansAndCLocaleTransform() {
        long text = cString("aaab,tail");
        assertEquals(9, call("strlen", ptr(text)).integer());
        assertEquals(3, call("strspn", ptr(text), ptr(cString("a"))).integer());
        assertEquals(4, call("strcspn", ptr(text), ptr(cString(","))).integer());

        long transformed = zeroed(5);
        assertEquals(9, call("strxfrm", ptr(transformed), ptr(text), size(5)).integer());
        assertArrayEquals(new int[]{'a', 'a', 'a', 'b', ','}, readBytes(transformed, 5));
    }

    @Test
    void snapshotsStrtokCursorAndMutableTokenBuffer() {
        long text = stringBuffer("one,two,,three", 32);
        long delimiters = cString(",");

        long first = call("strtok", ptr(text), ptr(delimiters)).integer();
        RuntimeState firstState = runtime.snapshot();
        assertEquals("one", runtime.readCString(first));
        assertNotEquals(0, firstState.strtokCursor());

        long second = call("strtok", ptr(0), ptr(delimiters)).integer();
        RuntimeState secondState = runtime.snapshot();
        assertEquals("two", runtime.readCString(second));
        assertNotEquals(firstState.strtokCursor(), secondState.strtokCursor());
        assertEquals(',', snapshotByte(firstState, text, 7));
        assertEquals(0, snapshotByte(secondState, text, 7));

        long third = call("strtok", ptr(0), ptr(delimiters)).integer();
        assertEquals("three", runtime.readCString(third));
        assertEquals(0, runtime.strtokCursor());
        assertEquals(0, call("strtok", ptr(0), ptr(delimiters)).integer());

        assertNotEquals(0, firstState.strtokCursor());
    }

    @Test
    void snapshotsStrerrorStaticBufferAndMessage() {
        long first = call("strerror", integer(22)).integer();
        RuntimeState invalidArgument = runtime.snapshot();
        assertEquals("Invalid argument", runtime.readCString(first));
        assertEquals(first, invalidArgument.strerrorPointer());
        assertEquals("Invalid argument", invalidArgument.strerrorMessage());
        assertEquals(1, invalidArgument.libraryMemory().size());

        long second = call("strerror", integer(2)).integer();
        RuntimeState missingFile = runtime.snapshot();
        assertEquals(first, second);
        assertEquals("No such file or directory", runtime.readCString(second));
        assertEquals("No such file or directory", missingFile.strerrorMessage());
        assertEquals("Invalid argument", invalidArgument.strerrorMessage());

        long unknown = call("strerror", integer(999)).integer();
        assertEquals("Unknown error 999", runtime.readCString(unknown));
    }

    private Value call(String name, Value... arguments) {
        DebugLibraryCallResult result = library.invoke(name, runtime, List.of(arguments)).orElseThrow();
        return ((Returned) result).value();
    }

    private long cString(String value) {
        return stringBuffer(value, value.getBytes(StandardCharsets.ISO_8859_1).length + 1);
    }

    private long stringBuffer(String value, int capacity) {
        byte[] encoded = value.getBytes(StandardCharsets.ISO_8859_1);
        if (encoded.length + 1 > capacity) {
            throw new IllegalArgumentException("buffer is too small");
        }
        long address = zeroed(capacity);
        for (int index = 0; index < encoded.length; index++) {
            runtime.writeByte(address + index, encoded[index]);
        }
        runtime.writeByte(address + encoded.length, 0);
        return address;
    }

    private long bytes(int... values) {
        long address = zeroed(values.length);
        for (int index = 0; index < values.length; index++) {
            runtime.writeByte(address + index, values[index]);
        }
        return address;
    }

    private long zeroed(int size) {
        return runtime.allocateZeroed(size, 1, "heap", "test");
    }

    private int[] readBytes(long address, int size) {
        int[] values = new int[size];
        Arrays.setAll(values, index -> runtime.readUnsignedByte(address + index));
        return values;
    }

    private int snapshotByte(RuntimeState state, long address, int offset) {
        String bytes = state.heap().stream()
                .filter(block -> block.address() == address)
                .findFirst().orElseThrow()
                .bytes();
        return Integer.parseInt(bytes.substring(offset * 2, offset * 2 + 2), 16);
    }

    private Value ptr(long value) {
        return Value.of(IrType.POINTER, value);
    }

    private Value integer(int value) {
        return Value.of(IrType.INT, value);
    }

    private Value size(long value) {
        return Value.of(IrType.UNSIGNED_LONG_LONG, value);
    }

    private static Stream<Arguments> comparisonCases() {
        return Stream.of(
                Arguments.of("strcmp", "abc", "abc", 0, 0),
                Arguments.of("strcmp", "abc", "abd", 0, -1),
                Arguments.of("strcmp", "abe", "abd", 0, 1),
                Arguments.of("strncmp", "abc", "abd", 2, 0),
                Arguments.of("strncmp", "abc", "abd", 3, -1),
                Arguments.of("strncmp", "abc", "abd", 0, 0),
                Arguments.of("strcoll", "alpha", "beta", 0, -1),
                Arguments.of("strcmp", "\u00ff", "\u0001", 0, 1)
        );
    }

    private static Stream<Arguments> searchCases() {
        return Stream.of(
                Arguments.of("strchr", "banana", "a", 1),
                Arguments.of("strrchr", "banana", "a", 5),
                Arguments.of("strchr", "banana", "z", -1),
                Arguments.of("strpbrk", "abcde", "dx", 3),
                Arguments.of("strpbrk", "abcde", "xy", -1),
                Arguments.of("strstr", "abcabc", "cab", 2),
                Arguments.of("strstr", "abcabc", "", 0)
        );
    }
}
