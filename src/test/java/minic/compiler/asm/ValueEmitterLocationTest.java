package minic.compiler.asm;

import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue.IrTemporary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class ValueEmitterLocationTest {
    static Stream<Arguments> transfers() {
        return Stream.of(
                Arguments.of(IrType.BOOL, "r10", "rax", "mov r10b, al", "movzx eax, r10b"),
                Arguments.of(IrType.SIGNED_CHAR, "r10", "rax", "mov r10b, al", "movsx eax, r10b"),
                Arguments.of(IrType.UNSIGNED_CHAR, "r11", "r10", "mov r11b, r10b", "movzx r10d, r11b"),
                Arguments.of(IrType.SHORT, "r10", "rax", "mov r10w, ax", "movsx eax, r10w"),
                Arguments.of(IrType.UNSIGNED_SHORT, "r11", "r10", "mov r11w, r10w", "movzx r10d, r11w"),
                Arguments.of(IrType.INT, "r10", "rax", "mov r10d, eax", "mov eax, r10d"),
                Arguments.of(IrType.UNSIGNED_LONG, "r11", "r10", "mov r11d, r10d", "mov r10d, r11d"),
                Arguments.of(IrType.LONG_LONG, "r10", "rax", "mov r10, rax", "mov rax, r10"),
                Arguments.of(IrType.UNSIGNED_LONG_LONG, "r11", "r10", "mov r11, r10", "mov r10, r11"),
                Arguments.of(IrType.POINTER, "r10", "rax", "mov r10, rax", "mov rax, r10"),
                Arguments.of(IrType.FLOAT, "xmm4", "xmm0", "movss xmm4, xmm0", "movss xmm0, xmm4"),
                Arguments.of(IrType.DOUBLE, "xmm5", "xmm1", "movsd xmm5, xmm1", "movsd xmm1, xmm5"));
    }

    @ParameterizedTest @MethodSource("transfers")
    void transfersUseTheValueWidthAndExtendNarrowReads(IrType type, String home, String scratch,
                                                     String expectedStore, String expectedLoad) {
        var temporary = new IrTemporary("t", type);
        var frame = ValueLocationTest.frame(temporary);
        var locations = TemporaryLocations.withOverrides(frame, Map.of("t", new ValueLocation.Register(type, home)));
        var emitter = new ValueEmitter(frame, Set.of(), locations);
        var text = new StringBuilder();
        emitter.emitStoreTemporary(text, temporary, scratch);
        emitter.emitLoadValue(text, temporary, scratch);
        assertEquals(lines(expectedStore, expectedLoad), text.toString());
    }

    @Test void sameRegisterTransferIsOmittedOnlyWhenItDoesNotLoseRequiredExtension() {
        var temporary = new IrTemporary("t", IrType.SHORT);
        var frame = ValueLocationTest.frame(temporary);
        var emitter = new ValueEmitter(frame, Set.of(), TemporaryLocations.withOverrides(frame,
                Map.of("t", new ValueLocation.Register(IrType.SHORT, "r10"))));
        var text = new StringBuilder();
        emitter.emitStoreTemporary(text, temporary, "r10");
        emitter.emitLoadValue(text, temporary, "r10w");
        assertEquals("", text.toString());
        emitter.emitLoadValue(text, temporary, "r10");
        assertEquals(lines("movsx r10d, r10w"), text.toString());
    }

    @Test void stackLocationsKeepTheExistingLoadAndStoreInstructions() {
        for (IrType type : IrType.values()) {
            var temporary = new IrTemporary("t", type);
            var frame = ValueLocationTest.frame(temporary);
            var emitter = new ValueEmitter(frame, Set.of());
            var text = new StringBuilder();
            emitter.emitStoreTemporary(text, temporary, type.isFloatingScalar() ? "xmm0" : "rax");
            emitter.emitLoadValue(text, temporary, type.isFloatingScalar() ? "xmm0" : "rax");
            String slot = frame.temporarySlot(temporary);
            String store = type.isFloatingScalar() ? (type == IrType.FLOAT ? "movss " : "movsd ") + slot + ", xmm0"
                    : "mov " + slot + ", " + emitter.storeRegister("rax", type);
            String load = type.isFloatingScalar() ? (type == IrType.FLOAT ? "movss " : "movsd ") + "xmm0, " + slot
                    : type.sizeBytes() < 4 ? (type.isSignedInteger() ? "movsx " : "movzx ") + "eax, " + slot
                    : "mov " + emitter.loadRegister("rax", type) + ", " + slot;
            assertEquals(lines(store, load), text.toString(), type.toString());
        }
    }

    @Test void fullRegisterReadOfAnIntClearsItsHighHalfEvenWhenTheHomeIsTheSameRegister() {
        var temporary = new IrTemporary("t", IrType.UNSIGNED_INT);
        var frame = ValueLocationTest.frame(temporary);
        var emitter = new ValueEmitter(frame, Set.of(), TemporaryLocations.withOverrides(frame,
                Map.of("t", new ValueLocation.Register(IrType.UNSIGNED_INT, "r10"))));
        var text = new StringBuilder();
        emitter.emitLoadValue(text, temporary, "r10");
        assertEquals(lines("mov r10d, r10d"), text.toString());
    }

    private static String lines(String... instructions) {
        return "    " + String.join(System.lineSeparator() + "    ", instructions) + System.lineSeparator();
    }
}
