package craken.debug.visualization;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static craken.debug.visualization.DebugMemoryReader.ScalarType.*;
import static craken.debug.visualization.DebugStructureDescriptor.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class DebugStructureDescriptorTest {
    @Test void aScalarAndReferenceNameCanOnlyShareTheSamePointerField() {
        var valid=descriptor("valid",ViewKind.POINT,16,List.of(new Field("next",8,POINTER)),
                List.of(new Reference("next",8,"valid",Relation.TOPOLOGY,Direction.FORWARD)),null);
        assertEquals(1,valid.fields().size());
        assertThrows(IllegalArgumentException.class,()->descriptor("nonpointer",ViewKind.POINT,16,List.of(new Field("next",8,SIGNED32)),
                List.of(new Reference("next",8,"nonpointer",Relation.TOPOLOGY,Direction.FORWARD)),null));
    }
    @Test void aScalarAndReferenceNameCannotHideTwoDifferentOffsets() {
        assertThrows(IllegalArgumentException.class,()->descriptor("offset collision",ViewKind.POINT,16,List.of(new Field("next",0,POINTER)),
                List.of(new Reference("next",8,"offset collision",Relation.TOPOLOGY,Direction.FORWARD)),null));
    }
    @Test
    void callerSchemasRetainExactOffsetsAndDistinguishDrawingEdgesFromOwnership() {
        var descriptor = descriptor("tree", ViewKind.TREE, 24, List.of(new Field("value", 0, SIGNED32)),
                List.of(new Reference("child", 8, "tree", Relation.TOPOLOGY, Direction.FORWARD)), null);
        assertEquals("tree", descriptor.pageTypeKey());
        assertEquals(Relation.TOPOLOGY, descriptor.references().getFirst().relation());
        assertEquals(8, descriptor.references().getFirst().offset());
        var singleton = descriptor("singleton", ViewKind.POINT, 8, List.of(),
                List.of(new Reference("tree", 0, "tree", Relation.OWNERSHIP, Direction.NONE)), null);
        assertEquals(Relation.OWNERSHIP, singleton.references().getFirst().relation());
        var array = descriptor("array", ViewKind.ARRAY, 16, List.of(), List.of(), new ArrayLayout(0, 4, 4, "int"));
        assertEquals(4, array.array().stride());
    }

    @Test
    void schemasCannotRetainMutableCallerLists() {
        List<Field> fields = new ArrayList<>(List.of(new Field("value", 0, SIGNED32)));
        var schema = descriptor("int", ViewKind.POINT, 4, fields, List.of(), null);
        fields.clear();
        assertEquals(1, schema.fields().size());
        assertThrows(UnsupportedOperationException.class, () -> schema.fields().clear());
    }

    @Test
    void invalidFieldNamesOffsetsSizesAndDuplicateNamesAreRejectedBeforeReadingMemory() {
        assertThrows(IllegalArgumentException.class, () -> descriptor("", ViewKind.POINT, 4, List.of(), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> descriptor("small", ViewKind.POINT, 1,
                List.of(new Field("integer", 0, SIGNED32)), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> descriptor("offset", ViewKind.POINT, 8,
                List.of(new Field("value", -1, SIGNED32)), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> descriptor("dupe", ViewKind.POINT, 8,
                List.of(new Field("same", 0, SIGNED32), new Field("same", 4, SIGNED32)), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> descriptor("bad ref", ViewKind.POINT, 4, List.of(),
                List.of(new Reference("pointer", 0, "target", Relation.OWNERSHIP, Direction.NONE)), null));
    }

    @Test
    void arrayBoundsAndOverflowAreValidatedAsPartOfTheManualSchema() {
        assertThrows(IllegalArgumentException.class, () -> descriptor("not array", ViewKind.POINT, 8, List.of(), List.of(),
                new ArrayLayout(0, 2, 4, "int")));
        assertThrows(IllegalArgumentException.class, () -> descriptor("too small", ViewKind.ARRAY, 4, List.of(), List.of(),
                new ArrayLayout(0, 2, 4, "int")));
        assertThrows(IllegalArgumentException.class, () -> descriptor("overflow", ViewKind.ARRAY, 8, List.of(), List.of(),
                new ArrayLayout(0, Integer.MAX_VALUE, Integer.MAX_VALUE, "int")));
        assertThrows(IllegalArgumentException.class, () -> new RootAddress("", 100));
        assertThrows(IllegalArgumentException.class, () -> new RootAddress("point", 0));
    }

    private static DebugStructureDescriptor descriptor(String key, ViewKind kind, int size, List<Field> fields,
                                                        List<Reference> references, ArrayLayout array) {
        return new DebugStructureDescriptor(key, key, kind, size, fields, references, array, ReallocationPolicy.RECREATE);
    }
}
