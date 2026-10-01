package minic.stdlib;

import minic.compiler.SourceFile;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class StdioOpaqueTypeTest {
    @Test void acceptsOpaqueFilePointersThroughTheHeader() {
        var fixture=CompilerFixture.fromSource(new SourceFile("stdio-opaque.c", """
            #include <stdio.h>
            FILE* identity(FILE* value){return value;}
            int main(){FILE* stream=stdin;return identity(stream)==stream?0:1;}
            """));
        fixture.compilerApi().runThrough(fixture.irLowerer());
        assertTrue(fixture.semanticAnalyzer().succeeded(),fixture.semanticAnalyzer().errors()::toString);
        assertTrue(fixture.irLowerer().succeeded(),fixture.irLowerer().errors()::toString);
    }
    @Test void fileRemainsIncompleteAndHasNoInventedLayout() {
        var fixture=CompilerFixture.fromSource(new SourceFile("stdio-incomplete.c", """
            #include <stdio.h>
            int main(){return sizeof(FILE);}
            """));
        fixture.compilerApi().runThrough(fixture.semanticAnalyzer());
        assertTrue(fixture.parser().succeeded(),fixture.parser().errors()::toString);
        assertFalse(fixture.semanticAnalyzer().succeeded());
        assertTrue(fixture.semanticAnalyzer().errors().stream().anyMatch(error ->
            error.code().equals("SEM001") && error.message().contains("sizeof")
                && error.range().startLine()==2),fixture.semanticAnalyzer().errors()::toString);
    }
}
