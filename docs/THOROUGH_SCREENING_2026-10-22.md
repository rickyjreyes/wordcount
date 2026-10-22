# Multi-seed falsification screening — 2026-10-22

**Evidence:** [Scientific Sorting Validation run 39857502447](https://github.com/rickyjreyes/wordcount/actions/runs/39857502447), source head `af1bbcadeb62a7e917bc83f58ddcfbc3d88be30b`, artifact `scientific-sort-validation-39857502447` (raw `samples.csv`, `summary.csv`, environment/JMH JSON/logs). GitHub Actions used Temurin 17.0.20.1 on a 4-vCPU virtualized AMD EPYC 7763 Ubuntu runner.

**Correctness:** 29 JUnit tests, zero failures/errors. 1,008 method/case summaries marked PASS; 3,024 individual measured output arrays independently matched the exact reference oracle, with immutable input SHA-256 rechecked. No general runtime improvement is inferred from correctness.

## Study scope

- 8 distributions: random (counts 1–1000), equal, Zipf-like, sparse wide-range, sorted, reversed, Unicode, and long common-prefix words.
- Sizes: 128, 2,048, 8,192 distinct word-frequency records. Seeds: 2017, 42, 73; total **72 dataset/size/seed cases**.
- 14 methods per case: JDK-native, Merge, Frequency Bucket, Radix, Sparse Bucket, PDQ-inspired, Parallel, Quick, Heap, Shell, Hybrid, WCT-inspired, RCA-inspired, Prime-inspired.
- 1 warmup + 3 timed iterations per method with randomized method order within every iteration. Array clone and whole `ResearchSorts.sort` method, including allocations, instrumentation and explicit fallbacks, are timed. Oracle, input generation, output checks, hashes and CSV output are outside timed region.
- Reported case times are 3-observation medians; cross-seed times below are medians of the three case medians. These are *screening estimates*, NOT controlled JMH confidence intervals.

## Main scientific result: Frequency Bucket is workload-dependent

- Frequency Bucket was fastest **in 23/72 cases** among the 14 candidates.
- It beat handwritten Merge **in 37/72 cases**.
- It used its genuine histogram path for 54 cases, beating Merge in 35/54 of those. In 18 sparse/Zipf cases it explicitly fell back to Merge, so these cannot be counted as separate bucket-sort successes.
- The previous single-runner/random-generator claim of universal superiority is **not supported**. The actual conventional integer-count histogram wins in several restricted input domains; specialized/adaptive alternatives outperform it in others.

### At n=8,192 — cross-seed median of case medians (milliseconds)

| Shape | Frequency Bucket | Merge | Fastest by cross-seed median | Frequency Bucket beats Merge |
| --- | ---: | ---: | --- | --- |
| Random dense counts | **0.857** | 1.787 | Frequency Bucket (0.857) | 3/3 seeds |
| Long common-prefix words | **0.537** | 1.402 | Frequency Bucket (0.537) | 3/3 seeds |
| Unicode words | **0.797** | 2.149 | Frequency Bucket (0.797) | 3/3 seeds |
| All counts equal | 2.478 | **2.346** | Merge (2.346) | 0/3 |
| Sparse/wide count range | 1.137 (Merge fallback) | 1.069 | PDQ-inspired (0.945) | 0/3 |
| Zipf-like counts | 1.630 (Merge fallback) | 1.630 | Sparse Bucket (1.252) | 0/3 |
| Already sorted | 0.736 | 1.222 | RCA natural runs (0.328) | 3/3 |
| Reversed | 0.690 | 1.131 | RCA natural runs (0.197) | 3/3 |

The "fastest" cross-seed column uses method median **independently** across each seed, not a pooled benchmark-run score. For reversed n=8192, RCA won two seeds and WCT won one; do not interpret a tiny median difference as a portable performance result.

### Size dependence

On n=128, Frequency Bucket beat Merge in 6/24 cases: fixed histogram/array allocation overhead often dominates. At n=2048 it beat Merge in 16/24, and at n=8192 it beat Merge in 15/24. These are paired, distribution-dependent results; the dataset weights are not a representative population estimate.

## Independent JMH smoke — *functional*, not conclusive

The dedicated shaded JMH module built and executed with one JVM fork, one 200 ms warmup iteration, two 200 ms measurements, GC profiler; random n=8192, seed 2017. The observed average scores were:

| Method | JMH smoke us/op | Allocation B/op (approx.) |
| --- | ---: | ---: |
| Frequency Bucket | **1,460.3** | 102,491 |
| Merge | 2,467.3 | 65,720 |
| PDQ-inspired | 4,873.2 | 33,007 |
| Native JDK | 5,064.2 | 60,992 |

The smoke agrees in *direction* with the restricted random-data lead, but **two measurements and one fork cannot support uncertainty intervals or scientific generalization**. Frequency Bucket also allocates more bytes per operation than Merge under this API. This matters for scalability and GC sensitivity.

## Interpretation and next falsification gate

PASS: exact correctness in tested cases; original coursework compilation; the fully automated raw/paired campaign; independent benchmark-module construction and JMH smoke.

NOT PROVED: fastest for arbitrary WordCount corpora, asymptotic novelty, speedup independent of instrumentation or hardware, WCT/RCA/Prime explanatory mechanism, or a general algorithmic breakthrough.

Required before a broad claim: independently run **extended JMH** with at least two JVM forks, multiple warmup/measurement iterations, several seeds and distributions; report allocation/GC and confidence or dispersion. Add 32,768+ item cases, natural corpora, and end-to-end tokenization + counting + sorting. Separately compare an equally instrumented native comparator baseline and benchmark a pure no-meter sorter. In a final paper, quote both favorable and negative cases, and don't count the fallback to Merge as bucket-sort superiority.

See [THOROUGH_VALIDATION.md](THOROUGH_VALIDATION.md) for commands and the manually dispatchable extended GitHub Actions profile. This report is a scoped screening, not an independently replicated result.
