package minic.uilocal;

import java.util.List;

/**
 * MiniC UI 内置样例。
 */
public final class MiniCSamplePrograms {
    private static final List<MiniCSampleProgram> SAMPLES = List.of(
            new MiniCSampleProgram("main.mc", "int main() {\n    return 0;\n}\n"),
            new MiniCSampleProgram("arithmetic.mc", "int main() {\n    int x = 1 + 2 * 3;\n    return x;\n}\n"),
            new MiniCSampleProgram("if_else.mc", "int main() {\n    int x = 7;\n    if (x > 3) {\n        return x;\n    }\n    return 0;\n}\n")
    );

    private MiniCSamplePrograms() {
    }

    /**
     * 返回内置样例。
     *
     * @return 样例列表
     */
    public static List<MiniCSampleProgram> all() {
        return SAMPLES;
    }

    /**
     * 返回默认样例。
     *
     * @return 默认样例
     */
    public static MiniCSampleProgram defaultSample() {
        return SAMPLES.getFirst();
    }
}
