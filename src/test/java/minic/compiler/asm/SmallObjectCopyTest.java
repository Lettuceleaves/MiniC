package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrMemCopyInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

final class SmallObjectCopyTest {
    private static final SourceRange R=new SourceRange(1,0,1,12);
    private static final IrTemporary TO=new IrTemporary("to",IrType.POINTER), FROM=new IrTemporary("from",IrType.POINTER);

    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16})
    void copiesEveryByteExactlyOnceAndReadsTheEntireSourceBeforeWriting(int size) {
        String text=emit(size,false,true,true);
        assertFalse(text.contains("rep movsb"),text);
        assertFalse(text.contains("rsi")||text.contains("rdi")||text.contains("push ")||text.contains("pop "),text);
        assertEquals(1,text.lines().filter(line->line.equals("    mov rax, r10")).count(),text);
        assertEquals(1,text.lines().filter(line->line.equals("    mov rcx, r11")).count(),text);
        var reads=Pattern.compile("(?m)^    (?:mov|movsd) [a-z0-9]+, (BYTE|WORD|DWORD|QWORD) PTR \\[rcx(?:\\+(\\d+))?\\]$").matcher(text);
        var writes=Pattern.compile("(?m)^    (?:mov|movsd) (BYTE|WORD|DWORD|QWORD) PTR \\[rax(?:\\+(\\d+))?\\], [a-z0-9]+$").matcher(text);
        int[] source=new int[size],destination=new int[size];int lastRead=-1,firstWrite=Integer.MAX_VALUE;
        while(reads.find()){cover(source,reads.group(1),reads.group(2));lastRead=reads.end();}
        while(writes.find()){cover(destination,writes.group(1),writes.group(2));firstWrite=Math.min(firstWrite,writes.start());}
        assertTrue(lastRead>=0&&lastRead<firstWrite,text);
        for(int index=0;index<size;index++){assertEquals(1,source[index],text);assertEquals(1,destination[index],text);}
        assertFalse(text.matches("(?s).*(?:mov|movsd) (?:r10|r11|rbx|r12|r13|r14|r15|xmm[6-9]|xmm1[0-5])(?:[dwb])?,.*"),text);
    }

    @ParameterizedTest @ValueSource(ints={1,8,15,16,17,32,128})
    void baselineVolatileAndLargeCopiesKeepTheExactRepPath(int size) {
        String baseline=emit(size,false,false,false);
        assertTrue(baseline.contains("rep movsb")&&baseline.contains("mov ecx, "+size),baseline);
        assertTrue(baseline.contains("push rsi")&&baseline.contains("push rdi")&&baseline.contains("pop rdi")&&baseline.contains("pop rsi"),baseline);
        assertEquals(baseline,emit(size,true,true,false));
        if(size>16)assertEquals(baseline,emit(size,false,true,false));
    }

    @Test void zeroSizeRetainsTheExistingIrValidationFailure() {
        assertThrows(IllegalArgumentException.class,()->new IrMemCopyInstruction(TO,FROM,0,R));
    }

    private static void cover(int[] coverage,String width,String displacement) {
        int start=displacement==null?0:Integer.parseInt(displacement);
        int length=switch(width){case "BYTE"->1;case "WORD"->2;case "DWORD"->4;default->8;};
        assertTrue(start>=0&&start+length<=coverage.length,"copy accessed bytes outside its exact size");
        for(int index=start;index<start+length;index++)coverage[index]++;
    }
    private static String emit(int size,boolean vol,boolean optimized,boolean registers) {
        var pointer=MiniType.INT.pointerTo();
        var to=new IrParameter("destination",pointer,IrType.POINTER,R);var from=new IrParameter("source",pointer,IrType.POINTER,R);
        var copy=new IrMemCopyInstruction(TO,FROM,size,vol,R);
        var function=new IrFunction("copy",MiniType.VOID,List.of(to,from),false,List.of(new IrBlock("entry",List.of(
                new IrMoveInstruction(TO,to.ref(),R),new IrMoveInstruction(FROM,from.ref(),R),copy))),R);
        var frame=FrameLayout.create(function);
        var locations=registers?TemporaryLocations.withOverrides(frame,Map.of("to",new ValueLocation.Register(IrType.POINTER,"r10"),
                "from",new ValueLocation.Register(IrType.POINTER,"r11"))):TemporaryLocations.allStack(frame);
        var builder=new StringBuilder();new InstructionEmitter(frame,Set.of(),function,locations,optimized).emitInstruction(builder,"copy","epilogue",copy);
        return builder.toString();
    }
}
