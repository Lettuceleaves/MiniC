package minic.compiler.semantic;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.compiler.parser.node.Expression.NameExpr;
import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.parser.node.Statement.VarDeclStmt;
import minic.compiler.type.MiniType;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
final class DeclarationScopeTest {
    @Test void variableIsInScopeInsideItsOwnSizeofInitializer() {
        String source = "int main() { int size = sizeof(size); return size; }";
        var semantic = analyze(source);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        assertDebugExit(source, 4);
    }

    @Test void selfReferenceBindsTheNewDeclarationInsteadOfOuterVariable() {
        var semantic = analyze("int main() { char value = 1; { int value = value; return 0; } }");
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var block = (BlockStmt) semantic.program().functions().getFirst().body().statements().get(1);
        var declaration = (VarDeclStmt) block.statements().getFirst();
        var initializer = assertInstanceOf(NameExpr.class, declaration.initializer());
        assertEquals(MiniType.INT, semantic.semanticResult().typeOf(initializer).orElseThrow());
        // Analyze binding only: actually reading the uninitialized variable is undefined C behavior.
    }

    @Test void arrayNameIsAvailableToUnevaluatedAggregateInitializer() {
        String source = "int main() { int items[3] = {sizeof(items), 2, 3}; return items[0]; }";
        var semantic = analyze(source);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        assertDebugExit(source, 12);
    }

    @Test void laterCasesShareEarlierDeclarations() {
        String source = """
                int main() {
                    int result = 0;
                    switch (1) {
                        case 1: int value; value = 4;
                        case 2: result = value + 3; break;
                    }
                    return result;
                }
                """;
        var semantic = analyze(source);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var functionScope = semantic.globalScope().children().getFirst();
        assertEquals(1, functionScope.children().size(), "The switch body has one lexical scope");
        assertTrue(functionScope.children().getFirst().resolveLocal("value").isPresent());
        assertDebugExit(source, 7);
    }

    @Test void differentCaseLabelsDoNotPermitDuplicateVariables() {
        var semantic = analyze("""
                int main() {
                    switch (1) {
                        case 1: int value; break;
                        case 2: int value; break;
                    }
                    return 0;
                }
                """);
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.message().contains("重复局部变量定义：value")),
                () -> semantic.errors().toString());
    }

    @Test void explicitCaseBlocksRetainSeparateScopes() {
        var semantic = analyze("""
                int main() {
                    switch (1) {
                        case 1: { int value = 1; break; }
                        case 2: { int value = 2; break; }
                    }
                    return 0;
                }
                """);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
    }

    @Test void switchDeclarationsRemainInvisibleAfterTheSwitch() {
        var semantic = analyze("int main() { switch (0) { case 0: int hidden = 1; break; } return hidden; }");
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.message().contains("未解析变量：hidden")),
                () -> semantic.errors().toString());
    }

    @Test void duplicateDeclarationsRemainErrorsAfterChangingDeclarationPoint() {
        var semantic = analyze("int main() { int value = 1; int value = sizeof(value); return value; }");
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.message().contains("重复局部变量定义：value")));
    }

    @Test void switchShadowDoesNotReplaceTheEnclosingVariableDuringLowering() {
        assertDebugExit("""
                int main() {
                    int value = 1;
                    switch (0) { case 0: int value; value = 2; break; }
                    return value;
                }
                """, 1);
    }

    @Test void nestedSwitchShadowEndsBeforeFollowingStatementsInOuterCase() {
        assertDebugExit("""
                int main() {
                    int result = 0;
                    switch (1) {
                        case 1:
                            int value; value = 3;
                            switch (2) { case 2: int value; value = 8; break; }
                            result = value;
                            break;
                    }
                    return result;
                }
                """, 3);
    }

    @Test void followingSwitchSelectorResolvesTheEnclosingVariable() {
        assertDebugExit("""
                int main() {
                    int value = 5;
                    int result = 0;
                    switch (0) { case 0: int value; value = 8; break; }
                    switch (value) {
                        case 5: result = 1; break;
                        default: result = 2; break;
                    }
                    return result;
                }
                """, 1);
    }

    @Test void explicitCaseBlockShadowEndsBeforeFallthrough() {
        assertDebugExit("""
                int main() {
                    int value = 5;
                    int result = 0;
                    switch (1) {
                        case 1: { int value = 8; result = value; }
                        case 2: result += value; break;
                    }
                    return result;
                }
                """, 13);
    }

    @Test void nativeSwitchShadowAlsoEndsAtTheSwitchBoundary() {
        var compiler = new CompilerApi(new SourceFile("scope-native-" + java.util.UUID.randomUUID() + ".mc", """
                int main() {
                    int value = 1;
                    int result = 0;
                    switch (0) { case 0: int value; value = 2; result = value; break; }
                    return value + result;
                }
                """));
        compiler.run();
        var execution = compiler.stages().stream().filter(ExecutableRunner.class::isInstance)
                .map(ExecutableRunner.class::cast).findFirst().orElseThrow();
        assertTrue(execution.succeeded(), () -> compiler.stages().stream().flatMap(s -> s.errors().stream()).toList().toString());
        assertEquals(3, execution.result().exitCode());
    }

    private static SemanticAnalyzer analyze(String content) {
        var compiler = new CompilerApi(new SourceFile("declaration-scope.mc", content));
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        return semantic;
    }

    private static void assertDebugExit(String content, int expected) {
        var debug = new DebugApi(new SourceFile("declaration-scope.mc", content));
        int steps = 0;
        while (debug.canNext() && steps++ < 1000) debug.next();
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop().error());
        assertEquals(expected, debug.current().runtime().termination().status());
    }
}
