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

    @Test
    void representsFixedLengthArrayTypes() {
        MiniType arrayType = MiniType.INT.arrayOf(3);

        assertThat(arrayType).isEqualTo(new MiniType.ArrayType(MiniType.INT, 3));
        assertThat(arrayType.isArray()).isTrue();
        assertThat(arrayType.elementType()).isEqualTo(MiniType.INT);
        assertThat(arrayType.arrayLength()).isEqualTo(3);
        assertThat(arrayType.toString()).isEqualTo("int[3]");
    }
}
