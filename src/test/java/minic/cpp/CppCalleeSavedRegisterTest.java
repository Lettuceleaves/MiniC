package minic.cpp;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.asm.AsmResult;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.optimize.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
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

@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppCalleeSavedRegisterTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 5);
    private static final List<String> NONVOLATILE = List.of("rbx", "r12", "r13", "r14", "r15");
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("byte", IrType.UNSIGNED_CHAR, "direct", 34),
                Arguments.of("short", IrType.SHORT, "direct", 34),
                Arguments.of("int", IrType.INT, "direct", 34),
                Arguments.of("wide", IrType.UNSIGNED_LONG_LONG, "direct", 34),
                Arguments.of("indirect", IrType.INT, "indirect", 34),
                Arguments.of("recursive", IrType.INT, "recursive", 84),
                Arguments.of("early-return", IrType.INT, "early", 28),
                Arguments.of("uninitialized-trap", IrType.INT, "uninitialized", 101),
                Arguments.of("zero-divisor-trap", IrType.INT, "zero", 102));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("programs")
    void nativeCallerRetainsFullNonvolatileRegistersOnEveryReturnPath(
            String name, IrType type, String mode, int expected) throws Exception {
        var ir = program(type, mode);
        var source = new SourceFile(name + ".cpp", reference(mode));
        IrVerifier.verify(ir);
        var plan = GlobalRegisterPlan.allocate(ir.functions().get(1), true);
        assertEquals(NONVOLATILE, plan.calleeSavedRegisters(), "fixture must use all five nonvolatile homes");
        var assembler = new Assembler(ir, new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of()));
        String assembly = assembler.assemble().text();
        assertTrue(assembler.succeeded(), () -> assembler.errors().toString());
        String work = assembly.substring(assembly.indexOf("minic$work PROC"), assembly.indexOf("minic$work ENDP"));
        for (String register : NONVOLATILE) {
            assertTrue(work.matches("(?s).*mov QWORD PTR \\[rbp-[0-9]+\\], " + register + "\\R.*"), "missing full-width prologue save: " + register + "\n" + work);
            String epilogue = work.substring(work.indexOf("minic$work$epilogue:"));
            assertTrue(epilogue.matches("(?s).*mov " + register + ", QWORD PTR \\[rbp-[0-9]+\\].*"), "missing full-width epilogue restore: " + register);
        }
        // Seven simultaneous survivors exhaust the five nonvolatile registers. Retain the
        // O08b typed volatile spill/reload contract under pressure, including short and byte.
        int callIndex = mode.equals("recursive") || mode.equals("early") ? 1 : 0;
        var spills = plan.spillsAt("recurse", callIndex);
        assertEquals(2, spills.size());
        int callText = work.indexOf(mode.equals("indirect") ? "call rax" : mode.equals("recursive") || mode.equals("early") ? "call minic$work" : "call minic$effect");
        for (var spilled : spills) {
            String reg = plan.registers().get(spilled.name());
            String width = switch (type.sizeBytes()) { case 1 -> "BYTE"; case 2 -> "WORD"; case 4 -> "DWORD"; default -> "QWORD"; };
            String suffix = switch (type.sizeBytes()) { case 1 -> "b"; case 2 -> "w"; case 4 -> "d"; default -> ""; };
            String reload = type.sizeBytes() < 4 ? (type.isSignedInteger() ? "movsx " : "movzx ") + reg + "d" : "mov " + reg + suffix;
            assertTrue(work.substring(0, callText).matches("(?s).*mov " + width + " PTR \\[rbp-[0-9]+\\], " + reg + suffix + "\\R.*"));
            assertTrue(work.substring(callText).matches("(?s).*" + reload + ", " + width + " PTR \\[rbp-[0-9]+\\].*"));
        }
        // These six arguments exercise the incoming rbp+ ABI and the outgoing stack tail.
        int first = mode.equals("recursive") ? 2 : 1;
        String caller = sentinelCaller(first, expected);
        int start = assembly.indexOf("main PROC");
        int end = assembly.indexOf("main ENDP", start) + "main ENDP".length();
        assembly = assembly.substring(0, start) + caller + assembly.substring(end);
        var builder = new ObjBuilder();
        var object = builder.build(source, new AsmResult("minic$entry", assembly), temporary, "sentinel");
        assertTrue(builder.succeeded(), () -> builder.errors().toString());
        var linker = new Linker(); linker.link(source, object, temporary, "sentinel");
        assertTrue(linker.succeeded(), () -> linker.errors().toString());
        var run = run(temporary.resolve("sentinel.exe"));
        assertEquals(0, run.exitCode(), "91=register corrupt, 92=result corrupt, 93=stack corrupt; " + run.stderr());
        assertSame(ir, assembler.input().irResult());
        assertTrue(assembler.optimizationResult().passNames().isEmpty());

        var debug = DebugApi.fromIr(source, ir, "");
        for (int steps = 0; debug.canNext() && steps < 20_000; steps++) debug.next();
        boolean trap = mode.equals("uninitialized") || mode.equals("zero");
        assertEquals(trap ? Debugger.Status.FAILED : Debugger.Status.COMPLETED, debug.current().stop().status());
        if (!trap) {
            assertEquals(expected, debug.current().runtime().termination().status());
            Path cpp = temporary.resolve("reference.cpp"), executable = temporary.resolve("reference.exe");
            Files.writeString(cpp, reference(mode));
            var compile = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()), "-std=c++17", "-O2",
                    cpp.toString(), "-o", executable.toString()), temporary, "", Duration.ofSeconds(20), 65_536);
            assertFalse(compile.timedOut()); assertFalse(compile.outputExceeded()); assertEquals(0, compile.exitCode(), compile::stderr);
            assertEquals(expected, run(executable).exitCode());
        }
        // Trap fixtures check MiniC's existing diagnostics; undefined C++ is never used as an oracle.
    }

    private BoundedProcess.Result run(Path executable) throws Exception {
        var result = BoundedProcess.run(List.of(executable.toString()), temporary, "", Duration.ofSeconds(5), 65_536);
        assertFalse(result.timedOut()); assertFalse(result.outputExceeded()); return result;
    }

    private static IrResult program(IrType type, String mode) {
        var params = new ArrayList<IrParameter>();
        for (int i = 0; i < 6; i++) params.add(new IrParameter("p" + i, MiniType.INT, IrType.INT, R));
        var values = new ArrayList<IrTemporary>();
        var entry = new ArrayList<IrInstruction>();
        for (int i = 0; i < 7; i++) {
            var value = new IrTemporary("value" + i, type); values.add(value);
            if (i == 5) entry.add(new IrCastInstruction(value, params.get(5).ref(), R));
            else entry.add(new IrMoveInstruction(value, new IrConstant(i + 1, type), R));
        }
        uses(entry, values, "before");
        var blocks = new ArrayList<IrBlock>();
        var after = new ArrayList<IrInstruction>();
        var answer = new IrTemporary("answer", IrType.INT);
        if (mode.equals("recursive") || mode.equals("early")) {
            var condition = new IrTemporary("condition", IrType.INT);
            entry.add(new IrBinaryInstruction(condition, IrBinaryOperator.LESS_EQUAL, params.get(0).ref(), new IrConstant(mode.equals("early") ? 1 : 0), R));
            entry.add(new IrBranchInstruction(condition, "base", "recurse", R));
            blocks.add(new IrBlock("entry", entry));
            var base = sum(values, type); blocks.add(new IrBlock("base", base));
            var next = new IrTemporary("next", IrType.INT);
            after.add(new IrBinaryInstruction(next, IrBinaryOperator.SUBTRACT, params.get(0).ref(), new IrConstant(1), R));
            var arguments = new ArrayList<IrValue>(); arguments.add(next);
            params.subList(1, 6).stream().map(IrParameter::ref).forEach(arguments::add);
            after.add(new IrCallInstruction(answer, "work", arguments, false, R));
        } else {
            entry.add(new IrJumpInstruction("recurse", R));
            blocks.add(new IrBlock("entry", entry));
            after.add(mode.equals("indirect")
                    ? new IrIndirectCallInstruction(answer, new IrFunctionAddress("effect"), List.of(params.getFirst().ref()), false, R)
                    : new IrCallInstruction(answer, "effect", List.of(params.getFirst().ref()), false, R));
        }
        if (mode.equals("zero")) after.add(new IrCheckNonZeroInstruction(new IrConstant(0), R));
        if (mode.equals("uninitialized")) {
            var local = new IrLocal("uninitialized", "uninitialized", MiniType.INT, IrType.INT, 4, 4, R);
            after.add(new IrDeclareLocalInstruction(local, R)); after.add(new IrCheckInitializedInstruction(local, R));
        }
        uses(after, values, "after");
        var sum = sum(values, type);
        var lastReturn = (IrReturnInstruction) sum.removeLast(); after.addAll(sum);
        var result = new IrTemporary("result", IrType.INT);
        after.add(new IrBinaryInstruction(result, IrBinaryOperator.ADD, lastReturn.value(), answer, R));
        after.add(new IrReturnInstruction(result, R)); blocks.add(new IrBlock("recurse", after));

        var work = new IrFunction("work", MiniType.INT, params, false, blocks, R);
        var mainResult = new IrTemporary("mainResult", IrType.INT);
        var arguments = new ArrayList<IrValue>();
        for (int i = 0; i < 6; i++) arguments.add(new IrConstant(i == 0 && mode.equals("recursive") ? 2 : i + 1));
        var main = new IrFunction("main", MiniType.INT, List.of(), false, List.of(new IrBlock("entry", List.of(
                new IrCallInstruction(mainResult, "work", arguments, false, R), new IrReturnInstruction(mainResult, R)))), R);
        var effectParam = new IrParameter("p", MiniType.INT, IrType.INT, R);
        var effectResult = new IrTemporary("effectResult", IrType.INT);
        var effect = new IrFunction("effect", MiniType.INT, List.of(effectParam), false, List.of(new IrBlock("entry", List.of(
                new IrBinaryInstruction(effectResult, IrBinaryOperator.ADD, effectParam.ref(), new IrConstant(5), R),
                new IrReturnInstruction(effectResult, R)))), R);
        return new IrResult(List.of(main, work, effect), List.of(), Set.of());
    }

    private static void uses(List<IrInstruction> code, List<IrTemporary> values, String prefix) {
        for (int i = 0; i < 3; i++) for (var value : values) code.add(new IrBinaryInstruction(
                new IrTemporary(prefix + i + value.name(), value.type()), IrBinaryOperator.ADD, value, new IrConstant(1, value.type()), R));
    }

    private static ArrayList<IrInstruction> sum(List<IrTemporary> values, IrType type) {
        var code = new ArrayList<IrInstruction>();
        var sum = new IrTemporary("sum", type); code.add(new IrMoveInstruction(sum, values.getFirst(), R));
        for (int i = 1; i < values.size(); i++) code.add(new IrBinaryInstruction(sum, IrBinaryOperator.ADD, sum, values.get(i), R));
        var exit = new IrTemporary("exit", IrType.INT); code.add(new IrCastInstruction(exit, sum, R));
        code.add(new IrReturnInstruction(exit, R)); return code;
    }

    private static String reference(String mode) {
        if (mode.equals("recursive")) return "int work(int n,int b,int c,int d,int e,int f){int s=1+2+3+4+5+f+7;return n<=0?s:s+work(n-1,b,c,d,e,f);}int main(){return work(2,2,3,4,5,6);}";
        if (mode.equals("early")) return "int work(int n,int b,int c,int d,int e,int f){return 1+2+3+4+5+f+7;}int main(){return work(1,2,3,4,5,6);}";
        return "int effect(int n){return n+5;}int work(int n,int b,int c,int d,int e,int f){int(*fn)(int)=effect;return 1+2+3+4+5+f+7+fn(n);}int main(){return work(1,2,3,4,5,6);}";
    }

    private static String sentinelCaller(int argument, int expected) {
        var body = new StringBuilder("main PROC\n    push rbp\n    mov rbp, rsp\n    sub rsp, 128\n");
        for (int i = 0; i < NONVOLATILE.size(); i++) {
            String reg = NONVOLATILE.get(i);
            body.append("    mov QWORD PTR [rbp-").append((i + 1) * 8).append("], ").append(reg).append('\n');
            body.append("    mov ").append(reg).append(", ").append(0x1122334455667700L + i).append('\n');
        }
        body.append("    mov ecx, ").append(argument).append("\n    mov edx, 2\n    mov r8d, 3\n    mov r9d, 4\n")
                .append("    mov DWORD PTR [rsp+32], 5\n    mov DWORD PTR [rsp+40], 6\n    call minic$work\n")
                .append("    cmp eax, ").append(expected).append("\n    jne main$bad_result\n");
        for (int i = 0; i < NONVOLATILE.size(); i++) body.append("    mov rax, ").append(0x1122334455667700L + i)
                .append("\n    cmp ").append(NONVOLATILE.get(i)).append(", rax\n    jne main$bad_register\n");
        body.append("    lea rax, [rbp-128]\n    cmp rsp, rax\n    jne main$bad_stack\n    mov eax, 0\n    jmp main$done\n")
                .append("main$bad_register:\n    mov eax, 91\n    jmp main$done\nmain$bad_result:\n    mov eax, 92\n    jmp main$done\n")
                .append("main$bad_stack:\n    mov eax, 93\nmain$done:\n");
        for (int i = 0; i < NONVOLATILE.size(); i++) body.append("    mov ").append(NONVOLATILE.get(i)).append(", QWORD PTR [rbp-").append((i + 1) * 8).append("]\n");
        return body.append("    mov rsp, rbp\n    pop rbp\n    ret\nmain ENDP").toString();
    }
}
