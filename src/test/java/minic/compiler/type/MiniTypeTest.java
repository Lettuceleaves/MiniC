package minic.compiler.type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniTypeTest {
    @Test
    void representsIntAndPointerTypes() {
        MiniType intType = MiniType.INT;
        MiniType intPointer = intType.pointerTo();

        assertThat(intType.toString()).isEqualTo("int");
        assertThat(intPointer).isEqualTo(new MiniType.PointerType(MiniType.INT));
        assertThat(intPointer.toString()).isEqualTo("int*");
    }
}
