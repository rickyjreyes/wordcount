# WordCount / WordCount Lab

**2017 Java coursework by Richard (Ricky) Reyes and Jennifer Earley**, preserved alongside a new, independently testable companion implementation. This repository is not a retroactive rewrite of the student project.

## Original program (preserved)

The original files in `source_code/` and `sample_text/` remain unchanged. They implement word-frequency counting with a BST, an experimental AVL tree, and a custom hash table, plus insertion, quick and merge sorting.

With a JDK installed, from the repository root:

```sh
javac source_code/*.java
java -cp source_code WordCount -b -ms sample_text/hamlet.txt
```

Original syntax: `java WordCount [-b | -a | -h] [-is | -qs | -ms] <filename>`

| Flag | Meaning |
| --- | --- |
| `-b` | Unbalanced BST |
| `-a` | Original experimental AVL |
| `-h` | Original experimental hash table |
| `-is`, `-qs`, `-ms` | Insertion, quick, merge sorting |

**Historical caveat:** This original implementation is coursework, not a verified benchmark. The AVL insertion paths can bypass balancing, the hash table can mishandle collisions/repeated keys and enumerate duplicate entries, and sort tie behavior varies. Keep the historical program for study; use the modern companion for verified comparisons. The 2017 baseline is commit `8dbb861fc355ccfc384ea5ea2d50530cada48bab`; the first documentation-only update was in October 2026.

## WordCount Lab (modern companion)

Requires **Java 17+** and **Maven**. The `modern/` implementation preserves the original teaching goals while adding correct, separate implementations of BST, AVL and resizing hash table, plus deterministic insertion/quick/merge sorting.

```sh
mvn -B -f modern/pom.xml verify
java -jar modern/target/wordcount-lab-1.0.0.jar count --input sample_text/hamlet.txt --structure avl --sort merge --top 20
java -jar modern/target/wordcount-lab-1.0.0.jar compare --input sample_text/hamlet.txt
java -jar modern/target/wordcount-lab-1.0.0.jar benchmark --input sample_text/hamlet.txt --warmup 3 --runs 10
java -jar modern/target/wordcount-lab-1.0.0.jar analyze --input sample_text/hamlet.txt --top 25
java -jar modern/target/wordcount-lab-1.0.0.jar count --input sample_text/hamlet.txt --output csv --top 25
```

### Command reference

| Command | Behavior |
| --- | --- |
| `count --input FILE [--structure bst|avl|hash] [--sort insertion|quick|merge] [--top N] [--output text|csv]` | Count and deterministically sort frequencies; validates against a Java HashMap before output |
| `compare --input FILE` | Validate all 3 counters and all 3 sorts against independent standard-library references |
| `benchmark --input FILE [--warmup N] [--runs N]` | Count-only median timing and throughput, after pre-tokenizing the same input |
| `analyze --input FILE [--top N]` | Total tokens, distinct tokens, type/token lexical diversity, most frequent words |

Defaults: hash counter, merge sort, top 25, 3 warmups, 10 measured runs. The modern tokenizer reads UTF-8, normalizes NFC, uses Unicode letters/numbers, preserves internal apostrophes, and lowercases with `Locale.ROOT`. It deliberately differs from the historical Java `StreamTokenizer`; do **not** compare raw count totals across these two modes without normalizing tokenization first. Empty input is supported.

Every modern counter must return exactly one entry per unique word with the same frequencies as `HashMap`. Every sorting algorithm uses frequency descending, then word ascending (ties). The modern implementation intentionally has no runtime dependency beyond the JDK; JUnit is only a test dependency.

### Benchmark boundaries

The benchmark excludes tokenization, sorting and console output from reported count times; read/tokenization time is shown separately. It includes structure construction and count retrieval, uses `System.nanoTime()`, warmups and a median. Runtime depends on hardware, JVM, GC and input order. This is an educational comparative runner, **not** a JMH-grade microbenchmark or a scientifically controlled performance claim. `compare` and the test suite should pass before interpreting benchmarks.

## Project layout

```text
source_code/             Original 2017 Java sources, unchanged
sample_text/             Original Hamlet and New Atlantis inputs, unchanged
modern/pom.xml           Java 17 Maven companion
modern/src/main/java/    Corrected counting, sorting, Unicode tokenizer, CLI
modern/src/test/java/    JUnit regression and equivalence tests
.github/workflows/       Build and test automation
docs/HISTORY.md          Original attribution and preservation policy
docs/ROADMAP.md          Follow-on feature plan
```

## Attribution and licensing

The original coursework is credited to Ricky Reyes and Jennifer Earley. Modern additions are documented separately. The repository has **no explicit software license**; public visibility alone does not grant reuse rights. Adding a license should be done with appropriate contributor approval. See [HISTORY](docs/HISTORY.md).

## Experimental algorithm competition

An optional [research sorting competition](docs/RESEARCH_SORTING.md) now compares separate WCT-inspired, RCA-inspired, Prime Engine-inspired, cryptographically audited and hybrid prototypes against JDK/merge/insertion controls. It preserves the exact original comparator and fails on incorrect results. These are experimental heuristics and wrappers, **not established research-performance or cryptographic-security claims**.

```sh
mvn -B -f modern/pom.xml verify
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ExperimentLab --dataset random --size 2048 --seed 2017 --runs 7 --warmup 2
```

The [first experimental screening report](docs/EXPERIMENTAL_RESULTS_2026-10-22.md) archives correctness and benchmark results, including candidate underperformance. Future competitive claims require controlled replication (see issue #2).

The [optimized performance screening](docs/EXPERIMENTAL_RESULTS_OPTIMIZED_2026-10-22.md) adds a domain-specific frequency-bucket control and records both positive and negative results against native Java and Merge, including the earlier baseline's limitations. This does not establish a new general sorting algorithm.

## Expanded competitive baselines and separate Top-K task

The [competitive-controls protocol](docs/COMPETITIVE_CONTROLS.md) adds **stable LSD radix**, **sparse frequency buckets**, **PDQ-inspired 3-way introsort** (not an upstream pdqsort port), and **native Java parallel sorting**. Added adversarial data: Zipf-like frequencies, sparse integer ranges, Unicode words and long shared lexical prefixes. All full sorts must match the original frequency-DESC/word-ASC reference.

Top-K is benchmarked **separately** against full sorting plus truncation, as it intentionally returns only the requested K items:

```sh
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.TopKLab --dataset random --size 8192 --top 10 --seed 2017 --runs 7 --warmup 2
```

Previously reported CI timings remain historical screening measurements, **not** a new verified ranking after these competitors are introduced.
