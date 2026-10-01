package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class InitializationFlagStorageTest {
    private static final SourceRange R=new SourceRange(1,1,1,20);
    private static IrLocal local(String name){return new IrLocal(name,"same",MiniType.INT,IrType.INT,4,4,R);}
    private static IrFunction function(IrInstruction... instructions){
        return new IrFunction("function",MiniType.INT,List.of(),false,List.of(new IrBlock("entry",List.of(instructions))),R);
    }

    @Test void uncheckedLocalsKeepObjectStorageButNeedNoFlag(){
        var local=local("one");
        var function=function(new IrDeclareLocalInstruction(local,R),new IrStoreLocalInstruction(local,new IrConstant(7),R),
                new IrReturnInstruction(new IrConstant(0),R));
        var baseline=FrameLayout.create(function);
        var optimized=FrameLayout.create(function,true);
        assertTrue(baseline.localInitializedOffsets().containsKey("one"));
        assertEquals(Set.of("one"),optimized.localOffsets().keySet());
        assertTrue(optimized.localInitializedOffsets().isEmpty());
        assertTrue(optimized.frameSize()<=baseline.frameSize());
    }

    @Test void onlyFlagsWhichFeedChecksAreRetainedByUniqueLocalIdentity(){
        var checked=local("checked");var unchecked=local("unchecked");
        var function=function(new IrDeclareLocalInstruction(checked,R),new IrDeclareLocalInstruction(unchecked,R),
                new IrStoreLocalInstruction(unchecked,new IrConstant(7),R),new IrCheckInitializedInstruction(checked,R),
                new IrReturnInstruction(new IrConstant(0),R));
        var frame=FrameLayout.create(function,true);
        assertEquals(Set.of("checked"),frame.localInitializedOffsets().keySet());
        assertEquals(2,frame.localOffsets().size());
        assertNotEquals(frame.localOffsets().get("checked"),frame.localInitializedOffsets().get("checked"));
    }

    @Test void AddressedLocalChecksAlreadyOmittedByNativeDoNotKeepDeadFlags(){
        var local=local("escaped");var pointer=new IrTemporary("address",IrType.POINTER);
        var function=function(new IrDeclareLocalInstruction(local,R),new IrAddressOfLocalInstruction(pointer,local,R),
                new IrCheckInitializedInstruction(local,R),new IrReturnInstruction(new IrConstant(0),R));
        assertTrue(FrameLayout.create(function,true).localInitializedOffsets().isEmpty());
        assertTrue(FrameLayout.create(function).localInitializedOffsets().containsKey("escaped"));
    }

    @Test void EmissionRemovesOnlyPrivateFlagWrites(){
        var local=local("one");
        var declaration=new IrDeclareLocalInstruction(local,R);
        var store=new IrStoreLocalInstruction(local,new IrConstant(7),R);
        var function=function(declaration,store,new IrReturnInstruction(new IrConstant(0),R));
        var optimized=FrameLayout.create(function,true);
        var emitter=new InstructionEmitter(optimized,Set.of(),function);
        var declarationText=new StringBuilder();emitter.emitInstruction(declarationText,"function","done",declaration);
        assertEquals("",declarationText.toString());
        var storeText=new StringBuilder();emitter.emitInstruction(storeText,"function","done",store);
        assertEquals(1,storeText.toString().lines().filter(line->line.startsWith("    mov DWORD PTR ")).count());
        assertTrue(storeText.toString().contains(optimized.localSlot(local)),storeText::toString);
    }

    @Test void remainingChecksStillResetSetAndReadTheirFlag(){
        var local=local("one");
        var declaration=new IrDeclareLocalInstruction(local,R);
        var store=new IrStoreLocalInstruction(local,new IrConstant(7),R);
        var check=new IrCheckInitializedInstruction(local,R);
        var function=function(declaration,store,check,new IrReturnInstruction(new IrConstant(0),R));
        var frame=FrameLayout.create(function,true);
        var emitter=new InstructionEmitter(frame,Set.of(),function);
        var text=new StringBuilder();
        for(var instruction:List.of(declaration,store,check))emitter.emitInstruction(text,"function","done",instruction);
        assertTrue(text.toString().contains("mov "+frame.localInitializedSlot(local)+", 0"));
        assertTrue(text.toString().contains("mov "+frame.localInitializedSlot(local)+", 1"));
        assertTrue(text.toString().contains("cmp "+frame.localInitializedSlot(local)+", 0"));
        assertTrue(text.toString().contains("je function$trap_uninitialized"));
    }
}
