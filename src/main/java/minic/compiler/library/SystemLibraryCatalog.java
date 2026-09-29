package minic.compiler.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 编译器内置系统库目录。
 *
 * <p>函数到 DLL 的绑定和 MiniC 兼容声明头都来自资源文件；Parser、IR、ASM 和 Linker
 * 不按函数名实现特例。</p>
 */
public final class SystemLibraryCatalog {
    private static final String IMPORTS_RESOURCE = "/minic/system/imports.properties";
    private static final String INCLUDE_ROOT = "/minic/include/";
    private static final SystemLibraryCatalog DEFAULT = loadDefault();

    private final Map<String, String> imports;

    private SystemLibraryCatalog(Map<String, String> imports) {
        this.imports = Map.copyOf(imports);
    }

    public static SystemLibraryCatalog defaults() {
        return DEFAULT;
    }

    public Map<String, String> imports() {
        return imports;
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
        LinkedHashMap<String, String> imports = new LinkedHashMap<>();
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
                    throw new IllegalStateException("invalid system import at line " + lineNumber);
                }
                String symbol = value.substring(0, equals).strip();
                String library = value.substring(equals + 1).strip();
                if (imports.putIfAbsent(symbol, library) != null) {
                    throw new IllegalStateException("duplicate system import: " + symbol);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read system import catalog", exception);
        }
        return new SystemLibraryCatalog(imports);
    }

    public record Header(String name, String resourceName, String content) {
        public Header {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(resourceName, "resourceName");
            Objects.requireNonNull(content, "content");
        }
    }
}
