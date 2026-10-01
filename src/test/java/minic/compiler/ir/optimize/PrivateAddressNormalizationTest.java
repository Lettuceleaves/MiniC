package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
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

final class PrivateAddressNormalizationTest {
    private static final SourceRange R = new SourceRange(2, 1, 2, 10);
    private static final IrLocal LOCAL = new IrLocal("local", "local", MiniType.INT, IrType.INT, 4, 4, R);
    private static final IrParameter P = new IrParameter("p", MiniType.INT, IrType.INT, R);
    private static final IrTemporary A = new IrTemporary("a", IrType.POINTER);
    private static final IrTemporary B = new IrTemporary("b", IrType.POINTER);
    private static final IrTemporary C = new IrTemporary("c", IrType.POINTER);
    private static final IrTemporary X = new IrTemporary("x", IrType.INT);
    private static final IrTemporary Y = new IrTemporary("y", IrType.INT);

    @Test void compoundAccessBecomesLocalAccessAndThenPromotionCanRemoveTheSlot() {
        var source = program(block("entry", declare(), store(7), address(),
                new IrLoadPointerInstruction(X, A, R), new IrBinaryInstruction(Y, IrBinaryOperator.ADD, X, c(2), R),
                new IrStorePointerInstruction(A, Y, R), new IrCheckInitializedInstruction(LOCAL, R),
                new IrLoadLocalInstruction(X, LOCAL, R), ret(X)));
        var normalized = run(source);
        assertEquals(0, count(normalized, IrAddressOfLocalInstruction.class));
        assertEquals(0, count(normalized, IrLoadPointerInstruction.class));
        assertEquals(2, count(normalized, IrStoreLocalInstruction.class));
        assertEquals(2, count(normalized, IrLoadLocalInstruction.class));
        var promoted = new LocalScalarPromotionPass().apply(new InitializedCheckEliminationPass().apply(normalized));
        IrVerifier.verify(promoted);
        assertEquals(0, count(promoted, IrDeclareLocalInstruction.class));
        assertEquals(1, count(source, IrAddressOfLocalInstruction.class));
    }

    @Test void fullWidthIndirectFirstWriteProvesTheSubsequentDirectReadInitialized() {
        var source = program(block("entry", declare(), address(), new IrStorePointerInstruction(A, c(9), R),
                new IrCheckInitializedInstruction(LOCAL, R), new IrLoadLocalInstruction(X, LOCAL, R), ret(X)));
        var result = run(source);
        assertEquals(0, count(result, IrStorePointerInstruction.class));
        assertEquals(1, count(result, IrStoreLocalInstruction.class));
    }

    @Test void uninitializedIndirectReadsAndChecksRetainTheirEntireAddressedObject() {
        for (IrInstruction read : List.of(new IrLoadPointerInstruction(X, A, R), new IrCheckInitializedInstruction(LOCAL, R))) {
            var source = program(block("entry", declare(), address(), read, ret(c(0))));
            assertSame(source, run(source));
        }
    }

    @Test void sameTypeZeroOffsetMoveAndCastAliasesNormalizeTogether() {
        var source = program(block("entry", declare(), store(3), address(), new IrMoveInstruction(B, A, R),
                new IrCastInstruction(C, B, R), new IrElementAddressInstruction(BIG, C, c(0), MiniType.INT, 4, R),
                new IrLoadPointerInstruction(X, BIG, R), ret(X)));
        var result = run(source);
        assertEquals(0, count(result, IrAddressOfLocalInstruction.class));
        assertEquals(0, count(result, IrElementAddressInstruction.class));
        assertEquals(0, count(result, IrCastInstruction.class));
        assertEquals(1, count(result, IrLoadLocalInstruction.class));
    }
    private static final IrTemporary BIG = new IrTemporary("big", IrType.POINTER);

    static Stream<Arguments> escapes() {
        return Stream.of(
                Arguments.of("call", new IrCallInstruction(null, "external", List.of(A), false, R)),
                Arguments.of("stored-address", new IrStorePointerInstruction(new IrGlobalAddress("sink"), A, R)),
                Arguments.of("comparison", new IrBinaryInstruction(X, IrBinaryOperator.EQUAL, A, new IrConstant(0, IrType.POINTER), R)),
                Arguments.of("nonzero-offset", new IrElementAddressInstruction(B, A, c(1), MiniType.INT, 4, R)),
                Arguments.of("different-type-zero-offset", new IrElementAddressInstruction(B, A, c(0), MiniType.CHAR, 1, R)),
                Arguments.of("partial-read", new IrLoadPointerInstruction(new IrTemporary("byte", IrType.CHAR), A, R)),
                Arguments.of("partial-write", new IrStorePointerInstruction(A, new IrConstant(1, IrType.CHAR), R)),
                Arguments.of("volatile-read", new IrLoadPointerInstruction(X, A, true, R)),
                Arguments.of("volatile-write", new IrStorePointerInstruction(A, c(1), true, R)),
                Arguments.of("memcpy", new IrMemCopyInstruction(A, new IrGlobalAddress("sink"), 4, R)));
    }

    @ParameterizedTest(name="{0}") @MethodSource("escapes")
    void anyUnsupportedUseRetainsTheWholeObject(String reason, IrInstruction use) {
        var source = program(block("entry", declare(), store(5), address(), use,
                new IrLoadPointerInstruction(Y, A, R), ret(Y)));
        assertSame(source, run(source), reason);
    }

    @Test void volatileDirectAccessAlsoPreventsAddressNormalization() {
        var source = program(block("entry", declare(), store(5), address(),
                new IrLoadLocalInstruction(Y, LOCAL, true, R), new IrLoadPointerInstruction(X, A, R), ret(X)));
        assertSame(source, run(source));
    }

    @Test void branchMustInitializationIncludesIndirectWritesOnEveryIncomingEdge() {
        for (boolean both : List.of(true, false)) {
            var source = program(block("entry", declare(), address(), new IrBranchInstruction(P.ref(), "yes", "no", R)),
                    block("yes", new IrStorePointerInstruction(A, c(4), R), jump("join")),
                    both ? block("no", store(8), jump("join")) : block("no", jump("join")),
                    block("join", new IrLoadPointerInstruction(X, A, R), ret(X)));
            assertEquals(both ? 0 : 1, count(run(source), IrAddressOfLocalInstruction.class));
        }
    }

    @Test void aDeclarationOnABackEdgeResetsThePreviousLifetimeInitializationProof() {
        var source = program(block("entry", jump("loop")),
                block("loop", declare(), address(), new IrBranchInstruction(P.ref(), "write", "read", R)),
                block("write", new IrStorePointerInstruction(A, c(4), R), jump("read")),
                block("read", new IrLoadPointerInstruction(X, A, R), new IrBranchInstruction(P.ref(), "loop", "done", R)),
                block("done", ret(X)));
        assertSame(source, run(source));
    }

    @Test void multipleDefinitionsOfAnAddressDoNotBecomeAnAliasFact() {
        var source = program(block("entry", declare(), store(3), address(),
                new IrMoveInstruction(A, new IrGlobalAddress("sink"), R), new IrLoadPointerInstruction(X, A, R), ret(X)));
        assertSame(source, run(source));
    }

    @Test void deadTailAddressUseDoesNotLoseItsRequiredStorageOrTemporaryDefinition() {
        var source = program(block("entry", declare(), store(3), address(), new IrLoadPointerInstruction(X, A, R), ret(X),
                new IrLoadPointerInstruction(Y, A, R)));
        assertSame(source, run(source));
    }

    @Test void parameterMutationUsesAnInvocationSnapshotWhileEarlierReadsStaySnapshots() {
        var source = program(block("entry", new IrMoveInstruction(X, P.ref(), R),
                new IrMoveInstruction(A, new IrParameterAddress("p"), R), new IrStorePointerInstruction(A, c(9), R),
                new IrBinaryInstruction(Y, IrBinaryOperator.ADD, X, P.ref(), R), ret(Y)));
        var result = run(source);
        assertEquals(0, count(result, IrStorePointerInstruction.class));
        var moves = code(result).stream().filter(IrMoveInstruction.class::isInstance).map(IrMoveInstruction.class::cast).toList();
        var entryCapture = moves.getFirst();
        assertEquals(P.ref(), entryCapture.value());
        assertEquals(entryCapture.result(), moves.get(1).value());
        assertEquals(X, moves.get(1).result());
        assertTrue(code(result).stream().skip(1).flatMap(i -> IrValueUses.inputs(i).stream()).noneMatch(P.ref()::equals));
        assertFalse(code(result).stream().flatMap(i -> IrValueUses.inputs(i).stream()).anyMatch(IrParameterAddress.class::isInstance));
    }

    @Test void parameterAddressEscapeAndIncomingArgumentAreaRetainTheParameterHome() {
        var escaped = program(block("entry", new IrCallInstruction(null, "external", List.of(new IrParameterAddress("p")), false, R),
                new IrStorePointerInstruction(new IrParameterAddress("p"), c(4), R), ret(P.ref())));
        assertSame(escaped, run(escaped));
        var incoming = program(block("entry", new IrAddressOfLocalInstruction(B, IrLocal.incomingArgumentArea(0, R), R),
                new IrStorePointerInstruction(new IrParameterAddress("p"), c(4), R), ret(P.ref())));
        assertSame(incoming, run(incoming));
    }

    @Test void anEntryBackEdgeMustNotRecaptureAnAlreadyMutatedParameter() {
        var source = program(block("entry", new IrMoveInstruction(A, new IrParameterAddress("p"), R),
                new IrLoadPointerInstruction(X, A, R), new IrBinaryInstruction(Y, IrBinaryOperator.SUBTRACT, X, c(1), R),
                new IrStorePointerInstruction(A, Y, R), new IrBranchInstruction(P.ref(), "entry", "done", R)),
                block("done", ret(P.ref())));
        assertSame(source, run(source));
    }

    @Test void sourceMetadataRangesAndNamesRemainStable() {
        var plain = program(block("entry", declare(), store(3), address(), new IrLoadPointerInstruction(X, A, R), ret(X)));
        var source = new IrResult(plain.functions(), List.of(new IrStringData("text", "kept")), List.of(),
                Set.of("external"), Set.of("sink"), Map.of(), null, "subject", Map.of("f", "display"), "f");
        var result = run(source);
        assertEquals(source.stringData(), result.stringData());
        assertEquals(source.displayNames(), result.displayNames());
        assertEquals(source.entryFunction(), result.entryFunction());
        assertEquals(source.currentSubject(), result.currentSubject());
        assertTrue(code(result).stream().allMatch(i -> i.range().equals(R)));
        assertSame(LOCAL, ((IrLoadLocalInstruction) code(result).get(2)).local());
    }

    private static IrResult run(IrResult source) { IrVerifier.verify(source); var result = new PrivateAddressNormalizationPass().apply(source); IrVerifier.verify(result); return result; }
    private static List<IrInstruction> code(IrResult source) { return source.functions().getFirst().blocks().stream().flatMap(b -> b.instructions().stream()).toList(); }
    private static long count(IrResult source, Class<?> type) { return code(source).stream().filter(type::isInstance).count(); }
    private static IrResult program(IrBlock... blocks) { return new IrResult(List.of(new IrFunction("f", MiniType.INT, List.of(P), false, List.of(blocks), R)), List.of(), List.of(), Set.of("external"), Set.of("sink"), Map.of()); }
    private static IrBlock block(String name, IrInstruction... code) { return new IrBlock(name, List.of(code)); }
    private static IrDeclareLocalInstruction declare() { return new IrDeclareLocalInstruction(LOCAL, R); }
    private static IrStoreLocalInstruction store(int value) { return new IrStoreLocalInstruction(LOCAL, c(value), R); }
    private static IrAddressOfLocalInstruction address() { return new IrAddressOfLocalInstruction(A, LOCAL, R); }
    private static IrConstant c(int value) { return new IrConstant(value); }
    private static IrReturnInstruction ret(IrValue value) { return new IrReturnInstruction(value, R); }
    private static IrJumpInstruction jump(String label) { return new IrJumpInstruction(label, R); }
}
