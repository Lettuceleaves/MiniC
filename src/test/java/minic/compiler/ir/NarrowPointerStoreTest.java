package minic.compiler.ir;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
final class NarrowPointerStoreTest {
    @Test
    void castsPointerIndexAndFieldStoresToTheirTargetIrTypes() {
        CompilerFixture session = CompilerFixture.fromSource(sourceFile());
        IrResult ir = session.compilerApi().runToIr();

        Set<IrType> storedTypes = ir.findFunction("main").orElseThrow().blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(IrStorePointerInstruction.class::isInstance)
                .map(IrStorePointerInstruction.class::cast)
                .map(store -> store.value().type())
                .collect(Collectors.toSet());

        assertEquals(Set.of(IrType.CHAR, IrType.SHORT, IrType.FLOAT, IrType.POINTER), storedTypes);
        assertFalse(storedTypes.contains(IrType.INT), "char/short stores must not retain promoted int width");
        assertFalse(storedTypes.contains(IrType.DOUBLE), "float stores must not retain double width");
    }

    @Test
    void preservesAdjacentGuardValuesNativelyAndInTheDebugger() {
        SourceFile source = sourceFile();
        CompilerFixture nativeSession = CompilerFixture.fromSource(source);
        nativeSession.compilerApi().runThrough(nativeSession.linker());
        assertTrue(nativeSession.linker().succeeded(), () -> "pre=" + nativeSession.preprocessor().errors()
                + ", lex=" + nativeSession.lexer().errors()
                + ", parse=" + nativeSession.parser().errors()
                + ", semantic=" + nativeSession.semanticAnalyzer().errors()
                + ", obj=" + nativeSession.objBuilder().errors()
                + ", link=" + nativeSession.linker().errors());

        var nativeExecutionStage = new ExecutableRunner();
        var nativeExecution = nativeExecutionStage.run(
                source,
                nativeSession.linker().result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(nativeExecutionStage.errors().isEmpty(), nativeExecutionStage.errors()::toString);
        assertEquals(0, nativeExecution.exitCode(), nativeExecution::stderr);

        DebugApi debug = new DebugApi(source);
        int remaining = 10_000;
        while (debug.canNext() && remaining-- > 0) {
            debug.next();
        }
        assertFalse(debug.canNext(), "debugger did not complete within the step budget");
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop().error());
        assertEquals(0, debug.current().runtime().returnValue().integer());
    }

    private static SourceFile sourceFile() {
        return new SourceFile("narrow-pointer-stores.mc", """
                struct FloatPair {
                    float value;
                    float guard;
                };

                struct BytePair {
                    char value;
                    char guard;
                };

                int main(void) {
                    char characters[3];
                    characters[0] = 11;
                    characters[1] = 22;
                    characters[2] = 33;
                    char *middle = &characters[1];
                    *middle = 52;
                    if (characters[0] != 11 || characters[1] != 52 || characters[2] != 33) return 1;

                    char *selected = &characters[1];
                    char **selection = &selected;
                    char *replacement = &characters[0];
                    *selection = replacement;
                    if (selected != &characters[0]) return 6;

                    struct BytePair bytes;
                    bytes.value = 12;
                    bytes.guard = 34;
                    bytes.value = 56;
                    if (bytes.value != 56 || bytes.guard != 34) return 4;

                    short words[3];
                    words[0] = 111;
                    words[1] = 222;
                    words[2] = 333;
                    words[1] = 4660;
                    if (words[0] != 111 || words[1] != 4660 || words[2] != 333) return 2;

                    struct FloatPair pair;
                    float initial_value = 1.0f;
                    float initial_guard = 3.0f;
                    pair.value = initial_value;
                    pair.guard = initial_guard;
                    pair.value = 1.5;
                    if (pair.value != 1.5f || pair.guard != 3.0f) return 3;

                    float floating_values[2];
                    float pointer_guard = 7.0f;
                    floating_values[1] = pointer_guard;
                    float *float_pointer = &floating_values[0];
                    *float_pointer = 1.25f;
                    if (floating_values[0] != 1.25f || floating_values[1] != 7.0f) return 5;
                    return 0;
                }
                """);
    }
}
