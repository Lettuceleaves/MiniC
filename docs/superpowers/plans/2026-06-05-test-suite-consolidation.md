# Test Suite Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Physically reduce MiniC test source from 113 Java test files and 486 counted JUnit test items to no more than 50 coarse-grained tests while keeping the compiler, runtime debugger, UI API, workbench, and text-style/highlight smoke coverage.

**Architecture:** Replace fine-grained micro-tests with package-local regression suites so package-private access does not force production API changes. Each retained `@Test` covers a scenario matrix with named sub-scenarios and assertion messages; old unit tests are deleted after their high-value behaviors have been folded into the new suites.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Gradle, JavaFX test helpers, PowerShell inventory commands.

---

## Current Baseline

Inventory command used on 2026-06-05:

```powershell
$files = Get-ChildItem -Path src\test\java -Recurse -Filter *.java
$tests = (rg -n "@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)" src\test\java | Measure-Object).Count
"$($files.Count) files, $tests tests"
```

Current result:

```text
113 files, 486 tests
```

Definition used by this plan:

- Required target: `<= 50` counted JUnit test annotations.
- Planned target: `<= 16` test files and `45` counted JUnit test annotations.
- Planned non-essential test-item reduction: `486 - 45 = 441` test annotations are deleted or folded into coarse scenario loops.
- Minimum required reduction to satisfy the user request: `486 - 50 = 436` test annotations.

Rules for the new suite:

- Do not use `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, or `@TestTemplate`; those hide many executable cases behind one annotation and make the inventory less honest.
- Use one `@Test` per workflow-level behavior and loop over named scenarios inside the method.
- Keep assertion messages specific enough that a coarse test failure still points to the failed scenario.
- Do not add production-only visibility changes to rescue a test. If package-private access is needed, keep the replacement suite in the same package.

## Target Test Inventory

| Target file | Test budget | Responsibility |
| --- | ---: | --- |
| `src/test/java/minic/compiler/preprocess/MiniCPreprocessorRegressionTest.java` | 1 | Includes, macros, conditionals, header rules, and diagnostics. |
| `src/test/java/minic/compiler/lexer/LexerRegressionTest.java` | 2 | Representative tokens plus recoverable lexical diagnostics. |
| `src/test/java/minic/compiler/parser/ParserRegressionTest.java` | 2 | Representative valid syntax plus representative syntax recovery. |
| `src/test/java/minic/compiler/semantic/SemanticRegressionTest.java` | 3 | Legal semantic programs, representative diagnostics, scopes/types/layouts. |
| `src/test/java/minic/compiler/pipeline/MiniCompilerPipelineRegressionTest.java` | 2 | Full compile pipeline success/failure and configured toolchain smoke. |
| `src/test/java/minic/compiler/ir/lowering/IrLowererRegressionTest.java` | 3 | IR lowering for program shape, control flow, and memory/pointer/struct cases. |
| `src/test/java/minic/compiler/codegen/windows/WindowsX64CodegenRegressionTest.java` | 2 | Windows x64 assembly emission and incremental codegen state. |
| `src/test/java/minic/compiler/toolchain/WindowsMsvcToolchainRegressionTest.java` | 1 | Tool discovery, command construction, and unavailable-tool diagnostics. |
| `src/test/java/minic/runtime/debug/IrDebugRuntimeRegressionTest.java` | 5 | IR debug execution, breakpoints, stepping, call-stack controls, data-flow events. |
| `src/test/java/minic/runtime/debug/visual/VisualProjectionRegressionTest.java` | 5 | Annotation parsing, descriptor registry, typed memory graph, visual projection, baseline samples. |
| `src/test/java/minic/runtime/step/CompileStageStepperRegressionTest.java` | 3 | Unified stage-stepper contract, current/global data, execution input wait state. |
| `src/test/java/minic/session/CompileObservationSessionRegressionTest.java` | 2 | Session creation, next-stage behavior, playback state, disabled reverse controls. |
| `src/test/java/minic/uiapi/MiniCUiApiRegressionTest.java` | 4 | Observation API, debug API, DTO boundary, IR/ASM/AST/data-structure views. |
| `src/test/java/minic/ui/MiniCWorkbenchRegressionTest.java` | 4 | Workbench shell/controller/view-model/control-hub/debug-pane smoke paths. |
| `src/test/java/minic/ui/MiniCEditorViewportTextRegressionTest.java` | 4 | Editor diagnostics, breakpoints, viewport controls, text styles, IR/ASM highlighting. |
| `src/test/java/minic/MiniCUtilityRegressionTest.java` | 2 | CLI/Main/source/settings/diagnostics/theme utility smoke coverage. |

Total target: `45` counted tests.

## Behavior Retention Map

Compiler replacement suites must preserve these high-value behaviors from current tests:

- `MiniCPreprocessorRegressionTest`: fold all scenarios from `MiniCPreprocessorTest` into one named scenario loop covering include roots, `.mh` suffix validation, missing includes, include cycles, object macros, identifier-boundary replacement, shared macros, `#ifdef` and `#ifndef`, conditional diagnostics, excluded branches, header declarations, header function rejection, and header executable-statement rejection.
- `LexerRegressionTest`: keep token coverage from `lexesRepresentativeTokensAndSkipsComments`, `distinguishesKeywordsFromIdentifierBoundaries`, `lexesExtendedLiterals`, `lexesIncrementAndCompoundAssignmentOperators`, `lexesPhaseDOperatorsAndEllipsis`, and `lexesStringLiteralsWithEscapes`; keep diagnostic coverage from `reportsIncompleteEllipsisWithoutTreatingItAsTwoDots`, `reportsNumericLiteralOverflowInsteadOfThrowing`, and `reportsRepresentativeLexicalDiagnosticsAndContinuesWhenPossible`.
- `ParserRegressionTest`: keep valid syntax coverage from declarations, function pointers, variadic declarations, control flow, `do while`, `switch`, expression precedence, and empty program; keep invalid syntax coverage from representative syntax errors, variadic marker misuse, and unclosed blocks.
- `SemanticRegressionTest`: keep legal-program, variadic-call, composite-lvalue, pointer/array/struct, phase-D expression, return-path, recursive-struct, control-flow-scope, switch-case, and struct-layout coverage.
- `MiniCompilerPipelineRegressionTest`: keep successful full compile, stopped-stage diagnostics, include expansion, untrusted input diagnostics, lowering limits, configured toolchain, and compile-run smoke.
- `IrLowererRegressionTest`: keep program-shape, arithmetic/calls/runtime checks, control flow, loops, switch, pointers/arrays/structs/function pointers, address-of composite lvalues, scalar widths, phase-D expressions, short-circuit side effects, layout-driven locals, and shadowed-local coverage.
- `WindowsX64CodegenRegressionTest`: keep envelope/entry/external-data, calling convention, branches/comparisons, pointer/array/struct/function-pointer calls, scalar widths, floating casts/comparisons, phase-D instructions, short-circuit control flow, switch control flow, and incremental assembly-line state.
- `WindowsMsvcToolchainRegressionTest`: keep default construction outside developer prompt, unavailable assembler diagnostics, runtime library arguments, configured library paths, explicit tool commands, and bundled toolchain root discovery.

Runtime and session replacement suites must preserve these high-value behaviors:

- `IrDebugRuntimeRegressionTest`: keep execution of locals, loads, branches, loops, switch, nested/recursive calls, division-by-zero failure, debug stubs, breakpoints, run-to-end, pause request, step back, back-to-breakpoint, step over, step into, reverse layer controls, source-visible IR snapshots, and runtime visual event recording.
- `VisualProjectionRegressionTest`: keep data-flow event explanations, typed memory graph shapes, visual annotation parsing, descriptor registry, graph/array/composite structures, projection warnings, runtime event replay, self-loop/cycle/shared-node metadata, array/matrix/struct/pointer/list/tree/hash/heap/fenwick projections, and data-structure baseline samples.
- `CompileStageStepperRegressionTest`: keep unified lexer/parser/semantic/IR/codegen/toolchain/execution stage stepping, capability results, current/global/stage step data defensive copies, progress states, and execution input wait handling.
- `CompileObservationSessionRegressionTest`: keep session construction from source text/file, stage order, current stepper, global step count, next-stage transitions, diagnostic stop points, playback tick modes, pause behavior, and disabled reverse controls.

UI, UI API, and utility replacement suites must preserve these high-value behaviors:

- `MiniCUiApiRegressionTest`: keep observation workflow, stage-specific visual data, lexer ranges, semantic scope tree, codegen/IR review data, debug API breakpoints, debug DTO isolation, AST/IR/ASM/metadata view builders, data-structure view builders, DTO defensive copies, and end-to-end visual sample exposure.
- `MiniCWorkbenchRegressionTest`: keep shell layer type, source/visual mode switching, document tabs, left menu sections, controller next/next-stage behavior, view-model source/session/control actions, execution input auto-confirm, public API type boundary, pending breakpoints, selected visual stage review, command registry/control hub behavior, active tracking, debug-pane rendering, and debug controls.
- `MiniCEditorViewportTextRegressionTest`: keep source loader breakpoints, editor refresh, token style preservation, current execution range styling, diagnostic details, completion placement, brace typing/backspace, viewport zoom/scroll/centering, graph viewport zoom, scroll-pane adapter, viewport registry, text-style resolver, syntax mapper, theme CSS, IR highlighting, assembly highlighting, and visual-pane/debug-pane styled rows.
- `MiniCUtilityRegressionTest`: keep CLI invalid-program diagnostics, assembly output/tool diagnostics, compile-run output, project name, diagnostic field validation, source range/source file offset mapping, settings defaults, sample source availability, and theme CSS smoke.

## Deletion Strategy

Do not delete old files until the replacement suite for the same package passes. The deletion is still physical: old test files are removed from `src/test/java`, not merely disabled with tags.

Use this inventory check after every deletion batch:

```powershell
$files = Get-ChildItem -Path src\test\java -Recurse -Filter *.java
$tests = (rg -n "@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)" src\test\java | Measure-Object).Count
"$($files.Count) files, $tests tests"
```

Final expected inventory:

```text
16 files, 45 tests
```

Run the full suite after every batch:

```powershell
.\gradlew.bat --no-daemon test
```

Expected result:

```text
BUILD SUCCESSFUL
```

## Tasks

### Task 1: Create Compiler Regression Suites

**Files:**

- Create: `src/test/java/minic/compiler/preprocess/MiniCPreprocessorRegressionTest.java`
- Create: `src/test/java/minic/compiler/lexer/LexerRegressionTest.java`
- Create: `src/test/java/minic/compiler/parser/ParserRegressionTest.java`
- Create: `src/test/java/minic/compiler/semantic/SemanticRegressionTest.java`
- Create: `src/test/java/minic/compiler/pipeline/MiniCompilerPipelineRegressionTest.java`
- Create: `src/test/java/minic/compiler/ir/lowering/IrLowererRegressionTest.java`
- Create: `src/test/java/minic/compiler/codegen/windows/WindowsX64CodegenRegressionTest.java`
- Create: `src/test/java/minic/compiler/toolchain/WindowsMsvcToolchainRegressionTest.java`

- [ ] **Step 1: Add package-local coarse compiler suites**

Create the eight files above. Each file uses only the test methods listed in the target inventory table. Move the assertion bodies from the current source tests into private helper methods and call them from the coarse `@Test` methods with named scenario messages.

Required test method names:

```java
// src/test/java/minic/compiler/preprocess/MiniCPreprocessorRegressionTest.java
@Test void preprocessesIncludesMacrosConditionalsHeadersAndDiagnostics()

// src/test/java/minic/compiler/lexer/LexerRegressionTest.java
@Test void lexesRepresentativeTokensCommentsLiteralsOperatorsAndStrings()
@Test void reportsRecoverableLexicalDiagnosticsWithoutThrowing()

// src/test/java/minic/compiler/parser/ParserRegressionTest.java
@Test void parsesRepresentativeDeclarationsControlFlowAndExpressions()
@Test void reportsRepresentativeSyntaxErrorsAndRecovers()

// src/test/java/minic/compiler/semantic/SemanticRegressionTest.java
@Test void acceptsRepresentativeLegalProgramsAndRecordsTypesScopesAndLayouts()
@Test void reportsRepresentativeSemanticDiagnostics()
@Test void validatesControlFlowReturnsSwitchRulesAndStructContainment()

// src/test/java/minic/compiler/pipeline/MiniCompilerPipelineRegressionTest.java
@Test void compilesRepresentativeProgramsThroughPipelineAndToolchainSmoke()
@Test void stopsAtTheExpectedStageForPreprocessLexParseSemanticAndLoweringDiagnostics()

// src/test/java/minic/compiler/ir/lowering/IrLowererRegressionTest.java
@Test void lowersProgramShapeArithmeticCallsAndRuntimeChecks()
@Test void lowersControlFlowLoopsSwitchAndShortCircuitExpressions()
@Test void lowersPointersArraysStructsFunctionPointersLayoutsAndShadowedLocals()

// src/test/java/minic/compiler/codegen/windows/WindowsX64CodegenRegressionTest.java
@Test void emitsWindowsX64AssemblyForRepresentativeRuntimeAndLanguageFeatures()
@Test void advancesCodegenStepStateThroughAssemblyLines()

// src/test/java/minic/compiler/toolchain/WindowsMsvcToolchainRegressionTest.java
@Test void buildsToolchainCommandsAndReportsUnavailableTools()
```

- [ ] **Step 2: Run compiler replacement suites while old tests still exist**

Run:

```powershell
.\gradlew.bat --no-daemon test --tests "minic.compiler.*RegressionTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit compiler replacement suites**

Run:

```powershell
git add src/test/java/minic/compiler
git commit -m "test: consolidate compiler regression coverage"
```

### Task 2: Create Runtime and Session Regression Suites

**Files:**

- Create: `src/test/java/minic/runtime/debug/IrDebugRuntimeRegressionTest.java`
- Create: `src/test/java/minic/runtime/debug/visual/VisualProjectionRegressionTest.java`
- Create: `src/test/java/minic/runtime/step/CompileStageStepperRegressionTest.java`
- Create: `src/test/java/minic/session/CompileObservationSessionRegressionTest.java`

- [ ] **Step 1: Add package-local coarse runtime and session suites**

Required test method names:

```java
// src/test/java/minic/runtime/debug/IrDebugRuntimeRegressionTest.java
@Test void executesIrProgramsBranchesLoopsCallsStubsAndRuntimeFailures()
@Test void supportsBreakpointsRunPauseRestartAndRunToEndControls()
@Test void supportsStepBackBackToBreakpointStepOverAndStepIntoControls()
@Test void preservesSourceVisibleIrSnapshotsAndDebugMappings()
@Test void recordsRuntimeVisualAndDataFlowEventsForStructuresAndPointers()

// src/test/java/minic/runtime/debug/visual/VisualProjectionRegressionTest.java
@Test void parsesVisualAnnotationsAndRegistersDescriptors()
@Test void buildsTypedMemoryGraphsForScalarsPointersArraysStructsAndNestedValues()
@Test void projectsGraphArrayCompositePointerListTreeHashHeapAndFenwickStructures()
@Test void replaysRuntimeEventsAndMarksCyclesSharedNodesAndRewiredEdges()
@Test void runsDataStructureBaselineSamplesAndReportsProjectionWarnings()

// src/test/java/minic/runtime/step/CompileStageStepperRegressionTest.java
@Test void advancesAllCompileStagesThroughUnifiedForwardOnlyContract()
@Test void exposesStableCurrentStageGlobalProgressCapabilitiesAndResults()
@Test void waitsForConfirmedExecutionInputBeforeRunningExecutable()

// src/test/java/minic/session/CompileObservationSessionRegressionTest.java
@Test void createsSessionsAdvancesStagesAndStopsAtDiagnosticBoundaries()
@Test void supportsPlaybackTickModesPauseStateAndReservedReverseControls()
```

- [ ] **Step 2: Run runtime and session replacement suites while old tests still exist**

Run:

```powershell
.\gradlew.bat --no-daemon test --tests "minic.runtime.*RegressionTest" --tests "minic.session.*RegressionTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit runtime and session replacement suites**

Run:

```powershell
git add src/test/java/minic/runtime src/test/java/minic/session
git commit -m "test: consolidate runtime and session regression coverage"
```

### Task 3: Create UI, UI API, and Utility Regression Suites

**Files:**

- Create: `src/test/java/minic/uiapi/MiniCUiApiRegressionTest.java`
- Create: `src/test/java/minic/ui/MiniCWorkbenchRegressionTest.java`
- Create: `src/test/java/minic/ui/MiniCEditorViewportTextRegressionTest.java`
- Create: `src/test/java/minic/MiniCUtilityRegressionTest.java`

- [ ] **Step 1: Add coarse UI/API/utility suites**

Required test method names:

```java
// src/test/java/minic/uiapi/MiniCUiApiRegressionTest.java
@Test void exposesObservationWorkflowControlsDiagnosticsAndStageVisualData()
@Test void exposesDebugControlsViewsAndDtoBoundariesWithoutRuntimeTypes()
@Test void buildsAstIrAsmMetadataAndDataStructureViews()
@Test void preservesUiDtoDefensiveCopiesAndEndToEndVisualSamples()

// src/test/java/minic/ui/MiniCWorkbenchRegressionTest.java
@Test void startsShellControllerViewModelDocumentsAndPipelineModes()
@Test void drivesWorkbenchControlsPlaybackExecutionInputAndSelectedVisualStages()
@Test void routesCommandsActiveTrackingAndViewportOperationsThroughControlHub()
@Test void rendersDebugPaneSourceIrAsmAstMetadataAndDataStructures()

// src/test/java/minic/ui/MiniCEditorViewportTextRegressionTest.java
@Test void handlesSourceLoaderBreakpointsEditorDiagnosticsTypingAndRealtimeAnalysis()
@Test void controlsTextGraphAndScrollPaneViewportsWithStableActiveTracking()
@Test void resolvesReusableTextStylesSyntaxDiagnosticsThemeCssIrAndAssemblyHighlighting()
@Test void rendersStyledIrAndAssemblyRowsInVisualPaneAndDebugPane()

// src/test/java/minic/MiniCUtilityRegressionTest.java
@Test void runsCliMainSourceSettingsDiagnosticsSamplesAndThemeSmoke()
@Test void mapsSourceOffsetsRangesAndReportsToolDiagnostics()
```

- [ ] **Step 2: Run UI/API/utility replacement suites while old tests still exist**

Run:

```powershell
.\gradlew.bat --no-daemon test --tests "minic.uiapi.MiniCUiApiRegressionTest" --tests "minic.ui.*RegressionTest" --tests "minic.MiniCUtilityRegressionTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit UI/API/utility replacement suites**

Run:

```powershell
git add src/test/java/minic/uiapi src/test/java/minic/ui src/test/java/minic/MiniCUtilityRegressionTest.java
git commit -m "test: consolidate ui and utility regression coverage"
```

### Task 4: Delete Fine-Grained Compiler Tests

**Files:**

- Delete all old compiler test files listed in the command below.

- [ ] **Step 1: Remove old compiler tests after Task 1 passes**

Run:

```powershell
git rm -- `
  src/test/java/minic/compiler/ast/decl/DeclarationAstTest.java `
  src/test/java/minic/compiler/ast/expr/ExpressionAstTest.java `
  src/test/java/minic/compiler/ast/stmt/StatementAstTest.java `
  src/test/java/minic/compiler/codegen/windows/WindowsX64AssemblyEmitterTest.java `
  src/test/java/minic/compiler/codegen/windows/WindowsX64CodegenStepStateTest.java `
  src/test/java/minic/compiler/ir/lowering/IrLowererTest.java `
  src/test/java/minic/compiler/ir/lowering/IrStepStateTest.java `
  src/test/java/minic/compiler/ir/model/IrModelTest.java `
  src/test/java/minic/compiler/lexer/LexerStateTest.java `
  src/test/java/minic/compiler/lexer/LexerTest.java `
  src/test/java/minic/compiler/lexer/LexResultTest.java `
  src/test/java/minic/compiler/parser/ParserStepStateTest.java `
  src/test/java/minic/compiler/parser/ParserTest.java `
  src/test/java/minic/compiler/pipeline/MiniCompilerTest.java `
  src/test/java/minic/compiler/preprocess/MiniCPreprocessorTest.java `
  src/test/java/minic/compiler/semantic/ScopeTest.java `
  src/test/java/minic/compiler/semantic/SemanticAnalyzerTest.java `
  src/test/java/minic/compiler/semantic/SemanticStepStateTest.java `
  src/test/java/minic/compiler/semantic/StructLayoutTest.java `
  src/test/java/minic/compiler/stage/CompilerStageModelTest.java `
  src/test/java/minic/compiler/toolchain/WindowsMsvcToolchainTest.java `
  src/test/java/minic/compiler/type/MiniTypeTest.java `
  src/test/java/minic/compiler/type/TypeLayoutTest.java
```

- [ ] **Step 2: Verify compiler deletion batch**

Run:

```powershell
.\gradlew.bat --no-daemon test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit compiler deletion batch**

Run:

```powershell
git add -u src/test/java/minic/compiler
git commit -m "test: remove fine grained compiler tests"
```

### Task 5: Delete Fine-Grained Runtime and Session Tests

**Files:**

- Delete all old runtime and session test files listed in the command below.

- [ ] **Step 1: Remove old runtime and session tests after Task 2 passes**

Run:

```powershell
git rm -- `
  src/test/java/minic/runtime/debug/dataflow/DataFlowEventTest.java `
  src/test/java/minic/runtime/debug/DataStructureVisualizationBaselineTest.java `
  src/test/java/minic/runtime/debug/DebugMappingIndexTest.java `
  src/test/java/minic/runtime/debug/DebugProcessSpaceTest.java `
  src/test/java/minic/runtime/debug/DebugSessionTest.java `
  src/test/java/minic/runtime/debug/DebugValueTest.java `
  src/test/java/minic/runtime/debug/IrDebugInterpreterTest.java `
  src/test/java/minic/runtime/debug/memory/TypedMemoryGraphBuilderTest.java `
  src/test/java/minic/runtime/debug/visual/DataStructureDescriptorRegistryTest.java `
  src/test/java/minic/runtime/debug/visual/VisualAnnotationParserTest.java `
  src/test/java/minic/runtime/debug/visual/VisualEventTest.java `
  src/test/java/minic/runtime/debug/visual/VisualProjectionBuilderTest.java `
  src/test/java/minic/runtime/debug/visual/VisualStructureTest.java `
  src/test/java/minic/runtime/execution/ExecutableRunnerTest.java `
  src/test/java/minic/runtime/step/CodegenStageStepperTest.java `
  src/test/java/minic/runtime/step/CompileStageTest.java `
  src/test/java/minic/runtime/step/CurrentStepStateTest.java `
  src/test/java/minic/runtime/step/ExecutionStageStepperTest.java `
  src/test/java/minic/runtime/step/GlobalStepDataTest.java `
  src/test/java/minic/runtime/step/IrStageStepperTest.java `
  src/test/java/minic/runtime/step/LexerStageStepperTest.java `
  src/test/java/minic/runtime/step/ParserStageStepperTest.java `
  src/test/java/minic/runtime/step/SemanticStageStepperTest.java `
  src/test/java/minic/runtime/step/StageProgressTest.java `
  src/test/java/minic/runtime/step/StageStepDataTest.java `
  src/test/java/minic/runtime/step/StageStepperTest.java `
  src/test/java/minic/runtime/step/StepCapabilitiesTest.java `
  src/test/java/minic/runtime/step/StepResultTest.java `
  src/test/java/minic/runtime/step/ToolchainStageStepperTest.java `
  src/test/java/minic/session/CompileObservationSessionNextTest.java `
  src/test/java/minic/session/CompileObservationSessionPlaybackStateTest.java `
  src/test/java/minic/session/CompileObservationSessionPlaybackTickTest.java `
  src/test/java/minic/session/CompileObservationSessionReverseCapabilityTest.java `
  src/test/java/minic/session/CompileObservationSessionTest.java
```

- [ ] **Step 2: Verify runtime and session deletion batch**

Run:

```powershell
.\gradlew.bat --no-daemon test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit runtime and session deletion batch**

Run:

```powershell
git add -u src/test/java/minic/runtime src/test/java/minic/session
git commit -m "test: remove fine grained runtime and session tests"
```

### Task 6: Delete Fine-Grained UI, UI API, and Utility Tests

**Files:**

- Delete all old UI, UI API, CLI, color, diagnostics, settings, source, and main test files listed in the command below.

- [ ] **Step 1: Remove old UI/API/utility tests after Task 3 passes**

Run:

```powershell
git rm -- `
  src/test/java/minic/cli/MiniCliTest.java `
  src/test/java/minic/color/ThemeCssGeneratorTest.java `
  src/test/java/minic/diagnostics/DiagnosticTest.java `
  src/test/java/minic/MainTest.java `
  src/test/java/minic/settings/MiniCSettingsTest.java `
  src/test/java/minic/source/SourceFileTest.java `
  src/test/java/minic/source/SourceRangeTest.java `
  src/test/java/minic/ui/MiniCActiveTrackingServiceTest.java `
  src/test/java/minic/ui/MiniCAssemblyTextModelFactoryTest.java `
  src/test/java/minic/ui/MiniCAstGraphModelFactoryTest.java `
  src/test/java/minic/ui/MiniCAstTreeModelFactoryTest.java `
  src/test/java/minic/ui/MiniCBottomPanelModelFactoryTest.java `
  src/test/java/minic/ui/MiniCBottomPanelSourceMaskTest.java `
  src/test/java/minic/ui/MiniCCodeEditorBreakpointGutterTest.java `
  src/test/java/minic/ui/MiniCCodeEditorDiagnosticDetailTest.java `
  src/test/java/minic/ui/MiniCCodeEditorViewportControlTest.java `
  src/test/java/minic/ui/MiniCCommandRegistryTest.java `
  src/test/java/minic/ui/MiniCDebugPaneTest.java `
  src/test/java/minic/ui/MiniCDiagnosticListFactoryTest.java `
  src/test/java/minic/ui/MiniCEditorTypingTest.java `
  src/test/java/minic/ui/MiniCGraphViewportAdapterTest.java `
  src/test/java/minic/ui/MiniCInspectorModelFactoryTest.java `
  src/test/java/minic/ui/MiniCInspectorViewControlHubTest.java `
  src/test/java/minic/ui/MiniCIrAssemblyHighlightRenderingTest.java `
  src/test/java/minic/ui/MiniCLexerOverlayModelFactoryTest.java `
  src/test/java/minic/ui/MiniCPlaybackControllerTest.java `
  src/test/java/minic/ui/MiniCRealtimeAnalyzerTest.java `
  src/test/java/minic/ui/MiniCSampleProgramsTest.java `
  src/test/java/minic/ui/MiniCScrollPaneViewportAdapterTest.java `
  src/test/java/minic/ui/MiniCSemanticScopeTreeModelFactoryTest.java `
  src/test/java/minic/ui/MiniCSourceLineFactoryTest.java `
  src/test/java/minic/ui/MiniCSourceLoaderViewBreakpointTest.java `
  src/test/java/minic/ui/MiniCStageListFactoryTest.java `
  src/test/java/minic/ui/MiniCViewportPointMapperTest.java `
  src/test/java/minic/ui/MiniCViewportRegistryTest.java `
  src/test/java/minic/ui/MiniCVisualModelFactoryTest.java `
  src/test/java/minic/ui/MiniCVisualPaneExplanationTest.java `
  src/test/java/minic/ui/MiniCVisualPaneInspectorSourceTest.java `
  src/test/java/minic/ui/MiniCWorkbenchAppTest.java `
  src/test/java/minic/ui/MiniCWorkbenchControlHubTest.java `
  src/test/java/minic/ui/MiniCWorkbenchControllerTest.java `
  src/test/java/minic/ui/MiniCWorkbenchShellTest.java `
  src/test/java/minic/ui/MiniCWorkbenchViewModelTest.java `
  src/test/java/minic/ui/text/MiniCIntermediateTextHighlighterTest.java `
  src/test/java/minic/ui/text/MiniCSyntaxTextStyleMapperTest.java `
  src/test/java/minic/ui/text/MiniCTextStyleResolverTest.java `
  src/test/java/minic/uiapi/MiniCDebugApiTest.java `
  src/test/java/minic/uiapi/MiniCObservationApiEndToEndTest.java `
  src/test/java/minic/uiapi/MiniCObservationApiTest.java `
  src/test/java/minic/uiapi/UiDebugAsmViewBuilderTest.java `
  src/test/java/minic/uiapi/UiDebugAstViewBuilderTest.java `
  src/test/java/minic/uiapi/UiDebugDataStructureEndToEndTest.java `
  src/test/java/minic/uiapi/UiDebugDataStructureViewBuilderTest.java `
  src/test/java/minic/uiapi/UiDebugIrViewBuilderTest.java `
  src/test/java/minic/uiapi/UiDebugMetadataViewBuilderTest.java `
  src/test/java/minic/uiapi/UiDtoTest.java
```

- [ ] **Step 2: Verify UI/API/utility deletion batch**

Run:

```powershell
.\gradlew.bat --no-daemon test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit UI/API/utility deletion batch**

Run:

```powershell
git add -u src/test/java/minic
git commit -m "test: remove fine grained ui and api tests"
```

### Task 7: Final Inventory, Documentation, and Guardrails

**Files:**

- Create: `docs/testing-strategy.md`
- Modify: `README.md`

- [ ] **Step 1: Document the new coarse regression strategy**

Create `docs/testing-strategy.md` with this content:

```markdown
# MiniC Testing Strategy

MiniC keeps the default development suite intentionally small. The target is no more than 50 counted JUnit test annotations under `src/test/java`.

The suite favors workflow-level regression tests over one-assertion micro-tests. Each `@Test` may cover a named matrix of related scenarios, and each assertion should include a scenario label so failures remain diagnosable.

Current ownership:

- Compiler tests cover preprocessing, lexing, parsing, semantic analysis, full pipeline behavior, IR lowering, Windows x64 code generation, and MSVC toolchain command behavior.
- Runtime tests cover IR debug execution, breakpoints, reverse stepping controls, data-flow events, typed memory graphs, visual projections, stage stepping, and observation sessions.
- UI tests cover the UI API boundary, workbench shell/controller/view-model/control-hub flows, editor diagnostics, viewport control, reusable text styles, and IR/assembly highlighting.
- Utility tests cover CLI behavior, project identity, source mapping, settings defaults, diagnostics, samples, and theme CSS smoke behavior.

When adding coverage, prefer extending an existing scenario matrix inside one of the regression suites. Add a new `@Test` only when the behavior is a separate workflow and the total count remains at or below 50.
```

- [ ] **Step 2: Link the strategy from README**

Add this bullet to the existing documentation section in `README.md`:

```markdown
- [Testing strategy](docs/testing-strategy.md) explains the intentionally small regression suite and the 50-test budget.
```

- [ ] **Step 3: Verify final inventory budget**

Run:

```powershell
$files = Get-ChildItem -Path src\test\java -Recurse -Filter *.java
$tests = (rg -n "@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)" src\test\java | Measure-Object).Count
if ($tests -gt 50) { throw "Test budget exceeded: $tests" }
"$($files.Count) files, $tests tests"
```

Expected:

```text
16 files, 45 tests
```

- [ ] **Step 4: Run full verification**

Run:

```powershell
.\gradlew.bat --no-daemon clean test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit documentation and final budget check**

Run:

```powershell
git add README.md docs/testing-strategy.md
git commit -m "docs: describe consolidated test strategy"
```

## Risk Controls

- Coarse tests can become hard to diagnose if they only use broad assertions. Use named scenarios and assertion messages such as `scenario.name()`.
- Keep old tests until the replacement package passes. This avoids deleting coverage before the new regression suite exists.
- Do not remove sample programs or production visual/debug examples. Only delete files under `src/test/java`.
- If a replacement suite reaches more than its budget, merge related checks into helper methods inside the same `@Test` rather than adding another test annotation.
- If a scenario depends on OS tooling such as MSVC, assert command construction and unavailable-tool diagnostics in the default suite; keep actual executable build/run behavior guarded by existing toolchain availability checks.

## Self-Review

- Spec coverage: This plan directly addresses physical deletion, code-volume reduction, coarse-grained tests, and the strict `<= 50` test-item target.
- Inventory coverage: The plan starts from the verified `113 files, 486 tests` baseline and targets `16 files, 45 tests`.
- Deletion coverage: All current test files are either replaced by one of the target regression suites or explicitly deleted in Tasks 4, 5, and 6.
- Placeholder scan: The plan contains no deferred implementation markers; every task has exact paths, commands, expected outputs, and fixed target test method names.
