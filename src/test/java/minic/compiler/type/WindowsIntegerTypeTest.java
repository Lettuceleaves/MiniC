package minic.compiler.type;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WindowsIntegerTypeTest {
    @Test
    void usesTheWindowsLlp64IntegerLayout() {
        assertEquals(1, TypeLayout.sizeOf(MiniType.CHAR));
        assertEquals(1, TypeLayout.sizeOf(MiniType.SIGNED_CHAR));
        assertEquals(1, TypeLayout.sizeOf(MiniType.UNSIGNED_CHAR));
        assertEquals(2, TypeLayout.sizeOf(MiniType.SHORT));
        assertEquals(2, TypeLayout.sizeOf(MiniType.UNSIGNED_SHORT));
        assertEquals(4, TypeLayout.sizeOf(MiniType.INT));
        assertEquals(4, TypeLayout.sizeOf(MiniType.UNSIGNED_INT));
        assertEquals(4, TypeLayout.sizeOf(MiniType.LONG));
        assertEquals(4, TypeLayout.sizeOf(MiniType.UNSIGNED_LONG));
        assertEquals(8, TypeLayout.sizeOf(MiniType.LONG_LONG));
        assertEquals(8, TypeLayout.sizeOf(MiniType.UNSIGNED_LONG_LONG));
    }

    @Test
    void parsesAndRunsSignedAndUnsignedIntegerTypesAcrossCalls() {
        String source = """
                short echo_short(short value) { return value; }
                unsigned short echo_ushort(unsigned short value) { return value; }
                long echo_long(long value) { return value; }
                unsigned long echo_ulong(unsigned long value) { return value; }
                long long echo_long_long(long long value) { return value; }
                unsigned long long echo_ulong_long(unsigned long long value) { return value; }
                int sum_short_args(short a, short b, short c, short d, short e, unsigned short f) {
                    return a + b + c + d + e + f;
                }

                int main() {
                    signed char signed_byte = -7;
                    unsigned char unsigned_byte = 250U;
                    signed short signed_short = -1234;
                    unsigned short unsigned_short_value = 65535U;
                    signed signed_int = -20;
                    unsigned unsigned_int = 4294967295U;
                    signed long signed_long = -2000000000L;
                    unsigned long unsigned_long_value = 4000000000UL;
                    signed long long signed_wide = -5000000000LL;
                    unsigned long long unsigned_wide = 5000000000ULL;
                    unsigned long long all_bits = 18446744073709551615ULL;

                    if (sizeof(short) != 2) return 10;
                    if (sizeof(long) != 4) return 11;
                    if (sizeof(long long) != 8) return 12;
                    if (signed_byte != -7 || unsigned_byte != 250U) return 13;
                    if (echo_short(signed_short) != -1234) return 14;
                    if (echo_ushort(unsigned_short_value) != 65535U) return 15;
                    if (signed_int != -20) return 16;
                    if (echo_long(signed_long) != -2000000000L) return 17;
                    if (echo_ulong(unsigned_long_value) != 4000000000UL) return 18;
                    if (echo_long_long(signed_wide) != -5000000000LL) return 19;
                    if (echo_ulong_long(unsigned_wide) != 5000000000ULL) return 20;
                    if (unsigned_int <= 1U) return 21;
                    if (unsigned_int / 2U != 2147483647U) return 22;
                    if ((unsigned_int >> 31) != 1U) return 23;
                    if (unsigned_short_value + 1 != 65536) return 24;
                    if (all_bits / 3ULL != 6148914691236517205ULL) return 25;
                    if ((all_bits >> 63) != 1ULL) return 26;
                    if (all_bits <= 1ULL) return 27;
                    if (sum_short_args(1, 2, 3, 4, -5, 65535U) != 65540) return 28;
                    if (-1 < 1U) return 29;
                    if (!(-1LL < 1U)) return 30;
                    unsigned short zero_short = 0U;
                    if (zero_short) return 31;
                    double unsigned_as_double = unsigned_int;
                    if (unsigned_as_double != 4294967295.0) return 32;
                    unsigned int unsigned_from_double = 4000000000.0;
                    if (unsigned_from_double != 4000000000U) return 33;
                    unsigned long wrapped_long = 4294967295UL;
                    wrapped_long += 1UL;
                    if (wrapped_long != 0UL) return 34;
                    unsigned short truncated_short = 65536U;
                    if (truncated_short != 0U) return 35;
                    return 0;
                }
                """;

        SourceFile sourceFile = new SourceFile("windows-integer-types.mc", source);
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);
        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> "pre=" + session.preprocessor().diagnostics()
                + ", lex=" + session.lexer().diagnostics()
                + ", parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics()
                + ", obj=" + session.objBuilder().diagnostics()
                + ", link=" + session.linker().diagnostics());
        var artifact = session.linker().result().executableArtifactOptional().orElseThrow();
        var execution = new ExecutableRunner().run(sourceFile, artifact, "");
        assertTrue(execution.diagnostics().isEmpty(), () -> execution.diagnostics().toString());
        assertEquals("", execution.stderr());
        assertEquals(0, execution.exitCode());
    }

    @Test
    void acceptsTypedIntegerConstantsAsArrayBounds() {
        SourceFile sourceFile = new SourceFile("typed-array-bound.mc", """
                int main() {
                    short values[2ULL];
                    return sizeof(values) == 4 ? 0 : 1;
                }
                """);
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);
        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> "parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics()
                + ", link=" + session.linker().diagnostics());
        var artifact = session.linker().result().executableArtifactOptional().orElseThrow();
        assertEquals(0, new ExecutableRunner().run(sourceFile, artifact, "").exitCode());
    }
}
