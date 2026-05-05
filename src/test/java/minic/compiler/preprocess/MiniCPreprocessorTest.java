package minic.compiler.preprocess;

import minic.source.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCPreprocessorTest {
    @TempDir
    Path tempDir;

    @Test
    void expandsMhIncludesFromSourceDirectoryInStableOrder() throws Exception {
        Path nestedPath = tempDir.resolve("nested.mh");
        Path headerPath = tempDir.resolve("defs.mh");
        Path sourcePath = tempDir.resolve("main.mc");
        Files.writeString(nestedPath, "extern int nested();\n");
        Files.writeString(headerPath, "#include \"nested.mh\"\nextern int value();\n");
        SourceFile sourceFile = new SourceFile(sourcePath.toString(), "#include \"defs.mh\"\nint main() { return value(); }\n");

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.sourceFile().content()).isEqualTo("""
                extern int nested();
                extern int value();
                int main() { return value(); }
                """);
        assertThat(result.includes())
                .extracting(IncludeSummary::requestedPath)
                .containsExactly("defs.mh", "nested.mh");
        assertThat(result.includes())
                .extracting(IncludeSummary::expanded)
                .containsExactly(true, true);
        assertThat(result.includes().getFirst().resolvedPathOptional()).contains(headerPath.toAbsolutePath().normalize());
    }

    @Test
    void resolvesIncludesFromExplicitRoots() throws Exception {
        Path includeRoot = tempDir.resolve("include");
        Files.createDirectories(includeRoot);
        Files.writeString(includeRoot.resolve("lib.mh"), "extern int lib();\n");
        SourceFile sourceFile = new SourceFile("main.mc", "#include \"lib.mh\"\nint main() { return lib(); }\n");

        PreprocessResult result = new MiniCPreprocessor().preprocess(
                sourceFile,
                new PreprocessOptions(List.of(includeRoot))
        );

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.sourceFile().content()).contains("extern int lib();");
        assertThat(result.includes()).hasSize(1);
    }

    @Test
    void rejectsNonMhIncludeSuffix() {
        SourceFile sourceFile = new SourceFile("main.mc", "#include \"stdio.h\"\nint main() { return 0; }\n");

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.includes()).hasSize(1);
        assertThat(result.includes().getFirst().expanded()).isFalse();
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("include 目标必须使用 .mh 后缀：stdio.h");
    }

    @Test
    void reportsMissingIncludeFile() {
        SourceFile sourceFile = new SourceFile(tempDir.resolve("main.mc").toString(), "#include \"missing.mh\"\n");

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.includes()).hasSize(1);
        assertThat(result.includes().getFirst().expanded()).isFalse();
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("include 文件不存在：missing.mh");
    }

    @Test
    void detectsIncludeCycles() throws Exception {
        Path aPath = tempDir.resolve("a.mh");
        Path bPath = tempDir.resolve("b.mh");
        Files.writeString(aPath, "#include \"b.mh\"\n");
        Files.writeString(bPath, "#include \"a.mh\"\n");
        SourceFile sourceFile = new SourceFile(tempDir.resolve("main.mc").toString(), "#include \"a.mh\"\n");

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.includes())
                .extracting(IncludeSummary::requestedPath)
                .containsExactly("a.mh", "b.mh", "a.mh");
        assertThat(result.includes())
                .extracting(IncludeSummary::expanded)
                .containsExactly(true, true, false);
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("检测到 include 循环：a.mh");
    }

    @Test
    void definesAndUndefinesObjectMacros() {
        SourceFile sourceFile = new SourceFile("main.mc", """
                #define VALUE 42
                #define PRESENT
                int main() { return VALUE; }
                #undef VALUE
                int other() { return VALUE; }
                """);

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.sourceFile().content()).isEqualTo("""
                int main() { return 42; }
                int other() { return VALUE; }
                """);
        assertThat(result.macros())
                .extracting(MacroSummary::name)
                .containsExactly("VALUE", "PRESENT", "VALUE");
        assertThat(result.macros())
                .extracting(MacroSummary::defined)
                .containsExactly(true, true, false);
        assertThat(result.macros())
                .extracting(MacroSummary::replacement)
                .containsExactly("42", "", "");
    }

    @Test
    void replacesMacrosOnlyAtIdentifierBoundariesAndOutsideStrings() {
        SourceFile sourceFile = new SourceFile("main.mc", """
                #define VALUE 7
                int VALUE2 = VALUE;
                char *text = "VALUE";
                char c = 'V';
                """);

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.sourceFile().content()).isEqualTo("""
                int VALUE2 = 7;
                char *text = "VALUE";
                char c = 'V';
                """);
    }

    @Test
    void reportsInvalidAndDirectSelfReferentialMacros() {
        SourceFile sourceFile = new SourceFile("main.mc", """
                #define 1BAD 1
                #define LOOP LOOP
                int main() { return 0; }
                """);

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.sourceFile().content()).isEqualTo("int main() { return 0; }\n");
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("define 指令必须使用对象宏名称", "宏不能直接自引用：LOOP");
    }

    @Test
    void macrosCanBeSharedAcrossIncludedSources() throws Exception {
        Path headerPath = tempDir.resolve("defs.mh");
        Files.writeString(headerPath, "#define VALUE 9\n");
        SourceFile sourceFile = new SourceFile(
                tempDir.resolve("main.mc").toString(),
                "#include \"defs.mh\"\nint main() { return VALUE; }\n"
        );

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.sourceFile().content()).isEqualTo("int main() { return 9; }\n");
    }

    @Test
    void supportsIfdefIfndefElseAndNestedConditions() {
        SourceFile sourceFile = new SourceFile("main.mc", """
                #define ENABLED
                #ifdef ENABLED
                int enabled() { return 1; }
                #ifndef MISSING
                int nested() { return 2; }
                #else
                int hiddenNested() { return @; }
                #endif
                #else
                int hidden() { return @; }
                #endif
                """);

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.sourceFile().content()).isEqualTo("""
                int enabled() { return 1; }
                int nested() { return 2; }
                """);
    }

    @Test
    void reportsConditionStructureDiagnostics() {
        SourceFile sourceFile = new SourceFile("main.mc", """
                #else
                #endif
                #ifdef FLAG
                int hidden() { return @; }
                #else
                int shown() { return 1; }
                #else
                #ifndef OTHER
                int missingEndif() { return 2; }
                """);

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.sourceFile().content()).contains("int shown() { return 1; }", "int missingEndif() { return 2; }");
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly(
                        "孤立的 #else",
                        "多余的 #endif",
                        "同一条件编译块不能出现多个 #else",
                        "条件编译块缺少 #endif",
                        "条件编译块缺少 #endif"
                );
    }

    @Test
    void excludedBranchesDoNotTriggerDirectiveDiagnostics() {
        SourceFile sourceFile = new SourceFile("main.mc", """
                #ifdef MISSING
                #include <bad.h>
                int hidden() { return @; }
                #else
                int main() { return 0; }
                #endif
                """);

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.sourceFile().content()).isEqualTo("int main() { return 0; }\n");
    }

    @Test
    void acceptsHeaderDeclarationsAndRejectsHeaderFunctionDefinitions() throws Exception {
        Path goodHeader = tempDir.resolve("good.mh");
        Path badHeader = tempDir.resolve("bad.mh");
        Files.writeString(goodHeader, """
                struct Point { int x; int y; };
                extern int runtime(char *format, ...);
                int declared(int value);
                """);
        Files.writeString(badHeader, "int defined() { return 1; }\n");
        SourceFile goodSource = new SourceFile(tempDir.resolve("good-main.mc").toString(), "#include \"good.mh\"\nint main() { return declared(1); }\n");
        SourceFile badSource = new SourceFile(tempDir.resolve("bad-main.mc").toString(), "#include \"bad.mh\"\nint main() { return 0; }\n");

        PreprocessResult goodResult = new MiniCPreprocessor().preprocess(goodSource);
        PreprocessResult badResult = new MiniCPreprocessor().preprocess(badSource);

        assertThat(goodResult.diagnostics()).isEmpty();
        assertThat(goodResult.sourceFile().content()).contains(
                "struct Point",
                "extern int runtime(char *format, ...);",
                "int declared(int value);"
        );
        assertThat(badResult.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("头文件不能包含函数定义：defined");
    }

    @Test
    void rejectsHeaderExecutableStatementsAndInvalidContent() throws Exception {
        Path header = tempDir.resolve("bad.mh");
        Files.writeString(header, "return 1;\n");
        SourceFile sourceFile = new SourceFile(tempDir.resolve("main.mc").toString(), "#include \"bad.mh\"\nint main() { return 0; }\n");

        PreprocessResult result = new MiniCPreprocessor().preprocess(sourceFile);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("头文件只能包含函数声明、外部函数声明和结构体声明");
    }
}
