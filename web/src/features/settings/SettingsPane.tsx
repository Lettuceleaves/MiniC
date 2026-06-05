import { Save } from "lucide-react";
import { useEffect, useState } from "react";

import type { MiniCClient, SettingsSnapshot, ThemeListResponse } from "../../api/minicClient";

type SettingsClient = Pick<MiniCClient, "getSettings" | "listThemes" | "updateSettings">;

type SettingsPaneProps = {
  client: SettingsClient;
  onStatusChange?: (status: string) => void;
};

export function SettingsPane({ client, onStatusChange }: SettingsPaneProps) {
  const [settings, setSettings] = useState<SettingsSnapshot | null>(null);
  const [themes, setThemes] = useState<ThemeListResponse | null>(null);
  const [frameInterval, setFrameInterval] = useState("1000");
  const [uiScale, setUiScale] = useState("1");

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      const [nextSettings, nextThemes] = await Promise.all([client.getSettings(), client.listThemes()]);
      if (cancelled) {
        return;
      }
      setSettings(nextSettings);
      setThemes(nextThemes);
      setFrameInterval(String(nextSettings.frameIntervalMillis));
      setUiScale(String(nextSettings.uiScale));
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [client]);

  const save = async () => {
    const nextSettings = await client.updateSettings({
      frameIntervalMillis: Number(frameInterval),
      theme: settings?.theme ?? themes?.currentTheme ?? "dark",
      uiScale: Number(uiScale),
    });
    setSettings(nextSettings);
    onStatusChange?.("Settings saved");
  };

  return (
    <section className="settings-pane" aria-label="Settings panel">
      <div className="settings-grid">
        <label>
          <span>Theme</span>
          <select
            aria-label="Theme"
            value={settings?.theme ?? ""}
            onChange={(event) => {
              setSettings((current) => (
                current == null
                  ? current
                  : { ...current, theme: event.currentTarget.value }
              ));
            }}
          >
            {(themes?.themes ?? []).map((theme) => (
              <option key={theme} value={theme}>{theme}</option>
            ))}
          </select>
        </label>
        <label>
          <span>Frame interval</span>
          <input
            aria-label="Frame interval"
            type="number"
            value={frameInterval}
            onChange={(event) => {
              setFrameInterval(event.currentTarget.value);
            }}
          />
        </label>
        <label>
          <span>UI scale</span>
          <input
            aria-label="UI scale"
            step="0.05"
            type="number"
            value={uiScale}
            onChange={(event) => {
              setUiScale(event.currentTarget.value);
            }}
          />
        </label>
        <button className="command-button" type="button" onClick={() => void save()}>
          <Save aria-hidden="true" size={16} />
          <span>Save settings</span>
        </button>
      </div>
    </section>
  );
}
