package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class RegisterUseDefPositionTest {
    private static final SourceRange R=new SourceRange(1,0,1,1);
    private static final IrConstant ONE=new IrConstant(1);
    @Test void resultReusesItsLastUsedOperandHome() {
        var a=t("a");var result=t("result");
        var plan=plan(move(a,7),binary(result,a,ONE),ret(result));
        assertEquals(plan.registers().get("a"),plan.registers().get("result"));
    }
    @Test void simultaneousInputsRemainDistinctButResultCanReuseEither() {
        var a=t("a");var b=t("b");var result=t("result");
        var plan=plan(move(a,7),move(b,3),binary(result,a,b),ret(result));
        assertNotEquals(plan.registers().get("a"),plan.registers().get("b"));
        assertNotNull(plan.registers().get("result"));
        assertTrue(Set.of(plan.registers().get("a"),plan.registers().get("b")).contains(plan.registers().get("result")));
    }
    @Test void liveLeftOperandProtectsItsHomeWhileDyingRightCanBeReused() {
        var a=t("a");var b=t("b");var result=t("result");var end=t("end");
        var plan=plan(move(a,7),move(b,3),binary(result,a,b),binary(end,a,result),ret(end));
        assertNotEquals(plan.registers().get("a"),plan.registers().get("result"));
        assertEquals(plan.registers().get("b"),plan.registers().get("result"));
    }
    @Test void changingTheWidthDoesNotPreventReusingThePhysicalHome() {
        var a=new IrTemporary("a",IrType.SIGNED_CHAR);var result=new IrTemporary("result",IrType.LONG_LONG);
        var plan=plan(new IrMoveInstruction(a,new IrConstant(-7,IrType.SIGNED_CHAR),R),new IrCastInstruction(result,a,R),ret(result));
        assertEquals(plan.registers().get("a"),plan.registers().get("result"));
    }
    @Test void selectReadsAllThreeInputsBeforeItsResultDefinition() {
        var condition=t("condition");var yes=t("yes");var no=t("no");var result=t("result");
        var function=f(block("entry",move(condition,1),move(yes,7),move(no,8),
                new IrSelectInstruction(result,condition,yes,no,R),ret(result)));
        var plan=GlobalRegisterPlan.allocate(function,true);
        assertEquals(Set.of("condition","yes","no"),IrLiveness.analyze(function).liveBefore("entry",3));
        var assigned=List.of(condition,yes,no).stream().map(v->plan.registers().get(v.name())).filter(Objects::nonNull).toList();
        assertEquals(new HashSet<>(assigned).size(),assigned.size());
        assertEquals(plan.registers().get("condition"),plan.registers().get("result"));
    }
    @Test void callSurvivorDoesNotShareTheNewResultAndOriginalSpillIndicesRemainValid() {
        var live=t("live");var argument=t("argument");var result=t("result");var end=t("end");
        var code=new ArrayList<IrInstruction>();code.add(move(live,9));
        for(int i=0;i<3;i++)code.add(binary(t("before"+i),live,ONE));
        code.add(move(argument,7));int callIndex=code.size();
        code.add(new IrCallInstruction(result,"effect",List.of(argument),false,R));
        code.add(binary(end,live,result));code.add(ret(end));var function=f(new IrBlock("entry",code));
        for(boolean nonvolatile:List.of(false,true)) {
            var plan=GlobalRegisterPlan.allocate(function,nonvolatile);
            assertNotNull(plan.registers().get("live"));
            assertNotEquals(plan.registers().get("live"),plan.registers().get("result"));
            assertEquals(plan.registers().get("argument"),plan.registers().get("result"));
            assertEquals(Set.of("live"),IrLiveness.analyze(function).liveAcrossCall("entry",callIndex));
            if(!nonvolatile)assertEquals(List.of(live),plan.spillsAt("entry",callIndex));
            assertTrue(plan.spillsAt("entry",callIndex+1).isEmpty());
        }
    }
    @Test void indirectCalleeAndArgumentCannotShareUntilTheCallReturns() {
        var callee=new IrTemporary("callee",IrType.POINTER);var argument=t("argument");var result=t("result");
        var plan=plan(new IrMoveInstruction(callee,new IrFunctionAddress("effect"),R),move(argument,7),
                new IrIndirectCallInstruction(result,callee,List.of(argument),false,R),ret(result));
        assertNotEquals(plan.registers().get("callee"),plan.registers().get("argument"));
        assertEquals(plan.registers().get("callee"),plan.registers().get("result"));
    }
    @Test void nonSsaLoopAndBackedgeKeepBothLiveValuesSeparate() {
        var a=t("a");var b=t("b");var result=t("result");
        var function=f(block("entry",move(a,1),move(b,2),new IrJumpInstruction("loop",R)),
                block("loop",binary(result,a,b),binary(a,result,ONE),new IrBranchInstruction(a,"loop","done",R)),
                block("done",binary(result,a,b),ret(result)));
        var plan=GlobalRegisterPlan.allocate(function,true);
        assertNotEquals(plan.registers().get("a"),plan.registers().get("b"));
        assertNotEquals(plan.registers().get("a"),plan.registers().get("result"));
        assertNotEquals(plan.registers().get("b"),plan.registers().get("result"));
    }
    @Test void deadTailDefinitionsRemainExcluded() {
        var a=t("a");var result=t("result");
        var plan=plan(move(a,7),binary(result,a,ONE),ret(result),move(a,8));
        assertTrue(plan.stackTemporaries().contains("a"));
    }
    private static GlobalRegisterPlan plan(IrInstruction... instructions){return GlobalRegisterPlan.allocate(f(block("entry",instructions)),true);}
    private static IrTemporary t(String name){return new IrTemporary(name,IrType.INT);}
    private static IrMoveInstruction move(IrTemporary target,int value){return new IrMoveInstruction(target,new IrConstant(value,target.type()),R);}
    private static IrBinaryInstruction binary(IrTemporary result,minic.compiler.ir.value.IrValue a,minic.compiler.ir.value.IrValue b){return new IrBinaryInstruction(result,IrBinaryOperator.SUBTRACT,a,b,R);}
    private static IrReturnInstruction ret(IrTemporary value){return new IrReturnInstruction(value,R);}
    private static IrBlock block(String name,IrInstruction... instructions){return new IrBlock(name,List.of(instructions));}
    private static IrFunction f(IrBlock... blocks){return new IrFunction("main",MiniType.INT,List.of(),false,List.of(blocks),R);}
}
