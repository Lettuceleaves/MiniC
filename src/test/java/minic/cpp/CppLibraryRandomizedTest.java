package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed-seed operation traces with independent Java models; queued for unified acceptance. */
@Tag("stl-contract") @Timeout(600)
final class CppLibraryRandomizedTest {
    @TempDir Path temporary;
    record Program(String source,String expected) {}
    static Stream<Arguments> cases(){return Stream.of(1729L,65537L,314159L).flatMap(seed->Stream.of(
            Arguments.of("vector-"+seed,sequence(seed,false)),Arguments.of("deque-"+seed,sequence(seed,true)),
            Arguments.of("ordered-"+seed,ordered(seed))));}
    static long mix(long hash,int value){return (hash*131+value+1000)%1_000_000_007;}
    static Program sequence(long seed,boolean deque){
        Random random=new Random(seed);List<Integer> values=new ArrayList<>();long hash=0;
        StringBuilder source=new StringBuilder("#include <stdio.h>\n#include <"+(deque?"deque":"vector")+">\nint main(){std::"+(deque?"deque":"vector")+"<int> a;long long hash=0;int steps[160][4]={\n");
        for(int step=0;step<160;step++){
            int op=values.isEmpty()?0:random.nextInt(7),value=random.nextInt(201)-100;
            int index=values.isEmpty()?0:random.nextInt(values.size()),count=0;
            switch(op){
                case 0->values.add(value);
                case 1->values.add(index,value);
                case 2->values.remove(index);
                case 3->values.set(index,value);
                case 4->values.remove(values.size()-1);
                case 5->{count=random.nextInt(18);while(values.size()>count)values.remove(values.size()-1);while(values.size()<count)values.add(value);}
                case 6->values.add(0,value);
                default->throw new AssertionError();
            }
            hash=mix(hash,values.size());for(int item:values)hash=mix(hash,item);
            source.append('{').append(op).append(',').append(index).append(',').append(value).append(',').append(count).append("},\n");
        }
        // Execute exactly the same generated trace with a bounded number of source locals.
        // Unrolling all steps created hundreds of dead locals in every debugger snapshot.
        source.append("};for(int step=0;step<160;++step){int op=steps[step][0],index=steps[step][1],value=steps[step][2],count=steps[step][3];")
                .append("if(op==0)a.push_back(value);else if(op==1)a.insert(a.begin()+index,value);else if(op==2)a.erase(a.begin()+index);")
                .append("else if(op==3)a[index]=value;else if(op==4)a.pop_back();else if(op==5)a.resize(count,value);else ")
                .append(deque?"a.push_front(value);":"a.insert(a.begin(),value);")
                .append("hash=(hash*131+(int)a.size()+1000)%1000000007;for(int x:a)hash=(hash*131+x+1000)%1000000007;}\n")
                .append("printf(\"%lld\\n\",hash);return 0;}\n");
        return new Program(source.toString(),hash+"\n");
    }
    static Program ordered(long seed){
        Random random=new Random(seed);TreeMap<Integer,Integer> map=new TreeMap<>(),counts=new TreeMap<>();TreeSet<Integer> set=new TreeSet<>();long hash=0;
        StringBuilder source=new StringBuilder("#include <stdio.h>\n#include <map>\n#include <set>\n"+TREE_CHECK+"\nint main(){std::map<int,int> m;std::set<int>s;std::multiset<int>d;long long hash=0;int steps[180][3]={\n");
        for(int step=0;step<180;step++){
            int key=random.nextInt(41)-20,value=random.nextInt(101)-50,op=random.nextInt(4);
            switch(op){
                case 0->{map.put(key,value);set.add(key);counts.merge(key,1,Integer::sum);}
                case 1->{map.remove(key);set.remove(key);counts.remove(key);}
                case 2->{map.putIfAbsent(key,value);set.add(key);counts.merge(key,1,Integer::sum);}
                case 3->{var first=map.ceilingEntry(key);hash=mix(hash,first==null?777:first.getKey());}
                default->throw new AssertionError();
            }
            hash=mix(hash,map.size());for(var entry:map.entrySet()){hash=mix(hash,entry.getKey());hash=mix(hash,entry.getValue());}
            hash=mix(hash,set.size());for(int item:set)hash=mix(hash,item);
            int total=counts.values().stream().mapToInt(Integer::intValue).sum();hash=mix(hash,total);
            for(var entry:counts.entrySet())for(int i=0;i<entry.getValue();i++)hash=mix(hash,entry.getKey());
            source.append('{').append(op).append(',').append(key).append(',').append(value).append("},\n");
        }
        source.append("};for(int step=0;step<180;++step){int op=steps[step][0],key=steps[step][1],value=steps[step][2];")
                .append("if(op==0){m[key]=value;s.insert(key);d.insert(key);}else if(op==1){m.erase(key);s.erase(key);d.erase(key);}")
                .append("else if(op==2){m.insert(std::make_pair(key,value));s.insert(s.end(),key);d.insert(d.begin(),key);}")
                .append("else{auto it=m.lower_bound(key);hash=(hash*131+(it==m.end()?777:it->first)+1000)%1000000007;}")
                .append("if(!tree_ok(m)||!tree_ok(s)||!tree_ok(d))return 3;")
                .append("hash=(hash*131+(int)m.size()+1000)%1000000007;for(auto it=m.begin();it!=m.end();++it){hash=(hash*131+it->first+1000)%1000000007;hash=(hash*131+it->second+1000)%1000000007;}")
                .append("hash=(hash*131+(int)s.size()+1000)%1000000007;for(int x:s)hash=(hash*131+x+1000)%1000000007;")
                .append("hash=(hash*131+(int)d.size()+1000)%1000000007;for(int x:d)hash=(hash*131+x+1000)%1000000007;}\n")
                .append("printf(\"%lld\\n\",hash);return 0;}\n");
        return new Program(source.toString(),hash+"\n");
    }
    // Internal checks supplement public-output oracles only for the two builds of our library.
    static final String TREE_CHECK="""
        #if defined(__MINIC__) || defined(__MINIC_SELF_STL__)
        int depth(std::__minic_rb_node* n,std::__minic_rb_node* p,int& remaining){
            if(!n)return 1;
            if(remaining--<=0||n->parent!=p)return -1;
            if(n->red&&((n->left&&n->left->red)||(n->right&&n->right->red)))return -1;
            int left=depth(n->left,n,remaining),right=depth(n->right,n,remaining);
            if(left<0||right<0||left!=right)return -1;
            return left+(n->red?0:1);
        }
        template<class C>bool tree_ok(C& c){
            auto h=c.end().node;int remaining=(int)c.size();
            if(!h->red)return false;
            if(!remaining)return !h->parent&&h->left==h&&h->right==h;
            if(!h->parent||h->parent->red||h->parent->parent!=h)return false;
            if(depth(h->parent,h,remaining)<0||remaining!=0)return false;
            return h->left==std::__minic_rb_min(h->parent)&&h->right==std::__minic_rb_max(h->parent);
        }
        #else
        template<class C>bool tree_ok(C& c){return true;}
        #endif
        """;
    static Stream<Arguments> casesWithOptimization(){return cases().flatMap(arguments->{
        Object[] values=arguments.get();
        return Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED)
                .map(level->Arguments.of(values[0],values[1],level));
    });}
    @ParameterizedTest(name="{0} [{2}]") @MethodSource("casesWithOptimization")
    void generatedTraceMatchesIndependentModel(String name,Program program,OptimizationLevel level)throws Exception{
        var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(120),Duration.ofSeconds(45),10_000_000,1_048_576);
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,LanguageMode.CPP17_ALGORITHM,level)
                .run(name+"-"+level,program.source(),"");
        var own=CppOwnLibraryReference.run(temporary,program.source(),"",limits);
        assertAll(()->assertTrue(report.passed(),report::describe),()->assertTrue(own.passed(),own::toString));
        for(var outcome:report.outcomes().values())assertEquals(program.expected(),outcome.stdout().replace("\r\n","\n"),report::describe);
        assertEquals(program.expected(),own.stdout().replace("\r\n","\n"),own::toString);
    }
}
