import { useEffect, useMemo, useRef, useState, type MutableRefObject } from "react";
import { createMiniCWorkbenchViewModel } from "../api/createMiniCWorkbenchViewModel";
import { MiniCControlTargetType } from "../control/MiniCControlTargetType";
import { MiniCWorkbenchControlHub } from "../control/MiniCWorkbenchControlHub";
import MiniCDebugPane from "../debug/MiniCDebugPane";
import MiniCInfoView from "../info/MiniCInfoView";
import MiniCBottomPanel from "../panel/MiniCBottomPanel";
import { MiniCHoverInspector } from "../panel/MiniCHoverInspector";
import MiniCInspectorView from "../panel/MiniCInspectorView";
import { MiniCSettings, ThemeManager } from "../settings";
import MiniCSettingsPane from "../settings/MiniCSettingsPane";
import MiniCSourceLoaderView from "../source/MiniCSourceLoaderView";
import type { JavaMirrorFile } from "../translation/javaMirror";
import MiniCVisualPane from "../visual/MiniCVisualPane";
import { MiniCSamplePrograms } from "../editor/MiniCSamplePrograms";
import { MiniCKeyBindingConfig, type MiniCInputEvent } from "./MiniCKeyBindingConfig";
import { MiniCPlaybackController } from "./MiniCPlaybackController";
import { MiniCSidebarView } from "./MiniCSidebarView";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "./MiniCWorkbenchViewModel";

export const miniCWorkbenchShellMirror = {
  "javaPath": "src/main/java/minic/uilocal/workbench/MiniCWorkbenchShell.java",
  "webPath": "uiweb/src/workbench/MiniCWorkbenchShell.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCWorkbenchShell",
  "kind": "component",
  "imports": [
    "java.io.IOException",
    "java.math.BigDecimal",
    "java.nio.charset.StandardCharsets",
    "java.nio.file.Files",
    "java.nio.file.Path",
    "java.util.ArrayList",
    "java.util.LinkedHashSet",
    "java.util.List",
    "java.util.Objects",
    "javafx.geometry.Point2D",
    "javafx.scene.Parent",
    "javafx.scene.control.Button",
    "javafx.scene.control.Label",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.control.TextField",
    "javafx.scene.control.Tooltip",
    "javafx.scene.input.KeyCode",
    "javafx.scene.input.KeyEvent",
    "javafx.scene.input.MouseButton",
    "javafx.scene.input.MouseEvent",
    "javafx.scene.input.ScrollEvent",
    "javafx.scene.layout.BorderPane",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Priority",
    "javafx.scene.layout.Region",
    "javafx.scene.layout.StackPane",
    "javafx.scene.layout.VBox",
    "javafx.scene.shape.SVGPath",
    "javafx.stage.FileChooser",
    "javafx.stage.Window",
    "minic.color.ThemeManager",
    "minic.settings.MiniCSettings",
    "minic.settings.MiniCSettingsPane",
    "minic.uilocal.control.MiniCActiveTrackingService",
    "minic.uilocal.control.MiniCControlTargetType",
    "minic.uilocal.control.MiniCViewportAdapter",
    "minic.uilocal.control.MiniCWorkbenchControlHub"
  ],
  "fields": [
    {
      "name": "activeDocumentIndex",
      "signature": "private int activeDocumentIndex"
    },
    {
      "name": "activeSection",
      "signature": "private ActivitySection activeSection="
    },
    {
      "name": "body",
      "signature": "private HBox body"
    },
    {
      "name": "COMPILER_SHORTCUT_ACTIONS",
      "signature": "private static final List<String>COMPILER_SHORTCUT_ACTIONS="
    },
    {
      "name": "controlHub",
      "signature": "private final MiniCWorkbenchControlHub controlHub="
    },
    {
      "name": "documents",
      "signature": "private final ArrayList<DocumentTab>documents="
    },
    {
      "name": "draggedTabIndex",
      "signature": "private int draggedTabIndex="
    },
    {
      "name": "editingTabField",
      "signature": "private TextField editingTabField"
    },
    {
      "name": "editor",
      "signature": "private VBox editor"
    },
    {
      "name": "hoverInspector",
      "signature": "private MiniCHoverInspector hoverInspector"
    },
    {
      "name": "iconPath",
      "signature": "private final String iconPath"
    },
    {
      "name": "keyBindings",
      "signature": "private final MiniCKeyBindingConfig keyBindings="
    },
    {
      "name": "mainContent",
      "signature": "private StackPane mainContent"
    },
    {
      "name": "nextUntitledIndex",
      "signature": "private int nextUntitledIndex="
    },
    {
      "name": "placeholder",
      "signature": "private final String placeholder"
    },
    {
      "name": "pressedKeys",
      "signature": "private final LinkedHashSet<KeyCode>pressedKeys="
    },
    {
      "name": "root",
      "signature": "private BorderPane root"
    },
    {
      "name": "SETTINGS_SHORTCUT_ACTIONS",
      "signature": "private static final List<String>SETTINGS_SHORTCUT_ACTIONS="
    },
    {
      "name": "sourceLoader",
      "signature": "private MiniCSourceLoaderView sourceLoader"
    },
    {
      "name": "sourcePane",
      "signature": "private VBox sourcePane"
    },
    {
      "name": "tabs",
      "signature": "private HBox tabs"
    },
    {
      "name": "TEXT_ZOOM_STEP",
      "signature": "private static final double TEXT_ZOOM_STEP="
    },
    {
      "name": "title",
      "signature": "private final String title"
    },
    {
      "name": "viewModel",
      "signature": "private MiniCWorkbenchViewModel viewModel"
    },
    {
      "name": "VIEWPORT_KEY_SCROLL_DELTA",
      "signature": "private static final double VIEWPORT_KEY_SCROLL_DELTA="
    },
    {
      "name": "visualPane",
      "signature": "private MiniCVisualPane visualPane"
    }
  ],
  "methods": [
    {
      "name": "activeViewportAdapters",
      "signature": "activeViewportAdapters()"
    },
    {
      "name": "activityBar",
      "signature": "activityBar()"
    },
    {
      "name": "activityIcon",
      "signature": "activityIcon(ActivitySection section)"
    },
    {
      "name": "activityItem",
      "signature": "activityItem(ActivitySection section)"
    },
    {
      "name": "addDocument",
      "signature": "addDocument(String name,String source,Path path,BigDecimal order,MiniCWorkbenchViewModel model,boolean persist)"
    },
    {
      "name": "applyRememberedDirectory",
      "signature": "applyRememberedDirectory(FileChooser chooser)"
    },
    {
      "name": "beginRenameDocument",
      "signature": "beginRenameDocument(int index,HBox tab,Label title)"
    },
    {
      "name": "closeDocument",
      "signature": "closeDocument(int index)"
    },
    {
      "name": "commitRenameDocument",
      "signature": "commitRenameDocument(int index,String rawName)"
    },
    {
      "name": "createRoot",
      "signature": "createRoot()"
    },
    {
      "name": "displayName",
      "signature": "displayName()"
    },
    {
      "name": "documentIndex",
      "signature": "documentIndex(Path path)"
    },
    {
      "name": "DocumentTab",
      "signature": "DocumentTab(String name,Path path,BigDecimal order,MiniCWorkbenchViewModel viewModel)"
    },
    {
      "name": "editorArea",
      "signature": "editorArea()"
    },
    {
      "name": "handleCommandShortcut",
      "signature": "handleCommandShortcut(KeyEvent event,List<String>actions)"
    },
    {
      "name": "handleCommandShortcut",
      "signature": "handleCommandShortcut(ScrollEvent event,List<String>actions)"
    },
    {
      "name": "handleKeyPressed",
      "signature": "handleKeyPressed(KeyEvent event)"
    },
    {
      "name": "handleKeyReleased",
      "signature": "handleKeyReleased(KeyEvent event)"
    },
    {
      "name": "handleShortcut",
      "signature": "handleShortcut(KeyEvent event)"
    },
    {
      "name": "handleShortcut",
      "signature": "handleShortcut(ScrollEvent event)"
    },
    {
      "name": "handleViewportShortcut",
      "signature": "handleViewportShortcut(KeyEvent event)"
    },
    {
      "name": "handleViewportShortcut",
      "signature": "handleViewportShortcut(ScrollEvent event)"
    },
    {
      "name": "isModifier",
      "signature": "isModifier(KeyCode code)"
    },
    {
      "name": "newDocument",
      "signature": "newDocument()"
    },
    {
      "name": "nextDocumentOrder",
      "signature": "nextDocumentOrder()"
    },
    {
      "name": "nextUntitledName",
      "signature": "nextUntitledName()"
    },
    {
      "name": "normalizePath",
      "signature": "normalizePath(Path path)"
    },
    {
      "name": "openDocument",
      "signature": "openDocument()"
    },
    {
      "name": "orderForIndex",
      "signature": "orderForIndex(int index)"
    },
    {
      "name": "persistOpenDocuments",
      "signature": "persistOpenDocuments()"
    },
    {
      "name": "placeholderPage",
      "signature": "placeholderPage(ActivitySection section)"
    },
    {
      "name": "rebuildWorkbenchBody",
      "signature": "rebuildWorkbenchBody()"
    },
    {
      "name": "refreshTabs",
      "signature": "refreshTabs()"
    },
    {
      "name": "registerSettingsCommands",
      "signature": "registerSettingsCommands()"
    },
    {
      "name": "renumberDocumentOrders",
      "signature": "renumberDocumentOrders()"
    },
    {
      "name": "reorderDocumentTab",
      "signature": "reorderDocumentTab(int fromIndex,int toIndex)"
    },
    {
      "name": "reorderDraggedTab",
      "signature": "reorderDraggedTab(int targetIndex)"
    },
    {
      "name": "restorePersistedDocuments",
      "signature": "restorePersistedDocuments(MiniCWorkbenchViewModel initialModel)"
    },
    {
      "name": "saveDocument",
      "signature": "saveDocument()"
    },
    {
      "name": "saveDocumentAs",
      "signature": "saveDocumentAs()"
    },
    {
      "name": "saveDocumentAs",
      "signature": "saveDocumentAs(Path rawPath)"
    },
    {
      "name": "sectionContent",
      "signature": "sectionContent()"
    },
    {
      "name": "selectActivitySection",
      "signature": "selectActivitySection(ActivitySection section)"
    },
    {
      "name": "settingsPage",
      "signature": "settingsPage()"
    },
    {
      "name": "shiftTheme",
      "signature": "shiftTheme(int delta)"
    },
    {
      "name": "sidebar",
      "signature": "sidebar()"
    },
    {
      "name": "sourceArea",
      "signature": "sourceArea()"
    },
    {
      "name": "sourceMode",
      "signature": "sourceMode()"
    },
    {
      "name": "statusBar",
      "signature": "statusBar()"
    },
    {
      "name": "switchDocument",
      "signature": "switchDocument(int index)"
    },
    {
      "name": "syncActiveEditorToModel",
      "signature": "syncActiveEditorToModel()"
    },
    {
      "name": "toolbarButton",
      "signature": "toolbarButton(String text,String tooltip,Runnable action)"
    },
    {
      "name": "updateMainContent",
      "signature": "updateMainContent()"
    },
    {
      "name": "viewportZoomDelta",
      "signature": "viewportZoomDelta(double direction)"
    },
    {
      "name": "window",
      "signature": "window()"
    },
    {
      "name": "withName",
      "signature": "withName(String name)"
    },
    {
      "name": "withOrder",
      "signature": "withOrder(BigDecimal order)"
    },
    {
      "name": "withPath",
      "signature": "withPath(Path path)"
    },
    {
      "name": "workbenchBody",
      "signature": "workbenchBody()"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCWorkbenchShellProps {
  readonly title?: string;
}

type ActivitySectionId = "CODE" | "DEBUG" | "SETTINGS" | "INFO";

interface ActivitySection {
  readonly id: ActivitySectionId;
  readonly iconPath: string;
  readonly title: string;
}

interface DocumentTab {
  readonly name: string;
  readonly path: string | null;
  readonly order: number;
  readonly viewModel: MiniCWorkbenchViewModel;
}

const ACTIVITY_SECTIONS: readonly ActivitySection[] = [
  {
    id: "CODE",
    iconPath: "M6 2 L14 2 L20 8 L20 22 L6 22 Z M14 2 L14 8 L20 8 M9 13 L17 13 M9 17 L17 17",
    title: "代码区",
  },
  {
    id: "DEBUG",
    iconPath:
      "M8 9 A4 4 0 0 1 16 9 L16 17 A4 4 0 0 1 8 17 Z M9.2 5 L14.8 5 M10 5 L8 2 M14 5 L16 2 M4 11 L8 11 M16 11 L20 11 M4 15 L8 15 M16 15 L20 15 M6 20 L8.5 17.5 M15.5 17.5 L18 20",
    title: "调试",
  },
  {
    id: "SETTINGS",
    iconPath:
      "M9.7 3 L14.3 3 L14.9 4.8 L16.5 5.5 L18.2 4.7 L20.5 8.7 L19.1 9.9 L19.1 11.8 L20.5 13 L18.2 17 L16.5 16.5 L14.9 17.2 L14.3 19 L9.7 19 L9.1 17.2 L7.5 16.5 L5.8 17 L3.5 13 L4.9 11.8 L4.9 9.9 L3.5 8.7 L5.8 4.7 L7.5 5.5 L9.1 4.8 Z M12 7.6 A3.4 3.4 0 1 0 12 14.4 A3.4 3.4 0 1 0 12 7.6",
    title: "设置",
  },
  {
    id: "INFO",
    iconPath: "M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 M12 10 L12 17 M12 7 L12 7.1",
    title: "信息",
  },
];

const DOCUMENTS_STORAGE_KEY = "minic.uiweb.documents";
const TEXT_ZOOM_STEP = 1.0;
const VIEWPORT_KEY_SCROLL_DELTA = 48.0;
const COMPILER_SHORTCUT_ACTIONS = [
  MiniCWorkbenchControlHub.COMPILER_NEXT,
  MiniCWorkbenchControlHub.COMPILER_NEXT_STAGE,
  MiniCWorkbenchControlHub.COMPILER_RUN_TO_EXECUTION,
  MiniCWorkbenchControlHub.COMPILER_PLAY,
  MiniCWorkbenchControlHub.COMPILER_PLAY_FAST,
  MiniCWorkbenchControlHub.COMPILER_PAUSE,
] as const;
const DEBUG_SHORTCUT_ACTIONS = [
  MiniCWorkbenchControlHub.DEBUG_START,
  MiniCWorkbenchControlHub.DEBUG_RUN_TO_END,
  MiniCWorkbenchControlHub.DEBUG_RUN_TO_BREAKPOINT,
  MiniCWorkbenchControlHub.DEBUG_STEP_OVER,
  MiniCWorkbenchControlHub.DEBUG_STEP_INTO,
  MiniCWorkbenchControlHub.DEBUG_BACK_TO_BREAKPOINT,
  MiniCWorkbenchControlHub.DEBUG_STEP_BACK_OVER,
  MiniCWorkbenchControlHub.DEBUG_STEP_BACK,
] as const;
const SETTINGS_SHORTCUT_ACTIONS = [
  MiniCWorkbenchControlHub.SETTINGS_THEME_NEXT,
  MiniCWorkbenchControlHub.SETTINGS_THEME_PREVIOUS,
  MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_INCREASE,
  MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_DECREASE,
  MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_INCREASE,
  MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_DECREASE,
] as const;

export function MiniCWorkbenchShell({ title = "MiniC Workbench" }: MiniCWorkbenchShellProps) {
  const [activeSection, setActiveSection] = useState<ActivitySectionId>("CODE");
  const [documents, setDocuments] = useState<readonly DocumentTab[]>(() => restorePersistedDocuments());
  const [activeDocumentIndex, setActiveDocumentIndex] = useState(0);
  const [editingTabIndex, setEditingTabIndex] = useState<number | null>(null);
  const [editingTabName, setEditingTabName] = useState("");
  const controlHub = useMemo(() => new MiniCWorkbenchControlHub(), []);
  const keyBindings = useMemo(() => MiniCKeyBindingConfig.loadDefault(), []);
  const pressedKeys = useRef(new Set<string>());
  const [hoverInspector] = useState(() => new MiniCHoverInspector());
  const activeDocument = documents[Math.min(activeDocumentIndex, Math.max(0, documents.length - 1))] ?? documents[0];
  const activeModel = activeDocument.viewModel;
  const activePlaybackController = useMemo(() => new MiniCPlaybackController(activeModel), [activeModel]);
  const activeSnapshot = useWorkbenchShellSnapshot(activeModel);
  const activeModelRef = useRef(activeModel);
  const activePlaybackControllerRef = useRef(activePlaybackController);
  const activeSnapshotRef = useRef(activeSnapshot);
  const activeSectionRef = useRef(activeSection);

  useEffect(() => {
    persistOpenDocuments(documents);
  }, [documents]);

  useEffect(() => {
    activeModelRef.current = activeModel;
    activeSnapshotRef.current = activeSnapshot;
    activeSectionRef.current = activeSection;
  }, [activeModel, activeSection, activeSnapshot]);

  useEffect(() => {
    activePlaybackControllerRef.current = activePlaybackController;
    return () => activePlaybackController.dispose();
  }, [activePlaybackController]);

  useEffect(() => {
    registerWorkbenchCommands(controlHub, activeModelRef, activePlaybackControllerRef);
  }, [controlHub]);

  useEffect(() => {
    return installShortcutDispatch(controlHub, keyBindings, pressedKeys);
  }, [controlHub, keyBindings]);

  const switchDocument = (index: number): void => {
    if (index >= 0 && index < documents.length) {
      setActiveDocumentIndex(index);
    }
  };

  const addDocument = (name: string, source: string, path: string | null = null): void => {
    const viewModel = createMiniCWorkbenchViewModel(name, source);
    setDocuments((current) => [...current, { name, path, order: nextDocumentOrder(current), viewModel }]);
    setActiveDocumentIndex(documents.length);
  };

  const newDocument = (): void => {
    addDocument(nextUntitledName(documents), "");
  };

  const closeDocument = (index: number): void => {
    if (documents.length <= 1) {
      const sample = MiniCSamplePrograms.defaultSample();
      const viewModel = createMiniCWorkbenchViewModel(sample.name, sample.source);
      setDocuments([{ name: sample.name, path: null, order: 1, viewModel }]);
      setActiveDocumentIndex(0);
      return;
    }
    setDocuments((current) => current.filter((_document, currentIndex) => currentIndex !== index));
    setActiveDocumentIndex((current) => Math.max(0, Math.min(current, documents.length - 2)));
  };

  const commitRenameDocument = (index: number, rawName: string): void => {
    const name = rawName.trim();
    if (name.length === 0) {
      setEditingTabIndex(null);
      return;
    }
    setDocuments((current) =>
      current.map((document, currentIndex) => {
        if (currentIndex !== index) {
          return document;
        }
        document.viewModel.runInBackground(document.viewModel.renameSource(name), "重命名源码失败");
        return { ...document, name };
      }),
    );
    setEditingTabIndex(null);
  };

  const saveDocument = (name = activeDocument.name, source = activeModel.sourceTextProperty().get()): void => {
    setDocuments((current) =>
      current.map((document, index) => (index === activeDocumentIndex ? { ...document, name } : document)),
    );
    activeModel.runInBackground(activeModel.renameSource(name), "保存源码名失败");
  };

  const reorderDocumentTab = (fromIndex: number, toIndex: number): void => {
    if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= documents.length || toIndex >= documents.length) {
      return;
    }
    setDocuments((current) => {
      const next = [...current];
      const [moved] = next.splice(fromIndex, 1);
      if (!moved) {
        return current;
      }
      next.splice(toIndex, 0, moved);
      return renumberDocumentOrders(next);
    });
    setActiveDocumentIndex(toIndex);
  };

  return (
    <section className="workbench-root" data-java-source={miniCWorkbenchShellMirror.javaPath} aria-label={title}>
      {activityBar(activeSection, setActiveSection)}
      {sectionContent(activeSection, activeModel, activePlaybackController, activeSnapshot, hoverInspector, controlHub, {
        activeDocument,
        activeDocumentIndex,
        documents,
        editingTabIndex,
        editingTabName,
        setEditingTabName,
        setEditingTabIndex,
        switchDocument,
        closeDocument,
        newDocument,
        commitRenameDocument,
        reorderDocumentTab,
        saveDocument,
        addDocument,
      })}
      {statusBar(activeSnapshot)}
    </section>
  );
}

MiniCWorkbenchShell.mirror = miniCWorkbenchShellMirror;

interface ShellActions {
  readonly activeDocument: DocumentTab;
  readonly activeDocumentIndex: number;
  readonly documents: readonly DocumentTab[];
  readonly editingTabIndex: number | null;
  readonly editingTabName: string;
  readonly setEditingTabName: (name: string) => void;
  readonly setEditingTabIndex: (index: number | null) => void;
  readonly switchDocument: (index: number) => void;
  readonly closeDocument: (index: number) => void;
  readonly newDocument: () => void;
  readonly commitRenameDocument: (index: number, name: string) => void;
  readonly reorderDocumentTab: (fromIndex: number, toIndex: number) => void;
  readonly saveDocument: (name?: string, source?: string) => void;
  readonly addDocument: (name: string, source: string, path?: string | null) => void;
}

function activityBar(activeSection: ActivitySectionId, selectActivitySection: (section: ActivitySectionId) => void) {
  return (
    <nav className="activity-bar" aria-label="MiniC activity bar">
      {ACTIVITY_SECTIONS.map((section) => (
        <button
          aria-label={section.title}
          className={`activity-item${activeSection === section.id ? " active" : ""}`}
          key={section.id}
          onClick={() => selectActivitySection(section.id)}
          title={section.title}
          type="button"
        >
          <svg className="activity-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d={section.iconPath} />
          </svg>
        </button>
      ))}
    </nav>
  );
}

function sectionContent(
  section: ActivitySectionId,
  viewModel: MiniCWorkbenchViewModel,
  playbackController: MiniCPlaybackController,
  snapshot: MiniCWorkbenchSnapshot,
  hoverInspector: MiniCHoverInspector,
  controlHub: MiniCWorkbenchControlHub,
  actions: ShellActions,
) {
  switch (section) {
    case "CODE":
      return workbenchBody(viewModel, playbackController, snapshot, hoverInspector, actions);
    case "DEBUG":
      return <MiniCDebugPane viewModel={viewModel} />;
    case "SETTINGS":
      return settingsPage(controlHub);
    case "INFO":
      return <MiniCInfoView />;
  }
}

function workbenchBody(
  viewModel: MiniCWorkbenchViewModel,
  playbackController: MiniCPlaybackController,
  snapshot: MiniCWorkbenchSnapshot,
  hoverInspector: MiniCHoverInspector,
  actions: ShellActions,
) {
  const sourceVisible = sourceMode(snapshot);
  return (
    <main className="workbench-body">
      <MiniCSidebarView viewModel={viewModel} />
      <section className="editor-area">
        {tabs(actions)}
        <div className={`split workbench-main ${sourceVisible ? "source-mode" : "visual-mode"}`}>
          {sourceVisible ? (
            <div className="source-area">
              <MiniCSourceLoaderView
                viewModel={viewModel}
                onOpenDocument={(name, source) => actions.addDocument(name, source, name)}
                onSaveDocument={(name, source) => actions.saveDocument(name, source)}
              />
            </div>
          ) : (
            <div className="main-content">
              <MiniCVisualPane inspector={hoverInspector} viewModel={viewModel} />
            </div>
          )}
        </div>
        <MiniCBottomPanel inspector={hoverInspector} viewModel={viewModel} />
      </section>
      <MiniCInspectorView playbackController={playbackController} viewModel={viewModel} />
    </main>
  );
}

function sourceMode(snapshot: MiniCWorkbenchSnapshot): boolean {
  const selectedStage = snapshot.selectedVisualStage;
  if (selectedStage === "source") {
    return true;
  }
  if (selectedStage.length > 0) {
    return false;
  }
  return !snapshot.sessionStarted || snapshot.currentState === null || snapshot.currentState.currentStage === "source";
}

function tabs(actions: ShellActions) {
  return (
    <div className="tabs" role="tablist">
      {actions.documents.map((document, index) => (
        <div
          className={`tab${index === actions.activeDocumentIndex ? " active" : ""}`}
          draggable
          key={`${document.order}-${document.name}`}
          onDragStart={(event) => event.dataTransfer.setData("text/plain", String(index))}
          onDrop={(event) => {
            event.preventDefault();
            actions.reorderDocumentTab(Number(event.dataTransfer.getData("text/plain")), index);
          }}
          onDragOver={(event) => event.preventDefault()}
          role="tab"
        >
          {actions.editingTabIndex === index ? (
            <input
              autoFocus
              className="tab-rename"
              onBlur={() => actions.commitRenameDocument(index, actions.editingTabName)}
              onChange={(event) => actions.setEditingTabName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  actions.commitRenameDocument(index, actions.editingTabName);
                }
                if (event.key === "Escape") {
                  actions.setEditingTabIndex(null);
                }
              }}
              value={actions.editingTabName}
            />
          ) : (
            <button
              className="tab-title"
              onClick={() => actions.switchDocument(index)}
              onDoubleClick={() => {
                actions.setEditingTabName(document.name);
                actions.setEditingTabIndex(index);
              }}
              type="button"
            >
              {document.name}
            </button>
          )}
          <button className="tab-close" onClick={() => actions.closeDocument(index)} type="button" aria-label="Close document">
            x
          </button>
        </div>
      ))}
      <button className="tab-action" onClick={actions.newDocument} type="button">
        +
      </button>
    </div>
  );
}

function settingsPage(controlHub: MiniCWorkbenchControlHub) {
  return (
    <main className="settings-scroll">
      <MiniCSettingsPane controlHub={controlHub} />
    </main>
  );
}

function statusBar(snapshot: MiniCWorkbenchSnapshot) {
  return (
    <footer className="status-bar">
      <span className="label">{snapshot.runtimeError ?? (snapshot.runtimePending ? "UIAPI 请求中" : "MiniC 可视化工作台")}</span>
      <span className="status-spacer" />
      <span className="label">C030 · 工作台</span>
    </footer>
  );
}

function useWorkbenchShellSnapshot(viewModel: MiniCWorkbenchViewModel): MiniCWorkbenchSnapshot {
  const [snapshot, setSnapshot] = useState(() => viewModel.snapshot());

  useEffect(() => {
    setSnapshot(viewModel.snapshot());
    return viewModel.subscribe(() => {
      setSnapshot(viewModel.snapshot());
    });
  }, [viewModel]);

  return snapshot;
}

function restorePersistedDocuments(): readonly DocumentTab[] {
  const sample = MiniCSamplePrograms.defaultSample();
  const fallback = [{ name: sample.name, path: null, order: 1, viewModel: createMiniCWorkbenchViewModel(sample.name, sample.source) }];
  try {
    const raw = window.localStorage.getItem(DOCUMENTS_STORAGE_KEY);
    if (!raw) {
      return fallback;
    }
    const parsed = JSON.parse(raw) as Array<{ name: string; path: string | null; order: number; source: string }>;
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return fallback;
    }
    return parsed.map((entry, index) => ({
      name: typeof entry.name === "string" && entry.name.length > 0 ? entry.name : nextUntitledName([]),
      path: typeof entry.path === "string" ? entry.path : null,
      order: Number.isFinite(entry.order) ? entry.order : index + 1,
      viewModel: createMiniCWorkbenchViewModel(entry.name, typeof entry.source === "string" ? entry.source : ""),
    }));
  } catch {
    return fallback;
  }
}

function persistOpenDocuments(documents: readonly DocumentTab[]): void {
  const payload = documents.map((document) => ({
    name: document.name,
    path: document.path,
    order: document.order,
    source: document.viewModel.sourceTextProperty().get(),
  }));
  window.localStorage.setItem(DOCUMENTS_STORAGE_KEY, JSON.stringify(payload));
}

function nextDocumentOrder(documents: readonly DocumentTab[]): number {
  return documents.reduce((max, document) => Math.max(max, document.order), 0) + 1;
}

function nextUntitledName(documents: readonly DocumentTab[]): string {
  const existing = new Set(documents.map((document) => document.name));
  let index = 1;
  while (existing.has(`untitled-${index}.mc`)) {
    index += 1;
  }
  return `untitled-${index}.mc`;
}

function renumberDocumentOrders(documents: readonly DocumentTab[]): readonly DocumentTab[] {
  return documents.map((document, index) => ({ ...document, order: index + 1 }));
}

function registerWorkbenchCommands(
  controlHub: MiniCWorkbenchControlHub,
  activeModelRef: MutableRefObject<MiniCWorkbenchViewModel>,
  activePlaybackControllerRef: MutableRefObject<MiniCPlaybackController>,
): void {
  const model = () => activeModelRef.current;
  const playbackController = () => activePlaybackControllerRef.current;
  controlHub.registerCompilerCommands({
    canNext: () => model().canNextControl(),
    next: () => runModelCommand(model(), model().next(), "下一步失败"),
    canNextStage: () => model().canNextStageControl(),
    nextStage: () => runModelCommand(model(), playbackController().nextStage(), "下一阶段失败"),
    canRunToExecution: () => model().canRunToExecutionControl(),
    runToExecution: () => runModelCommand(model(), model().runToExecution(), "到执行失败"),
    canPlay: () => model().canPlayControl(),
    play: () => playbackController().play(),
    canPlayFast: () => model().canPlayFastControl(),
    playFast: () => playbackController().playFast(),
    canPause: () => model().snapshot().currentState?.canPause ?? false,
    pause: () => playbackController().pause(),
  });
  controlHub.registerDebuggerCommands({
    canStart: () => true,
    start: () => runModelCommand(model(), model().startDebug(), "启动 Debug 失败"),
    canRunToEnd: () => model().snapshot().debugStarted,
    runToEnd: () => runModelCommand(model(), model().debugRunToEnd(), "运行到结束失败"),
    canRunToBreakpoint: () => model().snapshot().debugStarted,
    runToBreakpoint: () => runModelCommand(model(), model().debugRunToBreakpoint(), "运行到断点失败"),
    canStepOver: () => model().snapshot().debugStarted,
    stepOver: () => runModelCommand(model(), model().debugStepOver(), "本层下一句失败"),
    canStepInto: () => model().snapshot().debugStarted,
    stepInto: () => runModelCommand(model(), model().debugStepInto(), "下一句失败"),
    canBackToBreakpoint: () => model().snapshot().debugStarted,
    backToBreakpoint: () => runModelCommand(model(), model().debugBackToBreakpoint(), "上个断点失败"),
    canStepBackOver: () => model().snapshot().debugStarted,
    stepBackOver: () => runModelCommand(model(), model().debugStepBackOver(), "本层上一句失败"),
    canStepBack: () => model().snapshot().debugStarted,
    stepBack: () => runModelCommand(model(), model().debugStepBack(), "上一句失败"),
  });
  controlHub.registerSettingsCommands({
    themeSetter: ThemeManager.setTheme,
    themeNext: () => shiftTheme(1),
    themePrevious: () => shiftTheme(-1),
    frameIntervalSetter: MiniCSettings.setFrameIntervalMillis,
    currentFrameInterval: MiniCSettings.frameIntervalMillis,
    minFrameInterval: MiniCSettings.minFrameInterval,
    maxFrameInterval: MiniCSettings.maxFrameInterval,
    frameIntervalStep: 50,
    uiScaleSetter: (value) => MiniCSettings.setUiScale(roundUiScale(value)),
    currentUiScale: MiniCSettings.uiScale,
    minUiScale: MiniCSettings.minUiScale,
    maxUiScale: MiniCSettings.maxUiScale,
    uiScaleStep: 0.05,
  });
}

function runModelCommand(model: MiniCWorkbenchViewModel, promise: Promise<unknown>, label: string): void {
  model.runInBackground(promise, label);
}

function installShortcutDispatch(
  controlHub: MiniCWorkbenchControlHub,
  keyBindings: MiniCKeyBindingConfig,
  pressedKeys: MutableRefObject<Set<string>>,
): () => void {
  const handleKeyDown = (event: KeyboardEvent): void => {
    if (isKeyBindingCaptureTarget(event.target)) {
      return;
    }
    if (!isModifierKey(event.key)) {
      pressedKeys.current.add(event.key);
    }
    if (handleShortcut(controlHub, keyBindings, event, pressedKeys.current)) {
      event.preventDefault();
      event.stopPropagation();
    }
  };
  const handleKeyUp = (event: KeyboardEvent): void => {
    if (!isModifierKey(event.key)) {
      pressedKeys.current.delete(event.key);
    }
  };
  const handleMouseDown = (event: MouseEvent): void => {
    if (isKeyBindingCaptureTarget(event.target)) {
      return;
    }
    if (handleShortcut(controlHub, keyBindings, event, pressedKeys.current)) {
      event.preventDefault();
      event.stopPropagation();
    }
  };
  const handleWheel = (event: WheelEvent): void => {
    if (isKeyBindingCaptureTarget(event.target)) {
      return;
    }
    if (handleShortcut(controlHub, keyBindings, event, pressedKeys.current)) {
      event.preventDefault();
      event.stopPropagation();
    }
  };
  window.addEventListener("keydown", handleKeyDown, { capture: true });
  window.addEventListener("keyup", handleKeyUp, { capture: true });
  window.addEventListener("mousedown", handleMouseDown, { capture: true });
  window.addEventListener("wheel", handleWheel, { capture: true, passive: false });
  return () => {
    window.removeEventListener("keydown", handleKeyDown, { capture: true });
    window.removeEventListener("keyup", handleKeyUp, { capture: true });
    window.removeEventListener("mousedown", handleMouseDown, { capture: true });
    window.removeEventListener("wheel", handleWheel, { capture: true });
    pressedKeys.current.clear();
  };
}

function handleShortcut(
  controlHub: MiniCWorkbenchControlHub,
  keyBindings: MiniCKeyBindingConfig,
  event: MiniCInputEvent,
  pressedKeys: ReadonlySet<string>,
): boolean {
  return (
    handleCommandShortcut(controlHub, keyBindings, event, pressedKeys, COMPILER_SHORTCUT_ACTIONS)
    || handleCommandShortcut(controlHub, keyBindings, event, pressedKeys, DEBUG_SHORTCUT_ACTIONS)
    || handleCommandShortcut(controlHub, keyBindings, event, pressedKeys, SETTINGS_SHORTCUT_ACTIONS)
    || handleViewportShortcut(controlHub, keyBindings, event, pressedKeys)
  );
}

function handleCommandShortcut(
  controlHub: MiniCWorkbenchControlHub,
  keyBindings: MiniCKeyBindingConfig,
  event: MiniCInputEvent,
  pressedKeys: ReadonlySet<string>,
  actions: readonly string[],
): boolean {
  for (const action of actions) {
    if (keyBindings.matches(action, event, pressedKeys) && controlHub.execute(action)) {
      return true;
    }
  }
  return false;
}

function handleViewportShortcut(
  controlHub: MiniCWorkbenchControlHub,
  keyBindings: MiniCKeyBindingConfig,
  event: MiniCInputEvent,
  pressedKeys: ReadonlySet<string>,
): boolean {
  const point = event instanceof MouseEvent || event instanceof WheelEvent
    ? { x: event.offsetX, y: event.offsetY }
    : { x: 0, y: 0 };
  if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN, event, pressedKeys)) {
    controlHub.handleZoom(point, viewportZoomDelta(controlHub, 1.0));
    return true;
  }
  if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT, event, pressedKeys)) {
    controlHub.handleZoom(point, viewportZoomDelta(controlHub, -1.0));
    return true;
  }
  if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_UP, event, pressedKeys)) {
    controlHub.handleScrollVertical(-VIEWPORT_KEY_SCROLL_DELTA);
    return true;
  }
  if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, event, pressedKeys)) {
    controlHub.handleScrollVertical(VIEWPORT_KEY_SCROLL_DELTA);
    return true;
  }
  if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_LEFT, event, pressedKeys)) {
    controlHub.handleScrollHorizontal(-VIEWPORT_KEY_SCROLL_DELTA);
    return true;
  }
  if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_RIGHT, event, pressedKeys)) {
    controlHub.handleScrollHorizontal(VIEWPORT_KEY_SCROLL_DELTA);
    return true;
  }
  if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_CENTER_ACTIVE, event, pressedKeys)) {
    controlHub.handleCenterActive();
    return true;
  }
  return false;
}

function viewportZoomDelta(controlHub: MiniCWorkbenchControlHub, direction: number): number {
  const target = controlHub.viewportRegistry().currentTarget();
  return direction * (target?.type() === MiniCControlTargetType.TEXT ? TEXT_ZOOM_STEP : MiniCSettings.graphZoomStep());
}

function shiftTheme(delta: number): void {
  const themes = ThemeManager.availableThemes();
  if (themes.length === 0) {
    return;
  }
  const current = ThemeManager.currentTheme();
  const index = current === null ? -1 : themes.indexOf(current);
  const next = themes[floorMod(index + delta, themes.length)];
  if (next !== undefined) {
    ThemeManager.setTheme(next);
  }
}

function isModifierKey(key: string): boolean {
  return key === "Control" || key === "Ctrl" || key === "Alt" || key === "Shift" || key === "Meta";
}

function isKeyBindingCaptureTarget(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest(".key-binding-capturing") !== null;
}

function floorMod(value: number, divisor: number): number {
  return ((value % divisor) + divisor) % divisor;
}

function roundUiScale(value: number): number {
  return Math.round(value * 100) / 100;
}

export default MiniCWorkbenchShell;
