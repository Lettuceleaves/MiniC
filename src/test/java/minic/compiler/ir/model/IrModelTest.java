package minic.compiler.ir.model;

import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBinaryOperator;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IrModelTest {
    @Test
    void representsFunctionsTemporariesArithmeticCallsAndReturn() {
        SourceRange range = range("int main() { return add(1, 2) * 3; }");
        IrTemporary callResult = new IrTemporary("%0", IrType.INT);
        IrTemporary multiplyResult = new IrTemporary("%1", IrType.INT);
        IrCallInstruction call = new IrCallInstruction(
                callResult,
                "add",
                List.of(new IrConstant(1), new IrConstant(2)),
                range
        );
        IrBinaryInstruction multiply = new IrBinaryInstruction(
                multiplyResult,
                IrBinaryOperator.MULTIPLY,
                callResult,
                new IrConstant(3),
                range
        );
        IrReturnInstruction returnInstruction = new IrReturnInstruction(multiplyResult, range);
        IrBlock entry = new IrBlock("entry", List.of(call, multiply, returnInstruction));
        IrFunction main = new IrFunction("main", List.of(), List.of(entry), range);
        IrModule module = new IrModule(List.of(main));

        assertThat(module.findFunction("main")).contains(main);
        assertThat(main.blocks()).containsExactly(entry);
        assertThat(entry.instructions()).containsExactly(call, multiply, returnInstruction);
        assertThat(call.result()).isEqualTo(callResult);
        assertThat(call.arguments()).extracting(IrValue::type).containsExactly(IrType.INT, IrType.INT);
        assertThat(multiply.operator()).isEqualTo(IrBinaryOperator.MULTIPLY);
        assertThat(returnInstruction.value()).isEqualTo(multiplyResult);
    }

    @Test
    void defensivelyCopiesLists() {
        SourceRange range = range("int id(int x) { return x; }");
        IrParameter parameter = new IrParameter("x", IrType.INT, range);
        IrReturnInstruction returnInstruction = new IrReturnInstruction(parameter.ref(), range);
        ArrayList<IrValue> arguments = new ArrayList<>();
        ArrayList<IrInstruction> instructions = new ArrayList<>();
        ArrayList<IrParameter> parameters = new ArrayList<>();
        ArrayList<IrBlock> blocks = new ArrayList<>();
        ArrayList<IrFunction> functions = new ArrayList<>();

        IrCallInstruction call = new IrCallInstruction(new IrTemporary("%0", IrType.INT), "id", arguments, range);
        IrBlock block = new IrBlock("entry", instructions);
        IrFunction function = new IrFunction("id", parameters, blocks, range);
        IrModule module = new IrModule(functions);
        arguments.add(new IrConstant(1));
        instructions.add(returnInstruction);
        parameters.add(parameter);
        blocks.add(block);
        functions.add(function);

        assertThat(call.arguments()).isEmpty();
        assertThat(block.instructions()).isEmpty();
        assertThat(function.parameters()).isEmpty();
        assertThat(function.blocks()).isEmpty();
        assertThat(module.functions()).isEmpty();
        assertThatThrownBy(() -> call.arguments().add(new IrConstant(2)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> block.instructions().add(returnInstruction))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> function.parameters().add(parameter))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> function.blocks().add(block))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> module.functions().add(function))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankNames() {
        SourceRange range = range("int main() { return 1; }");

        assertThatThrownBy(() -> new IrTemporary(" ", IrType.INT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IrParameter("", IrType.INT, range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IrCallInstruction(new IrTemporary("%0", IrType.INT), " ", List.of(), range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IrBlock("", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IrFunction(" ", List.of(), List.of(), range))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SourceRange range(String source) {
        SourceFile sourceFile = new SourceFile("ir.mc", source);
        return new SourceRange(sourceFile, 0, source.length());
    }
}
