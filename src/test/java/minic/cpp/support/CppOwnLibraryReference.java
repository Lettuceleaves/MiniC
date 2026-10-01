package minic.cpp.support;

import minic.compiler.library.CppLibraryProfile;
import minic.compiler.library.SystemLibraryCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import static minic.cpp.support.CppDifferentialHarness.Status;

/** Reference-only fourth build: the host compiler compiles MiniC's real .mh sources.
 * -nostdinc++ prevents a C compatibility wrapper from silently importing host STL templates.
 * C headers, the ABI runtime and compiler support remain owned by the reference toolchain. */
public final class CppOwnLibraryReference {
    private CppOwnLibraryReference() {}
    public record Result(Status status,int exitCode,String stdout,String stderr,String diagnostics) {
        public boolean passed(){return status==Status.OK && exitCode==0;}
    }
    public static Result run(Path temporary,String source,String stdin,CppDifferentialHarness.Limits limits)
            throws IOException,InterruptedException {
        return compileOrRun(temporary,source,stdin,limits,true);
    }
    public static Result compile(Path temporary,String source,CppDifferentialHarness.Limits limits)
            throws IOException,InterruptedException {
        return compileOrRun(temporary,source,"",limits,false);
    }
    private static Result compileOrRun(Path temporary,String source,String stdin,CppDifferentialHarness.Limits limits,boolean run)
            throws IOException,InterruptedException {
        Files.createDirectories(temporary);
        Path directory=Files.createTempDirectory(temporary,"own-library-");
        Path headers=prepareHeaders(directory.resolve("headers"),SystemLibraryCatalog.defaults().includeRoot().resolve("cpp"));
        Path program=directory.resolve("program.cpp"),executable=directory.resolve("program.exe");
        Files.writeString(program,source);
        var command=compileCommand(CppDifferentialHarness.referenceCompiler(System.getenv()),directory,headers,program,executable);
        var compile=BoundedProcess.run(command,directory,"",limits.compileTimeout(),limits.maxOutputBytes());
        if(compile.timedOut())return new Result(Status.COMPILE_TIMEOUT,-1,"","",compile.stderr());
        if(compile.outputExceeded())return new Result(Status.OUTPUT_LIMIT,-1,"","",compile.stderr());
        if(compile.exitCode()!=0)return new Result(Status.COMPILE_ERROR,compile.exitCode(),"","",compile.stdout()+compile.stderr());
        if(!run)return new Result(Status.OK,0,"","","");
        var execute=BoundedProcess.run(List.of(executable.toString()),directory,stdin,limits.runTimeout(),limits.maxOutputBytes());
        Status status=execute.timedOut()?Status.RUN_TIMEOUT:execute.outputExceeded()?Status.OUTPUT_LIMIT:
                execute.exitCode()==0?Status.OK:Status.NONZERO_EXIT;
        return new Result(status,execute.exitCode(),execute.stdout(),execute.stderr(),"");
    }
    /** Creates only catalogued extensionless shims over the supplied, frozen .mh tree. */
    public static Path prepareHeaders(Path headers,Path library)throws IOException {
        headers=headers.toAbsolutePath().normalize();library=library.toAbsolutePath().normalize();
        Files.createDirectories(headers);
        var names=new LinkedHashSet<String>();
        for(var entry:CppLibraryProfile.defaults().entries().values())
            names.add(entry.header().substring("lib/cpp/".length(),entry.header().length()-3));
        for(String name:names){
            Path implementation=library.resolve(name+".mh");
            if(!Files.isRegularFile(implementation))throw new IOException("Missing own-library implementation: "+implementation);
            writeShim(headers.resolve(name),implementation);
        }
        if(names.contains("__all"))writeShim(headers.resolve("bits/stdc++.h"),library.resolve("__all.mh"));
        return headers;
    }

    /** The caller owns execution, time limits, logs and the persistent output artifact. */
    public static List<String> compileCommand(String compiler,Path directory,Path headers,Path source,Path executable)throws IOException {
        var command=new ArrayList<>(List.of(compiler));
        command.addAll(CppDifferentialHarness.referenceFlags(directory));
        command.addAll(List.of("-nostdinc++","-D__MINIC_SELF_STL__=1","-I",headers.toAbsolutePath().toString(),
                source.toAbsolutePath().toString(),"-o",executable.toAbsolutePath().toString()));
        return List.copyOf(command);
    }

    private static void writeShim(Path file,Path implementation)throws IOException{
        Files.createDirectories(file.getParent());
        Files.writeString(file,"#include \""+implementation.toString().replace('\\','/')+"\"\n");
    }
}
