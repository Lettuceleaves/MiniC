package minic.compiler.semantic;

import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticAnalyzerTest {
    @Test
    void reportsDuplicateFunctions() {
        SemanticResult result = analyze("int main() {} int main() {}");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("重复函数定义：main/0");
    }

    @Test
    void acceptsFunctionDeclarationBeforeDefinition() {
        SemanticResult result = analyze("""
                int add(int a, int b);
                int main() { return add(1, 2); }
                int add(int a, int b) { return a + b; }
                """);

        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void reportsDuplicateFunctionDefinitionsAfterDeclaration() {
        SemanticResult result = analyze("""
                int helper();
                int helper() { return 1; }
                int helper() { return 2; }
                int main() { return helper(); }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("重复函数定义：helper/0");
    }

    @Test
    void reportsDeclaredFunctionCallWithoutDefinition() {
        SemanticResult result = analyze("""
                int helper();
                int main() { return helper(); }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未定义函数调用：helper");
    }

    @Test
    void reportsFunctionDeclarationSignatureMismatch() {
        SemanticResult result = analyze("""
                int helper(int value);
                int helper() { return 1; }
                int main() { return 0; }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("函数声明签名不一致：helper");
    }

    @Test
    void reportsMainDeclarationWithoutDefinition() {
        SemanticResult result = analyze("int main();");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("缺少 main 函数定义");
    }

    @Test
    void reportsInvalidFunctionNames() {
        SemanticResult result = analyze("int _helper() { return 1; } int main() { return 0; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("非法函数名：_helper");
    }

    @Test
    void reportsInvalidMainSignature() {
        SemanticResult result = analyze("int main(int argc) { return argc; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("非法 main 函数签名：main 必须无参数");
    }

    @Test
    void acceptsUserFunctionsWithStackPassedParameters() {
        SemanticResult result = analyze("""
                int sum6(int a, int b, int c, int d, int e, int f) { return a + b + c + d + e + f; }
                int main() { return sum6(1, 2, 3, 4, 5, 6); }
                """);

        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void acceptsValidFunctionNamesAndSignatures() {
        SemanticResult result = analyze("""
                int helper_1(int a, int b, int c, int d) { return a; }
                int main() { return helper_1(1, 2, 3, 4); }
                """);

        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void reportsDuplicateLocalVariables() {
        SemanticResult result = analyze("int main() { int x; int x; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("重复局部变量定义：x");
    }

    @Test
    void reportsUnresolvedVariables() {
        SemanticResult result = analyze("int main() { return x; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未解析变量：x");
    }

    @Test
    void reportsUnresolvedFunctionCalls() {
        SemanticResult result = analyze("int main() { missing(); }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未解析函数调用：missing");
    }

    @Test
    void reportsMissingMainFunction() {
        SemanticResult result = analyze("int helper() { return 1; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("缺少 main 函数");
    }

    @Test
    void reportsFunctionArgumentCountMismatch() {
        SemanticResult result = analyze("""
                int add(int a, int b) { return a; }
                int main() { return add(1); }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("函数调用实参数量不匹配：add");
    }

    @Test
    void reportsEmptyReturnInIntFunction() {
        SemanticResult result = analyze("int main() { return; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("int 函数中 return 必须包含表达式");
    }

    @Test
    void resolvesFunctionsParametersAndLocals() {
        SemanticResult result = analyze("""
                int id(int x) { return x; }
                int main() { int y = id(1); y = y + 1; }
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.globalScope().resolve("id")).isPresent();
        assertThat(result.globalScope().resolve("main")).isPresent();
    }

    @Test
    void allowsNestedBlockToShadowOuterVariable() {
        SemanticResult result = analyze("int main() { int x; { int x; } return 1; }");

        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void analyzesIfBranchesWithChildScopes() {
        SemanticResult result = analyze("""
                int main() {
                    if (1) int x = 1; else int x = 2;
                    return x;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未解析变量：x");
    }

    @Test
    void analyzesElseIfChainConditionsAndBranchScopes() {
        SemanticResult result = analyze("""
                int main() {
                    if (missing) {
                        return 1;
                    } else if (1) {
                        int y = 2;
                    } else {
                        int y = 3;
                    }
                    return y;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未解析变量：missing", "未解析变量：y");
    }

    private SemanticResult analyze(String source) {
        SourceFile sourceFile = new SourceFile("semantic.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        return new SemanticAnalyzer().analyze(parseResult.program());
    }
}
