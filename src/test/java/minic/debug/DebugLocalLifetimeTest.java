package minic.debug;

import minic.SourceRange;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
final class DebugLocalLifetimeTest {
    static Stream<Arguments> uninitializedPrograms() {
        return Stream.of(LanguageMode.C, LanguageMode.CPP17_ALGORITHM).flatMap(mode -> Stream.of(
                Arguments.of(mode, "scalar", "int main(){int sum=0;for(int i=0;i<2;i++){int value;if(i==0)value=3;sum+=value;}return sum;}"),
                Arguments.of(mode, "addressed-scalar", "int main(){int sum=0;for(int i=0;i<2;i++){int value;int *p=&value;if(i==0)*p=3;sum+=*p;}return sum;}"),
                Arguments.of(mode, "array-partial-write", "int main(){int sum=0;for(int i=0;i<2;i++){int value[2];if(i==0)value[1]=3;value[0]=1;sum+=value[1];}return sum;}"),
                Arguments.of(mode, "struct-partial-write", "struct Pair{int first;int second;};int main(){int sum=0;for(int i=0;i<2;i++){struct Pair value;if(i==0)value.second=3;value.first=1;sum+=value.second;}return sum;}")));
    }

    @ParameterizedTest(name = "{0}: {1}") @MethodSource("uninitializedPrograms")
    void eachDeclarationStartsANewUninitializedLifetime(LanguageMode mode, String name, String source) {
        // These are undefined C/C++ programs; the assertion is MiniC's existing checked-read contract, not a G++ oracle.
        var debug = new DebugApi(new SourceFile(name + ".mc", source), "", mode);
        finish(debug);
        assertEquals(Debugger.Status.FAILED, debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"), debug.current().stop()::error);
    }

    @Test void redeclarationReusesStorageButResetsInitializationAndPreservesHistory() {
        var source = new SourceFile("local-history.mc", """
                int main(){
                    int sum=0;
                    for(int i=0;i<3;i++){
                        int value;
                        value=i+1;
                        sum+=value;
                    }
                    return sum;
                }
                """);
        var debug = new DebugApi(source);
        var history = new ArrayList<Debugger.Context>();
        history.add(debug.current());
        for (int steps=0; debug.canNext() && steps<2000; steps++) history.add(debug.next());
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status());
        assertEquals(6, debug.current().runtime().termination().status());
        var blocks = history.stream().flatMap(context -> context.runtime().stackMemory().stream())
                .filter(block -> block.label().equals("value")).toList();
        assertFalse(blocks.isEmpty());
        assertEquals(1, blocks.stream().map(DebugRuntime.MemoryBlock::address).distinct().count());
        boolean wasInitialized = false, sawReset = false;
        for (var block : blocks) {
            if (wasInitialized && block.initializedBytes() == 0) sawReset = true;
            if (block.initializedBytes() == 4) wasInitialized = true;
        }
        assertTrue(sawReset, "The next iteration clears the old lifetime's initialized bits");
        for (int index=history.size()-2; index>=0; index--) assertSame(history.get(index), debug.previous());
        for (int index=1; index<history.size(); index++) assertSame(history.get(index), debug.next());
    }

    @Test void declaringAnIncomingArgumentPseudoSlotDoesNotEraseItsStoredBytes() {
        // Guard the borrowed-storage distinction without claiming variadic debug execution support.
        var range = new SourceRange(1,1,1,25);
        var area = IrLocal.incomingArgumentArea(0, range);
        var pointer = new IrTemporary("%pointer", IrType.POINTER);
        var result = new IrTemporary("%result", IrType.INT);
        var value = new IrConstant(99, IrType.POINTER);
        var ir = new IrResult(List.of(new IrFunction("main", MiniType.INT, List.of(), false,
                List.of(new IrBlock("entry", List.of(new IrDeclareLocalInstruction(area, range),
                        new IrStoreLocalInstruction(area, value, range), new IrDeclareLocalInstruction(area, range),
                        new IrLoadLocalInstruction(pointer, area, range),
                        new IrBinaryInstruction(result, IrBinaryOperator.NOT_EQUAL, pointer, value, range),
                        new IrReturnInstruction(result, range)))), range)));
        var debug = DebugApi.fromIr(new SourceFile("pseudo.mc", "int main(){return 0;}"), ir, "");
        finish(debug);
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status());
        assertEquals(0, debug.current().runtime().termination().status());
    }

    private static void finish(DebugApi debug) {
        for (int steps=0; debug.canNext() && steps<2000; steps++) debug.next();
        assertFalse(debug.canNext(), "Bounded debugger step budget");
    }
}
