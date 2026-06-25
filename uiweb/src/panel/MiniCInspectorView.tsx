import { useEffect, useMemo, useState } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";
import { MiniCInspectorModel } from "./MiniCInspectorModel";
import { MiniCInspectorModelFactory } from "./MiniCInspectorModelFactory";

export const miniCInspectorViewMirror = {
  "javaPath": "src/main/java/minic/uilocal/panel/MiniCInspectorView.java",
  "webPath": "uiweb/src/panel/MiniCInspectorView.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCInspectorView",
  "kind": "component",
  "imports": [
    "java.util.Objects",
    "javafx.scene.control.Label",
    "javafx.scene.layout.VBox",
    "javafx.scene.text.TextFlow",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "minic.uilocal.text.MiniCExplanationTextHighlighter",
    "minic.uilocal.text.MiniCTextFlowFactory"
  ],
  "fields": [
    {
      "name": "accumulatedOutput",
      "signature": "private final TextFlow accumulatedOutput="
    },
    {
      "name": "currentItem",
      "signature": "private final TextFlow currentItem="
    },
    {
      "name": "currentState",
      "signature": "private final TextFlow currentState="
    },
    {
      "name": "explanationTextHighlighter",
      "signature": "private final MiniCExplanationTextHighlighter explanationTextHighlighter="
    },
    {
      "name": "modelFactory",
      "signature": "private final MiniCInspectorModelFactory modelFactory="
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel"
    }
  ],
  "methods": [
    {
      "name": "body",
      "signature": "body(String text)"
    },
    {
      "name": "label",
      "signature": "label(String text,String styleClass)"
    },
    {
      "name": "refresh",
      "signature": "refresh()"
    },
    {
      "name": "setBody",
      "signature": "setBody(TextFlow target,String text)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCInspectorViewProps {
  readonly viewModel: MiniCWorkbenchViewModel;
}

export function MiniCInspectorView({ viewModel }: MiniCInspectorViewProps) {
  const snapshot = useInspectorSnapshot(viewModel);
  const modelFactory = useMemo(() => new MiniCInspectorModelFactory(), []);
  const model = snapshot.inspectorModel === null
    ? modelFactory.create(snapshot.currentState, snapshot.currentStageData, snapshot.globalData)
    : new MiniCInspectorModel(
      snapshot.inspectorModel.currentState,
      snapshot.inspectorModel.currentItem,
      snapshot.inspectorModel.accumulatedOutput,
    );

  return (
    <div className="inspector-metadata" data-java-source={miniCInspectorViewMirror.javaPath}>
      {label("MiniC 观测", "panel-title")}
      {label("当前状态", "section-label")}
      {body(model.currentState)}
      {label("当前项", "section-label")}
      {body(model.currentItem)}
      {label("累计输出", "section-label")}
      {body(model.accumulatedOutput)}
    </div>
  );
}

MiniCInspectorView.mirror = miniCInspectorViewMirror;

export function body(text: string) {
  return <pre className="body-text">{text}</pre>;
}

export function label(text: string, styleClass: string) {
  return <h2 className={styleClass}>{text}</h2>;
}

export function useInspectorSnapshot(viewModel: MiniCWorkbenchViewModel): MiniCWorkbenchSnapshot {
  const [snapshot, setSnapshot] = useState(() => viewModel.snapshot());

  useEffect(() => {
    setSnapshot(viewModel.snapshot());
    return viewModel.subscribe(() => {
      setSnapshot(viewModel.snapshot());
    });
  }, [viewModel]);

  return snapshot;
}

export default MiniCInspectorView;
