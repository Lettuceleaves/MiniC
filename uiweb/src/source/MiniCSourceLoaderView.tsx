import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { MiniCCodeEditor } from "../editor/MiniCCodeEditor";
import type { MiniCViewportAdapter } from "../control/MiniCViewportAdapter";
import type { MiniCWorkbenchControlHub } from "../control/MiniCWorkbenchControlHub";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiSourceSpanDto } from "../translation/uiapi";
import type { UiRealtimeAnalysisDto as EditorRealtimeAnalysisDto } from "../translation/uiTypes";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";

export const miniCSourceLoaderViewMirror = {
  "javaPath": "src/main/java/minic/uilocal/source/MiniCSourceLoaderView.java",
  "webPath": "uiweb/src/source/MiniCSourceLoaderView.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCSourceLoaderView",
  "kind": "component",
  "imports": [
    "javafx.application.Platform",
    "javafx.geometry.Point2D",
    "javafx.scene.control.Button",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.input.ScrollEvent",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Priority",
    "javafx.scene.layout.VBox",
    "minic.uilocal.control.MiniCViewportAdapter",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "minic.uiapi.UiSourceSpanDto",
    "java.util.List",
    "java.util.Objects"
  ],
  "fields": [
    {
      "name": "CONTROL_SCROLL_FILTER_INSTALLED_KEY",
      "signature": "private static final String CONTROL_SCROLL_FILTER_INSTALLED_KEY ="
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel;"
    },
    {
      "name": "sourceEditor",
      "signature": "private final MiniCCodeEditor sourceEditor ="
    },
    {
      "name": "startButton",
      "signature": "private final Button startButton ="
    },
    {
      "name": "openButton",
      "signature": "private final Button openButton ="
    },
    {
      "name": "saveButton",
      "signature": "private final Button saveButton ="
    },
    {
      "name": "saveAsButton",
      "signature": "private final Button saveAsButton ="
    },
    {
      "name": "openAction",
      "signature": "private final Runnable openAction;"
    },
    {
      "name": "saveAction",
      "signature": "private final Runnable saveAction;"
    },
    {
      "name": "saveAsAction",
      "signature": "private final Runnable saveAsAction;"
    }
  ],
  "methods": [
    {
      "name": "startSession",
      "signature": "startSession()"
    },
    {
      "name": "loadCurrentSource",
      "signature": "loadCurrentSource()"
    },
    {
      "name": "breakpointLines",
      "signature": "breakpointLines()"
    },
    {
      "name": "setBreakpoint",
      "signature": "setBreakpoint(int line, boolean enabled)"
    },
    {
      "name": "setCurrentExecutionLine",
      "signature": "setCurrentExecutionLine(int line)"
    },
    {
      "name": "setCurrentExecutionRange",
      "signature": "setCurrentExecutionRange(UiSourceSpanDto range)"
    },
    {
      "name": "viewportAdapter",
      "signature": "viewportAdapter()"
    },
    {
      "name": "installViewportTarget",
      "signature": "installViewportTarget(MiniCWorkbenchControlHub controlHub)"
    },
    {
      "name": "usePersistentEditorScrollBars",
      "signature": "usePersistentEditorScrollBars(String scrollStyleClass)"
    },
    {
      "name": "submitRealtimeSource",
      "signature": "submitRealtimeSource()"
    },
    {
      "name": "fallbackSourceName",
      "signature": "fallbackSourceName()"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCSourceLoaderViewProps {
  readonly viewModel: MiniCWorkbenchViewModel;
  readonly onOpenDocument?: (name: string, source: string) => void;
  readonly onSaveDocument?: (name: string, source: string) => void;
  readonly className?: string;
}

export function MiniCSourceLoaderView({
  viewModel,
  onOpenDocument,
  onSaveDocument,
  className = "",
}: MiniCSourceLoaderViewProps) {
  const snapshot = useSourceLoaderSnapshot(viewModel);
  const fileInput = useRef<HTMLInputElement | null>(null);
  const [editorText, setEditorText] = useState(snapshot.sourceText);

  useEffect(() => {
    setEditorText(snapshot.sourceText);
  }, [snapshot.sourceText]);

  const sourceName = snapshot.sourceName || fallbackSourceName();

  const loadCurrentSource = (): void => {
    viewModel.loadSource(sourceName, editorText);
    viewModel.submitRealtimeSource(sourceName, editorText);
  };

  const startSession = (): void => {
    loadCurrentSource();
    viewModel.startSession();
  };

  const openAction = (): void => {
    fileInput.current?.click();
  };

  const saveAction = (): void => {
    onSaveDocument?.(sourceName, editorText);
    downloadSource(sourceName, editorText);
  };

  const saveAsAction = (): void => {
    onSaveDocument?.(sourceName, editorText);
    downloadSource(sourceName, editorText);
  };

  const handleFile = async (event: ChangeEvent<HTMLInputElement>): Promise<void> => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }
    const source = await file.text();
    viewModel.loadSource(file.name, source);
    viewModel.submitRealtimeSource(file.name, source);
    onOpenDocument?.(file.name, source);
  };

  return (
    <section className={`source-loader ${className}`.trim()} data-java-source={miniCSourceLoaderViewMirror.javaPath}>
      <div className="loader-controls">
        <button className="control-primary" type="button" onClick={startSession}>
          启动
        </button>
        <button className="control-secondary" type="button" onClick={openAction}>
          打开
        </button>
        <button className="control-secondary" type="button" onClick={saveAction}>
          保存
        </button>
        <button className="control-secondary" type="button" onClick={saveAsAction}>
          另存为
        </button>
        <input accept=".mc,.mh,.c,.h,text/*" hidden onChange={handleFile} ref={fileInput} type="file" />
      </div>
      <MiniCCodeEditor
        analysis={toEditorAnalysis(snapshot.realtimeAnalysis)}
        breakpoints={snapshot.debugBreakpointLines}
        className="source-editor"
        currentExecutionLine={snapshot.debugState?.currentLine ?? 0}
        currentExecutionRange={null}
        initialText={snapshot.sourceText}
        onBreakpointsChange={(lines) => viewModel.setDebugBreakpoints(lines)}
        onSubmitRealtimeSource={(name, source) => viewModel.submitRealtimeSource(name, source)}
        onTextChange={setEditorText}
        sourceName={sourceName}
        value={editorText}
      />
    </section>
  );
}

MiniCSourceLoaderView.mirror = miniCSourceLoaderViewMirror;

export function fallbackSourceName(): string {
  return "untitled.mc";
}

export function breakpointLines(snapshot: MiniCWorkbenchSnapshot): readonly number[] {
  return snapshot.debugBreakpointLines;
}

export function setBreakpoint(viewModel: MiniCWorkbenchViewModel, line: number, enabled: boolean): void {
  if (enabled) {
    viewModel.setDebugBreakpoint(line);
  } else {
    viewModel.clearDebugBreakpoint(line);
  }
}

export function setCurrentExecutionLine(): void {
  return undefined;
}

export function setCurrentExecutionRange(_range: UiSourceSpanDto | null): void {
  return undefined;
}

export function viewportAdapter(): MiniCViewportAdapter | null {
  return null;
}

export function installViewportTarget(_controlHub: MiniCWorkbenchControlHub): void {
  return undefined;
}

export function usePersistentEditorScrollBars(_scrollStyleClass: string): void {
  return undefined;
}

export function submitRealtimeSource(viewModel: MiniCWorkbenchViewModel): void {
  viewModel.submitRealtimeSource(viewModel.sourceNameProperty().get(), viewModel.sourceTextProperty().get());
}

function useSourceLoaderSnapshot(viewModel: MiniCWorkbenchViewModel): MiniCWorkbenchSnapshot {
  const [snapshot, setSnapshot] = useState(() => viewModel.snapshot());

  useEffect(() => {
    setSnapshot(viewModel.snapshot());
    return viewModel.subscribe(() => {
      setSnapshot(viewModel.snapshot());
    });
  }, [viewModel]);

  return snapshot;
}

function downloadSource(name: string, source: string): void {
  const blob = new Blob([source], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name || fallbackSourceName();
  link.click();
  URL.revokeObjectURL(url);
}

function toEditorAnalysis(analysis: MiniCWorkbenchSnapshot["realtimeAnalysis"]): EditorRealtimeAnalysisDto | null {
  if (analysis === null) {
    return null;
  }
  return {
    ...analysis,
    tokens: analysis.tokens.map((token) => {
      const range = token.range;
      return {
        ...token,
        startOffset: range?.startOffset ?? 0,
        endOffset: range?.endOffset ?? 0,
        startLine: range?.startLine ?? 1,
        startColumn: range?.startColumn ?? 1,
        endLine: range?.endLine ?? 1,
        endColumn: range?.endColumn ?? 1,
      };
    }),
  };
}

export default MiniCSourceLoaderView;
