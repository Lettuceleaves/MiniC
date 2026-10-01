package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import static minic.cpp.CppReferenceTest.agree;
import static org.junit.jupiter.api.Assertions.*;

/** Object lifetime in raw storage: construct once, explicitly destroy once, release storage. */
@Tag("cpp-differential") @Timeout(90)
final class CppPlacementDestructorTest {
    @TempDir Path temporary;
    private static final String ALLOCATION="""
            #include <stdio.h>
            #include <stdlib.h>
            void* operator new(unsigned long long size,void* storage){return storage;}
            """;
    private static final String TRACE="struct T{int id;T(int n):id(n){printf(\"C%d \",id);}~T(){printf(\"D%d \",id);}};";

    static Stream<Arguments> programs() { return Stream.of(
            Arguments.of("raw-lifetime",TRACE+"int main(){void*s=malloc(64);T*p=new(s)T(7);printf(\"B \");p->~T();free(s);return 0;}","C7 B D7 "),
            Arguments.of("const-alias-lifetime",TRACE+"typedef T Alias;int main(){void*s=malloc(64);const Alias*p=new(s)const Alias(8);p->~Alias();free(s);return 0;}","C8 D8 "),
            Arguments.of("private-destructor-inside-member","class T{int id;~T(){printf(\"D%d \",id);}public:T(int n):id(n){}void dispose(){this->~T();}};int main(){void*s=malloc(64);T*p=new(s)T(9);p->dispose();free(s);return 0;}","D9 "),
            Arguments.of("explicit-owner-reverse-members",TRACE+"struct Owner{T first;T second;Owner():first(1),second(2){}~Owner(){printf(\"O \");}};int main(){void*s=malloc(64);Owner*p=new(s)Owner;p->~Owner();free(s);return 0;}","C1 C2 O D2 D1 "),
            Arguments.of("implicit-owner-reverse-members",TRACE+"struct Owner{T first;T second;Owner():first(1),second(2){}};int main(){void*s=malloc(64);Owner*p=new(s)Owner;p->~Owner();free(s);return 0;}","C1 C2 D2 D1 "),
            Arguments.of("out-of-line-injected-name","namespace N{struct T{int id;T(int n):id(n){}~T();};}N::T::~T(){printf(\"D%d \",id);}int main(){void*s=malloc(64);N::T*p=new(s)N::T(3);p->~T();free(s);return 0;}","D3 "),
            Arguments.of("repeated-storage-lifetimes",TRACE+"int main(){void*s=malloc(64);for(int i=1;i<4;++i){T*p=new(s)T(i);p->~T();}free(s);return 0;}","C1 D1 C2 D2 C3 D3 "),
            Arguments.of("nontrivial-receiver-once",TRACE+"T*next(T*p){printf(\"G \");return p;}int main(){void*s=malloc(64);T*p=new(s)T(4);next(p)->~T();free(s);return 0;}","C4 G D4 "),
            Arguments.of("scalar-raw-lifetime","typedef int I;int main(){void*s=malloc(16);I*p=new(s)I(5);printf(\"%d \",*p);p->~I();free(s);return 0;}","5 "));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void explicitCallsDestroyActualConstructedObjects(String name,String source,String expected) throws Exception {
        agree(temporary,name,ALLOCATION+source,expected);
    }

    @Test void debugHistoryReplaysConstructionDestructionAndStorageRelease() {
        String source=ALLOCATION+"""
                struct T{
                    int id;
                    T(int n):id(n){printf("C%d ",id);}
                    ~T(){printf("D%d ",id);}
                };
                int main(){
                    void*s=malloc(64);
                    T*p=new(s)T(6);
                    p->~T();
                    free(s);
                    return 0;
                }
                """;
        var debug=new DebugApi(new SourceFile("explicit-destruction.cpp",source),"",LanguageMode.CPP17_ALGORITHM);
        var history=new ArrayList<Debugger.Context>();history.add(debug.current());
        for(int count=0;debug.canNext()&&count<2500;count++)history.add(debug.next());
        assertFalse(debug.canNext());assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals("C6 D6 ",debug.current().runtime().stdout());assertTrue(debug.current().runtime().heap().isEmpty());
        assertTrue(history.stream().anyMatch(context->context.runtime().stack().stream()
                .anyMatch(frame->frame.function().equals("T::~T")&&frame.parameters().containsKey("this"))));
        for(int index=history.size()-2;index>=0;index--)assertSame(history.get(index),debug.previous());
        for(int index=1;index<history.size();index++)assertSame(history.get(index),debug.next());
        assertEquals("C6 D6 ",debug.current().runtime().stdout());assertTrue(debug.current().runtime().heap().isEmpty());
    }
}
