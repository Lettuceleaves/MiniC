package minic.runtime.debug;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DebugVirtualAddressTest {
    @Test
    void displaysStackAndHeapAddressesWithShortPrefixes() {
        assertThat(new DebugVirtualAddress("stack", 0x10).display()).isEqualTo("s@10");
        assertThat(new DebugVirtualAddress("heap", 0x40).display()).isEqualTo("h@40");
    }
}
