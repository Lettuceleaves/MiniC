import { Suspense, lazy } from "react";
import type { Edge, Node } from "@xyflow/react";

import type { CompileSnapshotResponse, DebugSnapshotResponse } from "../../api/minicClient";

const FlowCanvas = lazy(async () => {
  const { Background, Controls, ReactFlow } = await import("@xyflow/react");
  return {
    default: function LazyFlowCanvas({ edges, nodes }: { edges: Edge[]; nodes: Node[] }) {
      return (
        <ReactFlow edges={edges} fitView nodes={nodes} nodesDraggable={false}>
          <Background />
          <Controls showInteractive={false} />
        </ReactFlow>
      );
    },
  };
});

type VisualRendererProps =
  | { mode: "compiler"; snapshot?: CompileSnapshotResponse | null }
  | { mode: "debugger"; snapshot?: DebugSnapshotResponse | null };

export function VisualRenderer(props: VisualRendererProps) {
  if (props.snapshot == null) {
    return (
      <section className="visual-renderer" data-testid="visual-empty" aria-label="Visual output">
        <div className="empty-state">No session</div>
      </section>
    );
  }

  if (props.mode === "compiler") {
    const visual = props.snapshot.visual;
    return (
      <section className="visual-renderer" aria-label="Compiler visual output">
        <div className="panel-toolbar">
          <span data-testid="visual-type">{visual.visualType}</span>
          <span>{visual.lexerTokens.length} tokens</span>
        </div>
        {visual.astRoot != null ? (
          <GraphView rootLabel={visual.astRoot.label} rootKind={visual.astRoot.kind} />
        ) : (
          <ol className="visual-list">
            {(visual.genericItems.length > 0 ? visual.genericItems : visual.lexerTokens.map((token) => token.text))
              .slice(0, 20)
              .map((item, index) => (
                <li key={`${item}-${String(index)}`}>{item}</li>
              ))}
          </ol>
        )}
      </section>
    );
  }

  const state = props.snapshot.state;
  return (
    <section className="visual-renderer" aria-label="Debugger visual output">
      <div className="panel-toolbar">
        <span>{state.executionState}</span>
        <span>{state.breakpoints.length} breakpoints</span>
      </div>
      <GraphView rootLabel={state.currentSnapshot.functionName} rootKind={state.currentSnapshot.stopReason} />
    </section>
  );
}

function GraphView({ rootKind, rootLabel }: { rootKind: string; rootLabel: string }) {
  const canRenderFlow = typeof ResizeObserver !== "undefined";
  const nodes: Node[] = [
    {
      data: { label: `${rootKind}: ${rootLabel}` },
      id: "root",
      position: { x: 80, y: 60 },
    },
  ];
  const edges: Edge[] = [];

  if (!canRenderFlow) {
    return (
      <div className="graph-fallback" data-testid="visual-graph">
        {rootKind}: {rootLabel}
      </div>
    );
  }

  return (
    <div className="graph-canvas" data-testid="visual-graph">
      <Suspense fallback={<div className="graph-fallback">{rootLabel}</div>}>
        <FlowCanvas edges={edges} nodes={nodes} />
      </Suspense>
    </div>
  );
}
