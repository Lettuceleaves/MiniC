package minic.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void everyStandardDllBindingHasAnEntityLedgerEntry() {
        StandardLibraryProfile profile = StandardLibraryProfile.c23();

        SystemLibraryCatalog.defaults().bindings().values().stream()
                .filter(binding -> !binding.runtimeFamily().equals(LibraryBinding.RuntimeFamily.WINDOWS))
                .forEach(binding -> {
                    StandardLibraryProfile.Entity entity = profile.entities().values().stream()
                            .filter(candidate -> candidate.name().equals(binding.sourceName()))
                            .findFirst()
                            .orElse(null);
                    assertNotNull(entity, binding.sourceName());
                    assertEquals(StandardLibraryProfile.NativeProvider.DLL_DIRECT, entity.nativeProvider());
                    assertTrue(entity.testIds().stream().anyMatch(testId -> testId.startsWith("native.")),
                            binding.sourceName());
                });
    }
}
