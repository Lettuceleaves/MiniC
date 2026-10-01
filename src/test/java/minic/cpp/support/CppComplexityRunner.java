package minic.cpp.support;

import minic.benchmark.StlBenchmarkProbes;
import minic.cpp.support.CppDifferentialHarness.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Count-only artifacts. Different conforming implementations need not have identical counts. */
public final class CppComplexityRunner {
 private CppComplexityRunner(){}
 public static void run(Path root,Path output,String kind,int n,int debugN)throws Exception {
  Files.createDirectories(output.getParent());Files.createDirectory(output);
  var instrumented=StlBenchmarkProbes.instrumentOwnHeaders(root,output.resolve("instrumented-project"));
  String source=Files.readString(root.resolve("benchmarks/stl/probe-hooks.mh"))+"\n"+Files.readString(root.resolve("src/test/resources/cpp/library-complexity/"+kind+".cpp"));
  Path common=output.resolve("exact-source.cpp");Files.writeString(common,source);
  String gxx=CppDifferentialHarness.referenceCompiler(System.getenv());
  List<Object> builds=new ArrayList<>();Map<String,Object> report=new LinkedHashMap<>();
  report.put("purpose","operation counts only; no time or performance ranking");report.put("largeSize",n);report.put("debugSize",debugN);report.put("instrumentation",instrumented.strategy());report.put("builds",builds);
  List<String> failures=new ArrayList<>();
  for(String id:List.of("minic-baseline","minic-optimized","gxx-own","gxx-host","minic-debug")) {
   Path dir=output.resolve(id);Files.createDirectory(dir);Map<String,Object> entry=new LinkedHashMap<>();entry.put("build",id);builds.add(entry);
   int size=id.equals("minic-debug")?debugN:n;String input=size+"\n";
   Path program=dir.resolve("program.cpp");Files.writeString(program,source);Files.writeString(dir.resolve("stdin.txt"),input);
   try {
    String stdout;
    if(id.startsWith("minic")) {
     boolean debug=id.equals("minic-debug");Backend backend=debug?Backend.MINIC_DEBUG:Backend.MINIC_NATIVE;
     Path result=dir.resolve("result.properties");
     var command=new ArrayList<>(ProcessProbe.javaCommand(MiniCWorker.class,backend.name(),program.toString(),dir.resolve("stdin.txt").toString(),result.toString(),"50000000","1048576",Long.toString(Duration.ofSeconds(180).toNanos()),"CPP17_ALGORITHM",Long.toString(Duration.ofSeconds(180).toNanos()),dir.resolve("phase").toString(),id.equals("minic-optimized")?"OPTIMIZED":"BASELINE","false"));
     command.set(1,"-Xmx384m");command.add(1,"-Dminic.project.root="+instrumented.projectRoot());
     var process=BoundedProcess.run(command,root,"",Duration.ofSeconds(debug?370:190),1048576);
     Files.writeString(dir.resolve("compiler-stdout.txt"),process.stdout());Files.writeString(dir.resolve("compiler-stderr.txt"),process.stderr());
     require(process,"MiniC worker");var outcome=MiniCWorker.readResult(result,backend);
     if(outcome.status()!=Status.OK)throw new IllegalStateException(outcome.toString());
     if(debug)stdout=outcome.stdout();
     else {var run=BoundedProcess.run(List.of(dir.resolve("native/program.exe").toString()),dir,input,Duration.ofSeconds(45),1048576);require(run,"native execution");stdout=run.stdout();}
    } else {
     List<String> command;
     if(id.equals("gxx-own")) {Path shims=CppOwnLibraryReference.prepareHeaders(dir.resolve("shims"),instrumented.projectRoot().resolve("lib/cpp"));command=CppOwnLibraryReference.compileCommand(gxx,dir,shims,program,dir.resolve("program.exe"));}
     else {var c=new ArrayList<>(List.of(gxx));c.addAll(CppDifferentialHarness.referenceFlags(dir));c.addAll(List.of(program.toString(),"-o",dir.resolve("program.exe").toString()));command=c;}
     var compilation=BoundedProcess.run(command,root,"",Duration.ofSeconds(180),1048576);Files.writeString(dir.resolve("compiler-stdout.txt"),compilation.stdout());Files.writeString(dir.resolve("compiler-stderr.txt"),compilation.stderr());require(compilation,"reference compilation");
     var run=BoundedProcess.run(List.of(dir.resolve("program.exe").toString()),dir,input,Duration.ofSeconds(45),1048576);require(run,"reference execution");stdout=run.stdout();
    }
    Files.writeString(dir.resolve("counts.txt"),stdout);
    var rows=CppComplexityContracts.validate(kind,size,stdout);
    entry.put("size",size);entry.put("status","OK");entry.put("counts",rows.stream().map(CppComplexityContracts.Row::values).toList());
   }catch(Exception|AssertionError failure){entry.put("status","FAILED");entry.put("error",failure.toString());failures.add(id+": "+failure);}
   Files.writeString(output.resolve("counts.json"),encode(report)+"\n");
  }
  if(!failures.isEmpty())throw new AssertionError(String.join("\n",failures)+"\nArtifacts: "+output);
 }
 public static String runQuadraticControl(Path root,Path output)throws Exception {
  Files.createDirectories(output.getParent());Files.createDirectory(output);
  String source="#define MINIC_COMPLEXITY_QUADRATIC_CONTROL 1\n"+Files.readString(root.resolve("benchmarks/stl/probe-hooks.mh"))+"\n"+Files.readString(root.resolve("src/test/resources/cpp/library-complexity/sort.cpp"));
  Path program=output.resolve("program.cpp"),executable=output.resolve("program.exe");Files.writeString(program,source);
  var command=new ArrayList<>(List.of(CppDifferentialHarness.referenceCompiler(System.getenv())));command.addAll(CppDifferentialHarness.referenceFlags(output));command.addAll(List.of(program.toString(),"-o",executable.toString()));
  var compilation=BoundedProcess.run(command,root,"",Duration.ofSeconds(90),1048576);Files.writeString(output.resolve("compiler-stderr.txt"),compilation.stderr());require(compilation,"control compilation");
  var run=BoundedProcess.run(List.of(executable.toString()),output,"4096\n",Duration.ofSeconds(45),1048576);require(run,"control execution");Files.writeString(output.resolve("counts.txt"),run.stdout());return run.stdout();
 }
 private static String encode(Object value){
  if(value instanceof Map<?,?> map)return map.entrySet().stream().map(e->encode(e.getKey().toString())+":"+encode(e.getValue())).collect(java.util.stream.Collectors.joining(",","{","}"));
  if(value instanceof Collection<?> collection)return collection.stream().map(CppComplexityRunner::encode).collect(java.util.stream.Collectors.joining(",","[","]"));
  if(value instanceof Number||value instanceof Boolean)return value.toString();
  StringBuilder text=new StringBuilder("\"");for(char c:value.toString().toCharArray()){if(c=='"'||c=='\\')text.append('\\').append(c);else if(c<32)text.append(String.format("\\u%04x",(int)c));else text.append(c);}return text.append('"').toString();
 }
 private static void require(BoundedProcess.Result result,String stage){if(result.timedOut()||result.outputExceeded()||result.exitCode()!=0)throw new IllegalStateException(stage+" failed: "+result);if(!stage.contains("compilation")&&!stage.contains("worker")&&!result.stderr().isEmpty())throw new IllegalStateException(stage+" stderr: "+result.stderr());}
}
