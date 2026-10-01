package minic.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Count builds have their own source and exact copied headers. Never use their times as benchmark samples. */
public final class StlBenchmarkProbes {
    private StlBenchmarkProbes() {}
    public record HeaderDigest(String path,String originalSha256,String instrumentedSha256) {}
    public record Instrumentation(Path projectRoot,List<HeaderDigest> headers,String strategy) {
        public Instrumentation{headers=List.copyOf(headers);}
    }
    public record ProbeRound(int round,long hash,Map<String,Long> counters){public ProbeRound{counters=Map.copyOf(counters);}}
    public static String source(Path repository)throws IOException {
        Path root=repository.resolve("benchmarks/stl");
        return Files.readString(root.resolve("probe-hooks.mh"))+"\n"+Files.readString(root.resolve("probe.cpp"));
    }
    public static String input(StlBenchmarkWorkloads.Workload workload,int size,int rounds,int seed){
        StlBenchmarkWorkloads.input(workload,size,rounds,seed);
        if(size>4096)throw new IllegalArgumentException("counter probes are bounded to 4096 input elements");
        return StlBenchmarkWorkloads.workloads().indexOf(workload)+" "+size+" "+rounds+" "+seed+"\n";
    }
    public static Instrumentation instrumentOwnHeaders(Path repository,Path newProjectRoot)throws IOException {
        Path original=repository.resolve("lib").toAbsolutePath().normalize();
        Path destination=newProjectRoot.toAbsolutePath().normalize();
        if(destination.startsWith(original))throw new IllegalArgumentException("instrumentation must be outside the original library");
        String memory=Files.readString(original.resolve("cpp/memory.mh"));
        if(occurrences(memory,"::malloc(")!=2||occurrences(memory,"::free(")!=1)
            throw new IllegalStateException("memory.mh backing-allocation hook shape changed; review before instrumenting");
        String instrumented=memory.replace("::malloc(","::bench_allocate(").replace("::free(","::bench_release(");
        Files.createDirectories(destination.getParent());Files.createDirectory(destination);
        List<HeaderDigest> headers=new ArrayList<>();
        try(var paths=Files.walk(original)){
            for(Path file:paths.filter(Files::isRegularFile).sorted().toList()){
                Path relative=original.relativize(file);byte[] before=Files.readAllBytes(file);
                byte[] after=relative.toString().replace('\\','/').equals("cpp/memory.mh")?instrumented.getBytes(StandardCharsets.UTF_8):before;
                Path output=destination.resolve("lib").resolve(relative);Files.createDirectories(output.getParent());Files.write(output,after);
                headers.add(new HeaderDigest("lib/"+relative.toString().replace('\\','/'),sha(before),sha(after)));
            }
        }
        String strategy="count-only: own headers hook two backing malloc and one free call in memory.mh; host STL hooks global new/delete; byte counts include actual allocator requests and over-alignment overhead, exclude unrelated C-runtime allocation; object-operation counters describe Tracked payloads only and remain zero for string/bitset workloads";
        StringBuilder manifest=new StringBuilder("strategy=").append(strategy).append('\n');
        for(var item:headers)manifest.append(item.path()).append('\t').append(item.originalSha256()).append('\t').append(item.instrumentedSha256()).append('\n');
        Files.writeString(destination.resolve("instrumented-headers.tsv"),manifest);
        return new Instrumentation(destination,headers,strategy);
    }
    private static int occurrences(String text,String token){int count=0,start=0;while((start=text.indexOf(token,start))>=0){count++;start+=token.length();}return count;}
    private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static final List<String> FIELDS=List.of("comparisons","value_ctor","copy_ctor","move_ctor","copy_assign","move_assign","destroyed","live","peak_live","allocations","frees","allocated_bytes","freed_bytes","live_bytes","peak_bytes");
    public static List<ProbeRound> validate(StlBenchmarkWorkloads.Workload workload,StlBenchmarkWorkloads.Input expected,String stdout){
        List<String> lines=stdout.replace("\r\n","\n").lines().toList(),checks=expected.expectedStdout().lines().toList();
        if(lines.size()!=checks.size())throw new IllegalStateException("counter round count mismatch");
        int size=Integer.parseInt(expected.stdin().split(" ")[0]);
        long complexity=128L*size*(33-Integer.numberOfLeadingZeros(size))+1024;
        List<ProbeRound> results=new ArrayList<>();
        for(int round=0;round<lines.size();round++){
            String line=lines.get(round);if(!line.startsWith(checks.get(round)+" "))throw new IllegalStateException("counter checksum mismatch at round "+round);
            Map<String,Long> fields=new LinkedHashMap<>();String[] words=line.substring(checks.get(round).length()+1).split(" ");
            for(String word:words){String[] item=word.split("=",-1);try{if(item.length!=2||!FIELDS.contains(item[0])||fields.putIfAbsent(item[0],Long.parseLong(item[1]))!=null)throw new IllegalStateException("invalid counter field: "+word);}catch(NumberFormatException invalid){throw new IllegalStateException("invalid counter number",invalid);}}
            if(fields.size()!=FIELDS.size()||fields.values().stream().anyMatch(value->value<0))throw new IllegalStateException("missing or negative counters");
            long constructed=Math.addExact(Math.addExact(fields.get("value_ctor"),fields.get("copy_ctor")),fields.get("move_ctor"));
            if(fields.get("destroyed")!=constructed||fields.get("live")!=0||fields.get("peak_live")>constructed
                    ||!fields.get("allocations").equals(fields.get("frees"))||!fields.get("allocated_bytes").equals(fields.get("freed_bytes"))
                    ||fields.get("live_bytes")!=0||fields.get("peak_bytes")>fields.get("allocated_bytes"))throw new IllegalStateException("unbalanced lifetime/allocation counters at round "+round);
            boolean tracked=!Set.of("string","bitset","string-short","bitset-count").contains(workload.id());
            if(tracked&&(constructed<size||fields.get("peak_live")==0||fields.get("allocations")==0))
                throw new IllegalStateException("tracking hooks did not observe workload objects/allocations");
            if(fields.get("comparisons")>complexity||constructed>complexity*4||fields.get("copy_assign")>complexity*4||fields.get("move_assign")>complexity*4||fields.get("allocations")>64L*size+1024)
                throw new IllegalStateException("counter exceeds broad n-log-n complexity guard for "+workload.id());
            String hash=checks.get(round).substring(checks.get(round).indexOf("hash=")+5);
            results.add(new ProbeRound(round,Long.parseUnsignedLong(hash),fields));
        }
        return List.copyOf(results);
    }
}
