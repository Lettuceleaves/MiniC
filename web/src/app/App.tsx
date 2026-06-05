import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import * as Tooltip from "@radix-ui/react-tooltip";
import { Bug, Code2, PanelBottom, Play, Settings, StepForward } from "lucide-react";

import { AppRoutes } from "../routes";
import { useWorkbenchStore, type WorkbenchMode } from "./workbenchStore";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

const activityItems: Array<{ mode: WorkbenchMode; label: string; icon: typeof Code2 }> = [
  { mode: "compiler", label: "编译", icon: Code2 },
  { mode: "debugger", label: "调试", icon: Bug },
  { mode: "settings", label: "设置", icon: Settings },
];

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <Tooltip.Provider delayDuration={200}>
        <AppRoutes />
      </Tooltip.Provider>
    </QueryClientProvider>
  );
}

export function WorkbenchShell() {
  const activeMode = useWorkbenchStore((state) => state.activeMode);
  const setActiveMode = useWorkbenchStore((state) => state.setActiveMode);

  return (
    <div className="workbench-shell" data-testid="workbench-shell">
      <aside className="activity-bar" aria-label="MiniC 工作区">
        {activityItems.map((item) => {
          const Icon = item.icon;
          return (
            <Tooltip.Root key={item.mode}>
              <Tooltip.Trigger asChild>
                <button
                  className="activity-button"
                  data-active={activeMode === item.mode}
                  type="button"
                  aria-label={item.label}
                  onClick={() => {
                    setActiveMode(item.mode);
                  }}
                >
                  <Icon aria-hidden="true" size={20} strokeWidth={1.8} />
                </button>
              </Tooltip.Trigger>
              <Tooltip.Portal>
                <Tooltip.Content className="tooltip-content" side="right" sideOffset={8}>
                  {item.label}
                </Tooltip.Content>
              </Tooltip.Portal>
            </Tooltip.Root>
          );
        })}
      </aside>
      <main className="workbench-main">
        <section className="editor-region" aria-label="源码编辑区">
          <div className="panel-toolbar">
            <Code2 aria-hidden="true" size={16} />
            <span>main.mc</span>
          </div>
          <div className="editor-canvas">
            <span className="line-number">1</span>
            <code>int main() {" {"} return 0; {"}"}</code>
          </div>
        </section>
        <section className="visual-region" aria-label="观测视图">
          <div className="panel-toolbar">
            <PanelBottom aria-hidden="true" size={16} />
            <span>Workbench</span>
          </div>
          <div className="visual-placeholder">
            <button className="icon-command" type="button" aria-label="运行">
              <Play aria-hidden="true" size={17} />
            </button>
            <button className="icon-command" type="button" aria-label="下一步">
              <StepForward aria-hidden="true" size={17} />
            </button>
          </div>
        </section>
      </main>
      <footer className="status-bar">
        <span>MiniC</span>
        <span>{activeMode}</span>
      </footer>
    </div>
  );
}
