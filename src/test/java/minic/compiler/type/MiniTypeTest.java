package minic.compiler.type;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void representsScalarTypesAndNullPointerType() {
        assertThat(MiniType.BOOL.toString()).isEqualTo("bool");
        assertThat(MiniType.CHAR.toString()).isEqualTo("char");
        assertThat(MiniType.INT.toString()).isEqualTo("int");
        assertThat(MiniType.LONG.toString()).isEqualTo("long");
        assertThat(MiniType.FLOAT.toString()).isEqualTo("float");
        assertThat(MiniType.DOUBLE.toString()).isEqualTo("double");
        assertThat(MiniType.NULL.toString()).isEqualTo("NULL");

        assertThat(MiniType.BOOL.isIntegerScalar()).isTrue();
        assertThat(MiniType.CHAR.isIntegerScalar()).isTrue();
        assertThat(MiniType.LONG.isIntegerScalar()).isTrue();
        assertThat(MiniType.FLOAT.isFloatingScalar()).isTrue();
        assertThat(MiniType.DOUBLE.isFloatingScalar()).isTrue();
        assertThat(MiniType.NULL.isNullPointer()).isTrue();
        assertThat(((MiniType.ScalarType) MiniType.BOOL).kind().signed()).isFalse();
        assertThat(((MiniType.ScalarType) MiniType.CHAR).kind().signed()).isTrue();
        assertThat(((MiniType.ScalarType) MiniType.INT).kind().signed()).isTrue();
        assertThat(((MiniType.ScalarType) MiniType.LONG).kind().signed()).isTrue();
        assertThat(((MiniType.ScalarType) MiniType.FLOAT).kind().signed()).isTrue();
        assertThat(((MiniType.ScalarType) MiniType.DOUBLE).kind().signed()).isTrue();
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

    @Test
    void representsStructTypes() {
        MiniType pointType = MiniType.struct("Point");

        assertThat(pointType).isEqualTo(new MiniType.StructType("Point"));
        assertThat(pointType.isStruct()).isTrue();
        assertThat(pointType.toString()).isEqualTo("struct Point");
    }

    @Test
    void representsFunctionTypesAndFunctionPointers() {
        MiniType functionType = MiniType.function(MiniType.INT, List.of(MiniType.INT, MiniType.INT.pointerTo()));
        MiniType functionPointer = functionType.pointerTo();

        assertThat(functionType).isEqualTo(new MiniType.FunctionType(
                MiniType.INT,
                List.of(MiniType.INT, MiniType.INT.pointerTo())
        ));
        assertThat(functionType.isFunction()).isTrue();
        assertThat(functionType.returnType()).isEqualTo(MiniType.INT);
        assertThat(functionType.parameterTypes()).containsExactly(MiniType.INT, MiniType.INT.pointerTo());
        assertThat(functionType.toString()).isEqualTo("int (int, int*)");
        assertThat(functionPointer.isPointer()).isTrue();
        assertThat(functionPointer.pointee()).isEqualTo(functionType);
        assertThat(functionPointer.toString()).isEqualTo("int (int, int*)*");
    }

    @Test
    void copiesFunctionParameterTypesDefensively() {
        ArrayList<MiniType> parameterTypes = new ArrayList<>(List.of(MiniType.INT));

        MiniType functionType = MiniType.function(MiniType.INT, parameterTypes);
        parameterTypes.add(MiniType.INT.pointerTo());

        assertThat(functionType.parameterTypes()).containsExactly(MiniType.INT);
        assertThatThrownBy(() -> functionType.parameterTypes().add(MiniType.INT))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
