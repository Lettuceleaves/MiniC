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
    "java.util.Objects",
    "javafx.animation.KeyFrame",
    "javafx.animation.Timeline",
    "javafx.util.Duration",
    "minic.settings.MiniCSettings",
    "minic.uiapi.UiControlResultDto"
  ],
  "fields": [
    {
      "name": "timeline",
      "signature": "private Timeline timeline"
    },
    {
      "name": "timelineEnabled",
      "signature": "private final boolean timelineEnabled"
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel"
    }
  ],
  "methods": [
    {
      "name": "nextStage",
      "signature": "nextStage()"
    },
    {
      "name": "pause",
      "signature": "pause()"
    },
    {
      "name": "play",
      "signature": "play()"
    },
    {
      "name": "playFast",
      "signature": "playFast()"
    },
    {
      "name": "restartTimeline",
      "signature": "restartTimeline()"
    },
    {
      "name": "running",
      "signature": "running()"
    },
    {
      "name": "stopTimeline",
      "signature": "stopTimeline()"
    },
    {
      "name": "tickOnce",
      "signature": "tickOnce()"
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
    this.viewModel.runInBackground(this.startPlayback(() => this.viewModel.play()), "播放失败");
  }

  playFast(): void {
    this.viewModel.runInBackground(this.startPlayback(() => this.viewModel.playFast()), "2x 播放失败");
  }

  pause(): void {
    this.stopTimeline();
    this.viewModel.runInBackground(this.viewModel.pause(), "暂停失败");
  }

  nextStage(): Promise<UiControlResultDto> {
    this.stopTimeline();
    return this.viewModel.nextStage();
  }

  async tickOnce(): Promise<UiControlResultDto | null> {
    const state = this.viewModel.currentStateProperty().get();
    if (state !== null && state.playbackMode === "PAUSED") {
      this.stopTimeline();
      return null;
    }
    const result = await this.viewModel.tick();
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
    const state = this.viewModel.currentStateProperty().get();
    if (state === null || state.playbackMode === "PAUSED" || !state.canNext) {
      return;
    }
    const interval = state.frameIntervalMillis;
    this.timer = window.setInterval(() => {
      this.viewModel.runInBackground(this.tickOnce(), "播放 tick 失败");
    }, interval);
  }

  private async startPlayback(start: () => Promise<UiControlResultDto>): Promise<void> {
    this.stopTimeline();
    await start();
    this.restartTimeline();
  }

  private stopTimeline(): void {
    if (this.timer !== null) {
      window.clearInterval(this.timer);
      this.timer = null;
    }
  }
}

export default MiniCPlaybackController;
