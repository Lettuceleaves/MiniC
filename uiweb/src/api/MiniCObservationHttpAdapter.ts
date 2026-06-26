import type {
  MiniCStageId,
  UiControlResultDto,
  UiCurrentStateDto,
  UiGlobalDataDto,
  UiInspectorModelDto,
  UiStageDataDto,
  UiStageViewDto,
  UiStageVisualDto,
} from "../translation/uiapi";
import {
  defaultMiniCUiApiClient,
  type MiniCUiApiClient,
  type MiniCUiApiSessionResponse,
  type MiniCUiApiStatusResponse,
} from "./MiniCUiApiClient";
import type { MiniCObservationApiAdapter } from "../workbench/MiniCWorkbenchViewModel";

export class MiniCObservationHttpAdapter implements MiniCObservationApiAdapter {
  private sessionIdPromise: Promise<string> | null = null;

  constructor(private readonly client: MiniCUiApiClient = defaultMiniCUiApiClient()) {
  }

  async loadSource(name: string, source: string): Promise<void> {
    await this.client.post<MiniCUiApiStatusResponse>(`${await this.basePath()}/source`, {
      sourceName: name,
      sourceText: source,
    });
  }

  async startSession(): Promise<void> {
    await this.client.post<UiCurrentStateDto>(`${await this.basePath()}/start`);
  }

  async next(): Promise<UiControlResultDto> {
    return this.client.post(`${await this.basePath()}/next`);
  }

  async nextStage(): Promise<UiControlResultDto> {
    return this.client.post(`${await this.basePath()}/next-stage`);
  }

  async runToExecution(): Promise<UiControlResultDto> {
    return this.client.post(`${await this.basePath()}/run-to-execution`);
  }

  async play(): Promise<UiControlResultDto> {
    return this.client.post(`${await this.basePath()}/play`);
  }

  async playFast(): Promise<UiControlResultDto> {
    return this.client.post(`${await this.basePath()}/play-fast`);
  }

  async tick(): Promise<UiControlResultDto> {
    return this.client.post(`${await this.basePath()}/tick`);
  }

  async pause(): Promise<UiControlResultDto> {
    return this.client.post(`${await this.basePath()}/pause`);
  }

  async confirmExecutionInput(standardInput: string): Promise<UiControlResultDto> {
    return this.client.post(`${await this.basePath()}/confirm-input`, { standardInput });
  }

  async previous(): Promise<UiControlResultDto> {
    return this.client.post(`${await this.basePath()}/previous`);
  }

  async reversePlay(): Promise<UiControlResultDto> {
    return this.client.post(`${await this.basePath()}/reverse-play`);
  }

  async currentState(): Promise<UiCurrentStateDto> {
    return this.client.get(`${await this.basePath()}/state`);
  }

  async currentStageData(): Promise<UiStageDataDto> {
    return this.client.get(`${await this.basePath()}/stage-data`);
  }

  async currentStageVisualData(): Promise<UiStageVisualDto> {
    return this.client.get(`${await this.basePath()}/visual/current`);
  }

  async lexerVisualData(): Promise<UiStageVisualDto> {
    return this.client.get(`${await this.basePath()}/visual/lexer`);
  }

  async astVisualData(): Promise<UiStageVisualDto> {
    return this.client.get(`${await this.basePath()}/visual/ast`);
  }

  async semanticVisualData(): Promise<UiStageVisualDto> {
    return this.client.get(`${await this.basePath()}/visual/semantic`);
  }

  async irVisualData(): Promise<UiStageVisualDto> {
    return this.client.get(`${await this.basePath()}/visual/ir`);
  }

  async codegenVisualData(): Promise<UiStageVisualDto> {
    return this.client.get(`${await this.basePath()}/visual/codegen`);
  }

  async globalData(): Promise<UiGlobalDataDto> {
    return this.client.get(`${await this.basePath()}/global`);
  }

  async stageViews(): Promise<readonly UiStageViewDto[]> {
    return this.client.get(`${await this.basePath()}/stage-views`);
  }

  async inspectorModel(): Promise<UiInspectorModelDto> {
    return this.client.get(`${await this.basePath()}/inspector`);
  }

  async visualDataFor(stage: MiniCStageId): Promise<UiStageVisualDto> {
    if (stage === "lexer") {
      return this.lexerVisualData();
    }
    if (stage === "parser") {
      return this.astVisualData();
    }
    if (stage === "semantic") {
      return this.semanticVisualData();
    }
    if (stage === "ir") {
      return this.irVisualData();
    }
    if (stage === "codegen" || stage === "toolchain" || stage === "execution") {
      return this.codegenVisualData();
    }
    return this.currentStageVisualData();
  }

  private async basePath(): Promise<string> {
    return `/api/observation/${await this.sessionId()}`;
  }

  private async sessionId(): Promise<string> {
    if (this.sessionIdPromise === null) {
      this.sessionIdPromise = this.client
        .post<MiniCUiApiSessionResponse>("/api/observation/sessions")
        .then((response) => response.sessionId);
    }
    return this.sessionIdPromise;
  }
}

export default MiniCObservationHttpAdapter;
