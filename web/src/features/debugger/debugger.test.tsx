import "@testing-library/jest-dom/vitest";

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";

import { DebuggerWorkbench, type DebuggerClient } from "./DebuggerWorkbench";
import { VisualRenderer } from "../visual/VisualRenderer";
import type { DebugSnapshotResponse } from "../../api/minicClient";

describe("DebuggerWorkbench", () => {
  afterEach(() => {
    cleanup();
  });

  test("starts debug, sets a breakpoint, and steps", async () => {
    const client: DebuggerClient = {
      addDebugBreakpoint: vi.fn<DebuggerClient["addDebugBreakpoint"]>().mockResolvedValue(debugSnapshot("BREAKPOINT").state),
      createDebugSession: vi.fn<DebuggerClient["createDebugSession"]>().mockResolvedValue({
        sessionId: "d1",
        version: 1,
      }),
      getDebugSnapshot: vi.fn<DebuggerClient["getDebugSnapshot"]>()
        .mockResolvedValueOnce(debugSnapshot("BREAKPOINT"))
        .mockResolvedValueOnce(debugSnapshot("RUNNING")),
      runDebugCommand: vi.fn<DebuggerClient["runDebugCommand"]>().mockResolvedValue(debugSnapshot("RUNNING").state),
      startDebugSession: vi.fn<DebuggerClient["startDebugSession"]>().mockResolvedValue(debugSnapshot("READY")),
    };

    render(<DebuggerWorkbench client={client} />);

    fireEvent.click(screen.getByRole("button", { name: "Start debug" }));
    expect(await screen.findByTestId("debug-state")).toHaveTextContent("READY");

    fireEvent.click(screen.getByRole("button", { name: "Set breakpoint" }));
    await waitFor(() => {
      expect(client.addDebugBreakpoint).toHaveBeenCalledWith("d1", 1);
    });

    fireEvent.click(screen.getByRole("button", { name: "Step over" }));
    await waitFor(() => expect(screen.getByTestId("debug-state")).toHaveTextContent("RUNNING"));
  });

  test("renders empty and populated debugger visuals", () => {
    const { rerender } = render(<VisualRenderer mode="debugger" snapshot={null} />);

    expect(screen.getByTestId("visual-empty")).toBeInTheDocument();

    rerender(<VisualRenderer mode="debugger" snapshot={debugSnapshot("READY")} />);

    expect(screen.getByTestId("visual-graph")).toHaveTextContent("STEP");
  });
});

export function debugSnapshot(executionState: string): DebugSnapshotResponse {
  return {
    asm: { explanation: "", lines: [], relatedIrIds: [] },
    ast: {
      relatedAsmIds: [],
      relatedIrIds: [],
      root: { active: false, children: [], id: "root", kind: "Function", label: "main" },
    },
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
