package org.wordcountlab.experimental;

import org.wordcountlab.Frequency;
import org.wordcountlab.Sorts;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Falsifiable sorting prototypes, NOT derivations or established performance results of WCT/RCA.
 * All candidates must match exactly the existing total-order comparator.
 */
public final class ResearchSorts {
    public static final List<String> METHODS =
        List.of("jdk-native", "jdk", "merge", "insertion", "freqbucket", "radix", "sparsebucket", "pdq", "parallel", "selection", "shell", "quick", "heap", "bubble", "comb", "cocktail", "wct", "rca", "prime", "crypto", "hybrid", "adaptive-v1");
    private ResearchSorts() {}
    static int compareKeys(Frequency a,Frequency b) {
        int countOrder=Integer.compare(b.count(),a.count());
        return countOrder!=0?countOrder:a.word().compareTo(b.word());
    }

    public record Result(Frequency[] values, long comparisons, long writes,
                         String implementation, String inputSha256, String outputSha256) {}
    static final class Meter {
        long comparisons, writes;
        int compare(Frequency a, Frequency b) {
            comparisons++;
            return compareKeys(a,b);
        }
        int compareWords(Frequency a, Frequency b) {
            comparisons++;
            return a.word().compareTo(b.word());
        }
        void swap(Frequency[] a, int i, int j) {
            if (i == j) return;
            Frequency t=a[i]; a[i]=a[j]; a[j]=t; writes+=2;
        }
    }
    public static Result sort(Frequency[] original, String method) {
        Objects.requireNonNull(original);
        if (!METHODS.contains(method)) throw new IllegalArgumentException("Unknown method: " + method);
        Frequency[] a=original.clone(); Meter meter=new Meter();
        String implementation=method;
        String in=null,out=null;
        switch (method) {
            case "jdk-native" -> { Arrays.sort(a,ResearchSorts::compareKeys);meter.comparisons=-1;meter.writes=-1; }
            case "jdk" -> { Arrays.sort(a,meter::compare); meter.writes=-1; }
            case "merge" -> merge(a,new Frequency[a.length],0,a.length,meter);
            case "insertion" -> insertion(a,0,a.length,meter);
            case "freqbucket" -> { implementation=frequencyBuckets(a,meter); }
            case "radix" -> { implementation=CompetitiveSorts.radix(a,meter); }
            case "sparsebucket" -> { implementation=CompetitiveSorts.sparseBucket(a,meter); }
            case "pdq" -> { implementation=CompetitiveSorts.pdq(a,meter); }
            case "parallel" -> { implementation=CompetitiveSorts.parallel(a,meter); }
            case "selection" -> { implementation=ClassicSorts.selection(a,meter); }
            case "shell" -> { implementation=ClassicSorts.shell(a,meter); }
            case "quick" -> { implementation=ClassicSorts.quick(a,meter); }
            case "heap" -> { implementation=ClassicSorts.heap(a,meter); }
            case "bubble" -> { implementation=ClassicSorts.bubble(a,meter); }
            case "comb" -> { implementation=ClassicSorts.comb(a,meter); }
            case "cocktail" -> { implementation=ClassicSorts.cocktail(a,meter); }
            case "wct" -> curvatureBlocks(a,meter);
            case "rca" -> naturalRuns(a,meter);
            case "prime" -> primeIntro(a,meter);
            case "crypto" -> {
                in=digest(a);
                merge(a,new Frequency[a.length],0,a.length,meter);
                out=digest(a);
                implementation="sha256+merge (audit wrapper, not a new sort)";
            }
            case "adaptive-v1" -> implementation=AdaptiveSelector.sort(a,meter);
            case "hybrid" -> {
                long inversions=0, curvature=0;
                int previous=0,min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
                for(Frequency f:a) { min=Math.min(min,f.count());max=Math.max(max,f.count()); }
                for(int i=1;i<a.length;i++) {
                    int sign=Integer.signum(meter.compare(a[i-1],a[i]));
                    if(sign>0) inversions++;
                    if(i>1 && sign!=previous) curvature++;
                    previous=sign;
                }
                long width=a.length==0?0:(long)max-min+1;
                if(inversions==Math.max(0,a.length-1) ||
                   (inversions<=Math.max(1,a.length/16) && curvature<=Math.max(1,a.length/8))) {
                    naturalRuns(a,meter); implementation="hybrid->rca-natural-runs";
                } else if(width>=32 && width<=Math.min(1_000_000L,2L*a.length)) {
                    implementation="hybrid->"+frequencyBuckets(a,meter);
                } else {
                    primeIntro(a,meter); implementation="hybrid->prime-introsort";
                }
            }
            default -> throw new IllegalArgumentException(method);
        }
        return new Result(a,meter.comparisons,meter.writes,implementation,in,out);
    }
    /**
     * Domain-specialized exact sort. Dense counts: linear histogram scattering by
     * descending integer count; alphabetically sort each equal-count segment.
     * The histogram and tie-sort are BOTH included in timed execution.
     * Sparse range is explicitly attributed to the conventional merge fallback.
     * Unlike comparison sorting, integers can be bucketed, but words still
     * require sorting for ties. This is a conventional specialization, not WCT.
     */
    static String frequencyBuckets(Frequency[] a, Meter m) {
        final int n=a.length;
        if(n<2)return "freqbucket->trivial";
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(Frequency f:a) { min=Math.min(min,f.count());max=Math.max(max,f.count()); }
        long width=(long)max-min+1;
        if(width>1_000_000L || width>Math.max(4096L,4L*n)) {
            merge(a,new Frequency[n],0,n,m);
            return "freqbucket->merge(sparse-frequency-range)";
        }
        int slots=(int)width;
        int[] offsets=new int[slots];
        for(Frequency f:a)offsets[max-f.count()]++;
        int start=0;
        for(int i=0;i<slots;i++) {
            int size=offsets[i];
            offsets[i]=start;
            start+=size;
        }
        // Assign frequency buckets without comparisons. Reconstruct boundaries
        // from final positions; sorted order is count DESC, word ASC within ties.
        Frequency[] grouped=new Frequency[n];
        for(Frequency f:a) { grouped[offsets[max-f.count()]++]=f;m.writes++; }
        Frequency[] scratch=new Frequency[n];
        int low=0;
        for(int i=0;i<slots;i++) {
            int high=offsets[i];
            if(high-low>1) {
                if(high-low<=24) insertion(grouped,low,high,m);
                else merge(grouped,scratch,low,high,m);
            }
            low=high;
        }
        System.arraycopy(grouped,0,a,0,n);m.writes+=n;
        return "freqbucket->histogram+lexical-tie-sort";
    }
    /** Own baseline mergesort; never silently falls back to a JDK sort. */
    static void merge(Frequency[] a, Frequency[] temp, int low, int high, Meter m) {
        if(high-low<2) return;
        int mid=low+(high-low)/2;
        merge(a,temp,low,mid,m); merge(a,temp,mid,high,m);
        mergeSegments(a,temp,low,mid,high,m);
    }
    private static void mergeSegments(Frequency[] a, Frequency[] temp, int low, int mid, int high, Meter m) {
        int i=low,j=mid,k=low;
        while(i<mid&&j<high) { temp[k++]=m.compare(a[i],a[j])<=0?a[i++]:a[j++];m.writes++; }
        while(i<mid) { temp[k++]=a[i++];m.writes++; }
        while(j<high) { temp[k++]=a[j++];m.writes++; }
        System.arraycopy(temp,low,a,low,high-low);m.writes+=high-low;
    }
    static void insertion(Frequency[] a,int low,int high,Meter m) {
        for(int i=low+1;i<high;i++) {
            Frequency value=a[i];int j=i-1;
            while(j>=low&&m.compare(a[j],value)>0) { a[j+1]=a[j];m.writes++;j--; }
            a[j+1]=value;m.writes++;
        }
    }
    /**
     * WCT-inspired discrete sign-change defect heuristic, not WCT PDE evolution.
     * Collect block boundaries BEFORE modifying the input; detect globally ordered
     * sequences at the same time. Sort blocks and merge consecutive blocks in
     * rounds with ONE reusable scratch array (no per-record priority queue).
     */
    private static void curvatureBlocks(Frequency[] a,Meter m) {
        final int minBlock=16,maxBlock=64,defectThreshold=4;
        int n=a.length;if(n<2)return;
        List<int[]> blocks=new ArrayList<>();
        int start=0,defects=0,previous=0;
        boolean nondecreasing=true,nonincreasing=true;
        for(int i=1;i<n;i++) {
            int sign=Integer.signum(m.compare(a[i-1],a[i]));
            if(sign>0)nondecreasing=false;
            if(sign<0)nonincreasing=false;
            if(i<=start) { previous=0;continue; } // crossing an existing block boundary
            if(i>start+1 && sign!=previous) defects++;
            previous=sign;
            int length=i-start+1;
            if(length>=maxBlock || (length>=minBlock&&defects>=defectThreshold)) {
                int end=i+1;
                blocks.add(new int[]{start,end});
                start=end;defects=0;previous=0;
            }
        }
        if(nondecreasing)return;
        if(nonincreasing) {
            for(int x=0,y=n-1;x<y;x++,y--)m.swap(a,x,y);
            return;
        }
        if(start<n)blocks.add(new int[]{start,n});
        Frequency[] scratch=new Frequency[n];
        for(int[] block:blocks) {
            if(block[1]-block[0]<=24)insertion(a,block[0],block[1],m);
            else merge(a,scratch,block[0],block[1],m);
        }
        while(blocks.size()>1) {
            List<int[]> next=new ArrayList<>((blocks.size()+1)/2);
            for(int j=0;j<blocks.size();j+=2) {
                int[] left=blocks.get(j);
                if(j+1==blocks.size()) { next.add(left);continue; }
                int[] right=blocks.get(j+1);
                mergeSegments(a,scratch,left[0],left[1],right[1],m);
                next.add(new int[]{left[0],right[1]});
            }
            blocks=next;
        }
    }
    /**
     * RCA-inspired bounded feedback: natural run discovery/reversal followed by pairwise
     * merge rounds. Every round decreases run count; worst case O(n log n).
     * This is a conventional natural mergesort guided by run disorder, not a novel theorem.
     */
    private static void naturalRuns(Frequency[] a,Meter m) {
        int n=a.length;if(n<2)return;
        List<int[]> runs=new ArrayList<>();int i=0;
        while(i<n) {
            int lo=i++;
            if(i<n) {
                boolean descending=m.compare(a[i-1],a[i])>0;
                i++;
                if(descending) {
                    while(i<n&&m.compare(a[i-1],a[i])>0)i++;
                    for(int x=lo,y=i-1;x<y;x++,y--)m.swap(a,x,y);
                } else {
                    while(i<n&&m.compare(a[i-1],a[i])<=0)i++;
                }
            }
            runs.add(new int[]{lo,i});
        }
        Frequency[] tmp=new Frequency[n];
        while(runs.size()>1) {
            List<int[]> next=new ArrayList<>((runs.size()+1)/2);
            for(int j=0;j<runs.size();j+=2) {
                int[] left=runs.get(j);
                if(j+1==runs.size()) { next.add(left);continue; }
                int[] right=runs.get(j+1);
                mergeSegments(a,tmp,left[0],left[1],right[1],m);
                next.add(new int[]{left[0],right[1]});
            }
            runs=next;
        }
    }
    /** Prime-stride pivot sampling with three-way partitioning and heapsort depth guard. */
    private static void primeIntro(Frequency[] a,Meter m) {
        int depth=0;
        for(int x=a.length;x>1;x>>=1)depth++;
        intro(a,0,a.length,2*depth,m);
    }
    private static void intro(Frequency[] a,int lo,int hi,int depth,Meter m) {
        while(hi-lo>24) {
            if(depth--==0) { heap(a,lo,hi,m);return; }
            int len=hi-lo;
            int stride=primeStride(len);
            int second=(int)((2L*stride)%len);
            Frequency pivot=median(a[lo],a[lo+stride],a[lo+second],m);
            int lt=lo,i=lo,gt=hi-1;
            while(i<=gt) {
                int c=m.compare(a[i],pivot);
                if(c<0) m.swap(a,lt++,i++);
                else if(c>0) m.swap(a,i,gt--);
                else i++;
            }
            if(lt-lo<hi-(gt+1)) { intro(a,lo,lt,depth,m);lo=gt+1; }
            else { intro(a,gt+1,hi,depth,m);hi=lt; }
        }
        insertion(a,lo,hi,m);
    }
    private static Frequency median(Frequency a,Frequency b,Frequency c,Meter m) {
        if(m.compare(a,b)>0) { Frequency t=a;a=b;b=t; }
        if(m.compare(b,c)>0) { Frequency t=b;b=c;c=t; }
        if(m.compare(a,b)>0) b=a;
        return b;
    }
    private static int gcd(int a,int b) { while(b!=0){int t=a%b;a=b;b=t;}return a; }
    private static boolean prime(int x) {
        if(x<2)return false;
        for(int d=2;d<=x/d;d++)if(x%d==0)return false;
        return true;
    }
    private static int primeStride(int length) {
        int target=Math.max(2,length/3);
        for(int p=target;p<length;p++)if(prime(p)&&gcd(p,length)==1)return p;
        for(int p=2;p<length;p++)if(prime(p)&&gcd(p,length)==1)return p;
        return 1; // unreachable for introsort's length > 24
    }
    static void heap(Frequency[] a,int lo,int hi,Meter m) {
        int n=hi-lo;
        for(int i=n/2-1;i>=0;i--)sift(a,lo,n,i,m);
        for(int end=n-1;end>0;end--) { m.swap(a,lo,lo+end);sift(a,lo,end,0,m); }
    }
    private static void sift(Frequency[] a,int lo,int length,int root,Meter m) {
        while(root<length/2) {
            int child=2*root+1;
            if(child+1<length&&m.compare(a[lo+child],a[lo+child+1])<0)child++;
            if(m.compare(a[lo+root],a[lo+child])>=0)return;
            m.swap(a,lo+root,lo+child);root=child;
        }
    }
    /** SHA-256 canonical transcript fingerprint; a hash alone is neither a signature nor a sorting proof. */
    public static String digest(Frequency[] entries) {
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            DataOutputStream data=new DataOutputStream(buffer);
            byte[] domain="wordcount-lab/sort-transcript/v1".getBytes(StandardCharsets.UTF_8);
            data.writeInt(domain.length);data.write(domain);data.writeInt(entries.length);
            for(Frequency f:entries) {
                byte[] word=f.word().getBytes(StandardCharsets.UTF_8);
                data.writeInt(word.length);data.write(word);data.writeInt(f.count());
            }
            data.flush();
            return HexFormat.of().formatHex(md.digest(buffer.toByteArray()));
        } catch (NoSuchAlgorithmException | java.io.IOException e) {
            throw new IllegalStateException("SHA-256 or in-memory serialization unavailable",e);
        }
    }
}
