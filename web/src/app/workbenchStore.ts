import { create } from "zustand";

export type WorkbenchMode = "compiler" | "debugger" | "settings";

type WorkbenchState = {
  activeMode: WorkbenchMode;
  setActiveMode: (mode: WorkbenchMode) => void;
};

export const useWorkbenchStore = create<WorkbenchState>((set) => ({
  activeMode: "compiler",
  setActiveMode: (activeMode) => {
    set({ activeMode });
  },
}));
