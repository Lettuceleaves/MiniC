package minic.runtime.debug;

import minic.compiler.ast.decl.Program;
import minic.compiler.ir.lowering.IrLowerer;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DataStructureVisualizationBaselineTest {
    @Test
    void structInitRunsAndPreservesRealFieldNames() {
        DebugSession session = assertDoesNotThrow(() -> runSample("struct_init.mc"));

        assertAll(
                () -> assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED),
                () -> assertThat(session.currentSnapshot().processSpace().io().stdout())
                        .isEqualTo("return 34"),
                () -> assertThat(localsNamed(session, "p"))
                        .as("struct p snapshots")
                        .anySatisfy(local -> assertThat(fieldNames(local.value()))
                                .contains("x", "y")
                                .doesNotContain("field0", "field1"))
        );
    }

    @Test
    void structArrayRunsAndPreservesStructElementFields() {
        DebugSession session = assertDoesNotThrow(() -> runSample("struct_array.mc"));

        assertAll(
                () -> assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED),
                () -> assertThat(session.currentSnapshot().processSpace().io().stdout())
                        .isEqualTo("return 100"),
                () -> assertThat(localsNamed(session, "arr"))
                        .as("struct array snapshots")
                        .anySatisfy(local -> {
                            assertThat(local.value().elements()).hasSize(3);
                            assertThat(local.value().elements())
                                    .anySatisfy(element -> assertStructElement(element, 0, "10", "20"))
                                    .anySatisfy(element -> assertStructElement(element, 1, "30", "40"))
                                    .anySatisfy(element -> assertStructElement(element, 2, "50", "60"));
                        })
        );
    }

    @Test
    void existingDataStructureSamplesCanEstablishDebugSessions() {
        List<String> samples = List.of(
                "debugger_basic.mc",
                "debugger_array_matrix.mc",
                "struct_linked.mc",
                "struct_multiptr.mc"
        );

        for (String sample : samples) {
            DebugSession session = assertDoesNotThrow(() -> runSample(sample), sample);

            assertThat(session.state())
                    .as(sample + " state")
                    .isEqualTo(DebugExecutionState.COMPLETED);
            assertThat(session.snapshots())
                    .as(sample + " snapshots")
                    .isNotEmpty();
        }
    }

    private DebugSession runSample(String sampleName) throws IOException {
        SourceFile sourceFile = sampleSource(sampleName);
        return new IrDebugInterpreter().runMain(lower(sourceFile), sourceFile);
    }

    private SourceFile sampleSource(String sampleName) throws IOException {
        Path samplePath = Path.of(System.getProperty("user.dir"), "samples", sampleName);
        return new SourceFile(sampleName, Files.readString(samplePath));
    }

    private List<DebugMemoryEntry> localsNamed(DebugSession session, String name) {
        return session.snapshots().stream()
                .flatMap(snapshot -> snapshot.processSpace().stack().frames().stream())
                .flatMap(frame -> frame.locals().stream())
                .filter(local -> local.name().equals(name))
                .toList();
    }

    private List<String> fieldNames(DebugValue value) {
        return value.fields().stream()
                .map(DebugValueField::name)
                .toList();
    }

    private void assertStructElement(DebugValueElement element, long index, String x, String y) {
        assertThat(element.index()).isEqualTo(index);
        assertThat(fieldNames(element.value()))
                .contains("x", "y")
                .doesNotContain("field0", "field1");
        assertThat(fieldValue(element.value(), "x").summary()).isEqualTo(x);
        assertThat(fieldValue(element.value(), "y").summary()).isEqualTo(y);
    }

    private DebugValue fieldValue(DebugValue value, String fieldName) {
        return value.fields().stream()
                .filter(field -> field.name().equals(fieldName))
                .map(DebugValueField::value)
                .findFirst()
                .orElseThrow();
    }

    private IrModule lower(SourceFile sourceFile) {
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        Program program = parseResult.program();
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).as(semanticResult.diagnostics().toString()).isEmpty();
        return new IrLowerer().lower(program, semanticResult);
    }
}
