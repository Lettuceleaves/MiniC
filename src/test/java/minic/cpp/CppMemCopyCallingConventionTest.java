package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.asm.AsmResult;
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30) @Execution(ExecutionMode.SAME_THREAD)
final class CppMemCopyCallingConventionTest {
    @TempDir Path temporary;

    static Stream<Arguments> copies() {
        return Stream.of(OptimizationLevel.BASELINE, OptimizationLevel.OPTIMIZED).flatMap(level ->
                Stream.of(false, true).flatMap(aggregate -> Stream.of("rsi", "rdi")
                        .map(register -> Arguments.of(level, aggregate, register))));
    }

    @ParameterizedTest(name = "{0}: aggregate={1}, preserved={2}") @MethodSource("copies")
    void actualNativeCallerKeepsNonvolatileRegistersAndGetsTheCopiedBytes(
            OptimizationLevel level, boolean aggregate, String register) throws Exception {
        String body = aggregate ? "*to=*from;" : "to->x=from->x;to->y=from->y;";
        var source = new SourceFile("copy.c", "struct Pair{int x;int y;};void copy(struct Pair *to,struct Pair *from){"
                + body + "}int main(){struct Pair from={7,9};struct Pair to;copy(&to,&from);return to.x;}");
        var ir = new CompilerApi(source).runToIr();
        // OPTIMIZED selects native allocation. The empty pass list retains this callee for the ABI probe.
        var assembler = new Assembler(ir, new IrOptimizationPipeline(level, List.of()));
        String assembly = assembler.assemble().text();
        assertTrue(assembler.succeeded(), () -> assembler.errors().toString());
        assertEquals(aggregate && level == OptimizationLevel.BASELINE, assembly.contains("rep movsb"));

        // A source-only test cannot keep a live value in a specific physical register. Replace
        // only main with an ABI caller; copy remains exactly the production compiler's output.
        int start = assembly.indexOf("main PROC");
        int end = assembly.indexOf("main ENDP", start) + "main ENDP".length();
        assertTrue(start >= 0 && end > start);
        String caller = """
                main PROC
                    push rbp
                    mov rbp, rsp
                    sub rsp, 64
                    mov DWORD PTR [rbp-8], 7
                    mov DWORD PTR [rbp-4], 9
                    mov rsi, 287454020
                    mov rdi, 1432778632
                    lea rcx, [rbp-16]
                    lea rdx, [rbp-8]
                    call minic$copy
                    cmp %s, %s
                    jne main$bad_register
                    cmp DWORD PTR [rbp-16], 7
                    jne main$bad_copy
                    cmp DWORD PTR [rbp-12], 9
                    jne main$bad_copy
                    lea rax, [rbp-64]
                    cmp rsp, rax
                    jne main$bad_stack
                    mov eax, 0
                    jmp main$done
                main$bad_register:
                    mov eax, 91
                    jmp main$done
                main$bad_copy:
                    mov eax, 92
                    jmp main$done
                main$bad_stack:
                    mov eax, 93
                main$done:
                    mov rsp, rbp
                    pop rbp
                    ret
                main ENDP
                """.formatted(register, register.equals("rsi") ? "287454020" : "1432778632");
        assembly = assembly.substring(0, start) + caller + assembly.substring(end);
        var builder = new ObjBuilder();
        var object = builder.build(source, new AsmResult("minic$entry", assembly), temporary, "probe");
        assertTrue(builder.succeeded(), () -> builder.errors().toString());
        var linker = new Linker();
        linker.link(source, object, temporary, "probe");
        assertTrue(linker.succeeded(), () -> linker.errors().toString());
        var run = BoundedProcess.run(List.of(temporary.resolve("probe.exe").toString()), temporary, "",
                Duration.ofSeconds(5), 65_536);
        assertFalse(run.timedOut());
        assertFalse(run.outputExceeded());
        assertEquals(0, run.exitCode(), "91=nonvolatile register lost, 92=copy corrupt, 93=stack unbalanced; " + run.stderr());
    }
}
