package craken.debug;

import craken.debug.DebugRuntime.Value;

import java.util.List;

/** 一个可由 IR 调试器解释的系统库函数。 */
@FunctionalInterface
interface DebugLibraryFunction {
    DebugLibraryCallResult invoke(DebugRuntime runtime, List<Value> arguments);
}
