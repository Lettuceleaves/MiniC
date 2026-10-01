package minic.benchmark;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;

final class StlBenchmarkSupport {
    private StlBenchmarkSupport() {}
    static final List<String> BUILDS=List.of("gxx-stl","gxx-own","minic-opt","minic-base");
    // Williams design: every position and every directed adjacent pair occurs once per four rounds.
    private static final int[][] ORDERS={{0,1,3,2},{1,2,0,3},{2,3,1,0},{3,0,2,1}};
    static List<String> order(int repetition,int seed) {
        if(repetition<0||seed<0)throw new IllegalArgumentException("Negative schedule input");
        var names=new ArrayList<>(BUILDS);Collections.shuffle(names,new Random(seed));
        return Arrays.stream(ORDERS[repetition%4]).mapToObj(names::get).toList();
    }
    static double median(List<? extends Number> values) {
        if(values.isEmpty())throw new IllegalArgumentException("Missing samples");
        double[] sorted=values.stream().mapToDouble(Number::doubleValue).sorted().toArray();
        int middle=sorted.length/2;return sorted.length%2==1?sorted[middle]:sorted[middle-1]/2+sorted[middle]/2;
    }
    static double mad(List<? extends Number> values) {
        double median=median(values);return median(values.stream().map(n->Math.abs(n.doubleValue()-median)).toList());
    }
    static String hash(Path file)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(file)){byte[] b=new byte[8192];for(int n;(n=input.read(b))>=0;)if(n>0)digest.update(b,0,n);}
        return HexFormat.of().formatHex(digest.digest());
    }
    static String hash(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    static Map<String,String> manifest(Path root)throws Exception {
        var map=new TreeMap<String,String>();try(var files=Files.walk(root)){
            for(Path file:files.filter(Files::isRegularFile).sorted().toList())map.put(root.relativize(file).toString().replace('\\','/'),hash(file));
        }return Collections.unmodifiableMap(map);
    }
    static void extract(Path zip,Path destination)throws IOException {
        destination=destination.toAbsolutePath().normalize();Files.createDirectories(destination);
        try(var input=new ZipInputStream(Files.newInputStream(zip))){
            for(var entry=input.getNextEntry();entry!=null;entry=input.getNextEntry()){
                Path target=destination.resolve(entry.getName()).normalize();
                if(!target.startsWith(destination))throw new IOException("Archive path escapes destination");
                if(entry.isDirectory())Files.createDirectories(target);
                else{Files.createDirectories(target.getParent());Files.copy(input,target);}
            }
        }
    }
    static String absoluteClasspath(){return Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
            .map(p->Path.of(p).toAbsolutePath().normalize().toString()).collect(java.util.stream.Collectors.joining(File.pathSeparator));}
    static Map<String,String> loadedToolHashes()throws Exception {
        var result=new TreeMap<String,String>();
        for(Class<?> type:List.of(StlBenchmarkMain.class,StlBenchmarkSupport.class,StlBenchmarkReport.class,
                WindowsBenchmarkProcess.class,StlBenchmarkWorkloads.class,NativeBenchmarkSupport.class)){
            String name=type.getSimpleName()+".class";try(var input=type.getResourceAsStream(name)){
                if(input==null)throw new IOException("Missing runtime tool class "+type);
                result.put(type.getName(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
            }
        }return result;
    }
}
