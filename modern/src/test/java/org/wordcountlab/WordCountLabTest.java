package org.wordcountlab;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WordCountLabTest {
    @TempDir Path temp;

    @Test void tokenizationIsUnicodeAwareAndDeterministic() {
        assertEquals(List.of("café","café","don't","don't","foo","bar","42"),
                     Tokens.parse("CAFÉ cafe\u0301 Don't don’t foo-bar 42"));
        assertEquals(List.of(),Tokens.parse("?! ..."));
    }
    @Test void countersMatchReferenceOnDuplicatesAndCollisions() {
        List<String> words=List.of("Aa","BB","Aa","AaAa","BBBB","BB","z","z","x");
        for(String name:List.of("bst","avl","hash")) {
            Counter c=Counter.create(name);
            assertTrue(WordCountLab.verify(c,WordCountLab.entries(c,words),WordCountLab.reference(words)),name);
            assertEquals(words.stream().distinct().count(),c.size());
        }
    }
    @Test void avlBalancesEveryInsertion() {
        Counter c=Counter.create("avl");
        for(int i=0;i<1000;i++) c.increment(String.format("%04d",i));
        assertEquals(1000,c.size());
        assertTrue(((AvlCounter)c).height()<=16);
        assertTrue(WordCountLab.verify(c,c.entries(),WordCountLab.reference(
            java.util.stream.IntStream.range(0,1000).mapToObj(i->String.format("%04d",i)).toList())));
    }
    @Test void hashRehashAndRepeatedKeysAreCorrect() {
        Counter c=Counter.create("hash");
        Map<String,Integer> expected=new HashMap<>();
        for(int i=0;i<400;i++) {
            String word="item"+i; c.increment(word); expected.put(word,1);
            if(i%2==0) { c.increment(word); expected.put(word,2); }
        }
        assertTrue(WordCountLab.verify(c,c.entries(),expected));
    }
    @Test void allSortsMatchJavaReferenceIncludingTiesAndEmpty() {
        List<Frequency> input=List.of(new Frequency("z",2),new Frequency("a",2),
             new Frequency("x",4),new Frequency("q",1),new Frequency("m",4));
        List<Frequency> expected=new ArrayList<>(input); expected.sort(Sorts.ORDER);
        for(String sort:List.of("insertion","quick","merge")) {
            assertEquals(expected,Sorts.sorted(input,sort),sort);
            assertEquals(List.of(),Sorts.sorted(List.of(),sort));
            assertEquals(List.of(input.get(0)),Sorts.sorted(List.of(input.get(0)),sort));
        }
    }
    @Test void allSortsMatchJavaOnRandomInputs() {
        Random random=new Random(2017);
        for(int size:new int[]{0,1,2,3,10,100,200}) {
            List<Frequency> list=new ArrayList<>();
            for(int i=0;i<size;i++) list.add(new Frequency(String.format("word%03d",i),1+random.nextInt(12)));
            Collections.shuffle(list,random);
            List<Frequency> expected=new ArrayList<>(list); expected.sort(Sorts.ORDER);
            for(String sort:List.of("insertion","quick","merge"))
                assertEquals(expected,Sorts.sorted(list,sort),sort+" at "+size);
        }
    }
    private Result run(String... args) {
        ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
        int code=WordCountLab.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),
             new PrintStream(err,true,StandardCharsets.UTF_8));
        return new Result(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private record Result(int code,String out,String err) {}
    @Test void cliCountsVerifiesComparesAndAnalyzes() throws Exception {
        Path file=Files.writeString(temp.resolve("sample.txt"),"Apple apple BANANA banana apple café",StandardCharsets.UTF_8);
        Result count=run("count","--input",file.toString(),"--structure","avl","--sort","quick","--top","2");
        assertEquals(0,count.code,count.err); assertTrue(count.out.contains("3 apple"));
        assertTrue(count.out.contains("2 banana")); assertFalse(count.out.contains("1 café"));
        Result csv=run("count","--input",file.toString(),"--output","csv");
        assertEquals(0,csv.code,csv.err); assertTrue(csv.out.startsWith("count,word\n")); assertTrue(csv.out.contains("3,\"apple\""));
        Result compare=run("compare","--input",file.toString());
        assertEquals(0,compare.code,compare.err); assertEquals(6,compare.out.split("PASS",-1).length-1);
        Result analyze=run("analyze","--input",file.toString());
        assertEquals(0,analyze.code,analyze.err); assertTrue(analyze.out.contains("Lexical diversity: 0.500000"));
        Result benchmark=run("benchmark","--input",file.toString(),"--runs","2","--warmup","0");
        assertEquals(0,benchmark.code,benchmark.err); assertTrue(benchmark.out.contains("hash,"));
    }
    @Test void emptyInputsAndErrors() throws Exception {
        Path file=Files.writeString(temp.resolve("empty.txt"),"",StandardCharsets.UTF_8);
        assertEquals(0,run("compare","--input",file.toString()).code);
        assertEquals(0,run("analyze","--input",file.toString()).code);
        assertEquals(2,run("count","--input",file.toString(),"--sort","bubble").code);
        assertEquals(2,run("count","--input",file.toString(),"--top","-1").code);
        assertEquals(2,run("count","--input",temp.resolve("missing.txt").toString()).code);
        assertEquals(2,run("count","--input",file.toString(),"--input",file.toString()).code);
    }
}
