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

        assertThat(program.structs()).isEmpty();
        assertThat(program.functions()).containsExactly(functionDecl);
        assertThat(program.range()).isSameAs(programRange);
        assertThat(functionDecl.name()).isEqualTo("add");
        assertThat(functionDecl.parameters()).containsExactly(parameter);
        assertThat(functionDecl.external()).isFalse();
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
        ArrayList<StructField> fields = new ArrayList<>();
        ArrayList<StructDecl> structs = new ArrayList<>();
        ArrayList<FunctionDecl> functions = new ArrayList<>();

        FunctionDecl functionDecl = new FunctionDecl("main", parameters, range);
        StructDecl structDecl = new StructDecl("Point", fields, range);
        Program program = new Program(structs, functions, range);
        parameters.add(parameter);
        fields.add(new StructField("x", minic.compiler.type.MiniType.INT, range));
        structs.add(structDecl);
        functions.add(functionDecl);

        assertThat(functionDecl.parameters()).isEmpty();
        assertThat(structDecl.fields()).isEmpty();
        assertThat(program.structs()).isEmpty();
        assertThat(program.functions()).isEmpty();
        assertThatThrownBy(() -> functionDecl.parameters().add(parameter))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> structDecl.fields().add(new StructField("y", minic.compiler.type.MiniType.INT, range)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> program.functions().add(functionDecl))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void storesStructDeclarationFields() {
        SourceFile sourceFile = new SourceFile("struct.mc", "struct Point { int x; int y; };");
        SourceRange range = new SourceRange(sourceFile, 0, sourceFile.content().length());
        StructField x = new StructField("x", minic.compiler.type.MiniType.INT, new SourceRange(sourceFile, 15, 21));
        StructField y = new StructField("y", minic.compiler.type.MiniType.INT, new SourceRange(sourceFile, 22, 28));

        StructDecl structDecl = new StructDecl("Point", List.of(x, y), range);

        assertThat(structDecl.name()).isEqualTo("Point");
        assertThat(structDecl.fields()).containsExactly(x, y);
        assertThat(structDecl.range()).isSameAs(range);
    }

    @Test
    void rejectsBlankNames() {
        SourceFile sourceFile = new SourceFile("invalid.mc", "int main() {}");
        SourceRange range = new SourceRange(sourceFile, 0, sourceFile.content().length());

        assertThatThrownBy(() -> new FunctionDecl(" ", List.of(), range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Parameter("", range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructDecl(" ", List.of(), range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructField("", minic.compiler.type.MiniType.INT, range))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesExternalFunctionDeclarationFlag() {
        SourceFile sourceFile = new SourceFile("extern.mc", "extern int puts(int value);");
        SourceRange range = new SourceRange(sourceFile, 0, sourceFile.content().length());

        FunctionDecl functionDecl = new FunctionDecl("puts", List.of(), true, range);

        assertThat(functionDecl.external()).isTrue();
        assertThat(functionDecl.hasBody()).isFalse();
        assertThat(functionDecl.bodyOptional()).isEmpty();
    }
}
