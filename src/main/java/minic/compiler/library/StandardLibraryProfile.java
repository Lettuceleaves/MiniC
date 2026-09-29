package minic.compiler.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * MiniC 对一个 ISO C 标准库 profile 的能力总账。
 *
 * <p>该总账只描述公开契约和实现状态。原生导出名仍由
 * {@link SystemLibraryCatalog} 管理；二者通过契约测试保持一致。</p>
 */
public final class StandardLibraryProfile {
    private static final String HEADERS_RESOURCE = "/minic/stdlib/c23-headers.properties";
    private static final String ENTITIES_RESOURCE = "/minic/stdlib/c23-entities.properties";
    private static final StandardLibraryProfile C23 = loadC23();

    private final Map<String, Header> headers;
    private final Map<String, Entity> entities;

    private StandardLibraryProfile(Map<String, Header> headers, Map<String, Entity> entities) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.entities = Collections.unmodifiableMap(new LinkedHashMap<>(entities));
    }

    public static StandardLibraryProfile c23() {
        return C23;
    }

    public Map<String, Header> headers() {
        return headers;
    }

    public Map<String, Entity> entities() {
        return entities;
    }

    public List<Entity> entitiesForHeader(String headerName) {
        Objects.requireNonNull(headerName, "headerName");
        return entities.values().stream()
                .filter(entity -> entity.headerName().equals(headerName))
                .toList();
    }

    private static StandardLibraryProfile loadC23() {
        LinkedHashMap<String, Header> headers = new LinkedHashMap<>();
        readResource(HEADERS_RESOURCE).forEach((name, value) -> {
            String[] fields = fields(value, 3, HEADERS_RESOURCE, name);
            Header header = new Header(
                    name,
                    SupportStatus.valueOf(fields[0]),
                    fields[1],
                    splitSet(fields[2], ";")
            );
            headers.put(name, header);
        });

        LinkedHashMap<String, Entity> entities = new LinkedHashMap<>();
        readResource(ENTITIES_RESOURCE).forEach((qualifiedName, value) -> {
            int separator = qualifiedName.indexOf('.');
            if (separator <= 0 || separator == qualifiedName.length() - 1) {
                throw invalid(ENTITIES_RESOURCE, qualifiedName, "expected header.entity");
            }
            String headerName = qualifiedName.substring(0, separator) + ".mh";
            if (!headers.containsKey(headerName)) {
                throw invalid(ENTITIES_RESOURCE, qualifiedName, "unknown header " + headerName);
            }
            String[] fields = fields(value, 6, ENTITIES_RESOURCE, qualifiedName);
            Entity entity = new Entity(
                    headerName,
                    qualifiedName.substring(separator + 1),
                    EntityKind.valueOf(fields[0]),
                    SupportStatus.valueOf(fields[1]),
                    NativeProvider.valueOf(fields[2]),
                    DebugProvider.valueOf(fields[3]),
                    parseEffects(fields[4]),
                    splitSet(fields[5], ",")
            );
            entities.put(qualifiedName, entity);
        });
        return new StandardLibraryProfile(headers, entities);
    }

    private static Map<String, String> readResource(String resourceName) {
        InputStream input = StandardLibraryProfile.class.getResourceAsStream(resourceName);
        if (input == null) {
            throw new IllegalStateException("missing standard library profile: " + resourceName);
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String rawLine;
            int lineNumber = 0;
            while ((rawLine = reader.readLine()) != null) {
                lineNumber++;
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals <= 0 || equals == line.length() - 1) {
                    throw new IllegalStateException("invalid standard library profile "
                            + resourceName + ":" + lineNumber);
                }
                String key = line.substring(0, equals).strip();
                String previous = result.putIfAbsent(key, line.substring(equals + 1).strip());
                if (previous != null) {
                    throw invalid(resourceName, key, "duplicate entry");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read standard library profile: " + resourceName, exception);
        }
        return result;
    }

    private static String[] fields(String value, int expected, String resourceName, String key) {
        String[] fields = value.split("\\|", -1);
        if (fields.length != expected || Arrays.stream(fields).anyMatch(String::isBlank)) {
            throw invalid(resourceName, key, "expected " + expected + " non-empty fields");
        }
        for (int index = 0; index < fields.length; index++) {
            fields[index] = fields[index].strip();
        }
        return fields;
    }

    private static Set<String> splitSet(String value, String separator) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : value.split(separator)) {
            String normalized = item.strip();
            if (normalized.isEmpty() || !values.add(normalized)) {
                throw new IllegalStateException("invalid duplicate or empty standard-library list item: " + value);
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private static Set<Effect> parseEffects(String value) {
        EnumSet<Effect> effects = EnumSet.noneOf(Effect.class);
        for (String effect : splitSet(value, ";")) {
            effects.add(Effect.valueOf(effect));
        }
        if (effects.contains(Effect.NONE) && effects.size() != 1) {
            throw new IllegalStateException("NONE cannot be combined with other standard-library effects");
        }
        return Collections.unmodifiableSet(effects);
    }

    private static IllegalStateException invalid(String resourceName, String key, String detail) {
        return new IllegalStateException("invalid standard library profile " + resourceName
                + " entry " + key + ": " + detail);
    }

    public record Header(
            String name,
            SupportStatus status,
            String standardSince,
            Set<String> requiredCapabilities
    ) {
        public Header {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(standardSince, "standardSince");
            requiredCapabilities = Set.copyOf(requiredCapabilities);
            if (!name.endsWith(".mh") || standardSince.isBlank() || requiredCapabilities.isEmpty()) {
                throw new IllegalArgumentException("invalid standard library header: " + name);
            }
        }
    }

    public record Entity(
            String headerName,
            String name,
            EntityKind kind,
            SupportStatus status,
            NativeProvider nativeProvider,
            DebugProvider debugProvider,
            Set<Effect> effects,
            Set<String> testIds
    ) {
        public Entity {
            Objects.requireNonNull(headerName, "headerName");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(nativeProvider, "nativeProvider");
            Objects.requireNonNull(debugProvider, "debugProvider");
            effects = Set.copyOf(effects);
            testIds = Set.copyOf(testIds);
            if (name.isBlank() || effects.isEmpty() || testIds.isEmpty()) {
                throw new IllegalArgumentException("invalid standard library entity: " + headerName + name);
            }
        }
    }

    public enum SupportStatus {
        IMPLEMENTED,
        PARTIAL,
        DEFERRED
    }

    public enum EntityKind {
        MACRO,
        CONSTANT,
        TYPE,
        FUNCTION
    }

    public enum NativeProvider {
        HEADER_ONLY,
        COMPILER_INTRINSIC,
        DLL_DIRECT,
        MINIC_ADAPTER,
        WIN32_RUNTIME,
        UNSUPPORTED
    }

    public enum DebugProvider {
        HEADER_ONLY,
        INTERPRETED,
        HOST_BRIDGE,
        NATIVE_ONLY,
        UNSUPPORTED
    }

    public enum Effect {
        NONE,
        MEMORY,
        HEAP,
        STDIN,
        STDOUT,
        STDERR,
        FILESYSTEM,
        ENVIRONMENT,
        CLOCK,
        LOCALE,
        ERRNO,
        FLOATING_ENVIRONMENT,
        RANDOM,
        CALLBACK,
        TERMINATION,
        SIGNAL,
        THREAD
    }
}
