package minic.cpp;

import minic.compiler.SourceFile;
import minic.compiler.asm.AsmResult;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.link.Linker;
import minic.cpp.support.BoundedProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
final class CppImmediateShiftRelocationTest {
    @TempDir Path temporary;
    @Test void immediateMemoryShiftModifiesTheNamedGlobalAfterCoffAndPeRelocation()throws Exception {
        var source=new SourceFile("rip-shift.cpp","int main(){return 32;}");
        var assembly=new AsmResult("minic$entry","""
                PUBLIC minic$entry
                EXTERN ExitProcess:PROC
                .data
                value BYTE 64, 0, 0, 0, 0, 0, 0, 0
                .code
                minic$entry PROC
                    sub rsp, 40
                    shr DWORD PTR [value], 1
                    mov ecx, DWORD PTR [value]
                    call ExitProcess
                minic$entry ENDP
                END
                """);
        var builder=new ObjBuilder();var object=builder.build(source,assembly,temporary,"program");
        assertTrue(builder.succeeded(),()->builder.errors().toString());
        var linker=new Linker();linker.link(source,object,temporary,"program");
        assertTrue(linker.succeeded(),()->linker.errors().toString());
        var result=BoundedProcess.run(List.of(temporary.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(5),65536);
        assertFalse(result.timedOut());assertFalse(result.outputExceeded());assertEquals(32,result.exitCode(),result::stderr);
    }
}
