import { expect, test } from "@playwright/test";

test("creates compile and debug sessions from the Workbench", async ({ page }) => {
  await page.route("**/api/analysis/realtime", async (route) => {
    await route.fulfill({ json: { diagnostics: [], sourceName: "main.mc", sourceText: "", tokens: [], version: 1 } });
  });
  await page.route("**/api/compile/sessions", async (route) => {
    await route.fulfill({ json: { sessionId: "c1", version: 1 }, status: 201 });
  });
  await page.route("**/api/compile/sessions/c1/start", async (route) => {
    await route.fulfill({ json: compileSnapshot("lexer") });
  });
  await page.route("**/api/compile/sessions/c1/commands/next", async (route) => {
    await route.fulfill({ json: { description: "advanced", diagnostics: [], outcome: "OK", stage: "parser", title: "next" } });
  });
  await page.route("**/api/compile/sessions/c1/snapshot", async (route) => {
    await route.fulfill({ json: compileSnapshot("parser") });
  });
  await page.route("**/api/debug-sessions", async (route) => {
    await route.fulfill({ json: { sessionId: "d1", version: 1 }, status: 201 });
  });
  await page.route("**/api/debug-sessions/d1/start", async (route) => {
    await route.fulfill({ json: debugSnapshot("READY") });
  });
  await page.route("**/api/debug-sessions/d1/breakpoints/1", async (route) => {
    await route.fulfill({ json: debugSnapshot("BREAKPOINT").state });
  });
  await page.route("**/api/debug-sessions/d1/step-over", async (route) => {
    await route.fulfill({ json: debugSnapshot("RUNNING").state });
  });
  await page.route("**/api/debug-sessions/d1/snapshot", async (route) => {
    await route.fulfill({ json: debugSnapshot("RUNNING") });
  });

  await page.goto("/?editor=textarea");
  await page.getByRole("tab", { name: "Code" }).click();
  await page.getByRole("textbox", { name: "MiniC source" }).fill("int main() { return 0; }");
  await page.getByRole("button", { name: "Start observation" }).click();
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByTestId("current-stage")).toHaveText("parser");

  await page.getByRole("tab", { name: "Debug" }).click();
  await page.getByRole("button", { name: "Start debug" }).click();
  await page.getByRole("button", { name: "Set breakpoint" }).click();
  await page.getByRole("button", { name: "Step over" }).click();
  await expect(page.getByTestId("debug-state")).toHaveText("RUNNING");
});

function compileSnapshot(stage: string) {
  return {
    global: {
      artifactSummary: [],
      assemblySummary: [],
      astSummary: [],
      diagnostics: [],
      executionInputSummary: [],
      executionOutputSummary: [],
      irSummary: [],
      preprocessSummary: [],
      semanticSummary: [],
      source: "int main() { return 0; }",
      stageSummaries: [stage],
      tokenSummary: ["return"],
    },
    stage: {
      accumulatedOutput: [],
      completed: false,
      completedSteps: 1,
      currentItem: "return",
      diagnostics: [],
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
      genericItems: ["return"],
      irLines: [],
      lexerTokens: [],
      semanticEdgesPointChildToParent: false,
      sourceText: "int main() { return 0; }",
      stage,
      visualType: stage,
    },
  };
}

function debugSnapshot(executionState: string) {
  return {
    asm: { lines: [] },
    ast: {},
    dataStructure: { processSpace: emptyProcessSpace(), visualStructure: { edges: [], elements: [] } },
    ir: { lines: [] },
    metadata: { timeline: [] },
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
    code: { segments: [] },
    heap: { blocks: [], segments: [] },
    io: { segments: [] },
    stack: { frames: [], segments: [] },
    staticData: { segments: [] },
  };
}
