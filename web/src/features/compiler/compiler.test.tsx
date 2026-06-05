import "@testing-library/jest-dom/vitest";

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";

import { CompilerWorkbench, type CompilerClient } from "./CompilerWorkbench";
import { VisualRenderer } from "../visual/VisualRenderer";
import type { CompileSnapshotResponse } from "../../api/minicClient";

describe("CompilerWorkbench", () => {
  afterEach(() => {
    cleanup();
  });

  test("starts observation and advances to the next snapshot", async () => {
    const client: CompilerClient = {
      analyzeRealtime: vi.fn<CompilerClient["analyzeRealtime"]>().mockResolvedValue({
        diagnostics: [],
        sourceName: "main.mc",
        sourceText: "int main() { return 0; }",
        tokens: [],
        version: 1,
      }),
      createCompileSession: vi.fn<CompilerClient["createCompileSession"]>().mockResolvedValue({
        sessionId: "c1",
        version: 1,
      }),
      getCompileSnapshot: vi.fn<CompilerClient["getCompileSnapshot"]>().mockResolvedValue(compileSnapshot("parser")),
      runCompileCommand: vi.fn<CompilerClient["runCompileCommand"]>().mockResolvedValue({
        description: "advanced",
        diagnostics: [],
        outcome: "OK",
        stage: "parser",
        title: "next",
      }),
      startCompileSession: vi.fn<CompilerClient["startCompileSession"]>().mockResolvedValue(compileSnapshot("lexer")),
    };

    render(<CompilerWorkbench client={client} />);

    fireEvent.change(screen.getByRole("textbox", { name: "MiniC source" }), {
      target: { value: "int main() { return 1; }" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Start observation" }));

    expect(await screen.findByTestId("current-stage")).toHaveTextContent("lexer");

    fireEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => expect(screen.getByTestId("current-stage")).toHaveTextContent("parser"));
    expect(client.runCompileCommand).toHaveBeenCalledWith("c1", "next");
  });

  test("renders empty and populated compiler visuals", () => {
    const { rerender } = render(<VisualRenderer mode="compiler" snapshot={null} />);

    expect(screen.getByTestId("visual-empty")).toBeInTheDocument();

    rerender(<VisualRenderer mode="compiler" snapshot={compileSnapshot("lexer")} />);

    expect(screen.getByTestId("visual-type")).toHaveTextContent("lexer");
    expect(screen.getByText("return")).toBeInTheDocument();
  });
});

export function compileSnapshot(stage: string): CompileSnapshotResponse {
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
      lexerTokens: [
        {
          active: false,
          endColumn: 10,
          endLine: 1,
          endOffset: 10,
          kind: "RETURN",
          startColumn: 4,
          startLine: 1,
          startOffset: 4,
          text: "return",
        },
      ],
      semanticEdgesPointChildToParent: false,
      sourceText: "int main() { return 0; }",
      stage,
      visualType: stage,
    },
  };
}
