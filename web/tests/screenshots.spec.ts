import { expect, test, type Page } from "@playwright/test";

test.describe("Workbench screenshots", () => {
  test("workbench-initial-1440x900", async ({ page }) => {
    await prepareMockApi(page, "lexer");
    await page.setViewportSize({ height: 900, width: 1440 });
    await page.goto("/");
    await expect(page.getByTestId("workbench-shell")).toBeVisible();
    await expect(page).toHaveScreenshot("workbench-initial-1440x900.png");
  });

  for (const stage of ["lexer", "parser", "semantic", "codegen", "execution"] as const) {
    test(`compile-${stage}-1440x900`, async ({ page }) => {
      await prepareMockApi(page, stage);
      await page.setViewportSize({ height: 900, width: 1440 });
      await page.goto("/?editor=textarea");
      await page.getByRole("button", { name: "Start observation" }).click();
      await expect(page.getByTestId("current-stage")).toHaveText(stage);
      await expect(page.getByTestId("workbench-shell")).toBeVisible();
      await expect(page).toHaveScreenshot(`compile-${stage}-1440x900.png`);
    });
  }

  for (const view of ["metadata", "ir", "asm", "data-structure"] as const) {
    test(`debug-${view}-1440x900`, async ({ page }) => {
      await prepareMockApi(page, "parser");
      await page.setViewportSize({ height: 900, width: 1440 });
      await page.goto("/?editor=textarea");
      await page.getByRole("tab", { name: "Debug" }).click();
      await page.getByRole("button", { name: "Start debug" }).click();
      await page.getByRole("button", { name: "Step over" }).click();
      await expect(page.getByTestId("active-graph-node")).toBeVisible();
      await expect(page).toHaveScreenshot(`debug-${view}-1440x900.png`);
    });
  }

  test("settings-1440x900", async ({ page }) => {
    await prepareMockApi(page, "lexer");
    await page.setViewportSize({ height: 900, width: 1440 });
    await page.goto("/");
    await page.getByRole("tab", { name: "Settings" }).click();
    await expect(page.getByRole("button", { name: "Save settings" })).toBeVisible();
    await expect(page).toHaveScreenshot("settings-1440x900.png");
  });

  test("error-diagnostics-1440x900", async ({ page }) => {
    await prepareMockApi(page, "lexer", true);
    await page.setViewportSize({ height: 900, width: 1440 });
    await page.goto("/?editor=textarea");
    await page.getByRole("button", { name: "Start observation" }).click();
    await expect(page.getByText("parser expected ')'")).toBeVisible();
    await expect(page).toHaveScreenshot("error-diagnostics-1440x900.png");
  });

  for (const viewport of [
    { height: 768, name: "workbench-1024x768", width: 1024 },
    { height: 844, name: "workbench-390x844", width: 390 },
    { height: 844, name: "debug-390x844", width: 390 },
  ] as const) {
    test(viewport.name, async ({ page }) => {
      await prepareMockApi(page, "lexer");
      await page.setViewportSize({ height: viewport.height, width: viewport.width });
      await page.goto("/?editor=textarea");
      if (viewport.name.startsWith("debug")) {
        await page.getByRole("tab", { name: "Debug" }).click();
        await page.getByRole("button", { name: "Start debug" }).click();
      }
      await expect(page.getByTestId("workbench-shell")).toBeVisible();
      await expect(page).toHaveScreenshot(`${viewport.name}.png`);
    });
  }
});

async function prepareMockApi(page: Page, stage: string, diagnostics = false): Promise<void> {
  await page.route("**/api/analysis/realtime", async (route) => {
    await route.fulfill({
      json: {
        diagnostics: diagnostics ? [{
          code: "P001",
          endOffset: 10,
          message: "parser expected ')'",
          severity: "ERROR",
          sourceName: "main.mc",
          startOffset: 4,
        }] : [],
        sourceName: "main.mc",
        sourceText: "",
        tokens: [],
        version: 1,
      },
    });
  });
  await page.route("**/api/compile/sessions", async (route) => {
    await route.fulfill({ json: { sessionId: "c1", version: 1 }, status: 201 });
  });
  await page.route("**/api/compile/sessions/c1/start", async (route) => {
    await route.fulfill({ json: compileSnapshot(stage, diagnostics) });
  });
  await page.route("**/api/compile/sessions/c1/commands/next", async (route) => {
    await route.fulfill({ json: { description: "advanced", diagnostics: [], outcome: "OK", stage, title: "next" } });
  });
  await page.route("**/api/compile/sessions/c1/snapshot", async (route) => {
    await route.fulfill({ json: compileSnapshot(stage, diagnostics) });
  });
  await page.route("**/api/debug-sessions", async (route) => {
    await route.fulfill({ json: { sessionId: "d1", version: 1 }, status: 201 });
  });
  await page.route("**/api/debug-sessions/d1/start", async (route) => {
    await route.fulfill({ json: debugSnapshot("READY") });
  });
  await page.route("**/api/debug-sessions/d1/step-over", async (route) => {
    await route.fulfill({ json: debugSnapshot("RUNNING").state });
  });
  await page.route("**/api/debug-sessions/d1/snapshot", async (route) => {
    await route.fulfill({ json: debugSnapshot("RUNNING") });
  });
  await page.route("**/api/settings", async (route) => {
    if (route.request().method() === "PATCH") {
      await route.fulfill({ json: { frameIntervalMillis: 1000, theme: "dark", uiScale: 1 } });
      return;
    }
    await route.fulfill({ json: { frameIntervalMillis: 1000, theme: "dark", uiScale: 1 } });
  });
  await page.route("**/api/settings/themes", async (route) => {
    await route.fulfill({ json: { currentTheme: "dark", themes: ["dark", "light"] } });
  });
}

function compileSnapshot(stage: string, diagnostics = false) {
  return {
    global: {
      artifactSummary: [],
      assemblySummary: ["mov eax, 0"],
      astSummary: ["Function main"],
      diagnostics: [],
      executionInputSummary: [],
      executionOutputSummary: ["exit 0"],
      irSummary: ["ret 0"],
      preprocessSummary: ["main.mc"],
      semanticSummary: ["global scope"],
      source: "int main() { return 0; }",
      stageSummaries: [stage],
      tokenSummary: ["return"],
    },
    stage: {
      accumulatedOutput: [],
      completed: false,
      completedSteps: 1,
      currentItem: "return",
      diagnostics: diagnostics ? [{
        code: "P001",
        endOffset: 10,
        message: "parser expected ')'",
        severity: "ERROR",
        sourceName: "main.mc",
        startOffset: 4,
      }] : [],
      inputSummary: [],
      stage,
      totalSteps: 2,
    },
    state: {
      canNext: true,
      canPause: false,
      canPlay: true,
      canPlayFast: true,
      canPrevious: false,
      canReversePlay: false,
      currentStage: stage,
      description: stage,
      diagnostics: [],
      frameIntervalMillis: 1000,
      globalStepIndex: 1,
      playbackMode: "paused",
      sourceName: "main.mc",
      stageStepIndex: 1,
      title: stage,
    },
    visual: {
      assemblyLines: [],
      astRoot: stage === "parser" || stage === "semantic" ? {
        active: true,
        children: [],
        id: "root",
        kind: "Function",
        label: "main",
      } : undefined,
      genericItems: ["return", "main"],
      irLines: [],
      lexerTokens: [{
        active: true,
        endColumn: 10,
        endLine: 1,
        endOffset: 10,
        kind: "RETURN",
        startColumn: 4,
        startLine: 1,
        startOffset: 4,
        text: "return",
      }],
      semanticEdgesPointChildToParent: false,
      sourceText: "int main() { return 0; }",
      stage,
      visualType: stage,
    },
  };
}

function debugSnapshot(executionState: string) {
  return {
    asm: { explanation: "", lines: [], relatedIrIds: [] },
    ast: { relatedAsmIds: [], relatedIrIds: [], root: { active: true, children: [], id: "root", kind: "Function", label: "main" } },
    dataStructure: { processSpace: emptyProcessSpace(), visuals: [], warnings: [] },
    ir: { currentInstructionId: "i1", explanation: "", lines: [], operands: [] },
    metadata: {
      breakpoints: [{ enabled: true, line: 1 }],
      callStack: [],
      currentFunction: "main",
      events: [],
      executionState,
      stderr: "",
      stdout: "",
      stopReason: "STEP",
      timeline: [],
      variables: [],
    },
    state: {
      breakpoints: [{ enabled: true, line: 1 }],
      currentSnapshot: {
        blockLabel: "entry",
        breakpointHit: false,
        callStackSummary: [],
        functionName: "main",
        instructionId: "i1",
        processSpace: emptyProcessSpace(),
        snapshotId: 1,
        sourceRange: { endColumn: 12, endLine: 1, endOffset: 12, sourceName: "debug.mc", startColumn: 4, startLine: 1, startOffset: 4 },
        stopReason: "STEP",
        visibleStepIndex: 1,
      },
      events: [],
      executionState,
      snapshots: [],
      sourceName: "debug.mc",
    },
  };
}

function emptyProcessSpace() {
  return {
    currentFunctionName: "main",
    currentInstructionId: "i1",
    functions: ["main"],
    heapValues: [],
    stackFrames: [],
    staticValues: [],
    stderr: "",
    stdin: "",
    stdout: "",
  };
}
