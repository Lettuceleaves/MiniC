package minic.benchmark;

import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Correctness/count preflight only: this test does not collect benchmark samples. */
@Tag("stl-contract") @Timeout(600)
class StlBitsetCountWorkloadsTest {
    @TempDir Path temporary;
    private static final List<String> ADDED=List.of("bitset-count-sparse","bitset-count-dense");
    private static StlBenchmarkWorkloads.Workload workload(String id){return StlBenchmarkWorkloads.workloads().stream().filter(w->w.id().equals(id)).findFirst().orElseThrow();}

    @Test void originalNineRemainInOrderAndNewWorkloadsAreDistinct(){
        var ids=StlBenchmarkWorkloads.workloads().stream().map(StlBenchmarkWorkloads.Workload::id).toList();
        assertEquals(List.of("vector-sort","binary-search","priority-queue","ordered-map","deque","string","bitset","string-short","bitset-count"),ids.subList(0,9));
        assertEquals(ADDED,ids.subList(9,ids.size()));
    }
    @Test void deltaOracleMatchesIndependentPerBitCountingWithoutDensityDrift(){
        for(String id:ADDED)for(int size:List.of(1,17,65,4096))for(int seed:List.of(0,23,1729)){
            var expected=slowOracle(id,size,2,seed);
            assertEquals(expected.stdout(),StlBenchmarkWorkloads.input(workload(id),size,2,seed).expectedStdout(),id+" "+size+" "+seed);
        }
    }
    @Test void newProbeCountersRequireExactlyTwoMutationsAndCountsPerInput(){
        for(String id:ADDED){
            var w=workload(id);var input=StlBenchmarkWorkloads.input(w,17,2,23);var oracle=slowOracle(id,17,2,23);
            String valid=counterText(input,oracle,34,34);
            assertEquals(2,StlBenchmarkProbes.validate(w,input,valid).size());
            assertThrows(IllegalStateException.class,()->StlBenchmarkProbes.validate(w,input,counterText(input,oracle,33,34)));
            assertThrows(IllegalStateException.class,()->StlBenchmarkProbes.validate(w,input,counterText(input,oracle,34,33)));
            assertThrows(IllegalStateException.class,()->StlBenchmarkProbes.validate(w,input,valid.replace("count_total=", "count_total=9")));
            assertThrows(IllegalStateException.class,()->StlBenchmarkProbes.validate(w,input,valid.replace(" count_calls=34", "")));
        }
    }
    private static final String ZERO=" comparisons=0 value_ctor=0 copy_ctor=0 move_ctor=0 copy_assign=0 move_assign=0 destroyed=0 live=0 peak_live=0 allocations=0 frees=0 allocated_bytes=0 freed_bytes=0 live_bytes=0 peak_bytes=0";
    private static String counterText(StlBenchmarkWorkloads.Input input,Oracle oracle,int calls,int mutations){
        var lines=input.expectedStdout().lines().toList();var out=new StringBuilder();
        for(int round=0;round<lines.size();round++)out.append(lines.get(round)).append(ZERO).append(" count_calls=").append(calls).append(" bit_mutations=").append(mutations).append(" count_total=").append(oracle.totals().get(round)).append('\n');
        return out.toString();
    }

    @ParameterizedTest @ValueSource(strings={"bitset-count-sparse","bitset-count-dense"})
    void fourUninstrumentedBuildsMatchPerBitOracle(String id)throws Exception {
        Path root=Path.of(System.getProperty("minic.project.root", ".")).toAbsolutePath();
        Path out=output(id);var w=workload(id);String source=Files.readString(root.resolve(w.sourcePath()));
        var report=new LinkedHashMap<String,Object>();report.put("purpose","four-build checksum correctness only; no timing samples");report.put("sourceSha256",StlBenchmarkSupport.hash(source));
        var builds=new ArrayList<Object>();report.put("builds",builds);
        try {
            for(String backend:List.of("minic-base","minic-opt","gxx-own","gxx-stl")){
                Path dir=out.resolve(backend);Files.createDirectories(dir);Path exe=compile(root,root,dir,source,backend);
                var entries=new ArrayList<Object>();
                for(int size:List.of(17,4096)){
                    var input=StlBenchmarkWorkloads.input(w,size,2,23);assertEquals(slowOracle(id,size,2,23).stdout(),input.expectedStdout());
                    var result=run(exe,dir,input.stdin(),"size-"+size);
                    assertEquals(input.expectedStdout(),result.stdout().replace("\r\n","\n"),backend);
                    entries.add(Map.of("size",size,"rounds",2,"stdin",input.stdin(),"stdout",result.stdout()));
                }
                builds.add(Map.of("backend",backend,"artifactSha256",StlBenchmarkSupport.hash(exe),"runs",entries));
            }
        } finally {Files.writeString(out.resolve("correctness.json"),NativeBenchmarkReport.encode(report));}
    }
    @Test void allElevenCounterProbesKeepHashesAndBalanceAcrossFourBuilds()throws Exception {
        Path root=Path.of(System.getProperty("minic.project.root", ".")).toAbsolutePath(),out=output("probes");
        var instrumented=StlBenchmarkProbes.instrumentOwnHeaders(root,out.resolve("instrumented-project"));
        String source=StlBenchmarkProbes.source(root);var report=new LinkedHashMap<String,Object>();
        report.put("purpose","counter correctness only; no timing samples");report.put("sourceSha256",StlBenchmarkSupport.hash(source));report.put("instrumentation",instrumented.strategy());
        var builds=new ArrayList<Object>();report.put("builds",builds);
        try {
            for(String backend:List.of("minic-base","minic-opt","gxx-own","gxx-stl")){
                Path dir=out.resolve(backend);Files.createDirectories(dir);Path exe=compile(root,instrumented.projectRoot(),dir,source,backend);
                var entries=new ArrayList<Object>();
                for(var w:StlBenchmarkWorkloads.workloads()){
                    int size=ADDED.contains(w.id())?4096:17;var expected=StlBenchmarkWorkloads.input(w,size,2,23);
                    String input=StlBenchmarkProbes.input(w,size,2,23);var result=run(exe,dir,input,w.id());
                    var checked=StlBenchmarkProbes.validate(w,expected,result.stdout());
                    entries.add(Map.of("id",w.id(),"stdin",input,"rounds",checked.stream().map(r->Map.of("round",r.round(),"hash",Long.toUnsignedString(r.hash()),"counters",r.counters())).toList()));
                }
                builds.add(Map.of("backend",backend,"artifactSha256",StlBenchmarkSupport.hash(exe),"workloads",entries));
            }
        } finally {Files.writeString(out.resolve("counts.json"),NativeBenchmarkReport.encode(report));}
    }
    private Path output(String id)throws Exception {
        Path root=System.getProperty("minic.bitset.workload.output")==null?temporary:Path.of(System.getProperty("minic.bitset.workload.output"));
        Path path=root.resolve(id);Files.createDirectories(path);return path;
    }
    private static Path compile(Path root,Path libraries,Path dir,String source,String backend)throws Exception {
        String gxx=CppDifferentialHarness.referenceCompiler(System.getenv());Path src=dir.resolve("program.cpp"),exe=dir.resolve("program.exe");Files.writeString(src,source);List<String> command;
        if(backend.startsWith("minic"))command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Xmx384m","-Dfile.encoding=UTF-8","-Duser.language=en","-cp",StlBenchmarkSupport.absoluteClasspath(),StlBenchmarkMain.class.getName(),"--compile-minic",src.toString(),backend.equals("minic-opt")?"OPTIMIZED":"BASELINE",libraries.toString());
        else if(backend.equals("gxx-own"))command=CppOwnLibraryReference.compileCommand(gxx,dir,CppOwnLibraryReference.prepareHeaders(dir.resolve("headers"),libraries.resolve("lib/cpp")),src,exe);
        else {var c=new ArrayList<>(List.of(gxx));c.addAll(CppDifferentialHarness.referenceFlags(dir));c.addAll(List.of(src.toString(),"-o",exe.toString()));command=c;}
        Files.writeString(dir.resolve("command.json"),NativeBenchmarkReport.encode(command));
        var compiled=BoundedProcess.run(command,root,"",Duration.ofSeconds(180),1048576);Files.writeString(dir.resolve("compile-stdout.txt"),compiled.stdout());Files.writeString(dir.resolve("compile-stderr.txt"),compiled.stderr());
        assertFalse(compiled.timedOut(),command.toString());assertFalse(compiled.outputExceeded());assertEquals(0,compiled.exitCode(),compiled.stderr()+compiled.stdout());
        if(backend.startsWith("minic"))Files.copy(NativeBenchmarkSupport.compilation(compiled.stdout()).artifact(),exe);
        return exe;
    }
    private static BoundedProcess.Result run(Path exe,Path dir,String input,String id)throws Exception {
        Files.writeString(dir.resolve(id+"-stdin.txt"),input);var result=BoundedProcess.run(List.of(exe.toString()),dir,input,Duration.ofSeconds(45),1048576);
        Files.writeString(dir.resolve(id+"-stdout.txt"),result.stdout());Files.writeString(dir.resolve(id+"-stderr.txt"),result.stderr());
        assertFalse(result.timedOut(),id);assertFalse(result.outputExceeded(),id);assertEquals(0,result.exitCode(),id+result.stderr());assertEquals("",result.stderr(),id);return result;
    }

    private record Oracle(String stdout,List<Long> totals){}
    /** Deliberately recounts all bits after each flip instead of using production's quantity delta. */
    private static Oracle slowOracle(String id,int size,int rounds,int seed){
        var output=new StringBuilder();var totals=new ArrayList<Long>();boolean dense=id.equals("bitset-count-dense");
        for(int round=0;round<rounds;round++){
            long state=(seed+(long)round*1013904223L)&0xffffffffL;boolean[] bits=new boolean[1024];Arrays.fill(bits,dense);int[] anchors=new int[16];var observations=new ArrayList<Long>();long total=0;
            for(int word=0;word<16;word++){state=(state*1664525L+1013904223L)&0xffffffffL;anchors[word]=(int)((state>>>8)&65535)%64;bits[word*64+anchors[word]]=!dense;}
            for(int i=0;i<size;i++){
                state=(state*1664525L+1013904223L)&0xffffffffL;int word=(int)((state>>>8)&65535)%16;
                state=(state*1664525L+1013904223L)&0xffffffffL;int bit=(int)((state>>>8)&65535)%64;int index=word*64+(i%4==0?anchors[word]:bit);observations.add((long)index);
                for(int flip=0;flip<2;flip++){
                    bits[index]=!bits[index];int count=0;
                    for(boolean value:bits)if(value)count++;
                    for(int w=0;w<16;w++){int inWord=0;for(int b=0;b<64;b++)if(bits[w*64+b])inWord++;assertTrue(dense?inWord>=62&&inWord<=64:inWord<=2);}
                    observations.add((long)count);total+=count;
                }
            }
            for(boolean bit:bits)observations.add(bit?1L:0L);
            output.append("round=").append(round).append(" hash=").append(Long.toUnsignedString(StlBenchmarkWorkloads.sequenceHash(observations.stream().mapToLong(Long::longValue).toArray()))).append('\n');totals.add(total);
        }
        return new Oracle(output.toString(),List.copyOf(totals));
    }
}
