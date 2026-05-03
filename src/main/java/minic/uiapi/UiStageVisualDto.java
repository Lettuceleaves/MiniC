package minic.uiapi;

import minic.runtime.step.StageStepData;
import minic.compiler.ast.decl.Program;
import minic.compiler.lexer.Token;
import minic.compiler.semantic.Scope;
import minic.compiler.semantic.SemanticAction;
import minic.compiler.codegen.windows.WindowsX64AssemblyLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * UI 当前阶段图形化数据 DTO。
 *
 * @param stage 阶段 ID
 * @param visualType 图形类型
 * @param genericItems 通用 fallback 项
 * @param lexerTokens Lexer token 数据
 * @param astRoot AST 根节点；非 AST 阶段为 {@code null}
 * @param semanticRoot Semantic 根作用域；非 Semantic 阶段为 {@code null}
 * @param semanticEdgesPointChildToParent 作用域边是否表达 child -> parent
 * @param assemblyLines 汇编行数据
 */
public record UiStageVisualDto(
        String stage,
        String visualType,
        List<String> genericItems,
        List<UiLexerTokenVisualDto> lexerTokens,
        UiAstNodeVisualDto astRoot,
        UiSemanticScopeVisualDto semanticRoot,
        boolean semanticEdgesPointChildToParent,
        List<UiAssemblyLineVisualDto> assemblyLines
) {
    public UiStageVisualDto {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(visualType, "visualType");
        Objects.requireNonNull(genericItems, "genericItems");
        Objects.requireNonNull(lexerTokens, "lexerTokens");
        Objects.requireNonNull(assemblyLines, "assemblyLines");
        genericItems = List.copyOf(genericItems);
        lexerTokens = List.copyOf(lexerTokens);
        assemblyLines = List.copyOf(assemblyLines);
    }

    static UiStageVisualDto from(StageStepData data, UiCurrentStateDto state) {
        return switch (data.stage().id()) {
            case "lexer" -> lexerVisual(data, state);
            case "parser" -> parserVisual(data, state);
            case "semantic" -> semanticVisual(data, state);
            case "codegen" -> codegenVisual(data);
            default -> genericVisual(data);
        };
    }

    static UiStageVisualDto fromLexerTokens(StageStepData data, List<Token> sourceTokens, Token currentToken) {
        List<UiLexerTokenVisualDto> tokens = sourceTokens.stream()
                .map(token -> new UiLexerTokenVisualDto(
                        token.kind().name(),
                        token.lexeme(),
                        UiSourceSpanDto.from(token.range()),
                        token.equals(currentToken)
                ))
                .toList();
        return new UiStageVisualDto(data.stage().id(), "lexer", List.of(), tokens, null, null, false, List.of());
    }

    static UiStageVisualDto fromAst(StageStepData data, Program program, Object activeNode) {
        UiAstNodeVisualDto root = new UiAstVisualBuilder().buildProgram(program, activeNode);
        return new UiStageVisualDto(data.stage().id(), "ast", List.of(), List.of(), root, null, false, List.of());
    }

    static UiStageVisualDto fromAst(
            StageStepData data,
            Program program,
            Object activeNode,
            List<Object> visibleNodes
    ) {
        UiAstNodeVisualDto root = new UiAstVisualBuilder().buildProgram(program, activeNode, visibleNodes);
        return new UiStageVisualDto(data.stage().id(), "ast", List.of(), List.of(), root, null, false, List.of());
    }

    static UiStageVisualDto fromSemanticScope(StageStepData data, Scope globalScope, SemanticAction currentAction) {
        UiSemanticScopeVisualDto root = new UiSemanticScopeVisualBuilder().build(globalScope, currentAction);
        return new UiStageVisualDto(data.stage().id(), "semantic-scope", List.of(), List.of(), null, root, true, List.of());
    }

    static UiStageVisualDto fromAssemblyLines(
            StageStepData data,
            List<WindowsX64AssemblyLine> sourceLines,
            String currentSection
    ) {
        ArrayList<UiAssemblyLineVisualDto> lines = new ArrayList<>();
        int lineNumber = 1;
        for (WindowsX64AssemblyLine line : sourceLines) {
            lines.add(new UiAssemblyLineVisualDto(
                    lineNumber,
                    line.text(),
                    line.kind().name(),
                    currentSection,
                    line.subject(),
                    lineNumber == sourceLines.size()
            ));
            lineNumber++;
        }
        return new UiStageVisualDto(data.stage().id(), "assembly", List.of(), List.of(), null, null, false, lines);
    }

    private static UiStageVisualDto lexerVisual(StageStepData data, UiCurrentStateDto state) {
        List<UiLexerTokenVisualDto> tokens = new ArrayList<>();
        for (String item : data.accumulatedOutput()) {
            boolean active = item.equals(data.currentItem());
            tokens.add(tokenVisual(item, active, null));
        }
        if (tokens.stream().noneMatch(UiLexerTokenVisualDto::active) && !data.currentItem().isBlank()) {
            tokens.add(tokenVisual(data.currentItem(), true, null));
        }
        return new UiStageVisualDto(data.stage().id(), "lexer", List.of(), tokens, null, null, false, List.of());
    }

    private static UiLexerTokenVisualDto tokenVisual(String summary, boolean active, UiSourceSpanDto range) {
        int split = summary.indexOf(' ');
        String kind = split < 0 ? summary : summary.substring(0, split);
        String text = split < 0 ? "" : summary.substring(split + 1);
        return new UiLexerTokenVisualDto(kind, text, range, active);
    }

    private static UiStageVisualDto parserVisual(StageStepData data, UiCurrentStateDto state) {
        ArrayList<UiAstNodeVisualDto> children = new ArrayList<>();
        int index = 0;
        for (String item : data.accumulatedOutput()) {
            boolean active = item.equals(data.currentItem());
            children.add(new UiAstNodeVisualDto("ast-" + index, item, firstWord(item), null, active, List.of()));
            index++;
        }
        boolean rootActive = data.currentItem().isBlank() || children.stream().noneMatch(UiAstNodeVisualDto::active);
        UiAstNodeVisualDto root = new UiAstNodeVisualDto("ast-root", "Program", "Program", null, rootActive, children);
        return new UiStageVisualDto(data.stage().id(), "ast", List.of(), List.of(), root, null, false, List.of());
    }

    private static UiStageVisualDto semanticVisual(StageStepData data, UiCurrentStateDto state) {
        ArrayList<String> symbols = new ArrayList<>();
        ArrayList<UiSemanticScopeVisualDto> children = new ArrayList<>();
        int index = 0;
        for (String item : data.accumulatedOutput()) {
            if (item.startsWith("symbol ")) {
                symbols.add(item.substring("symbol ".length()));
            } else {
                boolean active = item.equals(data.currentItem());
                children.add(new UiSemanticScopeVisualDto("scope-" + index, item, List.of(), null, active, List.of()));
                index++;
            }
        }
        UiSemanticScopeVisualDto root = new UiSemanticScopeVisualDto(
                "scope-global",
                "global scope",
                symbols,
                null,
                !data.currentItem().isBlank(),
                children
        );
        return new UiStageVisualDto(data.stage().id(), "semantic-scope", List.of(), List.of(), null, root, true, List.of());
    }

    private static UiStageVisualDto codegenVisual(StageStepData data) {
        ArrayList<UiAssemblyLineVisualDto> lines = new ArrayList<>();
        int lineNumber = 1;
        for (String item : data.accumulatedOutput()) {
            boolean active = data.currentItem().startsWith(item);
            lines.add(new UiAssemblyLineVisualDto(lineNumber, item, firstWord(item), metadata(data.currentItem(), "section"), metadata(data.currentItem(), "label"), active));
            lineNumber++;
        }
        return new UiStageVisualDto(data.stage().id(), "assembly", List.of(), List.of(), null, null, false, lines);
    }

    private static UiStageVisualDto genericVisual(StageStepData data) {
        ArrayList<String> items = new ArrayList<>();
        if (!data.currentItem().isBlank()) {
            items.add(data.currentItem());
        }
        items.addAll(data.accumulatedOutput());
        return new UiStageVisualDto(data.stage().id(), "generic", items, List.of(), null, null, false, List.of());
    }

    private static String firstWord(String text) {
        int split = text.indexOf(' ');
        return split < 0 ? text : text.substring(0, split);
    }

    private static String metadata(String text, String key) {
        String prefix = key + "=";
        for (String part : text.split(" ")) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        return "";
    }
}
