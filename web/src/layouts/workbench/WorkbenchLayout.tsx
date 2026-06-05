import * as Tabs from "@radix-ui/react-tabs";
import { Bug, Code2, PanelLeft, Settings } from "lucide-react";
import { useState } from "react";

import { createMiniCClient, type MiniCClient } from "../../api/minicClient";
import { CompilerWorkbench } from "../../features/compiler/CompilerWorkbench";
import { DebuggerWorkbench } from "../../features/debugger/DebuggerWorkbench";
import { SettingsPane } from "../../features/settings/SettingsPane";

type WorkbenchTab = "code" | "debug" | "settings";

const defaultClient = createMiniCClient();

export function WorkbenchLayout({ client = defaultClient }: { client?: MiniCClient }) {
  const [activeTab, setActiveTab] = useState<WorkbenchTab>("code");
  const [status, setStatus] = useState("Ready");

  return (
    <div className="workbench-shell" data-testid="workbench-shell">
      <aside className="activity-bar" aria-label="Activity bar">
        <button
          className="activity-button"
          data-active={activeTab === "code"}
          type="button"
          aria-label="Code"
          onClick={() => {
            setActiveTab("code");
          }}
        >
          <Code2 aria-hidden="true" size={20} />
        </button>
        <button
          className="activity-button"
          data-active={activeTab === "debug"}
          type="button"
          aria-label="Debug"
          onClick={() => {
            setActiveTab("debug");
          }}
        >
          <Bug aria-hidden="true" size={20} />
        </button>
        <button
          className="activity-button"
          data-active={activeTab === "settings"}
          type="button"
          aria-label="Settings"
          onClick={() => {
            setActiveTab("settings");
          }}
        >
          <Settings aria-hidden="true" size={20} />
        </button>
      </aside>
      <Tabs.Root
        className="workbench-tabs"
        value={activeTab}
        onValueChange={(value) => {
          setActiveTab(value as WorkbenchTab);
        }}
      >
        <header className="document-tabs">
          <PanelLeft aria-hidden="true" size={16} />
          <Tabs.List aria-label="Workbench views">
            <Tabs.Trigger value="code">Code</Tabs.Trigger>
            <Tabs.Trigger value="debug">Debug</Tabs.Trigger>
            <Tabs.Trigger value="settings">Settings</Tabs.Trigger>
          </Tabs.List>
        </header>
        <Tabs.Content className="workbench-content" value="code">
          <CompilerWorkbench client={client} onStatusChange={setStatus} />
        </Tabs.Content>
        <Tabs.Content className="workbench-content" value="debug">
          <DebuggerWorkbench client={client} onStatusChange={setStatus} />
        </Tabs.Content>
        <Tabs.Content className="workbench-content" value="settings">
          <SettingsPane client={client} onStatusChange={setStatus} />
        </Tabs.Content>
      </Tabs.Root>
      <footer className="status-bar">
        <span>MiniC Web</span>
        <span>{status}</span>
      </footer>
    </div>
  );
}
