package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("cpp-differential")
@Timeout(60)
final class CppAnonymousMemberExecutionTest {
    @TempDir Path temporary;

    @Test void namedAnonymousStructAndUnionArrayRetainTheirLayoutsAndStoredValues() throws Exception {
        var harness = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), LanguageMode.CPP17_ALGORITHM);
        var report = harness.run("anonymous-member-layouts", """
                #include <stdio.h>
                typedef struct {
                    struct { int first; int second; } member;
                    union { int value; int pair[2]; } cells[2];
                } Box;
                int main() {
                    Box box;
                    box.member.first = 5;
                    box.member.second = 7;
                    box.cells[0].pair[0] = 11;
                    box.cells[0].pair[1] = 13;
                    box.cells[1].value = 17;
                    printf("%d %d %d %d %d\\n", box.member.first, box.member.second,
                        box.cells[0].pair[0], box.cells[0].pair[1], box.cells[1].value);
                    int total = box.member.first + box.member.second + box.cells[0].pair[0]
                        + box.cells[0].pair[1] + box.cells[1].value;
                    return total == 53 ? 0 : 1;
                }
                """, "");
        assertTrue(report.passed(), report::describe);
    }
}
