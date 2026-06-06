# UIWeb Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all UIWeb mirror placeholders with real TypeScript/React implementations that preserve the JavaFX UI feature set and visual styling.

**Architecture:** Keep `src/main/java/minic/uilocal` as the source of truth. Translate each JavaFX UI class into the matching `uiweb/src` file, preserving names, state fields, method semantics, CSS class names, and view structure wherever the browser platform allows. Network/runtime calls remain out of scope until the UI layer is fully real.

**Tech Stack:** TypeScript, React, Vite, existing uiapi DTO shapes, CSS translated from `src/main/resources/minic/uilocal/workbench.css` and `workbench-components.css`, local-only state.

---

### Task 1: Hard Parity Gates

**Files:**
- Modify: `uiweb/tools/verify-uiweb-mirror.mjs`
- Create: `uiweb/tools/verify-no-mirror-placeholders.mjs`
- Modify: `uiweb/package.json`

- [ ] Add a verifier that fails if any business component exports `createMirrorComponent(...)`.
- [ ] Add `npm run verify:parity` to run mirror coverage, placeholder ban, typecheck, and build.
- [ ] Verify the command fails before placeholder removal and passes only when all placeholders are replaced.
- [ ] Commit only parity-gate files.

### Task 2: JavaFX Style Translation

**Files:**
- Modify: `uiweb/src/styles/index.css`
- Create: `uiweb/src/styles/workbench.css`
- Create: `uiweb/src/styles/workbench-components.css`

- [ ] Translate JavaFX CSS classes into browser CSS classes using the same class names.
- [ ] Define CSS variables for JavaFX theme placeholders and use them consistently.
- [ ] Import translated CSS from `index.css`.
- [ ] Verify visual class names used by Java and TS share the same names.
- [ ] Commit only style files.

### Task 3: Workbench App And Shell

**Files:**
- Modify: `uiweb/src/app/MiniCWorkbenchApp.tsx`
- Modify: `uiweb/src/app/MiniCWorkbenchLauncher.ts`
- Modify: `uiweb/src/workbench/MiniCWorkbenchShell.tsx`

- [ ] Replace shell/app mirror components with real React components.
- [ ] Implement activity bar, workbench body, sidebar, editor area, source/visual/debug/info/settings sections, tabs, status bar, document switching, close, rename, reorder, and local persistence.
- [ ] Preserve Java method names as exported helpers or component methods where practical.
- [ ] Verify no mock runtime data is introduced.
- [ ] Commit only app/workbench shell files.

### Task 4: Source Loader And Editor Integration

**Files:**
- Modify: `uiweb/src/source/MiniCSourceLoaderView.tsx`
- Modify: `uiweb/src/source/MiniCSourceView.tsx`
- Modify: `uiweb/src/editor/MiniCCodeEditor.tsx`

- [ ] Replace mirror loader with real controls and editor wiring.
- [ ] Implement browser-real open/save/save-as using file input/download where supported, without network calls.
- [ ] Wire breakpoints, current execution line/range, persistent scrollbars, viewport adapter, and realtime submission.
- [ ] Commit only source/editor integration files.

### Task 5: Inspector And Bottom Panel

**Files:**
- Modify: `uiweb/src/panel/MiniCBottomPanel.tsx`
- Modify: `uiweb/src/panel/MiniCInspectorView.tsx`
- Modify: `uiweb/src/panel/MiniCHoverInspector.ts`
- Modify: `uiweb/src/diagnostics/MiniCDiagnosticSelection.ts`

- [ ] Replace mirror panel/inspector with real split content, resize handle, collapse/expand, source-range centering, hover source view, explanation text, and command buttons.
- [ ] Preserve property/listener semantics with explicit read-only property wrappers instead of raw values where needed.
- [ ] Commit only panel/diagnostic files.

### Task 6: Visual Views

**Files:**
- Modify: `uiweb/src/visual/MiniCVisualPane.tsx`
- Modify: `uiweb/src/visual/MiniCVisualAstGraphRenderer.tsx`
- Modify: `uiweb/src/visual/MiniCVisualSourceRows.tsx`
- Modify: `uiweb/src/visual/MiniCVisualExplanationFormatter.ts`

- [ ] Replace visual placeholders with real stage columns, lexer/source/AST/semantic/IR/assembly views.
- [ ] Implement AST graph SVG renderer with zoom, active node highlighting, scope masks, and inspector click data.
- [ ] Commit only visual files.

### Task 7: Debug Views

**Files:**
- Modify: `uiweb/src/debug/MiniCDebugPane.tsx`
- Modify: `uiweb/src/debug/MiniCDebugAstGraphRenderer.tsx`
- Modify: `uiweb/src/debug/MiniCDebugVisualDiagramRenderer.tsx`
- Modify: `uiweb/src/debug/MiniCDebugTextFormatter.ts`
- Modify: `uiweb/src/debug/MiniCDebugViewportController.ts`

- [ ] Replace debug placeholders with real controls, view selector, metadata/data/source/IR/ASM/AST/visual panels.
- [ ] Implement text formatter public entrypoints and diagram renderers using uiapi DTOs only.
- [ ] Commit only debug files.

### Task 8: Info And Markdown

**Files:**
- Modify: `uiweb/src/info/MiniCInfoView.tsx`
- Modify: `uiweb/src/info/MiniCMarkdownRenderer.tsx`
- Modify: `uiweb/src/info/MiniCGuideDocument.ts`

- [ ] Replace info placeholders with real guide loading and markdown rendering.
- [ ] Preserve headings, lists, inline code, strong text, code block syntax highlighting, and guide variable substitution.
- [ ] Commit only info files.

### Task 9: Full Verification

**Files:**
- Modify as needed only for failing parity tests.

- [ ] Run `npm run verify:parity`.
- [ ] Run Gradle Java compile/test commands from the repository root.
- [ ] Run a browser screenshot pass against the local Vite app.
- [ ] Confirm there are no `createMirrorComponent(...)` business exports and no network calls.
- [ ] Commit verification fixes.
