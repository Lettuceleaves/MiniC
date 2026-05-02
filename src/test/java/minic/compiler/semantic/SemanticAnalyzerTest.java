package minic.compiler.semantic;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.FieldAccessExpr;
import minic.compiler.ast.expr.IndexExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.expr.NullLiteralExpr;
import minic.compiler.ast.expr.UnaryExpr;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.type.MiniType;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticAnalyzerTest {
    @Test
    void acceptsComprehensiveLegalProgramAndRecordsRepresentativeTypes() {
        Program program = parse("""
                extern int printf(int *format, int value);

                struct Node {
                    int value;
                    int *next;
                    int values[3];
                };

                int add(int left, int right) { return left + right; }

                double mix(bool flag, char tag, long count, float ratio, double score) {
                    long total = count + tag;
                    float adjusted = ratio + flag;
                    double combined = adjusted + score;
                    return combined;
                }

                int writeFirst(int *values, int (*operation)(int, int), struct Node *node) {
                    int local = operation(1, 2);
                    values[0] = local;
                    node->value = values[0];
                    return node->value;
                }

                int main() {
                    struct Node node;
                    int values[3];
                    int *pointer = NULL;
                    int (*operation)(int, int) = add;
                    pointer = values;
                    node.value = writeFirst(values, operation, &node);
                    printf("value=%d\\n", node.value);
                    return pointer[0];
                }
                """);

        SemanticResult result = new SemanticAnalyzer().analyze(program);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.globalScope().resolve("printf")).hasValueSatisfying(symbol ->
                assertThat(symbol.type()).isEqualTo(MiniType.function(
                        MiniType.INT,
                        List.of(MiniType.INT.pointerTo(), MiniType.INT)
                )));
        assertThat(result.globalScope().resolve("Node")).hasValueSatisfying(symbol -> {
            assertThat(symbol.kind()).isEqualTo(SymbolKind.STRUCT);
            assertThat(symbol.type()).isEqualTo(MiniType.struct("Node"));
        });

        FunctionDecl main = program.functions().get(4);
        var mainStatements = main.body().statements();
        NullLiteralExpr nullInitializer = (NullLiteralExpr) ((VarDeclStmt) mainStatements.get(2))
                .initializerOptional().orElseThrow();
        NameExpr functionAddress = (NameExpr) ((VarDeclStmt) mainStatements.get(3))
                .initializerOptional().orElseThrow();
        NameExpr arrayDecay = (NameExpr) ((AssignmentExpr)
                ((ExprStmt) mainStatements.get(4)).expression()).value();
        FieldAccessExpr nodeValue = (FieldAccessExpr) ((CallExpr)
                ((ExprStmt) mainStatements.get(6)).expression()).arguments().get(1);
        IndexExpr returned = (IndexExpr) ((ReturnStmt) mainStatements.get(7))
                .expressionOptional().orElseThrow();

        assertThat(result.typeOf(nullInitializer)).contains(MiniType.NULL);
        assertThat(result.typeOf(functionAddress)).contains(MiniType.function(
                MiniType.INT,
                List.of(MiniType.INT, MiniType.INT)
        ).pointerTo());
        assertThat(result.typeOf(arrayDecay)).contains(MiniType.INT.pointerTo());
        assertThat(result.typeOf(nodeValue)).contains(MiniType.INT);
        assertThat(result.typeOf(returned.target())).contains(MiniType.INT.pointerTo());
        assertThat(result.typeOf(returned)).contains(MiniType.INT);

        FunctionDecl mix = program.functions().get(2);
        var mixStatements = mix.body().statements();
        BinaryExpr longSum = (BinaryExpr) ((VarDeclStmt) mixStatements.get(0))
                .initializerOptional().orElseThrow();
        BinaryExpr floatSum = (BinaryExpr) ((VarDeclStmt) mixStatements.get(1))
                .initializerOptional().orElseThrow();
        BinaryExpr doubleSum = (BinaryExpr) ((VarDeclStmt) mixStatements.get(2))
                .initializerOptional().orElseThrow();
        assertThat(result.typeOf(longSum)).contains(MiniType.LONG);
        assertThat(result.typeOf(floatSum)).contains(MiniType.FLOAT);
        assertThat(result.typeOf(doubleSum)).contains(MiniType.DOUBLE);
    }

    @Test
    void reportsRepresentativeFunctionAndNameResolutionDiagnostics() {
        SemanticResult result = analyze("""
                extern int badExternal() { return 1; }
                int declaredOnly(int value);
                int duplicate() { return 1; }
                int duplicate() { return 2; }
                int _badName() { return 0; }
                int main(int argc) {
                    int x;
                    int x;
                    missing();
                    return declaredOnly(unknown);
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly(
                        "外部函数不能携带函数体：badExternal",
                        "重复函数定义：duplicate/0",
                        "非法函数名：_badName",
                        "非法 main 函数签名：main 必须无参数",
                        "重复局部变量定义：x",
                        "未解析函数调用：missing",
                        "未解析变量：unknown",
                        "未定义函数调用：declaredOnly"
                );
    }

    @Test
    void reportsRepresentativeTypeDiagnostics() {
        SemanticResult result = analyze("""
                int unary(int value) { return value; }
                int read(int *value) { return value[0]; }

                int *badReturn() {
                    return 1;
                }

                int main() {
                    int value = 1;
                    int *pointer = 1;
                    int scalar = NULL;
                    int (*operation)(int, int) = unary;
                    value = pointer + value;
                    return read(1);
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly(
                        "return 类型不匹配",
                        "变量初始化类型不匹配：pointer",
                        "变量初始化类型不匹配：scalar",
                        "变量初始化类型不匹配：operation",
                        "二元表达式操作数类型不匹配",
                        "函数调用实参类型不匹配：read"
                );
    }

    @Test
    void reportsRepresentativePointerArrayAndStructDiagnostics() {
        SemanticResult result = analyze("""
                struct Point {
                    int x;
                    int x;
                };

                struct Node {
                    struct Node next;
                };

                int main() {
                    struct Missing missing;
                    struct Point point;
                    int value = 1;
                    int values[3] = 1;
                    value[0] = 1;
                    point.y = 2;
                    return value->x;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly(
                        "重复结构体字段：x",
                        "结构体字段不能直接包含自身：Node",
                        "未声明结构体类型：Missing",
                        "数组声明暂不支持初始化表达式",
                        "下标访问目标必须是数组或指针",
                        "未知结构体字段：y",
                        "指针字段访问目标必须是结构体指针"
                );
    }

    @Test
    void reportsRepresentativeControlFlowScopeDiagnostics() {
        SemanticResult result = analyze("""
                int main() {
                    if (missing) {
                        int branchValue = 1;
                    } else if (1) {
                        int branchValue = 2;
                    }
                    while (missingLoop) int loopValue = 1;
                    for (int i = 0; i < missingLimit; i = i + 1) {
                        int forValue = i;
                        break;
                        continue;
                    }
                    break;
                    continue;
                    return branchValue + loopValue + i + forValue;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly(
                        "未解析变量：missing",
                        "未解析变量：missingLoop",
                        "未解析变量：missingLimit",
                        "break 只能在循环内使用",
                        "continue 只能在循环内使用",
                        "未解析变量：branchValue",
                        "未解析变量：loopValue",
                        "未解析变量：i",
                        "未解析变量：forValue"
                );
    }

    @Test
    void computesRepresentativeStructLayouts() {
        SemanticResult result = analyze("""
                struct Point {
                    int x;
                    int y;
                };

                struct Mixed {
                    bool flag;
                    char tag;
                    long count;
                    float ratio;
                    double score;
                };

                struct Line {
                    struct Point start;
                    struct Point end;
                    int *tag;
                    int values[3];
                };

                int main() { return 0; }
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.structLayout("Point")).hasValueSatisfying(layout -> {
            assertThat(layout.size()).isEqualTo(8);
            assertThat(layout.alignment()).isEqualTo(4);
        });
        assertThat(result.structLayout("Mixed")).hasValueSatisfying(layout -> {
            assertThat(layout.size()).isEqualTo(32);
            assertThat(layout.alignment()).isEqualTo(8);
            assertThat(layout.field("flag")).hasValueSatisfying(field -> assertThat(field.offset()).isEqualTo(0));
            assertThat(layout.field("count")).hasValueSatisfying(field -> assertThat(field.offset()).isEqualTo(8));
            assertThat(layout.field("score")).hasValueSatisfying(field -> assertThat(field.offset()).isEqualTo(24));
        });
        assertThat(result.structLayout("Line")).hasValueSatisfying(layout -> {
            assertThat(layout.size()).isEqualTo(40);
            assertThat(layout.alignment()).isEqualTo(8);
            assertThat(layout.fields()).extracting(StructFieldLayout::name)
                    .containsExactly("start", "end", "tag", "values");
            assertThat(layout.field("start")).hasValueSatisfying(field -> assertThat(field.offset()).isEqualTo(0));
            assertThat(layout.field("end")).hasValueSatisfying(field -> assertThat(field.offset()).isEqualTo(8));
            assertThat(layout.field("tag")).hasValueSatisfying(field -> assertThat(field.offset()).isEqualTo(16));
            assertThat(layout.field("values")).hasValueSatisfying(field -> assertThat(field.offset()).isEqualTo(24));
        });
    }

    private SemanticResult analyze(String source) {
        return new SemanticAnalyzer().analyze(parse(source));
    }

    private Program parse(String source) {
        SourceFile sourceFile = new SourceFile("semantic.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        return parseResult.program();
    }
}
