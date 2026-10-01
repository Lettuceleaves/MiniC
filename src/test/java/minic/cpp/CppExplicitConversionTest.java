package minic.cpp;

import minic.compiler.semantic.cpp.CppExplicitConversion;
import minic.compiler.type.MiniType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class CppExplicitConversionTest {
    static Stream<Arguments> conversions(){
        MiniType pointer=MiniType.INT.pointerTo();
        MiniType function=MiniType.function(MiniType.INT,List.of());
        MiniType constInt=MiniType.qualified(MiniType.INT,Set.of(MiniType.TypeQualifier.CONST));
        return Stream.of(
                Arguments.of(pointer,MiniType.INT,"INVALID"),
                Arguments.of(pointer,MiniType.LONG,"INVALID"),
                Arguments.of(pointer,MiniType.LONG_LONG,"ALLOWED"),
                Arguments.of(pointer,MiniType.UNSIGNED_LONG_LONG,"ALLOWED"),
                Arguments.of(pointer,MiniType.BOOL,"ALLOWED"),
                Arguments.of(pointer,MiniType.DOUBLE,"INVALID"),
                Arguments.of(MiniType.NULL,MiniType.INT,"INVALID"),
                Arguments.of(MiniType.NULL,MiniType.LONG_LONG,"ALLOWED"),
                Arguments.of(MiniType.NULL,MiniType.BOOL,"ALLOWED"),
                Arguments.of(MiniType.NULL,MiniType.FLOAT,"INVALID"),
                Arguments.of(MiniType.NULL,pointer,"ALLOWED"),
                Arguments.of(MiniType.INT,pointer,"ALLOWED"),
                Arguments.of(MiniType.BOOL,pointer,"ALLOWED"),
                Arguments.of(MiniType.CHAR,pointer,"ALLOWED"),
                Arguments.of(MiniType.FLOAT,pointer,"INVALID"),
                Arguments.of(MiniType.INT.arrayOf(3),MiniType.INT,"INVALID"),
                Arguments.of(function,MiniType.INT,"INVALID"),
                Arguments.of(function,MiniType.UNSIGNED_LONG_LONG,"ALLOWED"),
                Arguments.of(function.pointerTo(),MiniType.VOID.pointerTo(),"ALLOWED"),
                Arguments.of(constInt.pointerTo(),pointer,"ALLOWED"),
                Arguments.of(MiniType.DOUBLE.pointerTo(),constInt.pointerTo(),"ALLOWED"),
                Arguments.of(MiniType.DOUBLE,MiniType.INT,"ALLOWED"),
                Arguments.of(MiniType.INT,MiniType.DOUBLE,"ALLOWED"),
                Arguments.of(MiniType.VOID,MiniType.INT,"INVALID"),
                Arguments.of(MiniType.VOID,MiniType.VOID,"ALLOWED"),
                Arguments.of(MiniType.struct("Record"),MiniType.VOID,"ALLOWED"),
                Arguments.of(MiniType.struct("Record"),MiniType.INT,"OUTSIDE_SUBSET"),
                Arguments.of(MiniType.INT,MiniType.INT.referenceTo(),"OUTSIDE_SUBSET"),
                Arguments.of(MiniType.INT,MiniType.INT.arrayOf(2),"INVALID"));
    }
    @ParameterizedTest @MethodSource("conversions")
    void checksCpp17ExplicitConversionCategories(MiniType source,MiniType target,String expected){
        assertEquals(expected,CppExplicitConversion.check(source,target).name());
    }
}
