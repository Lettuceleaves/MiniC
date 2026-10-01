package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Ownership and reversible snapshots through the actual library implementation. */
@Tag("stl-contract") @Timeout(300)
final class CppLibraryDebugHistoryTest {
    @Test
    void vectorOwnershipAndOutputSurviveCompleteHistoryRoundTrip(){
        var source=new SourceFile("vector-history.cpp","""
            #include <stdio.h>
            #include <vector>
            #include <utility>
            int live=0;
            struct Item{
                int value;
                Item(int x):value(x){++live;}
                Item(const Item& other):value(other.value){++live;}
                Item(Item&& other):value(other.value){other.value=-1;++live;}
                Item& operator=(const Item& other){value=other.value;return *this;}
                Item& operator=(Item&& other){value=other.value;other.value=-1;return *this;}
                ~Item(){--live;}
            };
            int main(){
                {
                    std::vector<Item> first;
                    first.emplace_back(3);first.emplace_back(5);
                    auto copy=first;copy[0].value=8;
                    std::vector<Item> moved=std::move(copy);
                    first.clear();
                    printf("%d %d %d\\n",live,moved[0].value,moved[1].value);
                }
                printf("%d\\n",live);
                return 0;
            }
            """);
        var debug=new DebugApi(source,"",LanguageMode.CPP17_ALGORITHM);
        var history=new ArrayList<Debugger.Context>();history.add(debug.current());
        for(int step=0;debug.canNext()&&step<50_000;++step)history.add(debug.next());
        assertFalse(debug.canNext(),"Library fixture exceeded its source-step budget");
        var completed=debug.current();
        assertEquals(Debugger.Status.COMPLETED,completed.stop().status(),completed.stop()::error);
        assertEquals("2 8 5\n0\n",completed.runtime().stdout().replace("\r\n","\n"));
        assertTrue(completed.runtime().heap().isEmpty(),"All container allocations must have been freed");
        assertTrue(history.stream().anyMatch(context->!context.runtime().heap().isEmpty()));
        for(int i=history.size()-2;i>=0;--i)assertSame(history.get(i),debug.previous());
        assertEquals("",debug.current().runtime().stdout());
        for(int i=1;i<history.size();++i)assertSame(history.get(i),debug.next());
        assertSame(completed,debug.current());
        assertTrue(completed.runtime().heap().isEmpty());
    }
}
