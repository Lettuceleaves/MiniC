package minic.compiler.codegen.windows;

import minic.compiler.ast.decl.Program;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.target.TargetPlatform;
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

import static org.assertj.core.api.Assertions.assertThat;

class WindowsX64AssemblyEmitterTest {
    @Test
    void emitsWindowsX64EnvelopeEntryPointAndExternalData() {
        AssemblySource assemblySource = emit("""
                extern int printf(char *format, int value);

                int main() {
                    printf("value=%d\\n", 42);
                    return 42;
                }
                """);

        assertThat(assemblySource.targetPlatform()).isEqualTo(TargetPlatform.WINDOWS_X86_64);
        assertThat(assemblySource.entrySymbol()).isEqualTo("minic$entry");
        assertThat(assemblySource.text()).contains(
                "; target: windows-x86_64",
                "PUBLIC minic$entry",
                "EXTERN ExitProcess:PROC",
                "EXTERN printf:PROC",
                ".const",
                "__minic$str$0 BYTE 118, 97, 108, 117, 101, 61, 37, 100, 10, 0",
                ".code",
                "minic$entry PROC",
                "    call main",
                "    mov ecx, eax",
                "    call ExitProcess",
                "main PROC",
                "    lea rcx, __minic$str$0",
                "    mov edx, 42",
                "    call printf",
                "main$epilogue:",
                "main ENDP",
                "END"
        );
        assertThat(assemblySource.text()).doesNotContain("call minic$printf");
    }

    @Test
    void emitsCallingConventionStackArgumentsAndRuntimeTraps() {
        AssemblySource assemblySource = emit("""
                int sum6(int a, int b, int c, int d, int e, int f) {
                    return a + b + c + d + e + f;
                }

                int main() {
                    int x = 4;
                    x = sum6(x, 2, 3, 4, 5, 6);
                    return x / 2;
                }
                """);

        assertThat(assemblySource.text()).contains(
                "minic$sum6 PROC",
                "    mov DWORD PTR [rbp-36], ecx",
                "    mov DWORD PTR [rbp-40], edx",
                "    mov eax, DWORD PTR [rbp+48]",
                "    mov DWORD PTR [rbp-52], eax",
                "    mov eax, DWORD PTR [rbp+56]",
                "    mov DWORD PTR [rbp-56], eax",
                "main PROC",
                "    mov eax, 5",
                "    mov DWORD PTR [rsp+32], eax",
                "    mov eax, 6",
                "    mov DWORD PTR [rsp+40], eax",
                "    mov edx, 2",
                "    mov r8d, 3",
                "    mov r9d, 4",
                "    call minic$sum6",
                "    je main$trap_uninitialized",
                "    cmp eax, 0",
                "    je main$trap_divide_by_zero",
                "    cdq",
                "    idiv ecx",
                "main$trap_uninitialized:",
                "    mov eax, 101",
                "main$trap_divide_by_zero:",
                "    mov eax, 102"
        );
    }

    @Test
    void emitsControlFlowLabelsBranchesAndIntegerComparisons() {
        AssemblySource assemblySource = emit("""
                int main() {
                    int total = 0;
                    for (int i = 0; i < 3; i = i + 1) {
                        if (i == 1) {
                            continue;
                        } else if (i > 1) {
                            total = total + i;
                        } else {
                            total = total + 1;
                        }
                    }
                    while (total <= 5) {
                        total = total + 1;
                        if (total != 4) {
                            break;
                        }
                    }
                    return (total >= 4) + (total < 9);
                }
                """);

        assertThat(assemblySource.text()).contains(
                "    setl al",
                "    setle al",
                "    setg al",
                "    setge al",
                "    sete al",
                "    setne al",
                "    movzx eax, al"
        );
        assertThat(assemblySource.text()).containsPattern("main\\$for_condition_\\d+:");
        assertThat(assemblySource.text()).containsPattern("main\\$for_body_\\d+:");
        assertThat(assemblySource.text()).containsPattern("main\\$for_step_\\d+:");
        assertThat(assemblySource.text()).containsPattern("main\\$for_exit_\\d+:");
        assertThat(assemblySource.text()).containsPattern("main\\$while_condition_\\d+:");
        assertThat(assemblySource.text()).containsPattern("main\\$while_body_\\d+:");
        assertThat(assemblySource.text()).containsPattern("main\\$while_exit_\\d+:");
        assertThat(assemblySource.text()).containsPattern("main\\$then_\\d+:");
        assertThat(assemblySource.text()).containsPattern("main\\$else_\\d+:");
        assertThat(assemblySource.text()).containsPattern("main\\$merge_\\d+:");
    }

    @Test
    void emitsPointersArraysStructFieldsAndFunctionPointerCalls() {
        AssemblySource assemblySource = emit("""
                struct Point {
                    int x;
                    int y;
                };

                int add(int left, int right) {
                    return left + right;
                }

                int apply(int (*operation)(int, int), int left, int right) {
                    return operation(left, right);
                }

                int writeFirst(int *values) {
                    values[0] = 7;
                    return values[0];
                }

                int writePoint(struct Point *point) {
                    point->y = 9;
                    return point->y;
                }

                int main() {
                    int x = 1;
                    int *p = &x;
                    *p = 3;
                    int values[2];
                    values[1] = writeFirst(values);
                    struct Point point;
                    point.y = *p;
                    int (*operation)(int, int) = add;
                    return apply(operation, point.y, values[1]) + writePoint(&point);
                }
                """);

        assertThat(assemblySource.text()).contains(
                "minic$apply PROC",
                "    call rax",
                "minic$writeFirst PROC",
                "minic$writePoint PROC",
                "    lea rax, [rax+4]",
                "main PROC",
                "    lea rax, [rbp-",
                "    mov QWORD PTR [rbp-",
                "    movsxd rcx, ecx",
                "    lea rax, [rax+rcx*4]",
                "    mov DWORD PTR [rax], ecx",
                "    mov eax, DWORD PTR [rax]",
                "    lea rax, minic$add",
                "    call minic$writeFirst",
                "    call minic$apply",
                "    call minic$writePoint"
        );
        assertThat(assemblySource.text()).containsPattern("    cmp DWORD PTR \\[rbp-\\d+], 0");
    }

    @Test
    void emitsScalarWidthsNullAndLongArithmetic() {
        AssemblySource assemblySource = emit("""
                long bump(long value) {
                    return value + 2L;
                }

                int main() {
                    bool flag = true;
                    char tag = 'a';
                    long total = bump(40L);
                    int *missing = NULL;
                    return flag + tag + (total == 42L);
                }
                """);

        assertThat(assemblySource.text()).contains(
                "minic$bump PROC",
                "    mov QWORD PTR [rbp-40], rcx",
                "    add rax, rcx",
                "main PROC",
                "    mov BYTE PTR [rbp-",
                "    mov rcx, 40",
                "    call minic$bump",
                "    mov rax, 0",
                "    mov QWORD PTR [rbp-",
                "    movzx ecx, BYTE PTR [rbp-",
                "    movsx ecx, BYTE PTR [rbp-",
                "    cmp rax, rcx"
        );
    }

    @Test
    void emitsFloatingScalarsCastsComparisonsAndArrayElements() {
        AssemblySource assemblySource = emit("""
                double mix(float ratio, double score) {
                    double widened = ratio + score;
                    double values[2];
                    values[1] = widened / 2.0;
                    return values[1] > 1.0;
                }

                int main() {
                    double result = mix(1.5f, 2.0);
                    return result == 1.75;
                }
                """);

        assertThat(assemblySource.text()).contains(
                "minic$mix PROC",
                "    movss DWORD PTR [rbp-",
                "    movsd QWORD PTR [rbp-",
                "    cvtss2sd xmm0, xmm0",
                "    addsd xmm0, xmm1",
                "    divsd xmm0, xmm1",
                "    lea rax, [rax+rcx*8]",
                "    ucomisd xmm0, xmm1",
                "    seta al",
                "main PROC",
                "    movd xmm0, eax",
                "    movq xmm1, rax",
                "    call minic$mix",
                "    movsd QWORD PTR [rbp-",
                "    sete al"
        );
    }

    @Test
    void emitsPhaseDExpressionInstructions() {
        AssemblySource assemblySource = emit("""
                int main() {
                    int value = 7;
                    value %= 3;
                    value = (value & 3) | 4 ^ 1;
                    value = value << 1;
                    value = value >> 1;
                    value = !value || ~value;
                    value = value ? sizeof value : sizeof(int);
                    return value;
                }
                """);

        assertThat(assemblySource.text()).contains(
                "    idiv ecx",
                "    mov eax, edx",
                "    and eax, ecx",
                "    or eax, ecx",
                "    xor eax, ecx",
                "    shl eax, cl",
                "    sar eax, cl",
                "    sete al",
                "    not eax",
                "    je minic$select_false_",
                "    mov rcx, 4"
        );
    }

    private AssemblySource emit(String source) {
        return new WindowsX64AssemblyEmitter().emit(lowerWithSemantic(source));
    }

    private IrModule lowerWithSemantic(String source) {
        SourceFile sourceFile = new SourceFile("codegen.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        Program program = parseResult.program();
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();
        return new IrLowerer().lower(program, semanticResult);
    }
}
