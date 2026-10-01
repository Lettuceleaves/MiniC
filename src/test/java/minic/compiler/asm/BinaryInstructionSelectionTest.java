package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Width-sensitive x64 selection preserves baseline output and the assigned result home. */
final class BinaryInstructionSelectionTest {
    private static final SourceRange R = new SourceRange(1, 0, 1, 10);

    static Stream<Arguments> operations() {
        return Stream.of(Arguments.of(IrBinaryOperator.ADD, "add"), Arguments.of(IrBinaryOperator.SUBTRACT, "sub"),
                Arguments.of(IrBinaryOperator.MULTIPLY, "imul"), Arguments.of(IrBinaryOperator.BITWISE_AND, "and"),
                Arguments.of(IrBinaryOperator.BITWISE_OR, "or"), Arguments.of(IrBinaryOperator.BITWISE_XOR, "xor"));
    }
    @ParameterizedTest @MethodSource("operations")
    void usesAnEncodableImmediateWithoutLoadingAnExtraRegister(IrBinaryOperator operator, String mnemonic) {
        for (IrType type : List.of(IrType.INT, IrType.UNSIGNED_INT, IrType.LONG_LONG, IrType.UNSIGNED_LONG_LONG)) {
            var instruction = binary(operator, type, 123);
            String text = emit(instruction, true, false);
            String register = type.sizeBytes() == 8 ? "rax" : "eax";
            assertTrue(text.contains(mnemonic + " " + register + ", 123"), text);
            assertFalse(text.contains("mov ecx, 123") || text.contains("mov rcx, 123"), text);
            assertFalse(text.contains("push rax") || text.contains("pop rax"), text);
        }
    }

    @ParameterizedTest @ValueSource(longs={-2147483648L, -129L, -128L, 127L, 128L, 2147483647L})
    void signedImmediateBoundariesRemainExact(long value) {
        String text = emit(binary(IrBinaryOperator.ADD, IrType.LONG_LONG, value), true, false);
        assertTrue(text.contains("add rax, " + value), text);
    }

    @ParameterizedTest @ValueSource(longs={2147483648L, 4294967295L, 4294967296L, -2147483649L, Long.MIN_VALUE, Long.MAX_VALUE})
    void wideValuesThatCannotBeSignExtendedFrom32BitsUseTheRegisterPath(long value) {
        String text = emit(binary(IrBinaryOperator.ADD, IrType.UNSIGNED_LONG_LONG, value), true, false);
        assertTrue(text.contains("mov rcx, " + value), text);
        assertTrue(text.contains("add rax, rcx"), text);
        assertFalse(text.contains("push rax") || text.contains("pop rax"), text);
    }

    @Test void unsigned32BitPatternsAreNormalizedToEncodableSignedText() {
        String text = emit(binary(IrBinaryOperator.BITWISE_XOR, IrType.UNSIGNED_INT, 4294967295L), true, false);
        assertTrue(text.contains("xor eax, -1"), text);
        String high = emit(binary(IrBinaryOperator.BITWISE_OR, IrType.UNSIGNED_INT, 2147483648L), true, false);
        assertTrue(high.contains("or eax, -2147483648"), high);
    }

    @Test void resultStillGoesToItsAssignedRegister() {
        String text = emit(binary(IrBinaryOperator.MULTIPLY, IrType.LONG_LONG, 7), true, true);
        assertTrue(text.contains("imul r11, 7"), text);
        assertFalse(text.contains("mov r11, rax"), text);
        assertFalse(text.contains("push rax"), text);
    }

    @Test void baselineKeepsItsPreviousOperandSaveAndRegisterInstructions() {
        for (IrBinaryOperator operator : List.of(IrBinaryOperator.ADD, IrBinaryOperator.SUBTRACT, IrBinaryOperator.MULTIPLY)) {
            String text = emit(binary(operator, IrType.INT, 123), false, false);
            assertTrue(text.contains("push rax") && text.contains("pop rax"), text);
            assertTrue(text.contains("mov ecx, 123"), text);
        }
    }

    @Test void divisionShiftComparisonAndFloatingInstructionsKeepTheirOperandRules() {
        for (IrBinaryOperator operator : List.of(IrBinaryOperator.DIVIDE, IrBinaryOperator.MODULO,
                IrBinaryOperator.SHIFT_LEFT, IrBinaryOperator.SHIFT_RIGHT, IrBinaryOperator.LESS_THAN)) {
            String text = emit(binary(operator, IrType.INT, 3), true, false);
            assertTrue(text.contains("mov ecx, 3"), text);
            assertFalse(text.contains("push rax"), text);
        }
        var floating = new IrBinaryInstruction(new IrTemporary("result", IrType.DOUBLE), IrBinaryOperator.ADD,
                new IrParameterRef("value", IrType.DOUBLE), new IrFloatConstant(2.5, IrType.DOUBLE), R);
        assertEquals(emit(floating, false, false), emit(floating, true, false));
    }

    @Test void loadingARegisterOrAddressRightOperandNeedsNoIntegerSave() {
        for (IrValue value : List.of(new IrTemporary("right", IrType.INT), new IrGlobalAddress("global"))) {
            var operation = new IrBinaryInstruction(new IrTemporary("result", value.type()), IrBinaryOperator.ADD,
                    new IrParameterRef("value", value.type()), value, R);
            String text = emit(operation, true, true);
            assertFalse(text.contains("push rax") || text.contains("pop rax"), text);
            assertTrue(text.contains(value instanceof IrTemporary ? "mov ecx, r10d" : "lea rcx, global"), text);
        }
    }

    private static IrBinaryInstruction binary(IrBinaryOperator operator, IrType type, long constant) {
        return new IrBinaryInstruction(new IrTemporary("result", type), operator,
                new IrParameterRef("value", type), new IrConstant(constant, type), R);
    }
    private static String emit(IrBinaryInstruction instruction, boolean optimized, boolean assigned) {
        IrType type = instruction.left().type();
        var instructions = new java.util.ArrayList<minic.compiler.ir.instruction.IrInstruction>();
        if (instruction.right() instanceof IrTemporary right)
            instructions.add(new IrMoveInstruction(right, new IrConstant(5, right.type()), R));
        instructions.add(instruction);
        var function = new IrFunction("function", MiniType.INT,
                List.of(new IrParameter("value", MiniType.INT, type, R)), false,
                List.of(new IrBlock("entry", instructions)), R);
        var frame = FrameLayout.create(function);
        Map<String, ValueLocation> registers = assigned ? instruction.right() instanceof IrTemporary right
                ? Map.of(instruction.result().name(), new ValueLocation.Register(instruction.result().type(), "r11"),
                         right.name(), new ValueLocation.Register(right.type(), "r10"))
                : Map.of(instruction.result().name(), new ValueLocation.Register(instruction.result().type(), "r11")) : Map.of();
        var locations = TemporaryLocations.withOverrides(frame, registers);
        var emitter = new InstructionEmitter(frame, Set.of(), function, locations, optimized);
        var text = new StringBuilder();
        emitter.emitInstruction(text, "function", "epilogue", instruction);
        return text.toString();
    }
}
