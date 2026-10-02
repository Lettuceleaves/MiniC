package minic.compiler.library;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Extensionless library headers such as {@code <vector>}, installed under {@code lib/stl}.
 * Names starting with {@code __} are implementation details and cannot be included directly;
 * {@code <bits/stdc++.h>} is the conventional spelling of the all-headers bundle.
 */
public final class StlHeaderCatalog {
    private static final String DIRECTORY = "stl";
    private static final String BUNDLE = "bits/stdc++.h";
    private static final String BUNDLE_FILE = "__all";
    private static final StlHeaderCatalog DEFAULT = new StlHeaderCatalog();

    private StlHeaderCatalog() {
    }

    public static StlHeaderCatalog defaults() {
        return DEFAULT;
    }

    public boolean isKnown(String requestedName) {
        return fileName(requestedName).map(name -> Files.isRegularFile(path(name))).orElse(false);
    }

    /** Reads only the catalog's library location, never source/include directories. */
    public Optional<SystemLibraryCatalog.Header> header(String requestedName) {
        Optional<String> name = fileName(requestedName);
        if (name.isEmpty()) return Optional.empty();
        Path file = path(name.get());
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(new SystemLibraryCatalog.Header(requestedName, file.toString(),
                    Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read library header: " + requestedName, exception);
        }
    }

    private static Optional<String> fileName(String requestedName) {
        if (BUNDLE.equals(requestedName)) return Optional.of(BUNDLE_FILE);
        if (requestedName == null || !requestedName.matches("[a-z][a-z_]*")) return Optional.empty();
        return Optional.of(requestedName);
    }

    private static Path path(String name) {
        return SystemLibraryCatalog.defaults().includeRoot().resolve(DIRECTORY).resolve(name + ".mh").normalize();
    }
}
