package minic.runtime.debug;

import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebugSessionTest {
    @Test
    void createsPausedSessionWithInitialSnapshot() {
        DebugSession session = DebugSession.fromSource(new SourceFile("main.mc", "int main() { return 0; }"));

        assertThat(session.state()).isEqualTo(DebugExecutionState.PAUSED);
        assertThat(session.snapshots()).hasSize(1);
        assertThat(session.currentSnapshot().stopReason()).isEqualTo(DebugStopReason.START);
        assertThat(session.currentSnapshot().cursor().functionName()).isEqualTo("main");
        assertThat(session.events()).isEmpty();
    }

    @Test
    void appendsSnapshotsAndEventsDefensively() {
        DebugSession session = DebugSession.fromSource(new SourceFile("main.mc", "int main() { return 0; }"));
        ArrayList<String> stack = new ArrayList<>(List.of("main", "helper"));
        DebugSnapshot snapshot = new DebugSnapshot(
                1,
                1,
                new DebugCursor("helper", "entry", "ir-1", null, "ast-1", List.of("asm-1")),
                stack,
                new DebugProcessSpace(
                        DebugCodeSegment.empty(),
                        DebugStaticSegment.empty(),
                        DebugStackSegment.empty(),
                        DebugHeapSegment.empty(),
                        DebugIoSegment.empty().appendStdout("out")
                ),
                true,
                DebugStopReason.BREAKPOINT
        );

        session.appendSnapshot(snapshot);
        stack.add("mutated");
        session.appendEvent(new DebugEvent(0, 1, "BREAKPOINT", "命中断点", "停在 helper。", null, List.of("local:x")));

        assertThat(session.currentSnapshot()).isEqualTo(snapshot);
        assertThat(session.currentSnapshot().callStackSummary()).containsExactly("main", "helper");
        assertThat(session.currentSnapshot().processSpace().io().stdout()).isEqualTo("out");
        assertThat(session.currentSnapshot().breakpointHit()).isTrue();
        assertThat(session.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("BREAKPOINT");
            assertThat(event.affectedValueRefs()).containsExactly("local:x");
        });
        assertThatThrownBy(() -> session.snapshots().add(snapshot))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> session.events().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNonBreakableLine() {
        DebugSession session = DebugSession.fromSource(new SourceFile("main.mc", "int main() { return 0; }"));

        DebugBreakpointResult result = session.setBreakpoint(99);

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("不可断");
        assertThat(session.breakpoints()).isEmpty();
    }
}
