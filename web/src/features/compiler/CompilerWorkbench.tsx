import { Play, StepForward } from "lucide-react";
import { useState } from "react";

import type { CompileSnapshotResponse, MiniCClient, RealtimeAnalysisResponse } from "../../api/minicClient";
import { SourceEditor } from "../editor/SourceEditor";
import { VisualRenderer } from "../visual/VisualRenderer";

export type CompilerClient = Pick<
  MiniCClient,
  "analyzeRealtime" | "createCompileSession" | "getCompileSnapshot" | "runCompileCommand" | "startCompileSession"
>;

type CompilerWorkbenchProps = {
  client: CompilerClient;
  onStatusChange?: (status: string) => void;
};

export function CompilerWorkbench({ client, onStatusChange }: CompilerWorkbenchProps) {
  const [sourceText, setSourceText] = useState("int main() { return 0; }");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [snapshot, setSnapshot] = useState<CompileSnapshotResponse | null>(null);
  const [analysis, setAnalysis] = useState<RealtimeAnalysisResponse | null>(null);
  const [busy, setBusy] = useState(false);

  const startObservation = async () => {
    setBusy(true);
    try {
      const realtime = await client.analyzeRealtime({ sourceName: "main.mc", sourceText, version: Date.now() });
      setAnalysis(realtime);
      const created = await client.createCompileSession({ sourceName: "main.mc", sourceText });
      const nextSnapshot = await client.startCompileSession(created.sessionId);
      setSessionId(created.sessionId);
      setSnapshot(nextSnapshot);
      onStatusChange?.(`Compile: ${nextSnapshot.state.currentStage}`);
    } finally {
      setBusy(false);
    }
  };

  const nextStep = async () => {
    if (sessionId == null) {
      return;
    }
    setBusy(true);
    try {
      await client.runCompileCommand(sessionId, "next");
      const nextSnapshot = await client.getCompileSnapshot(sessionId);
      setSnapshot(nextSnapshot);
      onStatusChange?.(`Compile: ${nextSnapshot.state.currentStage}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="workbench-grid" data-testid="compiler-workbench">
      <SourceEditor
        diagnostics={analysis?.diagnostics}
        sourceText={sourceText}
        onSourceTextChange={setSourceText}
      />
      <section className="control-panel" aria-label="Compiler controls">
        <button className="command-button" disabled={busy} type="button" onClick={() => void startObservation()}>
          <Play aria-hidden="true" size={16} />
          <span>Start observation</span>
        </button>
        <button className="command-button" disabled={busy || sessionId == null} type="button" onClick={() => void nextStep()}>
          <StepForward aria-hidden="true" size={16} />
          <span>Next</span>
        </button>
        <dl className="inspector-list">
          <dt>Stage</dt>
          <dd data-testid="current-stage">{snapshot?.state.currentStage ?? "source"}</dd>
          <dt>Diagnostics</dt>
          <dd>{analysis?.diagnostics.length ?? 0}</dd>
        </dl>
      </section>
      <VisualRenderer mode="compiler" snapshot={snapshot} />
      <section className="bottom-panel" aria-label="Compiler diagnostics">
        {(snapshot?.stage.diagnostics ?? analysis?.diagnostics ?? []).map((diagnostic) => (
          <p key={`${diagnostic.code}-${String(diagnostic.startOffset)}`}>{diagnostic.message}</p>
        ))}
      </section>
    </div>
  );
}
