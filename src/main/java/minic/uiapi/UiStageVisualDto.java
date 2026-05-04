package minic.uiapi;

import minic.runtime.step.StageStepData;
import minic.compiler.ast.decl.Program;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.ir.lowering.IrLoweringAction;
import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCastInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrJumpInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrStorePointerInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrFloatConstant;
import minic.compiler.ir.value.IrFunctionAddress;
import minic.compiler.ir.value.IrParameterRef;
import minic.compiler.ir.value.IrStringLiteral;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.compiler.lexer.Token;
import minic.compiler.semantic.Scope;
import minic.compiler.semantic.SemanticAction;
import minic.compiler.codegen.windows.WindowsX64AssemblyLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
 * @param irLines IR 行数据
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
        List<UiIrLineVisualDto> irLines,
        List<UiAssemblyLineVisualDto> assemblyLines
) {
    public UiStageVisualDto {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(visualType, "visualType");
        Objects.requireNonNull(genericItems, "genericItems");
        Objects.requireNonNull(lexerTokens, "lexerTokens");
        Objects.requireNonNull(irLines, "irLines");
        Objects.requireNonNull(assemblyLines, "assemblyLines");
        genericItems = List.copyOf(genericItems);
        lexerTokens = List.copyOf(lexerTokens);
        irLines = List.copyOf(irLines);
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
        return new UiStageVisualDto(data.stage().id(), "lexer", List.of(), tokens, null, null, false, List.of(), List.of());
    }

    static UiStageVisualDto fromAst(StageStepData data, Program program, Object activeNode) {
        UiAstNodeVisualDto root = new UiAstVisualBuilder().buildProgram(program, activeNode);
        return new UiStageVisualDto(data.stage().id(), "ast", List.of(), List.of(), root, null, false, List.of(), List.of());
    }

    static UiStageVisualDto fromAst(
            StageStepData data,
            Program program,
            Object activeNode,
            List<Object> visibleNodes
    ) {
        UiAstNodeVisualDto root = new UiAstVisualBuilder().buildProgram(program, activeNode, visibleNodes);
        return new UiStageVisualDto(data.stage().id(), "ast", List.of(), List.of(), root, null, false, List.of(), List.of());
    }

    static UiStageVisualDto fromSemanticScope(StageStepData data, Scope globalScope, SemanticAction currentAction) {
        UiSemanticScopeVisualDto root = new UiSemanticScopeVisualBuilder().build(globalScope, currentAction);
        return new UiStageVisualDto(data.stage().id(), "semantic-scope", List.of(), List.of(), null, root, true, List.of(), List.of());
    }

    static UiStageVisualDto fromSemanticAstAndScope(
            StageStepData data,
            Program program,
            Scope globalScope,
            SemanticAction currentAction
    ) {
        Object activeAstNode = currentAction == null ? null : currentAction.astNode();
        UiAstNodeVisualDto astRoot = new UiAstVisualBuilder().buildProgram(program, activeAstNode);
        UiSemanticScopeVisualDto semanticRoot = new UiSemanticScopeVisualBuilder().build(globalScope, currentAction);
        return new UiStageVisualDto(data.stage().id(), "semantic-ast-scope", List.of(), List.of(), astRoot, semanticRoot, true, List.of(), List.of());
    }

    static UiStageVisualDto fromIrAstAndScope(
            StageStepData data,
            Program program,
            Scope globalScope,
            IrLoweringAction currentAction
    ) {
        Object activeAstNode = currentAction == null ? null : currentAction.astNode();
        UiAstNodeVisualDto astRoot = new UiAstVisualBuilder().buildProgram(program, activeAstNode);
        UiSemanticScopeVisualDto semanticRoot = new UiSemanticScopeVisualBuilder().build(globalScope, null);
        return new UiStageVisualDto(data.stage().id(), "ir-ast-scope", List.of(), List.of(), astRoot, semanticRoot, true, List.of(), List.of());
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
                    line.sourceRange() == null ? null : UiSourceSpanDto.from(line.sourceRange()),
                    lineNumber == sourceLines.size()
            ));
            lineNumber++;
        }
        return new UiStageVisualDto(data.stage().id(), "assembly", List.of(), List.of(), null, null, false, List.of(), lines);
    }

    static UiStageVisualDto fromCodegen(
            StageStepData data,
            IrModule module,
            List<WindowsX64AssemblyLine> sourceLines,
            String currentSection
    ) {
        UiStageVisualDto assembly = fromAssemblyLines(data, sourceLines, currentSection);
        UiSourceSpanDto activeRange = assembly.assemblyLines().stream()
                .filter(UiAssemblyLineVisualDto::active)
                .map(UiAssemblyLineVisualDto::range)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new UiStageVisualDto(
                assembly.stage(),
                assembly.visualType(),
                assembly.genericItems(),
                assembly.lexerTokens(),
                assembly.astRoot(),
                assembly.semanticRoot(),
                assembly.semanticEdgesPointChildToParent(),
                irLines(module, activeRange),
                assembly.assemblyLines()
        );
    }

    private static List<UiIrLineVisualDto> irLines(IrModule module, UiSourceSpanDto activeRange) {
        ArrayList<UiIrLineVisualDto> lines = new ArrayList<>();
        for (IrFunction function : module.functions()) {
            lines.add(new UiIrLineVisualDto(lines.size() + 1, "function " + function.name(), UiSourceSpanDto.from(function.range()), false));
            for (IrBlock block : function.blocks()) {
                lines.add(new UiIrLineVisualDto(lines.size() + 1, "  block " + block.label(), null, false));
                for (IrInstruction instruction : block.instructions()) {
                    UiSourceSpanDto range = UiSourceSpanDto.from(instruction.range());
                    lines.add(new UiIrLineVisualDto(
                            lines.size() + 1,
                            "    " + formatInstruction(instruction),
                            range,
                            sameRange(range, activeRange)
                    ));
                }
            }
        }
        return lines;
    }

    private static String formatInstruction(IrInstruction instruction) {
        if (instruction instanceof IrDeclareLocalInstruction declareLocal) {
            return "declare " + formatLocal(declareLocal.local());
        }
        if (instruction instanceof IrCheckInitializedInstruction checkInitialized) {
            return "check_initialized " + formatLocal(checkInitialized.local());
        }
        if (instruction instanceof IrAddressOfLocalInstruction addressOfLocal) {
            return formatValue(addressOfLocal.result()) + " = address_of " + formatLocal(addressOfLocal.local());
        }
        if (instruction instanceof IrLoadLocalInstruction loadLocal) {
            return formatValue(loadLocal.result()) + " = load " + formatLocal(loadLocal.local());
        }
        if (instruction instanceof IrStoreLocalInstruction storeLocal) {
            return "store " + formatValue(storeLocal.value()) + ", " + formatLocal(storeLocal.local());
        }
        if (instruction instanceof IrLoadPointerInstruction loadPointer) {
            return formatValue(loadPointer.result()) + " = load_ptr " + formatValue(loadPointer.address());
        }
        if (instruction instanceof IrStorePointerInstruction storePointer) {
            return "store_ptr " + formatValue(storePointer.value()) + ", " + formatValue(storePointer.address());
        }
        if (instruction instanceof IrElementAddressInstruction elementAddress) {
            return formatValue(elementAddress.result())
                    + " = element_address "
                    + formatValue(elementAddress.baseAddress())
                    + ", "
                    + formatValue(elementAddress.index())
                    + ", size "
                    + elementAddress.elementSizeBytes();
        }
        if (instruction instanceof IrFieldAddressInstruction fieldAddress) {
            return formatValue(fieldAddress.result())
                    + " = field_address "
                    + formatValue(fieldAddress.baseAddress())
                    + "."
                    + fieldAddress.fieldName()
                    + ", offset "
                    + fieldAddress.offset();
        }
        if (instruction instanceof IrBinaryInstruction binary) {
            return formatValue(binary.result())
                    + " = "
                    + binary.operator().name().toLowerCase()
                    + " "
                    + formatValue(binary.left())
                    + ", "
                    + formatValue(binary.right());
        }
        if (instruction instanceof IrCheckNonZeroInstruction checkNonZero) {
            return "check_nonzero " + formatValue(checkNonZero.value());
        }
        if (instruction instanceof IrCastInstruction cast) {
            return formatValue(cast.result()) + " = cast " + formatValue(cast.value()) + " to " + cast.result().type();
        }
        if (instruction instanceof IrCallInstruction call) {
            return formatValue(call.result()) + " = call " + call.calleeName() + "(" + formatValues(call.arguments()) + ")";
        }
        if (instruction instanceof IrIndirectCallInstruction call) {
            return formatValue(call.result()) + " = call* " + formatValue(call.calleeAddress()) + "(" + formatValues(call.arguments()) + ")";
        }
        if (instruction instanceof IrBranchInstruction branch) {
            return "branch "
                    + formatValue(branch.condition())
                    + ", "
                    + branch.thenLabel()
                    + ", "
                    + branch.elseLabel();
        }
        if (instruction instanceof IrJumpInstruction jump) {
            return "jump " + jump.targetLabel();
        }
        if (instruction instanceof IrReturnInstruction ret) {
            return "return " + formatValue(ret.value());
        }
        return instruction.getClass().getSimpleName();
    }

    private static String formatValues(List<IrValue> values) {
        return values.stream()
                .map(UiStageVisualDto::formatValue)
                .collect(Collectors.joining(", "));
    }

    private static String formatValue(IrValue value) {
        if (value instanceof IrTemporary temporary) {
            return temporary.name();
        }
        if (value instanceof IrParameterRef parameter) {
            return parameter.name();
        }
        if (value instanceof IrConstant constant) {
            return Long.toString(constant.value());
        }
        if (value instanceof IrFloatConstant constant) {
            return Double.toString(constant.value());
        }
        if (value instanceof IrStringLiteral stringLiteral) {
            return stringLiteral.label();
        }
        if (value instanceof IrFunctionAddress functionAddress) {
            return "&" + functionAddress.functionName();
        }
        return value.getClass().getSimpleName();
    }

    private static String formatLocal(IrLocal local) {
        if (local.name().equals(local.sourceName())) {
            return local.name();
        }
        return local.name() + "(" + local.sourceName() + ")";
    }

    private static boolean sameRange(UiSourceSpanDto left, UiSourceSpanDto right) {
        return left != null
                && right != null
                && left.sourceName().equals(right.sourceName())
                && left.startOffset() == right.startOffset()
                && left.endOffset() == right.endOffset();
    }

    static UiStageVisualDto fromAssemblySource(StageStepData data, AssemblySource assemblySource) {
        return fromAssemblySource(data, assemblySource, null);
    }

    static UiStageVisualDto fromAssemblySource(StageStepData data, AssemblySource assemblySource, IrModule module) {
        ArrayList<UiAssemblyLineVisualDto> lines = new ArrayList<>();
        int lineNumber = 1;
        for (String line : assemblySource.text().replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            if (!line.isEmpty()) {
                lines.add(new UiAssemblyLineVisualDto(lineNumber, line, "ASSEMBLY", "", assemblySource.entrySymbol(), false));
                lineNumber++;
            }
        }
        List<UiIrLineVisualDto> irLines = module == null ? List.of() : irLines(module, null);
        return new UiStageVisualDto("codegen", "assembly", List.of(), List.of(), null, null, false, irLines, lines);
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
        return new UiStageVisualDto(data.stage().id(), "lexer", List.of(), tokens, null, null, false, List.of(), List.of());
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
        return new UiStageVisualDto(data.stage().id(), "ast", List.of(), List.of(), root, null, false, List.of(), List.of());
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
        return new UiStageVisualDto(data.stage().id(), "semantic-scope", List.of(), List.of(), null, root, true, List.of(), List.of());
    }

    private static UiStageVisualDto codegenVisual(StageStepData data) {
        ArrayList<UiAssemblyLineVisualDto> lines = new ArrayList<>();
        int lineNumber = 1;
        for (String item : data.accumulatedOutput()) {
            boolean active = data.currentItem().startsWith(item);
            lines.add(new UiAssemblyLineVisualDto(lineNumber, item, firstWord(item), metadata(data.currentItem(), "section"), metadata(data.currentItem(), "label"), active));
            lineNumber++;
        }
        return new UiStageVisualDto(data.stage().id(), "assembly", List.of(), List.of(), null, null, false, List.of(), lines);
    }

    private static UiStageVisualDto genericVisual(StageStepData data) {
        ArrayList<String> items = new ArrayList<>();
        if (!data.currentItem().isBlank()) {
            items.add(data.currentItem());
        }
        items.addAll(data.accumulatedOutput());
        return new UiStageVisualDto(data.stage().id(), "generic", items, List.of(), null, null, false, List.of(), List.of());
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
