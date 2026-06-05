import { Bug, Crosshair, StepForward } from "lucide-react";
import { useState } from "react";

import type { DebugSnapshotResponse, MiniCClient } from "../../api/minicClient";
import { SourceEditor } from "../editor/SourceEditor";
import { VisualRenderer } from "../visual/VisualRenderer";

export type DebuggerClient = Pick<
  MiniCClient,
  "addDebugBreakpoint" | "createDebugSession" | "getDebugSnapshot" | "runDebugCommand" | "startDebugSession"
>;

type DebuggerWorkbenchProps = {
  client: DebuggerClient;
  onStatusChange?: (status: string) => void;
};

export function DebuggerWorkbench({ client, onStatusChange }: DebuggerWorkbenchProps) {
  const [sourceText, setSourceText] = useState("int main() { int x = 1; return x; }");
  const [breakpointLine, setBreakpointLine] = useState(1);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [snapshot, setSnapshot] = useState<DebugSnapshotResponse | null>(null);
  const [busy, setBusy] = useState(false);

  const startDebug = async () => {
    setBusy(true);
    try {
      const created = await client.createDebugSession({ sourceName: "debug.mc", sourceText });
      const nextSnapshot = await client.startDebugSession(created.sessionId);
      setSessionId(created.sessionId);
      setSnapshot(nextSnapshot);
      onStatusChange?.(`Debug: ${nextSnapshot.state.executionState}`);
    } finally {
      setBusy(false);
    }
  };

  const setBreakpoint = async () => {
    if (sessionId == null) {
      return;
    }
    await client.addDebugBreakpoint(sessionId, breakpointLine);
    const nextSnapshot = await client.getDebugSnapshot(sessionId);
    setSnapshot(nextSnapshot);
  };

  const stepOver = async () => {
    if (sessionId == null) {
      return;
    }
    await client.runDebugCommand(sessionId, "step-over");
    const nextSnapshot = await client.getDebugSnapshot(sessionId);
    setSnapshot(nextSnapshot);
    onStatusChange?.(`Debug: ${nextSnapshot.state.executionState}`);
  };

  return (
    <div className="workbench-grid" data-testid="debugger-workbench">
      <SourceEditor sourceText={sourceText} onSourceTextChange={setSourceText} />
      <section className="control-panel" aria-label="Debug controls">
        <button className="command-button" disabled={busy} type="button" onClick={() => void startDebug()}>
          <Bug aria-hidden="true" size={16} />
          <span>Start debug</span>
        </button>
        <label className="number-field">
          <span>Line</span>
          <input
            aria-label="Breakpoint line"
            min={1}
            type="number"
            value={breakpointLine}
            onChange={(event) => {
              setBreakpointLine(Number(event.currentTarget.value));
            }}
          />
        </label>
        <button
          className="command-button"
          disabled={sessionId == null}
          type="button"
          onClick={() => void setBreakpoint()}
        >
          <Crosshair aria-hidden="true" size={16} />
          <span>Set breakpoint</span>
        </button>
        <button className="command-button" disabled={sessionId == null} type="button" onClick={() => void stepOver()}>
          <StepForward aria-hidden="true" size={16} />
          <span>Step over</span>
        </button>
        <dl className="inspector-list">
          <dt>State</dt>
          <dd data-testid="debug-state">{snapshot?.state.executionState ?? "idle"}</dd>
          <dt>Breakpoints</dt>
          <dd>{snapshot?.state.breakpoints.length ?? 0}</dd>
        </dl>
      </section>
      <VisualRenderer mode="debugger" snapshot={snapshot} />
      <section className="bottom-panel" aria-label="Debug events">
        {(snapshot?.state.events ?? []).map((event) => (
          <p key={event.eventId}>{event.title}</p>
        ))}
      </section>
    </div>
  );
}
