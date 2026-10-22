# Expanded competitor screening — 2026-10-22

**Source:** commit `bdb60cb4c833eb160473d0edee3aa5a497c2ef57`, [GitHub Actions push run 39853520292](https://github.com/rickyjreyes/wordcount/actions/runs/39853520292). Hosted Java 17, Ubuntu. **25 JUnit tests passed; full-sort oracle, separate Top-K oracle, existing CLI checks and expanded smoke matrix all passed.**

Preliminary CI medians only: shared ephemeral runner, no JMH, no confidence intervals or portable performance claims. Main matrix uses 7 measured runs, 2 warmups and seed 2017, each algorithm gets identical arrays; cloning, sorting and all candidate-specific work are timed. The separate smoke datasets use fewer repetitions as shown in logs.

## Full sort: random, n=8192 unique synthetic frequency records

| Method | Median ms |
| --- | ---: |
| Frequency bucket (existing conventional specialization) | **0.530** |
| Hybrid (dispatches to frequency bucket; dispatch included) | 0.841 |
| Handwritten Merge | 1.325 |
| Sparse bucket | 1.454 |
| PDQ-inspired three-way introsort | 1.735 |
| Prime-inspired introsort | 1.830 |
| WCT-inspired blocks | 2.183 |
| RCA-inspired natural runs | 2.691 |
| Native JDK parallel sort | 4.226 |
| Instrumented JDK | 4.303 |
| Radix with full lexical presort | 4.601 |
| SHA-256 audit + Merge | 4.651 |
| Uninstrumented JDK | 4.784 |

Original Insertion is visibly skipped above 4096 records. This is a **within-run ranking**, not a new independent record or complexity result. The already implemented conventional dense frequency-bucket method remains fastest for this narrow generator.

## Full sort: Hamlet n=5075 distinct word-frequency records

From this run's final full matrix: frequency bucket 0.851 ms, handwritten Merge 0.905 ms, PDQ-inspired 1.233 ms, Prime-inspired 1.247 ms, Hybrid 1.396 ms, WCT-inspired 1.427 ms, RCA 1.782 ms, native JDK 1.914 ms, Radix 2.287 ms, Parallel 2.290 ms, Sparse Bucket 2.401 ms, Crypto+Merge 4.658 ms. Differences this small, especially 0.851 versus 0.905 ms, require stable-hardware replication.

## Harder shapes, smoke runs only

- Sparse unique counts, n=512: Merge 0.081 ms; Frequency Bucket **explicitly fell back to Merge** and timed 0.129 ms, Sparse Bucket 0.446 ms, Radix 0.603 ms. Sparse Bucket correctness and bounded memory matter, but no speed win was established.
- Zipf-like n=2048: Merge 0.330 ms, PDQ-inspired 0.385 ms, Frequency Bucket **explicit fallback to Merge** 0.655 ms, Sparse Bucket 0.909 ms, Radix 1.595 ms.
- Unicode and long-prefix input also PASS the exact reference oracle. All preprocessing is counted; Radix performs a full lexical presort, so its result is NOT evidence that sorting the composite key is linear overall.

## Separate Top-K (not a full-sort ranking)

| Task | Heap + exact final K ordering | Full JDK sort + truncate | Within-run ratio |
| --- | ---: | ---: | ---: |
| Random n=8192, k=10 (5 runs, 1 warmup) | **0.848 ms** | 4.833 ms | 5.70× |
| Hamlet n=5075, k=10 (3 runs, 1 warmup) | **0.447 ms** | 1.607 ms | 3.60× |
| Equal counts n=512, k=1000 (2 runs, no warmup) | 0.555 ms | **0.362 ms** | Heap slower |

Top-K deliberately solves only partial selection and must **not** be placed on the same speed leaderboard as full-sort methods. At k ≥ n, heap overhead can be worse than full sorting.

See [COMPETITIVE_CONTROLS.md](COMPETITIVE_CONTROLS.md) for algorithm definitions, caveats and commands. Next claims need JMH, independent reruns, larger datasets, allocations, CPU environment, and multiple seeds.
