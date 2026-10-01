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
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class BlockCopyPropagationTest {
    private static final SourceRange R=new SourceRange(1,1,1,9);
    private static final IrTemporary X=t("x"),Y=t("y"),Z=t("z"),OUT=t("out");

    @Test void snapshotsOfMultiplyDefinedLoopHomesCanBeForwardedInsideOneIteration() {
        var source=ir(block("entry",move(X,c(0)),new IrJumpInstruction("loop",R)),
                block("loop",move(Y,X),add(OUT,Y,c(1)),move(X,OUT),new IrBranchInstruction(X,"loop","done",R)),block("done",ret(X)));
        var result=apply(source);
        assertEquals(X,((IrBinaryInstruction)code(result,1).get(1)).left());
        assertEquals(Y,((IrBinaryInstruction)code(source,1).get(1)).left());
    }
    @Test void transitiveCopyChainsResolveToTheCurrentSource() {
        var result=apply(ir(block("entry",move(X,c(7)),move(Y,X),move(Z,Y),ret(Z))));
        assertEquals(X,((IrReturnInstruction)code(result,0).getLast()).value());
    }
    @Test void overwritingTheSourceRetiresEverySnapshotAlias() {
        var result=apply(ir(block("entry",move(X,c(7)),move(Y,X),move(Z,Y),move(X,c(9)),add(OUT,Y,Z),ret(OUT))));
        var sum=(IrBinaryInstruction)code(result,0).get(4);
        assertEquals(Y,sum.left());assertEquals(Z,sum.right());
    }
    @Test void readsPrecedeResultKillsForInPlaceUpdates() {
        var result=apply(ir(block("entry",move(X,c(7)),move(Y,X),add(X,Y,c(1)),ret(Y))));
        assertEquals(X,((IrBinaryInstruction)code(result,0).get(2)).left());
        assertEquals(Y,((IrReturnInstruction)code(result,0).getLast()).value());
    }
    @Test void branchJoinDoesNotImportOtherBlocksCopies() {
        var source=ir(block("entry",move(X,c(7)),move(Y,X),new IrJumpInstruction("done",R)),block("done",ret(Y)));
        assertSame(source,apply(source));
    }
    @Test void callsCannotModifyUnaddressableTemporariesButTheirResultsKillAliases() {
        var result=apply(ir(block("entry",move(X,c(7)),move(Y,X),new IrCallInstruction(Z,"effect",List.of(Y),false,R),
                new IrCallInstruction(X,"effect",List.of(Y),false,R),ret(Y))));
        assertEquals(List.of(X),((IrCallInstruction)code(result,0).get(2)).arguments());
        assertEquals(List.of(X),((IrCallInstruction)code(result,0).get(3)).arguments());
        assertEquals(Y,((IrReturnInstruction)code(result,0).getLast()).value());
    }
    @Test void parameterSlotReadsAreNotRememberedAsTemporaryCopies() {
        var parameter=new IrParameter("p",MiniType.INT,IrType.INT,R);
        var function=new IrFunction("main",MiniType.INT,List.of(parameter),false,List.of(block("entry",move(X,parameter.ref()),ret(X))),R);
        var source=new IrResult(List.of(function));assertSame(source,apply(source));
    }
    @Test void widthChangingConversionsAreNotTreatedAsCopies() {
        var narrow=new IrTemporary("narrow",IrType.UNSIGNED_CHAR);
        var source=ir(block("entry",move(X,c(256)),new IrCastInstruction(narrow,X,R),new IrCastInstruction(OUT,narrow,R),ret(OUT)));
        assertSame(source,apply(source));
    }
    @Test void volatilePointerAccessKeepsItsEffectAndExactWidth() {
        var p=new IrTemporary("p",IrType.POINTER);var q=new IrTemporary("q",IrType.POINTER);
        var result=apply(ir(block("entry",move(p,new IrGlobalAddress("global")),move(q,p),
                new IrLoadPointerInstruction(X,q,true,R),new IrStorePointerInstruction(q,X,true,R),ret(X))));
        var load=(IrLoadPointerInstruction)code(result,0).get(2);var store=(IrStorePointerInstruction)code(result,0).get(3);
        assertEquals(p,load.address());assertTrue(load.volatileAccess());assertEquals(IrType.INT,load.result().type());
        assertEquals(p,store.address());assertTrue(store.volatileAccess());
    }
    @Test void unreachableBlocksAndTerminatorTailsStayUntouched() {
        var tail=add(OUT,Y,c(2));
        var source=ir(block("entry",move(X,c(7)),move(Y,X),ret(Y),tail),block("dead",move(Z,Y),ret(Z)));
        var result=apply(source);
        assertEquals(X,((IrReturnInstruction)code(result,0).get(2)).value());
        assertSame(tail,code(result,0).getLast());assertSame(source.functions().getFirst().blocks().get(1),result.functions().getFirst().blocks().get(1));
    }
    private static IrResult apply(IrResult source){IrVerifier.verify(source);var result=new BlockCopyPropagationPass().apply(source);IrVerifier.verify(result);return result;}
    private static IrResult ir(IrBlock... blocks){return new IrResult(List.of(new IrFunction("main",MiniType.INT,List.of(),false,List.of(blocks),R)),List.of(),List.of(),Set.of("effect"),Set.of("global"),Map.of(),null,"test",Map.of(),"main");}
    private static IrBlock block(String name,IrInstruction... code){return new IrBlock(name,List.of(code));}
    private static List<IrInstruction> code(IrResult result,int index){return result.functions().getFirst().blocks().get(index).instructions();}
    private static IrTemporary t(String name){return new IrTemporary(name,IrType.INT);}
    private static IrConstant c(int value){return new IrConstant(value);}
    private static IrMoveInstruction move(IrTemporary to,IrValue from){return new IrMoveInstruction(to,from,R);}
    private static IrBinaryInstruction add(IrTemporary to,IrValue a,IrValue b){return new IrBinaryInstruction(to,IrBinaryOperator.ADD,a,b,R);}
    private static IrReturnInstruction ret(IrValue value){return new IrReturnInstruction(value,R);}
}
