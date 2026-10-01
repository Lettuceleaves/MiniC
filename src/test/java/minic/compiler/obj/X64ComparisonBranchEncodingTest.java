package minic.compiler.obj;

import minic.compiler.obj.machine.*;
import minic.compiler.obj.x64.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class X64ComparisonBranchEncodingTest {
    @ParameterizedTest @CsvSource({"jb,130","jae,131","je,132","jne,133","jbe,134","ja,135","jp,138","jl,140","jge,141","jle,142","jg,143"})
    void encodesSignedUnsignedAndParityConditionsWithTheExistingRel32Contract(String mnemonic,int opcode) {
        var instruction=new MachineInstruction(mnemonic,new SymbolOperand("target"));
        var local=new X64Encoder().encode(new MachineSection(".text",MachineSectionKind.CODE,16,List.of(instruction,new MachineLabel("target",false))));
        assertArrayEquals(new byte[]{0x0f,(byte)opcode,0,0,0,0},local.bytes());assertTrue(local.relocations().isEmpty());
        var external=new X64Encoder().encode(new MachineSection(".text",MachineSectionKind.CODE,16,List.of(instruction)));
        assertArrayEquals(local.bytes(),external.bytes());
        assertEquals(List.of(new MachineRelocation(2,"target",MachineRelocationKind.REL32,0)),external.relocations());
    }
}
