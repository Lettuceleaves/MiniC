package minic.compiler.preprocess;

import minic.compiler.SourceFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 处理条件编译指令并维护条件嵌套状态。
 */
final class ConditionalCompilationManager {
    private static final Pattern IFDEF_PATTERN = Pattern.compile("^\\s*#\\s*ifdef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern IFNDEF_PATTERN = Pattern.compile("^\\s*#\\s*ifndef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern ELSE_PATTERN = Pattern.compile("^\\s*#\\s*else\\s*$");
    private static final Pattern ENDIF_PATTERN = Pattern.compile("^\\s*#\\s*endif\\s*$");
    private static final Pattern IFDEF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*ifdef\\b.*$");
    private static final Pattern IFNDEF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*ifndef\\b.*$");
    private static final Pattern ELSE_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*else\\b.*$");
    private static final Pattern ENDIF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*endif\\b.*$");

    boolean handleDirective(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String line
    ) {
        Matcher ifdefMatcher = IFDEF_PATTERN.matcher(line);
        Matcher ifndefMatcher = IFNDEF_PATTERN.matcher(line);
        if (ifdefMatcher.matches()) {
            pushCondition(sourceFile, work, startOffset, endOffset, work.macros.containsKey(ifdefMatcher.group(1)));
            return true;
        }
        if (ifndefMatcher.matches()) {
            pushCondition(sourceFile, work, startOffset, endOffset, !work.macros.containsKey(ifndefMatcher.group(1)));
            return true;
        }
        if (ELSE_PATTERN.matcher(line).matches()) {
            switchElse(sourceFile, work, startOffset, endOffset);
            return true;
        }
        if (ENDIF_PATTERN.matcher(line).matches()) {
            popCondition(sourceFile, work, startOffset, endOffset);
            return true;
        }
        if (IFDEF_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, "ifdef 指令必须使用宏名称"));
            return true;
        }
        if (IFNDEF_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, "ifndef 指令必须使用宏名称"));
            return true;
        }
        if (ELSE_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, "else 指令不能带参数"));
            return true;
        }
        if (ENDIF_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, "endif 指令不能带参数"));
            return true;
        }
        return false;
    }

    boolean isActive(Preprocessor.Work work) {
        return work.conditionStack.stream()
                .allMatch(frame -> frame.parentActive() && frame.branchActive());
    }

    void closeUnterminatedConditions(Preprocessor.Work work, int initialDepth) {
        while (work.conditionStack.size() > initialDepth) {
            ConditionFrame frame = work.conditionStack.removeLast();
            work.diagnostics.add(Preprocessor.diagnostic(
                    frame.sourceFile(),
                    frame.startOffset(),
                    frame.endOffset(),
                    "条件编译块缺少 #endif"
            ));
        }
    }

    private void pushCondition(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            boolean conditionActive
    ) {
        boolean parentActive = isActive(work);
        work.conditionStack.add(new ConditionFrame(
                sourceFile,
                startOffset,
                endOffset,
                parentActive,
                conditionActive,
                false
        ));
    }

    private void switchElse(SourceFile sourceFile, Preprocessor.Work work, int startOffset, int endOffset) {
        if (work.conditionStack.isEmpty()) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, "孤立的 #else"));
            return;
        }
        ConditionFrame frame = work.conditionStack.removeLast();
        if (frame.elseSeen()) {
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    "同一条件编译块不能出现多个 #else"
            ));
            work.conditionStack.add(frame);
            return;
        }
        work.conditionStack.add(new ConditionFrame(
                frame.sourceFile(),
                frame.startOffset(),
                frame.endOffset(),
                frame.parentActive(),
                !frame.branchActive(),
                true
        ));
    }

    private void popCondition(SourceFile sourceFile, Preprocessor.Work work, int startOffset, int endOffset) {
        if (work.conditionStack.isEmpty()) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, "多余的 #endif"));
            return;
        }
        work.conditionStack.removeLast();
    }

    record ConditionFrame(
            SourceFile sourceFile,
            int startOffset,
            int endOffset,
            boolean parentActive,
            boolean branchActive,
            boolean elseSeen
    ) {
    }
}
