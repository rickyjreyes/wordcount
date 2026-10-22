# Adaptive v1 prospective holdout — first run, 2026-10-22

**Verdict: FROZEN V1 FAILS preregistered P0–P3 joint success gate. Do not retune v1 or quietly replace these numbers.**

This report describes the **first**, prospective PR run: [Frozen Adaptive V1 Holdout run 39860412091](https://github.com/rickyjreyes/wordcount/actions/runs/39860412091), artifact `adaptive-v1-frozen-holdout-39860412091`, containing all raw samples, CSV summaries, per-case and per-shape comparisons, verdict and Java/JMH environment metadata. The workflow's checked-out PR merge revision recorded `dc56977b8ffa49abc9505fdb350f90b2c3d2ad2f`; the immutable frozen feature head was `0a283d37a9376a747856b628b938946c2679f91c`. Source/thresholds/grid were fixed in [ADAPTIVE_V1_FROZEN_PROTOCOL.md](ADAPTIVE_V1_FROZEN_PROTOCOL.md) before this first execution. Future PR/master runs are **replications**, not fresh holdouts.

Environment: GitHub-hosted Ubuntu, Temurin Java 17.0.20.1, 4 available processors. This is a shared ephemeral virtual runner; timings are exploratory and may be affected by CPU/JIT/GC/system noise. No multi-host independent result.

## Exact coverage and gates

- 14 predeclared dataset families × 3 sizes (512, 2048, 8192) × 5 unseen seeds (101, 4099, 65537, 9001, 20261) = **210 matched workload cases**.
- Eleven full-sort implementations; 3 measured repetitions after one warmup per case, with randomized method order = **6,930 valid raw observations**. Exact independent total-order reference and identical input/output SHA-256 were verified; 34 Java tests also passed. Python holdout report analyzer regression tests passed.
- Data collection and reporting were outside the timer. The timed public method includes clone, adaptive inspection, comparator accounting, allocations, and the selected underlying sort.

| Frozen gate | Observed | Result |
| --- | --- | --- |
| P0 — exact correctness, full coverage, fingerprints | 210 complete cases; 6,930 individually audited runs; 34 Java tests PASS | **PASS** |
| P1 — adaptive vs prior Hybrid: geometric-mean ratio <0.95 **and** >=60% paired case wins | **0.868819** (13.1% lower), **159/210** case wins (75.7%) | **PASS** |
| P2 — adaptive vs best *single* fixed method over holdout: ratio <0.95 | Best fixed = **Frequency Bucket**. Adaptive/bucket = **1.179567** (17.96% slower) | **FAIL** |
| P3 — p90 regret against each case's hindsight fastest fixed method <=1.50 | **1.974103** | **FAIL** |

Overall **FAIL**, despite the encouraging improvement over the previous Hybrid. The per-case hindsight minimum is a diagnostic lower bound, not a deployable oracle.

Frequency Bucket itself used a labeled sparse-range fallback to Merge in **60/210** cases. Adaptive v1's actual routes: already-ordered **15** cases; strict reverse **15**; genuine histogram+lexical tie sorting **90**; Merge **90**. No misleading attribution of fallback to the bucket mechanism.

## Shape-level generalization / falsification

For each shape below, 15 cases (three sizes × five unseen seeds). Ratios are geometric means of **paired case medians**; under 1 is favorable to adaptive v1.

| Shape | Adaptive / old Hybrid | Paired wins vs Hybrid | Adaptive / overall best fixed (Frequency Bucket) |
| --- | ---: | ---: | ---: |
| Random dense counts | 1.495 | 3/15 | 2.266 |
| Already sorted | 0.661 | 13/15 | 1.081 |
| Reversed | 0.593 | 15/15 | 0.790 |
| Nearly sorted | 0.816 | 13/15 | 1.669 |
| Few counts | 0.737 | 15/15 | 1.213 |
| Equal counts | 0.891 | 15/15 | 1.053 |
| Natural runs | 0.686 | 14/15 | 1.126 |
| Zipf-like | 0.881 | 11/15 | 0.974 |
| Sparse range | 0.990 | 5/15 | 0.980 |
| Unicode | 0.970 | 9/15 | 1.458 |
| Long prefix | 0.954 | 11/15 | 1.223 |
| Staircase (new holdout family) | 0.999 | 9/15 | 1.130 |
| Alternating (new holdout family) | 0.897 | 15/15 | 1.065 |
| Clusters/outliers (new holdout family) | 0.895 | 11/15 | 1.061 |

In the random-dense family, v1 scans and then generally calls bucket anyway: the extra scan costs more than it saves. On reversed inputs its strictly monotone shortcut improves performance substantially. The fixed bucket method remains the best overall single strategy for this test mix, and the selector's tail behavior violates the preregistered constraint. No evidence for a new asymptotic sorting bound, universal dominance, or physical-theory derivation follows.

## Separate JMH *smoke* (not primary, insufficient repetitions)

One fork and two 200ms measured iterations for one random n=8192 seed=101 case; JMH checks that the executable measures the intended API:

| Method | us/op (observed smoke) |
| --- | ---: |
| Frequency Bucket | 448.197 |
| Hybrid | 558.477 |
| Adaptive v1 | 580.072 |
| Merge | 1,229.223 |
| Native JDK | 5,095.503 |

Different instrumentation overhead between methods, shared-runner variability and scant JMH iterations prevent a broad claim. This short result is directionally consistent with random-family v1 overhead; it is **not** a held-out independent generalization result.

## Reproducibility and next scientific step

Download **the artifact of the first run** from [GitHub Actions run 39860412091](https://github.com/rickyjreyes/wordcount/actions/runs/39860412091); retain `verdict.txt`, `cases.csv`, `by_shape.csv`, `summary.csv`, `samples.csv`, CPU/JVM metadata and JMH JSON. All negative cases are retained. Artifact ZIP SHA-256 observed at analysis: `c69690ef99e21d1b15a8f10d32fd892ce8e3703b3d39dfc82a4cbe0c6e93ea8f`.

Any new selector must be named **adaptive-v2**, formulated without fitting to the v1 holdout, and evaluated on an independently specified new holdout. This v1 experiment supports a narrower finding: a simple input-order/density heuristic can outperform the old hybrid on some distributions, but did **not** beat the strongest fixed comparator in the prospective test.
