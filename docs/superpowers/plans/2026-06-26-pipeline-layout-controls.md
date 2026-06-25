# Pipeline Layout Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a persistent, dockable compiler control module, collapsible pipeline/metadata sidebars, and a reversible two-column workspace tab split.

**Architecture:** Keep the JavaFX workbench shell as the layout coordinator, but extract compiler controls from the metadata inspector into a reusable component. Persist global layout state in `MiniCSettings`; mirror the same state and behavior in UIWeb.

**Tech Stack:** Java 21, JavaFX, JUnit 5/assertj, TypeScript/React, existing UIWeb mirror verification scripts.

## Global Constraints

- Floating compiler controls are constrained to the central workspace split, the common parent of before and after workspace groups.
- Floating coordinates are persisted relative to that workspace split's top-left corner.
- The workspace has at most two tab groups: left and right.
- If the right group becomes empty, it disappears.
- If the left group becomes empty, all right-group tabs move back to the left group.
- Routine pipeline progress must not rebuild the whole workbench or workspace split unless tab layout actually changes.
- All settings are global and persisted in `config/settings.json`.

---

## File Structure

- Modify `src/main/java/minic/settings/MiniCSettings.java`: add persisted layout fields and floating-rect helpers.
- Modify `src/test/java/minic/uilocal/MiniCSettingsInteractionRegressionTest.java`: cover settings persistence.
- Create `src/main/java/minic/uilocal/panel/MiniCCompilerControlsView.java`: reusable compiler controls component.
- Modify `src/main/java/minic/uilocal/panel/MiniCInspectorView.java`: metadata-only inspector.
- Modify `src/main/java/minic/uilocal/workbench/MiniCWorkbenchShell.java`: layout orchestration, docking, floating, collapsible sidebars, two-group tab model.
- Modify `src/test/java/minic/uilocal/MiniCWorkbenchFileSessionRegressionTest.java`: layout behavior regressions.
- Modify `src/main/resources/minic/uilocal/workbench.css` and `workbench-components.css`: layout rail, dock, floating, and tab action styles.
- Modify `uiweb/src/settings/MiniCSettings.ts`: mirror persisted state.
- Create `uiweb/src/panel/MiniCCompilerControlsView.tsx`: reusable controls component.
- Modify `uiweb/src/panel/MiniCInspectorView.tsx`: metadata-only inspector.
- Modify `uiweb/src/workbench/MiniCWorkbenchShell.tsx`: mirror shell layout and tab behavior.
- Modify `uiweb/tools/verify-uiweb-runtime-workflows.mjs`: verify new layout markers and tab movement.

---

### Task 1: Persist Pipeline Layout State

**Files:**
- Modify: `src/main/java/minic/settings/MiniCSettings.java`
- Modify: `src/test/java/minic/uilocal/MiniCSettingsInteractionRegressionTest.java`
- Modify: `uiweb/src/settings/MiniCSettings.ts`

**Interfaces:**
- Produces Java methods:
  - `pipelineLeftSidebarCollapsed(): boolean`
  - `setPipelineLeftSidebarCollapsed(boolean collapsed): void`
  - `pipelineRightSidebarCollapsed(): boolean`
  - `setPipelineRightSidebarCollapsed(boolean collapsed): void`
  - `compilerControlsDock(): String`
  - `setCompilerControlsDock(String dock): void`
  - `compilerControlsFloatingRect(): MiniCSettings.FloatingRect`
  - `setCompilerControlsFloatingRect(MiniCSettings.FloatingRect rect): void`
  - `record FloatingRect(double x, double y, double width, double height)`
- Produces UIWeb equivalents on `MiniCSettings`.

- [ ] **Step 1: Write failing settings tests**

Add assertions that write a settings file with the new fields, call `MiniCSettings.load()`, and verify getters return the stored values. Add assertions that setters persist the same keys as strings/numbers.

- [ ] **Step 2: Run failing tests**

Run: `.\gradlew.bat test --tests minic.uilocal.MiniCSettingsInteractionRegressionTest`

Expected: FAIL because the new getters do not exist.

- [ ] **Step 3: Implement Java settings**

Add defaults:

```java
private static final boolean DEFAULT_PIPELINE_LEFT_SIDEBAR_COLLAPSED = false;
private static final boolean DEFAULT_PIPELINE_RIGHT_SIDEBAR_COLLAPSED = false;
private static final String DEFAULT_COMPILER_CONTROLS_DOCK = "RIGHT_METADATA_TOP";
private static final FloatingRect DEFAULT_COMPILER_CONTROLS_FLOATING_RECT =
        new FloatingRect(24, 24, 320, 120);
```

Store the floating rect as four scalar keys:

```java
compilerControlsFloatingX
compilerControlsFloatingY
compilerControlsFloatingWidth
compilerControlsFloatingHeight
```

- [ ] **Step 4: Implement UIWeb settings mirror**

Add the same fields and defaults in `uiweb/src/settings/MiniCSettings.ts`.

- [ ] **Step 5: Verify**

Run:

```powershell
.\gradlew.bat test --tests minic.uilocal.MiniCSettingsInteractionRegressionTest
cd uiweb; npm run typecheck
```

Expected: PASS.

---

### Task 2: Extract Compiler Controls From Inspector

**Files:**
- Create: `src/main/java/minic/uilocal/panel/MiniCCompilerControlsView.java`
- Modify: `src/main/java/minic/uilocal/panel/MiniCInspectorView.java`
- Create: `uiweb/src/panel/MiniCCompilerControlsView.tsx`
- Modify: `uiweb/src/panel/MiniCInspectorView.tsx`

**Interfaces:**
- Produces Java constructor:
  - `MiniCCompilerControlsView(MiniCWorkbenchViewModel viewModel, MiniCWorkbenchControlHub controlHub)`
- Produces UIWeb component:
  - `MiniCCompilerControlsView({ viewModel, playbackController }: MiniCCompilerControlsViewProps)`

- [ ] **Step 1: Write failing inspector/control tests**

Add a JavaFX regression that creates `MiniCInspectorView` and verifies it no longer contains control buttons, then creates `MiniCCompilerControlsView` and verifies it contains the six compiler controls.

- [ ] **Step 2: Run failing test**

Run: `.\gradlew.bat test --tests minic.uilocal.MiniCWorkbenchFileSessionRegressionTest`

Expected: FAIL because `MiniCCompilerControlsView` does not exist.

- [ ] **Step 3: Implement Java controls component**

Move button creation, `registerCompilerCommands`, and button enable refresh from `MiniCInspectorView` into `MiniCCompilerControlsView`. Keep `MiniCInspectorView` responsible only for title, current state, current item, and accumulated output.

- [ ] **Step 4: Implement UIWeb controls component**

Move exported `controls()` rendering into `MiniCCompilerControlsView.tsx`. Keep `MiniCInspectorView.tsx` metadata-only.

- [ ] **Step 5: Verify**

Run:

```powershell
.\gradlew.bat test --tests minic.uilocal.MiniCWorkbenchFileSessionRegressionTest
cd uiweb; npm run typecheck
```

Expected: PASS.

---

### Task 3: JavaFX Docking, Floating, And Sidebar Collapse

**Files:**
- Modify: `src/main/java/minic/uilocal/workbench/MiniCWorkbenchShell.java`
- Modify: `src/test/java/minic/uilocal/MiniCWorkbenchFileSessionRegressionTest.java`
- Modify: `src/main/resources/minic/uilocal/workbench.css`
- Modify: `src/main/resources/minic/uilocal/workbench-components.css`

**Interfaces:**
- Produces shell methods for tests:
  - `setCompilerControlsDockForTesting(String dock): void`
  - `setPipelineLeftSidebarCollapsedForTesting(boolean collapsed): void`
  - `setPipelineRightSidebarCollapsedForTesting(boolean collapsed): void`
  - `setCompilerControlsFloatingRectForTesting(double x, double y, double width, double height): void`

- [ ] **Step 1: Write failing layout tests**

Add tests for:

- right dock puts controls in the right metadata column
- left dock puts controls below the pipeline list
- floating dock renders controls above workspace split content
- collapsing left/right sidebars keeps pipeline state non-null
- floating rect clamps inside the workspace split bounds

- [ ] **Step 2: Run failing tests**

Run: `.\gradlew.bat test --tests minic.uilocal.MiniCWorkbenchFileSessionRegressionTest`

Expected: FAIL because layout testing hooks and control docks do not exist.

- [ ] **Step 3: Implement shell layout state**

Add shell fields loaded from `MiniCSettings`, rebuild left and right sidebars based on collapse state, and persist changes when toggled.

- [ ] **Step 4: Implement dock rendering**

Render one `MiniCCompilerControlsView` in the selected dock:

- right metadata top
- left pipeline bottom
- floating overlay inside a workspace host whose coordinate system matches the workspace split

- [ ] **Step 5: Implement floating drag/resize**

Attach mouse handlers to update `MiniCSettings.setCompilerControlsFloatingRect(...)`; clamp rendered coordinates to the workspace host's bounds.

- [ ] **Step 6: Verify**

Run: `.\gradlew.bat test --tests minic.uilocal.MiniCWorkbenchFileSessionRegressionTest`

Expected: PASS.

---

### Task 4: Reversible Two-Group Workspace Tabs

**Files:**
- Modify: `src/main/java/minic/uilocal/workbench/MiniCWorkbenchShell.java`
- Modify: `src/test/java/minic/uilocal/MiniCWorkbenchFileSessionRegressionTest.java`
- Modify: `src/main/resources/minic/uilocal/workbench.css`

**Interfaces:**
- Produces shell method:
  - `moveWorkspaceTabLeft(String id): void`
- Keeps existing:
  - `splitWorkspaceTabRight(String id): void`

- [ ] **Step 1: Write failing tab tests**

Add tests for:

- splitting a left tab moves it right
- moving a right tab left removes it from right ids
- right group disappears when empty
- if the active left tab is moved/closed and no left tabs remain, all right tabs move left

- [ ] **Step 2: Run failing tests**

Run: `.\gradlew.bat test --tests minic.uilocal.MiniCWorkbenchFileSessionRegressionTest`

Expected: FAIL because move-left and empty-left reflow are missing.

- [ ] **Step 3: Implement tab movement**

Add move-left action and a `normalizeWorkspaceGroups()` helper that removes invalid right ids, collapses empty right group, and moves all right tabs left when the left group is empty.

- [ ] **Step 4: Add tab UI actions**

Show a right arrow for left-group tabs and a left arrow for right-group tabs. Keep close behavior unchanged.

- [ ] **Step 5: Verify**

Run: `.\gradlew.bat test --tests minic.uilocal.MiniCWorkbenchFileSessionRegressionTest`

Expected: PASS.

---

### Task 5: UIWeb Mirror And Runtime Verification

**Files:**
- Modify: `uiweb/src/workbench/MiniCWorkbenchShell.tsx`
- Modify: `uiweb/src/panel/index.ts`
- Modify: `uiweb/src/styles/workbench.css`
- Modify: `uiweb/src/styles/workbench-components.css`
- Modify: `uiweb/tools/verify-uiweb-runtime-workflows.mjs`

**Interfaces:**
- Mirrors JavaFX:
  - compiler control dock modes
  - floating rect relative to `.workspace-split`
  - left/right sidebar collapse state
  - split-right and move-left tab actions

- [ ] **Step 1: Write failing verifier expectations**

Update `verify-uiweb-runtime-workflows.mjs` to expect runtime patterns for the new controls component, dock states, floating rect, move-left action, and empty-left reflow.

- [ ] **Step 2: Run failing verifier**

Run: `cd uiweb; npm run verify:runtime-workflows`

Expected: FAIL because the mirror behavior is not implemented.

- [ ] **Step 3: Implement UIWeb shell layout**

Mirror the JavaFX layout state and render paths. Avoid adding a global render counter for routine pipeline state changes.

- [ ] **Step 4: Implement styles**

Add styles for collapsed rails, docked controls, floating controls, and left/right tab movement buttons.

- [ ] **Step 5: Verify**

Run:

```powershell
cd uiweb
npm run typecheck
npm run verify:runtime-workflows
npm run verify:strict-final
```

Expected: PASS.

---

## Final Verification

Run from repo root:

```powershell
.\gradlew.bat test
cd uiweb
npm run verify:strict-final
```

Then restart the JavaFX app with:

```powershell
.\gradlew.bat runUi
```
