package org.wordcountlab;
import java.util.*;
/** The original DataCounter concept, retained as the modern implementation contract. */
public interface Counter {
    void increment(String word);
    int size();
    List<Frequency> entries();
    static Counter create(String name) {
        return switch(name) {
            case "bst" -> new BstCounter();
            case "avl" -> new AvlCounter();
            case "hash" -> new HashCounter();
            default -> throw new IllegalArgumentException("Unknown structure: " + name);
        };
    }
}
final class BstCounter implements Counter {
    private static final class Node {
        String word; int count = 1; Node left, right;
        Node(String word) { this.word = word; }
    }
    private Node root; private int size;
    public void increment(String word) {
        Objects.requireNonNull(word);
        if (root == null) { root = new Node(word); size++; return; }
        Node n = root;
        while (true) {
            int c = word.compareTo(n.word);
            if (c == 0) { n.count++; return; }
            if (c < 0) {
                if (n.left == null) { n.left = new Node(word); size++; return; }
                n = n.left;
            } else {
                if (n.right == null) { n.right = new Node(word); size++; return; }
                n = n.right;
            }
        }
    }
    public int size() { return size; }
    public List<Frequency> entries() {
        List<Frequency> out = new ArrayList<>(size);
        Deque<Node> stack = new ArrayDeque<>(); Node n = root;
        while (n != null || !stack.isEmpty()) {
            while (n != null) { stack.push(n); n = n.left; }
            n = stack.pop(); out.add(new Frequency(n.word, n.count)); n = n.right;
        }
        return out;
    }
}
final class AvlCounter implements Counter {
    private static final class Node {
        String word; int count = 1, height = 1; Node left, right;
        Node(String word) { this.word = word; }
    }
    private Node root; private int size;
    private static int h(Node n) { return n == null ? 0 : n.height; }
    private static void update(Node n) { n.height = 1 + Math.max(h(n.left), h(n.right)); }
    private static int balance(Node n) { return h(n.left) - h(n.right); }
    private static Node right(Node y) {
        Node x = y.left, mid = x.right; x.right = y; y.left = mid; update(y); update(x); return x;
    }
    private static Node left(Node x) {
        Node y = x.right, mid = y.left; y.left = x; x.right = mid; update(x); update(y); return y;
    }
    public void increment(String word) { Objects.requireNonNull(word); root = insert(root, word); }
    private Node insert(Node n, String word) {
        if (n == null) { size++; return new Node(word); }
        int c = word.compareTo(n.word);
        if (c == 0) { n.count++; return n; }
        if (c < 0) n.left = insert(n.left, word); else n.right = insert(n.right, word);
        update(n); int b = balance(n);
        if (b > 1) { if (balance(n.left) < 0) n.left = left(n.left); return right(n); }
        if (b < -1) { if (balance(n.right) > 0) n.right = right(n.right); return left(n); }
        return n;
    }
    public int size() { return size; }
    int height() { return h(root); }
    public List<Frequency> entries() {
        List<Frequency> out = new ArrayList<>(size); visit(root, out); return out;
    }
    private static void visit(Node n, List<Frequency> out) {
        if (n == null) return; visit(n.left, out); out.add(new Frequency(n.word, n.count)); visit(n.right, out);
    }
}
final class HashCounter implements Counter {
    private static final class Node {
        String word; int count = 1; Node next;
        Node(String word, Node next) { this.word = word; this.next = next; }
    }
    private Node[] buckets = new Node[16]; private int size;
    private static int index(String word, int cap) { return Math.floorMod(word.hashCode(), cap); }
    public void increment(String word) {
        Objects.requireNonNull(word); int at = index(word, buckets.length);
        for (Node n = buckets[at]; n != null; n = n.next) {
            if (n.word.equals(word)) { n.count++; return; }
        }
        buckets[at] = new Node(word, buckets[at]); size++;
        if (size > buckets.length * 0.75) resize();
    }
    private void resize() {
        Node[] enlarged = new Node[buckets.length * 2];
        for (Node bucket : buckets) {
            Node n = bucket;
            while (n != null) {
                Node next = n.next; int at = index(n.word, enlarged.length);
                n.next = enlarged[at]; enlarged[at] = n; n = next;
            }
        }
        buckets = enlarged;
    }
    public int size() { return size; }
    public List<Frequency> entries() {
        List<Frequency> out = new ArrayList<>(size);
        for (Node bucket : buckets) for (Node n = bucket; n != null; n = n.next)
            out.add(new Frequency(n.word, n.count));
        return out;
    }
}
