package minic.cpp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static minic.cpp.CppReferenceTest.*;
@Timeout(90)
final class CppLazyConvertingConstructorTest {
    @TempDir Path temporary;
    @Test void prototypeParameterClassIsCompletedBeforeConversionSelection() throws Exception {
        agree(temporary,"lazy-conversion-before-definition", """
                #include <stdio.h>
                template<bool Constant>struct Iter{
                    int value;Iter(int n):value(n){}
                    template<bool Other>Iter(const Iter<Other>& other):value(other.value){}
                };
                void use(Iter<true>,Iter<true>);
                int main(){use(Iter<false>(3),Iter<false>(4));return 0;}
                void use(Iter<true>a,Iter<true>b){printf("%d %d\\n",a.value,b.value);}
                """, "3 4\n");
    }
    @Test void sameParameterConversionWorksBeforeAndAfterTheDefinition() throws Exception {
        agree(temporary,"lazy-conversion-overloads", """
                #include <stdio.h>
                template<class T>struct Box{T value;Box(T n):value(n){}};
                void take(Box<int>);void take(const char*);
                void before(){take(5);}
                void take(Box<int> box){printf("%d\\n",box.value);}
                void take(const char* text){printf("%s\\n",text);}
                int main(){before();take(7);take("text");return 0;}
                """, "5\n7\ntext\n");
    }
    @Test void selectedPrivateConversionStillFailsAccessChecking() throws Exception {
        reject(temporary,"lazy-conversion-private", """
                template<class T>class Box{Box(T value){}};
                void take(Box<int>);
                int main(){take(3);return 0;} // bad
                void take(Box<int> value){}
                """);
    }
    @Test void mapErasureConvertsItsTwoIteratorBounds() throws Exception {
        agree(temporary,"map-erase-iterator-conversion", """
                #include <map>
                #include <stdio.h>
                int main(){std::map<int,int> values;values[3]=7;values[5]=9;
                    unsigned long long erased=values.erase(3);
                    printf("%llu %llu %d\\n",erased,(unsigned long long)values.size(),values.begin()->second);return 0;}
                """, "1 1 9\n");
    }
}
