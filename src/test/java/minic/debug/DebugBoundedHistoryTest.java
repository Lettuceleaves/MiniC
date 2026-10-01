package minic.debug;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DebugBoundedHistoryTest {
    private static final SourceFile SOURCE = new SourceFile("bounded-history.c", """
        #include <stdio.h>
        int main(){
            int value=0;
            while(value<12){
                value++;
                printf("%d ",value);
            }
            return 0;
        }
        """);
    private DebugApi bounded(int limit){return DebugApi.fromIr(SOURCE,new CompilerApi(SOURCE).runToIr(),"",limit);}
    @Test void currentOnlyKeepsAbsoluteStepIndicesAndExecutionResults(){
        var debug=bounded(1);int last=0;
        while(debug.canNext()){
            assertEquals(++last,debug.next().index());
            assertFalse(debug.canPrevious());
            assertSame(debug.current(),debug.previous());
        }
        assertTrue(last>12);
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status());
        assertEquals("1 2 3 4 5 6 7 8 9 10 11 12 ",debug.current().runtime().stdout());
    }
    @Test void retainedWindowReplaysIdenticalContextsWithoutReexecution(){
        var debug=bounded(3);
        for(int i=0;i<10;i++)debug.next();
        var end=debug.current();var middle=debug.previous();var oldest=debug.previous();
        assertEquals(end.index()-2,oldest.index());assertFalse(debug.canPrevious());
        assertSame(middle,debug.next());assertSame(end,debug.next());
        var next=debug.next();assertEquals(end.index()+1,next.index());
        assertSame(end,debug.previous());assertSame(middle,debug.previous());assertFalse(debug.canPrevious());
    }
    @Test void defaultSessionsStillRetainTheInitialContext(){
        var debug=DebugApi.fromIr(SOURCE,new CompilerApi(SOURCE).runToIr(),"");
        var initial=debug.current();while(debug.canNext())debug.next();
        while(debug.canPrevious())debug.previous();assertSame(initial,debug.current());
    }
    @Test void invalidLimitsAreRejected(){assertThrows(IllegalArgumentException.class,()->bounded(0));}
}
