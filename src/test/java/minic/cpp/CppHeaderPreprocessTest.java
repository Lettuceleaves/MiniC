package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.library.CppLibraryProfile;
import minic.compiler.preprocess.Preprocessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-frontend")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
final class CppHeaderPreprocessTest {
    @TempDir Path temporary;
    private String previousRoot;

    @BeforeEach
    void isolateTheLibraryRoot() {
        previousRoot = System.getProperty("minic.project.root");
        System.setProperty("minic.project.root", temporary.toString());
    }

    @AfterEach
    void restoreTheLibraryRoot() {
        if (previousRoot == null) System.clearProperty("minic.project.root");
        else System.setProperty("minic.project.root", previousRoot);
    }

    @Test
    void mapsKnownCppHeadersToTheirExactInternalLocationWithoutChangingCapabilityProfile() throws Exception {
        var profileBefore = CppLibraryProfile.defaults();
        Path installed = write("lib/cpp/utility.mh", "int installed_utility;\n");
        var preprocessor = preprocess("#include <utility>\n", LanguageMode.CPP17_ALGORITHM);
        assertTrue(preprocessor.succeeded(), () -> preprocessor.errors().toString());
        assertEquals("int installed_utility;\n", preprocessor.preprocessResult().sourceFile().content());
        var include = preprocessor.preprocessResult().includes().getFirst();
        assertEquals("utility", include.requestedPath());
        assertEquals(installed, include.resolvedPath());
        assertTrue(include.expanded());
        assertEquals(profileBefore, CppLibraryProfile.defaults(), "Header resolution cannot promote API capabilities");
    }

    @Test
    void preservesGuardsRelativeNestedIncludesAndCppLexerMode() throws Exception {
        write("lib/cpp/detail.mh", "#ifndef TEST_DETAIL\n#define TEST_DETAIL\nnamespace helper { int marker; }\n#endif\n");
        write("lib/cpp/utility.mh", "#ifndef TEST_UTILITY\n#define TEST_UTILITY\n"
                + "#include \"detail.mh\"\nint utility_marker;\n#endif\n");
        var preprocessor = preprocess("#include <utility>\n#include <utility>\nhelper::marker;\n", LanguageMode.CPP17_ALGORITHM);
        assertTrue(preprocessor.succeeded(), () -> preprocessor.errors().toString());
        String expanded = preprocessor.preprocessResult().sourceFile().content();
        assertEquals(1, expanded.split("int utility_marker;", -1).length - 1);
        assertEquals(1, expanded.split("int marker;", -1).length - 1);
        assertEquals(3, preprocessor.preprocessResult().includes().size());
        Lexer lexer = new Lexer(preprocessor);
        lexer.lex();
        assertTrue(lexer.succeeded(), () -> lexer.errors().toString());
        assertEquals(LanguageMode.CPP17_ALGORITHM, lexer.languageMode());
        assertTrue(lexer.tokens().stream().anyMatch(token -> token.type() == TokenType.NAMESPACE));
        assertTrue(lexer.tokens().stream().anyMatch(token -> token.type() == TokenType.SCOPE));
    }

    @Test
    void systemHeadersCannotBeShadowedBySourceOrExplicitIncludeDirectories() throws Exception {
        write("lib/cpp/utility.mh", "int installed_marker;\n");
        write("source/utility.mh", "int local_impostor;\n");
        write("source/cpp/utility.mh", "int nested_impostor;\n");
        write("extra/utility.mh", "int include_impostor;\n");
        var stage = new Preprocessor();
        stage.preprocess(new SourceFile(temporary.resolve("source/main.cpp").toString(), "#include <utility>\n"),
                new Preprocessor.Options(List.of(temporary.resolve("source"), temporary.resolve("extra")), LanguageMode.CPP17_ALGORITHM));
        assertTrue(stage.succeeded(), () -> stage.errors().toString());
        assertEquals("int installed_marker;\n", stage.preprocessResult().sourceFile().content());
    }

    @Test
    void reportsKnownButUnimplementedHeadersInsteadOfCreatingEmptyStubs() {
        var stage = preprocess("\n#include <vector>\n", LanguageMode.CPP17_ALGORITHM);
        assertFalse(stage.succeeded());
        assertEquals(1, stage.errors().size());
        assertTrue(stage.errors().getFirst().message().contains("尚未实现或未安装"), () -> stage.errors().toString());
        assertTrue(stage.errors().getFirst().message().contains("lib/cpp/vector.mh"), () -> stage.errors().toString());
        assertEquals(2, stage.errors().getFirst().range().startLine());
        assertFalse(stage.preprocessResult().includes().getFirst().expanded());
        assertFalse(Files.exists(temporary.resolve("lib/cpp/vector.mh")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "../utility", "cpp/utility", "..\\utility", "utility.mh", "VECTOR", "utility.hpp"})
    void rejectsUnknownAndNonCanonicalCppHeaderNames(String name) throws Exception {
        write("lib/cpp/utility.mh", "int marker;\n");
        var stage = preprocess("#include <" + name + ">\n", LanguageMode.CPP17_ALGORITHM);
        assertFalse(stage.succeeded());
        assertEquals(1, stage.errors().size());
        assertTrue(stage.errors().getFirst().message().contains("未知或非法的 C++ 标准头文件"), () -> stage.errors().toString());
        assertFalse(stage.preprocessResult().includes().getFirst().expanded());
    }

    @Test
    void cModeStillRejectsExtensionlessSystemHeaders() throws Exception {
        write("lib/cpp/utility.mh", "int marker;\n");
        var stage = preprocess("#include <utility>\n", LanguageMode.C);
        assertFalse(stage.succeeded());
        assertTrue(stage.errors().getFirst().message().contains(".h 标准头文件名"));
    }

    @Test
    void dotHHeadersKeepTheExistingCLibraryMappingInEitherMode() throws Exception {
        Path installed = write("lib/stdio.mh", "int c_header_marker;\n");
        for (LanguageMode mode : LanguageMode.values()) {
            var stage = preprocess("#include <stdio.h>\n", mode);
            assertTrue(stage.succeeded(), () -> stage.errors().toString());
            assertEquals(installed, stage.preprocessResult().includes().getFirst().resolvedPath());
            assertEquals("int c_header_marker;\n", stage.preprocessResult().sourceFile().content());
        }
    }

    @Test
    void quotedMhHeadersKeepLocalResolutionInEitherMode() throws Exception {
        Path local = write("source/local.mh", "int local_marker;\n");
        write("lib/local.mh", "int system_marker;\n");
        for (LanguageMode mode : LanguageMode.values()) {
            var stage = preprocess("#include \"local.mh\"\n", mode);
            assertTrue(stage.succeeded(), () -> stage.errors().toString());
            assertEquals(local, stage.preprocessResult().includes().getFirst().resolvedPath());
            assertEquals("int local_marker;\n", stage.preprocessResult().sourceFile().content());
        }
    }

    private Preprocessor preprocess(String source, LanguageMode mode) {
        var stage = new Preprocessor();
        stage.preprocess(new SourceFile(temporary.resolve("source/main.cpp").toString(), source),
                Preprocessor.Options.defaults(mode));
        return stage;
    }

    private Path write(String relative, String content) throws IOException {
        Path path = temporary.resolve(relative).toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }
}
