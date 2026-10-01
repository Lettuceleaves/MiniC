package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.ir.optimize.*;
import minic.compiler.semantic.model.StructLayout;
import minic.compiler.semantic.model.StructLayout.StructFieldLayout;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class FieldLoadFusionTest {
    private static final SourceRange R=new SourceRange(1,0,1,8);
    @ParameterizedTest @EnumSource(IrType.class)
    void exactTypedLoadsUseFieldDisplacementsWithoutMaterializingTheirAddresses(IrType type) {
        for(int offset:List.of(0,128,4096)) {
            var ir=program(type,offset,"none");String optimized=emit(ir,OptimizationLevel.OPTIMIZED);
            String baseline=emit(ir,OptimizationLevel.BASELINE);
            String address=offset==0?"[rax]":"[rax+"+offset+"]";
            assertTrue(optimized.contains(load(type,address)),type+" offset="+offset+"\n"+optimized);
            assertFalse(optimized.contains("lea rax, "+address),optimized);
            assertTrue(optimized.lines().count()<baseline.lines().count(),"even offset zero must remove the address-home round trip\n"+optimized);
        }
    }
    @Test void byteLoadsCoverDisp8BoundaryAndLargeSignedDisp32() {
        for(int offset:List.of(127,128,2147483646)) {
            String text=emit(program(IrType.CHAR,offset,"none"),OptimizationLevel.OPTIMIZED);
            assertTrue(text.contains("movsx eax, BYTE PTR [rax+"+offset+"]"),text);
        }
    }
    @Test void baselineAndUnsafeGroupsKeepTheirOriginalAddressInstructions() {
        for(String barrier:List.of("volatile","multi-use","redefine","call","store","check","cast","cross-block","dead-tail")) {
            String text=emit(program(IrType.INT,128,barrier),OptimizationLevel.OPTIMIZED);
            assertTrue(text.contains("lea rax, [rax+128]"),barrier+"\n"+text);
            assertFalse(text.contains("mov eax, DWORD PTR [rax+128]"),barrier+"\n"+text);
        }
        assertTrue(emit(program(IrType.INT,128,"none"),OptimizationLevel.BASELINE).contains("lea rax, [rax+128]"));
    }
    @Test void negativeOffsetsAreRejectedByTheIrAddressConstructor() {
        for(int offset:List.of(-1,Integer.MIN_VALUE))
            assertEquals("offset must not be negative",assertThrows(IllegalArgumentException.class,()->program(IrType.INT,offset,"none")).getMessage());
    }
    @Test void addressCostDiscountDoesNotRemoveItsLiveInterval() {
        var a=new IrTemporary("a",IrType.INT);var b=new IrTemporary("b",IrType.INT);
        var address=new IrTemporary("address",IrType.POINTER);var value=new IrTemporary("value",IrType.FLOAT);
        var sum=new IrTemporary("sum",IrType.INT);
        var base=new IrParameter("base",MiniType.struct("Record").pointerTo(),IrType.POINTER,R);
        var function=new IrFunction("read",MiniType.INT,List.of(base),false,List.of(
                new IrBlock("entry",List.of(new IrMoveInstruction(a,new IrConstant(1),R),new IrMoveInstruction(b,new IrConstant(2),R),new IrJumpInstruction("loop",R))),
                new IrBlock("loop",List.of(new IrFieldAddressInstruction(address,base.ref(),"Record","f",0,MiniType.FLOAT,R),
                        new IrLoadPointerInstruction(value,address,R),new IrBranchInstruction(a,"loop","done",R))),
                new IrBlock("done",List.of(new IrBinaryInstruction(sum,IrBinaryOperator.ADD,a,b,R),new IrReturnInstruction(sum,R)))),R);
        var allocation=GlobalRegisterPlan.allocate(function,true);
        assertTrue(allocation.calleeSavedRegisters().isEmpty(),allocation.registers().toString());
        assertTrue(allocation.stackTemporaries().contains("address"));
        assertTrue(IrLiveness.analyze(function).instruction("loop",1).liveBefore().contains("address"));
    }
    private static String emit(IrResult ir,OptimizationLevel level) {
        var asm=new Assembler(ir,new IrOptimizationPipeline(level,List.of()));String text=asm.assemble().text();
        assertTrue(asm.succeeded(),()->asm.errors().toString());assertSame(ir,asm.input().irResult());
        return text.substring(text.indexOf("minic$load PROC"),text.indexOf("minic$load ENDP"));
    }
    private static IrResult program(IrType type,int offset,String barrier) {
        MiniType fieldType=sourceType(type);var base=new IrParameter("base",MiniType.struct("Record").pointerTo(),IrType.POINTER,R);
        var address=new IrTemporary("address",IrType.POINTER);var value=new IrTemporary("value",type);
        var field=new IrFieldAddressInstruction(address,base.ref(),"Record","value",offset,fieldType,R);
        var code=new ArrayList<IrInstruction>();
        if(barrier.equals("redefine"))code.add(new IrMoveInstruction(address,base.ref(),R));
        code.add(field);
        if(barrier.equals("call"))code.add(new IrCallInstruction(null,"effect",List.of(),false,R));
        if(barrier.equals("store"))code.add(new IrStorePointerInstruction(base.ref(),new IrConstant(3),R));
        if(barrier.equals("check"))code.add(new IrCheckNonZeroInstruction(new IrConstant(1),R));
        var loadAddress=address;
        if(barrier.equals("cast")){loadAddress=new IrTemporary("converted",IrType.POINTER);code.add(new IrCastInstruction(loadAddress,address,R));}
        var blocks=new ArrayList<IrBlock>();
        if(barrier.equals("cross-block")){code.add(new IrJumpInstruction("body",R));blocks.add(new IrBlock("entry",code));code=new ArrayList<>();}
        code.add(new IrLoadPointerInstruction(value,loadAddress,barrier.equals("volatile"),R));
        if(barrier.equals("multi-use"))code.add(new IrMoveInstruction(new IrTemporary("extra",IrType.POINTER),address,R));
        code.add(new IrReturnInstruction(value,R));if(barrier.equals("dead-tail"))code.add(field);
        blocks.add(new IrBlock(barrier.equals("cross-block")?"body":"entry",code));
        var function=new IrFunction("load",fieldType,List.of(base),false,blocks,R);
        var main=new IrFunction("main",MiniType.INT,List.of(),false,List.of(new IrBlock("entry",List.of(new IrReturnInstruction(new IrConstant(0),R)))),R);
        int actualOffset=Math.max(0,offset);var layout=new StructLayout("Record",actualOffset+type.sizeBytes(),1,
                List.of(new StructFieldLayout("value",fieldType,actualOffset,type.sizeBytes(),1)));
        return new IrResult(List.of(main,function),List.of(),Set.of("effect"),Map.of("Record",layout));
    }
    private static String load(IrType type,String address){
        String prefix=type.sizeBytes()==1?"BYTE":type.sizeBytes()==2?"WORD":type.sizeBytes()==4?"DWORD":"QWORD";
        String operation=type==IrType.FLOAT?"movss xmm0":type==IrType.DOUBLE?"movsd xmm0":type.sizeBytes()<4?(type.isSignedInteger()?"movsx eax":"movzx eax"):type.sizeBytes()==4?"mov eax":"mov rax";
        return operation+", "+prefix+" PTR "+address;
    }
    private static MiniType sourceType(IrType type){return switch(type){case BOOL->MiniType.BOOL;case CHAR->MiniType.CHAR;case SIGNED_CHAR->MiniType.SIGNED_CHAR;
        case UNSIGNED_CHAR->MiniType.UNSIGNED_CHAR;case SHORT->MiniType.SHORT;case UNSIGNED_SHORT->MiniType.UNSIGNED_SHORT;case INT->MiniType.INT;case UNSIGNED_INT->MiniType.UNSIGNED_INT;
        case LONG->MiniType.LONG;case UNSIGNED_LONG->MiniType.UNSIGNED_LONG;case LONG_LONG->MiniType.LONG_LONG;case UNSIGNED_LONG_LONG->MiniType.UNSIGNED_LONG_LONG;
        case FLOAT->MiniType.FLOAT;case DOUBLE->MiniType.DOUBLE;case POINTER->MiniType.INT.pointerTo();};}
}
