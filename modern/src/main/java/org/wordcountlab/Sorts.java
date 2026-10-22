package org.wordcountlab;
import java.util.*;
/** Same comparator in all three hand-written algorithms: frequency DESC, word ASC. */
public final class Sorts {
    public static final Comparator<Frequency> ORDER =
        Comparator.comparingInt(Frequency::count).reversed().thenComparing(Frequency::word);
    private Sorts() {}
    public static List<Frequency> sorted(List<Frequency> input, String kind) {
        Frequency[] a = input.toArray(new Frequency[0]);
        switch (kind) {
            case "insertion" -> insertion(a);
            case "quick" -> quick(a, 0, a.length - 1);
            case "merge" -> merge(a, new Frequency[a.length], 0, a.length);
            default -> throw new IllegalArgumentException("Unknown sort: " + kind);
        }
        return new ArrayList<>(Arrays.asList(a));
    }
    private static void insertion(Frequency[] a) {
        for (int i=1;i<a.length;i++) {
            Frequency value=a[i]; int j=i-1;
            while (j>=0 && ORDER.compare(a[j],value)>0) { a[j+1]=a[j]; j--; }
            a[j+1]=value;
        }
    }
    private static void quick(Frequency[] a, int lo, int hi) {
        while(lo<hi) {
            int i=lo,j=hi; Frequency pivot=a[lo+(hi-lo)/2];
            while(i<=j) {
                while(ORDER.compare(a[i],pivot)<0) i++;
                while(ORDER.compare(a[j],pivot)>0) j--;
                if(i<=j) { Frequency t=a[i];a[i]=a[j];a[j]=t;i++;j--; }
            }
            if(j-lo<hi-i) { if(lo<j) quick(a,lo,j); lo=i; }
            else { if(i<hi) quick(a,i,hi); hi=j; }
        }
    }
    private static void merge(Frequency[] a, Frequency[] tmp, int lo, int hi) {
        if(hi-lo<2) return;
        int mid=lo+(hi-lo)/2; merge(a,tmp,lo,mid);merge(a,tmp,mid,hi);
        int i=lo,j=mid,k=lo;
        while(i<mid&&j<hi) tmp[k++]=ORDER.compare(a[i],a[j])<=0?a[i++]:a[j++];
        while(i<mid) tmp[k++]=a[i++];
        while(j<hi) tmp[k++]=a[j++];
        System.arraycopy(tmp,lo,a,lo,hi-lo);
    }
}
