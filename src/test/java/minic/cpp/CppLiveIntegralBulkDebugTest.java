package minic.cpp;

import minic.compiler.*;
import minic.debug.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Invalid scalar values are tested only in the checked interpreter, never run in native/G++ oracles. */
final class CppLiveIntegralBulkDebugTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void copiedInitializationMaskDoesNotInventInitializedDestination(boolean algorithm){
        String source="""
                #include <algorithm>
                #include <cstring>
                #include <stdio.h>
                int main(){
                    int source[2];source[0]=7;
                    int destination[2]={9,9};
                    %s
                    printf("copied:%%d\\n",destination[0]);
                    return destination[1];
                }
                """.formatted(algorithm?"std::copy(source,source+2,destination);":"std::memmove(destination,source,sizeof(source));");
        var result=execute(source);
        assertEquals(Debugger.Status.FAILED,result.context().stop().status());
        assertTrue(result.context().stop().error().contains("uninitialized"),result.context().stop()::error);
        assertEquals("copied:7\n",result.context().runtime().stdout());
        assertEquals(9,result.context().stop().range().startLine(),"failure is the actual destination read");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void copiedUninitializedBytesMayRemainUnreadUnderAcceptedDiagnosticBoundary(boolean algorithm){
        String source="""
                #include <algorithm>
                #include <cstring>
                #include <stdio.h>
                int main(){int source[2];source[0]=7;int destination[2]={9,9};
                    %s
                    printf("copied:%%d\\n",destination[0]);return 0;}
                """.formatted(algorithm?"std::copy(source,source+2,destination);":"std::memmove(destination,source,sizeof(source));");
        var result=execute(source);
        assertEquals(Debugger.Status.COMPLETED,result.context().stop().status(),result.context().stop()::error);
        assertEquals("copied:7\n",result.context().runtime().stdout());
        assertEquals(0,result.context().runtime().termination().status());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void overlapUsesTheOriginalInitializationMask(boolean algorithm){
        String source="""
                #include <algorithm>
                #include <cstring>
                #include <stdio.h>
                int main(){int values[5];values[1]=4;values[3]=9;
                    %s
                    printf("copied:%%d,%%d\\n",values[0],values[2]);
                    return values[1];}
                """.formatted(algorithm?"std::copy(values+1,values+4,values);":"std::memmove(values,values+1,3*sizeof(int));");
        var result=execute(source);
        assertEquals(Debugger.Status.FAILED,result.context().stop().status());
        assertTrue(result.context().stop().error().contains("uninitialized"),result.context().stop()::error);
        assertEquals("copied:4,9\n",result.context().runtime().stdout());
        assertEquals(7,result.context().stop().range().startLine());
    }
    private static Debugger.Execution execute(String text){
        var source=new SourceFile("bulk-mask.cpp",text);
        var ir=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var result=DebugApi.execute(source,ir,"",100_000,1024);
        assertFalse(result.stepLimitReached());assertFalse(result.outputLimitReached());return result;
    }
}
