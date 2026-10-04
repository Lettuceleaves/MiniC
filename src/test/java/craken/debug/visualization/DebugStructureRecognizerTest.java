package craken.debug;

import craken.debug.visualization.*;
import craken.visualization.api.PageTypeRegistry;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static craken.debug.visualization.DebugStructureDescriptor.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class DebugStructureRecognizerTest {
    @Test void anExplicitlyAdoptedCandidateUsesTheExistingDescriptorAndPageTypeRegistrationChain() {
        var memory=new DebugMemoryReader(List.of(new DebugRuntime.MemoryBlock(100,4,"value","07000000",4,1,"0f")));
        var value=new DebugStructureDescriptor("value","point",ViewKind.POINT,4,
                List.of(new Field("value",0,DebugMemoryReader.ScalarType.SIGNED32)),List.of(),null,ReallocationPolicy.RECREATE);
        AtomicInteger invocations=new AtomicInteger();
        DebugStructureRecognizer recognizer=(reader,address)-> {
            invocations.incrementAndGet();assertEquals("7",reader.readScalar(address,0,DebugMemoryReader.ScalarType.SIGNED32));
            return Optional.of(value);
        };
        assertEquals(0,invocations.get());
        var candidate=recognizer.recognize(memory,memory.resolve(100)).orElseThrow();
        var types=new PageTypeRegistry();types.register(BuiltinPageTypes.point());
        var session=new DefaultVisualizationSession();var adapter=new DebugVisualizationAdapter(session,types);
        assertTrue(adapter.locations().isEmpty());
        adapter.registerDescriptor(candidate);adapter.registerRoot(new RootAddress(candidate.key(),100));
        var result=adapter.project(0,memory,new RuntimeEventBatch(0,true,true,List.of(),""));
        assertTrue(result.accepted(),result.diagnostic());assertEquals(1,adapter.locations().size());assertEquals(1,invocations.get());
        assertEquals("7",session.model().node(adapter.locations().values().iterator().next()).content().fields().get("value"));
        adapter.close();
    }
}
