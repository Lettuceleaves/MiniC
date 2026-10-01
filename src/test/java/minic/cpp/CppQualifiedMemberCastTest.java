package minic.cpp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static minic.cpp.CppReferenceTest.*;
@Timeout(90)
final class CppQualifiedMemberCastTest {
    @TempDir Path temporary;
    @Test void scalarMemberAliasCastsAndQueries() throws Exception {
        agree(temporary,"class-alias-casts", """
                #include <stdio.h>
                struct Model{typedef unsigned long long size_type;};
                int main(){int n=3;Model::size_type size=(Model::size_type)n;
                    printf("%llu %d %d\\n",size,(int)sizeof(Model::size_type),(int)alignof(Model::size_type));return 0;}
                """, "3 8 8\n");
    }
    @Test void pointerAndReferenceMemberAliasesRemainTypeIds() throws Exception {
        agree(temporary,"class-alias-pointer-casts", """
                #include <stdio.h>
                struct Model{typedef int value_type;};
                int main(){int value=7;void* erased=&value;Model::value_type* pointer=(Model::value_type*)erased;
                    Model::value_type& ref=(Model::value_type&)value;ref=9;
                    printf("%d %d\\n",*pointer,(Model::value_type*)0==nullptr);return 0;}
                """, "9 1\n");
    }
    @Test void functionalMemberAliasConstructionIsAnExpression() throws Exception {
        agree(temporary,"class-alias-functional", """
                #include <stdio.h>
                struct Model{typedef int value_type;};
                int main(){printf("%d %d\\n",Model::value_type(4),Model::value_type{});return 0;}
                """, "4 0\n");
    }
    @Test void functionPointerAliasesKeepNoexceptInCasts() throws Exception {
        agree(temporary,"class-alias-noexcept-function", """
                #include <stdio.h>
                struct Model{typedef int(*function)() noexcept;};
                int safe()noexcept{return 8;}
                int main(){Model::function f=(Model::function)&safe;
                    printf("%d %d %d\\n",f(),noexcept(f()),(int)sizeof(Model::function));return 0;}
                """, "8 1 8\n");
    }
    @Test void templatedMemberAliasesKeepTheirArguments() throws Exception {
        agree(temporary,"class-alias-template", """
                #include <stdio.h>
                template<class T> struct Model{typedef T value_type;};
                int main(){printf("%d %d\\n",(Model<int>::value_type)4,(int)sizeof(Model<char>::value_type));return 0;}
                """, "4 1\n");
    }
    @Test void qualifiedValueGroupingDoesNotBecomeACast() throws Exception {
        agree(temporary,"class-value-grouping", """
                #include <stdio.h>
                struct Model{static const int value=6;static int twice(int n){return n*2;}};
                int main(){printf("%d %d %d\\n",(Model::value)+1,(Model::twice)(4),(int)sizeof(Model::value));return 0;}
                """, "7 8 4\n");
    }
}
