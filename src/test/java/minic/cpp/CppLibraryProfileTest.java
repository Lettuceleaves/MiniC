package minic.cpp;

import minic.compiler.library.CppLibraryProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("stl-contract")
final class CppLibraryProfileTest {
    @Test
    void inventoriesSelectedProfileAndKeepsLegacyStatusDistinct() {
        var profile = CppLibraryProfile.defaults();
        assertEquals("c++17", profile.standard());
        assertEquals("windows-x64-llp64", profile.target());
        for (String name : Set.of("vector", "queue", "priority_queue", "deque", "set", "multiset", "map", "bitset")) {
            assertTrue(profile.entries().containsKey(name), name);
            var status = profile.entries().get(name).status();
            assertTrue(status == CppLibraryProfile.Status.PLANNED || status == CppLibraryProfile.Status.SUPPORTED, name);
            assertEquals(status == CppLibraryProfile.Status.SUPPORTED, profile.supports(name), name);
        }
        assertFalse(profile.supports("unknown"));
        assertEquals("lib/cpp/string.mh", profile.entries().get("string").header());
        var legacy = profile.entries().get("random_shuffle").status().name();
        assertTrue(legacy.equals("LEGACY_PLANNED") || legacy.equals("LEGACY_SUPPORTED"), legacy);
        assertEquals(legacy.equals("LEGACY_SUPPORTED"), profile.supports("random_shuffle"));
    }

    @Test
    void requiresNativeDebugAndReferenceEvidenceBeforeAdvertisingSupport() {
        String base = "profile.standard=c++17\nprofile.target=windows-x64-llp64\n";
        assertThrows(IllegalArgumentException.class, () -> CppLibraryProfile.read(new StringReader(base
                + "api.vector=std::vector<T>|lib/cpp/vector.mh|supported|L04|\n")));
        var verified = CppLibraryProfile.read(new StringReader(base
                + "api.vector=std::vector<T>|lib/cpp/vector.mh|supported|L04|native:VectorTest#native;debug:VectorTest#debug;reference:VectorTest#reference\n"));
        assertTrue(verified.supports("vector"));
        assertThrows(UnsupportedOperationException.class, () -> verified.entries().clear());
    }


    @Test
    void legacySupportRequiresTheSameThreeBackendsWithoutLosingItsLegacyIdentity() {
        String base = "profile.standard=c++17\nprofile.target=windows-x64-llp64\n";
        String references = "native:LegacyTest#run;debug:LegacyTest#run;reference:LegacyTest#run";
        var profile = CppLibraryProfile.read(new StringReader(base
                + "api.random_shuffle=std::random_shuffle|lib/cpp/algorithm.mh|legacy-supported|L21|" + references + "\n"));
        assertEquals("LEGACY_SUPPORTED", profile.entries().get("random_shuffle").status().name());
        assertNotEquals(CppLibraryProfile.Status.SUPPORTED, profile.entries().get("random_shuffle").status());
        assertTrue(profile.supports("random_shuffle"));
        assertEquals(3, profile.entries().get("random_shuffle").evidence().size());
        assertThrows(UnsupportedOperationException.class, () -> profile.entries().get("random_shuffle").evidence().clear());
        for (String status : new String[]{"supported", "legacy-supported"}) {
            for (String missing : new String[]{"native", "debug", "reference"}) {
                String incomplete = java.util.Arrays.stream(references.split(";"))
                        .filter(item -> !item.startsWith(missing + ":"))
                        .collect(java.util.stream.Collectors.joining(";"));
                assertThrows(IllegalArgumentException.class, () -> CppLibraryProfile.read(new StringReader(base
                        + "api.random_shuffle=std::random_shuffle|lib/cpp/algorithm.mh|" + status + "|L21|" + incomplete + "\n")),
                        status + " missing " + missing);
            }
        }
    }

    @Test
    void plannedStatesRemainUnadvertisedEvenWhenCandidateEvidenceHasBeenRecorded() {
        String base = "profile.standard=c++17\nprofile.target=windows-x64-llp64\n";
        for (String status : new String[]{"planned", "legacy-planned"}) {
            var profile = CppLibraryProfile.read(new StringReader(base
                    + "api.example=example|lib/cpp/algorithm.mh|" + status
                    + "|V04|native:SomeTest#run;debug:SomeTest#run;reference:SomeTest#run\n"));
            assertFalse(profile.supports("example"));
            assertEquals(3, profile.entries().get("example").evidence().size());
        }
    }

    @Test
    void rejectsMalformedStatusesHeadersAndDuplicateEntries() {
        String base = "profile.standard=c++17\nprofile.target=windows-x64-llp64\n";
        for (String bad : new String[]{
                "api.vector=vector|lib/cpp/vector.mh|almost|L04|\n",
                "api.vector=vector|../vector.mh|planned|L04|\n",
                "api.vector=vector|lib/cpp/vector.mh|planned||\n",
                "api.vector=vector|lib/cpp/vector.mh|planned|L04|\napi.vector=other|lib/cpp/vector.mh|planned|L04|\n"}) {
            assertThrows(IllegalArgumentException.class, () -> CppLibraryProfile.read(new StringReader(base + bad)), bad);
        }
    }
}
