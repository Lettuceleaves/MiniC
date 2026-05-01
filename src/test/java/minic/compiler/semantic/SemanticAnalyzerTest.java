package minic.compiler.semantic;

import minic.compiler.ast.decl.Program;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.IndexExpr;
import minic.compiler.ast.expr.FieldAccessExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.expr.StringLiteralExpr;
import minic.compiler.ast.expr.UnaryExpr;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.type.MiniType;
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
    void acceptsExternalFunctionCallWithoutDefinition() {
        SemanticResult result = analyze("""
                extern int helper(int value);
                int main() { return helper(1); }
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.globalScope().resolve("helper")).isPresent();
    }

    @Test
    void stillChecksExternalFunctionArity() {
        SemanticResult result = analyze("""
                extern int helper(int value);
                int main() { return helper(); }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("函数调用实参数量不匹配：helper");
    }

    @Test
    void reportsExternalFunctionWithBody() {
        SemanticResult result = analyze("""
                extern int helper() { return 1; }
                int main() { return 0; }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("外部函数不能携带函数体：helper");
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
    void resolvesFunctionNameAsFunctionPointerValue() {
        Program program = parse("""
                int add(int left, int right) { return left + right; }

                int main() {
                    int (*operation)(int, int) = add;
                    operation = add;
                    return 0;
                }
                """);

        SemanticResult result = new SemanticAnalyzer().analyze(program);

        assertThat(result.diagnostics()).isEmpty();
        MiniType functionPointerType = MiniType.function(
                MiniType.INT,
                java.util.List.of(MiniType.INT, MiniType.INT)
        ).pointerTo();
        NameExpr initializer = (NameExpr) ((minic.compiler.ast.stmt.VarDeclStmt)
                program.functions().get(1).body().statements().getFirst())
                .initializerOptional().orElseThrow();
        NameExpr assignmentValue = (NameExpr) ((minic.compiler.ast.expr.AssignmentExpr)
                ((ExprStmt) program.functions().get(1).body().statements().get(1)).expression()).value();

        assertThat(result.typeOf(initializer)).contains(functionPointerType);
        assertThat(result.typeOf(assignmentValue)).contains(functionPointerType);
    }

    @Test
    void reportsFunctionPointerAssignmentSignatureMismatch() {
        SemanticResult result = analyze("""
                int unary(int value) { return value; }

                int main() {
                    int (*operation)(int, int);
                    operation = unary;
                    return 0;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("赋值类型不匹配");
    }

    @Test
    void reportsFunctionPointerInitializerSignatureMismatch() {
        SemanticResult result = analyze("""
                int unary(int value) { return value; }

                int main() {
                    int (*operation)(int, int) = unary;
                    return 0;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("变量初始化类型不匹配：operation");
    }

    @Test
    void reportsDeclaredFunctionAddressWithoutDefinition() {
        SemanticResult result = analyze("""
                int add(int left, int right);

                int main() {
                    int (*operation)(int, int) = add;
                    return 0;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未定义函数取址：add");
    }

    @Test
    void acceptsFunctionPointerCall() {
        SemanticResult result = analyze("""
                int add(int left, int right) { return left + right; }

                int main() {
                    int (*operation)(int, int) = add;
                    return operation(5, 7);
                }
                """);

        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void acceptsFunctionPointerParameterCall() {
        SemanticResult result = analyze("""
                int add(int left, int right) { return left + right; }
                int apply(int (*operation)(int, int), int left, int right) {
                    return operation(left, right);
                }

                int main() {
                    return apply(add, 5, 7);
                }
                """);

        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void reportsFunctionPointerCallArgumentMismatch() {
        SemanticResult result = analyze("""
                int add(int left, int right) { return left + right; }

                int main() {
                    int (*operation)(int, int) = add;
                    return operation(5);
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("函数指针调用实参数量不匹配");
    }

    @Test
    void reportsCallingNonFunctionPointer() {
        SemanticResult result = analyze("""
                int main() {
                    int value = 1;
                    return (value)(2);
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("函数指针调用目标必须是函数指针");
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
    void recordsExpressionTypesAndSymbolTypes() {
        Program program = parse("""
                extern int printf(int format, int value);

                int id(int x) {
                    return x;
                }

                int main() {
                    int y = id(1);
                    printf("value=%d\\n", y);
                    return y + 1;
                }
                """);

        SemanticResult result = new SemanticAnalyzer().analyze(program);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.globalScope().resolve("printf")).hasValueSatisfying(symbol ->
                assertThat(symbol.type()).isEqualTo(MiniType.function(
                        MiniType.INT,
                        java.util.List.of(MiniType.INT, MiniType.INT)
                )));
        ReturnStmt idReturn = (ReturnStmt) program.functions().get(1).body().statements().getFirst();
        NameExpr returnedName = (NameExpr) idReturn.expressionOptional().orElseThrow();
        ExprStmt printfStatement = (ExprStmt) program.functions().get(2).body().statements().get(1);
        CallExpr printfCall = (CallExpr) printfStatement.expression();
        StringLiteralExpr format = (StringLiteralExpr) printfCall.arguments().getFirst();
        NameExpr valueArgument = (NameExpr) printfCall.arguments().get(1);
        ReturnStmt mainReturn = (ReturnStmt) program.functions().get(2).body().statements().get(2);
        BinaryExpr sum = (BinaryExpr) mainReturn.expressionOptional().orElseThrow();
        IntegerLiteralExpr one = (IntegerLiteralExpr) sum.right();

        assertThat(result.typeOf(returnedName)).contains(MiniType.INT);
        assertThat(result.typeOf(printfCall)).contains(MiniType.INT);
        assertThat(result.typeOf(format)).contains(MiniType.INT.pointerTo());
        assertThat(result.typeOf(valueArgument)).contains(MiniType.INT);
        assertThat(result.typeOf(sum)).contains(MiniType.INT);
        assertThat(result.typeOf(one)).contains(MiniType.INT);
    }

    @Test
    void recordsPointerExpressionTypes() {
        Program program = parse("""
                int main() {
                    int x = 1;
                    int *p = &x;
                    *p = 2;
                    return *p;
                }
                """);

        SemanticResult result = new SemanticAnalyzer().analyze(program);

        assertThat(result.diagnostics()).isEmpty();
        var statements = program.functions().getFirst().body().statements();
        UnaryExpr addressOf = (UnaryExpr) ((minic.compiler.ast.stmt.VarDeclStmt) statements.get(1))
                .initializerOptional()
                .orElseThrow();
        UnaryExpr dereferenceStore = (UnaryExpr) ((minic.compiler.ast.expr.AssignmentExpr)
                ((ExprStmt) statements.get(2)).expression()).target();
        UnaryExpr dereferenceReturn = (UnaryExpr) ((ReturnStmt) statements.get(3)).expressionOptional().orElseThrow();

        assertThat(result.typeOf(addressOf)).contains(MiniType.INT.pointerTo());
        assertThat(result.typeOf(dereferenceStore)).contains(MiniType.INT);
        assertThat(result.typeOf(dereferenceReturn)).contains(MiniType.INT);
    }

    @Test
    void recordsArrayIndexTypes() {
        Program program = parse("""
                int main() {
                    int values[3];
                    values[0] = 7;
                    return values[0];
                }
                """);

        SemanticResult result = new SemanticAnalyzer().analyze(program);

        assertThat(result.diagnostics()).isEmpty();
        var statements = program.functions().getFirst().body().statements();
        IndexExpr assignmentTarget = (IndexExpr) ((minic.compiler.ast.expr.AssignmentExpr)
                ((ExprStmt) statements.get(1)).expression()).target();
        IndexExpr returned = (IndexExpr) ((ReturnStmt) statements.get(2)).expressionOptional().orElseThrow();

        assertThat(result.typeOf(assignmentTarget.target())).contains(MiniType.INT.pointerTo());
        assertThat(result.typeOf(assignmentTarget)).contains(MiniType.INT);
        assertThat(result.typeOf(returned)).contains(MiniType.INT);
    }

    @Test
    void acceptsArrayArgumentForPointerParameter() {
        SemanticResult result = analyze("""
                int writeFirst(int *values) {
                    values[0] = 7;
                    return values[0];
                }

                int main() {
                    int values[2];
                    return writeFirst(values);
                }
                """);

        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void reportsScalarArgumentForPointerParameter() {
        SemanticResult result = analyze("""
                int read(int *value) {
                    return value[0];
                }

                int main() {
                    int value = 1;
                    return read(value);
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("函数调用实参类型不匹配：read");
    }

    @Test
    void reportsIndexingNonArrayOrPointer() {
        SemanticResult result = analyze("int main() { int x = 1; return x[0]; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("下标访问目标必须是数组或指针");
    }

    @Test
    void reportsArrayInitializer() {
        SemanticResult result = analyze("int main() { int values[3] = 1; return 0; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("数组声明暂不支持初始化表达式");
    }

    @Test
    void definesStructTypeSymbolsAndAcceptsStructVariables() {
        SemanticResult result = analyze("""
                struct Point {
                    int x;
                    int y;
                };

                int main() {
                    struct Point point;
                    return 0;
                }
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.globalScope().resolve("Point")).hasValueSatisfying(symbol -> {
            assertThat(symbol.kind()).isEqualTo(SymbolKind.STRUCT);
            assertThat(symbol.type()).isEqualTo(MiniType.struct("Point"));
        });
    }

    @Test
    void reportsDuplicateStructFields() {
        SemanticResult result = analyze("""
                struct Point {
                    int x;
                    int x;
                };

                int main() { return 0; }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("重复结构体字段：x");
    }

    @Test
    void reportsUndeclaredStructTypeUse() {
        SemanticResult result = analyze("""
                int main() {
                    struct Missing value;
                    return 0;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未声明结构体类型：Missing");
    }

    @Test
    void computesStructLayoutForIntPointerAndArrayFields() {
        SemanticResult result = analyze("""
                struct Node {
                    int value;
                    int *next;
                    int values[3];
                };

                int main() { return 0; }
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.structLayout("Node")).hasValueSatisfying(layout -> {
            assertThat(layout.size()).isEqualTo(32);
            assertThat(layout.alignment()).isEqualTo(8);
            assertThat(layout.fields()).extracting(StructFieldLayout::name)
                    .containsExactly("value", "next", "values");
            assertThat(layout.field("value")).hasValueSatisfying(field -> {
                assertThat(field.offset()).isEqualTo(0);
                assertThat(field.size()).isEqualTo(4);
                assertThat(field.alignment()).isEqualTo(4);
            });
            assertThat(layout.field("next")).hasValueSatisfying(field -> {
                assertThat(field.offset()).isEqualTo(8);
                assertThat(field.size()).isEqualTo(8);
                assertThat(field.alignment()).isEqualTo(8);
            });
            assertThat(layout.field("values")).hasValueSatisfying(field -> {
                assertThat(field.offset()).isEqualTo(16);
                assertThat(field.size()).isEqualTo(12);
                assertThat(field.alignment()).isEqualTo(4);
            });
        });
    }

    @Test
    void computesNestedStructLayout() {
        SemanticResult result = analyze("""
                struct Point {
                    int x;
                    int y;
                };

                struct Line {
                    struct Point start;
                    struct Point end;
                    int *tag;
                };

                int main() { return 0; }
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.structLayout("Point")).hasValueSatisfying(layout -> {
            assertThat(layout.size()).isEqualTo(8);
            assertThat(layout.alignment()).isEqualTo(4);
        });
        assertThat(result.structLayout("Line")).hasValueSatisfying(layout -> {
            assertThat(layout.size()).isEqualTo(24);
            assertThat(layout.alignment()).isEqualTo(8);
            assertThat(layout.field("start")).hasValueSatisfying(field -> assertThat(field.offset()).isEqualTo(0));
            assertThat(layout.field("end")).hasValueSatisfying(field -> assertThat(field.offset()).isEqualTo(8));
            assertThat(layout.field("tag")).hasValueSatisfying(field -> assertThat(field.offset()).isEqualTo(16));
        });
    }

    @Test
    void reportsDirectRecursiveStructField() {
        SemanticResult result = analyze("""
                struct Node {
                    struct Node next;
                };

                int main() { return 0; }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("结构体字段不能直接包含自身：Node");
    }

    @Test
    void reportsInvalidDereference() {
        SemanticResult result = analyze("int main() { int x = 1; return *x; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("解引用操作数必须是指针");
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

    @Test
    void analyzesWhileConditionAndBodyScope() {
        SemanticResult result = analyze("""
                int main() {
                    while (missing) int y = 1;
                    return y;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未解析变量：missing", "未解析变量：y");
    }

    @Test
    void analyzesForClausesAndLoopScope() {
        SemanticResult result = analyze("""
                int main() {
                    for (int i = 0; i < missing; i = i + 1) {
                        int y = i;
                    }
                    return i + y;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未解析变量：missing", "未解析变量：i", "未解析变量：y");
    }

    @Test
    void reportsBreakAndContinueOutsideLoops() {
        SemanticResult result = analyze("""
                int main() {
                    break;
                    continue;
                    return 0;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("break 只能在循环内使用", "continue 只能在循环内使用");
    }

    @Test
    void acceptsBreakAndContinueInsideNestedBranchesInLoops() {
        SemanticResult result = analyze("""
                int main() {
                    while (1) {
                        if (1) break; else continue;
                    }
                    return 0;
                }
                """);

        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void resolvesStructFieldAccessTypes() {
        Program program = parse("""
                struct Point {
                    int x;
                    int y;
                };

                int main() {
                    struct Point point;
                    point.x = 7;
                    return point.x;
                }
                """);

        SemanticResult result = new SemanticAnalyzer().analyze(program);

        assertThat(result.diagnostics()).isEmpty();
        FieldAccessExpr assignmentTarget = (FieldAccessExpr) ((minic.compiler.ast.expr.AssignmentExpr)
                ((minic.compiler.ast.stmt.ExprStmt) program.functions().getFirst().body().statements().get(1))
                        .expression()).target();
        FieldAccessExpr returned = (FieldAccessExpr) ((minic.compiler.ast.stmt.ReturnStmt)
                program.functions().getFirst().body().statements().get(2))
                .expressionOptional().orElseThrow();

        assertThat(result.typeOf(assignmentTarget.target())).contains(MiniType.struct("Point"));
        assertThat(result.typeOf(assignmentTarget)).contains(MiniType.INT);
        assertThat(result.typeOf(returned)).contains(MiniType.INT);
    }

    @Test
    void reportsUnknownStructFieldAccess() {
        SemanticResult result = analyze("""
                struct Point {
                    int x;
                };

                int main() {
                    struct Point point;
                    return point.y;
                }
                """);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未知结构体字段：y");
    }

    @Test
    void resolvesStructPointerFieldAccessTypes() {
        Program program = parse("""
                struct Point {
                    int x;
                    int y;
                };

                int write(struct Point *point) {
                    point->y = 9;
                    return point->y;
                }

                int main() {
                    return 0;
                }
                """);

        SemanticResult result = new SemanticAnalyzer().analyze(program);

        assertThat(result.diagnostics()).isEmpty();
        FieldAccessExpr assignmentTarget = (FieldAccessExpr) ((minic.compiler.ast.expr.AssignmentExpr)
                ((minic.compiler.ast.stmt.ExprStmt) program.functions().getFirst().body().statements().getFirst())
                        .expression()).target();
        FieldAccessExpr returned = (FieldAccessExpr) ((minic.compiler.ast.stmt.ReturnStmt)
                program.functions().getFirst().body().statements().get(1))
                .expressionOptional().orElseThrow();

        assertThat(result.typeOf(assignmentTarget.target())).contains(MiniType.struct("Point").pointerTo());
        assertThat(result.typeOf(assignmentTarget)).contains(MiniType.INT);
        assertThat(result.typeOf(returned)).contains(MiniType.INT);
    }

    @Test
    void reportsStructPointerFieldAccessOnNonStructPointer() {
        SemanticResult result = analyze("int main() { int *p; return p->x; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("指针字段访问目标必须是结构体指针");
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
