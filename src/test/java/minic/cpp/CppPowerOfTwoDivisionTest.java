package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
final class CppPowerOfTwoDivisionTest {
    @TempDir Path temporary;
    record Matrix(String type,String suffix,String format,int width,boolean signed) {
        @Override public String toString(){return type;}
    }
    static Stream<Matrix> matrices(){return Stream.of(
            new Matrix("int","","%d",32,true),new Matrix("long","L","%ld",32,true),
            new Matrix("long long","LL","%lld",64,true),new Matrix("unsigned int","U","%u",32,false),
            new Matrix("unsigned long","UL","%lu",32,false),new Matrix("unsigned long long","ULL","%llu",64,false));}

    @ParameterizedTest(name="{0}") @MethodSource("matrices")
    void allPowerExponentsAndBoundaryDividendsMatchAnIndependentIntegerOracle(Matrix matrix)throws Exception {
        BigInteger high=BigInteger.ONE.shiftLeft(matrix.width-(matrix.signed?1:0));
        List<BigInteger> values=matrix.signed
                ? List.of(high.negate(),high.negate().add(BigInteger.ONE),BigInteger.valueOf(-129),BigInteger.valueOf(-128),
                          BigInteger.valueOf(-127),BigInteger.valueOf(-1),BigInteger.ZERO,BigInteger.ONE,
                          BigInteger.valueOf(127),BigInteger.valueOf(128),BigInteger.valueOf(129),high.subtract(BigInteger.ONE))
                : List.of(BigInteger.ZERO,BigInteger.ONE,BigInteger.valueOf(127),BigInteger.valueOf(128),
                          high.shiftRight(1).subtract(BigInteger.ONE),high.shiftRight(1),high.subtract(BigInteger.ONE));
        var source=new StringBuilder("#include <stdio.h>\nint main(){volatile "+matrix.type+" values[]={");
        for(int i=0;i<values.size();i++){
            if(i>0)source.append(',');
            BigInteger value=values.get(i);
            // The signed minimum is formed without an out-of-range positive signed literal.
            source.append(value.equals(high.negate())?"(-"+high.subtract(BigInteger.ONE)+matrix.suffix+"-1"+matrix.suffix+")":value+matrix.suffix);
        }
        source.append("};for(int i=0;i<").append(values.size()).append(";++i){").append(matrix.type).append(" x=values[i];\n");
        for(int k=0;k<matrix.width-(matrix.signed?1:0);k++){
            String divisor=BigInteger.ONE.shiftLeft(k)+matrix.suffix;
            source.append("printf(\"").append(matrix.format).append(' ').append(matrix.format).append("\\n\",x/")
                    .append(divisor).append(",x%").append(divisor).append(");\n");
        }
        source.append("}return 0;}");
        var expected=new StringBuilder();
        for(var value:values)for(int k=0;k<matrix.width-(matrix.signed?1:0);k++){
            var qr=value.divideAndRemainder(BigInteger.ONE.shiftLeft(k));expected.append(qr[0]).append(' ').append(qr[1]).append('\n');
        }
        check(matrix.type,source.toString(),expected.toString(),LanguageMode.CPP17_ALGORITHM);
    }

    @Test void cModeKeepsNegativeRemaindersAndSingleEvaluation()throws Exception {
        check("c-single-evaluation","""
                #include <stdio.h>
                int calls=0;long long next(){++calls;return -129LL;}
                int main(){printf("%lld ",next()/128LL);long long remainder=next()%128LL;printf("%lld %d\\n",remainder,calls);
                  unsigned long long value=18446744073709551615ULL;
                  value/=9223372036854775808ULL;printf("%llu\\n",value);return 0;}
                ""","-1 -1 2\n1\n",LanguageMode.C);
    }

    private void check(String name,String source,String expected,LanguageMode mode)throws Exception {
        var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(40),Duration.ofSeconds(15),500_000,1_048_576);
        for(var level:OptimizationLevel.values()){
            var report=new CppDifferentialHarness(temporary.resolve(level.name()),CppDifferentialHarness.referenceCompiler(System.getenv()),limits,mode,level)
                    .run(name,source,"");
            assertTrue(report.passed(),report::describe);
            for(var outcome:report.outcomes().values())assertEquals(expected,outcome.stdout().replace("\r\n","\n"),outcome.backend().toString());
        }
    }
}
