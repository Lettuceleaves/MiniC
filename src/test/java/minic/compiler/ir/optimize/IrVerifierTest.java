package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.semantic.model.StructLayout;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class IrVerifierTest {
    private static final SourceRange RANGE = new SourceRange(3, 2, 3, 9);
    private static final IrConstant ZERO = new IrConstant(0);
    private static final IrTemporary VALUE = new IrTemporary("%value", IrType.INT);

    @Test void damagedCfgReportsStableLocationsAndDoesNotThrowDuringInspection() {
        var problems = IrVerifier.inspect(program(block("entry", new IrJumpInstruction("absent", RANGE))));
        var problem = problems.stream().filter(p -> p.code().equals("MISSING_TARGET")).findFirst().orElseThrow();
        assertEquals("main", problem.function());
        assertEquals("entry", problem.block());
        assertEquals(0, problem.instructionIndex());
        assertEquals(RANGE, problem.range());
        var failure = assertThrows(IrVerifier.VerificationException.class,
                () -> IrVerifier.verify(program(block("entry", new IrJumpInstruction("absent", RANGE)))));
        assertEquals(problems, failure.problems());
        assertThrows(UnsupportedOperationException.class, () -> failure.problems().clear());
        reject("DUPLICATE_BLOCK", program(block("entry", ret()), block("entry", ret())));
        reject("MISSING_TERMINATOR", program(block("entry", new IrMoveInstruction(VALUE, ZERO, RANGE))));
        reject("NO_ENTRY", new IrResult(List.of(function("main", List.of(), List.of()))));
    }

    @Test void deadTailAndUnreachableEmptyMergeRemainValid() {
        valid(program(block("entry", ret(), new IrMoveInstruction(VALUE, ZERO, RANGE)), block("dead", List.of())));
    }

    @Test void branchDefinitionsCanShareOneTemporaryWithoutBeingSsa() {
        valid(diamond(true));
        reject("UNDEFINED_TEMPORARY", diamond(false));
    }

    @Test void backedgeDoesNotMakeATemporaryDefinedOnTheFirstIteration() {
        var loop = program(block("entry", new IrJumpInstruction("loop", RANGE)),
                block("loop", new IrMoveInstruction(new IrTemporary("%copy", IrType.INT), VALUE, RANGE),
                        new IrMoveInstruction(VALUE, ZERO, RANGE), new IrJumpInstruction("loop", RANGE)));
        reject("UNDEFINED_TEMPORARY", loop);
    }

    @Test void temporaryUseBeforeLocalDefinitionAndUnknownTemporaryAreDifferentFailures() {
        reject("UNDEFINED_TEMPORARY", program(block("entry", new IrCheckNonZeroInstruction(VALUE, RANGE),
                new IrMoveInstruction(VALUE, ZERO, RANGE), ret())));
        reject("UNKNOWN_TEMPORARY", program(block("entry", new IrReturnInstruction(VALUE, RANGE))));
        reject("TEMPORARY_TYPE", program(block("entry", new IrMoveInstruction(VALUE, ZERO, RANGE),
                new IrReturnInstruction(new IrTemporary(VALUE.name(), IrType.LONG_LONG), RANGE))));
    }

    @Test void parametersMustExistAndKeepTheirDeclaredAbiType() {
        var parameter = new IrParameter("x", MiniType.INT, IrType.INT, RANGE);
        valid(new IrResult(List.of(function("main", List.of(parameter), List.of(block("entry", new IrReturnInstruction(parameter.ref(), RANGE)))))));
        reject("UNKNOWN_PARAMETER", program(block("entry", new IrReturnInstruction(new IrParameterRef("lost", IrType.INT), RANGE))));
        reject("UNKNOWN_PARAMETER", program(block("entry", new IrLoadPointerInstruction(VALUE, new IrParameterAddress("lost"), RANGE), ret())));
        reject("PARAMETER_TYPE", new IrResult(List.of(function("main", List.of(parameter), List.of(block("entry",
                new IrReturnInstruction(new IrParameterRef("x", IrType.DOUBLE), RANGE)))))));
        reject("DUPLICATE_PARAMETER", new IrResult(List.of(function("main", List.of(parameter, parameter), List.of(block("entry", ret()))))));
    }

    @Test void ordinaryLocalNeedsConsistentStorageButInitializationIsNotAVerifierProof() {
        IrLocal local = local("x#0", MiniType.INT, IrType.INT, 4);
        valid(program(block("entry", new IrDeclareLocalInstruction(local, RANGE),
                new IrCheckInitializedInstruction(local, RANGE), new IrLoadLocalInstruction(VALUE, local, RANGE), ret())));
        reject("LOCAL_CONFLICT", program(block("entry", new IrDeclareLocalInstruction(local, RANGE),
                new IrLoadLocalInstruction(new IrTemporary("%other", IrType.LONG_LONG),
                        local("x#0", MiniType.LONG_LONG, IrType.LONG_LONG, 8), RANGE), ret())));
        // va_start uses a pseudo slot with no ordinary declaration or frame storage.
        valid(program(block("entry", new IrAddressOfLocalInstruction(new IrTemporary("%area", IrType.POINTER),
                IrLocal.incomingArgumentArea(4, RANGE), RANGE), ret())));
    }

    @Test void localStorageMustHoldItsTypeAndRespectNaturalAlignment() {
        reject("LAYOUT", program(block("entry", new IrDeclareLocalInstruction(local("small", MiniType.INT, IrType.INT, 1), RANGE), ret())));
        var misaligned = new IrLocal("misaligned", "x", MiniType.INT, IrType.INT, 4, 2, RANGE);
        reject("LAYOUT", program(block("entry", new IrDeclareLocalInstruction(misaligned, RANGE), ret())));
        var invalidAlignment = new IrLocal("alignment", "x", MiniType.INT, IrType.INT, 12, 6, RANGE);
        reject("LAYOUT", program(block("entry", new IrDeclareLocalInstruction(invalidAlignment, RANGE), ret())));
        var overAligned = new IrLocal("aligned", "x", MiniType.INT, IrType.INT, 4, 64, RANGE);
        valid(program(block("entry", new IrDeclareLocalInstruction(overAligned, RANGE), ret())));
    }

    @Test void scalarAndAddressInstructionTypesAreCheckedWithoutInventingPointeeTypes() {
        reject("TYPE_MISMATCH", program(block("entry", new IrMoveInstruction(VALUE, new IrFloatConstant(1, IrType.DOUBLE), RANGE), ret())));
        reject("TYPE_MISMATCH", program(block("entry", new IrLoadPointerInstruction(VALUE, ZERO, RANGE), ret())));
        reject("TYPE_MISMATCH", program(block("entry", new IrMemCopyInstruction(ZERO, ZERO, 4, RANGE), ret())));
        reject("TYPE_MISMATCH", program(block("entry", new IrBinaryInstruction(VALUE, IrBinaryOperator.BITWISE_AND,
                new IrFloatConstant(1, IrType.DOUBLE), new IrFloatConstant(2, IrType.DOUBLE), RANGE), ret())));
        valid(program(block("entry", new IrBinaryInstruction(new IrTemporary("%difference", IrType.LONG_LONG),
                IrBinaryOperator.SUBTRACT, new IrConstant(100, IrType.POINTER), new IrConstant(96, IrType.POINTER), RANGE), ret())));
    }

    @Test void definedCallsUseLoweredParametersIncludingHiddenReturnPointer() {
        IrParameter argument = new IrParameter("x", MiniType.INT, IrType.INT, RANGE);
        var callee = function("callee", List.of(argument), List.of(block("entry", new IrReturnInstruction(argument.ref(), RANGE))));
        valid(withCallee(callee, new IrCallInstruction(VALUE, "callee", List.of(ZERO), false, RANGE)));
        reject("CALL_ABI", withCallee(callee, new IrCallInstruction(VALUE, "callee", List.of(), false, RANGE)));
        reject("CALL_ABI", withCallee(callee, new IrCallInstruction(VALUE, "callee", List.of(new IrConstant(0, IrType.POINTER)), false, RANGE)));
        reject("CALL_ABI", withCallee(callee, new IrCallInstruction(new IrTemporary("%bad", IrType.DOUBLE), "callee", List.of(ZERO), false, RANGE)));
        reject("CALL_ABI", withCallee(callee, new IrCallInstruction(VALUE, "callee", List.of(ZERO), true, RANGE)));
        // A caller is allowed to ignore a non-void result.
        valid(withCallee(callee, new IrCallInstruction(null, "callee", List.of(ZERO), false, RANGE)));
    }

    @Test void externalAndIndirectSignaturesAreNotClaimedToBeKnown() {
        var instructions = block("entry", new IrCallInstruction(VALUE, "external", List.of(ZERO), true, RANGE),
                new IrIndirectCallInstruction(null, new IrFunctionAddress("external"), List.of(), false, RANGE), ret());
        valid(new IrResult(List.of(function("main", List.of(), List.of(instructions))), List.of(), Set.of("external")));
        reject("UNKNOWN_SYMBOL", program(block("entry", new IrCallInstruction(VALUE, "missing", List.of(), false, RANGE), ret())));
        reject("TYPE_MISMATCH", program(block("entry", new IrIndirectCallInstruction(null, ZERO, List.of(), false, RANGE), ret())));
    }

    @Test void dataAddressesAndSymbolCollisionsAreChecked() {
        reject("UNKNOWN_SYMBOL", program(block("entry", new IrLoadPointerInstruction(VALUE, new IrGlobalAddress("missing"), RANGE), ret())));
        reject("UNKNOWN_SYMBOL", program(block("entry", new IrMoveInstruction(new IrTemporary("%s", IrType.POINTER), new IrStringLiteral("missing"), RANGE), ret())));
        var function = function("main", List.of(), List.of(block("entry", ret())));
        reject("DUPLICATE_SYMBOL", new IrResult(List.of(function, function)));
    }

    @Test void fieldAndElementLayoutsAgreeWithRetainedTypeInformation() {
        var field = new StructLayout.StructFieldLayout("promoted", MiniType.INT, 4, 4, 4);
        var layout = new StructLayout("Box", 8, 4, List.of(), Map.of("promoted", field));
        var pointer = new IrConstant(0, IrType.POINTER);
        var address = new IrTemporary("%address", IrType.POINTER);
        var good = new IrFieldAddressInstruction(address, pointer, "Box", "promoted", 4, MiniType.INT, RANGE);
        valid(new IrResult(List.of(function("main", List.of(), List.of(block("entry", good, ret())))), List.of(), Set.of(), Map.of("Box", layout)));
        var bad = new IrFieldAddressInstruction(address, pointer, "Box", "promoted", 0, MiniType.INT, RANGE);
        reject("LAYOUT", new IrResult(List.of(function("main", List.of(), List.of(block("entry", bad, ret())))), List.of(), Set.of(), Map.of("Box", layout)));
        var wrongType = new IrFieldAddressInstruction(address, pointer, "Box", "promoted", 4, MiniType.LONG, RANGE);
        reject("LAYOUT", new IrResult(List.of(function("main", List.of(), List.of(block("entry", wrongType, ret())))), List.of(), Set.of(), Map.of("Box", layout)));
        reject("LAYOUT", program(block("entry", new IrElementAddressInstruction(address, pointer, ZERO, MiniType.INT, 8, RANGE), ret())));
    }

    @Test void debugInstrumentationCannotEnterTheNativeOptimizationBoundary() {
        reject("DEBUG_TRAP", program(block("entry", new IrTrapInstruction(TrapKind.LINE, RANGE), ret())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "int main(){int x=1; return x?2:3;}",
            "int main(){return 0;int dead=5;}",
            "int f(int x){if(x)return 1;else return 2;}int main(){return f(1)-1;}",
            "int main(){while(1){break;int dead=5;}return 0;}",
            "int main(){int n=1;while(n<4)n++;return n-4;}",
            "int main(){int a[2]={1,2};int *p=a;return (int)((p+1)-p);}",
            "int main(){int *p=(int*)0;return p==0;}",
            "struct S{int x;int y;};struct S copy(struct S s){return s;}int main(){struct S a={1,2};struct S b=copy(a);return b.x-1;}",
            "#include <stdio.h>\nint main(){printf(\"%d %f\\n\",(char)1,(float)2);return 0;}",
            "#include <stdarg.h>\nint first(int n,...){va_list a;va_start(a,n);int v=va_arg(a,int);va_end(a);return v;}int main(){return first(1,2)-2;}",
            "#include <stdarg.h>\nint first(int n,...){va_list a;va_list b;va_start(a,n);va_start(b,n);int x=va_arg(a,int);int y=va_arg(b,int);va_end(a);va_end(b);return x+y;}int main(){return first(1,2)-4;}",
            "int f(int n){return n+1;}int main(){int(*p)(int)=f;return p(1)-2;}"
    })
    void acceptsRealLowererOutputWithoutModifyingIt(String text) {
        var ir = new CompilerApi(new SourceFile("verify.c", text)).runToIr();
        var functions = ir.functions();
        valid(ir);
        assertSame(functions, ir.functions());
    }

    @Test void cppMemberReturnAbiVerifiesWithBothHiddenParameters() {
        valid(new CompilerApi(new SourceFile("verify.cpp", """
                struct Result{int x;int y;};
                struct Box{int n;Result get(int a,int b,int c,int d,int e){Result r={n+a+b+c+d+e,n};return r;}};
                int main(){Box box={1};Result result=box.get(1,2,3,4,5);return result.x-16;}
                """), LanguageMode.CPP17_ALGORITHM).runToIr());
    }

    @Test void constMethodsAndQualifiedOwnerFieldsRetainValidAddressMetadata() {
        valid(new CompilerApi(new SourceFile("const-members.cpp", """
                struct Inner { int number; int read() const { return number; } };
                struct Outer {
                    Inner items[2]; int *pointer;
                    int read() const { return items[0].read() + items[1].read() + *pointer; }
                };
                int main() {
                    int number=3; Outer object={{{1},{2}}, &number};
                    const Outer *view=&object; return view->read()-6;
                }
                """), LanguageMode.CPP17_ALGORITHM).runToIr());
    }

    @Test void constAggregateInitializationCanAddTopLevelFieldQualification() {
        valid(new CompilerApi(new SourceFile("const-initializer.cpp", """
                struct Box { int x; int read() const { return x; } };
                int main(){ const Box value = {7}; return value.read(); }
                """), LanguageMode.CPP17_ALGORITHM).runToIr());
    }

    private static IrResult diamond(boolean defineElse) {
        return program(block("entry", new IrBranchInstruction(ZERO, "left", "right", RANGE)),
                block("left", new IrMoveInstruction(VALUE, ZERO, RANGE), new IrJumpInstruction("merge", RANGE)),
                defineElse ? block("right", new IrMoveInstruction(VALUE, new IrConstant(1), RANGE), new IrJumpInstruction("merge", RANGE))
                        : block("right", new IrJumpInstruction("merge", RANGE)),
                block("merge", new IrReturnInstruction(VALUE, RANGE)));
    }
    private static IrLocal local(String name, MiniType type, IrType irType, int size) {
        return new IrLocal(name, "x", type, irType, size, size, RANGE);
    }
    private static IrResult withCallee(IrFunction callee, IrInstruction call) {
        return new IrResult(List.of(callee, function("main", List.of(), List.of(block("entry", call, ret())))));
    }
    private static IrResult program(IrBlock... blocks) { return new IrResult(List.of(function("main", List.of(), List.of(blocks)))); }
    private static IrFunction function(String name, List<IrParameter> parameters, List<IrBlock> blocks) {
        return new IrFunction(name, MiniType.INT, parameters, false, blocks, RANGE);
    }
    private static IrBlock block(String name, IrInstruction... instructions) { return block(name, List.of(instructions)); }
    private static IrBlock block(String name, List<IrInstruction> instructions) { return new IrBlock(name, instructions); }
    private static IrReturnInstruction ret() { return new IrReturnInstruction(ZERO, RANGE); }
    private static void valid(IrResult ir) { assertEquals(List.of(), IrVerifier.inspect(ir)); }
    private static void reject(String code, IrResult ir) {
        var problems = IrVerifier.inspect(ir);
        assertTrue(problems.stream().anyMatch(problem -> problem.code().equals(code)), () -> code + " missing: " + problems);
    }
}
