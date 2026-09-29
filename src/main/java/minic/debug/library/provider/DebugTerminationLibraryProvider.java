package minic.debug;

import minic.debug.DebugLibraryCallResult.Terminated;
import minic.debug.DebugRuntime.Value;

import java.util.List;
import java.util.Map;

/** stdlib.h 中会立即终止被调试程序的函数。 */
final class DebugTerminationLibraryProvider implements DebugLibraryProvider {
    static final int ABORT_STATUS = 3;
    static final String ABORT_REASON = "abort";
    static final String EXIT_REASON = "exit";
    static final String IMMEDIATE_EXIT_REASON = "_Exit";

    private final Map<String, DebugLibraryFunction> functions = Map.of(
            "abort", this::abort,
            "exit", this::exit,
            "minic_assert_fail", this::assertFail,
            // _Exit 在 Windows CRT 中经由 stdlib.mh 的桥接名解析到 _exit。
            "minic_immediate_exit", this::immediateExit
    );

    @Override
    public String name() {
        return "termination";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private DebugLibraryCallResult abort(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("abort", arguments, 0);
        return new Terminated(ABORT_STATUS, ABORT_REASON);
    }

    private DebugLibraryCallResult assertFail(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("minic_assert_fail", arguments, 3);
        String expression = runtime.readCString(arguments.get(0).integer());
        String file = runtime.readCString(arguments.get(1).integer());
        long line = Integer.toUnsignedLong((int) arguments.get(2).integer());
        runtime.appendError("Assertion failed: " + expression + ", file " + file + ", line " + line + "\n");
        return new Terminated(ABORT_STATUS, "assert");
    }

    private DebugLibraryCallResult exit(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("exit", arguments, 1);
        return terminate(arguments, EXIT_REASON);
    }

    private DebugLibraryCallResult immediateExit(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("minic_immediate_exit", arguments, 2);
        return new Terminated((int) arguments.get(1).integer(), IMMEDIATE_EXIT_REASON);
    }

    private DebugLibraryCallResult terminate(List<Value> arguments, String reason) {
        return new Terminated((int) arguments.getFirst().integer(), reason);
    }
}
