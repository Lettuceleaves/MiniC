package minic.benchmark;

import minic.cpp.support.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.security.*;

/** Count-only entry point. Instrumented binaries are never included in performance rankings. */
public final class StlBenchmarkProbeMain {
    private StlBenchmarkProbeMain() {}
    public static void main(String[] args)throws Exception {
        if(args.length<2||List.of(args).contains("--help")) {
            System.out.println("Usage: StlBenchmarkProbeMain <frozen-project-root> <new-output> [size=32] [rounds=2] [seed=23] [g++]\nCount-only four-build correctness/allocation report. No timings or rankings.\nOptional -Dminic.benchmark.revision labels the compiled compiler; -Dminic.benchmark.supervisorSource overrides the tool source location.");return;
        }
        Path root=Path.of(args[0]).toAbsolutePath().normalize(),out=Path.of(args[1]).toAbsolutePath().normalize();
        int size=args.length>2?Integer.parseInt(args[2]):32,rounds=args.length>3?Integer.parseInt(args[3]):2,seed=args.length>4?Integer.parseInt(args[4]):23;
        String gxx=args.length>5?args[5]:CppDifferentialHarness.referenceCompiler(System.getenv());
        for(var w:StlBenchmarkWorkloads.workloads())StlBenchmarkProbes.input(w,size,rounds,seed);
        Files.createDirectories(out.getParent());Files.createDirectory(out);
        Map<String,Object> report=new LinkedHashMap<>();List<Object> builds=new ArrayList<>();report.put("schemaVersion",1);report.put("purpose","instrumented counts only; not a timing result");report.put("compilerRevision",System.getProperty("minic.benchmark.revision","unspecified"));report.put("builds",builds);report.put("size",size);report.put("rounds",rounds);report.put("seed",seed);
        int failures=0;
        try {
            String exactSource=StlBenchmarkProbes.source(root);Path source=out.resolve("probe.cpp");Files.writeString(source,exactSource);report.put("sourceSha256",sha(source));
            var instrumentation=StlBenchmarkProbes.instrumentOwnHeaders(root,out.resolve("instrumented-project"));report.put("instrumentation",instrumentation.strategy());report.put("headers",instrumentation.headers().stream().map(h->Map.of("path",h.path(),"originalSha256",h.originalSha256(),"instrumentedSha256",h.instrumentedSha256())).toList());
            Path observerSource=Path.of(System.getProperty("minic.benchmark.supervisorSource",root.resolve("benchmarks/tools/windows-supervisor.cpp").toString()));
            Path observer=WindowsBenchmarkProcess.buildSupervisor(gxx,observerSource,out.resolve("observer"),Duration.ofSeconds(90));report.put("observerSourceSha256",sha(observerSource));report.put("observerArtifactSha256",sha(observer));
            var version=BoundedProcess.run(List.of(gxx,"--version"),out,"",Duration.ofSeconds(20),65536);report.put("gxxVersion",version.stdout());
            for(String id:List.of("minic-baseline","minic-optimized","gxx-own","gxx-host")) {
                Map<String,Object> build=new LinkedHashMap<>();builds.add(build);build.put("id",id);Path dir=out.resolve(id);Files.createDirectory(dir);
                Path executable=dir.resolve("probe.exe");List<String> command;
                if(id.startsWith("minic")){
                    String cp=Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))).map(path->Path.of(path).toAbsolutePath().normalize().toString()).collect(java.util.stream.Collectors.joining(System.getProperty("path.separator")));
                    command=List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Xmx384m","-Dfile.encoding=UTF-8","-cp",cp,"minic.benchmark.StlBenchmarkMain","--compile-minic",source.toString(),id.equals("minic-baseline")?"BASELINE":"OPTIMIZED",instrumentation.projectRoot().toString());
                } else if(id.equals("gxx-own")) {
                    Path shims=CppOwnLibraryReference.prepareHeaders(dir.resolve("headers"),instrumentation.projectRoot().resolve("lib/cpp"));command=CppOwnLibraryReference.compileCommand(gxx,dir,shims,source,executable);
                } else {
                    var flags=new ArrayList<>(List.of(gxx));flags.addAll(CppDifferentialHarness.referenceFlags(dir));flags.addAll(List.of(source.toString(),"-o",executable.toString()));command=List.copyOf(flags);
                }
                build.put("command",command);Files.writeString(dir.resolve("command.json"),NativeBenchmarkReport.encode(command));
                var compiled=BoundedProcess.run(command,root,"",Duration.ofSeconds(180),1048576);Files.writeString(dir.resolve("compile-stdout.txt"),compiled.stdout());Files.writeString(dir.resolve("compile-stderr.txt"),compiled.stderr());
                if(compiled.timedOut()||compiled.outputExceeded()||compiled.exitCode()!=0){build.put("status","COMPILE_FAILED");build.put("exitCode",compiled.exitCode());failures++;continue;}
                if(id.startsWith("minic")){var result=NativeBenchmarkSupport.compilation(compiled.stdout());Files.copy(result.artifact(),executable);build.put("optimizationLevel",result.level().name());build.put("passNames",result.passNames());}
                build.put("artifactSha256",sha(executable));build.put("allocationDomain",id.equals("gxx-host")?"global new/delete":"instrumented own memory.mh backing malloc/free");
                List<Object> samples=new ArrayList<>();build.put("workloads",samples);boolean passed=true;
                for(var workload:StlBenchmarkWorkloads.workloads()) {
                    String input=StlBenchmarkProbes.input(workload,size,rounds,seed);var expected=StlBenchmarkWorkloads.input(workload,size,rounds,seed);Map<String,Object> sample=new LinkedHashMap<>();samples.add(sample);sample.put("id",workload.id());sample.put("operationDefinition",StlBenchmarkWorkloads.operationDescription(workload));sample.put("input",input);
                    var result=WindowsBenchmarkProcess.run(observer,executable,dir.resolve(workload.id()),input,Duration.ofSeconds(30),1048576);
                    try {result.requireSuccess();var verified=StlBenchmarkProbes.validate(workload,expected,result.stdout());sample.put("status","OK");sample.put("rounds",verified.stream().map(value->Map.of("round",value.round(),"hash",Long.toUnsignedString(value.hash()),"counters",value.counters())).toList());}
                    catch(RuntimeException failure){sample.put("status","FAILED");sample.put("error",failure.toString());passed=false;failures++;}
                }
                build.put("status",passed?"OK":"FAILED");System.out.println(id+": "+build.get("status"));
            }
        } finally {Files.writeString(out.resolve("counts.json"),NativeBenchmarkReport.encode(report)+"\n");}
        if(failures>0)throw new IllegalStateException("Count validation failures: "+failures+"; see "+out.resolve("counts.json"));
    }
    private static String sha(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
