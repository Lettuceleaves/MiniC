import type { JavaMirrorFile } from "../translation/javaMirror";
import type {
  MiniCPlaybackMode,
  MiniCStageId,
  UiAssemblyLineVisualDto,
  UiControlResultDto,
  UiCurrentStateDto,
  UiDebugAsmViewDto,
  UiDebugAstViewDto,
  UiDebugDataStructureViewDto,
  UiDebugIrViewDto,
  UiDebugMetadataViewDto,
  UiDebugStateDto,
  UiDiagnosticDto,
  UiGlobalDataDto,
  UiIrLineVisualDto,
  UiLexerTokenVisualDto,
  UiRealtimeAnalysisDto,
  UiSemanticScopeVisualDto,
  UiSourceRangeDto,
  UiStageDataDto,
  UiStageVisualDto,
} from "../translation/uiapi";

export const miniCWorkbenchViewModelMirror = {
  "javaPath": "src/main/java/minic/uilocal/workbench/MiniCWorkbenchViewModel.java",
  "webPath": "uiweb/src/workbench/MiniCWorkbenchViewModel.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCWorkbenchViewModel",
  "kind": "class",
  "imports": [
    "javafx.beans.property.ReadOnlyBooleanProperty",
    "javafx.beans.property.ReadOnlyBooleanWrapper",
    "javafx.beans.property.ReadOnlyObjectProperty",
    "javafx.beans.property.ReadOnlyObjectWrapper",
    "javafx.beans.property.ReadOnlyStringProperty",
    "javafx.beans.property.ReadOnlyStringWrapper",
    "minic.uiapi.MiniCObservationApi",
    "minic.uiapi.MiniCDebugApi",
    "minic.uiapi.UiControlResultDto",
    "minic.uiapi.UiCurrentStateDto",
    "minic.uiapi.UiDebugAsmViewDto",
    "minic.uiapi.UiDebugAstViewDto",
    "minic.uiapi.UiDebugDataStructureViewDto",
    "minic.uiapi.UiDebugIrViewDto",
    "minic.uiapi.UiDebugMetadataViewDto",
    "minic.uiapi.UiDebugStateDto",
    "minic.uiapi.UiGlobalDataDto",
    "minic.uiapi.UiRealtimeAnalysisDto",
    "minic.uiapi.UiStageDataDto",
    "minic.uiapi.UiStageVisualDto",
    "java.util.List",
    "java.util.Map",
    "java.util.Objects",
    "java.util.concurrent.ConcurrentHashMap"
  ],
  "fields": [
    {
      "name": "api",
      "signature": "private final MiniCObservationApi api;"
    },
    {
      "name": "debugApi",
      "signature": "private final MiniCDebugApi debugApi ="
    },
    {
      "name": "realtimeAnalyzer",
      "signature": "private final MiniCRealtimeAnalyzer realtimeAnalyzer;"
    },
    {
      "name": "sourceName",
      "signature": "private final ReadOnlyStringWrapper sourceName ="
    },
    {
      "name": "sourceText",
      "signature": "private final ReadOnlyStringWrapper sourceText ="
    },
    {
      "name": "lastOutcome",
      "signature": "private final ReadOnlyStringWrapper lastOutcome ="
    },
    {
      "name": "sessionStarted",
      "signature": "private final ReadOnlyBooleanWrapper sessionStarted ="
    },
    {
      "name": "currentState",
      "signature": "private final ReadOnlyObjectWrapper<UiCurrentStateDto> currentState ="
    },
    {
      "name": "currentStageData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageDataDto> currentStageData ="
    },
    {
      "name": "currentStageVisualData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageVisualDto> currentStageVisualData ="
    },
    {
      "name": "lexerVisualData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageVisualDto> lexerVisualData ="
    },
    {
      "name": "astVisualData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageVisualDto> astVisualData ="
    },
    {
      "name": "semanticVisualData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageVisualDto> semanticVisualData ="
    },
    {
      "name": "codegenVisualData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageVisualDto> codegenVisualData ="
    },
    {
      "name": "globalData",
      "signature": "private final ReadOnlyObjectWrapper<UiGlobalDataDto> globalData ="
    },
    {
      "name": "realtimeAnalysis",
      "signature": "private final ReadOnlyObjectWrapper<UiRealtimeAnalysisDto> realtimeAnalysis ="
    },
    {
      "name": "lastControlResult",
      "signature": "private final ReadOnlyObjectWrapper<UiControlResultDto> lastControlResult ="
    },
    {
      "name": "selectedVisualStage",
      "signature": "private final ReadOnlyStringWrapper selectedVisualStage ="
    },
    {
      "name": "debugStarted",
      "signature": "private final ReadOnlyBooleanWrapper debugStarted ="
    },
    {
      "name": "debugState",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugStateDto> debugState ="
    },
    {
      "name": "debugMetadataView",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugMetadataViewDto> debugMetadataView ="
    },
    {
      "name": "debugDataStructureView",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugDataStructureViewDto> debugDataStructureView ="
    },
    {
      "name": "debugAstView",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugAstViewDto> debugAstView ="
    },
    {
      "name": "debugIrView",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugIrViewDto> debugIrView ="
    },
    {
      "name": "debugAsmView",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugAsmViewDto> debugAsmView ="
    },
    {
      "name": "debugBreakpointLines",
      "signature": "private final ReadOnlyObjectWrapper<List<Integer>> debugBreakpointLines ="
    },
    {
      "name": "viewportStates",
      "signature": "private final Map<String, UiViewportState> viewportStates ="
    },
    {
      "name": "executionInputDraft",
      "signature": "private String executionInputDraft ="
    },
    {
      "name": "DEFAULT",
      "signature": "public static final UiViewportState DEFAULT ="
    }
  ],
  "methods": [
    {
      "name": "loadSource",
      "signature": "loadSource(String name, String source)"
    },
    {
      "name": "renameSource",
      "signature": "renameSource(String name)"
    },
    {
      "name": "clearSessionState",
      "signature": "clearSessionState()"
    },
    {
      "name": "clearDebugState",
      "signature": "clearDebugState()"
    },
    {
      "name": "submitRealtimeSource",
      "signature": "submitRealtimeSource(String name, String source)"
    },
    {
      "name": "startSession",
      "signature": "startSession()"
    },
    {
      "name": "next",
      "signature": "next()"
    },
    {
      "name": "nextStage",
      "signature": "nextStage()"
    },
    {
      "name": "runToExecution",
      "signature": "runToExecution()"
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
      "name": "tick",
      "signature": "tick()"
    },
    {
      "name": "pause",
      "signature": "pause()"
    },
    {
      "name": "confirmExecutionInput",
      "signature": "confirmExecutionInput(String standardInput)"
    },
    {
      "name": "updateExecutionInputDraft",
      "signature": "updateExecutionInputDraft(String standardInput)"
    },
    {
      "name": "executionInputDraft",
      "signature": "executionInputDraft()"
    },
    {
      "name": "canNextControl",
      "signature": "canNextControl()"
    },
    {
      "name": "canNextStageControl",
      "signature": "canNextStageControl()"
    },
    {
      "name": "canRunToExecutionControl",
      "signature": "canRunToExecutionControl()"
    },
    {
      "name": "canPlayControl",
      "signature": "canPlayControl()"
    },
    {
      "name": "canPlayFastControl",
      "signature": "canPlayFastControl()"
    },
    {
      "name": "selectVisualStage",
      "signature": "selectVisualStage(String stage)"
    },
    {
      "name": "startDebug",
      "signature": "startDebug()"
    },
    {
      "name": "setDebugBreakpoints",
      "signature": "setDebugBreakpoints(List<Integer> lines)"
    },
    {
      "name": "syncDebugBreakpoints",
      "signature": "syncDebugBreakpoints()"
    },
    {
      "name": "setDebugBreakpoint",
      "signature": "setDebugBreakpoint(int line)"
    },
    {
      "name": "clearDebugBreakpoint",
      "signature": "clearDebugBreakpoint(int line)"
    },
    {
      "name": "debugRunToBreakpoint",
      "signature": "debugRunToBreakpoint()"
    },
    {
      "name": "debugRunToEnd",
      "signature": "debugRunToEnd()"
    },
    {
      "name": "debugFastForward",
      "signature": "debugFastForward()"
    },
    {
      "name": "debugStepOver",
      "signature": "debugStepOver()"
    },
    {
      "name": "debugStepInto",
      "signature": "debugStepInto()"
    },
    {
      "name": "debugStepOut",
      "signature": "debugStepOut()"
    },
    {
      "name": "debugPause",
      "signature": "debugPause()"
    },
    {
      "name": "debugRestart",
      "signature": "debugRestart()"
    },
    {
      "name": "debugClose",
      "signature": "debugClose()"
    },
    {
      "name": "debugStepBack",
      "signature": "debugStepBack()"
    },
    {
      "name": "debugStepBackOver",
      "signature": "debugStepBackOver()"
    },
    {
      "name": "debugBackToBreakpoint",
      "signature": "debugBackToBreakpoint()"
    },
    {
      "name": "debugBackToCallSite",
      "signature": "debugBackToCallSite()"
    },
    {
      "name": "refreshDebug",
      "signature": "refreshDebug()"
    },
    {
      "name": "refreshAll",
      "signature": "refreshAll()"
    },
    {
      "name": "sourceNameProperty",
      "signature": "sourceNameProperty()"
    },
    {
      "name": "sourceTextProperty",
      "signature": "sourceTextProperty()"
    },
    {
      "name": "lastOutcomeProperty",
      "signature": "lastOutcomeProperty()"
    },
    {
      "name": "sessionStartedProperty",
      "signature": "sessionStartedProperty()"
    },
    {
      "name": "currentStateProperty",
      "signature": "currentStateProperty()"
    },
    {
      "name": "currentStageDataProperty",
      "signature": "currentStageDataProperty()"
    },
    {
      "name": "currentStageVisualDataProperty",
      "signature": "currentStageVisualDataProperty()"
    },
    {
      "name": "lexerVisualDataProperty",
      "signature": "lexerVisualDataProperty()"
    },
    {
      "name": "astVisualDataProperty",
      "signature": "astVisualDataProperty()"
    },
    {
      "name": "semanticVisualDataProperty",
      "signature": "semanticVisualDataProperty()"
    },
    {
      "name": "codegenVisualDataProperty",
      "signature": "codegenVisualDataProperty()"
    },
    {
      "name": "globalDataProperty",
      "signature": "globalDataProperty()"
    },
    {
      "name": "realtimeAnalysisProperty",
      "signature": "realtimeAnalysisProperty()"
    },
    {
      "name": "lastControlResultProperty",
      "signature": "lastControlResultProperty()"
    },
    {
      "name": "selectedVisualStageProperty",
      "signature": "selectedVisualStageProperty()"
    },
    {
      "name": "debugStartedProperty",
      "signature": "debugStartedProperty()"
    },
    {
      "name": "debugStateProperty",
      "signature": "debugStateProperty()"
    },
    {
      "name": "debugMetadataViewProperty",
      "signature": "debugMetadataViewProperty()"
    },
    {
      "name": "debugDataStructureViewProperty",
      "signature": "debugDataStructureViewProperty()"
    },
    {
      "name": "debugAstViewProperty",
      "signature": "debugAstViewProperty()"
    },
    {
      "name": "debugIrViewProperty",
      "signature": "debugIrViewProperty()"
    },
    {
      "name": "debugAsmViewProperty",
      "signature": "debugAsmViewProperty()"
    },
    {
      "name": "debugBreakpointLinesProperty",
      "signature": "debugBreakpointLinesProperty()"
    },
    {
      "name": "viewportState",
      "signature": "viewportState(String key)"
    },
    {
      "name": "saveViewportState",
      "signature": "saveViewportState(String key, double hvalue, double vvalue)"
    },
    {
      "name": "clampUnit",
      "signature": "clampUnit(double value)"
    },
    {
      "name": "applyControlResult",
      "signature": "applyControlResult(UiControlResultDto result)"
    },
    {
      "name": "autoConfirmExecutionInput",
      "signature": "autoConfirmExecutionInput()"
    },
    {
      "name": "executionAwaitingInput",
      "signature": "executionAwaitingInput()"
    },
    {
      "name": "applyRealtimeAnalysis",
      "signature": "applyRealtimeAnalysis(UiRealtimeAnalysisDto result)"
    },
    {
      "name": "ensureDebugStarted",
      "signature": "ensureDebugStarted()"
    },
    {
      "name": "applyPendingDebugBreakpoints",
      "signature": "applyPendingDebugBreakpoints()"
    },
    {
      "name": "mergeBreakpoint",
      "signature": "mergeBreakpoint(int line)"
    },
    {
      "name": "normalizeBreakpoints",
      "signature": "normalizeBreakpoints(List<Integer> lines)"
    },
    {
      "name": "UiViewportState",
      "signature": "UiViewportState(double hvalue, double vvalue)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCWorkbenchViewModel {
  static readonly mirror = miniCWorkbenchViewModelMirror;

  readonly mirror = miniCWorkbenchViewModelMirror;

  private readonly listeners = new Set<() => void>();

  private readonly sourceNameStore = new MiniCWritableProperty("", () => this.emit());

  private readonly sourceTextStore = new MiniCWritableProperty("", () => this.emit());

  private readonly lastOutcomeStore = new MiniCWritableProperty("", () => this.emit());

  private readonly sessionStartedStore = new MiniCWritableProperty(false, () => this.emit());

  private readonly currentStateStore = new MiniCWritableProperty<UiCurrentStateDto | null>(null, () =>
    this.emit(),
  );

  private readonly currentStageDataStore = new MiniCWritableProperty<UiStageDataDto | null>(null, () =>
    this.emit(),
  );

  private readonly currentStageVisualDataStore = new MiniCWritableProperty<UiStageVisualDto | null>(
    null,
    () => this.emit(),
  );

  private readonly lexerVisualDataStore = new MiniCWritableProperty<UiStageVisualDto | null>(null, () =>
    this.emit(),
  );

  private readonly astVisualDataStore = new MiniCWritableProperty<UiStageVisualDto | null>(null, () =>
    this.emit(),
  );

  private readonly semanticVisualDataStore = new MiniCWritableProperty<UiStageVisualDto | null>(null, () =>
    this.emit(),
  );

  private readonly codegenVisualDataStore = new MiniCWritableProperty<UiStageVisualDto | null>(null, () =>
    this.emit(),
  );

  private readonly globalDataStore = new MiniCWritableProperty<UiGlobalDataDto | null>(null, () =>
    this.emit(),
  );

  private readonly realtimeAnalysisStore = new MiniCWritableProperty<UiRealtimeAnalysisDto | null>(
    null,
    () => this.emit(),
  );

  private readonly lastControlResultStore = new MiniCWritableProperty<UiControlResultDto | null>(
    null,
    () => this.emit(),
  );

  private readonly selectedVisualStageStore = new MiniCWritableProperty("", () => this.emit());

  private readonly debugStartedStore = new MiniCWritableProperty(false, () => this.emit());

  private readonly debugStateStore = new MiniCWritableProperty<UiDebugStateDto | null>(null, () =>
    this.emit(),
  );

  private readonly debugMetadataViewStore = new MiniCWritableProperty<UiDebugMetadataViewDto | null>(
    null,
    () => this.emit(),
  );

  private readonly debugDataStructureViewStore =
    new MiniCWritableProperty<UiDebugDataStructureViewDto | null>(null, () => this.emit());

  private readonly debugAstViewStore = new MiniCWritableProperty<UiDebugAstViewDto | null>(null, () =>
    this.emit(),
  );

  private readonly debugIrViewStore = new MiniCWritableProperty<UiDebugIrViewDto | null>(null, () =>
    this.emit(),
  );

  private readonly debugAsmViewStore = new MiniCWritableProperty<UiDebugAsmViewDto | null>(null, () =>
    this.emit(),
  );

  private readonly debugBreakpointLinesStore = new MiniCWritableProperty<readonly number[]>([], () =>
    this.emit(),
  );

  private readonly viewportStates = new Map<string, UiViewportState>();

  private executionInputDraftText = "";

  private realtimeVersion = 0;

  constructor(initialSourceName = "", initialSourceText = "") {
    if (initialSourceName !== "" || initialSourceText !== "") {
      this.loadSource(initialSourceName, initialSourceText);
    }
  }

  loadSource(name: string, source: string): void {
    this.sourceNameStore.set(name);
    this.sourceTextStore.set(source);
    this.clearSessionState();
  }

  renameSource(name: string): void {
    this.sourceNameStore.set(name);
    this.clearSessionState();
  }

  submitRealtimeSource(name: string, source: string): void {
    this.sourceNameStore.set(name);
    this.sourceTextStore.set(source);
    this.realtimeAnalysisStore.set(createRealtimeAnalysis(name, source, ++this.realtimeVersion));
  }

  startSession(): void {
    this.sessionStartedStore.set(true);
    this.selectedVisualStageStore.set("");
    this.applyStage("source", 1, "PAUSED", controlResult("OK", "source", "会话已启动", "源码已加载。"));
  }

  next(): UiControlResultDto {
    this.autoConfirmExecutionInput();
    const state = this.currentStateStore.get();
    if (state === null) {
      const result = controlResult("CANNOT_ADVANCE", "source", "尚未启动", "请先启动观测会话。");
      this.applyControlResult(result);
      return result;
    }
    if (!state.canNext) {
      const result = controlResult("CANNOT_ADVANCE", state.currentStage, "无法前进", "当前阶段已经没有下一步。");
      this.applyControlResult(result);
      return result;
    }
    const definition = stageDefinition(state.currentStage);
    const nextStep = Math.min(definition.totalSteps, state.stageStepIndex + 1);
    if (nextStep > state.stageStepIndex || state.currentStage === "execution") {
      const result = controlResult("OK", state.currentStage, "下一步", definition.item(nextStep));
      this.applyStage(state.currentStage, nextStep, state.playbackMode, result);
      return result;
    }
    return this.nextStage();
  }

  nextStage(): UiControlResultDto {
    this.autoConfirmExecutionInput();
    const state = this.currentStateStore.get();
    if (state === null) {
      const result = controlResult("CANNOT_ADVANCE", "source", "尚未启动", "请先启动观测会话。");
      this.applyControlResult(result);
      return result;
    }
    const nextStage = stageAfter(state.currentStage);
    if (nextStage === null) {
      const result = controlResult("CANNOT_ADVANCE", state.currentStage, "已到末尾", "当前已经位于最后阶段。");
      this.applyControlResult(result);
      return result;
    }
    const result = controlResult("OK", nextStage.id, "下一阶段", nextStage.item(0));
    this.selectedVisualStageStore.set("");
    this.applyStage(nextStage.id, 0, state.playbackMode, result);
    return result;
  }

  runToExecution(): UiControlResultDto {
    const state = this.currentStateStore.get();
    if (state !== null && state.currentStage === "execution") {
      const result = controlResult("CANNOT_ADVANCE", "execution", "已在执行阶段", "当前已经位于执行阶段入口。");
      this.applyControlResult(result);
      return result;
    }
    const result = controlResult("OK", "execution", "到执行", "已定位到执行阶段入口。");
    this.selectedVisualStageStore.set("");
    this.applyStage("execution", 0, "PAUSED", result);
    return result;
  }

  play(): UiControlResultDto {
    return this.setPlaybackMode("PLAYING", "播放", "自动播放已开始。");
  }

  playFast(): UiControlResultDto {
    return this.setPlaybackMode("FAST_PLAYING", "2x", "快速播放已开始。");
  }

  tick(): UiControlResultDto {
    const result = this.next();
    const state = this.currentStateStore.get();
    if (state !== null && !state.canNext) {
      this.applyStage(state.currentStage, state.stageStepIndex, "PAUSED", result);
    }
    return result;
  }

  pause(): UiControlResultDto {
    return this.setPlaybackMode("PAUSED", "暂停", "播放已暂停。");
  }

  confirmExecutionInput(standardInput: string): UiControlResultDto {
    this.selectedVisualStageStore.set("");
    this.executionInputDraftText = standardInput ?? "";
    const stage = this.currentStateStore.get()?.currentStage ?? "execution";
    const result = controlResult("OK", stage, "输入已确认", "执行阶段标准输入已确认。");
    this.applyControlResult(result);
    this.refreshAll();
    return result;
  }

  updateExecutionInputDraft(standardInput: string): void {
    this.executionInputDraftText = standardInput ?? "";
  }

  executionInputDraft(): string {
    return this.executionInputDraftText;
  }

  canNextControl(): boolean {
    const state = this.currentStateStore.get();
    return state !== null && (state.canNext || this.executionAwaitingInput());
  }

  canNextStageControl(): boolean {
    return this.canNextControl();
  }

  canRunToExecutionControl(): boolean {
    const state = this.currentStateStore.get();
    return state !== null && state.canNext && state.currentStage !== "execution";
  }

  canPlayControl(): boolean {
    const state = this.currentStateStore.get();
    return state !== null && (state.canPlay || this.executionAwaitingInput());
  }

  canPlayFastControl(): boolean {
    const state = this.currentStateStore.get();
    return state !== null && (state.canPlayFast || this.executionAwaitingInput());
  }

  selectVisualStage(stage: string): void {
    this.selectedVisualStageStore.set(stage);
    const state = this.currentStateStore.get();
    if (stage !== "" && state !== null && state.playbackMode !== "PAUSED") {
      this.pause();
    }
    this.refreshVisualData();
  }

  startDebug(): void {
    this.debugStartedStore.set(true);
    this.refreshDebug();
  }

  setDebugBreakpoints(lines: readonly number[]): void {
    this.debugBreakpointLinesStore.set(normalizeBreakpoints(lines));
    this.syncDebugBreakpoints();
  }

  syncDebugBreakpoints(): void {
    if (this.debugStartedStore.get()) {
      this.refreshDebug();
    }
  }

  setDebugBreakpoint(line: number): void {
    this.ensureDebugStarted();
    this.debugBreakpointLinesStore.set(normalizeBreakpoints([...this.debugBreakpointLinesStore.get(), line]));
    this.refreshDebug();
  }

  clearDebugBreakpoint(line: number): void {
    this.ensureDebugStarted();
    this.debugBreakpointLinesStore.set(this.debugBreakpointLinesStore.get().filter((item) => item !== line));
    this.refreshDebug();
  }

  debugRunToBreakpoint(): void {
    this.ensureDebugStarted();
    this.refreshDebug(`运行到断点 ${this.debugBreakpointLinesStore.get()[0] ?? "-"}`);
  }

  debugRunToEnd(): void {
    this.ensureDebugStarted();
    this.refreshDebug("运行到结束");
  }

  debugFastForward(): void {
    this.ensureDebugStarted();
    this.refreshDebug("快进");
  }

  debugStepOver(): void {
    this.ensureDebugStarted();
    this.refreshDebug("本层下一句");
  }

  debugStepInto(): void {
    this.ensureDebugStarted();
    this.refreshDebug("下一句");
  }

  debugStepOut(): void {
    this.ensureDebugStarted();
    this.refreshDebug("步出");
  }

  debugPause(): void {
    this.ensureDebugStarted();
    this.refreshDebug("暂停");
  }

  debugRestart(): void {
    this.ensureDebugStarted();
    this.refreshDebug("重启");
  }

  debugClose(): void {
    this.debugStartedStore.set(false);
    this.clearDebugState();
  }

  debugStepBack(): void {
    this.ensureDebugStarted();
    this.refreshDebug("上一句");
  }

  debugStepBackOver(): void {
    this.ensureDebugStarted();
    this.refreshDebug("本层上一句");
  }

  debugBackToBreakpoint(): void {
    this.ensureDebugStarted();
    this.refreshDebug("上个断点");
  }

  debugBackToCallSite(): void {
    this.ensureDebugStarted();
    this.refreshDebug("返回调用处");
  }

  refreshDebug(message = "debug ready"): void {
    if (!this.debugStartedStore.get()) {
      return;
    }
    const state = this.currentStateStore.get();
    const currentLine = state?.sourceRange === null || state?.sourceRange === undefined ? 1 : 1;
    const breakpoints = this.debugBreakpointLinesStore
      .get()
      .map((line) => ({ line, enabled: true }));
    this.debugStateStore.set({
      sourceName: this.sourceNameStore.get(),
      playbackMode: "PAUSED",
      currentLine,
      breakpoints,
      timeline: [message],
    });
    this.debugMetadataViewStore.set({
      rows: [`源码: ${this.sourceNameStore.get() || "untitled.mc"}`, `断点: ${breakpoints.length}`],
    });
    this.debugDataStructureViewStore.set({
      title: "Debug 数据结构",
      rows: ["本地前端骨架暂未连接 runtime。"],
    });
    this.debugAstViewStore.set({ root: null, details: ["AST debug view pending runtime data."] });
    this.debugIrViewStore.set({ lines: [] });
    this.debugAsmViewStore.set({ lines: [] });
  }

  refreshAll(): void {
    if (!this.sessionStartedStore.get()) {
      return;
    }
    const state = this.currentStateStore.get();
    if (state !== null) {
      this.applyStage(state.currentStage, state.stageStepIndex, state.playbackMode);
    }
  }

  sourceNameProperty(): MiniCReadonlyProperty<string> {
    return this.sourceNameStore;
  }

  sourceTextProperty(): MiniCReadonlyProperty<string> {
    return this.sourceTextStore;
  }

  lastOutcomeProperty(): MiniCReadonlyProperty<string> {
    return this.lastOutcomeStore;
  }

  sessionStartedProperty(): MiniCReadonlyProperty<boolean> {
    return this.sessionStartedStore;
  }

  currentStateProperty(): MiniCReadonlyProperty<UiCurrentStateDto | null> {
    return this.currentStateStore;
  }

  currentStageDataProperty(): MiniCReadonlyProperty<UiStageDataDto | null> {
    return this.currentStageDataStore;
  }

  currentStageVisualDataProperty(): MiniCReadonlyProperty<UiStageVisualDto | null> {
    return this.currentStageVisualDataStore;
  }

  lexerVisualDataProperty(): MiniCReadonlyProperty<UiStageVisualDto | null> {
    return this.lexerVisualDataStore;
  }

  astVisualDataProperty(): MiniCReadonlyProperty<UiStageVisualDto | null> {
    return this.astVisualDataStore;
  }

  semanticVisualDataProperty(): MiniCReadonlyProperty<UiStageVisualDto | null> {
    return this.semanticVisualDataStore;
  }

  codegenVisualDataProperty(): MiniCReadonlyProperty<UiStageVisualDto | null> {
    return this.codegenVisualDataStore;
  }

  globalDataProperty(): MiniCReadonlyProperty<UiGlobalDataDto | null> {
    return this.globalDataStore;
  }

  realtimeAnalysisProperty(): MiniCReadonlyProperty<UiRealtimeAnalysisDto | null> {
    return this.realtimeAnalysisStore;
  }

  lastControlResultProperty(): MiniCReadonlyProperty<UiControlResultDto | null> {
    return this.lastControlResultStore;
  }

  selectedVisualStageProperty(): MiniCReadonlyProperty<string> {
    return this.selectedVisualStageStore;
  }

  debugStartedProperty(): MiniCReadonlyProperty<boolean> {
    return this.debugStartedStore;
  }

  debugStateProperty(): MiniCReadonlyProperty<UiDebugStateDto | null> {
    return this.debugStateStore;
  }

  debugMetadataViewProperty(): MiniCReadonlyProperty<UiDebugMetadataViewDto | null> {
    return this.debugMetadataViewStore;
  }

  debugDataStructureViewProperty(): MiniCReadonlyProperty<UiDebugDataStructureViewDto | null> {
    return this.debugDataStructureViewStore;
  }

  debugAstViewProperty(): MiniCReadonlyProperty<UiDebugAstViewDto | null> {
    return this.debugAstViewStore;
  }

  debugIrViewProperty(): MiniCReadonlyProperty<UiDebugIrViewDto | null> {
    return this.debugIrViewStore;
  }

  debugAsmViewProperty(): MiniCReadonlyProperty<UiDebugAsmViewDto | null> {
    return this.debugAsmViewStore;
  }

  debugBreakpointLinesProperty(): MiniCReadonlyProperty<readonly number[]> {
    return this.debugBreakpointLinesStore;
  }

  viewportState(key: string): UiViewportState {
    return this.viewportStates.get(key) ?? UiViewportState.DEFAULT;
  }

  saveViewportState(key: string, hvalue: number, vvalue: number): void {
    this.viewportStates.set(key, new UiViewportState(clampUnit(hvalue), clampUnit(vvalue)));
    this.emit();
  }

  snapshot(): MiniCWorkbenchSnapshot {
    return {
      sourceName: this.sourceNameStore.get(),
      sourceText: this.sourceTextStore.get(),
      lastOutcome: this.lastOutcomeStore.get(),
      sessionStarted: this.sessionStartedStore.get(),
      currentState: this.currentStateStore.get(),
      currentStageData: this.currentStageDataStore.get(),
      currentStageVisualData: this.currentStageVisualDataStore.get(),
      lexerVisualData: this.lexerVisualDataStore.get(),
      astVisualData: this.astVisualDataStore.get(),
      semanticVisualData: this.semanticVisualDataStore.get(),
      codegenVisualData: this.codegenVisualDataStore.get(),
      globalData: this.globalDataStore.get(),
      realtimeAnalysis: this.realtimeAnalysisStore.get(),
      lastControlResult: this.lastControlResultStore.get(),
      selectedVisualStage: this.selectedVisualStageStore.get(),
      debugStarted: this.debugStartedStore.get(),
      debugState: this.debugStateStore.get(),
      debugBreakpointLines: this.debugBreakpointLinesStore.get(),
      executionInputDraft: this.executionInputDraftText,
    };
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  summary(): string {
    return `MiniCWorkbenchViewModel: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }

  private setPlaybackMode(mode: MiniCPlaybackMode, title: string, description: string): UiControlResultDto {
    const state = this.currentStateStore.get();
    const stage = state?.currentStage ?? "source";
    const step = state?.stageStepIndex ?? 0;
    const result = controlResult("OK", stage, title, description);
    this.applyStage(stage, step, mode, result);
    return result;
  }

  private clearSessionState(): void {
    this.sessionStartedStore.set(false);
    this.currentStateStore.set(null);
    this.currentStageDataStore.set(null);
    this.currentStageVisualDataStore.set(null);
    this.lexerVisualDataStore.set(null);
    this.astVisualDataStore.set(null);
    this.semanticVisualDataStore.set(null);
    this.codegenVisualDataStore.set(null);
    this.globalDataStore.set(null);
    this.realtimeAnalysisStore.set(null);
    this.lastControlResultStore.set(null);
    this.selectedVisualStageStore.set("");
    this.lastOutcomeStore.set("");
    this.executionInputDraftText = "";
    this.clearDebugState();
  }

  private clearDebugState(): void {
    this.debugStartedStore.set(false);
    this.debugStateStore.set(null);
    this.debugMetadataViewStore.set(null);
    this.debugDataStructureViewStore.set(null);
    this.debugAstViewStore.set(null);
    this.debugIrViewStore.set(null);
    this.debugAsmViewStore.set(null);
  }

  private applyStage(
    stage: MiniCStageId,
    completedSteps: number,
    playbackMode: MiniCPlaybackMode,
    control?: UiControlResultDto,
  ): void {
    const definition = stageDefinition(stage);
    const safeCompleted = Math.max(0, Math.min(definition.totalSteps, Math.trunc(completedSteps)));
    const stageData = createStageData(stage, safeCompleted, this.sourceTextStore.get());
    const globalData = createGlobalData(
      this.sourceTextStore.get(),
      stage,
      stageData.completed,
      this.executionInputDraftText,
    );
    const state = createCurrentState(
      this.sourceNameStore.get(),
      stage,
      safeCompleted,
      playbackMode,
      stageData.diagnostics,
    );
    this.currentStateStore.set(state);
    this.currentStageDataStore.set(stageData);
    this.globalDataStore.set(globalData);
    this.refreshVisualData();
    if (control !== undefined) {
      this.applyControlResult(control);
    }
  }

  private refreshVisualData(): void {
    const state = this.currentStateStore.get();
    const stageData = this.currentStageDataStore.get();
    if (state === null || stageData === null) {
      return;
    }
    const source = this.sourceTextStore.get();
    const lexer = createStageVisual("lexer", source, stageData);
    const ast = createStageVisual("parser", source, stageData);
    const semantic = createStageVisual("semantic", source, stageData);
    const codegen = createStageVisual("codegen", source, stageData);
    const selected = this.selectedVisualStageStore.get();
    const currentStage = selected === "" ? state.currentStage : normalizeStageId(selected);
    this.lexerVisualDataStore.set(lexer);
    this.astVisualDataStore.set(ast);
    this.semanticVisualDataStore.set(semantic);
    this.codegenVisualDataStore.set(codegen);
    this.currentStageVisualDataStore.set(createStageVisual(currentStage, source, stageData));
  }

  private applyControlResult(result: UiControlResultDto): void {
    this.lastControlResultStore.set(result);
    this.lastOutcomeStore.set(result.outcome);
  }

  private autoConfirmExecutionInput(): void {
    if (!this.executionAwaitingInput()) {
      return;
    }
    const result = controlResult("OK", "execution", "输入已确认", "使用当前标准输入草稿继续执行。");
    this.applyControlResult(result);
  }

  private executionAwaitingInput(): boolean {
    const state = this.currentStateStore.get();
    const data = this.globalDataStore.get();
    return (
      state !== null &&
      data !== null &&
      state.currentStage === "execution" &&
      data.executionInputSummary.includes("stdin pending")
    );
  }

  private ensureDebugStarted(): void {
    if (!this.debugStartedStore.get()) {
      this.startDebug();
    }
  }

  private emit(): void {
    this.listeners.forEach((listener) => listener());
  }
}

export interface MiniCReadonlyProperty<T> {
  get(): T;
  subscribe(listener: (value: T, previous: T) => void): () => void;
}

export interface MiniCWorkbenchSnapshot {
  readonly sourceName: string;
  readonly sourceText: string;
  readonly lastOutcome: string;
  readonly sessionStarted: boolean;
  readonly currentState: UiCurrentStateDto | null;
  readonly currentStageData: UiStageDataDto | null;
  readonly currentStageVisualData: UiStageVisualDto | null;
  readonly lexerVisualData: UiStageVisualDto | null;
  readonly astVisualData: UiStageVisualDto | null;
  readonly semanticVisualData: UiStageVisualDto | null;
  readonly codegenVisualData: UiStageVisualDto | null;
  readonly globalData: UiGlobalDataDto | null;
  readonly realtimeAnalysis: UiRealtimeAnalysisDto | null;
  readonly lastControlResult: UiControlResultDto | null;
  readonly selectedVisualStage: string;
  readonly debugStarted: boolean;
  readonly debugState: UiDebugStateDto | null;
  readonly debugBreakpointLines: readonly number[];
  readonly executionInputDraft: string;
}

export class UiViewportState {
  static readonly DEFAULT = new UiViewportState(0, 0);

  constructor(
    readonly hvalue: number,
    readonly vvalue: number,
  ) {}
}

class MiniCWritableProperty<T> implements MiniCReadonlyProperty<T> {
  private readonly listeners = new Set<(value: T, previous: T) => void>();

  constructor(
    private value: T,
    private readonly onChange: () => void,
  ) {}

  get(): T {
    return this.value;
  }

  set(value: T): void {
    if (Object.is(value, this.value)) {
      return;
    }
    const previous = this.value;
    this.value = value;
    this.listeners.forEach((listener) => listener(value, previous));
    this.onChange();
  }

  subscribe(listener: (value: T, previous: T) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }
}

interface StageDefinition {
  readonly id: MiniCStageId;
  readonly title: string;
  readonly totalSteps: number;
  readonly item: (step: number) => string;
}

const LOCAL_STAGES: readonly StageDefinition[] = [
  { id: "source", title: "源码", totalSteps: 1, item: () => "源码已加载" },
  { id: "preprocess", title: "预编译", totalSteps: 2, item: (step) => `预处理步骤 ${step}` },
  { id: "lexer", title: "词法分析", totalSteps: 4, item: (step) => `扫描 token ${step}` },
  { id: "parser", title: "语法分析", totalSteps: 3, item: (step) => `构建 AST ${step}` },
  { id: "semantic", title: "语义分析", totalSteps: 3, item: (step) => `检查语义 ${step}` },
  { id: "ir", title: "IR 降级", totalSteps: 3, item: (step) => `生成 IR ${step}` },
  { id: "codegen", title: "代码生成", totalSteps: 3, item: (step) => `生成汇编 ${step}` },
  { id: "toolchain", title: "工具链", totalSteps: 2, item: (step) => `工具链产物 ${step}` },
  { id: "execution", title: "执行", totalSteps: 2, item: (step) => (step === 0 ? "等待输入" : `执行步骤 ${step}`) },
];

function stageDefinition(stage: MiniCStageId): StageDefinition {
  return LOCAL_STAGES.find((definition) => definition.id === stage) ?? LOCAL_STAGES[0];
}

function stageAfter(stage: MiniCStageId): StageDefinition | null {
  const index = LOCAL_STAGES.findIndex((definition) => definition.id === stage);
  return index < 0 || index + 1 >= LOCAL_STAGES.length ? null : LOCAL_STAGES[index + 1];
}

function normalizeStageId(stage: string): MiniCStageId {
  return LOCAL_STAGES.find((definition) => definition.id === stage)?.id ?? "source";
}

function createStageData(stage: MiniCStageId, completedSteps: number, source: string): UiStageDataDto {
  const definition = stageDefinition(stage);
  const completed = completedSteps >= definition.totalSteps;
  return {
    stage,
    completedSteps,
    totalSteps: definition.totalSteps,
    completed,
    inputSummary: stage === "source" ? [`${source.length} chars`] : [],
    currentItem: definition.item(completedSteps),
    accumulatedOutput: accumulatedOutput(stage, completedSteps, source),
    diagnostics: [],
  };
}

function createCurrentState(
  sourceName: string,
  stage: MiniCStageId,
  stageStepIndex: number,
  playbackMode: MiniCPlaybackMode,
  diagnostics: readonly UiDiagnosticDto[],
): UiCurrentStateDto {
  const definition = stageDefinition(stage);
  const atEnd = stage === "execution" && stageStepIndex >= definition.totalSteps;
  return {
    sourceName,
    currentStage: stage,
    globalStepIndex: globalStepIndex(stage, stageStepIndex),
    stageStepIndex,
    playbackMode,
    frameIntervalMillis: playbackMode === "FAST_PLAYING" ? 250 : 500,
    sourceRange: null,
    title: definition.title,
    description: definition.item(stageStepIndex),
    diagnostics,
    canNext: !atEnd,
    canPrevious: stageStepIndex > 0 || stage !== "source",
    canPlay: playbackMode === "PAUSED" && !atEnd,
    canPlayFast: playbackMode === "PAUSED" && !atEnd,
    canPause: playbackMode !== "PAUSED",
    canReversePlay: false,
  };
}

function createGlobalData(
  source: string,
  currentStage: MiniCStageId,
  activeCompleted: boolean,
  executionInputDraft: string,
): UiGlobalDataDto {
  const reached = (stage: MiniCStageId): boolean => {
    const currentIndex = LOCAL_STAGES.findIndex((definition) => definition.id === currentStage);
    const targetIndex = LOCAL_STAGES.findIndex((definition) => definition.id === stage);
    return currentIndex > targetIndex || (currentIndex === targetIndex && activeCompleted);
  };
  const tokenSummary = reached("lexer") ? tokenTexts(source) : [];
  const executionInputSummary =
    currentStage === "execution" && executionInputDraft.trim() === "" ? ["stdin pending"] : ["stdin confirmed"];
  return {
    source,
    stageSummaries: LOCAL_STAGES.map((stage) => stage.title),
    diagnostics: [],
    preprocessSummary: reached("preprocess") ? ["预处理产物已生成"] : [],
    tokenSummary,
    astSummary: reached("parser") ? ["Program", "FunctionDecl main"] : [],
    semanticSummary: reached("semantic") ? ["global scope", "symbol main"] : [],
    irSummary: reached("ir") ? ["function main", "return"] : [],
    assemblySummary: reached("codegen") ? ["main:", "  ret"] : [],
    artifactSummary: reached("toolchain") ? ["minic.exe"] : [],
    executionInputSummary,
    executionOutputSummary: reached("execution") ? ["program exited with code 0"] : [],
  };
}

function createStageVisual(stage: MiniCStageId, source: string, activeData: UiStageDataDto): UiStageVisualDto {
  if (stage === "lexer") {
    return {
      ...emptyStageVisual(stage, "lexer", source),
      lexerTokens: tokensFromSource(source),
    };
  }
  if (stage === "parser") {
    return {
      ...emptyStageVisual(stage, "ast", source),
      astRoot: {
        id: "ast-root",
        label: "Program",
        kind: "Program",
        range: null,
        active: activeData.stage === "parser",
        children: tokenTexts(source).slice(0, 8).map((text, index) => ({
          id: `ast-${index}`,
          label: text,
          kind: "Token",
          range: null,
          active: false,
          children: [],
        })),
      },
    };
  }
  if (stage === "semantic") {
    return {
      ...emptyStageVisual(stage, "semantic-scope", source),
      semanticRoot: semanticRoot(source, activeData.stage === "semantic"),
      semanticEdgesPointChildToParent: true,
    };
  }
  if (stage === "codegen") {
    return {
      ...emptyStageVisual(stage, "assembly", source),
      irLines: irLines(),
      assemblyLines: assemblyLines(),
    };
  }
  return {
    ...emptyStageVisual(stage, "generic", source),
    genericItems: accumulatedOutput(stage, activeData.completedSteps, source),
  };
}

function emptyStageVisual(stage: MiniCStageId, visualType: string, sourceText: string): UiStageVisualDto {
  return {
    stage,
    visualType,
    sourceText,
    genericItems: [],
    lexerTokens: [],
    astRoot: null,
    semanticRoot: null,
    semanticEdgesPointChildToParent: false,
    irLines: [],
    assemblyLines: [],
  };
}

function createRealtimeAnalysis(sourceName: string, sourceText: string, version: number): UiRealtimeAnalysisDto {
  return {
    sourceName,
    sourceText,
    diagnostics: [],
    tokens: tokensFromSource(sourceText),
    version,
  };
}

function accumulatedOutput(stage: MiniCStageId, completedSteps: number, source: string): readonly string[] {
  if (completedSteps <= 0) {
    return [];
  }
  if (stage === "lexer") {
    return tokenTexts(source).slice(0, completedSteps);
  }
  return Array.from({ length: completedSteps }, (_value, index) => `${stage} output ${index + 1}`);
}

function tokenTexts(source: string): readonly string[] {
  const matches = source.match(/[A-Za-z_][A-Za-z0-9_]*|\d+|==|!=|<=|>=|[{}()[\];,+\-*/=<>]/g);
  return matches ?? [];
}

function tokensFromSource(source: string): readonly UiLexerTokenVisualDto[] {
  const tokens: UiLexerTokenVisualDto[] = [];
  const pattern = /[A-Za-z_][A-Za-z0-9_]*|\d+|==|!=|<=|>=|[{}()[\];,+\-*/=<>]/g;
  let match: RegExpExecArray | null = pattern.exec(source);
  while (match !== null) {
    tokens.push({
      kind: tokenKind(match[0]),
      text: match[0],
      range: null,
      active: tokens.length === 0,
    });
    match = pattern.exec(source);
  }
  return tokens;
}

function tokenKind(text: string): string {
  if (/^\d+$/.test(text)) {
    return "NUMBER";
  }
  if (/^[A-Za-z_]/.test(text)) {
    return "IDENTIFIER";
  }
  return "PUNCTUATION";
}

function semanticRoot(source: string, active: boolean): UiSemanticScopeVisualDto {
  const symbols = tokenTexts(source)
    .filter((token) => /^[A-Za-z_]/.test(token))
    .slice(0, 8);
  return {
    id: "scope-global",
    label: "global scope",
    symbols,
    range: null,
    active,
    children: [],
  };
}

function irLines(): readonly UiIrLineVisualDto[] {
  return [
    { lineNumber: 1, text: "function main", range: null, active: true },
    { lineNumber: 2, text: "  return 0", range: null, active: false },
  ];
}

function assemblyLines(): readonly UiAssemblyLineVisualDto[] {
  return [
    { lineNumber: 1, text: "main:", kind: "LABEL", section: ".text", label: "main", range: null, active: false },
    { lineNumber: 2, text: "  ret", kind: "INSTRUCTION", section: ".text", label: "", range: null, active: true },
  ];
}

function globalStepIndex(stage: MiniCStageId, stageStepIndex: number): number {
  const previous = LOCAL_STAGES.slice(0, LOCAL_STAGES.findIndex((definition) => definition.id === stage)).reduce(
    (sum, definition) => sum + definition.totalSteps,
    0,
  );
  return previous + stageStepIndex;
}

function controlResult(
  outcome: string,
  stage: MiniCStageId,
  title: string,
  description: string,
  diagnostics: readonly UiDiagnosticDto[] = [],
): UiControlResultDto {
  return { outcome, stage, title, description, diagnostics };
}

function normalizeBreakpoints(lines: readonly number[]): readonly number[] {
  return [...new Set(lines.map((line) => Math.trunc(line)).filter((line) => line >= 1))].sort((left, right) => left - right);
}

function clampUnit(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.max(0, Math.min(1, value));
}

export default MiniCWorkbenchViewModel;
