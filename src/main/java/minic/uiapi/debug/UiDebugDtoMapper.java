package minic.uiapi;

import minic.compiler.SourceFile;
import minic.debug.DebugEvent;
import minic.debug.DebugHeapBlock;
import minic.debug.DebugMemoryEntry;
import minic.debug.DebugProcessSpace;
import minic.debug.DebugSession;
import minic.debug.DebugSnapshot;
import minic.debug.DebugStackFrame;
import minic.debug.DebugValue;
import minic.debug.DebugValueKind;

import java.util.List;

/**
 * Debug runtime 对象到 UI DTO 的转换器。
 */
final class UiDebugDtoMapper {
    private UiDebugDtoMapper() {
    }

    static UiDebugStateDto state(DebugSession session) {
        return new UiDebugStateDto(
                session.sourceFile().path(),
                session.state().name(),
                snapshot(session.sourceFile(), session.currentSnapshot()),
                session.snapshots().stream().map(snapshot -> snapshot(session.sourceFile(), snapshot)).toList(),
                session.events().stream().map(event -> event(session.sourceFile(), event)).toList(),
                session.breakpoints().stream()
                        .map(breakpoint -> UiDebugBreakpointDto.fromLine(breakpoint.line(), breakpoint.enabled()))
                        .toList()
        );
    }

    private static UiDebugSnapshotDto snapshot(SourceFile sourceFile, DebugSnapshot snapshot) {
        return new UiDebugSnapshotDto(
                snapshot.snapshotId(),
                snapshot.visibleStepIndex(),
                snapshot.cursor().functionName(),
                snapshot.cursor().basicBlockId(),
                snapshot.cursor().instructionId(),
                snapshot.cursor().sourceRangeOptional()
                        .map(range -> UiSourceSpanDto.from(sourceFile, range))
                        .orElse(null),
                snapshot.callStackSummary(),
                processSpace(sourceFile, snapshot.processSpace()),
                snapshot.breakpointHit(),
                snapshot.stopReason().name()
        );
    }

    static UiDebugProcessSpaceDto processSpace(SourceFile sourceFile, DebugProcessSpace processSpace) {
        return new UiDebugProcessSpaceDto(
                processSpace.code().currentFunctionOptional().orElse(""),
                processSpace.code().currentInstructionOptional().orElse(""),
                processSpace.code().functions(),
                processSpace.staticData().stringLiterals().stream().map(UiDebugDtoMapper::variable).toList(),
                processSpace.stack().frames().stream().map(frame -> frame(sourceFile, frame)).toList(),
                heapVariables(processSpace.heap().blocks()),
                processSpace.io().stdin(),
                processSpace.io().stdout(),
                processSpace.io().stderr()
        );
    }

    private static UiDebugFrameDto frame(SourceFile sourceFile, DebugStackFrame frame) {
        return new UiDebugFrameDto(
                frame.frameId(),
                frame.functionName(),
                frame.parameters().stream().map(UiDebugDtoMapper::variable).toList(),
                frame.locals().stream().map(UiDebugDtoMapper::variable).toList(),
                frame.returnTargetOptional().orElse(null),
                frame.currentSourceRangeOptional()
                        .map(range -> UiSourceSpanDto.from(sourceFile, range))
                        .orElse(null)
        );
    }

    private static List<UiDebugVariableDto> heapVariables(List<DebugHeapBlock> blocks) {
        return blocks.stream()
                .map(block -> new UiDebugVariableDto(
                        block.address().display(),
                        block.address().display(),
                        block.typeName(),
                        "HEAP_BLOCK",
                        block.status(),
                        "",
                        "HEAP_BLOCK",
                        false,
                        "",
                        block.entries().stream().map(UiDebugDtoMapper::variable).toList(),
                        List.of()
                ))
                .toList();
    }

    private static UiDebugVariableDto variable(DebugMemoryEntry entry) {
        return variable(
                entry.name(),
                entry.addressOptional().map(minic.debug.DebugVirtualAddress::display).orElse(""),
                entry.typeName(),
                entry.value()
        );
    }

    private static UiDebugVariableDto variable(String name, String address, String typeName, DebugValue value) {
        return new UiDebugVariableDto(
                name,
                address,
                typeName,
                value.kind().name(),
                value.summary(),
                value.pointerTargetOptional()
                        .map(minic.debug.DebugVirtualAddress::display)
                        .orElse(""),
                typeShape(value),
                false,
                "",
                value.fields().stream()
                        .map(field -> variable(
                                field.name(),
                                childAddress(address, "." + field.name()),
                                field.value().typeName(),
                                field.value()
                        ))
                        .toList(),
                value.elements().stream()
                        .map(element -> variable(
                                "[" + element.index() + "]",
                                childAddress(address, "[" + element.index() + "]"),
                                element.value().typeName(),
                                element.value()
                        ))
                        .toList()
        );
    }

    private static String childAddress(String parentAddress, String suffix) {
        return parentAddress.isBlank() ? "" : parentAddress + suffix;
    }

    private static String typeShape(DebugValue value) {
        DebugValueKind kind = value.kind();
        return switch (kind) {
            case ARRAY -> "ARRAY";
            case STRUCT -> "STRUCT";
            case POINTER -> "POINTER";
            case NULL -> "NULL";
            case UNINITIALIZED -> "UNINITIALIZED";
            default -> "SCALAR";
        };
    }

    private static UiDebugEventDto event(SourceFile sourceFile, DebugEvent event) {
        return new UiDebugEventDto(
                event.eventId(),
                event.snapshotId(),
                event.type(),
                event.title(),
                event.description(),
                event.sourceRangeOptional().map(range -> UiSourceSpanDto.from(sourceFile, range)).orElse(null),
                event.affectedValueRefs()
        );
    }
}
