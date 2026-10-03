package minic.compiler;

import minic.compiler.link.Linker;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AnonymousAggregateEndToEndTest {
    @Test
    void supportsAnonymousTypesAndPromotedAnonymousMembers() {
        SourceFile source = new SourceFile("anonymous.mc", """
                struct Container {
                    struct { int x; int y; };
                    union { int whole; unsigned char low; };
                };
                int main() {
                    struct { int left; int right; } local = { .right = 3 };
                    struct Container value = { { 4, 5 }, { 6 } };
                    value.x += 2;
                    return value.x == 6 && value.y == 5 && value.whole == 6 && value.low == 6
                            && local.left == 0 && local.right == 3 ? 0 : 1;
                }
                """);
        var session = new CompilerApi(source);
        session.runThrough(session.stage(Linker.class));
        assertTrue(session.stage(Linker.class).succeeded(), () -> session.stage(SemanticAnalyzer.class).errors().toString());
        var nativeRunStage = new ExecutableRunner();
        var nativeRun = nativeRunStage.run(source,
                session.stage(Linker.class).result().executableArtifactOptional().orElseThrow());
        assertEquals(0, nativeRun.exitCode(), () -> nativeRunStage.errors().toString());

        DebugApi api = new DebugApi(source);
        for (int budget = 20_000; api.canNext() && budget > 0; budget--) api.next();
        assertFalse(api.canNext());
        assertEquals(Debugger.Status.COMPLETED, api.current().stop().status(), api.current().stop().error());
        assertEquals(0, api.current().runtime().returnValue().integer());
    }
}
