package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.debug.DebugApi;
import minic.debug.DebugRuntime;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises presentation metadata independently of the namespace binder's implementation. */
@Tag("cpp-frontend")
@Timeout(10)
final class CppNamespaceDebugNamesTest {
    private static final SourceFile SOURCE = new SourceFile("bound-names.cpp", """
            int compiled_value = 2;
            int compiled_add(int compiled_amount) {
                int compiled_outer = 1;
                {
                    int compiled_inner = 4;
                    compiled_value += compiled_amount + compiled_outer + compiled_inner;
                }
                return compiled_value;
            }
            int main() {
                return compiled_add(3);
            }
            """);

    @Test
    void instrumentationRetainsDisplayMetadataAndSourceIndexUsesOriginalFunctionName() {
        var original = compiledWithNames();
        var debug = DebugApi.fromIr(SOURCE, original, "");
        assertEquals(original.displayNames(), debug.current().program().ir().displayNames());
        assertTrue(debug.current().program().line(6).stream()
                .anyMatch(location -> location.function().equals("Counter::add")));
        assertTrue(debug.current().program().ir().findFunction("compiled_add").isPresent(),
                "Executable function identity must remain canonical");
        assertTrue(debug.current().program().ir().findFunction("Counter::add").isEmpty());
    }

    @Test
    void snapshotsDisplayOriginalNamesWithoutLosingCollidingLocalsOrChangingExecution() {
        var original = compiledWithNames();
        var debug = DebugApi.fromIr(SOURCE, original, "");
        List<Debugger.Context> history = history(debug);
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(10, debug.current().runtime().termination().status(),
                "Calls and global writes must continue to use canonical runtime keys");
        assertTrue(history.stream().anyMatch(context -> context.stop().function().equals("Counter::add")));
        var paused = history.stream().filter(context -> context.runtime().stack().stream()
                .anyMatch(frame -> frame.function().equals("Counter::add") && visibleLocals(frame).size() == 2))
                .findFirst().orElseThrow(() -> new AssertionError("Both same-named locals must remain visible"));
        var frame = paused.runtime().stack().stream().filter(f -> f.function().equals("Counter::add")).findFirst().orElseThrow();
        assertEquals(3, frame.parameters().get("amount").integer());
        assertEquals(2, visibleLocals(frame).stream().distinct().count());
        assertTrue(frame.locals().keySet().stream().noneMatch(name -> name.contains("compiled_")));
        assertThrows(UnsupportedOperationException.class, () -> frame.locals().put("new", 0L));
        assertEquals(2, paused.runtime().stackMemory().stream().filter(block -> block.label().equals("value")).count());
        assertTrue(paused.runtime().globalMemory().stream().anyMatch(block -> block.label().equals("Counter::value")));
        assertTrue(paused.runtime().globalMemory().stream().noneMatch(block -> block.label().contains("compiled_")));
        for (int index = history.size() - 2; index >= 0; index--) assertSame(history.get(index), debug.previous());
        for (int index = 1; index < history.size(); index++) assertSame(history.get(index), debug.next());
        assertEquals(10, debug.current().runtime().termination().status());
    }

    @Test
    void emptyMetadataPreservesLegacyNames() {
        var ir = compile(SOURCE);
        var debug = DebugApi.fromIr(SOURCE, ir, "");
        var history = history(debug);
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status());
        assertTrue(history.stream().anyMatch(context -> context.stop().function().equals("compiled_add")));
        assertTrue(history.stream().anyMatch(context -> context.runtime().globalMemory().stream()
                .anyMatch(block -> block.label().equals("compiled_value"))));
    }

    @Test
    void runtimeDiagnosticsUseTheOriginalLocalName() {
        var source = new SourceFile("uninitialized-name.cpp", "int main() { int compiled_missing; return compiled_missing; }");
        var ir = compile(source);
        var named = withNames(ir, Map.of("compiled_missing", "visibleValue"));
        var debug = DebugApi.fromIr(source, named, "");
        history(debug);
        assertEquals(Debugger.Status.FAILED, debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("visibleValue"), debug.current().stop()::error);
        assertFalse(debug.current().stop().error().contains("compiled_missing"));
    }

    private static List<Long> visibleLocals(DebugRuntime.StackFrame frame) {
        return frame.locals().entrySet().stream().filter(entry -> entry.getKey().startsWith("value"))
                .map(Map.Entry::getValue).toList();
    }

    private static IrResult compiledWithNames() {
        var ir = compile(SOURCE);
        Map<String, String> names = new LinkedHashMap<>(Map.of(
                "compiled_add", "Counter::add", "compiled_value", "Counter::value",
                "compiled_amount", "amount", "compiled_outer", "value", "compiled_inner", "value"));
        // Deliberately collapse two exact slot spellings to one source name; snapshots must not
        // overwrite an address merely because two distinct declarations share that display name.
        ir.findFunction("compiled_add").orElseThrow().blocks().stream().flatMap(block -> block.instructions().stream())
                .filter(IrDeclareLocalInstruction.class::isInstance).map(IrDeclareLocalInstruction.class::cast)
                .map(IrDeclareLocalInstruction::local).filter(local -> local.sourceName().equals("compiled_outer")
                        || local.sourceName().equals("compiled_inner"))
                .forEach(local -> names.put(local.name(), "value"));
        return withNames(ir, names);
    }

    private static IrResult withNames(IrResult ir, Map<String, String> names) {
        return new IrResult(ir.functions(), ir.stringData(), ir.globalData(), ir.externalFunctionNames(),
                ir.externalObjectNames(), ir.structLayouts(), ir.currentAstNode(), ir.currentSubject(), names);
    }

    private static IrResult compile(SourceFile source) {
        var api = new CompilerApi(source);
        try { return api.runToIr(); }
        catch (IllegalStateException failure) {
            throw new AssertionError(api.stages().stream().flatMap(stage -> stage.errors().stream()).toList().toString(), failure);
        }
    }

    private static List<Debugger.Context> history(DebugApi debug) {
        var result = new ArrayList<Debugger.Context>();
        result.add(debug.current());
        for (int steps = 0; debug.canNext() && steps < 200; steps++) result.add(debug.next());
        assertFalse(debug.canNext(), "Fixture exceeded its step budget");
        return result;
    }
}
