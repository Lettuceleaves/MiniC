import { afterEach, describe, expect, test } from "vitest";

import {
  resetViewportStore,
  resolveTarget,
  resolveViewportTarget,
  useViewportStore,
} from "./viewportStore";

describe("viewport target priority", () => {
  afterEach(() => {
    resetViewportStore();
  });

  test("resolves business, pinned, then hover priority", () => {
    expect(resolveTarget({ business: "graph", hover: "ir", pinned: "source" })).toBe("business");
    expect(resolveTarget({ hover: "source", pinned: "graph" })).toBe("pinned");
    expect(resolveTarget({ hover: "source" })).toBe("hover");
    expect(resolveViewportTarget({ business: "graph", hover: "ir", pinned: "source" })).toBe("graph");
  });

  test("business command targets do not clear a pinned graph viewport", () => {
    useViewportStore.getState().setPinnedTarget("graph");
    useViewportStore.getState().setBusinessTarget("source");

    expect(useViewportStore.getState().pinnedTarget).toBe("graph");
    expect(resolveTarget(inputFromStore())).toBe("business");

    useViewportStore.getState().setBusinessTarget(null);

    expect(resolveViewportTarget(inputFromStore())).toBe("graph");
  });
});

function inputFromStore() {
  const state = useViewportStore.getState();
  return {
    business: state.businessTarget,
    hover: state.hoverTarget,
    pinned: state.pinnedTarget,
  };
}
