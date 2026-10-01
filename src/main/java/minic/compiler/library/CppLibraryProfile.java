package minic.compiler.library;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Explicit C++ algorithm-library inventory, separate from the existing C23 contract. */
public record CppLibraryProfile(String standard, String target, Map<String, Entry> entries) {
    public enum Status { PLANNED, SUPPORTED, LEGACY_PLANNED, LEGACY_SUPPORTED }

    public record Entry(String spelling, String header, Status status, String milestone, List<String> evidence) {
        public Entry {
            Objects.requireNonNull(status, "status");
            if (spelling == null || spelling.isBlank() || milestone == null || milestone.isBlank()) {
                throw new IllegalArgumentException("API spelling and milestone are required");
            }
            if (header == null || !header.matches("lib/cpp/[a-z_]+\\.mh")) {
                throw new IllegalArgumentException("C++ headers must be under lib/cpp: " + header);
            }
            evidence = List.copyOf(evidence);
            if (status == Status.SUPPORTED || status == Status.LEGACY_SUPPORTED) {
                for (String backend : List.of("native", "debug", "reference")) {
                    if (evidence.stream().noneMatch(item -> item.matches(backend + ":[A-Za-z_$][A-Za-z0-9_.$]*#[A-Za-z_$][A-Za-z0-9_$]*"))) {
                        throw new IllegalArgumentException("Supported API needs " + backend + " test evidence: " + spelling);
                    }
                }
            }
        }
    }

    public CppLibraryProfile {
        if (standard == null || standard.isBlank() || target == null || target.isBlank()) {
            throw new IllegalArgumentException("Profile standard and target are required");
        }
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        if (entries.isEmpty()) throw new IllegalArgumentException("Profile must inventory at least one API");
    }

    public boolean supports(String api) {
        Entry entry = entries.get(api);
        return entry != null && (entry.status() == Status.SUPPORTED || entry.status() == Status.LEGACY_SUPPORTED);
    }

    public static CppLibraryProfile defaults() {
        var stream = CppLibraryProfile.class.getResourceAsStream("/minic/cpp/cpp17-algorithm.properties");
        if (stream == null) throw new IllegalStateException("Missing C++ library inventory");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot close C++ library inventory", exception);
        }
    }

    public static CppLibraryProfile read(Reader reader) {
        Properties properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate profile key: " + key);
                return super.put(key, value);
            }
        };
        try {
            properties.load(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read C++ library inventory", exception);
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            if (key.equals("profile.standard") || key.equals("profile.target")) continue;
            if (!key.matches("api\\.[a-z_]+(?:\\.[a-z_]+)*")) {
                throw new IllegalArgumentException("Unknown profile key: " + key);
            }
            String[] fields = properties.getProperty(key).split("\\|", -1);
            if (fields.length != 5) throw new IllegalArgumentException("Expected five API fields: " + key);
            Status status = switch (fields[2]) {
                case "planned" -> Status.PLANNED;
                case "supported" -> Status.SUPPORTED;
                case "legacy-planned" -> Status.LEGACY_PLANNED;
                case "legacy-supported" -> Status.LEGACY_SUPPORTED;
                default -> throw new IllegalArgumentException("Unknown API status: " + fields[2]);
            };
            entries.put(key.substring(4), new Entry(fields[0], fields[1], status, fields[3],
                    fields[4].isBlank() ? List.of() : List.of(fields[4].split(";", -1))));
        }
        return new CppLibraryProfile(properties.getProperty("profile.standard"), properties.getProperty("profile.target"), entries);
    }
}
