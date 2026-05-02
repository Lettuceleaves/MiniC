package minic.compiler.parser;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.StructDecl;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.lexer.Token;
import minic.compiler.stage.CompilerStageStatus;
import minic.runtime.step.CompileStage;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserStepStateTest {
    @Test
    void advancesOneTopLevelAstNodeAtATimeAndBuildsEquivalentParseResult() {
        SourceFile sourceFile = new SourceFile(
                "parser-step.mc",
                """
                        struct Point { int x; };
                        int add(int a, int b) { return a + b; }
                        int main() { return add(1, 2); }
                        """
        );
        List<Token> tokens = lex(sourceFile);
        ParserStepState state = new ParserStepState(tokens);

        assertThat(state.stage()).isEqualTo(CompileStage.PARSER);
        assertThat(state.input().tokens()).containsExactlyElementsOf(tokens);
        assertThat(state.snapshot().status()).isEqualTo(CompilerStageStatus.NOT_STARTED);

        ParserStep first = state.next();
        ParserStep second = state.next();

        assertThat(first.nodeOptional()).hasValueSatisfying(node ->
                assertThat(node).isInstanceOf(StructDecl.class));
        assertThat(second.nodeOptional()).hasValueSatisfying(node ->
                assertThat(node).isInstanceOf(FunctionDecl.class));
        assertThat(state.currentNode()).hasValueSatisfying(node ->
                assertThat(((FunctionDecl) node).name()).isEqualTo("add"));
        assertThat(state.completedNodes()).hasSize(2);
        assertThat(state.work().completedNodeCount()).isEqualTo(2);
        assertThat(state.work().currentIndex()).isGreaterThan(0);

        while (state.canNext()) {
            state.next();
        }

        assertThat(state.snapshot().status()).isEqualTo(CompilerStageStatus.COMPLETED);
        assertThat(state.toParseResult().program().structs()).hasSize(1);
        assertThat(state.toParseResult().program().functions())
                .extracting(FunctionDecl::name)
                .containsExactly("add", "main");
        assertThat(state.result().output().parseResult().program().functions())
                .extracting(FunctionDecl::name)
                .containsExactlyElementsOf(new Parser(tokens).parse().program().functions().stream()
                        .map(FunctionDecl::name)
                        .toList());
    }

    @Test
    void advancesThroughInvalidTopLevelDeclarationAndKeepsDiagnostics() {
        SourceFile sourceFile = new SourceFile(
                "invalid-parser-step.mc",
                "main() {} int main() { return 0; }"
        );
        ParserStepState state = new ParserStepState(lex(sourceFile));

        ParserStep invalid = state.next();
        ParserStep valid = state.next();

        assertThat(invalid.nodeOptional()).isEmpty();
        assertThat(valid.nodeOptional()).hasValueSatisfying(node ->
                assertThat(((FunctionDecl) node).name()).isEqualTo("main"));
        assertThat(state.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("PAR001");
    }

    @Test
    void rejectsAdvancingAfterCompletion() {
        ParserStepState state = new ParserStepState(lex(new SourceFile("empty.mc", "")));

        assertThat(state.canNext()).isFalse();
        assertThat(state.toParseResult().program().functions()).isEmpty();
        assertThatThrownBy(state::next)
                .isInstanceOf(IllegalStateException.class);
    }

    private List<Token> lex(SourceFile sourceFile) {
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        return lexResult.tokens();
    }
}
