package minic.cpp;

import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Placement construction is needed by typed raw storage; declarations alone do not execute it. */
final class CppPlacementNewParserTest {
    @TempDir Path temporary;
    private static final String PREFIX = "namespace N{struct A{A(int n){}};}struct Empty{};";

    @ParameterizedTest @ValueSource(strings={"new(p) N::A(3)","::new(p) N::A{3}",
            "new(p) const unsigned long long(7)","new(p) int*", "new(p) Empty", "new(p) int{}"})
    void preservesPlacementConstructionAndRequiresAnAllocationDeclaration(String expression) throws Exception {
        String source=PREFIX+"int main(){char storage[64];void*p=storage;"+expression+";return 0;}";
        var api=compiler(source);var parser=stage(api,Parser.class);api.runThrough(parser);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        AstNode node=findNew(parser.result().program());
        assertNotNull(node);assertEquals(expression,source.substring(node.range().startByte(),node.range().endByte()));
        assertSame(node,AstChildren.firstCppSyntax(node));
        var semantic=new SemanticAnalyzer(parser.result().program());semantic.analyze();
        assertFalse(semantic.succeeded());assertTrue(semantic.errors().stream().anyMatch(error->error.message().contains("operator new")),()->semantic.errors().toString());
        Path cpp=temporary.resolve("placement.cpp");Files.writeString(cpp,"#include <new>\n"+source);
        var result=BoundedProcess.run(java.util.List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                "-std=c++17","-pedantic-errors","-fsyntax-only",cpp.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(result.timedOut());assertEquals(0,result.exitCode(),result::stderr);
    }

    @ParameterizedTest @ValueSource(strings={"new() int", "new(p,) int", "new(p) int(3", "new(p)"})
    void malformedPlacementSyntaxProducesSourceDiagnostics(String expression) {
        var api=compiler("int main(){void*p=0;"+expression+";return 0;}");var parser=stage(api,Parser.class);api.runThrough(parser);
        assertFalse(parser.succeeded());assertFalse(parser.errors().isEmpty());
    }

    @Test void allocationSyntaxCannotSilentlyBecomePlacementConstruction() {
        var api=compiler("int main(){int*p=new int(3);return 0;}");var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertFalse(semantic.succeeded());
    }

    private static AstNode findNew(AstNode root) {
        var pending=new ArrayDeque<AstNode>();pending.add(root);
        while(!pending.isEmpty()) {
            var node=pending.removeFirst();
            if(node.getClass().getSimpleName().equals("CppNewExpr"))return node;
            pending.addAll(AstChildren.of(node));
        }
        return null;
    }
}
