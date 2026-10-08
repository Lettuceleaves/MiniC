package craken.debug.visualization;

import java.util.List;
import java.util.Objects;
import java.util.HashSet;

/** Explicit caller schema; malloc events never imply a tree, list, or hash table. */
public record DebugStructureDescriptor(String key, String pageTypeKey, ViewKind viewKind, int minimumSize,
                                       List<Field> fields, List<Reference> references, ArrayLayout array,
                                       ReallocationPolicy reallocationPolicy, String label) {
    public DebugStructureDescriptor(String key, String pageTypeKey, ViewKind viewKind, int minimumSize,
                                    List<Field> fields, List<Reference> references, ArrayLayout array,
                                    ReallocationPolicy reallocationPolicy) {
        this(key, pageTypeKey, viewKind, minimumSize, fields, references, array, reallocationPolicy, key);
    }
    public enum ViewKind { POINT, ARRAY, LINKED, TREE, GRAPH }
    public enum Relation { TOPOLOGY, OWNERSHIP }
    public enum Direction { NONE, FORWARD, BACKWARD, BOTH }
    public enum ReallocationPolicy { RECREATE, PRESERVE_LOCATION }

    public record Field(String name, int offset, DebugMemoryReader.ScalarType type) {
        public Field { requireKey(name); requireOffset(offset); Objects.requireNonNull(type); }
    }
    public record Reference(String name, int offset, String targetDescriptorKey, Relation relation, Direction direction,
                            String sourcePort, String targetPort) {
        public Reference(String name,int offset,String targetDescriptorKey,Relation relation,Direction direction) {
            this(name,offset,targetDescriptorKey,relation,direction,
                    relation==Relation.TOPOLOGY?"field:"+name:"node","node");
        }
        public Reference {
            requireKey(name); requireOffset(offset); requireKey(targetDescriptorKey);
            Objects.requireNonNull(relation); Objects.requireNonNull(direction);
            craken.visualization.model.relation.TopologyEdge.requirePort(sourcePort);
            craken.visualization.model.relation.TopologyEdge.requirePort(targetPort);
            if (relation == Relation.OWNERSHIP && direction != Direction.NONE) {
                throw new IllegalArgumentException("Ownership describes expansion; direction belongs to topology");
            }
        }
    }
    public record ArrayLayout(int offset, int length, int stride, String elementDescriptorKey) {
        public ArrayLayout {
            requireOffset(offset); requireKey(elementDescriptorKey);
            if (length < 0 || stride <= 0) throw new IllegalArgumentException("Invalid array length or stride");
        }
    }
    public record RootAddress(String descriptorKey, long address) {
        public RootAddress { requireKey(descriptorKey); if (address <= 0) throw new IllegalArgumentException("Root address must be positive"); }
    }

    public DebugStructureDescriptor {
        requireKey(key); requireKey(pageTypeKey);
        label = label == null || label.isBlank() ? key : label;
        if (minimumSize <= 0) throw new IllegalArgumentException("Object size must be positive");
        fields = List.copyOf(fields);
        references = List.copyOf(references);
        Objects.requireNonNull(viewKind);
        Objects.requireNonNull(reallocationPolicy);
        var names = new HashSet<String>();
        for (Field field : fields) {
            if (!names.add(field.name())) throw new IllegalArgumentException("Duplicate scalar field: " + field.name());
            requireFits(field.offset(), field.type().sizeBytes(), minimumSize);
        }
        names.clear();
        for (Reference reference : references) {
            if (!names.add(reference.name())) throw new IllegalArgumentException("Duplicate reference field: " + reference.name());
            requireFits(reference.offset(), Long.BYTES, minimumSize);
            for(Field field:fields)if(field.name().equals(reference.name())
                    &&(field.offset()!=reference.offset()||field.type()!=DebugMemoryReader.ScalarType.POINTER))
                throw new IllegalArgumentException("Scalar/reference names must describe the same pointer field: "+reference.name());
        }
        if (array != null) {
            if (viewKind != ViewKind.ARRAY) throw new IllegalArgumentException("Array composition requires an ARRAY view node");
            long bytes = (long) array.length() * array.stride();
            requireFits(array.offset(), bytes, minimumSize);
        }
    }

    private static void requireFits(int offset, long bytes, int size) {
        if (bytes < 0 || bytes > size - (long) offset) throw new IllegalArgumentException("Declared field exceeds the object size");
    }
    private static void requireKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Descriptor keys and field names cannot be empty");
    }
    private static void requireOffset(int offset) {
        if (offset < 0) throw new IllegalArgumentException("Field offset cannot be negative");
    }
}
