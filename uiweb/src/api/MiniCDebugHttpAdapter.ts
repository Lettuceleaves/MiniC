import type {
  UiDebugAsmViewDto,
  UiDebugAstViewDto,
  UiDebugDataStructureViewDto,
  UiDebugIrViewDto,
  UiDebugMetadataViewDto,
  UiDebugStateDto,
} from "../translation/uiapi";
import {
  defaultMiniCUiApiClient,
  type MiniCUiApiClient,
  type MiniCUiApiSessionResponse,
  type MiniCUiApiStatusResponse,
} from "./MiniCUiApiClient";
import type { MiniCDebugApiAdapter } from "../workbench/MiniCWorkbenchViewModel";

export class MiniCDebugHttpAdapter implements MiniCDebugApiAdapter {
  private sessionIdPromise: Promise<string> | null = null;

  constructor(private readonly client: MiniCUiApiClient = defaultMiniCUiApiClient()) {
  }

  async loadSource(name: string, source: string): Promise<void> {
    await this.client.post<MiniCUiApiStatusResponse>(`${await this.basePath()}/source`, {
      sourceName: name,
      sourceText: source,
    });
  }

  async startDebug(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/start`);
  }

  async setBreakpoint(line: number): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/breakpoints/${encodeURIComponent(String(line))}`);
  }

  async clearBreakpoint(line: number): Promise<UiDebugStateDto> {
    return this.client.delete(`${await this.basePath()}/breakpoints/${encodeURIComponent(String(line))}`);
  }

  async runToBreakpoint(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/run-to-breakpoint`);
  }

  async runToEnd(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/run-to-end`);
  }

  async fastForward(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/fast-forward`);
  }

  async stepOver(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/step-over`);
  }

  async stepInto(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/step-into`);
  }

  async stepOut(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/step-out`);
  }

  async pause(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/pause`);
  }

  async restart(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/restart`);
  }

  async close(): Promise<UiDebugStateDto> {
    const state = await this.client.post<UiDebugStateDto>(`${await this.basePath()}/close`);
    this.sessionIdPromise = null;
    return state;
  }

  async stepBack(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/step-back`);
  }

  async stepBackOver(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/step-back-over`);
  }

  async backToBreakpoint(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/back-to-breakpoint`);
  }

  async backToCallSite(): Promise<UiDebugStateDto> {
    return this.client.post(`${await this.basePath()}/back-to-call-site`);
  }

  async state(): Promise<UiDebugStateDto> {
    return this.client.get(`${await this.basePath()}/state`);
  }

  async metadataView(): Promise<UiDebugMetadataViewDto> {
    return this.client.get(`${await this.basePath()}/metadata`);
  }

  async dataStructureView(): Promise<UiDebugDataStructureViewDto> {
    return this.client.get(`${await this.basePath()}/data-structure`);
  }

  async astView(): Promise<UiDebugAstViewDto> {
    return this.client.get(`${await this.basePath()}/ast`);
  }

  async irView(): Promise<UiDebugIrViewDto> {
    return this.client.get(`${await this.basePath()}/ir`);
  }

  async asmView(): Promise<UiDebugAsmViewDto> {
    return this.client.get(`${await this.basePath()}/asm`);
  }

  private async basePath(): Promise<string> {
    return `/api/debug/${await this.sessionId()}`;
  }

  private async sessionId(): Promise<string> {
    if (this.sessionIdPromise === null) {
      this.sessionIdPromise = this.client
        .post<MiniCUiApiSessionResponse>("/api/debug/sessions")
        .then((response) => response.sessionId);
    }
    return this.sessionIdPromise;
  }
}

export default MiniCDebugHttpAdapter;
