package org.wordcountlab.experimental;

import org.wordcountlab.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Comparable input and exact reference for each candidate. Timing is exploratory (not JMH);
 * correctness is always established outside the measured interval.
 */
public final class ExperimentLab {
    private ExperimentLab() {}
    private static final int MAX_SIZE=100_000;
    private static final List<String> DATASETS=
        List.of("random","sorted","reverse","nearly","few-counts","equal","runs");

    public static void main(String[] args) {
        System.exit(run(args,System.out,System.err));
    }
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if(args.length==0 || List.of("--help","help").contains(args[0])) { usage(out);return 0; }
        try {
            Set<String> allowed=Set.of("--input","--dataset","--size","--seed","--runs","--warmup","--methods");
            Map<String,String> opts=new LinkedHashMap<>();
            for(int i=0;i<args.length;i+=2) {
                if(!allowed.contains(args[i])||i+1>=args.length||args[i+1].startsWith("--"))
                    throw new IllegalArgumentException("Expected --option value, got "+args[i]);
                if(opts.putIfAbsent(args[i],args[i+1])!=null)
                    throw new IllegalArgumentException("Duplicate option: "+args[i]);
            }
            if(opts.containsKey("--input")&&opts.containsKey("--dataset"))
                throw new IllegalArgumentException("Supply --input or --dataset, not both");
            if(opts.containsKey("--input")&&opts.containsKey("--size"))
                throw new IllegalArgumentException("--size is for generated datasets only");
            String dataset=opts.getOrDefault("--dataset","random");
            if(!DATASETS.contains(dataset))throw new IllegalArgumentException("Unknown dataset: "+dataset);
            int size=number(opts,"--size",1024,0,MAX_SIZE);
            int seed=number(opts,"--seed",2017,Integer.MIN_VALUE,Integer.MAX_VALUE);
            int runs=number(opts,"--runs",7,1,30);
            int warmup=number(opts,"--warmup",2,0,10);
            Frequency[] input=opts.containsKey("--input")?fromFile(Path.of(opts.get("--input"))):generated(dataset,size,seed);
            if(input.length>MAX_SIZE)throw new IllegalArgumentException("At most "+MAX_SIZE+" unique items are supported");
            if(opts.containsKey("--input")) shuffle(input,new Random(seed)); // fixed-seed order prevents accidental map-order bias
            List<String> methods=methods(opts.getOrDefault("--methods","all"));
            return benchmark(input,methods,runs,warmup,out,err,
                opts.containsKey("--input")?"file:"+opts.get("--input"):dataset,seed);
        } catch(IOException|IllegalArgumentException e) {
            err.println("ERROR: "+e.getMessage());return 2;
        }
    }
    private static int number(Map<String,String> opts,String key,int fallback,int min,int max) {
        int n;
        try { n=Integer.parseInt(opts.getOrDefault(key,Integer.toString(fallback))); }
        catch(NumberFormatException e){throw new IllegalArgumentException("Expected integer for "+key,e);}
        if(n<min||n>max)throw new IllegalArgumentException(key+" must be in ["+min+","+max+"]");
        return n;
    }
    private static List<String> methods(String spec) {
        List<String> out=new ArrayList<>();
        if(!spec.equals("all")){ out.add("jdk-native"); out.add("jdk"); }
        for(String value:(spec.equals("all")?ResearchSorts.METHODS:Arrays.asList(spec.split(",")))) {
            String method=value.trim();
            if(!ResearchSorts.METHODS.contains(method))
                throw new IllegalArgumentException("Unknown method: "+method);
            if(!out.contains(method))out.add(method);
        }
        return out;
    }
    private static Frequency[] fromFile(Path input) throws IOException {
        Map<String,Integer> counts=new HashMap<>();
        for(String token:Tokens.read(input)) counts.merge(token,1,Integer::sum);
        return counts.entrySet().stream().map(e->new Frequency(e.getKey(),e.getValue()))
            .sorted(Comparator.comparing(Frequency::word)).toArray(Frequency[]::new);
    }
    public static Frequency[] generated(String dataset,int n,long seed) {
        if(!DATASETS.contains(dataset)||n<0||n>MAX_SIZE)
            throw new IllegalArgumentException("Invalid dataset or size");
        Random rng=new Random(seed);
        Frequency[] values=new Frequency[n];
        for(int i=0;i<n;i++)
            values[i]=new Frequency(String.format(Locale.ROOT,"word%06d",i),
                dataset.equals("equal")?1:dataset.equals("few-counts")?1+rng.nextInt(4):1+rng.nextInt(1000));
        Arrays.sort(values,Sorts.ORDER);
        switch(dataset) {
            case "reverse" -> reverse(values,0,n);
            case "random","few-counts","equal" -> shuffle(values,rng);
            case "nearly" -> {
                for(int i=0;i<n/100;i++) {
                    if(n<2)break;
                    int x=rng.nextInt(n-1), y=Math.min(n-1,x+1+rng.nextInt(Math.min(8,n-x-1)));
                    Frequency t=values[x];values[x]=values[y];values[y]=t;
                }
            }
            case "runs" -> {
                shuffle(values,rng);
                for(int i=0;i<n;i+=64)Arrays.sort(values,i,Math.min(n,i+64),Sorts.ORDER);
            }
            case "sorted" -> {}
            default -> throw new IllegalArgumentException(dataset);
        }
        return values;
    }
    private static void reverse(Frequency[] a,int lo,int hi) {
        for(int i=lo,j=hi-1;i<j;i++,j--) { Frequency t=a[i];a[i]=a[j];a[j]=t; }
    }
    private static void shuffle(Frequency[] a,Random rng) {
        for(int i=a.length-1;i>0;i--) {
            int j=rng.nextInt(i+1);Frequency t=a[i];a[i]=a[j];a[j]=t;
        }
    }
    public static int benchmark(Frequency[] input,List<String> methods,int runs,int warmup,
                                PrintStream out,PrintStream err,String source,int seed) {
        Frequency[] oracle=input.clone();Arrays.sort(oracle,Sorts.ORDER);
        String inputHash=ResearchSorts.digest(input),expectedHash=ResearchSorts.digest(oracle);
        out.printf("dataset=%s seed=%d items=%d input_sha256=%s expected_sha256=%s%n",
            source,seed,input.length,inputHash,expectedHash);
        out.printf("runs=%d warmup=%d; includes copy, algorithm and optional crypto digest; excludes verification/printing%n",
            runs,warmup);
        out.println("method,implementation,status,median_ms,ratio_to_jdk_native,comparisons,writes");
        List<String> selected=new ArrayList<>(new LinkedHashSet<>(methods));
        if(!selected.contains("jdk-native"))selected.add(0,"jdk-native");
        if(!selected.contains("jdk"))selected.add(Math.min(1,selected.size()),"jdk");
        List<String> runnable=new ArrayList<>();
        for(String method:selected) {
            if(method.equals("insertion")&&input.length>4096) {
                out.println("insertion,insertion,SKIPPED_QUADRATIC,,,,");
            } else runnable.add(method);
        }
        Map<String,long[]> timings=new HashMap<>();
        Map<String,ResearchSorts.Result> example=new HashMap<>();
        for(String method:runnable) {
            ResearchSorts.Result test=ResearchSorts.sort(input,method);
            if(!correct(test,input,oracle)) { err.println("FAIL correctness: "+method);return 1; }
            if(method.equals("crypto") &&
                (!inputHash.equals(test.inputSha256())||!expectedHash.equals(test.outputSha256()))) {
                err.println("FAIL audit fingerprint: "+method);return 1;
            }
            example.put(method,test);
            timings.put(method,new long[runs]);
            for(int w=0;w<warmup;w++)ResearchSorts.sort(input,method);
        }
        Random schedule=new Random(seed);
        for(int iteration=0;iteration<runs;iteration++) {
            List<String> scheduleOrder=new ArrayList<>(runnable);Collections.shuffle(scheduleOrder,schedule);
            for(String method:scheduleOrder) {
                long start=System.nanoTime();
                ResearchSorts.Result result=ResearchSorts.sort(input,method);
                long nanos=System.nanoTime()-start;
                if(!correct(result,input,oracle)) { err.println("FAIL during measured run: "+method);return 1; }
                timings.get(method)[iteration]=nanos;
                if(method.equals("crypto") &&
                    (!inputHash.equals(result.inputSha256())||!expectedHash.equals(result.outputSha256()))) {
                    err.println("FAIL audit during measured run");return 1;
                }
            }
        }
        double jdk=median(timings.get("jdk-native"));
        for(String method:runnable) {
            ResearchSorts.Result result=example.get(method);
            double elapsed=median(timings.get(method));
            out.printf(Locale.ROOT,"%s,%s,PASS,%.6f,%.3f,%s,%s%n",
                method,result.implementation(),elapsed/1e6,jdk==0?0:elapsed/jdk,
                result.comparisons()<0?"N/A":Long.toString(result.comparisons()),result.writes()<0?"N/A":Long.toString(result.writes()));
        }
        out.println("Interpretation: these are prototypes; no speedup, novelty, WCT-PDE derivation, or cryptographic security is implied.");
        return 0;
    }
    private static boolean correct(ResearchSorts.Result result,Frequency[] original,Frequency[] oracle) {
        // Exact array equality to independent total-order oracle proves both ordering and multiset preservation.
        return result.values().length==original.length && Arrays.equals(result.values(),oracle);
    }
    private static double median(long[] times) {
        long[] copy=times.clone();Arrays.sort(copy);int n=copy.length;
        return n%2==1?copy[n/2]:copy[n/2-1]/2.0+copy[n/2]/2.0;
    }
    private static void usage(PrintStream out) {
        out.println("WordCount Lab experimental competition (Java 17)");
        out.println("  --dataset random|sorted|reverse|nearly|few-counts|equal|runs [--size N] [--seed N]");
        out.println("  OR --input UTF8_FILE");
        out.println("  [--methods all|jdk-native,jdk,merge,insertion,freqbucket,wct,rca,prime,crypto,hybrid] [--runs 7] [--warmup 2]");
        out.println("Execution: java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ExperimentLab ...");
        out.println("Correctness checked against independent JDK sort. Insertion skipped if N > 4096. Max N=100000.");
    }
}
