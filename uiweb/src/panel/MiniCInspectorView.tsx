import { useEffect, useMemo, useState } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { MiniCPlaybackController } from "../workbench/MiniCPlaybackController";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";
import { MiniCInspectorModel } from "./MiniCInspectorModel";
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
  readonly playbackController: MiniCPlaybackController;
}

export function MiniCInspectorView({ viewModel, playbackController }: MiniCInspectorViewProps) {
  const snapshot = useInspectorSnapshot(viewModel);
  const modelFactory = useMemo(() => new MiniCInspectorModelFactory(), []);
  const model = snapshot.inspectorModel === null
    ? modelFactory.create(snapshot.currentState, snapshot.currentStageData, snapshot.globalData)
    : new MiniCInspectorModel(
      snapshot.inspectorModel.currentState,
      snapshot.inspectorModel.currentItem,
      snapshot.inspectorModel.accumulatedOutput,
    );

  return (
    <aside className="inspector" data-java-source={miniCInspectorViewMirror.javaPath}>
      {label("MiniC 观测", "panel-title")}
      {controls(viewModel, snapshot, playbackController)}
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

export function controls(
  viewModel: MiniCWorkbenchViewModel,
  snapshot: MiniCWorkbenchSnapshot,
  playbackController: MiniCPlaybackController,
) {
  return (
    <div className="controls inspector-controls">
      <div className="inspector-control-row">
        {control("下一步", true, () => viewModel.runInBackground(viewModel.next(), "下一步失败"), snapshot.currentState?.canNext ?? false)}
        {control("下一阶段", false, () => viewModel.runInBackground(playbackController.nextStage(), "下一阶段失败"), snapshot.currentState !== null)}
        {control("到执行", false, () => viewModel.runInBackground(viewModel.runToExecution(), "到执行失败"), snapshot.currentState !== null)}
      </div>
      <div className="inspector-control-row">
        {control("播放", false, () => playbackController.play(), snapshot.currentState?.canPlay ?? false)}
        {control("2x", false, () => playbackController.playFast(), snapshot.currentState?.canPlayFast ?? false)}
        {control("暂停", false, () => playbackController.pause(), snapshot.currentState?.canPause ?? false)}
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

export function execute(
  commandId: string,
  viewModel: MiniCWorkbenchViewModel,
  playbackController: MiniCPlaybackController,
): void {
  switch (commandId) {
    case "compiler.next":
      viewModel.runInBackground(viewModel.next(), "下一步失败");
      break;
    case "compiler.nextStage":
      viewModel.runInBackground(playbackController.nextStage(), "下一阶段失败");
      break;
    case "compiler.runToExecution":
      viewModel.runInBackground(viewModel.runToExecution(), "到执行失败");
      break;
    case "compiler.play":
      playbackController.play();
      break;
    case "compiler.playFast":
      playbackController.playFast();
      break;
    case "compiler.pause":
      playbackController.pause();
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
