package minic.uilocal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCUiLocalBoundaryRegressionTest {
    private static final List<String> FORBIDDEN_SNIPPETS = List.of(
            "import minic.compiler.",
            "import minic.runtime.",
            "import minic.source.",
            "import minic.session.",
            "import minic.diagnostics.",
            "minic.compiler.",
            "minic.runtime.",
            "minic.source.",
            "minic.session.",
            "minic.diagnostics.",
            "minic.uiapi.web.",
            "UiAstVisualBuilder",
            "UiSemanticScopeVisualBuilder",
            "UiDebugAstViewBuilder",
            "UiDebugIrViewBuilder",
            "UiDebugAsmViewBuilder",
            "UiDebugMetadataViewBuilder",
            "UiDebugDataStructureViewBuilder",
            "MiniCUiApiRouter",
            "MiniCUiApiSessionStore",
            "stdin pending",
            "stdin confirmed",
            "new Lexer(",
            "new Parser(",
            "new SemanticAnalyzer(",
            "new MiniCPreprocessor(",
            "CompileObservationSession",
            "IrDebugInterpreter",
            "IrLowerer",
            "SourceFile("
    );

    @Test
    void uilocalDoesNotReferenceCompilerRuntimeSourceSessionOrDiagnosticsPackages() throws IOException {
        Path root = Path.of("src", "main", "java", "minic", "uilocal");
        List<String> forbiddenReferences = Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> forbiddenReferences(path).stream())
                .toList();

        assertThat(forbiddenReferences).isEmpty();
    }

    private static List<String> forbiddenReferences(Path path) {
        try {
            return Files.readAllLines(path).stream()
                    .filter(MiniCUiLocalBoundaryRegressionTest::containsForbiddenSnippet)
                    .map(line -> path + ": " + line)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private static boolean containsForbiddenSnippet(String line) {
        return FORBIDDEN_SNIPPETS.stream().anyMatch(line::contains);
    }
}
