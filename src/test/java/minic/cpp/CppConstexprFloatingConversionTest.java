package minic.cpp;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import minic.SourceRange;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.semantic.cpp.CppConstantEvaluator;
import minic.compiler.type.MiniType;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** The binary32/binary64 conversion profile matches runtime conversion, not list narrowing. */
@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppConstexprFloatingConversionTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("original-explicit-cast", "constexpr float n=(float)1e100;int main(){return 0;}", ""),
        Arguments.of("infinity-storage", "constexpr float positive=(float)1e100;constexpr float negative=(float)-1e100;constexpr double wide=(double)positive;static_assert(positive>3.4028234663852886e38);static_assert(negative<-3.4028234663852886e38);static_assert(wide==positive);int main(){printf(\"%d %d %d\\n\",positive>3.4028234663852886e38,negative<-3.4028234663852886e38,wide==positive);return 0;}", "1 1 1\n"),
        Arguments.of("conversion-function", "constexpr float convert(double x){return (float)x;}constexpr float values[3]={convert(1e100),convert(-1e100),convert(0.1)};static_assert(values[0]>1e30&&values[1]<-1e30&&values[2]>0);int main(){double input=1e100;float runtime=(float)input;printf(\"%d %d\\n\",runtime==values[0],values[1]==-values[0]);return 0;}", "1 1\n"),
        Arguments.of("inrange-list-rounding", "constexpr float n{0.1};static_assert(n>0.09&&n<0.11);int main(){return 0;}", ""),
        Arguments.of("infinity-integral-bool", "constexpr float n=(float)1e100;static_assert((bool)n);static_assert((bool)-n);int main(){return 0;}", "")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void agreesWithRuntimeAndTheReferenceCompiler(String name,String source,String output)throws Exception{
        agree(temporary,name,"#include <stdio.h>\n"+source,output);
    }
    static Stream<Arguments> invalid(){return Stream.of(
        Arguments.of("list-narrowing", "constexpr float n{1e100}; // bad\nint main(){return 0;}"),
        Arguments.of("infinity-to-integer", "constexpr int n=(int)(float)1e100; // bad\nint main(){return 0;}"),
        Arguments.of("finite-operation-overflow", "constexpr double n=1e300*1e300; // bad\nint main(){return 0;}"),
        Arguments.of("zero-division", "constexpr double n=1.0/0.0; // bad\nint main(){return 0;}")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("invalid")
    void conversionsDoNotWeakenOtherConstantExpressionRules(String name,String source)throws Exception{reject(temporary,name,source);}
    @Test void theSerializedFloatRetainsInfinityAndItsOriginalSourceRange(){
        var range=new SourceRange(4,2,4,15);
        var evaluator=new CppConstantEvaluatorTest.Env().evaluator();
        var value=assertInstanceOf(CppConstantEvaluator.FloatingValue.class,evaluator.evaluate(
                new CastExpr(MiniType.FLOAT,new DoubleLiteralExpr(1e100,"1e100",range),range)));
        assertEquals(Double.POSITIVE_INFINITY,value.value());
        assertEquals(MiniType.FLOAT,value.type());
        var serialized=assertInstanceOf(FloatLiteralExpr.class,evaluator.constantExpression(value,range));
        assertEquals(Float.POSITIVE_INFINITY,serialized.value());
        assertEquals(range,serialized.range());
    }
}
