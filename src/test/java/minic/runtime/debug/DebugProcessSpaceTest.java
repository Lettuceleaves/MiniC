package minic.runtime.debug;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebugProcessSpaceTest {
    @Test
    void formatsVirtualAddressesForTeachingView() {
        DebugVirtualAddress address = new DebugVirtualAddress("STACK", 32);

        assertThat(address.segment()).isEqualTo("stack");
        assertThat(address.display()).isEqualTo("stack:0x20");
    }

    @Test
    void supportsStackFramePushAndPop() {
        DebugMemoryEntry local = new DebugMemoryEntry(
                "x",
                new DebugVirtualAddress("stack", 16),
                "int",
                "1"
        );
        DebugStackFrame frame = new DebugStackFrame("frame-1", "main", List.of(), List.of(local), null, null);

        DebugStackSegment pushed = DebugStackSegment.empty().push(frame);
        DebugStackSegment popped = pushed.pop();

        assertThat(pushed.frames()).containsExactly(frame);
        assertThat(popped.frames()).isEmpty();
    }

    @Test
    void representsHeapBlocksAndIoSnapshotsDefensively() {
        ArrayList<DebugMemoryEntry> entries = new ArrayList<>();
        entries.add(new DebugMemoryEntry("value", new DebugVirtualAddress("heap", 0), "int", "42"));
        DebugHeapBlock block = new DebugHeapBlock(
                new DebugVirtualAddress("heap", 4096),
                "Node",
                16,
                entries,
                "allocated"
        );
        entries.add(new DebugMemoryEntry("mutated", null, "int", "0"));

        DebugProcessSpace processSpace = new DebugProcessSpace(
                new DebugCodeSegment(List.of("main"), "main", "ir-1", List.of("asm-1")),
                new DebugStaticSegment(List.of(), List.of(
                        new DebugMemoryEntry("str0", new DebugVirtualAddress("static", 128), "char[]", "\"hello\"")
                )),
                DebugStackSegment.empty(),
                new DebugHeapSegment(List.of(block)),
                DebugIoSegment.empty().appendStdout("hello")
        );

        assertThat(processSpace.code().currentFunctionOptional()).contains("main");
        assertThat(processSpace.heap().blocks()).singleElement().satisfies(heapBlock ->
                assertThat(heapBlock.entries()).singleElement().satisfies(entry ->
                        assertThat(entry.name()).isEqualTo("value")));
        assertThat(processSpace.io().stdout()).isEqualTo("hello");
        assertThatThrownBy(() -> processSpace.heap().blocks().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
