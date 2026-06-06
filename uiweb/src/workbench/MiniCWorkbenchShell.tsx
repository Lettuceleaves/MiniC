import { useEffect, useState } from "react";
import { createMiniCWorkbenchViewModel } from "../api/createMiniCWorkbenchViewModel";
import MiniCDebugPane from "../debug/MiniCDebugPane";
import MiniCInfoView from "../info/MiniCInfoView";
import MiniCBottomPanel from "../panel/MiniCBottomPanel";
import { MiniCHoverInspector } from "../panel/MiniCHoverInspector";
import MiniCInspectorView from "../panel/MiniCInspectorView";
import MiniCSettingsPane from "../settings/MiniCSettingsPane";
import MiniCSourceLoaderView from "../source/MiniCSourceLoaderView";
import type { JavaMirrorFile } from "../translation/javaMirror";
import MiniCVisualPane from "../visual/MiniCVisualPane";
import { MiniCSamplePrograms } from "../editor/MiniCSamplePrograms";
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

export function MiniCWorkbenchShell({ title = "MiniC Workbench" }: MiniCWorkbenchShellProps) {
  const [activeSection, setActiveSection] = useState<ActivitySectionId>("CODE");
  const [documents, setDocuments] = useState<readonly DocumentTab[]>(() => restorePersistedDocuments());
  const [activeDocumentIndex, setActiveDocumentIndex] = useState(0);
  const [editingTabIndex, setEditingTabIndex] = useState<number | null>(null);
  const [editingTabName, setEditingTabName] = useState("");
  const [hoverInspector] = useState(() => new MiniCHoverInspector());
  const activeDocument = documents[Math.min(activeDocumentIndex, Math.max(0, documents.length - 1))] ?? documents[0];
  const activeModel = activeDocument.viewModel;
  const activeSnapshot = useWorkbenchShellSnapshot(activeModel);

  useEffect(() => {
    persistOpenDocuments(documents);
  }, [documents]);

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
        void document.viewModel.renameSource(name);
        return { ...document, name };
      }),
    );
    setEditingTabIndex(null);
  };

  const saveDocument = (name = activeDocument.name, source = activeModel.sourceTextProperty().get()): void => {
    setDocuments((current) =>
      current.map((document, index) => (index === activeDocumentIndex ? { ...document, name } : document)),
    );
    void activeModel.renameSource(name);
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
      {sectionContent(activeSection, activeModel, activeSnapshot, hoverInspector, {
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
      {statusBar()}
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
  snapshot: MiniCWorkbenchSnapshot,
  hoverInspector: MiniCHoverInspector,
  actions: ShellActions,
) {
  switch (section) {
    case "CODE":
      return workbenchBody(viewModel, snapshot, hoverInspector, actions);
    case "DEBUG":
      return <MiniCDebugPane viewModel={viewModel} />;
    case "SETTINGS":
      return settingsPage();
    case "INFO":
      return <MiniCInfoView />;
  }
}

function workbenchBody(
  viewModel: MiniCWorkbenchViewModel,
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
      <MiniCInspectorView viewModel={viewModel} />
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

function settingsPage() {
  return (
    <main className="settings-scroll">
      <MiniCSettingsPane />
    </main>
  );
}

function statusBar() {
  return (
    <footer className="status-bar">
      <span className="label">MiniC 可视化工作台</span>
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

export default MiniCWorkbenchShell;
