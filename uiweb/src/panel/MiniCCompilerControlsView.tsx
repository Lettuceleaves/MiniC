import MiniCPlaybackController from "../workbench/MiniCPlaybackController";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { MiniCWorkbenchSnapshot } from "../workbench/MiniCWorkbenchViewModel";
import MiniCWorkbenchViewModel from "../workbench/MiniCWorkbenchViewModel";
import { useInspectorSnapshot } from "./MiniCInspectorView";

export const miniCCompilerControlsViewMirror = {
  "javaPath": "src/main/java/minic/uilocal/panel/MiniCCompilerControlsView.java",
  "webPath": "uiweb/src/panel/MiniCCompilerControlsView.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCCompilerControlsView",
  "kind": "component",
  "imports": [
    "java.util.Objects",
    "javafx.scene.control.Button",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.VBox",
    "minic.uilocal.control.MiniCWorkbenchControlHub"
  ],
  "fields": [
    {
      "name": "CONTROL_BUTTON_HEIGHT",
      "signature": "private static final double CONTROL_BUTTON_HEIGHT="
    },
    {
      "name": "CONTROL_BUTTON_WIDTH",
      "signature": "private static final double CONTROL_BUTTON_WIDTH="
    },
    {
      "name": "controlHub",
      "signature": "private final MiniCWorkbenchControlHub controlHub"
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
      "name": "playButton",
      "signature": "private final Button playButton="
    },
    {
      "name": "playbackController",
      "signature": "private final MiniCPlaybackController playbackController"
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
      "name": "control",
      "signature": "control(String text,boolean primary)"
    },
    {
      "name": "execute",
      "signature": "execute(String commandId)"
    },
    {
      "name": "refresh",
      "signature": "refresh()"
    },
    {
      "name": "registerCompilerCommands",
      "signature": "registerCompilerCommands()"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCCompilerControlsViewProps {
  readonly viewModel: MiniCWorkbenchViewModel;
  readonly playbackController: MiniCPlaybackController;
}

export function MiniCCompilerControlsView({ viewModel, playbackController }: MiniCCompilerControlsViewProps) {
  const snapshot = useInspectorSnapshot(viewModel);
  return controls(viewModel, snapshot, playbackController);
}

MiniCCompilerControlsView.mirror = miniCCompilerControlsViewMirror;

export function controls(
  viewModel: MiniCWorkbenchViewModel,
  snapshot: MiniCWorkbenchSnapshot,
  playbackController: MiniCPlaybackController,
) {
  return (
    <div className="compiler-controls controls inspector-controls">
      <div className="inspector-control-row">
        {control("下一步", true, () => viewModel.runInBackground(viewModel.next(), "下一步失败"), viewModel.canNextControl())}
        {control("下一阶段", false, () => viewModel.runInBackground(playbackController.nextStage(), "下一阶段失败"), viewModel.canNextStageControl())}
        {control("到执行", false, () => viewModel.runInBackground(viewModel.runToExecution(), "到执行失败"), viewModel.canRunToExecutionControl())}
      </div>
      <div className="inspector-control-row">
        {control("播放", false, () => playbackController.play(), viewModel.canPlayControl())}
        {control("2x", false, () => playbackController.playFast(), viewModel.canPlayFastControl())}
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

export default MiniCCompilerControlsView;
