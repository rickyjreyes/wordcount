package org.wordcountlab.experimental;

import org.wordcountlab.Frequency;
import java.util.*;

/**
 * Established/simplified competitor families, independent of WCT/RCA/Prime claims.
 * Every path implements count DESC then Java String.compareTo ASC for ties.
 */
final class CompetitiveSorts {
    private CompetitiveSorts() {}

    /** Stable four-pass base-256 LSD radix on descending signed counts.
     * Alphabetic presort is REQUIRED for exact ties; both phases are timed.
     * Uninstrumented Arrays.sort writes => reports writes N/A, not a partial count.
     */
    static String radix(Frequency[] a,ResearchSorts.Meter m) {
        if(a.length<2)return "radix->trivial";
        Arrays.sort(a,m::compareWords);
        Frequency[] scratch=new Frequency[a.length];
        Frequency[] from=a,to=scratch;
        for(int shift=0;shift<32;shift+=8) {
            int[] histogram=new int[256];
            for(Frequency f:from)histogram[(radixKey(f.count())>>>shift)&255]++;
            int pos=0;
            for(int i=0;i<256;i++) { int count=histogram[i];histogram[i]=pos;pos+=count; }
            for(Frequency f:from)to[histogram[(radixKey(f.count())>>>shift)&255]++]=f;
            Frequency[] temp=from;from=to;to=temp;
        }
        // Four passes always end in original a.
        m.writes=-1;
        return "radix->lexical-presort+stable-LSD32";
    }
    private static int radixKey(int count) {
        // unsigned monotonic representation of signed counts descending
        return ~(count^Integer.MIN_VALUE);
    }

    /** Sparse hash grouping, primitive sort of distinct counts, lexical within ties.
     * No dense count-range allocation, including Integer.MIN/MAX edge cases.
     * Collections sorting/write operations are intentionally reported as N/A.
     */
    static String sparseBucket(Frequency[] a,ResearchSorts.Meter m) {
        if(a.length<2)return "sparsebucket->trivial";
        Map<Integer,ArrayList<Frequency>> groups=new HashMap<>();
        for(Frequency f:a)groups.computeIfAbsent(f.count(),k->new ArrayList<>()).add(f);
        int[] keys=new int[groups.size()];
        int j=0;for(int key:groups.keySet())keys[j++]=key;
        Arrays.sort(keys);
        int at=0;
        for(int i=keys.length-1;i>=0;i--) {
            ArrayList<Frequency> group=groups.get(keys[i]);
            if(group.size()>1)group.sort(m::compareWords);
            for(Frequency f:group)a[at++]=f;
        }
        m.writes=-1;
        return "sparsebucket->hash-group+sorted-counts+lexical-ties";
    }

    /**
     * PDQ-inspired Java competitor (NOT a literal upstream pdqsort port):
     * median-of-three pivot, three-way partition for ties, pattern-breaking
     * perturbations on highly imbalanced splits, insertion threshold and
     * heapsort depth guard. No hidden library sort fallback.
     */
    static String pdq(Frequency[] a,ResearchSorts.Meter m) {
        int depth=0;for(int n=a.length;n>1;n>>=1)depth++;
        partition(a,0,a.length,2*depth,m);
        return "pdq->pattern-defeating-3way-introsort";
    }
    private static Frequency median(Frequency a,Frequency b,Frequency c,ResearchSorts.Meter m) {
        if(m.compare(a,b)>0){Frequency t=a;a=b;b=t;}
        if(m.compare(b,c)>0){Frequency t=b;b=c;c=t;}
        if(m.compare(a,b)>0)b=a;
        return b;
    }
    private static void patternBreak(Frequency[] a,int lo,int hi,ResearchSorts.Meter m) {
        int n=hi-lo;if(n<16)return;
        m.swap(a,lo+n/4,lo+n/2);
        m.swap(a,lo+n/2,lo+(3*n)/4);
    }
    private static void partition(Frequency[] a,int lo,int hi,int depth,ResearchSorts.Meter m) {
        while(hi-lo>24) {
            if(depth--==0) {ResearchSorts.heap(a,lo,hi,m);return;}
            int len=hi-lo;
            Frequency pivot=median(a[lo],a[lo+(len>>>1)],a[hi-1],m);
            int lt=lo,i=lo,gt=hi-1;
            while(i<=gt) {
                int c=m.compare(a[i],pivot);
                if(c<0)m.swap(a,lt++,i++);
                else if(c>0)m.swap(a,i,gt--);
                else i++;
            }
            int right=gt+1;
            if(Math.min(lt-lo,hi-right)<len/8) {
                // Only permute WITHIN partitions; never disturb partition invariant.
                patternBreak(a,lo,lt,m);
                patternBreak(a,right,hi,m);
            }
            if(lt-lo<hi-right) {
                partition(a,lo,lt,depth,m);lo=right;
            } else {
                partition(a,right,hi,depth,m);hi=lt;
            }
        }
        ResearchSorts.insertion(a,lo,hi,m);
    }

    /** Java's established parallel object sort with identical comparator. */
    static String parallel(Frequency[] a,ResearchSorts.Meter m) {
        Arrays.parallelSort(a,ResearchSorts::compareKeys);
        m.comparisons=-1;m.writes=-1;
        return "parallel->Arrays.parallelSort";
    }
}
