package minic.runtime.debug;

import minic.compiler.ir.model.IrFunction;

import java.util.List;
import java.util.Map;

final class IrDebugProcessSpaceBuilder {
    DebugProcessSpace processSpace(InterpreterState state, DebugCursor cursor) {
        java.util.ArrayList<DebugStackFrame> frames = new java.util.ArrayList<>();
        for (CallFrame frameState : state.frames) {
            List<DebugMemoryEntry> frameLocals = frameState.locals.entrySet().stream()
                    .map(entry -> memoryEntry(frameState, entry))
                    .toList();
            List<DebugMemoryEntry> frameParameters = frameState.parameters.entrySet().stream()
                    .map(entry -> new DebugMemoryEntry(
                            entry.getKey(),
                            new DebugVirtualAddress("stack", Math.abs((frameState.function.name() + ":param:" + entry.getKey()).hashCode())),
                            entry.getValue().typeName(),
                            entry.getValue()
                    ))
                    .toList();
            frames.add(new DebugStackFrame(
                    frameState.frameId,
                    frameState.function.name(),
                    frameParameters,
                    frameLocals,
                    frameState.returnTarget == null ? null : frameState.returnTarget.name(),
                    frameState == state.currentFrame() ? cursor.sourceRange() : null
            ));
        }
        if (frames.isEmpty()) {
            frames.add(new DebugStackFrame(
                    "frame-completed",
                    state.lastFunctionName,
                    List.of(),
                    List.of(),
                    null,
                    cursor.sourceRange()
            ));
        }
        return new DebugProcessSpace(
                new DebugCodeSegment(
                        state.module.functions().stream().map(IrFunction::name).toList(),
                        state.currentFunctionName(),
                        cursor.instructionId(),
                        List.of()
                ),
                staticSegment(state),
                new DebugStackSegment(frames),
                DebugHeapSegment.empty(),
                new DebugIoSegment("", stdout(state), "")
        );
    }

    private DebugStaticSegment staticSegment(InterpreterState state) {
        List<DebugMemoryEntry> stringLiterals = state.module.stringData().stream()
                .map(stringData -> new DebugMemoryEntry(
                        stringData.label(),
                        stringAddress(stringData.label()),
                        "char[]",
                        DebugValue.arrayValue("char[]", stringElements(stringData.value()))
                ))
                .toList();
        return new DebugStaticSegment(List.of(), stringLiterals);
    }

    private List<DebugValueElement> stringElements(String value) {
        java.util.ArrayList<DebugValueElement> elements = new java.util.ArrayList<>();
        for (int i = 0; i < value.length(); i++) {
            elements.add(new DebugValueElement(i, DebugValue.charValue(value.charAt(i))));
        }
        return elements;
    }

    private String stdout(InterpreterState state) {
        String stdout = state.stdout.toString();
        if (state.returnValue == null) {
            return stdout;
        }
        return stdout + "return " + state.returnValue.summary();
    }

    private DebugMemoryEntry memoryEntry(CallFrame frame, Map.Entry<String, DebugValue> entry) {
        return new DebugMemoryEntry(
                frame.localNames.getOrDefault(entry.getKey(), entry.getKey()),
                frame.localAddresses.getOrDefault(
                        entry.getKey(),
                        new DebugVirtualAddress("stack", Math.abs((frame.function.name() + ":" + entry.getKey()).hashCode()))
                ),
                entry.getValue().typeName(),
                entry.getValue()
        );
    }

    private DebugVirtualAddress stringAddress(String label) {
        return new DebugVirtualAddress("static", Math.abs(label.hashCode()));
    }
}
