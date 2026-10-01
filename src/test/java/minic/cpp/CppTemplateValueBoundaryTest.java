package minic.cpp;
import minic.compiler.*;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.ClassTemplateDecl;
import minic.compiler.parser.node.CppTypeMemberExpr;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
final class CppTemplateValueBoundaryTest {
    static Parser parse(String source){var api=new CompilerApi(new SourceFile("template-value-boundary.cpp",source),LanguageMode.CPP17_ALGORITHM);var parser=api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();api.runThrough(parser);return parser;}
    @ParameterizedTest @ValueSource(strings={
        "template<class T>struct Flag{static const bool value=true;};template<class T,bool A=Flag<T>::value,bool B=Flag<T>::value>struct Select{};Select<int> result;",
        "template<class T>struct Flag{static const bool value=true;};template<class T>struct Box{};template<bool B>struct Pick{};Pick<Flag<Box<int>>::value> result;",
        "template<int N>struct Pick{};Pick<(8>>1)> result;Pick<(3>2)> comparison;",
        "template<class T>struct Flag{static const bool value=true;};template<bool B>struct Pick{};Pick<Flag<int>::value && (8>>2)> result;",
        "template<class T>struct Flag{static const bool value=true;};template<class T,bool B=Flag<T>::value>int take(T value){return value;}int main(){return take(0);}"
    }) void respectsTypeArgumentsAndExpressionDelimiters(String source){var parser=parse(source);assertTrue(parser.succeeded(),parser.errors()::toString);}
    @ParameterizedTest @ValueSource(strings={
        "template<class T>struct Gate{typedef int type;};template<class T,typename Gate<T>::type N=0>struct Pick{};Pick<int> item;",
        "template<class T,typename T::value_type N=0>int take(T value){return N;}",
        "template<class T>struct Gate{typedef int type;};template<class T>struct Box{template<class U=T,typename Gate<U>::type N=0>Box(){};};"
    }) void typenameSpecifierCanIntroduceNonTypeParameters(String source){var parser=parse(source);assertTrue(parser.succeeded(),parser.errors()::toString);}
    @Test void standaloneUtilityHeaderParsesWithoutCascadingDeclarationLoss(){var parser=parse("#include <utility>\nint main(){return 0;}");assertTrue(parser.succeeded(),parser.errors()::toString);}
    @Test void standaloneTypeTraitsHeaderParsesWithoutCascadingDeclarationLoss(){var parser=parse("#include <type_traits>\nint main(){return 0;}");assertTrue(parser.succeeded(),parser.errors()::toString);}
    @Test void dependentDefaultRetainsItsWholeSourceRangeAndFollowingDeclaration() {
        String prefix="template<class T>struct Flag{static const bool value=true;};\n";
        String declaration="template<class T,bool B=Flag<T>::value>struct Pick{};";
        var parser=parse(prefix+declaration+"\nint after;");
        assertTrue(parser.succeeded(),parser.errors()::toString);
        var template=(ClassTemplateDecl)parser.result().program().declarations().get(1);
        var parameter=(ClassTemplateDecl.ValueParameter)template.parameters().get(1);
        var value=assertInstanceOf(CppTypeMemberExpr.class,parameter.defaultValue());
        assertEquals(2,value.range().startLine());
        assertEquals("Flag<T>::value",declaration.substring(value.range().startByte(),value.range().endByte()));
        assertEquals("after",parser.result().program().globals().getFirst().name());
    }
    @Test void fullTypeTraitsAcceptanceProgramReachesTheSemanticStage() throws Exception {
        try(var input=getClass().getResourceAsStream("/cpp/library-noexcept/type-traits-only.cpp")) {
            assertNotNull(input);
            var parser=parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            assertTrue(parser.succeeded(),parser.errors()::toString);
        }
    }
    @Test void missingTemplateArgumentRetainsSourceDiagnostic(){var parser=parse("template<int N>struct Pick{};Pick<> bad;int after;");assertFalse(parser.succeeded());assertTrue(parser.errors().stream().allMatch(d->d.range().startLine()==1));}
}
