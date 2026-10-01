package minic.benchmark;

import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.link.Linker;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Four-build STL runner. Run only after functional acceptance, on an otherwise idle Windows host. */
public final class StlBenchmarkMain {
    private static final int OUTPUT_LIMIT=4_194_304;
    private StlBenchmarkMain() {}
    public static void main(String[] arguments)throws Exception {
        if(arguments.length==4&&arguments[0].equals("--compile-minic")){
            compileMiniC(Path.of(arguments[1]),OptimizationLevel.valueOf(arguments[2]),Path.of(arguments[3]));return;
        }
        if(List.of(arguments).contains("--help")){help();return;}
        Config config=Config.parse(arguments);
        if(!System.getProperty("os.name").startsWith("Windows"))throw new IllegalStateException("Windows x64 is required");
        Files.createDirectories(config.output.getParent());Files.createDirectory(config.output);
        var report=new StlBenchmarkReport();
        try {
            Path root=freeze(config,report);
            Path classes=compileFrozenCompiler(config,root,report);
            Path observer=WindowsBenchmarkProcess.buildSupervisor(config.gxx,root.resolve("benchmarks/tools/windows-supervisor.cpp"),config.output.resolve("observer"),config.compileTimeout);
            report.metadata.put("observerSourceSha256",StlBenchmarkSupport.hash(root.resolve("benchmarks/tools/windows-supervisor.cpp")));
            report.metadata.put("observerArtifactSha256",StlBenchmarkSupport.hash(observer));
            metadata(config,root,report);
            for(var workload:selected(config))benchmark(config,root,classes,observer,workload,report);
            report.complete=true;
        } catch(Exception|Error failure){report.errors.add(failure.toString());throw failure;}
        finally{report.write(config.output,config.minimumMillis*1_000_000L);System.out.println("STL report: "+config.output.resolve("report.json"));}
    }
    private static Path freeze(Config config,StlBenchmarkReport report)throws Exception {
        String revision=command(config,List.of("git","rev-parse","--verify",config.revision+"^{commit}"),config.repository,"revision").strip();
        if(!revision.matches("[0-9a-f]{40,64}"))throw new IllegalArgumentException("Not a full committed revision: "+revision);
        report.metadata.put("revision",revision);report.metadata.put("repository",config.repository.toString());
        Path zip=config.output.resolve("source.zip"),root=config.output.resolve("source");
        command(config,List.of("git","archive","--format=zip","--output="+zip,revision),config.repository,"archive");
        StlBenchmarkSupport.extract(zip,root);
        report.metadata.put("archiveSha256",StlBenchmarkSupport.hash(zip));
        report.metadata.put("sourceSnapshot",root.toString());
        report.metadata.put("sourceManifest",StlBenchmarkSupport.manifest(root));
        report.metadata.put("loadedToolClassSha256",StlBenchmarkSupport.loadedToolHashes());
        // No workspace headers or source files are consumed after this point.
        return root;
    }
    private static Path compileFrozenCompiler(Config config,Path root,StlBenchmarkReport report)throws Exception {
        Path output=config.output.resolve("compiler");Files.createDirectories(output);
        List<Path> source;
        try(var files=Files.walk(root.resolve("src/main/java/minic"))){source=files.filter(p->p.toString().endsWith(".java"))
                .filter(p->!p.toString().replace('\\','/').contains("/minic/ui/")).sorted().toList();}
        if(source.isEmpty())throw new IllegalStateException("Missing frozen compiler sources");
        Path args=output.resolve("sources.txt");Files.write(args,source.stream().map(p->"\""+p.toAbsolutePath().toString().replace('\\','/')+"\"").toList());
        Path classes=output.resolve("classes");Files.createDirectories(classes);
        command(config,List.of(Path.of(System.getProperty("java.home"),"bin","javac.exe").toString(),"-J-Xmx512m","--release","21","-encoding","UTF-8","-d",classes.toString(),"@"+args),root,"compiler-build");
        report.metadata.put("compilerClassManifest",StlBenchmarkSupport.manifest(classes));return classes;
    }
    private static void metadata(Config config,Path root,StlBenchmarkReport report)throws Exception {
        var m=report.metadata;m.put("startedAt",Instant.now().toString());m.put("scope","native Windows x64 C++17 algorithm profile; four distinct builds, no debug timing");
        m.put("gxxPath",config.gxx);m.put("gxxVersion",command(config,List.of(config.gxx,"--version"),root,"gxx-version").strip());
        m.put("gxxTarget",command(config,List.of(config.gxx,"-dumpmachine"),root,"gxx-target").strip());
        m.put("java",System.getProperty("java.runtime.version"));m.put("os",System.getProperty("os.name")+" "+System.getProperty("os.version")+" "+System.getProperty("os.arch"));
        m.put("cpu",System.getenv().getOrDefault("PROCESSOR_IDENTIFIER","unknown"));m.put("processors",Runtime.getRuntime().availableProcessors());
        Path reg=Path.of(System.getenv().getOrDefault("SystemRoot","C:/Windows"),"System32","reg.exe");
        m.put("cpuRegistry",command(config,List.of(reg.toString(),"query","HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0","/v","ProcessorNameString"),root,"cpu-identity").strip());
        m.put("hostNote",config.hostNote);m.put("hostControl","Caller must provide an idle fixed host; this tool does not stop other processes or change power policy");
        m.put("wallClock","QPC from immediately before ResumeThread to target exit/timeout observation; includes loader, input, workload and output; not isolated kernel timing");
        m.put("cpuClock","GetProcessTimes user/kernel time, converted from 100 ns units; target process only");
        m.put("memory","Job Object PeakProcessMemoryUsed: highest per-process peak committed bytes in this sample job (observer excluded); not RSS or allocator live bytes. Benchmark workloads do not spawn subprocesses.");
        m.put("compileTiming","One descriptive compile per artifact; process wall includes startup, and MiniC pipeline time excludes JVM startup. No compile-time distribution is claimed.");
        m.put("observerWallClock","Java observer process startup and wait; separate from target timing");
        m.put("memoryReference","https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_extended_limit_information");
        m.put("operationUnit","logical-input-element");m.put("order","Seeded four-row Williams design; every position and directed adjacent pair once per four repetitions");
        m.put("seed",config.seed);m.put("repetitions",config.repetitions);m.put("warmups",config.warmups);m.put("initialRounds",config.rounds);
        m.put("calibrationSteps",config.calibrationSteps);m.put("minimumSampleMillis",config.minimumMillis);m.put("runTimeoutMillis",config.runTimeout.toMillis());m.put("compileTimeoutMillis",config.compileTimeout.toMillis());
        m.put("outputLimitPerStreamBytes",OUTPUT_LIMIT);m.put("instrumentation","none in timing builds; operation-count probes must be reported separately");
        Path libraryVersion=config.output.resolve("setup/benchmarks-library-version.cpp");Files.writeString(libraryVersion,"#include <bits/c++config.h>\n");
        String macros=command(config,List.of(config.gxx,"-std=c++17","-dM","-E",libraryVersion.toString()),root,"libstdcxx-macros");
        m.put("libstdcxxIdentity",macros.lines().filter(s->s.matches("#define (__GLIBCXX__|_GLIBCXX_RELEASE|__GNUC__|__GNUC_MINOR__|__GNUC_PATCHLEVEL__) .*" )).toList());
    }
    private static List<StlBenchmarkWorkloads.Workload> selected(Config config) {
        var all=StlBenchmarkWorkloads.workloads();
        if(config.workloads.isEmpty())return all;
        for(String id:config.workloads)if(all.stream().noneMatch(w->w.id().equals(id)))throw new IllegalArgumentException("Unknown workload "+id);
        return all.stream().filter(w->config.workloads.contains(w.id())).toList();
    }
    private static void benchmark(Config config,Path root,Path classes,Path observer,StlBenchmarkWorkloads.Workload workload,StlBenchmarkReport report)throws Exception {
        int size=config.size==null?workload.defaultSize():config.size;
        if(size>workload.maximumSize())throw new IllegalArgumentException("Workload size exceeds limit: "+workload.id());
        Path source=root.resolve(workload.sourcePath()).normalize();
        if(!source.startsWith(root)||!Files.isRegularFile(source))throw new IllegalArgumentException("Missing frozen workload: "+source);
        String sourceHash=StlBenchmarkSupport.hash(source);
        Map<String,Path> executables=new LinkedHashMap<>();
        for(String build:StlBenchmarkSupport.BUILDS){
            Path directory=config.output.resolve("builds").resolve(workload.id()).resolve(build);Files.createDirectories(directory);
            Path executable=directory.resolve("program.exe");var row=new LinkedHashMap<String,Object>();
            row.put("workload",workload.id());row.put("build",build);row.put("sourceSha256",sourceHash);row.put("sourcePath",source.toString());row.put("directory",directory.toString());row.put("status","compiling");report.builds.add(row);
            List<String> command;
            if(build.startsWith("minic")){
                OptimizationLevel level=build.equals("minic-opt")?OptimizationLevel.OPTIMIZED:OptimizationLevel.BASELINE;
                command=javaCommand(root,classes,"--compile-minic",source.toString(),level.name(),root.toString());
            }else if(build.equals("gxx-own")){
                Path headers=CppOwnLibraryReference.prepareHeaders(directory.resolve("headers"),root.resolve("lib/cpp"));
                command=CppOwnLibraryReference.compileCommand(config.gxx,directory,headers,source,executable);
            }else{
                var c=new ArrayList<>(List.of(config.gxx));c.addAll(CppDifferentialHarness.referenceFlags(directory));c.addAll(List.of(source.toString(),"-o",executable.toString()));command=List.copyOf(c);
            }
            if(Files.isRegularFile(directory.resolve("reference-header-compat.h")))row.put("referenceCompatSha256",StlBenchmarkSupport.hash(directory.resolve("reference-header-compat.h")));
            if(Files.isDirectory(directory.resolve("headers")))row.put("shimManifest",StlBenchmarkSupport.manifest(directory.resolve("headers")));
            row.put("command",command);Files.writeString(directory.resolve("command.json"),NativeBenchmarkReport.encode(command)+"\n");
            var result=NativeBenchmarkSupport.run(command,directory,"",config.compileTimeout,OUTPUT_LIMIT);
            Files.writeString(directory.resolve("stdout.txt"),result.stdout());Files.writeString(directory.resolve("stderr.txt"),result.stderr());
            row.put("compileProcessWallNanos",result.wallNanos());row.put("compileExitCode",result.exitCode());row.put("compileTimedOut",result.timedOut());row.put("compileOutputTruncated",result.outputTruncated());
            try{result.requireSuccess();}catch(RuntimeException e){row.put("status","failed");throw e;}
            if(build.startsWith("minic")){
                var compiled=NativeBenchmarkSupport.compilation(result.stdout());
                OptimizationLevel expected=build.equals("minic-opt")?OptimizationLevel.OPTIMIZED:OptimizationLevel.BASELINE;
                if(compiled.level()!=expected)throw new IllegalStateException("Wrong MiniC optimization mode");
                Files.copy(compiled.artifact(),executable);row.put("optimizationLevel",compiled.level().name());row.put("passNames",compiled.passNames());row.put("compilerPipelineNanos",compiled.compilerPipelineNanos());
            }
            if(!Files.isRegularFile(executable))throw new IllegalStateException("Missing compiled executable "+executable);
            row.put("artifactPath",executable.toString());row.put("artifactSha256",StlBenchmarkSupport.hash(executable));row.put("artifactBytes",Files.size(executable));row.put("status","compiled");executables.put(build,executable);
            report.write(config.output,config.minimumMillis*1_000_000L);
        }
        int rounds=config.rounds;
        var input=StlBenchmarkWorkloads.input(workload,size,rounds,config.seed);
        runRound(config,observer,executables,workload,input,size,rounds,"preflight",0,report);
        if(config.minimumMillis>0){
            for(int step=0;step<=config.calibrationSteps;step++){
                long shortest=runRound(config,observer,executables,workload,input,size,rounds,"calibration",step,report);
                if(shortest>=config.minimumMillis*1_000_000L||step==config.calibrationSteps)break;
                int next=Math.multiplyExact(rounds,2);
                if(next>10000||(long)size*next>200_000_000L)break;
                rounds=next;input=StlBenchmarkWorkloads.input(workload,size,rounds,config.seed);
            }
        }
        for(int i=0;i<config.warmups;i++)runRound(config,observer,executables,workload,input,size,rounds,"warmup",i,report);
        for(int i=0;i<config.repetitions;i++)runRound(config,observer,executables,workload,input,size,rounds,"measurement",i,report);
        report.write(config.output,config.minimumMillis*1_000_000L);
        System.out.println(workload.id()+": correctness verified; size="+size+" rounds="+rounds);
    }
    private static long runRound(Config config,Path observer,Map<String,Path> executables,StlBenchmarkWorkloads.Workload workload,
                                 StlBenchmarkWorkloads.Input input,int size,int rounds,String phase,int repetition,StlBenchmarkReport report)throws Exception {
        long shortest=Long.MAX_VALUE;int index=0;
        for(String build:StlBenchmarkSupport.order(repetition,config.seed)){
            Path directory=config.output.resolve("runs").resolve(workload.id()).resolve(phase+"-"+repetition).resolve(index+"-"+build);
            var result=WindowsBenchmarkProcess.run(observer,executables.get(build),directory,input.stdin(),config.runTimeout,OUTPUT_LIMIT);
            String actual=result.stdout().replace("\r\n","\n"),expected=input.expectedStdout().replace("\r\n","\n");
            Files.writeString(directory.resolve("expected-stdout.txt"),expected);
            boolean match=actual.equals(expected)&&result.stderr().isEmpty();
            report.samples.add(new StlBenchmarkReport.Sample(workload.id(),build,phase,repetition,index++,size,rounds,config.seed,input.operationCount(),
                    StlBenchmarkSupport.hash(input.stdin()),StlBenchmarkSupport.hash(expected),StlBenchmarkSupport.hash(actual),result.status().name(),match,
                    result.wallNanos(),result.userCpuNanos(),result.kernelCpuNanos(),result.peakCommitBytes(),result.observerWallNanos(),result.exitCode(),directory.toString(),
                    StlBenchmarkSupport.hash(result.stdoutPath()),StlBenchmarkSupport.hash(result.stderrPath())));
            report.write(config.output,config.minimumMillis*1_000_000L);result.requireSuccess();
            if(!match)throw new IllegalStateException("Independent oracle mismatch: "+directory);
            shortest=Math.min(shortest,result.wallNanos());
        }return shortest;
    }
    private static List<String> javaCommand(Path root,Path classes,String... args){
        String cp=classes+java.io.File.pathSeparator+root.resolve("src/main/resources")+java.io.File.pathSeparator+StlBenchmarkSupport.absoluteClasspath();
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Xmx512m","-Dfile.encoding=UTF-8","-Duser.language=en","-Dminic.project.root="+root,"-cp",cp,StlBenchmarkMain.class.getName()));
        command.addAll(List.of(args));return List.copyOf(command);
    }
    private static void compileMiniC(Path source,OptimizationLevel level,Path root)throws Exception {
        System.setProperty("minic.project.root",root.toAbsolutePath().toString());
        long start=System.nanoTime();var api=new CompilerApi(new SourceFile(source.toAbsolutePath().toString(),Files.readString(source)),LanguageMode.CPP17_ALGORITHM,level);
        Linker linker=api.stages().stream().filter(Linker.class::isInstance).map(Linker.class::cast).findFirst().orElseThrow();api.runThrough(linker);
        if(!linker.succeeded())throw new IllegalStateException("MiniC compile failed: "+api.stages().stream().flatMap(s->s.errors().stream()).toList());
        long nanos=System.nanoTime()-start;var assembler=api.stages().stream().filter(Assembler.class::isInstance).map(Assembler.class::cast).findFirst().orElseThrow();
        System.out.println("compile_ns="+nanos);System.out.println("artifact="+linker.result().executableArtifactOptional().orElseThrow().path().toAbsolutePath());
        System.out.println("optimization_level="+assembler.optimizationLevel());var passes=assembler.optimizationResult().passNames();System.out.println("pass_count="+passes.size());
        for(int i=0;i<passes.size();i++)System.out.println("pass_"+i+"="+Base64.getEncoder().encodeToString(passes.get(i).getBytes(StandardCharsets.UTF_8)));
    }
    private static String command(Config config,List<String> command,Path directory,String name)throws Exception {
        Path logs=config.output.resolve("setup");Files.createDirectories(logs);Files.writeString(logs.resolve(name+"-command.json"),NativeBenchmarkReport.encode(command)+"\n");
        var result=NativeBenchmarkSupport.run(command,directory,"",config.compileTimeout,OUTPUT_LIMIT);
        Files.writeString(logs.resolve(name+"-stdout.txt"),result.stdout());Files.writeString(logs.resolve(name+"-stderr.txt"),result.stderr());result.requireSuccess();return result.stdout();
    }
    private static void help(){System.out.println("""
        Four-build Windows native STL benchmark; invoke only after functional acceptance.
        --repository=<git repository> --revision=HEAD --gxx=C:/mingw64/bin/g++.exe
        --output=<new directory> --workloads=vector-sort,deque --size=4096
        --rounds=1 --seed=1729 --warmups=2 --repetitions=12
        --minimum-ms=250 --calibration-steps=8 --run-timeout-seconds=120 --compile-timeout-seconds=180
        --host-note=<hardware/power/background conditions>
        A committed git archive and freshly compiled non-UI compiler classes supply all product inputs.
        Four builds: gxx-stl, gxx-own, minic-opt, minic-base. No debug timings.
        Raw samples, commands, hashes, expected/actual output, median/MAD, target user/kernel CPU
        and Job Object peak committed bytes are retained. Memory is not RSS or allocator live bytes.
        Timing includes target startup/input/output. Counter-instrumented builds are a separate probe suite.
        """);}
    record Config(Path repository,String revision,String gxx,Path output,Set<String> workloads,Integer size,int rounds,int seed,
                  int repetitions,int warmups,int calibrationSteps,long minimumMillis,Duration runTimeout,Duration compileTimeout,String hostNote){
        static Config parse(String[] args){
            var v=new LinkedHashMap<String,String>();for(String arg:args){int at=arg.indexOf('=');if(!arg.startsWith("--")||at<3||v.put(arg.substring(2,at),arg.substring(at+1))!=null)throw new IllegalArgumentException("Invalid/duplicate option "+arg);}
            Path repository=Path.of(v.getOrDefault("repository",".")).toAbsolutePath().normalize();v.remove("repository");
            String revision=v.getOrDefault("revision","HEAD");v.remove("revision");if(revision.isBlank()||revision.startsWith("-"))throw new IllegalArgumentException("Invalid revision");
            String gxx=v.getOrDefault("gxx","C:/mingw64/bin/g++.exe");v.remove("gxx");
            Path output=Path.of(v.getOrDefault("output","build/stl-benchmark/"+Instant.now().toEpochMilli())).toAbsolutePath().normalize();v.remove("output");
            if(Files.exists(output))throw new IllegalArgumentException("Output must be a new directory: "+output);
            Set<String> workloads=new LinkedHashSet<>();String chosen=v.remove("workloads");if(chosen!=null)for(String id:chosen.split(",",-1)){if(id.isBlank()||!workloads.add(id))throw new IllegalArgumentException("Invalid workload selection");}
            Integer size=v.containsKey("size")?integer(v,"size",4096,1,262144):null;
            int rounds=integer(v,"rounds",1,1,10000),seed=integer(v,"seed",1729,0,Integer.MAX_VALUE),repetitions=integer(v,"repetitions",12,1,1000),warmups=integer(v,"warmups",2,0,100);
            int calibration=integer(v,"calibration-steps",8,0,14),minimum=integer(v,"minimum-ms",250,0,60000);
            Duration run=Duration.ofSeconds(integer(v,"run-timeout-seconds",120,1,3600)),compile=Duration.ofSeconds(integer(v,"compile-timeout-seconds",180,1,3600));
            String note=v.getOrDefault("host-note","unspecified; not a controlled-host performance claim");v.remove("host-note");
            if(!v.isEmpty())throw new IllegalArgumentException("Unknown options "+v.keySet());
            if((long)(size==null?4096:size)*rounds>200_000_000L)throw new IllegalArgumentException("Input exceeds workload budget");
            return new Config(repository,revision,gxx,output,Collections.unmodifiableSet(workloads),size,rounds,seed,repetitions,warmups,calibration,minimum,run,compile,note);
        }
        private static int integer(Map<String,String> values,String key,int defaultValue,int min,int max){String raw=values.remove(key);int result=raw==null?defaultValue:Integer.parseInt(raw);if(result<min||result>max)throw new IllegalArgumentException("Invalid --"+key);return result;}
    }
}
