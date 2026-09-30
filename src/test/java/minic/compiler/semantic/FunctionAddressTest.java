package minic.compiler.semantic;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class FunctionAddressTest {
    @Test void explicitFunctionAddressAndImplicitDecayHaveTheSameValue() {
        check("int add(int x){return x+3;} int main(){int (*a)(int)=&add; int (*b)(int)=add; return a(2)+b(4)+(a==b);}", 13);
    }

    @Test void groupedFunctionAndAddressOfDereferencedPointerWork() {
        check("int add(int x){return x+3;} int main(){int (*a)(int)=&(add); int (*b)(int)=&*a; return b(5);}", 8);
    }

    @Test void globalFunctionPointerIsLoadedFromStorage() {
        check("int (*callback)(int); int add(int x){return x+3;} int main(){callback=add; int (*copy)(int)=callback; return copy(7);}", 10);
    }

    @Test void localShadowIsAnObjectWhenTakingItsAddress() {
        check("int add(int x){return x+3;} int main(){int add=4; int *p=&add; *p=8; return add;}", 8);
    }

    private static void check(String content, int expected) {
        var source = new SourceFile("function-address-" + java.util.UUID.randomUUID() + ".mc", content);
        var compiler = new CompilerApi(source);
        var ir = compiler.runToIr();
        var debug = DebugApi.fromIr(source, ir, "");
        for (int step=0; debug.canNext() && step<200; step++) debug.next();
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(expected, debug.current().runtime().termination().status());
        compiler.run();
        var runner = compiler.stages().stream().filter(ExecutableRunner.class::isInstance)
                .map(ExecutableRunner.class::cast).findFirst().orElseThrow();
        assertTrue(runner.succeeded(), () -> runner.errors().toString());
        assertEquals(expected, runner.result().exitCode());
    }
}
