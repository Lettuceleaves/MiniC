package minic.benchmark;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** Four-build observations. Probe counters, compile costs and runtime samples remain distinct. */
final class StlBenchmarkReport {
    final Map<String,Object> metadata=new LinkedHashMap<>();
    final List<Map<String,Object>> builds=new ArrayList<>();
    final List<Sample> samples=new ArrayList<>();
    final List<String> errors=new ArrayList<>();
    boolean complete;
    record Sample(String workload,String build,String phase,int repetition,int orderIndex,int size,int rounds,int seed,
                  long inputElements,String inputSha256,String expectedOutputSha256,String actualOutputSha256,
                  String status,boolean oracleMatched,long wallNanos,long userCpuNanos,long kernelCpuNanos,
                  long peakCommitBytes,long observerWallNanos,long exitCode,String directory,
                  String rawStdoutSha256,String rawStderrSha256) {
        Map<String,Object> fields(){
            var m=new LinkedHashMap<String,Object>();
            m.put("workload",workload);m.put("build",build);m.put("phase",phase);m.put("repetition",repetition);m.put("orderIndex",orderIndex);
            m.put("size",size);m.put("rounds",rounds);m.put("seed",seed);m.put("inputElements",inputElements);
            m.put("inputSha256",inputSha256);m.put("expectedOutputSha256",expectedOutputSha256);m.put("actualOutputSha256",actualOutputSha256);
            m.put("rawStdoutSha256",rawStdoutSha256);m.put("rawStderrSha256",rawStderrSha256);
            m.put("status",status);m.put("oracleMatched",oracleMatched);m.put("processWallNanos",wallNanos);
            m.put("userCpuNanos",userCpuNanos);m.put("kernelCpuNanos",kernelCpuNanos);m.put("peakCommitBytes",peakCommitBytes);
            m.put("observerWallNanos",observerWallNanos);m.put("exitCode",exitCode);m.put("directory",directory);return m;
        }
    }
    List<Map<String,Object>> summaries(long minimumNanos){
        Map<String,List<Sample>> groups=new LinkedHashMap<>();
        for(var s:samples)if(s.phase.equals("measurement")&&s.oracleMatched&&s.status.equals("COMPLETED")&&s.exitCode==0)
            groups.computeIfAbsent(s.workload+"/"+s.build+"/"+s.size+"/"+s.rounds+"/"+s.seed,ignored->new ArrayList<>()).add(s);
        var result=new ArrayList<Map<String,Object>>();
        for(var group:groups.values()){
            Sample first=group.getFirst();var times=group.stream().map(Sample::wallNanos).toList();
            var m=new LinkedHashMap<String,Object>();m.put("workload",first.workload);m.put("build",first.build);m.put("size",first.size);m.put("rounds",first.rounds);m.put("seed",first.seed);
            m.put("sampleCount",group.size());m.put("medianProcessWallNanos",StlBenchmarkSupport.median(times));m.put("madProcessWallNanos",StlBenchmarkSupport.mad(times));
            m.put("minProcessWallNanos",Collections.min(times));m.put("maxProcessWallNanos",Collections.max(times));
            m.put("medianNanosPerInputElement",StlBenchmarkSupport.median(group.stream().map(s->s.wallNanos/(double)s.inputElements).toList()));
            m.put("medianUserCpuNanos",StlBenchmarkSupport.median(group.stream().map(Sample::userCpuNanos).toList()));
            m.put("medianKernelCpuNanos",StlBenchmarkSupport.median(group.stream().map(Sample::kernelCpuNanos).toList()));
            m.put("medianPeakCommitBytes",StlBenchmarkSupport.median(group.stream().map(Sample::peakCommitBytes).toList()));
            m.put("maximumPeakCommitBytes",group.stream().mapToLong(Sample::peakCommitBytes).max().orElseThrow());
            m.put("shortSampleWarning",times.stream().anyMatch(t->t<minimumNanos));result.add(m);
        }return result;
    }
    void write(Path directory,long minimumNanos)throws IOException {
        Files.createDirectories(directory);var report=new LinkedHashMap<String,Object>();
        report.put("schemaVersion",1);report.put("status",errors.isEmpty()?(complete?"passed-correctness":"in-progress"):"failed");
        report.put("hashSemantics",Map.of(
                "inputSha256","Exact UTF-8 bytes in stdin.txt",
                "expectedOutputSha256","Expected UTF-8 text after CRLF-to-LF normalization",
                "actualOutputSha256","Captured stdout decoded as UTF-8, then CRLF-to-LF normalized and UTF-8 encoded",
                "rawStdoutSha256","Exact bytes in stdout.txt, including original line endings and invalid UTF-8",
                "rawStderrSha256","Exact bytes in stderr.txt, including original line endings and invalid UTF-8"));
        report.put("metadata",metadata);report.put("errors",errors);report.put("builds",builds);report.put("samples",samples.stream().map(Sample::fields).toList());report.put("summaries",summaries(minimumNanos));
        atomic(directory.resolve("report.json"),NativeBenchmarkReport.encode(report)+"\n");
        List<String> columns=List.of("workload","build","phase","repetition","orderIndex","size","rounds","seed","inputElements","inputSha256","expectedOutputSha256","actualOutputSha256","rawStdoutSha256","rawStderrSha256","status","oracleMatched","processWallNanos","userCpuNanos","kernelCpuNanos","peakCommitBytes","observerWallNanos","exitCode","directory");
        StringBuilder csv=new StringBuilder(String.join(",",columns)).append('\n');
        for(var sample:samples){var fields=sample.fields();csv.append(columns.stream().map(c->NativeBenchmarkSupport.csv(String.valueOf(fields.get(c)))).collect(Collectors.joining(","))).append('\n');}
        atomic(directory.resolve("samples.csv"),csv.toString());
    }
    private static void atomic(Path path,String text)throws IOException {
        Path staged=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(staged,text);
        try{Files.move(staged,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(staged,path,StandardCopyOption.REPLACE_EXISTING);}
    }
}
