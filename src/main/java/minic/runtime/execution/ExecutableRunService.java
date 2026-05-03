package minic.runtime.execution;

import minic.compiler.toolchain.ExecutableArtifact;
import minic.source.SourceFile;

/**
 * 可执行文件运行服务。
 */
@FunctionalInterface
public interface ExecutableRunService {
    /**
     * 运行可执行产物。
     *
     * @param sourceFile 源码文件
     * @param artifact 可执行产物
     * @param standardInput 标准输入文本
     * @return 运行结果
     */
    ExecutionResult run(SourceFile sourceFile, ExecutableArtifact artifact, String standardInput);
}
