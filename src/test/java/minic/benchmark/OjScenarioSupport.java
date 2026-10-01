package minic.benchmark;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

final class OjScenarioSupport {
    private OjScenarioSupport(){}
    static Path resolve(Path root,String relative)throws IOException {
        root=root.toAbsolutePath().normalize();Path part=Path.of(relative);
        if(relative.isBlank()||part.isAbsolute()||relative.contains(":"))throw new IOException("Not a relative evidence path: "+relative);
        Path target=root.resolve(part).normalize();
        if(!target.startsWith(root)||target.equals(root))throw new IOException("Evidence path escapes root: "+relative);
        if(Files.exists(target)&&!target.toRealPath().startsWith(root.toRealPath()))throw new IOException("Evidence symlink escapes root");
        return target;
    }
    static void auditFiles(Path root,Map<String,String> files)throws Exception {
        if(files.isEmpty())throw new IOException("Empty evidence manifest");
        for(var e:files.entrySet())if(!StlBenchmarkSupport.hash(resolve(root,e.getKey())).equals(e.getValue()))throw new IOException("Hash mismatch: "+e.getKey());
    }
    static void writeProperties(Path path,Map<String,String> values)throws IOException {
        Files.createDirectories(path.getParent());var properties=new Properties();properties.putAll(values);
        try(var out=Files.newBufferedWriter(path)){properties.store(out,"OJ scenario evidence v1");}
    }
    static Map<String,String> readProperties(Path path)throws IOException {
        var p=new UniqueProperties();
        try(var reader=Files.newBufferedReader(path)){p.load(reader);}
        var result=new TreeMap<String,String>();for(var key:p.stringPropertyNames())result.put(key,p.getProperty(key));return result;
    }
    private static final class UniqueProperties extends Properties {
        @Override public synchronized Object put(Object key,Object value){
            if(containsKey(key))throw new IllegalArgumentException("Duplicate property "+key);return super.put(key,value);
        }
    }
    static Map<String,String> classHashes(Class<?>... classes)throws Exception {
        var result=new TreeMap<String,String>();
        var pending=new ArrayDeque<Class<?>>(List.of(classes));var visited=new HashSet<Class<?>>();
        while(!pending.isEmpty()){var type=pending.removeFirst();if(!visited.add(type))continue;pending.addAll(List.of(type.getDeclaredClasses()));
        String resource=type.getName().substring(type.getPackageName().length()+1)+".class";
        try(var in=type.getResourceAsStream(resource)){
            if(in==null)throw new IOException("Missing tool bytecode "+type);result.put(type.getName(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        }}return result;
    }
    static void json(Path path,Object value)throws IOException {Files.createDirectories(path.getParent());Files.writeString(path,NativeBenchmarkReport.encode(value)+"\n");}
}
