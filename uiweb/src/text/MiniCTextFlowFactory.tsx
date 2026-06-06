import type { CSSProperties, ReactElement } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { MiniCStyledTextSegment } from "./MiniCStyledTextSegment";
import { MiniCTextStyleState } from "./MiniCTextStyleState";
import type { MiniCTextStyleState as MiniCTextStyleStateValue } from "./MiniCTextStyleState";
import { MiniCTextStyles } from "./MiniCTextStyles";

export const miniCTextFlowFactoryMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCTextFlowFactory.java",
  webPath: "uiweb/src/text/MiniCTextFlowFactory.tsx",
  packageName: "minic.uilocal.text",
  exportName: "MiniCTextFlowFactory",
  kind: "component",
  imports: [
    "java.util.List",
    "javafx.scene.text.Text",
    "javafx.scene.text.TextFlow"
  ],
  fields: [],
  methods: [
    {
      "name": "textFlow",
      "signature": "textFlow(List<MiniCStyledTextSegment>segments,String flowStyleClass,boolean active)"
    }
  ],
} as const satisfies JavaMirrorFile;

export interface MiniCTextFlowFactoryProps {
  readonly segments: readonly MiniCStyledTextSegment[];
  readonly flowStyleClass?: string;
  readonly active?: boolean;
  readonly states?: readonly MiniCTextStyleStateValue[];
}

export function segmentStyle(segment: MiniCStyledTextSegment, states: readonly MiniCTextStyleStateValue[] = []): CSSProperties {
  const resolved = MiniCTextStyles.resolve(segment.role, ...states);
  return resolved.cssProperties as CSSProperties;
}

export function segmentClassName(segment: MiniCStyledTextSegment, states: readonly MiniCTextStyleStateValue[] = []): string {
  return MiniCTextStyles.classes(segment.role, ...states).join(" ");
}

export function textFlow(
  segments: readonly MiniCStyledTextSegment[],
  flowStyleClass = "text-flow",
  active = false,
  states: readonly MiniCTextStyleStateValue[] = [],
): ReactElement {
  const flowStates = active ? [MiniCTextStyleState.ACTIVE, ...states] : states;
  const className = [flowStyleClass, active ? "active" : ""].filter(Boolean).join(" ");
  return (
    <span className={className}>
      {segments.map((segment, index) => (
        <span
          className={segmentClassName(segment, flowStates)}
          key={`${index}:${segment.text}`}
          style={segmentStyle(segment, flowStates)}
        >
          {segment.text}
        </span>
      ))}
    </span>
  );
}

export function MiniCTextFlowFactory({
  segments,
  flowStyleClass = "text-flow",
  active = false,
  states = [],
}: MiniCTextFlowFactoryProps): ReactElement {
  return textFlow(segments, flowStyleClass, active, states);
}

MiniCTextFlowFactory.mirror = miniCTextFlowFactoryMirror;

export default MiniCTextFlowFactory;
