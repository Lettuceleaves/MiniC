package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.StringLiteralExpr;
import minic.compiler.parser.node.Statement.ExprStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.cpp.CppOverloadResolver;
import minic.compiler.semantic.cpp.CppValueCategory;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** C++ literal source arrays retain the existing native string storage representation. */
@Timeout(90)
class CppStringLiteralTypeTest {
    @TempDir Path temporary;

    static Stream<Arguments> literals() {
        // UTF16/32 retain MiniC's existing unsigned-short/int representation; this does not
        // assert distinct C++ char16_t/char32_t overload identities, which are not implemented.
        return Stream.of(Arguments.of("\"\"", MiniType.CHAR, 1), Arguments.of("\"abc\"", MiniType.CHAR, 4),
                Arguments.of("\"中\"", MiniType.CHAR, 4), Arguments.of("\"😀\"", MiniType.CHAR, 5),
                Arguments.of("\"a\\0b\"", MiniType.CHAR, 4), Arguments.of("u8\"中\"", MiniType.CHAR, 4),
                Arguments.of("u\"😀\"", MiniType.UNSIGNED_SHORT, 3), Arguments.of("U\"😀\"", MiniType.UNSIGNED_INT, 2));
    }

    @ParameterizedTest @MethodSource("literals")
    void arrayTypeCountsEncodedCodeUnitsAndTerminator(String spelling, MiniType element, int count) {
        var result = analyze("int main(){" + spelling + "; return 0;}", LanguageMode.CPP17_ALGORITHM).semanticResult();
        var source = ((ExprStmt) result.sourceProgram().functions().getFirst().body().statements().getFirst()).expression();
        assertInstanceOf(StringLiteralExpr.class, source);
        MiniType expected = MiniType.qualified(element, java.util.Set.of(MiniType.TypeQualifier.CONST)).arrayOf(count);
        assertEquals(expected, result.typeOf(source).orElseThrow());
        var core = assertInstanceOf(Expression.class, result.sourceToCore().get(source));
        assertEquals(expected, result.typeOf(core).orElseThrow());
        assertEquals(source.range(), core.range());
    }

    @Test void sizeofUsesEncodedStorageLengthLikeCpp17() throws Exception {
        CppReferenceTest.agree(temporary, "literal-size", """
                #include <stdio.h>
                int main(){printf("%llu %llu %llu %llu %llu %llu %llu %llu\\n",
                    sizeof(""),sizeof("abc"),sizeof("中"),sizeof("😀"),sizeof("a\\0b"),sizeof(u8"中"),sizeof(u"😀"),sizeof(U"😀"));return 0;}
                """, "1 4 4 5 4 4 6 8\n");
    }

    @Test void literalArrayReferenceAndPointerToArrayHaveStableStorageAndNullTerminator() throws Exception {
        CppReferenceTest.agree(temporary, "literal-reference", """
                #include <stdio.h>
                int read(const char (&text)[4]){return text[0]+text[3];}
                int main(){const char (&text)[4]="abc";const char (*pointer)[4]=&text;
                    const char (*direct)[4]=&"xyz";const char *decayed="hello";
                    printf("%d %d %d %llu %d\\n",read("abc"),(*pointer)[1],(*direct)[2],sizeof(text),decayed[5]);return 0;}
                """, "97 98 122 4 0\n");
    }

    @Test void literalIndexAndPointerArithmeticRetainElementConstAndWidth() throws Exception {
        CppReferenceTest.agree(temporary, "literal-decay", """
                #include <stdio.h>
                int main(){const char *p="abc"+1;printf("%d %d %llu\\n",*p,"abc"[2],sizeof(*&"abc"));return 0;}
                """, "98 99 4\n");
    }

    @Test void stdioAndNumericConversionHeadersAcceptReadonlyInputWithoutChangingWritableBuffers() throws Exception {
        CppReferenceTest.agree(temporary, "readonly-library-input", """
                #include <stdio.h>
                #include <stdlib.h>
                int main(){char output[32];int parsed=0;sprintf(output,"%d",atoi("12"));sscanf("7","%d",&parsed);
                    printf("%s %d %ld %lld %.1f\\n",output,parsed,atol("-3"),atoll("20"),atof("2.5"));return 0;}
                """, "12 7 -3 20 2.5\n");
    }

    @Test void assertLocaleAndTimeHeadersAcceptReadonlyLiteralInputs() {
        analyze("""
                #include <assert.h>
                #include <locale.h>
                #include <time.h>
                int main(){char output[32];struct tm value={};assert(1);setlocale(LC_ALL,"C");
                    strftime(output,32,"%Y",&value);return 0;}
                """, LanguageMode.CPP17_ALGORITHM);
    }

    @ParameterizedTest @ValueSource(strings = {
            "int main(){\"abc\"[0]='z';return 0;}// bad\n",
            "int main(){char *pointer=\"abc\";return 0;}// bad\n",
            "int read(char *pointer){return 0;}\nint main(){return read(\"abc\");}// bad\n"
    })
    void literalCannotLoseConstOrBeModified(String source) throws Exception {
        CppReferenceTest.reject(temporary, "literal-const", source);
    }

    @Test void sourceTypeMakesOnlyTheConstPointerOverloadViable() {
        var result = analyze("int main(){\"abc\";return 0;}", LanguageMode.CPP17_ALGORITHM).semanticResult();
        var literal = ((ExprStmt) result.sourceProgram().functions().getFirst().body().statements().getFirst()).expression();
        var writable = new CppOverloadResolver.Candidate<>("writable", List.of(MiniType.CHAR.pointerTo()), false);
        var readonly = new CppOverloadResolver.Candidate<>("readonly", List.of(MiniType.qualified(MiniType.CHAR,
                java.util.Set.of(MiniType.TypeQualifier.CONST)).pointerTo()), false);
        var resolution = CppOverloadResolver.resolve(List.of(writable, readonly), List.of(new CppOverloadResolver.Argument(
                result.typeOf(literal).orElseThrow(), CppValueCategory.LVALUE, false)));
        assertEquals(CppOverloadResolver.Status.SELECTED, resolution.status());
        assertEquals("readonly", resolution.winner().identity());
        assertEquals(List.of(readonly), resolution.viable());
    }

    @Test void cModeKeepsItsExistingLiteralRepresentation() {
        var result = analyze("int main(){\"abc\";return 0;}", LanguageMode.C).semanticResult();
        var literal = ((ExprStmt) result.program().functions().getFirst().body().statements().getFirst()).expression();
        assertInstanceOf(StringLiteralExpr.class, literal);
        assertEquals(MiniType.CHAR.pointerTo(), result.typeOf(literal).orElseThrow());
    }

    @Test void characterArrayCopyInitializationAcceptsLocalGlobalAndNestedStorage() {
        for (String declaration : List.of("int main(){char text[]=\"abc\";return 0;}",
                "char text[]=\"abc\";int main(){return 0;}",
                "int main(){char text[4]=\"abc\";return 0;}",
                "char text[4]=\"abc\";int main(){return 0;}",
                "struct Box{char text[4];};int main(){Box box={\"abc\"};return 0;}",
                "int main(){char text[2][4]={{\"abc\"},{\"xyz\"}};return 0;}")) {
            var api = new CompilerApi(new SourceFile("array-copy.cpp", declaration), LanguageMode.CPP17_ALGORITHM);
            var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
            api.runThrough(semantic);
            assertTrue(semantic.succeeded(), () -> declaration + " " + semantic.errors());
        }
    }

    private static SemanticAnalyzer analyze(String text, LanguageMode mode) {
        var api = new CompilerApi(new SourceFile("literal.cpp", text), mode);
        var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        return semantic;
    }
}
