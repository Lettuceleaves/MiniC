package minic.debug;

import minic.debug.DebugRuntime.Value;

import java.util.List;

/** 各系统库 provider 共用的参数校验。 */
final class DebugLibrarySupport {
    private DebugLibrarySupport() {
    }

    static int allocationSize(String function, long size) {
        if (size <= 0 || size > 16 * 1024 * 1024) {
            throw new IllegalStateException(function + " allocation size is invalid: " + size);
        }
        return Math.toIntExact(size);
    }

    static void requireCount(String function, List<Value> arguments, int count) {
        if (arguments.size() != count) {
            throw new IllegalStateException(function + " argument count: " + arguments.size());
        }
    }
}
