import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCRealtimeAnalyzer } from "../editor/MiniCRealtimeAnalyzer";
import type {
  MiniCPlaybackMode,
  MiniCStageId,
  UiControlResultDto,
  UiCurrentStateDto,
  UiDiagnosticDto,
  UiGlobalDataDto,
  UiRealtimeAnalysisDto,
  UiDebugAsmViewDto,
  UiDebugAstViewDto,
  UiDebugDataStructureViewDto,
  UiDebugIrViewDto,
  UiDebugMetadataViewDto,
  UiDebugStateDto,
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

export interface MiniCObservationApiAdapter {
  loadSource(name: string, source: string): void;
  startSession(): void;
  next(): UiControlResultDto;
  nextStage(): UiControlResultDto;
  play(): UiControlResultDto;
  playFast(): UiControlResultDto;
  tick(): UiControlResultDto;
  pause(): UiControlResultDto;
  confirmExecutionInput(standardInput: string): UiControlResultDto;
  currentState(): UiCurrentStateDto | null;
  currentStageData(): UiStageDataDto | null;
  currentStageVisualData(): UiStageVisualDto | null;
  stageVisualData(stage: MiniCStageId): UiStageVisualDto | null;
  globalData(): UiGlobalDataDto | null;
}

export interface MiniCDebugApiAdapter {
  loadSource(name: string, source: string): void;
  startDebug(): void;
  setBreakpoint(line: number): void;
  clearBreakpoint(line: number): void;
  runToBreakpoint(): void;
  runToEnd(): void;
  fastForward(): void;
  stepOver(): void;
  stepInto(): void;
  stepOut(): void;
  pause(): void;
  restart(): void;
  close(): void;
  stepBack(): void;
  stepBackOver(): void;
  backToBreakpoint(): void;
  state(): UiDebugStateDto | null;
  metadataView(): UiDebugMetadataViewDto | null;
  dataStructureView(): UiDebugDataStructureViewDto | null;
  astView(): UiDebugAstViewDto | null;
  irView(): UiDebugIrViewDto | null;
  asmView(): UiDebugAsmViewDto | null;
}

export interface MiniCRealtimeAnalyzerAdapter {
  submit(name: string, source: string): UiRealtimeAnalysisDto | void;
  close?(): void;
}

export interface MiniCWorkbenchViewModelAdapters {
  readonly observationApi?: MiniCObservationApiAdapter | null;
  readonly debugApi?: MiniCDebugApiAdapter | null;
  readonly realtimeAnalyzer?: MiniCRealtimeAnalyzerAdapter | null;
}

export class MiniCWorkbenchViewModel {
  static readonly mirror = miniCWorkbenchViewModelMirror;

  readonly mirror = miniCWorkbenchViewModelMirror;

  private readonly api: MiniCObservationApiAdapter | null;

  private readonly debugApi: MiniCDebugApiAdapter | null;

  private readonly realtimeAnalyzer: MiniCRealtimeAnalyzerAdapter;

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

  constructor(initialSourceName = "", initialSourceText = "", adapters: MiniCWorkbenchViewModelAdapters = {}) {
    this.api = adapters.observationApi ?? null;
    this.debugApi = adapters.debugApi ?? null;
    this.realtimeAnalyzer = adapters.realtimeAnalyzer ?? new MiniCRealtimeAnalyzer((result) => this.applyRealtimeAnalysis(result));
    if (initialSourceName !== "" || initialSourceText !== "") {
      this.loadSource(initialSourceName, initialSourceText);
      this.submitRealtimeSource(initialSourceName, initialSourceText);
    }
  }

  loadSource(name: string, source: string): void {
    this.api?.loadSource(name, source);
    this.debugApi?.loadSource(name, source);
    this.sourceNameStore.set(name);
    this.sourceTextStore.set(source);
    this.clearSessionState();
  }

  renameSource(name: string): void {
    this.api?.loadSource(name, this.sourceTextStore.get());
    this.debugApi?.loadSource(name, this.sourceTextStore.get());
    this.sourceNameStore.set(name);
    this.clearSessionState();
  }

  submitRealtimeSource(name: string, source: string): void {
    this.sourceNameStore.set(name);
    this.sourceTextStore.set(source);
    const result = this.realtimeAnalyzer.submit(name, source);
    if (result !== undefined) {
      this.applyRealtimeAnalysis(result);
    }
  }

  startSession(): void {
    if (this.api === null) {
      this.applyControlResult(noApiResult("source", "无法启动", "UIWeb 尚未连接 MiniCObservationApi 适配器。"));
      return;
    }
    this.api.startSession();
    this.sessionStartedStore.set(true);
    this.selectedVisualStageStore.set("");
    this.refreshAll();
  }

  next(): UiControlResultDto {
    if (this.api === null) {
      const result = noApiResult(this.currentStateStore.get()?.currentStage ?? "source", "无法前进", "UIWeb 尚未连接 MiniCObservationApi 适配器。");
      this.applyControlResult(result);
      return result;
    }
    this.autoConfirmExecutionInput();
    const result = this.api.next();
    this.applyControlResult(result);
    this.refreshAll();
    return result;
  }

  nextStage(): UiControlResultDto {
    if (this.api === null) {
      const result = noApiResult(this.currentStateStore.get()?.currentStage ?? "source", "无法跳转", "UIWeb 尚未连接 MiniCObservationApi 适配器。");
      this.applyControlResult(result);
      return result;
    }
    this.autoConfirmExecutionInput();
    this.selectedVisualStageStore.set("");
    const state = this.currentStateStore.get();
    const result = state !== null && state.currentStage === "execution" && state.canNext ? this.api.next() : this.api.nextStage();
    this.applyControlResult(result);
    this.refreshAll();
    return result;
  }

  runToExecution(): UiControlResultDto {
    if (this.api === null) {
      const result = noApiResult(this.currentStateStore.get()?.currentStage ?? "source", "无法推进", "UIWeb 尚未连接 MiniCObservationApi 适配器。");
      this.applyControlResult(result);
      return result;
    }
    this.selectedVisualStageStore.set("");
    let result: UiControlResultDto | null = null;
    let guard = 0;
    while (
      this.currentStateStore.get() !== null &&
      this.currentStateStore.get()?.currentStage !== "execution" &&
      this.currentStateStore.get()?.canNext === true &&
      guard < 1000
    ) {
      guard += 1;
      result = this.api.nextStage();
      this.applyControlResult(result);
      this.refreshAll();
      if (result.outcome === "FAILED" || result.outcome === "CANNOT_ADVANCE") {
        return result;
      }
    }
    if (result === null) {
      result = noApiResult("execution", "已在执行阶段", "当前已经位于执行阶段入口。");
      this.applyControlResult(result);
    }
    this.refreshAll();
    return result;
  }

  play(): UiControlResultDto {
    if (this.api === null) {
      const result = noApiResult(this.currentStateStore.get()?.currentStage ?? "source", "无法播放", "UIWeb 尚未连接 MiniCObservationApi 适配器。");
      this.applyControlResult(result);
      return result;
    }
    this.selectedVisualStageStore.set("");
    this.autoConfirmExecutionInput();
    const result = this.api.play();
    this.applyControlResult(result);
    this.refreshAll();
    return result;
  }

  playFast(): UiControlResultDto {
    if (this.api === null) {
      const result = noApiResult(this.currentStateStore.get()?.currentStage ?? "source", "无法快放", "UIWeb 尚未连接 MiniCObservationApi 适配器。");
      this.applyControlResult(result);
      return result;
    }
    this.selectedVisualStageStore.set("");
    this.autoConfirmExecutionInput();
    const result = this.api.playFast();
    this.applyControlResult(result);
    this.refreshAll();
    return result;
  }

  tick(): UiControlResultDto {
    if (this.api === null) {
      const result = noApiResult(this.currentStateStore.get()?.currentStage ?? "source", "无法 tick", "UIWeb 尚未连接 MiniCObservationApi 适配器。");
      this.applyControlResult(result);
      return result;
    }
    this.autoConfirmExecutionInput();
    const result = this.api.tick();
    this.applyControlResult(result);
    this.refreshAll();
    return result;
  }

  pause(): UiControlResultDto {
    if (this.api === null) {
      const result = noApiResult(this.currentStateStore.get()?.currentStage ?? "source", "无法暂停", "UIWeb 尚未连接 MiniCObservationApi 适配器。");
      this.applyControlResult(result);
      return result;
    }
    const result = this.api.pause();
    this.applyControlResult(result);
    this.refreshAll();
    return result;
  }

  confirmExecutionInput(standardInput: string): UiControlResultDto {
    if (this.api === null) {
      const result = noApiResult("execution", "无法确认输入", "UIWeb 尚未连接 MiniCObservationApi 适配器。");
      this.applyControlResult(result);
      return result;
    }
    this.selectedVisualStageStore.set("");
    this.executionInputDraftText = standardInput ?? "";
    const result = this.api.confirmExecutionInput(standardInput);
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
    this.refreshVisualDataFromApi();
  }

  startDebug(): void {
    if (this.debugApi === null) {
      this.applyControlResult(noApiResult("source", "无法调试", "UIWeb 尚未连接 MiniCDebugApi 适配器。"));
      return;
    }
    const name = this.sourceNameStore.get().trim() === "" ? "untitled.mc" : this.sourceNameStore.get();
    this.debugApi.loadSource(name, this.sourceTextStore.get());
    this.debugApi.startDebug();
    this.debugStartedStore.set(true);
    this.refreshDebug();
  }

  setDebugBreakpoints(lines: readonly number[]): void {
    this.debugBreakpointLinesStore.set(normalizeBreakpoints(lines));
    this.syncDebugBreakpoints();
  }

  syncDebugBreakpoints(): void {
    if (!this.debugStartedStore.get() || this.debugApi === null) {
      return;
    }
    this.debugBreakpointLinesStore.get().forEach((line) => this.debugApi?.setBreakpoint(line));
    this.refreshDebug();
  }

  setDebugBreakpoint(line: number): void {
    this.ensureDebugStarted();
    this.debugBreakpointLinesStore.set(normalizeBreakpoints([...this.debugBreakpointLinesStore.get(), line]));
    this.debugApi?.setBreakpoint(line);
    this.refreshDebug();
  }

  clearDebugBreakpoint(line: number): void {
    this.ensureDebugStarted();
    this.debugBreakpointLinesStore.set(this.debugBreakpointLinesStore.get().filter((item) => item !== line));
    this.debugApi?.clearBreakpoint(line);
    this.refreshDebug();
  }

  debugRunToBreakpoint(): void {
    this.ensureDebugStarted();
    this.debugApi?.runToBreakpoint();
    this.refreshDebug();
  }

  debugRunToEnd(): void {
    this.ensureDebugStarted();
    this.debugApi?.runToEnd();
    this.refreshDebug();
  }

  debugFastForward(): void {
    this.ensureDebugStarted();
    this.debugApi?.fastForward();
    this.refreshDebug();
  }

  debugStepOver(): void {
    this.ensureDebugStarted();
    this.debugApi?.stepOver();
    this.refreshDebug();
  }

  debugStepInto(): void {
    this.ensureDebugStarted();
    this.debugApi?.stepInto();
    this.refreshDebug();
  }

  debugStepOut(): void {
    this.ensureDebugStarted();
    this.debugApi?.stepOut();
    this.refreshDebug();
  }

  debugPause(): void {
    this.ensureDebugStarted();
    this.debugApi?.pause();
    this.refreshDebug();
  }

  debugRestart(): void {
    this.ensureDebugStarted();
    this.debugApi?.restart();
    this.refreshDebug();
  }

  debugClose(): void {
    this.ensureDebugStarted();
    this.debugApi?.close();
    this.refreshDebug();
  }

  debugStepBack(): void {
    this.ensureDebugStarted();
    this.debugApi?.stepBack();
    this.refreshDebug();
  }

  debugStepBackOver(): void {
    this.ensureDebugStarted();
    this.debugApi?.stepBackOver();
    this.refreshDebug();
  }

  debugBackToBreakpoint(): void {
    this.ensureDebugStarted();
    this.debugApi?.backToBreakpoint();
    this.refreshDebug();
  }

  debugBackToCallSite(): void {
    this.debugStepBackOver();
  }

  refreshDebug(): void {
    if (!this.debugStartedStore.get() || this.debugApi === null) {
      return;
    }
    this.debugStateStore.set(this.debugApi.state());
    this.debugMetadataViewStore.set(this.debugApi.metadataView());
    this.debugDataStructureViewStore.set(this.debugApi.dataStructureView());
    this.debugAstViewStore.set(this.debugApi.astView());
    this.debugIrViewStore.set(this.debugApi.irView());
    this.debugAsmViewStore.set(this.debugApi.asmView());
  }

  refreshAll(): void {
    if (!this.sessionStartedStore.get() || this.api === null) {
      return;
    }
    this.currentStateStore.set(this.api.currentState());
    this.currentStageDataStore.set(this.api.currentStageData());
    this.globalDataStore.set(this.api.globalData());
    this.refreshVisualDataFromApi();
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
      debugMetadataView: this.debugMetadataViewStore.get(),
      debugDataStructureView: this.debugDataStructureViewStore.get(),
      debugAstView: this.debugAstViewStore.get(),
      debugIrView: this.debugIrViewStore.get(),
      debugAsmView: this.debugAsmViewStore.get(),
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

  private refreshVisualDataFromApi(): void {
    if (this.api === null) {
      return;
    }
    const state = this.currentStateStore.get();
    const selected = this.selectedVisualStageStore.get();
    const currentStage = selected === "" ? state?.currentStage ?? "source" : toStageId(selected);
    this.lexerVisualDataStore.set(this.api.stageVisualData("lexer"));
    this.astVisualDataStore.set(this.api.stageVisualData("parser"));
    this.semanticVisualDataStore.set(this.api.stageVisualData("semantic"));
    this.codegenVisualDataStore.set(this.api.stageVisualData("codegen"));
    this.currentStageVisualDataStore.set(selected === "" ? this.api.currentStageVisualData() : this.api.stageVisualData(currentStage));
  }

  private applyControlResult(result: UiControlResultDto): void {
    this.lastControlResultStore.set(result);
    this.lastOutcomeStore.set(result.outcome);
  }

  private autoConfirmExecutionInput(): void {
    if (!this.sessionStartedStore.get() || this.api === null || !this.executionAwaitingInput()) {
      return;
    }
    const result = this.api.confirmExecutionInput(this.executionInputDraftText);
    this.applyControlResult(result);
    this.refreshAll();
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

  private applyRealtimeAnalysis(result: UiRealtimeAnalysisDto): void {
    if (this.sourceNameStore.get() === result.sourceName && this.sourceTextStore.get() === result.sourceText) {
      this.realtimeAnalysisStore.set(result);
    }
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
  readonly debugMetadataView: UiDebugMetadataViewDto | null;
  readonly debugDataStructureView: UiDebugDataStructureViewDto | null;
  readonly debugAstView: UiDebugAstViewDto | null;
  readonly debugIrView: UiDebugIrViewDto | null;
  readonly debugAsmView: UiDebugAsmViewDto | null;
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

const STAGE_IDS: readonly MiniCStageId[] = ["source", "preprocess", "lexer", "parser", "semantic", "ir", "codegen", "toolchain", "execution"];

function toStageId(stage: string): MiniCStageId {
  return STAGE_IDS.includes(stage as MiniCStageId) ? (stage as MiniCStageId) : "source";
}

function noApiResult(stage: MiniCStageId, title: string, description: string): UiControlResultDto {
  return {
    outcome: "CANNOT_ADVANCE",
    stage,
    title,
    description,
    diagnostics: [],
  };
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
