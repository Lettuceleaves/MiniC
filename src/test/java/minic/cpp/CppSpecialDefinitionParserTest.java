package minic.cpp;

import minic.compiler.*;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CppSpecialDefinitionParserTest {
    private Parser parse(String source,LanguageMode mode){
        var lexer=new Lexer(new SourceFile("special-definitions.cpp",source),mode);
        var tokens=lexer.lex();assertTrue(lexer.succeeded(),()->lexer.errors().toString());
        var parser=new Parser(tokens.tokens(),mode,true);parser.parse();return parser;
    }
    @Test void markersRetainSignaturesNamesAndFullSourceRanges(){
        String text="struct Box{Box()=default;Box(const Box&)=delete;Box& operator=(const Box&)=default;~Box()=default;operator bool()const=delete;};";
        var parser=parse(text,LanguageMode.CPP17_ALGORITHM);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        var members=parser.result().program().structs().getFirst().cppInfo().members();
        var constructor=assertInstanceOf(ConstructorMember.class,members.get(0));
        var copy=assertInstanceOf(ConstructorMember.class,members.get(1));
        var assignment=assertInstanceOf(MethodMember.class,members.get(2));
        var destructor=assertInstanceOf(DestructorMember.class,members.get(3));
        var conversion=assertInstanceOf(MethodMember.class,members.get(4));
        assertEquals(DefinitionKind.DEFAULTED,constructor.definitionKind());
        assertEquals(DefinitionKind.DELETED,copy.definitionKind());
        assertEquals(DefinitionKind.DEFAULTED,assignment.method().definitionKind());
        assertEquals(DefinitionKind.DEFAULTED,destructor.definitionKind());
        assertEquals(DefinitionKind.DELETED,conversion.method().definitionKind());
        assertNotNull(assignment.method().operatorName());assertNotNull(conversion.method().conversionName());
        assertTrue(conversion.constQualified());assertNull(constructor.body());assertTrue(constructor.hasDefinition());
        var source=new SourceFile("special-definitions.cpp",text);
        assertEquals("Box(const Box&)=delete;",source.text(copy.range()));
        assertEquals("operator bool()const=delete;",source.text(conversion.range()));
    }
    @Test void directCoreGuardRecognizesFreeDeletedDeclarations(){
        var parser=parse("int f(int)=delete;",LanguageMode.CPP17_ALGORITHM);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        var function=parser.result().program().functions().getFirst();
        assertEquals(DefinitionKind.DELETED,function.definitionKind());
        assertSame(function,AstChildren.firstCppSyntax(function));
        assertFalse(function.hasBody());assertTrue(function.hasDefinition());
    }
    @Test void cModeRetainsOrdinaryDefinitionsAndRejectsTheCppSuffix(){
        assertTrue(parse("int f(int x){return x;}",LanguageMode.C).succeeded());
        assertFalse(parse("int f(int)=delete;",LanguageMode.C).succeeded());
    }
}
