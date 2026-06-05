# MiniC Testing Strategy

MiniC keeps the default development suite intentionally small. The current budget is no more than 50 counted JUnit test annotations under `src/test/java`.

The suite favors workflow-level regression tests over one-assertion micro-tests. Each `@Test` may cover a named matrix of related scenarios, and each assertion should include enough context for a coarse failure to stay diagnosable.

Current ownership:

- Compiler tests cover preprocessing, lexing, parsing, semantic analysis, full pipeline behavior, IR lowering, Windows x64 code generation, and MSVC toolchain command behavior.
- Runtime tests cover IR debug execution, breakpoints, reverse stepping controls, data-flow events, typed memory graphs, visual projections, stage stepping, and observation sessions.
- UI tests cover the UI API boundary, workbench shell/controller/view-model/control flows, editor diagnostics, viewport control, reusable text styles, and IR/assembly highlighting.
- Utility tests cover CLI behavior, project identity, source mapping, diagnostics, samples, and theme CSS smoke behavior.

When adding coverage, prefer extending an existing scenario matrix inside one of the regression suites. Add a new `@Test` only when the behavior is a separate workflow and the total count remains at or below 50.
