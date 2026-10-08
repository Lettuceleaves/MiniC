package craken.ui.editor.completion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 补全数据源：语言关键词，以及 {@code <...>} 与 {@code "..."} 两种 include 形式的头文件名单。
 * 关键词顺序与 Lexer 接受的关键词一致；头文件名单按项目 {@code lib} 目录和当前源码目录生成。
 */
public final class CompletionCatalog {
    /** 与 Lexer 关键词一致的展示顺序。 */
    public static final List<String> KEYWORDS = List.of(
            // 基础类型
            "bool", "char", "int", "long", "short", "signed", "unsigned",
            "float", "double", "void",
            // 类型修饰与声明
            "const", "volatile", "restrict", "typedef", "extern", "static", "inline",
            "struct", "union", "enum",
            // 控制流
            "return", "if", "else", "while", "do", "for", "break", "continue",
            "switch", "case", "default",
            // 运算符与查询
            "sizeof", "alignof", "alignas", "noreturn", "static_assert",
            // 字面量
            "true", "false", "NULL", "nullptr",
            // C++ 扩展
            "namespace", "using", "class", "template", "typename",
            "public", "private", "protected", "this", "operator",
            "auto", "decltype", "constexpr", "noexcept", "new", "delete", "explicit",
            // 替代运算符拼写
            "and", "or", "not", "bitand", "bitor", "xor", "compl",
            "and_eq", "or_eq", "xor_eq", "not_eq"
    );

    private static final Set<String> KEYWORD_SET = Set.copyOf(KEYWORDS);
    private static final String HEADER_SUFFIX = ".mh";
    private static final String STL_DIRECTORY = "stl";
    private static final String BUNDLE = "bits/stdc++.h";

    private final List<String> angleHeaders;
    private final List<String> quoteHeaders;

    public CompletionCatalog(List<String> angleHeaders, List<String> quoteHeaders) {
        Objects.requireNonNull(angleHeaders, "angleHeaders");
        Objects.requireNonNull(quoteHeaders, "quoteHeaders");
        this.angleHeaders = List.copyOf(new LinkedHashSet<>(angleHeaders));
        this.quoteHeaders = List.copyOf(new LinkedHashSet<>(quoteHeaders));
    }

    public List<String> keywords() {
        return KEYWORDS;
    }

    public boolean isKeyword(String text) {
        return KEYWORD_SET.contains(text);
    }

    /** {@code <...>} 中可用的头文件名：C 头带 .h，STL 头用裸名。 */
    public List<String> angleHeaders() {
        return angleHeaders;
    }

    /** {@code "..."} 中可用的头文件名：本目录与库根目录的 .mh 拼写。 */
    public List<String> quoteHeaders() {
        return quoteHeaders;
    }

    /** 从库根目录与源码目录收集头文件名单；目录不存在时该部分为空。 */
    public static CompletionCatalog fromDirectories(Path libraryRoot, Path sourceDirectory) {
        ArrayList<String> angle = new ArrayList<>();
        ArrayList<String> quote = new ArrayList<>();
        if (libraryRoot != null && Files.isDirectory(libraryRoot)) {
            for (String name : headerNames(libraryRoot)) {
                angle.add(name + ".h");
                quote.add(name + HEADER_SUFFIX);
            }
            for (String name : headerNames(libraryRoot.resolve(STL_DIRECTORY))) {
                angle.add(name);
            }
            angle.add(BUNDLE);
        }
        if (sourceDirectory != null && Files.isDirectory(sourceDirectory)) {
            List<String> local = headerNames(sourceDirectory).stream()
                    .map(name -> name + HEADER_SUFFIX)
                    .toList();
            quote.addAll(0, local);
        }
        return new CompletionCatalog(angle, quote);
    }

    /** 目录下可直接引用的 .mh 文件名（去掉后缀）；排除 {@code __} 开头的内部实现。 */
    private static List<String> headerNames(Path directory) {
        ArrayList<String> names = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return names;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(HEADER_SUFFIX))
                    .map(name -> name.substring(0, name.length() - HEADER_SUFFIX.length()))
                    .filter(name -> !name.isEmpty() && !name.startsWith("__"))
                    .distinct()
                    .sorted()
                    .forEach(names::add);
        } catch (IOException failure) {
            throw new IllegalStateException("failed to list headers in " + directory, failure);
        }
        return names;
    }
}
