package craken.compiler.library;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 将一个 Craken 外部符号绑定到具体的 Windows 原生导出。
 *
 * <p>{@code sourceName} 用于解析 COFF 中的未定义符号，{@code exportName}
 * 写入 PE 导入表。二者允许不同，从而不需要在 Parser、IR 或 ASM 中硬编码别名。</p>
 */
public record LibraryBinding(
        String sourceName,
        String dllName,
        String exportName,
        LibrarySymbol.SymbolKind symbolKind,
        RuntimeFamily runtimeFamily,
        NativeCallingConvention callingConvention,
        NativeKind nativeKind
) {
    private static final Pattern DLL_NAME = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9_.-]*\\.dll",
            Pattern.CASE_INSENSITIVE
    );

    public LibraryBinding {
        // LibrarySymbol owns the source-language identifier validation.
        new LibrarySymbol(sourceName, symbolKind);
        Objects.requireNonNull(dllName, "dllName");
        Objects.requireNonNull(exportName, "exportName");
        Objects.requireNonNull(runtimeFamily, "runtimeFamily");
        Objects.requireNonNull(callingConvention, "callingConvention");
        Objects.requireNonNull(nativeKind, "nativeKind");
        if (!DLL_NAME.matcher(dllName).matches()) {
            throw new IllegalArgumentException("invalid DLL name for " + sourceName + ": " + dllName);
        }
        if (!isValidExportName(exportName)) {
            throw new IllegalArgumentException("invalid native export name for " + sourceName + ": " + exportName);
        }
    }

    private static boolean isValidExportName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (character < 0x21 || character > 0x7e || character == '|') {
                return false;
            }
        }
        return true;
    }

    public LibrarySymbol symbol() {
        return new LibrarySymbol(sourceName, symbolKind);
    }

    /** 提供原生实现并拥有其进程级状态的运行库家族。 */
    public enum RuntimeFamily {
        WINDOWS,
        MSVCRT,
        UCRT,
        CRAKEN
    }

    /** Craken 当前支持的原生调用约定。 */
    public enum NativeCallingConvention {
        WINDOWS_X64
    }

    /** 绑定的原生实现方式。 */
    public enum NativeKind {
        DLL_IMPORT,
        STATIC_WRAPPER,
        RUNTIME_SHIM,
        COMPILER_INTRINSIC
    }
}
