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
        assertThat(assemblySource.entrySymbol()).isEqualTo("main");
        assertThat(assemblySource.text()).contains(
                "; target: windows-x86_64",
                "PUBLIC main",
                ".code",
                "main PROC",
                "    mov eax, 1",
                "    ret",
                "main ENDP",
                "END"
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
