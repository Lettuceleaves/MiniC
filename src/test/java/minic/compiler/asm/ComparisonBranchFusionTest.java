package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.ir.optimize.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class ComparisonBranchFusionTest {
    private static final SourceRange R=new SourceRange(1,0,1,8);
    private static final List<IrType> TYPES=List.of(IrType.values());

    @ParameterizedTest @EnumSource(value=IrBinaryOperator.class,names={"EQUAL","NOT_EQUAL","LESS_THAN","LESS_EQUAL","GREATER_THAN","GREATER_EQUAL"})
    void emitsDirectConditionsWithoutMaterializingComparisonOrBoolResults(IrBinaryOperator operator) {
        for(var type:TYPES)for(boolean cast:List.of(false,true)) {
            String text=emit(type,operator,cast,"none",OptimizationLevel.OPTIMIZED);
            assertFalse(text.contains("    set"),type+" "+operator+"\n"+text);
            assertTrue(text.contains(jump(type,operator)+" minic$compare$yes"),text);
            if(type.isFloatingScalar()) {
                if(operator==IrBinaryOperator.EQUAL)assertTrue(text.contains("jp minic$compare$no"),text);
                if(operator==IrBinaryOperator.NOT_EQUAL)assertTrue(text.contains("jp minic$compare$yes"),text);
                if(operator==IrBinaryOperator.LESS_THAN||operator==IrBinaryOperator.LESS_EQUAL)
                    assertTrue(text.contains((type==IrType.FLOAT?"ucomiss":"ucomisd")+" xmm1, xmm0"),text);
            }
        }
    }

    @Test void baselineKeepsMaterializedComparisonsAndBooleanNormalization() {
        for(var type:TYPES)assertTrue(emit(type,IrBinaryOperator.LESS_THAN,true,"none",OptimizationLevel.BASELINE).contains("set"));
    }

    @ParameterizedTest @EnumSource(value=IrBinaryOperator.class,names={"EQUAL","LESS_THAN"})
    void rejectsNonlocalMultipleUseRedefinedAndCallSeparatedChains(IrBinaryOperator operator) {
        for(String barrier:List.of("extra-compare-use","extra-bool-use","redefine","call","cross-block")) {
            String text=emit(IrType.INT,operator,true,barrier,OptimizationLevel.OPTIMIZED);
            assertTrue(text.contains(operator==IrBinaryOperator.EQUAL?"sete":"setl"),barrier+"\n"+text);
        }
    }

    private static String jump(IrType type,IrBinaryOperator operator) {
        boolean unsigned=type.isUnsignedInteger()||type.isFloatingScalar()||type==IrType.POINTER;
        return switch(operator){case EQUAL->"je";case NOT_EQUAL->"jne";
            case LESS_THAN->type.isFloatingScalar()?"ja":unsigned?"jb":"jl";
            case LESS_EQUAL->type.isFloatingScalar()?"jae":unsigned?"jbe":"jle";
            case GREATER_THAN->unsigned?"ja":"jg";case GREATER_EQUAL->unsigned?"jae":"jge";
            default->throw new IllegalArgumentException();};
    }

    private static String emit(IrType type,IrBinaryOperator operator,boolean cast,String barrier,OptimizationLevel level) {
        MiniType declared=switch(type){case BOOL->MiniType.BOOL;case CHAR->MiniType.CHAR;case SIGNED_CHAR->MiniType.SIGNED_CHAR;case UNSIGNED_CHAR->MiniType.UNSIGNED_CHAR;case SHORT->MiniType.SHORT;case UNSIGNED_SHORT->MiniType.UNSIGNED_SHORT;case LONG->MiniType.LONG;case UNSIGNED_LONG->MiniType.UNSIGNED_LONG;case POINTER->MiniType.INT.pointerTo();case INT->MiniType.INT;case UNSIGNED_INT->MiniType.UNSIGNED_INT;case LONG_LONG->MiniType.LONG_LONG;
            case UNSIGNED_LONG_LONG->MiniType.UNSIGNED_LONG_LONG;case FLOAT->MiniType.FLOAT;case DOUBLE->MiniType.DOUBLE;default->throw new IllegalArgumentException();};
        var value=new IrTemporary("comparison",IrType.INT);var truth=new IrTemporary("truth",IrType.BOOL);
        var left=new IrParameter("left",declared,type,R);var right=new IrParameter("right",declared,type,R);
        var comparison=new IrBinaryInstruction(value,operator,left.ref(),right.ref(),R);
        var code=new ArrayList<IrInstruction>();
        if(barrier.equals("redefine"))code.add(new IrMoveInstruction(value,new IrConstant(0),R));
        code.add(comparison);
        if(barrier.equals("call"))code.add(new IrCallInstruction(null,"effect",List.of(),false,R));
        var blocks=new ArrayList<IrBlock>();
        if(barrier.equals("cross-block")) {code.add(new IrJumpInstruction("condition",R));blocks.add(new IrBlock("entry",code));code=new ArrayList<>();}
        if(cast)code.add(new IrCastInstruction(truth,value,R));
        code.add(new IrBranchInstruction(cast?truth:value,"yes","no",R));
        blocks.add(new IrBlock(barrier.equals("cross-block")?"condition":"entry",code));
        var yes=new ArrayList<IrInstruction>();
        if(barrier.equals("extra-bool-use"))yes.add(new IrCastInstruction(new IrTemporary("extra",IrType.INT),truth,R));
        yes.add(new IrReturnInstruction(barrier.equals("extra-compare-use")?value:new IrConstant(7),R));
        blocks.add(new IrBlock("yes",yes));blocks.add(new IrBlock("no",List.of(new IrReturnInstruction(new IrConstant(3),R))));
        var function=new IrFunction("compare",MiniType.INT,List.of(left,right),false,blocks,R);
        var main=new IrFunction("main",MiniType.INT,List.of(),false,List.of(new IrBlock("entry",List.of(new IrReturnInstruction(new IrConstant(0),R)))),R);
        var ir=new IrResult(List.of(main,function),List.of(),Set.of("effect"));
        var assembler=new Assembler(ir,new IrOptimizationPipeline(level,List.of()));
        String all=assembler.assemble().text();assertTrue(assembler.succeeded(),()->assembler.errors().toString());assertSame(ir,assembler.input().irResult());
        return all.substring(all.indexOf("minic$compare PROC"),all.indexOf("minic$compare ENDP"));
    }
}
