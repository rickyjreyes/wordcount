package org.wordcountlab.experimental;

import org.wordcountlab.Frequency;
import org.wordcountlab.Sorts;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reproducible multi-seed, multi-shape screening. NOT JMH: independent JVM forks,
 * warmup convergence, allocation/GC profiling and cross-host replication are
 * still required before scientific speed claims.
 *
 * Every candidate, warmup and measured result is compared to an independent
 * reference array. Raw samples and aggregate medians/ranges are archived.
 */
public final class ThoroughLab {
    private ThoroughLab() {}
    private static final Set<String> QUADRATIC=Set.of("insertion","selection","bubble","cocktail");
    private static final List<String> DEFAULT_METHODS=List.of(
        "jdk-native","merge","freqbucket","radix","sparsebucket","pdq","parallel",
        "quick","heap","shell","comb","wct","rca","prime","hybrid"
    );
    private static final List<String> DATASETS=List.of(
        "random","sorted","reverse","nearly","few-counts","equal","runs",
        "zipf","sparse","unicode","prefix"
    );
    public static void main(String[] args) {System.exit(run(args,System.out,System.err));}
    static int run(String[] args, java.io.PrintStream out,java.io.PrintStream err) {
        if(args.length==0 || (args.length==1&&args[0].equals("--help"))) {
            out.println("ThoroughLab: raw paired multi-seed screening, NOT JMH.");
            out.println("  [--datasets random,zipf,sparse,equal,sorted] [--sizes 128,2048,8192]");
            out.println("  [--seeds 2017,42,73] [--methods jdk-native,merge,freqbucket,radix,...]");
            out.println("  [--runs 7] [--warmup 3] [--out modern/target/thorough]");
            out.println("  CSV: samples.csv, summary.csv; file: environment.txt");
            return 0;
        }
        try {
            if(args.length%2!=0)throw new IllegalArgumentException("Expected --option value pairs");
            Set<String> allowed=Set.of("--datasets","--sizes","--seeds","--methods","--runs","--warmup","--out");
            Map<String,String> opts=new HashMap<>();
            for(int i=0;i<args.length;i+=2) {
                if(!allowed.contains(args[i])||opts.putIfAbsent(args[i],args[i+1])!=null)
                    throw new IllegalArgumentException("Unknown or repeated option "+args[i]);
            }
            List<String> shapes=parse(opts.getOrDefault("--datasets","random,sorted,reverse,nearly,few-counts,equal,runs,zipf,sparse,unicode,prefix"));
            for(String shape:shapes)if(!DATASETS.contains(shape))throw new IllegalArgumentException("Unknown shape "+shape);
            List<Integer> sizes=integers(opts.getOrDefault("--sizes","128,2048,8192,32768"),0,100_000);
            List<Integer> seeds=integers(opts.getOrDefault("--seeds","2017,42,73,101,4099"),Integer.MIN_VALUE,Integer.MAX_VALUE);
            List<String> methods=parse(opts.getOrDefault("--methods",String.join(",",DEFAULT_METHODS)));
            for(String method:methods)if(!ResearchSorts.METHODS.contains(method))
                throw new IllegalArgumentException("Unknown method "+method);
            if(!methods.contains("merge"))methods.add(0,"merge");
            int runs=bounded(opts.getOrDefault("--runs","7"),1,50);
            int warmup=bounded(opts.getOrDefault("--warmup","3"),0,20);
            Path directory=Path.of(opts.getOrDefault("--out","modern/target/thorough"));
            Files.createDirectories(directory);
            try(PrintWriter raw=new PrintWriter(Files.newBufferedWriter(directory.resolve("samples.csv"),StandardCharsets.UTF_8));
                PrintWriter summary=new PrintWriter(Files.newBufferedWriter(directory.resolve("summary.csv"),StandardCharsets.UTF_8))) {
                raw.println("dataset,n,seed,method,implementation,iteration,nanoseconds,input_sha256,oracle_sha256,count_range,distinct_counts,max_tie_group");
                summary.println("dataset,n,seed,method,implementation,status,samples,median_ns,p10_ns,p90_ns,min_ns,max_ns,ratio_to_merge,input_sha256,oracle_sha256,count_range,distinct_counts,max_tie_group");
                for(String shape:shapes)for(int size:sizes)for(int seed:seeds) {
                    Frequency[] input=ExperimentLab.generated(shape,size,seed);
                    if(exercise(input,shape,size,seed,methods,runs,warmup,raw,summary,err)!=0)return 1;
                }
            }
            String env="revision=record exact Git commit SHA externally\n"+
                "java.runtime.version="+System.getProperty("java.runtime.version")+"\n"+
                "java.vm.name="+System.getProperty("java.vm.name")+"\n"+
                "java.vendor="+System.getProperty("java.vendor")+"\n"+
                "os.name="+System.getProperty("os.name")+"\n"+
                "os.arch="+System.getProperty("os.arch")+"\n"+
                "os.version="+System.getProperty("os.version")+"\n"+
                "available.processors="+Runtime.getRuntime().availableProcessors()+"\n"+
                "max.memory.bytes="+Runtime.getRuntime().maxMemory()+"\n"+
                "shapes="+shapes+"\nsizes="+sizes+"\nseeds="+seeds+"\nmethods="+methods+
                "\nruns="+runs+"\nwarmup="+warmup+"\n"+
                "timed=clone+entire ResearchSorts.sort; oracle+CSV excluded; no JVM forks or fixed GC\n";
            Files.writeString(directory.resolve("environment.txt"),env,StandardCharsets.UTF_8);
            out.println("PASS: samples.csv, summary.csv, environment.txt in "+directory);
            return 0;
        }catch(IOException|IllegalArgumentException e){err.println("ERROR: "+e.getMessage());return 2;}
    }
    private static int exercise(Frequency[] input,String shape,int size,int seed,
        List<String> methods,int runs,int warmup,PrintWriter raw,PrintWriter summary,
        java.io.PrintStream err) {
        Frequency[] oracle=input.clone();Arrays.sort(oracle,Sorts.ORDER);
        String hash=ResearchSorts.digest(input),expectedHash=ResearchSorts.digest(oracle);
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE,largest=0;
        Map<Integer,Integer> counts=new HashMap<>();
        for(Frequency f:input){
            min=Math.min(min,f.count());max=Math.max(max,f.count());
            largest=Math.max(largest,counts.merge(f.count(),1,Integer::sum));
        }
        long range=input.length==0?0:(long)max-min+1;
        Map<String,long[]> timings=new LinkedHashMap<>();
        Map<String,String> implementations=new LinkedHashMap<>();
        for(String method:methods) {
            if(QUADRATIC.contains(method)&&size>4096) {
                summary.printf(Locale.ROOT,"%s,%d,%d,%s,,SKIPPED_QUADRATIC,0,,,,,,,%s,%s,%d,%d,%d%n",
                    shape,size,seed,method,hash,expectedHash,range,counts.size(),largest);
                continue;
            }
            ResearchSorts.Result test=ResearchSorts.sort(input,method);
            if(!valid(test,input,oracle,hash,expectedHash,method)) {
                err.println("FAIL preflight shape="+shape+" n="+size+" seed="+seed+" method="+method);return 1;
            }
            implementations.put(method,test.implementation());
            timings.put(method,new long[runs]);
            for(int w=0;w<warmup;w++) {
                ResearchSorts.Result warmed=ResearchSorts.sort(input,method);
                if(!valid(warmed,input,oracle,hash,expectedHash,method)){
                    err.println("FAIL warmup "+shape+" "+method);return 1;
                }
            }
        }
        Random rng=new Random(((long)seed<<32)^size^shape.hashCode());
        for(int i=0;i<runs;i++) {
            List<String> order=new ArrayList<>(timings.keySet());
            Collections.shuffle(order,rng);
            for(String method:order) {
                long start=System.nanoTime();
                ResearchSorts.Result result=ResearchSorts.sort(input,method);
                long elapsed=System.nanoTime()-start;
                if(!valid(result,input,oracle,hash,expectedHash,method)){
                    err.println("FAIL measurement shape="+shape+" n="+size+" seed="+seed+" method="+method+" iteration="+i);
                    return 1;
                }
                timings.get(method)[i]=elapsed;
                raw.printf(Locale.ROOT,"%s,%d,%d,%s,%s,%d,%d,%s,%s,%d,%d,%d%n",
                    shape,size,seed,method,csv(result.implementation()),i,elapsed,hash,expectedHash,range,counts.size(),largest);
            }
        }
        double merge=percentile(timings.get("merge"),.5);
        for(var entry:timings.entrySet()) {
            String method=entry.getKey();
            long[] observations=entry.getValue();
            summary.printf(Locale.ROOT,"%s,%d,%d,%s,%s,PASS,%d,%.1f,%.1f,%.1f,%d,%d,%.6f,%s,%s,%d,%d,%d%n",
                shape,size,seed,method,csv(implementations.get(method)),runs,
                percentile(observations,.5),percentile(observations,.1),percentile(observations,.9),
                Arrays.stream(observations).min().orElseThrow(),Arrays.stream(observations).max().orElseThrow(),
                percentile(observations,.5)/merge,hash,expectedHash,range,counts.size(),largest);
        }
        return 0;
    }
    private static boolean valid(ResearchSorts.Result r,Frequency[] input,Frequency[] expected,
        String inputHash,String expectedHash,String method) {
        if(!Arrays.equals(r.values(),expected) || !ResearchSorts.digest(input).equals(inputHash))return false;
        return !method.equals("crypto")||(Objects.equals(inputHash,r.inputSha256())&&Objects.equals(expectedHash,r.outputSha256()));
    }
    static double percentile(long[] values,double p) {
        long[] sorted=values.clone();Arrays.sort(sorted);
        if(sorted.length==1)return sorted[0];
        double position=p*(sorted.length-1);
        int low=(int)Math.floor(position),high=(int)Math.ceil(position);
        return sorted[low]+(sorted[high]-sorted[low])*(position-low);
    }
    private static String csv(String value) {
        return value.indexOf(',')>=0 || value.indexOf('"')>=0 || value.indexOf('\n')>=0
            ? "\"" + value.replace("\"","\"\"") + "\"" : value;
    }
    private static List<String> parse(String value) {
        List<String> out=new ArrayList<>();
        for(String part:value.split(",",-1)){
            String item=part.trim();
            if(item.isEmpty()||out.contains(item))throw new IllegalArgumentException("Empty or duplicate CSV value");
            out.add(item);
        }
        return out;
    }
    private static List<Integer> integers(String value,int min,int max) {
        List<Integer> out=new ArrayList<>();
        for(String item:parse(value))out.add(bounded(item,min,max));
        return out;
    }
    private static int bounded(String s,int min,int max) {
        int x;
        try{x=Integer.parseInt(s);}catch(NumberFormatException e){throw new IllegalArgumentException("Bad integer: "+s,e);}
        if(x<min||x>max)throw new IllegalArgumentException("Outside allowed range ["+min+","+max+"]: "+s);
        return x;
    }
}
