package craken.debug;

import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.debug.visualization.RuntimeEventCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户报告的场景：`int ab[2]` 的栈行曾把 9、1 两个 int 拼成 0x100000009 当成指针显示。
 * 指针宽度的数组与结构体仍必须是聚合槽位，标量照常显示值。
 */
@Timeout(60)
final class DebugStackSnapshotTest {
    @Test
    void pointerSizedArrayAndStructStayAggregateInStackSnapshots() {
        DebugRuntime.StackFrame frame = frameAtLine("""
                typedef struct { int x; int y; } Pair;
                int main(void) {
                    int a;
                    a = 100;
                    int ab[2];
                    ab[0] = 9;
                    ab[1] = 1;
                    Pair pair;
                    pair.x = 3;
                    pair.y = 4;
                    return a + ab[0] + ab[1] + pair.x + pair.y;
                }
                """, 11, "main");

        DebugRuntime.StackVariable ab = locals(frame).get("ab");
        assertEquals("int[2]", ab.type());
        assertTrue(ab.aggregate(), "int[2] 是指针宽度，但仍是聚合槽位");
        assertNull(ab.value(), "聚合槽位不能按指针读值");

        DebugRuntime.StackVariable pair = locals(frame).get("pair");
        assertTrue(pair.type().startsWith("struct "), pair.type());
        assertTrue(pair.aggregate(), "8 字节结构体仍是指针宽度的聚合槽位");
        assertNull(pair.value(), "聚合槽位不能按指针读值");

        DebugRuntime.StackVariable a = locals(frame).get("a");
        assertFalse(a.aggregate());
        assertEquals(100, a.value().integer(), "标量照常显示值");
    }

    private static DebugRuntime.StackFrame frameAtLine(String source, int line, String function) {
        SourceFile file = new SourceFile("stack-snapshot.mc", source);
        Debugger debugger = Debugger.fromIr(file, new CompilerApi(file).runToIr(), "", 4096,
                new RuntimeEventCollector(), Set.of());
        Debugger.Context context = debugger.initialContext();
        while (debugger.canStep()) {
            context = debugger.step();
            var range = context.stop().range();
            if (range != null && range.startLine() >= line) break;
        }
        return context.runtime().stack().stream()
                .filter(frame -> frame.function().equals(function))
                .findFirst().orElseThrow();
    }

    private static Map<String, DebugRuntime.StackVariable> locals(DebugRuntime.StackFrame frame) {
        Map<String, DebugRuntime.StackVariable> locals = new LinkedHashMap<>();
        frame.locals().forEach(variable -> locals.put(variable.name(), variable));
        return locals;
    }
}
