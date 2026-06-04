package minic.uiapi;

import minic.runtime.debug.DebugEvent;
import minic.runtime.debug.DebugHeapBlock;
import minic.runtime.debug.DebugMemoryEntry;
import minic.runtime.debug.DebugProcessSpace;
import minic.runtime.debug.DebugSession;
import minic.runtime.debug.DebugSnapshot;
import minic.runtime.debug.DebugStackFrame;
import minic.runtime.debug.DebugValue;
import minic.runtime.debug.DebugValueKind;

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
                snapshot(session.currentSnapshot()),
                session.snapshots().stream().map(UiDebugDtoMapper::snapshot).toList(),
                session.events().stream().map(UiDebugDtoMapper::event).toList(),
                session.breakpoints().stream()
                        .map(breakpoint -> UiDebugBreakpointDto.fromLine(breakpoint.line(), breakpoint.enabled()))
                        .toList()
        );
    }

    private static UiDebugSnapshotDto snapshot(DebugSnapshot snapshot) {
        return new UiDebugSnapshotDto(
                snapshot.snapshotId(),
                snapshot.visibleStepIndex(),
                snapshot.cursor().functionName(),
                snapshot.cursor().basicBlockId(),
                snapshot.cursor().instructionId(),
                snapshot.cursor().sourceRangeOptional().map(UiSourceSpanDto::from).orElse(null),
                snapshot.callStackSummary(),
                processSpace(snapshot.processSpace()),
                snapshot.breakpointHit(),
                snapshot.stopReason().name()
        );
    }

    static UiDebugProcessSpaceDto processSpace(DebugProcessSpace processSpace) {
        return new UiDebugProcessSpaceDto(
                processSpace.code().currentFunctionOptional().orElse(""),
                processSpace.code().currentInstructionOptional().orElse(""),
                processSpace.code().functions(),
                processSpace.staticData().stringLiterals().stream().map(UiDebugDtoMapper::variable).toList(),
                processSpace.stack().frames().stream().map(UiDebugDtoMapper::frame).toList(),
                heapVariables(processSpace.heap().blocks()),
                processSpace.io().stdin(),
                processSpace.io().stdout(),
                processSpace.io().stderr()
        );
    }

    private static UiDebugFrameDto frame(DebugStackFrame frame) {
        return new UiDebugFrameDto(
                frame.frameId(),
                frame.functionName(),
                frame.parameters().stream().map(UiDebugDtoMapper::variable).toList(),
                frame.locals().stream().map(UiDebugDtoMapper::variable).toList(),
                frame.returnTargetOptional().orElse(null),
                frame.currentSourceRangeOptional().map(UiSourceSpanDto::from).orElse(null)
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
                entry.addressOptional().map(minic.runtime.debug.DebugVirtualAddress::display).orElse(""),
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
                        .map(minic.runtime.debug.DebugVirtualAddress::display)
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

    private static UiDebugEventDto event(DebugEvent event) {
        return new UiDebugEventDto(
                event.eventId(),
                event.snapshotId(),
                event.type(),
                event.title(),
                event.description(),
                event.sourceRangeOptional().map(UiSourceSpanDto::from).orElse(null),
                event.affectedValueRefs()
        );
    }
}
