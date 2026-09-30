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
    void inventoriesManualContainersWithoutClaimingTheyAlreadyWork() {
        var profile = CppLibraryProfile.defaults();
        assertEquals("c++17", profile.standard());
        assertEquals("windows-x64-llp64", profile.target());
        for (String name : Set.of("vector", "queue", "priority_queue", "deque", "set", "multiset", "map", "bitset")) {
            assertTrue(profile.entries().containsKey(name), name);
            assertFalse(profile.supports(name), name);
        }
        assertFalse(profile.supports("unknown"));
        assertEquals("lib/cpp/string.mh", profile.entries().get("string").header());
        assertEquals(CppLibraryProfile.Status.LEGACY_PLANNED, profile.entries().get("random_shuffle").status());
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
