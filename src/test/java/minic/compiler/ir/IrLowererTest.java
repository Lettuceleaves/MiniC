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
            assertThat(block.instructions()).hasSize(4);
            IrBinaryInstruction subtract = (IrBinaryInstruction) block.instructions().get(0);
            IrCheckNonZeroInstruction checkNonZero = (IrCheckNonZeroInstruction) block.instructions().get(1);
            IrBinaryInstruction divide = (IrBinaryInstruction) block.instructions().get(2);

            assertThat(subtract.operator()).isEqualTo(IrBinaryOperator.SUBTRACT);
            assertThat(subtract.left()).isEqualTo(new IrConstant(8));
            assertThat(subtract.right()).isEqualTo(new IrConstant(2));
            assertThat(checkNonZero.value()).isEqualTo(new IrConstant(3));
            assertThat(divide.operator()).isEqualTo(IrBinaryOperator.DIVIDE);
            assertThat(divide.left()).isEqualTo(subtract.result());
            assertThat(divide.right()).isEqualTo(new IrConstant(3));
        });
    }

    @Test
    void lowersLocalsAssignmentAndInitializedReadChecks() {
        Program program = parse("""
                int main() {
                    int x = 1;
                    x = x + 2;
                    return x;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).singleElement().satisfies(block -> {
            assertThat(block.instructions()).hasSize(9);
            IrDeclareLocalInstruction declare = (IrDeclareLocalInstruction) block.instructions().get(0);
            IrStoreLocalInstruction initialize = (IrStoreLocalInstruction) block.instructions().get(1);
            IrCheckInitializedInstruction assignmentCheck = (IrCheckInitializedInstruction) block.instructions().get(2);
            IrLoadLocalInstruction assignmentLoad = (IrLoadLocalInstruction) block.instructions().get(3);
            IrBinaryInstruction add = (IrBinaryInstruction) block.instructions().get(4);
            IrStoreLocalInstruction assignmentStore = (IrStoreLocalInstruction) block.instructions().get(5);
            IrCheckInitializedInstruction returnCheck = (IrCheckInitializedInstruction) block.instructions().get(6);
            IrLoadLocalInstruction returnLoad = (IrLoadLocalInstruction) block.instructions().get(7);
            IrReturnInstruction returnInstruction = (IrReturnInstruction) block.instructions().get(8);

            assertThat(declare.local().sourceName()).isEqualTo("x");
            assertThat(declare.local().name()).isEqualTo("x#0");
            assertThat(initialize.local()).isEqualTo(declare.local());
            assertThat(initialize.value()).isEqualTo(new IrConstant(1));
            assertThat(assignmentCheck.local()).isEqualTo(declare.local());
            assertThat(assignmentLoad.local()).isEqualTo(declare.local());
            assertThat(add.left()).isEqualTo(assignmentLoad.result());
            assertThat(add.right()).isEqualTo(new IrConstant(2));
            assertThat(assignmentStore.local()).isEqualTo(declare.local());
            assertThat(assignmentStore.value()).isEqualTo(add.result());
            assertThat(returnCheck.local()).isEqualTo(declare.local());
            assertThat(returnLoad.local()).isEqualTo(declare.local());
            assertThat(returnInstruction.value()).isEqualTo(returnLoad.result());
        });
    }

    @Test
    void keepsShadowedLocalsDistinct() {
        Program program = parse("""
                int main() {
                    int x = 1;
                    {
                        int x = 2;
                        x = x + 1;
                    }
                    return x;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).singleElement().satisfies(block -> {
            IrDeclareLocalInstruction outerDeclare = (IrDeclareLocalInstruction) block.instructions().get(0);
            IrDeclareLocalInstruction innerDeclare = (IrDeclareLocalInstruction) block.instructions().get(2);
            IrLoadLocalInstruction innerLoad = (IrLoadLocalInstruction) block.instructions().get(5);
            IrLoadLocalInstruction outerLoad = (IrLoadLocalInstruction) block.instructions().get(9);

            assertThat(outerDeclare.local().name()).isEqualTo("x#0");
            assertThat(innerDeclare.local().name()).isEqualTo("x#1");
            assertThat(innerLoad.local()).isEqualTo(innerDeclare.local());
            assertThat(outerLoad.local()).isEqualTo(outerDeclare.local());
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
