package minic.compiler.preprocess;

import minic.source.SourceFile;

/**
 * MiniC 预编译阶段入口。
 */
@FunctionalInterface
public interface Preprocessor {
    /**
     * 对源码执行预编译。
     *
     * @param sourceFile 原始源码
     * @return 预编译结果
     */
    PreprocessResult preprocess(SourceFile sourceFile);
}
