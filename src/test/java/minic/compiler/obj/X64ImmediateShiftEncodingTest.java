package minic.compiler.obj;

import minic.compiler.obj.machine.*;
import minic.compiler.obj.x64.X64Encoder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class X64ImmediateShiftEncodingTest {
    @Test void encodesAllThreeImmediateShiftGroupsAt32And64BitsIncludingExtendedRegisters() {
        assertArrayEquals(bytes(0x48,0xC1,0xE8,1, 0xC1,0xF8,31, 0x49,0xC1,0xE4,63,
                        0x41,0xC1,0xE9,0, 0x49,0xC1,0xFA,63, 0x41,0xC1,0xE5,255),
                encode(shift("shr","rax",1),shift("sar","eax",31),shift("shl","r12",63),
                        shift("shr","r9d",0),shift("sar","r10",63),shift("shl","r13d",255)));
    }

    @Test void immediateGroupsRetainNarrowMemoryPrefixesAndRexBits() {
        assertArrayEquals(bytes(0xC0,0x65,0xFF,7, 0x66,0xC1,0xE8,15, 0x41,0xC0,0xF8,7),
                encode(new MachineInstruction("shl",MemoryOperand.base(8,"rbp",-1),new ImmediateOperand(7)),
                        shift("shr","ax",15),shift("sar","r8b",7)));
    }

    @Test void rejectsNonByteImmediateAndNonClVariableCounts() {
        for(long count:new long[]{-1,256,Long.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->encode(shift("shr","rax",count)));
        assertThrows(IllegalArgumentException.class,()->encode(new MachineInstruction("shr",new RegisterOperand("rax"),new RegisterOperand("dl"))));
    }

    @Test void existingVariableCountEncodingIsUnchanged() {
        assertArrayEquals(bytes(0x48,0xD3,0xE8, 0xD3,0xF8, 0x66,0xD3,0xE0, 0x41,0xD2,0xE8),
                encode(new MachineInstruction("shr",new RegisterOperand("rax"),new RegisterOperand("cl")),
                        new MachineInstruction("sar",new RegisterOperand("eax"),new RegisterOperand("cl")),
                        new MachineInstruction("shl",new RegisterOperand("ax"),new RegisterOperand("cl")),
                        new MachineInstruction("shr",new RegisterOperand("r8b"),new RegisterOperand("cl"))));
    }

    @Test void immediateIntegerShiftsRejectVectorAndUnsupportedMemoryWidths() {
        assertThrows(IllegalArgumentException.class,()->encode(shift("shr","xmm0",1)));
        assertThrows(IllegalArgumentException.class,()->encode(new MachineInstruction("sar",MemoryOperand.base(128,"rbp",-16),new ImmediateOperand(1))));
    }

    private static MachineInstruction shift(String mnemonic,String register,long count){return new MachineInstruction(mnemonic,new RegisterOperand(register),new ImmediateOperand(count));}
    private static byte[] encode(MachineInstruction... instructions){return new X64Encoder().encode(new MachineSection(".text",MachineSectionKind.CODE,16,List.of(instructions))).bytes();}
    private static byte[] bytes(int... values){byte[] result=new byte[values.length];for(int i=0;i<values.length;i++)result[i]=(byte)values[i];return result;}
}
