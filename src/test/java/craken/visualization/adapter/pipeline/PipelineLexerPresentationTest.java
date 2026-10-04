package craken.visualization.adapter.pipeline;

import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.lexer.LexerResult;
import craken.ui.pipeline.PipelineSession;
import craken.visualization.snapshot.VisualizationSnapshot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class PipelineLexerPresentationTest {
    @TempDir Path directory;

    @Test void macroTokensWithTheSameOriginalRangeHighlightOnlyTheTokenJustEmitted() throws Exception {
        var compiler = new CompilerApi(new SourceFile("macro-highlight.mc",
                "#define SUM 1+2\nint main(){return SUM;}"), directory);
        try (var session = new PipelineSession(compiler)) {
            session.nextStage(() -> false);
            var frame = emit(session, compiler, "+");
            var output = frame.output();
            var highlighted = accessed(output);
            assertEquals("+", highlighted.content().fields().get("lexeme"),
                    "A macro may map several tokens to one original source range; focus must use the emitted token identity");
            assertEquals(1, highlighted.highlights().size());
        }
    }

    @Test void includedLinesDoNotShiftTheExpandedSourceHighlightToAnotherDeclaration() throws Exception {
        Files.writeString(directory.resolve("declarations.mh"), "int first;\nint second;\nint third;\n");
        var compiler = new CompilerApi(new SourceFile(directory.resolve("include-highlight.mc").toString(),
                "#include \"declarations.mh\"\nint main(){return 1;}\n"), directory);
        try (var session = new PipelineSession(compiler)) {
            session.nextStage(() -> false);
            var frame = emit(session, compiler, "main");
            assertEquals("int main(){return 1;}", accessed(frame.input()).content().fields().get("text"),
                    "Input highlights are in the expanded text coordinate space, not the original file's line numbers");
        }
    }

    @Test void escapedUnicodeStringAndCharacterUseTheirRawExpandedSpelling() throws Exception {
        Files.writeString(directory.resolve("declarations.mh"), "int first;\r\nint second;\r\nint third;\r\n");
        String stringLine = "  const char* text = \"first\\n第二\";";
        String characterLine = "  char letter = '\\n';";
        var compiler = new CompilerApi(new SourceFile(directory.resolve("literals.mc").toString(),
                "#include \"declarations.mh\"\r\nint main(){\r\n" + stringLine + "\r\n" + characterLine + "\r\nreturn 0;}"), directory);
        try (var session = new PipelineSession(compiler)) {
            session.nextStage(() -> false);
            var stringFrame = emit(session, compiler, "\"first\\n第二\"");
            assertEquals(stringLine + "\r", accessed(stringFrame.input()).content().fields().get("text"));
            assertEquals("first\n第二", accessed(stringFrame.output()).content().fields().get("literal"));
            assertEquals("\"first\\n第二\"", accessed(stringFrame.output()).content().fields().get("lexeme"));
            var characterFrame = emit(session, compiler, "'\\n'");
            assertEquals(characterLine + "\r", accessed(characterFrame.input()).content().fields().get("text"));
            assertEquals("'\\n'", accessed(characterFrame.output()).content().fields().get("lexeme"));
        }
    }

    @Test void skippingABlockCommentKeepsEveryConsumedExpandedLineHighlighted() throws Exception {
        var compiler = new CompilerApi(new SourceFile("comment-highlight.mc",
                "int main(){ /*first\n  second\n  third */return 0;}"), directory);
        try (var session = new PipelineSession(compiler)) {
            session.nextStage(() -> false);
            for (int remaining = 200; remaining > 0 && compiler.currentStageIndex() == 1; remaining--) {
                session.nextStep(() -> false);
                assertFalse(session.snapshot().visualizationPending(), session.snapshot().visualizationError());
                var result = compiler.lastStepResult().orElseThrow();
                if (result.operation().equals("SKIP") && result.sourceRange().endLine() > result.sourceRange().startLine()) {
                    var frame = session.snapshot().visualization();
                    assertEquals(3, accessed(frame.input()).highlights().size(),
                            "One real lexer step consumes the entire three-line block comment");
                    assertTrue(accessed(frame.output()).highlights().isEmpty(), "Skipping emits no token");
                    return;
                }
            }
            fail("Real lexer did not skip the multiline comment");
        }
    }

    @Test void aMultilineLiteralDiagnosticUsesItsActualExpandedSpanAndEmitsNoToken() throws Exception {
        Files.writeString(directory.resolve("declarations.mh"), "int first;\nint second;\nint third;\n");
        String invalidLine = "  char* text = \"unterminated";
        var compiler = new CompilerApi(new SourceFile(directory.resolve("diagnostic.mc").toString(),
                "#include \"declarations.mh\"\nint main(){\n" + invalidLine + "\nreturn 0;}"), directory);
        try (var session = new PipelineSession(compiler)) {
            session.nextStage(() -> false);
            for (int remaining = 200; remaining > 0 && compiler.currentStageIndex() == 1; remaining--) {
                session.nextStep(() -> false);
                assertFalse(session.snapshot().visualizationPending(), session.snapshot().visualizationError());
                var result = compiler.lastStepResult().orElseThrow();
                if (result.operation().equals("DIAGNOSTIC")) {
                    assertTrue(result.hasError());
                    var frame = session.snapshot().visualization();
                    assertEquals(invalidLine, accessed(frame.input()).content().fields().get("text"));
                    assertEquals(1, accessed(frame.input()).highlights().size());
                    assertTrue(accessed(frame.output()).highlights().isEmpty());
                    return;
                }
            }
            fail("Real lexer did not report the multiline string diagnostic");
        }
    }

    private static PipelineVisualizationFrame emit(PipelineSession session, CompilerApi compiler, String lexeme) throws Exception {
        for (int remaining = 200; remaining > 0 && compiler.currentStageIndex() == 1; remaining--) {
            session.nextStep(() -> false);
            assertFalse(session.snapshot().visualizationPending(), session.snapshot().visualizationError());
            assertFalse(session.snapshot().failed(), session.snapshot().stages().toString());
            var result = compiler.lastStepResult().orElseThrow();
            var context = (LexerResult) result.context();
            if (result.operation().startsWith("EMIT_") && context.tokens().getLast().lexeme().equals(lexeme))
                return session.snapshot().visualization();
        }
        throw new AssertionError("Real lexer did not emit " + lexeme);
    }

    private static VisualizationSnapshot.NodeState accessed(VisualizationSnapshot snapshot) {
        var location = snapshot.interaction().accessed();
        assertNotNull(location);
        return snapshot.pages().get(location.pageId()).nodes().get(location.nodeId());
    }
}
