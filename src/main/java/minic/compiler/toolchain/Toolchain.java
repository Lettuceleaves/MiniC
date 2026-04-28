package minic.compiler.toolchain;

import minic.compiler.codegen.AssemblySource;
import minic.source.SourceFile;

import java.nio.file.Path;

/**
 * 目标平台工具链接口。
 */
public interface Toolchain {
    /**
     * 写出汇编并尝试生成可执行文件。
     *
     * @param sourceFile 源码文件，用于诊断 range
     * @param assemblySource 汇编文本
     * @param outputDirectory 输出目录
     * @param artifactName 产物基础名称
     * @return 工具链结果
     */
    ToolchainResult buildExecutable(
            SourceFile sourceFile,
            AssemblySource assemblySource,
            Path outputDirectory,
            String artifactName
    );
}
