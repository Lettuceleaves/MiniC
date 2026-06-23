import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCSettingsMirror = {
  javaPath: "src/main/java/minic/settings/MiniCSettings.java",
  webPath: "uiweb/src/settings/MiniCSettings.ts",
  packageName: "minic.settings",
  exportName: "MiniCSettings",
  kind: "class",
  imports: [
    "java.io.IOException",
    "java.math.BigDecimal",
    "java.math.MathContext",
    "java.nio.charset.StandardCharsets",
    "java.nio.file.Files",
    "java.nio.file.Path",
    "java.util.ArrayList",
    "java.util.Comparator",
    "java.util.LinkedHashMap",
    "java.util.List",
    "java.util.Map",
    "java.util.Objects",
    "java.util.Optional",
  ],
  fields: [
    { name: "SETTINGS_FILE", signature: 'private static final Path SETTINGS_FILE = Path.of("config", "settings.json");' },
    { name: "DEFAULT_THEME", signature: 'private static final String DEFAULT_THEME = "dark";' },
    { name: "DEFAULT_FRAME_INTERVAL", signature: "private static final long DEFAULT_FRAME_INTERVAL = 1000;" },
    { name: "MIN_FRAME_INTERVAL", signature: "private static final long MIN_FRAME_INTERVAL = 1;" },
    { name: "MAX_FRAME_INTERVAL", signature: "private static final long MAX_FRAME_INTERVAL = 1000;" },
    { name: "DEFAULT_UI_SCALE", signature: "private static final double DEFAULT_UI_SCALE = 1.0;" },
    { name: "MIN_UI_SCALE", signature: "private static final double MIN_UI_SCALE = 0.75;" },
    { name: "MAX_UI_SCALE", signature: "private static final double MAX_UI_SCALE = 1.5;" },
    { name: "DEFAULT_GRAPH_ZOOM_STEP", signature: "private static final double DEFAULT_GRAPH_ZOOM_STEP = 0.025;" },
    { name: "MIN_GRAPH_ZOOM_STEP", signature: "private static final double MIN_GRAPH_ZOOM_STEP = 0.001;" },
    { name: "MAX_GRAPH_ZOOM_STEP", signature: "private static final double MAX_GRAPH_ZOOM_STEP = 0.25;" },
    { name: "DEFAULT_GRAPH_ZOOM_ANCHOR", signature: 'private static final String DEFAULT_GRAPH_ZOOM_ANCHOR = "mouse";' },
    { name: "DEFAULT_AUTO_SPLIT_PIPELINE_TABS", signature: "private static final boolean DEFAULT_AUTO_SPLIT_PIPELINE_TABS = false;" },
    { name: "LAST_FILE_DIALOG_DIRECTORY_KEY", signature: 'private static final String LAST_FILE_DIALOG_DIRECTORY_KEY = "lastFileDialogDirectory";' },
    { name: "OPEN_FILES_KEY", signature: 'private static final String OPEN_FILES_KEY = "openFiles";' },
    { name: "values", signature: "private static final Map<String, String> values =" },
    { name: "openFiles", signature: "private static final List<OpenFileState> openFiles =" },
  ],
  methods: [
    { name: "load", signature: "load()" },
    { name: "theme", signature: "theme()" },
    { name: "setTheme", signature: "setTheme(String name)" },
    { name: "frameIntervalMillis", signature: "frameIntervalMillis()" },
    { name: "setFrameIntervalMillis", signature: "setFrameIntervalMillis(long millis)" },
    { name: "setFrameIntervalChangeListener", signature: "setFrameIntervalChangeListener(Runnable listener)" },
    { name: "minFrameInterval", signature: "minFrameInterval()" },
    { name: "maxFrameInterval", signature: "maxFrameInterval()" },
    { name: "uiScale", signature: "uiScale()" },
    { name: "setUiScale", signature: "setUiScale(double scale)" },
    { name: "addUiScaleChangeListener", signature: "addUiScaleChangeListener(Runnable listener)" },
    { name: "removeUiScaleChangeListener", signature: "removeUiScaleChangeListener(Runnable listener)" },
    { name: "minUiScale", signature: "minUiScale()" },
    { name: "maxUiScale", signature: "maxUiScale()" },
    { name: "graphZoomStep", signature: "graphZoomStep()" },
    { name: "setGraphZoomStep", signature: "setGraphZoomStep(double step)" },
    { name: "minGraphZoomStep", signature: "minGraphZoomStep()" },
    { name: "maxGraphZoomStep", signature: "maxGraphZoomStep()" },
    { name: "graphZoomAnchor", signature: "graphZoomAnchor()" },
    { name: "setGraphZoomAnchor", signature: "setGraphZoomAnchor(String anchor)" },
    { name: "graphZoomAnchoredAtMouse", signature: "graphZoomAnchoredAtMouse()" },
    { name: "autoSplitPipelineTabs", signature: "autoSplitPipelineTabs()" },
    { name: "setAutoSplitPipelineTabs", signature: "setAutoSplitPipelineTabs(boolean enabled)" },
  ],
} as const satisfies JavaMirrorFile;

export interface MiniCOpenFileState {
  readonly path: string;
  readonly order: string;
}

export interface MiniCSettingsSnapshot {
  readonly theme: string;
  readonly frameInterval: number;
  readonly uiScale: number;
  readonly graphZoomStep: number;
  readonly graphZoomAnchor: "mouse" | "center";
  readonly autoSplitPipelineTabs: boolean;
  readonly lastFileDialogDirectory: string;
  readonly openFiles: readonly MiniCOpenFileState[];
}

const LOCAL_STORAGE_KEY = "minic.uiweb.settings";
const DEFAULT_THEME = "dark";
const DEFAULT_FRAME_INTERVAL = 1000;
const MIN_FRAME_INTERVAL = 1;
const MAX_FRAME_INTERVAL = 1000;
const DEFAULT_UI_SCALE = 1.0;
const MIN_UI_SCALE = 0.75;
const MAX_UI_SCALE = 1.5;
const DEFAULT_GRAPH_ZOOM_STEP = 0.025;
const MIN_GRAPH_ZOOM_STEP = 0.001;
const MAX_GRAPH_ZOOM_STEP = 0.25;
const DEFAULT_GRAPH_ZOOM_ANCHOR = "mouse";
const DEFAULT_AUTO_SPLIT_PIPELINE_TABS = false;
const LAST_FILE_DIALOG_DIRECTORY_KEY = "lastFileDialogDirectory";

let currentValues = defaultValues();
let currentOpenFiles: MiniCOpenFileState[] = [];
let frameIntervalChangeListener: (() => void) | null = null;
const uiScaleChangeListeners = new Set<() => void>();

export class MiniCSettings {
  static readonly mirror = miniCSettingsMirror;

  static load(): void {
    currentValues = defaultValues();
    currentOpenFiles = [];
    const saved = readSettings();
    if (saved === null) {
      save();
      return;
    }
    currentValues = { ...currentValues, ...saved.values };
    currentOpenFiles = saved.openFiles;
    save();
  }

  static snapshot(): MiniCSettingsSnapshot {
    return {
      theme: MiniCSettings.theme(),
      frameInterval: MiniCSettings.frameIntervalMillis(),
      uiScale: MiniCSettings.uiScale(),
      graphZoomStep: MiniCSettings.graphZoomStep(),
      graphZoomAnchor: MiniCSettings.graphZoomAnchor(),
      autoSplitPipelineTabs: MiniCSettings.autoSplitPipelineTabs(),
      lastFileDialogDirectory: currentValues[LAST_FILE_DIALOG_DIRECTORY_KEY] ?? "",
      openFiles: [...currentOpenFiles],
    };
  }

  static theme(): string {
    return currentValues.theme ?? DEFAULT_THEME;
  }

  static setTheme(name: string): void {
    currentValues.theme = name;
    save();
  }

  static frameIntervalMillis(): number {
    return clampInteger(readNumber(currentValues.frameInterval, DEFAULT_FRAME_INTERVAL), MIN_FRAME_INTERVAL, MAX_FRAME_INTERVAL);
  }

  static setFrameIntervalMillis(millis: number): void {
    currentValues.frameInterval = String(clampInteger(millis, MIN_FRAME_INTERVAL, MAX_FRAME_INTERVAL));
    save();
    frameIntervalChangeListener?.();
  }

  static setFrameIntervalChangeListener(listener: (() => void) | null): void {
    frameIntervalChangeListener = listener;
  }

  static minFrameInterval(): number {
    return MIN_FRAME_INTERVAL;
  }

  static maxFrameInterval(): number {
    return MAX_FRAME_INTERVAL;
  }

  static uiScale(): number {
    return clampNumber(readNumber(currentValues.uiScale, DEFAULT_UI_SCALE), MIN_UI_SCALE, MAX_UI_SCALE);
  }

  static setUiScale(scale: number): void {
    currentValues.uiScale = String(clampNumber(scale, MIN_UI_SCALE, MAX_UI_SCALE));
    save();
    [...uiScaleChangeListeners].forEach((listener) => listener());
  }

  static setUiScaleChangeListener(listener: (() => void) | null): void {
    uiScaleChangeListeners.clear();
    if (listener !== null) {
      uiScaleChangeListeners.add(listener);
    }
  }

  static addUiScaleChangeListener(listener: () => void): void {
    uiScaleChangeListeners.add(listener);
  }

  static removeUiScaleChangeListener(listener: () => void): void {
    uiScaleChangeListeners.delete(listener);
  }

  static minUiScale(): number {
    return MIN_UI_SCALE;
  }

  static maxUiScale(): number {
    return MAX_UI_SCALE;
  }

  static graphZoomStep(): number {
    return clampNumber(readNumber(currentValues.graphZoomStep, DEFAULT_GRAPH_ZOOM_STEP), MIN_GRAPH_ZOOM_STEP, MAX_GRAPH_ZOOM_STEP);
  }

  static setGraphZoomStep(step: number): void {
    currentValues.graphZoomStep = String(clampNumber(step, MIN_GRAPH_ZOOM_STEP, MAX_GRAPH_ZOOM_STEP));
    save();
  }

  static minGraphZoomStep(): number {
    return MIN_GRAPH_ZOOM_STEP;
  }

  static maxGraphZoomStep(): number {
    return MAX_GRAPH_ZOOM_STEP;
  }

  static graphZoomAnchor(): "mouse" | "center" {
    return currentValues.graphZoomAnchor === "center" ? "center" : DEFAULT_GRAPH_ZOOM_ANCHOR;
  }

  static setGraphZoomAnchor(anchor: string): void {
    currentValues.graphZoomAnchor = anchor.toLowerCase() === "center" ? "center" : DEFAULT_GRAPH_ZOOM_ANCHOR;
    save();
  }

  static graphZoomAnchoredAtMouse(): boolean {
    return MiniCSettings.graphZoomAnchor() === "mouse";
  }

  static autoSplitPipelineTabs(): boolean {
    return readBoolean(currentValues.autoSplitPipelineTabs, DEFAULT_AUTO_SPLIT_PIPELINE_TABS);
  }

  static setAutoSplitPipelineTabs(enabled: boolean): void {
    currentValues.autoSplitPipelineTabs = String(enabled);
    save();
  }
}

function defaultValues(): Record<string, string> {
  return {
    theme: DEFAULT_THEME,
    frameInterval: String(DEFAULT_FRAME_INTERVAL),
    uiScale: String(DEFAULT_UI_SCALE),
    graphZoomStep: String(DEFAULT_GRAPH_ZOOM_STEP),
    graphZoomAnchor: DEFAULT_GRAPH_ZOOM_ANCHOR,
    autoSplitPipelineTabs: String(DEFAULT_AUTO_SPLIT_PIPELINE_TABS),
    [LAST_FILE_DIALOG_DIRECTORY_KEY]: "",
  };
}

function readSettings(): { readonly values: Record<string, string>; readonly openFiles: MiniCOpenFileState[] } | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(LOCAL_STORAGE_KEY);
  if (raw === null) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!isRecord(parsed)) {
      return null;
    }
    const values: Record<string, string> = {};
    Object.entries(parsed).forEach(([key, value]) => {
      if (key !== "openFiles" && (typeof value === "string" || typeof value === "number" || typeof value === "boolean")) {
        values[key] = String(value);
      }
    });
    return { values, openFiles: parseOpenFiles(parsed.openFiles) };
  } catch {
    return null;
  }
}

function parseOpenFiles(value: unknown): MiniCOpenFileState[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter(isRecord)
    .flatMap((entry) => {
      const path = entry.path;
      const order = entry.order;
      if (typeof path !== "string" || (typeof order !== "string" && typeof order !== "number")) {
        return [];
      }
      return [{ path, order: String(order) }];
    });
}

function save(): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(
    LOCAL_STORAGE_KEY,
    JSON.stringify(
      {
        ...currentValues,
        frameInterval: MiniCSettings.frameIntervalMillis(),
        uiScale: MiniCSettings.uiScale(),
        graphZoomStep: MiniCSettings.graphZoomStep(),
        graphZoomAnchor: MiniCSettings.graphZoomAnchor(),
        autoSplitPipelineTabs: String(MiniCSettings.autoSplitPipelineTabs()),
        openFiles: currentOpenFiles,
      },
      null,
      2,
    ),
  );
}

function readNumber(raw: string | undefined, fallback: number): number {
  if (raw === undefined) {
    return fallback;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : fallback;
}

function readBoolean(raw: string | undefined, fallback: boolean): boolean {
  if (raw === undefined) {
    return fallback;
  }
  return raw.toLowerCase() === "true";
}

function clampInteger(value: number, min: number, max: number): number {
  return Math.trunc(clampNumber(value, min, max));
}

function clampNumber(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

MiniCSettings.load();

export default MiniCSettings;
