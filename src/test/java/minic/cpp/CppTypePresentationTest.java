package minic.cpp;

import minic.compiler.SymbolNames;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CppTypePresentationTest {
    @Test void unionTypeIdentitiesAndWholeIdentifiersArePresentedWithoutInternalPrefixes() {
        var names = Map.of("$union$minicCppSymbol7", "Data::Value", "minicCppSymbol8", "Box");
        assertEquals("struct Data::Value; struct Box; minicCppSymbol80",
                SymbolNames.displayText("struct $union$minicCppSymbol7; struct minicCppSymbol8; minicCppSymbol80", names));
    }
}
