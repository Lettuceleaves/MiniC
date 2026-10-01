package minic.cpp.support;
import java.util.*;
/** Count acceptance, never elapsed-time measurements. */
public final class CppComplexityContracts {
 private CppComplexityContracts() {}
 public record Row(String kind,Map<String,Long> values) { public Row { values=Map.copyOf(values); } }
 public static List<Row> validate(String kind,int n,String stdout) {
  if(!Set.of("sort","vector","tree").contains(kind)||n<8||n>4096)throw new IllegalArgumentException("unsupported count model");
  int expected=kind.equals("sort")?6:kind.equals("vector")?3:5;
  var lines=stdout.replace("\r\n","\n").lines().toList();check(lines.size()==expected,"incomplete counter rows");
  List<Row> result=new ArrayList<>();Set<Long> seen=new HashSet<>();int log=32-Integer.numberOfLeadingZeros(n-1);
  for(String line:lines){
   String[] words=line.split(" ");check(words[0].equals(kind),"wrong counter kind");Map<String,Long> m=new LinkedHashMap<>();
   for(int i=1;i<words.length;i++){String[] field=words[i].split("=",-1);check(field.length==2,"malformed counter field");try{long v=Long.parseLong(field[1]);check(v>=0&&m.putIfAbsent(field[0],v)==null,"negative/duplicate counter");}catch(NumberFormatException invalid){throw new IllegalStateException("invalid counter number",invalid);}}
   long mode=get(m,"case");check(mode<expected&&seen.add(mode),"duplicate/unknown case");check(get(m,"n")==n,"input size mismatch");
   check(get(m,"checksum")==checksum(kind,(int)mode,n),"independent result checksum");
   if(kind.equals("sort")) {
    // 16 is deliberately loose across different conforming O(n log n) sorts.
    // At n=4096 this is 851968, far below n(n-1)/2 = 8386560.
    check(get(m,"comparisons")>0&&get(m,"comparisons")<=16L*n*(log+1),"sort comparisons exceed n-log-n bound");
   } else {
    long copies=get(m,kind.equals("tree")?"all_copies":"copies"),moves=get(m,"moves"),value=get(m,"value");
    check(get(m,"live")==0&&get(m,"destroyed")==value+copies+moves,"lifetime imbalance");
    check(get(m,"allocations")>0,"allocation hooks did not observe storage");
    check(get(m,"allocations")==get(m,"frees")&&get(m,"bytes")==get(m,"freed_bytes"),"allocation/free imbalance");
    if(kind.equals("vector")) {
     check(value==n,"vector value construction count");
     check(copies+moves<=8L*n+64,"vector relocation exceeds linear bound");
     check(get(m,"allocations")<=2L*log+4,"vector growth allocation count");
     if(mode==1)check(get(m,"extra_alloc")==0&&get(m,"extra_copy")==0&&get(m,"extra_move")==0&&get(m,"stable")==1,"reserve below capacity reallocates or relocates");
    } else {
     check(get(m,"comparisons")<=8L*n+16,"hint comparisons exceed linear bound");
     check(get(m,"copy_comparisons")<=4L*n+16,"copy comparisons exceed linear bound");
     check(get(m,"copies")>=n&&get(m,"copies")<=2L*n,"tree copy element count");
     check(get(m,"copy_allocations")>=1&&get(m,"copy_allocations")<=2L*n+4,"tree copy allocations");
     check(value+copies+moves<=16L*n+64&&get(m,"allocations")<=4L*n+8,"tree total work count");
    }
   }
   result.add(new Row(kind,m));
  }
  return List.copyOf(result);
 }
 private static long get(Map<String,Long> values,String key){Long v=values.get(key);if(v==null)throw new IllegalStateException("missing counter "+key);return v;}
 private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
 public static long checksum(String kind,int mode,int n) {
  int[] values=new int[n];for(int i=0;i<n;i++)values[i]=kind.equals("sort")?switch(mode){case 0->i;case 1->n-1-i;case 2->7;case 3->i*37%5;case 4->i<n/2?i:n-i;default->i<n/2?(i%2==0?i+1:n/2+i):2*(i-n/2)+2;}:kind.equals("tree")&&mode==4?i/2:i;
  Arrays.sort(values);long h=0;for(int v:values)h=(h*131+v+10000)%1000000007;return h;
 }
}
