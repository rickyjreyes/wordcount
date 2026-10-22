# Expanded competitive sorting controls

This stage tests WordCount's count-descending/word-ascending ordering against additional **established algorithm families**. It does not assert new WCT, RCA, or Prime Engine results. Run with Java 17 from repository root:

```sh
mvn -B -f modern/pom.xml verify
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ExperimentLab --dataset random --size 8192 --seed 2017 --runs 7 --warmup 2
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ExperimentLab --dataset sparse --size 8192 --seed 2017 --methods radix,sparsebucket,freqbucket,merge,pdq,parallel --runs 7 --warmup 2
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ExperimentLab --dataset unicode --size 8192 --seed 2017 --runs 7 --warmup 2
```

## New full-sort competitors

| CLI method | Algorithm & exactness | Complexity and limitations |
| --- | --- | --- |
| `radix` | Alphabetical presort by `String.compareTo` (instrumented lexical comparisons), then four stable bytewise LSD counting passes on signed 32-bit integer frequency transformed to descending order | Full presort is included; no claim of a standalone linear-time full composite-key sort. Four passes cost O(4n+1024) beyond lexical presort. Uninstrumented JDK presort writes reported N/A |
| `sparsebucket` | Hash-map grouping by exact integer count, sort distinct primitive count keys descending, lexical sort each equal-count list | O(n + d log d + Σ n_c log n_c) expected under standard hashing assumptions; unlike `freqbucket`, does not allocate by full numeric range; Collections internal writes reported N/A |
| `pdq` | A **PDQ-inspired** Java reference implementation: median-of-three, 3-way partition, imbalanced-split perturbations inside partitions, insertion base cases and heapsort depth bound | Not a verbatim upstream pdqsort port or an independent performance claim; worst-case O(n log n) with heapsort guard |
| `parallel` | Java `Arrays.parallelSort` using the same direct composite comparator as uninstrumented `jdk-native` | Parallelism may not engage on small input; timing includes thread/scheduling overhead. Internal operation counters N/A |

All four are included in the same output oracle and seeded exploratory full-sort table with the existing native Java, instrumented Java, Merge, Insertion, Frequency Bucket, WCT, RCA, Prime, crypto audit and hybrid comparators. `freqbucket` still explicitly labels its sparse-range fallback to Merge. No baseline should be renamed as a research breakthrough.

## Separate task: Top-K

A request for the highest-frequency K words does **not** require fully sorting the entire input. It belongs in a separate benchmark category with an exact top-K reference.

```sh
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.TopKLab --dataset random --size 8192 --top 10 --seed 2017 --runs 7 --warmup 2
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.TopKLab --input sample_text/hamlet.txt --top 100 --runs 7 --warmup 2
```

- `heap`: maintain a worst-at-root bounded max heap (under the output comparator), replace when a better record arrives, then exactly sort selected records; O(n log k + k log k).
- `full`: conventional full `Arrays.sort` using the same ordering, then truncate to K. O(n log n).
- Both include all algorithm-specific allocations, selection and final ordering in measured time. Input generation and exact independent oracle checks are excluded from timing, common to both methods.
- For all `k >= 0`, return exactly min(k,n) records. `k=0` produces an empty output. All ties are resolved by word ASC. This table must never be merged with the full-sort speed ranking as if it solved identical work.

## Added input distributions

`zipf` (heavy-tailed generated frequencies), `sparse` (widely spaced counts), `unicode` (Unicode and mixed scripts with unique suffixes), `prefix` (shared long lexical prefixes), in addition to random, sorted, reverse, nearly sorted, few-counts, equal counts and local runs. The UTF-8 Hamlet corpus provides a real-text check. Generated words are deliberately still synthetic; longer independent natural-language corpora and multiple hardware environments are needed for any portable speed claim.

## Benchmarks and scientific interpretation

The CI matrix is an exploratory regression screen with identical seeded records, seven measured iterations and two warmups for its primary cases. Results should be reported with source revision, data shape, JVM/CPU, sorting-only versus end-to-end timings, error bars where available, all preprocessing and fallback attribution. Direct the controlled JMH benchmark effort to issue #2. An improvement against Merge alone is insufficient when Radix, sparse buckets, native Java and parallel sorting could be stronger on different distributions. 
