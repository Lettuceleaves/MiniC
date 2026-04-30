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
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsX64AssemblyEmitterTest {
    @Test
    void emitsMinimalWindowsX64AssemblyForConstantReturnMain() {
        IrModule module = lower("int main() { return 1; }");

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.targetPlatform()).isEqualTo(TargetPlatform.WINDOWS_X86_64);
        assertThat(assemblySource.entrySymbol()).isEqualTo("minic$entry");
        assertThat(assemblySource.text()).contains(
                "; target: windows-x86_64",
                "PUBLIC minic$entry",
                "EXTERN ExitProcess:PROC",
                ".code",
                "minic$entry PROC",
                "    call main",
                "    mov ecx, eax",
                "    call ExitProcess",
                "main PROC",
                "    push rbp",
                "    mov rbp, rsp",
                "    mov eax, 1",
                "    jmp main$epilogue",
                "main$epilogue:",
                "main ENDP",
                "END"
        );
    }

    @Test
    void emitsWindowsX64AssemblyForLocalsAssignmentCallsAndRuntimeChecks() {
        IrModule module = lower("""
                int add(int a, int b) {
                    return a + b;
                }

                int main() {
                    int x = 4;
                    x = add(x, 2);
                    return x / 2;
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "minic$add PROC",
                "    mov DWORD PTR [rbp-36], ecx",
                "    mov DWORD PTR [rbp-40], edx",
                "    add eax, ecx",
                "main PROC",
                "    mov DWORD PTR [rbp-40], 1",
                "    cmp DWORD PTR [rbp-40], 0",
                "    je main$trap_uninitialized",
                "    mov ecx, DWORD PTR [rbp-44]",
                "    mov edx, 2",
                "    call minic$add",
                "    cmp eax, 0",
                "    je main$trap_divide_by_zero",
                "    cdq",
                "    idiv ecx",
                "main$trap_uninitialized:",
                "    mov eax, 101",
                "    jmp main$epilogue",
                "main$trap_divide_by_zero:",
                "    mov eax, 102",
                "    jmp main$epilogue"
        );
    }

    @Test
    void emitsWindowsX64AssemblyForStackPassedArguments() {
        IrModule module = lower("""
                int pick6(int a, int b, int c, int d, int e, int f) {
                    return e + f;
                }

                int main() {
                    return pick6(1, 2, 3, 4, 5, 6);
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "minic$pick6 PROC",
                "    mov eax, DWORD PTR [rbp+48]",
                "    mov DWORD PTR [rbp-52], eax",
                "    mov eax, DWORD PTR [rbp+56]",
                "    mov DWORD PTR [rbp-56], eax",
                "main PROC",
                "    mov eax, 5",
                "    mov DWORD PTR [rsp+32], eax",
                "    mov eax, 6",
                "    mov DWORD PTR [rsp+40], eax",
                "    mov ecx, 1",
                "    mov edx, 2",
                "    mov r8d, 3",
                "    mov r9d, 4",
                "    call minic$pick6"
        );
    }

    @Test
    void emitsRecursiveCallTarget() {
        IrModule module = lower("""
                int recur() {
                    return recur();
                }

                int main() {
                    return 0;
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "minic$recur PROC",
                "    call minic$recur"
        );
    }

    @Test
    void emitsReadOnlyStringDataAndPassesAddressToCall() {
        IrModule module = lower("""
                extern int puts(int text);

                int main() {
                    return puts("hello");
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                ".const",
                "EXTERN puts:PROC",
                "__minic$str$0 BYTE 104, 101, 108, 108, 111, 0",
                ".code",
                "    lea rcx, __minic$str$0",
                "    call puts"
        );
    }

    @Test
    void emitsPrintfCallWithStringFormatAndIntegerArgument() {
        IrModule module = lower("""
                extern int printf(int format, int value);

                int main() {
                    printf("value=%d\\n", 42);
                    return 42;
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "EXTERN printf:PROC",
                "__minic$str$0 BYTE 118, 97, 108, 117, 101, 61, 37, 100, 10, 0",
                "    lea rcx, __minic$str$0",
                "    mov edx, 42",
                "    call printf"
        );
        assertThat(assemblySource.text()).doesNotContain("call minic$printf");
    }


    @Test
    void emitsWindowsX64AssemblyForComparisons() {
        IrModule module = lower("""
                int main() {
                    return (1 < 2) + (2 <= 2) + (3 > 4) + (5 >= 5) + (6 == 6) + (7 != 8);
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "    cmp eax, ecx",
                "    setl al",
                "    setle al",
                "    setg al",
                "    setge al",
                "    sete al",
                "    setne al",
                "    movzx eax, al"
        );
    }

    @Test
    void emitsWindowsX64AssemblyForIfElseBlocks() {
        IrModule module = lower("""
                int main() {
                    if (1 < 2) {
                        return 7;
                    } else {
                        return 9;
                    }
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "    cmp eax, 0",
                "    jne main$then_0",
                "    jmp main$else_1",
                "main$then_0:",
                "    mov eax, 7",
                "    jmp main$epilogue",
                "main$else_1:",
                "    mov eax, 9",
                "main$merge_2:"
        );
    }

    @Test
    void emitsWindowsX64AssemblyForElseIfChain() {
        IrModule module = lower("""
                int main() {
                    if (0) {
                        return 1;
                    } else if (1) {
                        return 2;
                    } else {
                        return 3;
                    }
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "    jne main$then_0",
                "    jmp main$else_1",
                "main$else_1:",
                "    jne main$then_3",
                "    jmp main$else_4",
                "main$then_3:",
                "    mov eax, 2",
                "main$else_4:",
                "    mov eax, 3",
                "main$merge_5:",
                "main$merge_2:"
        );
    }

    @Test
    void emitsWindowsX64AssemblyForWhileLoop() {
        IrModule module = lower("""
                int main() {
                    int x = 0;
                    while (x < 3) {
                        x = x + 1;
                    }
                    return x;
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "    jmp main$while_condition_0",
                "main$while_condition_0:",
                "    jne main$while_body_1",
                "    jmp main$while_exit_2",
                "main$while_body_1:",
                "    jmp main$while_condition_0",
                "main$while_exit_2:"
        );
    }

    @Test
    void emitsWindowsX64AssemblyForForLoop() {
        IrModule module = lower("""
                int main() {
                    int x = 0;
                    for (int i = 0; i < 3; i = i + 1) {
                        x = x + i;
                    }
                    return x;
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "    jmp main$for_condition_0",
                "main$for_condition_0:",
                "    jne main$for_body_1",
                "    jmp main$for_exit_3",
                "main$for_body_1:",
                "    jmp main$for_step_2",
                "main$for_step_2:",
                "    jmp main$for_condition_0",
                "main$for_exit_3:"
        );
    }

    @Test
    void emitsWindowsX64AssemblyForPointerAddressDereferenceAndStore() {
        IrModule module = lower("""
                int main() {
                    int x = 1;
                    int *p = &x;
                    *p = 3;
                    return *p;
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "    lea rax, [rbp-",
                "    mov QWORD PTR [rbp-",
                "    mov eax, DWORD PTR [rax]",
                "    mov DWORD PTR [rax], ecx"
        );
    }

    @Test
    void emitsWindowsX64AssemblyForArrayIndexReadAndWrite() {
        IrModule module = lower("""
                int main() {
                    int values[3];
                    values[1] = 7;
                    return values[1];
                }
                """);

        AssemblySource assemblySource = new WindowsX64AssemblyEmitter().emit(module);

        assertThat(assemblySource.text()).contains(
                "    lea rax, [rbp-",
                "    movsxd rcx, ecx",
                "    lea rax, [rax+rcx*4]",
                "    mov DWORD PTR [rax], ecx",
                "    mov eax, DWORD PTR [rax]"
        );
    }

    private IrModule lower(String source) {
        SourceFile sourceFile = new SourceFile("codegen.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        Program program = parseResult.program();
        return new IrLowerer().lower(program);
    }
}
