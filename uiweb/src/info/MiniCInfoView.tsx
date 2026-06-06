import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCGuideDocument } from "./MiniCGuideDocument";
import { MiniCMarkdownRenderer } from "./MiniCMarkdownRenderer";

export const miniCInfoViewMirror = {
  "javaPath": "src/main/java/minic/uilocal/info/MiniCInfoView.java",
  "webPath": "uiweb/src/info/MiniCInfoView.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCInfoView",
  "kind": "component",
  "imports": [
    "javafx.scene.control.ScrollPane",
    "javafx.scene.layout.VBox"
  ],
  "fields": [],
  "methods": []
} as const satisfies JavaMirrorFile;

export function MiniCInfoView() {
  return (
    <main className="info-scroll" data-java-source={miniCInfoViewMirror.javaPath}>
      <MiniCMarkdownRenderer markdown={MiniCGuideDocument.loadDefault()} />
    </main>
  );
}

MiniCInfoView.mirror = miniCInfoViewMirror;

export default MiniCInfoView;
