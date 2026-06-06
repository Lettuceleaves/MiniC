import { MiniCSourceLineFactory } from "./MiniCSourceLineFactory";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiCurrentStateDto, UiSourceRangeDto } from "../translation/uiTypes";

export const miniCSourceViewMirror = {
  "javaPath": "src/main/java/minic/uilocal/source/MiniCSourceView.java",
  "webPath": "uiweb/src/source/MiniCSourceView.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCSourceView",
  "kind": "component",
  "imports": [
    "javafx.scene.control.Label",
    "javafx.scene.layout.GridPane",
    "javafx.scene.layout.Priority",
    "javafx.scene.layout.VBox",
    "minic.uiapi.UiCurrentStateDto",
    "minic.uiapi.UiSourceRangeDto",
    "java.util.List",
    "java.util.Objects"
  ],
  "fields": [
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel;"
    },
    {
      "name": "lineFactory",
      "signature": "private final MiniCSourceLineFactory lineFactory ="
    },
    {
      "name": "diagnosticSelection",
      "signature": "private final MiniCDiagnosticSelection diagnosticSelection;"
    },
    {
      "name": "header",
      "signature": "private final Label header ="
    },
    {
      "name": "lines",
      "signature": "private final GridPane lines ="
    }
  ],
  "methods": [
    {
      "name": "refresh",
      "signature": "refresh()"
    },
    {
      "name": "headerText",
      "signature": "headerText(UiSourceRangeDto range)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCSourceViewProps {
  readonly source?: string | null;
  readonly range?: UiSourceRangeDto | null;
  readonly selectedRange?: UiSourceRangeDto | null;
  readonly currentState?: UiCurrentStateDto | null;
  readonly title?: string;
  readonly lineFactory?: MiniCSourceLineFactory;
}

export function headerText(range: UiSourceRangeDto | null | undefined): string {
  if (!range) {
    return "源码";
  }
  return `源码 · 当前范围 ${range.startOffset}-${range.endOffset}`;
}

export function MiniCSourceView({
  source = "",
  range = null,
  selectedRange = null,
  currentState = null,
  title,
  lineFactory = new MiniCSourceLineFactory(),
}: MiniCSourceViewProps) {
  const activeRange = selectedRange ?? range ?? currentState?.sourceRange ?? null;
  const sourceLines = lineFactory.create(source, activeRange);
  return (
    <section className="pane source-view" data-java-source={miniCSourceViewMirror.javaPath}>
      <header className="pane-head">{title ?? headerText(activeRange)}</header>
      <div className="code-lines" role="table">
        {sourceLines.map((line) => (
          <div className={`source-line${line.focused ? " focus" : ""}`} key={line.lineNumber} role="row">
            <span className={`line-number${line.focused ? " focus" : ""}`} role="cell">
              {line.lineNumber}
            </span>
            <pre className={`line-text${line.focused ? " focus" : ""}`} role="cell">
              {line.text.length > 0 ? line.text : " "}
            </pre>
          </div>
        ))}
      </div>
    </section>
  );
}

MiniCSourceView.mirror = miniCSourceViewMirror;

export default MiniCSourceView;
