package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator;
import minic.compiler.ir.instruction.ComputeInstruction.IrCastInstruction;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.optimize.IrVerifier;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppDifferentialHarness.Backend;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit conversions use the Windows x64 representation; synthetic addresses are never dereferenced. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppPointerCastTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("integer-to-pointer-local", """
                        #include <stdio.h>
                        int main(){int *p=(int*)1;printf("%d\\n",p!=0);return 0;}
                        """, "1\n", true, false, false),
                Arguments.of("pointer-to-wide-integer", """
                        #include <stdio.h>
                        int main(){int value=1;int *p=&value;unsigned long long bits=(unsigned long long)p;
                            printf("%d\\n",bits!=0);return 0;}
                        """, "1\n", false, true, false),
                Arguments.of("converted-pointer-call-argument", """
                        #include <stdio.h>
                        int accepts(int *p){return p!=0;}
                        int main(){printf("%d\\n",accepts((int*)1));return 0;}
                        """, "1\n", true, false, false),
                Arguments.of("signed-integer-to-pointer-extends-before-conversion", """
                        #include <stdio.h>
                        int main(){int number=-1;int *p=(int*)number;
                            printf("%llu\\n",(unsigned long long)p);return 0;}
                        """, "18446744073709551615\n", true, true, false),
                Arguments.of("unsigned-integer-to-pointer-does-not-sign-extend", """
                        #include <stdio.h>
                        int main(){unsigned int number=0xffffffffu;int *p=(int*)number;
                            printf("%llu\\n",(unsigned long long)p);return 0;}
                        """, "4294967295\n", true, true, false),
                Arguments.of("pointer-to-bool-tests-all-address-bits", """
                        #include <stdio.h>
                        int main(){int *p=(int*)256;bool present=(bool)p;bool absent=(bool)((int*)0);
                            printf("%d %d\\n",present?1:0,absent?1:0);return 0;}
                        """, "1 0\n", true, false, true)
        );
    }

    @ParameterizedTest(name = "IR: {0}")
    @MethodSource("programs")
    void explicitConversionsRetainTargetTypesForVerification(String name, String source, String expected,
                                                            boolean toPointer, boolean toInteger, boolean toBool) {
        var ir = new CompilerApi(new SourceFile(name + ".cpp", source), LanguageMode.CPP17_ALGORITHM).runToIr();
        var instructions = ir.functions().stream().flatMap(function -> function.blocks().stream())
                .flatMap(block -> block.instructions().stream()).toList();
        if (toPointer) assertTrue(instructions.stream().anyMatch(instruction -> instruction instanceof IrCastInstruction cast
                        && cast.result().type() == IrType.POINTER && cast.value().type().isIntegerScalar()),
                "The explicit integer-to-pointer conversion must produce a pointer-typed result");
        if (toInteger) assertTrue(instructions.stream().anyMatch(instruction -> instruction instanceof IrCastInstruction cast
                        && cast.result().type() == IrType.UNSIGNED_LONG_LONG && cast.value().type() == IrType.POINTER),
                "The explicit pointer-to-integer conversion must produce an integer-typed result");
        if (toBool) assertTrue(instructions.stream().anyMatch(instruction -> instruction instanceof IrBinaryInstruction binary
                        && binary.result().type() == IrType.BOOL && binary.operator() == IrBinaryOperator.NOT_EQUAL
                        && binary.left().type() == IrType.POINTER && binary.right().type() == IrType.POINTER),
                "Pointer-to-bool must compare the full address against null");
        assertDoesNotThrow(() -> IrVerifier.verify(ir));
    }

    @ParameterizedTest(name = "execution: {0}")
    @MethodSource("programs")
    void explicitConversionsAgreeAcrossNativeDebugAndCpp17(String name, String source, String expected,
                                                         boolean toPointer, boolean toInteger, boolean toBool) throws Exception {
        var report = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(),
                LanguageMode.CPP17_ALGORITHM).run(name, source, "");
        assertEquals(CppDifferentialHarness.Status.OK, report.outcomes().get(Backend.GXX).status(), report::describe);
        assertEquals(expected, report.outcomes().get(Backend.GXX).stdout().replace("\r\n", "\n"), report::describe);
        assertTrue(report.passed(), report::describe);
        for (var outcome : report.outcomes().values())
            assertEquals(expected, outcome.stdout().replace("\r\n", "\n"), report::describe);
    }
}
