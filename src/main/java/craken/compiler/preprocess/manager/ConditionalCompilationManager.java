package craken.compiler.preprocess;

import craken.compiler.SourceFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 处理条件编译指令并维护条件嵌套状态。
 */
final class ConditionalCompilationManager {
    private static final Pattern IFDEF_PATTERN = Pattern.compile("^\\s*#\\s*ifdef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern IFNDEF_PATTERN = Pattern.compile("^\\s*#\\s*ifndef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern IF_PATTERN = Pattern.compile("^\\s*#\\s*if\\s+(.+?)\\s*$");
    private static final Pattern ELIF_PATTERN = Pattern.compile("^\\s*#\\s*elif\\s+(.+?)\\s*$");
    private static final Pattern ELSE_PATTERN = Pattern.compile("^\\s*#\\s*else\\s*$");
    private static final Pattern ENDIF_PATTERN = Pattern.compile("^\\s*#\\s*endif\\s*$");
    private static final Pattern IFDEF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*ifdef\\b.*$");
    private static final Pattern IFNDEF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*ifndef\\b.*$");
    private static final Pattern IF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*if\\b.*$");
    private static final Pattern ELIF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*elif\\b.*$");
    private static final Pattern ELSE_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*else\\b.*$");
    private static final Pattern ENDIF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*endif\\b.*$");

    boolean handleDirective(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String line,
            TextReplacementManager textReplacementManager
    ) {
        Matcher ifdefMatcher = IFDEF_PATTERN.matcher(line);
        Matcher ifndefMatcher = IFNDEF_PATTERN.matcher(line);
        Matcher ifMatcher = IF_PATTERN.matcher(line);
        Matcher elifMatcher = ELIF_PATTERN.matcher(line);
        if (ifdefMatcher.matches()) {
            pushCondition(sourceFile, work, startOffset, endOffset, isDefined(work, ifdefMatcher.group(1)));
            return true;
        }
        if (ifndefMatcher.matches()) {
            pushCondition(sourceFile, work, startOffset, endOffset, !isDefined(work, ifndefMatcher.group(1)));
            return true;
        }
        if (ifMatcher.matches()) {
            boolean parentActive = isActive(work);
            pushCondition(sourceFile, work, startOffset, endOffset, parentActive && evaluate(
                    sourceFile,
                    work,
                    startOffset,
                    endOffset,
                    ifMatcher.group(1),
                    textReplacementManager
            ));
            return true;
        }
        if (elifMatcher.matches()) {
            switchElif(
                    sourceFile,
                    work,
                    startOffset,
                    endOffset,
                    elifMatcher.group(1),
                    textReplacementManager
            );
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
        if (IF_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, "if 指令缺少常量表达式"));
            return true;
        }
        if (ELIF_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, "elif 指令缺少常量表达式"));
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
                conditionActive,
                false
        ));
    }

    private void switchElif(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String text,
            TextReplacementManager textReplacementManager
    ) {
        if (work.conditionStack.isEmpty()) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, "孤立的 #elif"));
            return;
        }
        ConditionFrame frame = work.conditionStack.removeLast();
        if (frame.elseSeen()) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, "#else 后不能出现 #elif"));
            work.conditionStack.add(frame);
            return;
        }
        boolean active = !frame.branchTaken()
                && frame.parentActive()
                && evaluate(sourceFile, work, startOffset, endOffset, text, textReplacementManager);
        work.conditionStack.add(new ConditionFrame(frame.sourceFile(), frame.startOffset(), frame.endOffset(),
                frame.parentActive(), active, frame.branchTaken() || active, false));
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
                !frame.branchTaken(),
                true,
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

    private static boolean isDefined(Preprocessor.Work work, String name) {
        return TextReplacementManager.isPredefinedMacro(name) || work.macros.containsKey(name);
    }

    record ConditionFrame(
            SourceFile sourceFile,
            int startOffset,
            int endOffset,
            boolean parentActive,
            boolean branchActive,
            boolean branchTaken,
            boolean elseSeen
    ) {
    }

    private boolean evaluate(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String text,
            TextReplacementManager textReplacementManager
    ) {
        int diagnosticCount = work.diagnostics.size();
        String expanded = textReplacementManager.expandConditionExpression(text, work, sourceFile, startOffset);
        if (work.diagnostics.size() != diagnosticCount) {
            return false;
        }
        try {
            return new ConstantExpression(expanded, work, 0).parse() != 0;
        } catch (IllegalArgumentException exception) {
            work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset,
                    "条件编译表达式非法：" + exception.getMessage()));
            return false;
        }
    }

    /** Integer constant-expression evaluator used only by #if and #elif. */
    private static final class ConstantExpression {
        private final String text;
        private final Preprocessor.Work work;
        private final int depth;
        private int index;

        ConstantExpression(String text, Preprocessor.Work work, int depth) {
            this.text = text;
            this.work = work;
            this.depth = depth;
        }

        long parse() {
            if (depth > 64) throw error("宏展开层级过深");
            long value = conditional(true);
            whitespace();
            if (index != text.length()) throw error("无法识别的内容 " + text.substring(index));
            return value;
        }

        private long conditional(boolean eval) {
            long condition = logicalOr(eval);
            if (!take("?")) return condition;
            long yes = conditional(eval && condition != 0);
            require(":");
            long no = conditional(eval && condition == 0);
            return eval ? condition != 0 ? yes : no : 0;
        }

        private long logicalOr(boolean eval) {
            long value = logicalAnd(eval);
            while (take("||")) {
                long right = logicalAnd(eval && value == 0);
                if (eval) value = value != 0 || right != 0 ? 1 : 0;
            }
            return value;
        }

        private long logicalAnd(boolean eval) {
            long value = bitOr(eval);
            while (take("&&")) {
                long right = bitOr(eval && value != 0);
                if (eval) value = value != 0 && right != 0 ? 1 : 0;
            }
            return value;
        }

        private long bitOr(boolean eval) { long v = bitXor(eval); while (takeSingle('|')) { long r = bitXor(eval); if (eval) v |= r; } return v; }
        private long bitXor(boolean eval) { long v = bitAnd(eval); while (take("^")) { long r = bitAnd(eval); if (eval) v ^= r; } return v; }
        private long bitAnd(boolean eval) { long v = equality(eval); while (takeSingle('&')) { long r = equality(eval); if (eval) v &= r; } return v; }
        private long equality(boolean eval) { long v = relational(eval); while (true) { if (take("==")) { long r=relational(eval); if(eval)v=v==r?1:0; } else if(take("!=")){long r=relational(eval);if(eval)v=v!=r?1:0;} else return v; } }
        private long relational(boolean eval) { long v=shift(eval); while(true){ if(take("<=")){long r=shift(eval);if(eval)v=v<=r?1:0;}else if(take(">=")){long r=shift(eval);if(eval)v=v>=r?1:0;}else if(take("<")){long r=shift(eval);if(eval)v=v<r?1:0;}else if(take(">")){long r=shift(eval);if(eval)v=v>r?1:0;}else return v;} }
        private long shift(boolean eval) { long v=add(eval); while(true){if(take("<<")){long r=add(eval);if(eval)v<<=r;}else if(take(">>")){long r=add(eval);if(eval)v>>=r;}else return v;} }
        private long add(boolean eval) { long v=multiply(eval); while(true){if(take("+")){long r=multiply(eval);if(eval)v+=r;}else if(take("-")){long r=multiply(eval);if(eval)v-=r;}else return v;} }
        private long multiply(boolean eval) { long v=unary(eval); while(true){if(take("*")){long r=unary(eval);if(eval)v*=r;}else if(take("/")){long r=unary(eval);if(eval){if(r==0)throw error("除数为零");v/=r;}}else if(take("%")){long r=unary(eval);if(eval){if(r==0)throw error("除数为零");v%=r;}}else return v;} }

        private long unary(boolean eval) {
            if (take("!")) {
                long operand = unary(eval);
                return eval && operand == 0 ? 1 : 0;
            }
            if (take("~")) return eval ? ~unary(true) : unary(false);
            if (take("+")) return unary(eval);
            if (take("-")) return eval ? -unary(true) : unary(false);
            int save = index;
            String name = identifier();
            if ("defined".equals(name)) {
                boolean paren = take("(");
                String target = identifier();
                if (target == null) throw error("defined 后缺少宏名称");
                if (paren) require(")");
                return eval && isDefined(work, target) ? 1 : 0;
            }
            index = save;
            return primary(eval);
        }

        private long primary(boolean eval) {
            if (take("(")) { long value=conditional(eval); require(")"); return value; }
            whitespace();
            if (index < text.length() && Character.isDigit(text.charAt(index))) return number(eval);
            String name = identifier();
            if (name != null) {
                TextReplacementManager.MacroDefinition macro = work.macros.get(name);
                if (!eval || macro == null || macro.functionLike() || macro.replacement().isBlank()) return 0;
                return new ConstantExpression(macro.replacement(), work, depth + 1).parse();
            }
            throw error("期望整数、宏名称或括号表达式");
        }

        private long number(boolean eval) {
            int start=index;
            while(index<text.length() && (Character.isLetterOrDigit(text.charAt(index)) || text.charAt(index)=='x' || text.charAt(index)=='X')) index++;
            String raw=text.substring(start,index).replaceFirst("(?i)(u|l)+$", "");
            int radix=10; String digits=raw;
            if(raw.startsWith("0x")||raw.startsWith("0X")){radix=16;digits=raw.substring(2);}
            else if(raw.startsWith("0b")||raw.startsWith("0B")){radix=2;digits=raw.substring(2);}
            else if(raw.length()>1&&raw.startsWith("0")){radix=8;digits=raw.substring(1);}
            try { return eval ? Long.parseUnsignedLong(digits.isEmpty()?"0":digits, radix) : 0; }
            catch(NumberFormatException exception){throw error("无效整数 " + raw);}
        }

        private String identifier() {
            whitespace();
            if(index>=text.length() || !(text.charAt(index)=='_' || Character.isLetter(text.charAt(index)))) return null;
            int start=index++;
            while(index<text.length() && (text.charAt(index)=='_' || Character.isLetterOrDigit(text.charAt(index)))) index++;
            return text.substring(start,index);
        }

        private boolean takeSingle(char operator) {
            whitespace();
            if(index>=text.length() || text.charAt(index)!=operator) return false;
            if(index+1<text.length() && text.charAt(index+1)==operator) return false;
            index++; return true;
        }
        private boolean take(String token) { whitespace(); if(!text.startsWith(token,index))return false; index+=token.length(); return true; }
        private void require(String token) { if(!take(token))throw error("期望 " + token); }
        private void whitespace(){while(index<text.length()&&Character.isWhitespace(text.charAt(index)))index++;}
        private IllegalArgumentException error(String message){return new IllegalArgumentException(message);}
    }
}
