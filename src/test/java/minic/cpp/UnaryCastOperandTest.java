package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential")
final class UnaryCastOperandTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(LanguageMode.class)
    void castOperandsOfUnaryOperatorsRunWithoutExtraParentheses(LanguageMode mode) throws Exception {
        String source = """
                #include <stdio.h>
                struct Cell { int value; };
                int main() {
                    int value = 7;
                    void *raw = &value;
                    printf("%d %d %d %d %d %d\\n", *(int*)raw, (int)-(long)3,
                           (int)+(double)4.5, !(int)0, ~(int)0,
                           (int)sizeof(*(struct Cell*)0));
                    return 0;
                }
                """;
        var report = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), mode).run("unary-casts", source, "");
        assertTrue(report.passed(), report::describe);
        for (var outcome : report.outcomes().values()) {
            assertEquals("7 -3 4 1 -1 4\n", outcome.stdout().replace("\r\n", "\n"), report::describe);
        }
    }

    @ParameterizedTest
    @EnumSource(LanguageMode.class)
    void castBindsInsideUnaryAndBeforeMultiplication(LanguageMode mode) {
        var api = new CompilerApi(new SourceFile("precedence.cpp", "int main(){return -(long)2 * 3;}"), mode);
        var parser = api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        api.runThrough(parser);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        var returned = (ReturnStmt) parser.result().program().functions().getFirst().body().statements().getFirst();
        var product = assertInstanceOf(BinaryExpr.class, returned.expression());
        var unary = assertInstanceOf(UnaryExpr.class, product.left());
        assertInstanceOf(CastExpr.class, unary.operand());
    }

    @ParameterizedTest
    @ValueSource(strings = {"&(int)1", "++(int)1", "--(int)1"})
    void castPrvaluesRemainInvalidAddressOrUpdateTargets(String expression) {
        var api = new CompilerApi(new SourceFile("invalid-unary.cpp", "int main(){" + expression + ";return 0;}"),
                LanguageMode.CPP17_ALGORITHM);
        var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        var parser = api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertFalse(semantic.errors().isEmpty());
    }
}
