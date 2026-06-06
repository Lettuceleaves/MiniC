import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiControlResultDto } from "../translation/uiapi";
import type { MiniCWorkbenchViewModel } from "./MiniCWorkbenchViewModel";

export const miniCPlaybackControllerMirror = {
  "javaPath": "src/main/java/minic/uilocal/workbench/MiniCPlaybackController.java",
  "webPath": "uiweb/src/workbench/MiniCPlaybackController.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCPlaybackController",
  "kind": "class",
  "imports": [
    "javafx.animation.KeyFrame",
    "javafx.animation.Timeline",
    "javafx.util.Duration",
    "minic.settings.MiniCSettings",
    "minic.uiapi.UiControlResultDto",
    "java.util.Objects"
  ],
  "fields": [
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel;"
    },
    {
      "name": "timelineEnabled",
      "signature": "private final boolean timelineEnabled;"
    },
    {
      "name": "timeline",
      "signature": "private Timeline timeline;"
    }
  ],
  "methods": [
    {
      "name": "play",
      "signature": "play()"
    },
    {
      "name": "playFast",
      "signature": "playFast()"
    },
    {
      "name": "pause",
      "signature": "pause()"
    },
    {
      "name": "nextStage",
      "signature": "nextStage()"
    },
    {
      "name": "tickOnce",
      "signature": "tickOnce()"
    },
    {
      "name": "running",
      "signature": "running()"
    },
    {
      "name": "restartTimeline",
      "signature": "restartTimeline()"
    },
    {
      "name": "stopTimeline",
      "signature": "stopTimeline()"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCPlaybackController {
  static readonly mirror = miniCPlaybackControllerMirror;

  readonly mirror = miniCPlaybackControllerMirror;

  private timer: number | null = null;

  constructor(
    private readonly viewModel: MiniCWorkbenchViewModel,
    private readonly timelineEnabled = true,
  ) {
    this.viewModel.currentStateProperty().subscribe((state) => {
      if (this.running() && state !== null && state.playbackMode === "PAUSED") {
        this.stopTimeline();
      }
    });
  }

  play(): void {
    this.viewModel.play();
    this.restartTimeline();
  }

  playFast(): void {
    this.viewModel.playFast();
    this.restartTimeline();
  }

  pause(): void {
    this.stopTimeline();
    this.viewModel.pause();
  }

  nextStage(): UiControlResultDto {
    this.stopTimeline();
    return this.viewModel.nextStage();
  }

  tickOnce(): UiControlResultDto | null {
    const state = this.viewModel.currentStateProperty().get();
    if (state !== null && state.playbackMode === "PAUSED") {
      this.stopTimeline();
      return null;
    }
    const result = this.viewModel.tick();
    const nextState = this.viewModel.currentStateProperty().get();
    if (nextState !== null && !nextState.canNext) {
      this.stopTimeline();
    }
    return result;
  }

  running(): boolean {
    return this.timer !== null;
  }

  dispose(): void {
    this.stopTimeline();
  }

  summary(): string {
    return `MiniCPlaybackController: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }

  private restartTimeline(): void {
    this.stopTimeline();
    if (!this.timelineEnabled) {
      return;
    }
    const interval = this.viewModel.currentStateProperty().get()?.frameIntervalMillis ?? 1000;
    this.timer = window.setInterval(() => {
      this.tickOnce();
    }, interval);
  }

  private stopTimeline(): void {
    if (this.timer !== null) {
      window.clearInterval(this.timer);
      this.timer = null;
    }
  }
}

export default MiniCPlaybackController;
