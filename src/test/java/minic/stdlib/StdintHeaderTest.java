package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class StdintHeaderTest {
    @Test
    void exposesExactLeastFastAndPointerSizedIntegerTypes() {
        String source = """
                #include "stdint.mh"

                int main() {
                    int8_t i8 = INT8_MIN;
                    uint8_t u8 = UINT8_MAX;
                    int16_t i16 = INT16_MIN;
                    uint16_t u16 = UINT16_MAX;
                    int32_t i32 = INT32_MIN;
                    uint32_t u32 = UINT32_MAX;
                    int64_t i64 = INT64_MIN;
                    uint64_t u64 = UINT64_MAX;
                    intptr_t pointerSized = INTPTR_MAX;
                    uintptr_t unsignedPointerSized = UINTPTR_MAX;
                    if (sizeof(i8) != 1 || sizeof(u8) != 1) return 1;
                    if (sizeof(i16) != 2 || sizeof(u16) != 2) return 2;
                    if (sizeof(i32) != 4 || sizeof(u32) != 4) return 3;
                    if (sizeof(i64) != 8 || sizeof(u64) != 8) return 4;
                    if (sizeof(pointerSized) != 8 || sizeof(unsignedPointerSized) != 8) return 5;
                    if (i8 != -128 || u8 != 255U || i16 != -32768 || u16 != 65535U) return 6;
                    if (i32 != (-2147483647 - 1) || u32 != 4294967295U) return 7;
                    if (i64 != (-9223372036854775807LL - 1LL) || u64 != ~0ULL) return 8;
                    if (INT_FAST8_WIDTH != 32 || UINT_FAST16_WIDTH != 32 || SIZE_WIDTH != 64) return 9;
                    if (INT64_C(7) != 7LL || UINT64_C(9) != 9ULL) return 10;
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("stdint-e2e.mc", source);
        CompilerFixture session = CompilerFixture.fromSource(sourceFile);

        session.compilerApi().runThrough(session.linker());

        assertTrue(session.linker().succeeded(), () -> "pre=" + session.preprocessor().errors()
                + ", lex=" + session.lexer().errors()
                + ", parse=" + session.parser().errors()
                + ", semantic=" + session.semanticAnalyzer().errors()
                + ", obj=" + session.objBuilder().errors()
                + ", link=" + session.linker().errors());
        var artifact = session.linker().result().executableArtifactOptional().orElseThrow();
        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(sourceFile, artifact, "");
        assertTrue(executionStage.errors().isEmpty(), () -> executionStage.errors().toString());
        assertEquals(0, execution.exitCode(), execution::stderr);
    }
}
