# First algorithm competition — screening results (2026-10-22)

**Status:** preliminary exploratory screening, not frozen speed claims and not evidence of a new WCT/RCA/Prime sorting theorem.

- Source: [GitHub Actions push run #39847427221](https://github.com/rickyjreyes/wordcount/actions/runs/39847427221) for commit `1049fc00da844b9c195965ac8d0427855a047e53`; Temurin Java 17 on ephemeral GitHub-hosted Ubuntu runner.
- Execution: 7 measured runs, 2 warmups, seeded common inputs (`2017`), randomized method execution order each measurement round.
- Verification: 15 JUnit tests passed (zero failures/errors), original coursework compiled, normal CLI and all experimental smoke/matrix calls passed. Array equality to independently JDK-sorted reference was required for every candidate and every measured run.
- Timing in milliseconds includes copying the array and the algorithm; crypto includes input/output SHA-256 serialization/digests. It excludes tokenization, input generation, correctness verification and printed output. Comparator calls are instrumented for every implementation. JDK's internal writes are not instrumented. Medians are not confidence intervals; this is **not** JMH or controlled cross-host benchmarking.
- `N` denotes unique `Frequency(word,count)` records. The real Hamlet sample contained 5,075 unique entries with the modern tokenizer, deterministically shuffled prior to measurement.

## Medians (ms; lower is better)

| Method | Random N=512 | Random N=2048 | Random N=8192 | Hamlet N=5075 |
| --- | ---: | ---: | ---: | ---: |
| JDK baseline | 0.411 | 1.463 | 7.067 | 1.647 |
| Handwritten merge baseline | **0.097** | **0.467** | **2.763** | **1.101** |
| Insertion baseline | 0.705 | 7.427 | skipped | skipped |
| WCT block heuristic | 0.452 | 2.926 | 4.677 | 3.311 |
| RCA natural runs | 0.264 | 1.378 | 5.076 | 2.407 |
| Prime introsort | 0.188 | 1.696 | 4.202 | 1.473 |
| SHA-256 + merge audit | 1.354 | 5.021 | 12.222 | 3.423 |
| Hybrid dispatcher | 0.249 | 1.809 | 4.890 | 1.926 |

The **handwritten conventional merge control won all four columns in this screening**. Some experimental methods beat the instrumented JDK baseline for specific inputs; this is not a win over the strongest conventional comparator and may reflect measurement artifacts at these timescales.

### Distribution sensitivity at N=2048

| Dataset | JDK ms | Merge ms | WCT ms | RCA ms | Prime ms | Hybrid ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Random | 1.628 | 0.512 | 2.895 | 1.230 | 1.647 | 1.810 |
| Sorted | **0.124** | 0.491 | 1.490 | 0.178 | 0.740 | 0.442 |
| Reverse | **0.168** | 0.282 | 1.514 | 0.235 | 0.563 | 0.670 |
| Nearly sorted | 0.251 | 0.521 | 1.635 | 0.417 | 0.756 | 0.691 |
| Few counts | 1.565 | **1.351** | 2.841 | 1.872 | 1.903 | 2.139 |
| Equal counts | 1.159 | **0.730** | 2.119 | 1.474 | 1.009 | 1.265 |
| Locally sorted runs | 0.921 | **0.449** | 2.676 | 0.917 | 1.704 | 0.784 |

For nearly sorted N=2048 input, insertion took 0.119 ms (versus JDK 0.251 ms) because its displacement work was small; it took 12.953 ms for reverse input and is therefore not an across-input winner.

### Comparison counts on random N=2048

- JDK: 20,015.
- Handwritten merge: 19,939.
- WCT: 38,576.
- RCA: 20,975.
- Prime: 28,210 (prime selection overhead additionally affects runtime).
- Hybrid: 30,257 (includes dispatcher comparisons).
- Crypto: 19,939; SHA-256 adds cost without reducing comparisons.
- Insertion: 1,059,910.

### Scientific disposition

| Candidate | Correctness | Competitive verdict on these measurements | Next hypothesis to falsify |
| --- | --- | --- | --- |
| WCT | PASS | Not competitive with merge here; defect/block/heap overhead | Derive a principled *low-cost* spectral or discrete-curvature invariant that actually saves comparisons, rather than a renamed block sorter |
| RCA | PASS | Adaptive advantage on some ordered inputs but not consistently ahead of JDK/merge | Separate adaptivity gains from standard natural mergesort; measure run-count and allocations and compare against JDK TimSort on long-run corpora |
| Prime | PASS | Sometimes faster than instrumented JDK, but not merge in sampled columns | Ablate prime-stride choice against equivalent nonprime and randomized pivot sampling with identical introsort fallback; account for prime-finding time |
| Crypto | PASS | Expected timing overhead; offers reproducible digests rather than new sorting speed | Audit threat model, canonical transcript, tamper tests and established signatures for authenticated provenance |
| Hybrid | PASS | No overall competitive win; dispatch scan adds overhead | Compare selection regret against ideal choice, make dispatcher cheaper, and measure separately |

**Required follow-up:** issue #2's JMH-grade benchmark on stable hardware with full environment/GC/allocation reporting, independent reruns, larger sizes and repeated seeds. No published gain or complexity breakthrough should be claimed from a single CI runner.
