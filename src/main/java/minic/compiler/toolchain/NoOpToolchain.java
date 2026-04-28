package minic.compiler.toolchain;

import minic.compiler.codegen.AssemblySource;
import minic.source.SourceFile;

import java.nio.file.Path;

/**
 * 不执行外部工具链的占位实现。
 */
public final class NoOpToolchain implements Toolchain {
    @Override
    public ToolchainResult buildExecutable(
            SourceFile sourceFile,
            AssemblySource assemblySource,
            Path outputDirectory,
            String artifactName
    ) {
        return ToolchainResult.notRun();
    }
}
