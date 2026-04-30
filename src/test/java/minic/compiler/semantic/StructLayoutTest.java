package minic.compiler.semantic;

import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructLayoutTest {
    @Test
    void storesStructLayoutFieldsAndFindsFieldByName() {
        StructFieldLayout x = new StructFieldLayout("x", MiniType.INT, 0, 4, 4);
        StructFieldLayout next = new StructFieldLayout("next", MiniType.INT.pointerTo(), 8, 8, 8);
        ArrayList<StructFieldLayout> fields = new ArrayList<>(List.of(x, next));

        StructLayout layout = new StructLayout("Node", 16, 8, fields);
        fields.clear();

        assertThat(layout.name()).isEqualTo("Node");
        assertThat(layout.size()).isEqualTo(16);
        assertThat(layout.alignment()).isEqualTo(8);
        assertThat(layout.fields()).containsExactly(x, next);
        assertThat(layout.field("next")).contains(next);
        assertThat(layout.field("missing")).isEmpty();
        assertThatThrownBy(() -> layout.fields().add(x))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidLayoutValues() {
        assertThatThrownBy(() -> new StructFieldLayout("", MiniType.INT, 0, 4, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructFieldLayout("x", MiniType.INT, -1, 4, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructLayout("Point", 0, 4, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
