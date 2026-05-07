package minic.uiapi;

import minic.runtime.debug.DebugEvent;
import minic.runtime.debug.DebugHeapBlock;
import minic.runtime.debug.DebugMemoryEntry;
import minic.runtime.debug.DebugProcessSpace;
import minic.runtime.debug.DebugSession;
import minic.runtime.debug.DebugSnapshot;
import minic.runtime.debug.DebugStackFrame;

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

    private static UiDebugProcessSpaceDto processSpace(DebugProcessSpace processSpace) {
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
                        block.status()
                ))
                .toList();
    }

    private static UiDebugVariableDto variable(DebugMemoryEntry entry) {
        return new UiDebugVariableDto(
                entry.name(),
                entry.addressOptional().map(minic.runtime.debug.DebugVirtualAddress::display).orElse(""),
                entry.typeName(),
                entry.value().kind().name(),
                entry.valueSummary()
        );
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
