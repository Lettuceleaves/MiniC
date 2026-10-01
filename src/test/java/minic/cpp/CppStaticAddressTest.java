package minic.cpp;

import minic.compiler.*;
import minic.compiler.ir.model.IrGlobalData;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Static addresses must exist before startup actions. All cases are queued, not run during assembly. */
@Timeout(120) final class CppStaticAddressTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of(LanguageMode.C,"forward-array","extern int a[3];int *p=a+1;int a[3]={4,5,6};int main(){printf(\"%d %d\\n\",*p,p==&a[1]);return 0;}","5 1\n"),
        Arguments.of(LanguageMode.C,"function-pointer","int twice(int x){return x*2;}int(*fp)(int)=twice;int main(){printf(\"%d\\n\",fp(7));return 0;}","14\n"),
        Arguments.of(LanguageMode.C,"string-offset","const char *p=\"hello\"+2;int main(){printf(\"%s\\n\",p);return 0;}","llo\n"),
        Arguments.of(LanguageMode.C,"aggregate-addresses","struct Node{int value;struct Node*next;};extern struct Node later;struct Node first={3,&later};struct Node later={7,&first};int *member=&later.value;int main(){printf(\"%d %d %d\\n\",first.next->value,later.next->value,*member);return 0;}","7 3 7\n"),
        Arguments.of(LanguageMode.C,"array-member-decay","struct Data{char tag;int values[3];};struct Data d={1,{8,9,10}};int *p=d.values+2;int main(){printf(\"%d %d\\n\",*p,p==&d.values[2]);return 0;}","10 1\n"),
        Arguments.of(LanguageMode.C,"external-function-address","int(*output)(const char*)=puts;int main(){output(\"ready\");return 0;}","ready\n"),
        Arguments.of(LanguageMode.C,"truncated-address-offset","int a[300]={9};int *p=a+(unsigned char)256;int *q=&a[(unsigned short)65536];int main(){printf(\"%d %d %d\\n\",p==a,q==a,*p);return 0;}","1 1 9\n"),
        Arguments.of(LanguageMode.C,"short-circuit-address-offset","int a[2]={4,7};int *p=a+(0&&(1/0));int *q=a+(1||(1/0));int*r=0?&a[1/0]:a;int main(){printf(\"%d %d %d\\n\",*p,*q,*r);return 0;}","4 7 4\n"),
        Arguments.of(LanguageMode.C,"unsigned-and-wide-offset","int a[2]={5,8};int *p=a+((unsigned int)-1<0);int*q=(9007199254740993LL==9007199254740992LL)?a+1:a;int*r=a+((4294967295U+1U)==0);int main(){printf(\"%d %d %d\\n\",*p,*q,*r);return 0;}","5 5 8\n"),
        Arguments.of(LanguageMode.C,"typed-float-offset","int a[2]={2,6};int*p=a+((int)(float)16777217.0==16777216);int*q=a+(int)0.75;int main(){printf(\"%d %d\\n\",*p,*q);return 0;}","6 2\n"),
        Arguments.of(LanguageMode.CPP17_ALGORITHM,"constexpr-before-dynamic","extern const int *const address;int read(){return *address;}int observed=read();int target=13;constexpr const int *address=&target;int main(){printf(\"%d %d\\n\",observed,*address);return 0;}","13 13\n"),
        Arguments.of(LanguageMode.CPP17_ALGORITHM,"constexpr-aggregate-pointer","struct Ref{const int *p;constexpr Ref(const int *v):p(v){}};extern const Ref value;int read(){return *value.p;}int observed=read();int target=17;constexpr Ref value(&target);int main(){printf(\"%d %d\\n\",observed,*value.p);return 0;}","17 17\n"),
        Arguments.of(LanguageMode.CPP17_ALGORITHM,"constexpr-array-and-function","constexpr int f(int v){return v+3;}constexpr int values[3]={2,4,6};constexpr const int*p=&values[1];constexpr int(*fn)(int)=f;static_assert(*p==4);static_assert(fn(2)==5);int main(){printf(\"%d %d\\n\",*p,fn(5));return 0;}","4 8\n")
    );}
    @ParameterizedTest(name="{1}") @MethodSource("programs")
    void addressesAgreeInNativeDebugAndCpp(LanguageMode mode,String name,String body,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                new CppDifferentialHarness.Limits(Duration.ofSeconds(40),Duration.ofSeconds(10),100000,1048576),mode)
                .run(name,"#include <stdio.h>\n"+body,"");
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"),report::describe);
        assertTrue(report.passed(),report::describe);
    }
    @Test void loweringKeepsSymbolAndScaledAddend(){
        var ir=new CompilerApi(new SourceFile("addresses.c","int a[4]={1,2,3,4};int*p=a+2;int main(){return *p;}"),LanguageMode.C).runToIr();
        var p=ir.globalData().stream().filter(g->g.label().equals("p")).findFirst().orElseThrow();
        assertEquals(java.util.List.of(new IrGlobalData.Address(0,"a",8,IrGlobalData.AddressKind.OBJECT)),p.addresses());
        assertArrayEquals(new byte[8],p.bytes());
    }
    @Test void integerCastsAndUnsignedComparisonsAreRetainedInAddressAddends(){
        var ir=new CompilerApi(new SourceFile("typed-addresses.c","int a[300];int*p=a+(unsigned char)256;int*q=a+((unsigned)-1<0);int*r=a+(4294967295U+1U);int main(){return 0;}"),LanguageMode.C).runToIr();
        for(String name:java.util.List.of("p","q","r")) {
            var global=ir.globalData().stream().filter(g->g.label().equals(name)).findFirst().orElseThrow();
            assertEquals(java.util.List.of(new IrGlobalData.Address(0,"a",0,IrGlobalData.AddressKind.OBJECT)),global.addresses(),name);
        }
    }
}
