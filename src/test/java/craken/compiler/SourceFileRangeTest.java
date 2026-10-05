package craken.compiler;

import craken.SourceRange;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** Source positions stay exact while long lines convert ranges in constant amortized time. */
class SourceFileRangeTest {
    @Test void rangeKeepsUtf8ByteIndexesAcrossAsciiCjkAndSupplementaryCharacters() {
        // "A"=1 byte, "中"=3 bytes, "😀"=4 bytes.
        var source = new SourceFile("unicode.mc", "A中😀B\n第二行");
        assertEquals(new SourceRange(1, 0, 1, 1), source.range(0, 1));
        assertEquals(new SourceRange(1, 1, 1, 4), source.range(1, 2));
        assertEquals(new SourceRange(1, 4, 1, 8), source.range(2, 4));
        assertEquals(new SourceRange(1, 8, 1, 9), source.range(4, 5));
        assertEquals(new SourceRange(2, 0, 2, 3), source.range(6, 7));
        assertEquals(0, source.offsetAt(1, 0));
        assertEquals(2, source.offsetAt(1, 4));
        assertEquals(6, source.offsetAt(2, 0));
        assertEquals("中", source.text(new SourceRange(1, 1, 1, 4)));
        assertEquals("😀", source.text(new SourceRange(1, 4, 1, 8)));
    }

    @Test void aHundredThousandTokenSingleLineConvertsRangesInLinearTime() {
        var line = "x+".repeat(150_000);
        var source = new SourceFile("long-line.mc", line);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            long bytes = 0;
            for (int offset = 0; offset < line.length(); offset += 2) {
                var range = source.range(offset, offset + 1);
                bytes += range.endByte() - range.startByte();
            }
            assertTrue(bytes > 0);
            assertEquals("x", source.text(new SourceRange(1, 4000, 1, 4001)));
            return null;
        });
    }
}
