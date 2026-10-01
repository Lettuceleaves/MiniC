package minic.compiler.ir.optimize;
import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class AdjacentResultForwardingTest {
    static final SourceRange R=new SourceRange(1,1,1,8);
    static final IrTemporary HOME=t("home"),RESULT=t("result"),OTHER=t("other");
    @Test void arithmeticUpdateWritesDirectlyToItsExistingHome(){
        var source=ir(block("entry",move(HOME,1),add(RESULT,HOME),copy(HOME,RESULT),ret(HOME)));
        var result=apply(source);assertEquals(3,code(result).size());
        var binary=(IrBinaryInstruction)code(result).get(1);assertEquals(HOME,binary.result());assertEquals(HOME,binary.left());
        assertEquals(4,code(source).size());assertEquals(R,binary.range());
    }
    @Test void unaryAndSameWidthCastResultsCanBeForwarded(){
        var source=ir(block("entry",move(HOME,1),new IrUnaryInstruction(RESULT,IrUnaryOperator.NEGATE,HOME,R),copy(OTHER,RESULT),
                new IrCastInstruction(RESULT,OTHER,R),copy(HOME,RESULT),ret(HOME)));
        // Repeated result definitions reject both sites, even though one is overwritten.
        assertSame(source,apply(source));
        var cast=new IrTemporary("cast",IrType.INT);
        var distinct=apply(ir(block("entry",move(HOME,1),new IrUnaryInstruction(RESULT,IrUnaryOperator.NEGATE,HOME,R),copy(OTHER,RESULT),
                new IrCastInstruction(cast,OTHER,R),copy(HOME,cast),ret(HOME))));
        assertEquals(4,code(distinct).size());assertEquals(OTHER,((IrUnaryInstruction)code(distinct).get(1)).result());
        assertEquals(HOME,((IrCastInstruction)code(distinct).get(2)).result());
    }
    @Test void copyChainCanForwardTheSamePureResultToTheLastHome(){
        var source=ir(block("entry",move(HOME,1),add(RESULT,HOME),copy(OTHER,RESULT),copy(HOME,OTHER),ret(HOME)));
        var result=apply(source);assertEquals(3,code(result).size());assertEquals(HOME,((IrBinaryInstruction)code(result).get(1)).result());
    }
    @Test void extraUsersRetainTheOriginalTemporary(){
        var source=ir(block("entry",move(HOME,1),add(RESULT,HOME),copy(HOME,RESULT),ret(RESULT)));
        assertSame(source,apply(source));
    }
    @Test void evenDeadTailUsesKeepTheirDefinition(){
        var source=ir(block("entry",move(HOME,1),add(RESULT,HOME),copy(HOME,RESULT),ret(HOME),copy(OTHER,RESULT)));
        assertSame(source,apply(source));
    }
    @Test void doesNotCrossCallsOrControlFlowEdges(){
        var source=ir(block("entry",move(HOME,1),add(RESULT,HOME),new IrCallInstruction(null,"effect",List.of(),false,R),copy(HOME,RESULT),ret(HOME)));
        assertSame(source,apply(source));
        var split=ir(block("entry",move(HOME,1),add(RESULT,HOME),new IrJumpInstruction("next",R)),block("next",copy(HOME,RESULT),ret(HOME)));
        assertSame(split,apply(split));
    }
    @Test void callResultsAreOutsideThisPureOperationRule(){
        var source=ir(block("entry",new IrCallInstruction(RESULT,"effect",List.of(),false,R),copy(HOME,RESULT),ret(HOME)));
        assertSame(source,apply(source));
    }
    @Test void branchInputAndReturnUseTheNewDestination(){
        var source=ir(block("entry",move(HOME,1),new IrBinaryInstruction(RESULT,IrBinaryOperator.LESS_THAN,HOME,new IrConstant(7),R),copy(HOME,RESULT),
                new IrBranchInstruction(HOME,"yes","no",R)),block("yes",ret(HOME)),block("no",ret(HOME)));
        var result=apply(source);assertEquals(3,code(result).size());assertEquals(HOME,((IrBinaryInstruction)code(result).get(1)).result());
    }
    private static IrResult apply(IrResult source){IrVerifier.verify(source);var result=new AdjacentResultForwardingPass().apply(source);IrVerifier.verify(result);return result;}
    private static IrResult ir(IrBlock...blocks){return new IrResult(List.of(new IrFunction("main",MiniType.INT,List.of(),false,List.of(blocks),R)),List.of(),List.of(),Set.of("effect"),Set.of(),Map.of(),null,"test",Map.of(),"main");}
    private static List<IrInstruction> code(IrResult result){return result.functions().getFirst().blocks().getFirst().instructions();}
    private static IrTemporary t(String name){return new IrTemporary(name,IrType.INT);}
    private static IrMoveInstruction move(IrTemporary to,int n){return new IrMoveInstruction(to,new IrConstant(n),R);}
    private static IrMoveInstruction copy(IrTemporary to,IrTemporary from){return new IrMoveInstruction(to,from,R);}
    private static IrBinaryInstruction add(IrTemporary to,IrTemporary from){return new IrBinaryInstruction(to,IrBinaryOperator.ADD,from,new IrConstant(1),R);}
    private static IrReturnInstruction ret(IrTemporary value){return new IrReturnInstruction(value,R);}
    private static IrBlock block(String name,IrInstruction...code){return new IrBlock(name,List.of(code));}
}
