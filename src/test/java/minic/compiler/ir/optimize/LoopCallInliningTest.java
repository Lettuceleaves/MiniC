package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class LoopCallInliningTest {
    private static final SourceRange R = new SourceRange(1,1,1,8), COLD = new SourceRange(3,1,3,8), HOT = new SourceRange(8,1,8,8);
    private static final IrParameter P = new IrParameter("p",MiniType.INT,IrType.INT,R);
    private static final IrTemporary X = new IrTemporary("x",IrType.INT), Y = new IrTemporary("y",IrType.INT);

    @Test void loopSiteWinsSiteBudgetEvenWhenColdCallComesFirst() {
        var source = source(coldThenLoop(call("helper",COLD),call("helper",HOT)), helper());
        var result = run(source,new SmallFunctionInliningPass.Limits(24,6,96,512,256,1));
        assertEquals(List.of(COLD), calls(result).stream().map(IrCallInstruction::range).toList());
        assertSame(source.functions().getFirst().blocks().getFirst().instructions().getFirst(),calls(result).getFirst());
    }

    @Test void frameCallerAndModuleBudgetsRemainHardBoundsWithHotPriority() {
        var source = source(coldThenLoop(call("helper",COLD),call("helper",HOT)),helper());
        for(var limits:List.of(new SmallFunctionInliningPass.Limits(24,6,96,512,30,8),
                new SmallFunctionInliningPass.Limits(24,6,1,512,256,8),
                new SmallFunctionInliningPass.Limits(24,6,96,1,256,8))) {
            var result=run(source,limits);
            assertEquals(List.of(COLD),calls(result).stream().map(IrCallInstruction::range).toList());
            assertTrue(count(result)<=count(source)+1,"one helper grows IR by one instruction");
        }
    }

    @Test void originalOrderIsTheStableTieBreakerAmongEquallyColdSites() {
        var caller=function("main",block("entry",call("helper",COLD),call("helper",HOT),ret(X)));
        var result=run(source(caller,helper()),new SmallFunctionInliningPass.Limits(24,6,96,512,256,1));
        assertEquals(List.of(HOT),calls(result).stream().map(IrCallInstruction::range).toList());
    }

    @Test void aHotSiteThatCannotFitDoesNotPreventAColdAffordableExpansion() {
        var expensive=function("expensive",block("entry",new IrBinaryInstruction(X,IrBinaryOperator.ADD,P.ref(),new IrConstant(1),R),
                new IrBinaryInstruction(Y,IrBinaryOperator.MULTIPLY,X,P.ref(),R),ret(Y)));
        var result=run(source(coldThenLoop(call("helper",COLD),call("expensive",HOT)),helper(),expensive),
                new SmallFunctionInliningPass.Limits(24,6,96,512,30,8));
        assertEquals(List.of("expensive"),calls(result).stream().map(IrCallInstruction::calleeName).toList());
    }

    @Test void reusedInstructionIdentityStillRepresentsTwoDifferentCallSites() {
        var reused=call("helper",R);
        var result=run(source(coldThenLoop(reused,reused),helper()),new SmallFunctionInliningPass.Limits(24,6,96,512,256,1));
        var caller=result.findFunction("main").orElseThrow();
        assertTrue(caller.blocks().getFirst().instructions().contains(reused));
        assertTrue(caller.blocks().stream().filter(b->b.label().equals("loop")).flatMap(b->b.instructions().stream())
                .noneMatch(IrCallInstruction.class::isInstance));
    }

    @Test void irreducibleCycleSitesCountAsHotAndKeepTheirRelativeOrder() {
        var caller=function("main",block("entry",call("helper",COLD),new IrBranchInstruction(P.ref(),"left","right",R)),
                block("left",call("helper",HOT),new IrBranchInstruction(P.ref(),"right","done",R)),
                block("right",call("helper",R),new IrBranchInstruction(P.ref(),"left","done",R)),block("done",ret(X)));
        var result=run(source(caller,helper()),new SmallFunctionInliningPass.Limits(24,6,96,512,256,1));
        assertEquals(List.of(COLD,R),calls(result).stream().map(IrCallInstruction::range).toList());
    }

    @Test void unreachableAndDeadTailCallsNeverSpendTheHotSiteBudget() {
        var caller=function("main",block("entry",call("helper",COLD),jump("loop")),
                block("dead",call("helper",R),jump("dead")),
                block("loop",call("helper",HOT),new IrBranchInstruction(P.ref(),"loop","done",R),call("helper",R)),
                block("done",ret(X)));
        var result=run(source(caller,helper()),new SmallFunctionInliningPass.Limits(24,6,96,512,256,1));
        assertEquals(3,calls(result).size());
        assertTrue(calls(result).stream().noneMatch(c->c.range().equals(HOT)));
    }

    @Test void zeroBudgetsAndRecursiveBoundariesRemainUnchanged() {
        var source=source(coldThenLoop(call("helper",COLD),call("helper",HOT)),helper());
        assertSame(source,run(source,new SmallFunctionInliningPass.Limits(24,6,0,0,0,0)));
        var recursive=function("recursive",block("entry",call("recursive",R),ret(X)));
        var caller=coldThenLoop(call("recursive",COLD),call("recursive",HOT));
        var unchanged=source(caller,recursive);
        assertSame(unchanged,run(unchanged,SmallFunctionInliningPass.Limits.defaults()));
    }

    private static IrFunction coldThenLoop(IrCallInstruction cold,IrCallInstruction hot) { return function("main",block("entry",cold,jump("loop")),block("loop",hot,new IrBranchInstruction(P.ref(),"loop","done",R)),block("done",ret(X))); }
    private static IrFunction helper() { return function("helper",block("entry",ret(P.ref()))); }
    private static IrFunction function(String name,IrBlock...blocks) { return new IrFunction(name,MiniType.INT,List.of(P),false,List.of(blocks),R); }
    private static IrBlock block(String name,IrInstruction...code) { return new IrBlock(name,List.of(code)); }
    private static IrCallInstruction call(String target,SourceRange range) { return new IrCallInstruction(X,target,List.of(P.ref()),false,range); }
    private static IrReturnInstruction ret(IrValue value) { return new IrReturnInstruction(value,R); }
    private static IrJumpInstruction jump(String label) { return new IrJumpInstruction(label,R); }
    private static IrResult source(IrFunction...functions) { return new IrResult(List.of(functions)); }
    private static IrResult run(IrResult input,SmallFunctionInliningPass.Limits limits) { IrVerifier.verify(input);var result=new SmallFunctionInliningPass(limits).apply(input);IrVerifier.verify(result);return result; }
    private static List<IrCallInstruction> calls(IrResult result) { return result.findFunction("main").orElseThrow().blocks().stream().flatMap(b->b.instructions().stream()).filter(IrCallInstruction.class::isInstance).map(IrCallInstruction.class::cast).toList(); }
    private static int count(IrResult source) { return source.functions().stream().flatMap(f->f.blocks().stream()).mapToInt(b->b.instructions().size()).sum(); }
}
