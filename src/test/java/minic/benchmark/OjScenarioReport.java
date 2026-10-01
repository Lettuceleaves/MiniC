package minic.benchmark;

import java.util.*;

/** Pure report validation: never silently drops failed, duplicate or short observations. */
final class OjScenarioReport {
    static final List<String> BUILDS=List.of("minic-base","minic-opt","gxx-own","gxx-stl");
    static final List<String> MEASURED=List.of("minic-opt","gxx-own","gxx-stl");
    static final long MINIMUM_NANOS=100_000_000L;
    static final double MAXIMUM_RATIO=1.2;
    static boolean suitePassed(Set<String> required,Set<String> selected,boolean filtered,boolean functional,
                               Map<String,Summary> summaries){
        return !filtered&&functional&&!required.isEmpty()&&selected.equals(required)&&summaries.keySet().equals(required)
                &&summaries.values().stream().allMatch(s->s.valid&&s.passed);
    }
    private static final int[][] ORDERS={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
    static List<String> order(int repetition,int seed){
        if(repetition<0||seed<0)throw new IllegalArgumentException("Negative schedule");
        var names=new ArrayList<>(MEASURED);Collections.shuffle(names,new Random(seed));
        return Arrays.stream(ORDERS[repetition%6]).mapToObj(names::get).toList();
    }
    record Sample(String configuration,String build,String phase,int repetition,int order,int rounds,long inputItems,
                  String inputHash,String expectedHash,String outputHash,String status,long exitCode,long wallNanos,
                  long userCpuNanos,long kernelCpuNanos,long peakCommitBytes,String directory,Map<String,String> files) {
        Sample{files=Map.copyOf(files);}
        Map<String,String> fields(){
            var m=new TreeMap<String,String>();
            String[] keys={"configuration","build","phase","repetition","order","rounds","inputItems","inputHash","expectedHash","outputHash","status","exitCode","wallNanos","userCpuNanos","kernelCpuNanos","peakCommitBytes","directory"};
            Object[] values={configuration,build,phase,repetition,order,rounds,inputItems,inputHash,expectedHash,outputHash,status,exitCode,wallNanos,userCpuNanos,kernelCpuNanos,peakCommitBytes,directory};
            for(int i=0;i<keys.length;i++)m.put(keys[i],String.valueOf(values[i]));files.forEach((p,h)->m.put("file."+p,h));return m;
        }
        static Sample read(Map<String,String> m){
            var files=new TreeMap<String,String>();m.forEach((k,v)->{if(k.startsWith("file."))files.put(k.substring(5),v);});
            return new Sample(m.get("configuration"),m.get("build"),m.get("phase"),integer(m,"repetition"),integer(m,"order"),integer(m,"rounds"),number(m,"inputItems"),m.get("inputHash"),m.get("expectedHash"),m.get("outputHash"),m.get("status"),number(m,"exitCode"),number(m,"wallNanos"),number(m,"userCpuNanos"),number(m,"kernelCpuNanos"),number(m,"peakCommitBytes"),m.get("directory"),files);
        }
        Sample withWall(long n){var m=fields();m.put("wallNanos",""+n);return read(m);}
        Sample withInput(String hash,int rounds){var m=fields();m.put("inputHash",hash);m.put("rounds",""+rounds);return read(m);}
        Sample withOutput(String hash){var m=fields();m.put("outputHash",hash);return read(m);}
        Sample withStatus(String status,long exit){var m=fields();m.put("status",status);m.put("exitCode",""+exit);return read(m);}
        private static int integer(Map<String,String> m,String k){return Integer.parseInt(m.get(k));}
        private static long number(Map<String,String> m,String k){return Long.parseLong(m.get(k));}
    }
    record Summary(boolean valid,boolean passed,boolean shortSampleWarning,List<String> errors,Double optOverSystem,
                   Double ownOverSystem,Double optOverOwn,Map<String,Map<String,Object>> statistics){
        Map<String,Object> fields(){var m=new LinkedHashMap<String,Object>();m.put("valid",valid);m.put("passed",passed);m.put("shortSampleWarning",shortSampleWarning);m.put("errors",errors);m.put("optOverSystem",optOverSystem);m.put("ownOverSystem",ownOverSystem);m.put("optOverOwn",optOverOwn);m.put("statistics",statistics);return m;}
    }
    static Summary summarize(String config,List<Sample> all,int repetitions,int warmups,int seed){
        var errors=new ArrayList<String>();var selected=all.stream().filter(s->s.configuration.equals(config)).filter(s->!s.phase.equals("calibration")).toList();
        if(repetitions<6||repetitions%6!=0||warmups<1)errors.add("Unbalanced/insufficient sampling contract");
        var keys=new HashSet<String>();var statistics=new LinkedHashMap<String,Map<String,Object>>();boolean shortSample=false;
        Sample first=selected.isEmpty()?null:selected.getFirst();
        for(var s:selected){
            if(!keys.add(s.phase+"/"+s.repetition+"/"+s.build))errors.add("Duplicate observation");
            if(!s.status.equals("COMPLETED")||s.exitCode!=0||!s.expectedHash.equals(s.outputHash)||s.wallNanos<=0||s.rounds<1||s.rounds>1024||s.inputItems<1)errors.add("Failed observation");
            if(first!=null&&(!s.inputHash.equals(first.inputHash)||!s.expectedHash.equals(first.expectedHash)||s.rounds!=first.rounds||s.inputItems!=first.inputItems))errors.add("Unequal paired input/oracle/rounds");
            var order=s.phase.equals("verify")?BUILDS:order(Math.max(0,s.repetition),seed);
            int count=s.phase.equals("verify")?1:s.phase.equals("warmup")?warmups:s.phase.equals("measurement")?repetitions:0;
            if(s.repetition<0||s.repetition>=count||s.order<0||s.order>=order.size()||!order.get(s.order).equals(s.build))errors.add("Unexpected phase/order/build");
            if(s.phase.equals("measurement")&&s.wallNanos<MINIMUM_NANOS)shortSample=true;
        }
        for(String build:BUILDS)if(!keys.contains("verify/0/"+build))errors.add("Missing four-build verification");
        for(String build:MEASURED) {
            for(int i=0;i<warmups;i++)if(!keys.contains("warmup/"+i+"/"+build))errors.add("Missing warmup");
            for(int i=0;i<repetitions;i++)if(!keys.contains("measurement/"+i+"/"+build))errors.add("Missing measurement");
            var rows=selected.stream().filter(s->s.build.equals(build)&&s.phase.equals("measurement")).toList();
            if(!rows.isEmpty()){
                var times=rows.stream().map(Sample::wallNanos).toList();var m=new LinkedHashMap<String,Object>();
                m.put("samples",rows.size());m.put("medianWallNanos",StlBenchmarkSupport.median(times));m.put("madWallNanos",StlBenchmarkSupport.mad(times));
                m.put("minimumWallNanos",Collections.min(times));m.put("medianUserCpuNanos",StlBenchmarkSupport.median(rows.stream().map(Sample::userCpuNanos).toList()));
                m.put("medianKernelCpuNanos",StlBenchmarkSupport.median(rows.stream().map(Sample::kernelCpuNanos).toList()));
                m.put("maximumPeakCommitBytes",rows.stream().mapToLong(Sample::peakCommitBytes).max().orElseThrow());statistics.put(build,m);
            }
        }
        if(shortSample)errors.add("Measurement below 100 ms");
        Double opt=ratio(statistics,"minic-opt","gxx-stl"),own=ratio(statistics,"gxx-own","gxx-stl"),relative=ratio(statistics,"minic-opt","gxx-own");
        boolean valid=errors.isEmpty();return new Summary(valid,valid&&opt!=null&&opt<=MAXIMUM_RATIO,shortSample,List.copyOf(new LinkedHashSet<>(errors)),opt,own,relative,statistics);
    }
    private static Double ratio(Map<String,Map<String,Object>> statistics,String a,String b){
        if(!statistics.containsKey(a)||!statistics.containsKey(b))return null;double numerator=(double)statistics.get(a).get("medianWallNanos"),denominator=(double)statistics.get(b).get("medianWallNanos");
        return numerator>=0&&denominator>0?numerator/denominator:null;
    }
}
