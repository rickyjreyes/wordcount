package org.wordcountlab.experimental;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wordcountlab.Frequency;
import org.wordcountlab.Sorts;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ThoroughLabTest {
    @TempDir Path temp;
    private record Run(int code,String out,String err) {}
    private Run call(String...args){
        ByteArrayOutputStream o=new ByteArrayOutputStream(),e=new ByteArrayOutputStream();
        return new Run(ThoroughLab.run(args,
            new PrintStream(o,true,StandardCharsets.UTF_8),
            new PrintStream(e,true,StandardCharsets.UTF_8)),
            o.toString(StandardCharsets.UTF_8),e.toString(StandardCharsets.UTF_8));
    }
    @Test void campaignRecordsAllRawSamplesAndIndependentOracle() throws Exception {
        Path target=temp.resolve("experiment");
        Run r=call("--datasets","random,equal","--sizes","32,512","--seeds","2017,42",
            "--methods","merge,freqbucket,jdk-native,radix,quick,selection",
            "--runs","3","--warmup","1","--out",target.toString());
        assertEquals(0,r.code(),r.err());
        List<String> raw=Files.readAllLines(target.resolve("samples.csv"));
        List<String> aggregate=Files.readAllLines(target.resolve("summary.csv"));
        assertEquals(1+2*2*2*6*3,raw.size());
        assertEquals(1+2*2*2*6,aggregate.size());
        assertTrue(raw.get(0).contains("input_sha256"));
        assertTrue(aggregate.stream().anyMatch(x->x.contains("freqbucket->histogram+lexical-tie-sort")));
        assertTrue(aggregate.stream().noneMatch(x->x.contains(",FAIL,")));
        assertTrue(Files.readString(target.resolve("environment.txt")).contains("available.processors="));
        assertEquals(18,aggregate.get(0).split(",",-1).length);
        for(String line:raw.subList(1,raw.size()))assertEquals(12,line.split(",",-1).length,line);
    }
    @Test void quadraticControlsAreExplicitlySkippedAndDoNotInvalidateCampaign() throws Exception {
        Path target=temp.resolve("skip");
        Run r=call("--datasets","sparse","--sizes","4097","--seeds","2017",
            "--methods","merge,selection,bubble,cocktail,insertion",
            "--runs","1","--warmup","0","--out",target.toString());
        assertEquals(0,r.code(),r.err());
        String summary=Files.readString(target.resolve("summary.csv"));
        for(String method:List.of("selection","bubble","cocktail","insertion"))
            assertTrue(summary.contains(method+",,SKIPPED_QUADRATIC"),method);
        assertTrue(summary.contains("merge,merge,PASS"));
    }
    @Test void percentileAndBadArguments() {
        assertEquals(100,ThoroughLab.percentile(new long[]{100},.5));
        assertEquals(25,ThoroughLab.percentile(new long[]{0,100},.25));
        assertEquals(50,ThoroughLab.percentile(new long[]{100,0},.5));
        for(String[] args:List.of(
            new String[]{"--sizes","100001"},
            new String[]{"--runs","0"},
            new String[]{"--methods","unknown"},
            new String[]{"--datasets","fictional"},
            new String[]{"--seeds","wat"},
            new String[]{"--sizes","128,128"},
            new String[]{"--bad","value"}))
            assertEquals(2,call(args).code(),Arrays.toString(args));
    }
    @Test void classicControlsAgreeOnExhaustiveSmallPermutations() {
        var methods=List.of("bubble","cocktail","comb","selection","shell","heap","quick");
        for(int n=0;n<=5;n++) {
            List<Frequency> initial=new ArrayList<>();
            for(int i=0;i<n;i++)initial.add(new Frequency("w"+(i%3),1+(i%2)));
            permutations(initial,0,methods);
        }
    }
    private void permutations(List<Frequency> data,int next,List<String> methods) {
        if(next==data.size()){
            Frequency[] original=data.toArray(Frequency[]::new),expected=original.clone();
            Arrays.sort(expected,Sorts.ORDER);
            for(String method:methods)
                assertArrayEquals(expected,ResearchSorts.sort(original,method).values(),method);
            return;
        }
        for(int i=next;i<data.size();i++){
            Collections.swap(data,next,i);permutations(data,next+1,methods);Collections.swap(data,next,i);
        }
    }
}
