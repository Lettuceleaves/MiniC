package minic.compiler.ir.lowering;

import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrJumpInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrParameterRef;
import minic.compiler.ir.value.IrTemporary;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class IrFunctionBuilder {
    private final ArrayList<IrBlockBuilder> blocks = new ArrayList<>();
    private final Map<String, IrParameterRef> parameterRefs = new HashMap<>();
    private final Deque<Map<String, IrLocal>> localScopes = new ArrayDeque<>();
    private int nextTemporaryIndex;
    private int nextLocalIndex;
    private int nextBlockIndex;
    private IrBlockBuilder currentBlock = new IrBlockBuilder("entry");

    IrFunctionBuilder() {
        blocks.add(currentBlock);
    }

    List<IrBlock> buildBlocks() {
        ArrayList<IrBlock> result = new ArrayList<>();
        for (IrBlockBuilder block : blocks) {
            result.add(new IrBlock(block.label(), block.instructions()));
        }
        return result;
    }

    String newBlockLabel(String prefix) {
        return prefix + "_" + nextBlockIndex++;
    }

    void switchToBlock(String label) {
        currentBlock = new IrBlockBuilder(label);
        blocks.add(currentBlock);
    }

    void addInstruction(IrInstruction instruction) {
        currentBlock.addInstruction(instruction);
    }

    void addJumpIfOpen(String targetLabel, minic.source.SourceRange range) {
        if (!currentBlock.isTerminated()) {
            addInstruction(new IrJumpInstruction(targetLabel, range));
        }
    }

    void defineParameter(String name, IrParameterRef parameterRef) {
        parameterRefs.put(name, parameterRef);
    }

    void pushLocalScope() {
        localScopes.push(new HashMap<>());
    }

    void popLocalScope() {
        localScopes.pop();
    }

    IrLocal declareLocal(VarDeclStmt varDeclStmt) {
        IrLocal local = new IrLocal(
                varDeclStmt.name() + "#" + nextLocalIndex++,
                varDeclStmt.name(),
                IrType.INT,
                varDeclStmt.range()
        );
        localScopes.peek().put(varDeclStmt.name(), local);
        return local;
    }

    IrLocal resolveLocal(String name) {
        for (Map<String, IrLocal> scope : localScopes) {
            IrLocal local = scope.get(name);
            if (local != null) {
                return local;
            }
        }
        return null;
    }

    IrParameterRef resolveParameter(String name) {
        IrParameterRef parameterRef = parameterRefs.get(name);
        if (parameterRef == null) {
            throw new IllegalArgumentException("unresolved value: " + name);
        }
        return parameterRef;
    }

    IrTemporary newTemporary() {
        return new IrTemporary("%" + nextTemporaryIndex++, IrType.INT);
    }

    private static final class IrBlockBuilder {
        private final String label;
        private final ArrayList<IrInstruction> instructions = new ArrayList<>();

        private IrBlockBuilder(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private List<IrInstruction> instructions() {
            return instructions;
        }

        private void addInstruction(IrInstruction instruction) {
            instructions.add(instruction);
        }

        private boolean isTerminated() {
            if (instructions.isEmpty()) {
                return false;
            }
            IrInstruction lastInstruction = instructions.getLast();
            return lastInstruction instanceof IrReturnInstruction
                    || lastInstruction instanceof IrJumpInstruction
                    || lastInstruction instanceof IrBranchInstruction;
        }
    }
}
