package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class CalleeSavedFrameLayoutTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 5);

    @Test void independentEightByteHomesCannotOverlapArgumentsObjectsFlagsOrTemporaries() {
        var parameters = new ArrayList<IrParameter>();
        for (int i = 0; i < 6; i++) parameters.add(new IrParameter("p" + i, MiniType.CHAR, IrType.CHAR, R));
        var local = new IrLocal("local", "local", MiniType.SHORT, IrType.SHORT, 2, 16, R);
        var wide = new IrTemporary("wide", IrType.LONG_LONG);
        var function = new IrFunction("function", MiniType.INT, parameters, true, List.of(new IrBlock("entry", List.of(
                new IrDeclareLocalInstruction(local, R), new IrCheckInitializedInstruction(local, R),
                new IrMoveInstruction(wide, new IrConstant(9, IrType.LONG_LONG), R),
                new IrCallInstruction(null, "effect", parameters.stream().map(IrParameter::ref).map(IrValue.class::cast).toList(), false, R),
                new IrReturnInstruction(new IrConstant(0), R)))), R);
        var original = FrameLayout.create(function, true);
        var frame = original.withCalleeSavedRegisters(List.of("rbx", "r12", "r13", "r14", "r15"));
        assertEquals(original.parameterOffsets(), frame.parameterOffsets());
        assertEquals(original.parameterTypes(), frame.parameterTypes());
        assertEquals(original.localOffsets(), frame.localOffsets());
        assertEquals(original.localInitializedOffsets(), frame.localInitializedOffsets());
        assertEquals(original.temporaryOffsets(), frame.temporaryOffsets());
        assertSame(original.temporarySlotPlan(), frame.temporarySlotPlan());
        assertEquals(original.outgoingArgumentAreaSize(), frame.outgoingArgumentAreaSize());
        assertEquals(48, frame.outgoingArgumentAreaSize());
        assertEquals("[rbp+64]", frame.localAddress(IrLocal.incomingArgumentArea(6, R)));
        assertEquals(0, frame.frameSize() % 16);
        int firstPrivateEnd = original.frameSize() - original.outgoingArgumentAreaSize();
        int previous = firstPrivateEnd;
        for (int offset : frame.calleeSavedOffsets().values()) {
            assertEquals(0, offset % 8);
            assertTrue(offset - 8 >= previous, "save slots must not overlap private objects or each other");
            assertTrue(offset <= frame.frameSize() - frame.outgoingArgumentAreaSize(), "save must not overlap outgoing arguments");
            previous = offset;
        }
        assertEquals(5, frame.calleeSavedOffsets().size());
        assertThrows(UnsupportedOperationException.class, () -> frame.calleeSavedOffsets().clear());
    }

    @Test void noAssignedRegisterKeepsTheExactOriginalFrame() {
        var function = new IrFunction("main", MiniType.INT, List.of(), false,
                List.of(new IrBlock("entry", List.of(new IrReturnInstruction(new IrConstant(0), R)))), R);
        var frame = FrameLayout.create(function, true);
        assertSame(frame, frame.withCalleeSavedRegisters(List.of()));
        assertTrue(frame.calleeSavedOffsets().isEmpty());
    }

    @Test void rejectsDuplicateOrUnmanagedRegisterSaves() {
        var function = new IrFunction("main", MiniType.INT, List.of(), false,
                List.of(new IrBlock("entry", List.of(new IrReturnInstruction(new IrConstant(0), R)))), R);
        var frame = FrameLayout.create(function, true);
        assertThrows(IllegalArgumentException.class, () -> frame.withCalleeSavedRegisters(List.of("rbx", "rbx")));
        assertThrows(IllegalArgumentException.class, () -> frame.withCalleeSavedRegisters(List.of("rsp")));
        assertThrows(IllegalArgumentException.class, () -> frame.withCalleeSavedRegisters(List.of("xmm6")));
    }
}
