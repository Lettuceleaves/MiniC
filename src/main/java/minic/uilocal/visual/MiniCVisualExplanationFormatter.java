package minic.uilocal;

import minic.uiapi.ExplanationTemplates;
import minic.uiapi.UiAstNodeVisualDto;
import minic.uiapi.UiIrLineVisualDto;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiSourceSpanDto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

final class MiniCVisualExplanationFormatter {
    private final Function<UiSourceSpanDto, String> sourceSnippetProvider;

    MiniCVisualExplanationFormatter(Function<UiSourceSpanDto, String> sourceSnippetProvider) {
        this.sourceSnippetProvider = sourceSnippetProvider;
    }

    String explainToken(UiLexerTokenVisualDto token) {
        Map<String, String> variables = tokenVariables(token);
        String header = ExplanationTemplates.renderHeader("lexer", variables);
        String role = tokenRole(token.kind(), variables);
        String footer = ExplanationTemplates.renderFooter("lexer", variables);
        return header + "\n\n解释: " + role + "\n\n" + footer;
    }

    String explainAstNode(UiAstNodeVisualDto node) {
        Map<String, String> variables = astVariables(node);
        String role = ExplanationTemplates.render("parser", node.kind(), variables);
        String header = ExplanationTemplates.renderHeader("parser", variables);
        String footer = ExplanationTemplates.renderFooter("parser", variables);
        return header + "\n\n解释: " + role + "\n\n" + footer;
    }

    String explainIrLine(UiIrLineVisualDto line) {
        String text = line.text();
        String lower = text.toLowerCase(java.util.Locale.ROOT).trim();
        String roleKey;
        if (lower.contains("call")) {
            roleKey = "call";
        } else if (lower.contains("ret") || lower.startsWith("return")) {
            roleKey = "return";
        } else if (lower.contains("br") || lower.contains("jump")) {
            roleKey = "branch";
        } else if (lower.contains("cmp") || lower.contains("<") || lower.contains(">") || lower.contains("==")) {
            roleKey = "compare";
        } else if (lower.contains("store") || lower.contains("=")) {
            roleKey = "store";
        } else if (lower.contains("load")) {
            roleKey = "load";
        } else {
            roleKey = "default";
        }
        Map<String, String> variables = irVariables(line);
        String role = ExplanationTemplates.render("ir", roleKey, variables);
        String header = ExplanationTemplates.renderHeader("ir", variables);
        String footer = ExplanationTemplates.renderFooter("ir", variables);
        return header + "\n\n解释: " + role + "\n\n" + footer;
    }

    String explainAssemblyLine(MiniCAssemblyTextLine line) {
        String text = line.text().trim();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String roleKey;
        if (line.kind().equals("LABEL") || text.endsWith(":")) {
            roleKey = "label";
        } else if (lower.startsWith("mov")) {
            roleKey = "mov";
        } else if (lower.startsWith("add") || lower.startsWith("sub") || lower.startsWith("imul")) {
            roleKey = "arithmetic";
        } else if (lower.startsWith("cmp") || lower.startsWith("test")) {
            roleKey = "compare";
        } else if (lower.startsWith("j")) {
            roleKey = "jump";
        } else if (lower.startsWith("call")) {
            roleKey = "call";
        } else if (lower.startsWith("ret")) {
            roleKey = "ret";
        } else if (lower.startsWith("push") || lower.startsWith("pop")) {
            roleKey = "stack";
        } else {
            roleKey = "default";
        }
        Map<String, String> variables = assemblyVariables(line);
        String role = ExplanationTemplates.render("codegen", roleKey, variables);
        String header = ExplanationTemplates.renderHeader("codegen", variables);
        String footer = ExplanationTemplates.renderFooter("codegen", variables);
        return header + "\n\n解释: " + role + "\n\n" + footer;
    }

    String rangeLine(UiSourceSpanDto range) {
        if (range == null) {
            return "源码范围: 不可用";
        }
        return "源码范围: " + range.sourceName()
                + " " + range.startLine() + ":" + range.startColumn()
                + " - " + range.endLine() + ":" + range.endColumn()
                + " offset " + range.startOffset() + ".." + range.endOffset();
    }

    String blankValue(String value) {
        return value == null || value.isBlank() ? "<无>" : value;
    }

    String displayTokenText(UiLexerTokenVisualDto token) {
        return token.text().isEmpty() ? "<EOF>" : token.text();
    }

    String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private String tokenRole(String kind, Map<String, String> variables) {
        if (isTypeKeyword(kind)) {
            return ExplanationTemplates.render("lexer", "type_keyword", variables);
        }
        if (isControlKeyword(kind)) {
            return ExplanationTemplates.render("lexer", "control_keyword", variables);
        }
        if ("EXTERN".equals(kind)) {
            return ExplanationTemplates.render("lexer", "EXTERN", variables);
        }
        String key = switch (kind) {
            case "IDENTIFIER" -> "IDENTIFIER";
            case "INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL", "CHAR_LITERAL", "BOOL_LITERAL", "NULL_LITERAL" ->
                    "literal";
            case "STRING_LITERAL" -> "STRING_LITERAL";
            case "PLUS", "MINUS", "STAR", "SLASH", "PERCENT", "EQUAL", "PLUS_EQUAL", "MINUS_EQUAL", "PLUS_PLUS", "MINUS_MINUS",
                    "EQUAL_EQUAL", "BANG_EQUAL", "LESS", "LESS_EQUAL", "GREATER", "GREATER_EQUAL", "AMPERSAND", "BANG", "DOT" ->
                    "operator";
            case "LEFT_PAREN", "RIGHT_PAREN", "LEFT_BRACE", "RIGHT_BRACE", "LEFT_BRACKET", "RIGHT_BRACKET", "COMMA", "SEMICOLON" ->
                    "delimiter";
            case "EOF" -> "EOF";
            default -> "default";
        };
        return ExplanationTemplates.render("lexer", key, variables);
    }

    private boolean isTypeKeyword(String kind) {
        return switch (kind) {
            case "VOID", "BOOL", "CHAR", "INT", "LONG", "FLOAT", "DOUBLE", "STRUCT" -> true;
            default -> false;
        };
    }

    private boolean isControlKeyword(String kind) {
        return switch (kind) {
            case "RETURN", "IF", "ELSE", "WHILE", "FOR", "BREAK", "CONTINUE" -> true;
            default -> false;
        };
    }

    private Map<String, String> tokenVariables(UiLexerTokenVisualDto token) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("kind", token.kind());
        variables.put("text", displayTokenText(token));
        variables.put("source", sourceSnippetProvider.apply(token.range()));
        variables.put("range", rangeValue(token.range()));
        variables.put("startLine", String.valueOf(token.startLine()));
        variables.put("startColumn", String.valueOf(token.startColumn()));
        variables.put("endLine", String.valueOf(token.endLine()));
        variables.put("endColumn", String.valueOf(token.endColumn()));
        variables.put("startOffset", String.valueOf(token.startOffset()));
        variables.put("endOffset", String.valueOf(token.endOffset()));
        variables.put("active", yesNo(token.active()));
        return variables;
    }

    private Map<String, String> astVariables(UiAstNodeVisualDto node) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("kind", node.kind());
        variables.put("label", node.label());
        variables.put("id", node.id());
        variables.put("source", sourceSnippetProvider.apply(node.range()));
        variables.put("range", rangeValue(node.range()));
        variables.put("childCount", String.valueOf(node.children().size()));
        variables.put("active", yesNo(node.active()));
        return variables;
    }

    private Map<String, String> irVariables(UiIrLineVisualDto line) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("lineNumber", String.valueOf(line.lineNumber()));
        variables.put("text", line.text());
        variables.put("source", sourceSnippetProvider.apply(line.range()));
        variables.put("range", rangeValue(line.range()));
        variables.put("active", yesNo(line.active()));
        return variables;
    }

    private Map<String, String> assemblyVariables(MiniCAssemblyTextLine line) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("lineNumber", String.valueOf(line.lineNumber()));
        variables.put("text", line.text());
        variables.put("kind", line.kind());
        variables.put("section", blankValue(line.section()));
        variables.put("label", blankValue(line.label()));
        variables.put("source", sourceSnippetProvider.apply(line.range()));
        variables.put("range", rangeValue(line.range()));
        variables.put("active", yesNo(line.active()));
        return variables;
    }

    private String rangeValue(UiSourceSpanDto range) {
        if (range == null) {
            return "不可用";
        }
        return range.sourceName()
                + " " + range.startLine() + ":" + range.startColumn()
                + " - " + range.endLine() + ":" + range.endColumn()
                + " offset " + range.startOffset() + ".." + range.endOffset();
    }
}
