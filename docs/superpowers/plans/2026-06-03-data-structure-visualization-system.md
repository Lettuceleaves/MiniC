# MiniC Data Structure Visualization System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a type-driven debugger visualization system for scalars, pointers, pointer chains, arrays, matrices, structs, struct pointers, struct pointer chains, struct arrays, and struct matrices, with minimal source annotations and beginner-readable explanations.

**Architecture:** Source comments are parsed into `VisualSpec` objects. The debug interpreter preserves typed memory details and records data-flow events. A projection layer combines `VisualSpec`, typed memory, and events into UI template models, which JavaFX renders with explanations.

**Tech Stack:** Java 21, Gradle, JUnit 5, AssertJ, existing MiniC parser/semantic/IR/debugger/UI API layers.

---

## Execution Contract

- Work in phases. Do not advance to the next phase until the current phase passes its validation command.
- Each phase is suitable for an independent subagent with a bounded write scope.
- If validation fails once, fix within the phase. If it fails twice for the same reason, reduce the phase scope and retest. If it fails three times, discard that phase's implementation and restart from this plan.
- Do not revert unrelated dirty files.
- Use TDD: write or update the failing test first, run it, then implement.

## Phase 0: Baseline Fixture Matrix

**Goal:** Add explicit tests that describe the current desired data-shape coverage and expose known debugger failures.

**Files:**
- Create: `src/test/java/minic/runtime/debug/DataStructureVisualizationBaselineTest.java`
- Read only: `samples/*.mc`

**Required coverage:**
- scalar
- pointer
- pointer chain
- array
- pointer array
- matrix
- struct
- struct pointer
- struct pointer chain
- struct array
- struct matrix
- struct list

**Steps:**
- [ ] Add fixture snippets or sample loading helpers.
- [ ] Add tests proving `struct_init.mc` should run and preserve field names `x` and `y`.
- [ ] Add tests proving `struct_array.mc` should run and preserve paths `arr[0].x`, `arr[1].y`, `arr[2].x`.
- [ ] Add tests proving existing scalar, pointer, matrix, and struct linked samples still start debug successfully.
- [ ] Run: `.\gradlew.bat test --tests minic.runtime.debug.DataStructureVisualizationBaselineTest`

**Validation:**
- The test class exists.
- Tests for known broken behavior fail before Phase 1 implementation.
- After Phase 1, all tests in this class pass.

## Phase 1: Struct Debugger Correctness

**Goal:** Make struct initialization and struct arrays correct in the debug interpreter.

**Files:**
- Modify: `src/main/java/minic/compiler/ir/lowering/StatementLowerer.java`
- Modify: `src/main/java/minic/runtime/debug/IrDebugInterpreter.java`
- Modify: `src/test/java/minic/runtime/debug/IrDebugInterpreterTest.java`
- Modify: `src/test/java/minic/runtime/debug/DataStructureVisualizationBaselineTest.java`

**Required behavior:**
- `struct Point p = {10, 20};` writes fields `x` and `y`, never `field0` or `field1`.
- `struct Point arr[3]; arr[0].x = 10;` maps the element address to `arr[0]`, then maps field address to `arr[0].x`.
- Reading `arr[0].x + arr[1].y + arr[2].x` returns the correct value.

**Steps:**
- [ ] Add a failing test for real struct initializer field names.
- [ ] Add a failing test for struct array field writes and reads.
- [ ] Teach `StatementLowerer.lowerStructInit` to use field layout names.
- [ ] Teach `IrDebugInterpreter` to track array element addresses as addressable memory owners.
- [ ] Teach field read/write to update nested owners, not only top-level locals.
- [ ] Run: `.\gradlew.bat test --tests minic.runtime.debug.IrDebugInterpreterTest --tests minic.runtime.debug.DataStructureVisualizationBaselineTest`

**Validation:**
- No `<uninitialized>` numeric failure for `struct_init.mc`.
- No `<uninitialized>` numeric failure for `struct_array.mc`.
- `DebugValue.fields()` for initialized structs contains real field names.
- Existing struct pointer behavior still passes.

## Phase 2: Typed Memory Graph

**Goal:** Introduce a recursive typed memory graph that preserves fields, elements, pointers, addresses, and type shape for UI/projection.

**Files:**
- Create: `src/main/java/minic/runtime/debug/memory/TypeShape.java`
- Create: `src/main/java/minic/runtime/debug/memory/TypedMemoryNode.java`
- Create: `src/main/java/minic/runtime/debug/memory/TypedMemoryField.java`
- Create: `src/main/java/minic/runtime/debug/memory/TypedMemoryElement.java`
- Create: `src/main/java/minic/runtime/debug/memory/TypedPointerEdge.java`
- Create: `src/main/java/minic/runtime/debug/memory/TypedMemoryGraph.java`
- Create: `src/main/java/minic/runtime/debug/memory/TypedMemoryGraphBuilder.java`
- Create: `src/test/java/minic/runtime/debug/memory/TypedMemoryGraphBuilderTest.java`

**Required behavior:**
- Scalar locals become scalar nodes.
- Pointer locals include pointer targets.
- Arrays preserve all elements and indexes.
- Structs preserve all fields.
- Struct arrays preserve elements whose values are struct nodes.

**Steps:**
- [ ] Add failing graph builder tests using `DebugValue` fixtures.
- [ ] Implement immutable memory graph records.
- [ ] Implement graph builder from `DebugProcessSpace`.
- [ ] Run: `.\gradlew.bat test --tests minic.runtime.debug.memory.TypedMemoryGraphBuilderTest`

**Validation:**
- Tests assert recursive fields/elements, not only summaries.
- Pointer target addresses are preserved.
- Graph can represent `struct Point arr[3]`.

## Phase 3: Data Flow Events

**Goal:** Record data-flow events for writes, address calculations, pointer updates, and field/element mutations.

**Files:**
- Create: `src/main/java/minic/runtime/debug/dataflow/DataFlowEvent.java`
- Create: `src/main/java/minic/runtime/debug/dataflow/DataFlowEventType.java`
- Modify: `src/main/java/minic/runtime/debug/DebugSession.java`
- Modify: `src/main/java/minic/runtime/debug/IrDebugInterpreter.java`
- Create: `src/test/java/minic/runtime/debug/dataflow/DataFlowEventTest.java`

**Required event fields:**
- `snapshotId`
- `instructionId`
- `sourceRange`
- `cExpression`
- `lvaluePath`
- `oldValue`
- `newValue`
- `address`
- `pointerTarget`

**Steps:**
- [ ] Add failing tests for `a.value = 1`, `arr[1].x = 30`, `pp->x = 20`, and `p = &x`.
- [ ] Add event storage to `DebugSession`.
- [ ] Emit events from interpreter write paths.
- [ ] Run: `.\gradlew.bat test --tests minic.runtime.debug.dataflow.DataFlowEventTest`

**Validation:**
- Every tested mutation has a path-like `lvaluePath`.
- Events include old and new values.
- Pointer retargeting events include target addresses.

## Phase 4: VisualSpec DSL

**Goal:** Parse minimal annotations into normalized visual specs.

**Files:**
- Create: `src/main/java/minic/runtime/debug/visual/VisualSpec.java`
- Create: `src/main/java/minic/runtime/debug/visual/VisualKind.java`
- Modify: `src/main/java/minic/runtime/debug/visual/VisualAnnotationParser.java`
- Modify: `src/test/java/minic/runtime/debug/visual/VisualAnnotationParserTest.java`

**Supported syntax:**
- `// @visual root=arr`
- `// @visual root=matrix kind=matrix`
- `// @visual root=head kind=struct-list next=next label=value`
- `// @visual root=points kind=struct-array fields=x,y`

**Steps:**
- [ ] Add failing parser tests for normalized lowercase/kebab-case kinds.
- [ ] Add tests for default `kind=auto`.
- [ ] Add warnings for unknown kind and missing `next` in `struct-list`.
- [ ] Implement `VisualSpec` conversion.
- [ ] Run: `.\gradlew.bat test --tests minic.runtime.debug.visual.VisualAnnotationParserTest`

**Validation:**
- Users no longer need `@visual-node` or `@visual-edge` for typed structures.
- Existing annotations remain backward compatible.

## Phase 5: Projection and Template Selection

**Goal:** Project typed memory and specs into visual structures for all required kinds.

**Files:**
- Modify: `src/main/java/minic/runtime/debug/visual/VisualProjectionBuilder.java`
- Modify: `src/main/java/minic/runtime/debug/visual/ArrayStructure.java`
- Modify: `src/main/java/minic/runtime/debug/visual/ArrayCell.java`
- Modify: `src/main/java/minic/runtime/debug/visual/GraphStructure.java`
- Modify: `src/main/java/minic/runtime/debug/visual/CompositeStructure.java`
- Modify: `src/test/java/minic/runtime/debug/visual/VisualProjectionBuilderTest.java`

**Required projection behavior:**
- `int arr[3]` produces three cells.
- `int matrix[2][3]` produces shape `2x3`.
- `struct Point p` produces fields `x,y`.
- `struct Point arr[3]` produces three struct elements.
- `struct-list next=next` follows struct pointer fields.

**Steps:**
- [ ] Add failing tests for every VisualKind in the coverage matrix.
- [ ] Replace hard-coded `1x1` array projection with graph-derived shape and cells.
- [ ] Add struct, pointer, pointer-chain, struct-array, and struct-matrix projections.
- [ ] Preserve metadata needed by UI: addresses, index paths, field names, pointer targets.
- [ ] Run: `.\gradlew.bat test --tests minic.runtime.debug.visual.VisualProjectionBuilderTest`

**Validation:**
- Arrays are never projected as `1x1` unless they are actually one cell.
- Struct projections do not collapse into `struct{n fields}`.
- Pointer structures expose both source and target addresses.

## Phase 6: UI DTO and Renderer

**Goal:** Preserve nested visual data through UI DTOs and render it in the JavaFX debug pane.

**Files:**
- Modify: `src/main/java/minic/uiapi/debug/UiDebugVariableDto.java`
- Modify: `src/main/java/minic/uiapi/debug/UiDebugDtoMapper.java`
- Modify: `src/main/java/minic/uiapi/debug/UiDebugDataStructureViewBuilder.java`
- Modify: `src/main/java/minic/ui/debug/MiniCDebugPane.java`
- Modify: `src/test/java/minic/uiapi/UiDebugDataStructureViewBuilderTest.java`
- Modify: `src/test/java/minic/ui/MiniCDebugPaneTest.java`

**Required DTO fields:**
- nested children or fields/elements
- pointer target
- address
- type shape
- highlighted change
- explanation

**Steps:**
- [ ] Add failing UI API tests proving struct fields and array elements survive mapping.
- [ ] Add failing JavaFX pane tests for struct and pointer rendering text.
- [ ] Extend DTOs without breaking existing callers.
- [ ] Render nested fields/elements in the data structure tab.
- [ ] Run: `.\gradlew.bat test --tests minic.uiapi.UiDebugDataStructureViewBuilderTest --tests minic.ui.MiniCDebugPaneTest`

**Validation:**
- UI DTO for `struct Node a` contains `value` and `next`.
- UI DTO for `arr[1].x` can identify `arr -> [1] -> x`.
- The debug pane displays nested fields/elements, not only summaries.

## Phase 7: Beginner Explanation Templates

**Goal:** Add beginner-readable explanations for every visual template.

**Files:**
- Modify: `src/main/java/minic/ui/visual/ExplanationTemplates.java`
- Modify: `src/main/resources/minic/templates/*.md`
- Modify: `src/test/java/minic/ui/MiniCVisualPaneExplanationTest.java`

**Required placeholders:**
- `{{root}}`
- `{{typeName}}`
- `{{cExpression}}`
- `{{lvaluePath}}`
- `{{oldValue}}`
- `{{newValue}}`
- `{{address}}`
- `{{pointerTarget}}`
- `{{fieldName}}`
- `{{indexPath}}`

**Steps:**
- [ ] Add failing tests for array element explanation, pointer dereference explanation, struct field explanation, and struct matrix explanation.
- [ ] Add template entries for all VisualKind values.
- [ ] Ensure `render(stage, key, vars)` handles runtime variables consistently.
- [ ] Run: `.\gradlew.bat test --tests minic.ui.MiniCVisualPaneExplanationTest`

**Validation:**
- Explanations mention C expression, variable path, old value, and new value.
- Pointer explanations describe dereference.
- Struct explanations describe field access.
- Matrix explanations describe row and column in human terms.

## Phase 8: End-to-End Regression

**Goal:** Verify the full visualization system against representative MiniC programs.

**Files:**
- Create: `samples/visual_scalar.mc`
- Create: `samples/visual_pointer.mc`
- Create: `samples/visual_pointer_chain.mc`
- Create: `samples/visual_array.mc`
- Create: `samples/visual_pointer_array.mc`
- Create: `samples/visual_matrix.mc`
- Create: `samples/visual_struct.mc`
- Create: `samples/visual_struct_pointer.mc`
- Create: `samples/visual_struct_pointer_chain.mc`
- Create: `samples/visual_struct_array.mc`
- Create: `samples/visual_struct_matrix.mc`
- Create: `samples/visual_struct_list.mc`
- Create: `src/test/java/minic/uiapi/UiDebugDataStructureEndToEndTest.java`

**Steps:**
- [ ] Add all samples.
- [ ] Add end-to-end tests that load each sample, start debug, step to the key mutation, and inspect `dataStructureDebugView`.
- [ ] Run: `.\gradlew.bat test --tests minic.uiapi.UiDebugDataStructureEndToEndTest`
- [ ] Run: `.\gradlew.bat test`

**Validation:**
- Every sample starts debug without exception.
- Every sample has at least one visual structure.
- Every visual structure has a beginner-readable explanation.
- No existing test regresses.

