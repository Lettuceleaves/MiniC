package minic.compiler.semantic;

import minic.compiler.ast.decl.Program;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.type.MiniType;
import minic.runtime.step.CompileStage;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticStepStateTest {
    @Test
    void advancesSemanticActionsAndBuildsEquivalentResult() {
        Program program = parse("""
                struct Point { int x; };
                int add(int left, int right) { return left + right; }
                int main() { return add(1, 2); }
                """);
        SemanticStepState state = new SemanticStepState(program);

        assertThat(state.stage()).isEqualTo(CompileStage.SEMANTIC);
        assertThat(state.input().program()).isSameAs(program);

        assertThat(state.next().kind()).isEqualTo(SemanticActionKind.REGISTER_STRUCTS);
        assertThat(state.next().kind()).isEqualTo(SemanticActionKind.CHECK_TYPES);
        assertThat(state.next().kind()).isEqualTo(SemanticActionKind.COMPUTE_STRUCT_LAYOUTS);
        assertThat(state.next().kind()).isEqualTo(SemanticActionKind.REGISTER_FUNCTIONS);
        assertThat(state.next().kind()).isEqualTo(SemanticActionKind.VALIDATE_MAIN);
        assertThat(state.next()).satisfies(action -> {
            assertThat(action.kind()).isEqualTo(SemanticActionKind.ANALYZE_FUNCTION_BODY);
            assertThat(action.subject()).isEqualTo("add");
        });
        assertThat(state.next()).satisfies(action -> {
            assertThat(action.kind()).isEqualTo(SemanticActionKind.ANALYZE_STATEMENT);
            assertThat(action.subject()).contains("add", "ReturnStmt");
        });

        while (state.canNext()) {
            state.next();
        }

        SemanticResult stepped = state.toSemanticResult();
        SemanticResult direct = new SemanticAnalyzer().analyze(program);
        ReturnStmt addReturn = (ReturnStmt) program.functions().get(0).body().statements().getFirst();
        BinaryExpr sum = (BinaryExpr) addReturn.expressionOptional().orElseThrow();

        assertThat(stepped.diagnostics()).isEmpty();
        assertThat(stepped.globalScope().resolve("Point")).isPresent();
        assertThat(stepped.typeOf(sum)).contains(MiniType.INT);
        assertThat(stepped.expressionTypes()).containsAllEntriesOf(direct.expressionTypes());
        assertThat(state.result().output().semanticResult().diagnostics()).isEmpty();
        assertThat(state.work().expressionTypeCount()).isGreaterThan(0);
    }

    @Test
    void reportsDiagnosticsAsSemanticActions() {
        Program program = parse("""
                int main() {
                    return missing;
                }
                """);
        SemanticStepState state = new SemanticStepState(program);

        SemanticAction diagnosticAction = null;
        while (state.canNext()) {
            SemanticAction action = state.next();
            if (action.kind() == SemanticActionKind.REPORT_DIAGNOSTIC) {
                diagnosticAction = action;
            }
        }

        assertThat(diagnosticAction).isNotNull();
        assertThat(diagnosticAction.diagnosticOptional()).hasValueSatisfying(diagnostic ->
                assertThat(diagnostic.message()).isEqualTo("未解析变量：missing"));
        assertThat(state.diagnostics()).hasSize(1);
    }

    @Test
    void exposesFunctionBodySemanticEffectsStatementByStatement() {
        Program program = parse("""
                int main() {
                    int a = 1;
                    int b = a + 2;
                    return b;
                }
                """);
        SemanticStepState state = new SemanticStepState(program);

        advanceUntil(state, SemanticActionKind.REGISTER_FUNCTIONS);
        assertThat(state.work().globalScope().resolve("main")).isPresent();
        assertThat(state.work().expressionTypeCount()).isZero();

        SemanticAction enterFunction = state.next();

        assertThat(enterFunction.kind()).isEqualTo(SemanticActionKind.VALIDATE_MAIN);
        SemanticAction functionBody = state.next();
        assertThat(functionBody.kind()).isEqualTo(SemanticActionKind.ANALYZE_FUNCTION_BODY);
        assertThat(state.work().globalScope().children()).hasSize(1);
        assertThat(state.work().expressionTypeCount()).isZero();

        SemanticAction firstStatement = state.next();

        assertThat(firstStatement.kind()).isEqualTo(SemanticActionKind.ANALYZE_STATEMENT);
        assertThat(state.work().expressionTypeCount()).isEqualTo(1);
        assertThat(state.work().globalScope().children().getFirst().resolveLocal("a")).isPresent();

        SemanticAction initializerVisit = state.next();
        assertThat(initializerVisit.kind()).isEqualTo(SemanticActionKind.VISIT_AST_NODE);
        assertThat(initializerVisit.subject()).contains("IntegerLiteralExpr");

        SemanticAction secondStatement = state.next();

        assertThat(secondStatement.kind()).isEqualTo(SemanticActionKind.ANALYZE_STATEMENT);
        assertThat(state.work().expressionTypeCount()).isGreaterThan(1);
        assertThat(state.work().globalScope().children().getFirst().resolveLocal("b")).isPresent();
    }

    @Test
    void rejectsAdvancingAfterCompletion() {
        SemanticStepState state = new SemanticStepState(parse("int main() { return 0; }"));

        while (state.canNext()) {
            state.next();
        }

        assertThatThrownBy(state::next)
                .isInstanceOf(IllegalStateException.class);
    }

    private Program parse(String source) {
        SourceFile sourceFile = new SourceFile("semantic-step.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        return parseResult.program();
    }

    private void advanceUntil(SemanticStepState state, SemanticActionKind kind) {
        SemanticAction action;
        do {
            action = state.next();
        } while (state.canNext() && action.kind() != kind);
        assertThat(action.kind()).isEqualTo(kind);
    }
}
