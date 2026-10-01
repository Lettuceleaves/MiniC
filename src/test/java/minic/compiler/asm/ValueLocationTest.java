package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.IrConstant;
import minic.compiler.ir.value.IrValue.IrFloatConstant;
import minic.compiler.ir.value.IrValue.IrTemporary;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class ValueLocationTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 5);

    @Test void stackLocationRetainsItsValueTypeEvenWhenThePhysicalSlotIsShared() {
        assertEquals("DWORD PTR [rbp-16]", new ValueLocation.StackSlot(IrType.INT, 16).operand());
        assertEquals("DWORD PTR [rbp-16]", new ValueLocation.StackSlot(IrType.FLOAT, 16).operand());
        assertNotEquals(new ValueLocation.StackSlot(IrType.INT, 16), new ValueLocation.StackSlot(IrType.FLOAT, 16));
        assertThrows(IllegalArgumentException.class, () -> new ValueLocation.StackSlot(IrType.POINTER, 4));
        assertThrows(IllegalArgumentException.class, () -> new ValueLocation.StackSlot(IrType.INT, 0));
    }

    @Test void registerLocationSelectsTheExactStorageWidthAndRejectsWrongRegisterClasses() {
        assertEquals("r10b", new ValueLocation.Register(IrType.UNSIGNED_CHAR, "r10").operand());
        assertEquals("r11w", new ValueLocation.Register(IrType.SHORT, "r11").operand());
        assertEquals("r10d", new ValueLocation.Register(IrType.UNSIGNED_LONG, "r10").operand());
        assertEquals("r11", new ValueLocation.Register(IrType.POINTER, "r11").operand());
        assertEquals("xmm4", new ValueLocation.Register(IrType.DOUBLE, "xmm4").operand());
        assertThrows(IllegalArgumentException.class, () -> new ValueLocation.Register(IrType.INT, "xmm4"));
        assertThrows(IllegalArgumentException.class, () -> new ValueLocation.Register(IrType.FLOAT, "r10"));
        assertThrows(IllegalArgumentException.class, () -> new ValueLocation.Register(IrType.INT, "r10d"));
        assertThrows(IllegalArgumentException.class, () -> new ValueLocation.Register(IrType.INT, "unknown"));
    }

    @Test void temporaryAssignmentsAreImmutableAndDoNotSilentlyAliasAnUnknownOrMistypedValue() {
        var t = new IrTemporary("t", IrType.INT);
        var frame = frame(t);
        var overrides = new HashMap<String, ValueLocation>();
        overrides.put("t", new ValueLocation.Register(IrType.INT, "r10"));
        var locations = TemporaryLocations.withOverrides(frame, overrides);
        overrides.clear();
        assertEquals(new ValueLocation.Register(IrType.INT, "r10"), locations.location(t));
        assertThrows(IllegalArgumentException.class, () -> locations.location(new IrTemporary("missing", IrType.INT)));
        assertThrows(IllegalArgumentException.class, () -> locations.location(new IrTemporary("t", IrType.FLOAT)));
        assertThrows(IllegalArgumentException.class, () -> TemporaryLocations.withOverrides(frame,
                Map.of("missing", new ValueLocation.Register(IrType.INT, "r10"))));
        assertEquals(new ValueLocation.StackSlot(IrType.INT, frame.temporaryOffsets().get("t")),
                TemporaryLocations.allStack(frame).location(t));
    }

    static FrameLayout frame(IrTemporary temporary) {
        return FrameLayout.create(new IrFunction("main", MiniType.INT, List.of(), false,
                List.of(new IrBlock("entry", List.of(new IrMoveInstruction(temporary,
                        temporary.type().isFloatingScalar() ? new IrFloatConstant(0, temporary.type())
                                : new IrConstant(0, temporary.type()), R), new IrReturnInstruction(new IrConstant(0), R)))), R));
    }
}
