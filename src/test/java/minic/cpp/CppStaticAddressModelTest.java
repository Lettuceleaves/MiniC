package minic.cpp;

import minic.compiler.asm.AsmResult;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrGlobalData;
import minic.compiler.ir.optimize.IrVerifier;
import minic.compiler.link.pe.WindowsPeLinker;
import minic.compiler.obj.assembler.WindowsX64MachineAssembler;
import minic.compiler.obj.coff.*;
import minic.compiler.obj.machine.MachineData;
import minic.compiler.obj.x64.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import java.nio.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class CppStaticAddressModelTest {
    private static final String ASSEMBLY="""
        .data
        ALIGN 32
        target BYTE 7, 0, 0, 0
        ALIGN 8
        pointer QWORD OFFSET target + 4
            QWORD OFFSET entry
        .code
        entry PROC
            mov eax, 0
            ret
        entry ENDP
        END
        """;
    @Test void symbolicDataSurvivesMachineEncodingAndCoff(){
        var module=new WindowsX64MachineAssembler().assemble(new AsmResult("entry",ASSEMBLY));
        var section=module.sections().stream().filter(s->s.name().equals(".data")).findFirst().orElseThrow();
        assertEquals(32,section.alignment());
        var encoded=new X64Encoder().encode(section);
        assertEquals(8,encoded.symbols().get("pointer"));
        assertEquals(List.of(new MachineRelocation(8,"target",MachineRelocationKind.ADDR64,4),new MachineRelocation(16,"entry",MachineRelocationKind.ADDR64,0)),encoded.relocations());
        assertEquals(4,ByteBuffer.wrap(encoded.bytes()).order(ByteOrder.LITTLE_ENDIAN).getLong(8));
        var coff=new CoffObjectReader().read(new CoffObjectWriter().write(module));
        var data=coff.sections().stream().filter(s->s.name().equals(".data")).findFirst().orElseThrow();
        assertEquals(2,data.relocations().size());
        assertTrue(data.relocations().stream().allMatch(r->r.type()==1));
    }
    @Test void linkerResolvesDataAndFunctionAddressesBeforeExecution(){
        var module=new WindowsX64MachineAssembler().assemble(new AsmResult("entry",ASSEMBLY));
        byte[] bytes=new WindowsPeLinker().link(new CoffObjectWriter().write(module),"entry").bytes();
        var image=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int pe=image.getInt(0x3c);int optional=pe+24;long base=image.getLong(optional+24);
        int first=optional+Short.toUnsignedInt(image.getShort(pe+20));
        int textRva=image.getInt(first+12),dataRva=image.getInt(first+40+12),dataRaw=image.getInt(first+40+20);
        assertEquals(base+dataRva+4,image.getLong(dataRaw+8));
        assertEquals(base+textRva,image.getLong(dataRaw+16));
    }
    @Test void negativeAddendsRemainSigned(){
        String source=ASSEMBLY.replace("target + 4","target - 4");
        var module=new WindowsX64MachineAssembler().assemble(new AsmResult("entry",source));
        var encoded=new X64Encoder().encode(module).sections().stream().filter(s->s.name().equals(".data")).findFirst().orElseThrow();
        assertEquals(-4,ByteBuffer.wrap(encoded.bytes()).order(ByteOrder.LITTLE_ENDIAN).getLong(8));
        assertEquals(-4,encoded.relocations().getFirst().addend());
    }
    @Test void relocationStorageIsCheckedAndImmutable(){
        byte[] bytes=new byte[16];var address=new IrGlobalData.Address(8,"a",0,IrGlobalData.AddressKind.OBJECT);
        var data=new IrGlobalData("p",MiniType.INT.pointerTo().arrayOf(2),bytes,8,List.of(address));
        bytes[0]=9;assertEquals(0,data.bytes()[0]);assertThrows(UnsupportedOperationException.class,()->data.addresses().clear());
        assertThrows(IllegalArgumentException.class,()->new IrGlobalData("p",MiniType.INT.pointerTo(),new byte[8],8,List.of(address)));
        assertThrows(IllegalArgumentException.class,()->new IrGlobalData("p",MiniType.INT.pointerTo().arrayOf(2),new byte[16],8,List.of(address,address)));
        assertThrows(IllegalArgumentException.class,()->new MachineData(new byte[4],1,List.of(new MachineData.Address(0,"a",0))));
    }
    @Test void verifierRejectsUndefinedAddressTargets(){
        var data=new IrGlobalData("p",MiniType.INT.pointerTo(),new byte[8],8,List.of(new IrGlobalData.Address(0,"missing",0,IrGlobalData.AddressKind.OBJECT)));
        var result=new IrResult(List.of(),List.of(),List.of(data),Set.of(),Map.of());
        assertTrue(IrVerifier.inspect(result).stream().anyMatch(p->p.toString().contains("GLOBAL_ADDRESS")));
    }
    @Test void alignmentPrecedesLabelsAndPeSectionPlacement(){
        var module=new WindowsX64MachineAssembler().assemble(new AsmResult("entry",ASSEMBLY.replace("ALIGN 32","ALIGN 8192")));
        byte[] bytes=new WindowsPeLinker().link(new CoffObjectWriter().write(module),"entry").bytes();
        var image=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);int pe=image.getInt(0x3c);
        int first=pe+24+Short.toUnsignedInt(image.getShort(pe+20));
        assertEquals(0,image.getInt(first+40+12)%8192);
    }
    @Test void replacedPointerDesignatorsKeepOnlyTheLastRelocation(){
        var source=new minic.compiler.SourceFile("replace.c","int a,b;int*p[2]={[0]=&a,[0]=&b,[1]=&a,[1]=0};int main(){return 0;}");
        var ir=new minic.compiler.CompilerApi(source,minic.compiler.LanguageMode.C).runToIr();
        var pointers=ir.globalData().stream().filter(g->g.label().equals("p")).findFirst().orElseThrow();
        assertEquals(List.of(new IrGlobalData.Address(0,"b",0,IrGlobalData.AddressKind.OBJECT)),pointers.addresses());
        assertArrayEquals(new byte[16],pointers.bytes());
    }
    @Test void repeatedFieldDesignatorsUseTheLastValueInsteadOfCountingClauses(){
        var source=new minic.compiler.SourceFile("replace-field.c","struct S{int a;int b;};struct S value={.b=3,.a=1,.b=7,.a=5};int main(){return 0;}");
        var ir=new minic.compiler.CompilerApi(source,minic.compiler.LanguageMode.C).runToIr();
        var data=ir.globalData().stream().filter(g->g.label().equals("value")).findFirst().orElseThrow();
        var bytes=ByteBuffer.wrap(data.bytes()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(5,bytes.getInt(0));assertEquals(7,bytes.getInt(4));
    }
    @Test void designatorsStillRejectPositionsOutsideTheObject(){
        for(String declaration:List.of("int a[2]={[2]=1};","int a[2]={[1]=1,2};","struct S{int a;};struct S a={.a=1,2};")){
            var source=new minic.compiler.SourceFile("bad-designator.c",declaration+"int main(){return 0;}");
            var api=new minic.compiler.CompilerApi(source,minic.compiler.LanguageMode.C);
            var semantic=api.stages().stream().filter(minic.compiler.semantic.SemanticAnalyzer.class::isInstance)
                    .map(minic.compiler.semantic.SemanticAnalyzer.class::cast).findFirst().orElseThrow();
            api.runThrough(semantic);
            assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("SEM001")
                    && (error.message().contains("下标越界")||error.message().contains("值过多"))),()->semantic.errors().toString());
        }
    }
    @Test void scalarConstantArithmeticSharesTypedConversionsWithAddressOffsets(){
        var source=new minic.compiler.SourceFile("constants.c","unsigned char a=(unsigned char)256;unsigned long long b=9007199254740993ULL+2ULL;int c=0&&(1/0);int d=(unsigned)-1<0;float e=(float)16777217.0;int main(){return 0;}");
        var ir=new minic.compiler.CompilerApi(source,minic.compiler.LanguageMode.C).runToIr();
        var values=new java.util.LinkedHashMap<String,byte[]>();ir.globalData().forEach(g->values.put(g.label(),g.bytes()));
        assertEquals(0,values.get("a")[0]);
        assertEquals(9007199254740995L,ByteBuffer.wrap(values.get("b")).order(ByteOrder.LITTLE_ENDIAN).getLong());
        assertEquals(0,ByteBuffer.wrap(values.get("c")).order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertEquals(0,ByteBuffer.wrap(values.get("d")).order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertEquals(16777216.0f,ByteBuffer.wrap(values.get("e")).order(ByteOrder.LITTLE_ENDIAN).getFloat());
    }
}
