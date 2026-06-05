import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";

import {
  createMiniCClient,
  type CommandInputRequest,
  type CreateSessionRequest,
  type MiniCClient,
} from "./minicClient";
import { queryKeys } from "./queryKeys";

const defaultClient = createMiniCClient();

type CompileSnapshotClient = Pick<MiniCClient, "getCompileSnapshot">;
type CreateCompileClient = Pick<MiniCClient, "createCompileSession">;
type CompileCommandClient = Pick<MiniCClient, "runCompileCommand">;

export type CompileCommandVariables = {
  command: string;
  input?: CommandInputRequest;
  sessionId: string;
};

export function invalidateCompileSession(queryClient: QueryClient, sessionId: string): void {
  void queryClient.invalidateQueries({ queryKey: queryKeys.compile.session(sessionId) });
}

export function useCompileSnapshot(
  sessionId: string | null | undefined,
  options: { client?: CompileSnapshotClient } = {},
) {
  const client = options.client ?? defaultClient;
  return useQuery({
    enabled: sessionId != null && sessionId.length > 0,
    queryFn: () => client.getCompileSnapshot(requireSessionId(sessionId)),
    queryKey: queryKeys.compile.snapshot(sessionId ?? ""),
  });
}

export function useCreateCompileSession(options: { client?: CreateCompileClient } = {}) {
  const client = options.client ?? defaultClient;
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateSessionRequest) => client.createCompileSession(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.compile.all });
    },
  });
}

export function useCompileCommand(options: { client?: CompileCommandClient } = {}) {
  const client = options.client ?? defaultClient;
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: CompileCommandVariables) =>
      client.runCompileCommand(variables.sessionId, variables.command, variables.input),
    onSuccess: (_result, variables) => {
      invalidateCompileSession(queryClient, variables.sessionId);
    },
  });
}

function requireSessionId(sessionId: string | null | undefined): string {
  if (sessionId == null || sessionId.length === 0) {
    throw new Error("sessionId is required");
  }
  return sessionId;
}
