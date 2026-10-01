package minic.benchmark;

import minic.cpp.support.BoundedProcess;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Host-only observer. Metrics describe the target process, never the supervising JVM. */
public final class WindowsBenchmarkProcess {
    private WindowsBenchmarkProcess() {}
    public enum Status { COMPLETED, TIMEOUT, OUTPUT_LIMIT, TOOL_ERROR }
    public record Result(Status status,long exitCode,long wallNanos,long userCpuNanos,long kernelCpuNanos,
                         long peakCommitBytes,long observerWallNanos,long win32Error,String stdout,String stderr,
                         Path reportPath,Path stdoutPath,Path stderrPath) {
        public void requireSuccess() {
            if(status!=Status.COMPLETED||exitCode!=0)throw new IllegalStateException(
                    "Native sample failed: "+status+" exit="+exitCode+" Win32="+win32Error+" report="+reportPath+" stderr="+stderr);
        }
    }
    public static List<String> supervisorCommand(String compiler,Path source,Path executable) {
        return List.of(compiler,"-std=c++17","-O2","-municode","-mwindows",source.toAbsolutePath().toString(),
                "-o",executable.toAbsolutePath().toString(),"-lshell32");
    }
    public static Path buildSupervisor(String compiler,Path source,Path directory,Duration timeout)throws Exception {
        Files.createDirectories(directory);Path executable=directory.resolve("windows-supervisor.exe").toAbsolutePath();
        if(Files.exists(executable))throw new IllegalArgumentException("Supervisor output already exists: "+executable);
        var command=supervisorCommand(compiler,source,executable);
        var result=BoundedProcess.run(command,directory.toAbsolutePath(),"",timeout,65536);
        Files.writeString(directory.resolve("compile-command.json"),NativeBenchmarkReport.encode(command)+"\n");
        Files.writeString(directory.resolve("compile-stdout.txt"),result.stdout());Files.writeString(directory.resolve("compile-stderr.txt"),result.stderr());
        if(result.timedOut()||result.outputExceeded()||result.exitCode()!=0||!Files.isRegularFile(executable))
            throw new IOException("Cannot compile Windows observer: "+result);
        return executable;
    }
    public static Result run(Path supervisor,Path executable,Path directory,String stdin,Duration timeout,int outputLimit)throws Exception {
        if(!System.getProperty("os.name").startsWith("Windows"))throw new IllegalStateException("Windows x64 observer required");
        if(timeout.toMillis()<1||timeout.toMillis()>3_600_000||outputLimit<1||outputLimit>67_108_864)
            throw new IllegalArgumentException("Invalid native sample limits");
        if(!Files.isRegularFile(supervisor)||!Files.isRegularFile(executable))throw new IllegalArgumentException("Missing observer or executable");
        directory=directory.toAbsolutePath().normalize();Files.createDirectories(directory);
        Path input=directory.resolve("stdin.txt"),stdout=directory.resolve("stdout.txt"),stderr=directory.resolve("stderr.txt"),report=directory.resolve("observer.properties");
        for(Path file:List.of(input,stdout,stderr,report,directory.resolve("observer-stdout.txt"),directory.resolve("observer-stderr.txt")))
            if(Files.exists(file))throw new IllegalArgumentException("Sample already exists: "+file);
        Files.writeString(input,stdin,StandardOpenOption.CREATE_NEW);
        var command=List.of(supervisor.toAbsolutePath().toString(),"--exe="+executable.toAbsolutePath(),"--stdin="+input,
                "--stdout="+stdout,"--stderr="+stderr,"--report="+report,"--timeout-ms="+timeout.toMillis(),"--output-limit="+outputLimit);
        Files.writeString(directory.resolve("command.json"),NativeBenchmarkReport.encode(command)+"\n",StandardOpenOption.CREATE_NEW);
        long started=System.nanoTime();
        Process process=new ProcessBuilder(command).directory(directory.toFile())
                .redirectOutput(directory.resolve("observer-stdout.txt").toFile()).redirectError(directory.resolve("observer-stderr.txt").toFile()).start();
        try {
            // The observer's Job Object owns descendants; no system-wide process polling affects the sample.
            if(!process.waitFor(timeout.toMillis()+10_000,TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);
                throw new IOException("Native observer exceeded its outer deadline: "+directory);
            }
            long wall=System.nanoTime()-started;
            if(process.exitValue()!=0||!Files.isRegularFile(report))throw new IOException("Native observer failed: exit="+process.exitValue()+" directory="+directory);
            return parse(Files.readString(report),wall,readBounded(stdout,outputLimit),readBounded(stderr,outputLimit),report,stdout,stderr);
        } finally {
            if(process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}
        }
    }
    private static String readBounded(Path path,int limit)throws IOException {
        if(!Files.exists(path))return "";
        if(Files.size(path)>limit)throw new IOException("Observer exceeded capture limit: "+path);
        return Files.readString(path);
    }
    static Result parse(String text,long observerWall,String stdout,String stderr,Path report,Path out,Path err) {
        var fields=new LinkedHashMap<String,String>();
        for(String line:text.lines().toList()) {
            int equal=line.indexOf('=');if(equal<1||fields.put(line.substring(0,equal),line.substring(equal+1))!=null)
                throw new IllegalArgumentException("Malformed observer report");
        }
        if(!fields.keySet().equals(Set.of("schemaVersion","status","exitCode","win32Error","wallNanos","userCpuNanos","kernelCpuNanos","peakCommitBytes"))
                ||!"1".equals(fields.get("schemaVersion")))throw new IllegalArgumentException("Unknown observer report schema");
        Status status=Status.valueOf(fields.get("status").toUpperCase(Locale.ROOT));
        return new Result(status,nonnegative(fields,"exitCode"),nonnegative(fields,"wallNanos"),nonnegative(fields,"userCpuNanos"),
                nonnegative(fields,"kernelCpuNanos"),nonnegative(fields,"peakCommitBytes"),observerWall,nonnegative(fields,"win32Error"),stdout,stderr,report,out,err);
    }
    private static long nonnegative(Map<String,String> fields,String key){long n=Long.parseLong(fields.get(key));if(n<0)throw new IllegalArgumentException("Negative "+key);return n;}
}
