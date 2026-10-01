package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class PowerOfTwoDivisionSelectionTest {
    private static final SourceRange R = new SourceRange(1, 0, 1, 8);
    private static final List<IrBinaryOperator> OPERATORS = List.of(IrBinaryOperator.DIVIDE, IrBinaryOperator.MODULO);
    private static final List<IrType> TYPES = List.of(IrType.INT, IrType.LONG, IrType.LONG_LONG,
            IrType.UNSIGNED_INT, IrType.UNSIGNED_LONG, IrType.UNSIGNED_LONG_LONG);

    @ParameterizedTest @EnumSource(value=IrType.class, names={"INT","LONG","LONG_LONG","UNSIGNED_INT","UNSIGNED_LONG","UNSIGNED_LONG_LONG"})
    void everyRepresentablePositivePowerUsesNoHardwareDivide(IrType type) {
        int width=type.sizeBytes()*8;
        for(int shift=0;shift<width-(type.isSignedInteger()?1:0);shift++) {
            long divisor=1L<<shift;
            for(var operator:OPERATORS) {
                String text=emit(type,operator,divisor,true,false);
                assertFalse(text.contains("div "),type+" "+operator+" 2^"+shift+"\n"+text);
                if(shift>0 && operator==IrBinaryOperator.DIVIDE)
                    assertTrue(text.contains((type.isSignedInteger()?"sar ":"shr ")+accumulator(type)+", "+shift),text);
            }
        }
    }

    @Test void baselineRetainsItsOriginalDivisionSequenceAtAllWidths() {
        for(var type:TYPES) for(var operator:OPERATORS) {
            String text=emit(type,operator,128,false,false);
            assertTrue(text.contains("push rax") && text.contains("pop rax"),text);
            assertTrue(text.contains((type.isUnsignedInteger()?"div ":"idiv ")+(type.sizeBytes()==8?"rcx":"ecx")),text);
        }
    }

    @Test void zeroNonPowersAndNegativeSignedDivisorsRetainTheHardwarePath() {
        for(var type:TYPES) for(var operator:OPERATORS) for(long divisor:new long[]{0,3,7,-1,-2,-128}) {
            String text=emit(type,operator,divisor,true,false);
            assertTrue(text.contains("div "),type+" "+divisor+"\n"+text);
        }
        for(var type:List.of(IrType.INT,IrType.LONG,IrType.LONG_LONG)) for(var operator:OPERATORS) {
            long negativeMinimum=type.sizeBytes()==8?Long.MIN_VALUE:Integer.MIN_VALUE;
            assertTrue(emit(type,operator,negativeMinimum,true,false).contains("idiv "));
        }
    }

    @Test void unsigned32HighBitIsRecognizedEvenAsASignExtendedIrConstant() {
        for(var operator:OPERATORS) {
            String text=emit(IrType.UNSIGNED_INT,operator,Integer.MIN_VALUE,true,false);
            assertFalse(text.contains("div "),text);
        }
    }

    @Test void inPlaceNonSsaResultIsLoadedBeforeItsHomeIsWritten() {
        for(var type:TYPES) for(var operator:OPERATORS) {
            String text=emit(type,operator,128,true,true);
            String home=type.sizeBytes()==8?"r12":"r12d";
            assertTrue(text.startsWith("    mov "+accumulator(type)+", "+home),text);
            assertTrue(text.endsWith("    mov "+home+", "+accumulator(type)+System.lineSeparator()),text);
            assertFalse(text.contains("div "),text);
        }
    }

    private static String accumulator(IrType type) {return type.sizeBytes()==8?"rax":"eax";}
    private static String emit(IrType type,IrBinaryOperator operator,long divisor,boolean optimized,boolean sameHome) {
        var result=new IrTemporary("result",type);
        var binary=new IrBinaryInstruction(result,operator,sameHome?result:new IrParameterRef("value",type),new IrConstant(divisor,type),R);
        var function=new IrFunction("divide",MiniType.INT,List.of(new IrParameter("value",MiniType.INT,type,R)),false,
                List.of(new IrBlock("entry",List.of(new IrMoveInstruction(result,new IrConstant(7,type),R),binary))),R);
        var frame=FrameLayout.create(function);
        var locations=TemporaryLocations.withOverrides(frame,sameHome?Map.of("result",new ValueLocation.Register(type,"r12")):Map.of());
        var emitter=new InstructionEmitter(frame,Set.of(),function,locations,optimized);
        var text=new StringBuilder();emitter.emitInstruction(text,"divide","epilogue",binary);return text.toString();
    }
}
