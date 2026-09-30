package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("stl-contract")
final class CppLanguageModeTest {
    @Test
    void defaultCModeDoesNotReserveCppNamesEvenWithCppExtension() {
        var api = new CompilerApi(new SourceFile("legacy.cpp",
                "int main() { int class = 3; int namespace = 4; return class + namespace; }"));
        assertNotNull(api.runToIr().findFunction("main").orElseThrow());
        assertEquals(LanguageMode.C, stage(api, Lexer.class).languageMode());
        assertEquals(LanguageMode.C, stage(api, Parser.class).languageMode());
    }

    @Test
    void cppModeFlowsThroughPipelineWithoutChangingOrdinaryCSourceRanges() {
        var source = new SourceFile("mode.mc", "// 中文位置\nint main() { return 2 + 3; }");
        var api = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM);
        assertNotNull(api.runToIr().findFunction("main").orElseThrow());
        var parser = stage(api, Parser.class);
        assertEquals(LanguageMode.CPP17_ALGORITHM, parser.languageMode());
        assertEquals(LanguageMode.CPP17_ALGORITHM, stage(api, Preprocessor.class).languageMode());
        assertEquals("int main() { return 2 + 3; }",
                source.text(parser.result().program().functions().getFirst().range()));
    }

    @Test
    void cppKeywordsAndScopeAreTokensButShiftAndGreaterRemainDistinct() {
        var lexer = new Lexer(new SourceFile("tokens.cpp",
                "namespace std::vector<class T> >> > template typename using auto constexpr operator this nullptr noexcept"),
                LanguageMode.CPP17_ALGORITHM);
        assertEquals(List.of(TokenType.NAMESPACE, TokenType.IDENTIFIER, TokenType.SCOPE,
                        TokenType.IDENTIFIER, TokenType.LESS, TokenType.CLASS, TokenType.IDENTIFIER,
                        TokenType.GREATER, TokenType.GREATER_GREATER, TokenType.GREATER,
                        TokenType.TEMPLATE, TokenType.TYPENAME, TokenType.USING, TokenType.AUTO,
                        TokenType.CONSTEXPR, TokenType.OPERATOR, TokenType.THIS, TokenType.NULLPTR,
                        TokenType.NOEXCEPT, TokenType.EOF),
                lexer.lex().tokens().stream().map(token -> token.type()).toList());
        assertTrue(lexer.errors().isEmpty());
        var c = new Lexer(new SourceFile("tokens.mc", "namespace ::"));
        assertEquals(List.of(TokenType.IDENTIFIER, TokenType.COLON, TokenType.COLON, TokenType.EOF),
                c.lex().tokens().stream().map(token -> token.type()).toList());
    }

    @Test
    void unsupportedCppSyntaxProducesExplicitErrorAtOriginalMacroUse() {
        var source = new SourceFile("unsupported.cpp", "#define DECL template\n\nDECL<class T> struct Box {};\n");
        var api = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM);
        var parser = stage(api, Parser.class);
        api.runThrough(parser);
        var error = parser.errors().stream().filter(d -> d.code().equals("CPP001")).findFirst().orElseThrow();
        assertTrue(error.message().contains("template"), error::message);
        assertEquals(3, error.range().startLine());
        assertEquals("DECL", source.text(error.range()));
        assertFalse(parser.succeeded());
    }

    @Test
    void explicitTokenListParserRetainsModeAndRejectsReservedIdentifiers() {
        var lexer = new Lexer(new SourceFile("reserved.cpp", "int main() { int class = 1; return 0; }"),
                LanguageMode.CPP17_ALGORITHM);
        var parser = new Parser(lexer.lex().tokens(), LanguageMode.CPP17_ALGORITHM, true);
        parser.parse();
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")));
        assertEquals(LanguageMode.CPP17_ALGORITHM, parser.languageMode());
    }

    private static <T> T stage(CompilerApi api, Class<T> type) {
        return api.stages().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    @Test
    void debugUsesSelectedLanguageAndPreservesHistory() {
        var source = new SourceFile("debug.cpp", "int main() { return (true and not false) ? 7 : 0; }");
        var debug = new DebugApi(source, "", LanguageMode.CPP17_ALGORITHM);
        while (debug.canNext()) debug.next();
        var completed = debug.current();
        assertEquals(Debugger.Status.COMPLETED, completed.stop().status());
        assertEquals(7, completed.runtime().termination().status());
        assertTrue(debug.canPrevious());
        debug.previous();
        assertSame(completed, debug.next());
        assertThrows(IllegalStateException.class, () -> new DebugApi(source));
    }

    @Test
    void standalonePreprocessorHandsItsModeToLexer() {
        var preprocessor = new Preprocessor(new SourceFile("handoff.cpp", "class"),
                Preprocessor.Options.defaults(LanguageMode.CPP17_ALGORITHM));
        new CompilerApi(List.of(preprocessor)).run();
        var lexer = new Lexer(preprocessor);
        assertEquals(LanguageMode.CPP17_ALGORITHM, lexer.languageMode());
        assertEquals(TokenType.CLASS, lexer.lex().tokens().getFirst().type());
    }

    @Test
    void unsupportedReservedCppWordsCannotBecomeUserIdentifiers() {
        for (String word : List.of("asm", "export", "goto", "register")) {
            var source = new SourceFile("reserved.cpp", "int main() { int " + word + " = 1; return 0; }");
            var api = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM);
            var parser = stage(api, Parser.class);
            api.runThrough(parser);
            assertFalse(parser.succeeded(), word + " must remain reserved in C++17");
            assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")), word);
        }
    }
}
