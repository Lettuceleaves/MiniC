import { Suspense, lazy } from "react";

import type { RealtimeAnalysisResponse } from "../../api/minicClient";
import { useViewportTarget } from "../viewport/useViewportTarget";

const MonacoEditor = lazy(() => import("@monaco-editor/react"));

type SourceEditorProps = {
  diagnostics?: RealtimeAnalysisResponse["diagnostics"] | undefined;
  onSourceTextChange: (sourceText: string) => void;
  sourceText: string;
};

export function SourceEditor({ diagnostics = [], onSourceTextChange, sourceText }: SourceEditorProps) {
  const forceTextarea = new URLSearchParams(globalThis.location.search).get("editor") === "textarea";
  const useMonaco = !forceTextarea && typeof ResizeObserver !== "undefined" && typeof Worker !== "undefined";
  const viewportTarget = useViewportTarget("source");

  return (
    <section className="source-editor" aria-label="Source editor" tabIndex={0} {...viewportTarget}>
      <div className="panel-toolbar">
        <span>main.mc</span>
        <span>{diagnostics.length} diagnostics</span>
      </div>
      {useMonaco ? (
        <Suspense fallback={<FallbackEditor sourceText={sourceText} />}>
          <MonacoEditor
            height="100%"
            language="c"
            theme="vs-dark"
            value={sourceText}
          options={{
            ariaLabel: "MiniC source",
            fontSize: 14,
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            }}
            onChange={(value) => {
              onSourceTextChange(value ?? "");
            }}
          />
        </Suspense>
      ) : (
        <textarea
          className="source-textarea"
          aria-label="MiniC source"
          spellCheck={false}
          value={sourceText}
          onChange={(event) => {
            onSourceTextChange(event.currentTarget.value);
          }}
        />
      )}
    </section>
  );
}

function FallbackEditor({ sourceText }: { sourceText: string }) {
  return (
    <pre className="source-textarea" aria-hidden="true">
      {sourceText}
    </pre>
  );
}
