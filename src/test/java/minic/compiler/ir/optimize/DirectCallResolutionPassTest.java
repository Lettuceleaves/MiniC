package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DirectCallResolutionPassTest {
    static final SourceRange R = new SourceRange(1,1,1,30);
    static final SourceRange SITE = new SourceRange(5,1,5,30);
    static final IrTemporary RESULT = new IrTemporary("result", IrType.INT);
    static final IrParameter ARG = new IrParameter("argument", MiniType.INT, IrType.INT, R);
    static final IrConstant ZERO = new IrConstant(0);
    static final IrConstant ONE = new IrConstant(1);

    @Test void knownInternalAddressBecomesDirectWithoutChangingArgumentsResultsOrSource() {
        var call = new IrIndirectCallInstruction(RESULT,new IrFunctionAddress("helper"),List.of(ONE),false,SITE);
        var helper = function("helper",List.of(ARG),new IrReturnInstruction(ARG.ref(),R));
        var input = program(helper,function("main",List.of(),call,new IrReturnInstruction(RESULT,R)));
        var result = run(input);
        var actual = assertInstanceOf(IrCallInstruction.class,body(result).getFirst());
        assertEquals("helper",actual.calleeName());
        assertEquals(call.arguments(),actual.arguments()); assertSame(call.result(),actual.result());
        assertEquals(SITE,actual.range()); assertFalse(actual.variadic());
        assertSame(helper,result.functions().getFirst()); assertSame(call,body(input).getFirst());
        assertEquals(input.displayNames(),result.displayNames());
        assertEquals(input.entryFunction(),result.entryFunction());
        assertEquals(input.currentSubject(),result.currentSubject());
        assertSame(result,new DirectCallResolutionPass().apply(result));
    }

    @Test void knownExternalVariadicAddressKeepsTheVariadicAbiAndArgumentOrder() {
        var call = new IrIndirectCallInstruction(RESULT,new IrFunctionAddress("external"),List.of(ONE,ZERO),true,SITE);
        var result = run(program(function("main",List.of(),call,new IrReturnInstruction(RESULT,R))));
        var actual = assertInstanceOf(IrCallInstruction.class,body(result).getFirst());
        assertTrue(actual.variadic()); assertEquals(call.arguments(),actual.arguments());
        assertEquals(Set.of("external"),result.externalFunctionNames());
    }

    @Test void unknownLoadedOrMutableTargetsRemainIndirect() {
        var pointer = new IrParameter("target",MiniType.VOID.pointerTo(),IrType.POINTER,R);
        var dynamic = program(function("main",List.of(pointer),
                new IrIndirectCallInstruction(RESULT,pointer.ref(),List.of(),false,SITE),new IrReturnInstruction(RESULT,R)));
        assertSame(dynamic,run(dynamic));
        var temporary = new IrTemporary("address",IrType.POINTER);
        var copied = program(function("main",List.of(),new IrMoveInstruction(temporary,new IrFunctionAddress("external"),R),
                new IrIndirectCallInstruction(RESULT,temporary,List.of(),false,SITE),new IrReturnInstruction(RESULT,R)));
        assertSame(copied,run(copied));
    }

    @Test void discardedResultAndVoidCallRemainLegal() {
        var helper = new IrFunction("helper",MiniType.VOID,List.of(),false,
                List.of(new IrBlock("entry",List.of(new IrReturnInstruction(null,R)))),R);
        var result = run(program(helper,function("main",List.of(),
                new IrIndirectCallInstruction(null,new IrFunctionAddress("helper"),List.of(),false,SITE),new IrReturnInstruction(ZERO,R))));
        assertNull(assertInstanceOf(IrCallInstruction.class,body(result).getFirst()).result());
    }

    @Test void incompatibleFunctionPointerCastsAreNotAssignedAStrongerDirectSignature() {
        var helper = function("helper",List.of(ARG),new IrReturnInstruction(ARG.ref(),R));
        var wide = new IrTemporary("wide",IrType.LONG_LONG);
        var calls = List.of(
                new IrIndirectCallInstruction(RESULT,new IrFunctionAddress("helper"),List.of(),false,SITE),
                new IrIndirectCallInstruction(RESULT,new IrFunctionAddress("helper"),List.of(new IrConstant(1,IrType.LONG_LONG)),false,SITE),
                new IrIndirectCallInstruction(wide,new IrFunctionAddress("helper"),List.of(ONE),false,SITE),
                new IrIndirectCallInstruction(RESULT,new IrFunctionAddress("helper"),List.of(ONE),true,SITE));
        for(var call:calls){
            var input=program(helper,function("main",List.of(),call,new IrReturnInstruction(ZERO,R)));
            assertSame(input,run(input));
        }
    }

    @Test void legacyIntegerNullPointerArgumentIsStillCompatible() {
        var pointer = new IrParameter("p",MiniType.INT.pointerTo(),IrType.POINTER,R);
        var helper = function("helper",List.of(pointer),new IrReturnInstruction(ONE,R));
        var result = run(program(helper,function("main",List.of(),
                new IrIndirectCallInstruction(RESULT,new IrFunctionAddress("helper"),List.of(ZERO),false,SITE),new IrReturnInstruction(RESULT,R))));
        assertInstanceOf(IrCallInstruction.class,body(result).getFirst());
    }

    @Test void selfRecursionBecomesVisibleToTheExistingInliningRecursionGuard() {
        var input = program(function("main",List.of(),
                new IrIndirectCallInstruction(RESULT,new IrFunctionAddress("main"),List.of(),false,SITE),new IrReturnInstruction(RESULT,R)));
        var resolved = run(input);
        assertInstanceOf(IrCallInstruction.class,body(resolved).getFirst());
        assertSame(resolved,new SmallFunctionInliningPass().apply(resolved));
        assertSame(input,IrOptimizationPipeline.forLevel(OptimizationLevel.BASELINE).apply(input).ir());
    }

    static IrResult run(IrResult input){
        return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new DirectCallResolutionPass())).apply(input).ir();
    }
    static IrResult program(IrFunction... functions){
        return new IrResult(List.of(functions),List.of(),List.of(),Set.of("external"),Set.of(),Map.of(),null,"source identity",Map.of("helper","Box::helper"),"main");
    }
    static IrFunction function(String name,List<IrParameter> parameters,IrInstruction... body){
        return new IrFunction(name,MiniType.INT,parameters,false,List.of(new IrBlock("entry",List.of(body))),R);
    }
    static List<IrInstruction> body(IrResult ir){return ir.findFunction("main").orElseThrow().blocks().getFirst().instructions();}
}
