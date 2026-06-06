import { MiniCCommandRegistry } from "./MiniCCommandRegistry";
import { MiniCControlCommand } from "./MiniCControlCommand";
import type { MiniCViewportAdapter } from "./MiniCViewportAdapter";
import { MiniCViewportRegistry } from "./MiniCViewportRegistry";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type {
  BooleanSupplier,
  DoubleConsumer,
  LongConsumer,
  Runnable,
  Scheduler,
  ViewportPoint,
} from "../translation/uiTypes";
import { clampNumber, requireValue } from "../translation/uiTypes";

export const miniCWorkbenchControlHubMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCWorkbenchControlHub.java",
  "webPath": "uiweb/src/control/MiniCWorkbenchControlHub.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCWorkbenchControlHub",
  "kind": "class",
  "imports": [
    "javafx.application.Platform",
    "javafx.geometry.Point2D",
    "javafx.scene.Node",
    "javafx.scene.input.MouseButton",
    "javafx.scene.input.MouseEvent",
    "java.util.Collections",
    "java.util.ArrayList",
    "java.util.LinkedHashSet",
    "java.util.List",
    "java.util.Objects",
    "java.util.Set",
    "java.util.function.BooleanSupplier",
    "java.util.function.Consumer",
    "java.util.function.DoubleConsumer",
    "java.util.function.DoubleSupplier",
    "java.util.function.LongConsumer",
    "java.util.function.LongSupplier"
  ],
  "fields": [
    {
      "name": "VIEWPORT_TARGET_PROPERTY",
      "signature": "private static final String VIEWPORT_TARGET_PROPERTY ="
    },
    {
      "name": "DEBUG_START",
      "signature": "public static final String DEBUG_START ="
    },
    {
      "name": "DEBUG_RUN_TO_END",
      "signature": "public static final String DEBUG_RUN_TO_END ="
    },
    {
      "name": "DEBUG_RUN_TO_BREAKPOINT",
      "signature": "public static final String DEBUG_RUN_TO_BREAKPOINT ="
    },
    {
      "name": "DEBUG_STEP_OVER",
      "signature": "public static final String DEBUG_STEP_OVER ="
    },
    {
      "name": "DEBUG_STEP_INTO",
      "signature": "public static final String DEBUG_STEP_INTO ="
    },
    {
      "name": "DEBUG_BACK_TO_BREAKPOINT",
      "signature": "public static final String DEBUG_BACK_TO_BREAKPOINT ="
    },
    {
      "name": "DEBUG_STEP_BACK_OVER",
      "signature": "public static final String DEBUG_STEP_BACK_OVER ="
    },
    {
      "name": "DEBUG_STEP_BACK",
      "signature": "public static final String DEBUG_STEP_BACK ="
    },
    {
      "name": "COMPILER_NEXT",
      "signature": "public static final String COMPILER_NEXT ="
    },
    {
      "name": "COMPILER_NEXT_STAGE",
      "signature": "public static final String COMPILER_NEXT_STAGE ="
    },
    {
      "name": "COMPILER_RUN_TO_EXECUTION",
      "signature": "public static final String COMPILER_RUN_TO_EXECUTION ="
    },
    {
      "name": "COMPILER_PLAY",
      "signature": "public static final String COMPILER_PLAY ="
    },
    {
      "name": "COMPILER_PLAY_FAST",
      "signature": "public static final String COMPILER_PLAY_FAST ="
    },
    {
      "name": "COMPILER_PAUSE",
      "signature": "public static final String COMPILER_PAUSE ="
    },
    {
      "name": "SETTINGS_THEME_SET",
      "signature": "public static final String SETTINGS_THEME_SET ="
    },
    {
      "name": "SETTINGS_THEME_NEXT",
      "signature": "public static final String SETTINGS_THEME_NEXT ="
    },
    {
      "name": "SETTINGS_THEME_PREVIOUS",
      "signature": "public static final String SETTINGS_THEME_PREVIOUS ="
    },
    {
      "name": "SETTINGS_FRAME_INTERVAL_SET",
      "signature": "public static final String SETTINGS_FRAME_INTERVAL_SET ="
    },
    {
      "name": "SETTINGS_FRAME_INTERVAL_INCREASE",
      "signature": "public static final String SETTINGS_FRAME_INTERVAL_INCREASE ="
    },
    {
      "name": "SETTINGS_FRAME_INTERVAL_DECREASE",
      "signature": "public static final String SETTINGS_FRAME_INTERVAL_DECREASE ="
    },
    {
      "name": "SETTINGS_UI_SCALE_INCREASE",
      "signature": "public static final String SETTINGS_UI_SCALE_INCREASE ="
    },
    {
      "name": "SETTINGS_UI_SCALE_DECREASE",
      "signature": "public static final String SETTINGS_UI_SCALE_DECREASE ="
    },
    {
      "name": "VIEWPORT_ZOOM_IN",
      "signature": "public static final String VIEWPORT_ZOOM_IN ="
    },
    {
      "name": "VIEWPORT_ZOOM_OUT",
      "signature": "public static final String VIEWPORT_ZOOM_OUT ="
    },
    {
      "name": "VIEWPORT_SCROLL_UP",
      "signature": "public static final String VIEWPORT_SCROLL_UP ="
    },
    {
      "name": "VIEWPORT_SCROLL_DOWN",
      "signature": "public static final String VIEWPORT_SCROLL_DOWN ="
    },
    {
      "name": "VIEWPORT_SCROLL_LEFT",
      "signature": "public static final String VIEWPORT_SCROLL_LEFT ="
    },
    {
      "name": "VIEWPORT_SCROLL_RIGHT",
      "signature": "public static final String VIEWPORT_SCROLL_RIGHT ="
    },
    {
      "name": "VIEWPORT_CENTER_ACTIVE",
      "signature": "public static final String VIEWPORT_CENTER_ACTIVE ="
    },
    {
      "name": "commandRegistry",
      "signature": "private final MiniCCommandRegistry commandRegistry;"
    },
    {
      "name": "viewportRegistry",
      "signature": "private final MiniCViewportRegistry viewportRegistry;"
    },
    {
      "name": "commandIds",
      "signature": "private final Set<String> commandIds ="
    },
    {
      "name": "additionalActiveTrackingActions",
      "signature": "private final List<Runnable> additionalActiveTrackingActions ="
    },
    {
      "name": "activeTrackingAction",
      "signature": "private Runnable activeTrackingAction ="
    },
    {
      "name": "activeTrackingScheduler",
      "signature": "private Consumer<Runnable> activeTrackingScheduler ="
    },
    {
      "name": "pendingThemeName",
      "signature": "private String pendingThemeName;"
    },
    {
      "name": "pendingFrameIntervalMillis",
      "signature": "private Long pendingFrameIntervalMillis;"
    }
  ],
  "methods": [
    {
      "name": "commandRegistry",
      "signature": "commandRegistry()"
    },
    {
      "name": "viewportRegistry",
      "signature": "viewportRegistry()"
    },
    {
      "name": "commandIds",
      "signature": "commandIds()"
    },
    {
      "name": "setActiveTrackingAction",
      "signature": "setActiveTrackingAction(Runnable activeTrackingAction)"
    },
    {
      "name": "setActiveTrackingScheduler",
      "signature": "setActiveTrackingScheduler(Consumer<Runnable> activeTrackingScheduler)"
    },
    {
      "name": "addActiveTrackingAction",
      "signature": "addActiveTrackingAction(Runnable activeTrackingAction)"
    },
    {
      "name": "registerDebuggerCommands",
      "signature": "registerDebuggerCommands(DebuggerCommands commands)"
    },
    {
      "name": "registerCompilerCommands",
      "signature": "registerCompilerCommands(CompilerCommands commands)"
    },
    {
      "name": "registerSettingsCommands",
      "signature": "registerSettingsCommands(SettingsCommands commands)"
    },
    {
      "name": "commandEnabled",
      "signature": "commandEnabled(String commandId)"
    },
    {
      "name": "execute",
      "signature": "execute(String commandId)"
    },
    {
      "name": "setTheme",
      "signature": "setTheme(String themeName)"
    },
    {
      "name": "setFrameIntervalMillis",
      "signature": "setFrameIntervalMillis(long millis)"
    },
    {
      "name": "increaseFrameInterval",
      "signature": "increaseFrameInterval()"
    },
    {
      "name": "decreaseFrameInterval",
      "signature": "decreaseFrameInterval()"
    },
    {
      "name": "increaseUiScale",
      "signature": "increaseUiScale()"
    },
    {
      "name": "decreaseUiScale",
      "signature": "decreaseUiScale()"
    },
    {
      "name": "handleZoom",
      "signature": "handleZoom(Point2D localPoint, double delta)"
    },
    {
      "name": "handleScrollVertical",
      "signature": "handleScrollVertical(double delta)"
    },
    {
      "name": "handleScrollHorizontal",
      "signature": "handleScrollHorizontal(double delta)"
    },
    {
      "name": "handlePan",
      "signature": "handlePan(double deltaX, double deltaY)"
    },
    {
      "name": "handleCenterActive",
      "signature": "handleCenterActive()"
    },
    {
      "name": "installViewportTarget",
      "signature": "installViewportTarget(Node node, MiniCViewportAdapter adapter)"
    },
    {
      "name": "register",
      "signature": "register(String id, String label, BooleanSupplier enabled, Runnable action)"
    },
    {
      "name": "trackActiveViews",
      "signature": "trackActiveViews()"
    },
    {
      "name": "runLaterIfPossible",
      "signature": "runLaterIfPossible(Runnable action)"
    },
    {
      "name": "clamp",
      "signature": "clamp(long value, LongSupplier minSupplier, LongSupplier maxSupplier)"
    },
    {
      "name": "clamp",
      "signature": "clamp(double value, DoubleSupplier minSupplier, DoubleSupplier maxSupplier)"
    },
    {
      "name": "DebuggerCommands",
      "signature": "DebuggerCommands(BooleanSupplier canStart, Runnable start, BooleanSupplier canRunToEnd, Runnable runToEnd, BooleanSupplier canRunToBreakpoint, Runnable runToBreakpoint, BooleanSupplier canStepOver, Runnable stepOver, BooleanSupplier canStepInto, Runnable stepInto, BooleanSupplier canBackToBreakpoint, Runnable backToBreakpoint, BooleanSupplier canStepBackOver, Runnable stepBackOver, BooleanSupplier canStepBack, Runnable stepBack)"
    },
    {
      "name": "CompilerCommands",
      "signature": "CompilerCommands(BooleanSupplier canNext, Runnable next, BooleanSupplier canNextStage, Runnable nextStage, BooleanSupplier canRunToExecution, Runnable runToExecution, BooleanSupplier canPlay, Runnable play, BooleanSupplier canPlayFast, Runnable playFast, BooleanSupplier canPause, Runnable pause)"
    },
    {
      "name": "SettingsCommands",
      "signature": "SettingsCommands(Consumer<String> themeSetter, Runnable themeNext, Runnable themePrevious, LongConsumer frameIntervalSetter, LongSupplier currentFrameInterval, LongSupplier minFrameInterval, LongSupplier maxFrameInterval, long frameIntervalStep, DoubleConsumer uiScaleSetter, DoubleSupplier currentUiScale, DoubleSupplier minUiScale, DoubleSupplier maxUiScale, double uiScaleStep)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCDebuggerCommands {
  readonly canStart?: BooleanSupplier;
  readonly start: Runnable;
  readonly canRunToEnd?: BooleanSupplier;
  readonly runToEnd: Runnable;
  readonly canRunToBreakpoint?: BooleanSupplier;
  readonly runToBreakpoint: Runnable;
  readonly canStepOver?: BooleanSupplier;
  readonly stepOver: Runnable;
  readonly canStepInto?: BooleanSupplier;
  readonly stepInto: Runnable;
  readonly canBackToBreakpoint?: BooleanSupplier;
  readonly backToBreakpoint: Runnable;
  readonly canStepBackOver?: BooleanSupplier;
  readonly stepBackOver: Runnable;
  readonly canStepBack?: BooleanSupplier;
  readonly stepBack: Runnable;
}

export interface MiniCCompilerCommands {
  readonly canNext: BooleanSupplier;
  readonly next: Runnable;
  readonly canNextStage: BooleanSupplier;
  readonly nextStage: Runnable;
  readonly canRunToExecution: BooleanSupplier;
  readonly runToExecution: Runnable;
  readonly canPlay: BooleanSupplier;
  readonly play: Runnable;
  readonly canPlayFast: BooleanSupplier;
  readonly playFast: Runnable;
  readonly canPause: BooleanSupplier;
  readonly pause: Runnable;
}

export interface MiniCSettingsCommands {
  readonly themeSetter: (themeName: string) => void;
  readonly themeNext: Runnable;
  readonly themePrevious: Runnable;
  readonly frameIntervalSetter: LongConsumer;
  readonly currentFrameInterval: () => number;
  readonly minFrameInterval: () => number;
  readonly maxFrameInterval: () => number;
  readonly frameIntervalStep: number;
  readonly uiScaleSetter: DoubleConsumer;
  readonly currentUiScale: () => number;
  readonly minUiScale: () => number;
  readonly maxUiScale: () => number;
  readonly uiScaleStep: number;
}

const defaultTrue: BooleanSupplier = () => true;
const installedViewportTargets = new WeakMap<HTMLElement, MiniCViewportAdapter>();

export class MiniCWorkbenchControlHub {
  static readonly mirror = miniCWorkbenchControlHubMirror;

  static readonly VIEWPORT_TARGET_PROPERTY = "minic.uilocal.control.viewportTargetAdapter";
  static readonly DEBUG_START = "debug.start";
  static readonly DEBUG_RUN_TO_END = "debug.runToEnd";
  static readonly DEBUG_RUN_TO_BREAKPOINT = "debug.runToBreakpoint";
  static readonly DEBUG_STEP_OVER = "debug.stepOver";
  static readonly DEBUG_STEP_INTO = "debug.stepInto";
  static readonly DEBUG_BACK_TO_BREAKPOINT = "debug.backToBreakpoint";
  static readonly DEBUG_STEP_BACK_OVER = "debug.stepBackOver";
  static readonly DEBUG_STEP_BACK = "debug.stepBack";
  static readonly COMPILER_NEXT = "compiler.next";
  static readonly COMPILER_NEXT_STAGE = "compiler.nextStage";
  static readonly COMPILER_RUN_TO_EXECUTION = "compiler.runToExecution";
  static readonly COMPILER_PLAY = "compiler.play";
  static readonly COMPILER_PLAY_FAST = "compiler.playFast";
  static readonly COMPILER_PAUSE = "compiler.pause";
  static readonly SETTINGS_THEME_SET = "settings.theme.set";
  static readonly SETTINGS_THEME_NEXT = "settings.theme.next";
  static readonly SETTINGS_THEME_PREVIOUS = "settings.theme.previous";
  static readonly SETTINGS_FRAME_INTERVAL_SET = "settings.frameInterval.set";
  static readonly SETTINGS_FRAME_INTERVAL_INCREASE = "settings.frameInterval.increase";
  static readonly SETTINGS_FRAME_INTERVAL_DECREASE = "settings.frameInterval.decrease";
  static readonly SETTINGS_UI_SCALE_INCREASE = "settings.uiScale.increase";
  static readonly SETTINGS_UI_SCALE_DECREASE = "settings.uiScale.decrease";
  static readonly VIEWPORT_ZOOM_IN = "viewport.zoom.in";
  static readonly VIEWPORT_ZOOM_OUT = "viewport.zoom.out";
  static readonly VIEWPORT_SCROLL_UP = "viewport.scroll.up";
  static readonly VIEWPORT_SCROLL_DOWN = "viewport.scroll.down";
  static readonly VIEWPORT_SCROLL_LEFT = "viewport.scroll.left";
  static readonly VIEWPORT_SCROLL_RIGHT = "viewport.scroll.right";
  static readonly VIEWPORT_CENTER_ACTIVE = "viewport.centerActive";

  readonly mirror = miniCWorkbenchControlHubMirror;

  private readonly localCommandRegistry: MiniCCommandRegistry;

  private readonly localViewportRegistry: MiniCViewportRegistry;

  private readonly localCommandIds = new Set<string>();

  private readonly additionalActiveTrackingActions: Runnable[] = [];

  private activeTrackingAction: Runnable = () => undefined;

  private activeTrackingScheduler: Scheduler = MiniCWorkbenchControlHub.runLaterIfPossible;

  private pendingThemeName: string | null = null;

  private pendingFrameIntervalMillis: number | null = null;

  constructor(
    commandRegistry = new MiniCCommandRegistry(),
    viewportRegistry = new MiniCViewportRegistry(),
  ) {
    this.localCommandRegistry = requireValue(commandRegistry, "commandRegistry");
    this.localViewportRegistry = requireValue(viewportRegistry, "viewportRegistry");
    this.registerViewportCommands();
  }

  commandRegistry(): MiniCCommandRegistry {
    return this.localCommandRegistry;
  }

  viewportRegistry(): MiniCViewportRegistry {
    return this.localViewportRegistry;
  }

  commandIds(): ReadonlySet<string> {
    return new Set(this.localCommandIds);
  }

  setActiveTrackingAction(activeTrackingAction: Runnable): void {
    this.activeTrackingAction = requireValue(activeTrackingAction, "activeTrackingAction");
  }

  setActiveTrackingScheduler(activeTrackingScheduler: Scheduler): void {
    this.activeTrackingScheduler = requireValue(activeTrackingScheduler, "activeTrackingScheduler");
  }

  addActiveTrackingAction(activeTrackingAction: Runnable): void {
    this.additionalActiveTrackingActions.push(requireValue(activeTrackingAction, "activeTrackingAction"));
  }

  registerDebuggerCommands(commands: MiniCDebuggerCommands): void {
    const safeCommands = requireValue(commands, "commands");
    this.register(MiniCWorkbenchControlHub.DEBUG_START, "从头开始", safeCommands.canStart ?? defaultTrue, safeCommands.start);
    this.register(MiniCWorkbenchControlHub.DEBUG_RUN_TO_END, "运行到结束", safeCommands.canRunToEnd ?? defaultTrue, safeCommands.runToEnd);
    this.register(MiniCWorkbenchControlHub.DEBUG_RUN_TO_BREAKPOINT, "下个断点", safeCommands.canRunToBreakpoint ?? defaultTrue, safeCommands.runToBreakpoint);
    this.register(MiniCWorkbenchControlHub.DEBUG_STEP_OVER, "本层下一句", safeCommands.canStepOver ?? defaultTrue, safeCommands.stepOver);
    this.register(MiniCWorkbenchControlHub.DEBUG_STEP_INTO, "下一句", safeCommands.canStepInto ?? defaultTrue, safeCommands.stepInto);
    this.register(MiniCWorkbenchControlHub.DEBUG_BACK_TO_BREAKPOINT, "上个断点", safeCommands.canBackToBreakpoint ?? defaultTrue, safeCommands.backToBreakpoint);
    this.register(MiniCWorkbenchControlHub.DEBUG_STEP_BACK_OVER, "本层上一句", safeCommands.canStepBackOver ?? defaultTrue, safeCommands.stepBackOver);
    this.register(MiniCWorkbenchControlHub.DEBUG_STEP_BACK, "上一句", safeCommands.canStepBack ?? defaultTrue, safeCommands.stepBack);
  }

  registerCompilerCommands(commands: MiniCCompilerCommands): void {
    const safeCommands = requireValue(commands, "commands");
    this.register(MiniCWorkbenchControlHub.COMPILER_NEXT, "下一步", safeCommands.canNext, safeCommands.next);
    this.register(MiniCWorkbenchControlHub.COMPILER_NEXT_STAGE, "下一阶段", safeCommands.canNextStage, safeCommands.nextStage);
    this.register(MiniCWorkbenchControlHub.COMPILER_RUN_TO_EXECUTION, "到执行", safeCommands.canRunToExecution, safeCommands.runToExecution);
    this.register(MiniCWorkbenchControlHub.COMPILER_PLAY, "播放", safeCommands.canPlay, safeCommands.play);
    this.register(MiniCWorkbenchControlHub.COMPILER_PLAY_FAST, "2x", safeCommands.canPlayFast, safeCommands.playFast);
    this.register(MiniCWorkbenchControlHub.COMPILER_PAUSE, "暂停", safeCommands.canPause, safeCommands.pause);
  }

  registerSettingsCommands(commands: MiniCSettingsCommands): void {
    const safeCommands = requireValue(commands, "commands");
    if (safeCommands.frameIntervalStep < 1) {
      throw new RangeError("frameIntervalStep must be positive");
    }
    if (safeCommands.uiScaleStep <= 0) {
      throw new RangeError("uiScaleStep must be positive");
    }
    this.register(MiniCWorkbenchControlHub.SETTINGS_THEME_SET, "设置主题", () => this.pendingThemeName !== null, () => {
      if (this.pendingThemeName !== null) {
        safeCommands.themeSetter(this.pendingThemeName);
      }
    });
    this.register(MiniCWorkbenchControlHub.SETTINGS_THEME_NEXT, "下一个主题", defaultTrue, safeCommands.themeNext);
    this.register(MiniCWorkbenchControlHub.SETTINGS_THEME_PREVIOUS, "上一个主题", defaultTrue, safeCommands.themePrevious);
    this.register(MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_SET, "设置帧间隔", () => this.pendingFrameIntervalMillis !== null, () => {
      if (this.pendingFrameIntervalMillis !== null) {
        safeCommands.frameIntervalSetter(MiniCWorkbenchControlHub.clamp(
          this.pendingFrameIntervalMillis,
          safeCommands.minFrameInterval,
          safeCommands.maxFrameInterval,
        ));
      }
    });
    this.register(MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_INCREASE, "增加帧间隔", defaultTrue, () => {
      safeCommands.frameIntervalSetter(MiniCWorkbenchControlHub.clamp(
        safeCommands.currentFrameInterval() + safeCommands.frameIntervalStep,
        safeCommands.minFrameInterval,
        safeCommands.maxFrameInterval,
      ));
    });
    this.register(MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_DECREASE, "减少帧间隔", defaultTrue, () => {
      safeCommands.frameIntervalSetter(MiniCWorkbenchControlHub.clamp(
        safeCommands.currentFrameInterval() - safeCommands.frameIntervalStep,
        safeCommands.minFrameInterval,
        safeCommands.maxFrameInterval,
      ));
    });
    this.register(MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_INCREASE, "增加全局缩放", defaultTrue, () => {
      safeCommands.uiScaleSetter(MiniCWorkbenchControlHub.clamp(
        safeCommands.currentUiScale() + safeCommands.uiScaleStep,
        safeCommands.minUiScale,
        safeCommands.maxUiScale,
      ));
    });
    this.register(MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_DECREASE, "减少全局缩放", defaultTrue, () => {
      safeCommands.uiScaleSetter(MiniCWorkbenchControlHub.clamp(
        safeCommands.currentUiScale() - safeCommands.uiScaleStep,
        safeCommands.minUiScale,
        safeCommands.maxUiScale,
      ));
    });
  }

  commandEnabled(commandId: string): boolean {
    return this.localCommandRegistry.enabled(commandId);
  }

  execute(commandId: string): boolean {
    const executed = this.localCommandRegistry.execute(commandId);
    if (executed) {
      this.activeTrackingScheduler(() => this.trackActiveViews());
    }
    return executed;
  }

  setTheme(themeName: string): boolean {
    this.pendingThemeName = requireValue(themeName, "themeName");
    try {
      return this.execute(MiniCWorkbenchControlHub.SETTINGS_THEME_SET);
    } finally {
      this.pendingThemeName = null;
    }
  }

  setFrameIntervalMillis(millis: number): boolean {
    this.pendingFrameIntervalMillis = Math.trunc(millis);
    try {
      return this.execute(MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_SET);
    } finally {
      this.pendingFrameIntervalMillis = null;
    }
  }

  increaseFrameInterval(): boolean {
    return this.execute(MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_INCREASE);
  }

  decreaseFrameInterval(): boolean {
    return this.execute(MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_DECREASE);
  }

  increaseUiScale(): boolean {
    return this.execute(MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_INCREASE);
  }

  decreaseUiScale(): boolean {
    return this.execute(MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_DECREASE);
  }

  handleZoom(localPoint: ViewportPoint, delta: number): void {
    const adapter = this.localViewportRegistry.currentTarget();
    if (adapter?.canZoom()) {
      adapter.zoomAt(requireValue(localPoint, "localPoint"), delta);
    }
  }

  handleScrollVertical(delta: number): void {
    const adapter = this.localViewportRegistry.currentTarget();
    if (adapter?.canScrollVertical()) {
      adapter.scrollVertical(delta);
    }
  }

  handleScrollHorizontal(delta: number): void {
    const adapter = this.localViewportRegistry.currentTarget();
    if (adapter?.canScrollHorizontal()) {
      adapter.scrollHorizontal(delta);
    }
  }

  handlePan(deltaX: number, deltaY: number): void {
    const adapter = this.localViewportRegistry.currentTarget();
    if (adapter?.canPan()) {
      adapter.pan(deltaX, deltaY);
    }
  }

  handleCenterActive(): void {
    this.localViewportRegistry.currentTarget()?.centerActive();
  }

  installViewportTarget(node: HTMLElement, adapter: MiniCViewportAdapter): void {
    const safeNode = requireValue(node, "node");
    const safeAdapter = requireValue(adapter, "adapter");
    if (installedViewportTargets.get(safeNode) === safeAdapter) {
      return;
    }
    installedViewportTargets.set(safeNode, safeAdapter);
    safeNode.dataset.minicViewportTarget = MiniCWorkbenchControlHub.VIEWPORT_TARGET_PROPERTY;
    safeNode.addEventListener("mouseenter", () => this.localViewportRegistry.hover(safeAdapter));
    safeNode.addEventListener("mouseleave", () => this.localViewportRegistry.clearHover(safeAdapter));
    safeNode.addEventListener("click", (event) => {
      if (event.button === 0) {
        this.localViewportRegistry.pin(safeAdapter);
      }
    });
  }

  register(id: string, label: string, enabled: BooleanSupplier, action: Runnable): void {
    this.localCommandRegistry.register(new MiniCControlCommand(id, label, enabled, action));
    this.localCommandIds.add(id);
  }

  trackActiveViews(): void {
    this.activeTrackingAction();
    for (const action of this.additionalActiveTrackingActions) {
      action();
    }
  }

  static runLaterIfPossible(action: Runnable): void {
    if (typeof queueMicrotask === "function") {
      queueMicrotask(action);
      return;
    }
    window.setTimeout(action, 0);
  }

  static clamp(value: number, minSupplier: () => number, maxSupplier: () => number): number {
    const min = minSupplier();
    const max = maxSupplier();
    return clampNumber(value, min, max);
  }

  private registerViewportCommands(): void {
    this.register(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN, "放大", () => this.localViewportRegistry.currentTarget()?.canZoom() ?? false, () => {
      this.handleZoom({ x: 0, y: 0 }, 1);
    });
    this.register(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT, "缩小", () => this.localViewportRegistry.currentTarget()?.canZoom() ?? false, () => {
      this.handleZoom({ x: 0, y: 0 }, -1);
    });
    this.register(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_UP, "上滚", () => this.localViewportRegistry.currentTarget()?.canScrollVertical() ?? false, () => {
      this.handleScrollVertical(-48);
    });
    this.register(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, "下滚", () => this.localViewportRegistry.currentTarget()?.canScrollVertical() ?? false, () => {
      this.handleScrollVertical(48);
    });
    this.register(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_LEFT, "左滚", () => this.localViewportRegistry.currentTarget()?.canScrollHorizontal() ?? false, () => {
      this.handleScrollHorizontal(-48);
    });
    this.register(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_RIGHT, "右滚", () => this.localViewportRegistry.currentTarget()?.canScrollHorizontal() ?? false, () => {
      this.handleScrollHorizontal(48);
    });
    this.register(MiniCWorkbenchControlHub.VIEWPORT_CENTER_ACTIVE, "居中当前项", () => this.localViewportRegistry.currentTarget() !== undefined, () => {
      this.handleCenterActive();
    });
  }

  summary(): string {
    return `MiniCWorkbenchControlHub: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCWorkbenchControlHub;
