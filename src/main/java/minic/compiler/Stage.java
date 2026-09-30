package minic.compiler;

import minic.SourceRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 编译流水线中的一个可逐步执行阶段。
 *
 * <p>具体阶段必须实现 {@link #step()} 与 {@link #canNext()}。每次 step 都必须通过
 * {@link #finishStep(SourceRange, String, List, Supplier)} 生成统一 Result。默认只保留
 * 阶段最后一步；打开逐步记录后才保留全部中间 Result 及其上下文快照。</p>
 */
public abstract class Stage {
    private final ArrayList<Result> recordedResults = new ArrayList<>();
    private final ArrayList<Diagnostic> errors = new ArrayList<>();
    private final Set<Diagnostic> observedErrors = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean resultRecordingEnabled;
    private long observedStepCount;
    private Result latestStepResult;
    private Result stageResult;

    /**
     * 执行当前阶段的一个最小步骤。
     *
     * @return 本步对应的源码范围；最终空步骤或没有源码对应关系时为 {@code null}
     */
    public abstract SourceRange step();

    /**
     * 返回当前阶段是否还能继续执行。
     */
    public abstract boolean canNext();

    /**
     * 返回阶段结束后是否允许流水线进入下一阶段。
     * 成功的唯一判定是：Stage 已经耗尽，且错误列表为空。
     */
    public final boolean succeeded() {
        return !canNext() && errors.isEmpty();
    }

    /**
     * 返回阶段当前结果。打开逐步记录时，每次 step 都会覆盖该值；
     * 关闭时只在阶段最后一步设置。
     */
    public final Optional<Result> stageResult() {
        return Optional.ofNullable(stageResult);
    }

    /** 返回阶段保留的步骤结果；记录关闭时，阶段结束后只包含最后一步。 */
    public final List<Result> stepResults() {
        if (resultRecordingEnabled) {
            return List.copyOf(recordedResults);
        }
        return stageResult == null ? List.of() : List.of(stageResult);
    }

    /** 返回当前阶段是否保留全部步骤结果。 */
    public final boolean resultRecordingEnabled() {
        return resultRecordingEnabled;
    }

    /** 返回本阶段按发生顺序收集的全部错误。 */
    public final List<Diagnostic> errors() {
        return List.copyOf(errors);
    }

    /**
     * 完成一次 step 并生成 Result。上下文只在打开记录或最后一步时创建。
     */
    protected final SourceRange finishStep(
            SourceRange sourceRange,
            String operation,
            List<Diagnostic> reportedErrors,
            Supplier<? extends Context> context
    ) {
        Objects.requireNonNull(reportedErrors, "reportedErrors");
        Objects.requireNonNull(context, "context");
        ArrayList<Diagnostic> stepErrors = new ArrayList<>();
        for (Diagnostic error : reportedErrors) {
            Objects.requireNonNull(error, "reportedErrors must not contain null");
            if (error.severity() == Diagnostic.Severity.ERROR && observedErrors.add(error)) {
                errors.add(error);
                stepErrors.add(error);
            }
        }
        boolean lastStep = !canNext();
        Context capturedContext = resultRecordingEnabled || lastStep ? context.get() : null;
        String error = describeErrors(lastStep ? errors : stepErrors);
        Result result = new Result(
                observedStepCount++,
                getClass(),
                sourceRange,
                operation,
                error,
                lastStep,
                lastStep && succeeded(),
                capturedContext
        );
        latestStepResult = result;
        if (resultRecordingEnabled) {
            recordedResults.add(result);
            stageResult = result;
        }
        if (!resultRecordingEnabled && lastStep) {
            stageResult = result;
        }
        return sourceRange;
    }

    private static String describeErrors(List<Diagnostic> errors) {
        return errors.stream()
                .map(Diagnostic::describe)
                .collect(Collectors.joining("\n\n"));
    }

    final Result consumeLatestStepResult() {
        if (latestStepResult == null) {
            throw new IllegalStateException(getClass().getSimpleName() + ".step() did not generate a Result");
        }
        Result result = latestStepResult;
        latestStepResult = null;
        return result;
    }

    final void setResultRecordingEnabled(boolean enabled) {
        if (resultRecordingEnabled == enabled) {
            return;
        }
        resultRecordingEnabled = enabled;
        recordedResults.clear();
        if (!enabled && stageResult != null && !stageResult.lastStep()) {
            stageResult = null;
        } else if (enabled && stageResult != null) {
            recordedResults.add(stageResult);
        }
    }

    final void resetResultObservation() {
        recordedResults.clear();
        observedStepCount = 0;
        latestStepResult = null;
        stageResult = null;
        errors.clear();
        observedErrors.clear();
    }

    /**
     * 一次 step 产生的不可变上下文。
     *
     * <p>中间步骤的 {@code error} 保存本步新增错误的原因和预期解决方法；
     * 最后一步作为阶段结果时，保存整个阶段收集的全部错误。
     * 没有错误时为空字符串。</p>
     */
    public record Result(
            long stepIndex,
            Class<? extends Stage> stageType,
            SourceRange sourceRange,
            String operation,
            String error,
            boolean lastStep,
            boolean succeeded,
            Context context
    ) {
        public Result {
            if (stepIndex < 0) {
                throw new IllegalArgumentException("stepIndex must not be negative");
            }
            Objects.requireNonNull(stageType, "stageType");
            operation = Objects.requireNonNullElse(operation, "");
            error = Objects.requireNonNullElse(error, "");
            if (!lastStep && succeeded) {
                throw new IllegalArgumentException("only the last step can be successful");
            }
        }

        public Optional<SourceRange> sourceRangeOptional() {
            return Optional.ofNullable(sourceRange);
        }

        public Optional<Context> contextOptional() {
            return Optional.ofNullable(context);
        }

        public boolean hasError() {
            return !error.isEmpty();
        }

        public Optional<String> errorOptional() {
            return error.isEmpty() ? Optional.empty() : Optional.of(error);
        }

        public <T extends Context> Optional<T> contextAs(Class<T> type) {
            Objects.requireNonNull(type, "type");
            return type.isInstance(context) ? Optional.of(type.cast(context)) : Optional.empty();
        }
    }

    /** 阶段可暴露给 UI 的结果上下文标记。 */
    public interface Context {
    }
}
