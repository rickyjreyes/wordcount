package org.wordcountlab.experimental;

import org.wordcountlab.Frequency;

/**
 * Independent conventional educational controls inspired by the nine-algorithm
 * comparison in Viktor Bohush's SortingAlgorithmAnimations (MIT). This file
 * neither incorporates its source nor animation GUI. Same exact WordCount key.
 */
final class ClassicSorts {
    private ClassicSorts() {}
    static String selection(Frequency[] a,ResearchSorts.Meter m) {
        for(int i=0;i<a.length;i++) {
            int best=i;
            for(int j=i+1;j<a.length;j++)if(m.compare(a[j],a[best])<0)best=j;
            m.swap(a,i,best);
        }
        return "classic->selection";
    }
    static String bubble(Frequency[] a,ResearchSorts.Meter m) {
        for(int end=a.length-1;end>0;end--) {
            boolean changed=false;
            for(int i=0;i<end;i++)if(m.compare(a[i],a[i+1])>0) {
                m.swap(a,i,i+1);changed=true;
            }
            if(!changed)break;
        }
        return "classic->bubble";
    }
    static String cocktail(Frequency[] a,ResearchSorts.Meter m) {
        int start=0,end=a.length-1;
        while(start<end) {
            boolean changed=false;
            for(int i=start;i<end;i++)if(m.compare(a[i],a[i+1])>0) {
                m.swap(a,i,i+1);changed=true;
            }
            if(!changed)break;
            end--;changed=false;
            for(int i=end;i>start;i--)if(m.compare(a[i-1],a[i])>0) {
                m.swap(a,i-1,i);changed=true;
            }
            if(!changed)break;
            start++;
        }
        return "classic->cocktail";
    }
    static String comb(Frequency[] a,ResearchSorts.Meter m) {
        int gap=a.length;boolean swapped=true;
        while(gap>1||swapped) {
            gap=Math.max(1,(gap*10)/13);
            swapped=false;
            for(int i=0;i+gap<a.length;i++)if(m.compare(a[i],a[i+gap])>0) {
                m.swap(a,i,i+gap);swapped=true;
            }
        }
        return "classic->comb";
    }
    static String shell(Frequency[] a,ResearchSorts.Meter m) {
        int gap=1;
        while(gap<a.length/3)gap=3*gap+1;
        while(gap>=1) {
            for(int i=gap;i<a.length;i++) {
                Frequency item=a[i];int j=i;
                while(j>=gap && m.compare(a[j-gap],item)>0) {
                    a[j]=a[j-gap];m.writes++;j-=gap;
                }
                a[j]=item;m.writes++;
            }
            gap=(gap-1)/3;
        }
        return "classic->shell-knuth";
    }
    static String heap(Frequency[] a,ResearchSorts.Meter m) {
        ResearchSorts.heap(a,0,a.length,m);
        return "classic->heap";
    }
    static String quick(Frequency[] a,ResearchSorts.Meter m) {
        int depth=0;
        for(int n=a.length;n>1;n>>=1)depth++;
        quick(a,0,a.length,2*depth,m);
        return "classic->median3-3way-quick-with-heap-guard";
    }
    private static Frequency median(Frequency a,Frequency b,Frequency c,ResearchSorts.Meter m) {
        if(m.compare(a,b)>0){Frequency t=a;a=b;b=t;}
        if(m.compare(b,c)>0){Frequency t=b;b=c;c=t;}
        if(m.compare(a,b)>0)b=a;
        return b;
    }
    private static void quick(Frequency[] a,int lo,int hi,int depth,ResearchSorts.Meter m) {
        while(hi-lo>24) {
            if(depth--==0){ResearchSorts.heap(a,lo,hi,m);return;}
            Frequency pivot=median(a[lo],a[lo+(hi-lo)/2],a[hi-1],m);
            int lt=lo,i=lo,gt=hi-1;
            while(i<=gt) {
                int c=m.compare(a[i],pivot);
                if(c<0)m.swap(a,i++,lt++);
                else if(c>0)m.swap(a,i,gt--);
                else i++;
            }
            if(lt-lo<hi-(gt+1)){quick(a,lo,lt,depth,m);lo=gt+1;}
            else{quick(a,gt+1,hi,depth,m);hi=lt;}
        }
        ResearchSorts.insertion(a,lo,hi,m);
    }
}
