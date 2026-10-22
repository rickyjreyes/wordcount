package org.wordcountlab;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
/** Defined modern Unicode tokenizer; not a silent change to the historical StreamTokenizer. */
public final class Tokens {
    private static final Pattern WORD=Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*");
    private Tokens() {}
    public static List<String> read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }
    public static List<String> parse(String text) {
        List<String> out=new ArrayList<>();
        Matcher m=WORD.matcher(Normalizer.normalize(text,Normalizer.Form.NFC));
        while(m.find()) out.add(m.group().toLowerCase(Locale.ROOT).replace('’','\''));
        return out;
    }
}
