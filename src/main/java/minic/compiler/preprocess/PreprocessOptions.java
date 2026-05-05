package minic.compiler.preprocess;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 预编译阶段选项。
 *
 * @param includeRoots 显式 include 根目录
 */
public record PreprocessOptions(List<Path> includeRoots) {
    /**
     * 创建预编译选项。
     *
     * @param includeRoots 显式 include 根目录
     */
    public PreprocessOptions {
        Objects.requireNonNull(includeRoots, "includeRoots");
        includeRoots = includeRoots.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .toList();
    }

    /**
     * 创建默认预编译选项。
     *
     * @return 默认选项
     */
    public static PreprocessOptions defaults() {
        return new PreprocessOptions(List.of());
    }
}
