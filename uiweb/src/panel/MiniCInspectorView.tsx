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
    "java.util.Objects",
    "javafx.scene.control.Button",
    "javafx.scene.control.Label",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.VBox",
    "javafx.scene.text.TextFlow",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "minic.uilocal.text.MiniCExplanationTextHighlighter",
    "minic.uilocal.text.MiniCTextFlowFactory"
  ],
  "fields": [
    {
      "name": "accumulatedOutput",
      "signature": "private final TextFlow accumulatedOutput="
    },
    {
      "name": "controlHub",
      "signature": "private final MiniCWorkbenchControlHub controlHub"
    },
    {
      "name": "currentItem",
      "signature": "private final TextFlow currentItem="
    },
    {
      "name": "currentState",
      "signature": "private final TextFlow currentState="
    },
    {
      "name": "explanationTextHighlighter",
      "signature": "private final MiniCExplanationTextHighlighter explanationTextHighlighter="
    },
    {
      "name": "INSPECTOR_BUTTON_HEIGHT",
      "signature": "private static final double INSPECTOR_BUTTON_HEIGHT="
    },
    {
      "name": "INSPECTOR_BUTTON_WIDTH",
      "signature": "private static final double INSPECTOR_BUTTON_WIDTH="
    },
    {
      "name": "modelFactory",
      "signature": "private final MiniCInspectorModelFactory modelFactory="
    },
    {
      "name": "nextButton",
      "signature": "private final Button nextButton="
    },
    {
      "name": "nextStageButton",
      "signature": "private final Button nextStageButton="
    },
    {
      "name": "pauseButton",
      "signature": "private final Button pauseButton="
    },
    {
      "name": "playbackController",
      "signature": "private final MiniCPlaybackController playbackController"
    },
    {
      "name": "playButton",
      "signature": "private final Button playButton="
    },
    {
      "name": "playFastButton",
      "signature": "private final Button playFastButton="
    },
    {
      "name": "runToExecutionButton",
      "signature": "private final Button runToExecutionButton="
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel"
    }
  ],
  "methods": [
    {
      "name": "body",
      "signature": "body(String text)"
    },
    {
      "name": "control",
      "signature": "control(String text,boolean primary)"
    },
    {
      "name": "controls",
      "signature": "controls()"
    },
    {
      "name": "execute",
      "signature": "execute(String commandId)"
    },
    {
      "name": "label",
      "signature": "label(String text,String styleClass)"
    },
    {
      "name": "refresh",
      "signature": "refresh()"
    },
    {
      "name": "registerCompilerCommands",
      "signature": "registerCompilerCommands()"
    },
    {
      "name": "setBody",
      "signature": "setBody(TextFlow target,String text)"
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
      {label("MiniC 观测", "panel-title")}
      {controls(viewModel, snapshot)}
      {label("当前状态", "section-label")}
      {body(model.currentState)}
      {label("当前项", "section-label")}
      {body(model.currentItem)}
      {label("累计输出", "section-label")}
      {body(model.accumulatedOutput)}
    </aside>
  );
}

MiniCInspectorView.mirror = miniCInspectorViewMirror;

export function controls(viewModel: MiniCWorkbenchViewModel, snapshot: MiniCWorkbenchSnapshot) {
  return (
    <div className="controls inspector-controls">
      <div className="inspector-control-row">
        {control("下一步", true, () => void viewModel.next(), snapshot.currentState?.canNext ?? false)}
        {control("下一阶段", false, () => void viewModel.nextStage(), snapshot.currentState !== null)}
        {control("到执行", false, () => void viewModel.runToExecution(), snapshot.currentState !== null)}
      </div>
      <div className="inspector-control-row">
        {control("播放", false, () => void viewModel.play(), snapshot.currentState?.canPlay ?? false)}
        {control("2x", false, () => void viewModel.playFast(), snapshot.currentState?.canPlayFast ?? false)}
        {control("暂停", false, () => void viewModel.pause(), snapshot.currentState?.canPause ?? false)}
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

export function body(text: string) {
  return <pre className="body-text">{text}</pre>;
}

export function label(text: string, styleClass: string) {
  return <h2 className={styleClass}>{text}</h2>;
}

export function execute(commandId: string, viewModel: MiniCWorkbenchViewModel): void {
  switch (commandId) {
    case "compiler.next":
      void viewModel.next();
      break;
    case "compiler.nextStage":
      void viewModel.nextStage();
      break;
    case "compiler.runToExecution":
      void viewModel.runToExecution();
      break;
    case "compiler.play":
      void viewModel.play();
      break;
    case "compiler.playFast":
      void viewModel.playFast();
      break;
    case "compiler.pause":
      void viewModel.pause();
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
