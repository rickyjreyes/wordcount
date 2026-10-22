package org.wordcountlab.experimental;

import org.wordcountlab.Frequency;
import org.wordcountlab.Sorts;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Separate Top-K task: outputs ONLY the best min(k,n) frequency records. */
public final class TopKLab {
    private TopKLab() {}
    public static final List<String> METHODS=List.of("heap","full");
    public record Result(Frequency[] selected,long comparisons,String implementation) {}
    private static final class Meter {
        long compares;
        int compare(Frequency a,Frequency b){compares++;return Sorts.ORDER.compare(a,b);}
    }
    public static Result select(Frequency[] source,int k,String method) {
        Objects.requireNonNull(source);
        if(k<0)throw new IllegalArgumentException("top must be >= 0");
        if(!METHODS.contains(method))throw new IllegalArgumentException("Unknown Top-K method: "+method);
        int take=Math.min(k,source.length);
        Meter m=new Meter();
        if(method.equals("full")) {
            Frequency[] sorted=source.clone();
            Arrays.sort(sorted,m::compare);
            return new Result(Arrays.copyOf(sorted,take),m.compares,"full->JDK-sort+truncate");
        }
        if(take==0)return new Result(new Frequency[0],0,"heap->empty");
        // Comparator reverses our descending BEST order: heap.peek() is the WORST retained item.
        PriorityQueue<Frequency> heap=new PriorityQueue<>(Math.max(1,take),(a,b)->m.compare(b,a));
        for(Frequency f:source) {
            if(heap.size()<take)heap.offer(f);
            else if(m.compare(f,heap.peek())<0) {heap.remove();heap.offer(f);}
        }
        Frequency[] result=heap.toArray(Frequency[]::new);
        Arrays.sort(result,m::compare); // final order required, included in the timing
        return new Result(result,m.compares,"heap->bounded-worst-first+order-output");
    }

    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==0||(args.length==1&&args[0].equals("--help"))) {
            out.println("Top-K is a separate task from full sorting; k=0 returns no records.");
            out.println("java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.TopKLab --dataset random --size 8192 --top 10 --runs 7 --warmup 2 [--seed 2017]");
            out.println("Or --input UTF8_PATH instead of --dataset/--size. Methods: heap,full.");
            return 0;
        }
        try {
            Set<String> allowed=Set.of("--dataset","--size","--seed","--top","--runs","--warmup","--input");
            Map<String,String> options=new HashMap<>();
            if(args.length%2!=0)throw new IllegalArgumentException("Expected --option value pairs");
            for(int i=0;i<args.length;i+=2) {
                if(!allowed.contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)
                    throw new IllegalArgumentException("Unknown or duplicate option "+args[i]);
            }
            if(options.containsKey("--input")&&(options.containsKey("--dataset")||options.containsKey("--size")))
                throw new IllegalArgumentException("--input is exclusive with --dataset and --size");
            int size=option(options,"--size",8192,0,100_000);
            int seed=option(options,"--seed",2017,Integer.MIN_VALUE,Integer.MAX_VALUE);
            int k=option(options,"--top",10,0,100_000);
            int runs=option(options,"--runs",7,1,30);
            int warmup=option(options,"--warmup",2,0,10);
            Frequency[] input=options.containsKey("--input")
                ?ExperimentLab.fromFile(Path.of(options.get("--input")))
                :ExperimentLab.generated(options.getOrDefault("--dataset","random"),size,seed);
            if(input.length>100_000)throw new IllegalArgumentException("Too many distinct words");
            if(options.containsKey("--input")) {
                Random rng=new Random(seed);
                for(int i=input.length-1;i>0;i--) {
                    int j=rng.nextInt(i+1);Frequency t=input[i];input[i]=input[j];input[j]=t;
                }
            }
            Frequency[] oracle=input.clone();
            Arrays.sort(oracle,Sorts.ORDER);
            oracle=Arrays.copyOf(oracle,Math.min(k,oracle.length));
            out.printf("task=topk k=%d dataset=%s seed=%d items=%d runs=%d warmup=%d%n",
                k,options.getOrDefault("--input",options.getOrDefault("--dataset","random")),
                seed,input.length,runs,warmup);
            out.println("method,implementation,status,median_ms,ratio_to_full,comparisons");
            Map<String,long[]> samples=new LinkedHashMap<>();
            Map<String,Result> examples=new LinkedHashMap<>();
            for(String method:METHODS) {
                Result first=select(input,k,method);
                if(!Arrays.equals(first.selected(),oracle)) {
                    err.println("Top-K exact oracle failure: "+method);return 1;
                }
                examples.put(method,first);
                samples.put(method,new long[runs]);
                for(int j=0;j<warmup;j++)select(input,k,method);
            }
            Random schedule=new Random(seed);
            for(int i=0;i<runs;i++) {
                List<String> order=new ArrayList<>(METHODS);
                Collections.shuffle(order,schedule);
                for(String method:order) {
                    long start=System.nanoTime();
                    Result result=select(input,k,method);
                    long nanos=System.nanoTime()-start;
                    if(!Arrays.equals(result.selected(),oracle)) {
                        err.println("Top-K exact oracle failure during measurement: "+method);return 1;
                    }
                    samples.get(method)[i]=nanos;
                }
            }
            double baseline=median(samples.get("full"));
            for(String method:METHODS) {
                double ms=median(samples.get(method));
                Result first=examples.get(method);
                out.printf(Locale.ROOT,"%s,%s,PASS,%.6f,%.3f,%d%n",method,first.implementation(),
                    ms/1e6,baseline==0?0:ms/baseline,first.comparisons());
            }
            out.println("This is partial selection. Do not compare its time to full-sort ranking as equivalent work.");
            return 0;
        }catch(IOException|IllegalArgumentException e){err.println("ERROR: "+e.getMessage());return 2;}
    }
    private static int option(Map<String,String> o,String key,int fallback,int min,int max) {
        int value;
        try{value=Integer.parseInt(o.getOrDefault(key,Integer.toString(fallback)));}
        catch(NumberFormatException e){throw new IllegalArgumentException("Bad integer "+key,e);}
        if(value<min||value>max)throw new IllegalArgumentException(key+" outside ["+min+","+max+"]");
        return value;
    }
    private static double median(long[] data) {
        long[] copy=data.clone();Arrays.sort(copy);
        int mid=copy.length/2;
        return copy.length%2==0?copy[mid-1]/2.0+copy[mid]/2.0:copy[mid];
    }
}
