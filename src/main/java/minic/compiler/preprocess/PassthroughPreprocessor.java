package minic.compiler.preprocess;

import minic.source.SourceFile;

import java.util.Objects;

/**
 * 当前阶段使用的直通预编译器。
 */
public final class PassthroughPreprocessor implements Preprocessor {
    /**
     * 原样返回源码，不展开 include 或宏。
     *
     * @param sourceFile 原始源码
     * @return 直通预编译结果
     */
    @Override
    public PreprocessResult preprocess(SourceFile sourceFile) {
        return PreprocessResult.passthrough(Objects.requireNonNull(sourceFile, "sourceFile"));
    }
}
