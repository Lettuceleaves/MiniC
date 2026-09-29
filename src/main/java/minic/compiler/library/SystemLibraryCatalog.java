package minic.compiler.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static minic.compiler.library.LibraryBinding.NativeCallingConvention;
import static minic.compiler.library.LibraryBinding.NativeKind;
import static minic.compiler.library.LibraryBinding.RuntimeFamily;
import static minic.compiler.library.LibrarySymbol.SymbolKind;

/**
 * 编译器内置系统库目录。
 *
 * <p>源符号到原生导出的绑定和 MiniC 兼容声明头都来自资源文件；Parser、IR、ASM 和 Linker
 * 不按函数名实现特例。</p>
 */
public final class SystemLibraryCatalog {
    private static final String IMPORTS_RESOURCE = "/minic/system/imports.properties";
    private static final String INCLUDE_ROOT = "/minic/include/";
    private static final SystemLibraryCatalog DEFAULT = loadDefault();

    private final Map<String, LibraryBinding> bindings;

    private SystemLibraryCatalog(Map<String, LibraryBinding> bindings) {
        this.bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }

    public static SystemLibraryCatalog defaults() {
        return DEFAULT;
    }

    /** 按 MiniC 源符号名索引的原生绑定。 */
    public Map<String, LibraryBinding> bindings() {
        return bindings;
    }

    public Optional<LibraryBinding> binding(String sourceName) {
        Objects.requireNonNull(sourceName, "sourceName");
        return Optional.ofNullable(bindings.get(sourceName));
    }

    /** 创建经过重复符号校验的目录，主要供工具和隔离测试使用。 */
    public static SystemLibraryCatalog ofBindings(Collection<LibraryBinding> sourceBindings) {
        Objects.requireNonNull(sourceBindings, "sourceBindings");
        LinkedHashMap<String, LibraryBinding> bindings = new LinkedHashMap<>();
        for (LibraryBinding binding : sourceBindings) {
            Objects.requireNonNull(binding, "binding");
            if (bindings.putIfAbsent(binding.sourceName(), binding) != null) {
                throw new IllegalArgumentException("duplicate system library symbol: " + binding.sourceName());
            }
        }
        return new SystemLibraryCatalog(bindings);
    }

    /** 返回项目提供的 MiniC 兼容系统声明头。 */
    public Optional<Header> header(String requestedName) {
        Objects.requireNonNull(requestedName, "requestedName");
        if (!requestedName.matches("[A-Za-z0-9_.-]+")) {
            return Optional.empty();
        }
        String resourceName = INCLUDE_ROOT + requestedName;
        try (InputStream input = SystemLibraryCatalog.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                return Optional.empty();
            }
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(new Header(requestedName, resourceName, content));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read system header: " + requestedName, exception);
        }
    }

    private static SystemLibraryCatalog loadDefault() {
        InputStream input = SystemLibraryCatalog.class.getResourceAsStream(IMPORTS_RESOURCE);
        if (input == null) {
            throw new IllegalStateException("missing system import catalog: " + IMPORTS_RESOURCE);
        }
        return load(input, IMPORTS_RESOURCE);
    }

    static SystemLibraryCatalog load(InputStream input, String sourceDescription) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(sourceDescription, "sourceDescription");
        LinkedHashMap<String, LibraryBinding> bindings = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String value = line.strip();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                int equals = value.indexOf('=');
                if (equals <= 0 || equals == value.length() - 1) {
                    throw invalidBinding(sourceDescription, lineNumber, "expected sourceName=binding");
                }
                String sourceName = value.substring(0, equals).strip();
                String[] fields = value.substring(equals + 1).split("\\|", -1);
                if (fields.length != 6) {
                    throw invalidBinding(sourceDescription, lineNumber,
                            "expected dll|export|symbolKind|runtimeFamily|callingConvention|nativeKind");
                }
                LibraryBinding binding;
                try {
                    binding = new LibraryBinding(
                            sourceName,
                            fields[0].strip(),
                            fields[1].strip(),
                            SymbolKind.valueOf(fields[2].strip()),
                            RuntimeFamily.valueOf(fields[3].strip()),
                            NativeCallingConvention.valueOf(fields[4].strip()),
                            NativeKind.valueOf(fields[5].strip())
                    );
                } catch (IllegalArgumentException | NullPointerException exception) {
                    throw invalidBinding(sourceDescription, lineNumber, exception.getMessage());
                }
                if (bindings.putIfAbsent(sourceName, binding) != null) {
                    throw invalidBinding(sourceDescription, lineNumber,
                            "duplicate source symbol: " + sourceName);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read system import catalog: " + sourceDescription, exception);
        }
        return new SystemLibraryCatalog(bindings);
    }

    private static IllegalStateException invalidBinding(String sourceDescription, int lineNumber, String detail) {
        return new IllegalStateException(
                "invalid system library binding in " + sourceDescription + " at line " + lineNumber + ": " + detail
        );
    }

    public record Header(String name, String resourceName, String content) {
        public Header {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(resourceName, "resourceName");
            Objects.requireNonNull(content, "content");
        }
    }
}
