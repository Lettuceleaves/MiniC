package minic.benchmark;

import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Independent OJ scenario runner. Compilation, correctness, measurement and offline audit are separate invocations. */
public final class OjScenarioMain {
    private static final int LIMIT=4_194_304;
    private static final long CALIBRATION_NANOS=200_000_000L;
    private static final Duration COMPILE=Duration.ofSeconds(240),RUN=Duration.ofSeconds(120);
    private OjScenarioMain(){}
    public static void main(String[] args)throws Exception {
        if(List.of(args).contains("--help")){help();return;}
        var options=options(args);String stage=options.getOrDefault("stage","dry-run");
        if(stage.equals("audit")){audit(Path.of(required(options,"run")));return;}
        Path output=Path.of(required(options,"output")).toAbsolutePath().normalize();Files.createDirectories(output.getParent());Files.createDirectory(output);
        var selected=selected(options);
        if(stage.equals("dry-run")){plan(output,selected);return;}
        if(stage.equals("compile")){
            if(filtered(options))throw new IllegalArgumentException("compile creates all 32 artifacts; filtering belongs to validate/measure");
            compile(options,output);return;
        }
        if(!Set.of("validate","measure").contains(stage))throw new IllegalArgumentException("Unknown stage "+stage);
        Path cache=Path.of(required(options,"cache")).toAbsolutePath().normalize();var cached=auditCache(cache);
        int repetitions=Integer.parseInt(options.getOrDefault("repetitions","12")),warmups=Integer.parseInt(options.getOrDefault("warmups","2"));
        if(repetitions<6||repetitions%6!=0||warmups<1)throw new IllegalArgumentException("repetitions must be a positive multiple of 6; warmups >= 1");
        var state=new TreeMap<String,String>();state.put("schema","1");state.put("stage",stage);state.put("cache",cache.toString());
        state.put("cacheManifestSha256",StlBenchmarkSupport.hash(cache.resolve("cache-manifest.properties")));state.put("revision",cached.get("revision"));
        state.put("startedAt",Instant.now().toString());state.put("selected",String.join(",",selected.stream().map(OjScenarioWorkloads.Configuration::id).toList()));
        state.put("filtered",""+filtered(options));state.put("repetitions",""+repetitions);state.put("warmups",""+warmups);state.put("complete","false");
        state.put("hostNote",options.getOrDefault("host-note","unspecified; caller must establish an idle fixed host"));
        state.put("wallClock","QPC resume-to-exit process wall; includes loader, stdin, all algorithms and stdout; not isolated kernel timing");
        state.put("memory","Job Object peak committed bytes, not RSS or allocator live bytes");
        state.put("calibration","Common rounds doubled for all builds; fastest of OPT/own/system must reach 200ms; input shape and size fixed; each measured sample must still reach 100ms");
        state.put("order","All six permutations of the three measured builds; seeded names, balanced positions and directed within-repetition pairs");
        state.put("java",System.getProperty("java.runtime.version"));state.put("os",System.getProperty("os.name")+" "+System.getProperty("os.version")+" "+System.getProperty("os.arch"));
        state.put("cpu",System.getenv().getOrDefault("PROCESSOR_IDENTIFIER","unknown"));state.put("processors",""+Runtime.getRuntime().availableProcessors());
        state.put("registryClassSha256",registryHash());
        var tools=OjScenarioSupport.classHashes(OjScenarioMain.class,OjScenarioReport.class,OjScenarioSupport.class,OjScenarioWorkloads.class,WindowsBenchmarkProcess.class);
        tools.forEach((k,v)->state.put("tool."+k,v));
        var rows=new ArrayList<OjScenarioReport.Sample>();var errors=new ArrayList<String>();
        try {
            for(var config:selected){
                try {
                    int rounds=1;var input=OjScenarioWorkloads.input(config,rounds);
                    if(stage.equals("measure")) {
                        boolean calibrated=false;
                        for(int step=0;step<=10;step++){
                            long fastest=Long.MAX_VALUE;int position=0;
                            for(String build:OjScenarioReport.order(step,config.seed())) {
                                var sample=observe(cache,cached,output,config,input,build,"calibration",step,position++);rows.add(sample);requireCorrect(sample);
                                fastest=Math.min(fastest,sample.wallNanos());
                            }
                            if(fastest>=CALIBRATION_NANOS){calibrated=true;break;}
                            try{rounds=Math.multiplyExact(rounds,2);input=OjScenarioWorkloads.input(config,rounds);}
                            catch(IllegalArgumentException|ArithmeticException budget){break;}
                        }
                        if(!calibrated){state.put("case."+config.id(),"uncalibrated/insufficient-duration");errors.add(config.id()+": common-round calibration reached workload budget below the 200ms target");write(output,state,rows,errors);continue;}
                    }
                    int position=0;for(String build:OjScenarioReport.BUILDS){var sample=observe(cache,cached,output,config,input,build,"verify",0,position++);rows.add(sample);requireCorrect(sample);}
                    if(stage.equals("measure"))for(String phase:List.of("warmup","measurement"))for(int rep=0;rep<(phase.equals("warmup")?warmups:repetitions);rep++){
                        position=0;for(String build:OjScenarioReport.order(rep,config.seed())){var sample=observe(cache,cached,output,config,input,build,phase,rep,position++);rows.add(sample);requireCorrect(sample);}
                    }
                    state.put("case."+config.id(),"completed");
                }catch(Exception failure){state.put("case."+config.id(),"failed");errors.add(config.id()+": "+failure.toString().replace('\n',' '));}
                write(output,state,rows,errors);
            }
            state.put("complete","true");
        }finally{write(output,state,rows,errors);}
        audit(output);
        if(!errors.isEmpty())throw new IllegalStateException("Scenario failures; preserved report: "+output.resolve("report.json"));
    }
    private static void plan(Path output,List<OjScenarioWorkloads.Configuration> selected)throws Exception {
        var rows=new ArrayList<Map<String,Object>>();
        for(var config:selected){var input=OjScenarioWorkloads.input(config,1);Path directory=output.resolve(config.id());Files.createDirectories(directory);
            Files.writeString(directory.resolve("stdin.txt"),input.stdin());Files.writeString(directory.resolve("expected-stdout.txt"),input.expectedStdout());
            rows.add(Map.of("configuration",config.id(),"scenario",config.scenario().id(),"source",config.scenario().sourcePath().toString(),"shape",config.shape(),"size",config.size(),"seed",config.seed(),"sizeUnit",config.scenario().sizeUnit(),"operationDefinition",config.scenario().operationDefinition(),"inputSha256",StlBenchmarkSupport.hash(input.stdin()),"expectedOutputSha256",StlBenchmarkSupport.hash(input.expectedStdout())));
        }OjScenarioSupport.json(output.resolve("plan.json"),Map.of("stage","dry-run","performancePassed",false,"configurations",rows));
    }
    private static void compile(Map<String,String> options,Path output)throws Exception {
        Path repository=Path.of(options.getOrDefault("repository",".")).toAbsolutePath().normalize();String gxx=options.getOrDefault("gxx",CppDifferentialHarness.referenceCompiler(System.getenv()));
        var state=new TreeMap<String,String>();state.put("schema","1");state.put("ready","false");state.put("registryClassSha256",registryHash());
        OjScenarioSupport.writeProperties(output.resolve("cache.properties"),state);
        String revision=command(output,"revision",List.of("git","rev-parse","--verify",options.getOrDefault("revision","HEAD")+"^{commit}"),repository).strip();
        if(!revision.matches("[0-9a-f]{40,64}"))throw new IllegalArgumentException("Not a committed revision");state.put("revision",revision);state.put("repository",repository.toString());
        Path zip=output.resolve("source.zip"),source=output.resolve("source");
        command(output,"archive",List.of("git","archive","--format=zip","--output="+zip,revision),repository);StlBenchmarkSupport.extract(zip,source);
        state.put("archiveSha256",StlBenchmarkSupport.hash(zip));state.put("gxx",gxx);state.put("gxxVersion",command(output,"gxx-version",List.of(gxx,"--version"),source));
        state.put("gxxTarget",command(output,"gxx-target",List.of(gxx,"-dumpmachine"),source));state.put("java",System.getProperty("java.runtime.version"));
        Path classes=output.resolve("compiler/classes"),args=output.resolve("compiler/sources.txt");Files.createDirectories(classes);
        try(var stream=Files.walk(source.resolve("src/main/java/minic"))){Files.write(args,stream.filter(p->p.toString().endsWith(".java")).filter(p->!p.toString().replace('\\','/').contains("/minic/ui/")).sorted().map(p->"\""+p.toString().replace('\\','/')+"\"").toList());}
        command(output,"compiler",List.of(java("javac"),"-J-Xmx512m","--release","21","-encoding","UTF-8","-d",classes.toString(),"@"+args),source);
        state.put("javaExecutable",java("java"));state.put("workerClasspath",classes+java.io.File.pathSeparator+source.resolve("src/main/resources")+java.io.File.pathSeparator+StlBenchmarkSupport.absoluteClasspath());
        Path observer=WindowsBenchmarkProcess.buildSupervisor(gxx,source.resolve("benchmarks/tools/windows-supervisor.cpp"),output.resolve("observer"),COMPILE);state.put("observer",relative(output,observer));
        state.put("loadedWorkerClassHashes",NativeBenchmarkReport.encode(StlBenchmarkSupport.loadedToolHashes()));
        for(var scenario:OjScenarioWorkloads.scenarios())for(String build:OjScenarioReport.BUILDS){
            Path program=OjScenarioSupport.resolve(source,scenario.sourcePath().toString());if(!Files.isRegularFile(program))throw new IllegalStateException("Workload source is not in frozen revision: "+program);
            Path directory=output.resolve("builds/"+scenario.id()+"/"+build);Files.createDirectories(directory);Path exe=directory.resolve("program.exe");List<String> cmd;
            if(build.startsWith("minic")){
                String cp=classes+java.io.File.pathSeparator+source.resolve("src/main/resources")+java.io.File.pathSeparator+StlBenchmarkSupport.absoluteClasspath();
                cmd=List.of(java("java"),"-Xmx512m","-Dfile.encoding=UTF-8","-Duser.language=en","-Dminic.project.root="+source,"-cp",cp,StlBenchmarkMain.class.getName(),"--compile-minic",program.toString(),build.equals("minic-opt")?"OPTIMIZED":"BASELINE",source.toString());
            }else if(build.equals("gxx-own")){Path headers=CppOwnLibraryReference.prepareHeaders(directory.resolve("headers"),source.resolve("lib/cpp"));cmd=CppOwnLibraryReference.compileCommand(gxx,directory,headers,program,exe);}
            else{var c=new ArrayList<>(List.of(gxx));c.addAll(CppDifferentialHarness.referenceFlags(directory));c.addAll(List.of(program.toString(),"-o",exe.toString()));cmd=List.copyOf(c);}
            OjScenarioSupport.json(directory.resolve("command.json"),cmd);var result=NativeBenchmarkSupport.run(cmd,directory,"",COMPILE,LIMIT);
            Files.writeString(directory.resolve("stdout.txt"),result.stdout());Files.writeString(directory.resolve("stderr.txt"),result.stderr());result.requireSuccess();
            var meta=new LinkedHashMap<String,Object>();meta.put("scenario",scenario.id());meta.put("build",build);meta.put("revision",revision);meta.put("command",cmd);meta.put("sourceSha256",StlBenchmarkSupport.hash(program));meta.put("compileProcessWallNanos",result.wallNanos());
            if(build.startsWith("minic")){var compiled=NativeBenchmarkSupport.compilation(result.stdout());String wanted=build.equals("minic-opt")?"OPTIMIZED":"BASELINE";
                if(!compiled.level().name().equals(wanted))throw new IllegalStateException("Wrong MiniC mode");Files.copy(compiled.artifact(),exe);meta.put("optimizationLevel",wanted);meta.put("passNames",compiled.passNames());meta.put("compilerPipelineNanos",compiled.compilerPipelineNanos());}
            meta.put("artifactSha256",StlBenchmarkSupport.hash(exe));meta.put("artifactBytes",Files.size(exe));OjScenarioSupport.json(directory.resolve("artifact.json"),meta);
            OjScenarioSupport.writeProperties(directory.resolve("artifact.properties"),artifactRecord(meta,relative(output,program)));
            state.put("artifact."+scenario.id()+"."+build,relative(output,exe));
        }
        state.put("ready","true");OjScenarioSupport.writeProperties(output.resolve("cache.properties"),state);
        OjScenarioSupport.writeProperties(output.resolve("cache-manifest.properties"),StlBenchmarkSupport.manifest(output));
    }
    private static Map<String,String> auditCache(Path cache)throws Exception {
        var manifest=OjScenarioSupport.readProperties(cache.resolve("cache-manifest.properties"));OjScenarioSupport.auditFiles(cache,manifest);
        try(var files=Files.walk(cache)){
            var actual=files.filter(Files::isRegularFile).map(p->relative(cache,p)).filter(p->!p.equals("cache-manifest.properties")).collect(java.util.stream.Collectors.toSet());
            if(!actual.equals(manifest.keySet()))throw new IllegalStateException("Incomplete artifact cache manifest");
        }
        var state=OjScenarioSupport.readProperties(cache.resolve("cache.properties"));
        if(!"1".equals(state.get("schema"))||!"true".equals(state.get("ready"))||!registryHash().equals(state.get("registryClassSha256")))throw new IllegalStateException("Incomplete cache or changed oracle bytecode");
        if(!required(state,"revision").matches("[0-9a-f]{40,64}")||!required(state,"archiveSha256").equals(manifest.get("source.zip"))
                ||!manifest.containsKey(required(state,"observer")))throw new IllegalStateException("Missing archive/revision/observer provenance");
        var required=new HashSet<String>();for(var scenario:OjScenarioWorkloads.scenarios())for(var build:OjScenarioReport.BUILDS){
            String key="artifact."+scenario.id()+"."+build,path="builds/"+scenario.id()+"/"+build+"/program.exe";required.add(key);
            if(!path.equals(required(state,key))||!manifest.containsKey(path))throw new IllegalStateException("Missing or relabeled artifact "+key);
            var meta=OjScenarioSupport.readProperties(cache.resolve(path).getParent().resolve("artifact.properties"));
            String source="source/"+scenario.sourcePath().toString().replace('\\','/');
            if(!scenario.id().equals(meta.get("scenario"))||!build.equals(meta.get("build"))||!state.get("revision").equals(meta.get("revision"))
                    ||!source.equals(meta.get("source"))||!required(meta,"sourceSha256").equals(manifest.get(source))||!required(meta,"artifactSha256").equals(manifest.get(path)))
                throw new IllegalStateException("Artifact/source identity mismatch: "+key);
            var command=compileCommand(cache,state,scenario,build);var recorded=artifactMetadata(meta);
            if(!command.equals(recorded.get("command"))||!Files.readString(cache.resolve(path).getParent().resolve("command.json")).equals(NativeBenchmarkReport.encode(command)+"\n")
                    ||!Files.readString(cache.resolve(path).getParent().resolve("artifact.json")).equals(NativeBenchmarkReport.encode(recorded)+"\n")
                    ||Files.size(cache.resolve(path))!=(long)recorded.get("artifactBytes"))throw new IllegalStateException("Compile command/metadata do not match the labeled build");
            if(build.startsWith("minic")&&!(build.equals("minic-opt")?"OPTIMIZED":"BASELINE").equals(recorded.get("optimizationLevel")))throw new IllegalStateException("Wrong MiniC optimization level");
        }
        if(!state.keySet().stream().filter(k->k.startsWith("artifact.")).collect(java.util.stream.Collectors.toSet()).equals(required))throw new IllegalStateException("Artifact coverage is not 8 x 4");
        return state;
    }
    /** Reconstruct without calling referenceFlags(), which writes compatibility headers. */
    static List<String> compileCommand(Path cache,Map<String,String> state,OjScenarioWorkloads.Scenario scenario,String build){
        Path source=cache.resolve("source"),program=source.resolve(scenario.sourcePath()),directory=cache.resolve("builds/"+scenario.id()+"/"+build),exe=directory.resolve("program.exe");
        if(build.startsWith("minic")){
            String prefix=cache.resolve("compiler/classes")+java.io.File.pathSeparator+source.resolve("src/main/resources")+java.io.File.pathSeparator;
            if(!required(state,"workerClasspath").startsWith(prefix))throw new IllegalStateException("Worker does not use the frozen compiler first");
            return List.of(required(state,"javaExecutable"),"-Xmx512m","-Dfile.encoding=UTF-8","-Duser.language=en","-Dminic.project.root="+source,
                    "-cp",required(state,"workerClasspath"),StlBenchmarkMain.class.getName(),"--compile-minic",program.toString(),build.equals("minic-opt")?"OPTIMIZED":"BASELINE",source.toString());
        }
        var command=new ArrayList<>(List.of(required(state,"gxx"),"-std=c++17","-O2","-include",directory.resolve("reference-header-compat.h").toString()));
        if(build.equals("gxx-own"))command.addAll(List.of("-nostdinc++","-D__MINIC_SELF_STL__=1","-D__USE_MINGW_STRTOX=1","-I",directory.resolve("headers").toString()));
        command.addAll(List.of(program.toString(),"-o",exe.toString()));return List.copyOf(command);
    }
    static Map<String,String> artifactRecord(Map<String,Object> metadata,String source){
        var record=new TreeMap<String,String>();record.put("source",source);
        metadata.forEach((key,value)->{if(value instanceof List<?> values){record.put(key+".count",""+values.size());for(int i=0;i<values.size();i++)record.put(key+"."+i,String.valueOf(values.get(i)));}else record.put(key,String.valueOf(value));});return record;
    }
    static Map<String,Object> artifactMetadata(Map<String,String> record){
        var metadata=new LinkedHashMap<String,Object>();
        for(String key:List.of("scenario","build","revision"))metadata.put(key,required(record,key));
        metadata.put("command",recordList(record,"command"));metadata.put("sourceSha256",required(record,"sourceSha256"));metadata.put("compileProcessWallNanos",positiveOrZero(record,"compileProcessWallNanos"));
        if(record.get("build").startsWith("minic")){metadata.put("optimizationLevel",required(record,"optimizationLevel"));metadata.put("passNames",recordList(record,"passNames"));metadata.put("compilerPipelineNanos",positiveOrZero(record,"compilerPipelineNanos"));}
        metadata.put("artifactSha256",required(record,"artifactSha256"));metadata.put("artifactBytes",positiveOrZero(record,"artifactBytes"));return metadata;
    }
    private static long positiveOrZero(Map<String,String> record,String key){long value=Long.parseLong(required(record,key));if(value<0)throw new IllegalStateException("Negative "+key);return value;}
    private static List<String> recordList(Map<String,String> record,String key){
        int count=Integer.parseInt(required(record,key+".count"));if(count<0||count>4096)throw new IllegalStateException("Invalid metadata list");var values=new ArrayList<String>();for(int i=0;i<count;i++)values.add(required(record,key+"."+i));return List.copyOf(values);
    }
    private static OjScenarioReport.Sample observe(Path cache,Map<String,String> cached,Path output,OjScenarioWorkloads.Configuration config,OjScenarioWorkloads.Input input,String build,String phase,int repetition,int order)throws Exception {
        Path directory=output.resolve("runs/"+config.id()+"/"+phase+"-"+repetition+"/"+order+"-"+build);
        var result=WindowsBenchmarkProcess.run(OjScenarioSupport.resolve(cache,required(cached,"observer")),OjScenarioSupport.resolve(cache,required(cached,"artifact."+config.scenario().id()+"."+build)),directory,input.stdin(),RUN,LIMIT);
        Files.writeString(directory.resolve("expected-stdout.txt"),input.expectedStdout());String actual=result.stdout().replace("\r\n","\n"),expected=input.expectedStdout().replace("\r\n","\n");
        var row=new OjScenarioReport.Sample(config.id(),build,phase,repetition,order,input.rounds(),input.logicalInputItems(),StlBenchmarkSupport.hash(input.stdin()),StlBenchmarkSupport.hash(expected),StlBenchmarkSupport.hash(actual),result.stderr().isEmpty()?result.status().name():"STDERR",result.exitCode(),result.wallNanos(),result.userCpuNanos(),result.kernelCpuNanos(),result.peakCommitBytes(),relative(output,directory),StlBenchmarkSupport.manifest(directory));
        OjScenarioSupport.writeProperties(directory.resolve("sample.properties"),row.fields());return row;
    }
    private static void requireCorrect(OjScenarioReport.Sample row){if(!row.status().equals("COMPLETED")||row.exitCode()!=0||!row.expectedHash().equals(row.outputHash()))throw new IllegalStateException("Failed process/full-output oracle: "+row.directory());}
    private static void write(Path output,Map<String,String> state,List<OjScenarioReport.Sample> rows,List<String> errors)throws Exception {
        state.put("errors",String.join("\n",errors));OjScenarioSupport.writeProperties(output.resolve("run.properties"),state);
        var index=new TreeMap<String,String>();for(var row:rows){String file=row.directory()+"/sample.properties";index.put(file,StlBenchmarkSupport.hash(OjScenarioSupport.resolve(output,file)));}
        OjScenarioSupport.writeProperties(output.resolve("samples-index.properties"),index);OjScenarioSupport.json(output.resolve("report.json"),report(state,rows));
    }
    static Map<String,Object> report(Map<String,String> state,List<OjScenarioReport.Sample> rows){
        rows=rows.stream().sorted(Comparator.comparing(OjScenarioReport.Sample::directory)).toList();
        var configs=OjScenarioWorkloads.matrix();var chosen=Set.of(state.get("selected").split(","));var summaries=new LinkedHashMap<String,OjScenarioReport.Summary>();
        var required=configs.stream().map(OjScenarioWorkloads.Configuration::id).collect(java.util.stream.Collectors.toSet());
        boolean functional="true".equals(state.get("complete"))&&state.get("errors").isEmpty(),calibration=true;
        functional&=required.containsAll(chosen)&&!chosen.isEmpty();
        for(var row:rows)functional&=chosen.contains(row.configuration())&&OjScenarioReport.BUILDS.contains(row.build())
                &&row.status().equals("COMPLETED")&&row.exitCode()==0&&row.expectedHash().equals(row.outputHash());
        for(var config:configs)if(chosen.contains(config.id())){
            var verification=rows.stream().filter(r->r.configuration().equals(config.id())&&r.phase().equals("verify")).toList();
            functional&=verification.size()==4&&new HashSet<>(verification.stream().map(OjScenarioReport.Sample::build).toList()).equals(Set.copyOf(OjScenarioReport.BUILDS));
            for(var row:verification)functional&=row.status().equals("COMPLETED")&&row.exitCode()==0&&row.expectedHash().equals(row.outputHash())
                    &&row.repetition()==0&&row.order()==OjScenarioReport.BUILDS.indexOf(row.build());
            functional&=verification.stream().map(r->r.rounds()+"/"+r.inputHash()+"/"+r.expectedHash()).distinct().count()==1;
            if(state.get("stage").equals("measure")){
                summaries.put(config.id(),OjScenarioReport.summarize(config.id(),rows,Integer.parseInt(state.get("repetitions")),Integer.parseInt(state.get("warmups")),config.seed()));
                calibration&=!verification.isEmpty()&&calibrated(config,rows,verification.getFirst().rounds());
            }
        }
        boolean partial=Boolean.parseBoolean(state.get("filtered"))||!chosen.equals(required);
        var result=new LinkedHashMap<String,Object>();result.put("schemaVersion",1);result.put("metadata",state);result.put("partial",partial);result.put("functionalPassed",functional);
        result.put("calibrationPassed",state.get("stage").equals("measure")&&calibration&&!chosen.isEmpty());
        result.put("performancePassed",state.get("stage").equals("measure")&&calibration&&OjScenarioReport.suitePassed(required,chosen,partial,functional,summaries));
        result.put("caseThresholdOptOverSystem",OjScenarioReport.MAXIMUM_RATIO);result.put("samples",rows.stream().map(OjScenarioReport.Sample::fields).toList());
        var fields=new LinkedHashMap<String,Object>();summaries.forEach((key,value)->fields.put(key,value.fields()));result.put("summaries",fields);return result;
    }
    private static boolean calibrated(OjScenarioWorkloads.Configuration config,List<OjScenarioReport.Sample> rows,int finalRounds){
        var samples=rows.stream().filter(r->r.configuration().equals(config.id())&&r.phase().equals("calibration")).toList();
        if(samples.isEmpty())return false;int last=samples.stream().mapToInt(OjScenarioReport.Sample::repetition).max().orElseThrow();
        if(last<0||last>10||samples.size()!=3*(last+1)||finalRounds!=(1<<last))return false;
        for(int step=0;step<=last;step++){
            int current=step;var group=samples.stream().filter(r->r.repetition()==current).toList();var order=OjScenarioReport.order(step,config.seed());
            if(group.size()!=3||!new HashSet<>(group.stream().map(OjScenarioReport.Sample::build).toList()).equals(Set.copyOf(OjScenarioReport.MEASURED)))return false;
            for(var row:group)if(row.rounds()!=(1<<step)||row.order()!=order.indexOf(row.build())||!row.status().equals("COMPLETED")||row.exitCode()!=0||!row.outputHash().equals(row.expectedHash()))return false;
            if(group.stream().map(r->r.inputHash()+"/"+r.expectedHash()+"/"+r.inputItems()).distinct().count()!=1)return false;
            long fastest=group.stream().mapToLong(OjScenarioReport.Sample::wallNanos).min().orElseThrow();
            if(step==last?fastest<CALIBRATION_NANOS:fastest>=CALIBRATION_NANOS)return false;
        }return true;
    }
    static void audit(Path output)throws Exception {
        output=output.toAbsolutePath().normalize();var state=OjScenarioSupport.readProperties(output.resolve("run.properties"));Path cache=Path.of(required(state,"cache"));var cached=auditCache(cache);
        if(!"1".equals(state.get("schema"))||!Set.of("validate","measure").contains(state.get("stage"))||!cached.get("revision").equals(state.get("revision"))||!registryHash().equals(state.get("registryClassSha256")))throw new IllegalStateException("Changed schema/product/oracle identity");
        if(!StlBenchmarkSupport.hash(cache.resolve("cache-manifest.properties")).equals(state.get("cacheManifestSha256")))throw new IllegalStateException("Changed artifact cache identity");
        var index=OjScenarioSupport.readProperties(output.resolve("samples-index.properties"));if(index.isEmpty())throw new IllegalStateException("No native evidence");OjScenarioSupport.auditFiles(output,index);
        Path auditRoot=output;
        try(var files=Files.walk(output.resolve("runs"))){
            var records=files.filter(p->p.getFileName().toString().equals("sample.properties")).map(p->relative(auditRoot,p)).collect(java.util.stream.Collectors.toSet());
            if(!records.equals(index.keySet()))throw new IllegalStateException("Unindexed or missing native sample records");
        }
        var configs=new HashMap<String,OjScenarioWorkloads.Configuration>();for(var c:OjScenarioWorkloads.matrix())configs.put(c.id(),c);
        var rows=new ArrayList<OjScenarioReport.Sample>();
        // Only the latest (configuration, rounds) model is retained. Sorted paths group repeated
        // samples; no matrix-sized collection of potentially 128MiB inputs survives the audit.
        String cachedConfiguration=null,cachedOracle=null;OjScenarioWorkloads.Input input=null;
        for(var path:index.keySet()){
            var row=OjScenarioReport.Sample.read(OjScenarioSupport.readProperties(OjScenarioSupport.resolve(output,path)));Path directory=OjScenarioSupport.resolve(output,row.directory());
            if(!OjScenarioSupport.resolve(output,path).equals(directory.resolve("sample.properties")))throw new IllegalStateException("Mislocated sample record");OjScenarioSupport.auditFiles(directory,row.files());
            var config=configs.get(row.configuration());if(config==null)throw new IllegalStateException("Unknown configuration");
            String canonical="runs/"+config.id()+"/"+row.phase()+"-"+row.repetition()+"/"+row.order()+"-"+row.build();
            if(!canonical.equals(row.directory())||!Files.readString(directory.resolve("command.json")).equals(observerCommand(cache,cached,config,row.build(),directory)))
                throw new IllegalStateException("Sample command is not bound to its recorded build, input and output artifacts");
            if(!row.configuration().equals(cachedConfiguration)||input==null||input.rounds()!=row.rounds()){
                // Release a previous large model before generating its replacement.
                input=null;cachedOracle=null;input=OjScenarioWorkloads.input(config,row.rounds());
                cachedOracle=OjScenarioWorkloads.oracle(config.scenario(),input.stdin()).replace("\r\n","\n");cachedConfiguration=row.configuration();
            }
            String stdin=Files.readString(directory.resolve("stdin.txt")),actual=Files.readString(directory.resolve("stdout.txt")).replace("\r\n","\n"),expected=Files.readString(directory.resolve("expected-stdout.txt")).replace("\r\n","\n");
            if(!stdin.equals(input.stdin())||row.inputItems()!=input.logicalInputItems()||!expected.equals(input.expectedStdout().replace("\r\n","\n"))||!expected.equals(cachedOracle))throw new IllegalStateException("Changed input/independent oracle");
            if(!row.inputHash().equals(StlBenchmarkSupport.hash(stdin))||!row.expectedHash().equals(StlBenchmarkSupport.hash(expected))||!row.outputHash().equals(StlBenchmarkSupport.hash(actual)))throw new IllegalStateException("Sample text digest mismatch");
            var observed=WindowsBenchmarkProcess.parse(Files.readString(directory.resolve("observer.properties")),0,actual,Files.readString(directory.resolve("stderr.txt")),directory.resolve("observer.properties"),directory.resolve("stdout.txt"),directory.resolve("stderr.txt"));
            String status=observed.stderr().isEmpty()?observed.status().name():"STDERR";
            if(!status.equals(row.status())||observed.exitCode()!=row.exitCode()||observed.wallNanos()!=row.wallNanos()||observed.userCpuNanos()!=row.userCpuNanos()||observed.kernelCpuNanos()!=row.kernelCpuNanos()||observed.peakCommitBytes()!=row.peakCommitBytes())throw new IllegalStateException("Observer/summary mismatch");rows.add(row);
        }
        // Index is sorted; writing and auditing use this same stable order.
        rows.sort(Comparator.comparing(OjScenarioReport.Sample::directory));
        String expected=NativeBenchmarkReport.encode(report(state,rows))+"\n";
        if(!Files.readString(output.resolve("report.json")).equals(expected))throw new IllegalStateException("Report does not match audited raw evidence");
        System.out.println("Evidence audited: "+output+" (audit validates integrity; see performancePassed/partial in report)");
    }
    private static String observerCommand(Path cache,Map<String,String> cached,OjScenarioWorkloads.Configuration config,String build,Path directory)throws Exception {
        if(!OjScenarioReport.BUILDS.contains(build))throw new IllegalStateException("Unknown build label");
        return NativeBenchmarkReport.encode(List.of(OjScenarioSupport.resolve(cache,required(cached,"observer")).toString(),
                "--exe="+OjScenarioSupport.resolve(cache,required(cached,"artifact."+config.scenario().id()+"."+build)),
                "--stdin="+directory.resolve("stdin.txt"),"--stdout="+directory.resolve("stdout.txt"),"--stderr="+directory.resolve("stderr.txt"),
                "--report="+directory.resolve("observer.properties"),"--timeout-ms="+RUN.toMillis(),"--output-limit="+LIMIT))+"\n";
    }
    private static List<OjScenarioWorkloads.Configuration> selected(Map<String,String> options){
        var all=OjScenarioWorkloads.matrix();var result=all.stream().filter(c->!options.containsKey("scenario")||c.scenario().id().equals(options.get("scenario"))).filter(c->!options.containsKey("config-id")||c.id().equals(options.get("config-id"))).toList();
        if(result.isEmpty())throw new IllegalArgumentException("No matching configuration");return result;
    }
    private static boolean filtered(Map<String,String> options){return options.containsKey("scenario")||options.containsKey("config-id");}
    static String registryHash()throws Exception{return StlBenchmarkSupport.hash(NativeBenchmarkReport.encode(OjScenarioSupport.classHashes(OjScenarioWorkloads.class)));}
    private static String java(String tool){return Path.of(System.getProperty("java.home"),"bin",tool+".exe").toString();}
    private static String relative(Path root,Path file){return root.relativize(file).toString().replace('\\','/');}
    private static String required(Map<String,String> options,String key){String value=options.get(key);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+key);return value;}
    static Map<String,String> options(String[] args){var result=new LinkedHashMap<String,String>();var allowed=Set.of("stage","output","repository","revision","gxx","cache","run","scenario","config-id","repetitions","warmups","host-note");
        for(String arg:args){int equal=arg.indexOf('=');if(!arg.startsWith("--")||equal<3||equal==arg.length()-1)throw new IllegalArgumentException("Use --name=value");String key=arg.substring(2,equal);if(!allowed.contains(key)||result.putIfAbsent(key,arg.substring(equal+1))!=null)throw new IllegalArgumentException("Unknown/duplicate option "+key);}return result;}
    private static String command(Path output,String name,List<String> command,Path directory)throws Exception {
        Path logs=output.resolve("setup");Files.createDirectories(logs);OjScenarioSupport.json(logs.resolve(name+"-command.json"),command);
        var result=NativeBenchmarkSupport.run(command,directory,"",COMPILE,LIMIT);Files.writeString(logs.resolve(name+"-stdout.txt"),result.stdout());Files.writeString(logs.resolve(name+"-stderr.txt"),result.stderr());result.requireSuccess();return result.stdout();
    }
    private static void help(){System.out.println("""
        OJ algorithm scenarios, independent of the original STL microbenchmarks.
        --stage=dry-run --output=<new-directory> [--scenario=<id>|--config-id=<id>]
        --stage=compile --repository=<git-root> --revision=<committed-revision> --gxx=<g++.exe> --output=<new-cache>
        --stage=validate --cache=<compiled-cache> --output=<new-run> [--scenario=<id>|--config-id=<id>]
        --stage=measure --cache=<compiled-cache> --output=<new-run> --repetitions=12 --warmups=2 --host-note=<conditions>
        --stage=audit --run=<existing-run>   (offline: no compiler/process execution)
        Measure only on an idle fixed Windows x64 host after functional acceptance. Four builds verify every
        final input; only OPT/own/system are measured. All use identical complete stdin at common rounds.
        Calibration never alters shape or size. Registry budgets can leave a case uncalibrated; it cannot pass.
        Any measurement below 100ms, missing/failed case, or case OPT/system >1.2 prevents the full-suite gate.
        Filtered runs always remain partial. Input, complete output, commands, versions and hashes are retained.
        """);}
}
