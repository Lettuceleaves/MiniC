package craken.debug;

import craken.debug.DebugRuntime.Value;

import java.util.Objects;

/**
 * 一次调试系统库调用的结果。
 *
 * <p>系统库函数使用 {@link Returned} 或 {@link Terminated}；调用协议还预留了回调挂起、
 * 非局部跳转和结构化失败。后续接入 qsort、longjmp 等函数时不需要再次改变 Debugger
 * 与系统库之间的边界。</p>
 */
sealed interface DebugLibraryCallResult permits
        DebugLibraryCallResult.Returned,
        DebugLibraryCallResult.Suspended,
        DebugLibraryCallResult.Terminated,
        DebugLibraryCallResult.Jumped,
        DebugLibraryCallResult.Failed {

    /** 正常返回；void 函数的 value 为 null。 */
    record Returned(Value value) implements DebugLibraryCallResult {
    }

    /** 等待执行用户代码后再恢复的外部调用。 */
    record Suspended(String continuationId) implements DebugLibraryCallResult {
        public Suspended {
            Objects.requireNonNull(continuationId, "continuationId");
        }
    }

    /** 终止整个被调试程序。 */
    record Terminated(int status, String reason) implements DebugLibraryCallResult {
        public Terminated {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 请求执行非局部跳转。 */
    record Jumped(String target) implements DebugLibraryCallResult {
        public Jumped {
            Objects.requireNonNull(target, "target");
        }
    }

    /** 可向 Debugger 传播的稳定系统库错误。 */
    record Failed(String code, String message) implements DebugLibraryCallResult {
        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
