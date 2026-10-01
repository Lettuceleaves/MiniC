package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppFieldLoadFusionTest {
    @TempDir Path temporary;

    @ParameterizedTest @EnumSource(LanguageMode.class)
    void fieldReadsPreserveEveryWidthSignednessAndPointerValue(LanguageMode mode)throws Exception {
        for(int padding:new int[]{0,128}) {
            String fields=padding==0?"":"char padding[128];";
            String source="""
                    #include <stdio.h>
                    #include <stdbool.h>
                    struct Values {
                    """+fields+"""
                      bool b; char c; signed char sc; unsigned char uc;
                      short s; unsigned short us; int i; unsigned int ui;
                      long l; unsigned long ul; long long ll; unsigned long long ull;
                      float f; double d; int *p;
                    };
                    void show(struct Values *v) {
                      printf("%d %d %d %d %d %d %d %d %d %d %d %d %d %d %d\\n",
                        v->b, v->c==-7, v->sc==-7, v->uc==207,
                        v->s==-30007, v->us==60007, v->i==-1234574, v->ui==4000000007U,
                        v->l==-1234574L, v->ul==4000000007UL,
                        v->ll==-9000000000007LL, v->ull==9223372036854775815ULL,
                        v->f==-1.5f, v->d==-2.25, *v->p==21);
                    }
                    int main() {
                      int seed=0; if(scanf("%d", &seed)!=1)return 2;
                      struct Values v; int target=seed*3;
                      v.b=seed!=0;v.c=-seed;v.sc=-seed;v.uc=200+seed;
                      v.s=-30000-seed;v.us=60000+seed;v.i=-1234567-seed;v.ui=4000000000U+seed;
                      v.l=-1234567L-seed;v.ul=4000000000UL+seed;
                      v.ll=-9000000000000LL-seed;v.ull=9223372036854775808ULL+seed;
                      v.f=-1.5f;v.d=-2.25;v.p=&target;show(&v);return 0;
                    }
                    """;
            check("widths-"+padding,source,"7\n","1 1 1 1 1 1 1 1 1 1 1 1 1 1 1\n",mode);
        }
    }

    @Test void typedFloatLoadsPreserveZeroSignsAndNanPayloads()throws Exception {
        check("payloads","""
                #include <stdio.h>
                #include <string.h>
                struct Bits { char pad[127]; float f; double d; };
                float read_float(struct Bits *p){return p->f;}
                double read_double(struct Bits *p){return p->d;}
                int main(){struct Bits value;unsigned int fbits[3]={0U,2147483648U,2143294004U};
                  unsigned long long dbits[3]={0ULL,9223372036854775808ULL,9221120237041090626ULL};
                  for(int i=0;i<3;++i){
                    memcpy(&value.f,&fbits[i],sizeof(float));memcpy(&value.d,&dbits[i],sizeof(double));
                    float f=read_float(&value);double d=read_double(&value);
                    unsigned int fcopy=0;unsigned long long dcopy=0;
                    memcpy(&fcopy,&f,sizeof(float));memcpy(&dcopy,&d,sizeof(double));
                    printf("%d %d\\n",fcopy==fbits[i],dcopy==dbits[i]);
                  }return 0;}
                ""","","1 1\n1 1\n1 1\n",LanguageMode.CPP17_ALGORITHM);
    }

    @Test void volatileReadsAndEffectfulReceiversKeepTheirReadAndCallOrder()throws Exception {
        check("effects","""
                #include <stdio.h>
                struct Value{int value;};struct Value object={4};int trace=0;
                struct Value *receiver(int digit){trace=trace*10+digit;return &object;}
                int read_volatile(volatile struct Value *p){return p->value;}
                void change(){object.value=9;}
                int main(){int first=receiver(1)->value;change();
                  int second=receiver(2)->value;int third=read_volatile(&object);
                  object.value=12;int fourth=read_volatile(&object);
                  printf("%d %d %d %d %d\\n",first,second,third,fourth,trace);return 0;}
                ""","","4 9 9 12 12\n",LanguageMode.CPP17_ALGORITHM);
    }

    private void check(String name,String source,String stdin,String expected,LanguageMode mode)throws Exception {
        var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(30),Duration.ofSeconds(15),200_000,1_048_576);
        for(var level:OptimizationLevel.values()) {
            var harness=new CppDifferentialHarness(temporary.resolve(level.name()),CppDifferentialHarness.referenceCompiler(System.getenv()),limits,mode,level);
            var report=harness.run(name,source,stdin);
            assertTrue(report.passed(),report::describe);
            for(var result:report.outcomes().values())assertEquals(expected,result.stdout().replace("\r\n","\n"),result.backend().name());
        }
    }
}
