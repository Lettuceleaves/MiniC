package minic.compiler;

import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStoreLocalInstruction;
import minic.compiler.lexer.Lexer;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.parser.node.Statement.VarDeclStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 前端各阶段的结构性断言：词法、语法、语义诊断与 IR 形态。 */
final class FrontendTest {
    @Test
    void lexesCommentsNumericFormsAndReportsBadInput() {
        Lexer lexer = new Lexer(new SourceFile("tokens.mc", """
                unsigned long long value; /* line one
                line two */ 0 077 0xff 0b1010 1'000 42ULL .5 1e-3 0x1.fp+3
                """));
        List<Token> tokens = lexer.lex().tokens();
        assertTrue(lexer.errors().isEmpty(), lexer.errors()::toString);
        assertEquals(List.of(TokenType.UNSIGNED, TokenType.LONG, TokenType.LONG, TokenType.IDENTIFIER,
                TokenType.SEMICOLON), tokens.subList(0, 5).stream().map(Token::type).toList());
        assertEquals(9, tokens.size() - 6);
        assertTrue(tokens.stream().anyMatch(token -> token.lexeme().equals("0x1.fp+3")
                && token.type() == TokenType.DOUBLE_LITERAL));

        Lexer invalid = new Lexer(new SourceFile("bad.mc", "0b102 0x1g /* never closed"));
        invalid.lex();
        assertFalse(invalid.errors().isEmpty());
    }

    @Test
    void keepsDeclaratorTypesQualifiersAndScopes() {
        Program program = analyze("""
                typedef unsigned long DWORD;
                extern void *allocate(long size);
                extern int print(const char *format, ...);
                _Noreturn void spin(void) { while (1) { } }
                int main(void) {
                    DWORD outer = 1UL;
                    const int * volatile * restrict layered;
                    {
                        typedef unsigned long long DWORD;
                        DWORD nested = 2ULL;
                    }
                    return 0;
                }
                """);

        FunctionDecl allocate = program.functions().get(0);
        assertEquals(MiniType.VOID.pointerTo(), allocate.returnType());
        assertTrue(program.functions().get(1).variadic());
        assertTrue(program.functions().get(2).noReturn());
        var body = program.functions().get(3).bodyOptional().orElseThrow().statements();
        assertEquals(MiniType.UNSIGNED_LONG, ((VarDeclStmt) body.get(0)).type().unqualified());
        MiniType layered = ((VarDeclStmt) body.get(1)).type();
        assertTrue(layered.isRestrictQualified());
        assertTrue(layered.pointee().isVolatileQualified());
        assertTrue(layered.pointee().pointee().isConstQualified());
    }

    @Test
    void reportsSemanticViolations() {
        String messages = diagnostics("""
                noreturn int wrong(void) { return 1; }
                int main(void) {
                    const int fixed = 1;
                    restrict int invalid = 0;
                    alignas(3) int misaligned = 0;
                    fixed = 3;
                    return invalid;
                }
                """);
        for (String expected : List.of("const", "restrict", "2 的幂", "noreturn")) {
            assertTrue(messages.contains(expected), messages);
        }
    }

    @Test
    void lowersLayoutVolatileAccessAndReachableFunctionsIntoIr() {
        IrResult ir = new CompilerApi(new SourceFile("ir.mc", """
                extern int abandoned(int value);
                extern int external_operation(int value);
                struct Aligned { char first; alignas(16) int value; char last; };
                int unused(int value) { return abandoned(value); }
                int countdown(int value) { return value == 0 ? 0 : countdown(value - 1); }
                int main(void) {
                    _Alignas(long long) int local = 0;
                    volatile int watched = 1;
                    watched = watched + 1;
                    int (*operation)(int) = external_operation;
                    return countdown(sizeof(struct Aligned) == 32 ? 0 : 1) + local + operation(0);
                }
                """)).runToIr();

        assertEquals(Set.of("main", "countdown"),
                ir.functions().stream().map(function -> function.name()).collect(Collectors.toSet()));
        assertEquals(Set.of("external_operation"), ir.externalFunctionNames());
        List<IrInstruction> instructions = ir.findFunction("main").orElseThrow().blocks().stream()
                .flatMap(block -> block.instructions().stream()).toList();
        assertTrue(instructions.stream().anyMatch(instruction ->
                instruction instanceof IrLoadLocalInstruction load && load.volatileAccess()));
        assertTrue(instructions.stream().anyMatch(instruction ->
                instruction instanceof IrStoreLocalInstruction store && store.volatileAccess()));
        IrDeclareLocalInstruction local = instructions.stream()
                .filter(IrDeclareLocalInstruction.class::isInstance).map(IrDeclareLocalInstruction.class::cast)
                .filter(declaration -> ir.displayName(declaration.local().sourceName()).equals("local"))
                .findFirst().orElseThrow();
        assertEquals(8, local.local().alignmentBytes());
    }

    private static Program analyze(String source) {
        Lexer lexer = new Lexer(new SourceFile("frontend.mc", source));
        Parser parser = new Parser(lexer.lex().tokens());
        parser.parse();
        assertTrue(parser.errors().isEmpty(), parser.errors()::toString);
        SemanticAnalyzer semantic = new SemanticAnalyzer(parser.result().program());
        semantic.analyze();
        assertTrue(semantic.errors().isEmpty(), semantic.errors()::toString);
        return parser.result().program();
    }

    private static String diagnostics(String source) {
        Lexer lexer = new Lexer(new SourceFile("diagnostics.mc", source));
        Parser parser = new Parser(lexer.lex().tokens());
        parser.parse();
        assertTrue(parser.errors().isEmpty(), parser.errors()::toString);
        SemanticAnalyzer semantic = new SemanticAnalyzer(parser.result().program());
        semantic.analyze();
        return semantic.errors().toString();
    }
}
