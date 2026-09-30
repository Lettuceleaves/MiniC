package minic.compiler.parser;

import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SystemDeclarationParserTest {
    @Test
    @Tag("stdlib-contract")
    void parsesVoidPointersVoidReturnsAndVariadicFunctions() {
        String source = """
                extern void *malloc(long size);
                extern void free(void *pointer);
                extern int printf(char *format, ...);
                void noop(void) { return; }
                int main() { return 0; }
                """;

        var resultStage = new Parser(new Lexer(new SourceFile("declarations.mc", source)).lex().tokens());
        ParserResult result = resultStage.parse();

        assertTrue(resultStage.errors().isEmpty(), () -> resultStage.errors().toString());
        var malloc = result.program().functions().get(0);
        assertEquals(MiniType.VOID.pointerTo(), malloc.returnType());
        var free = result.program().functions().get(1);
        assertEquals(MiniType.VOID, free.returnType());
        assertEquals(MiniType.VOID.pointerTo(), free.parameters().getFirst().type());
        assertTrue(result.program().functions().get(2).variadic());
        assertTrue(result.program().functions().get(3).parameters().isEmpty());
        assertEquals(MiniType.VOID, result.program().functions().get(3).returnType());
    }
}
