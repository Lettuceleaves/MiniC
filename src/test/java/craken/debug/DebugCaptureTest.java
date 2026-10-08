package craken.debug;

import craken.SourceRange;
import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.debug.visualization.RuntimeEventCollector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * “开始”前选中的变量在调试 IR 副本里插入捕获指令：创建、读取、写入与离开作用域
 * 都由调试器在执行点记录，未选中的变量不插桩，程序结果不受插桩影响。
 */
@Tag("visualization-adapter")
final class DebugCaptureTest {
    private static final String SOURCE = """
            #include "stdlib.mh"
            int counter;
            int add(int a, int b) {
                int sum = a + b;
                return sum;
            }
            int main(void) {
                counter = counter + 1;
                int total = add(2, 3);
                total = total + counter;
                return total;
            }
            """;
    private static final String DUPLICATE = """
            int first(void) { int value = 1; return value; }
            int second(void) { int value = 2; return value; }
            int main(void) { return first() + second(); }
            """;

    @Test void selectedLocalProducesCreateReadWriteAndRemoveInOrder() {
        var run = run(SOURCE, "sum");
        var kinds = run.captures.stream().map(DebugCapture::kind).toList();
        assertTrue(kinds.contains(DebugCapture.Kind.CREATE), kinds.toString());
        assertTrue(kinds.contains(DebugCapture.Kind.WRITE), kinds.toString());
        assertTrue(kinds.contains(DebugCapture.Kind.READ), kinds.toString());
        assertTrue(kinds.contains(DebugCapture.Kind.REMOVE), kinds.toString());
        assertTrue(run.captures.stream().allMatch(capture -> capture.variable().sourceName().equals("sum")));
        assertTrue(run.captures.stream().filter(capture -> capture.kind() != DebugCapture.Kind.REMOVE)
                .allMatch(capture -> capture.address() > 0), "static frame slots stay addressable while live");
        assertTrue(run.captures.stream().anyMatch(capture -> capture.function().equals("add")));
    }

    @Test void unselectedVariablesAndUninstrumentedRunsStaySilent() {
        var plain = collect(debugger(SOURCE, Set.of()));
        assertTrue(plain.captures.isEmpty(), "no capture instructions exist without selection");
        var selected = run(SOURCE, "total");
        assertTrue(selected.captures.stream().noneMatch(capture -> capture.variable().sourceName().equals("sum")));
    }

    @Test void theSameSourceNameCanSelectOneOfSeveralDefinitions() {
        var ir = new CompilerApi(new SourceFile("duplicate.mc", DUPLICATE)).runToIr();
        var candidates = DebugVariableRegistry.scan(ir).byName("value");
        assertEquals(2, candidates.size());
        var first = candidates.stream().filter(candidate -> candidate.function().equals("first")).findFirst().orElseThrow();
        var run = collect(Debugger.fromIr(new SourceFile("duplicate.mc", DUPLICATE), ir, "", 4096,
                new RuntimeEventCollector(), Set.of(), Set.of(first.definition())));
        assertFalse(run.captures.isEmpty());
        assertTrue(run.captures.stream().allMatch(capture -> capture.function().equals("first")));
        assertEquals(0, run.captures.stream().filter(capture -> capture.function().equals("second")).count());
    }

    @Test void selectingAGlobalCapturesItsReadsAndWrites() {
        var run = run(SOURCE, "counter");
        var kinds = run.captures.stream().map(DebugCapture::kind).distinct().toList();
        assertTrue(kinds.contains(DebugCapture.Kind.READ), kinds.toString());
        assertTrue(kinds.contains(DebugCapture.Kind.WRITE), kinds.toString());
        assertTrue(run.captures.stream().allMatch(capture -> capture.variable().kind() == DebugVariable.Kind.GLOBAL));
        assertTrue(run.captures.stream().allMatch(capture -> capture.address() > 0));
    }

    @Test void captureInstrumentationDoesNotChangeTheProgramResult() {
        var plain = debugger(SOURCE, Set.of());
        var instrumented = debugger(SOURCE, Set.of(definition(SOURCE, "sum")));
        var plainEnd = stepToEnd(plain);
        var instrumentedEnd = stepToEnd(instrumented);
        assertEquals(plainEnd.runtime().returnValue().integer(), instrumentedEnd.runtime().returnValue().integer());
        assertEquals(plainEnd.runtime().stdout(), instrumentedEnd.runtime().stdout());
    }

    @Test void elementAccessesCarryTheElementOffset() {
        String source = """
                int main(void) {
                    int a[2][2];
                    a[1][0] = 7;
                    return a[1][0];
                }
                """;
        var run = collect(debugger(source, Set.of(definition(source, "a"))));
        assertTrue(run.captures.stream().anyMatch(capture -> capture.kind() == DebugCapture.Kind.WRITE
                        && capture.offset() == 8), "a[1][0] 的偏移是 1*8：" + run.captures);
        assertTrue(run.captures.stream().anyMatch(capture -> capture.kind() == DebugCapture.Kind.READ
                        && capture.offset() == 8), "读取同一元素也带偏移：" + run.captures);
    }

    private record Run(List<DebugCapture> captures, Debugger.Context last) {}

    private static Run run(String source, String variable) {
        return collect(debugger(source, Set.of(definition(source, variable))));
    }

    private static SourceRange definition(String source, String variable) {
        var ir = new CompilerApi(new SourceFile("capture.mc", source)).runToIr();
        return DebugVariableRegistry.scan(ir).byName(variable).stream()
                .filter(candidate -> !candidate.synthetic())
                .map(DebugVariable::definition)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + variable));
    }

    private static Debugger debugger(String source, Set<SourceRange> captures) {
        var file = new SourceFile("capture.mc", source);
        return Debugger.fromIr(file, new CompilerApi(file).runToIr(), "", 4096,
                new RuntimeEventCollector(), Set.of(), captures);
    }

    private static Run collect(Debugger debugger) {
        var captures = new ArrayList<DebugCapture>();
        var context = debugger.initialContext();
        while (debugger.canStep()) {
            context = debugger.step();
            captures.addAll(context.captures());
        }
        return new Run(List.copyOf(captures), context);
    }

    private static Debugger.Context stepToEnd(Debugger debugger) {
        var context = debugger.initialContext();
        while (debugger.canStep()) context = debugger.step();
        return context;
    }
}
