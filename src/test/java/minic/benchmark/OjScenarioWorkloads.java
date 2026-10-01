package minic.benchmark;

import java.nio.file.Path;
import java.util.*;

/** Complete OJ input generators and independent Java answer oracles. No native timing here. */
public final class OjScenarioWorkloads {
    private OjScenarioWorkloads() {}
    public record Scenario(String id, Path sourcePath, String sizeUnit, int smallSize, int largeSize,
                           List<String> shapes, String operationDefinition) {
        public Scenario { shapes=List.copyOf(shapes); }
    }
    public record Configuration(Scenario scenario, String shape, int size, int seed) {
        public String id(){return scenario.id()+"-"+shape+"-n"+size+"-s"+seed;}
    }
    public record Input(String stdin, String expectedStdout, long logicalInputItems, int rounds) {}
    private static Scenario scenario(String id,String unit,int small,int large,String definition,String... shapes){
        return new Scenario(id,Path.of("benchmarks","oj-scenarios",id+".cpp"),unit,small,large,List.of(shapes),definition);
    }
    private static final List<Scenario> SCENARIOS=List.of(
        scenario("dijkstra","vertices",256,1024,"One input item is a vertex or directed weighted edge; output is every vertex distance, with -1 for unreachable vertices.","chain-shortcuts","hub-spokes","layered-disconnected"),
        scenario("sliding-window-max","values",4096,32768,"One input item is a sequence value; output is the maximum for every full sliding window.","increasing","decreasing","duplicate-runs"),
        scenario("coordinate-compression","values",4096,32768,"One input item is an original coordinate; output includes the complete sorted distinct dictionary and every original coordinate rank.","wide-unique","few-values","mostly-sorted"),
        scenario("interval-scheduling","intervals",2048,16384,"One input item is an interval; output includes each sorted interval's acceptance, the accepted count and sorted final machine release times.","disjoint","congested","endpoint-ties"),
        scenario("sparse-accumulator","updates",2048,16384,"One input item is a signed key/delta update; output includes each updated value and all final nonzero entries in key order.","wide-keys","hot-keys","cancellation-pairs"),
        scenario("word-frequency","tokens",2048,16384,"One input item is a whitespace-separated ASCII word; output includes every lexicographically ordered word and frequency.","hot-short","unique-sso-boundary","shared-prefix"),
        scenario("bitset-knapsack","weights",128,512,"One input item is a nonnegative item weight for capacity 4096 (including zero and over-capacity shifts); output is every reachable sum from 0 through 4096.","dense-small","sparse-large","common-divisor"),
        scenario("grid-bfs","grid-side",32,128,"One input item is a grid cell; output is every row-major distance from the supplied start, with -1 for walls/unreachable cells.","open","corridor","fragmented")
    );
    public static List<Scenario> scenarios(){return SCENARIOS;}
    public static List<Configuration> matrix(){
        var result=new ArrayList<Configuration>();
        for(var s:SCENARIOS)for(String shape:s.shapes())for(int size:new int[]{s.smallSize(),s.largeSize()})for(int seed:new int[]{1729,104729})
            result.add(new Configuration(s,shape,size,seed));
        return List.copyOf(result);
    }
    public static Input input(Configuration config,int rounds){
        Objects.requireNonNull(config);Scenario s=config.scenario();requireScenario(s);
        if(!s.shapes().contains(config.shape())||config.size()<2||config.size()>s.largeSize()||config.seed()<0||rounds<1||rounds>1024)
            throw new IllegalArgumentException("Configuration outside the bounded scenario domain");
        StringBuilder input=new StringBuilder().append(rounds).append('\n');long items=0;
        for(int round=0;round<rounds;round++){
            var random=new Random((config.seed()+round*0x9e3779b9L)&0xffffffffL);
            items=Math.addExact(items,generate(s.id(),config.shape(),config.size(),random,input));
            if(items>4_000_000L||input.length()>128*1024*1024)throw new IllegalArgumentException("Input exceeds preparation budget");
        }
        String text=input.toString();return new Input(text,oracle(s,text),items,rounds);
    }
    private static long generate(String id,String shape,int n,Random r,StringBuilder out){
        switch(id){
            case "dijkstra" -> {
                var edges=new ArrayList<Edge>();
                if(shape.equals("chain-shortcuts")){
                    for(int i=1;i<n;i++)edges.add(new Edge(i-1,i,1+r.nextInt(20)));
                    for(int i=0;i<3*n;i++)edges.add(new Edge(r.nextInt(n),r.nextInt(n),1+r.nextInt(100)));
                }else if(shape.equals("hub-spokes")){
                    for(int i=1;i<n;i++){edges.add(new Edge(0,i,1000+r.nextInt(1000)));edges.add(new Edge(i,0,1+r.nextInt(50)));}
                    edges.add(new Edge(0,n-1,1));
                    for(int i=1;i<n-1;i++)edges.add(new Edge(n-1,i,1+r.nextInt(50)));
                    for(int i=0;i<2*n;i++)edges.add(new Edge(r.nextInt(n),r.nextInt(n),1+r.nextInt(100)));
                }else{
                    int cut=Math.max(1,n*3/4);
                    for(int i=1;i<n;i++)if(i!=cut)edges.add(new Edge(i-1,i,(2+r.nextInt(4))*10000000));
                    for(int u=0;u<n;u++){int limit=u<cut?cut:n;for(int j=0;j<4&&u+1<limit;j++){
                        int jump=1+r.nextInt(Math.min(16,limit-u-1));edges.add(new Edge(u,u+jump,(2*jump+r.nextInt(4))*10000000));
                    }}
                }
                out.append(n).append(' ').append(edges.size()).append('\n');
                for(var e:edges)out.append(e.from()).append(' ').append(e.to()).append(' ').append(e.weight()).append('\n');
                return n+(long)edges.size();
            }
            case "sliding-window-max" -> {
                int width=Math.max(2,n/16),salt=r.nextInt(100000);out.append(n).append(' ').append(width).append('\n');
                int value=0;for(int i=0;i<n;i++){
                    if(shape.equals("increasing"))value=salt+i;else if(shape.equals("decreasing"))value=salt+n-i;
                    else if(i%13==0)value=r.nextInt(17)-8;
                    out.append(value).append(' ');
                }out.append('\n');return n;
            }
            case "coordinate-compression" -> {
                int[] values=new int[n];int salt=r.nextInt(997);
                for(int i=0;i<n;i++)values[i]=shape.equals("few-values")?r.nextInt(17)-8:shape.equals("wide-unique")?i*7919-n*4000+salt:i/4+salt;
                if(shape.equals("wide-unique"))shuffle(values,r);
                if(shape.equals("mostly-sorted"))for(int i=0;i<n/128;i++){int a=r.nextInt(n),b=r.nextInt(n);int t=values[a];values[a]=values[b];values[b]=t;}
                out.append(n).append('\n');numbers(out,values);return n;
            }
            case "interval-scheduling" -> {
                int machines=Math.max(1,Math.min(16,n/128));out.append(n).append(' ').append(machines).append('\n');
                int salt=r.nextInt(1000);
                for(int i=0;i<n;i++){
                    int start,end;
                    if(shape.equals("disjoint")){start=salt+3*i;end=start+1+r.nextInt(2);}
                    else if(shape.equals("congested")){start=r.nextInt(Math.max(2,n/64));end=start+n/8+1+r.nextInt(Math.max(2,n/16));}
                    else{start=4*r.nextInt(Math.max(2,n/64));end=start+4*(1+r.nextInt(8));}
                    out.append(start).append(' ').append(end).append('\n');
                }return n;
            }
            case "sparse-accumulator" -> {
                out.append(n).append('\n');int pairedKey=0,pairedDelta=0;
                for(int i=0;i<n;i++){
                    int key,delta;
                    if(shape.equals("cancellation-pairs")){
                        if(i%2==0){pairedKey=r.nextInt(2*n)-n;pairedDelta=1+r.nextInt(100);}
                        key=pairedKey;delta=i%2==0?pairedDelta:-pairedDelta;
                    }else{key=shape.equals("hot-keys")?r.nextInt(32)-16:r.nextInt(1000000000)-500000000;delta=r.nextInt(201)-100;}
                    out.append(key).append(' ').append(delta).append('\n');
                }return n;
            }
            case "word-frequency" -> {
                out.append(n).append('\n');int[] order=new int[n];for(int i=0;i<n;i++)order[i]=i;shuffle(order,r);
                String salt=letters(r.nextInt(26*26),2);
                for(int i=0;i<n;i++){
                    String word;
                    if(shape.equals("hot-short"))word="w"+salt+letters(r.nextInt(32),2);
                    else if(shape.equals("unique-sso-boundary"))word="w"+salt+letters(order[i],4)+"abcdefgh"+(order[i]%2==0?"":"z");
                    else word="sharedprefixsharedprefixsharedprefix"+salt+letters(r.nextInt(Math.max(2,n/4)),6);
                    out.append(word).append('\n');
                }return n;
            }
            case "bitset-knapsack" -> {
                int[] boundary=shape.equals("dense-small")?new int[]{0,1,63,65,127}:shape.equals("sparse-large")?new int[]{128,4096,4097,8192}:new int[]{0,64,128,4096,8192};
                out.append(n).append('\n');for(int i=0;i<n;i++)out.append(i<boundary.length?boundary[i]:shape.equals("dense-small")?1+r.nextInt(16):shape.equals("sparse-large")?2048+r.nextInt(2049):64*(1+r.nextInt(32))).append(' ');
                out.append('\n');return n;
            }
            case "grid-bfs" -> {
                char[][] cells=new char[n][n];var open=new ArrayList<Integer>();
                for(int y=0;y<n;y++)for(int x=0;x<n;x++){
                    boolean pass=shape.equals("open")||shape.equals("corridor")?(shape.equals("open")||y%2==0||x==((y/2)%2==0?n-1:0)):(y!=n/2&&r.nextInt(100)>=35);
                    cells[y][x]=pass?'.':'#';if(pass)open.add(y*n+x);
                }
                if(open.isEmpty()){cells[0][0]='.';open.add(0);}int start=open.get(r.nextInt(open.size()));
                out.append(n).append(' ').append(start/n).append(' ').append(start%n).append('\n');
                for(char[] row:cells)out.append(row).append('\n');return (long)n*n;
            }
            default -> throw new IllegalArgumentException("Unknown scenario");
        }
    }
    private static void shuffle(int[] values,Random r){for(int i=values.length-1;i>0;i--){int j=r.nextInt(i+1),t=values[i];values[i]=values[j];values[j]=t;}}
    private static void numbers(StringBuilder out,int[] values){for(int v:values)out.append(v).append(' ');out.append('\n');}
    private static String letters(int value,int width){char[] text=new char[width];for(int i=width-1;i>=0;i--){text[i]=(char)('a'+value%26);value/=26;}return new String(text);}
    private record Edge(int from,int to,int weight) {}
    private record Interval(int start,int finish,int id) {}

    /** Parse real input and solve without executing or translating the C++ workload. */
    public static String oracle(Scenario scenario,String stdin){
        requireScenario(scenario);var in=new Tokens(stdin);int rounds=in.bounded(1,1024);var result=new StringBuilder();
        for(int round=0;round<rounds;round++){
            var hash=new Hash();switch(scenario.id()){
                case "dijkstra" -> dijkstra(in,hash);
                case "sliding-window-max" -> sliding(in,hash);
                case "coordinate-compression" -> compression(in,hash);
                case "interval-scheduling" -> scheduling(in,hash);
                case "sparse-accumulator" -> accumulation(in,hash);
                case "word-frequency" -> words(in,hash);
                case "bitset-knapsack" -> knapsack(in,hash);
                case "grid-bfs" -> grid(in,hash);
                default -> throw new IllegalArgumentException("Unknown scenario");
            }
            result.append("round=").append(round).append(" observations=").append(hash.count).append(" hash=").append(Long.toUnsignedString(hash.finish())).append('\n');
        }
        in.end();return result.toString();
    }
    private static void dijkstra(Tokens in,Hash hash){
        int n=in.bounded(1,1024),m=in.bounded(0,8*n);var edges=new ArrayList<List<Edge>>();for(int i=0;i<n;i++)edges.add(new ArrayList<>());
        for(int i=0;i<m;i++){int u=in.bounded(0,n-1),v=in.bounded(0,n-1),w=in.bounded(0,1000000000);edges.get(u).add(new Edge(u,v,w));}
        // Independent O(V^2+E) selection, deliberately no priority queue or pair comparator.
        long[] distance=new long[n];Arrays.fill(distance,Long.MAX_VALUE);distance[0]=0;boolean[] used=new boolean[n];
        for(int step=0;step<n;step++){
            int u=-1;for(int v=0;v<n;v++)if(!used[v]&&(u<0||distance[v]<distance[u]))u=v;
            if(u<0||distance[u]==Long.MAX_VALUE)break;used[u]=true;
            for(var e:edges.get(u))distance[e.to()]=Math.min(distance[e.to()],distance[u]+e.weight());
        }for(long value:distance)hash.add(value==Long.MAX_VALUE?-1:value);
    }
    private static void sliding(Tokens in,Hash hash){
        int n=in.bounded(1,32768),width=in.bounded(1,n);int[] values=new int[n];for(int i=0;i<n;i++)values[i]=in.integer();
        // Multiset counts provide an independent alternative to the C++ monotonic deque.
        var counts=new TreeMap<Integer,Integer>();
        for(int i=0;i<n;i++){counts.merge(values[i],1,Integer::sum);if(i>=width){int old=values[i-width],left=counts.get(old)-1;if(left==0)counts.remove(old);else counts.put(old,left);}if(i+1>=width)hash.add(counts.lastKey());}
    }
    private static void compression(Tokens in,Hash hash){
        int n=in.bounded(1,32768);int[] values=new int[n];var unique=new TreeSet<Integer>();for(int i=0;i<n;i++){values[i]=in.integer();unique.add(values[i]);}
        var ranks=new HashMap<Integer,Integer>();hash.add(unique.size());int rank=0;for(int value:unique){ranks.put(value,rank++);hash.add(value);}for(int value:values)hash.add(ranks.get(value));
    }
    private static void scheduling(Tokens in,Hash hash){
        int n=in.bounded(1,16384),k=in.bounded(1,16);var jobs=new ArrayList<Interval>();
        for(int i=0;i<n;i++){int start=in.bounded(0,1000000000),finish=in.bounded(start+1,1000000001);jobs.add(new Interval(start,finish,i));}
        jobs.sort(Comparator.comparingInt(Interval::finish).thenComparingInt(Interval::start).thenComparingInt(Interval::id));
        int[] releases=new int[k];int accepted=0;
        for(var job:jobs){int best=-1;for(int i=0;i<k;i++)if(releases[i]<=job.start()&&(best<0||releases[i]>releases[best]))best=i;
            hash.add(job.id());hash.add(best>=0?1:0);if(best>=0){accepted++;releases[best]=job.finish();}}
        hash.add(accepted);Arrays.sort(releases);for(int time:releases)hash.add(time);
    }
    private static void accumulation(Tokens in,Hash hash){
        int n=in.bounded(1,16384);var values=new HashMap<Integer,Long>();
        for(int i=0;i<n;i++){int key=in.integer(),delta=in.integer();long next=Math.addExact(values.getOrDefault(key,0L),delta);hash.add(key);hash.add(next);if(next==0)values.remove(key);else values.put(key,next);}
        hash.add(values.size());var keys=new ArrayList<>(values.keySet());Collections.sort(keys);for(int key:keys){hash.add(key);hash.add(values.get(key));}
    }
    private static void words(Tokens in,Hash hash){
        int n=in.bounded(1,16384);var counts=new HashMap<String,Integer>();for(int i=0;i<n;i++){String word=in.word();if(word.length()>127)throw new IllegalArgumentException("Word is too long");for(char c:word.toCharArray())if(c>127)throw new IllegalArgumentException("ASCII input required");counts.merge(word,1,Integer::sum);}
        var ordered=new ArrayList<>(counts.keySet());Collections.sort(ordered);hash.add(ordered.size());for(String word:ordered){hash.add(word.length());for(int i=0;i<word.length();i++)hash.add(word.charAt(i));hash.add(counts.get(word));}
    }
    private static void knapsack(Tokens in,Hash hash){
        int n=in.bounded(1,512);boolean[] reachable=new boolean[4097];reachable[0]=true;
        for(int i=0;i<n;i++){int weight=in.bounded(0,8192);for(int sum=4096;sum>=weight;sum--)reachable[sum]|=reachable[sum-weight];}
        for(boolean value:reachable)hash.add(value?1:0);
    }
    private static void grid(Tokens in,Hash hash){
        int n=in.bounded(1,128),sy=in.bounded(0,n-1),sx=in.bounded(0,n-1);char[][] grid=new char[n][];
        for(int y=0;y<n;y++){String row=in.word();if(row.length()!=n||row.chars().anyMatch(c->c!='.'&&c!='#'))throw new IllegalArgumentException("Malformed grid row");grid[y]=row.toCharArray();}
        if(grid[sy][sx]!='.')throw new IllegalArgumentException("Blocked start");int[] distances=new int[n*n],queue=new int[n*n];Arrays.fill(distances,-1);
        int head=0,tail=0;queue[tail++]=sy*n+sx;distances[sy*n+sx]=0;int[] dy={-1,0,1,0},dx={0,1,0,-1};
        while(head<tail){int u=queue[head++],y=u/n,x=u%n;for(int d=0;d<4;d++){int ny=y+dy[d],nx=x+dx[d];if(ny>=0&&ny<n&&nx>=0&&nx<n&&grid[ny][nx]=='.'&&distances[ny*n+nx]<0){distances[ny*n+nx]=distances[u]+1;queue[tail++]=ny*n+nx;}}}
        for(int value:distances)hash.add(value);
    }
    public static long sequenceHash(long... values){Hash h=new Hash();for(long value:values)h.add(value);return h.finish();}
    private static final class Hash {long state=0xcbf29ce484222325L,count;void add(long value){state=(state^value)*1099511628211L;count++;}long finish(){return (state^count)*1099511628211L;}}
    private static void requireScenario(Scenario scenario){if(!SCENARIOS.contains(scenario))throw new IllegalArgumentException("Unknown scenario");}
    private static final class Tokens {
        private final String input;private int index;
        Tokens(String input){this.input=Objects.requireNonNull(input);}
        String word(){skip();int start=index;while(index<input.length()&&!Character.isWhitespace(input.charAt(index)))index++;if(start==index)throw new IllegalArgumentException("Unexpected end of input");return input.substring(start,index);}
        int integer(){try{return Integer.parseInt(word());}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid integer",e);}}
        int bounded(int min,int max){int value=integer();if(value<min||value>max)throw new IllegalArgumentException("Input value outside "+min+".."+max);return value;}
        void skip(){while(index<input.length()&&Character.isWhitespace(input.charAt(index)))index++;}
        void end(){skip();if(index!=input.length())throw new IllegalArgumentException("Trailing input");}
    }
}
