package craken.debug.visualization;

import java.util.Optional;

/**
 * Optional structure candidate discovery over an immutable VM snapshot. The adapter never runs a
 * recognizer automatically; the caller explicitly adopts and registers any returned descriptor.
 */
@FunctionalInterface
public interface DebugStructureRecognizer {
    Optional<DebugStructureDescriptor> recognize(DebugMemoryReader memory, DebugMemoryReader.Address candidate);
}
