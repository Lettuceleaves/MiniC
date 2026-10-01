package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.optimize.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class TemporaryFrameLayoutTest {
    private static final SourceRange R=new SourceRange(1,1,1,10);
    private static final IrConstant ONE=new IrConstant(1);

    @Test void optimizedLayoutReusesAChainAndActuallyReducesAlignedFrameSize() {
        IrFunction function=chain();
        var baseline=FrameLayout.create(function);
        var optimized=FrameLayout.create(function,true);
        assertEquals(12,new HashSet<>(baseline.temporaryOffsets().values()).size());
        assertEquals(1,new HashSet<>(optimized.temporaryOffsets().values()).size());
        assertTrue(optimized.frameSize()<baseline.frameSize());
        assertEquals(0,optimized.frameSize()%16);
        assertEquals(baseline,FrameLayout.create(function,false));
        assertNull(baseline.temporarySlotPlan());
        assertNotNull(optimized.temporarySlotPlan());
        assertEquals(1,optimized.temporarySlotPlan().slotCount());
    }

    @Test void temporaryBaseIsAlignedAndNeverAliasesParameterLocalOrInitializationFlag() {
        var parameter=new IrParameter("parameter",MiniType.CHAR,IrType.CHAR,R);
        var local=new IrLocal("local","local",MiniType.SHORT,IrType.SHORT,2,2,R);
        var address=new IrTemporary("address",IrType.POINTER);
        var wide=new IrTemporary("wide",IrType.LONG_LONG);
        var narrow=new IrTemporary("narrow",IrType.CHAR);
        var function=new IrFunction("function",MiniType.INT,List.of(parameter),false,List.of(new IrBlock("entry",List.of(
                new IrDeclareLocalInstruction(local,R),new IrAddressOfLocalInstruction(address,local,R),
                new IrMoveInstruction(wide,new IrConstant(3,IrType.LONG_LONG),R),
                new IrMoveInstruction(narrow,new IrConstant(2,IrType.CHAR),R),new IrReturnInstruction(ONE,R)))),R);
        var layout=FrameLayout.create(function,true);
        assertEquals(1,layout.parameterOffsets().get("parameter"));
        int fixedEnd=layout.localInitializedOffsets().get("local");
        assertEquals(0,layout.temporaryOffsets().get("address")%8);
        assertEquals(0,layout.temporaryOffsets().get("wide")%8);
        assertTrue(layout.temporaryOffsets().get("address")-8>=fixedEnd);
        assertTrue(layout.temporaryOffsets().get("narrow")-1>=fixedEnd);
        assertEquals(0,layout.localOffsets().get("local")%2);
    }

    @Test void stackArgumentAreaAndIncomingVarargsAddressRemainOutsideReusableStorage() {
        var parameters=new ArrayList<IrParameter>();
        for(int i=0;i<6;i++)parameters.add(new IrParameter("p"+i,MiniType.INT,IrType.INT,R));
        var address=new IrTemporary("address",IrType.POINTER);
        var function=new IrFunction("function",MiniType.INT,parameters,true,List.of(new IrBlock("entry",List.of(
                new IrAddressOfLocalInstruction(address,IrLocal.incomingArgumentArea(6,R),R),
                new IrCallInstruction(null,"external",parameters.stream().map(IrParameter::ref).map(minic.compiler.ir.value.IrValue.class::cast).toList(),false,R),
                new IrReturnInstruction(ONE,R)))),R);
        var layout=FrameLayout.create(function,true);
        assertEquals(48,layout.outgoingArgumentAreaSize());
        assertEquals("[rbp+64]",layout.localAddress(IrLocal.incomingArgumentArea(6,R)));
        assertTrue(layout.localOffsets().isEmpty());
        assertEquals(6,new HashSet<>(layout.parameterOffsets().values()).size());
        assertTrue(layout.frameSize()>=layout.outgoingArgumentAreaSize()+layout.temporaryOffsets().get("address"));
    }

    @Test void optInAssemblerUsesSmallerPhysicalFrameWithoutAnInventedIrPassName() {
        var ir=new IrResult(List.of(chain()),List.of(),Set.of());
        var baseline=new Assembler(ir);
        var optimized=new Assembler(ir,new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of()));
        String before=baseline.assemble().text(),after=optimized.assemble().text();
        assertTrue(baseline.succeeded(),()->baseline.errors().toString());
        assertTrue(optimized.succeeded(),()->optimized.errors().toString());
        assertTrue(after.contains("    sub rsp, "+FrameLayout.create(chain(),true).frameSize()));
        assertNotEquals(before,after);
        assertSame(ir,optimized.input().irResult());
        assertTrue(optimized.optimizationResult().passNames().isEmpty(),"frame layout is a backend optimization, not an IR pass");
    }

    @Test void realCompilerTemporariesRetainStorageEvenWhenTheyAreInDeadTails() {
        var ir=new CompilerApi(new SourceFile("slots.c","int main(){int value=3;return value+1;value=value*2;}" )).runToIr();
        for(var function:ir.functions()) {
            var layout=FrameLayout.create(function,true);
            assertTrue(layout.temporaryOffsets().keySet().containsAll(IrLiveness.analyze(function).temporaryTypes().keySet()));
        }
    }

    @Test void alignmentPaddingFallsBackToBaselineWhenItWouldIncreaseTheFrame() {
        var parameters=new ArrayList<IrParameter>();
        for(int i=0;i<10;i++)parameters.add(new IrParameter("p"+i,MiniType.CHAR,IrType.CHAR,R));
        var local=new IrLocal("local","local",MiniType.CHAR,IrType.CHAR,1,1,R);
        var temporary=new IrTemporary("temporary",IrType.CHAR);
        var function=new IrFunction("function",MiniType.INT,parameters,false,List.of(new IrBlock("entry",List.of(
                new IrDeclareLocalInstruction(local,R),new IrMoveInstruction(temporary,new IrConstant(1,IrType.CHAR),R),
                new IrReturnInstruction(ONE,R)))),R);
        var baseline=FrameLayout.create(function);
        var optimized=FrameLayout.create(function,true);
        assertEquals(48,baseline.frameSize());
        assertEquals(baseline,optimized);
        assertNull(optimized.temporarySlotPlan(),"metadata must report the actual fallback layout");
    }

    private static IrFunction chain(){
        var instructions=new ArrayList<IrInstruction>();
        var previous=new IrTemporary("t0",IrType.INT);instructions.add(new IrMoveInstruction(previous,ONE,R));
        for(int i=1;i<12;i++){var next=new IrTemporary("t"+i,IrType.INT);instructions.add(new IrBinaryInstruction(next,IrBinaryOperator.ADD,previous,ONE,R));previous=next;}
        instructions.add(new IrReturnInstruction(previous,R));
        return new IrFunction("main",MiniType.INT,List.of(),false,List.of(new IrBlock("entry",instructions)),R);
    }
}
