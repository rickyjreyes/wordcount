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

class ResearchSortsTest {
    @TempDir Path tmp;
    private static final List<String> ALL=ResearchSorts.METHODS;

    static void oracle(Frequency[] input) {
        Frequency[] expected=input.clone();Arrays.sort(expected,Sorts.ORDER);
        String before=ResearchSorts.digest(input);
        for(String method:ALL) {
            ResearchSorts.Result actual=ResearchSorts.sort(input,method);
            assertArrayEquals(expected,actual.values(),method);
            assertTrue(actual.comparisons()>=0 || method.equals("jdk-native"));
            assertEquals(before,ResearchSorts.digest(input),"input mutated by "+method);
            if(method.equals("crypto")) {
                assertEquals(before,actual.inputSha256());
                assertEquals(ResearchSorts.digest(expected),actual.outputSha256());
            } else {
                assertNull(actual.inputSha256());
                assertNull(actual.outputSha256());
            }
        }
    }
    @Test void exhaustiveSmallPermutations() {
        // 0..6 elements, all 873 permutations overall, all candidates.
        for(int n=0;n<=6;n++) {
            List<Frequency> items=new ArrayList<>();
            for(int i=0;i<n;i++)items.add(new Frequency("w"+i,1+i%3));
            permutations(items,0);
        }
    }
    private static void permutations(List<Frequency> items,int at) {
        if(at==items.size()) { oracle(items.toArray(Frequency[]::new));return; }
        for(int i=at;i<items.size();i++) {
            Collections.swap(items,at,i);permutations(items,at+1);Collections.swap(items,at,i);
        }
    }
    @Test void adversarialDatasetsAndSizes() {
        for(String dataset:List.of("random","sorted","reverse","nearly","few-counts","equal","runs"))
            for(int size:new int[]{0,1,2,3,7,17,32,33,63,64,65,127,256,1024})
                oracle(ExperimentLab.generated(dataset,size,2017));
    }
    @Test void largerPrimeAndHybridRemainCorrect() {
        for(String dataset:List.of("sorted","reverse","random","few-counts")) {
            Frequency[] data=ExperimentLab.generated(dataset,8192,42);
            Frequency[] reference=data.clone();Arrays.sort(reference,Sorts.ORDER);
            for(String method:List.of("prime","hybrid","rca","wct"))
                assertArrayEquals(reference,ResearchSorts.sort(data,method).values(),method+" "+dataset);
        }
    }
    @Test void denseFrequencySortingAndSparseFallbackBothMatchOracle() {
        Frequency[] dense=new Frequency[5000];
        Random rng=new Random(123);
        for(int i=0;i<dense.length;i++)
            dense[i]=new Frequency("w"+String.format("%05d",i),1+rng.nextInt(32));
        // Including lexical ties with identical counts, worst-case repeated frequencies.
        oracle(dense);
        assertEquals("freqbucket->histogram+lexical-tie-sort",
            ResearchSorts.sort(dense,"freqbucket").implementation());
        Frequency[] sparse={
            new Frequency("z",Integer.MAX_VALUE),
            new Frequency("a",1),new Frequency("b",1),new Frequency("c",987654321)};
        oracle(sparse);
        assertEquals("freqbucket->merge(sparse-frequency-range)",
            ResearchSorts.sort(sparse,"freqbucket").implementation());
    }
    @Test void wctHasMonotoneFastPathsWithoutChangingCorrectness() {
        Frequency[] sorted=ExperimentLab.generated("sorted",4096,7);
        Frequency[] reversed=ExperimentLab.generated("reverse",4096,7);
        var a=ResearchSorts.sort(sorted,"wct");
        var b=ResearchSorts.sort(reversed,"wct");
        assertArrayEquals(sorted,a.values());
        assertArrayEquals(sorted,b.values());
        assertEquals(4095,a.comparisons());
        assertEquals(0,a.writes());
        assertEquals(4095,b.comparisons());
    }
    @Test void hybridDisclosesAndVerifiesItsDispatch() {
        var dense=ResearchSorts.sort(ExperimentLab.generated("random",8192,2017),"hybrid");
        assertEquals("hybrid->freqbucket->histogram+lexical-tie-sort",dense.implementation());
        var sparse=ResearchSorts.sort(ExperimentLab.generated("few-counts",2048,2017),"hybrid");
        assertEquals("hybrid->prime-introsort",sparse.implementation());
        var ordered=ResearchSorts.sort(ExperimentLab.generated("sorted",2048,2017),"hybrid");
        assertEquals("hybrid->rca-natural-runs",ordered.implementation());
        var reverse=ResearchSorts.sort(ExperimentLab.generated("reverse",2048,2017),"hybrid");
        assertEquals("hybrid->rca-natural-runs",reverse.implementation());
    }
    @Test void directComparatorAgreesWithReferenceOnExtremeCounts() {
        Frequency[] cases={
            new Frequency("z",Integer.MAX_VALUE),new Frequency("a",1),
            new Frequency("x",Integer.MAX_VALUE),new Frequency("À",17),
            new Frequency("a",17),new Frequency("b",17)};
        oracle(cases);
    }
    @Test void transcriptHashIsDomainSeparatedAndDetectsChanges() {
        Frequency[] original={new Frequency("a",2),new Frequency("b",1)};
        String hash=ResearchSorts.digest(original);
        assertEquals(hash,ResearchSorts.digest(original.clone()));
        assertNotEquals(hash,ResearchSorts.digest(new Frequency[]{original[1],original[0]}));
        assertNotEquals(hash,ResearchSorts.digest(new Frequency[]{new Frequency("a",3),original[1]}));
        assertNotEquals(hash,ResearchSorts.digest(new Frequency[]{new Frequency("aa",2),original[1]}));
        assertNotEquals(hash,ResearchSorts.digest(new Frequency[]{original[0]}));
    }
    private record Capture(int code,String out,String err) {}
    private static Capture run(String... args) {
        ByteArrayOutputStream o=new ByteArrayOutputStream(),e=new ByteArrayOutputStream();
        int code=ExperimentLab.run(args,
            new PrintStream(o,true,StandardCharsets.UTF_8),new PrintStream(e,true,StandardCharsets.UTF_8));
        return new Capture(code,o.toString(StandardCharsets.UTF_8),e.toString(StandardCharsets.UTF_8));
    }
    @Test void benchmarkChecksAllMethodsAndReportsAttribution() {
        Capture actual=run("--dataset","runs","--size","256","--seed","2017","--runs","2","--warmup","0");
        assertEquals(0,actual.code,actual.err);
        for(String method:ALL)assertTrue(actual.out.contains(method+","),method);
        assertTrue(actual.out.contains("hybrid->"));
        assertTrue(actual.out.contains("sha256+merge"));
        assertTrue(actual.out.contains("expected_sha256="));
        assertTrue(actual.out.contains("freqbucket->histogram"));
        assertTrue(actual.out.contains("jdk-native,"));
        assertTrue(actual.out.contains("ratio_to_jdk_native"));
    }
    @Test void csvLikeOutputSkipsQuadraticControlForLargeInputs() {
        Capture actual=run("--dataset","equal","--size","4097","--runs","1","--warmup","0",
            "--methods","insertion,prime,rca");
        assertEquals(0,actual.code,actual.err);
        assertTrue(actual.out.contains("SKIPPED_QUADRATIC"));
        assertTrue(actual.out.contains("jdk,"));
        assertTrue(actual.out.contains("prime,"));
    }
    @Test void realFileAndInvalidOptions() throws Exception {
        Path path=Files.writeString(tmp.resolve("data.txt"),"Apple apple banana BANANA café",StandardCharsets.UTF_8);
        Capture actual=run("--input",path.toString(),"--runs","1","--warmup","0");
        assertEquals(0,actual.code,actual.err);
        assertTrue(actual.out.contains("items=3"));
        assertEquals(2,run("--input",path.toString(),"--dataset","random").code);
        assertEquals(2,run("--dataset","unknown").code);
        assertEquals(2,run("--runs","0").code);
        assertEquals(2,run("--methods","fictional").code);
        assertEquals(2,run("--input",tmp.resolve("missing.txt").toString()).code);
    }
}
