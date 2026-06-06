import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCTextStyleRole } from "./MiniCTextStyleRole";
import type { MiniCTextStyleRole as MiniCTextStyleRoleValue } from "./MiniCTextStyleRole";

export const miniCStyledTextSegmentMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCStyledTextSegment.java",
  webPath: "uiweb/src/text/MiniCStyledTextSegment.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCStyledTextSegment",
  kind: "record",
  imports: ["java.util.Objects"],
  fields: [],
  methods: [{ name: "MiniCStyledTextSegment", signature: "MiniCStyledTextSegment(String text, MiniCTextStyleRole role)" }],
} as const satisfies JavaMirrorFile;

export class MiniCStyledTextSegment {
  static readonly mirror = miniCStyledTextSegmentMirror;

  readonly text: string;
  readonly role: MiniCTextStyleRoleValue;

  constructor(text: string, role: MiniCTextStyleRoleValue = MiniCTextStyleRole.CODE_PLAIN) {
    this.text = text;
    this.role = role;
  }
}

export function styledTextSegment(
  text: string,
  role: MiniCTextStyleRoleValue = MiniCTextStyleRole.CODE_PLAIN,
): MiniCStyledTextSegment {
  return new MiniCStyledTextSegment(text, role);
}

export default MiniCStyledTextSegment;
