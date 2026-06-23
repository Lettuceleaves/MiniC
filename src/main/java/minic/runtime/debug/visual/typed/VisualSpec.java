package minic.runtime.debug.visual.typed;

import java.util.List;
import java.util.Map;
import minic.runtime.debug.visual.VisualKind;

public interface VisualSpec {
    String name();

    String root();

    VisualKind kind();

    Map<String, String> attributes();

    List<String> fields();

    int line();

    Map<String, String> options();
}
