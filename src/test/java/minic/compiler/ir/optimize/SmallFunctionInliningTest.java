package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class SmallFunctionInliningTest {
    private static final SourceRange R = new SourceRange(2, 1, 2, 20);
    private static final SourceRange CALL = new SourceRange(8, 1, 8, 20);
    private static final IrConstant ZERO = new IrConstant(0), ONE = new IrConstant(1);
    private static final IrTemporary X = new IrTemporary("%x", IrType.INT);
    private static final IrTemporary Y = new IrTemporary("%y", IrType.INT);
    private static final IrParameter P = new IrParameter("p", MiniType.INT, IrType.INT, R);

    @Test void straightLineExpansionKeepsOriginalFunctionsAndSourceMetadataImmutable() {
        var source = program(simple(), caller("main", List.of(), call(X, "helper", ONE), ret(X)));
        var before = List.copyOf(source.functions());
        var result = run(source);
        assertEquals(before, source.functions());
        assertSame(source.functions().getFirst(), result.functions().getFirst());
        assertEquals(1, fn(result, "main").blocks().size(), "straight-line expansion needs no extra branches");
        assertEquals(0, calls(fn(result, "main")));
        assertEquals(source.displayNames(), result.displayNames());
        assertEquals(source.stringData(), result.stringData());
        assertEquals(source.structLayouts(), result.structLayouts());
        assertTrue(instructions(fn(result, "main")).stream().allMatch(i -> i.range().equals(R) || i.range().equals(CALL)));
        assertEquals(result, run(source), "fresh names and budgets are deterministic");
    }

    @Test void repeatedNonSsaDefinitionsRemainOneRenamedTemporaryPerExpansion() {
        var helper = function("helper", List.of(P), block("entry", new IrBranchInstruction(P.ref(), "yes", "no", R)),
                block("yes", new IrMoveInstruction(X, ONE, R), jump("join")),
                block("no", new IrMoveInstruction(X, ZERO, R), jump("join")), block("join", ret(X)));
        var result = run(program(helper, caller("main", List.of(), call(X, "helper", ONE), call(Y, "helper", ZERO), ret(Y))));
        assertEquals(0, calls(fn(result, "main")));
        var definitions = new HashMap<String, Integer>();
        instructions(fn(result, "main")).stream().filter(IrMoveInstruction.class::isInstance).map(IrMoveInstruction.class::cast)
                .filter(i -> i.range().equals(R)).forEach(i -> definitions.merge(i.result().name(), 1, Integer::sum));
        assertEquals(2, definitions.values().stream().filter(n -> n == 2).count());
    }

    @Test void multipleReturnsJoinAtTheOriginalCallResultAndVoidReturnsJoinWithoutValue() {
        var helper = function("helper", List.of(P), block("entry", new IrBranchInstruction(P.ref(), "yes", "no", R)),
                block("yes", ret(ONE)), block("no", ret(ZERO)));
        var result = run(program(helper, caller("main", List.of(), call(X, "helper", ONE), ret(X))));
        assertEquals(2, instructions(fn(result, "main")).stream().filter(i -> i instanceof IrMoveInstruction m && m.result().equals(X)).count());
        assertEquals(1, instructions(fn(result, "main")).stream().filter(IrReturnInstruction.class::isInstance).count());
        var empty = new IrFunction("empty", MiniType.VOID, List.of(), false, List.of(block("entry", ret(null))), R);
        assertEquals(0, calls(fn(run(program(empty, caller("main", List.of(), call(null, "empty"), ret(ZERO)))), "main")));
    }

    @Test void mutableCallerParameterIsCapturedBeforeInlinedSideEffects() {
        var pointer = new IrParameter("pointer", MiniType.INT.pointerTo(), IrType.POINTER, R);
        var helper = function("helper", List.of(P, pointer), block("entry", new IrStorePointerInstruction(pointer.ref(), ZERO, R), ret(P.ref())));
        var result = run(program(helper, caller("main", List.of(P), call(X, "helper", P.ref(), new IrParameterAddress("p")), ret(X))));
        var body = instructions(fn(result, "main"));
        int write = indexOf(body, IrStorePointerInstruction.class);
        assertTrue(body.subList(0, write).stream().anyMatch(i -> i instanceof IrMoveInstruction m && m.value().equals(P.ref())));
        assertTrue(body.subList(write, body.size()).stream().flatMap(i -> IrValueUses.inputs(i).stream()).noneMatch(P.ref()::equals));
    }

    @Test void addressedFormalGetsItsOwnSlotAndReadsReloadAfterWrites() {
        var helper = function("helper", List.of(P), block("entry", new IrStorePointerInstruction(new IrParameterAddress("p"), ONE, R), ret(P.ref())));
        var result = run(program(helper, caller("main", List.of(P), call(X, "helper", P.ref()), ret(X))));
        var body = instructions(fn(result, "main"));
        assertEquals(1, body.stream().filter(IrDeclareLocalInstruction.class::isInstance).count());
        assertEquals(1, body.stream().filter(IrLoadLocalInstruction.class::isInstance).count());
        assertTrue(indexOf(body, IrLoadLocalInstruction.class) > indexOf(body, IrStorePointerInstruction.class));
        assertTrue(body.stream().flatMap(i -> IrValueUses.inputs(i).stream()).noneMatch(IrParameterAddress.class::isInstance));
    }

    @Test void backEdgesAndRepeatedLocalDeclarationsStayInsideEachExpandedInvocation() {
        var local = new IrLocal("%x", "local", MiniType.INT, IrType.INT, 4, 4, R);
        var helper = function("helper", List.of(P), block("entry", new IrDeclareLocalInstruction(local, R),
                        new IrMoveInstruction(X, P.ref(), R), jump("loop")),
                block("loop", new IrStoreLocalInstruction(local, X, R),
                        new IrBinaryInstruction(X, IrBinaryOperator.SUBTRACT, X, ONE, R), new IrBranchInstruction(X, "loop", "done", R)),
                block("done", new IrLoadLocalInstruction(Y, local, R), ret(Y)));
        var result = run(program(helper, caller("main", List.of(), call(X, "helper", ONE), call(Y, "helper", ONE), ret(Y))));
        assertEquals(0, calls(fn(result, "main")));
        var names = instructions(fn(result, "main")).stream().filter(IrDeclareLocalInstruction.class::isInstance)
                .map(IrDeclareLocalInstruction.class::cast).map(i -> i.local().name()).toList();
        assertEquals(2, new HashSet<>(names).size());
    }

    @Test void preservesCalleeTrapReturnSemanticsByDecliningCheckedFunctions() {
        var zeroCheck = function("helper", List.of(P), block("entry", new IrCheckNonZeroInstruction(P.ref(), R), ret(P.ref())));
        var local = new IrLocal("local", "local", MiniType.INT, IrType.INT, 4, 4, R);
        var initCheck = function("helper", List.of(), block("entry", new IrDeclareLocalInstruction(local, R), new IrCheckInitializedInstruction(local, R), ret(ONE)));
        for (var helper : List.of(zeroCheck, initCheck)) {
            var source = program(helper, caller("main", List.of(), call(X, "helper", helper.parameters().isEmpty() ? new IrValue[0] : new IrValue[]{ONE}), ret(X)));
            assertSame(source, run(source));
        }
    }

    @Test void skipsSelfAndMutualRecursionVariadicsAndIndirectCalleeBodies() {
        var a = caller("a", List.of(P), call(X, "b", P.ref()), ret(X));
        var b = caller("b", List.of(P), call(X, "a", P.ref()), ret(X));
        var source = program(a, b, caller("main", List.of(), call(X, "a", ONE), ret(X)));
        assertSame(source, run(source));
        var recursive = caller("helper", List.of(P), call(X, "helper", P.ref()), ret(X));
        assertSame(recursive, run(program(recursive)).functions().getFirst());
        var variadic = new IrFunction("helper", MiniType.INT, List.of(P), true, List.of(block("entry", ret(P.ref()))), R);
        var variadicSource = program(variadic, caller("main", List.of(), new IrCallInstruction(X,"helper",List.of(ONE),true,CALL),ret(X)));
        assertSame(variadicSource, run(variadicSource));
        var indirect = function("helper", List.of(), block("entry",new IrIndirectCallInstruction(X,new IrFunctionAddress("external"),List.of(),false,R),ret(X)));
        var indirectSource = program(indirect, caller("main",List.of(),call(X,"helper"),ret(X)));
        assertSame(indirectSource,run(indirectSource));
    }

    @Test void newlyClonedCallsAreNotRecursivelyExpandedInTheSamePass() {
        var wrapper = caller("wrapper", List.of(P), call(X, "helper", P.ref()), ret(X));
        var result = run(program(simple(), wrapper, caller("main", List.of(), call(X, "wrapper", ONE), ret(X))));
        assertEquals(1, calls(fn(result, "main")));
        assertTrue(instructions(fn(result, "main")).stream().anyMatch(i -> i instanceof IrCallInstruction c && c.calleeName().equals("helper")));
    }

    @Test void growthAndFrameBudgetsAreHardLimitsAndDecliningDoesNotMutateInput() {
        var source = program(simple(), caller("main", List.of(), call(X, "helper", ONE), call(Y, "helper", ONE), ret(Y)));
        assertSame(source, run(source,new SmallFunctionInliningPass.Limits(1,6,96,512,256,8)));
        assertSame(source, run(source,new SmallFunctionInliningPass.Limits(24,6,0,0,256,8)));
        assertSame(source, run(source,new SmallFunctionInliningPass.Limits(24,6,96,512,0,8)));
        assertSame(source, run(source,new SmallFunctionInliningPass.Limits(24,6,96,512,256,0)));
        var result = run(source,new SmallFunctionInliningPass.Limits(24,6,96,512,256,1));
        assertEquals(1,calls(fn(result,"main")));
        assertTrue(instructions(fn(result,"main")).size() - instructions(fn(source,"main")).size() <= 96);
    }

    @Test void worksWithRealIrAndLeavesTheExplicitBaselinePipelineUnchanged() {
        var source = new CompilerApi(new SourceFile("inline.c","int twice(int x){return x*2;} int main(){return twice(3)-6;}")).runToIr();
        assertEquals(0,calls(fn(run(source),"main")));
        var baseline=IrOptimizationPipeline.forLevel(OptimizationLevel.BASELINE).apply(source);
        assertSame(source,baseline.ir());
        assertTrue(baseline.passNames().isEmpty());
    }

    @Test void unusedCallResultStillPerformsTheVolatileFormalReturnRead() {
        var original = new CompilerApi(new SourceFile("volatile.c","int read(volatile int value){return value;} int main(){read(3);return 0;}")).runToIr();
        var result = run(original);
        assertEquals(0,calls(fn(result,"main")));
        assertTrue(instructions(fn(result,"main")).stream().anyMatch(i -> i instanceof IrLoadLocalInstruction l && l.volatileAccess()));
    }

    @Test void generatedNamesAvoidExistingCallerLocalsTempsAndLabels() {
        var occupied = new IrTemporary("__inline_0",IrType.INT);
        var caller = function("main",List.of(),block("__inline_1",new IrMoveInstruction(occupied,ONE,R),call(X,"helper",occupied),ret(X)));
        var result = run(program(simple(),caller));
        assertEquals(0,calls(fn(result,"main")));
        assertEquals(1,instructions(fn(result,"main")).stream().filter(i -> occupied.equals(IrValueUses.result(i))).count());
    }

    @Test void theModuleGrowthBudgetIsSharedAcrossFunctions() {
        var input=program(simple(),caller("first",List.of(),call(X,"helper",ONE),ret(X)),caller("second",List.of(),call(X,"helper",ONE),ret(X)));
        var result=run(input,new SmallFunctionInliningPass.Limits(24,6,96,2,256,8));
        assertEquals(0,calls(fn(result,"first")));
        assertEquals(1,calls(fn(result,"second")));
        assertEquals(2,result.functions().stream().mapToInt(f->instructions(f).size()).sum()-input.functions().stream().mapToInt(f->instructions(f).size()).sum());
    }

    @Test void neverReturningCalleeKeepsItsCallResultDefinitionForUnreachableCallerUses() {
        var loop=function("helper",List.of(),block("entry",jump("entry")));
        var source=program(loop,caller("main",List.of(),call(X,"helper"),ret(X)));
        assertSame(source,run(source));
    }

    @Test void runtimeArgumentAreaAndExternalOrIndirectSitesRemainRealCalls() {
        var area=IrLocal.incomingArgumentArea(0,R);
        var address=new IrTemporary("address",IrType.POINTER);
        var helper=function("helper",List.of(),block("entry",new IrAddressOfLocalInstruction(address,area,R),ret(ZERO)));
        var input=program(helper,caller("main",List.of(),call(X,"helper"),call(Y,"external"),
                new IrIndirectCallInstruction(X,new IrFunctionAddress("helper"),List.of(),false,CALL),ret(X)));
        assertSame(input,run(input));
    }

    @Test void unreachableCalleeTailsAreNotClonedAndOriginalDeadCallerSitesStayUntouched() {
        var helper=function("helper",List.of(),block("entry",ret(ONE),call(null,"external")),block("dead",call(null,"external"),ret(ZERO)));
        var dead=call(Y,"helper");
        var input=program(helper,caller("main",List.of(),call(X,"helper"),ret(X),dead));
        var result=run(input);
        assertEquals(1,calls(fn(result,"main")));
        assertSame(dead,instructions(fn(result,"main")).getLast());
    }

    @Test void loopBackEdgesReloadAddressMutatedFormalAndPreserveNonSsaCounterDefinitions() {
        var helper=function("helper",List.of(P),block("entry",jump("loop")),
                block("loop",new IrBinaryInstruction(X,IrBinaryOperator.SUBTRACT,P.ref(),ONE,R),
                        new IrStorePointerInstruction(new IrParameterAddress("p"),X,R),new IrBranchInstruction(P.ref(),"loop","done",R)),
                block("done",ret(P.ref())));
        var result=run(program(helper,caller("main",List.of(),call(X,"helper",new IrConstant(3)),ret(X))));
        assertEquals(0,calls(fn(result,"main")));
        var body=instructions(fn(result,"main"));
        assertEquals(3,body.stream().filter(IrLoadLocalInstruction.class::isInstance).count());
        var flow=IrControlFlow.analyze(fn(result,"main"));
        assertTrue(fn(result,"main").blocks().stream().anyMatch(b->flow.successors(b.label()).contains(b.label())));
    }

    @Test void exactCalleeBlockAndAdditionalFrameThresholdsControlEligibility() {
        var input=program(simple(),caller("main",List.of(),call(X,"helper",ONE),ret(X)));
        assertSame(input,run(input,new SmallFunctionInliningPass.Limits(2,1,96,512,30,8)));
        assertEquals(0,calls(fn(run(input,new SmallFunctionInliningPass.Limits(2,1,96,512,31,8)),"main")));
        var branched=function("helper",List.of(P),block("entry",new IrBranchInstruction(P.ref(),"yes","no",R)),block("yes",ret(ONE)),block("no",ret(ZERO)));
        var branchInput=program(branched,caller("main",List.of(),call(X,"helper",ONE),ret(X)));
        assertSame(branchInput,run(branchInput,new SmallFunctionInliningPass.Limits(24,2,96,512,256,8)));
        assertEquals(0,calls(fn(run(branchInput,new SmallFunctionInliningPass.Limits(24,3,96,512,256,8)),"main")));
    }

    private static IrResult run(IrResult source) { return run(source,SmallFunctionInliningPass.Limits.defaults()); }
    private static IrResult run(IrResult source,SmallFunctionInliningPass.Limits limits) {
        return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new SmallFunctionInliningPass(limits))).apply(source).ir();
    }
    private static IrFunction simple() { return function("helper",List.of(P),block("entry",new IrBinaryInstruction(X,IrBinaryOperator.ADD,P.ref(),ONE,R),ret(X))); }
    private static IrResult program(IrFunction... functions) { return new IrResult(List.of(functions),List.of(),Set.of("external")); }
    private static IrFunction function(String name,List<IrParameter> params,IrBlock... blocks) { return new IrFunction(name,MiniType.INT,params,false,List.of(blocks),R); }
    private static IrFunction caller(String name,List<IrParameter> params,IrInstruction... body) { return function(name,params,block("entry",body)); }
    private static IrBlock block(String label,IrInstruction... body) { return new IrBlock(label,List.of(body)); }
    private static IrCallInstruction call(IrTemporary result,String name,IrValue... args) { return new IrCallInstruction(result,name,List.of(args),false,CALL); }
    private static IrReturnInstruction ret(IrValue value) { return new IrReturnInstruction(value,R); }
    private static IrJumpInstruction jump(String label) { return new IrJumpInstruction(label,R); }
    private static IrFunction fn(IrResult ir,String name) { return ir.functions().stream().filter(f->f.name().equals(name)).findFirst().orElseThrow(); }
    private static List<IrInstruction> instructions(IrFunction function) { return function.blocks().stream().flatMap(b->b.instructions().stream()).toList(); }
    private static long calls(IrFunction function) { return instructions(function).stream().filter(IrCallInstruction.class::isInstance).count(); }
    private static int indexOf(List<IrInstruction> body,Class<?> type) { for(int i=0;i<body.size();i++)if(type.isInstance(body.get(i)))return i; throw new AssertionError(type); }
}
