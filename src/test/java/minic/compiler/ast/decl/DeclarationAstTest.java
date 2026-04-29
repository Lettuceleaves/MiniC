package minic.compiler.ast.decl;

import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeclarationAstTest {
    @Test
    void storesProgramFunctionAndParameterFields() {
        SourceFile sourceFile = new SourceFile("decl.mc", "int add(int x) {}");
        SourceRange programRange = new SourceRange(sourceFile, 0, sourceFile.content().length());
        SourceRange functionRange = new SourceRange(sourceFile, 0, 17);
        SourceRange parameterRange = new SourceRange(sourceFile, 8, 13);
        Parameter parameter = new Parameter("x", parameterRange);
        FunctionDecl functionDecl = new FunctionDecl("add", List.of(parameter), functionRange);

        Program program = new Program(List.of(functionDecl), programRange);

        assertThat(program.functions()).containsExactly(functionDecl);
        assertThat(program.range()).isSameAs(programRange);
        assertThat(functionDecl.name()).isEqualTo("add");
        assertThat(functionDecl.parameters()).containsExactly(parameter);
        assertThat(functionDecl.hasBody()).isFalse();
        assertThat(functionDecl.bodyOptional()).isEmpty();
        assertThat(functionDecl.range()).isSameAs(functionRange);
        assertThat(parameter.name()).isEqualTo("x");
        assertThat(parameter.range()).isSameAs(parameterRange);
    }

    @Test
    void defensivelyCopiesLists() {
        SourceFile sourceFile = new SourceFile("immutable.mc", "int main() {}");
        SourceRange range = new SourceRange(sourceFile, 0, sourceFile.content().length());
        Parameter parameter = new Parameter("x", new SourceRange(sourceFile, 0, 1));
        ArrayList<Parameter> parameters = new ArrayList<>();
        ArrayList<FunctionDecl> functions = new ArrayList<>();

        FunctionDecl functionDecl = new FunctionDecl("main", parameters, range);
        Program program = new Program(functions, range);
        parameters.add(parameter);
        functions.add(functionDecl);

        assertThat(functionDecl.parameters()).isEmpty();
        assertThat(program.functions()).isEmpty();
        assertThatThrownBy(() -> functionDecl.parameters().add(parameter))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> program.functions().add(functionDecl))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankNames() {
        SourceFile sourceFile = new SourceFile("invalid.mc", "int main() {}");
        SourceRange range = new SourceRange(sourceFile, 0, sourceFile.content().length());

        assertThatThrownBy(() -> new FunctionDecl(" ", List.of(), range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Parameter("", range))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
