package minic.compiler.obj;

import minic.compiler.obj.machine.*;
import minic.compiler.obj.x64.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class X64ImmediateShiftRelocationTest {
    @Test void localRipRelativeTargetIsRelativeToTheEndOfTheImmediate() {
        var code=new MachineSection(".text",MachineSectionKind.CODE,16,List.of(
                new MachineInstruction("shr",MemoryOperand.ripRelative(32,"value"),new ImmediateOperand(1)),new MachineLabel("value",false)));
        var encoded=new X64Encoder().encode(code);
        assertArrayEquals(new byte[]{(byte)0xc1,0x2d,0,0,0,0,1},encoded.bytes());
        assertTrue(encoded.relocations().isEmpty());
    }
    @Test void unresolvedRipRelativeSymbolCarriesTheTrailingByteInItsCoffAddend() {
        var code=new MachineSection(".text",MachineSectionKind.CODE,16,List.of(
                new MachineInstruction("sar",new MemoryOperand(64,"value","rip",null,1,8),new ImmediateOperand(3))));
        var encoded=new X64Encoder().encode(code);
        assertArrayEquals(new byte[]{0x48,(byte)0xc1,0x3d,7,0,0,0,3},encoded.bytes());
        assertEquals(1,encoded.relocations().size());
        assertEquals(3,encoded.relocations().getFirst().offset());
        assertEquals(MachineRelocationKind.RIP_REL32,encoded.relocations().getFirst().kind());
    }
}
