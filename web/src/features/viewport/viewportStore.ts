import { create } from "zustand";

export type ViewportTargetKind = "source" | "graph" | "ir" | "asm" | "data";
export type ViewportTargetPriority = "business" | "pinned" | "hover" | "none";

export type ViewportTargetInput = {
  business?: ViewportTargetKind | null;
  hover?: ViewportTargetKind | null;
  pinned?: ViewportTargetKind | null;
};

export type ViewportState = {
  businessTarget: ViewportTargetKind | null;
  hoverTarget: ViewportTargetKind | null;
  pinnedTarget: ViewportTargetKind | null;
  setBusinessTarget: (target: ViewportTargetKind | null) => void;
  setHoverTarget: (target: ViewportTargetKind | null) => void;
  setPinnedTarget: (target: ViewportTargetKind | null) => void;
};

export const useViewportStore = create<ViewportState>((set) => ({
  businessTarget: null,
  hoverTarget: null,
  pinnedTarget: null,
  setBusinessTarget: (businessTarget) => {
    set({ businessTarget });
  },
  setHoverTarget: (hoverTarget) => {
    set({ hoverTarget });
  },
  setPinnedTarget: (pinnedTarget) => {
    set({ pinnedTarget });
  },
}));

export function resolveTarget(input: ViewportTargetInput): ViewportTargetPriority {
  if (input.business != null) {
    return "business";
  }
  if (input.pinned != null) {
    return "pinned";
  }
  if (input.hover != null) {
    return "hover";
  }
  return "none";
}

export function resolveViewportTarget(input: ViewportTargetInput): ViewportTargetKind | null {
  if (input.business != null) {
    return input.business;
  }
  if (input.pinned != null) {
    return input.pinned;
  }
  return input.hover ?? null;
}

export function resetViewportStore(): void {
  useViewportStore.setState({
    businessTarget: null,
    hoverTarget: null,
    pinnedTarget: null,
  });
}
