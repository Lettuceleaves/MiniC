package craken.debug;

import java.util.Map;

/** 按标准库模块批量提供调试函数，避免中央注册表逐函数硬编码。 */
interface DebugLibraryProvider {
    String name();

    Map<String, DebugLibraryFunction> functions();
}
