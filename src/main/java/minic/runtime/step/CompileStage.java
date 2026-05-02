package minic.runtime.step;

import java.util.Locale;

/**
 * 编译观测阶段标识。
 */
public enum CompileStage {
    /**
     * 源码阶段。
     */
    SOURCE("source"),

    /**
     * 词法分析阶段。
     */
    LEXER("lexer"),

    /**
     * 语法分析阶段。
     */
    PARSER("parser"),

    /**
     * 语义分析阶段。
     */
    SEMANTIC("semantic"),

    /**
     * IR lowering 阶段。
     */
    IR("ir"),

    /**
     * 代码生成阶段。
     */
    CODEGEN("codegen"),

    /**
     * 工具链阶段。
     */
    TOOLCHAIN("toolchain");

    private final String id;

    CompileStage(String id) {
        this.id = id;
    }

    /**
     * 返回供 UI 和序列化使用的稳定阶段 ID。
     *
     * @return 稳定阶段 ID
     */
    public String id() {
        return id;
    }

    /**
     * 按稳定 ID 查找阶段。
     *
     * @param id 阶段 ID
     * @return 编译阶段
     */
    public static CompileStage fromId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (CompileStage stage : values()) {
            if (stage.id.equals(normalized)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("unknown compile stage id: " + id);
    }
}
