package minic.compiler.library;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exact extensionless C++ header names from the algorithm-library inventory.
 * Knowing a header name does not imply an implemented file or supported APIs.
 */
public final class CppHeaderCatalog {
    private static final CppHeaderCatalog DEFAULT = new CppHeaderCatalog(CppLibraryProfile.defaults());
    private final Set<String> names;

    private CppHeaderCatalog(CppLibraryProfile profile) {
        names = profile.entries().values().stream()
                .map(entry -> entry.header().substring("lib/cpp/".length(), entry.header().length() - ".mh".length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static CppHeaderCatalog defaults() {
        return DEFAULT;
    }

    public boolean isKnown(String requestedName) {
        return requestedName != null && (names.contains(requestedName)
                || requestedName.equals("bits/stdc++.h") && names.contains("__all"));
    }

    /** Reads only the catalog's library location, never source/include directories. */
    public Optional<SystemLibraryCatalog.Header> header(String requestedName) {
        if (!isKnown(requestedName)) return Optional.empty();
        String internalName = requestedName.equals("bits/stdc++.h") ? "__all" : requestedName;
        Path file = SystemLibraryCatalog.defaults().includeRoot()
                .resolve("cpp").resolve(internalName + ".mh").normalize();
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(new SystemLibraryCatalog.Header(requestedName, file.toString(),
                    Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read C++ header: " + requestedName, exception);
        }
    }
}
