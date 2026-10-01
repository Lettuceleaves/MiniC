package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class TemporarySlotPlanTest {
    private static final SourceRange R=new SourceRange(1,1,1,10);
    private static final IrTemporary A=temp("a"), B=temp("b"), C=temp("c"), X=temp("x"), Y=temp("y");
    private static final IrConstant ONE=new IrConstant(1), TWO=new IrConstant(2);
    private static final IrParameter P=new IrParameter("p",MiniType.INT,IrType.INT,R);

    @Test void linearChainReusesOneSlotAndKeepsEveryLogicalTemporary() {
        var plan=plan(block("entry",move(A,ONE),add(B,A,ONE),add(C,B,ONE),ret(C)));
        assertEquals(3,plan.temporaryCount());assertEquals(1,plan.slotCount());
        assertEquals(4,plan.storageBytes());assertEquals(12,plan.unsharedBytes());
        assertEquals(plan.slot("a"),plan.slot("b"));assertEquals(plan.slot("b"),plan.slot("c"));
        assertEquals("temporary-stack-slot-reuse",plan.strategyName());
    }

    @Test void simultaneousOperandsCannotShareEvenWhenTheyBothDieAtTheInstruction() {
        var plan=plan(block("entry",move(A,ONE),move(B,TWO),add(C,A,B),ret(C)));
        assertNotEquals(plan.slot("a"),plan.slot("b"));
        assertEquals(2,plan.slotCount());assertEquals(8,plan.storageBytes());
    }

    @Test void deadDefinitionCannotOverwriteAnotherLiveValue() {
        var plan=plan(block("entry",move(A,ONE),move(B,TWO),add(X,B,ONE),ret(A)));
        assertNotEquals(plan.slot("a"),plan.slot("x"));
        assertNotEquals(plan.slot("a"),plan.slot("b"));
        assertEquals(plan.slot("b"),plan.slot("x"));
    }

    @Test void nonSsaBranchDefinitionsKeepOneSlotAndInterfereWithValuesAcrossTheJoin() {
        var function=fn(block("entry",move(A,ONE),branch("yes","no")),block("yes",move(X,TWO),jump("join")),
                block("no",move(X,ONE),jump("join")),block("join",add(Y,A,X),ret(Y)));
        var plan=TemporarySlotPlan.allocate(function);
        assertNotEquals(plan.slot("a"),plan.slot("x"));
        assertEquals(2,plan.slotCount());
        assertSound(function,plan);
    }

    @Test void loopCounterAndExitValueRemainDistinctAcrossBackEdges() {
        var function=fn(block("entry",move(X,ONE),move(Y,TWO),jump("loop")),
                block("loop",add(X,X,ONE),branch("loop","done")),block("done",add(A,X,Y),ret(A)));
        var plan=TemporarySlotPlan.allocate(function);
        assertNotEquals(plan.slot("x"),plan.slot("y"));
        assertEquals(2,plan.slotCount());assertSound(function,plan);
    }

    @Test void callResultCannotClobberAcrossCallValueIncludingSameNameResult() {
        var function=fn(block("entry",move(A,ONE),move(X,TWO),new IrCallInstruction(X,"callee",List.of(X),false,R),add(Y,A,X),ret(Y)));
        var plan=TemporarySlotPlan.allocate(function);
        assertNotEquals(plan.slot("a"),plan.slot("x"));
        assertEquals(2,plan.slotCount());assertSound(function,plan);
    }

    @Test void mixedWidthsUseDisjointAlignedGroupsAndSameWidthIntegerFloatMayReuse() {
        var byteValue=new IrTemporary("byte",IrType.CHAR);
        var shortValue=new IrTemporary("short",IrType.SHORT);
        var floatValue=new IrTemporary("float",IrType.FLOAT);
        var wide=new IrTemporary("wide",IrType.LONG_LONG);
        var pointer=new IrTemporary("pointer",IrType.POINTER);
        var plan=plan(block("entry",move(byteValue,new IrConstant(1,IrType.CHAR)),move(shortValue,new IrConstant(2,IrType.SHORT)),
                move(A,ONE),move(floatValue,new IrFloatConstant(2.0,IrType.FLOAT)),
                move(wide,new IrConstant(3,IrType.LONG_LONG)),move(pointer,new IrConstant(0,IrType.POINTER)),ret(ONE)));
        assertEquals(plan.slot("a"),plan.slot("float"));assertEquals(plan.slot("wide"),plan.slot("pointer"));
        assertEquals(4,plan.slotCount());assertEquals(15,plan.storageBytes());
        var physical=new HashSet<>(plan.slots().values());
        for(var slot:physical)assertEquals(0,slot.offset()%slot.sizeBytes());
        for(var left:physical)for(var right:physical)if(!left.equals(right))assertFalse(overlaps(left,right));
    }

    @Test void anyTemporaryMentionedInNonExecutableCodeGetsAnExclusiveSlot() {
        var function=fn(block("entry",move(A,ONE),add(B,A,ONE),ret(B),add(X,A,ONE)),block("dead",move(Y,TWO),ret(Y)));
        var plan=TemporarySlotPlan.allocate(function);
        assertEquals(Set.of("a","x","y"),plan.pinnedTemporaries());
        for(String name:plan.pinnedTemporaries())for(String other:plan.slots().keySet())if(!name.equals(other))
            assertNotEquals(plan.slot(name),plan.slot(other));
        assertEquals(4,plan.slotCount());
    }

    @Test void emptyPlansAndAllReturnedDataAreImmutableAndDeterministic() {
        var empty=plan(block("entry",ret(ONE)));
        assertEquals(0,empty.storageBytes());assertEquals(0,empty.slotCount());
        var function=fn(block("entry",move(A,ONE),add(B,A,ONE),ret(B)));
        var plan=TemporarySlotPlan.allocate(function);
        assertEquals(plan.slots(),TemporarySlotPlan.allocate(function).slots());
        assertThrows(UnsupportedOperationException.class,()->plan.slots().clear());
        assertThrows(UnsupportedOperationException.class,()->plan.pinnedTemporaries().add("x"));
        assertThrows(IllegalArgumentException.class,()->plan.slot("absent"));
    }

    private static void assertSound(IrFunction function,TemporarySlotPlan plan) {
        var analysis=IrLiveness.analyze(function);
        for(var block:function.blocks())for(int index=0;index<block.instructions().size();index++) {
            var point=analysis.instruction(block.label(),index);
            if(!point.executable())continue;
            for(String left:point.liveBefore())for(String right:point.liveBefore())if(!left.equals(right))
                assertFalse(overlaps(plan.slot(left),plan.slot(right)),left+" vs "+right);
            var result=IrValueUses.result(block.instructions().get(index));
            if(result!=null)for(String live:point.liveAfter())if(!live.equals(result.name()))
                assertFalse(overlaps(plan.slot(live),plan.slot(result.name())),result.name()+" overwrites "+live);
        }
    }
    private static boolean overlaps(TemporarySlotPlan.Slot a,TemporarySlotPlan.Slot b){return a.offset()>b.offset()-b.sizeBytes()&&b.offset()>a.offset()-a.sizeBytes();}
    private static TemporarySlotPlan plan(IrBlock... blocks){return TemporarySlotPlan.allocate(fn(blocks));}
    private static IrTemporary temp(String name){return new IrTemporary(name,IrType.INT);}
    private static IrFunction fn(IrBlock... blocks){return new IrFunction("function",MiniType.INT,List.of(P),false,List.of(blocks),R);}
    private static IrBlock block(String name,IrInstruction... instructions){return new IrBlock(name,List.of(instructions));}
    private static IrMoveInstruction move(IrTemporary result,IrValue value){return new IrMoveInstruction(result,value,R);}
    private static IrBinaryInstruction add(IrTemporary result,IrValue left,IrValue right){return new IrBinaryInstruction(result,IrBinaryOperator.ADD,left,right,R);}
    private static IrReturnInstruction ret(IrValue value){return new IrReturnInstruction(value,R);}
    private static IrJumpInstruction jump(String name){return new IrJumpInstruction(name,R);}
    private static IrBranchInstruction branch(String yes,String no){return new IrBranchInstruction(P.ref(),yes,no,R);}
}
