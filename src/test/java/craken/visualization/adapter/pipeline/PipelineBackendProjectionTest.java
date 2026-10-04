package craken.visualization.adapter.pipeline;

import craken.compiler.*;
import craken.compiler.asm.AsmResult;
import craken.compiler.obj.ObjResult;
import craken.compiler.obj.coff.CoffObjectReader;
import craken.compiler.link.LinkResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class PipelineBackendProjectionTest {
    @TempDir Path directory;
    @Test void backendProjectorsDisplayRealAssemblyCoffAndLinkedImageWithoutExecutingTheProgram() {
        CompilerApi api = new CompilerApi(new SourceFile("backend.mc", "int value=3; int main(){return value+4;}"), directory);
        api.setCaptureLatestContext(Stage.class, true);
        var registry = PipelineProjectionRegistry.standard();
        int checked = 0;
        while (api.currentStageIndex() < 8 && api.canNext()) {
            Stage executed = api.currentStage().orElseThrow(); int index = api.currentStageIndex();
            Stage.Result result = api.stepResult();
            if (!result.lastStep() || index < 5) continue;
            var observation = PipelineStepObservation.capture(executed,index,result);
            var plan = registry.project(observation,null).output();
            if (result.context() instanceof AsmResult assembly) {
                String text = plan.nodes().stream().skip(1).map(n -> n.content().fields().get("assembly"))
                        .collect(java.util.stream.Collectors.joining("\n"));
                assertEquals(assembly.text(), text);
            } else if (result.context() instanceof ObjResult object) {
                var parsed = new CoffObjectReader().read(object.objectFile());
                assertEquals(parsed.sections().size(), plan.nodes().stream().filter(n -> n.content().fields().containsKey("section")).count());
                assertEquals(parsed.symbols().size(), plan.nodes().stream().filter(n -> n.content().fields().containsKey("symbol")).count());
                assertEquals(parsed.sections().stream().mapToLong(s -> s.relocations().size()).sum(),
                        plan.nodes().stream().filter(n -> n.content().fields().containsKey("relocation")).count());
            } else if (result.context() instanceof LinkResult link) {
                assertTrue(plan.nodes().stream().anyMatch(n -> link.executableArtifact().path().toString().equals(n.content().fields().get("executable"))));
                assertTrue(plan.nodes().stream().anyMatch(n -> n.content().fields().containsKey("peBytes")));
            }
            checked++;
        }
        assertEquals(3,checked);
        assertEquals(8,api.currentStageIndex());
        assertTrue(api.currentStage().orElseThrow().stageResult().isEmpty(), "native execution must remain untouched");
    }

    @Test void earlyObjectAndLinkObservationsAreFrozenBeforeRetryOrHistory() {
        CompilerApi api = new CompilerApi(new SourceFile("early.mc", "int main(){return 0;}"), directory);
        api.setCaptureLatestContext(Stage.class,true);
        PipelineStepObservation encoded = null, image = null;
        while (api.currentStageIndex() < 8 && api.canNext()) {
            Stage stage=api.currentStage().orElseThrow(); int index=api.currentStageIndex();
            var result=api.stepResult();
            var observed=PipelineStepObservation.capture(stage,index,result);
            if (index==6 && observed.encodedModule()!=null && ((ObjResult) observed.context()).objectFile()==null) encoded=observed;
            if (index==7 && observed.peImage()!=null && ((LinkResult) observed.context()).executableArtifact()==null) image=observed;
        }
        assertNotNull(encoded); assertNotNull(image);
        var registry=PipelineProjectionRegistry.standard();
        assertTrue(registry.project(encoded,null).output().nodes().stream().anyMatch(n -> n.content().fields().containsKey("encodedBytes")));
        byte[] bytes=image.peImage(); bytes[0]=0;
        assertEquals('M',image.peImage()[0]);
        assertTrue(registry.project(image,null).output().nodes().stream().anyMatch(n -> n.content().fields().containsKey("peBytes")));
    }
}
