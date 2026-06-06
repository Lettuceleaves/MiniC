import { useEffect, useMemo, useRef, useState } from "react";
import type { MutableRefObject } from "react";
import { MiniCWorkbenchControlHub } from "../control/MiniCWorkbenchControlHub";
import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCKeyBindingConfig } from "../workbench/MiniCKeyBindingConfig";
import MiniCSettings from "./MiniCSettings";
import ThemeManager from "./ThemeManager";

export const miniCSettingsPaneMirror = {
  javaPath: "src/main/java/minic/settings/MiniCSettingsPane.java",
  webPath: "uiweb/src/settings/MiniCSettingsPane.tsx",
  packageName: "minic.settings",
  exportName: "MiniCSettingsPane",
  kind: "component",
  imports: [
    "javafx.collections.FXCollections",
    "javafx.collections.ObservableList",
    "javafx.scene.control.Button",
    "javafx.scene.control.ComboBox",
    "javafx.scene.control.Label",
    "javafx.scene.control.Slider",
    "javafx.scene.input.KeyCode",
    "javafx.scene.input.KeyEvent",
    "javafx.scene.input.MouseEvent",
    "javafx.scene.input.ScrollEvent",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.VBox",
    "javafx.stage.FileChooser",
    "minic.color.ThemeManager",
    "minic.uilocal.MiniCKeyBindingConfig",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
  ],
  fields: [
    { name: "FRAME_INTERVAL_STEP", signature: "private static final long FRAME_INTERVAL_STEP = 50;" },
    { name: "GRAPH_ZOOM_STEP_BLOCK", signature: "private static final double GRAPH_ZOOM_STEP_BLOCK = 0.005;" },
    { name: "UI_SCALE_STEP_BLOCK", signature: "private static final double UI_SCALE_STEP_BLOCK = 0.05;" },
    { name: "themeNames", signature: "private final ObservableList<String> themeNames =" },
    { name: "themeCombo", signature: "private final ComboBox<String> themeCombo =" },
    { name: "controlHub", signature: "private final MiniCWorkbenchControlHub controlHub =" },
    { name: "keyBindingConfig", signature: "private final MiniCKeyBindingConfig keyBindingConfig =" },
    { name: "keyBindingWarning", signature: 'private final Label keyBindingWarning = new Label("");' },
    { name: "captureAction", signature: 'private String captureAction = "";' },
    { name: "pendingCombo", signature: 'private String pendingCombo = "";' },
    { name: "captureKeys", signature: "private final LinkedHashSet<KeyCode> captureKeys =" },
  ],
  methods: [
    { name: "registerSettingsCommands", signature: "registerSettingsCommands()" },
    { name: "shiftTheme", signature: "shiftTheme(int delta)" },
    { name: "refreshThemeList", signature: "refreshThemeList()" },
    { name: "importTheme", signature: "importTheme()" },
    { name: "keyBindingRow", signature: "keyBindingRow(String action)" },
    { name: "bindingText", signature: "bindingText(String action)" },
    { name: "beginCapture", signature: "beginCapture(String action, Button button)" },
    { name: "handleCaptureKey", signature: "handleCaptureKey(String action, Button button, KeyEvent event)" },
    { name: "handleCaptureMouse", signature: "handleCaptureMouse(String action, Button button, MouseEvent event)" },
    { name: "handleCaptureScroll", signature: "handleCaptureScroll(String action, Button button, ScrollEvent event)" },
    { name: "confirmCapture", signature: "confirmCapture()" },
    { name: "cancelCapture", signature: "cancelCapture()" },
    { name: "formatZoomStep", signature: "formatZoomStep(double value)" },
    { name: "roundUiScale", signature: "roundUiScale(double value)" },
    { name: "formatPercent", signature: "formatPercent(double value)" },
  ],
} as const satisfies JavaMirrorFile;

const FRAME_INTERVAL_STEP = 50;
const GRAPH_ZOOM_STEP_BLOCK = 0.005;
const UI_SCALE_STEP_BLOCK = 0.05;

interface CaptureState {
  readonly action: string;
  readonly pendingCombo: string;
}

export function MiniCSettingsPane() {
  const keyBindingConfig = useMemo(() => MiniCKeyBindingConfig.loadDefault(), []);
  const controlHub = useMemo(() => new MiniCWorkbenchControlHub(), []);
  const heldKeys = useRef(new Set<string>());
  const fileInput = useRef<HTMLInputElement | null>(null);
  const [themeNames, setThemeNames] = useState<readonly string[]>(() => ThemeManager.availableThemes());
  const [currentTheme, setCurrentTheme] = useState(() => ThemeManager.currentTheme());
  const [frameInterval, setFrameInterval] = useState(() => MiniCSettings.frameIntervalMillis());
  const [uiScale, setUiScaleState] = useState(() => MiniCSettings.uiScale());
  const [graphZoomStep, setGraphZoomStepState] = useState(() => MiniCSettings.graphZoomStep());
  const [graphZoomAnchor, setGraphZoomAnchorState] = useState(() => MiniCSettings.graphZoomAnchor());
  const [capture, setCapture] = useState<CaptureState | null>(null);
  const [keyBindingWarning, setKeyBindingWarning] = useState("");
  const [bindingVersion, setBindingVersion] = useState(0);

  useEffect(() => {
    controlHub.registerSettingsCommands({
      themeSetter: (themeName) => {
        ThemeManager.setTheme(themeName);
        setCurrentTheme(ThemeManager.currentTheme());
      },
      themeNext: () => shiftTheme(1),
      themePrevious: () => shiftTheme(-1),
      frameIntervalSetter: setFrameIntervalValue,
      currentFrameInterval: () => MiniCSettings.frameIntervalMillis(),
      minFrameInterval: MiniCSettings.minFrameInterval,
      maxFrameInterval: MiniCSettings.maxFrameInterval,
      frameIntervalStep: FRAME_INTERVAL_STEP,
      uiScaleSetter: setUiScaleValue,
      currentUiScale: () => MiniCSettings.uiScale(),
      minUiScale: MiniCSettings.minUiScale,
      maxUiScale: MiniCSettings.maxUiScale,
      uiScaleStep: UI_SCALE_STEP_BLOCK,
    });
  }, [controlHub]);

  function shiftTheme(delta: number): void {
    const themes = ThemeManager.availableThemes();
    if (themes.length === 0) {
      return;
    }
    const currentIndex = Math.max(0, themes.indexOf(ThemeManager.currentTheme()));
    const nextTheme = themes[floorMod(currentIndex + delta, themes.length)];
    if (nextTheme !== undefined) {
      ThemeManager.setTheme(nextTheme);
      setThemeNames(themes);
      setCurrentTheme(nextTheme);
    }
  }

  function setFrameIntervalValue(value: number): void {
    MiniCSettings.setFrameIntervalMillis(value);
    setFrameInterval(MiniCSettings.frameIntervalMillis());
  }

  function setUiScaleValue(value: number): void {
    MiniCSettings.setUiScale(roundUiScale(value));
    setUiScaleState(MiniCSettings.uiScale());
  }

  function setGraphZoomStep(value: number): void {
    MiniCSettings.setGraphZoomStep(value);
    setGraphZoomStepState(MiniCSettings.graphZoomStep());
  }

  function setGraphZoomAnchor(value: string): void {
    MiniCSettings.setGraphZoomAnchor(value);
    setGraphZoomAnchorState(MiniCSettings.graphZoomAnchor());
  }

  function beginCapture(action: string): void {
    heldKeys.current.clear();
    setCapture({ action, pendingCombo: "" });
    setKeyBindingWarning("");
  }

  function updatePendingCombo(action: string, combo: string): void {
    if (combo.trim().length === 0) {
      return;
    }
    setCapture({ action, pendingCombo: combo });
    setKeyBindingWarning("");
  }

  function confirmCapture(): void {
    if (capture === null) {
      return;
    }
    const normalizedCombo = MiniCKeyBindingConfig.normalizeCombo(capture.pendingCombo);
    if (normalizedCombo === "") {
      setKeyBindingWarning("请输入包含普通按键或鼠标按键的组合。");
      return;
    }
    if (MiniCKeyBindingConfig.isReserved(normalizedCombo)) {
      setKeyBindingWarning("Enter/Esc 为保留键位，请重新输入组合。");
      return;
    }
    const conflict = MiniCKeyBindingConfig.conflictingAction(capture.action, normalizedCombo);
    if (conflict !== null) {
      setKeyBindingWarning(`键位冲突：${keyBindingConfig.labelFor(conflict)} 已使用 ${normalizedCombo}`);
      return;
    }
    MiniCKeyBindingConfig.setKeys(capture.action, [normalizedCombo]);
    heldKeys.current.clear();
    setCapture(null);
    setKeyBindingWarning("");
    setBindingVersion((version) => version + 1);
  }

  function cancelCapture(): void {
    heldKeys.current.clear();
    setCapture(null);
    setKeyBindingWarning("");
  }

  async function importTheme(file: File): Promise<void> {
    const importedName = await ThemeManager.importTheme(file);
    setThemeNames(ThemeManager.availableThemes());
    setCurrentTheme(importedName);
  }

  const actions = keyBindingConfig.actions();
  void bindingVersion;

  return (
    <section className="activity-placeholder" data-java-source={miniCSettingsPaneMirror.javaPath}>
      <h1 className="activity-placeholder-title">设置</h1>

      <label className="activity-placeholder-text" htmlFor="minic-theme">
        主题
      </label>
      <div className="settings-row">
        <select
          className="control-secondary"
          id="minic-theme"
          onChange={(event) => {
            ThemeManager.setTheme(event.target.value);
            setCurrentTheme(ThemeManager.currentTheme());
          }}
          value={currentTheme}
        >
          {themeNames.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
        <button className="control-secondary" onClick={() => fileInput.current?.click()} type="button">
          导入主题...
        </button>
        <input
          accept="application/json,.json"
          hidden
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file !== undefined) {
              void importTheme(file);
            }
            event.target.value = "";
          }}
          ref={fileInput}
          type="file"
        />
      </div>

      <label className="activity-placeholder-text" htmlFor="minic-frame-interval">
        帧间隔
      </label>
      <div className="settings-row">
        <input
          className="control-secondary"
          id="minic-frame-interval"
          max={MiniCSettings.maxFrameInterval()}
          min={MiniCSettings.minFrameInterval()}
          onChange={(event) => setFrameIntervalValue(Number(event.target.value))}
          step={FRAME_INTERVAL_STEP}
          type="range"
          value={frameInterval}
        />
        <span className="body-text settings-value">{frameInterval} ms</span>
      </div>

      <label className="activity-placeholder-text" htmlFor="minic-ui-scale">
        全局缩放
      </label>
      <div className="settings-row">
        <input
          className="control-secondary"
          id="minic-ui-scale"
          max={MiniCSettings.maxUiScale()}
          min={MiniCSettings.minUiScale()}
          onChange={(event) => setUiScaleValue(Number(event.target.value))}
          step={UI_SCALE_STEP_BLOCK}
          type="range"
          value={uiScale}
        />
        <span className="body-text settings-value">{formatPercent(uiScale)}</span>
      </div>

      <label className="activity-placeholder-text" htmlFor="minic-graph-zoom-step">
        图形缩放灵敏度
      </label>
      <div className="settings-row">
        <input
          className="control-secondary"
          id="minic-graph-zoom-step"
          max={MiniCSettings.maxGraphZoomStep()}
          min={MiniCSettings.minGraphZoomStep()}
          onChange={(event) => setGraphZoomStep(Number(event.target.value))}
          step={GRAPH_ZOOM_STEP_BLOCK}
          type="range"
          value={graphZoomStep}
        />
        <span className="body-text settings-value">{formatZoomStep(graphZoomStep)}</span>
      </div>

      <label className="activity-placeholder-text" htmlFor="minic-graph-zoom-anchor">
        图形缩放中心
      </label>
      <select
        className="control-secondary"
        id="minic-graph-zoom-anchor"
        onChange={(event) => setGraphZoomAnchor(event.target.value)}
        value={graphZoomAnchor}
      >
        <option value="mouse">mouse</option>
        <option value="center">center</option>
      </select>

      <label className="activity-placeholder-text">键位绑定</label>
      <div className="key-binding-list">
        {actions.map((action) => keyBindingRow(action, keyBindingConfig, capture, beginCapture, updatePendingCombo, confirmCapture, cancelCapture, heldKeys))}
      </div>
      <p className="body-text key-binding-warning">{keyBindingWarning}</p>
    </section>
  );
}

MiniCSettingsPane.mirror = miniCSettingsPaneMirror;

function keyBindingRow(
  action: string,
  keyBindingConfig: MiniCKeyBindingConfig,
  capture: CaptureState | null,
  beginCapture: (action: string) => void,
  updatePendingCombo: (action: string, combo: string) => void,
  confirmCapture: () => void,
  cancelCapture: () => void,
  heldKeys: MutableRefObject<Set<string>>,
) {
  const capturing = capture?.action === action;
  const text = capturing && capture.pendingCombo !== "" ? `${capture.pendingCombo}  Enter 确认` : bindingText(keyBindingConfig, action);
  return (
    <div className="settings-row key-binding-row" key={action}>
      <span className="body-text key-binding-action">{keyBindingConfig.labelFor(action)}</span>
      <button
        className={`control-secondary key-binding-button${capturing ? " key-binding-capturing" : ""}`}
        onClick={() => beginCapture(action)}
        onKeyDown={(event) => {
          if (!capturing) {
            return;
          }
          if (event.key === "Escape") {
            cancelCapture();
            event.preventDefault();
            return;
          }
          if (event.key === "Enter") {
            confirmCapture();
            event.preventDefault();
            return;
          }
          if (!isModifier(event.key)) {
            heldKeys.current.add(event.key);
          }
          updatePendingCombo(action, MiniCKeyBindingConfig.comboFrom(event.nativeEvent, heldKeys.current));
          event.preventDefault();
        }}
        onKeyUp={(event) => {
          if (!capturing || isModifier(event.key)) {
            return;
          }
          heldKeys.current.delete(event.key);
          event.preventDefault();
        }}
        onMouseDown={(event) => {
          if (!capturing) {
            return;
          }
          updatePendingCombo(action, MiniCKeyBindingConfig.comboFrom(event.nativeEvent, heldKeys.current));
          event.preventDefault();
        }}
        onWheel={(event) => {
          if (!capturing) {
            return;
          }
          updatePendingCombo(action, MiniCKeyBindingConfig.comboFrom(event.nativeEvent, heldKeys.current));
          event.preventDefault();
        }}
        type="button"
      >
        {text}
      </button>
    </div>
  );
}

function bindingText(keyBindingConfig: MiniCKeyBindingConfig, action: string): string {
  const keys = keyBindingConfig.keysFor(action);
  return keys.length === 0 ? "(未绑定)" : keys.join(" / ");
}

function formatZoomStep(value: number): string {
  return value.toFixed(3);
}

function floorMod(value: number, divisor: number): number {
  return ((value % divisor) + divisor) % divisor;
}

function roundUiScale(value: number): number {
  return Math.round(value * 100) / 100;
}

function formatPercent(value: number): string {
  return `${Math.round(value * 100)}%`;
}

function isModifier(key: string): boolean {
  return key === "Control" || key === "Ctrl" || key === "Alt" || key === "Shift" || key === "Meta";
}

export default MiniCSettingsPane;
