package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class BinaryRegisterHomeTest {
    private static final SourceRange R = new SourceRange(1, 0, 1, 8);
    private static final List<IrType> TYPES = List.of(IrType.INT, IrType.UNSIGNED_INT, IrType.LONG,
            IrType.UNSIGNED_LONG, IrType.LONG_LONG, IrType.UNSIGNED_LONG_LONG);
    private static final List<String> HOMES = List.of("r10", "r11", "rbx", "r12", "r13", "r14", "r15");

    static Stream<Arguments> operations() { return BinaryInstructionSelectionTest.operations(); }

    @ParameterizedTest @MethodSource("operations")
    void integerArithmeticUsesTheResultHomeWithoutAnAccumulatorTransfer(IrBinaryOperator operator, String mnemonic) {
        for (IrType type : TYPES) for (String home : HOMES) {
            var result = new IrTemporary("result", type);
            String register = new ValueLocation.Register(type, home).operand();
            var binary = new IrBinaryInstruction(result, operator, new IrParameterRef("left", type),
                    new IrParameterRef("right", type), R);
            String text = emit(binary, Map.of("result", home), true);
            assertTrue(text.contains(mnemonic + " " + register + ", " + width("rcx", type)), text);
            assertFalse(text.contains("rax") || text.contains("eax"), text);
            assertEquals(3, text.lines().count(), text);
        }
    }

    @ParameterizedTest @ValueSource(strings={"left", "right", "both"})
    void overlappingNonSsaOperandsAreReadBeforeTheResultIsOverwritten(String alias) {
        for (IrType type : TYPES) {
            var result = new IrTemporary("result", type);
            IrValue left = alias.equals("right") ? new IrParameterRef("left", type) : result;
            IrValue right = alias.equals("left") ? new IrParameterRef("right", type) : result;
            String text = emit(new IrBinaryInstruction(result, IrBinaryOperator.SUBTRACT, left, right, R),
                    Map.of("result", "r11"), true);
            String home = width("r11", type), scratch = width("rcx", type);
            assertTrue(text.contains("sub " + home + ", " + scratch), text);
            assertFalse(text.contains("rax") || text.contains("eax"), text);
            if (!alias.equals("left")) assertTrue(text.startsWith("    mov " + scratch + ", " + home), text);
            assertEquals(alias.equals("right") ? 3 : 2, text.lines().count(), text);
        }
    }

    @ParameterizedTest @MethodSource("operations")
    void inPlaceImmediateNeedsOnlyOneInstructionAndClearsTheHighHalfAt32Bits(IrBinaryOperator operator, String mnemonic) {
        for (IrType type : TYPES) {
            var result = new IrTemporary("result", type);
            String text = emit(new IrBinaryInstruction(result, operator, result, new IrConstant(-17, type), R),
                    Map.of("result", "r12"), true);
            assertEquals("    " + mnemonic + " " + width("r12", type) + ", -17" + System.lineSeparator(), text);
        }
    }

    @ParameterizedTest @ValueSource(longs={2147483648L,4294967295L,4294967296L,-2147483649L,Long.MIN_VALUE,Long.MAX_VALUE})
    void fullWidthImmediateFallsBackToScratchWithoutLosingBits(long value) {
        var result = new IrTemporary("result", IrType.UNSIGNED_LONG_LONG);
        String text = emit(new IrBinaryInstruction(result, IrBinaryOperator.ADD, result,
                new IrConstant(value, result.type()), R), Map.of("result", "rbx"), true);
        assertEquals("    mov rcx, " + value + System.lineSeparator() + "    add rbx, rcx" + System.lineSeparator(), text);
    }

    @Test void unsigned32ImmediatesUseTheirExactLowBitsInTheHome() {
        var result = new IrTemporary("result", IrType.UNSIGNED_INT);
        String text = emit(new IrBinaryInstruction(result, IrBinaryOperator.BITWISE_XOR, result,
                new IrConstant(4294967295L, result.type()), R), Map.of("result", "r10"), true);
        assertEquals("    xor r10d, -1" + System.lineSeparator(), text);
    }

    @Test void baselineAndUnsupportedOperationsKeepTheExistingAccumulatorPath() {
        var result = new IrTemporary("result", IrType.INT);
        var add = new IrBinaryInstruction(result, IrBinaryOperator.ADD, new IrParameterRef("left", IrType.INT), new IrConstant(7), R);
        String baseline = emit(add, Map.of("result", "r11"), false);
        assertTrue(baseline.contains("push rax") && baseline.contains("add eax, ecx") && baseline.contains("mov r11d, eax"), baseline);
        String stack = emit(add, Map.of(), true);
        assertTrue(stack.contains("add eax, 7") && stack.contains(", eax"), stack);
        for (IrBinaryOperator operator : List.of(IrBinaryOperator.DIVIDE, IrBinaryOperator.MODULO,
                IrBinaryOperator.SHIFT_LEFT, IrBinaryOperator.SHIFT_RIGHT, IrBinaryOperator.LESS_THAN)) {
            String text = emit(new IrBinaryInstruction(result, operator, new IrParameterRef("left", IrType.INT), new IrConstant(3), R),
                    Map.of("result", "r11"), true);
            assertTrue(text.contains("mov r11d, eax"), text);
        }
        for (IrType type : List.of(IrType.SHORT, IrType.UNSIGNED_CHAR, IrType.POINTER, IrType.DOUBLE)) {
            var target = new IrTemporary("result", type);
            IrValue right = type.isFloatingScalar() ? new IrFloatConstant(2.0, type) : new IrConstant(2, type);
            String text = emit(new IrBinaryInstruction(target, IrBinaryOperator.ADD, new IrParameterRef("left", type), right, R),
                    Map.of("result", type.isFloatingScalar() ? "xmm4" : "r11"), true);
            assertTrue(text.contains(type.isFloatingScalar() ? "movsd xmm4, xmm0" : "mov " + new ValueLocation.Register(type, "r11").operand() + ", "), text);
        }
    }

    private static String width(String register, IrType type) { return ValueLocation.generalRegisterName(register, type.sizeBytes()); }

    private static String emit(IrBinaryInstruction instruction, Map<String, String> homes, boolean optimized) {
        var instructions = new ArrayList<IrInstruction>();
        // Layout needs definitions for every named temporary, including a non-SSA result operand.
        instructions.add(new IrMoveInstruction(instruction.result(), instruction.result().type().isFloatingScalar()
                ? new IrFloatConstant(1, instruction.result().type()) : new IrConstant(1, instruction.result().type()), R));
        instructions.add(instruction);
        var function = new IrFunction("function", MiniType.INT,
                List.of(new IrParameter("left", MiniType.INT, instruction.left().type(), R),
                        new IrParameter("right", MiniType.INT, instruction.right().type(), R)), false,
                List.of(new IrBlock("entry", instructions)), R);
        var frame = FrameLayout.create(function);
        var overrides = new HashMap<String, ValueLocation>();
        homes.forEach((name, home) -> overrides.put(name, new ValueLocation.Register(instruction.result().type(), home)));
        var emitter = new InstructionEmitter(frame, Set.of(), function, TemporaryLocations.withOverrides(frame, overrides), optimized);
        var builder = new StringBuilder(); emitter.emitInstruction(builder, "function", "epilogue", instruction);
        return builder.toString();
    }
}
