package minic.compiler.preprocess;

import minic.compiler.SourceFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConditionalExpressionPreprocessTest {
    @Test
    void evaluatesIfElifDefinedAndNestedInactiveBranches() {
        String source = """
                #define VERSION 3
                #define FEATURE 1
                #if defined(FEATURE) && VERSION >= 4
                int selected = 1;
                #elif defined FEATURE && ((VERSION << 1) == 6)
                int selected = 2;
                #else
                int selected = 3;
                #endif
                #if 0 && (1 / 0)
                int unreachable = 1;
                #endif
                """;

        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("conditional.mc", source));
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertTrue(result.sourceFile().content().contains("int selected = 2;"));
        assertFalse(result.sourceFile().content().contains("int selected = 1;"));
        assertFalse(result.sourceFile().content().contains("int selected = 3;"));
        assertFalse(result.sourceFile().content().contains("unreachable"));
    }

    @Test
    void expandsNestedObjectAndFunctionMacrosInsideConditions() {
        String source = """
                #define LEVEL BASE
                #define BASE 7
                #define GREATER(a, b) ((a) > (b))
                #if defined(LEVEL) && defined(__LINE__) && (__LINE__ == 4) && GREATER(LEVEL, 3) && !defined(MISSING)
                int selected = 7;
                #else
                int selected = 0;
                #endif
                """;

        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("nested-condition.mc", source));
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertTrue(result.sourceFile().content().contains("int selected = 7;"));
        int outputOffset = result.sourceFile().content().indexOf("selected");
        assertEquals(source.indexOf("selected"), result.sourceMap()[outputOffset]);
    }

    @Test
    void diagnosesMalformedActiveConditionsButDoesNotEvaluateDeadOnes() {
        String source = """
                #if 0
                #if 1 / 0
                int ignored;
                #endif
                #endif
                #if (1 && )
                int malformed;
                #endif
                #if 1
                int chosen;
                #else
                #elif 1
                int invalid;
                #endif
                """;

        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("condition-errors.mc", source));
        assertEquals(2, result.diagnostics().size(), () -> result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("条件编译表达式非法")));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("#else 后不能出现 #elif")));
        assertFalse(result.diagnostics().stream().anyMatch(d -> d.message().contains("除数为零")));
    }
}
