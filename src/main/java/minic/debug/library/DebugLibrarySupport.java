package minic.debug;

import minic.debug.DebugRuntime.Value;

import java.util.List;

/** 各系统库 provider 共用的参数校验。 */
final class DebugLibrarySupport {
    static final int ENOENT = 2;
    static final int EIO = 5;
    static final int ENOMEM = 12;
    static final int EACCES = 13;
    static final int EEXIST = 17;
    static final int EINVAL = 22;
    static final int EDOM = 33;
    static final int ERANGE = 34;

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
