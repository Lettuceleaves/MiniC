import { MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";
import { defaultMiniCUiApiClient, type MiniCUiApiClient } from "./MiniCUiApiClient";
import { MiniCDebugHttpAdapter } from "./MiniCDebugHttpAdapter";
import { MiniCObservationHttpAdapter } from "./MiniCObservationHttpAdapter";
import { MiniCRealtimeAnalysisHttpAdapter } from "./MiniCRealtimeAnalysisHttpAdapter";

export function createMiniCWorkbenchViewModel(
  initialSourceName = "",
  initialSourceText = "",
  client: MiniCUiApiClient = defaultMiniCUiApiClient(),
): MiniCWorkbenchViewModel {
  return new MiniCWorkbenchViewModel(initialSourceName, initialSourceText, {
    observationApi: new MiniCObservationHttpAdapter(client),
    debugApi: new MiniCDebugHttpAdapter(client),
    realtimeAnalysisApi: new MiniCRealtimeAnalysisHttpAdapter(client),
  });
}

export default createMiniCWorkbenchViewModel;
