package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
final class CppExtendedAlignmentTest {
    @TempDir Path temporary;
    private void agree(String name, String source, String output) throws Exception {
        for (var level : OptimizationLevel.values()) {
            var limits = new CppDifferentialHarness.Limits(Duration.ofSeconds(30), Duration.ofSeconds(10), 200000, 1048576);
            var result = new CppDifferentialHarness(temporary, System.getenv().getOrDefault("MINIC_CXX", "g++"),
                    limits, LanguageMode.CPP17_ALGORITHM, level).run(name + level, source, "");
            assertTrue(result.passed(), result.describe());
            assertEquals(output, result.outcomes().get(CppDifferentialHarness.Backend.MINIC_NATIVE).stdout().replace("\r\n","\n"));
        }
    }
    @Test void localGlobalArraysAndNestedFieldsHaveRealAlignedAddresses() throws Exception {
        agree("objects", """
                #include <stdio.h>
                struct Aligned { alignas(64) int value; };
                struct Outer { char lead; Aligned object; };
                alignas(256) int global = 7;
                Aligned globals[2];
                struct Big {alignas(256) int value;};
                int check(){Big local={11}; Aligned array[3]={}; Outer outer={};
                    array[2].value=19;outer.object.value=23;
                    if((unsigned long long)&local%256) return 1;
                    if((unsigned long long)&global%256) return 2;
                    if((unsigned long long)&array[0]%64 || (unsigned long long)&array[2]%64) return 3;
                    if((unsigned long long)&globals[1]%64 || (unsigned long long)&outer.object%64) return 4;
                    return local.value+global+array[2].value+outer.object.value;
                }
                int main(){printf("%d %d %d\\n",check(),(int)sizeof(Aligned),(int)alignof(Outer));return 0;}
                """, "60 64 64\n");
    }
    @Test void stackArgumentsRecursionAndRecordReturnSurviveRealignment() throws Exception {
        agree("calls", """
                #include <stdio.h>
                struct Value{alignas(64) int number;};
                Value make(int a,int b,int c,int d,int e,int f){Value result={};
                    if((unsigned long long)&result%64)result.number=-100;else result.number=a+b+c+d+e+f;
                    return result;}
                struct Big{alignas(256) int value;};
                int recur(int depth,int a,int b,int c,int d,int e){Big held={depth+e};
                    if((unsigned long long)&held%256)return -100;
                    if(depth==0)return held.value;
                    int nested=recur(depth-1,a,b,c,d,e+1);return held.value+nested;}
                int main(){Value value=make(1,2,3,4,5,6);printf("%d %d\\n",value.number,recur(3,1,2,3,4,5));return 0;}
                """, "21 32\n");
    }
    @Test void variadicIncomingAreaUsesUnalignedEntryFrame() throws Exception {
        agree("variadic", """
                #include <stdio.h>
                #include <stdarg.h>
                struct Big{alignas(256) int value;};
                int total(int count,...){Big local={};va_list ap;va_start(ap,count);
                    for(int i=0;i<count;++i)local.value+=va_arg(ap,int);va_end(ap);
                    if((unsigned long long)&local%256)return -1;return local.value;}
                int main(){printf("%d\\n",total(7,1,2,3,4,5,6,7));return 0;}
                """, "28\n");
    }
    @Test void pageSizedAlignmentProbesAndRestoresTheStack() throws Exception {
        agree("page-alignment", """
                #include <stdio.h>
                struct Page{alignas(8192) char bytes[8192];};
                alignas(8192) int globalPage;
                int nested(int n){Page storage;storage.bytes[0]=(char)n;storage.bytes[8191]=3;
                    if((unsigned long long)&storage%8192)return -100;
                    if((unsigned long long)&globalPage%8192)return -200;
                    if(n==0)return storage.bytes[8191];int sum=nested(n-1);return storage.bytes[0]+storage.bytes[8191]+sum;}
                int main(){printf("%d\\n",nested(4));return 0;}
                """, "25\n");
    }
    @Test void explicitlyAlignedScalarObjectsAreSupportedIndependentlyOfOldMingwLimit() throws Exception {
        // MinGW 8 emits -Wattributes and ignores >16 alignment on scalar stack declarations.
        // Keep this supported MiniC implementation contract separate from that limited oracle.
        var source = new SourceFile("scalar-alignment.cpp", """
                #include <stdio.h>
                int total(int n){alignas(256) int value=n;
                    if((unsigned long long)&value%256)return -100;
                    if(n==0)return 0;int child=total(n-1);return value+child;}
                int main(){printf("%d\\n",total(4));return 0;}
                """);
        var compiler=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM);
        var ir=compiler.runToIr();
        var debug=minic.debug.DebugApi.fromIr(source,ir,"");
        for(int steps=0;debug.canNext()&&steps<10000;steps++)debug.next();
        assertFalse(debug.canNext());
        assertEquals(minic.debug.Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals("10\n",debug.current().runtime().stdout().replace("\r\n","\n"));
        for(var level:OptimizationLevel.values()){
            var asm=new Assembler(ir,level);
            String name="scalar-"+level;
            var obj=new minic.compiler.obj.ObjBuilder(source,asm,temporary,name);
            var link=new minic.compiler.link.Linker(source,obj,temporary,name);
            new CompilerApi(java.util.List.of(asm,obj,link)).runThrough(link);
            assertTrue(link.succeeded(),()->link.errors().toString());
            var result=minic.cpp.support.BoundedProcess.run(java.util.List.of(temporary.resolve(name+".exe").toString()),
                    temporary,"",Duration.ofSeconds(10),65536);
            assertFalse(result.timedOut());assertFalse(result.outputExceeded());
            assertEquals(0,result.exitCode(),result::stderr);
            assertEquals("10\n",result.stdout().replace("\r\n","\n"));
        }
    }
    @Test void ordinaryFramesKeepTheirOriginalPrologue() {
        var api = new CompilerApi(new SourceFile("ordinary.cpp", "int plus(int x){return x+1;} int main(){return plus(1);}"), LanguageMode.CPP17_ALGORITHM);
        var stage = api.stages().stream().filter(Assembler.class::isInstance).map(Assembler.class::cast).findFirst().orElseThrow();
        api.runThrough(stage);
        assertTrue(stage.succeeded(), stage.errors().toString());
        String assembly=stage.result().text();
        assertFalse(assembly.contains("$align_probe"));
        assertTrue(assembly.contains("mov rsp, rbp"));
    }
}
