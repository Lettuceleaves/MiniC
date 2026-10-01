package minic.cpp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static minic.cpp.CppReferenceTest.*;

/** Empty parentheses value-initialize aggregates in C++17; nonempty lists do not. */
@Timeout(90)
final class CppEmptyParenthesizedInitializationTest {
    @TempDir Path temporary;

    @Test void aggregateMemberIsValueInitialized() throws Exception {
        agree(temporary, "aggregate-member-empty-parens", """
                #include <stdio.h>
                struct State { int number; int *pointer; };
                struct Owner { State state; Owner():state(){} };
                int main(){Owner owner; printf("%d %d\\n",owner.state.number,owner.state.pointer==nullptr);return 0;}
                """, "0 1\n");
    }
    @Test void scalarArrayMemberIsValueInitialized() throws Exception {
        agree(temporary, "array-member-empty-parens", """
                #include <stdio.h>
                struct Owner { int values[3]; Owner():values(){} };
                int main(){Owner owner;printf("%d %d %d\\n",owner.values[0],owner.values[1],owner.values[2]);return 0;}
                """, "0 0 0\n");
    }
    @Test void emptyTemplateFunctorCanBeValueInitialized() throws Exception {
        agree(temporary, "template-functor-empty-parens", """
                #include <stdio.h>
                template<class T> struct Less { bool operator()(const T& a,const T& b)const{return a<b;} };
                template<class Compare> struct Holder { Compare compare; Holder():compare(){} bool run(){return compare(2,3);} };
                int main(){Holder<Less<int>> holder;printf("%d\\n",holder.run());return 0;}
                """, "1\n");
    }
    @Test void libraryComparatorsAndContainersCanBeDefaultConstructed() throws Exception {
        agree(temporary, "library-default-container", """
                #include <set>
                #include <map>
                #include <stdio.h>
                int main(){std::set<int> values;std::map<int,int> counts;
                    values.insert(7);counts[7]=9;
                    printf("%d %d %d\\n",(int)values.size(),*values.begin(),counts[7]);return 0;}
                """, "1 7 9\n");
    }
    @Test void nonemptyAggregateMemberParenthesesRemainInvalidInCpp17() throws Exception {
        reject(temporary,"aggregate-nonempty-parens", """
                struct State {int number;};
                struct Owner {State state; Owner():state(7){} }; // bad
                int main(){Owner owner;return 0;}
                """);
    }
}
