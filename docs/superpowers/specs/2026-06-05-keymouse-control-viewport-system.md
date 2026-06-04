# Keymouse Control And Viewport Tracking System SPEC

## Goal

Build a keyboard/mouse-first control abstraction for the MiniC workbench. The abstraction must route zoom, scroll, pan, business controls, theme switching, frame interval adjustment, and active-item tracking through tested interfaces instead of ad hoc UI handlers.

## User Intent

The primary consumer is the JavaFX UI itself. This is not an external scripting API. The design should make mouse hover, pinned viewport selection, button clicks, wheel gestures, and future shortcuts behave consistently for freshmen using the visual debugger and compiler pipeline.

## Non-Goals

1. Do not add user-visible debug output for current target, last command, or internal routing state.
2. Do not solve shortcut binding conflicts in this phase. Commands expose stable IDs, but concrete key mapping is explicitly outside this implementation.
3. Do not redesign visual templates or debugger data projection.
4. Do not replace existing JavaFX controls when a small adapter around them is enough.

## Core Concepts

### Control Target

A control target is an interactive viewport that may receive wheel, zoom, pan, and active-tracking commands.

Target categories:

- `TEXT`: source editor, compiler source/text columns, debugger metadata/IR/ASM text areas.
- `GRAPH`: AST graph, semantic AST graph, debugger data-structure graph.
- `STAGE`: compiler pipeline stage columns that contain text rows or embedded graph nodes.
- `NONE`: buttons, empty space, labels, and any component with no viewport behavior.

### Target Priority

When a pointer or command event needs a target, resolve it in this order:

1. Current hover target.
2. Pinned target from the most recent click inside a controllable viewport.
3. Current business active target supplied by the visible workbench mode.
4. No target; ignore viewport command safely.

This rule is required so clicking a debugger/compiler control button does not lose the viewport that the user was inspecting.

### Viewport Adapter

Every controllable viewport is represented by an adapter. A target may support only part of the interface.

Required operations:

- `canZoom()`
- `zoomAt(localPoint, delta)`
- `canScrollVertical()`
- `scrollVertical(delta)`
- `canScrollHorizontal()`
- `scrollHorizontal(delta)`
- `canPan()`
- `pan(deltaX, deltaY)`
- `centerActiveIfNeeded()`
- `centerActive()`
- `isActiveFullyVisible()`

Unsupported operations must be no-ops and must not throw.

### Command Registry

Business controls are registered as commands with stable IDs, enablement checks, and executable actions. Buttons and future shortcuts should call the registry instead of directly reaching into different UI classes.

Debugger commands:

| ID | Label | Existing Behavior |
|---|---|---|
| `debug.start` | 从头开始 | sync breakpoints, reload source, start debug |
| `debug.runToEnd` | 运行到结束 | run debug to termination |
| `debug.runToBreakpoint` | 下个断点 | run to next breakpoint |
| `debug.stepOver` | 本层下一句 | step over in current frame |
| `debug.stepInto` | 下一句 | step into |
| `debug.backToBreakpoint` | 上个断点 | rewind to previous breakpoint |
| `debug.stepBackOver` | 本层上一句 | rewind over called functions |
| `debug.stepBack` | 上一句 | rewind one instruction, may enter functions |

Compiler commands:

| ID | Label | Existing Behavior |
|---|---|---|
| `compiler.next` | 下一步 | advance one pipeline step |
| `compiler.nextStage` | 下一阶段 | advance to next compiler stage |
| `compiler.runToExecution` | 到执行 | advance to execution stage |
| `compiler.play` | 播放 | start playback |
| `compiler.playFast` | 2x | start fast playback |
| `compiler.pause` | 暂停 | pause playback |

Settings commands:

| ID | Behavior |
|---|---|
| `settings.theme.set` | set a named theme through `ThemeManager` |
| `settings.frameInterval.set` | set frame interval through `MiniCSettings` |
| `settings.frameInterval.increase` | increase frame interval and clamp through existing settings |
| `settings.frameInterval.decrease` | decrease frame interval and clamp through existing settings |

## Keymouse Behavior

### Hover And Pin

1. Moving over a controllable viewport updates hover target.
2. Leaving a controllable viewport clears hover target only when no descendant viewport is still under the mouse.
3. Clicking inside a controllable viewport pins that viewport.
4. Hover target overrides pinned target while the mouse is over another controllable viewport.
5. Mouse over buttons, labels, empty panes, or other `NONE` targets falls back to pinned target.

### Zoom

1. Graph zoom must be centered on the mouse position within the graph viewport.
2. Text zoom adjusts font size for the targeted text viewport.
3. Zoom must not affect unrelated visible viewports.
4. Existing AST zoom slider behavior must remain compatible; programmatic zoom and slider value should not fight each other.

### Scroll

1. Text vertical scroll affects only the targeted text viewport.
2. Graph vertical scroll affects graph vertical offset.
3. Graph horizontal scroll affects graph horizontal offset.
4. Text horizontal scroll is not required in phase one except where the existing editor already supports it safely.
5. Scroll on `NONE` target falls back to pinned target or is ignored.

### Pan

1. Graph targets support drag pan.
2. Text targets do not pan.
3. Existing AST drag-to-pan behavior must continue to work.

## Active Tracking

### Text Tracking

When a debugger/compiler command changes the active line or active range:

1. If the active line/range is fully visible, do not move the viewport.
2. If any part of the active line/range is outside the visible viewport, move quickly so the active line/range is centered.
3. If centering would go past the top or bottom, clamp to the natural top or bottom.
4. For source text, the highlighted range takes priority over the line marker.

### Graph Tracking

When a debugger/compiler command changes an active highlighted graph element:

1. Consider only highlighted `Circle` and `Rectangle` nodes in this phase.
2. If the highlighted shape is fully visible inside the graph viewport, do not move.
3. If any part of the shape is outside the graph viewport, set horizontal and vertical offsets so the shape center aligns with the viewport center.
4. Clamp offsets to valid scroll bounds.
5. This applies to AST/semantic AST graphs and debugger data-structure graphs.

## Test Strategy

Testing is the acceptance mechanism. No debug status output should be added.

### Required Test Groups

1. Target resolution tests
   - hover target beats pinned target
   - pinned target is used when mouse is over a button or empty area
   - no target produces no exception

2. Text viewport tests
   - vertical scroll changes only the targeted text viewport
   - font zoom changes only the targeted text viewport
   - active line/range fully visible does not scroll
   - active line/range partially invisible scrolls toward center
   - top/bottom clamp works

3. Graph viewport tests
   - vertical and horizontal scroll affect only targeted graph viewport
   - mouse-centered zoom preserves the visual anchor
   - active highlighted circle/rectangle fully visible does not scroll
   - active highlighted circle/rectangle partially invisible centers on viewport

4. Command registry tests
   - debugger eight commands call the expected existing behavior
   - compiler six commands call the expected existing behavior
   - every business command triggers active tracking after execution
   - disabled commands do not execute

5. Settings command tests
   - setting a theme uses `ThemeManager`
   - frame interval set/increase/decrease uses `MiniCSettings` and clamps to min/max

6. Regression tests
   - existing debugger buttons still render as two rows of four
   - existing compiler inspector buttons still render and execute
   - existing AST wheel zoom/drag pan behavior remains usable
   - source editor typing and completion are not blocked by the control layer

## Implementation Plan

### Phase 1: SPEC And Plan

**Files**

- Create `docs/superpowers/specs/2026-06-05-keymouse-control-viewport-system.md`
- Create `docs/superpowers/plans/2026-06-05-keymouse-control-viewport-system.md`

**Acceptance**

- SPEC contains target resolution, viewport operations, command table, active tracking, and test strategy.
- Plan has explicit TDD steps and subagent assignments.
- SPEC and plan are committed before production implementation.

### Phase 2: Core Control Abstractions

**Files**

- Create `src/main/java/minic/ui/control/MiniCControlTargetType.java`
- Create `src/main/java/minic/ui/control/MiniCViewportAdapter.java`
- Create `src/main/java/minic/ui/control/MiniCViewportRegistry.java`
- Create `src/main/java/minic/ui/control/MiniCControlCommand.java`
- Create `src/main/java/minic/ui/control/MiniCCommandRegistry.java`
- Test `src/test/java/minic/ui/MiniCViewportRegistryTest.java`
- Test `src/test/java/minic/ui/MiniCCommandRegistryTest.java`

**Acceptance**

- Hover/pinned priority is tested.
- Command enablement and execution are tested.
- Unsupported viewport operations are safe no-ops.

### Phase 3: Text Viewport Adapters

**Files**

- Modify `src/main/java/minic/ui/editor/MiniCCodeEditor.java`
- Modify `src/main/java/minic/ui/source/MiniCSourceLoaderView.java`
- Create `src/main/java/minic/ui/control/MiniCTextViewportAdapter.java`
- Test `src/test/java/minic/ui/MiniCCodeEditorViewportControlTest.java`

**Acceptance**

- Source editor can expose a viewport adapter.
- Text font zoom is bounded by existing font min/max.
- Active line/range visibility tests prove center-if-needed behavior.

### Phase 4: Graph Viewport Adapters

**Files**

- Create `src/main/java/minic/ui/control/MiniCGraphViewportAdapter.java`
- Modify `src/main/java/minic/ui/visual/MiniCVisualPane.java`
- Modify `src/main/java/minic/ui/debug/MiniCDebugPane.java`
- Test `src/test/java/minic/ui/MiniCGraphViewportAdapterTest.java`
- Extend `src/test/java/minic/ui/MiniCVisualPaneInspectorSourceTest.java`
- Extend `src/test/java/minic/ui/MiniCDebugPaneTest.java`

**Acceptance**

- Graph scroll and mouse-centered zoom are adapter-driven.
- Highlighted circle/rectangle center-if-needed is tested.
- Existing AST graph zoom/drag tests still pass.

### Phase 5: Business Commands

**Files**

- Create `src/main/java/minic/ui/control/MiniCWorkbenchControlHub.java`
- Modify `src/main/java/minic/ui/debug/MiniCDebugPane.java`
- Modify `src/main/java/minic/ui/panel/MiniCInspectorView.java`
- Modify `src/main/java/minic/settings/MiniCSettingsPane.java`
- Test `src/test/java/minic/ui/MiniCWorkbenchControlHubTest.java`
- Extend `src/test/java/minic/ui/MiniCDebugPaneTest.java`
- Extend `src/test/java/minic/ui/MiniCWorkbenchShellTest.java`

**Acceptance**

- Debugger buttons execute through registered commands.
- Compiler buttons execute through registered commands.
- Settings UI uses command paths for theme and interval changes.
- Every business command calls active tracking after successful execution.

### Phase 6: Active Tracking Integration

**Files**

- Create `src/main/java/minic/ui/control/MiniCActiveTrackingService.java`
- Modify `src/main/java/minic/ui/workbench/MiniCWorkbenchShell.java`
- Modify `src/main/java/minic/ui/visual/MiniCVisualPane.java`
- Modify `src/main/java/minic/ui/debug/MiniCDebugPane.java`
- Test `src/test/java/minic/ui/MiniCActiveTrackingServiceTest.java`

**Acceptance**

- Compiler pipeline active element tracking works after compiler commands.
- Debugger source active line/range tracking works after debugger commands.
- Debugger graph active node tracking works after debugger commands.
- Full test suite passes.

## Subagent Execution Standard

Subagents may be used for independent slices with disjoint write scopes.

Suggested assignments:

1. Explorer: text viewport integration points and test recommendations.
2. Explorer: graph viewport integration points and test recommendations.
3. Explorer: command registry integration points and test recommendations.
4. Worker: core control abstractions and unit tests.
5. Worker: text viewport adapter and tests.
6. Worker: graph viewport adapter and tests.
7. Worker: business command integration and active tracking tests.

Every worker must:

- Use TDD.
- Avoid reverting unrelated local changes.
- Report exact files changed.
- Run the narrow tests for its slice.
- Leave final integration and full-suite verification to the coordinating agent.

## Rework Rule

If a phase fails validation:

1. First failure: inspect error, fix within the phase, rerun the same command.
2. Second failure for the same acceptance item: simplify implementation and add one smaller test that isolates the failure.
3. Third failure for the same acceptance item: revert only the phase's own changes and redo the phase from tests.

## Final Acceptance

The feature is complete only when all are true:

1. SPEC and implementation plan are committed.
2. Core control abstractions are present under `minic.ui.control`.
3. Hover/pinned target resolution is covered by tests.
4. Text and graph viewport operations are covered by tests.
5. Debugger eight commands and compiler six commands are routed through the command abstraction.
6. Theme and frame interval commands are covered by tests.
7. Active text/graph tracking handles partially invisible targets.
8. No user-visible debug output was added.
9. `.\gradlew.bat test` succeeds.
