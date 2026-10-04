package craken.debug;

import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.ir.model.IrGlobalData;
import craken.compiler.type.CrakenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
final class DebugVariableRegistryTest {
    @Test
    void globalRangeReachesIrAndVariablesAreMarked() {
        SourceFile source = new SourceFile("variables.mc", """
                int counter = 3;

                int add(int value) {
                    int local = value + 1;
                    return local;
                }

                int main() {
                    int local = add(counter);
                    {
                        int local = 2;
                        local = local + 1;
                    }
                    return local;
                }
                """);
        DebugProgram program = new DebugProgram(source, new CompilerApi(source).runToIr());
        DebugVariableRegistry registry = program.variables();

        IrGlobalData counter = program.ir().globalData().stream()
                .filter(global -> global.label().equals("counter"))
                .findFirst().orElseThrow();
        assertEquals(1, counter.range().startLine());
        assertEquals(CrakenType.INT, counter.declaredType());

        DebugVariable counterMarker = registry.byDefinition(counter.range()).orElseThrow();
        assertEquals("counter", counterMarker.sourceName());
        assertEquals(DebugVariable.Kind.GLOBAL, counterMarker.kind());
        assertEquals("", counterMarker.function());
        assertEquals(1, registry.byName("counter").size());

        DebugVariable parameter = registry.byName("value").getFirst();
        assertEquals(DebugVariable.Kind.PARAMETER, parameter.kind());
        assertEquals("add", parameter.function());
        assertEquals(CrakenType.INT, parameter.declaredType());

        List<DebugVariable> locals = registry.byName("local");
        assertEquals(3, locals.size());
        assertTrue(locals.stream().allMatch(variable -> variable.kind() == DebugVariable.Kind.LOCAL));
        List<DebugVariable> mainLocals = locals.stream()
                .filter(variable -> variable.function().equals("main"))
                .toList();
        assertEquals(2, mainLocals.size());
        assertTrue(!mainLocals.get(0).definition().equals(mainLocals.get(1).definition()),
                "same-name declarations must have distinct definition ranges");
    }
}
