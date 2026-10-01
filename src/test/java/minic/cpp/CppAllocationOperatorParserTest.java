package minic.cpp;

import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Allocation-function declarations are required for library-defined raw storage construction. */
final class CppAllocationOperatorParserTest {
    @TempDir Path temporary;

    static Stream<Arguments> declarations() { return Stream.of(
            Arguments.of("operator new", "void* operator new(unsigned long long size);", 1),
            Arguments.of("operator new[]", "void* operator new [ ] (unsigned long long size);", 1),
            Arguments.of("operator delete", "void operator delete(void* pointer);", 1),
            Arguments.of("operator delete[]", "void operator delete [ ] (void* pointer);", 1),
            Arguments.of("operator new", "void* operator new(unsigned long long size,void* pointer){return pointer;}", 2),
            Arguments.of("operator delete", "void operator delete(void* pointer,void* placement){}", 2),
            Arguments.of("operator delete", "void operator delete(void* pointer,unsigned long long size);", 2)); }

    @ParameterizedTest(name="{1}") @MethodSource("declarations")
    void allocationDeclarationsRetainIdentityAndBindToCore(String name,String declaration,int parameters) throws Exception {
        referenceAccepts(declaration);
        var api=compiler(declaration+"int main(){return 0;}");var parser=stage(api,Parser.class);api.runThrough(parser);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        FunctionDecl function=parser.result().program().functions().getFirst();
        assertEquals(name,function.name());assertNotNull(function.operatorName());
        assertEquals(name,function.operatorName().spelling());
        assertEquals(parameters,function.parameters().size());
        assertSame(function,AstChildren.firstCppSyntax(function));
        assertEquals(declaration,new SourceFile("allocation.cpp",declaration).text(function.range()));
        var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
    }

    @Test void memberDeclarationsKeepImplicitStaticAllocationIdentityAndQualifiedDefinitions() throws Exception {
        String source="""
                struct A {
                    void* operator new(unsigned long long,void*);
                    void operator delete(void*,void*);
                    void* operator new[](unsigned long long size,void* storage){return storage;}
                };
                void* A::operator new(unsigned long long size,void* storage){return storage;}
                void A::operator delete(void* pointer,void* storage){}
                """;
        referenceAccepts(source);
        var api=compiler(source);var parser=stage(api,Parser.class);api.runThrough(parser);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        var program=parser.result().program();
        var members=program.structs().getFirst().cppInfo().members();
        assertEquals(List.of("operator new","operator delete","operator new[]"),members.stream().map(member->((MethodMember)member).method().name()).toList());
        var definition=(OutOfLineMethodDecl)program.declarations().get(1);
        assertEquals(List.of("A","operator new"),definition.qualifiedName().segments());
        assertEquals("operator new",new SourceFile("allocation.cpp",source).text(definition.nameRange()));
        assertNotNull(definition.method().body());
    }

    @ParameterizedTest @ValueSource(strings={"new[", "new[int]", "delete[3]", "delete[[]]"})
    void allocationOperatorBracketsMustBeEmptyAndComplete(String name) {
        var api=compiler("void* operator "+name+"(unsigned long long size);");
        var parser=stage(api,Parser.class);api.runThrough(parser);
        assertFalse(parser.succeeded());assertFalse(parser.errors().isEmpty());
    }

    @Test void keywordNamesHaveUnambiguousCanonicalSpelling() {
        var range=new minic.SourceRange(1,0,1,14);
        for(var kind:minic.compiler.parser.node.OperatorName.Kind.values()) {
            var name=new minic.compiler.parser.node.OperatorName(kind,range);
            assertEquals("operator"+(kind.allocation()?" ":"")+kind.symbol(),name.spelling());
        }
    }

    private void referenceAccepts(String source) throws Exception {
        Path file=temporary.resolve("allocation.cpp");Files.writeString(file,source);
        var result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(result.timedOut());assertEquals(0,result.exitCode(),result::stderr);
    }
}
