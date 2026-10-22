# WordCount — Data Structures and Sorting Comparison

A Java coursework project by **Ricky Reyes and Jennifer Earley** for counting word frequencies in text files and comparing storage and sorting approaches. It includes sample literary texts and hand-written data structures/sorts.

## Command-line interface

The program accepts exactly three arguments: a counting data structure, a sorting method and an input filename.

```text
java WordCount [-b | -a | -h] [-is | -qs | -ms] <filename>
```

| Choice | Implementation |
| --- | --- |
| `-b` | Unbalanced binary search tree. |
| `-a` | AVL-tree implementation (see limitations). |
| `-h` | Separate-chaining hash table (see limitations). |
| `-is` | Insertion sort. |
| `-qs` | Quick sort. |
| `-ms` | Merge sort. |

The main program prints count/word pairs and elapsed sorting time in milliseconds. Sorting is by frequency, with ordering and tie behavior depending on the selected algorithm.

## Build and example

From the repository root with a JDK installed:

```sh
javac source_code/*.java
java -cp source_code WordCount -b -ms sample_text/hamlet.txt
java -cp source_code WordCount -b -qs sample_text/the-new-atlantis.txt
```

On Windows, compile the Java files from `source_code/` with your preferred shell or IDE. Input words are processed through `FileWordReader`, which lowercases words and tokenizes them using Java's `StreamTokenizer`.

## Layout

- `source_code/WordCount.java` — CLI, count selection, sorting and output.
- `source_code/DataCounter.java` — counting interface and data/count record.
- `source_code/BinarySearchTree.java` — basic BST counter.
- `source_code/AVLTree.java` — experimental AVL extension.
- `source_code/HashTable.java` — experimental custom hash table.
- `source_code/FileWordReader.java` — file tokenization.
- `sample_text/hamlet.txt`, `sample_text/the-new-atlantis.txt` — sample inputs.

## Important limitations

This is an educational snapshot, not a validated benchmark library. In particular, the checked-in `AVLTree.incCount` returns early on insertions and may bypass its intended rebalancing; the hash table's insertion/count traversal and `getCounts` can mishandle repeated keys and bucket contents. The sorting implementations also have differing tie behavior. Use the BST path to explore the interface, and independently verify counts and sort output before using any implementation for comparisons or timing claims. No automated test suite or explicit license is included.
