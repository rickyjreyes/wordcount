package org.wordcountlab.experimental;

import org.wordcountlab.Frequency;

/**
 * FROZEN heuristic v1 for prospective holdout evaluation.
 *
 * Rule is deliberately simple and data-independent: a full O(n) feature scan
 * records integer range and exact order, then dispatches without learned
 * coefficients or access to dataset names/seeds. The cost of the inspection,
 * cloning, and delegated sort is included in every timed sort() call.
 *
 * The prior hybrid is preserved verbatim as an independent comparator.
 */
final class AdaptiveSelector {
    private AdaptiveSelector() {}

    static String sort(Frequency[] a, ResearchSorts.Meter meter) {
        int n=a.length;
        if(n<2)return "adaptive-v1->trivial";
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        boolean ordered=true,strictDescending=true;
        for(int i=0;i<n;i++) {
            int count=a[i].count();
            if(count<min)min=count;
            if(count>max)max=count;
            if(i>0) {
                int c=meter.compare(a[i-1],a[i]);
                if(c>0)ordered=false;
                if(c<=0)strictDescending=false;
            }
        }
        // Monotone cases are fully sorted by the inspection itself. Reversal
        // requires strictness, or it would reverse the order within equal keys.
        if(ordered)return "adaptive-v1->already-ordered";
        if(strictDescending) {
            for(int i=0,j=n-1;i<j;i++,j--)meter.swap(a,i,j);
            return "adaptive-v1->strict-reverse";
        }
        long width=(long)max-min+1L;
        // Freeze before running the holdout; this is an engineering hypothesis,
        // not an empirical fit to the holdout and not a new sorting theorem.
        if(n>=512 && width>=16 && width<=Math.min(1_000_000L,2L*n))
            return "adaptive-v1->"+ResearchSorts.frequencyBuckets(a,meter);
        ResearchSorts.merge(a,new Frequency[n],0,n,meter);
        return "adaptive-v1->merge";
    }
}
