package craken.compiler.ir.optimize;

import craken.compiler.ir.model.IrFunction;
import craken.compiler.ir.value.IrValue.IrTemporary;
import java.util.*;

/** A native frame plan for temporary values only; offsets are relative ending offsets. */
public final class TemporarySlotPlan {
    public record Slot(int offset, int sizeBytes) {
        public Slot {
            if ((sizeBytes != 1 && sizeBytes != 2 && sizeBytes != 4 && sizeBytes != 8)
                    || offset < sizeBytes || offset % sizeBytes != 0) throw new IllegalArgumentException("invalid temporary slot");
        }
    }
    private final Map<String, Slot> slots;
    private final Set<String> pinnedTemporaries;
    private final int storageBytes, unsharedBytes, slotCount;

    private TemporarySlotPlan(IrFunction function) {
        var analysis = IrLiveness.analyze(function);
        var names = new ArrayList<>(analysis.temporaryTypes().keySet());
        var indices = new HashMap<String, Integer>();
        int count = names.size(), independentBytes = 0;
        int[] widths = new int[count];
        for (int index = 0; index < count; index++) {
            indices.put(names.get(index), index);
            widths[index] = analysis.temporaryTypes().get(names.get(index)).sizeBytes();
            independentBytes = Math.addExact(independentBytes, widths[index]);
        }
        var pinned = new BitSet(count);
        for (var block : function.blocks()) for (int index = 0; index < block.instructions().size(); index++) {
            if (analysis.instruction(block.label(), index).executable()) continue;
            var instruction = block.instructions().get(index);
            IrTemporary result = IrValueUses.result(instruction);
            if (result != null) pinned.set(indices.get(result.name()));
            for (var value : IrValueUses.inputs(instruction))
                if (value instanceof IrTemporary temporary) pinned.set(indices.get(temporary.name()));
        }
        BitSet[] interference = new BitSet[count];
        for (int index = 0; index < count; index++) interference[index] = new BitSet();
        for (var block : function.blocks()) for (int index = 0; index < block.instructions().size(); index++) {
            var point = analysis.instruction(block.label(), index);
            if (!point.executable()) continue;
            // All simultaneously read operands need separate bytes, even when both die here.
            BitSet[] beforeByWidth = { new BitSet(), new BitSet(), new BitSet(), new BitSet() };
            for (String name : point.liveBefore()) {
                int node = indices.get(name);
                if (!pinned.get(node)) beforeByWidth[Integer.numberOfTrailingZeros(widths[node])].set(node);
            }
            for (BitSet group : beforeByWidth)
                for (int node = group.nextSetBit(0); node >= 0; node = group.nextSetBit(node + 1)) {
                    interference[node].or(group); interference[node].clear(node);
                }
            // A dead destination still writes memory; never let it overwrite any surviving value.
            IrTemporary result = IrValueUses.result(block.instructions().get(index));
            if (result != null) {
                int definition = indices.get(result.name());
                if (!pinned.get(definition)) for (String name : point.liveAfter()) {
                    int live = indices.get(name);
                    if (live != definition && !pinned.get(live) && widths[live] == widths[definition]) {
                        interference[definition].set(live); interference[live].set(definition);
                    }
                }
            }
        }

        Slot[] assigned = new Slot[count];
        int end = 0, physicalCount = 0;
        // Descending widths keep every group aligned relative to an eight-byte-aligned frame base.
        for (int width : new int[]{8, 4, 2, 1}) {
            var nodes = new ArrayList<Integer>();
            for (int node = 0; node < count; node++) if (widths[node] == width && !pinned.get(node)) nodes.add(node);
            nodes.sort(Comparator.<Integer>comparingInt(node -> interference[node].cardinality()).reversed().thenComparingInt(node -> node));
            int[] colors = new int[count]; Arrays.fill(colors, -1);
            int colorCount = 0;
            for (int node : nodes) {
                var unavailable = new BitSet();
                for (int neighbor = interference[node].nextSetBit(0); neighbor >= 0; neighbor = interference[node].nextSetBit(neighbor + 1))
                    if (colors[neighbor] >= 0) unavailable.set(colors[neighbor]);
                int color = unavailable.nextClearBit(0);
                colors[node] = color; colorCount = Math.max(colorCount, color + 1);
            }
            for (int node : nodes) assigned[node] = new Slot(Math.addExact(end, Math.multiplyExact(colors[node] + 1, width)), width);
            end = Math.addExact(end, Math.multiplyExact(colorCount, width)); physicalCount += colorCount;
            for (int node = pinned.nextSetBit(0); node >= 0; node = pinned.nextSetBit(node + 1)) if (widths[node] == width) {
                end = Math.addExact(end, width); assigned[node] = new Slot(end, width); physicalCount++;
            }
        }
        var mapping = new LinkedHashMap<String, Slot>();
        var pinnedNames = new LinkedHashSet<String>();
        for (int node = 0; node < count; node++) {
            mapping.put(names.get(node), assigned[node]);
            if (pinned.get(node)) pinnedNames.add(names.get(node));
        }
        slots = Collections.unmodifiableMap(mapping);
        pinnedTemporaries = Collections.unmodifiableSet(pinnedNames);
        storageBytes = end; unsharedBytes = independentBytes; slotCount = physicalCount;
    }
    public static TemporarySlotPlan allocate(IrFunction function) { return new TemporarySlotPlan(Objects.requireNonNull(function, "function")); }
    public String strategyName() { return "temporary-stack-slot-reuse"; }
    public Map<String, Slot> slots() { return slots; }
    public Slot slot(String temporary) {
        Slot slot = slots.get(temporary);
        if (slot == null) throw new IllegalArgumentException("Unknown temporary: " + temporary);
        return slot;
    }
    public Set<String> pinnedTemporaries() { return pinnedTemporaries; }
    public int storageBytes() { return storageBytes; }
    public int unsharedBytes() { return unsharedBytes; }
    public int temporaryCount() { return slots().size(); }
    public int slotCount() { return slotCount; }
}
