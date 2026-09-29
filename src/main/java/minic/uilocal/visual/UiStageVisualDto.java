package minic.uilocal;

import minic.compiler.SourceFile;
import minic.session.Observation.StageData;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.asm.AsmResult;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.instruction.MemoryInstruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrBranchInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrCastInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrJumpInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.value.IrValue.IrConstant;
import minic.compiler.ir.value.IrValue.IrFloatConstant;
import minic.compiler.ir.value.IrValue.IrFunctionAddress;
import minic.compiler.ir.value.IrValue.IrParameterRef;
import minic.compiler.ir.value.IrValue.IrStringLiteral;
import minic.compiler.ir.value.IrValue.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.compiler.lexer.token.Token;
import minic.compiler.semantic.model.Scope;
import minic.compiler.semantic.model.SemanticAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * UI 当前阶段图形化数据 DTO。
 *
 * @param stage 阶段 ID
 * @param visualType 图形类型
 * @param sourceText 当前可视化关联的源码文本
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
        String sourceText,
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
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(genericItems, "genericItems");
        Objects.requireNonNull(lexerTokens, "lexerTokens");
        Objects.requireNonNull(irLines, "irLines");
        Objects.requireNonNull(assemblyLines, "assemblyLines");
        genericItems = List.copyOf(genericItems);
        lexerTokens = List.copyOf(lexerTokens);
        irLines = List.copyOf(irLines);
        assemblyLines = List.copyOf(assemblyLines);
    }

    static UiStageVisualDto from(StageData data, UiCurrentStateDto state) {
        return switch (data.stage().id()) {
            case "lexer" -> lexerVisual(data, state);
            case "parser" -> parserVisual(data, state);
            case "semantic" -> semanticVisual(data, state);
            case "asm" -> asmVisual(data);
            default -> genericVisual(data);
        };
    }

    static UiStageVisualDto fromLexerTokens(
            StageData data,
            SourceFile sourceFile,
            List<Token> sourceTokens,
            Token currentToken
    ) {
        List<UiLexerTokenVisualDto> tokens = sourceTokens.stream()
                .map(token -> new UiLexerTokenVisualDto(
                        token.type().name(),
                        token.lexeme(),
                        UiSourceSpanDto.from(sourceFile, token.range()),
                        token.equals(currentToken)
                ))
                .toList();
        return new UiStageVisualDto(data.stage().id(), "lexer", sourceFile.content(), List.of(), tokens, null, null, false, List.of(), List.of());
    }

    static UiStageVisualDto fromAst(SourceFile sourceFile, StageData data, Program program, Object activeNode) {
        UiAstNodeVisualDto root = new UiAstVisualBuilder(sourceFile).buildProgram(program, activeNode);
        return new UiStageVisualDto(data.stage().id(), "ast", sourceFile.content(), List.of(), List.of(), root, null, false, List.of(), List.of());
    }

    static UiStageVisualDto fromAst(
            SourceFile sourceFile,
            StageData data,
            Program program,
            Object activeNode,
            List<Object> visibleNodes
    ) {
        UiAstNodeVisualDto root = new UiAstVisualBuilder(sourceFile).buildProgram(program, activeNode, visibleNodes);
        return new UiStageVisualDto(data.stage().id(), "ast", sourceFile.content(), List.of(), List.of(), root, null, false, List.of(), List.of());
    }

    static UiStageVisualDto fromSemanticScope(
            SourceFile sourceFile,
            StageData data,
            Scope globalScope,
            SemanticAction currentAction
    ) {
        UiSemanticScopeVisualDto root = new UiSemanticScopeVisualBuilder(sourceFile).build(globalScope, currentAction);
        return new UiStageVisualDto(data.stage().id(), "semantic-scope", "", List.of(), List.of(), null, root, true, List.of(), List.of());
    }

    static UiStageVisualDto fromSemanticAstAndScope(
            SourceFile sourceFile,
            StageData data,
            Program program,
            Scope globalScope,
            SemanticAction currentAction
    ) {
        Object activeAstNode = currentAction == null ? null : currentAction.astNode();
        UiAstNodeVisualDto astRoot = new UiAstVisualBuilder(sourceFile).buildProgram(program, activeAstNode);
        UiSemanticScopeVisualDto semanticRoot = new UiSemanticScopeVisualBuilder(sourceFile).build(globalScope, currentAction);
        return new UiStageVisualDto(data.stage().id(), "semantic-ast-scope", sourceFile.content(), List.of(), List.of(), astRoot, semanticRoot, true, List.of(), List.of());
    }

    static UiStageVisualDto fromIrAstAndScope(
            SourceFile sourceFile,
            StageData data,
            Program program,
            Scope globalScope,
            Object activeAstNode,
            IrResult irResult
    ) {
        UiAstNodeVisualDto astRoot = new UiAstVisualBuilder(sourceFile).buildProgram(program, activeAstNode);
        UiSemanticScopeVisualDto semanticRoot = new UiSemanticScopeVisualBuilder(sourceFile).build(globalScope, null);
        List<UiIrLineVisualDto> irLines = irResult == null ? List.of() : irLines(sourceFile, irResult, null);
        return new UiStageVisualDto(data.stage().id(), "ir-ast-scope", sourceFile.content(), List.of(), List.of(), astRoot, semanticRoot, true, irLines, List.of());
    }

    static UiStageVisualDto fromAssemblyLines(
            SourceFile sourceFile,
            StageData data,
            Assembler.Work work
    ) {
        ArrayList<UiAssemblyLineVisualDto> lines = new ArrayList<>();
        List<String> sourceLines = work.assemblyLines();
        int lineNumber = 1;
        for (int index = 0; index < sourceLines.size(); index++) {
            lines.add(new UiAssemblyLineVisualDto(
                    lineNumber,
                    sourceLines.get(index),
                    "ASM_LINE",
                    work.currentSection(),
                    "",
                    work.sourceRangeAt(index).map(range -> UiSourceSpanDto.from(sourceFile, range)).orElse(null),
                    lineNumber == sourceLines.size()
            ));
            lineNumber++;
        }
        return new UiStageVisualDto(data.stage().id(), "assembly", "", List.of(), List.of(), null, null, false, List.of(), lines);
    }

    static UiStageVisualDto fromAsm(
            SourceFile sourceFile,
            StageData data,
            IrResult irResult,
            Assembler.Work work
    ) {
        UiStageVisualDto assembly = fromAssemblyLines(sourceFile, data, work);
        UiSourceSpanDto activeRange = assembly.assemblyLines().stream()
                .filter(UiAssemblyLineVisualDto::active)
                .map(UiAssemblyLineVisualDto::range)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new UiStageVisualDto(
                assembly.stage(),
                assembly.visualType(),
                assembly.sourceText(),
                assembly.genericItems(),
                assembly.lexerTokens(),
                assembly.astRoot(),
                assembly.semanticRoot(),
                assembly.semanticEdgesPointChildToParent(),
                irLines(sourceFile, irResult, activeRange),
                assembly.assemblyLines()
        );
    }

    private static List<UiIrLineVisualDto> irLines(
            SourceFile sourceFile,
            IrResult irResult,
            UiSourceSpanDto activeRange
    ) {
        ArrayList<UiIrLineVisualDto> lines = new ArrayList<>();
        for (IrFunction function : irResult.functions()) {
            lines.add(new UiIrLineVisualDto(lines.size() + 1, "function " + function.name(), UiSourceSpanDto.from(sourceFile, function.range()), false));
            for (IrBlock block : function.blocks()) {
                lines.add(new UiIrLineVisualDto(lines.size() + 1, "  block " + block.label(), null, false));
                for (IrInstruction instruction : block.instructions()) {
                    UiSourceSpanDto range = UiSourceSpanDto.from(sourceFile, instruction.range());
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
                    + ", type "
                    + elementAddress.elementType()
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

    static UiStageVisualDto fromAsmResult(SourceFile sourceFile, StageData data, AsmResult asmResult) {
        return fromAsmResult(sourceFile, data, asmResult, null);
    }

    static UiStageVisualDto fromAsmResult(SourceFile sourceFile, StageData data, AsmResult asmResult, IrResult irResult) {
        ArrayList<UiAssemblyLineVisualDto> lines = new ArrayList<>();
        int lineNumber = 1;
        for (String line : asmResult.text().lines().toList()) {
            lines.add(new UiAssemblyLineVisualDto(
                    lineNumber++,
                    line,
                    "ASM_LINE",
                    "",
                    "",
                    false
            ));
        }
        List<UiIrLineVisualDto> irLines = irResult == null ? List.of() : irLines(sourceFile, irResult, null);
        return new UiStageVisualDto("asm", "assembly", "", List.of(), List.of(), null, null, false, irLines, lines);
    }

    private static UiStageVisualDto lexerVisual(StageData data, UiCurrentStateDto state) {
        List<UiLexerTokenVisualDto> tokens = new ArrayList<>();
        for (String item : data.accumulatedOutput()) {
            boolean active = item.equals(data.currentItem());
            tokens.add(tokenVisual(item, active, null));
        }
        if (tokens.stream().noneMatch(UiLexerTokenVisualDto::active) && !data.currentItem().isBlank()) {
            tokens.add(tokenVisual(data.currentItem(), true, null));
        }
        return new UiStageVisualDto(data.stage().id(), "lexer", "", List.of(), tokens, null, null, false, List.of(), List.of());
    }

    private static UiLexerTokenVisualDto tokenVisual(String summary, boolean active, UiSourceSpanDto range) {
        int split = summary.indexOf(' ');
        String kind = split < 0 ? summary : summary.substring(0, split);
        String text = split < 0 ? "" : summary.substring(split + 1);
        return new UiLexerTokenVisualDto(kind, text, range, active);
    }

    private static UiStageVisualDto parserVisual(StageData data, UiCurrentStateDto state) {
        ArrayList<UiAstNodeVisualDto> children = new ArrayList<>();
        int index = 0;
        for (String item : data.accumulatedOutput()) {
            boolean active = item.equals(data.currentItem());
            children.add(new UiAstNodeVisualDto("ast-" + index, item, firstWord(item), null, active, List.of()));
            index++;
        }
        boolean rootActive = data.currentItem().isBlank() || children.stream().noneMatch(UiAstNodeVisualDto::active);
        UiAstNodeVisualDto root = new UiAstNodeVisualDto("ast-root", "Program", "Program", null, rootActive, children);
        return new UiStageVisualDto(data.stage().id(), "ast", "", List.of(), List.of(), root, null, false, List.of(), List.of());
    }

    private static UiStageVisualDto semanticVisual(StageData data, UiCurrentStateDto state) {
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
        return new UiStageVisualDto(data.stage().id(), "semantic-scope", "", List.of(), List.of(), null, root, true, List.of(), List.of());
    }

    private static UiStageVisualDto asmVisual(StageData data) {
        ArrayList<UiAssemblyLineVisualDto> lines = new ArrayList<>();
        int lineNumber = 1;
        for (String item : data.accumulatedOutput()) {
            boolean active = data.currentItem().startsWith(item);
            lines.add(new UiAssemblyLineVisualDto(lineNumber, item, firstWord(item), metadata(data.currentItem(), "section"), metadata(data.currentItem(), "label"), active));
            lineNumber++;
        }
        return new UiStageVisualDto(data.stage().id(), "assembly", "", List.of(), List.of(), null, null, false, List.of(), lines);
    }

    private static UiStageVisualDto genericVisual(StageData data) {
        ArrayList<String> items = new ArrayList<>();
        if (!data.currentItem().isBlank()) {
            items.add(data.currentItem());
        }
        items.addAll(data.accumulatedOutput());
        return new UiStageVisualDto(data.stage().id(), "generic", "", items, List.of(), null, null, false, List.of(), List.of());
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
