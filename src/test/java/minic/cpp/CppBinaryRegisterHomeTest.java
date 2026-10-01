package minic.cpp;

import minic.SourceRange;
import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
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
final class CppBinaryRegisterHomeTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 5);
    private static final List<IrBinaryOperator> OPERATORS = List.of(IrBinaryOperator.ADD, IrBinaryOperator.SUBTRACT,
            IrBinaryOperator.MULTIPLY, IrBinaryOperator.BITWISE_AND, IrBinaryOperator.BITWISE_OR, IrBinaryOperator.BITWISE_XOR);
    private static final List<String> TOKENS = List.of("+", "-", "*", "&", "|", "^");
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(IrType.INT, IrType.UNSIGNED_INT, IrType.LONG_LONG, IrType.UNSIGNED_LONG_LONG)
                .flatMap(type -> Stream.of(false, true).map(calls -> Arguments.of(type, calls)));
    }

    @ParameterizedTest(name="{0}, call survivor={1}") @MethodSource("programs")
    void aliasedResultHomesMatchBaselineOriginalDebugAndCpp(IrType type, boolean calls) throws Exception {
        String reference = source(type, calls);
        var source = new SourceFile("register-home.cpp", reference);
        var ir = program(type, calls);
        IrVerifier.verify(ir);
        int expected = expected(type);
        var plan = GlobalRegisterPlan.allocate(ir.functions().get(1), true);
        String home = plan.registers().get("value");
        assertNotNull(home, "fixture must place the updated result in a register");
        if (calls) assertTrue(plan.calleeSavedRegisters().contains(home), "updated value must survive calls in a nonvolatile home");
        for (OptimizationLevel level : OptimizationLevel.values()) {
            var assembler = new Assembler(ir, new IrOptimizationPipeline(level, List.of()));
            String text = assembler.assemble().text();
            assertTrue(assembler.succeeded(), () -> assembler.errors().toString());
            if (level == OptimizationLevel.OPTIMIZED) {
                String register = home + (type.sizeBytes() == 4 ? home.equals("rbx") ? "" : "d" : "");
                if (home.equals("rbx") && type.sizeBytes() == 4) register = "ebx";
                String body = text.substring(text.indexOf("minic$work PROC"), text.indexOf("minic$work ENDP"));
                for (String mnemonic : List.of("add", "sub", "imul", "and", "or", "xor"))
                    assertTrue(body.contains(mnemonic + " " + register + ", " + (type.sizeBytes() == 4 ? "ecx" : "rcx")), body);
                if (calls) {
                    assertTrue(body.matches("(?s).*mov QWORD PTR \\[rbp-[0-9]+\\], " + home + "\\R.*"), body);
                    assertTrue(body.substring(body.indexOf("minic$work$epilogue:")).matches("(?s).*mov " + home + ", QWORD PTR \\[rbp-[0-9]+\\].*"), body);
                }
            }
            Path directory = temporary.resolve(level.name());
            var object = new ObjBuilder(source, assembler, directory, "program");
            var link = new Linker(source, object, directory, "program");
            new CompilerApi(List.of(object, link)).runThrough(link);
            assertTrue(link.succeeded(), () -> object.errors() + " / " + link.errors());
            assertEquals(expected, run(directory.resolve("program.exe")).exitCode());
            assertSame(ir, assembler.input().irResult());
        }
        var debug = DebugApi.fromIr(source, ir, "");
        for (int steps = 0; debug.canNext() && steps < 20_000; steps++) debug.next();
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(expected, debug.current().runtime().termination().status());
        Path cpp = temporary.resolve("reference.cpp"), executable = temporary.resolve("reference.exe");
        Files.writeString(cpp, reference);
        var compile = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()), "-std=c++17", "-O2",
                cpp.toString(), "-o", executable.toString()), temporary, "", Duration.ofSeconds(20), 65_536);
        assertFalse(compile.timedOut()); assertFalse(compile.outputExceeded()); assertEquals(0, compile.exitCode(), compile::stderr);
        assertEquals(expected, run(executable).exitCode());
    }

    private BoundedProcess.Result run(Path executable) throws Exception {
        var result = BoundedProcess.run(List.of(executable.toString()), temporary, "", Duration.ofSeconds(5), 65_536);
        assertFalse(result.timedOut()); assertFalse(result.outputExceeded()); return result;
    }

    private static IrResult program(IrType type, boolean calls) {
        MiniType sourceType = sourceType(type);
        var a = new IrParameter("a", sourceType, type, R); var b = new IrParameter("b", sourceType, type, R);
        var value = new IrTemporary("value", type); var total = new IrTemporary("total", IrType.UNSIGNED_INT);
        var low = new IrTemporary("low", IrType.UNSIGNED_INT); var high = new IrTemporary("high", IrType.UNSIGNED_INT);
        var bits = new IrTemporary("bits", IrType.UNSIGNED_LONG_LONG); var shifted = new IrTemporary("shifted", IrType.UNSIGNED_LONG_LONG);
        var body = new ArrayList<IrInstruction>();
        body.add(new IrMoveInstruction(total, new IrConstant(0, total.type()), R));
        for (var operator : OPERATORS) for (int alias = 0; alias < 3; alias++) {
            body.add(new IrMoveInstruction(value, alias == 1 ? b.ref() : a.ref(), R));
            IrValue left = alias == 1 ? a.ref() : value;
            IrValue right = alias == 0 ? b.ref() : value;
            body.add(new IrBinaryInstruction(value, operator, left, right, R));
            if (calls) body.add(new IrCallInstruction(null, "effect", List.of(), false, R));
            body.add(new IrCastInstruction(bits, value, R));
            body.add(new IrCastInstruction(low, bits, R));
            body.add(new IrBinaryInstruction(shifted, IrBinaryOperator.SHIFT_RIGHT, bits, new IrConstant(32, bits.type()), R));
            body.add(new IrCastInstruction(high, shifted, R));
            body.add(new IrBinaryInstruction(total, IrBinaryOperator.MULTIPLY, total, new IrConstant(33, total.type()), R));
            body.add(new IrBinaryInstruction(total, IrBinaryOperator.ADD, total, low, R));
            body.add(new IrBinaryInstruction(total, IrBinaryOperator.ADD, total, high, R));
        }
        body.add(new IrReturnInstruction(total, R));
        var work = new IrFunction("work", MiniType.UNSIGNED_INT, List.of(a, b), false, List.of(new IrBlock("entry", body)), R);
        var answer = new IrTemporary("answer", IrType.UNSIGNED_INT); var exit = new IrTemporary("exit", IrType.INT);
        var main = new IrFunction("main", MiniType.INT, List.of(), false, List.of(new IrBlock("entry", List.of(
                new IrCallInstruction(answer, "work", List.of(new IrConstant(seed(type), type), new IrConstant(5, type)), false, R),
                new IrCastInstruction(exit, answer, R), new IrReturnInstruction(exit, R)))), R);
        var first = new IrTemporary("first", IrType.INT); var second = new IrTemporary("second", IrType.INT);
        var effect = new IrFunction("effect", MiniType.INT, List.of(), false, List.of(new IrBlock("entry", List.of(
                new IrMoveInstruction(first, new IrConstant(10), R), new IrMoveInstruction(second, new IrConstant(3), R),
                new IrBinaryInstruction(first, IrBinaryOperator.ADD, first, second, R), new IrReturnInstruction(first, R)))), R);
        return new IrResult(List.of(main, work, effect));
    }

    private static MiniType sourceType(IrType type) {
        return switch (type) { case INT -> MiniType.INT; case UNSIGNED_INT -> MiniType.UNSIGNED_INT;
            case LONG_LONG -> MiniType.LONG_LONG; case UNSIGNED_LONG_LONG -> MiniType.UNSIGNED_LONG_LONG;
            default -> throw new IllegalArgumentException(); };
    }
    private static long seed(IrType type) {
        return switch (type) { case INT -> -17; case UNSIGNED_INT -> 4000000001L;
            case LONG_LONG -> -1000000001L; case UNSIGNED_LONG_LONG -> 0xf123456780000009L;
            default -> throw new IllegalArgumentException(); };
    }
    private static int expected(IrType type) {
        long a = seed(type), total = 0;
        for (int operator = 0; operator < 6; operator++) for (int alias = 0; alias < 3; alias++) {
            long b = alias == 2 ? a : 5;
            long value = switch (operator) { case 0 -> a + b; case 1 -> a - b; case 2 -> a * b;
                case 3 -> a & b; case 4 -> a | b; default -> a ^ b; };
            if(type.sizeBytes()==4) value=type.isSignedInteger()?(int)value:value&0xffff_ffffL;
            total = total * 33 + (value & 0xffff_ffffL) + (value >>> 32);
        }
        return (int) total;
    }
    private static String source(IrType type, boolean calls) {
        String spelling = switch (type) { case INT -> "int"; case UNSIGNED_INT -> "unsigned int";
            case LONG_LONG -> "long long"; case UNSIGNED_LONG_LONG -> "unsigned long long";
            default -> throw new IllegalArgumentException(); };
        String literal = type == IrType.UNSIGNED_LONG_LONG ? Long.toUnsignedString(seed(type)) + "ULL" : seed(type) + (type.sizeBytes() == 8 ? "LL" : type.isUnsignedInteger() ? "U" : "");
        var text = new StringBuilder("int effect(){return 13;}unsigned int work(" + spelling + " a," + spelling + " b){" + spelling + " value;unsigned int total=0;unsigned long long bits;");
        for (String operator : TOKENS) for (int alias = 0; alias < 3; alias++) {
            text.append("value=").append(alias == 1 ? "b" : "a").append(";value=")
                    .append(alias == 1 ? "a" : "value").append(operator).append(alias == 0 ? "b" : "value").append(';');
            if (calls) text.append("effect();");
            text.append("bits=(unsigned long long)value;total=total*33U+(unsigned int)bits+(unsigned int)(bits>>32);");
        }
        return text.append("return total;}int main(){return (int)work(").append(literal).append(",5);}").toString();
    }
}
