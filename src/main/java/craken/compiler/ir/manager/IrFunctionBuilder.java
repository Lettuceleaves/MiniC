package craken.compiler.ir.manager;

import craken.compiler.parser.node.Statement.VarDeclStmt;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.instruction.ControlInstruction.IrBranchInstruction;
import craken.compiler.ir.instruction.ControlInstruction.IrJumpInstruction;
import craken.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import craken.compiler.ir.model.IrBlock;
import craken.compiler.ir.model.IrLocal;
import craken.compiler.ir.model.IrType;
import craken.compiler.ir.value.IrValue.IrParameterRef;
import craken.compiler.ir.value.IrValue.IrTemporary;
import craken.compiler.semantic.model.StructLayout.StructFieldLayout;
import craken.compiler.semantic.model.StructLayout;
import craken.compiler.type.CrakenType;
import craken.compiler.type.TypeLayout;

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

    boolean currentBlockIsTerminated() {
        return currentBlock.isTerminated();
    }

    void addJumpIfOpen(String targetLabel, craken.SourceRange range) {
        if (!currentBlock.isTerminated()) {
            addInstruction(new IrJumpInstruction(targetLabel, range));
        }
    }

    void addVoidReturnIfOpen(craken.SourceRange range) {
        if (!currentBlock.isTerminated()) {
            addInstruction(new IrReturnInstruction(null, range));
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

    record LocalScopeSnapshot(List<Map<String, IrLocal>> scopes) { }

    LocalScopeSnapshot snapshotLocalScopes() {
        return new LocalScopeSnapshot(localScopes.stream().map(Map::copyOf).toList());
    }

    /** Replace, rather than overlay, every local scope so absent names still resolve to globals/parameters. */
    void withLocalScopes(LocalScopeSnapshot snapshot, Runnable action) {
        var previous = new ArrayList<>(localScopes);
        localScopes.clear();
        snapshot.scopes().forEach(scope -> localScopes.addLast(new HashMap<>(scope)));
        try {
            action.run();
        } finally {
            localScopes.clear();
            localScopes.addAll(previous);
        }
    }

    IrLocal declareLocal(VarDeclStmt varDeclStmt) {
        CrakenType declaredType = varDeclStmt.type();
        IrType irType = IrTypeLowerer.lower(declaredType);
        IrLocal local = new IrLocal(
                varDeclStmt.name() + "#" + nextLocalIndex++,
                varDeclStmt.name(),
                declaredType,
                irType,
                sizeOf(declaredType),
                declaredAlignment(varDeclStmt),
                varDeclStmt.range()
        );
        localScopes.peek().put(varDeclStmt.name(), local);
        return local;
    }

    IrLocal declareAnonymousLocal(CrakenType declaredType, craken.SourceRange range) {
        String name = "__copy#" + nextLocalIndex++;
        return new IrLocal(
                name,
                name,
                declaredType,
                IrTypeLowerer.lower(declaredType),
                sizeOf(declaredType),
                alignmentOf(declaredType),
                range
        );
    }

    /**
     * Returns a pseudo local whose address is the canonical Windows x64 argument
     * slot in the caller-provided home/stack area.  It is intentionally not
     * entered into a source scope and does not consume frame storage.
     */
    IrLocal incomingArgumentArea(int argumentIndex, craken.SourceRange range) {
        return IrLocal.incomingArgumentArea(argumentIndex, range);
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

    StructFieldLayout fieldLayout(String structName, String fieldName) {
        return structLayout(structName).field(fieldName)
                .orElseThrow(() -> new IllegalArgumentException("missing struct field: " + structName + "." + fieldName));
    }

    StructFieldLayout fieldLayout(String structName, int fieldIndex) {
        return structLayout(structName).fields().get(fieldIndex);
    }

    int fieldCount(String structName) { return structLayout(structName).fields().size(); }

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

    int sizeOf(CrakenType declaredType) {
        declaredType = declaredType.unqualified();
        if (declaredType instanceof CrakenType.ArrayType arrayType) {
            return Math.multiplyExact(sizeOf(arrayType.elementType()), arrayType.length());
        }
        if (declaredType instanceof CrakenType.StructType structType) {
            StructLayout layout = structLayout(structType.name());
            return layout.size();
        }
        return TypeLayout.sizeOf(declaredType);
    }

    int alignmentOf(CrakenType declaredType) {
        declaredType = declaredType.unqualified();
        if (declaredType instanceof CrakenType.ArrayType arrayType) {
            return alignmentOf(arrayType.elementType());
        }
        if (declaredType instanceof CrakenType.StructType structType) {
            StructLayout layout = structLayout(structType.name());
            return layout.alignment();
        }
        return TypeLayout.alignmentOf(declaredType);
    }

    private int declaredAlignment(VarDeclStmt declaration) {
        int alignment = alignmentOf(declaration.type());
        for (craken.compiler.parser.node.Declaration.AlignmentSpec specification : declaration.alignmentSpecs()) {
            int requested = specification.constant() != null
                    ? specification.constant()
                    : alignmentOf(specification.type());
            if (requested > alignment) {
                alignment = requested;
            }
        }
        return alignment;
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
