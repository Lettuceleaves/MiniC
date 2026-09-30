package minic.testing;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.Stage;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrLowerer;
import minic.compiler.lexer.Lexer;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;

/** Typed access to the production compiler pipeline for stage-level test assertions. */
public final class CompilerFixture {
    private final CompilerApi compilerApi;

    private CompilerFixture(SourceFile source) {
        compilerApi = new CompilerApi(source);
    }

    public static CompilerFixture fromSource(SourceFile source) {
        return new CompilerFixture(source);
    }

    public CompilerApi compilerApi() {
        return compilerApi;
    }

    public Preprocessor preprocessor() {
        return stage(Preprocessor.class);
    }

    public Lexer lexer() {
        return stage(Lexer.class);
    }

    public Parser parser() {
        return stage(Parser.class);
    }

    public SemanticAnalyzer semanticAnalyzer() {
        return stage(SemanticAnalyzer.class);
    }

    public IrLowerer irLowerer() {
        return stage(IrLowerer.class);
    }

    public Assembler assembler() {
        return stage(Assembler.class);
    }

    public ObjBuilder objBuilder() {
        return stage(ObjBuilder.class);
    }

    public Linker linker() {
        return stage(Linker.class);
    }

    private <T extends Stage> T stage(Class<T> type) {
        return compilerApi.stages().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("compiler pipeline is missing " + type.getName()));
    }
}
