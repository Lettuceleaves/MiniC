package minic.benchmark;

import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Tiny host fixtures validate observation/containment, not STL performance. */
@Tag("native-perf-contract") @Timeout(90) final class WindowsBenchmarkProcessTest {
    @TempDir static Path temporary;
    static Path observer,fixture;
    @BeforeAll static void compileFixtures()throws Exception{
        String compiler=CppDifferentialHarness.referenceCompiler(System.getenv());
        Path root=Path.of(System.getProperty("minic.project.root",".")).toAbsolutePath();
        observer=WindowsBenchmarkProcess.buildSupervisor(compiler,root.resolve("benchmarks/tools/windows-supervisor.cpp"),temporary.resolve("observer"),Duration.ofSeconds(30));
        Path source=temporary.resolve("fixture.cpp");fixture=temporary.resolve("fixture.exe");
        Files.writeString(source,"""
                #include <windows.h>
                #include <stdio.h>
                #include <stdlib.h>
                #include <string.h>
                int main(int argc,char**argv){
                    if(argc>1){Sleep(60000);return 0;}
                    char mode[32];if(scanf("%31s",mode)!=1)return 9;
                    if(!strcmp(mode,"echo")){char text[128];scanf("%127s",text);printf("echo:%s\\n",text);return 0;}
                    if(!strcmp(mode,"fail")){fputs("expected error\\n",stderr);return 7;}
                    if(!strcmp(mode,"flood")){for(int i=0;i<65536;i++){fputc('o',stdout);fputc('e',stderr);}return 0;}
                    if(!strcmp(mode,"memory")){volatile unsigned char*p=(volatile unsigned char*)malloc(16*1024*1024);if(!p)return 8;for(int i=0;i<16*1024*1024;i+=4096)p[i]=1;printf("memory=%d\\n",p[4096]);free((void*)p);return 0;}
                    if(!strcmp(mode,"tree")){wchar_t path[32768],command[32768];GetModuleFileNameW(0,path,32768);swprintf(command,32768,L"\\\"%ls\\\" --child",path);STARTUPINFOW s={};s.cb=sizeof(s);PROCESS_INFORMATION p={};if(!CreateProcessW(path,command,0,0,TRUE,CREATE_NO_WINDOW,0,0,&s,&p))return 8;printf("child=%lu\\n",p.dwProcessId);fflush(stdout);WaitForSingleObject(p.hProcess,INFINITE);return 0;}
                    return 10;
                }
                """);
        var result=BoundedProcess.run(List.of(compiler,"-std=c++17","-O2",source.toString(),"-o",fixture.toString()),temporary,"",Duration.ofSeconds(30),65536);
        assertEquals(0,result.exitCode(),result::stderr);assertFalse(result.timedOut());
    }
    @Test void unicodePathsAndInputRemainExact()throws Exception{
        Path directory=temporary.resolve("space and 中文");Files.createDirectory(directory);Path executable=directory.resolve("fixture 程序.exe");Files.copy(fixture,executable);
        var result=WindowsBenchmarkProcess.run(observer,executable,directory.resolve("run"),"echo 中文\n",Duration.ofSeconds(3),1024);
        result.requireSuccess();assertEquals("echo:中文\n",result.stdout().replace("\r\n","\n"));assertEquals("",result.stderr());assertTrue(result.wallNanos()>0);assertTrue(result.peakCommitBytes()>0);
        assertThrows(IllegalArgumentException.class,()->WindowsBenchmarkProcess.run(observer,executable,directory.resolve("run"),"",Duration.ofSeconds(1),1024));
    }
    @Test void nonzeroExitAndIndependentStderrArePreserved()throws Exception{
        var result=WindowsBenchmarkProcess.run(observer,fixture,temporary.resolve("failure"),"fail\n",Duration.ofSeconds(3),1024);
        assertEquals(WindowsBenchmarkProcess.Status.COMPLETED,result.status());assertEquals(7,result.exitCode());assertEquals("expected error\n",result.stderr().replace("\r\n","\n"));assertThrows(IllegalStateException.class,result::requireSuccess);
    }
    @Test void outputIsBoundedEvenWhenTheChildExitsQuickly()throws Exception{
        var result=WindowsBenchmarkProcess.run(observer,fixture,temporary.resolve("flood"),"flood\n",Duration.ofSeconds(3),128);
        assertEquals(WindowsBenchmarkProcess.Status.OUTPUT_LIMIT,result.status());assertTrue(Files.size(result.stdoutPath())<=128);assertTrue(Files.size(result.stderrPath())<=128);
    }
    @Test void jobPeakRetainsAnAllocationAlreadyFreedByTheChild()throws Exception{
        var result=WindowsBenchmarkProcess.run(observer,fixture,temporary.resolve("memory"),"memory\n",Duration.ofSeconds(3),1024);
        result.requireSuccess();assertEquals("memory=1\n",result.stdout().replace("\r\n","\n"));assertTrue(result.peakCommitBytes()>=16L*1024*1024);
        assertTrue(result.userCpuNanos()>=0);assertTrue(result.kernelCpuNanos()>=0);
    }
    @Test void timeoutTerminatesTheTargetAndItsDescendant()throws Exception{
        var result=WindowsBenchmarkProcess.run(observer,fixture,temporary.resolve("timeout"),"tree\n",Duration.ofMillis(600),1024);
        assertEquals(WindowsBenchmarkProcess.Status.TIMEOUT,result.status());
        String pid=result.stdout().strip().replace("child=","");assertTrue(pid.matches("[0-9]+"),result::stdout);
        assertFalse(ProcessHandle.of(Long.parseLong(pid)).map(ProcessHandle::isAlive).orElse(false));
    }
}
