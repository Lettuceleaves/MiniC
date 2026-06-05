import { useCallback } from "react";

import { useViewportStore, type ViewportTargetKind } from "./viewportStore";

export type ViewportTargetHandlers = {
  onBlur: () => void;
  onDoubleClick: () => void;
  onFocus: () => void;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
};

export function useViewportTarget(target: ViewportTargetKind): ViewportTargetHandlers {
  const setHoverTarget = useViewportStore((state) => state.setHoverTarget);
  const setPinnedTarget = useViewportStore((state) => state.setPinnedTarget);

  const activateHover = useCallback(() => {
    setHoverTarget(target);
  }, [setHoverTarget, target]);

  const clearHover = useCallback(() => {
    setHoverTarget(null);
  }, [setHoverTarget]);

  const pinTarget = useCallback(() => {
    setPinnedTarget(target);
  }, [setPinnedTarget, target]);

  return {
    onBlur: clearHover,
    onDoubleClick: pinTarget,
    onFocus: activateHover,
    onMouseEnter: activateHover,
    onMouseLeave: clearHover,
  };
}
