package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
final class CppCstringHeaderTest {
    @TempDir Path temporary;

    @Test void mutableAndConstSearchesAgreeAcrossAllBuilds() throws Exception {
        String source="""
                #include <cstring>
                #include <cstdio>
                int main(){char text[]="abca";const char* input=text;
                    char* a=std::strchr(text,'a');const char* ca=std::strchr(input,'a');
                    char* b=std::strrchr(text,'a');const char* cb=std::strrchr(input,'a');
                    char* c=std::strpbrk(text,"bc");const char* cc=std::strpbrk(input,"bc");
                    char* d=std::strstr(text,"bc");const char* cd=std::strstr(input,"bc");
                    void* e=std::memchr(text,'c',4);const void* ce=std::memchr(input,'c',4);
                    *a='z';std::printf("%d %d %d %d %d %s\\n",a==ca,b==cb,c==cc,d==cd,e==ce,text);
                    return 0;}
                """;
        var limits=CppDifferentialHarness.Limits.defaults();
        var report=new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), limits,
                LanguageMode.CPP17_ALGORITHM).run("cstring-overloads",source,"");
        var own=CppOwnLibraryReference.run(temporary,source,"",limits);
        assertTrue(report.passed(),report::describe);
        assertTrue(own.passed(),own::toString);
        assertEquals("1 1 1 1 1 zbca\n",own.stdout().replace("\r\n","\n"));
    }
}
