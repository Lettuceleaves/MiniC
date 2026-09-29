package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugCtypeLibraryParityTest {
    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach
    void setUp() {
        SourceFile source = new SourceFile("ctype-provider.mc", "");
        runtime = new DebugRuntime(new DebugProgram(source, new IrResult(java.util.List.of())), "");
        library = new DebugSystemLibrary();
    }

    @ParameterizedTest(name = "{0}({1}) == {2}")
    @MethodSource("classificationCases")
    void followsCByteClassificationInsteadOfUnicode(
            String function,
            int input,
            int expected
    ) {
        assertEquals(expected, invoke(function, input));
    }

    @ParameterizedTest(name = "{0}({1}) == {2}")
    @MethodSource("conversionCases")
    void convertsOnlyAsciiLetters(String function, int input, int expected) {
        assertEquals(expected, invoke(function, input));
    }

    @Test
    void rejectsValuesOutsideEofAndUnsignedByteRange() {
        assertThrows(IllegalStateException.class, () -> invoke("isalpha", -2));
        assertThrows(IllegalStateException.class, () -> invoke("toupper", 256));
    }

    private int invoke(String name, int input) {
        DebugLibraryCallResult result = library.invoke(
                name,
                runtime,
                java.util.List.of(Value.of(IrType.INT, input))
        ).orElseThrow();
        return (int) ((Returned) result).value().integer();
    }

    private static Stream<Arguments> classificationCases() {
        return Stream.of(
                Arguments.of("isalnum", 'A', 1), Arguments.of("isalnum", '7', 1),
                Arguments.of("isalnum", '-', 0),
                Arguments.of("isalpha", 'z', 1), Arguments.of("isalpha", 0xe9, 0),
                Arguments.of("isblank", ' ', 1), Arguments.of("isblank", '\t', 1),
                Arguments.of("isblank", '\n', 0),
                Arguments.of("iscntrl", 0, 1), Arguments.of("iscntrl", 127, 1),
                Arguments.of("iscntrl", 128, 0),
                Arguments.of("isdigit", '0', 1), Arguments.of("isdigit", 'a', 0),
                Arguments.of("isgraph", '!', 1), Arguments.of("isgraph", ' ', 0),
                Arguments.of("islower", 'q', 1), Arguments.of("islower", 'Q', 0),
                Arguments.of("isprint", ' ', 1), Arguments.of("isprint", 127, 0),
                Arguments.of("ispunct", '?', 1), Arguments.of("ispunct", 'A', 0),
                Arguments.of("isspace", 11, 1), Arguments.of("isspace", 'x', 0),
                Arguments.of("isupper", 'Q', 1), Arguments.of("isupper", 'q', 0),
                Arguments.of("isxdigit", 'F', 1), Arguments.of("isxdigit", 'g', 0),
                Arguments.of("isalpha", -1, 0), Arguments.of("isdigit", -1, 0)
        );
    }

    private static Stream<Arguments> conversionCases() {
        return Stream.of(
                Arguments.of("tolower", 'A', (int) 'a'),
                Arguments.of("tolower", 'z', (int) 'z'),
                Arguments.of("tolower", 0xe9, 0xe9),
                Arguments.of("tolower", -1, -1),
                Arguments.of("toupper", 'z', (int) 'Z'),
                Arguments.of("toupper", 'A', (int) 'A'),
                Arguments.of("toupper", 0xe9, 0xe9),
                Arguments.of("toupper", -1, -1)
        );
    }
}
