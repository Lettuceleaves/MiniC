package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBinaryOperator;
import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrJumpInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrStorePointerInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrFunctionAddress;
import minic.compiler.ir.value.IrParameterRef;
import minic.compiler.ir.value.IrStringLiteral;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.compiler.type.MiniType;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IrLowererTest {
    @Test
    void skipsFunctionDeclarationsWithoutBodies() {
        Program program = parse("""
                int add(int a, int b);
                int main() { return 0; }
                int add(int a, int b) { return a + b; }
                """);

        IrModule module = new IrLowerer().lower(program);

        assertThat(module.functions()).extracting(IrFunction::name).containsExactly("main", "add");
    }

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
    void lowersStringLiteralArgumentsToReadOnlyData() {
        Program program = parse("""
                extern int puts(int text);

                int main() {
                    return puts("hello");
                }
                """);

        IrModule module = new IrLowerer().lower(program);

        assertThat(module.externalFunctionNames()).containsExactly("puts");
        assertThat(module.stringData()).singleElement().satisfies(stringData -> {
            assertThat(stringData.label()).isEqualTo("__minic$str$0");
            assertThat(stringData.value()).isEqualTo("hello");
        });
        IrFunction main = module.findFunction("main").orElseThrow();
        IrCallInstruction call = (IrCallInstruction) main.blocks().getFirst().instructions().getFirst();
        assertThat(call.arguments()).containsExactly(new IrStringLiteral("__minic$str$0"));
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
    void lowersComparisonOperators() {
        Program program = parse("int main() { return 1 < 2 == 3 != 4 <= 5 > 6 >= 7; }");

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).singleElement().satisfies(block -> {
            assertThat(block.instructions())
                    .filteredOn(IrBinaryInstruction.class::isInstance)
                    .map(IrBinaryInstruction.class::cast)
                    .extracting(IrBinaryInstruction::operator)
                    .containsExactly(
                            IrBinaryOperator.LESS_THAN,
                            IrBinaryOperator.EQUAL,
                            IrBinaryOperator.LESS_EQUAL,
                            IrBinaryOperator.GREATER_THAN,
                            IrBinaryOperator.GREATER_EQUAL,
                            IrBinaryOperator.NOT_EQUAL
                    );
        });
    }

    @Test
    void lowersIfElseToBasicBlocks() {
        Program program = parse("""
                int main() {
                    if (1) {
                        return 2;
                    } else {
                        return 3;
                    }
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .contains("entry", "then_0", "else_1", "merge_2");
        IrBranchInstruction branch = firstBranch(main, "entry");
        assertThat(branch.thenLabel()).isEqualTo("then_0");
        assertThat(branch.elseLabel()).isEqualTo("else_1");
    }

    @Test
    void lowersIfWithoutElseToMergeBlock() {
        Program program = parse("""
                int main() {
                    int x = 1;
                    if (1) x = 2;
                    return x;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .contains("entry", "then_0", "merge_1");
        IrBranchInstruction branch = lastBranch(main, "entry");
        assertThat(branch.thenLabel()).isEqualTo("then_0");
        assertThat(branch.elseLabel()).isEqualTo("merge_1");
    }

    @Test
    void lowersElseIfChainAsNestedIfBlocks() {
        Program program = parse("""
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

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .contains("entry", "then_0", "else_1", "then_3", "else_4", "merge_5", "merge_2");
        IrBranchInstruction outerBranch = firstBranch(main, "entry");
        IrBranchInstruction elseIfBranch = firstBranch(main, "else_1");
        assertThat(outerBranch.thenLabel()).isEqualTo("then_0");
        assertThat(outerBranch.elseLabel()).isEqualTo("else_1");
        assertThat(elseIfBranch.thenLabel()).isEqualTo("then_3");
        assertThat(elseIfBranch.elseLabel()).isEqualTo("else_4");
    }

    @Test
    void lowersWhileToConditionBodyAndExitBlocks() {
        Program program = parse("""
                int main() {
                    int x = 0;
                    while (x < 3) {
                        x = x + 1;
                    }
                    return x;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .contains("entry", "while_condition_0", "while_body_1", "while_exit_2");
        IrBranchInstruction branch = lastBranch(main, "while_condition_0");
        assertThat(branch.thenLabel()).isEqualTo("while_body_1");
        assertThat(branch.elseLabel()).isEqualTo("while_exit_2");
    }

    @Test
    void lowersForToConditionBodyStepAndExitBlocks() {
        Program program = parse("""
                int main() {
                    int x = 0;
                    for (int i = 0; i < 3; i = i + 1) {
                        x = x + i;
                    }
                    return x;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .contains("entry", "for_condition_0", "for_body_1", "for_step_2", "for_exit_3");
        IrBranchInstruction branch = lastBranch(main, "for_condition_0");
        assertThat(branch.thenLabel()).isEqualTo("for_body_1");
        assertThat(branch.elseLabel()).isEqualTo("for_exit_3");
    }

    @Test
    void lowersForWithOmittedClauses() {
        Program program = parse("""
                int main() {
                    for (;;) {
                        return 7;
                    }
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .contains("entry", "for_condition_0", "for_body_1", "for_step_2", "for_exit_3");
        IrJumpInstruction jump = (IrJumpInstruction) block(main, "for_condition_0").instructions().getLast();
        assertThat(jump.targetLabel()).isEqualTo("for_body_1");
    }

    @Test
    void lowersBreakAndContinueInWhileLoop() {
        Program program = parse("""
                int main() {
                    int x = 0;
                    while (x < 10) {
                        x = x + 1;
                        if (x == 3) continue;
                        if (x == 5) break;
                    }
                    return x;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .contains("while_condition_0", "while_exit_2");
        assertThat(main.blocks()).flatExtracting(IrBlock::instructions)
                .filteredOn(IrJumpInstruction.class::isInstance)
                .map(IrJumpInstruction.class::cast)
                .extracting(IrJumpInstruction::targetLabel)
                .contains("while_condition_0", "while_exit_2");
    }

    @Test
    void lowersContinueInForLoopToStepBlock() {
        Program program = parse("""
                int main() {
                    int sum = 0;
                    for (int i = 0; i < 5; i = i + 1) {
                        if (i == 2) continue;
                        sum = sum + i;
                    }
                    return sum;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .contains("for_step_2");
        assertThat(main.blocks()).flatExtracting(IrBlock::instructions)
                .filteredOn(IrJumpInstruction.class::isInstance)
                .map(IrJumpInstruction.class::cast)
                .extracting(IrJumpInstruction::targetLabel)
                .contains("for_step_2");
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
    void lowersLocalStorageSizeFromDeclaredTypeLayout() {
        SourceFile sourceFile = new SourceFile("manual-lower.mc", "");
        SourceRange range = new SourceRange(sourceFile, 0, 0);
        Program program = new Program(java.util.List.of(new FunctionDecl(
                "main",
                java.util.List.of(),
                new BlockStmt(java.util.List.of(
                        new VarDeclStmt("value", MiniType.LONG, null, range),
                        new ReturnStmt(new minic.compiler.ast.expr.IntegerLiteralExpr(0, "0", range), range)
                ), range),
                false,
                range
        )), range);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        IrDeclareLocalInstruction declare = (IrDeclareLocalInstruction) main.blocks().getFirst().instructions().getFirst();
        assertThat(declare.local().sizeBytes()).isEqualTo(8);
    }

    @Test
    void lowersScalarWidthsAndNullConstants() {
        Program program = parse("""
                long idLong(long value) { return value; }
                int main() {
                    bool flag = true;
                    char tag = 'a';
                    long count = 1L + 2L;
                    int *missing = NULL;
                    return idLong(count) == 3L;
                }
                """);
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();

        IrModule module = new IrLowerer().lower(program, semanticResult);

        IrFunction idLong = module.findFunction("idLong").orElseThrow();
        assertThat(idLong.parameters()).singleElement().satisfies(parameter ->
                assertThat(parameter.type()).isEqualTo(IrType.LONG));

        IrFunction main = module.findFunction("main").orElseThrow();
        assertThat(main.blocks().getFirst().instructions())
                .filteredOn(IrStoreLocalInstruction.class::isInstance)
                .map(IrStoreLocalInstruction.class::cast)
                .satisfiesExactly(
                        store -> {
                            assertThat(store.local().type()).isEqualTo(IrType.BOOL);
                            assertThat(store.value()).isEqualTo(new IrConstant(1, IrType.BOOL));
                        },
                        store -> {
                            assertThat(store.local().type()).isEqualTo(IrType.CHAR);
                            assertThat(store.value()).isEqualTo(new IrConstant('a', IrType.CHAR));
                        },
                        store -> {
                            assertThat(store.local().type()).isEqualTo(IrType.LONG);
                            assertThat(store.value().type()).isEqualTo(IrType.LONG);
                        },
                        store -> {
                            assertThat(store.local().type()).isEqualTo(IrType.POINTER);
                            assertThat(store.value()).isEqualTo(new IrConstant(0, IrType.POINTER));
                        }
                );
        assertThat(main.blocks().getFirst().instructions())
                .filteredOn(IrBinaryInstruction.class::isInstance)
                .map(IrBinaryInstruction.class::cast)
                .extracting(binary -> binary.result().type())
                .contains(IrType.LONG, IrType.INT);
        assertThat(main.blocks().getFirst().instructions())
                .filteredOn(IrCallInstruction.class::isInstance)
                .map(IrCallInstruction.class::cast)
                .singleElement()
                .satisfies(call -> assertThat(call.result().type()).isEqualTo(IrType.LONG));
    }

    @Test
    void lowersAddressOfDereferenceAndPointerStore() {
        Program program = parse("""
                int main() {
                    int x = 1;
                    int *p = &x;
                    *p = 2;
                    return x;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).singleElement().satisfies(block -> {
            assertThat(block.instructions())
                    .filteredOn(IrAddressOfLocalInstruction.class::isInstance)
                    .map(IrAddressOfLocalInstruction.class::cast)
                    .singleElement()
                    .satisfies(addressOf -> {
                        assertThat(addressOf.local().sourceName()).isEqualTo("x");
                        assertThat(addressOf.result().type()).isEqualTo(IrType.POINTER);
                    });
            assertThat(block.instructions())
                    .filteredOn(IrStorePointerInstruction.class::isInstance)
                    .hasSize(1);
        });
    }

    @Test
    void lowersDereferenceRead() {
        Program program = parse("""
                int main() {
                    int x = 1;
                    int *p = &x;
                    return *p;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).singleElement().satisfies(block ->
                assertThat(block.instructions())
                        .filteredOn(IrLoadPointerInstruction.class::isInstance)
                        .hasSize(1));
    }

    @Test
    void lowersArrayIndexReadAndWriteToElementAddresses() {
        Program program = parse("""
                int main() {
                    int values[3];
                    values[0] = 7;
                    return values[0];
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).singleElement().satisfies(block -> {
            IrDeclareLocalInstruction declare = (IrDeclareLocalInstruction) block.instructions().getFirst();
            assertThat(declare.local().type()).isEqualTo(IrType.INT_ARRAY);
            assertThat(declare.local().elementCount()).isEqualTo(3);
            assertThat(block.instructions())
                    .filteredOn(IrElementAddressInstruction.class::isInstance)
                    .hasSize(2);
            assertThat(block.instructions())
                    .filteredOn(IrStorePointerInstruction.class::isInstance)
                    .hasSize(1);
            assertThat(block.instructions())
                    .filteredOn(IrLoadPointerInstruction.class::isInstance)
                    .hasSize(1);
        });
    }

    @Test
    void lowersArrayArgumentToPointerForFunctionCall() {
        Program program = parse("""
                int writeFirst(int *values) {
                    values[0] = 7;
                    return values[0];
                }

                int main() {
                    int values[2];
                    return writeFirst(values);
                }
                """);

        IrModule module = new IrLowerer().lower(program);

        IrFunction writeFirst = module.findFunction("writeFirst").orElseThrow();
        assertThat(writeFirst.parameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.name()).isEqualTo("values");
            assertThat(parameter.type()).isEqualTo(IrType.POINTER);
        });

        IrFunction main = module.findFunction("main").orElseThrow();
        assertThat(main.blocks()).singleElement().satisfies(block -> {
            IrDeclareLocalInstruction declare = (IrDeclareLocalInstruction) block.instructions().getFirst();
            IrAddressOfLocalInstruction addressOf = (IrAddressOfLocalInstruction) block.instructions().get(1);
            IrCallInstruction call = (IrCallInstruction) block.instructions().get(2);

            assertThat(declare.local().sourceName()).isEqualTo("values");
            assertThat(declare.local().type()).isEqualTo(IrType.INT_ARRAY);
            assertThat(addressOf.local()).isEqualTo(declare.local());
            assertThat(call.arguments()).containsExactly(addressOf.result());
        });
    }

    @Test
    void lowersFunctionPointerCallToIndirectCall() {
        Program program = parse("""
                int add(int left, int right) { return left + right; }

                int main() {
                    int (*operation)(int, int) = add;
                    return operation(5, 7);
                }
                """);
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();

        IrFunction main = new IrLowerer().lower(program, semanticResult).findFunction("main").orElseThrow();

        assertThat(main.blocks()).singleElement().satisfies(block -> {
            IrStoreLocalInstruction initialize = (IrStoreLocalInstruction) block.instructions().get(1);
            assertThat(initialize.value()).isEqualTo(new IrFunctionAddress("add"));

            IrIndirectCallInstruction call = (IrIndirectCallInstruction) block.instructions().get(4);
            assertThat(call.arguments()).containsExactly(new IrConstant(5), new IrConstant(7));
        });
    }

    @Test
    void lowersFunctionPointerParameterCallToIndirectCall() {
        Program program = parse("""
                int add(int left, int right) { return left + right; }
                int apply(int (*operation)(int, int), int left, int right) {
                    return operation(left, right);
                }

                int main() {
                    return apply(add, 5, 7);
                }
                """);
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();

        IrModule module = new IrLowerer().lower(program, semanticResult);

        IrFunction apply = module.findFunction("apply").orElseThrow();
        assertThat(apply.parameters()).extracting(IrParameter::type)
                .containsExactly(IrType.POINTER, IrType.INT, IrType.INT);
        assertThat(apply.blocks().getFirst().instructions())
                .filteredOn(IrIndirectCallInstruction.class::isInstance)
                .map(IrIndirectCallInstruction.class::cast)
                .singleElement()
                .satisfies(call -> assertThat(call.calleeAddress()).isEqualTo(new IrParameterRef("operation", IrType.POINTER)));

        IrFunction main = module.findFunction("main").orElseThrow();
        IrCallInstruction call = (IrCallInstruction) main.blocks().getFirst().instructions().getFirst();
        assertThat(call.arguments()).containsExactly(new IrFunctionAddress("add"), new IrConstant(5), new IrConstant(7));
    }

    @Test
    void lowersStructFieldReadAndWriteToFieldAddresses() {
        Program program = parse("""
                struct Point {
                    int x;
                    int y;
                };

                int main() {
                    struct Point point;
                    point.y = 9;
                    return point.y;
                }
                """);
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();

        IrFunction main = new IrLowerer().lower(program, semanticResult).findFunction("main").orElseThrow();

        assertThat(main.blocks()).singleElement().satisfies(block -> {
            IrDeclareLocalInstruction declare = (IrDeclareLocalInstruction) block.instructions().getFirst();
            assertThat(declare.local().type()).isEqualTo(IrType.STRUCT);
            assertThat(declare.local().sizeBytes()).isEqualTo(8);
            assertThat(block.instructions())
                    .filteredOn(IrFieldAddressInstruction.class::isInstance)
                    .map(IrFieldAddressInstruction.class::cast)
                    .extracting(IrFieldAddressInstruction::offset)
                    .containsExactly(4, 4);
            assertThat(block.instructions())
                    .filteredOn(IrStorePointerInstruction.class::isInstance)
                    .hasSize(1);
            assertThat(block.instructions())
                    .filteredOn(IrLoadPointerInstruction.class::isInstance)
                    .hasSize(1);
        });
    }

    @Test
    void lowersStructPointerFieldReadAndWriteToFieldAddresses() {
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
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();

        IrFunction write = new IrLowerer().lower(program, semanticResult).findFunction("write").orElseThrow();

        assertThat(write.parameters()).singleElement().satisfies(parameter ->
                assertThat(parameter.type()).isEqualTo(IrType.POINTER));
        assertThat(write.blocks()).singleElement().satisfies(block -> {
            assertThat(block.instructions())
                    .filteredOn(IrAddressOfLocalInstruction.class::isInstance)
                    .isEmpty();
            assertThat(block.instructions())
                    .filteredOn(IrFieldAddressInstruction.class::isInstance)
                    .map(IrFieldAddressInstruction.class::cast)
                    .extracting(IrFieldAddressInstruction::offset)
                    .containsExactly(4, 4);
            assertThat(block.instructions())
                    .filteredOn(IrStorePointerInstruction.class::isInstance)
                    .hasSize(1);
            assertThat(block.instructions())
                    .filteredOn(IrLoadPointerInstruction.class::isInstance)
                    .hasSize(1);
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

    private IrBranchInstruction firstBranch(IrFunction function, String label) {
        return (IrBranchInstruction) block(function, label).instructions().getFirst();
    }

    private IrBranchInstruction lastBranch(IrFunction function, String label) {
        return (IrBranchInstruction) block(function, label).instructions().getLast();
    }

    private IrBlock block(IrFunction function, String label) {
        return function.blocks().stream()
                .filter(block -> block.label().equals(label))
                .findFirst()
                .orElseThrow();
    }
}
