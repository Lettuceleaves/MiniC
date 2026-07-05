package minic.runtime.debug.visual.layout;

import java.util.Objects;

/**
 * Resolved annotation root binding.
 *
 * @param variableName annotation variable name
 * @param rootAddress first assigned root memory reference
 */
public record VisualRootBinding(String variableName, String rootAddress) {
    public VisualRootBinding {
        Objects.requireNonNull(variableName, "variableName");
        Objects.requireNonNull(rootAddress, "rootAddress");
        if (variableName.isBlank()) {
            throw new IllegalArgumentException("variableName must not be blank");
        }
        if (rootAddress.isBlank()) {
            throw new IllegalArgumentException("rootAddress must not be blank");
        }
    }
}
