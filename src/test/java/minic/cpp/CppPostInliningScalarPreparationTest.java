package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.MemoryInstruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.optimize.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential") @Timeout(120)
final class CppPostInliningScalarPreparationTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs() { return Stream.concat(Stream.of(
            Arguments.of("pointer-advance", "void advance(int**p,int n){if(n)*p+=n;}int run(){int values[4]={1,2,3,4};int*middle=values;advance(&middle,2);return *middle;}",3,true),
            Arguments.of("repeated-local-lifetime", "void add(int*p){*p+=2;}int run(){int total=0;for(int i=0;i<3;++i){int local=i;add(&local);total+=local;}return total;}",9,true),
            Arguments.of("first-whole-write", "void assign(int*p){*p=9;}int run(){int value;assign(&value);return value;}",9,true),
            Arguments.of("volatile-control", "void add(volatile int*p){*p+=2;}int run(){volatile int value=4;add(&value);return value;}",6,false)
    ).flatMap(argument->Stream.of(LanguageMode.C,LanguageMode.CPP17_ALGORITHM).map(mode->{
        Object[] values=argument.get();return Arguments.of(mode,values[0],values[1],values[2],values[3]);
    })),Stream.of(Arguments.of(LanguageMode.CPP17_ALGORITHM,"actual-reference-helper",
            "void advance(int*&p,int n){if(n)p+=n;}int run(){int values[4]={1,2,3,4};int*middle=values;advance(middle,2);return *middle;}",3,true))); }

    @ParameterizedTest(name="{0}: {1}") @MethodSource("programs")
    void postInlinePreparationUnblocksOnlyProvenPrivateScalars(LanguageMode mode,String name,String body,int answer,boolean mustImprove) throws Exception {
        String text="#include <stdio.h>\n"+body+"\nint main(){printf(\"%d\\n\",run());return 0;}";
        String expected=answer+"\n";
        var source=new SourceFile(name+".cpp",text);
        var original=new CompilerApi(source,mode).runToIr();
        var originalFunctions=original.functions();
        var earlier=List.<IrPass>of(new DirectCallResolutionPass(),new PrivateAddressNormalizationPass(),
                new InitializedCheckEliminationPass(),new SmallFunctionInliningPass());
        var inlined=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,earlier).apply(original).ir();
        var prepared=new PostInliningScalarPreparationPass().apply(inlined);
        if(mustImprove)assertTrue(addresses(prepared)<addresses(inlined),"call expansion must expose an address that becomes private");
        else assertEquals(addresses(inlined),addresses(prepared),"volatile address must remain");
        var all=new java.util.ArrayList<>(earlier);all.add(new PostInliningScalarPreparationPass());all.add(new LocalScalarPromotionPass());
        all.add(new ConstantPropagationPass());all.add(new DeadCodeEliminationPass());
        var pipeline=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,all);
        var optimized=pipeline.apply(original).ir();
        var report=new CppDifferentialHarness(temporary.resolve("reference"),CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),mode).run(name,text,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,normalize(outcome.stdout()),report::describe));
        Path output=temporary.resolve("optimized");
        var assembler=new Assembler(original,pipeline);
        var object=new ObjBuilder(source,assembler,output,"program");
        var linker=new Linker(source,object,output,"program");
        new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
        var actual=BoundedProcess.run(List.of(output.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(15),65536);
        assertFalse(actual.timedOut());assertFalse(actual.outputExceeded());assertEquals(0,actual.exitCode(),actual::stderr);
        assertEquals(expected,normalize(actual.stdout()));
        var debug=DebugApi.fromIr(source,optimized,"");
        for(int steps=0;debug.canNext()&&steps<20000;steps++)debug.next();
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(expected,normalize(debug.current().runtime().stdout()));
        assertSame(originalFunctions,original.functions());
    }
    private static long addresses(IrResult ir) { return ir.findFunction("run").orElseThrow().blocks().stream().flatMap(b->b.instructions().stream()).filter(IrAddressOfLocalInstruction.class::isInstance).count(); }
    private static String normalize(String text) { return text.replace("\r\n","\n"); }
}
