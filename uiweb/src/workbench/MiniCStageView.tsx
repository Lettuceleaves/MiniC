import type { MouseEventHandler } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCStageViewMirror = {
  "javaPath": "src/main/java/minic/uilocal/workbench/MiniCStageView.java",
  "webPath": "uiweb/src/workbench/MiniCStageView.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCStageView",
  "kind": "component",
  "imports": [
    "java.util.Objects"
  ],
  "fields": [],
  "methods": []
} as const satisfies JavaMirrorFile;

export type MiniCStageState = "queued" | "running" | "done" | "error";

export interface MiniCStageViewModel {
  readonly id: string;
  readonly title: string;
  readonly state: MiniCStageState;
  readonly detail: string;
  readonly progressPercent: number;
}

export interface MiniCStageViewProps {
  readonly stage: MiniCStageViewModel;
  readonly selected?: boolean;
  readonly onSelect?: (stageId: string) => void;
}

export function createMiniCStageView(
  id: string,
  title: string,
  state: MiniCStageState,
  detail: string,
  progressPercent: number,
): MiniCStageViewModel {
  if (progressPercent < 0 || progressPercent > 100) {
    throw new Error("progressPercent must be between 0 and 100");
  }
  return { id, title, state, detail, progressPercent };
}

export function MiniCStageView({ stage, selected = false, onSelect }: MiniCStageViewProps) {
  const handleClick: MouseEventHandler<HTMLButtonElement> = () => {
    onSelect?.(stage.id);
  };

  return (
    <button
      type="button"
      className={`stage-card ${stage.state}${selected ? " selected" : ""}`}
      onClick={handleClick}
      disabled={stage.id !== "source" && stage.state === "queued"}
      data-stage-id={stage.id}
    >
      <span className="stage-top">
        <span>{stage.title}</span>
        <span>{stage.state}</span>
      </span>
      <span className="stage-meta">
        {stage.progressPercent}% · {stage.detail}
      </span>
    </button>
  );
}

export default MiniCStageView;
