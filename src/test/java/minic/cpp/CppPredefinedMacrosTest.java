package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.preprocess.Preprocessor;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class CppPredefinedMacrosTest {
    @Test void definesFrontendIdentityInBothModesAndStandardOnlyInCpp() {
        for (LanguageMode mode : LanguageMode.values()) {
            var preprocessor = new Preprocessor();
            preprocessor.preprocess(new SourceFile("macros.cpp", """
                    #if !defined(__MINIC__) || __MINIC__ != 1
                    bad_identity
                    #endif
                    #ifdef __cplusplus
                    cpp __cplusplus
                    #else
                    c_only
                    #endif
                    """), new Preprocessor.Options(List.of(), mode));
            assertTrue(preprocessor.succeeded(), () -> preprocessor.errors().toString());
            String output = preprocessor.preprocessResult().sourceFile().content();
            assertFalse(output.contains("bad_identity"));
            assertEquals(mode == LanguageMode.CPP17_ALGORITHM, output.contains("cpp 201703L"));
            assertEquals(mode == LanguageMode.C, output.contains("c_only"));
        }
    }
}
