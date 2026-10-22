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

class TopKLabTest {
    @TempDir Path temp;

    static void oracle(Frequency[] input,int k) {
        Frequency[] full=input.clone();
        Arrays.sort(full,Sorts.ORDER);
        Frequency[] expected=Arrays.copyOf(full,Math.min(full.length,k));
        Frequency[] original=input.clone();
        for(String method:TopKLab.METHODS) {
            TopKLab.Result result=TopKLab.select(input,k,method);
            assertArrayEquals(expected,result.selected(),method+" k="+k+" n="+input.length);
            assertTrue(result.comparisons()>=0);
            assertArrayEquals(original,input,"Top-K must not mutate input");
        }
    }
    @Test void everySizeAndBoundaryK() {
        for(String dataset:List.of("random","sorted","reverse","equal","few-counts","zipf","sparse","unicode","prefix")) {
            for(int n:new int[]{0,1,2,3,7,32,127,1024}) {
                Frequency[] input=ExperimentLab.generated(dataset,n,42);
                for(int k:new int[]{0,1,2,3,10,31,100,1024,100_000})oracle(input,k);
            }
        }
    }
    @Test void exactTieAndUnicodeOrdering() {
        Frequency[] a={
            new Frequency("東京",7),new Frequency("alpha",7),
            new Frequency("é",7),new Frequency("a",7),
            new Frequency("beta",8),new Frequency("z",1),
            new Frequency("a",7),new Frequency("Ω",7)
        };
        for(int k=0;k<=a.length+2;k++)oracle(a,k);
    }
    @Test void invalidInputsFailClosed() {
        assertThrows(IllegalArgumentException.class,()->TopKLab.select(new Frequency[0],-1,"heap"));
        assertThrows(IllegalArgumentException.class,()->TopKLab.select(new Frequency[0],1,"unknown"));
        assertThrows(NullPointerException.class,()->TopKLab.select(null,1,"heap"));
    }
    private record Capture(int code,String out,String err) {}
    static Capture run(String... args) {
        ByteArrayOutputStream o=new ByteArrayOutputStream(),e=new ByteArrayOutputStream();
        int status=TopKLab.run(args,new PrintStream(o,true,StandardCharsets.UTF_8),
                                   new PrintStream(e,true,StandardCharsets.UTF_8));
        return new Capture(status,o.toString(StandardCharsets.UTF_8),e.toString(StandardCharsets.UTF_8));
    }
    @Test void cliSeparateFromFullSortAndFailures() throws Exception {
        Capture c=run("--dataset","zipf","--size","256","--top","10","--runs","2","--warmup","0");
        assertEquals(0,c.code,c.err);
        assertTrue(c.out.contains("task=topk k=10"));
        assertTrue(c.out.contains("heap,"));
        assertTrue(c.out.contains("full,"));
        Path file=Files.writeString(temp.resolve("corpus.txt"),
            "apple apple banana BANANA café 東京 東京",StandardCharsets.UTF_8);
        assertEquals(0,run("--input",file.toString(),"--top","2","--runs","1","--warmup","0").code);
        assertEquals(2,run("--top","-1").code);
        assertEquals(2,run("--runs","0").code);
        assertEquals(2,run("--size","100001").code);
        assertEquals(2,run("--dataset","nonexistent").code);
        assertEquals(2,run("--input",file.toString(),"--dataset","random").code);
        assertEquals(2,run("--input",temp.resolve("missing.txt").toString()).code);
    }
}
