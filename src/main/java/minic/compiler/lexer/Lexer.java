package minic.compiler.lexer;

import minic.source.SourceFile;

import java.util.Objects;

/**
 * MiniC 词法分析器。
 */
public final class Lexer {
    private final SourceFile sourceFile;

    /**
     * 创建词法分析器。
     *
     * @param sourceFile 待分析源码文件
     */
    public Lexer(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
    }

    /**
     * 执行词法分析。
     *
     * @return 词法分析结果
     */
    public LexResult lex() {
        LexerState state = new LexerState(sourceFile);
        while (state.canNext()) {
            state.next();
        }
        return state.toLexResult();
    }
}
