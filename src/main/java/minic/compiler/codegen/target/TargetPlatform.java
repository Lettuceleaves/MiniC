package minic.compiler.codegen.target;

/**
 * MiniC 支持的目标平台。
 */
public enum TargetPlatform {
    /**
     * v0.1 首个目标平台：Windows x86_64，汇编文本采用 MASM 风格。
     */
    WINDOWS_X86_64("windows-x86_64");

    private final String id;

    TargetPlatform(String id) {
        this.id = id;
    }

    /**
     * 返回稳定平台标识。
     *
     * @return 平台标识
     */
    public String id() {
        return id;
    }
}
