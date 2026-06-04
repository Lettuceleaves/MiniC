# Keymouse Control And Viewport Tracking System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tested keyboard/mouse-first control layer that routes viewport zoom/scroll/pan, pinned target selection, debugger/compiler commands, settings commands, and active tracking through shared interfaces.

**Architecture:** Add small `minic.ui.control` abstractions for viewport adapters, target registry, command registry, control hub, and active tracking. Existing JavaFX views expose adapters and register commands, while current buttons keep their visual style and delegate to the new command layer.

**Tech Stack:** Java 21, JavaFX, RichTextFX/Flowless, Gradle, JUnit 5, AssertJ.

---

## Execution Contract

- Use TDD for every production behavior.
- Keep commits small: docs, core abstractions, text viewport, graph viewport, command integration, active tracking.
- Do not touch unrelated local files such as `.claude/settings.local.json`, `.superpowers/`, or `java_pid*.hprof`.
- Do not add user-visible debug output for target routing.
- If a phase fails validation three times for the same acceptance item, revert only that phase's own files and redo it from tests.

## Subagent Assignments

### Explorer A: Text Viewports

**Scope:** Read-only investigation of `MiniCCodeEditor`, `MiniCSourceLoaderView`, compiler text columns, and debugger source highlighting.

**Deliverable:** Integration points, risks, and exact tests to add.

### Explorer B: Graph Viewports

**Scope:** Read-only investigation of AST/data-structure graph zoom, pan, scroll, and highlighted circle/rectangle shapes.

**Deliverable:** Integration points, active-shape detection approach, and exact tests to add.

### Explorer C: Business Commands

**Scope:** Read-only investigation of debugger eight controls, compiler six controls, theme switching, and frame interval settings.

**Deliverable:** Command ID mapping and minimal command registry interface.

### Worker 1: Core Control Abstractions

**Write Scope:**

- `src/main/java/minic/ui/control/MiniCControlTargetType.java`
- `src/main/java/minic/ui/control/MiniCViewportAdapter.java`
- `src/main/java/minic/ui/control/MiniCViewportRegistry.java`
- `src/main/java/minic/ui/control/MiniCControlCommand.java`
- `src/main/java/minic/ui/control/MiniCCommandRegistry.java`
- `src/test/java/minic/ui/MiniCViewportRegistryTest.java`
- `src/test/java/minic/ui/MiniCCommandRegistryTest.java`

### Worker 2: Text Viewport Adapter

**Write Scope:**

- `src/main/java/minic/ui/control/MiniCTextViewportAdapter.java`
- `src/main/java/minic/ui/editor/MiniCCodeEditor.java`
- `src/main/java/minic/ui/source/MiniCSourceLoaderView.java`
- `src/test/java/minic/ui/MiniCCodeEditorViewportControlTest.java`

### Worker 3: Graph Viewport Adapter

**Write Scope:**

- `src/main/java/minic/ui/control/MiniCGraphViewportAdapter.java`
- `src/main/java/minic/ui/visual/MiniCVisualPane.java`
- `src/main/java/minic/ui/debug/MiniCDebugPane.java`
- `src/test/java/minic/ui/MiniCGraphViewportAdapterTest.java`

### Coordinator: Integration

**Write Scope:**

- `src/main/java/minic/ui/control/MiniCWorkbenchControlHub.java`
- `src/main/java/minic/ui/control/MiniCActiveTrackingService.java`
- `src/main/java/minic/ui/debug/MiniCDebugPane.java`
- `src/main/java/minic/ui/panel/MiniCInspectorView.java`
- `src/main/java/minic/settings/MiniCSettingsPane.java`
- `src/main/java/minic/ui/workbench/MiniCWorkbenchShell.java`
- related UI tests

---

## Phase 1: Core Control Abstractions

**Files:**

- Create: `src/main/java/minic/ui/control/MiniCControlTargetType.java`
- Create: `src/main/java/minic/ui/control/MiniCViewportAdapter.java`
- Create: `src/main/java/minic/ui/control/MiniCViewportRegistry.java`
- Create: `src/main/java/minic/ui/control/MiniCControlCommand.java`
- Create: `src/main/java/minic/ui/control/MiniCCommandRegistry.java`
- Create: `src/test/java/minic/ui/MiniCViewportRegistryTest.java`
- Create: `src/test/java/minic/ui/MiniCCommandRegistryTest.java`

- [ ] **Step 1: Write failing viewport registry tests**

Create tests asserting:

```java
assertThat(registry.currentTarget()).isEmpty();
registry.pin(textAdapter);
assertThat(registry.currentTarget()).containsSame(textAdapter);
registry.hover(graphAdapter);
assertThat(registry.currentTarget()).containsSame(graphAdapter);
registry.clearHover(graphAdapter);
assertThat(registry.currentTarget()).containsSame(textAdapter);
```

Also assert `MiniCViewportAdapter.noop().zoomAt(Point2D.ZERO, 1.0)` and scroll/pan operations do not throw.

- [ ] **Step 2: Run red test**

Run:

```powershell
.\gradlew.bat test --tests minic.ui.MiniCViewportRegistryTest
```

Expected: compilation fails because classes do not exist.

- [ ] **Step 3: Implement registry and adapter contracts**

Implement:

```java
package minic.ui.control;

public enum MiniCControlTargetType {
    TEXT,
    GRAPH,
    STAGE,
    NONE
}
```

```java
package minic.ui.control;

import javafx.geometry.Point2D;

public interface MiniCViewportAdapter {
    MiniCControlTargetType type();
    default boolean canZoom() { return false; }
    default void zoomAt(Point2D localPoint, double delta) {}
    default boolean canScrollVertical() { return false; }
    default void scrollVertical(double delta) {}
    default boolean canScrollHorizontal() { return false; }
    default void scrollHorizontal(double delta) {}
    default boolean canPan() { return false; }
    default void pan(double deltaX, double deltaY) {}
    default boolean isActiveFullyVisible() { return true; }
    default void centerActiveIfNeeded() {
        if (!isActiveFullyVisible()) {
            centerActive();
        }
    }
    default void centerActive() {}
    static MiniCViewportAdapter noop() {
        return () -> MiniCControlTargetType.NONE;
    }
}
```

Implement `MiniCViewportRegistry` with `hover`, `clearHover`, `pin`, `clearPinned`, and `currentTarget`.

- [ ] **Step 4: Write failing command registry tests**

Create tests proving:

```java
MiniCCommandRegistry registry = new MiniCCommandRegistry();
AtomicInteger calls = new AtomicInteger();
registry.register(new MiniCControlCommand("debug.stepOver", "本层下一句", () -> true, calls::incrementAndGet));
assertThat(registry.execute("debug.stepOver")).isTrue();
assertThat(calls).hasValue(1);
assertThat(registry.execute("missing")).isFalse();
```

Also test disabled command does not run.

- [ ] **Step 5: Implement command records and registry**

Implement `MiniCControlCommand` as an immutable record with `id`, `label`, `BooleanSupplier enabled`, and `Runnable action`. Implement `MiniCCommandRegistry.register`, `command`, `enabled`, and `execute`.

- [ ] **Step 6: Validate phase**

Run:

```powershell
.\gradlew.bat test --tests minic.ui.MiniCViewportRegistryTest --tests minic.ui.MiniCCommandRegistryTest
```

Expected: pass.

- [ ] **Step 7: Commit**

```powershell
git add -- src/main/java/minic/ui/control src/test/java/minic/ui/MiniCViewportRegistryTest.java src/test/java/minic/ui/MiniCCommandRegistryTest.java
git commit -m "FEAT: 添加键鼠控制核心抽象"
```

---

## Phase 2: Text Viewport Adapter

**Files:**

- Create: `src/main/java/minic/ui/control/MiniCTextViewportAdapter.java`
- Modify: `src/main/java/minic/ui/editor/MiniCCodeEditor.java`
- Modify: `src/main/java/minic/ui/source/MiniCSourceLoaderView.java`
- Create: `src/test/java/minic/ui/MiniCCodeEditorViewportControlTest.java`

- [ ] **Step 1: Write failing text adapter tests**

Test expected behavior:

```java
MiniCCodeEditor editor = new MiniCCodeEditor();
editor.setText(String.join("\n", java.util.Collections.nCopies(80, "int value;")));
MiniCViewportAdapter adapter = editor.viewportAdapter();
double before = editor.editorFontSizeForTesting();
adapter.zoomAt(new Point2D(20, 20), 1.0);
assertThat(editor.editorFontSizeForTesting()).isGreaterThan(before);
```

Add tests for zoom upper/lower bounds, `canScrollVertical`, active line centering, and no scroll when active line is fully visible.

- [ ] **Step 2: Run red test**

```powershell
.\gradlew.bat test --tests minic.ui.MiniCCodeEditorViewportControlTest
```

Expected: compilation fails because `viewportAdapter` and testing accessors do not exist.

- [ ] **Step 3: Implement text adapter**

`MiniCTextViewportAdapter` wraps a `MiniCCodeEditor` first. It calls editor methods for font zoom, scroll, and active tracking.

Expose from `MiniCCodeEditor`:

- `public MiniCViewportAdapter viewportAdapter()`
- package-private or testing accessors for font size and estimated scroll.
- `public void zoomFontBy(double delta)`
- `public void scrollVerticalBy(double pixels)`
- `public boolean isCurrentExecutionFullyVisible()`
- `public void centerCurrentExecutionIfNeeded()`

Use RichTextFX/Flowless APIs already present in the editor where possible. Clamp font size with existing constants.

- [ ] **Step 4: Expose source loader adapter**

Add:

```java
public MiniCViewportAdapter viewportAdapter() {
    return sourceEditor.viewportAdapter();
}
```

- [ ] **Step 5: Validate phase**

```powershell
.\gradlew.bat test --tests minic.ui.MiniCCodeEditorViewportControlTest --tests minic.ui.MiniCSourceLoaderViewBreakpointTest --tests minic.ui.MiniCCodeEditorBreakpointGutterTest
```

Expected: pass.

- [ ] **Step 6: Commit**

```powershell
git add -- src/main/java/minic/ui/control/MiniCTextViewportAdapter.java src/main/java/minic/ui/editor/MiniCCodeEditor.java src/main/java/minic/ui/source/MiniCSourceLoaderView.java src/test/java/minic/ui/MiniCCodeEditorViewportControlTest.java
git commit -m "FEAT: 添加文本视口控制适配器"
```

---

## Phase 3: Graph Viewport Adapter

**Files:**

- Create: `src/main/java/minic/ui/control/MiniCGraphViewportAdapter.java`
- Modify: `src/main/java/minic/ui/visual/MiniCVisualPane.java`
- Modify: `src/main/java/minic/ui/debug/MiniCDebugPane.java`
- Create: `src/test/java/minic/ui/MiniCGraphViewportAdapterTest.java`

- [ ] **Step 1: Write failing graph adapter unit tests**

Create a `ScrollPane` containing a `Pane` with a highlighted `Circle` or `Rectangle`. Assert:

```java
MiniCGraphViewportAdapter adapter = new MiniCGraphViewportAdapter(scrollPane, content, zoomConsumer);
assertThat(adapter.isActiveFullyVisible()).isFalse();
adapter.centerActive();
assertThat(activeCenterInViewport(scrollPane, circle)).isCloseTo(viewportCenter, within(2.0));
```

Also test horizontal and vertical scroll methods modify `hvalue`/`vvalue`.

- [ ] **Step 2: Run red test**

```powershell
.\gradlew.bat test --tests minic.ui.MiniCGraphViewportAdapterTest
```

Expected: compilation fails because adapter does not exist.

- [ ] **Step 3: Implement graph adapter**

Implement constructor with:

- `ScrollPane scrollPane`
- `Node content`
- `DoubleConsumer zoomDeltaConsumer`
- active node predicate: style class contains `active`, `current`, `debug-active`, or node property `active=true`

Implement visible bounds by converting active node bounds to scroll-pane viewport coordinates. If active shape is not fully inside viewport, compute `hvalue` and `vvalue` from content bounds and shape center.

- [ ] **Step 4: Register graph adapters in visual/debug graph builders**

When creating AST/data-structure graph scroll panes:

- add style class identifying graph viewport
- keep hover/pin handler registration for Phase 5, where `ControlHub` owns event routing
- retain existing wheel zoom and drag pan behavior until Phase 5 centralizes event routing

- [ ] **Step 5: Validate phase**

```powershell
.\gradlew.bat test --tests minic.ui.MiniCGraphViewportAdapterTest --tests minic.ui.MiniCDebugPaneTest --tests minic.ui.MiniCVisualPaneInspectorSourceTest
```

Expected: pass.

- [ ] **Step 6: Commit**

```powershell
git add -- src/main/java/minic/ui/control/MiniCGraphViewportAdapter.java src/main/java/minic/ui/visual/MiniCVisualPane.java src/main/java/minic/ui/debug/MiniCDebugPane.java src/test/java/minic/ui/MiniCGraphViewportAdapterTest.java
git commit -m "FEAT: 添加图形视口控制适配器"
```

---

## Phase 4: Control Hub And Business Commands

**Files:**

- Create: `src/main/java/minic/ui/control/MiniCWorkbenchControlHub.java`
- Modify: `src/main/java/minic/ui/debug/MiniCDebugPane.java`
- Modify: `src/main/java/minic/ui/panel/MiniCInspectorView.java`
- Modify: `src/main/java/minic/settings/MiniCSettingsPane.java`
- Create: `src/test/java/minic/ui/MiniCWorkbenchControlHubTest.java`
- Extend: `src/test/java/minic/ui/MiniCDebugPaneTest.java`
- Extend: `src/test/java/minic/ui/MiniCWorkbenchShellTest.java`

- [ ] **Step 1: Write failing command integration tests**

Test that hub registers all command IDs:

```java
assertThat(hub.commandIds()).contains(
    "debug.start", "debug.runToEnd", "debug.runToBreakpoint", "debug.stepOver",
    "debug.stepInto", "debug.backToBreakpoint", "debug.stepBackOver", "debug.stepBack",
    "compiler.next", "compiler.nextStage", "compiler.runToExecution",
    "compiler.play", "compiler.playFast", "compiler.pause",
    "settings.theme.set", "settings.frameInterval.set"
);
```

UI tests should still assert debugger buttons are two rows of four and compiler inspector buttons are two rows of three.

- [ ] **Step 2: Run red test**

```powershell
.\gradlew.bat test --tests minic.ui.MiniCWorkbenchControlHubTest
```

Expected: compilation fails because hub does not exist.

- [ ] **Step 3: Implement hub**

`MiniCWorkbenchControlHub` owns:

- `MiniCViewportRegistry viewportRegistry`
- `MiniCCommandRegistry commandRegistry`
- `Runnable activeTrackingAction`

It exposes:

- `registerDebuggerCommands(...)`
- `registerCompilerCommands(...)`
- `registerSettingsCommands(...)`
- `execute(String commandId)`
- `handleZoom(Point2D localPoint, double delta)`
- `handleScrollVertical(double delta)`
- `handleScrollHorizontal(double delta)`
- `handlePan(double deltaX, double deltaY)`

After successful business command execution, call active tracking.

- [ ] **Step 4: Wire existing buttons through commands**

Debugger buttons call IDs:

- `debug.start`
- `debug.runToEnd`
- `debug.runToBreakpoint`
- `debug.stepOver`
- `debug.stepInto`
- `debug.backToBreakpoint`
- `debug.stepBackOver`
- `debug.stepBack`

Compiler buttons call IDs:

- `compiler.next`
- `compiler.nextStage`
- `compiler.runToExecution`
- `compiler.play`
- `compiler.playFast`
- `compiler.pause`

Settings pane calls settings command methods for theme and interval.

- [ ] **Step 5: Validate phase**

```powershell
.\gradlew.bat test --tests minic.ui.MiniCWorkbenchControlHubTest --tests minic.ui.MiniCDebugPaneTest --tests minic.ui.MiniCWorkbenchShellTest --tests minic.ui.MiniCPlaybackControllerTest
```

Expected: pass.

- [ ] **Step 6: Commit**

```powershell
git add -- src/main/java/minic/ui/control/MiniCWorkbenchControlHub.java src/main/java/minic/ui/debug/MiniCDebugPane.java src/main/java/minic/ui/panel/MiniCInspectorView.java src/main/java/minic/settings/MiniCSettingsPane.java src/test/java/minic/ui/MiniCWorkbenchControlHubTest.java src/test/java/minic/ui/MiniCDebugPaneTest.java src/test/java/minic/ui/MiniCWorkbenchShellTest.java
git commit -m "FEAT: 统一调试器和编译器控制命令"
```

---

## Phase 5: Active Tracking Service And Keymouse Routing

**Files:**

- Create: `src/main/java/minic/ui/control/MiniCActiveTrackingService.java`
- Modify: `src/main/java/minic/ui/workbench/MiniCWorkbenchShell.java`
- Modify: `src/main/java/minic/ui/visual/MiniCVisualPane.java`
- Modify: `src/main/java/minic/ui/debug/MiniCDebugPane.java`
- Create: `src/test/java/minic/ui/MiniCActiveTrackingServiceTest.java`

- [ ] **Step 1: Write failing active tracking tests**

Use fake adapters:

```java
FakeAdapter visible = new FakeAdapter(true);
FakeAdapter hidden = new FakeAdapter(false);
service.track(List.of(visible, hidden));
assertThat(visible.centerCalls()).isZero();
assertThat(hidden.centerCalls()).isOne();
```

Add UI-level tests for source execution line/range and graph active shape where practical.

- [ ] **Step 2: Implement service**

`MiniCActiveTrackingService` receives active adapters from current visible views and calls `centerActiveIfNeeded` on each.

- [ ] **Step 3: Wire hover/pin and wheel handlers**

Register handlers on viewport roots:

- `MOUSE_ENTERED`: `registry.hover(adapter)`
- `MOUSE_EXITED`: `registry.clearHover(adapter)`
- `MOUSE_CLICKED`: `registry.pin(adapter)` for primary clicks
- wheel events call hub scroll/zoom depending on gesture type already handled by UI code

Do not capture key events from `StyleClassedTextArea` that would break typing.

- [ ] **Step 4: Validate phase**

```powershell
.\gradlew.bat test --tests minic.ui.MiniCActiveTrackingServiceTest --tests minic.ui.MiniCCodeEditorViewportControlTest --tests minic.ui.MiniCGraphViewportAdapterTest --tests minic.ui.MiniCDebugPaneTest --tests minic.ui.MiniCWorkbenchShellTest
```

Expected: pass.

- [ ] **Step 5: Full verification**

```powershell
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add -- src/main/java/minic/ui/control/MiniCActiveTrackingService.java src/main/java/minic/ui/workbench/MiniCWorkbenchShell.java src/main/java/minic/ui/visual/MiniCVisualPane.java src/main/java/minic/ui/debug/MiniCDebugPane.java src/test/java/minic/ui/MiniCActiveTrackingServiceTest.java
git commit -m "FEAT: 添加键鼠视口追踪与自动居中"
```

---

## Final Verification

Run:

```powershell
.\gradlew.bat test
git status --short
```

Final acceptance:

- Full suite succeeds.
- Worktree contains no unintended staged changes.
- No user-visible debug routing output is added.
- SPEC and plan are committed.
- Implementation is split into meaningful commits.
