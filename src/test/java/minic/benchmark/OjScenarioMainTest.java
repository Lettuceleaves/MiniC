package minic.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic raw evidence only: these tests launch no compiler, benchmark or native observer. */
@Tag("native-perf-contract")
final class OjScenarioMainTest {
    @TempDir Path temporary;
    @Test void validOfflineEvidenceIsAuditableButFilteredValidationNeverClaimsPerformance()throws Exception {
        var fixture=fixture();OjScenarioMain.audit(fixture.run);
        var report=OjScenarioMain.report(fixture.state,fixture.rows);
        assertEquals(true,report.get("functionalPassed"));assertEquals(true,report.get("partial"));assertEquals(false,report.get("performancePassed"));
    }
    @Test void sourceExecutableInputAndOutputTamperingAreRejected()throws Exception {
        var fixture=fixture();Path sample=fixture.run.resolve(fixture.rows.getFirst().directory());
        for(Path path:List.of(fixture.cache.resolve("source.cpp"),fixture.cache.resolve("builds/dijkstra/minic-base/program.exe"),sample.resolve("stdin.txt"),sample.resolve("stdout.txt"))){
            byte[] bytes=Files.readAllBytes(path);Files.writeString(path,"tampered");
            assertThrows(Exception.class,()->OjScenarioMain.audit(fixture.run),path.toString());Files.write(path,bytes);
        }
        OjScenarioMain.audit(fixture.run);
    }
    @Test void commandCannotExecuteAnotherBuildEvenWithConsistentUpdatedDigests()throws Exception {
        var fixture=fixture();var row=fixture.rows.getFirst();Path directory=fixture.run.resolve(row.directory());
        Files.writeString(directory.resolve("command.json"),command(fixture,"minic-opt",directory));
        fixture.refreshRow(0);fixture.persist();
        assertThrows(Exception.class,()->OjScenarioMain.audit(fixture.run));
    }
    @Test void allThirtyTwoBuildsAreRequiredEvenForFilteredValidation()throws Exception {
        var fixture=fixture();fixture.cached.remove("artifact.grid-bfs.gxx-stl");fixture.persistCache();fixture.persist();
        assertThrows(Exception.class,()->OjScenarioMain.audit(fixture.run));
    }
    @Test void buildLabelsCannotAliasAnotherExecutableEvenWithConsistentDigests()throws Exception {
        var fixture=fixture();fixture.cached.put("artifact.dijkstra.minic-base",fixture.cached.get("artifact.dijkstra.gxx-stl"));
        fixture.persistCache();Path directory=fixture.run.resolve(fixture.rows.getFirst().directory());
        Files.writeString(directory.resolve("command.json"),command(fixture,"minic-base",directory));fixture.refreshRow(0);fixture.persist();
        assertThrows(Exception.class,()->OjScenarioMain.audit(fixture.run));
    }
    @Test void compileModeAndRecordedCompilerMustMatchTheBuildLabel()throws Exception {
        var fixture=fixture();Path directory=fixture.cache.resolve("builds/dijkstra/minic-opt");
        var record=new TreeMap<>(OjScenarioSupport.readProperties(directory.resolve("artifact.properties")));record.put("optimizationLevel","BASELINE");
        OjScenarioSupport.writeProperties(directory.resolve("artifact.properties"),record);OjScenarioSupport.json(directory.resolve("artifact.json"),OjScenarioMain.artifactMetadata(record));
        fixture.persistCache();fixture.persist();assertThrows(Exception.class,()->OjScenarioMain.audit(fixture.run));
        record.put("optimizationLevel","OPTIMIZED");record.put("command.0","wrong-tool.exe");
        OjScenarioSupport.writeProperties(directory.resolve("artifact.properties"),record);var metadata=OjScenarioMain.artifactMetadata(record);
        OjScenarioSupport.json(directory.resolve("artifact.json"),metadata);OjScenarioSupport.json(directory.resolve("command.json"),metadata.get("command"));
        fixture.persistCache();fixture.persist();assertThrows(Exception.class,()->OjScenarioMain.audit(fixture.run));
    }
    @Test void aHashLedgerCannotOmitSourceEvidence()throws Exception {
        var fixture=fixture();Path manifest=fixture.cache.resolve("cache-manifest.properties");
        var files=OjScenarioSupport.readProperties(manifest);files.remove("source.cpp");OjScenarioSupport.writeProperties(manifest,files);
        fixture.state.put("cacheManifestSha256",StlBenchmarkSupport.hash(manifest));fixture.persist();assertThrows(Exception.class,()->OjScenarioMain.audit(fixture.run));
    }
    @Test void anUnindexedNativeRecordCannotBeSilentlyExcluded()throws Exception {
        var fixture=fixture();Path extra=fixture.run.resolve("runs/unindexed/sample.properties");Files.createDirectories(extra.getParent());
        Files.copy(fixture.run.resolve(fixture.rows.getFirst().directory()).resolve("sample.properties"),extra);
        assertThrows(Exception.class,()->OjScenarioMain.audit(fixture.run));
    }
    @Test void recomputedEvidenceOfWrongFullOutputCanNeverPass()throws Exception {
        var fixture=fixture();var row=fixture.rows.getFirst();Path directory=fixture.run.resolve(row.directory());
        Files.writeString(directory.resolve("stdout.txt"),fixture.input.expectedStdout()+"unexpected trailing result\n");
        fixture.refreshRow(0);fixture.persist();OjScenarioMain.audit(fixture.run);
        assertEquals(false,OjScenarioMain.report(fixture.state,fixture.rows).get("functionalPassed"));
    }
    @Test void alteredDerivedGateCannotOverrideRawEvidence()throws Exception {
        var fixture=fixture();var report=new LinkedHashMap<>(OjScenarioMain.report(fixture.state,fixture.rows));report.put("performancePassed",true);
        OjScenarioSupport.json(fixture.run.resolve("report.json"),report);assertThrows(Exception.class,()->OjScenarioMain.audit(fixture.run));
    }
    @Test void optionTyposAndDuplicateFiltersFailBeforeAnyProcessExecution(){
        assertThrows(IllegalArgumentException.class,()->OjScenarioMain.options(new String[]{"--stage=measure","--stage=audit"}));
        assertThrows(IllegalArgumentException.class,()->OjScenarioMain.options(new String[]{"--skip-errors=true"}));
    }
    @Test void recordedCalibrationIsRequiredAndItsFinalRoundMustMatchVerification()throws Exception {
        var fixture=fixture();fixture.state.put("stage","measure");
        assertEquals(false,OjScenarioMain.report(fixture.state,fixture.rows).get("calibrationPassed"));
        int order=0;for(String build:OjScenarioReport.order(0,fixture.config.seed())){
            var fields=fixture.rows.getFirst().fields();fields.put("build",build);fields.put("phase","calibration");fields.put("order",""+order++);fields.put("wallNanos","200000000");
            fixture.rows.add(OjScenarioReport.Sample.read(fields));
        }
        assertEquals(true,OjScenarioMain.report(fixture.state,fixture.rows).get("calibrationPassed"));
        fixture.rows.set(fixture.rows.size()-1,fixture.rows.getLast().withInput(fixture.rows.getLast().inputHash(),2));
        assertEquals(false,OjScenarioMain.report(fixture.state,fixture.rows).get("calibrationPassed"));
    }
    private Fixture fixture()throws Exception {
        var fixture=new Fixture();fixture.cache=temporary.resolve("cache");fixture.run=temporary.resolve("run");Files.createDirectories(fixture.cache);Files.createDirectories(fixture.run);
        fixture.config=OjScenarioWorkloads.matrix().getFirst();fixture.input=OjScenarioWorkloads.input(fixture.config,1);
        fixture.cached.putAll(Map.of("schema","1","ready","true","registryClassSha256",OjScenarioMain.registryHash(),"revision","0".repeat(40),"observer","observer.exe"));
        Files.writeString(fixture.cache.resolve("source.cpp"),"synthetic source evidence");Files.writeString(fixture.cache.resolve("observer.exe"),"synthetic observer evidence");
        Files.writeString(fixture.cache.resolve("source.zip"),"synthetic archive evidence");fixture.cached.put("archiveSha256",StlBenchmarkSupport.hash(fixture.cache.resolve("source.zip")));
        fixture.cached.put("gxx","synthetic-g++.exe");fixture.cached.put("javaExecutable","synthetic-java.exe");
        fixture.cached.put("workerClasspath",fixture.cache.resolve("compiler/classes")+java.io.File.pathSeparator+fixture.cache.resolve("source/src/main/resources")+java.io.File.pathSeparator+"synthetic-tools");
        for(var scenario:OjScenarioWorkloads.scenarios())for(var build:OjScenarioReport.BUILDS){
            String path="builds/"+scenario.id()+"/"+build+"/program.exe";Path file=fixture.cache.resolve(path);Files.createDirectories(file.getParent());Files.writeString(file,"synthetic "+scenario.id()+" "+build);
            String source="source/"+scenario.sourcePath().toString().replace('\\','/');Path program=fixture.cache.resolve(source);Files.createDirectories(program.getParent());Files.writeString(program,"synthetic source "+scenario.id());
            var metadata=new LinkedHashMap<String,Object>();metadata.put("scenario",scenario.id());metadata.put("build",build);metadata.put("revision","0".repeat(40));
            metadata.put("command",OjScenarioMain.compileCommand(fixture.cache,fixture.cached,scenario,build));metadata.put("sourceSha256",StlBenchmarkSupport.hash(program));metadata.put("compileProcessWallNanos",1L);
            if(build.startsWith("minic")){metadata.put("optimizationLevel",build.equals("minic-opt")?"OPTIMIZED":"BASELINE");metadata.put("passNames",List.of());metadata.put("compilerPipelineNanos",1L);}
            metadata.put("artifactSha256",StlBenchmarkSupport.hash(file));metadata.put("artifactBytes",Files.size(file));
            OjScenarioSupport.writeProperties(file.getParent().resolve("artifact.properties"),OjScenarioMain.artifactRecord(metadata,source));
            OjScenarioSupport.json(file.getParent().resolve("artifact.json"),metadata);OjScenarioSupport.json(file.getParent().resolve("command.json"),metadata.get("command"));
            fixture.cached.put("artifact."+scenario.id()+"."+build,path);
        }
        fixture.state.putAll(Map.of("schema","1","stage","validate","cache",fixture.cache.toAbsolutePath().toString(),"revision","0".repeat(40),"registryClassSha256",OjScenarioMain.registryHash(),"selected",fixture.config.id(),"filtered","true","repetitions","6","warmups","1","complete","true"));fixture.state.put("errors","");
        fixture.persistCache();int order=0;
        for(var build:OjScenarioReport.BUILDS){
            String relative="runs/"+fixture.config.id()+"/verify-0/"+order+"-"+build;Path directory=fixture.run.resolve(relative);Files.createDirectories(directory);
            Files.writeString(directory.resolve("stdin.txt"),fixture.input.stdin());Files.writeString(directory.resolve("expected-stdout.txt"),fixture.input.expectedStdout());
            Files.writeString(directory.resolve("stdout.txt"),fixture.input.expectedStdout());Files.writeString(directory.resolve("stderr.txt"),"");
            Files.writeString(directory.resolve("observer-stdout.txt"),"");Files.writeString(directory.resolve("observer-stderr.txt"),"");
            Files.writeString(directory.resolve("observer.properties"),"schemaVersion=1\nstatus=completed\nexitCode=0\nwin32Error=0\nwallNanos=110000000\nuserCpuNanos=100000000\nkernelCpuNanos=1000000\npeakCommitBytes=4096\n");
            Files.writeString(directory.resolve("command.json"),command(fixture,build,directory));
            fixture.rows.add(new OjScenarioReport.Sample(fixture.config.id(),build,"verify",0,order++,1,fixture.input.logicalInputItems(),StlBenchmarkSupport.hash(fixture.input.stdin()),StlBenchmarkSupport.hash(fixture.input.expectedStdout()),StlBenchmarkSupport.hash(fixture.input.expectedStdout()),"COMPLETED",0,110000000,100000000,1000000,4096,relative,StlBenchmarkSupport.manifest(directory)));
        }
        fixture.persist();return fixture;
    }
    private static String command(Fixture fixture,String build,Path directory){
        return NativeBenchmarkReport.encode(List.of(fixture.cache.resolve("observer.exe").toString(),"--exe="+fixture.cache.resolve(fixture.cached.get("artifact."+fixture.config.scenario().id()+"."+build)),"--stdin="+directory.resolve("stdin.txt"),"--stdout="+directory.resolve("stdout.txt"),"--stderr="+directory.resolve("stderr.txt"),"--report="+directory.resolve("observer.properties"),"--timeout-ms=120000","--output-limit=4194304"))+"\n";
    }
    private static final class Fixture {
        Path cache,run;OjScenarioWorkloads.Configuration config;OjScenarioWorkloads.Input input;
        final Map<String,String> state=new TreeMap<>(),cached=new TreeMap<>();final List<OjScenarioReport.Sample> rows=new ArrayList<>();
        void persistCache()throws Exception {
            OjScenarioSupport.writeProperties(cache.resolve("cache.properties"),cached);var manifest=new TreeMap<>(StlBenchmarkSupport.manifest(cache));manifest.remove("cache-manifest.properties");
            OjScenarioSupport.writeProperties(cache.resolve("cache-manifest.properties"),manifest);state.put("cacheManifestSha256",StlBenchmarkSupport.hash(cache.resolve("cache-manifest.properties")));
        }
        void refreshRow(int index)throws Exception {
            var row=rows.get(index);Path directory=run.resolve(row.directory());var fields=row.fields();fields.keySet().removeIf(k->k.startsWith("file."));
            var manifest=new TreeMap<>(StlBenchmarkSupport.manifest(directory));manifest.remove("sample.properties");manifest.forEach((path,hash)->fields.put("file."+path,hash));
            fields.put("outputHash",StlBenchmarkSupport.hash(Files.readString(directory.resolve("stdout.txt")).replace("\r\n","\n")));rows.set(index,OjScenarioReport.Sample.read(fields));
        }
        void persist()throws Exception {
            var index=new TreeMap<String,String>();for(var row:rows){String relative=row.directory()+"/sample.properties";Path path=run.resolve(relative);OjScenarioSupport.writeProperties(path,row.fields());index.put(relative,StlBenchmarkSupport.hash(path));}
            OjScenarioSupport.writeProperties(run.resolve("samples-index.properties"),index);OjScenarioSupport.writeProperties(run.resolve("run.properties"),state);OjScenarioSupport.json(run.resolve("report.json"),OjScenarioMain.report(state,rows));
        }
    }
}
