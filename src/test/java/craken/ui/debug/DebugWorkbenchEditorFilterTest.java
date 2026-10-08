package craken.ui.debug;

import craken.SourceRange;
import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.model.IrFunction;
import craken.debug.DebugVariable;
import craken.debug.DebugVariableRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户报告的场景：调试工作台的捕获检索与栈区只显示编辑器缓冲区里写下的变量，
 * 任何 include 展开（库头文件或用户自己的 .mh）出的函数、形参与局部变量都不出现。
 */
@Timeout(60)
final class DebugWorkbenchEditorFilterTest {
    private static final String SOURCE = """
            #include "stdlib.mh"
            int main(void) {
                double result = strtod("1.5", 0);
                return (int)result;
            }
            """;
    private static final List<String> INCLUDED_NAMES =
            List.of("string", "endPointer", "foreign_errno", "saved_errno");

    /** 过滤依赖的不变量：include 展开的内容在调试 IR 里塌缩为 include 行行首的单字符范围。 */
    @Test
    void includedContentCollapsesOntoTheIncludeLine() {
        SourceFile source = new SourceFile("included-range.mc", SOURCE);
        IrResult ir = new CompilerApi(source).runToIr();

        IrFunction strtod = ir.findFunction("strtod").orElseThrow();
        assertEquals(new SourceRange(1, 0, 1, 1), strtod.range());

        DebugVariable result = DebugVariableRegistry.scan(ir).byName("result").stream()
                .filter(variable -> variable.function().equals("main"))
                .findFirst().orElseThrow();
        assertEquals(3, result.definition().startLine(), "编辑器里的声明保留自己的范围");
        assertTrue(result.definition().endByte() > 1);
    }

    @Test
    void captureSearchOnlyOffersEditorDefinitions() {
        try (var session = new DebugWorkbenchSession(new SourceFile("editor-search.mc", SOURCE), Set.of())) {
            var results = session.search("result");
            assertEquals(1, results.size(), "只剩编辑器里的 result：" + results);
            assertEquals("main", results.getFirst().function());
            assertTrue(session.search("saved_errno").isEmpty(), "库函数内部变量不在编辑器里");
            assertTrue(session.search("endPointer").isEmpty(), "库函数形参不在编辑器里");
        }
    }

    /** 用户自己头文件里的定义同样来自 include 展开，也不在检索里。 */
    @Test
    void captureSearchSkipsUserHeaderDefinitions(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("helpers.mh"), """
                int helperValue = 3;
                """);
        try (var session = new DebugWorkbenchSession(new SourceFile(directory.resolve("main.mc").toString(), """
                #include "helpers.mh"
                int main(void) { return helperValue; }
                """), Set.of())) {
            assertTrue(session.search("helperValue").isEmpty(), "用户头文件里的变量也不在检索里");
        }
    }

    @Test
    void stackRowsSkipIncludedFramesAndVariables() {
        try (var session = new DebugWorkbenchSession(new SourceFile("editor-stack.mc", SOURCE), Set.of())) {
            session.start(session.search("result"));
            var inside = session.snapshot();
            for (int step = 0; step < 40 && !"COMPLETED".equals(inside.status()); step++) {
                inside = session.stepInto();
                if ("strtod".equals(inside.function())) break;
            }
            assertEquals("strtod", inside.function(), "必须步入库函数才能验证它的帧被隐藏");

            List<String> names = inside.stack().stream().map(DebugWorkbenchSession.MemoryRow::name).toList();
            assertFalse(names.stream().anyMatch(name -> name.contains("strtod")),
                    "include 展开出的帧不显示：" + names);
            assertTrue(names.stream().noneMatch(INCLUDED_NAMES::contains),
                    "include 展开出的变量不显示：" + names);
            assertEquals(1, names.stream().filter("result"::equals).count(),
                    "只保留编辑器里的 result：" + names);
        }
    }
}
