package minic.compiler.ir.optimize;

import minic.SourceRange;
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
import minic.compiler.type.MiniType.TypeQualifier;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class CallSiteConstantInliningTest {
    private static final SourceRange R=new SourceRange(1,1,1,20);
    private static final IrParameter FLAG=new IrParameter("flag",MiniType.INT,IrType.INT,R);
    private static final IrTemporary X=new IrTemporary("x",IrType.INT),Y=new IrTemporary("y",IrType.INT);
    private static final IrGlobalAddress SINK=new IrGlobalAddress("sink");

    @Test void anOversizedCalleeBecomesSmallOnlyAtItsLiteralCallSite() {
        var helper=large(FLAG,List.of());
        assertEquals(27,code(helper).size());
        var source=program(helper,main(List.of(),call(X,c(1)),ret(X)));
        var result=run(source);
        assertEquals(0,calls(result));
        assertEquals(12,stores(result).size());
        // 13 specialized callee instructions: twelve effects and its return.
        // Emission adds one unchanged argument capture and the caller return.
        assertEquals(15,code(result.findFunction("main").orElseThrow()).size());
        assertEquals(1,result.findFunction("main").orElseThrow().blocks().size());
        assertTrue(stores(result).stream().allMatch(i->((IrConstant)i.value()).value()>0));
        assertSame(helper,result.findFunction("helper").orElseThrow(),"specialization never rewrites the ABI-visible original");
        assertEquals(1,calls(source));
        assertEquals(source.displayNames(),result.displayNames());
        assertEquals(result,run(source),"site selection and fresh names remain deterministic");
    }

    @Test void differentLiteralsSelectDifferentBodiesWithoutPollutingTheDynamicSite() {
        var source=program(large(FLAG,List.of()),main(List.of(FLAG),call(X,c(1)),call(Y,c(0)),call(X,FLAG.ref()),ret(Y)));
        var result=run(source);
        assertEquals(1,calls(result));
        assertEquals(24,stores(result).size());
        assertEquals(12,stores(result).stream().filter(i->((IrConstant)i.value()).value()>0).count());
        assertEquals(12,stores(result).stream().filter(i->((IrConstant)i.value()).value()<0).count());
    }

    @Test void narrowIntegerActualsUseTheirTargetWidth() {
        for(var parameter:List.of(new IrParameter("flag",MiniType.UNSIGNED_CHAR,IrType.UNSIGNED_CHAR,R),
                new IrParameter("flag",MiniType.CHAR,IrType.CHAR,R))) {
            var result=run(program(large(parameter,List.of()),main(List.of(),call(X,new IrConstant(256,parameter.type())),ret(X))));
            assertEquals(0,calls(result));
            assertTrue(stores(result).stream().allMatch(i->((IrConstant)i.value()).value()<0));
        }
    }

    @Test void nonCanonicalBoolIsNotGuessed() {
        var flag=new IrParameter("flag",MiniType.BOOL,IrType.BOOL,R);
        var source=program(large(flag,List.of()),main(List.of(),call(X,new IrConstant(2,IrType.BOOL)),ret(X)));
        assertSame(source,run(source));
    }

    @Test void pointerAndFloatingConstantsDoNotExpandTheIntegerOnlyScope() {
        var pointer=new IrParameter("flag",MiniType.INT.pointerTo(),IrType.POINTER,R);
        var floating=new IrParameter("flag",MiniType.DOUBLE,IrType.DOUBLE,R);
        var pointerSource=program(large(pointer,List.of()),main(List.of(),call(X,new IrConstant(0,IrType.POINTER)),ret(X)));
        var floatingSource=program(large(floating,List.of()),main(List.of(),call(X,new IrFloatConstant(1.0,IrType.DOUBLE)),ret(X)));
        assertSame(pointerSource,run(pointerSource));assertSame(floatingSource,run(floatingSource));
    }

    @Test void volatileOrAddressedFormalCannotBeReplacedByItsInitialLiteral() {
        var volatileFlag=new IrParameter("flag",MiniType.qualified(MiniType.INT,Set.of(TypeQualifier.VOLATILE)),IrType.INT,R);
        for(var helper:List.of(large(volatileFlag,List.of()),large(FLAG,List.of(new IrStorePointerInstruction(new IrParameterAddress("flag"),c(0),R))))) {
            var source=program(helper,main(List.of(),new IrCallInstruction(X,"helper",List.of(c(1)),helper.variadic(),R),ret(X)));
            assertSame(source,run(source));
        }
    }

    @Test void unprovedChecksRemainHardBoundariesEvenWhenAnotherArmWouldDisappear() {
        var local=new IrLocal("uninitialized","uninitialized",MiniType.INT,IrType.INT,4,4,R);
        for(var prefix:List.of(List.<IrInstruction>of(new IrCheckNonZeroInstruction(FLAG.ref(),R)),
                List.<IrInstruction>of(new IrDeclareLocalInstruction(local,R),new IrCheckInitializedInstruction(local,R)))) {
            for(int actual:List.of(0,1)) {
                var source=program(large(FLAG,prefix),main(List.of(),call(X,c(actual)),ret(X)));
                assertSame(source,run(source));
            }
        }
    }

    @Test void recursionAndVariadicIncomingHomesAreNotRescuedByLiterals() {
        var recursive=large(FLAG,List.of(new IrCallInstruction(Y,"helper",List.of(FLAG.ref()),false,R)));
        var base=large(FLAG,List.of());
        var variadic=new IrFunction(base.name(),base.returnType(),base.parameters(),true,base.blocks(),R);
        var incoming=large(FLAG,List.of(new IrAddressOfLocalInstruction(new IrTemporary("area",IrType.POINTER),IrLocal.incomingArgumentArea(0,R),R)));
        for(var helper:List.of(recursive,variadic,incoming)) {
            var source=program(helper,main(List.of(),new IrCallInstruction(X,"helper",List.of(c(1)),helper.variadic(),R),ret(X)));
            assertSame(source,run(source));
        }
    }

    @Test void allExistingInstructionGrowthFrameAndSiteBudgetsStillApply() {
        assertEquals(new SmallFunctionInliningPass.Limits(24,6,96,512,256,8),SmallFunctionInliningPass.Limits.defaults());
        var source=program(large(FLAG,List.of()),main(List.of(),call(X,c(1)),ret(X)));
        for(var limits:List.of(new SmallFunctionInliningPass.Limits(12,6,96,512,256,8),
                new SmallFunctionInliningPass.Limits(24,6,0,512,256,8),new SmallFunctionInliningPass.Limits(24,6,96,0,256,8),
                new SmallFunctionInliningPass.Limits(24,6,96,512,0,8),new SmallFunctionInliningPass.Limits(24,6,96,512,256,0))) {
            assertSame(source,new SmallFunctionInliningPass(limits).apply(source),limits.toString());
        }
        var two=program(large(FLAG,List.of()),main(List.of(),call(X,c(1)),call(Y,c(0)),ret(Y)));
        var limited=new SmallFunctionInliningPass(new SmallFunctionInliningPass.Limits(24,6,96,512,256,1)).apply(two);
        assertEquals(1,calls(limited));
    }

    @Test void specializedExpansionIsChargedItsActualSetupGrowthAndFrame() {
        var source=program(large(FLAG,List.of()),main(List.of(),call(X,c(1)),ret(X)));
        // Candidate is 13 instructions, expansion is 14 (including argument
        // capture), growth is 13 after replacing the call; frame is 8 + 15.
        for(var limits:List.of(new SmallFunctionInliningPass.Limits(13,1,12,512,256,8),
                new SmallFunctionInliningPass.Limits(13,1,96,12,256,8),new SmallFunctionInliningPass.Limits(13,1,96,512,22,8)))
            assertSame(source,new SmallFunctionInliningPass(limits).apply(source),limits.toString());
        var exact=new SmallFunctionInliningPass(new SmallFunctionInliningPass.Limits(13,1,13,13,23,1)).apply(source);
        IrVerifier.verify(exact);assertEquals(0,calls(exact));
        assertEquals(15,code(exact.findFunction("main").orElseThrow()).size());
    }

    @Test void aConstantMayRescueTheBlockBudgetButRemainingDynamicBranchesStillCount() {
        var other=new IrParameter("other",MiniType.INT,IrType.INT,R);
        var helper=new IrFunction("helper",MiniType.INT,List.of(FLAG,other),false,List.of(
                block("entry",new IrBranchInstruction(FLAG.ref(),"small","choice",R)),block("small",ret(c(8))),
                block("choice",new IrBranchInstruction(other.ref(),"left","right",R)),block("left",ret(c(1))),block("right",ret(c(2)))),R);
        var limits=new SmallFunctionInliningPass.Limits(24,1,96,512,256,8);
        for(int flag:List.of(0,1)) {
            var caller=main(List.of(other),new IrCallInstruction(X,"helper",List.of(c(flag),other.ref()),false,R),ret(X));
            var result=new SmallFunctionInliningPass(limits).apply(program(helper,caller));
            IrVerifier.verify(result);
            assertEquals(flag==1?0:1,calls(result));
        }
    }

    @Test void unusedActualsAreStillCapturedBeforeTheSelectedBodyAndParameterAbiIsUnchanged() {
        var helper=large(FLAG,List.of());
        var parameters=new ArrayList<>(helper.parameters());
        for(int i=1;i<6;i++)parameters.add(new IrParameter("p"+i,MiniType.INT,IrType.INT,R));
        helper=new IrFunction(helper.name(),helper.returnType(),parameters,false,helper.blocks(),R);
        var actuals=new ArrayList<IrValue>();actuals.add(c(1));for(int i=1;i<6;i++)actuals.add(FLAG.ref());
        var source=program(helper,main(List.of(FLAG),new IrCallInstruction(X,"helper",actuals,false,R),ret(X)));
        var result=run(source);
        assertEquals(0,calls(result));
        var body=code(result.findFunction("main").orElseThrow());
        assertTrue(body.subList(0,6).stream().allMatch(IrMoveInstruction.class::isInstance));
        assertEquals(5,body.subList(0,6).stream().filter(i->i instanceof IrMoveInstruction m&&m.value().equals(FLAG.ref())).count());
        assertEquals(6,result.findFunction("helper").orElseThrow().parameters().size());
    }

    @Test void alreadyEligibleGenericBodiesRetainTheExistingExpansionContract() {
        var helper=new IrFunction("helper",MiniType.INT,List.of(FLAG),false,List.of(block("entry",new IrBranchInstruction(FLAG.ref(),"yes","no",R)),block("yes",ret(c(1))),block("no",ret(c(0)))),R);
        var result=run(program(helper,main(List.of(),call(X,c(1)),ret(X))));
        assertEquals(2,code(result.findFunction("main").orElseThrow()).stream().filter(i->i instanceof IrMoveInstruction m&&m.result().equals(X)).count());
    }

    private static IrFunction large(IrParameter flag,List<IrInstruction> prefix) {
        var entry=new ArrayList<>(prefix);entry.add(new IrBranchInstruction(flag.ref(),"yes","no",R));
        var yes=new ArrayList<IrInstruction>();var no=new ArrayList<IrInstruction>();
        for(int i=1;i<=12;i++){yes.add(new IrStorePointerInstruction(SINK,c(i),R));no.add(new IrStorePointerInstruction(SINK,c(-i),R));}
        yes.add(ret(c(1)));no.add(ret(c(0)));
        return new IrFunction("helper",MiniType.INT,List.of(flag),false,List.of(new IrBlock("entry",entry),new IrBlock("yes",yes),new IrBlock("no",no)),R);
    }
    private static IrResult run(IrResult source){IrVerifier.verify(source);var result=new SmallFunctionInliningPass().apply(source);IrVerifier.verify(result);return result;}
    private static IrResult program(IrFunction...f){return new IrResult(List.of(f),List.of(),List.of(),Set.of(),Set.of("sink"),Map.of(),null,"source",Map.of("helper","helper"),"main");}
    private static IrFunction main(List<IrParameter> p,IrInstruction...code){return new IrFunction("main",MiniType.INT,p,false,List.of(block("entry",code)),R);}
    private static IrBlock block(String label,IrInstruction...code){return new IrBlock(label,List.of(code));}
    private static IrConstant c(int v){return new IrConstant(v);}
    private static IrCallInstruction call(IrTemporary result,IrValue actual){return new IrCallInstruction(result,"helper",List.of(actual),false,R);}
    private static IrReturnInstruction ret(IrValue v){return new IrReturnInstruction(v,R);}
    private static List<IrInstruction> code(IrFunction f){return f.blocks().stream().flatMap(b->b.instructions().stream()).toList();}
    private static long calls(IrResult ir){return code(ir.findFunction("main").orElseThrow()).stream().filter(IrCallInstruction.class::isInstance).count();}
    private static List<IrStorePointerInstruction> stores(IrResult ir){return code(ir.findFunction("main").orElseThrow()).stream().filter(IrStorePointerInstruction.class::isInstance).map(IrStorePointerInstruction.class::cast).toList();}
}
