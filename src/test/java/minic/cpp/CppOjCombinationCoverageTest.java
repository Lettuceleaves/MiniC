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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Small OJ combinations; algorithms and data are independent of timing workloads. */
@Tag("stl-contract") @Timeout(600)
final class CppOjCombinationCoverageTest {
    @TempDir Path temporary;
    record Case(String shape,String input,String expected) {}
    record Problem(String name,List<Case> cases) {
        public String toString(){return name;}
        String input(){var text=new StringBuilder().append(cases.size()).append('\n');cases.forEach(c->text.append(c.input()));return text.toString();}
        String expected(){var text=new StringBuilder();cases.forEach(c->text.append(c.expected()));return text.toString();}
    }
    static Stream<Arguments> programs(){return problems().stream().flatMap(p->Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED).map(level->Arguments.of(p,level)));}
    static List<Problem> problems(){return List.of(dijkstra(),windows(),compression(),scheduling(),sparse(),words(),knapsack(),grids(),tracked());}

    @ParameterizedTest(name="{0} [{1}]") @MethodSource("programs")
    void realCombinationsMatchIndependentModels(Problem problem,OptimizationLevel level)throws Exception {
        String source;try(var input=getClass().getResourceAsStream("/cpp/oj-combinations/"+problem.name()+".cpp")){
            assertNotNull(input,problem.name());source=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        String override=System.getProperty("minic.oj.combinations.output");
        Path directory=(override==null?temporary:Path.of(override)).resolve(problem.name()+"-"+level);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("input.txt"),problem.input());Files.writeString(directory.resolve("expected.txt"),problem.expected());
        Files.writeString(directory.resolve("cases.txt"),String.join("\n",problem.cases().stream().map(Case::shape).toList()));
        var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(180),Duration.ofSeconds(90),20_000_000,1_048_576);
        var actual=new CppDifferentialHarness(directory.resolve("differential"),CppDifferentialHarness.referenceCompiler(System.getenv()),limits,LanguageMode.CPP17_ALGORITHM,level)
                .run(problem.name(),source,problem.input());
        var own=CppOwnLibraryReference.run(directory.resolve("own"),source,problem.input(),limits);
        Files.writeString(directory.resolve("result.txt"),actual.describe()+"\nOWN="+own);
        assertAll(()->assertTrue(actual.passed(),actual::describe),()->assertTrue(own.passed(),own::toString));
        for(var outcome:actual.outcomes().values())assertEquals(problem.expected(),normalize(outcome.stdout()),problem.name()+" / "+outcome.backend()+" / "+actual.describe());
        assertEquals(problem.expected(),normalize(own.stdout()),own::toString);
    }

    private static final long INF=4_000_000_000_000_000L;
    private static final long[] SEEDS={44117L,95171L};
    private static String normalize(String text){return text.replace("\r\n","\n");}
    private static String line(long[] values){var text=new StringBuilder();for(int i=0;i<values.length;i++){if(i>0)text.append(' ');text.append(values[i]);}return text.append('\n').toString();}
    private static Case graph(String shape,int n,int start,long[][] edges){
        var input=new StringBuilder().append(n).append(' ').append(edges.length).append(' ').append(start).append('\n');
        for(var e:edges)input.append(e[0]).append(' ').append(e[1]).append(' ').append(e[2]).append('\n');
        long[] distance=new long[n];Arrays.fill(distance,INF);distance[start]=0;
        // Bellman-Ford intentionally does not reproduce the C++ priority queue.
        for(int round=1;round<n;round++)for(var e:edges){int from=(int)e[0],to=(int)e[1];if(distance[from]!=INF)distance[to]=Math.min(distance[to],distance[from]+e[2]);}
        for(int i=0;i<n;i++)if(distance[i]==INF)distance[i]=-1;
        return new Case(shape,input.toString(),line(distance));
    }
    private static Problem dijkstra(){
        var cases=new ArrayList<Case>();cases.add(graph("singleton",1,0,new long[0][]));cases.add(graph("disconnected-nonzero-start",6,3,new long[0][]));
        cases.add(graph("parallel-stale-queue",5,0,new long[][]{{0,1,90},{0,1,5},{0,2,1},{2,1,2},{1,3,7},{2,3,50},{3,4,0}}));
        cases.add(graph("zero-cycle-and-unreachable",6,0,new long[][]{{0,1,0},{1,2,0},{2,0,0},{2,3,2},{4,5,1}}));
        cases.add(graph("distance-beyond-int",4,0,new long[][]{{0,1,3_000_000_000L},{1,2,4_000_000_000L},{0,2,8_000_000_000L},{2,3,9_000_000_000L}}));
        for(int mode=0;mode<SEEDS.length;mode++){var random=new Random(SEEDS[mode]);int n=mode==0?9:13,m=mode==0?13:104;long[][] edges=new long[m][3];
            for(var e:edges){e[0]=random.nextInt(n);e[1]=random.nextInt(n);e[2]=random.nextInt(5)==0?3_000_000_000L+random.nextInt(17):random.nextInt(11);}
            cases.add(graph((mode==0?"sparse":"dense")+"-seed-"+SEEDS[mode],n,mode==0?0:7,edges));}
        return new Problem("dijkstra",cases);
    }
    private static Case window(String shape,long[] values,int k){
        var expected=new long[values.length-k+1];for(int i=0;i<expected.length;i++){expected[i]=Long.MIN_VALUE;for(int j=i;j<i+k;j++)expected[i]=Math.max(expected[i],values[j]);}
        return new Case(shape,values.length+" "+k+"\n"+line(values),line(expected));
    }
    private static Problem windows(){
        var cases=new ArrayList<Case>();cases.add(window("singleton",new long[]{-7},1));cases.add(window("window-one",new long[]{4,-2,9,9,-8},1));
        cases.add(window("whole-array",new long[]{-10,-3,-7,-3},4));long[] same=new long[65];Arrays.fill(same,17);cases.add(window("equal-expiry",same,32));
        long[] up=new long[129],down=new long[129];for(int i=0;i<129;i++){up[i]=i-64;down[i]=64-i;}
        cases.add(window("increasing-129",up,64));cases.add(window("decreasing-129",down,65));
        for(long seed:SEEDS){var random=new Random(seed);long[] data=new long[97];for(int i=0;i<data.length;i++)data[i]=random.nextInt(17)-8;cases.add(window("duplicates-seed-"+seed,data,31));}
        return new Problem("sliding-maximum",cases);
    }
    private static Case compress(String shape,long[] values){
        var sorted=new TreeSet<Long>();for(long value:values)sorted.add(value);var ranks=new HashMap<Long,Integer>();var expected=new StringBuilder().append(sorted.size());int rank=0;
        for(long value:sorted){expected.append(' ').append(value);ranks.put(value,rank++);}expected.append('\n');long[] output=new long[values.length];for(int i=0;i<values.length;i++)output[i]=ranks.get(values[i]);
        return new Case(shape,values.length+"\n"+line(values),expected+line(output));
    }
    private static Problem compression(){
        var cases=new ArrayList<Case>();cases.add(compress("empty",new long[0]));cases.add(compress("singleton",new long[]{-9}));cases.add(compress("all-duplicates",new long[]{8,8,8,8,8}));
        cases.add(compress("already-sorted",new long[]{-8,-1,0,3,5}));cases.add(compress("reverse-and-repeated",new long[]{9,9,8,7,7,1,-1}));
        cases.add(compress("wide-signed-keys",new long[]{900_000_000_000L,-900_000_000_000L,0,900_000_000_000L}));
        for(long seed:SEEDS){var random=new Random(seed);long[] data=new long[97];for(int i=0;i<data.length;i++)data[i]=(random.nextInt(31)-15)*1_000_000_007L;cases.add(compress("seed-"+seed,data));}
        return new Problem("coordinate-compression",cases);
    }
    private static Case schedule(String shape,int machines,long[][] jobs){
        var input=new StringBuilder().append(jobs.length).append(' ').append(machines).append('\n');for(var job:jobs)input.append(job[0]).append(' ').append(job[1]).append('\n');
        // Every subset is feasible iff its half-open intervals need <= K machines.
        // This optimal-cardinality oracle shares neither sorting nor multiset lookup.
        int optimum=0;for(int mask=0;mask<(1<<jobs.length);mask++){int size=Integer.bitCount(mask);if(size<=optimum)continue;boolean valid=true;
            for(int i=0;i<jobs.length&&valid;i++)if((mask&(1<<i))!=0){int active=0;for(int j=0;j<jobs.length;j++)if((mask&(1<<j))!=0&&jobs[j][0]<=jobs[i][0]&&jobs[i][0]<jobs[j][1])active++;if(active>machines)valid=false;}
            if(valid)optimum=size;}
        return new Case(shape,input.toString(),optimum+"\n");
    }
    private static Problem scheduling(){
        var cases=new ArrayList<Case>();cases.add(schedule("empty",2,new long[0][]));cases.add(schedule("no-machines",0,new long[][]{{0,2},{2,3}}));
        cases.add(schedule("touching-endpoints",1,new long[][]{{0,2},{2,4},{1,5},{4,6}}));cases.add(schedule("duplicate-intervals",2,new long[][]{{1,4},{1,4},{1,4},{4,5},{4,5}}));
        cases.add(schedule("negative-wide-time",1,new long[][]{{-9_000_000_000L,-1},{-1,9_000_000_000L},{0,1}}));
        for(int mode=0;mode<SEEDS.length;mode++){var random=new Random(SEEDS[mode]);long[][] jobs=new long[12][2];for(var job:jobs){job[0]=random.nextInt(15)-5;job[1]=job[0]+1+random.nextInt(8);}cases.add(schedule("seed-"+SEEDS[mode],mode+1,jobs));}
        return new Problem("interval-scheduling",cases);
    }
    private static Case ledger(String shape,long[][] changes,long[][] queries){
        var input=new StringBuilder().append(changes.length).append(' ').append(queries.length).append('\n');var map=new TreeMap<Long,Long>();
        for(var change:changes){input.append(change[0]).append(' ').append(change[1]).append('\n');long value=map.getOrDefault(change[0],0L)+change[1];if(value==0)map.remove(change[0]);else map.put(change[0],value);}
        var expected=new StringBuilder().append(map.size());map.forEach((key,value)->expected.append(' ').append(key).append(':').append(value));expected.append('\n');long[] sums=new long[queries.length];
        for(int i=0;i<queries.length;i++){var q=queries[i];input.append(q[0]).append(' ').append(q[1]).append('\n');for(var e:map.entrySet())if(e.getKey()>=q[0]&&e.getKey()<=q[1])sums[i]+=e.getValue();}
        return new Case(shape,input.toString(),expected+line(sums));
    }
    private static Problem sparse(){
        var cases=new ArrayList<Case>();cases.add(ledger("empty",new long[0][],new long[][]{{-1,1},{4,3}}));cases.add(ledger("cancel-erase-reinsert",new long[][]{{7,4},{7,-4},{7,9},{2,-3},{2,3}},new long[][]{{7,7},{0,10},{8,10}}));
        cases.add(ledger("wide-values-and-keys",new long[][]{{-9_000_000_000L,8_000_000_000L},{9_000_000_000L,-7_000_000_000L},{0,4_000_000_000L}},new long[][]{{Long.MIN_VALUE+1,Long.MAX_VALUE},{0,9_000_000_000L}}));
        cases.add(ledger("zero-updates",new long[][]{{1,0},{2,0},{1,0}},new long[0][]));
        for(long seed:SEEDS){var random=new Random(seed);long[][] changes=new long[80][2],queries=new long[12][2];for(var c:changes){c[0]=(random.nextInt(21)-10)*1_000_000_007L;c[1]=random.nextInt(19)-9;}for(var q:queries){q[0]=(random.nextInt(25)-12)*1_000_000_007L;q[1]=q[0]+random.nextInt(12)*1_000_000_007L;}cases.add(ledger("seed-"+seed,changes,queries));}
        return new Problem("sparse-accumulation",cases);
    }
    private static Case ranking(String shape,List<String> words){
        var counts=new HashMap<String,Integer>();for(String word:words)counts.merge(word,1,Integer::sum);var entries=new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.<Map.Entry<String,Integer>>comparingInt(Map.Entry::getValue).reversed().thenComparing(Map.Entry::getKey));
        var expected=new StringBuilder().append(entries.size());for(var e:entries)expected.append(' ').append(e.getKey()).append(':').append(e.getValue());
        return new Case(shape,words.size()+"\n"+String.join(" ",words)+"\n",expected.append('\n').toString());
    }
    private static Problem words(){
        var cases=new ArrayList<Case>();cases.add(ranking("empty",List.of()));cases.add(ranking("singleton",List.of("one")));cases.add(ranking("equal-frequencies",List.of("z","A","a","z","A","a")));
        cases.add(ranking("all-equal",Collections.nCopies(65,"same")));var tokens=List.of("x","x".repeat(15),"x".repeat(16),"x".repeat(23),"x".repeat(24),"x".repeat(63),"x".repeat(64),"prefix_A","prefix_a");cases.add(ranking("string-size-boundaries",tokens));
        for(long seed:SEEDS){var random=new Random(seed);var list=new ArrayList<String>();for(int i=0;i<120;i++)list.add(tokens.get(random.nextInt(tokens.size())));cases.add(ranking("seed-"+seed,list));}
        return new Problem("word-ranking",cases);
    }
    private static Case subset(String shape,int[] weights){
        boolean[] reachable=new boolean[129];reachable[0]=true;var input=new StringBuilder().append(weights.length).append('\n');
        for(int weight:weights){input.append(weight).append(' ');for(int total=128;total>=weight;total--)reachable[total]|=reachable[total-weight];}input.append('\n');
        int count=0;var bits=new StringBuilder();for(boolean present:reachable){if(present)count++;bits.append(present?'1':'0');}
        return new Case(shape,input.toString(),count+" "+bits+"\n");
    }
    private static Problem knapsack(){
        var cases=new ArrayList<Case>();cases.add(subset("empty",new int[0]));cases.add(subset("zero-only",new int[]{0,0,0}));cases.add(subset("word-boundaries",new int[]{1,63,64}));
        cases.add(subset("last-bit",new int[]{128}));cases.add(subset("oversized-shifts",new int[]{129,130,256}));cases.add(subset("repeated-values",new int[]{32,32,32,32,32}));cases.add(subset("dense-powers",new int[]{1,2,4,8,16,32,64,128}));
        for(long seed:SEEDS){var random=new Random(seed);int[] data=new int[45];for(int i=0;i<data.length;i++)data[i]=random.nextInt(160);cases.add(subset("seed-"+seed,data));}
        return new Problem("bitset-knapsack",cases);
    }
    private static Case grid(String shape,List<String> rows){
        int h=rows.size(),w=rows.getFirst().length();int[][] distance=new int[h][w];for(int[] row:distance)Arrays.fill(row,1_000_000);if(rows.getFirst().charAt(0)!='#')distance[0][0]=0;
        int[] dr={1,-1,0,0},dc={0,0,1,-1};boolean changed;
        // Repeated whole-grid relaxation, intentionally no queue oracle.
        do{changed=false;for(int r=0;r<h;r++)for(int c=0;c<w;c++)if(rows.get(r).charAt(c)!='#')for(int direction=0;direction<4;direction++){
            int nr=r+dr[direction],nc=c+dc[direction];if(nr>=0&&nr<h&&nc>=0&&nc<w&&rows.get(nr).charAt(nc)!='#'&&distance[nr][nc]+1<distance[r][c]){distance[r][c]=distance[nr][nc]+1;changed=true;}}}while(changed);
        long[] flat=new long[h*w];for(int r=0;r<h;r++)for(int c=0;c<w;c++)flat[r*w+c]=distance[r][c]==1_000_000?-1:distance[r][c];
        return new Case(shape,h+" "+w+"\n"+String.join("\n",rows)+"\n",line(flat));
    }
    private static Problem grids(){
        var cases=new ArrayList<Case>();cases.add(grid("singleton-open",List.of(".")));cases.add(grid("singleton-blocked",List.of("#")));cases.add(grid("long-row",List.of(".".repeat(65))));
        cases.add(grid("long-column",Collections.nCopies(33,".")));cases.add(grid("unreachable-island",List.of("...#...","...#...","####...",".......")));cases.add(grid("many-shortest-paths",List.of(".........",".........",".........",".........")));
        for(long seed:SEEDS){var random=new Random(seed);var rows=new ArrayList<String>();for(int r=0;r<9;r++){var text=new StringBuilder();for(int c=0;c<11;c++)text.append(r==0&&c==0?'.':random.nextInt(4)==0?'#':'.');rows.add(text.toString());}cases.add(grid("seed-"+seed,rows));}
        return new Problem("grid-bfs",cases);
    }
    private static long mix(long hash,int value){return (hash*131+value+1000)%1_000_000_007;}
    private static Case sequence(String shape,int initial,long seed){
        var random=new Random(seed);var model=new ArrayList<Integer>();var steps=new ArrayList<int[]>();long hash=0;
        for(int step=0;step<initial+90;step++){
            int op=step<initial||model.isEmpty()?0:step%13==0?6:step%11==0?1:random.nextInt(9),index=0,other=0,value=random.nextInt(401)-200;
            switch(op){
                case 0->model.add(value);
                case 1->{index=random.nextInt(model.size()+1);other=random.nextInt(model.size());model.add(index,model.get(other));}
                case 2->{index=random.nextInt(model.size());model.remove(index);}
                case 3->{index=random.nextInt(77);while(model.size()>index)model.removeLast();while(model.size()<index)model.add(value);}
                case 4->{index=random.nextInt(model.size());model.set(index,value);}
                case 5,6->{}
                case 7->model.removeLast();
                case 8->model.clear();
                default->throw new AssertionError();
            }
            steps.add(new int[]{op,index,other,value});hash=mix(hash,model.size());for(int item:model)hash=mix(hash,item);
        }
        var input=new StringBuilder().append(steps.size()).append('\n');for(int[] step:steps)input.append(step[0]).append(' ').append(step[1]).append(' ').append(step[2]).append(' ').append(step[3]).append('\n');
        return new Case(shape,input.toString(),hash+" 0 0\n");
    }
    private static Problem tracked(){return new Problem("tracked-sequences",List.of(sequence("empty-start-seed-44117",0,44117),sequence("record-block-crossing-33",33,95171),sequence("record-two-blocks-65",65,104729)));}
}
