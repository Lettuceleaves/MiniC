package craken.visualization.adapter.pipeline;

import craken.SourceRange;
import craken.compiler.*;
import craken.compiler.parser.Parser;
import craken.compiler.parser.node.AstNode;
import craken.compiler.preprocess.Preprocessor;
import craken.compiler.lexer.Lexer;
import craken.compiler.semantic.SemanticResult;
import craken.compiler.ir.IrResult;
import craken.compiler.obj.ObjBuilder;
import craken.compiler.obj.machine.MachineModule;
import craken.compiler.obj.x64.EncodedMachineModule;
import craken.compiler.link.Linker;
import java.util.Objects;

/** Frozen public observations from the stage that actually executed this step. */
public record PipelineStepObservation(Stage executedStage, int stageIndex, Stage.Result result,
        SourceFile source, int cursor, AstNode currentAstNode, MachineModule machineModule,
        EncodedMachineModule encodedModule, byte[] peImage, SourceRange expandedSourceRange) {
    public PipelineStepObservation(Stage executedStage, int stageIndex, Stage.Result result,
            SourceFile source, int cursor, AstNode currentAstNode, MachineModule machineModule,
            EncodedMachineModule encodedModule, byte[] peImage) {
        this(executedStage, stageIndex, result, source, cursor, currentAstNode, machineModule,
                encodedModule, peImage, null);
    }
    public PipelineStepObservation {
        Objects.requireNonNull(executedStage); Objects.requireNonNull(result);
        if (stageIndex < 0 || result.stageType() != executedStage.getClass())
            throw new IllegalArgumentException("Result must belong to the executed stage");
        if (result.context() == null) throw new IllegalArgumentException("Latest context capture is required");
        peImage = peImage == null ? null : peImage.clone();
    }
    @Override public byte[] peImage() { return peImage == null ? null : peImage.clone(); }
    public Stage.Context context() { return result.context(); }
    public static PipelineStepObservation capture(Stage stage, int index, Stage.Result result) {
        SourceFile source = stage instanceof Preprocessor p ? p.sourceFile() : null;
        int cursor = stage instanceof Parser p ? p.currentIndex() : stage instanceof Lexer l ? l.currentOffset() : -1;
        AstNode current = null;
        if (stage instanceof Parser p && result.operation().startsWith("PARSE_") && !p.completedNodes().isEmpty())
            current = p.completedNodes().getLast();
        if (result.context() instanceof SemanticResult semantic && semantic.action() != null
                && semantic.action().astNode() instanceof AstNode node) current = node;
        if (result.context() instanceof IrResult ir) current = ir.currentAstNode();
        MachineModule machine = stage instanceof ObjBuilder obj ? obj.machineModule().orElse(null) : null;
        EncodedMachineModule encoded = stage instanceof ObjBuilder obj ? obj.encodedMachineModule().orElse(null) : null;
        byte[] image = stage instanceof Linker linker ? linker.peImage().map(p -> p.bytes()).orElse(null) : null;
        SourceRange expanded = stage instanceof Lexer lexer ? lexer.expandedStepRange() : null;
        return new PipelineStepObservation(stage, index, result, source, cursor, current, machine, encoded, image, expanded);
    }
}
