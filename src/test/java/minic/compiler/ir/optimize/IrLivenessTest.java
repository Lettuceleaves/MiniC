package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
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

import static org.junit.jupiter.api.Assertions.*;

final class IrLivenessTest {
    private static final SourceRange R=new SourceRange(1,1,1,10);
    private static final IrTemporary A=temp("a"), B=temp("b"), X=temp("x"), Y=temp("y");
    private static final IrConstant ONE=new IrConstant(1), TWO=new IrConstant(2);
    private static final IrParameter P=new IrParameter("condition",MiniType.INT,IrType.INT,R);

    @Test void includesInputsOfDeadComputationsAndReportsTheirDestinationsBesideSurvivingValues() {
        var function=fn(block("entry",move(A,ONE),move(B,TWO),add(X,A,ONE),ret(B)));
        var live=IrLiveness.analyze(function);
        assertEquals(Set.of("a","b"),live.liveBefore("entry",2));
        assertEquals(Set.of("b"),live.liveAfter("entry",2));
        assertEquals(Set.of("a"),live.liveAfter("entry",0));
        assertEquals(Map.of("a",IrType.INT,"b",IrType.INT,"x",IrType.INT),live.temporaryTypes());
        assertTrue(live.instruction("entry",2).executable());
        assertEquals(List.of(move(A,ONE),move(B,TWO),add(X,A,ONE),ret(B)),function.blocks().getFirst().instructions());
    }

    @Test void repeatedDefinitionsKillThePreviousValueWithoutAssumingSsa() {
        var live=IrLiveness.analyze(fn(block("entry",move(X,ONE),move(X,TWO),ret(X))));
        assertEquals(Set.of(),live.liveBefore("entry",0));
        assertEquals(Set.of(),live.liveAfter("entry",0));
        assertEquals(Set.of(),live.liveBefore("entry",1));
        assertEquals(Set.of("x"),live.liveAfter("entry",1));
        assertEquals(Set.of("x"),live.liveBefore("entry",2));
        assertEquals(Set.of(),live.liveAfter("entry",2));
    }

    @Test void readModifyWriteHasTheSameNameLiveOnBothSides() {
        var live=IrLiveness.analyze(fn(block("entry",move(X,ONE),add(X,X,ONE),ret(X))));
        assertEquals(Set.of("x"),live.liveBefore("entry",1));
        assertEquals(Set.of("x"),live.liveAfter("entry",1));
    }

    @Test void branchMergeKeepsBothDefinitionsLiveButNoUndefinedMergeValueAtEntry() {
        var live=IrLiveness.analyze(fn(block("entry",branch("yes","no")),
                block("yes",move(X,ONE),jump("join")),block("no",move(X,TWO),jump("join")),block("join",ret(X))));
        assertEquals(Set.of(),live.liveIn("entry"));assertEquals(Set.of(),live.liveOut("entry"));
        assertEquals(Set.of("x"),live.liveOut("yes"));assertEquals(Set.of("x"),live.liveOut("no"));
        assertEquals(Set.of("x"),live.liveIn("join"));
        assertEquals(Set.of("yes","no"),new HashSet<>(live.controlFlow().predecessors("join")));
    }

    @Test void loopBackEdgesReachAFixedPointAndKeepDistinctExitNeeds() {
        var live=IrLiveness.analyze(fn(block("entry",move(X,ONE),move(Y,TWO),jump("loop")),
                block("loop",add(X,X,ONE),branch("loop","done")),block("done",add(A,X,Y),ret(A))));
        assertEquals(Set.of("x","y"),live.liveIn("loop"));
        assertEquals(Set.of("x","y"),live.liveOut("loop"));
        assertEquals(Set.of("x","y"),live.liveBefore("loop",0));
        assertEquals(Set.of(),live.liveIn("entry"));
        assertEquals(Set.of("x","y"),live.liveOut("entry"));
    }

    @Test void directCallExcludesItsSameNamedNewResultFromValuesLiveAcrossTheCall() {
        var live=IrLiveness.analyze(fn(block("entry",move(A,ONE),move(X,TWO),
                new IrCallInstruction(X,"callee",List.of(X),false,R),add(Y,A,X),ret(Y))));
        assertEquals(Set.of("a","x"),live.liveBefore("entry",2));
        assertEquals(Set.of("a","x"),live.liveAfter("entry",2));
        assertEquals(Set.of("a"),live.liveAcrossCall("entry",2));
        assertEquals(Set.of("a"),live.liveAcrossCalls());
        assertEquals(Set.of(),live.liveAcrossCall("entry",3));
    }

    @Test void indirectCalleeAndArgumentUsesParticipateAndVoidCallsHaveNoKill() {
        var pointer=new IrTemporary("function",IrType.POINTER);
        var live=IrLiveness.analyze(fn(block("entry",move(pointer,new IrFunctionAddress("callee")),move(X,ONE),
                new IrIndirectCallInstruction(null,pointer,List.of(X),false,R),
                new IrIndirectCallInstruction(Y,pointer,List.of(),false,R),ret(X))));
        assertEquals(Set.of("function","x"),live.liveBefore("entry",2));
        assertEquals(Set.of("function","x"),live.liveAcrossCall("entry",2));
        assertEquals(Set.of("x"),live.liveAcrossCall("entry",3));
        assertEquals(IrType.POINTER,live.temporaryTypes().get("function"));
    }

    @Test void localsParameterSlotsAndInitializationFlagsRemainOutsideTheTemporaryDomain() {
        var local=new IrLocal("x","x",MiniType.INT,IrType.INT,4,4,R);
        var address=new IrTemporary("address",IrType.POINTER);
        var live=IrLiveness.analyze(fn(block("entry",new IrDeclareLocalInstruction(local,R),
                new IrStoreLocalInstruction(local,P.ref(),R),new IrCheckInitializedInstruction(local,R),
                new IrAddressOfLocalInstruction(address,local,R),new IrStorePointerInstruction(new IrParameterAddress(P.name()),ONE,R),
                new IrLoadPointerInstruction(X,address,R),ret(X))));
        assertEquals(Set.of("address","x"),live.temporaryTypes().keySet());
        assertEquals(Set.of(),live.liveBefore("entry",0));
        assertEquals(Set.of("address"),live.liveAfter("entry",3));
        assertEquals(Set.of("address"),live.liveBefore("entry",4));
    }

    @Test void unreachableBlocksAndDeadTailsHaveExplicitNonExecutableFactsButRetainEveryTemporaryType() {
        var floating=new IrTemporary("tail",IrType.DOUBLE);
        var live=IrLiveness.analyze(fn(block("entry",move(X,ONE),ret(X),move(floating,new IrFloatConstant(2.0,IrType.DOUBLE)),
                        new IrCallInstruction(Y,"external",List.of(floating),false,R)),
                block("dead",move(A,TWO),ret(A)),block("empty")));
        assertEquals(Set.of("entry"),live.controlFlow().reachable());
        assertEquals(4,live.instructions("entry").size());
        assertFalse(live.instruction("entry",2).executable());
        assertFalse(live.instruction("entry",3).executable());
        assertFalse(live.instruction("dead",0).executable());
        assertEquals(Set.of(),live.liveBefore("entry",3));assertEquals(Set.of(),live.liveAfter("dead",0));
        assertEquals(Set.of(),live.liveIn("dead"));assertEquals(Set.of(),live.liveOut("dead"));
        assertEquals(Set.of(),live.liveAcrossCalls());
        assertEquals(List.of(),live.instructions("empty"));
        assertEquals(Map.of("x",IrType.INT,"tail",IrType.DOUBLE,"y",IrType.INT,"a",IrType.INT),live.temporaryTypes());
    }

    @Test void allReturnedCollectionsAreImmutableAndQueriesUseOriginalInstructionIndices() {
        var live=IrLiveness.analyze(fn(block("entry",move(X,ONE),ret(X))));
        assertThrows(UnsupportedOperationException.class,()->live.temporaryTypes().put("extra",IrType.INT));
        assertThrows(UnsupportedOperationException.class,()->live.liveBefore("entry",1).add("extra"));
        assertThrows(UnsupportedOperationException.class,()->live.liveAfter("entry",0).clear());
        assertThrows(UnsupportedOperationException.class,()->live.instructions("entry").clear());
        assertThrows(UnsupportedOperationException.class,()->live.liveIn("entry").add("extra"));
        assertThrows(UnsupportedOperationException.class,()->live.liveOut("entry").add("extra"));
        assertThrows(UnsupportedOperationException.class,()->live.liveAcrossCalls().add("extra"));
        assertThrows(IllegalArgumentException.class,()->live.instructions("missing"));
        assertThrows(IndexOutOfBoundsException.class,()->live.instruction("entry",2));
        assertThrows(IndexOutOfBoundsException.class,()->live.instruction("entry",-1));
    }

    @Test void rejectsConflictingTemporaryTypesEvenInUnreachableCodeAndMissingCfgTargets() {
        var conflicting=new IrTemporary("x",IrType.DOUBLE);
        assertThrows(IllegalArgumentException.class,()->IrLiveness.analyze(fn(block("entry",move(X,ONE),ret(X)),
                block("dead",move(conflicting,new IrFloatConstant(1.0,IrType.DOUBLE)),ret(ONE)))));
        assertThrows(IllegalArgumentException.class,()->IrLiveness.analyze(fn(block("entry",jump("missing")))));
        assertThrows(NullPointerException.class,()->IrLiveness.analyze(null));
    }

    @Test void realLoopSwitchAndCallIrSatisfiesAllBlockAndInstructionEquations() {
        var source=new SourceFile("liveness.c","""
                int add(int x){return x+1;}
                int main(){int sum=0;for(int i=0;i<8;i++){if(i==2)continue;
                switch(i){case 4:sum+=add(i);break;default:sum+=i;}if(i==6)break;}return sum;}
                """);
        var ir=new CompilerApi(source).runToIr();
        for(var function:ir.functions()) {
            var original=List.copyOf(function.blocks());
            var live=IrLiveness.analyze(function);
            assertEquations(function,live);
            assertEquals(original,function.blocks());
        }
    }

    @Test void memoryAddressesIndicesSelectionsAndChecksKeepTheirTemporaryInputsLive() {
        var base=new IrTemporary("base",IrType.POINTER);
        var address=new IrTemporary("address",IrType.POINTER);
        var field=new IrTemporary("field",IrType.POINTER);
        var local=new IrLocal("local","local",MiniType.INT,IrType.INT,4,4,R);
        var live=IrLiveness.analyze(fn(block("entry",move(base,new IrParameterAddress(P.name())),move(X,ONE),
                new IrElementAddressInstruction(address,base,X,MiniType.INT,4,R),
                new IrFieldAddressInstruction(field,base,"Record","value",0,MiniType.INT,R),
                new IrMemCopyInstruction(address,field,4,R),new IrLoadPointerInstruction(A,address,true,R),
                new IrCheckNonZeroInstruction(A,R),new IrStorePointerInstruction(field,A,true,R),
                new IrStoreLocalInstruction(local,A,true,R),new IrLoadLocalInstruction(B,local,true,R),
                new IrSelectInstruction(Y,X,A,B,R),ret(Y))));
        assertEquals(Set.of("base","x"),live.liveBefore("entry",2));
        assertEquals(Set.of("address","field","x"),live.liveBefore("entry",4));
        assertEquals(Set.of("a","field","x"),live.liveBefore("entry",6));
        assertEquals(Set.of("x","a","b"),live.liveBefore("entry",10));
        assertEquals(Set.of("y"),live.liveAfter("entry",10));
        assertTrue(live.liveAcrossCalls().isEmpty());
    }

    @Test void deadSelfNamedCallResultStillKillsOldValueAndMustNotBecomeAcrossCall() {
        var live=IrLiveness.analyze(fn(block("entry",move(X,ONE),move(A,TWO),
                new IrCallInstruction(X,"callee",List.of(X),false,R),ret(A))));
        assertEquals(Set.of("a","x"),live.liveBefore("entry",2));
        assertEquals(Set.of("a"),live.liveAfter("entry",2));
        assertEquals(Set.of("a"),live.liveAcrossCall("entry",2));
        assertEquals(Set.of("a","x"),live.temporaryTypes().keySet());
    }

    @Test void aResultAndAnOperandMustAgreeOnTypeEvenIfTheOnlyUseIsDead() {
        var wrong=new IrTemporary("x",IrType.DOUBLE);
        assertThrows(IllegalArgumentException.class,()->IrLiveness.analyze(fn(block("entry",move(X,ONE),ret(X),
                new IrCastInstruction(Y,wrong,R)))));
    }

    @Test void unreachablePredecessorsDoNotPolluteAReachableMerge() {
        var live=IrLiveness.analyze(fn(block("entry",move(X,ONE),jump("join")),
                block("dead",move(Y,TWO),jump("join")),block("join",ret(X))));
        assertEquals(Set.of("x"),live.liveOut("entry"));
        assertEquals(Set.of(),live.liveOut("dead"));
        assertEquals(Set.of("x"),live.liveIn("join"));
        assertFalse(live.instruction("dead",1).executable());
    }

    private static void assertEquations(IrFunction function,IrLiveness analysis) {
        var flow=analysis.controlFlow();
        for(var block:function.blocks()) {
            if(!flow.reachable().contains(block.label()))continue;
            var expectedOut=new HashSet<String>();
            for(String successor:flow.successors(block.label()))expectedOut.addAll(analysis.liveIn(successor));
            assertEquals(expectedOut,analysis.liveOut(block.label()));
            Set<String> next=expectedOut;
            var instructions=flow.effectiveInstructions(block.label());
            for(int index=instructions.size()-1;index>=0;index--) {
                var instruction=instructions.get(index);
                assertTrue(analysis.instruction(block.label(),index).executable());
                assertEquals(next,analysis.liveAfter(block.label(),index));
                var before=new HashSet<>(next);
                IrTemporary result=IrValueUses.result(instruction);
                if(result!=null)before.remove(result.name());
                for(IrValue value:IrValueUses.inputs(instruction))if(value instanceof IrTemporary temp)before.add(temp.name());
                assertEquals(before,analysis.liveBefore(block.label(),index));
                next=before;
            }
            assertEquals(next,analysis.liveIn(block.label()));
        }
    }
    private static IrTemporary temp(String name){return new IrTemporary(name,IrType.INT);}
    private static IrFunction fn(IrBlock... blocks){return new IrFunction("function",MiniType.INT,List.of(P),false,List.of(blocks),R);}
    private static IrBlock block(String name,IrInstruction... instructions){return new IrBlock(name,List.of(instructions));}
    private static IrMoveInstruction move(IrTemporary result,IrValue value){return new IrMoveInstruction(result,value,R);}
    private static IrBinaryInstruction add(IrTemporary result,IrValue left,IrValue right){return new IrBinaryInstruction(result,IrBinaryOperator.ADD,left,right,R);}
    private static IrReturnInstruction ret(IrValue value){return new IrReturnInstruction(value,R);}
    private static IrJumpInstruction jump(String name){return new IrJumpInstruction(name,R);}
    private static IrBranchInstruction branch(String yes,String no){return new IrBranchInstruction(P.ref(),yes,no,R);}
}
