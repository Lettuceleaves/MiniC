# UIWeb Strict UIAPI Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `uiweb` and `uilocal` strictly equivalent UI shells, with all compiler, realtime-analysis, and debugger behavior provided by `uiapi` and no mock, fallback, placeholder, degraded, or omitted functionality.

**Architecture:** `src/main/java/minic/uilocal` remains JavaFX UI only. `src/main/java/minic/uiapi` owns all behavior needed by UI clients and exposes it through Java facades plus a local HTTP service for browser clients. `uiweb` owns React/TypeScript rendering and interaction only; every pipeline, debugger, realtime-analysis, tokenization, and view-model data transition must flow through `uiapi` adapters.

**Tech Stack:** Java 21, JavaFX/RichTextFX, `MiniCObservationApi`, `MiniCDebugApi`, `MiniCRealtimeAnalysisApi`, JDK `HttpServer`, Jackson JSON serialization, TypeScript, React, Vite, browser automation for screenshots and runtime checks.

---

## Non-Negotiable Definition Of Done

The work is not complete unless every item below is true:

- `uilocal` has zero direct references to `compiler`, `runtime`, `source`, `session`, or `diagnostics` internals, except through `uiapi` DTOs and facade classes.
- `uiweb` has zero local compiler, parser, semantic, debugger, or runtime emulation. In particular, TS lexer/analyzer implementations are removed or converted into pure transport wrappers.
- `uiweb` starts with real `MiniCObservationApi`, `MiniCDebugApi`, and `MiniCRealtimeAnalysisApi` adapters injected into every `MiniCWorkbenchViewModel`.
- No production UIWeb code contains disconnected messages such as `UIWeb 尚未连接`, `noApiResult`, runtime `mock`, runtime `stub`, runtime `dummy`, `TODO`, `as any`, `@ts-ignore`, or `@ts-expect-error`.
- Every public method on `MiniCObservationApi`, `MiniCDebugApi`, and `MiniCRealtimeAnalysisApi` is represented by the matching TS adapter method, with only explicitly documented name aliases allowed.
- Every JavaFX `uilocal` file has a UIWeb file with current imports, fields, and method mirror metadata; stale mirror metadata fails verification.
- Source, pipeline, visualizer, debugger, settings, info/about, bottom panel, hover inspector, keybindings, and viewport controls pass automated runtime checks.
- Screenshot verification covers all main pages and debugger subpages, not only the source editor.
- Java and Web behavior are checked using the same sample programs and the same `uiapi` DTO snapshots.
- Every implementation phase is committed separately after its verification commands pass.

---

## Current Known Failures To Eliminate

- `uiweb/src/workbench/MiniCWorkbenchShell.tsx` creates `MiniCWorkbenchViewModel` without API adapters, so pipeline/debugger controls are still disconnected.
- `MiniCObservationApiAdapter` is missing `previous`, `reversePlay`, and explicit stage visual methods `lexerVisualData`, `astVisualData`, `semanticVisualData`, `codegenVisualData`.
- `MiniCDebugApiAdapter` is missing `backToCallSite`; current `debugBackToCallSite()` delegates to `debugStepBackOver()`, which is a behavior downgrade.
- `MiniCRealtimeAnalyzer.ts` still implements local TS lexing instead of calling `MiniCRealtimeAnalysisApi`.
- UIWeb mirror metadata is stale for at least `MiniCRealtimeAnalyzer` and `MiniCBottomPanel`.
- Existing parity scripts prove file existence and no exported mirror placeholders, but they do not prove method/field/import parity, API completeness, real runtime wiring, or screenshot coverage.

---

## Task 1: Strict Verification Gates First

**Files:**
- Create: `uiweb/tools/verify-uiweb-mirror-signatures.mjs`
- Create: `uiweb/tools/verify-uiapi-adapter-completeness.mjs`
- Create: `uiweb/tools/verify-no-uiweb-runtime-downgrade.mjs`
- Modify: `uiweb/package.json`
- Modify: `src/test/java/minic/uilocal/MiniCUiLocalBoundaryRegressionTest.java`

- [ ] Add `verify-uiweb-mirror-signatures.mjs`.

Acceptance:
- It parses every `src/main/java/minic/uilocal/**/*.java`.
- It locates the matching `uiweb/src/**/*.{ts,tsx}` file.
- It compares mirror `imports`, `fields.name`, and `methods.name/signature` against the Java source.
- It fails on stale imports such as `minic.compiler.lexer.Lexer` in a mirror file when Java no longer imports it.
- It fails if Java has a field/method missing from UIWeb mirror metadata.
- It prints exact file and missing/stale item.

- [ ] Add `verify-uiapi-adapter-completeness.mjs`.

Acceptance:
- It parses public Java methods from `MiniCObservationApi`, `MiniCDebugApi`, and `MiniCRealtimeAnalysisApi`.
- It parses TS adapter interfaces from `MiniCWorkbenchViewModel.ts` and any new adapter files.
- It allows only these aliases: `currentState -> state`, `astDebugView -> astView`, `irDebugView -> irView`, `asmDebugView -> asmView`, `dataStructureDebugView -> dataStructureView`.
- It fails until `previous`, `reversePlay`, `backToCallSite`, `analyze`, and `tokenize` are represented.
- It fails if a Java API method is silently collapsed into a generic method unless the Java API itself has that generic method.

- [ ] Add `verify-no-uiweb-runtime-downgrade.mjs`.

Acceptance:
- It scans `uiweb/src`.
- It fails on `UIWeb 尚未连接`, `noApiResult`, `mock`, `stub`, `dummy`, `TODO`, `@ts-ignore`, `@ts-expect-error`, `as any`, and local compiler/debugger class names.
- It allows legacy CSS class names only if they are already present in JavaFX CSS and are not used as incomplete feature pages.
- It fails on `new MiniCRealtimeAnalyzer(` unless that class is a transport scheduler that calls `MiniCRealtimeAnalysisApi`.

- [ ] Wire all gates into `npm run verify:strict-parity`.

Required command:

```powershell
cd E:\projects\MiniC\uiweb
npm run verify:strict-parity
```

Expected before implementation:
- Fails with the known gaps listed above.

Expected after implementation:
- Passes with zero allowlisted runtime downgrades.

- [ ] Commit Task 1 only.

Commit message:

```powershell
git add uiweb/tools uiweb/package.json src/test/java/minic/uilocal/MiniCUiLocalBoundaryRegressionTest.java
git commit -m "test(uiweb): add strict parity gates"
```

---

## Task 2: UIAPI HTTP Service With Real DTO Transport

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/minic/uiapi/web/MiniCUiApiServer.java`
- Create: `src/main/java/minic/uiapi/web/MiniCUiApiRouter.java`
- Create: `src/main/java/minic/uiapi/web/MiniCUiApiJson.java`
- Create: `src/main/java/minic/uiapi/web/MiniCUiApiSessionStore.java`
- Create: `src/main/java/minic/uiapi/web/MiniCUiApiServerLauncher.java`
- Create: `src/test/java/minic/uiapi/MiniCUiApiWebRegressionTest.java`

- [ ] Add Jackson dependencies instead of hand-written JSON.

Acceptance:
- `build.gradle` includes Jackson databind for record DTO serialization.
- No production code builds JSON with string concatenation except trivial health text.
- DTO snapshots from Java facades serialize and deserialize without losing nested lists, source ranges, debug snapshots, visual structures, or diagnostics.

- [ ] Implement service endpoints.

Required endpoints:
- `GET /api/health`
- `POST /api/realtime/analyze`
- `POST /api/realtime/tokenize`
- `POST /api/observation/sessions`
- `POST /api/observation/{id}/source`
- `POST /api/observation/{id}/start`
- `POST /api/observation/{id}/next`
- `POST /api/observation/{id}/next-stage`
- `POST /api/observation/{id}/play`
- `POST /api/observation/{id}/play-fast`
- `POST /api/observation/{id}/tick`
- `POST /api/observation/{id}/pause`
- `POST /api/observation/{id}/confirm-input`
- `POST /api/observation/{id}/previous`
- `POST /api/observation/{id}/reverse-play`
- `GET /api/observation/{id}/state`
- `GET /api/observation/{id}/stage-data`
- `GET /api/observation/{id}/visual/current`
- `GET /api/observation/{id}/visual/lexer`
- `GET /api/observation/{id}/visual/ast`
- `GET /api/observation/{id}/visual/semantic`
- `GET /api/observation/{id}/visual/codegen`
- `GET /api/observation/{id}/global`
- `POST /api/debug/sessions`
- `POST /api/debug/{id}/source`
- `POST /api/debug/{id}/start`
- `POST /api/debug/{id}/breakpoints/{line}`
- `DELETE /api/debug/{id}/breakpoints/{line}`
- `POST /api/debug/{id}/run-to-breakpoint`
- `POST /api/debug/{id}/run-to-end`
- `POST /api/debug/{id}/fast-forward`
- `POST /api/debug/{id}/step-over`
- `POST /api/debug/{id}/step-into`
- `POST /api/debug/{id}/step-out`
- `POST /api/debug/{id}/pause`
- `POST /api/debug/{id}/restart`
- `POST /api/debug/{id}/close`
- `POST /api/debug/{id}/step-back`
- `POST /api/debug/{id}/step-back-over`
- `POST /api/debug/{id}/back-to-breakpoint`
- `POST /api/debug/{id}/back-to-call-site`
- `GET /api/debug/{id}/state`
- `GET /api/debug/{id}/metadata`
- `GET /api/debug/{id}/data-structure`
- `GET /api/debug/{id}/ast`
- `GET /api/debug/{id}/ir`
- `GET /api/debug/{id}/asm`

Acceptance:
- Every endpoint calls the matching `uiapi` facade method.
- No endpoint returns fixture data.
- Errors return structured error JSON with HTTP status, method, path, and message.
- Session ids are opaque and scoped to the process.
- Sessions are cleaned up after `close` or after a documented idle timeout.

- [ ] Add Java regression tests.

Required tests:
- Realtime analyze/tokenize returns the same DTO content as direct `MiniCRealtimeAnalysisApi`.
- Observation endpoint sequence returns the same `currentState`, `currentStageData`, visual DTO, and global DTO as direct `MiniCObservationApi`.
- Debug endpoint sequence returns the same state, metadata, AST, IR, ASM, and data-structure DTOs as direct `MiniCDebugApi`.
- `back-to-call-site` calls `MiniCDebugApi.backToCallSite`, not `stepBackOver`.
- Missing session and invalid source errors are explicit, not swallowed.

Required command:

```powershell
cd E:\projects\MiniC
.\gradlew.bat test --tests minic.uiapi.MiniCUiApiWebRegressionTest
```

Expected:
- All tests pass.

- [ ] Commit Task 2 only.

Commit message:

```powershell
git add build.gradle src/main/java/minic/uiapi/web src/test/java/minic/uiapi/MiniCUiApiWebRegressionTest.java
git commit -m "feat(uiapi): expose ui facades over http"
```

---

## Task 3: Complete TS API Adapters And Remove Disconnected State

**Files:**
- Create: `uiweb/src/api/MiniCUiApiClient.ts`
- Create: `uiweb/src/api/MiniCObservationHttpAdapter.ts`
- Create: `uiweb/src/api/MiniCDebugHttpAdapter.ts`
- Create: `uiweb/src/api/MiniCRealtimeAnalysisHttpAdapter.ts`
- Modify: `uiweb/src/workbench/MiniCWorkbenchViewModel.ts`
- Modify: `uiweb/src/workbench/MiniCWorkbenchShell.tsx`
- Modify: `uiweb/src/editor/MiniCRealtimeAnalyzer.ts`
- Modify: `uiweb/src/text/MiniCSourceTextHighlighter.ts`
- Modify: `uiweb/src/panel/MiniCBottomPanel.tsx`

- [ ] Implement a typed HTTP client.

Acceptance:
- All requests go through one client with base URL configuration.
- It throws typed errors for non-2xx responses.
- It never fabricates success DTOs.
- It never silently returns `null` for failed API calls.

- [ ] Implement complete adapters.

Acceptance:
- `MiniCObservationHttpAdapter` exposes every Java `MiniCObservationApi` method, including `previous`, `reversePlay`, and explicit visual methods.
- `MiniCDebugHttpAdapter` exposes every Java `MiniCDebugApi` method, including `backToCallSite`.
- `MiniCRealtimeAnalysisHttpAdapter` exposes `analyze` and `tokenize`.
- Adapter completeness script passes.

- [ ] Inject adapters into every `MiniCWorkbenchViewModel`.

Acceptance:
- `new MiniCWorkbenchViewModel(...)` in UIWeb is replaced by a factory that always supplies real adapters.
- There is no production path where `observationApi` or `debugApi` is `null`.
- `UIWeb 尚未连接` and `noApiResult` are removed from production source.
- `debugBackToCallSite()` calls `debugApi.backToCallSite()`.

- [ ] Remove local TS compiler/realtime emulation.

Acceptance:
- `MiniCRealtimeAnalyzer.ts` becomes only a browser-side scheduler/transport wrapper around `MiniCRealtimeAnalysisHttpAdapter.analyze`.
- Local TS lexer/parser/semantic code is removed.
- Source text highlighters use token DTOs from `MiniCRealtimeAnalysisApi.tokenize`.
- The downgrade verifier fails if local tokenization reappears.

Required commands:

```powershell
cd E:\projects\MiniC\uiweb
npm run verify:strict-parity
npm run typecheck
```

Expected:
- Both pass.

- [ ] Commit Task 3 only.

Commit message:

```powershell
git add uiweb/src/api uiweb/src/workbench uiweb/src/editor uiweb/src/text uiweb/src/panel
git commit -m "feat(uiweb): connect workbench to uiapi adapters"
```

---

## Task 4: Method, Field, And DTO Snapshot Parity

**Files:**
- Create: `uiweb/tools/verify-uiapi-snapshot-parity.mjs`
- Create: `src/test/java/minic/uiapi/MiniCUiApiSnapshotFixtureWriter.java`
- Create: `src/test/java/minic/uiapi/MiniCUiApiSnapshotParityTest.java`
- Modify: `uiweb/package.json`

- [ ] Generate canonical DTO snapshots from Java `uiapi`.

Required scenarios:
- Realtime analysis for valid source, preprocessing source, and diagnostic source.
- Observation source stage.
- Observation lexer stage.
- Observation parser/AST stage.
- Observation semantic stage.
- Observation IR stage.
- Observation codegen/ASM stage.
- Observation execution/input stage when available.
- Debug initial state.
- Debug breakpoint stop.
- Debug step over.
- Debug step into/out if supported by sample.
- Debug step back.
- Debug back to breakpoint.
- Debug back to call site.
- Debug run to end.
- Debug metadata, data structure, AST, IR, ASM views.

Acceptance:
- Snapshot writer uses real sample programs and real `uiapi`, never static fixtures.
- Snapshot JSON is deterministic.
- TS DTO types parse every snapshot without `any`.
- UIWeb renderers consume these snapshots without throwing.

- [ ] Add snapshot parity verification.

Required command:

```powershell
cd E:\projects\MiniC
.\gradlew.bat test --tests minic.uiapi.MiniCUiApiSnapshotParityTest
cd E:\projects\MiniC\uiweb
npm run verify:snapshot-parity
```

Expected:
- Both pass.
- A missing DTO field, renamed field, or dropped nested debug visual structure fails.

- [ ] Commit Task 4 only.

Commit message:

```powershell
git add src/test/java/minic/uiapi uiweb/tools uiweb/package.json
git commit -m "test(uiweb): verify uiapi dto snapshot parity"
```

---

## Task 5: Runtime Workflow End-To-End Tests

**Files:**
- Create: `uiweb/tools/run-uiapi-server.mjs`
- Create: `uiweb/tools/verify-uiweb-runtime-workflows.mjs`
- Modify: `uiweb/package.json`

- [x] Add a script that starts the Java UIAPI HTTP service and Vite app together.

Acceptance:
- It fails if the UIAPI server is not healthy.
- It fails if Vite is not serving UIWeb.
- It tears both processes down reliably.

- [x] Add workflow tests.

Required workflows:
- Open source page, edit source, verify realtime diagnostics/tokens arrive from `/api/realtime/analyze`.
- Start pipeline, click `next`, `nextStage`, `play`, `tick`, `pause`, and verify stage/status/global data changes match HTTP DTOs.
- Visit each pipeline stage visual view and verify non-empty source/lexer/AST/semantic/IR/ASM content when the stage is available.
- Start debugger, set breakpoint, run to breakpoint, step over, step back, back to breakpoint, back to call site, run to end.
- Verify debugger subpages: metadata, data structure, AST, IR, ASM, source, visual diagram.
- Open settings page and verify every JavaFX settings control exists and mutates the same setting key.
- Open info/about page and verify guide sections render, including code block highlighting through `uiapi` tokenization.
- Save/open/new/rename/close document flows must not lose source text or adapter session state.

Acceptance:
- Tests inspect actual DOM and actual network calls.
- Tests fail if any workflow returns disconnected, null, fallback, mock, or empty DTO data.
- Tests fail if debug `backToCallSite` calls `stepBackOver` or returns identical result when Java direct API differs.

Required command:

```powershell
cd E:\projects\MiniC\uiweb
npm run verify:runtime-workflows
```

Expected:
- All workflows pass.

- [x] Commit Task 5 only.

Commit message:

```powershell
git add uiweb/tools uiweb/package.json
git commit -m "test(uiweb): cover runtime uiapi workflows"
```

---

## Task 6: Full Screenshot And Layout Parity

**Files:**
- Create: `uiweb/tools/capture-uiweb-screenshots.mjs`
- Create: `uiweb/tools/capture-uilocal-screenshots.ps1`
- Create: `uiweb/tools/verify-screenshot-parity.mjs`
- Create: `docs/uiweb-parity/screenshot-matrix.md`
- Create: `src/test/java/minic/uilocal/MiniCUiLocalScreenshotCapture.java`
- Modify: `build.gradle`
- Modify: `uiweb/package.json`

- [x] Define the screenshot matrix.

Required pages/states:
- Workbench source/editor initial state.
- Source editor with long file, scrollbars, line numbers, breakpoints, current execution marker.
- Pipeline before start.
- Pipeline after start.
- Pipeline at every supported stage: source, preprocess, lexer, parser, semantic, IR, codegen/ASM, toolchain/execution when available.
- Visual pane with lexer overlay.
- Visual pane with AST tree/graph.
- Visual pane with semantic scope tree.
- Visual pane with IR and ASM views.
- Debugger before start.
- Debugger after start, metadata selected.
- Debugger source selected with breakpoint and current line.
- Debugger data structure selected.
- Debugger AST selected.
- Debugger IR selected.
- Debugger ASM selected.
- Debugger visual diagram selected.
- Settings page.
- Info/about page.
- Bottom hover inspector expanded.
- Bottom hover inspector collapsed.

- [x] Capture JavaFX and UIWeb screenshots from the same sources and viewport sizes.

Viewport sizes:
- `1920x1080`
- `1366x768`
- `390x844`

Acceptance:
- Each state has both a JavaFX reference image and a UIWeb image.
- Missing screenshot is a failure.
- Blank panel is a failure.
- Overlapping text is a failure.
- Missing scrollbar, line number, breakpoint marker, status bar, tab bar, sidebar icon, or right-side debugger panel is a failure.

- [x] Add layout metric comparison.

Acceptance:
- Key bounding boxes differ by no more than 2 px: activity bar, sidebar, editor, pipeline right panel, debugger toolbar, debugger side tabs, status bar, bottom panel.
- CSS color tokens match JavaFX theme values exactly.
- Font family, font size, and line height match declared JavaFX settings.
- Pixel diff may ignore anti-aliasing noise only; structural differences, missing text, hidden panels, or wrong colors are not allowed.

Required command:

```powershell
cd E:\projects\MiniC\uiweb
npm run verify:screenshots
```

Expected:
- All states pass with generated report at `uiweb-render-check/parity-report/index.html`.

- [x] Commit Task 6 only.

Commit message:

```powershell
git add uiweb/tools uiweb/package.json docs/uiweb-parity/screenshot-matrix.md
git commit -m "test(uiweb): add full screenshot parity matrix"
```

---

## Task 7: Final Strict Verification Pipeline

**Files:**
- Modify: `uiweb/package.json`
- Modify: `docs/uiweb-parity/strict-acceptance.md`

- [x] Create one command for final acceptance.

Required command:

```powershell
cd E:\projects\MiniC
.\gradlew.bat test
cd E:\projects\MiniC\uiweb
npm run verify:strict-final
cd E:\projects\MiniC
git diff --check
```

`npm run verify:strict-final` must run:
- `verify:mirror`
- `verify:mirror-signatures`
- `verify:placeholders`
- `verify:no-runtime-downgrade`
- `verify:adapter-completeness`
- `verify:editor-scroll`
- `verify:snapshot-parity`
- `verify:runtime-workflows`
- `verify:screenshots`
- `typecheck`
- `build`

Acceptance:
- A single missing API method fails.
- A single disconnected fallback string fails.
- A single stale mirror import/method/field fails.
- A single unverified debugger subpage fails.
- A single blank pipeline stage fails.
- A single local TS compiler/debugger emulation path fails.

- [x] Run the final acceptance pipeline.

Expected:
- All commands pass.
- The report includes exact command output and screenshot matrix results.

- [x] Commit Task 7 only.

Commit message:

```powershell
git add uiweb/package.json docs/uiweb-parity/strict-acceptance.md
git commit -m "test(uiweb): enforce strict final parity acceptance"
```

---

## Required Manual Review After Automation

Manual review is only allowed after all automated gates pass. It cannot waive failures.

Review checklist:
- Compare JavaFX and UIWeb source/editor pages at all viewport sizes.
- Compare pipeline right panel and every stage visualization.
- Compare debugger toolbar, source view, metadata, data structure, AST, IR, ASM, and visual diagram pages.
- Compare settings and info/about pages.
- Confirm all buttons that exist in JavaFX exist in UIWeb and invoke the same API method.
- Confirm disabled states match JavaFX.
- Confirm no user-facing text claims unsupported or disconnected behavior.

Manual acceptance can only record observations. It cannot replace failing automation.

---

## Final Completion Statement Template

The implementation may only be reported complete with evidence in this shape:

```text
Strict parity complete.

Verified:
- .\gradlew.bat test: PASS
- npm run verify:strict-final: PASS
- git diff --check: PASS
- uilocal boundary scan: PASS, zero forbidden references
- adapter completeness: PASS, zero missing methods
- mirror signature parity: PASS, zero stale imports/fields/methods
- runtime workflows: PASS, all pipeline/debug/settings/info flows covered
- screenshots: PASS, all matrix states covered

Commit range:
- <first commit>..<last commit>
```

Any weaker statement is not acceptable.
