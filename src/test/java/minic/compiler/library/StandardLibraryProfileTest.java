package minic.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("stdlib-contract")
final class StandardLibraryProfileTest {
    @Test
    void loadsTheC23ProfileAsImmutableTypedData() {
        StandardLibraryProfile profile = StandardLibraryProfile.c23();

        assertEquals(31, profile.headers().size());
        assertEquals(StandardLibraryProfile.SupportStatus.IMPLEMENTED,
                profile.headers().get("iso646.mh").status());
        assertEquals(11, profile.entitiesForHeader("iso646.mh").size());
        assertEquals(Set.of(StandardLibraryProfile.Effect.HEAP),
                profile.entities().get("stdlib.malloc").effects());
    }
}
