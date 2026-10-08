package craken.ui.editor.completion;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("visualization-model")
final class CompletionEngineTest {
    private static final CompletionCatalog CATALOG = new CompletionCatalog(
            List.of("stdio.h", "stdlib.h", "vector", "bits/stdc++.h"),
            List.of("stdio.mh", "util.mh"));

    @Test
    void keywordPrefixYieldsOnlyMatchingKeywords() {
        var result = completeAt("int main() { wh| }");

        assertEquals(List.of(new CompletionCandidate("while", CompletionKind.KEYWORD)),
                result.candidates());
        assertEquals("wh", result.prefix().text());
        assertEquals(CompletionContext.IDENTIFIER, result.prefix().context());
    }

    @Test
    void keywordMatchingIsCaseSensitive() {
        assertTrue(completeAt("int main() { WH| }").candidates().isEmpty(),
                "WH 不应该匹配小写关键词 while");
    }

    @Test
    void declaredNamesAreSuggestedInFirstOccurrenceOrder() {
        String source = "int counter = 0;\nint total = 1;\nint main() { to| }";
        var result = completeAt(source);

        List<String> texts = result.candidates().stream()
                .map(CompletionCandidate::text).toList();
        assertEquals(List.of("total"), texts);
        assertEquals(CompletionKind.VARIABLE, result.candidates().getFirst().kind());
    }

    @Test
    void preprocessorNamesNeverLeakIntoIdentifierCompletion() {
        String source = "#include <stdio.h>\n#define LIMIT 4\nint main() { | }";
        var result = completeAt(source);

        List<String> texts = result.candidates().stream()
                .map(CompletionCandidate::text).toList();
        assertTrue(texts.contains("main"), "正文标识符应该保留：" + texts);
        assertFalse(texts.contains("include") || texts.contains("stdio")
                        || texts.contains("h") || texts.contains("define")
                        || texts.contains("LIMIT"),
                "预处理行里的名字不能成为候选：" + texts);
    }

    @Test
    void typingOnADirectiveLineOffersNoCodeCompletion() {
        assertTrue(completeAt("#define LIM|").candidates().isEmpty(),
                "在预处理指令行上不应弹出关键词候选");
    }

    @Test
    void typingInsideAnAngleIncludeOffersStandardHeaders() {
        var result = completeAt("#include <st|");

        assertEquals(CompletionContext.INCLUDE_ANGLE, result.prefix().context());
        assertEquals("st", result.prefix().text());
        assertEquals(List.of(
                        new CompletionCandidate("stdio.h", CompletionKind.HEADER),
                        new CompletionCandidate("stdlib.h", CompletionKind.HEADER)),
                result.candidates());
    }

    @Test
    void typingInsideAQuotedIncludeOffersMhSpellings() {
        var result = completeAt("#include \"ut|");

        assertEquals(CompletionContext.INCLUDE_QUOTE, result.prefix().context());
        assertEquals(List.of(new CompletionCandidate("util.mh", CompletionKind.HEADER)),
                result.candidates());
    }

    @Test
    void noSuggestionsInsideStringCharOrComment() {
        assertTrue(completeAt("int main() { printf(\"fo|\"); }").candidates().isEmpty());
        assertTrue(completeAt("int a; // re|").candidates().isEmpty());
        assertTrue(completeAt("/* in|t */").candidates().isEmpty());
        assertTrue(completeAt("char c = 'a|").candidates().isEmpty());
    }

    @Test
    void aCommentedOutIncludeOffersNothing() {
        assertTrue(completeAt("// #include <st|").candidates().isEmpty());
        assertTrue(completeAt("/* #include <st| */").candidates().isEmpty());
    }

    @Test
    void anAlreadyCompleteKeywordIsNotOfferedAgain() {
        assertTrue(completeAt("int|").candidates().isEmpty(),
                "前缀已经是完整关键词时不应再提供同样的关键词");
    }

    @Test
    void emptyPrefixReturnsKeywordsThenDeclaredNamesForForcedCompletion() {
        var result = completeAt("int answer = 42;\nint main() { | }");

        assertEquals(CompletionKind.KEYWORD, result.candidates().getFirst().kind());
        assertEquals("bool", result.candidates().getFirst().text());
        assertTrue(result.candidates().stream()
                        .anyMatch(candidate -> candidate.text().equals("answer")
                                && candidate.kind() == CompletionKind.VARIABLE),
                "空前缀必须同时给出关键词与变量：" + result.candidates());
    }

    @Test
    void prefixAtClampsAndReportsReplacementRange() {
        String source = "int main() { wh";
        var prefix = CompletionEngine.prefixAt(source, source.length());
        assertEquals("wh", prefix.text());
        assertEquals(source.length() - 2, prefix.startOffset());
        assertEquals(source.length(), prefix.endOffset());

        var angle = CompletionEngine.prefixAt("#include <st", 12);
        assertEquals(CompletionContext.INCLUDE_ANGLE, angle.context());
        assertEquals("st", angle.text());
        assertEquals(10, angle.startOffset());
    }

    @Test
    void catalogReadsTheLibraryLayoutAndSkipsInternalHeaders(@TempDir Path library) throws Exception {
        Files.createDirectories(library.resolve("stl"));
        Files.writeString(library.resolve("stdio.mh"), "");
        Files.writeString(library.resolve("stl").resolve("vector.mh"), "");
        Files.writeString(library.resolve("stl").resolve("__all.mh"), "");
        Files.writeString(library.resolve("stl").resolve("__tree.mh"), "");

        CompletionCatalog catalog = CompletionCatalog.fromDirectories(library, null);

        assertTrue(catalog.angleHeaders().containsAll(List.of("stdio.h", "vector", "bits/stdc++.h")));
        assertFalse(catalog.angleHeaders().contains("__all"));
        assertFalse(catalog.angleHeaders().contains("__tree"));
        assertTrue(catalog.quoteHeaders().contains("stdio.mh"));
    }

    @Test
    void localDirectoryHeadersComeFirstInQuotedIncludes(@TempDir Path sourceDirectory) throws Exception {
        Files.writeString(sourceDirectory.resolve("util.mh"), "");

        CompletionCatalog catalog = CompletionCatalog.fromDirectories(null, sourceDirectory);
        var result = CompletionEngine.complete("#include \"uti", 13, catalog);

        assertEquals(List.of(new CompletionCandidate("util.mh", CompletionKind.HEADER)),
                result.candidates());
    }

    private static CompletionResult completeAt(String sourceWithCaret) {
        int caret = sourceWithCaret.indexOf('|');
        assertTrue(caret >= 0, "缺少光标标记 '|'");
        String source = sourceWithCaret.substring(0, caret) + sourceWithCaret.substring(caret + 1);
        return CompletionEngine.complete(source, caret, CATALOG);
    }
}
