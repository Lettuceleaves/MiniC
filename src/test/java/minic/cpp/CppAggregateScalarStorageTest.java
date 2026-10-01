package minic.cpp;

import minic.compiler.*;
import minic.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
final class CppAggregateScalarStorageTest {
    @TempDir Path temporary;
    static Stream<Arguments> cases(){
        return Stream.of(
            Arguments.of("char", "char data[2]={'a',0};return data[0]=='a' && data[1]==0?0:1;",IrType.CHAR),
            Arguments.of("unsigned-char", "unsigned char data[3]={0,128,255};return data[1]==128 && data[2]==255?0:1;",IrType.UNSIGNED_CHAR),
            Arguments.of("short", "volatile short data[3]={-1,32767,0};return data[0]==-1 && data[1]==32767 && data[2]==0?0:1;",IrType.SHORT),
            Arguments.of("wide", "long long data[2]={-1,-2147483647};return data[0]==-1LL && data[1]==-2147483647LL?0:1;",IrType.LONG_LONG),
            Arguments.of("float", "float data[2]={1,2};return data[0]==1.0f && data[1]==2.0f?0:1;",IrType.FLOAT),
            Arguments.of("double", "double data[2]={-1,2};return data[0]==-1.0 && data[1]==2.0?0:1;",IrType.DOUBLE),
            Arguments.of("null-pointer", "void* data[2]={0,0};return data[0]==0 && data[1]==0?0:1;",IrType.POINTER)
        ).flatMap(row->Stream.of(LanguageMode.C,LanguageMode.CPP17_ALGORITHM)
            .flatMap(mode->Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED)
                .map(level->Arguments.of(row.get()[0],row.get()[1],row.get()[2],mode,level))));
    }
    @ParameterizedTest(name="{0} [{3}/{4}]") @MethodSource("cases")
    void scalarElementsAreConvertedBeforeWritingTheirStorage(String name,String body,IrType elementType,
            LanguageMode mode,OptimizationLevel level)throws Exception {
        String source="int main(){"+body+"}";
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),mode,level).run(name,source,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->{assertEquals(0,outcome.exitCode(),report::describe);assertEquals("",outcome.stdout());});
        var ir=new CompilerApi(new SourceFile(name+".cpp",source),mode).runToIr();
        var stores=ir.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream())
                .filter(IrStorePointerInstruction.class::isInstance).map(IrStorePointerInstruction.class::cast).toList();
        assertFalse(stores.isEmpty());
        assertTrue(stores.stream().allMatch(store->store.value().type()==elementType),
                ()->"Every zero/explicit initializer store must use the destination element type: "+stores);
    }
}
