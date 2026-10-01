package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.asm.Assembler;
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

import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

final class ReadOnlyParameterPromotionTest {
    private static final SourceRange R = new SourceRange(2, 1, 2, 10);
    private static final IrParameter P = new IrParameter("p", MiniType.INT, IrType.INT, R);
    private static final IrTemporary X = new IrTemporary("x", IrType.INT), Y = new IrTemporary("y", IrType.INT);
    private static final IrConstant ONE = new IrConstant(1);

    @Test void repeatedReadsBecomeOneEntrySnapshotAndInputMetadataIsUntouched() {
        var source = program(false, List.of(P), block("entry", add(X, P.ref(), P.ref()), ret(X)));
        var result = run(source);
        assertEquals(1, parameterReads(result, "p"));
        var capture = (IrMoveInstruction) code(result).getFirst();
        assertEquals(P.ref(), capture.value());
        assertEquals(P.range(), capture.range());
        var sum = (IrBinaryInstruction) code(result).get(1);
        assertEquals(capture.result(), sum.left()); assertEquals(capture.result(), sum.right());
        assertEquals(2, parameterReads(source, "p"));
        assertSame(source.displayNames(), result.displayNames());
        assertEquals(source.entryFunction(), result.entryFunction());
        assertEquals(source.currentSubject(), result.currentSubject());
        assertTrue(code(result).stream().allMatch(i -> R.equals(i.range())));
        assertSame(result, new ReadOnlyParameterPromotionPass().apply(result), "promotion must be idempotent");
    }

    @Test void oneStaticReadInALoopGetsARegisterEligibleHome() {
        var source = program(false, List.of(P), block("entry", new IrMoveInstruction(X, ONE, R), jump("loop")),
                block("loop", add(X, X, P.ref()), new IrBranchInstruction(X, "loop", "done", R)), block("done", ret(X)));
        var result = run(source);
        var capture = (IrMoveInstruction) code(result).getFirst();
        assertEquals(P.ref(), capture.value());
        assertEquals(1, parameterReads(result, "p"));
        var home = capture.result();
        assertTrue(GlobalRegisterPlan.allocate(result.functions().getFirst(), true).registers().containsKey(home.name()));
    }

    @Test void oneAcyclicReadDoesNotAddACopy() {
        var source = program(false, List.of(P), block("entry", ret(P.ref())));
        assertSame(source, run(source));
    }

    @Test void takingTheAddressRejectsOnlyThatParameter() {
        var q = new IrParameter("q", MiniType.INT, IrType.INT, R);
        var source = program(false, List.of(P, q), block("entry",
                new IrStorePointerInstruction(new IrParameterAddress("p"), ONE, R),
                add(X, P.ref(), P.ref()), add(Y, q.ref(), q.ref()), ret(Y)));
        var result = run(source);
        assertEquals(2, parameterReads(result, "p"));
        assertEquals(1, parameterReads(result, "q"));
        assertEquals(1, code(result).stream().filter(IrStorePointerInstruction.class::isInstance).count());
    }

    @Test void volatileFloatingAndRecordParametersStayInTheirExistingHomes() {
        for (var parameter : List.of(
                new IrParameter("p", MiniType.qualified(MiniType.INT, Set.of(MiniType.TypeQualifier.VOLATILE)), IrType.INT, R),
                new IrParameter("p", MiniType.DOUBLE, IrType.DOUBLE, R),
                new IrParameter("p", MiniType.struct("Box"), IrType.POINTER, R))) {
            var first = new IrTemporary("one", parameter.type());
            var second = new IrTemporary("two", parameter.type());
            var source = program(false, List.of(parameter), block("entry", new IrMoveInstruction(first, parameter.ref(), R),
                    new IrMoveInstruction(second, parameter.ref(), R), ret(ONE)));
            assertSame(source, run(source));
        }
    }

    @Test void variadicAndIncomingAreaFunctionsKeepLiveParameterSlots() {
        var variadic = program(true, List.of(P), block("entry", add(X, P.ref(), P.ref()), ret(X)));
        assertSame(variadic, run(variadic));
        var area = new IrTemporary("area", IrType.POINTER);
        var incoming = program(false, List.of(P), block("entry", new IrAddressOfLocalInstruction(area, IrLocal.incomingArgumentArea(0, R), R),
                add(X, P.ref(), P.ref()), ret(X)));
        assertSame(incoming, run(incoming));
    }

    @Test void entryBackEdgesAreConservativelyExcluded() {
        var source = program(false, List.of(P), block("entry", add(X, P.ref(), P.ref()),
                new IrBranchInstruction(X, "entry", "done", R)), block("done", ret(X)));
        assertSame(source, run(source));
    }

    @Test void deadTailAndUnreachableAddressUsesCannotHideAliasingOrRequireMissingTemps() {
        var ptr = new IrTemporary("ptr", IrType.POINTER);
        var deadAddress = program(false, List.of(P), block("entry", add(X, P.ref(), P.ref()), ret(X),
                new IrMoveInstruction(ptr, new IrParameterAddress("p"), R)));
        assertSame(deadAddress, run(deadAddress));
        var deadRead = program(false, List.of(P), block("entry", add(X, P.ref(), P.ref()), ret(X), new IrMoveInstruction(Y, P.ref(), R)));
        assertSame(deadRead, run(deadRead));
        var unreachable = program(false, List.of(P), block("entry", add(X, P.ref(), P.ref()), ret(X)),
                block("dead", new IrMoveInstruction(ptr, new IrParameterAddress("p"), R), ret(ONE)));
        assertSame(unreachable, run(unreachable));
    }

    @Test void freshHomesAvoidAllExistingNamesAndSurviveConstantPropagation() {
        var taken = new IrTemporary("__readonly_parameter_0", IrType.INT);
        var source = program(false, List.of(P), block("entry", new IrMoveInstruction(taken, ONE, R),
                add(X, P.ref(), P.ref()), add(Y, X, taken), ret(Y)));
        var result = run(source);
        var capture = (IrMoveInstruction) code(result).getFirst();
        assertNotEquals(taken.name(), capture.result().name());
        var propagated = new ConstantPropagationPass().apply(result);
        assertEquals(1, parameterReads(propagated, "p"));
    }

    @Test void aCallCannotMutateAnUnaddressedParameterAndRegisterPlanPreservesTheCapture() {
        var source = program(false, List.of(P), block("entry", add(X, P.ref(), P.ref()),
                new IrCallInstruction(null, "external", List.of(X), false, R), add(Y, P.ref(), P.ref()), ret(Y)));
        var result = run(source);
        var capture = (IrMoveInstruction) code(result).getFirst();
        var plan = GlobalRegisterPlan.allocate(result.functions().getFirst(), true);
        assertTrue(plan.calleeSavedRegisters().contains(plan.registers().get(capture.result().name())));
        assertEquals(1, parameterReads(result, "p"));
    }

    @Test void actualAssemblyReadsTheParameterSlotOnlyOnce() {
        var source = program(false, List.of(P), block("entry", add(X, P.ref(), P.ref()), add(Y, X, P.ref()), ret(Y)));
        var plain = new Assembler(source, new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of())).assemble().text();
        var changed = new Assembler(source, new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(new ReadOnlyParameterPromotionPass()))).assemble().text();
        var slot = Pattern.compile("mov (DWORD PTR \\[rbp-\\d+\\]), ecx").matcher(plain);
        assertTrue(slot.find(), plain);
        String suffix = ", " + slot.group(1);
        assertEquals(3, plain.lines().filter(line -> line.strip().endsWith(suffix)).count(), plain);
        assertEquals(1, changed.lines().filter(line -> line.strip().endsWith(suffix)).count(), changed);
    }

    private static IrResult run(IrResult source) { IrVerifier.verify(source); var result=new ReadOnlyParameterPromotionPass().apply(source); IrVerifier.verify(result); return result; }
    private static List<IrInstruction> code(IrResult source) { return source.functions().getFirst().blocks().stream().flatMap(b->b.instructions().stream()).toList(); }
    private static long parameterReads(IrResult source,String name) { return code(source).stream().flatMap(i->IrValueUses.inputs(i).stream()).filter(v->v instanceof IrParameterRef p&&p.name().equals(name)).count(); }
    private static IrResult program(boolean variadic,List<IrParameter> parameters,IrBlock...blocks) { return new IrResult(List.of(new IrFunction("f",MiniType.INT,parameters,variadic,List.of(blocks),R)),List.of(),List.of(),Set.of("external"),Set.of(),Map.of(),null,"subject",Map.of("f","sourceName"),"f"); }
    private static IrBlock block(String name,IrInstruction...code) { return new IrBlock(name,List.of(code)); }
    private static IrBinaryInstruction add(IrTemporary result,IrValue a,IrValue b) { return new IrBinaryInstruction(result,IrBinaryOperator.ADD,a,b,R); }
    private static IrReturnInstruction ret(IrValue value) { return new IrReturnInstruction(value,R); }
    private static IrJumpInstruction jump(String label) { return new IrJumpInstruction(label,R); }
}
