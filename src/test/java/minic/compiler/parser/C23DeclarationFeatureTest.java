package minic.compiler.parser;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.parser.node.Statement.VarDeclStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.compiler.type.TypeLayout;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class C23DeclarationFeatureTest {
    @Test
    void resolvesTypedefNamesAcrossNestedScopesAndComplexDeclarators() {
        Frontend frontend = analyze("""
                typedef unsigned long DWORD;
                typedef unsigned long *IntPointer;
                struct Pair { int left; int right; };
                typedef struct Pair Pair;

                int main(void) {
                    DWORD outer = 1UL;
                    IntPointer pointer = &outer;
                    Pair pair;
                    {
                        typedef unsigned long long DWORD;
                        DWORD nested = 2ULL;
                        if (sizeof(DWORD) != 8) return 1;
                    }
                    if (sizeof(DWORD) != 4) return 2;
                    pair.left = *pointer;
                    return pair.left == 1 ? 0 : 3;
                }
                """);

        assertTrue(frontend.parserStage().errors().isEmpty(), () -> frontend.parserStage().errors().toString());
        assertTrue(frontend.semanticStage().errors().isEmpty(), () -> frontend.semanticStage().errors().toString());
        List<minic.compiler.parser.node.Statement> statements = frontend.parser().program()
                .functions().getLast().bodyOptional().orElseThrow().statements();
        VarDeclStmt outer = (VarDeclStmt) statements.getFirst();
        BlockStmt nested = (BlockStmt) statements.get(3);
        VarDeclStmt nestedValue = (VarDeclStmt) nested.statements().get(1);
        assertEquals(4, TypeLayout.sizeOf(outer.type()));
        assertEquals(8, TypeLayout.sizeOf(nestedValue.type()));
    }

    @Test
    void rejectsConstWritesAndRestrictOnNonPointerObjects() {
        Frontend frontend = analyze("""
                int main(void) {
                    const int fixed = 1;
                    const int *read_only = &fixed;
                    int mutable = 2;
                    int * const fixed_pointer = &mutable;
                    restrict int invalid = 0;
                    fixed = 3;
                    *read_only = 4;
                    fixed_pointer = &invalid;
                    return mutable;
                }
                """);

        assertTrue(frontend.parserStage().errors().isEmpty(), () -> frontend.parserStage().errors().toString());
        String diagnostics = frontend.semanticStage().errors().toString();
        assertTrue(diagnostics.contains("const"), diagnostics);
        assertTrue(diagnostics.contains("restrict"), diagnostics);
        assertEquals(4, frontend.semanticStage().errors().stream()
                .filter(diagnostic -> diagnostic.message().contains("const")
                        || diagnostic.message().contains("restrict"))
                .count(), diagnostics);
    }

    @Test
    void keepsQualifiersOnTheExactPointerLayer() {
        Frontend frontend = analyze("""
                int main(void) {
                    const int * volatile * restrict layered;
                    return 0;
                }
                """);

        assertTrue(frontend.parserStage().errors().isEmpty(), () -> frontend.parserStage().errors().toString());
        assertTrue(frontend.semanticStage().errors().isEmpty(), () -> frontend.semanticStage().errors().toString());
        VarDeclStmt declaration = (VarDeclStmt) frontend.parser().program().functions().getFirst()
                .bodyOptional().orElseThrow().statements().getFirst();
        MiniType outerPointer = declaration.type();
        MiniType innerPointer = outerPointer.pointee();
        MiniType valueType = innerPointer.pointee();
        assertTrue(outerPointer.isRestrictQualified());
        assertTrue(innerPointer.isVolatileQualified());
        assertTrue(valueType.isConstQualified());
    }

    @Test
    void carriesVolatileAccessesIntoIrInsteadOfErasingTheQualifier() {
        Frontend frontend = analyze("""
                int main(void) {
                    volatile int watched = 1;
                    watched = watched + 1;
                    return watched - 2;
                }
                """);
        assertTrue(frontend.semanticStage().errors().isEmpty(), () -> frontend.semanticStage().errors().toString());

        IrResult ir = new IrLowerer(frontend.parser().program(), frontend.semantic()).lower();
        List<IrInstruction> instructions = ir.findFunction("main").orElseThrow().blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .toList();
        assertTrue(instructions.stream().anyMatch(instruction -> isVolatileLoad(instruction)
                && volatileAccess(instruction)));
        assertTrue(instructions.stream().anyMatch(instruction -> isVolatileStore(instruction)
                && volatileAccess(instruction)));
    }

    @Test
    void computesAlignofAndAppliesAlignasToStructAndLocalLayout() {
        Frontend frontend = analyze("""
                struct Aligned {
                    char first;
                    alignas(16) int value;
                    char last;
                };

                int main(void) {
                    _Alignas(long long) int local = 0;
                    if (_Alignof(int) != 4) return 1;
                    if (alignof(struct Aligned) != 16) return 2;
                    if (sizeof(struct Aligned) != 32) return 3;
                    return local;
                }
                """);

        assertTrue(frontend.parserStage().errors().isEmpty(), () -> frontend.parserStage().errors().toString());
        assertTrue(frontend.semanticStage().errors().isEmpty(), () -> frontend.semanticStage().errors().toString());
        var layout = frontend.semantic().structLayout("Aligned").orElseThrow();
        assertEquals(16, layout.alignment());
        assertEquals(32, layout.size());
        assertEquals(16, layout.field("value").orElseThrow().offset());

        IrResult ir = new IrLowerer(frontend.parser().program(), frontend.semantic()).lower();
        IrDeclareLocalInstruction local = ir.findFunction("main").orElseThrow().blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(IrDeclareLocalInstruction.class::isInstance)
                .map(IrDeclareLocalInstruction.class::cast)
                .filter(instruction -> ir.displayName(instruction.local().sourceName()).equals("local"))
                .findFirst().orElseThrow();
        assertEquals(8, local.local().alignmentBytes());
    }

    @Test
    void diagnosesInvalidAlignmentWithoutSilentlyRoundingIt() {
        Frontend frontend = analyze("""
                int main(void) {
                    alignas(3) int invalid = 0;
                    alignas(2) int weaker = 0;
                    return invalid + weaker;
                }
                """);

        assertTrue(frontend.parserStage().errors().isEmpty(), () -> frontend.parserStage().errors().toString());
        String diagnostics = frontend.semanticStage().errors().toString();
        assertTrue(diagnostics.contains("2 的幂"), diagnostics);
        assertTrue(diagnostics.contains("弱于自然对齐"), diagnostics);
    }

    @Test
    void retainsNoreturnMetadataAndDiagnosesReturningDefinitions() {
        Frontend frontend = analyze("""
                _Noreturn void spin(void) { while (1) { } }
                noreturn int wrong(void) { return 1; }
                int main(void) { return 0; }
                """);

        assertTrue(frontend.parserStage().errors().isEmpty(), () -> frontend.parserStage().errors().toString());
        assertTrue(functionNoreturn(frontend.parser().program().functions().get(0)));
        assertTrue(functionNoreturn(frontend.parser().program().functions().get(1)));
        String diagnostics = frontend.semanticStage().errors().toString();
        assertTrue(diagnostics.contains("noreturn"), diagnostics);
        assertFalse(frontend.semanticStage().errors().stream()
                .anyMatch(diagnostic -> diagnostic.message().contains("spin")), diagnostics);
    }

    private Frontend analyze(String source) {
        Lexer lexer = new Lexer(new SourceFile("c23-declarations.mc", source));
        var lexerResult = lexer.lex();
        assertTrue(lexer.errors().isEmpty(), () -> lexer.errors().toString());
        var parserResultStage = new Parser(lexerResult.tokens());
        ParserResult parserResult = parserResultStage.parse();
        assertTrue(parserResultStage.errors().isEmpty(), () -> parserResultStage.errors().toString());
        SemanticAnalyzer semanticStage = new SemanticAnalyzer(parserResult.program());
        semanticStage.analyze();
        return new Frontend(parserResultStage, semanticStage);
    }

    private boolean isVolatileLoad(IrInstruction instruction) {
        return instruction instanceof IrLoadLocalInstruction || instruction instanceof IrLoadPointerInstruction;
    }

    private boolean isVolatileStore(IrInstruction instruction) {
        return instruction instanceof IrStoreLocalInstruction || instruction instanceof IrStorePointerInstruction;
    }

    private boolean volatileAccess(IrInstruction instruction) {
        return booleanAccessor(instruction, "volatileAccess");
    }

    private boolean functionNoreturn(Object function) {
        return booleanAccessor(function, "noReturn");
    }

    private boolean booleanAccessor(Object target, String method) {
        try {
            return (boolean) target.getClass().getMethod(method).invoke(target);
        } catch (NoSuchMethodException exception) {
            return false;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new AssertionError(exception);
        }
    }

    private record Frontend(Parser parserStage, SemanticAnalyzer semanticStage) {
        ParserResult parser() {
            return parserStage.result();
        }

        SemanticResult semantic() {
            return semanticStage.semanticResult();
        }
    }
}
