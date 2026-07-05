package minic.runtime.debug.visual.layout;

import minic.runtime.debug.dataflow.DataFlowEvent;
import minic.runtime.debug.dataflow.DataFlowEventType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Binds annotation root variable names to their first runtime memory target.
 */
public final class VisualRootBinder {
    private VisualRootBinder() {
    }

    public static List<VisualRootBinding> bind(List<String> variables, List<DataFlowEvent> events) {
        Objects.requireNonNull(variables, "variables");
        Objects.requireNonNull(events, "events");
        LinkedHashMap<String, VisualRootBinding> bindings = new LinkedHashMap<>();
        for (String variable : variables) {
            if (variable == null || variable.isBlank()) {
                throw new IllegalArgumentException("variables must not contain blank values");
            }
            bindings.put(variable, null);
        }
        for (DataFlowEvent event : events) {
            String variable = event.lvaluePath();
            if (!bindings.containsKey(variable) || bindings.get(variable) != null) {
                continue;
            }
            String target = bindingTarget(event);
            if (!target.isBlank()) {
                bindings.put(variable, new VisualRootBinding(variable, target));
            }
        }
        ArrayList<VisualRootBinding> result = new ArrayList<>();
        for (VisualRootBinding binding : bindings.values()) {
            if (binding != null) {
                result.add(binding);
            }
        }
        return result;
    }

    private static String bindingTarget(DataFlowEvent event) {
        if (event.type() == DataFlowEventType.POINTER_RETARGET) {
            return validPointerTarget(event.pointerTarget()) ? event.pointerTarget() : "";
        }
        if (validPointerTarget(event.pointerTarget())) {
            return event.pointerTarget();
        }
        return event.address();
    }

    private static boolean validPointerTarget(String value) {
        return value != null && !value.isBlank() && !value.equals("null");
    }
}
