package minic.cpp;

import minic.SourceRange;
import minic.compiler.*;
import minic.compiler.asm.*;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.IrMemCopyInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.optimize.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjResult;
import minic.compiler.obj.assembler.WindowsX64MachineAssembler;
import minic.compiler.obj.coff.CoffObjectWriter;
import minic.compiler.obj.machine.*;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppSmallObjectCopyTest {
    private static final SourceRange R = new SourceRange(1, 0, 1, 12);
    private static final List<String> NONVOLATILE = List.of("rbx", "rsi", "rdi", "r12", "r13", "r14", "r15");
    @TempDir Path temporary;

    static Stream<Arguments> copies() {
        var cases = new ArrayList<Arguments>();
        for (var level : OptimizationLevel.values()) {
            for (int size = 1; size <= 16; size++)
                cases.add(Arguments.of(level, size, List.of("separate", "self", "forward", "backward").get((size - 1) % 4), false));
            cases.add(Arguments.of(level, 16, "forward", false));
            cases.add(Arguments.of(level, 16, "forward", true));
            cases.add(Arguments.of(level, 17, "forward", false));
            cases.add(Arguments.of(level, 64, "separate", false));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}: size={1}, {2}, volatile={3}") @MethodSource("copies")
    void nativeCopyPreservesExactBytesAllNonvolatileRegistersAndLiveTemporaryHomes(
            OptimizationLevel level, int size, String shape, boolean vol) throws Exception {
        var ir = copyProgram(size, vol);
        IrVerifier.verify(ir);
        var plan = GlobalRegisterPlan.allocate(ir.functions().get(1), true);
        assertEquals(Set.of("r10", "r11", "rbx", "r12", "r13", "r14", "r15"),
                IntStream.range(0, 7).mapToObj(index -> plan.registers().get("live" + index)).collect(Collectors.toSet()));
        var assembler = new Assembler(ir, new IrOptimizationPipeline(level, List.of()));
        String assembly = assembler.assemble().text();
        assertTrue(assembler.succeeded(), () -> assembler.errors().toString());
        boolean snapshot = level == OptimizationLevel.OPTIMIZED && !vol && size <= 16;
        assertEquals(!snapshot, assembly.contains("rep movsb"));
        int start = assembly.indexOf("main PROC"), end = assembly.indexOf("main ENDP", start) + "main ENDP".length();
        assertTrue(start >= 0 && end > start);
        assembly = assembly.substring(0, start) + sentinelCaller(size, shape, snapshot) + assembly.substring(end);
        var source = new SourceFile("copy-ir.c", "int main(){return 0;}");
        var result = runNative(source, new AsmResult("minic$entry", assembly), temporary, "copy");
        assertEquals(0, result.exitCode(), "91=nonvolatile lost, 92=bytes corrupt, 93=stack corrupt, 94=live temporary corrupt; " + result.stderr());
        assertSame(ir, assembler.input().irResult());
        assertTrue(assembler.optimizationResult().passNames().isEmpty());
    }

    @Test void nativeSentinelDetectsCorruptionConfinedToTheUpperHalfOfAnXmmRegister() throws Exception {
        var assembler = new Assembler(copyProgram(16, false), new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of()));
        String assembly = assembler.assemble().text();
        assertTrue(assembler.succeeded(), () -> assembler.errors().toString());
        // MOVQ preserves the expected low half while zeroing the high half. A 64-bit-only
        // sentinel would miss this ABI violation. Keep the callee's return value intact.
        assembly = assembly.replace("minic$copy$epilogue:", "minic$copy$epilogue:\n    push rax\n    mov rax, "
                + sentinel(16) + "\n    movq xmm15, rax\n    pop rax");
        int start = assembly.indexOf("main PROC"), end = assembly.indexOf("main ENDP", start) + "main ENDP".length();
        assembly = assembly.substring(0, start) + sentinelCaller(16, "separate", true) + assembly.substring(end);
        var result = runNative(new SourceFile("sentinel.c", "int main(){return 0;}"),
                new AsmResult("minic$entry", assembly), temporary, "sentinel");
        assertEquals(91, result.exitCode(), result::stderr);
    }

    static Stream<Arguments> sourcePrograms() {
        return Stream.of(LanguageMode.C, LanguageMode.CPP17_ALGORITHM).flatMap(mode ->
                Stream.of(OptimizationLevel.BASELINE, OptimizationLevel.OPTIMIZED).flatMap(level ->
                        Stream.of("byte-objects", "union-byte-objects", "aggregate-arguments-and-return").map(name -> Arguments.of(mode, level, name))));
    }

    @ParameterizedTest(name = "{0}/{1}: {2}") @MethodSource("sourcePrograms")
    void definedAggregateCopiesAgreeWithSourceDebugAndGxx(LanguageMode mode, OptimizationLevel level, String name) throws Exception {
        boolean byteObjects = name.endsWith("byte-objects");
        String text = byteObjects ? byteObjects(name.equals("union-byte-objects")) : """
                #include <stdio.h>
                struct Pair { double x; unsigned long long y; };
                struct Pair relay(struct Pair p,int n){struct Pair out=p;out.y+=n;return out;}
                int main(){struct Pair a={1.25,1234567890123ULL};struct Pair b=relay(a,7);
                    struct Pair c=b;c=c;printf("%.2f %llu %.2f %llu\\n",a.x,a.y,c.x,c.y);return 0;}
                """;
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), mode, level).run(name, text, "");
        assertTrue(report.passed(), report::describe);
        String expected = byteObjects ? "9435\n" : "1.25 1234567890123 1.25 1234567890130\n";
        report.outcomes().values().forEach(outcome -> assertEquals(expected, outcome.stdout().replace("\r\n", "\n")));
        var source = new SourceFile(name + ".cpp", text);
        var ir = new CompilerApi(source, mode).runToIr();
        // [class.copy.assign]/12 specifies memberwise assignment for non-union C++
        // classes; it need not become a representation-copy IR instruction. Keep the
        // original source and output check, while the union variant exercises the
        // representation-copy contract in /13 through every 1..17-byte boundary.
        // https://timsong-cpp.github.io/cppwp/n4659/class.copy.assign#12
        // https://timsong-cpp.github.io/cppwp/n4659/class.copy.assign#13
        if (mode == LanguageMode.C || !name.equals("byte-objects")) {
            var copySizes = ir.functions().stream().flatMap(f -> f.blocks().stream()).flatMap(b -> b.instructions().stream())
                    .filter(IrMemCopyInstruction.class::isInstance).map(IrMemCopyInstruction.class::cast)
                    .map(IrMemCopyInstruction::sizeBytes).collect(Collectors.toSet());
            assertFalse(copySizes.isEmpty(), "fixture must exercise aggregate copy IR");
            if (byteObjects) assertTrue(copySizes.containsAll(IntStream.rangeClosed(1, 17).boxed().toList()),
                    () -> "source representation copies must cover every size 1..17: " + copySizes);
        }
    }

    private static String byteObjects(boolean union) {
        var source = new StringBuilder("#include <stdio.h>\n");
        String recordKey = union ? "union" : "struct";
        for (int size = 1; size <= 17; size++) source.append(recordKey).append(" Bytes").append(size).append("{unsigned char data[").append(size)
                .append("];};struct Holder").append(size).append("{char prefix;").append(recordKey).append(" Bytes").append(size).append(" value;};\n");
        source.append("int main(){int total=0;\n");
        for (int size = 1; size <= 17; size++) {
            // Struct alignment is one. The actual struct objects are deliberately preceded by
            // a char, rather than casting a char buffer to an object with a different lifetime.
            source.append("{struct Holder").append(size).append(" from;struct Holder").append(size).append(" to;")
                    .append("for(int i=0;i<").append(size).append(";i++)from.value.data[i]=(unsigned char)(i*11+3);")
                    .append("to.value=from.value;to.value=to.value;")
                    .append("for(int j=0;j<").append(size).append(";j++)total+=to.value.data[j];}\n");
        }
        return source.append("printf(\"%d\\n\",total);return 0;}\n").toString();
    }

    private static IrResult copyProgram(int size, boolean vol) {
        var to = new IrParameter("to", MiniType.INT.pointerTo(), IrType.POINTER, R);
        var from = new IrParameter("from", MiniType.INT.pointerTo(), IrType.POINTER, R);
        var values = new ArrayList<IrTemporary>();
        var code = new ArrayList<IrInstruction>();
        for (int index = 0; index < 7; index++) {
            var value = new IrTemporary("live" + index, IrType.UNSIGNED_LONG_LONG); values.add(value);
            code.add(new IrMoveInstruction(value, new IrConstant(sentinel(index), value.type()), R));
        }
        // Make all seven homes worthwhile, then keep them simultaneously live across copy.
        for (int round = 0; round < 3; round++) for (var value : values)
            code.add(new IrBinaryInstruction(new IrTemporary("use" + round + value.name(), value.type()),
                    IrBinaryOperator.ADD, value, new IrConstant(1, value.type()), R));
        code.add(new IrMemCopyInstruction(to.ref(), from.ref(), size, vol, R));
        var blocks = new ArrayList<IrBlock>();
        for (int index = 0; index < 7; index++) {
            var check = new IrTemporary("ok" + index, IrType.INT);
            code.add(new IrBinaryInstruction(check, IrBinaryOperator.EQUAL, values.get(index),
                    new IrConstant(sentinel(index), IrType.UNSIGNED_LONG_LONG), R));
            code.add(new IrBranchInstruction(check, index == 6 ? "success" : "check" + (index + 1), "failure", R));
            blocks.add(new IrBlock(index == 0 ? "entry" : "check" + index, code)); code = new ArrayList<>();
        }
        blocks.add(new IrBlock("success", List.of(new IrReturnInstruction(new IrConstant(77), R))));
        blocks.add(new IrBlock("failure", List.of(new IrReturnInstruction(new IrConstant(79), R))));
        var copy = new IrFunction("copy", MiniType.INT, List.of(to, from), false, blocks, R);
        var main = new IrFunction("main", MiniType.INT, List.of(), false,
                List.of(new IrBlock("entry", List.of(new IrReturnInstruction(new IrConstant(0), R)))), R);
        return new IrResult(List.of(main, copy), List.of(), Set.of());
    }

    private static long sentinel(int index) { return 0x1122334455667700L + index; }

    private static String sentinelCaller(int size, String shape, boolean snapshot) {
        int source = 9;
        int destination = switch (shape) { case "self" -> source; case "forward" -> source + 1; case "backward" -> source - 1; default -> 97; };
        byte[] bytes = new byte[192];
        for (int index = 0; index < bytes.length; index++) bytes[index] = (byte) (index * 37 + 19);
        for (int index = 0; index < 8; index++) {
            bytes[source + index] = (byte) (0x7ff0000000000042L >>> (index * 8)); // signaling NaN payload
            bytes[source + 8 + index] = (byte) (0x8000000000000000L >>> (index * 8)); // negative zero
        }
        byte[] expected = bytes.clone();
        // Deliberate hand-IR overlap has no C++ memcpy oracle. The old REP path remains
        // forward bytewise; only the new small-copy path promises a source snapshot.
        if (snapshot) System.arraycopy(bytes, source, expected, destination, size);
        else for (int index = 0; index < size; index++) expected[destination + index] = expected[source + index];
        var body = new StringBuilder("main PROC\n    push rbp\n    mov rbp, rsp\n    sub rsp, 1024\n");
        for (int index = 0; index < NONVOLATILE.size(); index++) body
                .append("    mov QWORD PTR [rbp-").append((index + 1) * 8).append("], ").append(NONVOLATILE.get(index)).append('\n')
                .append("    mov ").append(NONVOLATILE.get(index)).append(", ").append(sentinel(index)).append('\n');
        for (int index = 0; index < 10; index++) {
            body.append("__probe_xmm_save").append(index).append(":\n");
            for (int half = 0; half < 2; half++) body.append("    mov rax, ").append(sentinel(index + half * 10 + 7))
                    .append("\n    mov QWORD PTR [rbp-").append(960 - index * 16 - half * 8).append("], rax\n");
            body.append("__probe_xmm_load").append(index).append(":\n");
        }
        for (int index = 0; index < bytes.length; index++) body.append("    mov BYTE PTR [rbp-").append(400 - index).append("], ")
                .append(Byte.toUnsignedInt(bytes[index])).append('\n');
        body.append("    lea rcx, [rbp-").append(400 - destination).append("]\n    lea rdx, [rbp-").append(400 - source)
                .append("]\n    call minic$copy\n    cmp eax, 77\n    jne main$bad_live\n");
        for (int index = 0; index < NONVOLATILE.size(); index++) body.append("    mov rax, ").append(sentinel(index)).append("\n    cmp ")
                .append(NONVOLATILE.get(index)).append(", rax\n    jne main$bad_register\n");
        for (int index = 0; index < 10; index++) {
            body.append("__probe_xmm_capture").append(index).append(":\n");
            for (int half = 0; half < 2; half++) body.append("    mov rax, ").append(sentinel(index + half * 10 + 7))
                    .append("\n    cmp QWORD PTR [rbp-").append(800 - index * 16 - half * 8)
                    .append("], rax\n    jne main$bad_register\n");
        }
        for (int index = 0; index < bytes.length; index++) body.append("    cmp BYTE PTR [rbp-").append(400 - index).append("], ")
                .append(Byte.toUnsignedInt(expected[index])).append("\n    jne main$bad_copy\n");
        body.append("    lea rax, [rbp-1024]\n    cmp rsp, rax\n    jne main$bad_stack\n    mov eax, 0\n    jmp main$done\n")
                .append("main$bad_register:\n    mov eax, 91\n    jmp main$done\nmain$bad_copy:\n    mov eax, 92\n    jmp main$done\n")
                .append("main$bad_stack:\n    mov eax, 93\n    jmp main$done\nmain$bad_live:\n    mov eax, 94\nmain$done:\n");
        for (int index = 0; index < NONVOLATILE.size(); index++) body.append("    mov ").append(NONVOLATILE.get(index))
                .append(", QWORD PTR [rbp-").append((index + 1) * 8).append("]\n");
        for (int index = 0; index < 10; index++) body.append("__probe_xmm_restore").append(index).append(":\n");
        return body.append("    mov rsp, rbp\n    pop rbp\n    ret\nmain ENDP").toString();
    }

    private BoundedProcess.Result runNative(SourceFile source, AsmResult assembly, Path directory, String name) throws Exception {
        var module = new WindowsX64MachineAssembler().assemble(assembly);
        var sections = new ArrayList<MachineSection>();
        int probes = 0;
        for (var section : module.sections()) {
            var items = new ArrayList<MachineItem>();
            for (var item : section.items()) {
                items.add(item);
                if (item instanceof MachineLabel label && label.name().startsWith("__probe_xmm_")) {
                    String action = label.name().substring("__probe_xmm_".length());
                    int index = Integer.parseInt(action.substring(action.length() - 1));
                    action = action.substring(0, action.length() - 1);
                    int offset = (action.equals("load") ? 960 : action.equals("capture") ? 800 : 640) - index * 16;
                    items.add(xmmMove(index + 6, -offset, action.equals("save") || action.equals("capture")));
                    probes++;
                }
            }
            sections.add(new MachineSection(section.name(), section.kind(), section.alignment(), items));
        }
        assertEquals(40, probes);
        var object = new ObjResult(null, null, new CoffObjectWriter().write(
                new MachineModule(module.entrySymbol(), sections, module.externalSymbols())), module.entrySymbol());
        var linker = new Linker(); linker.link(source, object, directory, name);
        assertTrue(linker.succeeded(), () -> linker.errors().toString());
        var result = BoundedProcess.run(List.of(directory.resolve(name + ".exe").toString()), directory, "", Duration.ofSeconds(5), 65_536);
        assertFalse(result.timedOut()); assertFalse(result.outputExceeded()); return result;
    }

    private static MachineData xmmMove(int register, int displacement, boolean store) {
        // MOVDQU is only a caller probe, not a new compiler opcode. Its full 128-bit save,
        // load and capture let us check both halves of every Windows nonvolatile XMM.
        // F3 [REX.R] 0F 6F/7F /r, mod=10 with rbp+disp32; no relocation is involved.
        var bytes = ByteBuffer.allocate(register >= 8 ? 9 : 8).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 0xf3);
        if (register >= 8) bytes.put((byte) 0x44);
        bytes.put((byte) 0x0f).put((byte) (store ? 0x7f : 0x6f)).put((byte) (0x85 | ((register & 7) << 3))).putInt(displacement);
        return new MachineData(bytes.array(), 1);
    }
}
