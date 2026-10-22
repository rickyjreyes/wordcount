# Thorough WordCount sorting validation

The question is **whether the existing Frequency Bucket lead generalizes**. A single CI leaderboard cannot answer that. This suite deliberately tests data shapes where the current bucket algorithm is expected to lose, reports all results, and separates correctness from performance.

## External comparison reference

[Viktor Bohush's *SortingAlgorithmAnimations*](https://github.com/vbohush/SortingAlgorithmAnimations) (MIT, copyright Viktor Bohush, repository's LICENSE.txt) illustrates nine classical methods: Selection, Shell, Insertion, Merge, Quick, Heap, Bubble, Comb and Cocktail, with random/few unique/reversed/almost-sorted inputs. We used this as a **comparison and educational reference only**. The seven missing classic controls in WordCount Lab are independently written in `ClassicSorts.java`; original WordCount's Insertion and Merge cover the other two. No third-party source or Swing GUI is copied into the repository. Visualization/animation should run in a **different process**, never inside a timed sort.

Quick is a median-of-three three-way quicksort with a clearly labeled heapsort depth guard; it is not an unguarded historical quicksort or a verbatim copy of the reference. Heap uses the same independent heap implementation already in WordCount, invoked as a standalone control. Educational O(n²) Bubble, Cocktail, Selection and Insertion are visibly SKIPPED over 4,096 records in the matrix, never reported as a PASS or silently replaced.

## Correctness gate (must pass before any speed discussion)

All full sorts produce a byte-for-byte equivalent `Frequency[]` to the separately defined JDK `Sorts.ORDER` oracle: **count descending; Java `String.compareTo` ascending for ties**. That array-equality check also verifies multiset conservation. The immutable input fingerprint is checked again on every measured run. Crypto mode additionally verifies input/output transcript hashes; it is not a sorting proof or signature. Tests include small permutations (with duplicate keys), 11 dataset shapes, multiple sizes, Unicode, long lexical prefixes, dense/sparse/equal counts and separate Top-K exactness. Never infer general performance from a correctness pass.

## Multi-seed paired campaign (raw CSV)

```sh
mvn -B -f modern/pom.xml verify
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ThoroughLab \
  --datasets random,sorted,reverse,nearly,few-counts,equal,runs,zipf,sparse,unicode,prefix \
  --sizes 128,2048,8192,32768 --seeds 2017,42,73,101,4099 \
  --methods jdk-native,merge,freqbucket,radix,sparsebucket,pdq,parallel,quick,heap,shell,comb,wct,rca,prime,hybrid \
  --runs 7 --warmup 3 --out modern/target/thorough
```

Files: `samples.csv` (every timing in nanoseconds, implementation branch/fallback, hash and tie-density metadata), `summary.csv` (median/p10/p90/min/max and per-case ratio to Merge), `environment.txt` (JVM/OS, available processors, memory and campaign parameters).

Within every (dataset, n, seed) case, candidates get **identical immutable input**, a separately generated reference array, preflight and warmup checks, randomized per-iteration execution order, and validation after each measurement. The timer includes `ResearchSorts.sort(input, method)` in full: input clone, per-invocation allocations, candidate work, comparator instrumentation, and Result creation. It excludes input generation, oracle computation, post-measurement verification and reporting. This campaign does not isolate pure sorting operations and is NOT JMH. Uninstrumented JDK/parallel differ from instrumented methods in counter overhead. Treat differences below a millisecond with particular skepticism on shared CI hosts.

Review fallback strings and count range. When `freqbucket` reports `freqbucket->merge(sparse-frequency-range)`, it **did not win as a histogram algorithm**. Never summarize that result as a distinct bucket-sort success. At high tie density, the lexical secondary sort is nontrivial; there is no claim of universal O(n) composite-key sorting.

## Independent, forked JMH benchmark

This is a distinct Maven module under `modern/benchmarks/` (JMH 1.37; Apache Maven builds the executable fat jar). It leaves the production Jar/CLI unchanged.

```sh
mvn -B -f modern/pom.xml verify install
mvn -B -f modern/benchmarks/pom.xml package
java -jar modern/benchmarks/target/wordcount-jmh.jar 'FullSortBenchmark.fullSort' \
  -p dataset=random,zipf,sparse,equal \
  -p n=8192,32768 -p seed=2017,42,73 \
  -p algorithm=freqbucket,merge,jdk-native,pdq,sparsebucket \
  -wi 2 -i 3 -w 300ms -r 300ms -f 2 -t 1 -prof gc \
  -rf json -rff modern/target/thorough/jmh-results.json
```

JMH generates the workload at trial setup, validates its one reference result **outside timed measurement**, forks JVMs, uses Blackhole consumption, and can export GC/allocation data. Run the independent full-sort campaign in addition to JMH: it validates each individual sort output, whereas JMH performs its exact-output preflight once per trial. **The JMH target is intentionally the actual ResearchSorts.sort public API** (clone/counters/results included); per-method instrumented comparisons are an unresolved fairness limitation if making an algorithm-only claim. JMH smoke timings in CI are for executable validation, not a scientifically meaningful score. Measure isolated algorithm-only or equal-instrumentation implementations before publishing generic speed claims.

## GitHub Actions, raw evidence and verdict criteria

[Scientific Sorting Validation](../.github/workflows/validation.yml) runs PR/branch verification: original coursework, normal regression tests, paired smoke campaign, shaded JMH build and forked JMH smoke. It uploads all raw and environment files using `actions/upload-artifact`. From the repository's **Actions** tab, select **Scientific Sorting Validation → Run workflow → extended** for a larger forked study. A run is not asynchronous work performed by ChatGPT; GitHub Actions owns it.

For each shape/size report: all seeds, median and spread, per-seed Merge ratio, frequency-bucket fallback and tie density, native Java, radix/sparse-bucket, PDQ-inspired and parallel comparisons, JMH confidence/GC, and counterexamples. Report Top-K separately (different problem). A generalized advantage remains **OPEN** until repeated controlled host-independent experiments establish it; WCT/RCA/Prime theoretical novelty is a separate claim.
