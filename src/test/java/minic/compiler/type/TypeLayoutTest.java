package minic.compiler.type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeLayoutTest {
    @Test
    void definesScalarPointerArrayAndNullLayouts() {
        assertThat(TypeLayout.sizeOf(MiniType.BOOL)).isEqualTo(1);
        assertThat(TypeLayout.alignmentOf(MiniType.BOOL)).isEqualTo(1);
        assertThat(TypeLayout.sizeOf(MiniType.CHAR)).isEqualTo(1);
        assertThat(TypeLayout.alignmentOf(MiniType.CHAR)).isEqualTo(1);
        assertThat(TypeLayout.sizeOf(MiniType.INT)).isEqualTo(4);
        assertThat(TypeLayout.alignmentOf(MiniType.INT)).isEqualTo(4);
        assertThat(TypeLayout.sizeOf(MiniType.LONG)).isEqualTo(8);
        assertThat(TypeLayout.alignmentOf(MiniType.LONG)).isEqualTo(8);
        assertThat(TypeLayout.sizeOf(MiniType.FLOAT)).isEqualTo(4);
        assertThat(TypeLayout.alignmentOf(MiniType.FLOAT)).isEqualTo(4);
        assertThat(TypeLayout.sizeOf(MiniType.DOUBLE)).isEqualTo(8);
        assertThat(TypeLayout.alignmentOf(MiniType.DOUBLE)).isEqualTo(8);
        assertThat(TypeLayout.sizeOf(MiniType.INT.pointerTo())).isEqualTo(8);
        assertThat(TypeLayout.alignmentOf(MiniType.INT.pointerTo())).isEqualTo(8);
        assertThat(TypeLayout.sizeOf(MiniType.NULL)).isEqualTo(8);
        assertThat(TypeLayout.alignmentOf(MiniType.NULL)).isEqualTo(8);
        assertThat(TypeLayout.sizeOf(MiniType.CHAR.arrayOf(3))).isEqualTo(3);
        assertThat(TypeLayout.alignmentOf(MiniType.CHAR.arrayOf(3))).isEqualTo(1);
    }

    @Test
    void rejectsContextualStructLayoutRequests() {
        assertThat(TypeLayout.hasFixedLayout(MiniType.struct("Point"))).isFalse();
        assertThatThrownBy(() -> TypeLayout.sizeOf(MiniType.struct("Point")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TypeLayout.alignmentOf(MiniType.struct("Point")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
