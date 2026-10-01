package minic.benchmark;

import minic.cpp.support.CppOwnLibraryReference;
import minic.compiler.library.CppLibraryProfile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("native-perf-contract") final class StlBenchmarkToolsTest {
    @TempDir Path temporary;
    @Test void fourBuildScheduleBalancesPositionsAndAdjacentPairs(){
        Set<String> pairs=new HashSet<>();
        for(int i=0;i<4;i++){
            var row=StlBenchmarkSupport.order(i,1729);assertEquals(Set.copyOf(StlBenchmarkSupport.BUILDS),Set.copyOf(row));
            assertEquals(row,StlBenchmarkSupport.order(i,1729));
            for(int j=0;j<3;j++)assertTrue(pairs.add(row.get(j)+"/"+row.get(j+1)));
        }
        assertEquals(12,pairs.size());
        for(String build:StlBenchmarkSupport.BUILDS)for(int position=0;position<4;position++){
            Set<String> seen=new HashSet<>();for(int round=0;round<4;round++)seen.add(StlBenchmarkSupport.order(round,1729).get(position));
            assertEquals(Set.copyOf(StlBenchmarkSupport.BUILDS),seen);
        }
        assertThrows(IllegalArgumentException.class,()->StlBenchmarkSupport.order(-1,0));
    }
    @Test void medianAndMadRetainHalfUnitDifferences(){
        var samples=List.of(1L,2L);assertEquals(1.5,StlBenchmarkSupport.median(samples));assertEquals(0.5,StlBenchmarkSupport.mad(samples));
        assertEquals(List.of(1L,2L),samples);assertThrows(IllegalArgumentException.class,()->StlBenchmarkSupport.median(List.of()));
    }
    private StlBenchmarkReport.Sample sample(String phase,long wall,boolean match){return new StlBenchmarkReport.Sample("work","gxx-own",phase,0,0,4,1,1729,4,"input","expected","actual","COMPLETED",match,wall,1,2,4096,wall+100,0,"raw/path","raw-stdout","raw-stderr");}
    @Test void rawFailuresAndSetupStayVisibleButDoNotEnterMeasurementStatistics()throws Exception{
        var report=new StlBenchmarkReport();report.samples.add(sample("warmup",999,true));report.samples.add(sample("measurement",1,true));report.samples.add(sample("measurement",2,true));report.samples.add(sample("measurement",88,false));
        var summary=report.summaries(5).getFirst();assertEquals(2,summary.get("sampleCount"));assertEquals(1.5,summary.get("medianProcessWallNanos"));assertEquals(0.5,summary.get("madProcessWallNanos"));assertEquals(true,summary.get("shortSampleWarning"));
        report.errors.add("wrong result");report.write(temporary,5);
        String json=Files.readString(temporary.resolve("report.json"));assertTrue(json.contains("\"status\":\"failed\""));assertTrue(json.contains("\"oracleMatched\":false"));assertEquals(5,Files.readAllLines(temporary.resolve("samples.csv")).size());
    }
    @Test void emptyReportsCannotClaimCompletedAcceptance()throws Exception{
        var report=new StlBenchmarkReport();report.write(temporary,0);assertTrue(Files.readString(temporary.resolve("report.json")).contains("\"status\":\"in-progress\""));
    }
    @Test void reportDistinguishesNormalizedOracleHashFromExactCapturedBytes()throws Exception{
        Path stdout=temporary.resolve("stdout.txt"),stderr=temporary.resolve("stderr.txt");
        Files.write(stdout,new byte[]{(byte)0xff,'\r','\n'});Files.writeString(stderr,"diagnostic\r\n");
        String normalized=StlBenchmarkSupport.hash("\ufffd\n"),raw=StlBenchmarkSupport.hash(stdout),rawError=StlBenchmarkSupport.hash(stderr);
        assertNotEquals(normalized,raw);
        var observed=new StlBenchmarkReport.Sample("work","gxx-own","measurement",0,0,4,1,1729,4,
                "input","expected",normalized,"COMPLETED",false,1,1,2,4096,101,0,temporary.toString(),raw,rawError);
        var report=new StlBenchmarkReport();report.samples.add(observed);report.write(temporary,0);
        var fields=observed.fields();assertEquals(normalized,fields.get("actualOutputSha256"));
        assertEquals(raw,fields.get("rawStdoutSha256"));assertEquals(rawError,fields.get("rawStderrSha256"));
        String json=Files.readString(temporary.resolve("report.json"));
        assertTrue(json.contains("\"hashSemantics\""));assertTrue(json.contains("CRLF-to-LF"));
        assertTrue(json.contains("\"rawStdoutSha256\":\""+raw+"\""));
        String csv=Files.readString(temporary.resolve("samples.csv"));
        assertTrue(csv.lines().findFirst().orElseThrow().contains("rawStdoutSha256,rawStderrSha256"));
        assertTrue(csv.contains("\""+raw+"\",\""+rawError+"\""));
    }
    @Test void archiveExtractionRejectsEscapingEntries()throws Exception{
        Path zip=temporary.resolve("bad.zip");try(var output=new ZipOutputStream(Files.newOutputStream(zip))){output.putNextEntry(new ZipEntry("../escape"));output.write(1);output.closeEntry();}
        assertThrows(java.io.IOException.class,()->StlBenchmarkSupport.extract(zip,temporary.resolve("archive")));assertFalse(Files.exists(temporary.resolve("escape")));
    }
    @Test void shimPreparationUsesOnlyTheRequestedFrozenLibrary()throws Exception{
        Path library=temporary.resolve("own library");Files.createDirectories(library);
        for(var entry:CppLibraryProfile.defaults().entries().values()){Path file=library.resolve(Path.of(entry.header()).getFileName());Files.writeString(file,"// "+file.getFileName());}
        Path headers=CppOwnLibraryReference.prepareHeaders(temporary.resolve("shims"),library);
        assertTrue(Files.readString(headers.resolve("vector")).contains(library.toAbsolutePath().toString().replace('\\','/')+"/vector.mh"));
        Path source=temporary.resolve("program.cpp"),exe=temporary.resolve("program.exe");
        var command=CppOwnLibraryReference.compileCommand("g++",temporary,headers,source,exe);
        assertTrue(command.contains("-nostdinc++"));assertTrue(command.contains("-D__MINIC_SELF_STL__=1"));assertTrue(command.contains("-include"));assertEquals(exe.toAbsolutePath().toString(),command.getLast());
        Files.delete(library.resolve("vector.mh"));assertThrows(java.io.IOException.class,()->CppOwnLibraryReference.prepareHeaders(temporary.resolve("missing"),library));
    }
    @Test void observerReportRejectsDuplicateMissingAndNegativeMetrics(){
        String valid="schemaVersion=1\nstatus=completed\nexitCode=0\nwin32Error=0\nwallNanos=3\nuserCpuNanos=1\nkernelCpuNanos=0\npeakCommitBytes=4096\n";
        var result=WindowsBenchmarkProcess.parse(valid,5,"a","",temporary,temporary,temporary);assertEquals(4096,result.peakCommitBytes());result.requireSuccess();
        assertThrows(IllegalArgumentException.class,()->WindowsBenchmarkProcess.parse(valid+"wallNanos=5\n",5,"","",temporary,temporary,temporary));
        assertThrows(IllegalArgumentException.class,()->WindowsBenchmarkProcess.parse(valid.replace("wallNanos=3","wallNanos=-1"),5,"","",temporary,temporary,temporary));
        assertThrows(IllegalArgumentException.class,()->WindowsBenchmarkProcess.parse(valid.replace("kernelCpuNanos=0\n",""),5,"","",temporary,temporary,temporary));
    }
    @Test void configurationRequiresFreshOutputAndValidBoundedInputs(){
        String output="--output="+temporary.resolve("new-output");
        var config=StlBenchmarkMain.Config.parse(new String[]{output,"--seed=7","--repetitions=12"});assertEquals(7,config.seed());assertEquals(12,config.repetitions());
        assertThrows(IllegalArgumentException.class,()->StlBenchmarkMain.Config.parse(new String[]{"--output="+temporary}));
        assertThrows(IllegalArgumentException.class,()->StlBenchmarkMain.Config.parse(new String[]{output,"--seed=-1"}));
        assertThrows(IllegalArgumentException.class,()->StlBenchmarkMain.Config.parse(new String[]{output,"--seed=1","--seed=2"}));
        assertThrows(IllegalArgumentException.class,()->StlBenchmarkMain.Config.parse(new String[]{output,"--size=262144","--rounds=10000"}));
        assertThrows(IllegalArgumentException.class,()->StlBenchmarkMain.Config.parse(new String[]{output,"--revision=--help"}));
    }
}
