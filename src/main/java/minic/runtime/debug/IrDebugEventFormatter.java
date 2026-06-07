package minic.runtime.debug;

import minic.compiler.ir.instruction.IrInstruction;
import minic.runtime.debug.dataflow.DataFlowEventType;

final class IrDebugEventFormatter {
    private IrDebugEventFormatter() {
    }

    static String title(String eventType) {
        return switch (eventType) {
            case "DECLARE_LOCAL" -> "声明局部变量";
            case "ADDRESS_OF_LOCAL" -> "取得局部变量地址";
            case "FIELD_ADDRESS" -> "计算结构体字段地址";
            case "ELEMENT_ADDRESS" -> "计算元素地址";
            case "STORE_LOCAL" -> "写入局部变量";
            case "LOAD_LOCAL" -> "读取局部变量";
            case "LOAD_POINTER" -> "通过指针读取";
            case "STORE_POINTER" -> "通过指针写入";
            case "MOVE" -> "移动临时值";
            case "CHECK_INITIALIZED" -> "检查局部变量初始化";
            case "CHECK_NON_ZERO" -> "检查除数非零";
            case "BINARY" -> "计算二元表达式";
            case "UNARY" -> "计算一元表达式";
            case "CAST" -> "转换值类型";
            case "BRANCH" -> "条件跳转";
            case "JUMP" -> "无条件跳转";
            case "CALL_EXTERNAL" -> "调用外部函数";
            case "RETURN" -> "函数返回";
            default -> eventType;
        };
    }

    static String valueSummary(DebugValue value) {
        return value == null ? "<uninitialized>" : value.summary();
    }

    static String dataFlowExpression(IrInstruction instruction, String lvaluePath, DataFlowEventType type) {
        String sourceText = instruction.range() == null ? "" : instruction.range().text().strip();
        if (sourceText.isBlank()) {
            return lvaluePath + " " + type.name().toLowerCase(java.util.Locale.ROOT);
        }
        String compactSource = sourceText.replaceAll("\\s+", " ");
        if (!mentionsPath(compactSource, lvaluePath)) {
            return compactSource + " -> " + lvaluePath;
        }
        return compactSource;
    }

    private static boolean mentionsPath(String sourceText, String lvaluePath) {
        if (lvaluePath.isBlank()) {
            return true;
        }
        if (sourceText.contains(lvaluePath)) {
            return true;
        }
        String leaf = lvaluePath;
        int dotIndex = Math.max(leaf.lastIndexOf('.'), leaf.lastIndexOf('>'));
        if (dotIndex >= 0 && dotIndex + 1 < leaf.length()) {
            leaf = leaf.substring(dotIndex + 1);
        }
        int bracketIndex = leaf.indexOf('[');
        if (bracketIndex > 0) {
            leaf = leaf.substring(0, bracketIndex);
        }
        return !leaf.isBlank() && sourceText.contains(leaf);
    }
}
