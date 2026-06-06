import { useEffect, useMemo, useState } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";
import { MiniCInspectorModelFactory } from "./MiniCInspectorModelFactory";

export const miniCInspectorViewMirror = {
  "javaPath": "src/main/java/minic/uilocal/panel/MiniCInspectorView.java",
  "webPath": "uiweb/src/panel/MiniCInspectorView.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCInspectorView",
  "kind": "component",
  "imports": [
    "javafx.scene.control.Button",
    "javafx.scene.control.Label",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.VBox",
    "javafx.scene.text.TextFlow",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "minic.uilocal.text.MiniCExplanationTextHighlighter",
    "minic.uilocal.text.MiniCTextFlowFactory",
    "java.util.Objects"
  ],
  "fields": [
    {
      "name": "INSPECTOR_BUTTON_WIDTH",
      "signature": "private static final double INSPECTOR_BUTTON_WIDTH ="
    },
    {
      "name": "INSPECTOR_BUTTON_HEIGHT",
      "signature": "private static final double INSPECTOR_BUTTON_HEIGHT ="
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel;"
    },
    {
      "name": "modelFactory",
      "signature": "private final MiniCInspectorModelFactory modelFactory ="
    },
    {
      "name": "playbackController",
      "signature": "private final MiniCPlaybackController playbackController;"
    },
    {
      "name": "controlHub",
      "signature": "private final MiniCWorkbenchControlHub controlHub;"
    },
    {
      "name": "explanationTextHighlighter",
      "signature": "private final MiniCExplanationTextHighlighter explanationTextHighlighter ="
    },
    {
      "name": "currentState",
      "signature": "private final TextFlow currentState ="
    },
    {
      "name": "currentItem",
      "signature": "private final TextFlow currentItem ="
    },
    {
      "name": "accumulatedOutput",
      "signature": "private final TextFlow accumulatedOutput ="
    },
    {
      "name": "nextButton",
      "signature": "private final Button nextButton ="
    },
    {
      "name": "nextStageButton",
      "signature": "private final Button nextStageButton ="
    },
    {
      "name": "runToExecutionButton",
      "signature": "private final Button runToExecutionButton ="
    },
    {
      "name": "playButton",
      "signature": "private final Button playButton ="
    },
    {
      "name": "playFastButton",
      "signature": "private final Button playFastButton ="
    },
    {
      "name": "pauseButton",
      "signature": "private final Button pauseButton ="
    }
  ],
  "methods": [
    {
      "name": "refresh",
      "signature": "refresh()"
    },
    {
      "name": "registerCompilerCommands",
      "signature": "registerCompilerCommands()"
    },
    {
      "name": "execute",
      "signature": "execute(String commandId)"
    },
    {
      "name": "controls",
      "signature": "controls()"
    },
    {
      "name": "control",
      "signature": "control(String text, boolean primary)"
    },
    {
      "name": "body",
      "signature": "body(String text)"
    },
    {
      "name": "setBody",
      "signature": "setBody(TextFlow target, String text)"
    },
    {
      "name": "label",
      "signature": "label(String text, String styleClass)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCInspectorViewProps {
  readonly viewModel: MiniCWorkbenchViewModel;
}

export function MiniCInspectorView({ viewModel }: MiniCInspectorViewProps) {
  const snapshot = useInspectorSnapshot(viewModel);
  const modelFactory = useMemo(() => new MiniCInspectorModelFactory(), []);
  const model = modelFactory.create(snapshot.currentState, snapshot.currentStageData, snapshot.globalData);

  return (
    <aside className="inspector" data-java-source={miniCInspectorViewMirror.javaPath}>
      {controls(viewModel, snapshot)}
      {body("当前状态", model.currentState)}
      {body("当前项", model.currentItem)}
      {body("累计输出", model.accumulatedOutput)}
    </aside>
  );
}

MiniCInspectorView.mirror = miniCInspectorViewMirror;

export function refresh(): void {
  return undefined;
}

export function controls(viewModel: MiniCWorkbenchViewModel, snapshot: MiniCWorkbenchSnapshot) {
  return (
    <div className="controls inspector-controls">
      <div className="inspector-control-row">
        {control("下一步", true, () => viewModel.next(), snapshot.currentState?.canNext ?? false)}
        {control("下一阶段", false, () => viewModel.nextStage(), snapshot.currentState !== null)}
        {control("到执行", false, () => viewModel.runToExecution(), snapshot.currentState !== null)}
      </div>
      <div className="inspector-control-row">
        {control("播放", true, () => viewModel.play(), snapshot.currentState?.canPlay ?? false)}
        {control("2x", false, () => viewModel.playFast(), snapshot.currentState?.canPlayFast ?? false)}
        {control("暂停", false, () => viewModel.pause(), snapshot.currentState?.canPause ?? false)}
      </div>
    </div>
  );
}

export function control(text: string, primary: boolean, action: () => void, enabled = true) {
  return (
    <button
      className={`inspector-control-button ${primary ? "control-primary" : "control-secondary"}`}
      disabled={!enabled}
      onClick={action}
      type="button"
    >
      {text}
    </button>
  );
}

export function body(title: string, text: string) {
  return (
    <section className="debug-section">
      {label(title, "debug-section-title")}
      <pre className="body-text">{text}</pre>
    </section>
  );
}

export function setBody(): void {
  return undefined;
}

export function label(text: string, styleClass: string) {
  return <h2 className={styleClass}>{text}</h2>;
}

export function execute(commandId: string, viewModel: MiniCWorkbenchViewModel): void {
  switch (commandId) {
    case "compiler.next":
      viewModel.next();
      break;
    case "compiler.nextStage":
      viewModel.nextStage();
      break;
    case "compiler.runToExecution":
      viewModel.runToExecution();
      break;
    case "compiler.play":
      viewModel.play();
      break;
    case "compiler.playFast":
      viewModel.playFast();
      break;
    case "compiler.pause":
      viewModel.pause();
      break;
  }
}

function useInspectorSnapshot(viewModel: MiniCWorkbenchViewModel): MiniCWorkbenchSnapshot {
  const [snapshot, setSnapshot] = useState(() => viewModel.snapshot());

  useEffect(() => {
    setSnapshot(viewModel.snapshot());
    return viewModel.subscribe(() => {
      setSnapshot(viewModel.snapshot());
    });
  }, [viewModel]);

  return snapshot;
}

export default MiniCInspectorView;
