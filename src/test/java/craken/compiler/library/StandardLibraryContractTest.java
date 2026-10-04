package craken.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-contract")
final class StandardLibraryContractTest {
    private static final Set<String> MSVCRT_HEADERS = Set.of(
            "ctype.mh", "errno.mh", "inttypes.mh", "locale.mh", "math.mh",
            "signal.mh", "stdio.mh", "stdlib.mh", "string.mh", "time.mh"
    );

    /** Source aliases that expose one public standard entity through a private binding name. */
    private static final Map<String, String> PUBLIC_BINDING_ALIASES = Map.of(
            "craken_immediate_exit", "_Exit",
            "craken_assert_fail", "assert"
    );

    /** Runtime bootstrap and native-test instrumentation, not published C entities. */
    private static final Set<String> INTERNAL_BINDINGS = Set.of(
            "ExitProcess",
            "craken_stdout_stream",
            "craken_set_process_error_mode",
            "craken_iob_base",
            "craken_ucrt_strtod",
            "craken_ucrt_strtof",
            "craken_ucrt_errno_location",
            "craken_isctype",
            "craken_string_malloc",
            "setvbuf",
            "fputs"
    );

    @Test
    void runtimeCapabilitiesNameTheActualMsvcrtProfile() {
        StandardLibraryProfile profile = StandardLibraryProfile.c23();

        profile.headers().values().forEach(header -> {
            assertFalse(header.requiredCapabilities().contains("ucrt"), header.name());
            assertFalse(header.requiredCapabilities().contains("windows-crt"), header.name());
        });
        MSVCRT_HEADERS.forEach(header -> assertTrue(
                profile.headers().get(header).requiredCapabilities().contains("msvcrt"),
                header
        ));
    }

    @Test
    void publicImportsHaveExactlyOneEntityAndInternalBindingsHaveNone() {
        StandardLibraryProfile profile = StandardLibraryProfile.c23();

        SystemLibraryCatalog.defaults().bindings().values().forEach(binding -> {
            if (INTERNAL_BINDINGS.contains(binding.sourceName())) {
                long internalMatches = profile.entities().values().stream()
                        .filter(entity -> entity.name().equals(binding.sourceName()))
                        .count();
                assertEquals(0, internalMatches, binding.sourceName());
                return;
            }

            String publicName = PUBLIC_BINDING_ALIASES.getOrDefault(
                    binding.sourceName(), binding.sourceName());
            var matchingEntities = profile.entities().values().stream()
                    .filter(entity -> entity.name().equals(publicName))
                    .toList();
            assertEquals(1, matchingEntities.size(), binding.sourceName() + " -> " + publicName);

            StandardLibraryProfile.NativeProvider expectedProvider =
                    binding.sourceName().equals("craken_assert_fail")
                            ? StandardLibraryProfile.NativeProvider.CRAKEN_ADAPTER
                    : binding.runtimeFamily() == LibraryBinding.RuntimeFamily.WINDOWS
                            ? StandardLibraryProfile.NativeProvider.WIN32_RUNTIME
                            : StandardLibraryProfile.NativeProvider.DLL_DIRECT;
            StandardLibraryProfile.Entity entity = matchingEntities.getFirst();
            assertEquals(expectedProvider, entity.nativeProvider(), publicName);
            assertTrue(entity.testIds().stream().anyMatch(testId -> testId.startsWith("native.")),
                    publicName);
        });
    }

    @Test
    void terminationEntitiesRecordThePublicContractAndRealProviders() {
        StandardLibraryProfile profile = StandardLibraryProfile.c23();

        assertTerminationEntity(profile, "abort", StandardLibraryProfile.NativeProvider.DLL_DIRECT);
        assertTerminationEntity(profile, "exit", StandardLibraryProfile.NativeProvider.DLL_DIRECT);
        assertTerminationEntity(profile, "_Exit", StandardLibraryProfile.NativeProvider.WIN32_RUNTIME);
        assertFalse(profile.entities().containsKey("stdlib.craken_immediate_exit"));
    }

    private static void assertTerminationEntity(
            StandardLibraryProfile profile,
            String name,
            StandardLibraryProfile.NativeProvider nativeProvider
    ) {
        StandardLibraryProfile.Entity entity = profile.entities().get("stdlib." + name);
        assertEquals(StandardLibraryProfile.EntityKind.FUNCTION, entity.kind(), name);
        assertEquals(StandardLibraryProfile.SupportStatus.IMPLEMENTED, entity.status(), name);
        assertEquals(nativeProvider, entity.nativeProvider(), name);
        assertEquals(StandardLibraryProfile.DebugProvider.INTERPRETED, entity.debugProvider(), name);
        assertEquals(Set.of(StandardLibraryProfile.Effect.TERMINATION), entity.effects(), name);
    }
}
