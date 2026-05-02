package minic.compiler.lexer;

import minic.compiler.stage.CompilerStageStatus;
import minic.runtime.step.CompileStage;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LexerStateTest {
    @Test
    void advancesOneTokenAtATimeAndBuildsEquivalentLexResult() {
        SourceFile sourceFile = new SourceFile("step.mc", "int x = 1;");
        LexerState state = new LexerState(sourceFile);

        assertThat(state.stage()).isEqualTo(CompileStage.LEXER);
        assertThat(state.input().sourceFile()).isSameAs(sourceFile);
        assertThat(state.snapshot().status()).isEqualTo(CompilerStageStatus.NOT_STARTED);

        LexStep first = state.next();
        LexStep second = state.next();

        assertThat(first.tokenOptional()).hasValueSatisfying(token ->
                assertThat(token.kind()).isEqualTo(TokenKind.INT));
        assertThat(second.tokenOptional()).hasValueSatisfying(token ->
                assertThat(token.lexeme()).isEqualTo("x"));
        assertThat(state.currentToken()).hasValueSatisfying(token ->
                assertThat(token.kind()).isEqualTo(TokenKind.IDENTIFIER));
        assertThat(state.tokens()).extracting(Token::kind)
                .containsExactly(TokenKind.INT, TokenKind.IDENTIFIER);
        assertThat(state.work().currentOffset()).isEqualTo(5);

        while (state.canNext()) {
            state.next();
        }

        assertThat(state.snapshot().status()).isEqualTo(CompilerStageStatus.COMPLETED);
        assertThat(state.toLexResult().tokens()).extracting(Token::kind)
                .containsExactly(
                        TokenKind.INT,
                        TokenKind.IDENTIFIER,
                        TokenKind.EQUAL,
                        TokenKind.INTEGER_LITERAL,
                        TokenKind.SEMICOLON,
                        TokenKind.EOF
                );
        assertThat(state.result().output().lexResult().tokens())
                .containsExactlyElementsOf(new Lexer(sourceFile).lex().tokens());
    }

    @Test
    void advancesOneDiagnosticAtATimeAndContinuesAfterRecoverableErrors() {
        SourceFile sourceFile = new SourceFile("invalid.mc", "int @ return");
        LexerState state = new LexerState(sourceFile);

        assertThat(state.next().tokenOptional()).hasValueSatisfying(token ->
                assertThat(token.kind()).isEqualTo(TokenKind.INT));
        LexStep diagnosticStep = state.next();

        assertThat(diagnosticStep.diagnosticOptional()).hasValueSatisfying(diagnostic ->
                assertThat(diagnostic.code()).isEqualTo("LEX001"));
        assertThat(state.currentDiagnostic()).hasValueSatisfying(diagnostic ->
                assertThat(diagnostic.message()).isEqualTo("非法字符：@"));
        assertThat(state.diagnostics()).hasSize(1);

        LexStep recovered = state.next();

        assertThat(recovered.tokenOptional()).hasValueSatisfying(token ->
                assertThat(token.kind()).isEqualTo(TokenKind.RETURN));
    }

    @Test
    void rejectsAdvancingAfterCompletion() {
        LexerState state = new LexerState(new SourceFile("empty.mc", ""));

        assertThat(state.next().tokenOptional()).hasValueSatisfying(token ->
                assertThat(token.kind()).isEqualTo(TokenKind.EOF));
        assertThat(state.canNext()).isFalse();
        assertThatThrownBy(state::next)
                .isInstanceOf(IllegalStateException.class);
    }
}
