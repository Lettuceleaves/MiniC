package minic.benchmark;

import java.nio.file.Path;
import java.util.Arrays;

/** Independent value oracle for the optional O11 microbenchmark. No timing or compiler dependency. */
public final class StlBulkCopyWorkload {
    private StlBulkCopyWorkload() {}
    public static final Path SOURCE=Path.of("benchmarks","stl","bulk-copy.cpp");
    public enum Mode { VECTOR_ASSIGN, VECTOR_ERASE, POINTER_COPY, POINTER_COPY_BACKWARD }
    public record Input(String stdin,String expectedStdout,long transferredElements,long observedElements) {}
    public static Input input(Mode mode,int size,int rounds,int iterations,int seed) {
        if(mode==null||size<1||size>262144||rounds<1||rounds>10000||iterations<1||iterations>10000||seed<0
                ||(long)size*rounds*iterations>200_000_000L)throw new IllegalArgumentException("invalid bulk-copy input");
        StringBuilder expected=new StringBuilder();long transferred=0,observed=0;
        for(int round=0;round<rounds;round++) {
            Random random=new Random((seed+(long)round*1013904223L)&0xffffffffL);
            long hash=0xcbf29ce484222325L;
            int[] values=new int[size+(mode.ordinal()>=2?8:0)];
            for(int i=0;i<values.length;i++)values[i]=random.next();
            int liveSize=size;
            for(int iteration=0;iteration<iterations;iteration++) {
                if(mode==Mode.VECTOR_ERASE)Arrays.fill(values,liveSize,size,0);
                random.state^=hash&0xffffffffL;
                values[random.next()%values.length]=random.next();
                hash=add(hash,iteration);
                if(mode==Mode.VECTOR_ASSIGN) {
                    int[] destination=values.clone();
                    hash=observe(hash,destination,destination.length);
                    transferred+=size;observed+=size;
                } else if(mode==Mode.VECTOR_ERASE) {
                    int count=1+random.next()%(size/4+1),first=random.next()%(size-count+1);
                    int[] before=values.clone();
                    for(int i=first;i<size-count;i++)values[i]=before[i+count];
                    liveSize=size-count;
                    hash=add(hash,first);hash=add(hash,count);hash=observe(hash,values,liveSize);
                    transferred+=size-first-count;observed+=liveSize;
                } else {
                    int gap=1+random.next()%8;
                    int[] before=values.clone();
                    if(mode==Mode.POINTER_COPY) {
                        for(int i=0;i<size;i++)values[i]=before[gap+i];
                        hash=add(hash,size);
                    } else {
                        for(int i=0;i<size;i++)values[gap+i]=before[i];
                        hash=add(hash,gap);
                    }
                    hash=observe(hash,values,values.length);
                    transferred+=size;observed+=values.length;
                }
            }
            expected.append("round=").append(round).append(" hash=").append(Long.toUnsignedString(hash)).append('\n');
        }
        return new Input(mode.ordinal()+" "+size+" "+rounds+" "+iterations+" "+seed+"\n",expected.toString(),transferred,observed);
    }
    private static long add(long hash,long value){return (hash^value)*1099511628211L;}
    private static long observe(long hash,int[] values,int count){hash=add(hash,count);for(int i=0;i<count;i++)hash=add(hash,values[i]);return hash;}
    private static final class Random {
        long state;Random(long seed){state=seed;}
        int next(){state=(state*1664525L+1013904223L)&0xffffffffL;return(int)((state>>>8)&65535L);}
    }
}
