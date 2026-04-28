package minic.compiler.ir;

import minic.compiler.ast.Program;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IrLowererTest {
    @Test
    void lowersReturnLiteralArithmeticAndCallInEvaluationOrder() {
        Program program = parse("""
                int add(int a, int b) {
                    return a + b;
                }

                int main() {
                    return add(1, 2) * 3;
                }
                """);

        IrModule module = new IrLowerer().lower(program);

        IrFunction add = module.findFunction("add").orElseThrow();
        assertThat(add.parameters()).extracting(IrParameter::name).containsExactly("a", "b");
        assertThat(add.blocks()).singleElement().satisfies(block -> {
            assertThat(block.label()).isEqualTo("entry");
            assertThat(block.instructions()).hasSize(2);

            IrBinaryInstruction sum = (IrBinaryInstruction) block.instructions().get(0);
            assertThat(sum.result()).isEqualTo(new IrTemporary("%0", IrType.INT));
            assertThat(sum.operator()).isEqualTo(IrBinaryOperator.ADD);
            assertThat(sum.left()).isEqualTo(new IrParameterRef("a", IrType.INT));
            assertThat(sum.right()).isEqualTo(new IrParameterRef("b", IrType.INT));

            IrReturnInstruction returnInstruction = (IrReturnInstruction) block.instructions().get(1);
            assertThat(returnInstruction.value()).isEqualTo(sum.result());
        });

        IrFunction main = module.findFunction("main").orElseThrow();
        assertThat(main.blocks()).singleElement().satisfies(block -> {
            assertThat(block.instructions()).hasSize(3);

            IrCallInstruction call = (IrCallInstruction) block.instructions().get(0);
            assertThat(call.result()).isEqualTo(new IrTemporary("%0", IrType.INT));
            assertThat(call.calleeName()).isEqualTo("add");
            assertThat(call.arguments()).containsExactly(new IrConstant(1), new IrConstant(2));

            IrBinaryInstruction multiply = (IrBinaryInstruction) block.instructions().get(1);
            assertThat(multiply.result()).isEqualTo(new IrTemporary("%1", IrType.INT));
            assertThat(multiply.operator()).isEqualTo(IrBinaryOperator.MULTIPLY);
            assertThat(multiply.left()).isEqualTo(call.result());
            assertThat(multiply.right()).isEqualTo(new IrConstant(3));

            IrReturnInstruction returnInstruction = (IrReturnInstruction) block.instructions().get(2);
            assertThat(returnInstruction.value()).isEqualTo(multiply.result());
        });
    }

    @Test
    void lowersGroupedArithmeticOperators() {
        Program program = parse("int main() { return (8 - 2) / 3; }");

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).singleElement().satisfies(block -> {
            assertThat(block.instructions()).hasSize(3);
            IrBinaryInstruction subtract = (IrBinaryInstruction) block.instructions().get(0);
            IrBinaryInstruction divide = (IrBinaryInstruction) block.instructions().get(1);

            assertThat(subtract.operator()).isEqualTo(IrBinaryOperator.SUBTRACT);
            assertThat(subtract.left()).isEqualTo(new IrConstant(8));
            assertThat(subtract.right()).isEqualTo(new IrConstant(2));
            assertThat(divide.operator()).isEqualTo(IrBinaryOperator.DIVIDE);
            assertThat(divide.left()).isEqualTo(subtract.result());
            assertThat(divide.right()).isEqualTo(new IrConstant(3));
        });
    }

    private Program parse(String source) {
        SourceFile sourceFile = new SourceFile("lower.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        return parseResult.program();
    }
}
