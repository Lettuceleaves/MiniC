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

final class PostInliningScalarPreparationTest {
    private static final SourceRange R=new SourceRange(1,1,1,9);
    private static final IrLocal LOCAL=new IrLocal("value","value",MiniType.INT,IrType.INT,4,4,R);
    private static final IrTemporary ADDRESS=new IrTemporary("address",IrType.POINTER), X=new IrTemporary("x",IrType.INT), Y=new IrTemporary("y",IrType.INT);
    private static final IrParameter REF=new IrParameter("ref",MiniType.INT.pointerTo(),IrType.POINTER,R);

    @Test void referenceHelperEscapeBecomesPrivateAfterInliningAndThenPromotable() {
        var original=program(caller(true,false,false),increment(false));
        assertSame(original,new PrivateAddressNormalizationPass().apply(original));
        var inlined=inline(original);
        assertEquals(1,count(inlined,IrAddressOfLocalInstruction.class));
        var prepared=prepare(inlined);
        assertEquals(0,count(prepared,IrAddressOfLocalInstruction.class));
        assertEquals(0,count(prepared,IrCheckInitializedInstruction.class));
        var promoted=new LocalScalarPromotionPass().apply(prepared);
        IrVerifier.verify(promoted);
        assertEquals(0,count(promoted,IrDeclareLocalInstruction.class));
        assertEquals(1,count(original,IrAddressOfLocalInstruction.class));
    }

    @Test void pointerAdvanceHelperUnblocksAPointerLocalWithoutTreatingItsPointeeAsTheSlot() {
        var local=new IrLocal("middle","middle",MiniType.INT.pointerTo(),IrType.POINTER,8,8,R);
        var ref=new IrParameter("ref",MiniType.INT.pointerTo().pointerTo(),IrType.POINTER,R);
        var old=new IrTemporary("old",IrType.POINTER);var advanced=new IrTemporary("advanced",IrType.POINTER);
        var helper=function("advance",List.of(ref),new IrLoadPointerInstruction(old,ref.ref(),R),
                new IrElementAddressInstruction(advanced,old,c(2),MiniType.INT,4,R),new IrStorePointerInstruction(ref.ref(),advanced,R),ret(null));
        var caller=function("main",List.of(),new IrDeclareLocalInstruction(local,R),new IrStoreLocalInstruction(local,new IrGlobalAddress("array"),R),
                new IrAddressOfLocalInstruction(ADDRESS,local,R),new IrCallInstruction(null,"advance",List.of(ADDRESS),false,R),
                new IrCheckInitializedInstruction(local,R),new IrLoadLocalInstruction(old,local,R),new IrLoadPointerInstruction(X,old,R),ret(X));
        var prepared=prepare(inline(program(caller,helper)));
        assertEquals(0,count(prepared,IrAddressOfLocalInstruction.class));
        assertEquals(1,count(prepared,IrLoadPointerInstruction.class),"actual array-element read is retained");
        assertEquals(1,count(prepared,IrElementAddressInstruction.class));
    }

    @Test void remainingExternalEscapeKeepsTheAddressAndPointerOperations() {
        var prepared=prepare(inline(program(caller(true,true,false),increment(false))));
        assertEquals(1,count(prepared,IrAddressOfLocalInstruction.class));
        assertEquals(1,count(prepared,IrLoadPointerInstruction.class));
        assertEquals(1,count(prepared,IrStorePointerInstruction.class));
    }

    @Test void volatileHelperAccessCannotBeNormalized() {
        var prepared=prepare(inline(program(caller(true,false,false),increment(true))));
        assertEquals(1,count(prepared,IrAddressOfLocalInstruction.class));
        assertTrue(code(prepared).stream().anyMatch(i->i instanceof IrLoadPointerInstruction p&&p.volatileAccess()));
    }

    @Test void unknownInitialValueRetainsItsReadAndDiagnosticBoundary() {
        var prepared=prepare(inline(program(caller(false,false,true),increment(false))));
        assertEquals(1,count(prepared,IrAddressOfLocalInstruction.class));
        assertEquals(1,count(prepared,IrLoadPointerInstruction.class));
        assertTrue(count(prepared,IrCheckInitializedInstruction.class)>0);
    }

    @Test void aProvenWholeFirstWriteBecomesInitializedBeforeTheCallerCheck() {
        var helper=function("increment",List.of(REF),new IrStorePointerInstruction(REF.ref(),c(9),R),ret(null));
        var prepared=prepare(inline(program(caller(false,false,false),helper)));
        assertEquals(0,count(prepared,IrAddressOfLocalInstruction.class));
        assertEquals(0,count(prepared,IrCheckInitializedInstruction.class));
        assertEquals(1,count(prepared,IrStoreLocalInstruction.class));
    }

    @Test void stagesHaveDistinctNamesAndPreserveOriginalMetadata() {
        var original=program(caller(true,false,false),increment(false));
        var pipeline=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new PrivateAddressNormalizationPass(),
                new InitializedCheckEliminationPass(),new SmallFunctionInliningPass(),new PostInliningScalarPreparationPass(),new LocalScalarPromotionPass()));
        var result=pipeline.apply(original).ir();
        assertEquals(original.displayNames(),result.displayNames());
        assertEquals(original.entryFunction(),result.entryFunction());
        assertEquals(original.currentSubject(),result.currentSubject());
        assertEquals(1,count(original,IrAddressOfLocalInstruction.class));
        assertEquals(0,count(result,IrAddressOfLocalInstruction.class));
    }

    private static IrFunction caller(boolean initialized,boolean escape,boolean checkBefore) {
        var code=new ArrayList<IrInstruction>();code.add(new IrDeclareLocalInstruction(LOCAL,R));
        if(initialized)code.add(new IrStoreLocalInstruction(LOCAL,c(4),R));
        code.add(new IrAddressOfLocalInstruction(ADDRESS,LOCAL,R));
        if(checkBefore)code.add(new IrCheckInitializedInstruction(LOCAL,R));
        code.add(new IrCallInstruction(null,"increment",List.of(ADDRESS),false,R));
        if(escape)code.add(new IrCallInstruction(null,"external",List.of(ADDRESS),false,R));
        code.add(new IrCheckInitializedInstruction(LOCAL,R));code.add(new IrLoadLocalInstruction(X,LOCAL,R));code.add(ret(X));
        return function("main",List.of(),code.toArray(IrInstruction[]::new));
    }
    private static IrFunction increment(boolean vol) { return function("increment",List.of(REF),new IrLoadPointerInstruction(X,REF.ref(),vol,R),new IrBinaryInstruction(Y,IrBinaryOperator.ADD,X,c(1),R),new IrStorePointerInstruction(REF.ref(),Y,vol,R),ret(null)); }
    private static IrResult inline(IrResult source) { IrVerifier.verify(source);var result=new SmallFunctionInliningPass().apply(source);IrVerifier.verify(result);return result; }
    private static IrResult prepare(IrResult source) { var result=new PostInliningScalarPreparationPass().apply(source);IrVerifier.verify(result);return result; }
    private static IrResult program(IrFunction...functions) { return new IrResult(List.of(functions),List.of(),List.of(),Set.of("external"),Set.of("array"),Map.of(),null,"subject",Map.of("main","main"),"main"); }
    private static IrFunction function(String name,List<IrParameter> parameters,IrInstruction...code) { return new IrFunction(name,name.equals("main")?MiniType.INT:MiniType.VOID,parameters,false,List.of(new IrBlock("entry",List.of(code))),R); }
    private static IrConstant c(int value) { return new IrConstant(value); }
    private static IrReturnInstruction ret(IrValue value) { return new IrReturnInstruction(value,R); }
    private static List<IrInstruction> code(IrResult source) { return source.findFunction("main").orElseThrow().blocks().stream().flatMap(b->b.instructions().stream()).toList(); }
    private static long count(IrResult source,Class<?> kind) { return code(source).stream().filter(kind::isInstance).count(); }
}
