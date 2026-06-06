import type { UiLexerTokenVisualDto, UiRealtimeAnalysisDto } from "../translation/uiapi";
import { defaultMiniCUiApiClient, type MiniCUiApiClient } from "./MiniCUiApiClient";
import type { MiniCRealtimeAnalysisApiAdapter } from "../workbench/MiniCWorkbenchViewModel";

export class MiniCRealtimeAnalysisHttpAdapter implements MiniCRealtimeAnalysisApiAdapter {
  constructor(private readonly client: MiniCUiApiClient = defaultMiniCUiApiClient()) {
  }

  analyze(sourceName: string, sourceText: string, version: number): Promise<UiRealtimeAnalysisDto> {
    return this.client.post("/api/realtime/analyze", { sourceName, sourceText, version });
  }

  tokenize(sourceName: string, sourceText: string): Promise<readonly UiLexerTokenVisualDto[]> {
    return this.client.post("/api/realtime/tokenize", { sourceName, sourceText });
  }
}

export default MiniCRealtimeAnalysisHttpAdapter;
