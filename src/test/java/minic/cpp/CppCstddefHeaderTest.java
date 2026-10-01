package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrLowerer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CppCstddefHeaderTest {
    @Test void cHeaderRetainsItsWcharTypedef() {
        accepts(LanguageMode.C, """
                #include <stddef.h>
                int main(){wchar_t character=65;size_t length=sizeof(character);return length==2?0:1;}
                """);
    }

    @Test void cHeaderDoesNotRedeclareCppKeywords() {
        accepts(LanguageMode.CPP17_ALGORITHM, """
                #include <stddef.h>
                int main(){size_t length=sizeof(int);ptrdiff_t offset=-1;return length==4&&offset<0?0:1;}
                """);
    }

    @Test void cppWrapperPublishesNamesAndNullptrType() {
        accepts(LanguageMode.CPP17_ALGORITHM, """
                #include <cstddef>
                #include <stddef.h>
                int main(){std::size_t length=sizeof(int);std::ptrdiff_t offset=-1;
                    std::nullptr_t null=nullptr;int* pointer=null;std::max_align_t value=0;
                    return length==4&&offset<0&&pointer==nullptr&&value==0?0:1;}
                """);
    }

    private static void accepts(LanguageMode mode, String source) {
        var compiler=new CompilerApi(new SourceFile("stddef-header.cpp",source),mode);
        var ir=compiler.stages().stream().filter(IrLowerer.class::isInstance)
                .map(IrLowerer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(ir);
        assertTrue(ir.succeeded(),()->compiler.stages().stream()
                .flatMap(stage->stage.errors().stream()).toList().toString());
    }
}
