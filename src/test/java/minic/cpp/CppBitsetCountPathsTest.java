package minic.cpp;
import minic.compiler.LanguageMode;
import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Library-only count/all acceptance and explicit macro-path evidence. No timing assertions. */
@Tag("stl-contract") @Timeout(600)
final class CppBitsetCountPathsTest {
 @TempDir Path temporary;
 static Stream<Arguments> modes(){return Stream.of(false,true).flatMap(fallback->Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED).map(level->Arguments.of(fallback,level)));}
 @ParameterizedTest(name="forced Kernighan={0} native={1}") @MethodSource("modes")
 void bothCountPathsMatchIndependentBitsOnEveryBoundary(boolean fallback,OptimizationLevel level)throws Exception {
  String body;try(var input=getClass().getResourceAsStream("/cpp/library-bitset-count/count-paths.cpp")){assertNotNull(input);body=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
  String source=(fallback?"#define MINIC_BITSET_FORCE_KERNIGHAN 1\n":"")+body;
  var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(180),Duration.ofSeconds(180),20_000_000,1_048_576);
  Path output=output("boundaries-"+fallback+"-"+level);
  var result=new CppDifferentialHarness(output,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,LanguageMode.CPP17_ALGORITHM,level).run("bitset-count-"+fallback+"-"+level,source,"");
  var own=CppOwnLibraryReference.run(output,source,"",limits);
  Files.writeString(output.resolve("result.txt"),result.describe()+"\nOWN="+own);
  assertAll(()->assertTrue(result.passed(),result::describe),()->assertTrue(own.passed(),own::toString));
  for(var outcome:result.outcomes().values()){assertEquals("bitset count paths ok\n",outcome.stdout().replace("\r\n","\n"),result::describe);assertEquals("",outcome.stderr());}
  assertEquals("bitset count paths ok\n",own.stdout().replace("\r\n","\n"),own::toString);assertEquals("",own.stderr());
 }

 @ParameterizedTest @EnumSource(OptimizationLevel.class)
 void macroSelectsRealSwarOrKernighanAssemblyAndAllNeverCallsCount(OptimizationLevel level)throws Exception {
  String body=resource("implementation-paths.cpp");
  for(boolean fallback:List.of(false,true)){
   Path output=output("assembly-"+fallback+"-"+level);
   String source=(fallback?"#define MINIC_BITSET_FORCE_KERNIGHAN 1\n":"")+body;
   var api=new CompilerApi(new SourceFile(output.resolve("program.cpp").toString(),source),LanguageMode.CPP17_ALGORITHM,level);
   Files.writeString(output.resolve("program.cpp"),source);
   var assembler=api.stages().stream().filter(Assembler.class::isInstance).map(Assembler.class::cast).findFirst().orElseThrow();
   api.runThrough(assembler);
   assertTrue(assembler.succeeded(),()->api.stages().stream().flatMap(stage->stage.errors().stream()).toList().toString());
   var ir=api.stages().stream().filter(IrLowerer.class::isInstance).map(IrLowerer.class::cast).findFirst().orElseThrow().result();
   String preprocessed=api.stages().stream().filter(Preprocessor.class::isInstance).map(Preprocessor.class::cast).findFirst().orElseThrow().preprocessResult().sourceFile().content();
   String assembly=assembler.result().text();
   Files.writeString(output.resolve("preprocessed.cpp"),preprocessed);Files.writeString(output.resolve("program.asm"),assembly);
   IrFunction count=method(ir,"count"),all=method(ir,"all");String countAsm=assemblyOf(assembly,count),allAsm=assemblyOf(assembly,all);
   Files.writeString(output.resolve("count.asm"),countAsm);Files.writeString(output.resolve("all.asm"),allAsm);
   // The default source must actually expand into unsigned SWAR, and the macro
   // must remove those arithmetic constants from both source and emitted code.
   assertEquals(!fallback,preprocessed.contains("0x5555555555555555ULL"));
   for(String constant:List.of("6148914691236517205","3689348814741910323","1085102592571150095","72340172838076673"))
    assertEquals(!fallback,countAsm.contains(constant),"SWAR constant "+constant+"; fallback="+fallback+"\n"+countAsm);
   if(!fallback)assertTrue(countAsm.contains("imul "),countAsm);
   // The retained fallback is the original clear-lowest-set-bit loop.
   assertEquals(fallback,preprocessed.replaceAll("\\s+","").contains("while(value){value&=value-1ULL;++count;}"));
   var calls=count.blocks().stream().flatMap(block->block.instructions().stream()).filter(IrCallInstruction.class::isInstance).map(IrCallInstruction.class::cast).toList();
   assertTrue(calls.isEmpty(),"The entire word loop has no helper calls: "+calls);
   assertFalse(countAsm.contains("call "),countAsm);
   assertTrue(all.blocks().stream().flatMap(block->block.instructions().stream()).noneMatch(IrCallInstruction.class::isInstance),"all compares words directly");
   assertFalse(allAsm.contains("call "),allAsm);
  }
 }

 @ParameterizedTest @ValueSource(strings={"library-containers/proxy-bits.cpp","library-review/bitset-stream-npos.cpp","library-noexcept/bit-proxy-contract.cpp"})
 void existingBitsetContractsRetainBehavior(String fixture)throws Exception {
  String source;try(var input=getClass().getResourceAsStream("/cpp/"+fixture)){assertNotNull(input);source=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
  String stdin=fixture.contains("stream")?"x 101q 11":"";Path output=output("existing-"+fixture.replace('/','-'));
  var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(180),Duration.ofSeconds(120),20_000_000,1_048_576);
  var result=new CppDifferentialHarness(output,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,LanguageMode.CPP17_ALGORITHM,OptimizationLevel.OPTIMIZED).run(fixture,source,stdin);
  var own=CppOwnLibraryReference.run(output,source,stdin,limits);Files.writeString(output.resolve("result.txt"),result.describe()+"\nOWN="+own);
  assertAll(()->assertTrue(result.passed(),result::describe),()->assertTrue(own.passed(),own::toString));
  assertEquals(result.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"),own.stdout().replace("\r\n","\n"));
 }
 @ParameterizedTest @ValueSource(strings={"18446744073709551615ULL","18446744073709551553ULL","18446744073709551552ULL"})
 void oversizedBitsetsReachTheExistingArrayBoundLimitInsteadOfWrapping(String size){
  // This is the MiniC algorithm-profile layout limit, not an ISO invalidity
  // assertion. Never execute such an object or demand that G++ reject its type.
  String source="#include <bitset>\nint main(){std::bitset<"+size+"> value;return sizeof(value)==8;}";
  var api=new CompilerApi(new SourceFile("oversized-bitset.cpp",source),LanguageMode.CPP17_ALGORITHM);
  var semantic=api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
  api.runThrough(semantic);
  assertFalse(semantic.succeeded(),"An oversized bitset must not silently become a single-word object");
  String diagnostics=api.stages().stream().flatMap(stage->stage.errors().stream()).map(error->error.message()).collect(java.util.stream.Collectors.joining("\n"));
  assertTrue(diagnostics.contains("Array bound must be in 1..2147483647"),diagnostics);
 }
 private static IrFunction method(IrResult ir,String name){return ir.functions().stream().filter(function->ir.displayName(function.name()).contains("bitset")&&ir.displayName(function.name()).endsWith("::"+name)).findFirst().orElseThrow(()->new AssertionError("Missing "+name+" in "+ir.functions().stream().map(function->ir.displayName(function.name())).filter(display->display.contains("bitset")).toList()));}
 private static String assemblyOf(String assembly,IrFunction function){
  String label="minic$"+function.name(),start=label+" PROC",end=label+" ENDP";int from=assembly.indexOf(start),to=assembly.indexOf(end,from);
  assertTrue(from>=0&&to>from,"Missing function assembly: "+label);return assembly.substring(from,to+end.length());
 }
 private String resource(String name)throws Exception {try(var input=getClass().getResourceAsStream("/cpp/library-bitset-count/"+name)){assertNotNull(input);return new String(input.readAllBytes(),StandardCharsets.UTF_8);}}
 private Path output(String name)throws Exception {String explicit=System.getProperty("minic.bitset.paths.output");Path root=explicit==null?temporary:Path.of(explicit);Path output=root.resolve(name);Files.createDirectories(output);return output;}
}
