import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { MiniCCodeEditor } from "../editor/MiniCCodeEditor";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiRealtimeAnalysisDto as EditorRealtimeAnalysisDto } from "../translation/uiTypes";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";

export const miniCSourceLoaderViewMirror = {
  "javaPath": "src/main/java/minic/uilocal/source/MiniCSourceLoaderView.java",
  "webPath": "uiweb/src/source/MiniCSourceLoaderView.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCSourceLoaderView",
  "kind": "component",
  "imports": [
    "java.util.List",
    "java.util.Objects",
    "javafx.application.Platform",
    "javafx.geometry.Point2D",
    "javafx.scene.control.Button",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.input.ScrollEvent",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Priority",
    "javafx.scene.layout.VBox",
    "minic.uiapi.UiSourceSpanDto",
    "minic.uilocal.control.MiniCViewportAdapter",
    "minic.uilocal.control.MiniCWorkbenchControlHub"
  ],
  "fields": [
    {
      "name": "CONTROL_SCROLL_FILTER_INSTALLED_KEY",
      "signature": "private static final String CONTROL_SCROLL_FILTER_INSTALLED_KEY="
    },
    {
      "name": "openAction",
      "signature": "private final Runnable openAction"
    },
    {
      "name": "openButton",
      "signature": "private final Button openButton="
    },
    {
      "name": "saveAction",
      "signature": "private final Runnable saveAction"
    },
    {
      "name": "saveAsAction",
      "signature": "private final Runnable saveAsAction"
    },
    {
      "name": "saveAsButton",
      "signature": "private final Button saveAsButton="
    },
    {
      "name": "saveButton",
      "signature": "private final Button saveButton="
    },
    {
      "name": "sourceEditor",
      "signature": "private final MiniCCodeEditor sourceEditor="
    },
    {
      "name": "startButton",
      "signature": "private final Button startButton="
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel"
    }
  ],
  "methods": [
    {
      "name": "breakpointLines",
      "signature": "breakpointLines()"
    },
    {
      "name": "fallbackSourceName",
      "signature": "fallbackSourceName()"
    },
    {
      "name": "installViewportTarget",
      "signature": "installViewportTarget(MiniCWorkbenchControlHub controlHub)"
    },
    {
      "name": "loadCurrentSource",
      "signature": "loadCurrentSource()"
    },
    {
      "name": "setBreakpoint",
      "signature": "setBreakpoint(int line,boolean enabled)"
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
      "name": "startSession",
      "signature": "startSession()"
    },
    {
      "name": "submitRealtimeSource",
      "signature": "submitRealtimeSource()"
    },
    {
      "name": "usePersistentEditorScrollBars",
      "signature": "usePersistentEditorScrollBars(String scrollStyleClass)"
    },
    {
      "name": "viewportAdapter",
      "signature": "viewportAdapter()"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCSourceLoaderViewProps {
  readonly viewModel: MiniCWorkbenchViewModel;
  readonly onOpenDocument?: (name: string, source: string) => void;
  readonly onSaveDocument?: (name: string, source: string) => void;
  readonly className?: string;
  readonly showControls?: boolean;
  readonly editorScrollClassName?: string;
}

export function MiniCSourceLoaderView({
  viewModel,
  onOpenDocument,
  onSaveDocument,
  className = "",
  showControls = true,
  editorScrollClassName = "",
}: MiniCSourceLoaderViewProps) {
  const snapshot = useSourceLoaderSnapshot(viewModel);
  const fileInput = useRef<HTMLInputElement | null>(null);
  const [editorText, setEditorText] = useState(snapshot.sourceText);

  useEffect(() => {
    setEditorText(snapshot.sourceText);
  }, [snapshot.sourceText]);

  const sourceName = snapshot.sourceName || fallbackSourceName();

  const loadCurrentSource = async (): Promise<void> => {
    await viewModel.loadSource(sourceName, editorText);
    viewModel.runInBackground(viewModel.submitRealtimeSource(sourceName, editorText), "实时分析失败");
  };

  const startSession = async (): Promise<void> => {
    await loadCurrentSource();
    await viewModel.startSession();
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

  const handleFile = (event: ChangeEvent<HTMLInputElement>): void => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }
    viewModel.runInBackground((async () => {
      const source = await file.text();
      await viewModel.loadSource(file.name, source);
      viewModel.runInBackground(viewModel.submitRealtimeSource(file.name, source), "实时分析失败");
      onOpenDocument?.(file.name, source);
    })(), "打开源码失败");
  };

  return (
    <section className={`source-loader ${className}`.trim()} data-java-source={miniCSourceLoaderViewMirror.javaPath}>
      {showControls && (
        <div className="loader-controls">
          <button className="control-primary" type="button" onClick={() => viewModel.runInBackground(startSession(), "启动编译 pipeline 失败")}>
            开始
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
      )}
      <MiniCCodeEditor
        analysis={toEditorAnalysis(snapshot.realtimeAnalysis)}
        breakpoints={snapshot.debugBreakpointLines}
        className="source-editor"
        currentExecutionLine={snapshot.debugState?.currentSnapshot.sourceRange?.startLine ?? 0}
        currentExecutionRange={snapshot.debugState?.currentSnapshot.sourceRange ?? null}
        initialText={snapshot.sourceText}
        onBreakpointsChange={(lines) => viewModel.setDebugBreakpoints(lines)}
        onSubmitRealtimeSource={(name, source) => viewModel.runInBackground(viewModel.submitRealtimeSource(name, source), "实时分析失败")}
        onTextChange={setEditorText}
        scrollContainerClassName={editorScrollClassName}
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
    viewModel.runInBackground(viewModel.setDebugBreakpoint(line), "设置断点失败");
  } else {
    viewModel.runInBackground(viewModel.clearDebugBreakpoint(line), "清除断点失败");
  }
}

export function submitRealtimeSource(viewModel: MiniCWorkbenchViewModel): void {
  viewModel.runInBackground(
    viewModel.submitRealtimeSource(viewModel.sourceNameProperty().get(), viewModel.sourceTextProperty().get()),
    "实时分析失败",
  );
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
