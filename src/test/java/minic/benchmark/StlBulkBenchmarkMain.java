package minic.benchmark;

import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Paired old/new product experiment; smoke is the default and produces no performance summaries. */
public final class StlBulkBenchmarkMain {
    static final String BASELINE="b6464790fd3748edfe2d02981f0f6a421a133d6b";
    static final int LIMIT=4_194_304;
    private final Config config;
    private final Map<String,Object> metadata=new LinkedHashMap<>();
    private final List<Map<String,Object>> builds=new ArrayList<>(),samples=new ArrayList<>();
    private final List<String> errors=new ArrayList<>();
    private boolean complete;
    private StlBulkBenchmarkMain(Config config){this.config=config;}
    public static void main(String[] args)throws Exception {
        if(List.of(args).contains("--help")){System.out.println("""
            Paired bulk-copy Windows experiment. Default: correctness smoke only, no timing summary.
            --repository=. --baseline=b646479 --candidate=HEAD --workload-revision=<committed source SHA>
            --output=<new directory> --gxx=C:/mingw64/bin/g++.exe --size=4096 --rounds=2 --iterations=8 --seed=1729
            --measure=false --warmups=2 --repetitions=12 --minimum-ms=250 --calibration-steps=8
            --run-timeout-seconds=120 --compile-timeout-seconds=240 --host-note=<conditions>
            All four modes run with identical sources/input across G++ STL/G++ own/MiniC OPT/MiniC BASE.
            --smoke-source=<file> permits uncommitted workload ONLY with measure=false; hashes are retained.
            Product revisions are always committed archives. Formal sampling requires workload-revision.
            """);return;}
        var runner=new StlBulkBenchmarkMain(Config.parse(args));
        Files.createDirectories(runner.config.output.getParent());Files.createDirectory(runner.config.output);
        try{runner.run();runner.complete=true;}catch(Exception|Error failure){runner.errors.add(failure.toString());throw failure;}
        finally{runner.write();System.out.println("Bulk report: "+runner.config.output.resolve("report.json"));}
    }
    private void run()throws Exception {
        metadata.put("startedAt",Instant.now().toString());metadata.put("measurementEnabled",config.measure);
        metadata.put("baselineRevision",resolve(config.baseline,"baseline"));metadata.put("candidateRevision",resolve(config.candidate,"candidate"));
        metadata.put("seed",config.seed);metadata.put("size",config.size);metadata.put("rounds",config.rounds);metadata.put("initialIterations",config.iterations);
        metadata.put("warmups",config.measure?config.warmups:0);metadata.put("repetitions",config.measure?config.repetitions:0);
        metadata.put("minimumMillis",config.minimumMillis);metadata.put("calibrationSteps",config.calibrationSteps);
        metadata.put("hostNote",config.hostNote);metadata.put("os",System.getProperty("os.name")+" "+System.getProperty("os.version"));
        metadata.put("java",System.getProperty("java.runtime.version"));metadata.put("cpu",System.getenv().getOrDefault("PROCESSOR_IDENTIFIER","unknown"));
        metadata.put("gxxPath",config.gxx);metadata.put("gxxVersion",command(List.of(config.gxx,"--version"),config.repository,"gxx-version"));
        metadata.put("order","Seeded Williams four-build order; baseline/candidate adjacent, pair direction alternates by round and build position.");
        metadata.put("scope","Process time includes loader, setup, input/output, transfer, ordered full-sequence observation through a volatile function pointer, and erased-tail recreation. No control-time subtraction.");
        metadata.put("units","transferredElements counts intended assignments/shifts; observedElements counts hashed data. Neither is a count of generated machine operations.");
        metadata.put("metrics","Target QPC wall and GetProcessTimes user/kernel CPU; Job Object PeakProcessMemoryUsed is peak committed bytes, not RSS. Observer excluded. No child workloads.");
        metadata.put("runTimeoutMillis",config.runTimeout.toMillis());metadata.put("compileTimeoutMillis",config.compileTimeout.toMillis());metadata.put("outputLimitPerStream",LIMIT);
        metadata.put("toolClassHashes",toolHashes());
        var roots=new LinkedHashMap<String,Path>();var classes=new LinkedHashMap<String,Path>();
        for(String product:List.of("baseline","candidate")){
            Path root=archive((String)metadata.get(product+"Revision"),product);roots.put(product,root);
            classes.put(product,compileCompiler(root,product));
        }
        Path source=config.output.resolve("bulk-copy.cpp");
        if(config.smokeSource!=null){Files.copy(config.smokeSource,source);metadata.put("workloadOrigin","uncommitted smoke-only file");metadata.put("smokeSourcePath",config.smokeSource.toString());}
        else{String revision=resolve(config.workloadRevision,"workload");metadata.put("workloadRevision",revision);Path root=archive(revision,"workload");Files.copy(root.resolve(StlBulkCopyWorkload.SOURCE),source);metadata.put("workloadOrigin","committed archive");}
        metadata.put("sourceSha256",StlBenchmarkSupport.hash(source));
        Path observer=WindowsBenchmarkProcess.buildSupervisor(config.gxx,roots.get("candidate").resolve("benchmarks/tools/windows-supervisor.cpp"),config.output.resolve("observer"),config.compileTimeout);
        metadata.put("observerSha256",StlBenchmarkSupport.hash(observer));
        metadata.put("observerSourceSha256",StlBenchmarkSupport.hash(roots.get("candidate").resolve("benchmarks/tools/windows-supervisor.cpp")));
        var executables=new LinkedHashMap<String,Path>();
        for(String product:roots.keySet())for(String build:StlBenchmarkSupport.BUILDS)executables.put(product+"/"+build,compileArtifact(product,build,roots.get(product),classes.get(product),source));
        for(var mode:StlBulkCopyWorkload.Mode.values()){
            int iterations=config.iterations;runRound(observer,executables,mode,iterations,"preflight",0);
            if(config.measure){
                for(int step=0;step<=config.calibrationSteps;step++){
                    long shortest=runRound(observer,executables,mode,iterations,"calibration",step);
                    if(shortest>=config.minimumMillis*1_000_000L||step==config.calibrationSteps||iterations>5000||(long)config.size*config.rounds*iterations*2>200_000_000L)break;
                    iterations*=2;
                }
                for(int i=0;i<config.warmups;i++)runRound(observer,executables,mode,iterations,"warmup",i);
                for(int i=0;i<config.repetitions;i++)runRound(observer,executables,mode,iterations,"measurement",i);
            }
            System.out.println(mode+": 8 artifacts match oracle; iterations="+iterations+" measure="+config.measure);
        }
    }
    private String resolve(String revision,String label)throws Exception {
        String result=command(List.of("git","rev-parse","--verify",revision+"^{commit}"),config.repository,label+"-revision").strip();
        if(!result.matches("[0-9a-f]{40,64}"))throw new IllegalStateException("Invalid committed revision "+result);return result;
    }
    private Path archive(String revision,String label)throws Exception {
        Path directory=config.output.resolve(label);Files.createDirectories(directory);Path zip=directory.resolve("source.zip"),root=directory.resolve("source");
        command(List.of("git","archive","--format=zip","--output="+zip,revision),config.repository,label+"-archive");
        StlBenchmarkSupport.extract(zip,root);metadata.put(label+"ArchiveSha256",StlBenchmarkSupport.hash(zip));metadata.put(label+"SourceManifest",StlBenchmarkSupport.manifest(root));return root;
    }
    private Path compileCompiler(Path root,String product)throws Exception {
        Path output=config.output.resolve(product).resolve("compiler");Files.createDirectories(output);Path classes=output.resolve("classes");Files.createDirectory(classes);
        List<String> sources;try(var files=Files.walk(root.resolve("src/main/java/minic"))){sources=files.filter(p->p.toString().endsWith(".java")&&!p.toString().replace('\\','/').contains("/minic/ui/")).sorted().map(p->"\""+p.toString().replace('\\','/')+"\"").toList();}
        Path args=output.resolve("sources.txt");Files.write(args,sources);
        command(List.of(javaTool("javac"),"-J-Xmx512m","--release","21","-encoding","UTF-8","-d",classes.toString(),"@"+args),root,product+"-javac");
        metadata.put(product+"CompilerManifest",StlBenchmarkSupport.manifest(classes));return classes;
    }
    private Path compileArtifact(String product,String build,Path root,Path classes,Path source)throws Exception {
        Path directory=config.output.resolve("builds").resolve(product).resolve(build);Files.createDirectories(directory);Path executable=directory.resolve("program.exe");
        var row=new LinkedHashMap<String,Object>();row.put("product",product);row.put("build",build);row.put("sourceSha256",StlBenchmarkSupport.hash(source));row.put("status","compiling");builds.add(row);write();
        List<String> cmd;
        if(build.startsWith("minic")){
            String cp=classes+java.io.File.pathSeparator+root.resolve("src/main/resources")+java.io.File.pathSeparator+StlBenchmarkSupport.absoluteClasspath();
            cmd=List.of(javaTool("java"),"-Xmx512m","-Dfile.encoding=UTF-8","-Duser.language=en","-Dminic.project.root="+root,"-cp",cp,StlBenchmarkMain.class.getName(),"--compile-minic",source.toString(),build.equals("minic-opt")?"OPTIMIZED":"BASELINE",root.toString());
        }else if(build.equals("gxx-own")){
            Path headers=CppOwnLibraryReference.prepareHeaders(directory.resolve("headers"),root.resolve("lib/cpp"));
            cmd=CppOwnLibraryReference.compileCommand(config.gxx,directory,headers,source,executable);row.put("shimManifest",StlBenchmarkSupport.manifest(headers));
        }else{var command=new ArrayList<>(List.of(config.gxx));command.addAll(CppDifferentialHarness.referenceFlags(directory));command.addAll(List.of(source.toString(),"-o",executable.toString()));cmd=List.copyOf(command);}
        row.put("command",cmd);Files.writeString(directory.resolve("command.json"),NativeBenchmarkReport.encode(cmd));
        var result=NativeBenchmarkSupport.run(cmd,directory,"",config.compileTimeout,LIMIT);
        Files.writeString(directory.resolve("stdout.txt"),result.stdout());Files.writeString(directory.resolve("stderr.txt"),result.stderr());
        row.put("compileWallNanos",result.wallNanos());row.put("exitCode",result.exitCode());result.requireSuccess();
        if(build.startsWith("minic")){var compiled=NativeBenchmarkSupport.compilation(result.stdout());String expected=build.equals("minic-opt")?"OPTIMIZED":"BASELINE";
            if(!compiled.level().name().equals(expected))throw new IllegalStateException("Wrong optimization level");
            Files.copy(compiled.artifact(),executable);row.put("optimizationLevel",expected);row.put("passes",compiled.passNames());row.put("compilerPipelineNanos",compiled.compilerPipelineNanos());}
        row.put("artifactSha256",StlBenchmarkSupport.hash(executable));row.put("artifactPath",executable.toString());row.put("status","compiled");
        if(Files.exists(directory.resolve("reference-header-compat.h")))row.put("compatSha256",StlBenchmarkSupport.hash(directory.resolve("reference-header-compat.h")));write();return executable;
    }
    static List<String> pairedOrder(int repetition,int seed){
        var result=new ArrayList<String>();int i=0;
        for(String build:StlBenchmarkSupport.order(repetition,seed)){
            var pair=((repetition+i++)&1)==0?List.of("baseline","candidate"):List.of("candidate","baseline");
            for(String product:pair)result.add(product+"/"+build);
        }return List.copyOf(result);
    }
    private long runRound(Path observer,Map<String,Path> executables,StlBulkCopyWorkload.Mode mode,int iterations,String phase,int repetition)throws Exception {
        var input=StlBulkCopyWorkload.input(mode,config.size,config.rounds,iterations,config.seed);long shortest=Long.MAX_VALUE;int order=0;
        for(String key:pairedOrder(repetition,config.seed)){
            Path directory=config.output.resolve("runs").resolve(mode.name()).resolve(phase+"-"+repetition).resolve(order+"-"+key.replace('/','-'));
            var result=WindowsBenchmarkProcess.run(observer,executables.get(key),directory,input.stdin(),config.runTimeout,LIMIT);
            String actual=result.stdout().replace("\r\n","\n");boolean correct=actual.equals(input.expectedStdout())&&result.stderr().isEmpty();
            Files.writeString(directory.resolve("expected-stdout.txt"),input.expectedStdout());
            var row=new LinkedHashMap<String,Object>();row.put("mode",mode.name());row.put("product",key.split("/")[0]);row.put("build",key.split("/")[1]);
            row.put("phase",phase);row.put("repetition",repetition);row.put("order",order++);row.put("size",config.size);row.put("rounds",config.rounds);row.put("iterations",iterations);row.put("seed",config.seed);
            row.put("transferredElements",input.transferredElements());row.put("observedElements",input.observedElements());
            row.put("inputSha256",StlBenchmarkSupport.hash(input.stdin()));row.put("expectedSha256",StlBenchmarkSupport.hash(input.expectedStdout()));row.put("stdoutSha256",StlBenchmarkSupport.hash(result.stdoutPath()));row.put("stderrSha256",StlBenchmarkSupport.hash(result.stderrPath()));
            row.put("status",result.status().name());row.put("exitCode",result.exitCode());row.put("correct",correct);row.put("wallNanos",result.wallNanos());row.put("userCpuNanos",result.userCpuNanos());row.put("kernelCpuNanos",result.kernelCpuNanos());row.put("peakCommitBytes",result.peakCommitBytes());row.put("observerWallNanos",result.observerWallNanos());row.put("directory",directory.toString());samples.add(row);write();
            result.requireSuccess();if(!correct)throw new IllegalStateException("Oracle mismatch: "+directory);shortest=Math.min(shortest,result.wallNanos());
        }return shortest;
    }
    private void write()throws Exception {
        var report=new LinkedHashMap<String,Object>();report.put("schemaVersion",1);report.put("complete",complete);report.put("metadata",metadata);report.put("builds",builds);report.put("samples",samples);report.put("errors",errors);
        var summaries=new ArrayList<Map<String,Object>>();
        if(config.measure)for(var mode:StlBulkCopyWorkload.Mode.values())for(String product:List.of("baseline","candidate"))for(String build:StlBenchmarkSupport.BUILDS){
            var selected=samples.stream().filter(s->s.get("phase").equals("measurement")&&s.get("mode").equals(mode.name())&&s.get("product").equals(product)&&s.get("build").equals(build)&&Boolean.TRUE.equals(s.get("correct"))&&s.get("status").equals("COMPLETED")&&((Number)s.get("exitCode")).longValue()==0).toList();
            if(selected.isEmpty())continue;var summary=new LinkedHashMap<String,Object>();summary.put("mode",mode.name());summary.put("product",product);summary.put("build",build);summary.put("samples",selected.size());
            for(String metric:List.of("wallNanos","userCpuNanos","kernelCpuNanos","peakCommitBytes")){var values=selected.stream().map(s->(Number)s.get(metric)).toList();summary.put(metric+"Median",StlBenchmarkSupport.median(values));summary.put(metric+"Mad",StlBenchmarkSupport.mad(values));}
            summary.put("shortSample",selected.stream().anyMatch(s->((Number)s.get("wallNanos")).longValue()<config.minimumMillis*1_000_000L));
            summary.put("iterations",selected.getFirst().get("iterations"));summary.put("transferredElements",selected.getFirst().get("transferredElements"));summary.put("observedElements",selected.getFirst().get("observedElements"));summaries.add(summary);
        }
        report.put("summaries",summaries);Files.writeString(config.output.resolve("report.json"),NativeBenchmarkReport.encode(report)+"\n");
        List<String> columns=List.of("mode","product","build","phase","repetition","order","size","rounds","iterations","seed","transferredElements","observedElements","status","exitCode","correct","wallNanos","userCpuNanos","kernelCpuNanos","peakCommitBytes","observerWallNanos","inputSha256","expectedSha256","stdoutSha256","stderrSha256","directory");
        var csv=new StringBuilder(String.join(",",columns)).append('\n');for(var sample:samples){for(int i=0;i<columns.size();i++){if(i>0)csv.append(',');csv.append(NativeBenchmarkSupport.csv(String.valueOf(sample.get(columns.get(i)))));}csv.append('\n');}Files.writeString(config.output.resolve("samples.csv"),csv);
    }
    private String command(List<String> command,Path directory,String name)throws Exception {
        Path logs=config.output.resolve("setup");Files.createDirectories(logs);Files.writeString(logs.resolve(name+"-command.json"),NativeBenchmarkReport.encode(command));
        var result=NativeBenchmarkSupport.run(command,directory,"",config.compileTimeout,LIMIT);Files.writeString(logs.resolve(name+"-stdout.txt"),result.stdout());Files.writeString(logs.resolve(name+"-stderr.txt"),result.stderr());result.requireSuccess();return result.stdout();
    }
    private static String javaTool(String name){return Path.of(System.getProperty("java.home"),"bin",name+".exe").toString();}
    private static Map<String,String> toolHashes()throws Exception {
        var result=new TreeMap<String,String>();var queue=new ArrayDeque<Class<?>>(List.of(StlBulkBenchmarkMain.class,StlBulkCopyWorkload.class,StlBenchmarkMain.class,StlBenchmarkSupport.class,NativeBenchmarkSupport.class,NativeBenchmarkReport.class,WindowsBenchmarkProcess.class,CppOwnLibraryReference.class,CppDifferentialHarness.class));
        while(!queue.isEmpty()){Class<?> type=queue.removeFirst();Collections.addAll(queue,type.getDeclaredClasses());try(var input=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){if(input==null)throw new IllegalStateException("Missing tool class "+type);result.put(type.getName(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}}return result;
    }
    record Config(Path repository,String baseline,String candidate,String workloadRevision,Path smokeSource,Path output,String gxx,int size,int rounds,int iterations,int seed,boolean measure,int warmups,int repetitions,int calibrationSteps,int minimumMillis,Duration runTimeout,Duration compileTimeout,String hostNote){
        static Config parse(String[] args){
            var v=new LinkedHashMap<String,String>();for(String arg:args){int at=arg.indexOf('=');if(!arg.startsWith("--")||at<3||v.put(arg.substring(2,at),arg.substring(at+1))!=null)throw new IllegalArgumentException("Invalid option "+arg);}
            Path repository=Path.of(take(v,"repository",".")).toAbsolutePath().normalize(),output=Path.of(take(v,"output","build/bulk-benchmark/run-"+Instant.now().toEpochMilli())).toAbsolutePath().normalize();
            if(Files.exists(output))throw new IllegalArgumentException("Output must be new");
            String baseline=take(v,"baseline",BASELINE),candidate=take(v,"candidate","HEAD"),workload=v.remove("workload-revision"),smoke=v.remove("smoke-source");
            for(String revision:List.of(baseline,candidate,workload==null?"HEAD":workload))if(revision.isBlank()||revision.startsWith("-"))throw new IllegalArgumentException("Invalid revision");
            String measureRaw=take(v,"measure","false");if(!Set.of("true","false").contains(measureRaw))throw new IllegalArgumentException("Invalid measure");boolean measure=Boolean.parseBoolean(measureRaw);
            if((workload==null)==(smoke==null)||measure&&smoke!=null)throw new IllegalArgumentException("Choose committed workload-revision, or smoke-source ONLY for nonmeasurement");
            String gxx=take(v,"gxx","C:/mingw64/bin/g++.exe"),note=take(v,"host-note","unspecified; not a controlled-host claim");
            int size=integer(v,"size",4096,1,262144),rounds=integer(v,"rounds",2,1,10000),iterations=integer(v,"iterations",8,1,10000),seed=integer(v,"seed",1729,0,Integer.MAX_VALUE);
            int warmups=integer(v,"warmups",2,0,100),repetitions=integer(v,"repetitions",12,1,1000),steps=integer(v,"calibration-steps",8,0,14),minimum=integer(v,"minimum-ms",250,0,60000);
            Duration run=Duration.ofSeconds(integer(v,"run-timeout-seconds",120,1,3600)),compile=Duration.ofSeconds(integer(v,"compile-timeout-seconds",240,1,3600));
            if(!v.isEmpty()||(long)size*rounds*iterations>200_000_000L)throw new IllegalArgumentException("Unknown options or excessive input "+v);
            return new Config(repository,baseline,candidate,workload,smoke==null?null:Path.of(smoke).toAbsolutePath().normalize(),output,gxx,size,rounds,iterations,seed,measure,warmups,repetitions,steps,minimum,run,compile,note);
        }
        private static String take(Map<String,String> v,String key,String fallback){String result=v.remove(key);return result==null?fallback:result;}
        private static int integer(Map<String,String> v,String key,int fallback,int min,int max){int result=Integer.parseInt(take(v,key,Integer.toString(fallback)));if(result<min||result>max)throw new IllegalArgumentException("Invalid "+key);return result;}
    }
}
