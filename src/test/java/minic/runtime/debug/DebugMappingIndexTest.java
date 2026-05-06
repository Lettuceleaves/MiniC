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
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DebugMappingIndexTest {
    @Test
    void mapsSourceRangeToAstIrAndAsmItems() {
        SourceFile sourceFile = new SourceFile("mapping.mc", """
                int main() {
                    int value = 1;
                    return value + 1;
                }
                """);
        Lowered lowered = lower(sourceFile);
        DebugMappingIndex index = DebugMappingIndex.build(lowered.program(), lowered.module());
        int returnStart = sourceFile.content().indexOf("return");
        SourceRange returnRange = new SourceRange(sourceFile, returnStart, returnStart + "return value + 1".length());

        DebugMappingQueryResult result = index.findBySourceRange(returnRange);

        assertThat(result.astItems()).extracting(DebugMappingItem::id).anyMatch(id -> id.startsWith("ast-root"));
        assertThat(result.irItems()).extracting(DebugMappingItem::kind).contains("IR_INSTRUCTION");
        assertThat(result.asmItems()).extracting(DebugMappingItem::kind).contains("ASM_LINE");
        assertThat(result.asmItems()).allSatisfy(item ->
                assertThat(item.detail()).contains("映射展示"));
    }

    @Test
    void mapsSourceLineWithStableDebugIds() {
        SourceFile sourceFile = new SourceFile("mapping-line.mc", """
                int main() {
                    int value = 1;
                    value = value + 2;
                    return value;
                }
                """);
        Lowered lowered = lower(sourceFile);
        DebugMappingIndex index = DebugMappingIndex.build(lowered.program(), lowered.module());

        DebugMappingQueryResult result = index.findBySourceLine(3);

        assertThat(index.astItems()).extracting(DebugMappingItem::id).contains("ast-root");
        assertThat(result.irItems()).extracting(DebugMappingItem::id)
                .anyMatch(id -> id.startsWith("ir-main-"));
        assertThat(result.asmItems()).extracting(DebugMappingItem::id)
                .anyMatch(id -> id.startsWith("asm-"));
    }

    private Lowered lower(SourceFile sourceFile) {
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        Program program = parseResult.program();
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();
        return new Lowered(program, new IrLowerer().lower(program, semanticResult));
    }

    private record Lowered(Program program, IrModule module) {
    }
}
