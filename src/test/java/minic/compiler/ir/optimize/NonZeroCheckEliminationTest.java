package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class NonZeroCheckEliminationTest {
    private static final SourceRange R = new SourceRange(2, 1, 2, 8);
    private static final IrTemporary X = new IrTemporary("x", IrType.INT);
    private static final IrParameter P = new IrParameter("p", MiniType.INT, IrType.INT, R);

    static Stream<Arguments> constants() {
        return Stream.of(
                Arguments.of(IrType.INT, 4L, true), Arguments.of(IrType.INT, 0L, false),
                Arguments.of(IrType.INT, 1L << 32, false), Arguments.of(IrType.INT, (1L << 32) + 1, true),
                Arguments.of(IrType.CHAR, 256L, false), Arguments.of(IrType.CHAR, 255L, true),
                Arguments.of(IrType.UNSIGNED_CHAR, 257L, true), Arguments.of(IrType.SHORT, 65536L, false),
                Arguments.of(IrType.UNSIGNED_SHORT, 65535L, true), Arguments.of(IrType.LONG, 1L << 32, false),
                Arguments.of(IrType.UNSIGNED_LONG, (1L << 32) + 1, true),
                Arguments.of(IrType.LONG_LONG, Long.MIN_VALUE, true),
                Arguments.of(IrType.UNSIGNED_LONG_LONG, -1L, true),
                Arguments.of(IrType.BOOL, 1L, true), Arguments.of(IrType.BOOL, 0L, false),
                Arguments.of(IrType.BOOL, 2L, false), Arguments.of(IrType.POINTER, 1L, false));
    }

    @ParameterizedTest(name="{0} {1} removable={2}") @MethodSource("constants")
    void removesOnlyCanonicalNonzeroIntegerValues(IrType type, long value, boolean removable) {
        IrCheckNonZeroInstruction check = new IrCheckNonZeroInstruction(new IrConstant(value, type), R);
        IrResult source = program(block("entry", check, ret()));
        IrResult result = new NonZeroCheckEliminationPass().apply(source);
        assertEquals(removable ? 0 : 1, checks(result));
        assertSame(check, source.functions().getFirst().blocks().getFirst().instructions().getFirst());
        if (!removable) assertSame(source, result);
    }

    @Test void unknownAndFloatingChecksAreNotIntegerProofs() {
        IrTemporary floating = new IrTemporary("floating", IrType.DOUBLE);
        IrResult source = program(block("entry", new IrCheckNonZeroInstruction(P.ref(), R),
                new IrCheckNonZeroInstruction(floating, R), ret()));
        assertSame(source, new NonZeroCheckEliminationPass().apply(source));
    }

    @Test void propagationRequiresAgreementAtEveryNonSsaBranch() {
        assertEquals(0, checks(simplify(diamond(5))));
        assertEquals(1, checks(simplify(diamond(0))));
    }

    @Test void mutableLoopDefinitionCannotRetainAnEarlierNonzeroFact() {
        IrResult source = program(block("entry", new IrMoveInstruction(X, new IrConstant(3), R), jump("loop")),
                block("loop", new IrCheckNonZeroInstruction(X, R), new IrMoveInstruction(X, new IrConstant(0), R),
                        new IrBranchInstruction(P.ref(), "loop", "done", R)), block("done", ret()));
        assertEquals(1, checks(simplify(source)));
    }

    @Test void allModuleMetadataAndRetainedInstructionIdentitiesRemainIntact() {
        var returned = ret();
        IrResult plain = program(block("entry", new IrCheckNonZeroInstruction(new IrConstant(-7), R), returned));
        IrResult source = new IrResult(plain.functions(), List.of(new IrStringData("text", "kept")), List.of(),
                Set.of("external"), Set.of("object"), Map.of(), null, "subject", Map.of("f", "Friendly::f"), "f");
        IrResult result = new NonZeroCheckEliminationPass().apply(source);
        assertEquals(source.stringData(), result.stringData());
        assertEquals(source.externalFunctionNames(), result.externalFunctionNames());
        assertEquals(source.externalObjectNames(), result.externalObjectNames());
        assertEquals(source.displayNames(), result.displayNames());
        assertEquals(source.currentSubject(), result.currentSubject());
        assertEquals(source.entryFunction(), result.entryFunction());
        assertSame(returned, result.functions().getFirst().blocks().getFirst().instructions().getFirst());
        assertEquals(1, checks(source));
    }

    private static IrResult diamond(int other) {
        return program(block("entry", new IrBranchInstruction(P.ref(), "yes", "no", R)),
                block("yes", new IrMoveInstruction(X, new IrConstant(5), R), jump("join")),
                block("no", new IrMoveInstruction(X, new IrConstant(other), R), jump("join")),
                block("join", new IrCheckNonZeroInstruction(X, R), ret()));
    }
    private static IrResult simplify(IrResult source) {
        return new NonZeroCheckEliminationPass().apply(new ConstantPropagationPass().apply(source));
    }
    private static long checks(IrResult result) { return result.functions().stream().flatMap(f -> f.blocks().stream())
            .flatMap(b -> b.instructions().stream()).filter(IrCheckNonZeroInstruction.class::isInstance).count(); }
    private static IrResult program(IrBlock... blocks) { return new IrResult(List.of(new IrFunction("f", MiniType.INT,
            List.of(P), false, List.of(blocks), R))); }
    private static IrBlock block(String name, IrInstruction... instructions) { return new IrBlock(name, List.of(instructions)); }
    private static IrReturnInstruction ret() { return new IrReturnInstruction(new IrConstant(0), R); }
    private static IrJumpInstruction jump(String name) { return new IrJumpInstruction(name, R); }
}
