package minic.compiler;

import minic.compiler.ir.IrLowerer;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class UnicodeLiteralEndToEndTest {
    @Test
    void encodesUtf8Utf16Utf32AndUniversalCharacterEscapes() {
        SourceFile source = new SourceFile("unicode.mc", """
                int main() {
                    char *utf8 = u8"é";
                    unsigned short *wide = L"你\\u597d";
                    unsigned int *full = U"\\U0001F600";
                    return (unsigned char)utf8[0] == 0xC3
                        && (unsigned char)utf8[1] == 0xA9
                        && wide[0] == 0x4F60 && wide[1] == 0x597D
                        && full[0] == 0x1F600
                        && L'A' == 65 && U'\\U0001F600' == 0x1F600 ? 0 : 1;
                }
                """);
        var session = new CompilerApi(source);
        session.runThrough(session.stage(Linker.class));
        assertTrue(session.stage(Linker.class).succeeded(), () -> "parse=" + session.stage(Parser.class).errors()
                + ", semantic=" + session.stage(SemanticAnalyzer.class).errors()
                + ", obj=" + session.stage(ObjBuilder.class).errors());
        var ir = session.stage(IrLowerer.class).result();
        assertEquals(3, ir.stringData().size());
        assertArrayEquals(new byte[] {(byte) 0xC3, (byte) 0xA9, 0}, ir.stringData().get(0).bytes());
        var nativeRunStage = new ExecutableRunner();
        var nativeRun = nativeRunStage.run(source,
                session.stage(Linker.class).result().executableArtifactOptional().orElseThrow());
        assertEquals(0, nativeRun.exitCode(), () -> nativeRunStage.errors().toString());

        DebugApi api = new DebugApi(source);
        for (int budget = 20_000; api.canNext() && budget > 0; budget--) api.next();
        assertEquals(Debugger.Status.COMPLETED, api.current().stop().status(), api.current().stop().error());
        assertEquals(0, api.current().runtime().returnValue().integer());
    }
}
