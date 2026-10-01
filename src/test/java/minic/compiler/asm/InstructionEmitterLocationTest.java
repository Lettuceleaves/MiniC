package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class InstructionEmitterLocationTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 5);
    private static final IrConstant ONE = new IrConstant(1);
    private static final IrLocal LOCAL = new IrLocal("local", "local", MiniType.INT, IrType.INT, 4, 4, R);

    static Stream<Arguments> definitions() {
        var integer = new IrTemporary("t", IrType.INT);
        var address = new IrTemporary("t", IrType.POINTER);
        var floating = new IrTemporary("t", IrType.DOUBLE);
        var bool = new IrTemporary("t", IrType.BOOL);
        IrValue pointer = new IrGlobalAddress("global");
        return Stream.of(
                Arguments.of(integer, new IrMoveInstruction(integer, ONE, R)),
                Arguments.of(integer, new IrBinaryInstruction(integer, IrBinaryOperator.ADD, ONE, ONE, R)),
                Arguments.of(integer, new IrUnaryInstruction(integer, IrUnaryOperator.NEGATE, ONE, R)),
                Arguments.of(integer, new IrSelectInstruction(integer, ONE, ONE, new IrConstant(0), R)),
                Arguments.of(integer, new IrCastInstruction(integer, new IrConstant(2, IrType.SHORT), R)),
                Arguments.of(bool, new IrCastInstruction(bool, new IrConstant(256), R)),
                Arguments.of(bool, new IrCastInstruction(bool, new IrFloatConstant(0.5, IrType.DOUBLE), R)),
                Arguments.of(floating, new IrCastInstruction(floating, ONE, R)),
                Arguments.of(integer, new IrCastInstruction(integer, new IrFloatConstant(2.5, IrType.DOUBLE), R)),
                Arguments.of(address, new IrAddressOfLocalInstruction(address, LOCAL, R)),
                Arguments.of(address, new IrElementAddressInstruction(address, pointer, ONE, MiniType.INT, 4, R)),
                Arguments.of(address, new IrFieldAddressInstruction(address, pointer, "Record", "field", 4, MiniType.INT, R)),
                Arguments.of(integer, new IrLoadLocalInstruction(integer, LOCAL, R)),
                Arguments.of(integer, new IrLoadPointerInstruction(integer, pointer, R)),
                Arguments.of(integer, new IrCallInstruction(integer, "callee", List.of(ONE), false, R)),
                Arguments.of(floating, new IrIndirectCallInstruction(floating, new IrFunctionAddress("callee"), List.of(ONE), false, R)));
    }

    @ParameterizedTest @MethodSource("definitions")
    void everyTemporaryDefinitionUsesItsAssignedHome(IrTemporary result, IrInstruction instruction) {
        var function = new IrFunction("function", MiniType.INT, List.of(), false,
                List.of(new IrBlock("entry", List.of(instruction))), R);
        var frame = FrameLayout.create(function);
        var location = new ValueLocation.Register(result.type(), result.type().isFloatingScalar() ? "xmm4" : "r10");
        var locations = TemporaryLocations.withOverrides(frame, Map.of(result.name(), location));
        var emitter = new InstructionEmitter(frame, Set.of(), function, locations);
        var text = new StringBuilder();
        emitter.emitInstruction(text, "function", "epilogue", instruction);
        assertTrue(text.toString().contains(" " + location.operand() + ", "), text::toString);
        assertFalse(text.toString().contains(frame.temporarySlot(result)), text::toString);

        var legacy = new StringBuilder();
        new InstructionEmitter(frame, Set.of(), function).emitInstruction(legacy, "function", "epilogue", instruction);
        var explicitStack = new StringBuilder();
        new InstructionEmitter(frame, Set.of(), function, TemporaryLocations.allStack(frame))
                .emitInstruction(explicitStack, "function", "epilogue", instruction);
        assertEquals(legacy.toString(), explicitStack.toString());
    }
}
