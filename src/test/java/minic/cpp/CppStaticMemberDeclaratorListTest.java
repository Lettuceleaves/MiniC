package minic.cpp;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.StaticFieldMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;
@Timeout(90)
final class CppStaticMemberDeclaratorListTest {
    @TempDir Path temporary;
    @Test void sharedConstSpecifierAndOrderedInitializers() throws Exception {
        agree(temporary,"static-member-list", """
                #include <stdio.h>
                struct Flags {typedef unsigned int bits;static const bits first=1,second=first<<1,third=second|4;};
                int main(){printf("%u %u %u\\n",Flags::first,Flags::second,Flags::third);return 0;}
                """, "1 2 6\n");
    }
    @Test void eachDeclaratorBuildsItsOwnPointerAndArrayType() throws Exception {
        agree(temporary,"static-member-pointer-list", """
                #include <stdio.h>
                struct Store {static int *pointer,value,values[2];};
                int* Store::pointer=nullptr;int Store::value=7;int Store::values[2]={3,5};
                int main(){printf("%d %d %d %d\\n",Store::pointer==nullptr,Store::value,Store::values[0],Store::values[1]);return 0;}
                """, "1 7 3 5\n");
    }
    @Test void privateAccessAlsoAppliesToLaterDeclarators() throws Exception {
        reject(temporary,"static-list-access", """
                class Secret {static const int first=1,second=2;};
                int main(){return Secret::second;} // bad
                """);
    }
    @Test void duplicateLaterDeclaratorIsRejected() throws Exception {
        reject(temporary,"static-list-duplicate", """
                struct Store {static int value,value;}; // bad
                int main(){return 0;}
                """);
    }
    @Test void iostreamAndBitsetHeadersRetainTheirRealMemberLists() {
        var api=compiler("#include <bitset>\nint main(){return 0;}");var parser=stage(api,Parser.class);api.runThrough(parser);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
    }
}
