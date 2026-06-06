package minic.uilocal.control;

import java.util.Objects;
import java.util.Optional;

public final class MiniCViewportRegistry {
    private MiniCViewportAdapter hoverTarget;
    private MiniCViewportAdapter pinnedTarget;
    private MiniCViewportAdapter businessActiveTarget;

    public void hover(MiniCViewportAdapter target) {
        if (isControllable(target)) {
            hoverTarget = target;
        }
    }

    public void clearHover(MiniCViewportAdapter target) {
        if (hoverTarget == target) {
            hoverTarget = null;
        }
    }

    public void pin(MiniCViewportAdapter target) {
        if (isControllable(target)) {
            pinnedTarget = target;
        }
    }

    public void clearPinned(MiniCViewportAdapter target) {
        if (pinnedTarget == target) {
            pinnedTarget = null;
        }
    }

    public void clearPinned() {
        pinnedTarget = null;
    }

    public void businessActive(MiniCViewportAdapter target) {
        if (isControllable(target)) {
            businessActiveTarget = target;
        }
    }

    public void clearBusinessActive(MiniCViewportAdapter target) {
        if (businessActiveTarget == target) {
            businessActiveTarget = null;
        }
    }

    public void clearBusinessActive() {
        businessActiveTarget = null;
    }

    public Optional<MiniCViewportAdapter> currentTarget() {
        if (hoverTarget != null) {
            return Optional.of(hoverTarget);
        }
        if (pinnedTarget != null) {
            return Optional.of(pinnedTarget);
        }
        return Optional.ofNullable(businessActiveTarget);
    }

    private static boolean isControllable(MiniCViewportAdapter target) {
        Objects.requireNonNull(target, "target");
        return target.type() != MiniCControlTargetType.NONE;
    }
}
