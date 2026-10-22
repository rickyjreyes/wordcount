package org.wordcountlab;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Runnable companion to the unmodified 2017 WordCount CLI. */
public final class WordCountLab {
    private static final List<String> STRUCTURES = List.of("bst", "avl", "hash");
    private static final List<String> SORTS = List.of("insertion", "quick", "merge");
    private WordCountLab() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Testable entry point; returns 0 on success, 1 on correctness failure, 2 on input errors. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
            usage(out); return 0;
        }
        try {
            String command = args[0];
            if (!List.of("count","compare","benchmark","analyze").contains(command))
                throw new IllegalArgumentException("Unknown command: " + command);
            Map<String,String> opts = new HashMap<>();
            Set<String> allowed = Set.of("--input","--structure","--sort","--top","--output","--runs","--warmup");
            for (int i=1;i<args.length;i+=2) {
                if (!allowed.contains(args[i]) || i+1>=args.length || args[i+1].startsWith("--"))
                    throw new IllegalArgumentException("Expected --option value, got: " + args[i]);
                if (opts.putIfAbsent(args[i], args[i+1]) != null)
                    throw new IllegalArgumentException("Duplicate option: " + args[i]);
            }
            String input = required(opts,"--input");
            String structure = opts.getOrDefault("--structure","hash");
            String sort = opts.getOrDefault("--sort","merge");
            if (!STRUCTURES.contains(structure)) throw new IllegalArgumentException("Invalid structure: " + structure);
            if (!SORTS.contains(sort)) throw new IllegalArgumentException("Invalid sort: " + sort);
            int top = number(opts,"--top",25,1,1_000_000);
            int runs = number(opts,"--runs",10,1,1000);
            int warmup = number(opts,"--warmup",3,0,1000);
            String format = opts.getOrDefault("--output","text");
            if (!List.of("text","csv").contains(format))
                throw new IllegalArgumentException("Invalid output: " + format);
            long readStart=System.nanoTime();
            List<String> words=Tokens.read(Path.of(input));
            long readNanos=System.nanoTime()-readStart;
            Map<String,Integer> expected=reference(words);
            return switch (command) {
                case "count" -> count(words,expected,structure,sort,top,format,out,err);
                case "compare" -> compare(words,expected,out,err);
                case "benchmark" -> benchmark(words,expected,readNanos,runs,warmup,out,err);
                case "analyze" -> analyze(words,expected,top,out);
                default -> throw new IllegalArgumentException("Unknown command");
            };
        } catch (IOException | IllegalArgumentException e) {
            err.println("ERROR: " + e.getMessage());
            return 2;
        }
    }
    private static String required(Map<String,String> options,String key) {
        String v=options.get(key);
        if(v==null || v.isBlank()) throw new IllegalArgumentException("Missing required option: "+key);
        return v;
    }
    private static int number(Map<String,String> options,String key,int fallback,int min,int max) {
        int value;
        try { value=Integer.parseInt(options.getOrDefault(key,Integer.toString(fallback))); }
        catch(NumberFormatException e) { throw new IllegalArgumentException("Expected integer for "+key,e); }
        if(value<min||value>max) throw new IllegalArgumentException(key+" must be between "+min+" and "+max);
        return value;
    }
    static Map<String,Integer> reference(List<String> words) {
        Map<String,Integer> expected=new HashMap<>();
        for(String word:words) expected.merge(word,1,Integer::sum);
        return expected;
    }
    static List<Frequency> entries(Counter counter,List<String> words) {
        for(String word:words) counter.increment(word);
        return counter.entries();
    }
    static boolean verify(Counter counter,List<Frequency> entries,Map<String,Integer> expected) {
        if(counter.size()!=expected.size() || entries.size()!=expected.size()) return false;
        Map<String,Integer> actual=new HashMap<>();
        for(Frequency f:entries) if(actual.putIfAbsent(f.word(),f.count())!=null) return false;
        return actual.equals(expected);
    }
    private static int count(List<String> words,Map<String,Integer> expected,String structure,
                             String sort,int top,String format,PrintStream out,PrintStream err) {
        Counter counter=Counter.create(structure);
        List<Frequency> entries=entries(counter,words);
        if(!verify(counter,entries,expected)) { err.println("FAIL: counter differs from HashMap reference"); return 1; }
        List<Frequency> ordered=Sorts.sorted(entries,sort);
        if(format.equals("csv")) out.println("count,word");
        else out.printf(Locale.ROOT,"Tokens: %d; unique: %d; structure: %s; sort: %s%n",
                        words.size(),expected.size(),structure,sort);
        for(Frequency f:ordered.subList(0,Math.min(top,ordered.size()))) {
            if(format.equals("csv")) out.printf("%d,%s%n",f.count(),csv(f.word()));
            else out.printf("%d %s%n",f.count(),f.word());
        }
        return 0;
    }
    private static String csv(String value) {
        return "\"" + value.replace("\"","\"\"") + "\"";
    }
    private static int compare(List<String> words,Map<String,Integer> expected,PrintStream out,PrintStream err) {
        List<Frequency> oracle=new ArrayList<>();
        expected.forEach((word,n)->oracle.add(new Frequency(word,n)));
        oracle.sort(Sorts.ORDER);
        boolean pass=true;
        out.printf("Input: %d tokens, %d unique words%n",words.size(),expected.size());
        for(String name:STRUCTURES) {
            Counter counter=Counter.create(name);
            List<Frequency> entries=entries(counter,words);
            boolean ok=verify(counter,entries,expected);
            out.printf("counter %-9s %s%n",name,ok?"PASS":"FAIL");
            pass &= ok;
        }
        for(String name:SORTS) {
            boolean ok=Sorts.sorted(oracle,name).equals(oracle);
            out.printf("sort    %-9s %s%n",name,ok?"PASS":"FAIL");
            pass &= ok;
        }
        if(!pass) err.println("FAIL: one or more implementations disagree with the reference");
        return pass?0:1;
    }
    private static int benchmark(List<String> words,Map<String,Integer> expected,long readNanos,
                                 int runs,int warmup,PrintStream out,PrintStream err) {
        out.printf(Locale.ROOT,"Input: %d tokens; %d unique; tokenization/read: %.3f ms%n",
                   words.size(),expected.size(),readNanos/1e6);
        out.printf("Counting only; excludes tokenization, sorting, printing; %d warmups + %d runs%n",warmup,runs);
        out.println("structure,median_ms,tokens_per_second,verified");
        for(String name:STRUCTURES) {
            Counter check=Counter.create(name);
            if(!verify(check,entries(check,words),expected)) {
                err.println("FAIL: "+name+" differs from reference; benchmark aborted"); return 1;
            }
            for(int w=0;w<warmup;w++) entries(Counter.create(name),words);
            long[] samples=new long[runs];
            for(int i=0;i<runs;i++) {
                Counter current=Counter.create(name);
                long start=System.nanoTime();
                entries(current,words);
                samples[i]=System.nanoTime()-start;
            }
            Arrays.sort(samples);
            double median=(runs%2==1)?samples[runs/2]:(samples[runs/2-1]/2.0+samples[runs/2]/2.0);
            double rate=median>0?words.size()*1e9/median:0;
            out.printf(Locale.ROOT,"%s,%.3f,%.1f,PASS%n",name,median/1e6,rate);
        }
        return 0;
    }
    private static int analyze(List<String> words,Map<String,Integer> expected,int top,PrintStream out) {
        out.printf(Locale.ROOT,"Tokens: %d%nUnique words: %d%nLexical diversity: %.6f%n",
            words.size(),expected.size(),words.isEmpty()?0:(double)expected.size()/words.size());
        List<Frequency> frequencies=new ArrayList<>();
        expected.forEach((word,n)->frequencies.add(new Frequency(word,n)));
        for(Frequency f:Sorts.sorted(frequencies,"merge").subList(0,Math.min(top,frequencies.size())))
            out.printf("%d %s%n",f.count(),f.word());
        return 0;
    }
    private static void usage(PrintStream out) {
        out.println("WordCount Lab (Java 17) — companion to the unchanged 2017 source_code/");
        out.println("  count --input FILE [--structure bst|avl|hash] [--sort insertion|quick|merge] [--top N] [--output text|csv]");
        out.println("  compare --input FILE");
        out.println("  benchmark --input FILE [--runs N] [--warmup N]");
        out.println("  analyze --input FILE [--top N]");
        out.println("Defaults: hash, merge, top 25, runs 10, warmup 3. Input is UTF-8.");
    }
}
