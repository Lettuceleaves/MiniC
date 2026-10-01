package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.cpp.CppNameBinder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

final class CppIncompleteRecordDefinitionTest {
    @ParameterizedTest @ValueSource(strings={
        "struct Node {\n int first;\n Node value;\n};",
        "struct Node {\n int first;\n Node value[2];\n};",
        "template<class T>struct Node {\n int first;\n Node<T> value;\n};\nNode<int> instance;",
        "struct Node {\n typedef Node Self;\n const Self value;\n};",
        "struct Other;\nstruct Node {\n Other value;\n};\nstruct Other{Node value;};",
        "struct Other;\nstruct Node {\n Other value;\n};\nstruct Other{int value;};"
    })
    void incompleteValueMembersAreDiagnosedBeforeSpecialMemberPlanning(String declaration) {
        var source=parse(declaration+"\nint main(){return 0;}");
        var result=assertDoesNotThrow(()->CppNameBinder.bind(source));
        assertTrue(result.diagnostics().stream().anyMatch(d->d.message().contains("不完整") && d.range().startLine()==3),
                ()->result.diagnostics().toString());
    }

    @ParameterizedTest @ValueSource(strings={
        "struct Node{int value;Node*next;};",
        "struct Node{int value;Node*next[2];};",
        "template<class T>struct Node{T value;Node<T>*next;};Node<int> instance;",
        "struct Node{Node&other;};",
        "struct Other;struct Node{Other*next;};struct Other{Node*next;};",
        "struct Part{int value;};struct Node{Part first;Part rest[2];};"
    })
    void indirectionAndCompleteValueMembersRemainLegal(String declaration) {
        var bound=assertDoesNotThrow(()->CppNameBinder.bind(parse(declaration+"\nint main(){return 0;}")));
        assertTrue(bound.diagnostics().isEmpty(),()->bound.diagnostics().toString());
        var semantic=new SemanticAnalyzer(bound.program());semantic.analyze();
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
    }

    private static Program parse(String source) {
        var lexer=new Lexer(new SourceFile("incomplete-record.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var parser=new Parser(lexer.lex().tokens(),LanguageMode.CPP17_ALGORITHM,false);parser.parse();
        assertTrue(parser.succeeded(),()->parser.errors().toString());return parser.result().program();
    }
}
