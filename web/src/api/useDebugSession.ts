import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";

import { createMiniCClient, type CreateSessionRequest, type MiniCClient } from "./minicClient";
import { queryKeys } from "./queryKeys";

const defaultClient = createMiniCClient();

type DebugSnapshotClient = Pick<MiniCClient, "getDebugSnapshot">;
type CreateDebugClient = Pick<MiniCClient, "createDebugSession">;
type DebugCommandClient = Pick<MiniCClient, "runDebugCommand">;

export type DebugCommandVariables = {
  command: string;
  sessionId: string;
};

export function invalidateDebugSession(queryClient: QueryClient, sessionId: string): void {
  void queryClient.invalidateQueries({ queryKey: queryKeys.debug.session(sessionId) });
}

export function useDebugSnapshot(
  sessionId: string | null | undefined,
  options: { client?: DebugSnapshotClient } = {},
) {
  const client = options.client ?? defaultClient;
  return useQuery({
    enabled: sessionId != null && sessionId.length > 0,
    queryFn: () => client.getDebugSnapshot(requireSessionId(sessionId)),
    queryKey: queryKeys.debug.snapshot(sessionId ?? ""),
  });
}

export function useCreateDebugSession(options: { client?: CreateDebugClient } = {}) {
  const client = options.client ?? defaultClient;
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateSessionRequest) => client.createDebugSession(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.debug.all });
    },
  });
}

export function useDebugCommand(options: { client?: DebugCommandClient } = {}) {
  const client = options.client ?? defaultClient;
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: DebugCommandVariables) =>
      client.runDebugCommand(variables.sessionId, variables.command),
    onSuccess: (_result, variables) => {
      invalidateDebugSession(queryClient, variables.sessionId);
    },
  });
}

function requireSessionId(sessionId: string | null | undefined): string {
  if (sessionId == null || sessionId.length === 0) {
    throw new Error("sessionId is required");
  }
  return sessionId;
}
