package minic.runtime.debug;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebugValueTest {
    @Test
    void representsScalarValuesWithStableSummaries() {
        assertThat(DebugValue.intValue(7).summary()).isEqualTo("7");
        assertThat(DebugValue.longValue(9L).kind()).isEqualTo(DebugValueKind.LONG);
        assertThat(DebugValue.charValue('x').summary()).isEqualTo("'x'");
        assertThat(DebugValue.boolValue(true).summary()).isEqualTo("true");
        assertThat(DebugValue.nullValue("int *").summary()).isEqualTo("null");
        assertThat(DebugValue.uninitialized("int").summary()).isEqualTo("<uninitialized>");
    }

    @Test
    void representsPointersArraysAndStructsDefensively() {
        DebugVirtualAddress address = new DebugVirtualAddress("heap", 64);
        DebugValue pointer = DebugValue.pointerValue("Node *", address);
        ArrayList<DebugValueElement> elements = new ArrayList<>();
        elements.add(new DebugValueElement(0, DebugValue.intValue(1)));
        DebugValue array = DebugValue.arrayValue("int[]", elements);
        elements.add(new DebugValueElement(1, DebugValue.intValue(2)));
        DebugValue struct = DebugValue.structValue(
                "Node",
                List.of(
                        new DebugValueField("value", DebugValue.intValue(1)),
                        new DebugValueField("next", pointer)
                )
        );

        assertThat(pointer.pointerTargetOptional()).contains(address);
        assertThat(array.summary()).isEqualTo("array[1]");
        assertThat(array.elements()).singleElement().satisfies(element ->
                assertThat(element.value().summary()).isEqualTo("1"));
        assertThat(struct.summary()).isEqualTo("Node{2 fields}");
        assertThat(struct.fields()).extracting(DebugValueField::name).containsExactly("value", "next");
        assertThatThrownBy(() -> struct.fields().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void memoryEntryExposesValueSummary() {
        DebugMemoryEntry entry = new DebugMemoryEntry("flag", null, "bool", DebugValue.boolValue(false));

        assertThat(entry.addressOptional()).isEmpty();
        assertThat(entry.valueSummary()).isEqualTo("false");
    }
}
