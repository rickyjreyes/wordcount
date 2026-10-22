# Optimized sorting screening — 2026-10-22

**Classification:** preliminary, reproducible CI screening; exact output correctness verified, performance generalization OPEN. These are established algorithm families and research-inspired heuristics, not new theoretical sorting-complexity results.

## Provenance and protocol

- Source commit: `b40a4b470f1a83ad686e1f549acf28bb2494f34f`.
- [GitHub Actions push run 39849423118](https://github.com/rickyjreyes/wordcount/actions/runs/39849423118); [PR run 39849434150](https://github.com/rickyjreyes/wordcount/actions/runs/39849434150). Both passed.
- Java 17 on GitHub-hosted Ubuntu; **19 JUnit tests passed, 0 failures, 0 errors**, including exhaustive small permutations, dense/sparse frequency ranges, monotone fast paths, hybrid branches, extreme counts and oracle checks.
- 7 measured iterations and 2 warmups; seeded identical inputs (2017), randomized candidate execution order. Each timing includes cloning the array and the **entire selected algorithm**, including histogram, tie sorting, prime selection, dispatch scanning or crypto digest as applicable. Input generation, file tokenization, output printing and correctness validation are outside timings. Insertion is explicitly skipped over 4096 records.
- Every output is checked against a separately defined JDK `Sorts.ORDER` sorted copy (descending count, ascending word) on every measured run. The native JDK performance control and candidates use the same direct key comparator; it is not instrumented, so its operation counts are N/A. Performance remains indicative only because this is **not JMH**, uses small timings, has no confidence intervals, and runs on shared/ephemeral hardware.
- All counts below are milliseconds (lower is better). N counts **distinct Frequency records**, not text token count.

## Same-run medians on generated random datasets

| Method | N=128 | N=512 | N=2048 | N=8192 |
| --- | ---: | ---: | ---: | ---: |
| Native JDK same comparator | 0.108 | 0.179 | 0.685 | 5.109 |
| Handwritten Merge | **0.020** | **0.081** | 0.555 | 2.141 |
| Frequency bucket + lexical ties | 0.090 | 0.205 | **0.198** | **0.942** |
| WCT curvature blocks (revised) | 0.078 | 0.162 | 0.849 | 3.530 |
| RCA natural runs | 0.059 | 0.230 | 0.991 | 4.670 |
| Prime introsort | 0.097 | 0.178 | 0.683 | 4.885 |
| Hybrid dispatcher (selected frequency buckets for N≥512 here) | 0.122 | 0.291 | 0.635 | 1.516 |
| SHA-256 + Merge audit | 0.310 | 0.829 | 3.020 | 9.080 |

The specialized conventional frequency-bucket approach took **0.942 ms versus 2.141 ms** for Merge and 5.109 ms for native JDK on random N=8192: ratios 2.27× and 5.42× respectively. These are *within-run preliminary ratios*, not robust or general speedup claims. At N=128 and 512, ordinary Merge won.

At N=2048, frequency buckets took 0.198 ms versus Merge 0.555 ms (2.81× on that generated random input). The generator used positive integer counts in 1..1000; results must not be extrapolated to arbitrary sparse ranges or long lexical ties.

## Real corpus: Hamlet

With the modern UTF-8 tokenizer, the sample yielded 5,075 distinct frequency records, then received a deterministic seed-2017 shuffle. The same-run medians were:

| Method | Milliseconds |
| --- | ---: |
| Frequency bucket | **1.043** |
| Handwritten Merge | 1.170 |
| Prime introsort | 1.302 |
| WCT curvature blocks | 1.648 |
| Native JDK | 2.974 |
| Hybrid (dispatched to frequency bucket) | 1.957 |
| RCA natural runs | 2.332 |
| SHA-256 audit + Merge | 5.576 |

The apparent 1.12× edge of the specialized sort over Merge here is small and **not reliable evidence of a repeatable Hamlet advantage**. A preceding CI run (with the earlier JDK comparator control) measured a slightly slower bucket result than Merge; the corpus results should be treated as inconclusive until stable-hardware replication.

## Domain-specialized mechanism

`freqbucket` scans min/max positive integer count, allocates a bounded dense histogram, scatters records by descending frequency, and exactly sorts each equal-count segment lexically. Its measured work includes the min/max scan, histogram, allocations, scattering, lexical ties, and final copy. With `R=max-min+1` and `n_c` items in frequency c, dense-path time is `O(n + R + Σ_c n_c log n_c)` and memory `O(n+R)`. Tiny ties use insertion sort and large ties use merge. A range exceeding 1,000,000 or `max(4096,4n)` selects an *explicitly labeled* conventional Merge fallback; this prevents a giant sparse histogram. This exploits the restricted integer primary key and does not contradict the generic comparison-sorting lower bound.

`hybrid` now compares adjacency disorder, sign changes and dense-frequency range, and reports whether it selected RCA, frequency buckets or Prime. Its scan and selected algorithm are both timed. Its 1.516 ms at N=8192 remained slower than directly selecting buckets at 0.942 ms; adaptation has a real cost. Prior benchmark results are archived separately in [EXPERIMENTAL_RESULTS_2026-10-22.md](EXPERIMENTAL_RESULTS_2026-10-22.md), not overwritten.

## Falsification / next work

- Test randomized **multiple seeds** and full word-count distributions, extreme counts, long identical-frequency groups, non-ASCII, and sparse count ranges.
- Run JMH in stable environments with GC, heap, CPU, allocations, warmup and confidence intervals; benchmark tokenization/counting/sorting separately and end-to-end.
- Ablate frequency histogram from word tie sorting and compare with a specialized conventional baseline, not only generic JDK.
- Measure input preparation and total work if used in a production WordCount pipeline.
- Do not claim WCT/RCA/Prime has won from the success of a conventional frequency-bucket method. Keep negative WCT and Hybrid results visible.
