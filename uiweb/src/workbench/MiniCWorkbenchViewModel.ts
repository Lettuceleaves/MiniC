import type { JavaMirrorFile } from "../translation/javaMirror";
import type {
  MiniCPlaybackMode,
  MiniCStageId,
  UiControlResultDto,
  UiCurrentStateDto,
  UiDiagnosticDto,
  UiGlobalDataDto,
  UiInspectorModelDto,
  UiLexerTokenVisualDto,
  UiRealtimeAnalysisDto,
  UiDebugAsmViewDto,
  UiDebugAstViewDto,
  UiDebugDataStructureViewDto,
  UiDebugIrViewDto,
  UiDebugMetadataViewDto,
  UiDebugStateDto,
  UiStageDataDto,
  UiStageViewDto,
  UiStageVisualDto,
} from "../translation/uiapi";
import { MiniCUiApiError } from "../api/MiniCUiApiClient";

export const miniCWorkbenchViewModelMirror = {
  "javaPath": "src/main/java/minic/uilocal/workbench/MiniCWorkbenchViewModel.java",
  "webPath": "uiweb/src/workbench/MiniCWorkbenchViewModel.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCWorkbenchViewModel",
  "kind": "class",
  "imports": [
    "java.util.List",
    "java.util.Map",
    "java.util.Objects",
    "java.util.concurrent.ConcurrentHashMap",
    "javafx.beans.property.ReadOnlyBooleanProperty",
    "javafx.beans.property.ReadOnlyBooleanWrapper",
    "javafx.beans.property.ReadOnlyObjectProperty",
    "javafx.beans.property.ReadOnlyObjectWrapper",
    "javafx.beans.property.ReadOnlyStringProperty",
    "javafx.beans.property.ReadOnlyStringWrapper",
    "minic.uiapi.MiniCDebugApi",
    "minic.uiapi.MiniCObservationApi",
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
    "minic.uiapi.UiStageVisualDto"
  ],
  "fields": [
    {
      "name": "api",
      "signature": "private final MiniCObservationApi api"
    },
    {
      "name": "astVisualData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageVisualDto>astVisualData="
    },
    {
      "name": "codegenVisualData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageVisualDto>codegenVisualData="
    },
    {
      "name": "currentStageData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageDataDto>currentStageData="
    },
    {
      "name": "currentStageVisualData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageVisualDto>currentStageVisualData="
    },
    {
      "name": "currentState",
      "signature": "private final ReadOnlyObjectWrapper<UiCurrentStateDto>currentState="
    },
    {
      "name": "debugApi",
      "signature": "private final MiniCDebugApi debugApi="
    },
    {
      "name": "debugAsmView",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugAsmViewDto>debugAsmView="
    },
    {
      "name": "debugAstView",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugAstViewDto>debugAstView="
    },
    {
      "name": "debugBreakpointLines",
      "signature": "private final ReadOnlyObjectWrapper<List<Integer>>debugBreakpointLines="
    },
    {
      "name": "debugDataStructureView",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugDataStructureViewDto>debugDataStructureView="
    },
    {
      "name": "debugIrView",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugIrViewDto>debugIrView="
    },
    {
      "name": "debugMetadataView",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugMetadataViewDto>debugMetadataView="
    },
    {
      "name": "debugStarted",
      "signature": "private final ReadOnlyBooleanWrapper debugStarted="
    },
    {
      "name": "debugState",
      "signature": "private final ReadOnlyObjectWrapper<UiDebugStateDto>debugState="
    },
    {
      "name": "DEFAULT",
      "signature": "public static final UiViewportState DEFAULT="
    },
    {
      "name": "executionInputDraft",
      "signature": "private String executionInputDraft="
    },
    {
      "name": "globalData",
      "signature": "private final ReadOnlyObjectWrapper<UiGlobalDataDto>globalData="
    },
    {
      "name": "lastControlResult",
      "signature": "private final ReadOnlyObjectWrapper<UiControlResultDto>lastControlResult="
    },
    {
      "name": "lastOutcome",
      "signature": "private final ReadOnlyStringWrapper lastOutcome="
    },
    {
      "name": "lexerVisualData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageVisualDto>lexerVisualData="
    },
    {
      "name": "realtimeAnalysis",
      "signature": "private final ReadOnlyObjectWrapper<UiRealtimeAnalysisDto>realtimeAnalysis="
    },
    {
      "name": "realtimeAnalyzer",
      "signature": "private final MiniCRealtimeAnalyzer realtimeAnalyzer"
    },
    {
      "name": "selectedVisualStage",
      "signature": "private final ReadOnlyStringWrapper selectedVisualStage="
    },
    {
      "name": "semanticVisualData",
      "signature": "private final ReadOnlyObjectWrapper<UiStageVisualDto>semanticVisualData="
    },
    {
      "name": "sessionStarted",
      "signature": "private final ReadOnlyBooleanWrapper sessionStarted="
    },
    {
      "name": "sourceName",
      "signature": "private final ReadOnlyStringWrapper sourceName="
    },
    {
      "name": "sourceText",
      "signature": "private final ReadOnlyStringWrapper sourceText="
    },
    {
      "name": "viewportStates",
      "signature": "private final Map<String,UiViewportState>viewportStates="
    }
  ],
  "methods": [
    {
      "name": "applyControlResult",
      "signature": "applyControlResult(UiControlResultDto result)"
    },
    {
      "name": "applyPendingDebugBreakpoints",
      "signature": "applyPendingDebugBreakpoints()"
    },
    {
      "name": "applyRealtimeAnalysis",
      "signature": "applyRealtimeAnalysis(UiRealtimeAnalysisDto result)"
    },
    {
      "name": "astVisualDataProperty",
      "signature": "astVisualDataProperty()"
    },
    {
      "name": "autoConfirmExecutionInput",
      "signature": "autoConfirmExecutionInput()"
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
      "name": "canPlayControl",
      "signature": "canPlayControl()"
    },
    {
      "name": "canPlayFastControl",
      "signature": "canPlayFastControl()"
    },
    {
      "name": "canRunToExecutionControl",
      "signature": "canRunToExecutionControl()"
    },
    {
      "name": "clampUnit",
      "signature": "clampUnit(double value)"
    },
    {
      "name": "clearDebugBreakpoint",
      "signature": "clearDebugBreakpoint(int line)"
    },
    {
      "name": "clearDebugState",
      "signature": "clearDebugState()"
    },
    {
      "name": "clearSessionState",
      "signature": "clearSessionState()"
    },
    {
      "name": "codegenVisualDataProperty",
      "signature": "codegenVisualDataProperty()"
    },
    {
      "name": "confirmExecutionInput",
      "signature": "confirmExecutionInput(String standardInput)"
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
      "name": "currentStateProperty",
      "signature": "currentStateProperty()"
    },
    {
      "name": "debugAsmViewProperty",
      "signature": "debugAsmViewProperty()"
    },
    {
      "name": "debugAstViewProperty",
      "signature": "debugAstViewProperty()"
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
      "name": "debugBreakpointLinesProperty",
      "signature": "debugBreakpointLinesProperty()"
    },
    {
      "name": "debugClose",
      "signature": "debugClose()"
    },
    {
      "name": "debugDataStructureViewProperty",
      "signature": "debugDataStructureViewProperty()"
    },
    {
      "name": "debugFastForward",
      "signature": "debugFastForward()"
    },
    {
      "name": "debugIrViewProperty",
      "signature": "debugIrViewProperty()"
    },
    {
      "name": "debugMetadataViewProperty",
      "signature": "debugMetadataViewProperty()"
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
      "name": "debugRunToBreakpoint",
      "signature": "debugRunToBreakpoint()"
    },
    {
      "name": "debugRunToEnd",
      "signature": "debugRunToEnd()"
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
      "name": "debugStepBack",
      "signature": "debugStepBack()"
    },
    {
      "name": "debugStepBackOver",
      "signature": "debugStepBackOver()"
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
      "name": "debugStepOver",
      "signature": "debugStepOver()"
    },
    {
      "name": "ensureDebugStarted",
      "signature": "ensureDebugStarted()"
    },
    {
      "name": "executionAwaitingInput",
      "signature": "executionAwaitingInput()"
    },
    {
      "name": "executionInputDraft",
      "signature": "executionInputDraft()"
    },
    {
      "name": "globalDataProperty",
      "signature": "globalDataProperty()"
    },
    {
      "name": "lastControlResultProperty",
      "signature": "lastControlResultProperty()"
    },
    {
      "name": "lastOutcomeProperty",
      "signature": "lastOutcomeProperty()"
    },
    {
      "name": "lexerVisualDataProperty",
      "signature": "lexerVisualDataProperty()"
    },
    {
      "name": "loadSource",
      "signature": "loadSource(String name,String source)"
    },
    {
      "name": "mergeBreakpoint",
      "signature": "mergeBreakpoint(int line)"
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
      "name": "normalizeBreakpoints",
      "signature": "normalizeBreakpoints(List<Integer>lines)"
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
      "name": "realtimeAnalysisProperty",
      "signature": "realtimeAnalysisProperty()"
    },
    {
      "name": "refreshAll",
      "signature": "refreshAll()"
    },
    {
      "name": "refreshDebug",
      "signature": "refreshDebug()"
    },
    {
      "name": "renameSource",
      "signature": "renameSource(String name)"
    },
    {
      "name": "runToExecution",
      "signature": "runToExecution()"
    },
    {
      "name": "saveViewportState",
      "signature": "saveViewportState(String key,double hvalue,double vvalue)"
    },
    {
      "name": "selectedVisualStageProperty",
      "signature": "selectedVisualStageProperty()"
    },
    {
      "name": "selectVisualStage",
      "signature": "selectVisualStage(String stage)"
    },
    {
      "name": "semanticVisualDataProperty",
      "signature": "semanticVisualDataProperty()"
    },
    {
      "name": "sessionStartedProperty",
      "signature": "sessionStartedProperty()"
    },
    {
      "name": "setDebugBreakpoint",
      "signature": "setDebugBreakpoint(int line)"
    },
    {
      "name": "setDebugBreakpoints",
      "signature": "setDebugBreakpoints(List<Integer>lines)"
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
      "name": "startDebug",
      "signature": "startDebug()"
    },
    {
      "name": "startSession",
      "signature": "startSession()"
    },
    {
      "name": "submitRealtimeSource",
      "signature": "submitRealtimeSource(String name,String source)"
    },
    {
      "name": "syncDebugBreakpoints",
      "signature": "syncDebugBreakpoints()"
    },
    {
      "name": "tick",
      "signature": "tick()"
    },
    {
      "name": "UiViewportState",
      "signature": "UiViewportState(double hvalue,double vvalue)"
    },
    {
      "name": "updateExecutionInputDraft",
      "signature": "updateExecutionInputDraft(String standardInput)"
    },
    {
      "name": "viewportState",
      "signature": "viewportState(String key)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCObservationApiAdapter {
  loadSource(name: string, source: string): Promise<void>;
  startSession(): Promise<void>;
  next(): Promise<UiControlResultDto>;
  nextStage(): Promise<UiControlResultDto>;
  runToExecution(): Promise<UiControlResultDto>;
  play(): Promise<UiControlResultDto>;
  playFast(): Promise<UiControlResultDto>;
  tick(): Promise<UiControlResultDto>;
  pause(): Promise<UiControlResultDto>;
  confirmExecutionInput(standardInput: string): Promise<UiControlResultDto>;
  previous(): Promise<UiControlResultDto>;
  reversePlay(): Promise<UiControlResultDto>;
  currentState(): Promise<UiCurrentStateDto>;
  currentStageData(): Promise<UiStageDataDto>;
  currentStageVisualData(): Promise<UiStageVisualDto>;
  lexerVisualData(): Promise<UiStageVisualDto>;
  astVisualData(): Promise<UiStageVisualDto>;
  semanticVisualData(): Promise<UiStageVisualDto>;
  codegenVisualData(): Promise<UiStageVisualDto>;
  globalData(): Promise<UiGlobalDataDto>;
  stageViews(): Promise<readonly UiStageViewDto[]>;
  inspectorModel(): Promise<UiInspectorModelDto>;
}

export interface MiniCDebugApiAdapter {
  loadSource(name: string, source: string): Promise<void>;
  startDebug(): Promise<UiDebugStateDto>;
  setBreakpoint(line: number): Promise<UiDebugStateDto>;
  clearBreakpoint(line: number): Promise<UiDebugStateDto>;
  runToBreakpoint(): Promise<UiDebugStateDto>;
  runToEnd(): Promise<UiDebugStateDto>;
  fastForward(): Promise<UiDebugStateDto>;
  stepOver(): Promise<UiDebugStateDto>;
  stepInto(): Promise<UiDebugStateDto>;
  stepOut(): Promise<UiDebugStateDto>;
  pause(): Promise<UiDebugStateDto>;
  restart(): Promise<UiDebugStateDto>;
  close(): Promise<UiDebugStateDto>;
  stepBack(): Promise<UiDebugStateDto>;
  stepBackOver(): Promise<UiDebugStateDto>;
  backToBreakpoint(): Promise<UiDebugStateDto>;
  backToCallSite(): Promise<UiDebugStateDto>;
  state(): Promise<UiDebugStateDto>;
  metadataView(): Promise<UiDebugMetadataViewDto>;
  dataStructureView(): Promise<UiDebugDataStructureViewDto>;
  astView(): Promise<UiDebugAstViewDto>;
  irView(): Promise<UiDebugIrViewDto>;
  asmView(): Promise<UiDebugAsmViewDto>;
}

export interface MiniCRealtimeAnalysisApiAdapter {
  analyze(sourceName: string, sourceText: string, version: number): Promise<UiRealtimeAnalysisDto>;
  tokenize(sourceName: string, sourceText: string): Promise<readonly UiLexerTokenVisualDto[]>;
}

export interface MiniCWorkbenchViewModelAdapters {
  readonly observationApi: MiniCObservationApiAdapter;
  readonly debugApi: MiniCDebugApiAdapter;
  readonly realtimeAnalysisApi: MiniCRealtimeAnalysisApiAdapter;
}

export class MiniCWorkbenchViewModel {
  static readonly mirror = miniCWorkbenchViewModelMirror;

  readonly mirror = miniCWorkbenchViewModelMirror;

  private readonly api: MiniCObservationApiAdapter;

  private readonly debugApi: MiniCDebugApiAdapter;

  private readonly realtimeAnalysisApi: MiniCRealtimeAnalysisApiAdapter;

  private nextRealtimeVersion = 0;

  private sourceLoadPromise: Promise<void> = Promise.resolve();

  private debugOperationPromise: Promise<void> = Promise.resolve();

  private readonly listeners = new Set<() => void>();

  private readonly sourceNameStore = new MiniCWritableProperty("", () => this.emit());

  private readonly sourceTextStore = new MiniCWritableProperty("", () => this.emit());

  private readonly lastOutcomeStore = new MiniCWritableProperty("", () => this.emit());

  private readonly runtimePendingStore = new MiniCWritableProperty(false, () => this.emit());

  private readonly runtimeErrorStore = new MiniCWritableProperty<string | null>(null, () => this.emit());

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

  private readonly stageViewsStore = new MiniCWritableProperty<readonly UiStageViewDto[]>([], () =>
    this.emit(),
  );

  private readonly inspectorModelStore = new MiniCWritableProperty<UiInspectorModelDto | null>(null, () =>
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

  constructor(initialSourceName = "", initialSourceText = "", adapters: MiniCWorkbenchViewModelAdapters) {
    this.api = adapters.observationApi;
    this.debugApi = adapters.debugApi;
    this.realtimeAnalysisApi = adapters.realtimeAnalysisApi;
    if (initialSourceName !== "" || initialSourceText !== "") {
      this.background(this.loadSource(initialSourceName, initialSourceText), "加载初始源码失败");
      this.background(this.submitRealtimeSource(initialSourceName, initialSourceText), "实时分析失败");
    }
  }

  async loadSource(name: string, source: string): Promise<void> {
    await this.withRuntimeOperation("加载源码", async () => {
      this.sourceNameStore.set(name);
      this.sourceTextStore.set(source);
      this.clearSessionState();
      const promise = Promise.all([
        this.api.loadSource(name, source),
        this.debugApi.loadSource(name, source),
      ]).then(() => undefined);
      this.sourceLoadPromise = promise.catch((error: unknown) => {
        this.sourceLoadPromise = Promise.resolve();
        throw error;
      });
      await this.sourceLoadPromise;
    });
  }

  async renameSource(name: string): Promise<void> {
    await this.loadSource(name, this.sourceTextStore.get());
  }

  async submitRealtimeSource(name: string, source: string): Promise<void> {
    this.sourceNameStore.set(name);
    this.sourceTextStore.set(source);
    const version = this.nextRealtimeVersion + 1;
    this.nextRealtimeVersion = version;
    try {
      const result = await this.realtimeAnalysisApi.analyze(name, source, version);
      if (version === this.nextRealtimeVersion) {
        this.applyRealtimeAnalysis(result);
        this.clearRuntimeError("实时分析");
      }
    } catch (error) {
      if (version === this.nextRealtimeVersion) {
        this.recordRuntimeError("实时分析失败", error);
      }
    }
  }

  async startSession(): Promise<void> {
    await this.withRuntimeOperation("启动编译 pipeline", async () => {
      await this.sourceLoadPromise;
      await this.api.startSession();
      this.sessionStartedStore.set(true);
      this.selectedVisualStageStore.set("");
      await this.refreshAll();
    });
  }

  async next(): Promise<UiControlResultDto> {
    return this.withRuntimeOperation("下一步", async () => {
      await this.autoConfirmExecutionInput();
      const result = await this.api.next();
      this.applyControlResult(result);
      await this.refreshAll();
      return result;
    });
  }

  async nextStage(): Promise<UiControlResultDto> {
    return this.withRuntimeOperation("下一阶段", async () => {
      await this.autoConfirmExecutionInput();
      this.selectedVisualStageStore.set("");
      const state = this.currentStateStore.get();
      const result = state !== null && state.currentStage === "execution" && state.canNext
        ? await this.api.next()
        : await this.api.nextStage();
      this.applyControlResult(result);
      await this.refreshAll();
      return result;
    });
  }

  async runToExecution(): Promise<UiControlResultDto> {
    return this.withRuntimeOperation("到执行", async () => {
      this.selectedVisualStageStore.set("");
      const result = await this.api.runToExecution();
      this.applyControlResult(result);
      await this.refreshAll();
      return result;
    });
  }

  async play(): Promise<UiControlResultDto> {
    return this.withRuntimeOperation("播放", async () => {
      this.selectedVisualStageStore.set("");
      await this.autoConfirmExecutionInput();
      const result = await this.api.play();
      this.applyControlResult(result);
      await this.refreshAll();
      return result;
    });
  }

  async playFast(): Promise<UiControlResultDto> {
    return this.withRuntimeOperation("2x 播放", async () => {
      this.selectedVisualStageStore.set("");
      await this.autoConfirmExecutionInput();
      const result = await this.api.playFast();
      this.applyControlResult(result);
      await this.refreshAll();
      return result;
    });
  }

  async tick(): Promise<UiControlResultDto> {
    return this.withRuntimeOperation("播放 tick", async () => {
      await this.autoConfirmExecutionInput();
      const result = await this.api.tick();
      this.applyControlResult(result);
      await this.refreshAll();
      return result;
    });
  }

  async pause(): Promise<UiControlResultDto> {
    return this.withRuntimeOperation("暂停", async () => {
      const result = await this.api.pause();
      this.applyControlResult(result);
      await this.refreshAll();
      return result;
    });
  }

  async confirmExecutionInput(standardInput: string): Promise<UiControlResultDto> {
    return this.withRuntimeOperation("确认运行输入", async () => {
      this.selectedVisualStageStore.set("");
      this.executionInputDraftText = standardInput ?? "";
      const result = await this.api.confirmExecutionInput(standardInput);
      this.applyControlResult(result);
      await this.refreshAll();
      return result;
    });
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

  async selectVisualStage(stage: string): Promise<void> {
    this.selectedVisualStageStore.set(stage);
    const state = this.currentStateStore.get();
    if (stage !== "" && state !== null && state.playbackMode !== "PAUSED") {
      await this.pause();
    }
    await this.refreshVisualDataFromApi();
  }

  async startDebug(): Promise<void> {
    await this.withDebugOperation("启动 Debug", () => this.startDebugWithinOperation());
  }

  setDebugBreakpoints(lines: readonly number[]): void {
    this.debugBreakpointLinesStore.set(normalizeBreakpoints(lines));
    this.background(this.syncDebugBreakpoints(), "同步断点失败");
  }

  async syncDebugBreakpoints(): Promise<void> {
    await this.withDebugOperation("同步断点", async () => {
      if (!this.debugStartedStore.get()) {
        return;
      }
      await this.applyPendingDebugBreakpoints();
      await this.refreshDebug();
    });
  }

  async setDebugBreakpoint(line: number): Promise<void> {
    await this.withDebugOperation("设置断点", async () => {
      await this.ensureDebugStarted();
      this.debugBreakpointLinesStore.set(normalizeBreakpoints([...this.debugBreakpointLinesStore.get(), line]));
      this.debugStateStore.set(await this.debugApi.setBreakpoint(line));
      await this.refreshDebug();
    });
  }

  async clearDebugBreakpoint(line: number): Promise<void> {
    await this.withDebugOperation("清除断点", async () => {
      await this.ensureDebugStarted();
      this.debugBreakpointLinesStore.set(this.debugBreakpointLinesStore.get().filter((item) => item !== line));
      this.debugStateStore.set(await this.debugApi.clearBreakpoint(line));
      await this.refreshDebug();
    });
  }

  async debugRunToBreakpoint(): Promise<void> {
    await this.withDebugControl("运行到断点", () => this.debugApi.runToBreakpoint());
  }

  async debugRunToEnd(): Promise<void> {
    await this.withDebugControl("运行到结束", () => this.debugApi.runToEnd());
  }

  async debugFastForward(): Promise<void> {
    await this.withDebugControl("Debug 快进", () => this.debugApi.fastForward());
  }

  async debugStepOver(): Promise<void> {
    await this.withDebugControl("本层下一句", () => this.debugApi.stepOver());
  }

  async debugStepInto(): Promise<void> {
    await this.withDebugControl("下一句", () => this.debugApi.stepInto());
  }

  async debugStepOut(): Promise<void> {
    await this.withDebugControl("Debug 步出", () => this.debugApi.stepOut());
  }

  async debugPause(): Promise<void> {
    await this.withDebugControl("Debug 暂停", () => this.debugApi.pause());
  }

  async debugRestart(): Promise<void> {
    await this.withDebugControl("Debug 重启", () => this.debugApi.restart());
  }

  async debugClose(): Promise<void> {
    await this.withDebugOperation("关闭 Debug", async () => {
      await this.ensureDebugStarted();
      this.debugStateStore.set(await this.debugApi.close());
      this.debugStartedStore.set(false);
      this.debugMetadataViewStore.set(null);
      this.debugDataStructureViewStore.set(null);
      this.debugAstViewStore.set(null);
      this.debugIrViewStore.set(null);
      this.debugAsmViewStore.set(null);
    });
  }

  async debugStepBack(): Promise<void> {
    await this.withDebugControl("上一句", () => this.debugApi.stepBack());
  }

  async debugStepBackOver(): Promise<void> {
    await this.withDebugControl("本层上一句", () => this.debugApi.stepBackOver());
  }

  async debugBackToBreakpoint(): Promise<void> {
    await this.withDebugControl("上个断点", () => this.debugApi.backToBreakpoint());
  }

  async debugBackToCallSite(): Promise<void> {
    await this.withDebugControl("返回调用处", () => this.debugApi.backToCallSite());
  }

  async refreshDebug(): Promise<void> {
    if (!this.debugStartedStore.get()) {
      return;
    }
    const state = await this.debugApi.state();
    this.debugStateStore.set(state);
    const settled = await Promise.allSettled([
      this.debugApi.metadataView(),
      this.debugApi.dataStructureView(),
      this.debugApi.astView(),
      this.debugApi.irView(),
      this.debugApi.asmView(),
    ]);
    if (settled[0]?.status === "fulfilled") this.debugMetadataViewStore.set(settled[0].value);
    if (settled[1]?.status === "fulfilled") this.debugDataStructureViewStore.set(settled[1].value);
    if (settled[2]?.status === "fulfilled") this.debugAstViewStore.set(settled[2].value);
    if (settled[3]?.status === "fulfilled") this.debugIrViewStore.set(settled[3].value);
    if (settled[4]?.status === "fulfilled") this.debugAsmViewStore.set(settled[4].value);
    const rejected = settled.find((result) => result.status === "rejected");
    if (rejected?.status === "rejected") {
      this.recordRuntimeError("刷新 Debug 视图失败", rejected.reason);
    }
  }

  async refreshAll(): Promise<void> {
    if (!this.sessionStartedStore.get()) {
      return;
    }
    const [state, stageData, globalData] = await Promise.all([
      this.api.currentState(),
      this.api.currentStageData(),
      this.api.globalData(),
    ]);
    this.currentStateStore.set(state);
    this.currentStageDataStore.set(stageData);
    this.globalDataStore.set(globalData);
    const derived = await Promise.allSettled([
      this.api.stageViews(),
      this.api.inspectorModel(),
    ]);
    if (derived[0]?.status === "fulfilled") {
      this.stageViewsStore.set(derived[0].value);
    }
    if (derived[1]?.status === "fulfilled") {
      this.inspectorModelStore.set(derived[1].value);
    }
    const rejected = derived.find((result) => result.status === "rejected");
    if (rejected?.status === "rejected") {
      this.recordRuntimeError("刷新派生 UI 状态失败", rejected.reason);
    }
    await this.refreshVisualDataFromApi();
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

  runtimePendingProperty(): MiniCReadonlyProperty<boolean> {
    return this.runtimePendingStore;
  }

  runtimeErrorProperty(): MiniCReadonlyProperty<string | null> {
    return this.runtimeErrorStore;
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

  stageViewsProperty(): MiniCReadonlyProperty<readonly UiStageViewDto[]> {
    return this.stageViewsStore;
  }

  inspectorModelProperty(): MiniCReadonlyProperty<UiInspectorModelDto | null> {
    return this.inspectorModelStore;
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
      runtimePending: this.runtimePendingStore.get(),
      runtimeError: this.runtimeErrorStore.get(),
      sessionStarted: this.sessionStartedStore.get(),
      currentState: this.currentStateStore.get(),
      currentStageData: this.currentStageDataStore.get(),
      currentStageVisualData: this.currentStageVisualDataStore.get(),
      lexerVisualData: this.lexerVisualDataStore.get(),
      astVisualData: this.astVisualDataStore.get(),
      semanticVisualData: this.semanticVisualDataStore.get(),
      codegenVisualData: this.codegenVisualDataStore.get(),
      globalData: this.globalDataStore.get(),
      stageViews: this.stageViewsStore.get(),
      inspectorModel: this.inspectorModelStore.get(),
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
    this.stageViewsStore.set([]);
    this.inspectorModelStore.set(null);
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

  private async refreshVisualDataFromApi(): Promise<void> {
    const state = this.currentStateStore.get();
    const selected = this.selectedVisualStageStore.get();
    const currentStage = selected === "" ? state?.currentStage ?? "source" : toStageId(selected);
    const [lexer, ast, semantic, codegen, current] = await Promise.all([
      this.api.lexerVisualData(),
      this.api.astVisualData(),
      this.api.semanticVisualData(),
      this.api.codegenVisualData(),
      selected === "" ? this.api.currentStageVisualData() : this.visualDataForStage(currentStage),
    ]);
    this.lexerVisualDataStore.set(lexer);
    this.astVisualDataStore.set(ast);
    this.semanticVisualDataStore.set(semantic);
    this.codegenVisualDataStore.set(codegen);
    this.currentStageVisualDataStore.set(current);
  }

  private visualDataForStage(stage: MiniCStageId): Promise<UiStageVisualDto> {
    if (stage === "lexer") {
      return this.api.lexerVisualData();
    }
    if (stage === "parser") {
      return this.api.astVisualData();
    }
    if (stage === "semantic" || stage === "ir") {
      return this.api.semanticVisualData();
    }
    if (stage === "codegen" || stage === "toolchain" || stage === "execution") {
      return this.api.codegenVisualData();
    }
    return this.api.currentStageVisualData();
  }

  private applyControlResult(result: UiControlResultDto): void {
    this.lastControlResultStore.set(result);
    this.lastOutcomeStore.set(result.outcome);
  }

  private async autoConfirmExecutionInput(): Promise<void> {
    if (!this.sessionStartedStore.get() || !this.executionAwaitingInput()) {
      return;
    }
    const result = await this.api.confirmExecutionInput(this.executionInputDraftText);
    this.applyControlResult(result);
    await this.refreshAll();
  }

  private executionAwaitingInput(): boolean {
    const state = this.currentStateStore.get();
    const data = this.globalDataStore.get();
    return (
      state !== null &&
      data !== null &&
      state.currentStage === "execution" &&
      data.executionInputPending
    );
  }

  private applyRealtimeAnalysis(result: UiRealtimeAnalysisDto): void {
    if (this.sourceNameStore.get() === result.sourceName && this.sourceTextStore.get() === result.sourceText) {
      this.realtimeAnalysisStore.set(result);
    }
  }

  private async ensureDebugStarted(): Promise<void> {
    if (!this.debugStartedStore.get()) {
      await this.startDebugWithinOperation();
    }
  }

  private async startDebugWithinOperation(): Promise<void> {
    await this.sourceLoadPromise;
    const name = this.sourceNameStore.get().trim() === "" ? "untitled.mc" : this.sourceNameStore.get();
    await this.debugApi.loadSource(name, this.sourceTextStore.get());
    this.debugStateStore.set(await this.debugApi.startDebug());
    this.debugStartedStore.set(true);
    await this.applyPendingDebugBreakpoints();
    await this.refreshDebug();
  }

  private async withDebugControl(label: string, command: () => Promise<UiDebugStateDto>): Promise<void> {
    await this.withDebugOperation(label, async () => {
      await this.ensureDebugStarted();
      this.debugStateStore.set(await command());
      await this.refreshDebug();
    });
  }

  private async withDebugOperation<T>(label: string, operation: () => Promise<T>): Promise<T> {
    const next = this.debugOperationPromise.then(() => this.withRuntimeOperation(label, operation));
    this.debugOperationPromise = next.then(() => undefined, () => undefined);
    return next;
  }

  private async withRuntimeOperation<T>(label: string, operation: () => Promise<T>): Promise<T> {
    this.runtimePendingStore.set(true);
    this.clearRuntimeError(label);
    try {
      const result = await operation();
      return result;
    } catch (error) {
      this.recordRuntimeError(`${label}失败`, error);
      throw error;
    } finally {
      this.runtimePendingStore.set(false);
    }
  }

  private background(promise: Promise<unknown>, fallbackLabel: string): void {
    void promise.catch((error) => {
      this.recordRuntimeError(fallbackLabel, error);
    });
  }

  runInBackground(promise: Promise<unknown>, fallbackLabel: string): void {
    this.background(promise, fallbackLabel);
  }

  reportRuntimeError(label: string, error: unknown): void {
    this.recordRuntimeError(label, error);
  }

  private clearRuntimeError(_label: string): void {
    this.runtimeErrorStore.set(null);
  }

  private recordRuntimeError(label: string, error: unknown): void {
    this.runtimeErrorStore.set(`${label}: ${errorMessage(error)}`);
  }

  private async applyPendingDebugBreakpoints(): Promise<void> {
    let state = this.debugStateStore.get();
    for (const breakpoint of state?.breakpoints ?? []) {
      state = await this.debugApi.clearBreakpoint(breakpoint.line);
    }
    for (const line of this.debugBreakpointLinesStore.get()) {
      state = await this.debugApi.setBreakpoint(line);
    }
    if (state !== null) {
      this.debugStateStore.set(state);
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
  readonly runtimePending: boolean;
  readonly runtimeError: string | null;
  readonly sessionStarted: boolean;
  readonly currentState: UiCurrentStateDto | null;
  readonly currentStageData: UiStageDataDto | null;
  readonly currentStageVisualData: UiStageVisualDto | null;
  readonly lexerVisualData: UiStageVisualDto | null;
  readonly astVisualData: UiStageVisualDto | null;
  readonly semanticVisualData: UiStageVisualDto | null;
  readonly codegenVisualData: UiStageVisualDto | null;
  readonly globalData: UiGlobalDataDto | null;
  readonly stageViews: readonly UiStageViewDto[];
  readonly inspectorModel: UiInspectorModelDto | null;
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

function normalizeBreakpoints(lines: readonly number[]): readonly number[] {
  return [...new Set(lines.map((line) => Math.trunc(line)).filter((line) => line >= 1))].sort((left, right) => left - right);
}

function errorMessage(error: unknown): string {
  if (error instanceof MiniCUiApiError) {
    return error.body.message;
  }
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return String(error);
}

function clampUnit(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.max(0, Math.min(1, value));
}

export default MiniCWorkbenchViewModel;
