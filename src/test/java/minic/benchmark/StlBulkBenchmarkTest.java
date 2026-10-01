package minic.benchmark;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StlBulkBenchmarkTest {
    @Test void smokeCannotBecomeUncommittedFormalMeasurement(){
        assertThrows(IllegalArgumentException.class,()->StlBulkBenchmarkMain.Config.parse(new String[]{"--smoke-source=x.cpp","--measure=true"}));
        assertThrows(IllegalArgumentException.class,()->StlBulkBenchmarkMain.Config.parse(new String[]{"--smoke-source=x.cpp","--workload-revision=HEAD"}));
        assertFalse(StlBulkBenchmarkMain.Config.parse(new String[]{"--smoke-source=x.cpp"}).measure());
        assertTrue(StlBulkBenchmarkMain.Config.parse(new String[]{"--workload-revision=HEAD","--measure=true"}).measure());
    }
    @Test void everyBuildHasAdjacentAlternatingProductPairs(){
        for(int repetition=0;repetition<8;repetition++){
            var order=StlBulkBenchmarkMain.pairedOrder(repetition,1729);assertEquals(8,new HashSet<>(order).size());
            for(int i=0;i<8;i+=2){assertEquals(order.get(i).split("/")[1],order.get(i+1).split("/")[1]);assertNotEquals(order.get(i).split("/")[0],order.get(i+1).split("/")[0]);}
        }
        var first=StlBulkBenchmarkMain.pairedOrder(0,1729);var again=StlBulkBenchmarkMain.pairedOrder(4,1729);assertEquals(first,again);
    }
    @Test void oracleCoversAllModesAndSeparatesObservationCounts(){
        for(var mode:StlBulkCopyWorkload.Mode.values()){
            var a=StlBulkCopyWorkload.input(mode,17,2,3,1729);var b=StlBulkCopyWorkload.input(mode,17,2,3,1729);
            assertEquals(a,b);assertEquals(2,a.expectedStdout().lines().count());assertTrue(a.observedElements()>0);assertTrue(a.transferredElements()>0);
            assertNotEquals(a.expectedStdout(),StlBulkCopyWorkload.input(mode,17,2,3,1730).expectedStdout());
            if(mode.ordinal()>=2)assertEquals(25L*2*3,a.observedElements());
        }
    }
    @Test void invalidBudgetsAndUnknownFlagsFailBeforeCompilation(){
        assertThrows(IllegalArgumentException.class,()->StlBulkCopyWorkload.input(StlBulkCopyWorkload.Mode.VECTOR_ASSIGN,262144,10000,10000,0));
        assertThrows(IllegalArgumentException.class,()->StlBulkBenchmarkMain.Config.parse(new String[]{"--smoke-source=x.cpp","--measure=maybe"}));
        assertThrows(IllegalArgumentException.class,()->StlBulkBenchmarkMain.Config.parse(new String[]{"--smoke-source=x.cpp","--unknown=x"}));
    }
}
