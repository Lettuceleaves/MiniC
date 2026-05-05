package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBinaryOperator;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCastInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrJumpInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.IrMoveInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrStorePointerInstruction;
import minic.compiler.ir.instruction.IrUnaryInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrFloatConstant;
import minic.compiler.ir.value.IrFunctionAddress;
import minic.compiler.ir.value.IrParameterRef;
import minic.compiler.ir.value.IrStringLiteral;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IrLowererTest {
    @Test
    void lowersProgramShapeDeclarationsExternalCallsAndReadOnlyData() {
        Program program = parse("""
                extern int puts(int text);
                int declared(int value);

                int main() {
                    return puts("hello");
                }

                int declared(int value) {
                    return value;
                }
                """);

        IrModule module = new IrLowerer().lower(program);

        assertThat(module.externalFunctionNames()).containsExactly("puts");
        assertThat(module.functions()).extracting(IrFunction::name).containsExactly("main", "declared");
        assertThat(module.stringData()).singleElement().satisfies(stringData -> {
            assertThat(stringData.label()).startsWith("__minic$str$");
            assertThat(stringData.value()).isEqualTo("hello");
        });
        assertThat(instructions(module.findFunction("main").orElseThrow()))
                .filteredOn(IrCallInstruction.class::isInstance)
                .map(IrCallInstruction.class::cast)
                .singleElement()
                .satisfies(call -> {
                    assertThat(call.calleeName()).isEqualTo("puts");
                    assertThat(call.arguments()).containsExactly(new IrStringLiteral(module.stringData().getFirst().label()));
                });
    }

    @Test
    void lowersScalarArithmeticCallsRuntimeChecksAndLocalDataFlow() {
        Program program = parse("""
                int add(int left, int right) {
                    return left + right;
                }

                int main() {
                    int x = add(1, 2);
                    x = (x * 3) / 2;
                    return x >= 4;
                }
                """);

        IrModule module = new IrLowerer().lower(program);

        IrFunction add = module.findFunction("add").orElseThrow();
        assertThat(add.parameters()).extracting(IrParameter::name).containsExactly("left", "right");
        assertThat(instructions(add))
                .filteredOn(IrBinaryInstruction.class::isInstance)
                .map(IrBinaryInstruction.class::cast)
                .singleElement()
                .satisfies(binary -> {
                    assertThat(binary.operator()).isEqualTo(IrBinaryOperator.ADD);
                    assertThat(binary.left()).isEqualTo(new IrParameterRef("left", IrType.INT));
                    assertThat(binary.right()).isEqualTo(new IrParameterRef("right", IrType.INT));
                });

        List<IrInstruction> mainInstructions = instructions(module.findFunction("main").orElseThrow());
        IrDeclareLocalInstruction x = mainInstructions.stream()
                .filter(IrDeclareLocalInstruction.class::isInstance)
                .map(IrDeclareLocalInstruction.class::cast)
                .filter(declare -> declare.local().sourceName().equals("x"))
                .findFirst()
                .orElseThrow();
        assertThat(mainInstructions).filteredOn(IrCallInstruction.class::isInstance).hasSize(1);
        assertThat(mainInstructions).filteredOn(IrCheckInitializedInstruction.class::isInstance).hasSizeGreaterThanOrEqualTo(2);
        assertThat(mainInstructions).filteredOn(IrCheckNonZeroInstruction.class::isInstance).hasSize(1);
        assertThat(mainInstructions)
                .filteredOn(IrBinaryInstruction.class::isInstance)
                .map(IrBinaryInstruction.class::cast)
                .extracting(IrBinaryInstruction::operator)
                .contains(IrBinaryOperator.MULTIPLY, IrBinaryOperator.DIVIDE, IrBinaryOperator.GREATER_EQUAL);
        assertThat(mainInstructions)
                .filteredOn(IrStoreLocalInstruction.class::isInstance)
                .map(IrStoreLocalInstruction.class::cast)
                .extracting(IrStoreLocalInstruction::local)
                .contains(x.local());
        assertThat(mainInstructions)
                .filteredOn(IrLoadLocalInstruction.class::isInstance)
                .map(IrLoadLocalInstruction.class::cast)
                .extracting(IrLoadLocalInstruction::local)
                .contains(x.local());
        assertThat(mainInstructions).last().isInstanceOf(IrReturnInstruction.class);
    }

    @Test
    void lowersControlFlowToBlocksBranchesAndLoopJumps() {
        Program program = parse("""
                int main() {
                    int sum = 0;
                    if (sum == 0) {
                        sum = 1;
                    } else {
                        sum = 2;
                    }
                    while (sum < 10) {
                        sum = sum + 1;
                        if (sum == 3) continue;
                        if (sum == 5) break;
                    }
                    for (int i = 0; i < 3; i = i + 1) {
                        if (i == 1) continue;
                        sum = sum + i;
                    }
                    return sum;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .anyMatch(label -> label.startsWith("then_"))
                .anyMatch(label -> label.startsWith("else_"))
                .anyMatch(label -> label.startsWith("merge_"))
                .anyMatch(label -> label.startsWith("while_condition_"))
                .anyMatch(label -> label.startsWith("while_body_"))
                .anyMatch(label -> label.startsWith("while_exit_"))
                .anyMatch(label -> label.startsWith("for_condition_"))
                .anyMatch(label -> label.startsWith("for_body_"))
                .anyMatch(label -> label.startsWith("for_step_"))
                .anyMatch(label -> label.startsWith("for_exit_"));
        assertThat(branches(main)).allSatisfy(branch -> {
            assertThat(hasBlock(main, branch.thenLabel())).isTrue();
            assertThat(hasBlock(main, branch.elseLabel())).isTrue();
        });
        assertThat(jumpTargets(main))
                .anyMatch(target -> target.startsWith("while_condition_"))
                .anyMatch(target -> target.startsWith("while_exit_"))
                .anyMatch(target -> target.startsWith("for_step_"));
    }

    @Test
    void lowersDoWhileToBodyConditionAndExitBlocks() {
        Program program = parse("""
                int main() {
                    int value = 0;
                    do {
                        value = value + 1;
                        if (value == 2) continue;
                        if (value == 3) break;
                    } while (value < 5);
                    return value;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .anyMatch(label -> label.startsWith("do_body_"))
                .anyMatch(label -> label.startsWith("do_condition_"))
                .anyMatch(label -> label.startsWith("do_exit_"));
        assertThat(jumpTargets(main))
                .anyMatch(target -> target.startsWith("do_condition_"))
                .anyMatch(target -> target.startsWith("do_exit_"));
        assertThat(branches(main))
                .anySatisfy(branch -> {
                    assertThat(branch.thenLabel()).startsWith("do_body_");
                    assertThat(branch.elseLabel()).startsWith("do_exit_");
                });
    }

    @Test
    void lowersSwitchCaseDefaultToComparisonsAndFallthroughBlocks() {
        Program program = parse("""
                int main() {
                    int value = 1;
                    switch (value) {
                        case 1:
                            value = 2;
                        case 2:
                            value = value + 1;
                            break;
                        default:
                            value = 0;
                    }
                    return value;
                }
                """);

        IrFunction main = new IrLowerer().lower(program).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .anyMatch(label -> label.startsWith("switch_case_"))
                .anyMatch(label -> label.startsWith("switch_default_"))
                .anyMatch(label -> label.startsWith("switch_exit_"));
        assertThat(instructions(main))
                .filteredOn(IrBinaryInstruction.class::isInstance)
                .map(IrBinaryInstruction.class::cast)
                .extracting(IrBinaryInstruction::operator)
                .contains(IrBinaryOperator.EQUAL);
        assertThat(jumpTargets(main))
                .anyMatch(target -> target.startsWith("switch_case_"))
                .anyMatch(target -> target.startsWith("switch_exit_"));
    }

    @Test
    void lowersPointersArraysStructsAndFunctionPointers() {
        Program program = parse("""
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

                int readY(struct Point *point) {
                    point->y = 9;
                    return point->y;
                }

                int main() {
                    int x = 1;
                    int *p = &x;
                    int values[2];
                    struct Point point;
                    int (*operation)(int, int) = add;
                    *p = 2;
                    values[0] = apply(operation, x, 7);
                    point.y = values[0];
                    return readY(&point);
                }
                """);
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();

        IrModule module = new IrLowerer().lower(program, semanticResult);

        IrFunction apply = module.findFunction("apply").orElseThrow();
        assertThat(apply.parameters()).extracting(IrParameter::type)
                .containsExactly(IrType.POINTER, IrType.INT, IrType.INT);
        assertThat(instructions(apply))
                .filteredOn(IrIndirectCallInstruction.class::isInstance)
                .map(IrIndirectCallInstruction.class::cast)
                .singleElement()
                .satisfies(call -> assertThat(call.calleeAddress()).isEqualTo(new IrParameterRef("operation", IrType.POINTER)));

        IrFunction readY = module.findFunction("readY").orElseThrow();
        assertThat(readY.parameters()).singleElement().satisfies(parameter ->
                assertThat(parameter.type()).isEqualTo(IrType.POINTER));
        assertThat(instructions(readY)).filteredOn(IrAddressOfLocalInstruction.class::isInstance).isEmpty();
        assertThat(instructions(readY))
                .filteredOn(IrFieldAddressInstruction.class::isInstance)
                .map(IrFieldAddressInstruction.class::cast)
                .extracting(IrFieldAddressInstruction::offset)
                .containsExactly(4, 4);

        List<IrInstruction> mainInstructions = instructions(module.findFunction("main").orElseThrow());
        assertThat(mainInstructions)
                .filteredOn(IrDeclareLocalInstruction.class::isInstance)
                .map(IrDeclareLocalInstruction.class::cast)
                .extracting(declare -> declare.local().type())
                .contains(IrType.INT, IrType.POINTER, IrType.INT_ARRAY, IrType.STRUCT);
        assertThat(mainInstructions)
                .filteredOn(IrStoreLocalInstruction.class::isInstance)
                .map(IrStoreLocalInstruction.class::cast)
                .extracting(IrStoreLocalInstruction::value)
                .contains(new IrFunctionAddress("add"));
        assertThat(mainInstructions).filteredOn(IrAddressOfLocalInstruction.class::isInstance).hasSizeGreaterThanOrEqualTo(2);
        assertThat(mainInstructions).filteredOn(IrElementAddressInstruction.class::isInstance).hasSizeGreaterThanOrEqualTo(2);
        assertThat(mainInstructions).filteredOn(IrFieldAddressInstruction.class::isInstance).hasSizeGreaterThanOrEqualTo(1);
        assertThat(mainInstructions).filteredOn(IrStorePointerInstruction.class::isInstance).hasSizeGreaterThanOrEqualTo(3);
        assertThat(mainInstructions).filteredOn(IrLoadPointerInstruction.class::isInstance).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void lowersScalarWidthsNullFloatingConstantsAndCasts() {
        Program program = parse("""
                long idLong(long value) { return value; }
                int main() {
                    bool flag = true;
                    char tag = 'a';
                    long count = 1L + 2L;
                    int *missing = NULL;
                    float ratio = 1.25f;
                    double score = ratio + 2.5;
                    double widened = 3;
                    int integerCheck = idLong(count) == 3L;
                    int floatCheck = score > widened;
                    return integerCheck + floatCheck;
                }
                """);
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();

        IrModule module = new IrLowerer().lower(program, semanticResult);

        assertThat(module.findFunction("idLong").orElseThrow().parameters()).singleElement()
                .satisfies(parameter -> assertThat(parameter.type()).isEqualTo(IrType.LONG));
        List<IrInstruction> mainInstructions = instructions(module.findFunction("main").orElseThrow());
        assertThat(mainInstructions)
                .filteredOn(IrStoreLocalInstruction.class::isInstance)
                .map(IrStoreLocalInstruction.class::cast)
                .extracting(store -> store.local().type())
                .contains(IrType.BOOL, IrType.CHAR, IrType.LONG, IrType.POINTER, IrType.FLOAT, IrType.DOUBLE);
        assertThat(mainInstructions)
                .filteredOn(IrStoreLocalInstruction.class::isInstance)
                .map(IrStoreLocalInstruction.class::cast)
                .extracting(IrStoreLocalInstruction::value)
                .contains(
                        new IrConstant(1, IrType.BOOL),
                        new IrConstant('a', IrType.CHAR),
                        new IrConstant(0, IrType.POINTER),
                        new IrFloatConstant(1.25f)
                );
        assertThat(mainInstructions)
                .filteredOn(IrCastInstruction.class::isInstance)
                .map(IrCastInstruction.class::cast)
                .extracting(cast -> cast.result().type())
                .contains(IrType.DOUBLE);
        assertThat(mainInstructions)
                .filteredOn(IrBinaryInstruction.class::isInstance)
                .map(IrBinaryInstruction.class::cast)
                .extracting(binary -> binary.result().type())
                .contains(IrType.LONG, IrType.DOUBLE, IrType.INT);
        assertThat(mainInstructions)
                .filteredOn(IrCallInstruction.class::isInstance)
                .map(IrCallInstruction.class::cast)
                .singleElement()
                .satisfies(call -> assertThat(call.result().type()).isEqualTo(IrType.LONG));
    }

    @Test
    void lowersPhaseDExpressionsToIrInstructions() {
        Program program = parse("""
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
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();

        List<IrInstruction> mainInstructions = instructions(new IrLowerer().lower(program, semanticResult)
                .findFunction("main").orElseThrow());

        assertThat(mainInstructions)
                .filteredOn(IrBinaryInstruction.class::isInstance)
                .map(IrBinaryInstruction.class::cast)
                .extracting(IrBinaryInstruction::operator)
                .contains(
                        IrBinaryOperator.MODULO,
                        IrBinaryOperator.BITWISE_AND,
                        IrBinaryOperator.BITWISE_OR,
                        IrBinaryOperator.BITWISE_XOR,
                        IrBinaryOperator.SHIFT_LEFT,
                        IrBinaryOperator.SHIFT_RIGHT
                );
        assertThat(mainInstructions).filteredOn(IrUnaryInstruction.class::isInstance).hasSizeGreaterThanOrEqualTo(2);
        IrFunction main = new IrLowerer().lower(program, semanticResult).findFunction("main").orElseThrow();
        assertThat(main.blocks()).extracting(IrBlock::label)
                .anyMatch(label -> label.startsWith("logical_or_true_"))
                .anyMatch(label -> label.startsWith("logical_or_rhs_"))
                .anyMatch(label -> label.startsWith("conditional_then_"))
                .anyMatch(label -> label.startsWith("conditional_else_"))
                .anyMatch(label -> label.startsWith("conditional_merge_"));
        assertThat(mainInstructions).filteredOn(IrMoveInstruction.class::isInstance).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void preservesShortCircuitSideEffectsInLogicalAndConditionalExpressions() {
        Program program = parse("""
                int side() {
                    return 1;
                }

                int main() {
                    int value = 0;
                    int result = value && (value = side());
                    result = 1 || (value = side());
                    result = value ? (value = 2) : (value = 3);
                    return value + result;
                }
                """);
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();

        IrFunction main = new IrLowerer().lower(program, semanticResult).findFunction("main").orElseThrow();

        assertThat(main.blocks()).extracting(IrBlock::label)
                .anyMatch(label -> label.startsWith("logical_and_rhs_"))
                .anyMatch(label -> label.startsWith("logical_and_false_"))
                .anyMatch(label -> label.startsWith("logical_or_true_"))
                .anyMatch(label -> label.startsWith("logical_or_rhs_"))
                .anyMatch(label -> label.startsWith("conditional_then_"))
                .anyMatch(label -> label.startsWith("conditional_else_"));
        assertThat(block(main, "logical_and_rhs_").instructions())
                .anyMatch(IrCallInstruction.class::isInstance)
                .anyMatch(IrStoreLocalInstruction.class::isInstance);
        assertThat(block(main, "logical_or_rhs_").instructions())
                .anyMatch(IrCallInstruction.class::isInstance)
                .anyMatch(IrStoreLocalInstruction.class::isInstance);
        assertThat(block(main, "logical_or_true_").instructions())
                .noneMatch(IrCallInstruction.class::isInstance)
                .anyMatch(IrMoveInstruction.class::isInstance);
        assertThat(block(main, "conditional_then_").instructions())
                .filteredOn(IrStoreLocalInstruction.class::isInstance)
                .map(IrStoreLocalInstruction.class::cast)
                .extracting(IrStoreLocalInstruction::value)
                .contains(new IrConstant(2));
        assertThat(block(main, "conditional_else_").instructions())
                .filteredOn(IrStoreLocalInstruction.class::isInstance)
                .map(IrStoreLocalInstruction.class::cast)
                .extracting(IrStoreLocalInstruction::value)
                .contains(new IrConstant(3));
    }

    @Test
    void usesDeclaredTypeLayoutForLocalStorage() {
        SourceFile sourceFile = new SourceFile("manual-lower.mc", "");
        SourceRange range = new SourceRange(sourceFile, 0, 0);
        Program program = new Program(List.of(new FunctionDecl(
                "main",
                List.of(),
                new BlockStmt(List.of(
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
    void keepsShadowedLocalsDistinctWithoutDependingOnGeneratedNames() {
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

        List<IrInstruction> instructions = instructions(new IrLowerer().lower(program).findFunction("main").orElseThrow());
        List<IrDeclareLocalInstruction> xDeclarations = instructions.stream()
                .filter(IrDeclareLocalInstruction.class::isInstance)
                .map(IrDeclareLocalInstruction.class::cast)
                .filter(declare -> declare.local().sourceName().equals("x"))
                .toList();

        assertThat(xDeclarations).hasSize(2);
        assertThat(xDeclarations).extracting(declare -> declare.local().name()).doesNotHaveDuplicates();
        assertThat(instructions)
                .filteredOn(IrLoadLocalInstruction.class::isInstance)
                .map(IrLoadLocalInstruction.class::cast)
                .extracting(IrLoadLocalInstruction::local)
                .contains(xDeclarations.get(0).local(), xDeclarations.get(1).local());
    }

    private Program parse(String source) {
        SourceFile sourceFile = new SourceFile("lower.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        return parseResult.program();
    }

    private List<IrInstruction> instructions(IrFunction function) {
        return function.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .toList();
    }

    private List<IrBranchInstruction> branches(IrFunction function) {
        return instructions(function).stream()
                .filter(IrBranchInstruction.class::isInstance)
                .map(IrBranchInstruction.class::cast)
                .toList();
    }

    private List<String> jumpTargets(IrFunction function) {
        return instructions(function).stream()
                .filter(IrJumpInstruction.class::isInstance)
                .map(IrJumpInstruction.class::cast)
                .map(IrJumpInstruction::targetLabel)
                .toList();
    }

    private boolean hasBlock(IrFunction function, String label) {
        return function.blocks().stream().anyMatch(block -> block.label().equals(label));
    }

    private IrBlock block(IrFunction function, String labelPrefix) {
        return function.blocks().stream()
                .filter(block -> block.label().startsWith(labelPrefix))
                .findFirst()
                .orElseThrow();
    }
}
