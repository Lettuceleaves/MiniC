import { useEffect, useMemo, useState } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { MiniCWorkbenchViewModel, MiniCWorkbenchSnapshot } from "./MiniCWorkbenchViewModel";
import { MiniCStageListFactory } from "./MiniCStageListFactory";
import { MiniCStageView } from "./MiniCStageView";

export const miniCSidebarViewMirror = {
  "javaPath": "src/main/java/minic/uilocal/workbench/MiniCSidebarView.java",
  "webPath": "uiweb/src/workbench/MiniCSidebarView.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCSidebarView",
  "kind": "component",
  "imports": [
    "java.util.List",
    "java.util.Objects",
    "javafx.scene.control.Label",
    "javafx.scene.layout.VBox"
  ],
  "fields": [
    {
      "name": "stageList",
      "signature": "private final VBox stageList="
    },
    {
      "name": "stageListFactory",
      "signature": "private final MiniCStageListFactory stageListFactory"
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel"
    }
  ],
  "methods": [
    {
      "name": "label",
      "signature": "label(String text,String styleClass)"
    },
    {
      "name": "refresh",
      "signature": "refresh()"
    },
    {
      "name": "stageCard",
      "signature": "stageCard(MiniCStageView stage)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCSidebarViewProps {
  readonly viewModel: MiniCWorkbenchViewModel;
}

export function MiniCSidebarView({ viewModel }: MiniCSidebarViewProps) {
  const snapshot = useWorkbenchSnapshot(viewModel);
  const factory = useMemo(() => new MiniCStageListFactory(), []);
  const stages = useMemo(
    () => factory.create(snapshot.currentState, snapshot.currentStageData, snapshot.globalData),
    [factory, snapshot.currentState, snapshot.currentStageData, snapshot.globalData],
  );

  return (
    <aside className="sidebar">
      <div className="stage-list">
        {stages.map((stage) => (
          <MiniCStageView
            key={stage.id}
            stage={stage}
            selected={stage.id === snapshot.selectedVisualStage}
            onSelect={(stageId) => void viewModel.selectVisualStage(stageId)}
          />
        ))}
      </div>
    </aside>
  );
}

function useWorkbenchSnapshot(viewModel: MiniCWorkbenchViewModel): MiniCWorkbenchSnapshot {
  const [snapshot, setSnapshot] = useState(() => viewModel.snapshot());

  useEffect(() => {
    setSnapshot(viewModel.snapshot());
    return viewModel.subscribe(() => {
      setSnapshot(viewModel.snapshot());
    });
  }, [viewModel]);

  return snapshot;
}

export default MiniCSidebarView;
