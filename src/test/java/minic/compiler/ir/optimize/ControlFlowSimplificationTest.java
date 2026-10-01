package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class ControlFlowSimplificationTest {
    private static final SourceRange R=new SourceRange(2,1,2,9);
    private static final IrTemporary X=new IrTemporary("x",IrType.INT);
    private static IrBlock block(String name,IrInstruction... instructions){return new IrBlock(name,List.of(instructions));}
    private static IrJumpInstruction jump(String name){return new IrJumpInstruction(name,R);}
    private static IrReturnInstruction ret(){return new IrReturnInstruction(new IrConstant(7),R);}
    private static IrResult program(IrBlock... blocks){return new IrResult(List.of(new IrFunction("main",MiniType.INT,List.of(),false,List.of(blocks),R)),
            List.of(),List.of(),Set.of("effect"),Set.of(),Map.of(),null,"source",Map.of("main","main"));}
    private static IrResult run(IrResult original){return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new ControlFlowSimplificationPass())).apply(original).ir();}
    @Test void joinsLongChainsAndKeepsOriginalInstructionsRangesAndMetadata(){
        var effects=new IrCallInstruction(null,"effect",List.of(),false,R);
        var end=ret();
        var source=program(block("entry",jump("a")),block("a",effects,jump("b")),block("b",jump("c")),block("c",end));
        var result=run(source);
        assertEquals(List.of(block("entry",effects,end)),result.functions().getFirst().blocks());
        assertSame(effects,result.functions().getFirst().blocks().getFirst().instructions().getFirst());
        assertSame(end,result.functions().getFirst().blocks().getFirst().instructions().getLast());
        assertEquals("source",result.currentSubject());assertEquals(source.displayNames(),result.displayNames());
        assertEquals(4,source.functions().getFirst().blocks().size());assertSame(result,new ControlFlowSimplificationPass().apply(result));
    }
    @Test void multiPredecessorMergeRemainsAnIndependentBlock(){
        var source=program(block("entry",new IrBranchInstruction(new IrConstant(1),"yes","no",R)),
                block("yes",new IrMoveInstruction(X,new IrConstant(1),R),jump("merge")),
                block("no",new IrMoveInstruction(X,new IrConstant(2),R),jump("merge")),
                block("merge",new IrReturnInstruction(X,R)));
        assertSame(source,run(source));
    }
    @Test void loopHeaderWithBackEdgeIsNotAbsorbedIntoEntry(){
        var source=program(block("entry",jump("loop")),block("loop",new IrBranchInstruction(new IrConstant(1),"body","exit",R)),
                block("body",jump("loop")),block("exit",ret()));
        assertSame(source,run(source));
    }
    @Test void internalCycleMayCollapseButCannotDeleteOrRunItsEffectsTwice(){
        var effect=new IrCallInstruction(null,"effect",List.of(),false,R);
        var source=program(block("entry",jump("loop")),block("loop",effect,jump("tail")),block("tail",jump("loop")));
        var result=run(source);
        assertEquals(List.of(block("entry",jump("loop")),block("loop",effect,jump("loop"))),result.functions().getFirst().blocks());
    }
    @Test void neverAbsorbsEntryOrChangesFunctionEntryOrder(){
        var source=program(block("entry",jump("other")),block("other",jump("entry")));
        var result=run(source);
        assertEquals(List.of(block("entry",jump("entry"))),result.functions().getFirst().blocks());
    }
    @Test void preservesFaultChecksAndEffectsInTheirOriginalOrder(){
        var check=new IrCheckNonZeroInstruction(new IrConstant(0),R);
        var effect=new IrCallInstruction(null,"effect",List.of(),false,R);
        var source=program(block("entry",check,jump("tail")),block("tail",effect,ret()));
        assertEquals(List.of(check,effect,ret()),run(source).functions().getFirst().blocks().getFirst().instructions());
    }
    @Test void unreachablePredecessorsAlsoPreventAbsorption(){
        var source=program(block("entry",jump("merge")),block("unreachable",jump("merge")),block("merge",ret()));
        assertSame(source,run(source));
    }
    @Test void referencesInNonExecutableTailsCannotBecomeDangling(){
        var source=program(block("entry",jump("a")),block("a",jump("merge")),
                block("unreachable",ret(),jump("merge")),block("merge",ret()));
        var result=run(source);
        assertEquals(List.of(block("entry",jump("merge")),block("unreachable",ret(),jump("merge")),block("merge",ret())),
                result.functions().getFirst().blocks());
    }
    @Test void baselineLeavesChainsUntouched(){
        var source=program(block("entry",jump("tail")),block("tail",ret()));
        assertSame(source,IrOptimizationPipeline.forLevel(OptimizationLevel.BASELINE).apply(source).ir());
    }
}
