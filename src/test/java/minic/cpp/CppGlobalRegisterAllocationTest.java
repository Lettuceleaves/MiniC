package minic.cpp;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.optimize.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential") @Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppGlobalRegisterAllocationTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 5);
    private static final IrConstant ONE = new IrConstant(1);
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        var value = temp("value"); var condition = temp("condition"); var scratch = temp("scratch");
        var joined = temp("joined");
        return Stream.of(
                Arguments.of("direct-call-same-name-result", callProgram(false),
                        "int effect(int value){return value+5;}int main(){int value=1;int survives=9;value=effect(value);return value+survives;}", 15, true),
                Arguments.of("indirect-call-same-name-result", callProgram(true),
                        "int effect(int value){return value+5;}int main(){int value=1;int survives=9;int(*fn)(int)=effect;value=fn(value);return value+survives;}", 15, true),
                Arguments.of("direct-call-byte-survivor", callProgram(false, IrType.UNSIGNED_CHAR, 250),
                        "unsigned char effect(unsigned char value){return (unsigned char)(value+5);}int main(){unsigned char value=1;unsigned char survives=250;value=effect(value);return (unsigned char)(value+survives);}", 0, true),
                Arguments.of("direct-call-short-survivor", callProgram(false, IrType.SHORT, 32000),
                        "short effect(short value){return (short)(value+5);}int main(){short value=1;short survives=32000;value=effect(value);return (short)(value+survives);}", 32006, true),
                Arguments.of("indirect-call-wide-survivor", callProgram(true, IrType.UNSIGNED_LONG_LONG, 4294967301L),
                        "unsigned long long effect(unsigned long long value){return value+5ULL;}int main(){unsigned long long value=1,survives=4294967301ULL;unsigned long long(*fn)(unsigned long long)=effect;value=fn(value);return (int)((value+survives)&255ULL);}", 11, true),
                Arguments.of("indirect-register-callee", registerCalleeProgram(),
                        "int effect(int value){return value+5;}int main(){int(*fn)(int)=effect;int argument=1;return fn(argument);}", 6, false),
                Arguments.of("backedge-survives-a-later-dead-write", result(function("main", List.of(), List.of(
                        block("entry", new IrJumpInstruction("body", R)),
                        block("header", new IrBinaryInstruction(condition, IrBinaryOperator.EQUAL, value, ONE, R), new IrBranchInstruction(condition, "done", "body", R)),
                        block("body", new IrMoveInstruction(value, ONE, R), new IrMoveInstruction(scratch, new IrConstant(9), R), new IrJumpInstruction("header", R)),
                        block("done", new IrReturnInstruction(new IrConstant(0), R))))),
                        "int main(){int value;do{value=1;int scratch=9;}while(value!=1);return 0;}", 0, false),
                Arguments.of("non-ssa-join", result(function("main", List.of(), List.of(
                        block("entry", new IrBranchInstruction(new IrConstant(0), "left", "right", R)),
                        block("left", new IrMoveInstruction(joined, new IrConstant(13), R), new IrJumpInstruction("done", R)),
                        block("right", new IrMoveInstruction(joined, new IrConstant(17), R), new IrJumpInstruction("done", R)),
                        block("done", new IrReturnInstruction(joined, R))))),
                        "int main(){int joined;if(0)joined=13;else joined=17;return joined;}", 17, false));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("programs")
    void nativePlacementMatchesOriginalIrDebugAndTheEquivalentCppProgram(
            String name, IrResult ir, String referenceSource, int expected, boolean requireSpill) throws Exception {
        var source = new SourceFile(name + ".cpp", referenceSource);
        IrVerifier.verify(ir);
        for (OptimizationLevel level : OptimizationLevel.values()) {
            var assembler = new Assembler(ir, new IrOptimizationPipeline(level, List.of()));
            String assembly = assembler.assemble().text();
            assertTrue(assembler.succeeded(), () -> assembler.errors().toString());
            if (level == OptimizationLevel.OPTIMIZED) {
                var plan = GlobalRegisterPlan.allocate(ir.functions().getFirst());
                assertFalse(plan.registers().isEmpty(), "fixture must use register homes");
                if (requireSpill) {
                    String call = name.startsWith("indirect") ? "call rax" : "call minic$effect";
                    String caller = assembly.substring(assembly.indexOf("main PROC"), assembly.indexOf("main ENDP"));
                    int callIndex = caller.indexOf(call);
                    IrType type = plan.temporaryTypes().get("survives");
                    String width = switch(type.sizeBytes()) { case 1 -> "BYTE"; case 2 -> "WORD"; case 8 -> "QWORD"; default -> "DWORD"; };
                    String suffix = switch(type.sizeBytes()) { case 1 -> "b"; case 2 -> "w"; case 8 -> ""; default -> "d"; };
                    String returnRegister = switch(type.sizeBytes()) { case 1 -> "al"; case 2 -> "ax"; case 8 -> "rax"; default -> "eax"; };
                    String restore = type.sizeBytes() < 4 ? (type.isSignedInteger() ? "movsx r11d" : "movzx r11d") : "mov r11" + suffix;
                    // The save precedes argument/callee setup; the restore follows result placement.
                    assertTrue(caller.substring(0, callIndex).matches("(?s).*mov " + width + " PTR \\[rbp-[0-9]+\\], r11" + suffix + "\\R.*"), caller);
                    assertTrue(caller.substring(callIndex).matches("(?s)" + java.util.regex.Pattern.quote(call)
                            + "\\R    mov r10" + suffix + ", " + returnRegister + "\\R    " + restore + ", " + width + " PTR \\[rbp-[0-9]+\\].*"), caller);
                    String callee = assembly.substring(assembly.indexOf("minic$effect PROC"));
                    assertTrue(callee.contains("mov r10" + suffix + ",") && callee.contains("mov r11" + suffix + ","), "callee must really clobber both allocated registers");
                }
                if (name.equals("indirect-register-callee")) {
                    String home = plan.registers().get("callee");
                    assertTrue(Set.of("r10", "r11").contains(home));
                    assertTrue(assembly.contains("mov rax, " + home + System.lineSeparator() + "    call rax"), assembly);
                }
            }
            Path directory = temporary.resolve(level.name());
            var obj = new ObjBuilder(source, assembler, directory, "program");
            var linker = new Linker(source, obj, directory, "program");
            new CompilerApi(List.of(obj, linker)).runThrough(linker);
            assertTrue(linker.succeeded(), () -> "obj=" + obj.errors() + ", link=" + linker.errors());
            var run = BoundedProcess.run(List.of(directory.resolve("program.exe").toString()), temporary, "", Duration.ofSeconds(5), 65_536);
            assertFalse(run.timedOut()); assertFalse(run.outputExceeded()); assertEquals(expected, run.exitCode(), run::stderr);
            assertSame(ir, assembler.input().irResult());
        }
        var debug = DebugApi.fromIr(source, ir, "");
        for (int step = 0; debug.canNext() && step < 10_000; step++) debug.next();
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(expected, debug.current().runtime().termination().status());

        Path cpp = temporary.resolve("reference.cpp"), executable = temporary.resolve("reference.exe");
        Files.writeString(cpp, referenceSource);
        var compile = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()), "-std=c++17", "-O2",
                cpp.toString(), "-o", executable.toString()), temporary, "", Duration.ofSeconds(20), 65_536);
        assertFalse(compile.timedOut()); assertFalse(compile.outputExceeded()); assertEquals(0, compile.exitCode(), compile::stderr);
        var reference = BoundedProcess.run(List.of(executable.toString()), temporary, "", Duration.ofSeconds(5), 65_536);
        assertFalse(reference.timedOut()); assertFalse(reference.outputExceeded()); assertEquals(expected, reference.exitCode());
    }

    private static IrResult callProgram(boolean indirect) {
        return callProgram(indirect, IrType.INT, 9);
    }
    private static IrResult callProgram(boolean indirect, IrType type, long saved) {
        MiniType sourceType = switch (type) { case UNSIGNED_CHAR -> MiniType.UNSIGNED_CHAR; case SHORT -> MiniType.SHORT; case UNSIGNED_LONG_LONG -> MiniType.UNSIGNED_LONG_LONG; default -> MiniType.INT; };
        var one = new IrConstant(1, type);
        var value = new IrTemporary("value", type); var survives = new IrTemporary("survives", type); var total = new IrTemporary("total", type);
        var instructions = new ArrayList<IrInstruction>();
        instructions.add(new IrMoveInstruction(value, one, R)); instructions.add(new IrMoveInstruction(survives, new IrConstant(saved, type), R));
        for (int i = 0; i < 3; i++) instructions.add(new IrBinaryInstruction(new IrTemporary("before" + i, type), IrBinaryOperator.ADD, survives, one, R));
        instructions.add(indirect ? new IrIndirectCallInstruction(value, new IrFunctionAddress("effect"), List.of(value), false, R)
                : new IrCallInstruction(value, "effect", List.of(value), false, R));
        for (int i = 0; i < 3; i++) instructions.add(new IrBinaryInstruction(new IrTemporary("after" + i, type), IrBinaryOperator.ADD, survives, one, R));
        instructions.add(new IrBinaryInstruction(total, IrBinaryOperator.ADD, value, survives, R));
        if (type == IrType.UNSIGNED_LONG_LONG) instructions.add(new IrBinaryInstruction(total, IrBinaryOperator.BITWISE_AND, total, new IrConstant(255, type), R));
        if (type != IrType.INT) {
            var exit = temp("exit"); instructions.add(new IrCastInstruction(exit, total, R)); instructions.add(new IrReturnInstruction(exit, R));
        } else instructions.add(new IrReturnInstruction(total, R));
        var parameter = new IrParameter("value", sourceType, type, R); var argument = new IrTemporary("argument", type); var answer = new IrTemporary("answer", type);
        return new IrResult(List.of(function("main", List.of(), List.of(new IrBlock("entry", instructions))),
                new IrFunction("effect", sourceType, List.of(parameter), false, List.of(block("entry", new IrMoveInstruction(argument, parameter.ref(), R),
                        new IrBinaryInstruction(answer, IrBinaryOperator.ADD, argument, new IrConstant(5, type), R), new IrReturnInstruction(answer, R))), R)), List.of(), Set.of());
    }
    private static IrResult registerCalleeProgram() {
        var callee = new IrTemporary("callee", IrType.POINTER); var argument = temp("argument"); var answer = temp("answer");
        return new IrResult(List.of(function("main", List.of(), List.of(block("entry",
                new IrMoveInstruction(callee, new IrFunctionAddress("effect"), R), new IrMoveInstruction(argument, ONE, R),
                new IrIndirectCallInstruction(answer, callee, List.of(argument), false, R), new IrReturnInstruction(answer, R)))),
                callProgram(false).functions().get(1)), List.of(), Set.of());
    }
    private static IrTemporary temp(String name) { return new IrTemporary(name, IrType.INT); }
    private static IrBlock block(String name, IrInstruction... instructions) { return new IrBlock(name, List.of(instructions)); }
    private static IrFunction function(String name, List<IrParameter> parameters, List<IrBlock> blocks) { return new IrFunction(name, MiniType.INT, parameters, false, blocks, R); }
    private static IrResult result(IrFunction function) { return new IrResult(List.of(function), List.of(), Set.of()); }
}
