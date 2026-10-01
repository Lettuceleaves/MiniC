package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.*;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class LoopInvariantCodeMotionTest {
    private static final SourceRange R = new SourceRange(2, 1, 2, 12);
    private static final IrTemporary A=t("a"), B=t("b"), I=t("i"), C=t("condition"), VALUE=t("value");
    private static final IrConstant ZERO=new IrConstant(0), ONE=new IrConstant(1);

    @Test void hoistsOnlyTheInvariantComputationAndRetainsOriginalNodesRangesAndMetadata() {
        var multiply=multiply(); var original=program(base(List.of(multiply)));
        var result=apply(original); var entry=code(result,"entry");
        assertSame(multiply,entry.get(entry.size()-2));
        assertFalse(code(result,"body").contains(multiply));
        assertTrue(code(original,"body").contains(multiply));
        assertSame(R,entry.get(entry.size()-2).range());
        assertSame(original.displayNames(),result.displayNames());
        assertEquals(original.currentSubject(),result.currentSubject());
        assertSame(original.functions().getFirst().range(),result.functions().getFirst().range());
        assertSame(result,apply(result),"second application must be a no-op");
    }

    @Test void hoistsDependenciesInOrderIncludingAComparisonWithBooleanResult() {
        var next=t("next"); var compare=new IrTemporary("compare",IrType.BOOL);
        var multiply=multiply(); var add=new IrBinaryInstruction(next,IrBinaryOperator.ADD,VALUE,ONE,R);
        var less=new IrBinaryInstruction(compare,IrBinaryOperator.LESS_THAN,next,new IrConstant(10),R);
        var result=apply(program(base(List.of(multiply,add,less))));
        var entry=code(result,"entry");
        assertEquals(List.of(multiply,add,less),entry.subList(entry.size()-4,entry.size()-1));
    }

    @ParameterizedTest @EnumSource(value=IrBinaryOperator.class,names={"ADD","SUBTRACT","MULTIPLY","BITWISE_AND","BITWISE_OR","BITWISE_XOR","EQUAL","NOT_EQUAL","LESS_THAN","LESS_EQUAL","GREATER_THAN","GREATER_EQUAL"})
    void acceptsOnlyThePromisedWidthsWithoutChangingTheComputation(IrBinaryOperator operator) {
        for(IrType type:List.of(IrType.INT,IrType.UNSIGNED_INT,IrType.LONG,IrType.UNSIGNED_LONG,IrType.LONG_LONG,IrType.UNSIGNED_LONG_LONG)) {
            boolean comparison=operator==IrBinaryOperator.EQUAL||operator==IrBinaryOperator.NOT_EQUAL||operator==IrBinaryOperator.LESS_THAN
                    ||operator==IrBinaryOperator.LESS_EQUAL||operator==IrBinaryOperator.GREATER_THAN||operator==IrBinaryOperator.GREATER_EQUAL;
            var operation=new IrBinaryInstruction(new IrTemporary("wide",comparison?IrType.BOOL:type),operator,
                    new IrConstant(type.sizeBytes()==8?4294967297L:17,type),new IrConstant(3,type),R);
            var result=apply(program(base(List.of(operation))));
            assertTrue(code(result,"entry").stream().anyMatch(instruction->instruction==operation));
            assertFalse(code(result,"body").contains(operation));
        }
    }

    @Test void harmlessConditionalWorkMayBeSpeculatedEvenWhenTheLoopExecutesZeroTimes() {
        var blocks=base(List.of());
        blocks.set(2,block("body",new IrBranchInstruction(I,"chosen","step",R)));
        blocks.add(block("chosen",multiply(),new IrJumpInstruction("step",R)));
        var result=apply(program(blocks));
        assertTrue(code(result,"entry").contains(multiply()));
        assertFalse(code(result,"chosen").contains(multiply()));
    }

    @Test void allBackedgesWithTheSameHeaderBelongToOneNaturalLoop() {
        var blocks=base(List.of(multiply()));
        blocks.set(3,block("step",new IrBranchInstruction(I,"header","other-latch",R)));
        blocks.add(block("other-latch",new IrJumpInstruction("header",R)));
        assertTrue(code(apply(program(blocks)),"entry").contains(multiply()));
    }

    @ParameterizedTest @ValueSource(strings={"result-redefined","operand-redefined","dead-tail-definition","unreachable-definition","defined-only-inside"})
    void nonSsaOrUnavailableDefinitionsPreventMotion(String scenario) {
        var blocks=base(List.of(multiply()));
        switch(scenario) {
            case "result-redefined" -> prepend(blocks,0,new IrMoveInstruction(VALUE,ZERO,R));
            case "operand-redefined" -> prepend(blocks,2,new IrMoveInstruction(A,ONE,R));
            case "dead-tail-definition" -> {
                var body=new ArrayList<>(blocks.get(2).instructions()); body.add(new IrMoveInstruction(VALUE,ZERO,R));
                blocks.set(2,new IrBlock("body",body));
            }
            case "unreachable-definition" -> blocks.add(block("unreachable",new IrMoveInstruction(VALUE,ZERO,R),new IrReturnInstruction(ZERO,R)));
            case "defined-only-inside" -> {
                var entry=new ArrayList<>(blocks.getFirst().instructions()); entry.removeFirst(); blocks.set(0,new IrBlock("entry",entry));
                prepend(blocks,2,new IrMoveInstruction(A,new IrParameterRef("p",IrType.INT),R));
            }
        }
        var original=program(blocks); assertSame(original,apply(original));
    }

    @ParameterizedTest @ValueSource(strings={"two-entries","critical-edge","irreducible","nested"})
    void leavesUnsupportedLoopShapesUnchanged(String scenario) {
        var blocks=base(List.of(multiply()));
        switch(scenario) {
            case "two-entries" -> {
                var entry=new ArrayList<>(blocks.getFirst().instructions()); entry.set(entry.size()-1,new IrBranchInstruction(A,"left-entry","right-entry",R));
                blocks.set(0,new IrBlock("entry",entry));
                blocks.add(block("left-entry",new IrJumpInstruction("header",R))); blocks.add(block("right-entry",new IrJumpInstruction("header",R)));
            }
            case "critical-edge" -> {
                var entry=new ArrayList<>(blocks.getFirst().instructions()); entry.set(entry.size()-1,new IrBranchInstruction(A,"header","exit",R));
                blocks.set(0,new IrBlock("entry",entry));
            }
            case "irreducible" -> {
                var entry=new ArrayList<>(blocks.getFirst().instructions()); entry.set(entry.size()-1,new IrBranchInstruction(A,"header","body",R));
                blocks.set(0,new IrBlock("entry",entry));
            }
            case "nested" -> {
                blocks.set(2,block("body",new IrJumpInstruction("inner",R)));
                blocks.add(block("inner",multiply(),new IrBranchInstruction(A,"inner","step",R)));
            }
        }
        var original=program(blocks); assertSame(original,apply(original));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void callsKeepTheWholeLoopUnchanged(boolean indirect) {
        IrInstruction call=indirect?new IrIndirectCallInstruction(null,new IrFunctionAddress("effect"),List.of(),false,R)
                :new IrCallInstruction(null,"effect",List.of(),false,R);
        var original=program(base(List.of(multiply(),call))); assertSame(original,apply(original));
    }

    @Test void parameterSlotsAndTheirAliasedWritesAreNotLoopInvariants() {
        var address=new IrTemporary("parameter-address",IrType.POINTER);
        var multiply=new IrBinaryInstruction(VALUE,IrBinaryOperator.MULTIPLY,new IrParameterRef("p",IrType.INT),B,R);
        var body=List.<IrInstruction>of(new IrMoveInstruction(address,new IrParameterAddress("p"),R),
                new IrStorePointerInstruction(address,ONE,R),multiply);
        var original=program(base(body)); assertSame(original,apply(original));
    }

    @Test void anExplicitSnapshotRemainsInvariantDespiteLaterParameterWrites() {
        var address=new IrTemporary("parameter-address",IrType.POINTER);
        var result=apply(program(base(List.of(new IrMoveInstruction(address,new IrParameterAddress("p"),R),
                new IrStorePointerInstruction(address,ONE,R),multiply()))));
        assertTrue(code(result,"entry").contains(multiply()));
        assertTrue(code(result,"body").stream().anyMatch(IrStorePointerInstruction.class::isInstance));
    }

    @ParameterizedTest @EnumSource(value=IrBinaryOperator.class,names={"DIVIDE","MODULO","SHIFT_LEFT","SHIFT_RIGHT"})
    void potentiallyTrappingOrExcludedIntegerOperationsStayInTheLoop(IrBinaryOperator operator) {
        var original=program(base(List.of(new IrBinaryInstruction(VALUE,operator,A,B,R)))); assertSame(original,apply(original));
    }

    @Test void floatingNarrowMemoryAndVolatileOperationsStayInPlace() {
        var local=new IrLocal("local","local",MiniType.INT,IrType.INT,4,4,R);
        var pointer=new IrTemporary("pointer",IrType.POINTER);
        var original=program(base(List.of(
                new IrBinaryInstruction(new IrTemporary("float",IrType.DOUBLE),IrBinaryOperator.ADD,new IrFloatConstant(1,IrType.DOUBLE),new IrFloatConstant(2,IrType.DOUBLE),R),
                new IrBinaryInstruction(new IrTemporary("short",IrType.SHORT),IrBinaryOperator.ADD,new IrConstant(1,IrType.SHORT),new IrConstant(2,IrType.SHORT),R),
                new IrDeclareLocalInstruction(local,R),new IrStoreLocalInstruction(local,ONE,R),
                new IrLoadLocalInstruction(VALUE,local,true,R),new IrAddressOfLocalInstruction(pointer,local,R),new IrLoadPointerInstruction(t("load"),pointer,true,R))));
        assertSame(original,apply(original));
    }

    @Test void ordinarySourceHasAnActualMultiplicationToMoveAfterLocalPromotionAndPropagation() {
        var source=new SourceFile("loop.cpp","int work(int a,int b,int n){int x=a;int y=b;int total=0;for(int i=0;i<n;i++){total=total+x*y;}return total;}int main(){return work(3,7,5);}");
        var original=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var prepared=IrOptimizationPipeline.forLevel(OptimizationLevel.OPTIMIZED).apply(original).ir();
        var result=apply(prepared);
        var work=result.functions().stream().filter(f->result.displayName(f.name()).equals("work")).findFirst().orElseThrow();
        assertTrue(work.blocks().getFirst().instructions().stream().anyMatch(i->i instanceof IrBinaryInstruction b&&b.operator()==IrBinaryOperator.MULTIPLY));
        assertFalse(work.blocks().stream().filter(b->!b.label().equals("entry")).flatMap(b->b.instructions().stream()).anyMatch(i->i instanceof IrBinaryInstruction b&&b.operator()==IrBinaryOperator.MULTIPLY));
        assertEquals(original.displayNames(),result.displayNames());
    }

    private static IrTemporary t(String name){return new IrTemporary(name,IrType.INT);}
    private static IrBinaryInstruction multiply(){return new IrBinaryInstruction(VALUE,IrBinaryOperator.MULTIPLY,A,B,R);}
    private static IrBlock block(String label,IrInstruction...instructions){return new IrBlock(label,List.of(instructions));}
    private static ArrayList<IrBlock> base(List<IrInstruction> body){
        var instructions=new ArrayList<>(body); instructions.add(new IrJumpInstruction("step",R));
        return new ArrayList<>(List.of(block("entry",new IrMoveInstruction(A,new IrParameterRef("p",IrType.INT),R),
                new IrMoveInstruction(B,new IrParameterRef("q",IrType.INT),R),new IrMoveInstruction(I,ZERO,R),new IrJumpInstruction("header",R)),
                block("header",new IrBinaryInstruction(C,IrBinaryOperator.LESS_THAN,I,new IrConstant(3),R),new IrBranchInstruction(C,"body","exit",R)),
                new IrBlock("body",instructions),block("step",new IrBinaryInstruction(I,IrBinaryOperator.ADD,I,ONE,R),new IrJumpInstruction("header",R)),
                block("exit",new IrReturnInstruction(ZERO,R))));
    }
    private static void prepend(List<IrBlock> blocks,int index,IrInstruction instruction){var code=new ArrayList<>(blocks.get(index).instructions());code.addFirst(instruction);blocks.set(index,new IrBlock(blocks.get(index).label(),code));}
    private static IrResult program(List<IrBlock> blocks){return new IrResult(List.of(new IrFunction("function",MiniType.INT,List.of(new IrParameter("p",MiniType.INT,IrType.INT,R),new IrParameter("q",MiniType.INT,IrType.INT,R)),false,blocks,R)),List.of(),List.of(),Set.of("effect"),Set.of(),Map.of(),null,"original",Map.of("function","original-name"));}
    private static List<IrInstruction> code(IrResult result,String block){return result.functions().getFirst().blocks().stream().filter(b->b.label().equals(block)).findFirst().orElseThrow().instructions();}
    private static IrResult apply(IrResult ir){return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new LoopInvariantCodeMotionPass())).apply(ir).ir();}
}
