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

final class LoopWeightedRegisterPlanTest {
    private static final SourceRange R=new SourceRange(1,0,1,8);
    private static final IrConstant ONE=new IrConstant(1);

    @Test void twoTransferLoopTemporaryCanRepayOneNonvolatileSave() {
        var function=loop(false,false);var plan=GlobalRegisterPlan.allocate(function,true);
        assertEquals("rbx",plan.registers().get("scratch"));
        assertEquals(List.of("rbx"),plan.calleeSavedRegisters());
        assertTrue(GlobalRegisterPlan.allocate(function).stackTemporaries().contains("scratch"),"legacy volatile-only allocation is unchanged");
    }

    @Test void bothBlocksOfAnIrreducibleCycleReceiveTheSameConservativeBenefit() {
        var a=t("a");var b=t("b");var first=t("first");var second=t("second");
        var function=f(block("entry",move(a,1),move(b,2),new IrBranchInstruction(ONE,"left","right",R)),
                block("left",add(first,a,b),add(a,first,ONE),new IrJumpInstruction("right",R)),
                block("right",add(second,a,b),add(b,second,ONE),new IrBranchInstruction(b,"left","done",R)),
                block("done",add(a,a,b),new IrReturnInstruction(a,R)));
        var plan=GlobalRegisterPlan.allocate(function,true);
        assertEquals("rbx",plan.registers().get("first"));assertEquals("rbx",plan.registers().get("second"));
    }

    @Test void anAlreadySavedFreeHomeCanServeACheapLaterValueWithoutAnotherSave() {
        var a=t("a");var b=t("b");var paid=t("paid");var cheap=t("cheap");
        var code=new ArrayList<IrInstruction>(List.of(move(a,1),move(b,2),move(paid,3)));
        for(int i=0;i<3;i++)code.add(add(t("drop"+i),paid,ONE));
        code.add(move(cheap,7));code.add(add(a,a,cheap));code.add(add(a,a,b));code.add(new IrReturnInstruction(a,R));
        var plan=GlobalRegisterPlan.allocate(f(new IrBlock("entry",code)),true);
        assertEquals("rbx",plan.registers().get("paid"));assertEquals("rbx",plan.registers().get("cheap"));
        assertEquals(List.of("rbx"),plan.calleeSavedRegisters());
    }

    @Test void acyclicTwoTransferValueDoesNotOpenANewNonvolatileHome() {
        var a=t("a");var b=t("b");var scratch=t("scratch");
        var plan=GlobalRegisterPlan.allocate(f(block("entry",move(a,1),move(b,2),add(scratch,a,b),
                add(a,a,scratch),add(a,a,b),new IrReturnInstruction(a,R))),true);
        assertTrue(plan.calleeSavedRegisters().isEmpty());assertTrue(plan.stackTemporaries().contains("scratch"));
    }

    @Test void merelyBeingLiveThroughALoopDoesNotIncreaseAccessBenefit() {
        var value=t("value");
        var plan=GlobalRegisterPlan.allocate(f(block("entry",move(value,5),new IrJumpInstruction("loop",R)),
                block("loop",new IrCallInstruction(null,"effect",List.of(),false,R),new IrBranchInstruction(ONE,"loop","done",R)),
                block("done",new IrReturnInstruction(value,R))),true);
        assertTrue(plan.stackTemporaries().contains("value"));assertTrue(plan.calleeSavedRegisters().isEmpty());
        assertTrue(plan.spillsAt("loop",0).isEmpty());
    }

    @Test void loopCallSurvivorUsesANonvolatileHomeWithoutPerIterationSpills() {
        var a=t("a");var scratch=t("scratch");
        var plan=GlobalRegisterPlan.allocate(f(block("entry",move(a,1),new IrJumpInstruction("loop",R)),
                block("loop",add(scratch,a,ONE),new IrCallInstruction(null,"effect",List.of(),false,R),
                        add(a,scratch,ONE),new IrBranchInstruction(a,"loop","done",R)),
                block("done",new IrReturnInstruction(a,R))),true);
        assertNotNull(plan.registers().get("scratch"));assertTrue(plan.calleeSavedRegisters().contains(plan.registers().get("scratch")));
        assertTrue(plan.spillsAt("loop",1).isEmpty());
    }

    @Test void fusedComparisonAndBooleanHomesDoNotOpenOtherwiseUnusedSavedRegisters() {
        var a=t("a");var b=t("b");var condition=t("condition");var truth=new IrTemporary("truth",IrType.BOOL);
        var plan=GlobalRegisterPlan.allocate(f(block("entry",move(a,1),move(b,2),new IrJumpInstruction("loop",R)),
                block("loop",add(a,a,ONE),add(b,b,ONE),new IrBinaryInstruction(condition,IrBinaryOperator.LESS_THAN,a,b,R),
                        new IrCastInstruction(truth,condition,R),new IrBranchInstruction(truth,"loop","done",R)),
                block("done",add(a,a,b),new IrReturnInstruction(a,R))),true);
        assertTrue(plan.calleeSavedRegisters().isEmpty(),plan.registers().toString());
    }

    @Test void unreachableCyclesAndDeadTailDefinitionsKeepTheirStackOnlyTreatment() {
        var function=loop(true,true);var plan=GlobalRegisterPlan.allocate(function,true);
        assertTrue(plan.stackTemporaries().containsAll(Set.of("scratch","unreachable")));
        assertTrue(plan.calleeSavedRegisters().isEmpty());
    }

    private static IrFunction loop(boolean deadTail,boolean unreachable) {
        var a=t("a");var b=t("b");var scratch=t("scratch");var condition=t("condition");
        var body=new ArrayList<IrInstruction>(List.of(add(scratch,a,b),add(a,scratch,b),add(b,b,ONE),
                new IrBinaryInstruction(condition,IrBinaryOperator.LESS_THAN,b,new IrConstant(4),R),
                new IrBranchInstruction(condition,"loop","done",R)));
        if(deadTail)body.add(move(scratch,0));
        var blocks=new ArrayList<IrBlock>(List.of(block("entry",move(a,0),move(b,1),new IrJumpInstruction("loop",R)),new IrBlock("loop",body),
                block("done",add(a,a,b),new IrReturnInstruction(a,R))));
        if(unreachable)blocks.add(block("unreachable",move(t("unreachable"),1),new IrJumpInstruction("unreachable",R)));
        return new IrFunction("main",MiniType.INT,List.of(),false,blocks,R);
    }
    private static IrTemporary t(String name){return new IrTemporary(name,IrType.INT);}
    private static IrMoveInstruction move(IrTemporary value,int n){return new IrMoveInstruction(value,new IrConstant(n),R);}
    private static IrBinaryInstruction add(IrTemporary result,minic.compiler.ir.value.IrValue left,minic.compiler.ir.value.IrValue right){return new IrBinaryInstruction(result,IrBinaryOperator.ADD,left,right,R);}
    private static IrBlock block(String name,IrInstruction... code){return new IrBlock(name,List.of(code));}
    private static IrFunction f(IrBlock... blocks){return new IrFunction("main",MiniType.INT,List.of(),false,List.of(blocks),R);}
}
