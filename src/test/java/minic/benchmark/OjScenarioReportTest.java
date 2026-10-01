package minic.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("native-perf-contract")
final class OjScenarioReportTest {
    @TempDir Path temporary;
    @Test void threeBuildScheduleBalancesPositionsAndDirectedPairs() {
        var positions=new HashMap<String,Integer>();var pairs=new HashMap<String,Integer>();
        for(int repetition=0;repetition<6;repetition++) {
            var order=OjScenarioReport.order(repetition,1729);assertEquals(3,new HashSet<>(order).size());
            for(int i=0;i<3;i++)positions.merge(order.get(i)+"/"+i,1,Integer::sum);
            for(int i=1;i<3;i++)pairs.merge(order.get(i-1)+"/"+order.get(i),1,Integer::sum);
        }
        assertEquals(Set.of(2),new HashSet<>(positions.values()));assertEquals(9,positions.size());
        assertEquals(Set.of(2),new HashSet<>(pairs.values()));assertEquals(6,pairs.size());
    }
    @Test void completeMatchedLongSamplesCanPassThePointTwoGate() {
        var result=summary(samples(120_000_000L));assertTrue(result.valid());assertTrue(result.passed());
        assertEquals(1.2,result.optOverSystem());assertEquals(6,result.statistics().get("minic-opt").get("samples"));
    }
    @Test void slowerOptimizedMedianFailsEvenWhenAllCorrect() {var result=summary(samples(121_000_000L));assertTrue(result.valid());assertFalse(result.passed());}
    @Test void anyShortSampleCannotPass() {
        var samples=samples(110_000_000L);samples.set(7,samples.get(7).withWall(99_999_999));
        var result=summary(samples);assertFalse(result.valid());assertFalse(result.passed());assertTrue(result.shortSampleWarning());
    }
    @Test void missingDuplicateOrMismatchedGroupsCannotPass() {
        var missing=samples(110_000_000L);missing.removeLast();assertFalse(summary(missing).valid());
        var duplicate=samples(110_000_000L);duplicate.add(duplicate.getLast());assertFalse(summary(duplicate).valid());
        var input=samples(110_000_000L);input.set(7,input.get(7).withInput("different",2));assertFalse(summary(input).valid());
    }
    @Test void failureAndWrongFullOutputCannotBeDroppedFromStatistics() {
        var failed=samples(110_000_000L);failed.set(7,failed.get(7).withStatus("TIMEOUT",0));assertFalse(summary(failed).valid());
        var wrong=samples(110_000_000L);wrong.set(7,wrong.get(7).withOutput("wrong"));assertFalse(summary(wrong).valid());
    }
    @Test void validationDoesNotSatisfyMeasurementCoverage() {
        assertFalse(summary(samples(110_000_000L).subList(0,7)).valid());
        var incomplete=samples(110_000_000L);incomplete.removeFirst();assertFalse(summary(incomplete).valid());
    }
    @Test void allNinetySixCasesMustPassAndAFilteredRunIsAlwaysPartial() {
        var required=new HashSet<String>();var summaries=new HashMap<String,OjScenarioReport.Summary>();
        for(int i=0;i<96;i++){required.add("case"+i);summaries.put("case"+i,summary(samples(110_000_000L)));}
        assertTrue(OjScenarioReport.suitePassed(required,required,false,true,summaries));
        assertFalse(OjScenarioReport.suitePassed(required,required,true,true,summaries));
        assertFalse(OjScenarioReport.suitePassed(required,required,false,false,summaries));
        summaries.put("case95",summary(samples(121_000_000L)));assertFalse(OjScenarioReport.suitePassed(required,required,false,true,summaries));
        summaries.remove("case95");assertFalse(OjScenarioReport.suitePassed(required,required,false,true,summaries));
    }
    @Test void offlineFileAuditRejectsTraversalAndTampering()throws Exception {
        for(String filename:List.of("source.cpp","input.txt","stdout.txt","program.exe")){
            Files.writeString(temporary.resolve(filename),"data");var manifest=Map.of(filename,StlBenchmarkSupport.hash(temporary.resolve(filename)));
            OjScenarioSupport.auditFiles(temporary,manifest);Files.writeString(temporary.resolve(filename),"changed");
            assertThrows(Exception.class,()->OjScenarioSupport.auditFiles(temporary,manifest));
        }
        assertThrows(Exception.class,()->OjScenarioSupport.resolve(temporary,"../outside"));
        assertThrows(Exception.class,()->OjScenarioSupport.resolve(temporary,"C:/outside"));
    }
    @Test void persistedRowsRoundTripWithoutDroppingRawEvidence()throws Exception {
        var row=samples(110_000_000L).getFirst();var path=temporary.resolve("row.properties");
        OjScenarioSupport.writeProperties(path,row.fields());
        assertEquals(row,OjScenarioReport.Sample.read(OjScenarioSupport.readProperties(path)));
        Files.writeString(path,"same=one\nsame=two\n");assertThrows(Exception.class,()->OjScenarioSupport.readProperties(path));
    }
    private static OjScenarioReport.Summary summary(List<OjScenarioReport.Sample> samples){return OjScenarioReport.summarize("case",samples,6,1,1729);}
    private static ArrayList<OjScenarioReport.Sample> samples(long optimized) {
        var rows=new ArrayList<OjScenarioReport.Sample>();int i=0;
        for(String build:OjScenarioReport.BUILDS)rows.add(row(build,"verify",0,i++,optimized));
        i=0;for(String build:OjScenarioReport.order(0,1729))rows.add(row(build,"warmup",0,i++,optimized));
        for(int rep=0;rep<6;rep++){i=0;for(String build:OjScenarioReport.order(rep,1729))rows.add(row(build,"measurement",rep,i++,optimized));}
        return rows;
    }
    private static OjScenarioReport.Sample row(String build,String phase,int rep,int order,long optimized) {
        return new OjScenarioReport.Sample("case",build,phase,rep,order,1,100,"input","output","output","COMPLETED",0,
                build.equals("minic-opt")?optimized:100_000_000,20,10,4096,"runs/"+phase+rep+build,Map.of("stdout.txt","rawhash"));
    }
}
