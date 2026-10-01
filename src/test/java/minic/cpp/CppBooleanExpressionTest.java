package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** C++ source bool results coexist with the core C integer comparison/logical instructions. */
@Tag("cpp-differential") @Timeout(90)
final class CppBooleanExpressionTest {
    @TempDir Path temporary;

    static Stream<String> operators() {
        return Stream.of("2 < 3", "2 <= 3", "2 > 3", "2 >= 3", "2 == 3", "2 != 3",
                "!2", "2 && 3", "2 || 3");
    }
    @ParameterizedTest(name="C++ bool: {0}") @MethodSource("operators")
    void sourceAndNormalizedOperationHaveBooleanTypeAndOriginalRange(String expression) {
        var source = new SourceFile("boolean.cpp", "int main(){return " + expression + ";}");
        var semantic = analyze(source, LanguageMode.CPP17_ALGORITHM);
        var result = semantic.semanticResult();
        var original = ((ReturnStmt) result.sourceProgram().functions().getFirst().body().statements().getFirst()).expression();
        assertEquals(MiniType.BOOL, result.typeOf(original).orElseThrow());
        assertEquals(expression, source.text(original.range()));
        var normalized = assertInstanceOf(Expression.class, result.sourceToCore().get(original));
        assertEquals(MiniType.BOOL, result.typeOf(normalized).orElseThrow());
        assertEquals(original.range(), normalized.range());
        assertNotSame(original, normalized);
    }
    @ParameterizedTest(name="C remains int: {0}") @MethodSource("operators")
    void cOperationResultsRemainIntegers(String expression) {
        var semantic = analyze(new SourceFile("boolean.c", "int main(){return " + expression + ";}"), LanguageMode.C);
        var result = semantic.semanticResult();
        var operation = ((ReturnStmt) result.program().functions().getFirst().body().statements().getFirst()).expression();
        assertEquals(MiniType.INT, result.typeOf(operation).orElseThrow());
    }
    static Stream<Arguments> literalTypes() {
        return Stream.of(Arguments.of("true", MiniType.BOOL), Arguments.of("false", MiniType.BOOL),
                Arguments.of("'x'", MiniType.CHAR), Arguments.of("'\\n'", MiniType.CHAR));
    }
    @ParameterizedTest @MethodSource("literalTypes")
    void cppBoolAndOrdinaryCharacterLiteralsAlreadyRetainTheirTypes(String expression, MiniType expected) {
        var result = analyze(new SourceFile("literal.cpp", "int main(){return " + expression + ";}"),
                LanguageMode.CPP17_ALGORITHM).semanticResult();
        var literal = ((ReturnStmt) result.sourceProgram().functions().getFirst().body().statements().getFirst()).expression();
        assertEquals(expected, result.typeOf(literal).orElseThrow());
    }
    @ParameterizedTest @ValueSource(strings={"(1 < 2)","(1 < 2, !0)","1 ? (2 < 3) : (3 < 4)","1 ? true : false"})
    void enclosingExpressionsDoNotLoseTheCppBooleanType(String expression) {
        var result = analyze(new SourceFile("enclosing.cpp", "int main(){return " + expression + ";}"),
                LanguageMode.CPP17_ALGORITHM).semanticResult();
        var operation = ((ReturnStmt) result.sourceProgram().functions().getFirst().body().statements().getFirst()).expression();
        assertEquals(MiniType.BOOL, result.typeOf(operation).orElseThrow());
    }
    static Stream<Arguments> conditionalTypes() {
        return Stream.of(
                Arguments.of("1 ? 'a' : 'b'", MiniType.CHAR),
                Arguments.of("1 ? (signed char)1 : (signed char)2", MiniType.SIGNED_CHAR),
                Arguments.of("1 ? (unsigned char)1 : (unsigned char)2", MiniType.UNSIGNED_CHAR),
                Arguments.of("1 ? (short)1 : (short)2", MiniType.SHORT),
                Arguments.of("1 ? (unsigned short)1 : (unsigned short)2", MiniType.UNSIGNED_SHORT),
                Arguments.of("1 ? (const char)1 : (volatile char)2", MiniType.CHAR),
                Arguments.of("1 ? (const short)1 : (short)2", MiniType.SHORT),
                Arguments.of("1 ? (short)1 : 2", MiniType.INT),
                Arguments.of("1 ? (short)1 : (unsigned short)2", MiniType.INT),
                Arguments.of("1 ? 'a' : (unsigned char)2", MiniType.INT),
                Arguments.of("1 ? true : 2", MiniType.INT),
                Arguments.of("1 ? (long)1 : (long)2", MiniType.LONG),
                Arguments.of("1 ? (float)1 : (float)2", MiniType.FLOAT));
    }
    @ParameterizedTest(name="conditional result: {0}") @MethodSource("conditionalTypes")
    void cppSameIntegerConditionalsPreserveTypeWhileMixedOperandsPromote(String expression, MiniType expected) {
        var result = analyze(new SourceFile("conditional.cpp", "int main(){return " + expression + ";}"),
                LanguageMode.CPP17_ALGORITHM).semanticResult();
        var operation = ((ReturnStmt) result.sourceProgram().functions().getFirst().body().statements().getFirst()).expression();
        assertEquals(expected, result.typeOf(operation).orElseThrow());
        var core = assertInstanceOf(Expression.class, result.sourceToCore().get(operation));
        assertEquals(operation.range(), core.range());
        assertEquals(expected, result.typeOf(core).orElseThrow());
    }
    @ParameterizedTest @EnumSource(OptimizationLevel.class)
    void sizeofAndShortCircuitMatchCppWithBothNativeOptimizationLevels(OptimizationLevel level) throws Exception {
        String source = """
                #include <stdio.h>
                int calls=0;
                int trace=0;
                int touch(int value){++calls;trace=trace*10+value;return value;}
                bool less(int left,int right){return left<right;}
                int take(bool value){return value;}
                int main(){
                    printf("%d %d %d %d %d %d %d %d %d\\n",
                        (int)sizeof(2<3),(int)sizeof(2<=3),(int)sizeof(2>3),(int)sizeof(2>=3),
                        (int)sizeof(2==3),(int)sizeof(2!=3),(int)sizeof(!2),(int)sizeof(2&&3),(int)sizeof(2||3));
                    bool a=0&&touch(1);
                    bool b=1||touch(2);
                    bool c=touch(3)&&touch(4);
                    bool d=touch(0)||touch(5);
                    bool e=!touch(0);
                    int size=sizeof(touch(9)<touch(8))+sizeof(!touch(7))+sizeof(touch(6)&&touch(5));
                    int score=0;
                    if(a||c)++score;
                    if(!a&&b)++score;
                    printf("%d %d %d %d %d %d %d %d %d\\n",a,b,c,d,e,calls,trace,size,score);
                    printf("%d %d %d %d %d %d %d\\n",(int)sizeof(true),(int)sizeof('x'),
                        (int)sizeof(1?(2<3):(3<4)),take(less(2,3)),!4294967296LL,4294967296LL&&1,
                        (int)sizeof(1?true:false));
                    printf("%d %d %d %d %d %d %d\\n",(int)sizeof(1?'a':'b'),
                        (int)sizeof(1?(short)1:(short)2),(int)sizeof(1?(const char)1:(volatile char)2),
                        (int)sizeof(1?(short)1:2),(int)sizeof(1?(short)1:(unsigned short)2),
                        (int)sizeof(1?true:2),(int)sizeof(1?(unsigned char)1:(unsigned char)2));
                    return 0;
                }
                """;
        var report = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()),
                new CppDifferentialHarness.Limits(Duration.ofSeconds(30),Duration.ofSeconds(10),50_000,1_048_576),
                LanguageMode.CPP17_ALGORITHM,level).run("cpp-boolean-"+level,source,"");
        assertEquals(CppDifferentialHarness.Status.OK, report.outcomes().get(CppDifferentialHarness.Backend.GXX).status(),report::describe);
        assertEquals("1 1 1 1 1 1 1 1 1\n0 1 1 1 1 5 34050 3 2\n1 1 1 1 0 1 1\n1 2 1 4 4 4 1\n",
                report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"),report::describe);
        assertTrue(report.passed(),report::describe);
    }
    @Test void cppBooleanNormalizationDoesNotMakeInvalidOperandsValid() {
        for(String expression:new String[]{"!value","value && 1","value < value"}) {
            var api = new CompilerApi(new SourceFile("invalid-bool.cpp",
                    "struct Item{int x;};int main(){Item value={};return "+expression+";}"),LanguageMode.CPP17_ALGORITHM);
            var semantic = stage(api);
            api.runThrough(semantic);
            assertFalse(semantic.succeeded(),expression);
            assertFalse(semantic.errors().isEmpty(),expression);
        }
    }
    private static SemanticAnalyzer analyze(SourceFile source, LanguageMode mode) {
        var api = new CompilerApi(source,mode);
        var semantic=stage(api);
        api.runThrough(semantic);
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        return semantic;
    }
    private static SemanticAnalyzer stage(CompilerApi api) {
        return api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
    }
}
