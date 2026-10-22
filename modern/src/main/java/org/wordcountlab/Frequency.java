package org.wordcountlab;
/** An immutable (word, count) pair. */
public record Frequency(String word, int count) {
    public Frequency {
        if (word == null || count < 1) throw new IllegalArgumentException("Invalid frequency");
    }
}
