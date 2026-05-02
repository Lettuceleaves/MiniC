package minic.compiler.ast.decl;

import minic.compiler.type.MiniType;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeclarationAstTest {
    @Test
    void declarationListsAreDefensivelyCopiedAndImmutable() {
        SourceRange range = range("immutable.mc", "int main() {}");
        Parameter parameter = new Parameter("x", range);
        FunctionDecl functionDecl = new FunctionDecl("main", new ArrayList<>(), range);
        StructField field = new StructField("x", MiniType.INT, range);
        StructDecl structDecl = new StructDecl("Point", new ArrayList<>(), range);

        ArrayList<Parameter> parameters = new ArrayList<>();
        ArrayList<StructField> fields = new ArrayList<>();
        ArrayList<StructDecl> structs = new ArrayList<>();
        ArrayList<FunctionDecl> functions = new ArrayList<>();

        FunctionDecl copiedFunction = new FunctionDecl("sum", parameters, range);
        StructDecl copiedStruct = new StructDecl("Pair", fields, range);
        Program copiedProgram = new Program(structs, functions, range);

        parameters.add(parameter);
        fields.add(field);
        structs.add(structDecl);
        functions.add(functionDecl);

        assertThat(copiedFunction.parameters()).isEmpty();
        assertThat(copiedStruct.fields()).isEmpty();
        assertThat(copiedProgram.structs()).isEmpty();
        assertThat(copiedProgram.functions()).isEmpty();
        assertThatThrownBy(() -> copiedFunction.parameters().add(parameter))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> copiedStruct.fields().add(field))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> copiedProgram.structs().add(structDecl))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> copiedProgram.functions().add(functionDecl))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void declarationNamesMustNotBeBlank() {
        SourceRange range = range("invalid.mc", "int main() {}");

        assertThatThrownBy(() -> new FunctionDecl(" ", List.of(), range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Parameter("", range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructDecl(" ", List.of(), range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructField("", MiniType.INT, range))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SourceRange range(String path, String content) {
        SourceFile sourceFile = new SourceFile(path, content);
        return new SourceRange(sourceFile, 0, content.length());
    }
}
