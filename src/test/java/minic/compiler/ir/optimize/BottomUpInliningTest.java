package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class BottomUpInliningTest {
    private static final SourceRange R = new SourceRange(2, 1, 2, 10);
    private static final IrTemporary X = new IrTemporary("x", IrType.INT);
    private static final IrParameter P = new IrParameter("p", MiniType.INT, IrType.INT, R);

    @Test void callersUseAlreadyExpandedCalleesRegardlessOfSourceOrder() {
        var leaf = function("leaf", new IrBinaryInstruction(X, IrBinaryOperator.ADD, P.ref(), c(1), R), ret(X));
        var middle = function("middle", call("leaf", P.ref()), ret(X));
        var main = function("main", call("middle", P.ref()), ret(X));
        for (var order : List.of(List.of(main, middle, leaf), List.of(leaf, middle, main))) {
            var source = new IrResult(order);
            var result = apply(source);
            assertEquals(0, calls(result, "main"));
            assertEquals(order.stream().map(IrFunction::name).toList(), result.functions().stream().map(IrFunction::name).toList());
            assertEquals(1, calls(source, "main"));
            assertEquals(result, apply(source));
        }
    }

    @Test void constantSafeChecksAreRemovedBeforeCandidateEligibility() {
        var leaf = function("leaf", new IrCheckNonZeroInstruction(c(4), R), ret(c(128)));
        var wrapper = function("wrapper", call("leaf", P.ref()), ret(X));
        var result = apply(new IrResult(List.of(function("main", call("wrapper", P.ref()), ret(X)), wrapper, leaf)));
        assertEquals(0, calls(result, "main"));
        assertTrue(code(result, "main").stream().noneMatch(IrCheckNonZeroInstruction.class::isInstance));
    }

    @Test void resolvedConstantBranchesLetOnlyTheReachedSafeCalleeBodyInline() {
        var leaf = new IrFunction("leaf", MiniType.INT, List.of(P), false, List.of(
                new IrBlock("entry", List.of(new IrBranchInstruction(c(1), "safe", "unsafe", R))),
                new IrBlock("safe", List.of(ret(c(9)))),
                new IrBlock("unsafe", List.of(new IrCheckNonZeroInstruction(c(0), R), ret(c(0))))), R);
        assertEquals(0, calls(apply(new IrResult(List.of(function("main", call("leaf", P.ref()), ret(X)), leaf))), "main"));
    }

    @Test void genuineZeroAndMutableChecksContinueToProtectTheCalleeReturnBoundary() {
        for (IrValue divisor : List.of(c(0), new IrConstant(256, IrType.CHAR), P.ref())) {
            var leaf = function("leaf", new IrCheckNonZeroInstruction(divisor, R), ret(c(7)));
            var result = apply(new IrResult(List.of(leaf, function("main", call("leaf", P.ref()), ret(X)))));
            assertEquals(1, calls(result, "main"));
        }
    }

    @Test void recursiveFunctionsKeepTheirBoundaryButMayInlineIndependentLeaves() {
        var leaf = function("leaf", ret(c(2)));
        var recursive = function("recursive", call("leaf", P.ref()), call("recursive", X), ret(X));
        var result = apply(new IrResult(List.of(function("main", call("recursive", P.ref()), ret(X)), recursive, leaf)));
        assertEquals(1, calls(result, "main"));
        var calls = code(result, "recursive").stream().filter(IrCallInstruction.class::isInstance).map(IrCallInstruction.class::cast).toList();
        assertEquals(List.of("recursive"), calls.stream().map(IrCallInstruction::calleeName).toList());
    }

    @Test void bottomUpExpansionHonorsSharedModuleAndCallerBudgets() {
        var leaf = function("leaf", new IrBinaryInstruction(X, IrBinaryOperator.ADD, P.ref(), c(1), R), ret(X));
        var wrapper = function("wrapper", call("leaf", P.ref()), ret(X));
        var source = new IrResult(List.of(function("main", call("wrapper", P.ref()), ret(X)), wrapper, leaf));
        var limited = new SmallFunctionInliningPass(new SmallFunctionInliningPass.Limits(24, 6, 96, 2, 256, 8)).apply(source);
        IrVerifier.verify(limited);
        assertEquals(0, calls(limited, "wrapper"));
        assertEquals(1, calls(limited, "main"));
        assertTrue(size(limited) <= size(source) + 2);
    }

    private static IrResult apply(IrResult source) { IrVerifier.verify(source); var result = new SmallFunctionInliningPass().apply(source); IrVerifier.verify(result); return result; }
    private static int size(IrResult ir) { return ir.functions().stream().flatMap(f -> f.blocks().stream()).mapToInt(b -> b.instructions().size()).sum(); }
    private static List<IrInstruction> code(IrResult ir, String name) { return ir.findFunction(name).orElseThrow().blocks().stream().flatMap(b -> b.instructions().stream()).toList(); }
    private static long calls(IrResult ir, String name) { return code(ir, name).stream().filter(IrCallInstruction.class::isInstance).count(); }
    private static IrFunction function(String name, IrInstruction... body) { return new IrFunction(name, MiniType.INT, List.of(P), false, List.of(new IrBlock("entry", List.of(body))), R); }
    private static IrCallInstruction call(String name, IrValue arg) { return new IrCallInstruction(X, name, List.of(arg), false, R); }
    private static IrReturnInstruction ret(IrValue value) { return new IrReturnInstruction(value, R); }
    private static IrConstant c(int value) { return new IrConstant(value); }
}
