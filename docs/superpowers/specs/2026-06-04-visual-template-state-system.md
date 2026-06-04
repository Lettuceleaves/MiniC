# Visual Template And Runtime State System SPEC

## Goal

Build a data-structure visualization model that starts from visual effect instead of ACM names. The user annotation should choose a high-level teaching template when that template saves configuration, while runtime-only phenomena such as cycles, self-loops, shared nodes, null edges, rewires, and aliases are detected as states rather than declared as separate templates.

## Product Principles

1. A user-visible template exists when it reduces annotation cost, carries stable teaching meaning, or supplies stable defaults.
2. A runtime state exists when the program naturally produces it during execution and the user should not have to predict it.
3. A template may reuse the same underlying layout as another template. The exposed template is justified by defaults, labels, marker rules, and expected teaching highlights.
4. The data-structure tab must stay visual-first. Repeated explanatory text belongs in tooltips/metadata, not as long visible prose inside every card.
5. A freshman should be able to configure common structures with one short `@visual` line and ordinary C field names such as `next`, `prev`, `left`, `right`, `key`, `value`, `top`, `front`, and `rear`.

## Terminology

- **Underlying shape**: the renderer primitive, such as sequence, grid, linked graph, hierarchy, bucketed graph, or general graph.
- **User template**: a `kind` the user may write in `@visual`, such as `doubly-list`, `lru-list`, `hash-chain-table`, or `binary-tree`.
- **Runtime state**: metadata inferred from current nodes/edges/events, such as `cycle`, `self-loop`, `shared-node`, `null-edge`, or `rewire`.
- **Primary edge**: the edge that determines main layout, for example `next` in a list or `left/right` in a binary tree.
- **Auxiliary edge**: an edge shown for understanding but not used as the main layout spine, for example `prev` in a doubly linked list.

## User Templates

### Sequence Family

| Template | Underlying Shape | Default Configuration | Acceptance Evidence |
|---|---|---|---|
| `array` | sequence | all cells, indexes, values | existing array tests still pass |
| `string` | sequence | character display, visible `'\0'` when present | parser accepts kind; projection keeps sequence kind |
| `stack` | sequence | `top=top`, valid range `[0, top)` | cell metadata exposes stack marker inputs |
| `queue` | sequence | `front=front`, `rear=rear` | cell metadata exposes queue marker inputs |
| `deque` | sequence | `front=front`, `rear=rear` | parser/projection preserve deque kind |
| `circular-queue` | sequence | ring index markers, not a separate graph | parser/projection preserve circular queue kind |
| `heap` | sequence plus parent/child metadata | parent index `(i-1)/2`, child indexes `2i+1`, `2i+2` | each cell exposes heap relation metadata |
| `fenwick-tree` | sequence plus lowbit coverage | each cell exposes coverage range | each cell exposes `rangeStart` and `rangeEnd` |
| `dsu` | sequence plus forest edges | `parent=fa` by default | parser/projection preserve dsu kind |

### Record Family

| Template | Underlying Shape | Default Configuration | Acceptance Evidence |
|---|---|---|---|
| `struct` | record | selected or all fields | existing struct tests still pass |
| `struct-array` | record table | selected or all fields per element | existing struct array tests still pass |
| `record-table` | record table | alias for table-like struct array | parser accepts kind |

### Linked Graph Family

| Template | Underlying Shape | Default Configuration | Acceptance Evidence |
|---|---|---|---|
| `singly-list` | linked graph | `next=next`, optional `label=value` | one-line annotation follows a list |
| `struct-list` | linked graph | legacy alias using `next` | existing struct-list behavior remains compatible |
| `doubly-list` | linked graph | `next=next`, `prev=prev`, `label=value` | next edges are primary; prev edges are auxiliary |
| `lru-list` | linked graph | `head=root`, `tail` inferred, `next/prev`, move/rewire states | head/tail node metadata is present |

### Hierarchy Family

| Template | Underlying Shape | Default Configuration | Acceptance Evidence |
|---|---|---|---|
| `binary-tree` | hierarchy | `left=left`, `right=right`, optional `label=value` | one-line annotation follows left/right pointers |
| `general-tree` | hierarchy | explicit child edge config later; currently parsed and preserved | parser accepts kind |
| `trie` | hierarchy | child edges carry character metadata when configured | parser accepts kind |
| `segment-tree` | interval hierarchy | interval metadata when available | parser accepts kind |

### Bucketed And Graph Family

| Template | Underlying Shape | Default Configuration | Acceptance Evidence |
|---|---|---|---|
| `hash-chain-table` | bucketed graph | bucket array horizontal, each bucket chain vertical, `next=next` | graph kind/layout is bucketed and bucket/chain metadata is present |
| `adjacency-list` | bucketed graph | vertex buckets with edge chains | parser/projection preserve kind |
| `graph` | general graph | explicit or runtime nodes/edges | existing graph annotations still pass |
| `persistent-tree` | versioned graph | version roots plus shared nodes | parser accepts kind |

## Runtime States

| State | Trigger | Rendering/Metadata Rule | Acceptance Evidence |
|---|---|---|---|
| `null-edge` | edge target is `0` or `null` | target is a visible null marker only when referenced | existing null event behavior still passes |
| `self-loop` | an edge goes from a node to itself | edge metadata contains `visual-state=self-loop` | projection test covers self-loop |
| `cycle` | a directed edge participates in a cycle with more than one node | edge metadata contains `visual-state=cycle`; layout does not need a separate cycle template | projection test covers `a -> b -> c -> a` |
| `shared-node` | a node has more than one incoming non-null edge | target node metadata contains `visual-state=shared-node` | projection test covers two parents to one child |
| `rewire` | a later edge event replaces an earlier edge with the same role/from but different target | new edge metadata contains `visual-state=rewire` and old edge is gone | projection test covers `a.next=b` then `a.next=c` |
| `auxiliary-edge` | template-defined edge that should not drive layout, such as `prev` | edge metadata contains `edge-role=auxiliary` | doubly-list test covers prev |
| `primary-edge` | template-defined edge that drives layout | edge metadata contains `edge-role=primary` | list/tree/hash tests cover primary edges |

## Annotation Examples

```c
// @visual root=head kind=singly-list label=value
// @visual root=head kind=doubly-list label=value
// @visual root=head kind=lru-list label=key
// @visual root=root kind=binary-tree label=value
// @visual root=buckets kind=hash-chain-table label=key next=next
// @visual root=heap kind=heap
// @visual root=bit kind=fenwick-tree
```

## Detailed Execution Plan And Gates

### Step 1: SPEC Artifact

**Files**

- Create `docs/superpowers/specs/2026-06-04-visual-template-state-system.md`

**Tests**

- No production test is required for the Markdown artifact.

**Acceptance**

- The SPEC explicitly separates underlying shapes, user templates, and runtime states.
- The SPEC includes detailed implementation steps and validation commands.
- The SPEC states that cycle-like behavior is a runtime state, while templates such as `doubly-list`, `lru-list`, `hash-chain-table`, `stack`, `queue`, and `heap` are exposed to reduce configuration.

### Step 2: Parser And Kind Normalization

**Files**

- Modify `src/main/java/minic/runtime/debug/visual/VisualKind.java`
- Modify `src/main/java/minic/runtime/debug/visual/VisualAnnotationParser.java`
- Modify `src/test/java/minic/runtime/debug/visual/VisualAnnotationParserTest.java`

**TDD**

1. Add failing parser tests for new template kinds in lowercase, kebab-case, and legacy underscore forms.
2. Add failing tests proving `doubly-list`, `lru-list`, `binary-tree`, `hash-chain-table`, `stack`, `queue`, `heap`, and `fenwick-tree` parse without warnings.
3. Implement enum values and normalization.

**Validation Command**

```powershell
.\gradlew.bat test --tests minic.runtime.debug.visual.VisualAnnotationParserTest
```

**Acceptance**

- New user templates parse into stable `VisualKind` constants.
- Existing `struct-list`, `pointer-array`, and uppercase/underscore spellings remain backward compatible.
- Parser does not require `next` for templates that have a default `next=next`.

### Step 3: Template Projection Defaults

**Files**

- Modify `src/main/java/minic/runtime/debug/visual/VisualProjectionBuilder.java`
- Modify `src/test/java/minic/runtime/debug/visual/VisualProjectionBuilderTest.java`

**TDD**

1. Add failing projection tests for `singly-list`, `doubly-list`, `lru-list`, `binary-tree`, `hash-chain-table`, `heap`, and `fenwick-tree`.
2. Implement list projection from default `next`.
3. Implement auxiliary `prev` edges for doubly/LRU lists.
4. Implement binary tree projection from default `left/right`.
5. Implement hash-chain-table as bucketed graph from pointer-array buckets.
6. Add heap parent/child metadata and Fenwick coverage metadata to sequence cells.

**Validation Command**

```powershell
.\gradlew.bat test --tests minic.runtime.debug.visual.VisualProjectionBuilderTest
```

**Acceptance**

- `kind=doubly-list` needs no explicit `next` or `prev` attributes.
- `kind=lru-list` marks root node as head and terminal node as tail.
- `kind=binary-tree` needs no explicit `left/right` attributes.
- `kind=hash-chain-table` produces a graph with `layoutHint=bucketed`, bucket nodes, chain nodes, `bucketIndex`, and `chainDepth`.
- `kind=heap` array cells expose `parentIndex`, `leftIndex`, and `rightIndex`.
- `kind=fenwick-tree` array cells expose `rangeStart` and `rangeEnd`.

### Step 4: Runtime State Inference

**Files**

- Modify `src/main/java/minic/runtime/debug/visual/VisualProjectionBuilder.java`
- Modify `src/test/java/minic/runtime/debug/visual/VisualProjectionBuilderTest.java`

**TDD**

1. Add failing tests for self-loop, multi-node cycle, shared node, and rewire.
2. Implement state inference when graph structures are built.
3. Preserve existing null-edge behavior.

**Validation Command**

```powershell
.\gradlew.bat test --tests minic.runtime.debug.visual.VisualProjectionBuilderTest
```

**Acceptance**

- Self-loop edge has `visual-state=self-loop`.
- Multi-node cycle edge has `visual-state=cycle`.
- Shared target node has `visual-state=shared-node`.
- Rewired edge replaces the old edge and has `visual-state=rewire`.
- States are metadata on the same graph, not separate templates.

### Step 5: UI DTO And JavaFX Layout

**Files**

- Modify `src/main/java/minic/uiapi/debug/UiDebugDataStructureViewBuilder.java`
- Modify `src/main/java/minic/ui/debug/MiniCDebugPane.java`
- Modify `src/test/java/minic/uiapi/UiDebugDataStructureViewBuilderTest.java`
- Modify `src/test/java/minic/ui/MiniCDebugPaneTest.java`

**TDD**

1. Add failing UI API tests proving template-specific metadata survives DTO mapping.
2. Add failing JavaFX tests proving bucketed graphs are not rendered as one horizontal chain.
3. Implement template element explanations and layout selection using kind/layout metadata.

**Validation Command**

```powershell
.\gradlew.bat test --tests minic.uiapi.UiDebugDataStructureViewBuilderTest --tests minic.ui.MiniCDebugPaneTest
```

**Acceptance**

- Hash-chain-table nodes include enough metadata for buckets-horizontal/chains-vertical rendering.
- `MiniCDebugPane` uses bucketed positioning for `hash-chain-table` and `adjacency-list`.
- Graph states appear as concise metadata/tooltips rather than repeated visible prose.

### Step 6: End-To-End Samples And Full Regression

**Files**

- Add or update samples under `samples/`
- Modify `src/test/java/minic/uiapi/UiDebugDataStructureEndToEndTest.java`

**TDD**

1. Add sample coverage for binary tree, hash-chain-table, LRU/doubly list, heap, and Fenwick.
2. Verify parser, debug startup, projection, and UI API summaries for each sample.

**Validation Commands**

```powershell
.\gradlew.bat test --tests minic.uiapi.UiDebugDataStructureEndToEndTest
.\gradlew.bat test
```

**Acceptance**

- All new samples debug successfully.
- No projection warning appears for valid annotations.
- Full Gradle test suite passes.

## Redo Rule

For each implementation step:

1. Write or update the failing test first.
2. Run the exact validation command and confirm the test fails for the expected missing behavior.
3. Implement only that step's production change.
4. Run the validation command again.
5. If it fails, fix within the same step and rerun.
6. If the same failure persists after two fixes, reduce the change to the smallest failing behavior and rebuild the step from that test.
7. Do not proceed to the next step until the current step passes.

## Completion Checklist

- [ ] SPEC exists and matches the visual-effect-first model.
- [ ] Parser accepts all required user templates.
- [ ] Projection supplies default configuration for high-value templates.
- [ ] Runtime states are inferred without requiring the user to declare them.
- [ ] UI keeps data-structure cards concise and renders bucketed graphs vertically under buckets.
- [ ] End-to-end samples cover tree, hash table, LRU/doubly list, heap, and Fenwick.
- [ ] All targeted validation commands pass.
- [ ] `.\gradlew.bat test` passes.
