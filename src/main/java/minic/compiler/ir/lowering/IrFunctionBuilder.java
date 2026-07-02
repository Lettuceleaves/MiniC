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
import minic.compiler.semantic.StructFieldLayout;
import minic.compiler.semantic.StructLayout;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;

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
    private final Map<String, StructLayout> structLayouts;
    private int nextTemporaryIndex;
    private int nextLocalIndex;
    private int nextBlockIndex;
    private IrBlockBuilder currentBlock = new IrBlockBuilder("entry");

    IrFunctionBuilder(Map<String, StructLayout> structLayouts) {
        this.structLayouts = Map.copyOf(structLayouts);
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
        MiniType declaredType = varDeclStmt.type();
        IrType irType = IrTypeLowerer.lower(declaredType);
        IrLocal local = new IrLocal(
                varDeclStmt.name() + "#" + nextLocalIndex++,
                varDeclStmt.name(),
                irType,
                IrTypeLowerer.elementCount(declaredType),
                sizeBytes(declaredType, irType),
                varDeclStmt.range()
        );
        localScopes.peek().put(varDeclStmt.name(), local);
        return local;
    }

    IrLocal declareAnonymousLocal(IrType type, int sizeBytes, minic.source.SourceRange range) {
        String name = "__copy#" + nextLocalIndex++;
        return new IrLocal(name, name, type, 1, sizeBytes, range);
    }

    int fieldOffset(String structName, String fieldName) {
        return fieldLayout(structName, fieldName).offset();
    }

    int fieldOffset(String structName, int fieldIndex) {
        return fieldLayout(structName, fieldIndex).offset();
    }

    String fieldName(String structName, int fieldIndex) {
        return fieldLayout(structName, fieldIndex).name();
    }

    int fieldIndex(String structName, String fieldName) {
        StructLayout layout = structLayout(structName);
        for (int i = 0; i < layout.fields().size(); i++) {
            if (layout.fields().get(i).name().equals(fieldName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("missing struct field: " + structName + "." + fieldName);
    }

    int pointerFieldIndex(String structName, int declaredFieldIndex) {
        StructLayout layout = structLayout(structName);
        StructFieldLayout declaredField = layout.fields().get(declaredFieldIndex);
        if (!declaredField.type().isPointer()) {
            return -1;
        }
        int pointerIndex = 0;
        for (int i = 0; i < declaredFieldIndex; i++) {
            if (layout.fields().get(i).type().isPointer()) {
                pointerIndex++;
            }
        }
        return pointerIndex;
    }

    String fieldType(String structName, int fieldIndex) {
        return fieldLayout(structName, fieldIndex).type().toString();
    }

    StructFieldLayout fieldLayout(String structName, String fieldName) {
        return structLayout(structName).field(fieldName)
                .orElseThrow(() -> new IllegalArgumentException("missing struct field: " + structName + "." + fieldName));
    }

    StructFieldLayout fieldLayout(String structName, int fieldIndex) {
        return structLayout(structName).fields().get(fieldIndex);
    }

    int structSize(String structName) {
        StructLayout layout = structLayout(structName);
        return layout.size();
    }

    private StructLayout structLayout(String structName) {
        StructLayout layout = structLayouts.get(structName);
        if (layout == null) {
            throw new IllegalArgumentException("missing struct layout: " + structName);
        }
        return layout;
    }

    private int sizeBytes(MiniType declaredType, IrType irType) {
        if (declaredType instanceof MiniType.StructType structType) {
            StructLayout layout = structLayout(structType.name());
            return layout.size();
        }
        if (declaredType.isArray() && declaredType.elementType() instanceof MiniType.StructType structType) {
            StructLayout layout = structLayout(structType.name());
            return layout.size() * declaredType.arrayLength();
        }
        if (irType == IrType.INT_ARRAY) {
            return TypeLayout.sizeOf(declaredType);
        }
        if (irType == IrType.POINTER) {
            return TypeLayout.sizeOf(declaredType);
        }
        return TypeLayout.sizeOf(declaredType);
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

    IrParameterRef findParameter(String name) {
        return parameterRefs.get(name);
    }

    IrTemporary newTemporary() {
        return newTemporary(IrType.INT);
    }

    IrTemporary newTemporary(IrType type) {
        return new IrTemporary("%" + nextTemporaryIndex++, type);
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
