package minic.compiler.codegen.windows;

/**
 * Windows x64 codegen 单步产出的汇编行类型。
 */
public enum WindowsX64AssemblyLineKind {
    /**
     * 文件头或符号声明行。
     */
    HEADER,

    /**
     * 只读数据区结构行。
     */
    CONST_SECTION,

    /**
     * 字符串数据行。
     */
    STRING_DATA,

    /**
     * 代码区结构行。
     */
    CODE_SECTION,

    /**
     * 入口函数行。
     */
    ENTRY_POINT,

    /**
     * 函数结构行。
     */
    FUNCTION_STRUCTURE,

    /**
     * 函数指令行。
     */
    INSTRUCTION,

    /**
     * 文件结束行。
     */
    END
}
