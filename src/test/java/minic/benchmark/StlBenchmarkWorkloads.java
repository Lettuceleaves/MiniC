package minic.benchmark;

import java.nio.file.Path;
import java.util.*;

/** Independent Java oracle for identical four-build STL sources. Work units are input elements,
 * not internal comparisons or public calls. Hashes encode each round's ordered observations and length. */
public final class StlBenchmarkWorkloads {
    private StlBenchmarkWorkloads() {}
    public record Workload(String id,Path sourcePath,int defaultSize,int maximumSize) {}
    public record Input(String stdin,String expectedStdout,long operationCount) {}
    private static final List<Workload> WORKLOADS=List.of("vector-sort","binary-search","priority-queue","ordered-map","deque","string","bitset","string-short","bitset-count","bitset-count-sparse","bitset-count-dense")
            .stream().map(id->new Workload(id,Path.of("benchmarks","stl",id+".cpp"),4096,262144)).toList();
    public static List<Workload> workloads(){return WORKLOADS;}
    public static String operationDescription(Workload workload){
        if(!WORKLOADS.contains(workload))throw new IllegalArgumentException("unknown workload: "+workload);
        if(Set.of("bitset-count-sparse","bitset-count-dense").contains(workload.id()))
            return "one logical input = two flip/count pairs on a 1024-bit value; process cost also includes per-round initialization, PRNG, ordered hash and final bit observation; not isolated count latency; each 64-bit word remains "+(workload.id().endsWith("sparse")?"0..2":"62..64")+" bits set";
        return "one logical input element; complete process includes setup, input/output and checksum; see archived workload source";
    }
    public static Input input(Workload workload,int size,int rounds,int seed){
        if(!WORKLOADS.contains(workload))throw new IllegalArgumentException("unknown workload: "+workload);
        if(size<1||size>workload.maximumSize()||rounds<1||rounds>10000||seed<0||(long)size*rounds>200_000_000L)
            throw new IllegalArgumentException("input outside bounded workload domain");
        StringBuilder expected=new StringBuilder();
        for(int round=0;round<rounds;round++){
            RandomValues random=new RandomValues((seed+(long)round*1013904223L)&0xffffffffL);Hash hash=new Hash();
            switch(workload.id()){
                case "vector-sort","binary-search","priority-queue"->{
                    int[] values=new int[size];for(int i=0;i<size;i++)values[i]=random.next();Arrays.sort(values);
                    if(workload.id().equals("vector-sort"))for(int value:values)hash.add(value);
                    else if(workload.id().equals("priority-queue"))for(int i=size-1;i>=0;i--)hash.add(values[i]);
                    else for(int i=0;i<size;i++){int key=random.next(),low=0,high=size;while(low<high){int middle=(low+high)>>>1;if(values[middle]<key)low=middle+1;else high=middle;}hash.add(low);hash.add(low<size&&values[low]==key?1:0);}
                }
                case "ordered-map"->{
                    var values=new TreeMap<Integer,Integer>();for(int i=0;i<size;i++)values.put(random.next()%((size+1)/2+1),i+1);
                    for(int i=0;i<size/4;i++)values.remove(random.next()%((size+1)/2+1));
                    values.forEach((key,value)->{hash.add(key);hash.add(value);});
                }
                case "deque"->{
                    var values=new ArrayDeque<Integer>();for(int i=0;i<size;i++){int value=random.next();if(i%2==1)values.addFirst(value);else values.addLast(value);}
                    for(int value:values)hash.add(value);for(int i=0;i<size;i++)hash.add(i%2==1?values.removeLast():values.removeFirst());
                }
                case "string"->{
                    var values=new StringBuilder();for(int i=0;i<size;i++)values.append((char)('a'+random.next()%26));
                    for(int i=0;i<size/16;i++)values.setCharAt(random.next()%size,(char)('A'+i%26));
                    values.append("tail");values.delete(size/3,size/3+size/5);for(int i=0;i<values.length();i++)hash.add(values.charAt(i));hash.add(values.indexOf("ab")+1);
                }
                case "bitset"->{
                    boolean[] values=new boolean[1024];for(int i=0;i<size;i++){int index=random.next()%1024;values[index]=!values[index];if(i%8==0)values[(index+13)%1024]=true;}
                    boolean[] shifted=new boolean[1024];int count=0;for(int i=0;i<1024;i++){shifted[i]=(i>=3&&values[i-3])^(i+5<1024&&values[i+5]);if(shifted[i])count++;}
                    hash.add(count);for(boolean value:shifted)hash.add(value?1:0);
                }
                case "string-short"->{
                    for(int i=0;i<size;i++){
                        String original=String.valueOf((char)('a'+random.next()%26)).repeat(i%16);
                        hash.text(original);hash.text(original); // original, then moved copy
                        String appended=original+(char)('A'+random.next()%26);
                        hash.text(appended);hash.text(appended); // appended, then copy/move assignment result
                    }
                }
                case "bitset-count"->{
                    boolean[] dense=new boolean[1024],sparse=new boolean[1024];Arrays.fill(dense,true);
                    int denseCount=1024,sparseCount=0;
                    for(int i=0;i<size;i++){
                        int denseIndex=random.next()%1024,sparseIndex=random.next()%64;
                        denseCount+=dense[denseIndex]?-1:1;sparseCount+=sparse[sparseIndex]?-1:1;
                        dense[denseIndex]=!dense[denseIndex];sparse[sparseIndex]=!sparse[sparseIndex];
                        hash.add(denseIndex);hash.add(sparseIndex);hash.add(denseCount);hash.add(sparseCount);
                    }
                    for(int bit=0;bit<1024;bit++){hash.add(dense[bit]?1:0);hash.add(sparse[bit]?1:0);}
                }
                case "bitset-count-sparse","bitset-count-dense" -> stableBitsetCount(workload.id(),size,random,hash);
                default->throw new IllegalArgumentException("unknown workload");
            }
            expected.append("round=").append(round).append(" hash=").append(Long.toUnsignedString(hash.finish())).append('\n');
        }
        return new Input(size+" "+rounds+" "+seed+"\n",expected.toString(),Math.multiplyExact((long)size,rounds));
    }
    /** Count-only oracle: two count calls per logical input. Density stays sparse/dense for any size. */
    public static List<Long> bitsetCountTotals(Workload workload,Input input){
        if(!Set.of("bitset-count-sparse","bitset-count-dense").contains(workload.id()))throw new IllegalArgumentException("Not a stable-density count workload");
        String[] words=input.stdin().strip().split(" ");int size=Integer.parseInt(words[0]),rounds=Integer.parseInt(words[1]),seed=Integer.parseInt(words[2]);
        var totals=new ArrayList<Long>();for(int round=0;round<rounds;round++)totals.add(stableBitsetCount(workload.id(),size,new RandomValues((seed+(long)round*1013904223L)&0xffffffffL),new Hash()));
        return List.copyOf(totals);
    }
    private static long stableBitsetCount(String id,int size,RandomValues random,Hash hash){
        boolean dense=id.equals("bitset-count-dense");boolean[] bits=new boolean[1024];Arrays.fill(bits,dense);int[] anchors=new int[16];
        for(int word=0;word<16;word++){anchors[word]=random.next()%64;bits[word*64+anchors[word]]=!dense;}
        int count=dense?1008:16;long total=0;
        for(int i=0;i<size;i++){
            int word=random.next()%16,bit=random.next()%64,index=word*64+(i%4==0?anchors[word]:bit);hash.add(index);
            for(int flip=0;flip<2;flip++){count+=bits[index]?-1:1;bits[index]=!bits[index];hash.add(count);total+=count;}
        }
        for(boolean value:bits)hash.add(value?1:0);
        return total;
    }
    public static long sequenceHash(long... values){Hash hash=new Hash();for(long value:values)hash.add(value);return hash.finish();}
    private static final class Hash {long value=0xcbf29ce484222325L,count;void add(long next){value=(value^next)*1099511628211L;count++;}void text(String text){add(text.length());for(int i=0;i<text.length();i++)add(text.charAt(i));}long finish(){return (value^count)*1099511628211L;}}
    private static final class RandomValues {long state;RandomValues(long seed){state=seed;}int next(){state=(state*1664525L+1013904223L)&0xffffffffL;return (int)((state>>>8)&65535);}}
}
